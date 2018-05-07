package soc.robot.stac;

/**
 * 
 * Types specific to the MCTS agent only, in addition to the Stac types.
 * Different to its parent, these types are not all mutually exclusive but some are.
 * 
 * @author MD
 *
 */
public class MCTSRobotType extends StacRobotType{
	/**	Use the factored belief model. Should be set to true if the game is not fully-observable.*/
	public static final String MCTS_FACTORED_BELIEF;
    public static final String MCTS_ITERATIONS;
    /** Exploration parameter of MCTS.*/
    public static final String MCTS_Cp;
    public static final String MCTS_THREADS;
    /** Number of visits before the statistics are used to perform non-random selection in the tree phase.*/
    public static final String MCTS_MINVISITS;
    /** Number of nodes the hashmap can hold to reduce the algorithm's memory footprint.*/
    public static final String MCTS_MAX_TREE_SIZE;
    public static final String MCTS_MAX_TREE_DEPTH;
    /** Number of nodes used to create a minibatch for NN seeding*/
    public static final String MCTS_NN_SEEDING_BATCH_SIZE;
    public static final String MCTS_TYPED_ROLLOUTS;
    /** Trading is an exchange action and only the current player decides if it should be executed. It keeps the tree small but it is not a sound approach.*/
    public static final String MCTS_NO_NEGOTIATIONS;
    /** Players can make counter offers in addition to responding with accept/reject.*/
    public static final String MCTS_ALLOW_COUNTEROFFERS_IN_TREE;
    /** Maximum number of resources needed to be discarded before the decision is turned into a uniform chance node.*/
    public static final String MCTS_MAX_N_DISCARDS;
    /** Added limit in addition to number of iterations.*/
    public static final String MCTS_TIME_LIMIT_MS;
    /** Tree policy to be used with seeding or without. This will replace the standard UCT one.*/
    public static final String MCTS_PUCT;
    /** Tree policy to be used with seeding or without. This will replace the standard UCT one.*/
    public static final String MCTS_UCT_RAVE;
    /** Flag to perform seeding with the distribution over action type learned from the human corpus. */
    public static final String MCTS_HUMAN_ACTION_TYPE_PDF_SEEDING;
    /** Flag to perform seeding with the conditioned distribution over action type learned from the human corpus. */
    public static final String MCTS_HUMAN_ACTION_TYPE_PDF_CONDITIONED_SEEDING;
    /** Temperature for smoothing whichever the type distribution is used.*/
    public static final String MCTS_HUMAN_ACTION_TYPE_PDF_SEEDING_TEMPERATURE;
    /** Flag to use NN for seeding.*/
    public static final String MCTS_NN_SEEDING;
    /** Number of threads used for seeding that can be active at the same time.*/
    public static final String MCTS_ACTIVE_SEEDERS;
    /** Percentage of top actions that receive a certain mass. It is a manual smoothing technique in combination with {@link MCTSRobotType#MCTS_NN_SEED_MASS}*/
    public static final String MCTS_NN_SEED_PERCENTAGE;
    /** Amount of mass used for top percentage nodes. It is a manual smoothing technique in combination with {@link MCTSRobotType#MCTS_NN_SEED_PERCENTAGE}*/
    public static final String MCTS_NN_SEED_MASS;
    /** Softmax temperature to smoth the NN output.*/
    public static final String MCTS_NN_SOFTMAX_TEMPERATURE;
    /** Which data type the model was trained on: human or synth?*/
    public static final String MCTS_NN_DATA_TYPE;
    /** Model type: SINGLE, MOE or TL*/
    public static final String MCTS_NN_MODEL_TYPE;
    /** Number of samples (i.e. obs states) if seeding in the partially observable game without masking the input.*/
    public static final String MCTS_NN_N_SAMPLES;
    /** Masks and reduces the input to the NN in the partially observable game when seeding an opponent's decision. It also uses the maskedNN model.*/
    public static final String MCTS_NN_MASK_INPUT;
    /** Laplace smoothing for the type distribution learned with MLE. This affects the seeding in the tree phase.*/
    public static final String MCTS_NN_LAPLACE_ALPHA;
    /** Lambda for NN with MLE distribution over types mixture. {@link MCTSRobotType#MCTS_NN_HUMAN_ACTION_TYPE_PDF_MIXTURE} or {@link MCTSRobotType#MCTS_NN_HUMAN_ACTION_TYPE_PDF_CONDITIONED_MIXTURE} must be set to true, otherwise a uniform distribution is used. */
    public static final String MCTS_NN_MIXTURE_LAMBDA;
    /** Use the unconditioned type distribution for the mixture.*/
    public static final String MCTS_NN_HUMAN_ACTION_TYPE_PDF_MIXTURE;
    /** Use the conditioned type distribution for the mixture.*/
    public static final String MCTS_NN_HUMAN_ACTION_TYPE_PDF_CONDITIONED_MIXTURE;
    /** Temperature for smoothing whichever the type distribution for mixture is used.*/
    public static final String MCTS_NN_HUMAN_ACTION_TYPE_PDF_MIXTURE_TEMPERATURE;
    /** It doesn't share statistics in the outcome nodes. It cannot be used with seeding.*/
    public static final String MCTS_NO_AFTERSTATES;
    /** Number of offers each agent is allowed to make per turn.*/
    public static final String MCTS_OFFERS_LIMIT;
    /** Flag to make the vp dev cards observable in the game model used by MCTS.*/
    public static final String MCTS_OBSERVABLE_VP;
    /** Turn BMCTS to BMCTSOR.*/
    public static final String MCTS_OBSERVABLE_ROLLOUTS;
    public static final String MCTS_NROLLOUTS_PER_ITERATION;
    public static final String MCTS_AVERAGE_ROLLOUTS_RESULTS;
    /** Max number of resources in the victim hand before stealing options are not listed. This is only for the case where pom effects are made observable in the partially observable game.*/
    public static final String MCTS_N_MAX_RSS_STEAL;
    /** Make discard effects observable in rollouts. This is generally kept to true, otherwise updating the belief becomes too expensive in random rollouts.*/
    public static final String MCTS_OBS_DISCARDS_IN_ROLLOUTS;
    /** Use and update the abstract belief representation in the tree instead of the factored belief representation. NOTE: this doesn't work for now.*/
    public static final String MCTS_ABSTRACT_BELIEF;
    /** Force all new belief chance events to be uniform in order to speed the belief game model.*/
    public static final String MCTS_UNIFORM_BELIEF_CHANCE_EVENTS;
    /** Option needed for POMCP to match the effects in the observable game to those of the game in the belief.*/
    public static final String MCTS_LIST_POMS;
    /** Option that makes the true effects of an action observable to every player. It needs {@link MCTSRobotType#MCTS_LIST_POMS} and it works in BMCTS or BMCTSOR.*/
    public static final String MCTS_OBSERVABLE_POM_EFFECTS;
    /** Use POMCP instead of BMCTS.*/
    public static final String MCTS_POMCP;
    /** Use ISMCTS instead of BMCTS.*/
    public static final String MCTS_ISMCTS;
    /** Weights the return with the probability of the state being the true one given the current belief. Works with ISMCTS, POMCP or BMCTSOR.*/
    public static final String MCTS_WEIGHTED_RETURN;
    /** Takes into account the expectation regarding the action legality probablities when backpropagating.*/
    public static final String MCTS_EXPECTED_RETURN;
    public static final String MCTS_EVERY_VISIT;
    /** Option to ignore the action legality probabilities in BMCTS or BMCTSOR.*/
    public static final String MCTS_UNWEIGHTED_SELECTION;
    /** Option to ignore the action legality probabilities in BMCTS during belief rollouts. It also ignores the type distribution if there is any during belief rollouts.*/
    public static final String MCTS_ENFORCE_UNIFORM_TYPE_DIST_IN_BELIEF;
    public static final String MCTS_N_ROOT_SMOOTHING_ACTION_PROBS;
    public static final String MCTS_N_ROOT_SMOOTHING_STATE_PROB;
    /** Biases the rollouts with the unconditioned type distribution learned from human data.*/
    public static final String MCTS_HUMAN_ACTION_TYPE_DIST_ROLLOUTS;
    /** Biases the rollouts with the conditioned type distribution learned from human data.*/
    public static final String MCTS_HUMAN_ACTION_TYPE_CONDITIONED_DIST_ROLLOUTS;
    /** Option to smooth the type dist used in rollouts.*/
    public static final String MCTS_HUMAN_ACTION_TYPE_DIST_ROLLOUTS_TEMPERATURE;
    
