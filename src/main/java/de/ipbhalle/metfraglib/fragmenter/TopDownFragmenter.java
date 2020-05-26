package de.ipbhalle.metfraglib.fragmenter;

import java.util.ArrayList;

import de.ipbhalle.metfraglib.FastBitArray;
import de.ipbhalle.metfraglib.fragment.AbstractTopDownBitArrayFragment;
import de.ipbhalle.metfraglib.interfaces.ICandidate;
import de.ipbhalle.metfraglib.list.FragmentList;
import de.ipbhalle.metfraglib.parameter.Constants;
import de.ipbhalle.metfraglib.parameter.VariableNames;
import de.ipbhalle.metfraglib.precursor.AbstractTopDownBitArrayPrecursor;
import de.ipbhalle.metfraglib.precursor.BitArrayPrecursor;
import de.ipbhalle.metfraglib.settings.Settings;

public class TopDownFragmenter extends AbstractTopDownFragmenter {
	
	protected Double minimumMassDeviationForFragmentGeneration = Constants.DEFAULT_MIN_MASS_DEV_FOR_FRAGMENT_GENERATION;
	protected Byte maximumNumberOfAFragmentAddedToQueue;
	protected int numberOfGeneratedFragments = 0;
	protected boolean ringBondsInitialised;
	protected FastBitArray ringBondFastBitArray;
	
	public TopDownFragmenter(final ICandidate candidate, Settings settings) throws Exception {
		super(settings);
		this.scoredCandidate = candidate;
		this.maximumTreeDepth = (Byte)settings.get(VariableNames.MAXIMUM_TREE_DEPTH_NAME);
		this.minimumFragmentMassLimit = (Double)settings.get(VariableNames.MINIMUM_FRAGMENT_MASS_LIMIT_NAME);
		this.maximumNumberOfAFragmentAddedToQueue = (Byte)settings.get(VariableNames.MAXIMUM_NUMBER_OF_TOPDOWN_FRAGMENT_ADDED_TO_QUEUE);
		this.ringBondsInitialised = false;
		this.ringBondFastBitArray = new FastBitArray(this.scoredCandidate.getPrecursorMolecule().getNonHydrogenBondCount(), false);
	}

