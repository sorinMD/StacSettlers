package soc.robot.stac.flatmcts;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import soc.message.SOCMessage;
import soc.message.SOCMessageForGame;


/**
 * This is the class that implements the nodes inside the tree for MCTS. It acts as a simple container storing
 * the list of its children, the value and number of visits and the action that resulted in this state. 
 * @author MD
 */
public class TreeNode {
	/**
	 * a list of children nodes; states reachable from current one through a set of actions
	 */
    public TreeNode[] children;
    /**
     * number of time we visited this node (or pulled this lever to use bandit terms).
     */
    public double nVisits = 0.0;
    /**
     * The total reward as a sum of the rewards for each visit.
     */
    public double totValue = 0.0;
    /**
     * The action which got us in this state as a SOCMessage,
     * so it can be passed to the brain as the next action it needs to take.
     * if null then this is the root node.
     */
    public SOCMessage message = null; 

    public boolean isLeaf() {
        return children == null;
    }

    /**
     * Updates the total value and increments the number of visits
     * @param value the value propagated from the result node/state.
     */
    public void updateStats(double value) {
        nVisits++;
        totValue += value;
    }
    
    public void setValue(double val){
    	totValue = val;
    }
    
    public void setVisits(double bestScore){
    	nVisits = bestScore;
    }
    
    public void addValue(double val){
    	totValue = totValue + val;
    }
    
    public void addVisits(double vis){
    	nVisits = nVisits + vis;
    }
    
    /**
     * @return number of possible actions from this node
     */
    public int arity() {
        return children == null ? 0 : children.length;
    }
}