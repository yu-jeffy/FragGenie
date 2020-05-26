package de.ipbhalle.metfraglib.fragmenterassignerscorer;

import de.ipbhalle.metfraglib.interfaces.ICandidate;
import de.ipbhalle.metfraglib.interfaces.IMatch;
import de.ipbhalle.metfraglib.list.MatchList;
import de.ipbhalle.metfraglib.list.SortedTandemMassPeakList;
import de.ipbhalle.metfraglib.match.MatchFragmentList;
import de.ipbhalle.metfraglib.match.MatchFragmentNode;
import de.ipbhalle.metfraglib.match.MatchPeakList;
import de.ipbhalle.metfraglib.settings.Settings;
import de.ipbhalle.metfraglib.parameter.Constants;
import de.ipbhalle.metfraglib.parameter.VariableNames;
import de.ipbhalle.metfraglib.precursor.AbstractTopDownBitArrayPrecursor;
import de.ipbhalle.metfraglib.fragment.AbstractTopDownBitArrayFragment;
import de.ipbhalle.metfraglib.fragment.AbstractTopDownBitArrayFragmentWrapper;

public class TopDownFragmenterAssignerScorer extends AbstractFragmenterAssignerScorer {

	protected boolean uniqueFragmentMatches;
	/*
	 * workaround
	 */
	protected java.util.Hashtable<String, Integer> bitArrayToFragment;
	
	/**
	 * 
	 */
	private final SortedTandemMassPeakList peakList;
	
	/**
	 * 
	 * @param settings
	 * @param candidate
	 * @param peakList
	 */
	public TopDownFragmenterAssignerScorer(Settings settings, ICandidate candidate, final SortedTandemMassPeakList peakList) throws Exception {
		super(settings, candidate);
		this.bitArrayToFragment = new java.util.Hashtable<>();
		this.uniqueFragmentMatches = (Boolean)this.settings.get(VariableNames.METFRAG_UNIQUE_FRAGMENT_MATCHES);
		this.peakList = peakList;
	}