	@Override
	public FragmentList generateFragments() {
		FragmentList generatedFragments = new FragmentList();
		java.util.Queue<AbstractTopDownBitArrayFragment> temporaryFragments = new java.util.LinkedList<>();
		java.util.Queue<Byte> numberOfFragmentAddedToQueue = new java.util.LinkedList<>();
		java.util.Queue<de.ipbhalle.metfraglib.FastBitArray> nextBondIndecesToRemove = new java.util.LinkedList<>();
		
		/*
		 * set first fragment as root for fragment generation (precursor)
		 */
		AbstractTopDownBitArrayFragment root = ((AbstractTopDownBitArrayPrecursor)this.scoredCandidate.getPrecursorMolecule()).toFragment();
		root.setID(++this.numberOfGeneratedFragments);
		temporaryFragments.add(root);
		generatedFragments.addElement(root);
		numberOfFragmentAddedToQueue.add((byte)1);
		nextBondIndecesToRemove.add(root.getBondsFastBitArray());
		
		for(int k = 1; k <= this.maximumTreeDepth; k++) {
			java.util.Queue<AbstractTopDownBitArrayFragment> newTemporaryFragments = new java.util.LinkedList<>();
			java.util.Queue<Byte> newNumberOfFragmentAddedToQueue = new java.util.LinkedList<>();
			java.util.Queue<de.ipbhalle.metfraglib.FastBitArray> newNextBondIndecesToRemove = new java.util.LinkedList<>();
			
			while (!temporaryFragments.isEmpty()) {
				AbstractTopDownBitArrayFragment nextTopDownFragmentForFragmentation = temporaryFragments.poll();
				byte numberOfNextTopDownFragmentForFragmentationAddedToQueue = numberOfFragmentAddedToQueue.poll();
				int[] indecesOfSetBondsOfNextTopDownFragment = nextBondIndecesToRemove.poll().getSetIndeces();
				
				for (int i = 0; i < indecesOfSetBondsOfNextTopDownFragment.length; i++) {
					short nextBondIndexToRemove = (short)indecesOfSetBondsOfNextTopDownFragment[i];
					/*
					* if index of selected bond is smaller than the maximum index of a cleaved bond of the
					* fragment then select another bond
					* prevents generating fragments redundantly
					*/
					if (nextBondIndexToRemove < nextTopDownFragmentForFragmentation.getMaximalIndexOfRemovedBond()
							|| !nextTopDownFragmentForFragmentation.getBondsFastBitArray().get(nextBondIndexToRemove)) {
						continue;
					}
					short[] indecesOfBondConnectedAtoms = ((BitArrayPrecursor)this.scoredCandidate.getPrecursorMolecule()).getConnectedAtomIndecesOfBondIndex(nextBondIndexToRemove);
					/*
					* getting fragment generated by cleavage of the current bond "nextBondIndexToRemove"
					*/
					AbstractTopDownBitArrayFragment[] newGeneratedTopDownFragments = 
						nextTopDownFragmentForFragmentation.traverseMolecule(this.scoredCandidate.getPrecursorMolecule(), nextBondIndexToRemove, indecesOfBondConnectedAtoms);
					
					/* 
					 * if we got two fragments then save these as valid ones
					 */
					if (newGeneratedTopDownFragments.length == 2) {
						if (newGeneratedTopDownFragments[0].getMonoisotopicMass(this.scoredCandidate.getPrecursorMolecule()) > this.minimumFragmentMassLimit - this.minimumMassDeviationForFragmentGeneration) {
							newGeneratedTopDownFragments[0].setID(++this.numberOfGeneratedFragments);
							generatedFragments.addElement(newGeneratedTopDownFragments[0]);
							newNextBondIndecesToRemove.add(newGeneratedTopDownFragments[0].getBondsFastBitArray());
							newNumberOfFragmentAddedToQueue.add((byte)1);
							newTemporaryFragments.add(newGeneratedTopDownFragments[0]);
						}
						if (newGeneratedTopDownFragments[1].getMonoisotopicMass(this.scoredCandidate.getPrecursorMolecule()) > this.minimumFragmentMassLimit - this.minimumMassDeviationForFragmentGeneration) {
							newGeneratedTopDownFragments[1].setID(++this.numberOfGeneratedFragments);
							generatedFragments.addElement(newGeneratedTopDownFragments[1]);
							newNextBondIndecesToRemove.add(newGeneratedTopDownFragments[1].getBondsFastBitArray());
							newNumberOfFragmentAddedToQueue.add((byte)1);
							newTemporaryFragments.add(newGeneratedTopDownFragments[1]);
						}
						
					}
					/*
					 *  if just one fragment then we have to cleave once again
					 */
					else {
						if (newGeneratedTopDownFragments[0].getMonoisotopicMass(this.scoredCandidate.getPrecursorMolecule()) > this.minimumFragmentMassLimit - this.minimumMassDeviationForFragmentGeneration) {
							if (numberOfNextTopDownFragmentForFragmentationAddedToQueue < this.maximumNumberOfAFragmentAddedToQueue) {
								temporaryFragments.add(newGeneratedTopDownFragments[0]);
								numberOfFragmentAddedToQueue.add((byte)(numberOfNextTopDownFragmentForFragmentationAddedToQueue + 1));
							//	nextBondIndecesToRemove.add(this.precursorMolecule.getFastBitArrayOfBondsBelongingtoRingLikeBondIndex(nextBondIndexToRemove));
								nextBondIndecesToRemove.add(newGeneratedTopDownFragments[0].getBondsFastBitArray());
							}
							else {
								newTemporaryFragments.add(newGeneratedTopDownFragments[0]);
								newNumberOfFragmentAddedToQueue.add((byte)1);
								newNextBondIndecesToRemove.add(newGeneratedTopDownFragments[0].getBondsFastBitArray());
							}
						}
					}
				}
			}
			temporaryFragments = newTemporaryFragments;
			numberOfFragmentAddedToQueue = newNumberOfFragmentAddedToQueue;
			nextBondIndecesToRemove = newNextBondIndecesToRemove;
		}
		
		temporaryFragments = null;
		numberOfFragmentAddedToQueue = null;
		nextBondIndecesToRemove = null;
		
		return generatedFragments;
	}
	
