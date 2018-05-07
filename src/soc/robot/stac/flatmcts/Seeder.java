package soc.robot.stac.flatmcts;

import java.util.List;

import soc.robot.stac.SettlementNode;

/**
 * Interface for the different seeder types: using JSettlers, using Corpus or both.
 * @author MD
 */
public interface Seeder {
	
	/**
	 * the method that performs the seeding part of the algorithm depending on the actionType.
	 * @param root the parent node for the level we want to seed into
	 * @param actionType the action type so we know what to look for inside the corpus/JSettlers logic
	 */
	public void seed(TreeNode root, String actionType);
	/**
	 * This is required for the expansion part of the algorithm as there is no metohd that returns a list of all the legal actions 
	 * @return an ordered/unordered(depending if it is Corpus or JSettlers) list of all the legal nodes to place a settlement 
	 */
	public List<SettlementNode> getLegalSettlements();
}
