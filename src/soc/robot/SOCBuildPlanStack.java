package soc.robot;

import java.io.Serializable;
import java.util.Enumeration;
import java.util.Stack;
import soc.game.SOCResourceSet;

/**
 * Stack implementation of a Build Plan.  Actually uses stack directly to save time - will need to change
 * to a wrapper if we ever change SOCBuildPlan to an abstract class instead of an interface.
 * @author kho30
 *
 */
public class SOCBuildPlanStack extends Stack<SOCPossiblePiece> implements SOCBuildPlan, Serializable {

	@Override
	/**
	 * NB: This does not check for a legal index
         */
	public SOCPossiblePiece getPlannedPiece(int pieceNum) {
            return super.get(elementCount - 1 - pieceNum);
	}

	@Override
	public int getPlanDepth() {
            return elementCount;
	}

	@Override
	/**
	 * NB: This does not check for a safe operation
	 */
	public void advancePlan() {
            pop();
	}

        /**
         * Return the total resources needed for building all the pieces on the stack.
         * @return 
         */
        public SOCResourceSet totalResourcesForBuidPlan() {
            SOCResourceSet rs = new SOCResourceSet();
            
            for (Enumeration e = super.elements(); e.hasMoreElements(); ) {
                SOCPossiblePiece pp = (SOCPossiblePiece) e.nextElement();
                SOCResourceSet addedResources = pp.getResourceCost();
                rs.add(addedResources);
            }
            
            return rs;
        }
}