	/**
	 * 
	 * @param newGeneratedTopDownFragments
	 */
	protected void processGeneratedFragments(AbstractTopDownBitArrayFragment[] newGeneratedTopDownFragments) {
		if (newGeneratedTopDownFragments.length == 2) {
			newGeneratedTopDownFragments[0].setID(++this.numberOfGeneratedFragments);
			newGeneratedTopDownFragments[0].setAddedToQueueCounts((byte)1);
			newGeneratedTopDownFragments[1].setID(++this.numberOfGeneratedFragments);
			newGeneratedTopDownFragments[1].setAddedToQueueCounts((byte)1);
			if (newGeneratedTopDownFragments[0].getMonoisotopicMass(this.scoredCandidate.getPrecursorMolecule()) <= this.minimumFragmentMassLimit - this.minimumMassDeviationForFragmentGeneration) {
				newGeneratedTopDownFragments[0].setAsDiscardedForFragmentation();
			}
			if (newGeneratedTopDownFragments[1].getMonoisotopicMass(this.scoredCandidate.getPrecursorMolecule()) <= this.minimumFragmentMassLimit - this.minimumMassDeviationForFragmentGeneration) {
				newGeneratedTopDownFragments[1].setAsDiscardedForFragmentation();
			} 
		}
		else 
			newGeneratedTopDownFragments[0].setID(++this.numberOfGeneratedFragments);
	}
	
	/**
	 * generates all fragments of the given precursor fragment to reach the new tree depth
	 */
	@Override
	public ArrayList<AbstractTopDownBitArrayFragment> getFragmentsOfNextTreeDepth(AbstractTopDownBitArrayFragment precursorFragment) {
		FastBitArray ringBonds = new FastBitArray(precursorFragment.getBondsFastBitArray().getSize(), false);
		java.util.Queue<AbstractTopDownBitArrayFragment> ringBondCuttedFragments = new java.util.LinkedList<>();
		java.util.Queue<Short> lastCuttedBondOfRing = new java.util.LinkedList<>();
		ArrayList<AbstractTopDownBitArrayFragment> fragmentsOfNextTreeDepth = new ArrayList<>();
		/*
		 * generate fragments of skipped bonds
		 */
		if(this.ringBondsInitialised) 
			this.generateFragmentsOfSkippedBonds(fragmentsOfNextTreeDepth, precursorFragment);
		
		/*
		 * get the last bond index that was removed; from there on the next bonds will be removed
		 */
		short nextBrokenIndexBondIndexToRemove = (short)(precursorFragment.getMaximalIndexOfRemovedBond() + 1);
		/*
		 * start from the last broken bond index
		 */
		for(short i = nextBrokenIndexBondIndexToRemove; i < precursorFragment.getBondsFastBitArray().getSize(); i++) {		
			if(!precursorFragment.getBondsFastBitArray().get(i)) continue;
			short[] indecesOfBondConnectedAtoms = ((BitArrayPrecursor)this.scoredCandidate.getPrecursorMolecule()).getConnectedAtomIndecesOfBondIndex(i);
			/*
			 * try to generate at most two fragments by the removal of the given bond
			 */
			AbstractTopDownBitArrayFragment[] newGeneratedTopDownFragments = precursorFragment.traverseMolecule(this.scoredCandidate.getPrecursorMolecule(), i, indecesOfBondConnectedAtoms);
			/*
			 * in case the precursor wasn't splitted try to cleave an additional bond until 
			 * 
			 * 1. two fragments are generated or
			 * 2. the maximum number of trials have been reached
			 * 3. no further bond can be removed
			 */
			if(newGeneratedTopDownFragments.length == 1) 
			{
				ringBonds.set(i, true);
				ringBondCuttedFragments.add(newGeneratedTopDownFragments[0]);
				lastCuttedBondOfRing.add(i);
				if(!this.ringBondsInitialised) this.ringBondFastBitArray.set(i);
			}
			/*
			 * pre-processing of the generated fragment/s
			 */
			this.processGeneratedFragments(newGeneratedTopDownFragments);
			/*
			 * if two new fragments have been generated set them as valid
			 */
			if(newGeneratedTopDownFragments.length == 2) {
				newGeneratedTopDownFragments[0].setAsValidFragment();
				newGeneratedTopDownFragments[1].setAsValidFragment();
			}
			/*
			 * add fragment/s to vector after setting the proper precursor
			 */ 
			for(int k = 0; k < newGeneratedTopDownFragments.length; k++) {
				if(newGeneratedTopDownFragments.length == 2) fragmentsOfNextTreeDepth.add(newGeneratedTopDownFragments[k]);
			}
		}
		/*
		 * create fragments by ring bond cleavage and store them in the given vector
		 */
		this.createRingBondCleavedFragments(fragmentsOfNextTreeDepth, precursorFragment, ringBondCuttedFragments, ringBonds, lastCuttedBondOfRing);
		this.ringBondsInitialised = true;
		
		return fragmentsOfNextTreeDepth;
	}
	
