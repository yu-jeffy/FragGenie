package uk.ac.liverpool.metfraglib.fragmenterassignerscorer;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import de.ipbhalle.metfraglib.interfaces.ICandidate;
import de.ipbhalle.metfraglib.interfaces.IFragment;
import de.ipbhalle.metfraglib.interfaces.IMolecularStructure;
import de.ipbhalle.metfraglib.parameter.VariableNames;
import de.ipbhalle.metfraglib.fragment.AbstractFragment;
import uk.ac.liverpool.metfraglib.fragment.AbstractTopDownBitArrayFragment;
import uk.ac.liverpool.metfraglib.fragment.AbstractTopDownBitArrayFragmentWrapper;
import uk.ac.liverpool.metfraglib.fragmenter.TopDownNeutralLossFragmenter;

public class TopDownFragmenterAssignerScorer {

	/**
	 * 
	 */
	private final Logger logger = Logger.getLogger(TopDownFragmenterAssignerScorer.class);

	/**
	 * 
	 */
	private final Map<String, Integer> bitArrayToFragment = new HashMap<>();

	/**
	 * 
	 */
	private final boolean positiveMode = true;

	/**
	 * 
	 */
	private final int maximumTreeDepth = 2;

	/**
	 * 
	 */
	private final ICandidate candidate;

	/**
	 * 
	 */
	private final TopDownNeutralLossFragmenter fragmenter;

	/**
	 * 
	 * @param settings
	 * @param candidate
	 */
	public TopDownFragmenterAssignerScorer(final ICandidate candidate) throws Exception {

		this.candidate = candidate;
		this.candidate.initialisePrecursorCandidate();
		this.candidate.setProperty(VariableNames.MAXIMUM_TREE_DEPTH_NAME, Integer.valueOf(this.maximumTreeDepth));

		this.fragmenter = new TopDownNeutralLossFragmenter(this.candidate, this.maximumTreeDepth);

		this.logger.setLevel(Level.ALL);
	}

	/**
	 * 
	 * @throws Exception
	 */
	public void calculate() throws Exception {
		final IMolecularStructure candidatePrecursor = this.candidate.getPrecursorMolecule();
		// generate root fragment to start fragmentation
		final IFragment root = candidatePrecursor.toFragment();

		Queue<AbstractTopDownBitArrayFragmentWrapper> toProcessFragments = new LinkedList<>();

		AbstractTopDownBitArrayFragmentWrapper rootFragmentWrapper = new AbstractTopDownBitArrayFragmentWrapper(root, Integer.valueOf(0));
		toProcessFragments.add(rootFragmentWrapper);

		/*
		 * iterate over the maximal allowed tree depth
		 */
		for (int k = 1; k <= this.maximumTreeDepth; k++) {
			java.util.Queue<AbstractTopDownBitArrayFragmentWrapper> newToProcessFragments = new java.util.LinkedList<>();
			/*
			 * use each fragment that is marked as to be processed
			 */
			while (!toProcessFragments.isEmpty()) {
				/*
				 * generate fragments of new tree depth
				 */
				AbstractTopDownBitArrayFragmentWrapper wrappedPrecursorFragment = toProcessFragments.poll();

				if (((AbstractFragment) wrappedPrecursorFragment.getWrappedFragment()).isDiscardedForFragmentation()) {
					AbstractTopDownBitArrayFragment clonedFragment = (AbstractTopDownBitArrayFragment) wrappedPrecursorFragment
							.getWrappedFragment().clone(candidatePrecursor);
					clonedFragment.setAsDiscardedForFragmentation();
					if (clonedFragment.getTreeDepth() < this.maximumTreeDepth)
						newToProcessFragments.add(new AbstractTopDownBitArrayFragmentWrapper(clonedFragment,
								wrappedPrecursorFragment.getCurrentPeakIndexPointer()));
					continue;
				}
				/*
				 * generate fragments of next tree depth
				 */
				java.util.ArrayList<AbstractTopDownBitArrayFragment> fragmentsOfCurrentTreeDepth = this.fragmenter
						.getFragmentsOfNextTreeDepth(
								(AbstractTopDownBitArrayFragment)wrappedPrecursorFragment.getWrappedFragment());

				/*
				 * get peak pointer of current precursor fragment
				 */
				int currentPeakPointer = wrappedPrecursorFragment.getCurrentPeakIndexPointer();
				/*
				 * start loop over all child fragments from precursor fragment to try assigning
				 * them to the current peak
				 */
				for (int l = 0; l < fragmentsOfCurrentTreeDepth.size(); l++) {
					AbstractTopDownBitArrayFragment currentFragment = fragmentsOfCurrentTreeDepth.get(l);

					if (!fragmentsOfCurrentTreeDepth.get(l).isValidFragment()) {
						if (currentFragment.getTreeDepth() < this.maximumTreeDepth)
							newToProcessFragments.add(new AbstractTopDownBitArrayFragmentWrapper(
									fragmentsOfCurrentTreeDepth.get(l), currentPeakPointer));
						continue;
					}
					/*
					 * needs to be set otherwise you get fragments generated by multiple cleavage in
					 * one chain
					 */

					if (this.wasAlreadyGeneratedByHashtable(currentFragment)) {
						currentFragment.setAsDiscardedForFragmentation();
						if (currentFragment.getTreeDepth() < this.maximumTreeDepth)
							newToProcessFragments.add(
									new AbstractTopDownBitArrayFragmentWrapper(currentFragment, currentPeakPointer));
						continue;
					}

					byte matched = -1;
					int tempPeakPointer = currentPeakPointer;
					while (matched != 1 && tempPeakPointer >= 0) {

						/*
						 * calculate match
						 */
						matched = currentFragment.matchToPeak(candidatePrecursor, 2, this.positiveMode);

						/*
						 * if the mass of the current fragment was greater than the peak mass then
						 * assign the current peak ID to the peak IDs of the child fragments as they
						 * have smaller masses
						 */
						if (matched == 1 || tempPeakPointer == 0) {
							/*
							 * mark current fragment for further fragmentation
							 */
							if (currentFragment.getTreeDepth() < this.maximumTreeDepth)
								newToProcessFragments.add(
										new AbstractTopDownBitArrayFragmentWrapper(currentFragment, tempPeakPointer));
						}
						/*
						 * if the current fragment has matched to the current peak then set the current
						 * peak index to the next peak as the current fragment can also match to the
						 * next peak if the current fragment mass was smaller than that of the current
						 * peak then set the current peak index to the next peak (reduce the index) as
						 * the next peak mass is smaller and could match the current smaller fragment
						 * mass
						 */
						if (matched == 0 || matched == -1)
							tempPeakPointer--;
					}
				}
			}
			toProcessFragments = newToProcessFragments;
		}
	}

	/**
	 * 
	 * @param currentFragment
	 * @return boolean
	 */
	private boolean wasAlreadyGeneratedByHashtable(final AbstractTopDownBitArrayFragment currentFragment) {
		String currentHash = currentFragment.getAtomsFastBitArray().toString();
		Integer minimalTreeDepth = this.bitArrayToFragment.get(currentHash);

		if (minimalTreeDepth == null) {
			this.bitArrayToFragment.put(currentHash, (int) currentFragment.getTreeDepth());
			return false;
		}

		if (minimalTreeDepth >= currentFragment.getTreeDepth()) {
			return false;
		}

		return true;
	}
}