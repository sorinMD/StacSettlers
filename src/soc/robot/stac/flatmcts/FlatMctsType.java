package soc.robot.stac.flatmcts;

import soc.robot.stac.StacRobotType;
/**
 * Similar to StacRobotType this class intended use is for defining the MCTS algorithm.
 * @author MD
 */
public class FlatMctsType extends StacRobotType{
	
	/**
	 * An integer value between 0-100. If negative or 0, it unsets this functionality in random brain. If over 100, it will work as if it was 100.
	 */
	public static final String SIMULATION_RANDOMNESS_PERCENTAGE;
	/**
	 * What type of action selection strategy are we using for selecting play after execution of MCTS. Options: UCT, MAX_VALUE, MAX_AVERAGE, MOST_EXPLORED
	 */
	public static final String ACTION_SELECTION_POLICY;
	/**
	 * How are we rewarding the algorithm in the terminal state of the simulation phase. For options see switch in {@link FlatMctsRewards#getReward(String, int, int)}
	 */
	public static final String REWARD_FUNCTION;
	/**
	 * On which game action are we performing this search. Options: INITIAL_SETTLEMENT, SECOND_SETTLEMENT, MOVE_ROBBER.
	 */
	public static final String GAME_ACTION;
	/**
	 * Seeding options: JSETTLERS, CORPUS, NONE, CORPUS_AND_JSETTLERS
	 */
	public static final String SEED_METHOD;
	/**
	 * If we are seeding from corpus, than do we want to cluster and the type of clustering. Options: 1 (NO_CLUSTERING), 2(STATE_RELEVANCE_CLUSTERING), 3(STATE_AND_ACTION_RELEVANCE_CLUSTERING)
	 */
	public static final String CLUSTERING_TYPE;
	
	static {
		SIMULATION_RANDOMNESS_PERCENTAGE = p("SIMULATION_RANDOMNESS_PERCENTAGE", Integer.class);
		ACTION_SELECTION_POLICY = p("ACTION_SELECTION_POLICY", String.class);
		REWARD_FUNCTION = p("REWARD_FUNCTION", String.class);
		GAME_ACTION = p("GAME_ACTION", String.class);
		SEED_METHOD = p("SEED_METHOD", String.class);
		CLUSTERING_TYPE = p("CLUSTERING_TYPE", Integer.class);
	}
	
}