	/**
	 * 
	 * @param newGeneratedTopDownFragments
	 * @param precursorFragment
	 * @param toProcess
	 * @param ringBondFastBitArray
	 * @param lastCuttedRingBond
	 * @return
	 */
	protected ArrayList<AbstractTopDownBitArrayFragment> createRingBondCleavedFragments(ArrayList<AbstractTopDownBitArrayFragment> newGeneratedTopDownFragments, AbstractTopDownBitArrayFragment precursorFragment, java.util.Queue<AbstractTopDownBitArrayFragment> toProcess, FastBitArray ringBondFastBitArray, java.util.Queue<Short> lastCuttedRingBond) {
		/*
		 * process all fragments that have been cutted in a ring without generating 
		 * a new one
		 */
		while(!toProcess.isEmpty() && lastCuttedRingBond.size() != 0) {
			/*
			 * 
			 */
			AbstractTopDownBitArrayFragment currentFragment = toProcess.poll();
			short nextRingBondToCut = (short)(lastCuttedRingBond.poll() + 1);
			/*
			 * 
			 */ 
			for(short currentBond = nextRingBondToCut; currentBond < ringBondFastBitArray.getSize(); currentBond++) 
			{
			 	if(!ringBondFastBitArray.get(currentBond)) continue;
				if(currentFragment.getBrokenBondsFastBitArray().get(currentBond)) continue;
				AbstractTopDownBitArrayFragment[] newFragments = {currentFragment}; 
				short[] connectedAtomIndeces = ((BitArrayPrecursor)this.scoredCandidate.getPrecursorMolecule()).getConnectedAtomIndecesOfBondIndex(currentBond);
				newFragments = currentFragment.traverseMolecule(this.scoredCandidate.getPrecursorMolecule(), currentBond, connectedAtomIndeces);
				
				//
				// pre-processing of the generated fragment/s
				//
				this.processGeneratedFragments(newFragments);
				//
				// if two new fragments have been generated set them as valid
				//
				if(newFragments.length == 2) {
					newFragments[0].setAsValidFragment();
					newFragments[1].setAsValidFragment();
					newFragments[0].setLastSkippedBond(currentFragment.getLastSkippedBond());
					newFragments[1].setLastSkippedBond(currentFragment.getLastSkippedBond());
				}
				//
				// set precursor fragment of generated fragment(s) and the child(ren) of precursor fragments
				//
				for(int k = 0; k < newFragments.length; k++) {
					if(newFragments.length == 2) {
						newGeneratedTopDownFragments.add(newFragments[k]);
					}
				}
				
				if(newFragments.length == 1) 
				{ 
					if(newFragments[0].getAddedToQueueCounts() < this.maximumNumberOfAFragmentAddedToQueue) {
						toProcess.add(newFragments[0]);
						lastCuttedRingBond.add(currentBond);
					}
					else {
						newGeneratedTopDownFragments.add(newFragments[0]);
					}
				}
			}
		}
		
		return newGeneratedTopDownFragments;
	}

	/*
	 * generate fragments by removing bonds that were skipped due to ring bond cleavage
	 */
	protected void generateFragmentsOfSkippedBonds(ArrayList<AbstractTopDownBitArrayFragment> newGeneratedTopDownFragments, AbstractTopDownBitArrayFragment precursorFragment) {
		short lastSkippedBonds = precursorFragment.getLastSkippedBond();
		if(lastSkippedBonds == -1 || precursorFragment.getNonHydrogenBondCount() <= lastSkippedBonds) return;
		
		for(short currentBond = lastSkippedBonds; currentBond < ringBondFastBitArray.getSize(); currentBond++) 
		{
		 	if(!this.ringBondFastBitArray.get(currentBond)) continue;
		 	if(!precursorFragment.getBondsFastBitArray().get(currentBond)) continue;
			short[] connectedAtomIndeces = ((BitArrayPrecursor)this.scoredCandidate.getPrecursorMolecule()).getConnectedAtomIndecesOfBondIndex(currentBond);
			AbstractTopDownBitArrayFragment[] newFragments = precursorFragment.traverseMolecule(this.scoredCandidate.getPrecursorMolecule(), currentBond, connectedAtomIndeces);
			this.processGeneratedFragments(newFragments);
			if(newFragments.length == 2) {
				newFragments[0].setAsValidFragment();
				newFragments[1].setAsValidFragment();
		 	}
		 	else {
		 		System.err.println("problem generating fragments");
		 		System.exit(1);
		 	}
			
			for(int k = 0; k < newFragments.length; k++) {
				if(newFragments.length == 2) {
					newGeneratedTopDownFragments.add(newFragments[k]);
				}
			}
		}
	}
}