	@Override
	public void calculate() {
		AbstractTopDownBitArrayPrecursor candidatePrecursor = (AbstractTopDownBitArrayPrecursor)(this.candidate).getPrecursorMolecule();
		//generate root fragment to start fragmentation
		AbstractTopDownBitArrayFragment root = candidatePrecursor.toFragment();
		Byte maximumTreeDepth = (Byte)settings.get(VariableNames.MAXIMUM_TREE_DEPTH_NAME);
		if(maximumTreeDepth == 0) {
			maximumTreeDepth = candidatePrecursor.getNumNodeDegreeOne() >= 4 ? (byte)3 : (byte)2;
		}
		this.candidate.setProperty(VariableNames.MAXIMUM_TREE_DEPTH_NAME, maximumTreeDepth);
		//read peaklist
		this.peakList.initialiseMassLimits((Double)this.settings.get(VariableNames.RELATIVE_MASS_DEVIATION_NAME), (Double)settings.get(VariableNames.ABSOLUTE_MASS_DEVIATION_NAME));
		Integer precursorIonType = (Integer)this.settings.get(VariableNames.PRECURSOR_ION_MODE_NAME);
		Boolean positiveMode = (Boolean)this.settings.get(VariableNames.IS_POSITIVE_ION_MODE_NAME);
		int precursorIonTypeIndex = Constants.ADDUCT_NOMINAL_MASSES.indexOf(precursorIonType);
		this.fragmenter.setMinimumFragmentMassLimit(this.fragmenter.getMinimumFragmentMassLimit() - Constants.ADDUCT_MASSES.get(precursorIonTypeIndex));
		
		/*
		 * prepare the processing
		 */
		java.util.Queue<AbstractTopDownBitArrayFragmentWrapper> toProcessFragments = new java.util.LinkedList<>();
		/*
		 * wrap the root fragment
		 */
		AbstractTopDownBitArrayFragmentWrapper rootFragmentWrapper = new AbstractTopDownBitArrayFragmentWrapper(root, this.peakList.getNumberElements() - 1);
		toProcessFragments.add(rootFragmentWrapper);
		java.util.HashMap<Integer, MatchFragmentList> peakIndexToPeakMatch = new java.util.HashMap<>();
		java.util.HashMap<Integer, MatchPeakList> fragmentIndexToPeakMatch = new java.util.HashMap<>();
		
		/*
		 * iterate over the maximal allowed tree depth
		 */
		for(int k = 1; k <= maximumTreeDepth; k++) {
			java.util.Queue<AbstractTopDownBitArrayFragmentWrapper> newToProcessFragments = new java.util.LinkedList<>();
			/*
			 * use each fragment that is marked as to be processed
			 */
			while(!toProcessFragments.isEmpty()) {
				/*
				 * generate fragments of new tree depth
				 */
				AbstractTopDownBitArrayFragmentWrapper wrappedPrecursorFragment = toProcessFragments.poll();
				
				if(wrappedPrecursorFragment.getWrappedFragment().isDiscardedForFragmentation()) {
					AbstractTopDownBitArrayFragment clonedFragment = (AbstractTopDownBitArrayFragment)wrappedPrecursorFragment.getWrappedFragment().clone(candidatePrecursor);
					clonedFragment.setAsDiscardedForFragmentation();
					if(clonedFragment.getTreeDepth() < maximumTreeDepth) newToProcessFragments.add(new AbstractTopDownBitArrayFragmentWrapper(clonedFragment, wrappedPrecursorFragment.getCurrentPeakIndexPointer()));
					continue;
				}
				/*
				 * generate fragments of next tree depth
				 */
				java.util.ArrayList<AbstractTopDownBitArrayFragment> fragmentsOfCurrentTreeDepth = this.fragmenter.getFragmentsOfNextTreeDepth(wrappedPrecursorFragment.getWrappedFragment());
				
				/*
				 * get peak pointer of current precursor fragment
				 */
				int currentPeakPointer = wrappedPrecursorFragment.getCurrentPeakIndexPointer();
				/*
				 * start loop over all child fragments from precursor fragment
				 * to try assigning them to the current peak
				 */
				for(int l = 0; l < fragmentsOfCurrentTreeDepth.size(); l++) {
					AbstractTopDownBitArrayFragment currentFragment = fragmentsOfCurrentTreeDepth.get(l);

					if(!fragmentsOfCurrentTreeDepth.get(l).isValidFragment()) {
						if(currentFragment.getTreeDepth() < maximumTreeDepth) newToProcessFragments.add(new AbstractTopDownBitArrayFragmentWrapper(fragmentsOfCurrentTreeDepth.get(l), currentPeakPointer));
						continue;
					}
					/*
					 * needs to be set
					 * otherwise you get fragments generated by multiple cleavage in one chain
					 */
					
					if(this.wasAlreadyGeneratedByHashtable(currentFragment)) {
						currentFragment.setAsDiscardedForFragmentation();
						if(currentFragment.getTreeDepth() < maximumTreeDepth) newToProcessFragments.add(new AbstractTopDownBitArrayFragmentWrapper(currentFragment, currentPeakPointer));
						continue;
					}

					byte matched = -1;
					int tempPeakPointer = currentPeakPointer;
					while(matched != 1 && tempPeakPointer >= 0) {
						IMatch[] match = new IMatch[1];
						/*
						 * calculate match
						 */
						matched = currentFragment.matchToPeak(candidatePrecursor, this.peakList.getElement(tempPeakPointer), precursorIonTypeIndex, positiveMode, match);
						/*
						 * check whether match has occurred
						 */
						if(matched == 0) {
							currentFragment.setPrecursorFragments(true);
							Double[][] currentScores = this.scoreCollection.calculateSingleMatch(match[0]);
							/*
							 * insert fragment into peak's fragment list 
							 */
							/*
							 * first generate the new fragment node and set the score values
							 */
							MatchFragmentNode newNode = new MatchFragmentNode(match[0]);
							newNode.setScore(currentScores[0][0]);
							newNode.setFragmentScores(currentScores[0]);
							newNode.setOptimalValues(currentScores[1]);
							/*
							 * find correct location in the fragment list
							 */
							boolean similarFragmentFound = false;
							if(peakIndexToPeakMatch.containsKey(tempPeakPointer)) {
								Double[] values = peakIndexToPeakMatch.get(tempPeakPointer).containsByFingerprint(currentFragment.getAtomsFastBitArray());
								if(values == null) {
									peakIndexToPeakMatch.get(tempPeakPointer).insert(newNode);
								}
								else {
									if(values[0] < currentScores[0][0]) {
										peakIndexToPeakMatch.get(tempPeakPointer).removeElementByID((int)Math.floor(values[1]));
										fragmentIndexToPeakMatch.get((int)Math.floor(values[1])).removeElementByID(tempPeakPointer);
										if(fragmentIndexToPeakMatch.get((int)Math.floor(values[1])).getRootNode() == null) {
											fragmentIndexToPeakMatch.remove((int)Math.floor(values[1]));
										}
										peakIndexToPeakMatch.get(tempPeakPointer).insert(newNode);
									}
									else similarFragmentFound = true;
								}
							}
							else {
								MatchFragmentList newFragmentList = new MatchFragmentList(newNode);
								peakIndexToPeakMatch.put(tempPeakPointer, newFragmentList);
							}
							/*
							 * insert peak into fragment's peak list 
							 */
							if(!similarFragmentFound) {
								if(fragmentIndexToPeakMatch.containsKey(currentFragment.getID())) {
									fragmentIndexToPeakMatch.get(currentFragment.getID()).insert(this.peakList.getElement(tempPeakPointer), currentScores[0][0], tempPeakPointer);
								}
								else {
									MatchPeakList newPeakList = new MatchPeakList(this.peakList.getElement(tempPeakPointer), currentScores[0][0], tempPeakPointer);
									fragmentIndexToPeakMatch.put(currentFragment.getID(), newPeakList);
								}
							}
						}
						/*
						 * if the mass of the current fragment was greater than the peak mass then assign the current peak ID to the peak IDs of the
						 * child fragments as they have smaller masses 
						 */
						if(matched == 1 || tempPeakPointer == 0) {
							/*
							 * mark current fragment for further fragmentation
							 */
							if(currentFragment.getTreeDepth() < maximumTreeDepth) newToProcessFragments.add(new AbstractTopDownBitArrayFragmentWrapper(currentFragment, tempPeakPointer));
						}
						/*
						 * if the current fragment has matched to the current peak then set the current peak index to the next peak as the current fragment can 
						 * also match to the next peak
						 * if the current fragment mass was smaller than that of the current peak then set the current peak index to the next peak (reduce the index) 
						 * as the next peak mass is smaller and could match the current smaller fragment mass 
						 */
						if(matched == 0 || matched == -1) tempPeakPointer--;
					}
				}
			}
			toProcessFragments = newToProcessFragments;
		}
		
		toProcessFragments.clear();
		this.matchList = new MatchList();
		
		/*
		 * collect score of all scores over all matches
		 */
		double[][] singleScores = new double[this.scoreCollection.getNumberScores()][peakIndexToPeakMatch.size()];
		/*
		 * collect the sum of all scores over all matches
		 */
		double[] summedScores = new double[this.scoreCollection.getNumberScores()];

		java.util.Iterator<Integer> it = peakIndexToPeakMatch.keySet().iterator();
		int index = 0;
		/*
		 * go over peak matches
		 */
		while(it.hasNext()) {
			int key = it.next();
			MatchFragmentList matchFragmentList = peakIndexToPeakMatch.get(key);
			MatchFragmentNode bestFragment = matchFragmentList.getRootNode();
			IMatch match = bestFragment.getMatch();
			Double[] scoreValuesSingleMatch = bestFragment.getFragmentScores();
			Double[] optimalValuesSingleMatch = bestFragment.getOptimalValues();
			for(int k = 1; k < scoreValuesSingleMatch.length; k++) {
				if(optimalValuesSingleMatch[k] != null) singleScores[k-1][index] = optimalValuesSingleMatch[k];
				summedScores[k-1] += scoreValuesSingleMatch[k];
			}
			
			bestFragment.getFragment().setIsBestMatchedFragment(true);
			//match.initialiseBestMatchedFragmentByFragmentID(bestFragment.getFragment().getID());
			this.matchList.addElementSorted(match);
			MatchFragmentNode currentFragment = bestFragment;
			while(currentFragment.hasNext()) {
				MatchFragmentNode node = currentFragment.getNext();
				match.addToMatch(node.getMatch());
				currentFragment = currentFragment.getNext();
			}
			index++;
		}
		
		for(int i = 0; i < this.matchList.getNumberElements(); i++)
			this.matchList.getElement(i).shallowNullify();
			
		this.settings.set(VariableNames.MATCH_LIST_NAME, this.matchList);
		
		this.candidate.setMatchList(this.matchList);
		
		if(this.scoreCollection == null) return;
		try {
			for(int i = 0; i < this.scoreCollection.getNumberScores(); i++) {
				if(!this.scoreCollection.getScore(i).calculationFinished()) {
					this.scoreCollection.getScore(i).calculate();
				}
				else 
					this.scoreCollection.getScore(i).setValue(summedScores[i]);
				if(singleScores[i].length != 0 && this.scoreCollection.getScore(i).hasInterimResults() && !this.scoreCollection.getScore(i).isInterimResultsCalculated()) {
					this.scoreCollection.getScore(i).setOptimalValues(singleScores[i]);
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
			logger.warn("candidate score calculation interrupted");
			return;
		}
	}

	protected boolean wasAlreadyGeneratedByHashtable(AbstractTopDownBitArrayFragment currentFragment) {
		String currentHash = currentFragment.getAtomsFastBitArray().toString();
		Integer minimalTreeDepth = this.bitArrayToFragment.get(currentHash);
		if(minimalTreeDepth == null) {
			this.bitArrayToFragment.put(currentHash, (int)currentFragment.getTreeDepth());
			return false;
		}
		//if(minimalTreeDepth.equals(currentFragment.getTreeDepth()))
		if(minimalTreeDepth >= currentFragment.getTreeDepth())
			return false;
		
		return true;
	}
}