    static{
    	MCTS_FACTORED_BELIEF = p("MCTS_FACTORED_BELIEF");
    	MCTS_ITERATIONS = p("MCTS_ITERATIONS", Integer.class);
    	MCTS_Cp = p("MCTS_Cp", Double.class);
    	MCTS_THREADS = p("MCTS_THREADS", Integer.class);
    	MCTS_MINVISITS = p("MCTS_MINVISITS", Integer.class);
    	MCTS_MAX_TREE_SIZE = p("MCTS_MAX_TREE_SIZE", Integer.class);
    	MCTS_MAX_TREE_DEPTH = p("MCTS_MAX_TREE_DEPTH", Integer.class);
    	MCTS_NN_SEEDING_BATCH_SIZE = p("MCTS_NN_SEEDING_BATCH_SIZE", Integer.class);
    	MCTS_TYPED_ROLLOUTS = p("MCTS_TYPED_ROLLOUTS");
    	MCTS_NO_NEGOTIATIONS = p("MCTS_NO_NEGOTIATIONS");
    	MCTS_ALLOW_COUNTEROFFERS_IN_TREE = p("MCTS_ALLOW_COUNTEROFFERS_IN_TREE");
    	MCTS_MAX_N_DISCARDS = p("MCTS_MAX_N_DISCARDS", Integer.class);
    	MCTS_TIME_LIMIT_MS = p("MCTS_TIME_LIMIT_MS", Integer.class);
    	MCTS_PUCT = p("MCTS_PUCT");
    	MCTS_UCT_RAVE = p("MCTS_UCT_RAVE", Integer.class);
    	MCTS_HUMAN_ACTION_TYPE_PDF_SEEDING = p("MCTS_HUMAN_ACTION_TYPE_PDF_SEEDING");
    	MCTS_HUMAN_ACTION_TYPE_PDF_CONDITIONED_SEEDING = p("MCTS_HUMAN_ACTION_TYPE_PDF_CONDITIONED_SEEDING");
    	MCTS_HUMAN_ACTION_TYPE_PDF_SEEDING_TEMPERATURE = p("MCTS_HUMAN_ACTION_TYPE_PDF_SEEDING_TEMPERATURE",Double.class);
    	MCTS_NN_SEEDING = p("MCTS_NN_SEEDING");
    	MCTS_ACTIVE_SEEDERS = p("MCTS_ACTIVE_SEEDERS", Integer.class);
    	MCTS_NN_SEED_PERCENTAGE = p("MCTS_NN_SEED_PERCENTAGE", Double.class);
    	MCTS_NN_SEED_MASS = p("MCTS_NN_SEED_MASS", Double.class);
    	MCTS_NN_SOFTMAX_TEMPERATURE = p("MCTS_NN_SOFTMAX_TEMPERATURE", Double.class);
    	MCTS_NN_DATA_TYPE = p("MCTS_NN_DATA_TYPE", String.class);
    	MCTS_NN_MODEL_TYPE = p("MCTS_NN_MODEL_TYPE", String.class);
    	MCTS_NN_N_SAMPLES = p("MCTS_NN_N_SAMPLES", Integer.class);
    	MCTS_NN_MASK_INPUT = p("MCTS_NN_MASK_INPUT");
    	MCTS_NN_LAPLACE_ALPHA = p("MCTS_NN_LAPLACE_ALPHA", Double.class);
    	MCTS_NN_MIXTURE_LAMBDA = p("MCTS_NN_MIXTURE_LAMBDA",Double.class);
    	MCTS_NN_HUMAN_ACTION_TYPE_PDF_MIXTURE = p("MCTS_NN_HUMAN_ACTION_TYPE_PDF_MIXTURE");
    	MCTS_NN_HUMAN_ACTION_TYPE_PDF_CONDITIONED_MIXTURE = p("MCTS_NN_HUMAN_ACTION_TYPE_PDF_CONDITIONED_MIXTURE");
    	MCTS_NN_HUMAN_ACTION_TYPE_PDF_MIXTURE_TEMPERATURE = p("MCTS_NN_HUMAN_ACTION_TYPE_PDF_MIXTURE_TEMPERATURE", Double.class);
    	MCTS_NO_AFTERSTATES = p("MCTS_NO_AFTERSTATES");
    	MCTS_OFFERS_LIMIT = p("MCTS_OFFERS_LIMIT", Integer.class);
    	MCTS_OBSERVABLE_VP = p("MCTS_OBSERVABLE_VP");
    	MCTS_OBSERVABLE_ROLLOUTS = p("MCTS_OBSERVABLE_ROLLOUTS");
    	MCTS_NROLLOUTS_PER_ITERATION = p("MCTS_NROLLOUTS_PER_ITERATION", Integer.class);
    	MCTS_AVERAGE_ROLLOUTS_RESULTS = p("MCTS_AVERAGE_ROLLOUTS_RESULTS");
    	MCTS_N_MAX_RSS_STEAL = p("MCTS_N_MAX_RSS_STEAL", Integer.class);
    	MCTS_OBS_DISCARDS_IN_ROLLOUTS = p("MCTS_OBS_DISCARDS_IN_ROLLOUTS");
    	MCTS_ABSTRACT_BELIEF = p("MCTS_ABSTRACT_BELIEF");
    	MCTS_UNIFORM_BELIEF_CHANCE_EVENTS = p("MCTS_UNIFORM_BELIEF_CHANCE_EVENTS");
    	MCTS_LIST_POMS = p("MCTS_LIST_POMS");
    	MCTS_OBSERVABLE_POM_EFFECTS = p("MCTS_OBSERVABLE_POM_EFFECTS");
    	MCTS_POMCP = p("MCTS_POMCP");
    	MCTS_ISMCTS = p("MCTS_ISMCTS");
    	MCTS_WEIGHTED_RETURN = p("MCTS_WEIGHTED_RETURN");
    	MCTS_EXPECTED_RETURN = p("MCTS_EXPECTED_RETURN");
    	MCTS_EVERY_VISIT = p("MCTS_EVERY_VISIT");
    	MCTS_UNWEIGHTED_SELECTION = p("MCTS_UNWEIGHTED_SELECTION");
    	MCTS_ENFORCE_UNIFORM_TYPE_DIST_IN_BELIEF = p("MCTS_ENFORCE_UNIFORM_TYPE_DIST_IN_BELIEF");
    	MCTS_N_ROOT_SMOOTHING_ACTION_PROBS = p("MCTS_N_ROOT_SMOOTHING_ACTION_PROBS",Integer.class);
    	MCTS_N_ROOT_SMOOTHING_STATE_PROB = p("MCTS_N_ROOT_SMOOTHING_STATE_PROB",Integer.class);
    	MCTS_HUMAN_ACTION_TYPE_DIST_ROLLOUTS = p("MCTS_HUMAN_ACTION_TYPE_DIST_ROLLOUTS");
    	MCTS_HUMAN_ACTION_TYPE_CONDITIONED_DIST_ROLLOUTS = p("MCTS_HUMAN_ACTION_TYPE_CONDITIONED_DIST_ROLLOUTS");
    	MCTS_HUMAN_ACTION_TYPE_DIST_ROLLOUTS_TEMPERATURE = p("MCTS_HUMAN_ACTION_TYPE_DIST_ROLLOUTS_TEMPERATURE",Double.class);
    }
}
