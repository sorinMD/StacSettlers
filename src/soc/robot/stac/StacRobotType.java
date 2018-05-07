/*
 * Constants to distinguish robot types
 */
package soc.robot.stac;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Specify different types of robots. 
 * 
 * These values are not meant to be mutually exclusive,
 * e.g. the ATHEIST robot can have a WEAK_MEMORY or not.
 * 
 * @author
 * Markus Guhe
 */
public class StacRobotType {
        //robot types
    
        public static final Map<String, Class> TYPES = new HashMap<String, Class>();
        
        //Stac types
        /** Set the player type according to the given integer */
        public static final String PLAYER_ICON; 
        
        /** The original JSettlers robot, only able to handle complete offers. */
        public static final String ORIGINAL_ROBOT;       
        
        /** Robot computes complete offers and partialises them 50% of the time. */
        public static final String PARTIALISING_COMPLETE_OFFERS_50_PERCENT;        
        /** Robot computes complete offers and partialises them 100% of the time. */
        public static final String PARTIALISING_COMPLETE_OFFERS_100_PERCENT;        
        /** Robot generates trade offers immediately from build plan. */
        public static final String SIMPLY_ASK;
        /** Robot gives a randomly completed offer in response to a partial trade offer. NB: This assumes that the original offer leaves the givables unspecified, e.g. 'I need wood'. */
        public static final String RANDOM_COMPLETION_OF_PARTIAL_OFFERS;
        /** For partial offers, choose the completed offer that is best for me. */
        public static final String PARTIAL_COMPLETION_CHOOSE_MY_BEST;
        /** For partial offers, choose the completed offer that is best for me. */
        public static final String PARTIAL_COMPLETION_CHOOSE_OPPONENT_WORST;
        public static final String PARTIAL_COMPLETION_PREFER_MY_BEST;
        public static final String PARTIAL_COMPLETION_PREFER_OPPONENT_WORST;
        
        /** Robot "sometimes" has "retrieval problems" in its declarative memory for information used to compute trade offers. */
        public static final String WEAK_MEMORY;
        /** 
         * Robot can't remember past trades. 
         * CAUTION: This just means the robot will make the same trade offer over and over again at the moment.
         */
        public static final String NO_MEMORY_OF_TRADES ;
        /** Robot can't remember who's selling what. */
        public static final String NO_MEMORY_OF_IS_SELLING;        
        /** Robot always "thinks" that a player is selling a resource. */
        public static final String ALWAYS_ASSUMING_IS_SELLING;
        
        /** Robot requests/announces resource holdings **/
        public static final String SHARE_RESOURCES;
        public static final String LISTEN_RESOURCES;        
        /** Robot has NO access to opponent resource counts - everything is treated as UNKNOWN.
         *   Note that this applies only while observing resource distribution after rolls.  If
         *   the agent is also listening to resource announcements, that will override the ignorance.
         */
        public static final String IGNORANT;
        /** Robot always assumes that opponents have 2 of the resources (which is what is maximally tested when considering offers). */
        public static final String IGNORANT_OPTIMIST;
        /** Robot always assumes that opponents have 0 of the resources. */
        public static final String IGNORANT_PESSIMIST;
        /** When considering trade offers, assume that unknown opponent resources are NOT favourable for us. */
        public static final String PESSIMISTIC_ABOUT_OPPONENT_UNKONWNS;
        /** 
         * Robots forgetting their beliefs about opponents' resources after the specified time.
         * Time is simulated as the number of messages the robot receives.
         * - Pessimist assumes the opponent does not have the resource, 
         * - Optimist assumes the opponent has 2 of the resource,
         * - Random 'remembers' a random value of 0 or 1.
         */ 
        public static final String FORGETTING_RESOURCES_PESSIMIST;
        public static final String FORGETTING_RESOURCES_OPTIMIST;
        public static final String FORGETTING_RESOURCES_RANDOM;
        
        /** Use a simple update for opponent resources from observed trade offers. Unknown resources are changed to the observed types if appropriate. */
        public static final String SIMPLE_UPDATE_OF_OPPONENT_RESOURCES_FROM_OFFER;
        
        /** Flags to influence has-resources calculation **/
        public static final String HRP_GUESS;

        /** Robot freely shares build plan */
        public static final String SHARE_BUILD_PLAN;
        /** Inform other players each time the build plan changes  */
        public static final String SHARE_BUILD_PLAN_CHANGE;
        /** Robot announces a false build plan */
        public static final String LYING_ABOUT_BUILD_PLAN;
        /** Robot listens and believes shared build plans */
        public static final String LISTEN_BUILD_PLAN;
        /** Robot doesn't consider whether other agents can/will accept offers it proposes **/
        public static final String BUILD_PLAN_INDIFFERENT;
        /** Add some optimistic offering with the probability specified by the parameter; range: 0-100 **/
        public static final String OPTIMISTIC_OFFERER;
        
        /** Agent discloses unneeded resources when asked */
        public static final String SHARE_EXTRA_RES;
        /** Agent request, listens and believes extra resources */
        public static final String LISTEN_EXTRA_RES;
        
        /** Agent discloses desired resources when asked */
        public static final String SHARE_WANTED_RES;
        /** Agent request, listens and believes desired resources */
        public static final String LISTEN_WANTED_RES;

        /** Robot announces a false resources */
        public static final String LYING_ABOUT_RESOURCES;
        public static final String LYING_ABOUT_RESOURCES_HAVE_1;
        public static final String LYING_ABOUT_RESOURCES_HAVE_2;

        /** Agent will solicit trade offers after making any announcements at the start of his turn 
         * TODO: Make this probabilistic or conditional.
         * */
        public static final String SOLICIT_TRADES;
        public static final String MAKE_SOLICITED_OFFER;
        
        /** Robot neither makes offers nor accepts trades **/
        public static final String NO_TRADES;        
        /** Robot randomly determines if a trade offer is issued.  Used to control trade volume **/
        public static final String PROBABILISTIC_OFFER_DENIER;
        /** Continue to trade with players if they have 8 or more VPs. */
        public static final String CONTINUE_TRADING_WITH_AGENTS_CLOSE_TO_WINNING;
        
        /** Use legacy handling of PLAY1 message **/
        public static final String LEGACY_PLAY1;
        
        /** Use the old fast building strategy. **/
        public static final String OLD_FAST_GAME_STRATEGY;
        
        /** Don't filter 'worse' offers (where give is subset or get superset of offer in past-trade-offers). */
        public static final String DONT_FILTER_WORSE_OFFERS;

        /** Don't filter offers where we give away too many resources of the types we need for the BBP, so that we won't be able to execute our BBP. */
        public static final String FILTER_GIVING_AWAY_RESOURCES_NEEDED_FOR_BBP;
                
        /** Use the old smart building strategy. **/
        public static final String SMART_GAME_STRATEGY;
        
        /** Favour a certain piece during the setup phase **/
        public static final String FAVOUR_ROADS_INITIALLY;
        public static final String FAVOUR_SETTLEMENTS_INITIALLY;
        public static final String FAVOUR_CITIES_INITIALLY;
        public static final String FAVOUR_DEV_CARDS_INITIALLY;
        
        /** Favour choosing a BP after the setup phase; the should use the nBest planning algorithm **/
        public static final String FAVOUR_ROADS;
        public static final String FAVOUR_SETTLEMENTS;
        public static final String FAVOUR_CITIES;
        public static final String FAVOUR_DEV_CARDS;
        public static final String FAVOUR_LR;
        public static final String FAVOUR_LA;
        
        /** Minimum VPs the robot has to have before considering to go for LR **/
        public static final String MIN_VP_TO_TRY_LR;
        /** Minimum VPs the robot has to have before considering to go for LA **/
        public static final String MIN_VP_TO_TRY_LA;
        
        /** Choose the best - n build plan instead of the BBP (best - 0) **/
        public static final String CHOOSE_BEST_MINUS_N_BUILD_PLAN; 
        
        /** Consider n best build plans */
        public static final String TRY_N_BEST_BUILD_PLANS;
        
        /** Rank the build plans by ETB - ES - EP. */
        public static final String RANK_BPS_TRADE_OFF_ES_FACTOR;
        public static final String RANK_BPS_TRADE_OFF_EP_FACTOR;

        /** Use Q-learning to adapt to opponents. */
        public static final String ADAPT_TO_OPPONENT_TYPE;
        
        /** Setting the ES values for cards, LA, LR */
        public static final String ES_CARDS;
        public static final String ES_LA;        
        public static final String ES_LR;
        
        /** Instead of robbing the player closest to winning, rob based on what resource we would like to get **/
        public static final String ROB_FOR_NEED;

        /** Flags to influence BBPPP calculation **/
        public static final String BBPPP_GUESS;
        /** Assume our own strategy for guessing other agents' best build plan */
        public static final String ASSUME_OTHER_AGENTS_USE_OUR_STRATEGY;

        /** Assume other agents use the 0-best strategy for guessing other agents' best build plan */
        public static final String ASSUME_OTHER_AGENTS_USE_0_BEST_STRATEGY;

        /** Use the ACT-R declarative memory **/
        public static final String USE_ACT_R_DECLARATIVE_MEMORY;
        /** Write the ACT-R output to logs/actrlogs/[gamename].actrlog (Redirects what ACT-R writes to stdout plus some ACT-R logging info) **/
        public static final String LOG_ACT_R_OUTPUT;
        /** Set the ACT-R :rt parameter (Retrieval Threshold) */
        public static final String ACT_R_PARAMETER_RT;
        
        /** Experimental generation of some basic dialogue structures */
        public static final String DIALOGUE_MANAGER_USE_SDRT;
        
        /** Gullible: believe all announcement (like resources/BPs); requires trading via Dialogue Manager */
        public static final String NP_ANNOUNCEMENT_GULLIBLE;
        
        /** Persuasion move: Force accept; requires trading via Dialogue Manager */
        public static final String NP_FORCE_ACCEPT_PROPOSER;
        /** Number of VPs the leader must have before making force-accept moves; serves as a general measure for how far the game has progressed */
        public static final String NP_FORCE_ACCEPT_WHEN_LEADER_HAS_VP;
        /** Number of force accept moves the proposer can make */
        public static final String NP_NUM_FORCE_ACCEPT_MOVES;
        /** Only make force-accept moves if we know that the trade will happen */
        public static final String NP_FORCE_ACCEPT_ONLY_IF_POSSIBLE;
        /** Only use a force-accept move if it allows us to build immediately  */
        public static final String NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_BUILD;
        /** "make this trade, and you can build immediately too!" */
        public static final String NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BUILD;
        /** "make this trade, and you can build immediately too something that you couldn't build before!" */
        public static final String NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BUILD_NOT_POSSIBLE_BEFORE;
        /** "make this trade, and you can make a trade with the bank/port!" */
        public static final String NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BANK_TRADE;
        /** "make this trade, and you can make a trade with the bank/port that you couldn't make before!" */
        public static final String NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BANK_TRADE_NOT_POSSIBLE_BEFORE;
        /** "make this trade, and you can make this bank trade and then build something that you couldn't build before!" */
        public static final String NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BANK_TRADE_FOR_BUILD_NOT_POSSIBLE_BEFORE;
        /** disjunction of Opponent Build and Opponent Bank Trade */
        public static final String NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BUILD_OR_BANK_TRADE_NOT_POSSIBLE_BEFORE;
        /** "make this trade, and you can make this bank trade and then build IMMEDIATELYsomething that you couldn't build before!" */
        public static final String NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BANK_TRADE_FOR_IMMEDIATE_BUILD_NOT_POSSIBLE_BEFORE;
        /** A (static) combination of all the 'ONLY' moves above */
        public static final String NP_FORCE_ACCEPT_MISC_MOVES_ALL;
        /** A (static) combination of all the 'ONLY' moves above restricted to the 'NOT POSSIBLE BEFOE' types */
        public static final String NP_FORCE_ACCEPT_MISC_MOVES_NPB;
        /** Only make a force-accept move if the normal offer has been unsuccessful */
        public static final String NP_FORCE_ACCEPT_ONLY_AFTER_REJECT;
        /** Comply with all force-accept trade offers */
        public static final String NP_FORCE_ACCEPT_GULLIBLE;
        /** Comply with force-accept trade offers that allow me to build something I couldn't build without the trade. */
        public static final String NP_FORCE_ACCEPT_COMPLY_ONLY_FOR_IMMEDIATE_BUILD_NOT_POSSIBLE_BEFORE;
        /** Comply with force-accept trade offers that allow me to make a bank/port trade that I couldn't make without the trade. */
        public static final String NP_FORCE_ACCEPT_COMPLY_ONLY_FOR_IMMEDIATE_BANK_TRADE_NOT_POSSIBLE_BEFORE;
        /** Comply with force-accept trade offers that allow me to make an immediate build or a bank/port trade that I couldn't make without the trade. */
        public static final String NP_FORCE_ACCEPT_COMPLY_ONLY_FOR_IMMEDIATE_BUILD_OR_BANK_TRADE_NOT_POSSIBLE_BEFORE;
        
        /***
         * Negotiator policies
         */
        //Use the old makeOffer method
        public static final String USE_OLD_NEGOTIATOR;
        //use the old makeCounterOffer method
        public static final String USE_OLD_NEGOTIATOR_COUNTEROFFER;
        
        /** Aggregate multiple trade offers into a partial offer if there are multiple offers with identical give/get sets */
        public static final String NP_AGGREGATE_TRADE_OFFERS;

        /** Don't make partial offers when aggregating trade offers */
        public static final String NP_DONT_MAKE_PARTIAL_OFFERS;
        
        /** Don't make disjunctive offers when aggregating trade offers */
        public static final String NP_DONT_MAKE_DISJUNCTIVE_OFFERS;

        /** Don't aggregate two trades if their ETBs differ by more than this value */
        public static final String NP_MAX_DELTA_ETB_FOR_OFFER_AGGREGATION;

        /** the maximum number of offers the robot can make without any build action */
        public static final String NP_MAX_NUMBER_OF_OFFERS_WITHOUT_BUILDING;
        
        public static final String NP_ALWAYS_ACCEPT;
        public static final String NP_ALWAYS_REJECT;
        public static final String NP_GREEDY_ACCEPT;
        public static final String NP_ALWAYS_EXPECT_ACCEPT; //expect that all offers are accepted and accept all offers (considerOffer always return ACCEPT)
        public static final String NP_GIVE_TRADE_TO_ACCEPTER_WITH_FEWEST_VP;
        public static final String NP_GIVE_TRADE_TO_ACCEPTER_WITH_FEWEST_RES;
        public static final String NP_GIVE_TRADE_TO_ACCEPTER_WITH_MOST_RES;
        public static final String NP_ACCEPT_BETTER_COUNTEROFFER;
        
        /** This first flag has to be set to activate embargoes! */
        public static final String NP_PROPOSE_EMBARGO_WHEN_LEADER_HAS_VP; //was: NP_INIT_EMBARGO_WHEN_FIRST_PLAYER_HAS_VP
        public static final String NP_PROPOSE_EMBARGO_AFTER_OFFER_FOR_IMMEDIATE_BUILD; //was: NP_INIT_EMBARGO_WHEN_FIRST_PLAYER_HAS_VP
        public static final String NP_COMPLY_WITH_EMBARGOES;
        public static final String NP_LIFT_EMBARGOES_AFTER_TURNS;
        public static final String NP_NUM_EMBARGOES_AGENT_CAN_PROPOSE;
        /** TODO: This is not implemented yet! */
        public static final String NP_NUM_EMBARGOES_AGENT_CAN_ENGAGE_IN;

        public static final String NP_PROPOSE_BLOCK_WHEN_LEADER_HAS_VP;
        public static final String NP_BLOCK_TRADES_NOT_MADE_TO_ME;
        public static final String NP_BLOCK_TRADES_I_WOULD_REJECT;
        public static final String NP_BLOCK_RESOURCES_ASKED_FOR;
        public static final String NP_BLOCK_ONLY_FOR_IMMEDIATE_BUILD;
        public static final String NP_BLOCK_TRADES_GULLIBLE;
        public static final String NP_NUM_BLOCKS_AGENT_CAN_PROPOSE;
        public static final String NP_LIFT_BLOCK_AFTER_TURNS;
        
        public static final String NP_MAX_ETB_FOR_TRADE_OFFER;
        
        public static final String DONT_CONSIDER_OFFERING_2_RESOURCES;
        public static final String DONT_CONSIDER_ASKING_FOR_2_RESOURCES;        
        // How much does our BSE have to improve (in turns) to consider a trade?
        public static final String NP_IMPROVEMENT_OVER_BATNA_THRESHOLD;
        public static final String BSE_IMPROVEMENT_THRESHOLD;
        public static final String BSE_IMPROVEMENT_THRESHOLD_RATIO;
        public static final String NP_IMPROVEMENT_OVER_BATNA_THRESHOLD_RATIO;
        
        public static final String JEDI_MIND_TRICK;
        
        /**
         * Build speed estimate calculation
         */
        public static final String BSE_ACCURATE;
        public static final String BSE_FRACTIONAL;
        /** Use beliefs about other players resources and build plans to calculate ETBs */
        public static final String BSE_USING_BELIEFS;
        
        
        /**
         * Robot Types used to define a persuader robot 
         */
        //Persuaders Possible Agent Types which define which persuasion types can be used by the robot
        public static final String PERSUADER_ALL; 
        public static final String PERSUADER_ALL_EXCEPT_AlwaysTrue;
        public static final String PERSUADER_ImmediateBuildPlan; 
        public static final String PERSUADER_ImmediateBuildPlan_City; 
        public static final String PERSUADER_ImmediateBuildPlan_Settlement; 
        public static final String PERSUADER_ImmediateBuildPlan_Road; 
        public static final String PERSUADER_ImmediateBuildPlan_DevCard;
        public static final String PERSUADER_ImmediateTradePlan; 
        public static final String PERSUADER_ImmediateTradePlanBank; 
        public static final String PERSUADER_ImmediateTradePlanPort; 
        public static final String PERSUADER_ResourceBased; 
        public static final String PERSUADER_ResourceBasedTooManyRes; 
        public static final String PERSUADER_ResourceBasedCantGetRes; 
        public static final String PERSUADER_AlwaysTrue; 
        public static final String PERSUADER_AlwaysTrue_RobPromise; 
        public static final String PERSUADER_AlwaysTrue_PromiseRes; 
        public static final String PERSUADER_NONE; 
        
        /** The robot will only attempt to persuade after having its trade offer rejected */
        public static final String Persuade_ONLY_AFTER_REJECT;
        /** The robot will only attempt a persuasion move if it can get an immediate build plan benefit*/
        public static final String Persuade_ONLY_TO_GET_ImmediateBuildPlan;
        /** Commit to making a persuasive statement only when it appears (ignoring unknowns) that one of the addressees has the resources to fulfil the trade. */
        public static final String Persuade_ONLY_WHEN_TRADE_LOOKS_POSSIBLE;
        /** Set the maximum number of unknowns which can be assigned to be able to get a persuasion (randomly assigned). DEFAULT = 0*/
        public static final String Persuade_Maximum_Number_Of_Unknowns_To_Be_Assigned;
        /** The robot will generate the best persuasion by a hardcoded ordering 
         * format must be of the something like: "itp->road->devcard->settlement->city->at->rb" where road, devcard, settlement and city are ibp persuasions
         * Note the default is randomly selecting. 
         * Language which can be used, along with associated persuasion type {itp (ITP), road (IBPRoad), devcard (IBPDevCard), settlement (IBPSettlement), city (IBPCity), atpr (ATPromiseResource), atrp (ATRobPromise), rbcgr (RBCantGetRes), rbtmr (RBTooManyRes), ibp (IBPSettlement, IBPDevCard, IBPCity, IBPRoad), rb (RBTooManyRes, RBCantGetRes), at (ATPromiseResource, ATRobPromise)} **/
        public static final String Persuade_Choose_Own_Ordering;
        /** The robot will not generate a persuasion which applies to a subset of the agents
         * The default is that, when a persuasion doesnt apply to all recipients - find one for a subset */
        public static final String Persuade_Only_With_Persuasion_That_Applies_to_Each_Recipient;
        
        
        /**
         * Robot Types used to define a persuader recipient robot 
         */
        //Persuaders Possible Agent Recipient Types which define which persuasion types can influence a robot recieving it.
        public static final String PERSUASION_RESP_ALL;
        public static final String PERSUASION_RESP_ALL_EXCEPT_AlwaysTrue;
        public static final String PERSUASION_RESP_ImmediateBuildPlan;
        public static final String PERSUASION_RESP_ImmediateBuildPlan_City;
        public static final String PERSUASION_RESP_ImmediateBuildPlan_Settlement;
        public static final String PERSUASION_RESP_ImmediateBuildPlan_Road;
        public static final String PERSUASION_RESP_ImmediateBuildPlan_DevCard;
        public static final String PERSUASION_RESP_ImmediateTradePlan;
        public static final String PERSUASION_RESP_ImmediateTradePlanBank;
        public static final String PERSUASION_RESP_ImmediateTradePlanPort;
        public static final String PERSUASION_RESP_ResourceBased; 
        public static final String PERSUASION_RESP_ResourceBasedTooManyRes; 
        public static final String PERSUASION_RESP_ResourceBasedCantGetRes; 
        public static final String PERSUASION_RESP_AlwaysTrue;
        public static final String PERSUASION_RESP_AlwaysTrue_RobPromise;
        public static final String PERSUASION_RESP_AlwaysTrue_PromiseRes;
        public static final String PERSUASION_RESP_NONE;
        
        /** Accept always true messages with probability - Set to 1 if not stated */
        public static final String Persuasion_RESP_ACCEPT_AlwaysTrue_With_Probability;
        /** Allows for rejection of peruasion moves even if they are consistent - Set to 1 if not stated */
        public static final String Persuasion_RESP_Accept_Consistent_Args_With_Probability;
        /** Ignore the persuasion if the opponent has X or more VPs*/
        public static final String Persuasion_RESP_IGNORE_IF_PERSUADER_HAS_X_VPS;
        /** The robot will automatically ignore a persuasion move if opponent appears to get an immediate build plan benefit*/
        public static final String Persuasion_RESP_IGNORE_IF_PERSUADER_TO_GET_ImmediateBuildPlan;
        /** REJECT all persuasion attempts where the offer does not help fulfil the current Build Plan */
        public static final String Persuasion_RESP_ONLY_IF_FULFILS_CURRENT_BUILD_PLAN;
        /** Ignore persuasion moves from players with higher visibile VPs */
        public static final String Persuasion_RESP_IGNORE_PERSUASIONS_OF_PLAYERS_WITH_HIGHER_VP;
        /** Ignore persuasion moves from players with the highest visibile VPs */
        public static final String Persuasion_RESP_IGNORE_PERSUASIONS_OF_PLAYERS_WITH_HIGHEST_VP;
        /** When a persuasion applies this robot will give a weight to the old consider offer rather than simply accepting*/
        public static final String Persuasion_RESP_Use_Weight_When_Persuasion_Is_Accepted;
        
        /** A list of all the possible Robot Types where 1 is required for the robot to be considered a Persuader*/
        public static final ArrayList<String> persuaderTypes;
        /** A list of all the possible Robot Types where 1 is required for the robot to be considered a Persuader recipient*/
        public static final ArrayList<String> persuaderRecipientTypes;
        
        public static final String SUP_LEARNING_NEGOTIATOR;
        public static final String DRL_LEARNING_NEGOTIATOR;
        public static final String RANDOM_NEGOTIATOR;
        public static final String RANDOM_LEGAL_NEGOTIATOR;
        public static final String MDP_LEARNING_NEGOTIATOR;
        public static final String ADVERSARY_NEGOTIATOR;
            
        static {
        	PLAYER_ICON = p("PLAYER_ICON", Integer.class);
            ORIGINAL_ROBOT = p("ORIGINAL_ROBOT");
            PARTIALISING_COMPLETE_OFFERS_50_PERCENT = p("PARTIALISING_COMPLETE_OFFERS_50_PERCENT");
            PARTIALISING_COMPLETE_OFFERS_100_PERCENT = p("PARTIALISING_COMPLETE_OFFERS_100_PERCENT");
            SIMPLY_ASK = p("SIMPLY_ASK");
            WEAK_MEMORY = p("WEAK_MEMORY");
            NO_MEMORY_OF_TRADES = p("NO_MEMORY_OF_TRADES");
            NO_MEMORY_OF_IS_SELLING = p("NO_MEMORY_OF_IS_SELLING");
            ALWAYS_ASSUMING_IS_SELLING = p("ALWAYS_ASSUMING_IS_SELLING");
            RANDOM_COMPLETION_OF_PARTIAL_OFFERS = p("RANDOM_COMPLETION_OF_PARTIAL_OFFERS");
            PARTIAL_COMPLETION_CHOOSE_MY_BEST = p("PARTIAL_COMPLETION_CHOOSE_MY_BEST");
            PARTIAL_COMPLETION_CHOOSE_OPPONENT_WORST = p("PARTIAL_COMPLETION_CHOOSE_OPPONENT_WORST");
            PARTIAL_COMPLETION_PREFER_MY_BEST = p("PARTIAL_COMPLETION_PREFER_MY_BEST");
            PARTIAL_COMPLETION_PREFER_OPPONENT_WORST = p("PARTIAL_COMPLETION_PREFER_OPPONENT_WORST");
            IGNORANT = p("IGNORANT");
            IGNORANT_OPTIMIST = p("IGNORANT_OPTIMIST");
            IGNORANT_PESSIMIST = p("IGNORANT_PESSIMIST");
            PESSIMISTIC_ABOUT_OPPONENT_UNKONWNS = p("PESSIMISTIC_ABOUT_OPPONENT_UNKONWNS");
            FORGETTING_RESOURCES_PESSIMIST = p("FORGETTING_RESOURCES_PESSIMIST", Integer.class);
            FORGETTING_RESOURCES_OPTIMIST = p("FORGETTING_RESOURCES_OPTIMIST", Integer.class);
            FORGETTING_RESOURCES_RANDOM = p("FORGETTING_RESOURCES_RANDOM", Integer.class);
            SIMPLE_UPDATE_OF_OPPONENT_RESOURCES_FROM_OFFER = p("UPDATE_OPPONENT_RESOURCES_FROM_OFFER");
            HRP_GUESS = p("HRP_GUESS");
            SHARE_BUILD_PLAN = p("SHARE_BUILD_PLAN");
            SHARE_BUILD_PLAN_CHANGE = p("SHARE_BUILD_PLAN_CHANGE");
            LYING_ABOUT_BUILD_PLAN = p("LYING_ABOUT_BUILD_PLAN");
            LISTEN_BUILD_PLAN = p("LISTEN_BUILD_PLAN");
            NO_TRADES = p("NO_TRADES");
            BUILD_PLAN_INDIFFERENT = p("BUILD_PLAN_INDIFFERENT");
            OPTIMISTIC_OFFERER = p("OPTIMISTIC_OFFERER", Integer.class);
            PROBABILISTIC_OFFER_DENIER = p("PROBABILISTIC_OFFER_DENIER", Integer.class);
            CONTINUE_TRADING_WITH_AGENTS_CLOSE_TO_WINNING = p("CONTINUE_TRADING_WITH_AGENTS_CLOSE_TO_WINNING");
            SHARE_RESOURCES = p("SHARE_RESOURCES");     
            LISTEN_RESOURCES = p("LISTEN_RESOURCES");    
            LEGACY_PLAY1 = p("LEGACY_PLAY1");
            OLD_FAST_GAME_STRATEGY = p("OLD_FAST_GAME_STRATEGY");
            DONT_FILTER_WORSE_OFFERS = p("DONT_FILTER_WORSE_OFFERS");
            FILTER_GIVING_AWAY_RESOURCES_NEEDED_FOR_BBP = p("DONT_FILTER_GIVING_AWAY_RESOURCES_NEEDED_FOR_BBP");
            SMART_GAME_STRATEGY = p("SMART_GAME_STRATEGY");
            FAVOUR_ROADS_INITIALLY = p("FAVOUR_ROADS_INITIALLY");
            FAVOUR_SETTLEMENTS_INITIALLY = p("FAVOUR_SETTLEMENTS_INITIALLY");
            FAVOUR_CITIES_INITIALLY = p("FAVOUR_CITIES_INITIALLY", Double.class);
            FAVOUR_DEV_CARDS_INITIALLY = p("FAVOUR_DEV_CARDS_INITIALLY", Double.class);
            FAVOUR_ROADS = p("FAVOUR_ROADS");     
            FAVOUR_SETTLEMENTS = p("FAVOUR_SETTLEMENTS", Double.class);
            FAVOUR_CITIES = p("FAVOUR_CITIES", Double.class);     
            FAVOUR_DEV_CARDS = p("FAVOUR_DEV_CARDS", Double.class);
            FAVOUR_LR = p("FAVOUR_LR", Double.class);
            FAVOUR_LA = p("FAVOUR_LA", Double.class);
            SHARE_EXTRA_RES = p("SHARE_EXTRA_RES");   
            LISTEN_EXTRA_RES = p("LISTEN_EXTRA_RES");   
            SHARE_WANTED_RES = p("SHARE_WANTED_RES");   
            LISTEN_WANTED_RES = p("LISTEN_WANTED_RES");   
            LYING_ABOUT_RESOURCES = p("LYING_ABOUT_RESOURCES");
            LYING_ABOUT_RESOURCES_HAVE_1 = p("LYING_ABOUT_RESOURCES_HAVE_1");
            LYING_ABOUT_RESOURCES_HAVE_2 = p("LYING_ABOUT_RESOURCES_HAVE_2");
            MIN_VP_TO_TRY_LR = p("MIN_VP_TO_TRY_LR", Integer.class);
            MIN_VP_TO_TRY_LA = p("MIN_VP_TO_TRY_LA", Integer.class);
            CHOOSE_BEST_MINUS_N_BUILD_PLAN = p("CHOOSE_BEST_MINUS_N_BUILD_PLAN", Integer.class);
            TRY_N_BEST_BUILD_PLANS = p("TRY_N_BEST_BUILD_PLANS", Integer.class);
            RANK_BPS_TRADE_OFF_ES_FACTOR = p("RANK_BPS_TRADE_OFF_ES_FACTOR", Double.class);
            RANK_BPS_TRADE_OFF_EP_FACTOR = p("RANK_BPS_TRADE_OFF_EP_FACTOR", Double.class);
            ADAPT_TO_OPPONENT_TYPE = p("ADAPT_TO_OPPONENT_TYPE");
            ES_CARDS = p("ES_CARDS", Integer.class);
            ES_LA = p("ES_LA", Integer.class);
            ES_LR = p("ES_LR", Integer.class);
            BBPPP_GUESS = p("BBPPP_GUESS", Integer.class);
            ASSUME_OTHER_AGENTS_USE_OUR_STRATEGY = p("ASSUME_OTHER_AGENTS_USE_OUR_STRATEGY");
            ASSUME_OTHER_AGENTS_USE_0_BEST_STRATEGY = p("ASSUME_OTHER_AGENTS_USE_0_BEST_STRATEGY");
            
            NP_ANNOUNCEMENT_GULLIBLE = p("NP_ANNOUNCEMENTS_GULLIBLE");
            
            NP_FORCE_ACCEPT_PROPOSER = p("NP_FORCE_ACCEPT_PROPOSER");
            NP_FORCE_ACCEPT_WHEN_LEADER_HAS_VP = p("NP_FORCE_ACCEPT_WHEN_LEADER_HAS_VP", Integer.class);
            NP_NUM_FORCE_ACCEPT_MOVES = p("NP_NUM_FORCE_ACCEPT_MOVES", Integer.class);
            NP_FORCE_ACCEPT_ONLY_IF_POSSIBLE = p("NP_FORCE_ACCEPT_ONLY_IF_POSSIBLE");
            NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_BUILD = p("NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_BUILD");
            NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BUILD = p("NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BUILD");
            NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BUILD_NOT_POSSIBLE_BEFORE = p("NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BUILD_NOT_POSSIBLE_BEFORE");
            NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BANK_TRADE = p("NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BANK_TRADE");
            NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BANK_TRADE_NOT_POSSIBLE_BEFORE = p("NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BANK_TRADE_NOT_POSSIBLE_BEFORE");
            NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BUILD_OR_BANK_TRADE_NOT_POSSIBLE_BEFORE = p("NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BUILD_OR_BANK_TRADE_NOT_POSSIBLE_BEFORE");
            NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BANK_TRADE_FOR_BUILD_NOT_POSSIBLE_BEFORE = p("NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BANK_TRADE_FOR_BUILD_NOT_POSSIBLE_BEFORE");
            NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BANK_TRADE_FOR_IMMEDIATE_BUILD_NOT_POSSIBLE_BEFORE = p("NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BANK_TRADE_FOR_IMMEDIATE_BUILD_NOT_POSSIBLE_BEFORE");
            NP_FORCE_ACCEPT_MISC_MOVES_ALL = p("NP_FORCE_ACCEPT_MISC_MOVES_ALL");
            NP_FORCE_ACCEPT_MISC_MOVES_NPB = p ("NP_FORCE_ACCEPT_MISC_MOVES_NPB");
            NP_FORCE_ACCEPT_ONLY_AFTER_REJECT = p("NP_FORCE_ACCEPT_ONLY_AFTER_REJECT");
            NP_FORCE_ACCEPT_GULLIBLE = p("NP_FORCE_ACCEPT_GULLIBLE");
            NP_FORCE_ACCEPT_COMPLY_ONLY_FOR_IMMEDIATE_BUILD_NOT_POSSIBLE_BEFORE = p("NP_FORCE_ACCEPT_COMPLY_ONLY_FOR_IMMEDIATE_BUILD_NOT_POSSIBLE_BEFORE");
            NP_FORCE_ACCEPT_COMPLY_ONLY_FOR_IMMEDIATE_BANK_TRADE_NOT_POSSIBLE_BEFORE = p("NP_FORCE_ACCEPT_COMPLY_ONLY_FOR_IMMEDIATE_BANK_TRADE_NOT_POSSIBLE_BEFORE");
            NP_FORCE_ACCEPT_COMPLY_ONLY_FOR_IMMEDIATE_BUILD_OR_BANK_TRADE_NOT_POSSIBLE_BEFORE = p("NP_FORCE_ACCEPT_COMPLY_ONLY_FOR_IMMEDIATE_BUILD_OR_BANK_TRADE_NOT_POSSIBLE_BEFORE");
            NP_ALWAYS_ACCEPT = p("NP_ALWAYS_ACCEPT");
            NP_ALWAYS_REJECT = p("NP_ALWAYS_REJECT");
            NP_GREEDY_ACCEPT = p("NP_GREEDY_ACCEPT");
            NP_ALWAYS_EXPECT_ACCEPT = p("NP_ALWAYS_EXPECT_ACCEPT");
            NP_GIVE_TRADE_TO_ACCEPTER_WITH_FEWEST_VP = p("NP_GIVE_TRADE_TO_ACCEPTER_WITH_FEWEST_VP");
            NP_GIVE_TRADE_TO_ACCEPTER_WITH_FEWEST_RES = p("NP_GIVE_TRADE_TO_ACCEPTER_WITH_FEWEST_RES");
            NP_GIVE_TRADE_TO_ACCEPTER_WITH_MOST_RES = p("NP_GIVE_TRADE_TO_ACCEPTER_WITH_MOST_RES");
            NP_ACCEPT_BETTER_COUNTEROFFER = p("NP_ACCEPT_BETTER_COUNTEROFFER");
            
            NP_PROPOSE_EMBARGO_WHEN_LEADER_HAS_VP = p("NP_PROPOSE_EMBARGO_WHEN_LEADER_HAS_VP", Integer.class);
            NP_PROPOSE_EMBARGO_AFTER_OFFER_FOR_IMMEDIATE_BUILD = p("NP_PROPOSE_EMBARGO_AFTER_OFFER_FOR_IMMEDIATE_BUILD");
            NP_COMPLY_WITH_EMBARGOES = p("NP_COMPLY_WITH_EMBARGOES");
            NP_LIFT_EMBARGOES_AFTER_TURNS = p("NP_LIFT_EMBARGOES_AFTER_TURNS", Integer.class);
            NP_NUM_EMBARGOES_AGENT_CAN_PROPOSE = p("NP_NUM_EMBARGOES_AGENT_CAN_PROPOSE", Integer.class);
            NP_NUM_EMBARGOES_AGENT_CAN_ENGAGE_IN = p("NP_NUM_EMBARGOES_AGENT_CAN_ENGAGE_IN", Integer.class);

            NP_PROPOSE_BLOCK_WHEN_LEADER_HAS_VP = p("NP_PROPPersuade_Maximum_Number_Of_Unknowns_To_Be_AssignedOSE_BLOCK_WHEN_LEADER_HAS_VP", Integer.class);
            NP_BLOCK_TRADES_NOT_MADE_TO_ME = p("NP_BLOCK_TRADES_NOT_MADE_TO_ME");
            NP_BLOCK_TRADES_I_WOULD_REJECT = p("NP_BLOCK_TRADES_I_WOULD_REJECT");
            NP_BLOCK_RESOURCES_ASKED_FOR = p("NP_BLOCK_RESOURCES_ASKED_FOR");
            NP_BLOCK_ONLY_FOR_IMMEDIATE_BUILD = p("NP_BLOCK_ONLY_FOR_IMMEDIATE_BUILD");
            NP_BLOCK_TRADES_GULLIBLE = p("NP_BLOCK_TRADES_GULLIBLE");
            NP_NUM_BLOCKS_AGENT_CAN_PROPOSE = p("NP_NUM_BLOCKS_AGENT_CAN_PROPOSE", Integer.class);
            NP_LIFT_BLOCK_AFTER_TURNS = p("NP_LIFT_BLOCK_AFTER_TURNS", Integer.class);
            
            NP_MAX_ETB_FOR_TRADE_OFFER = p("NP_MAX_ETB_FOR_TRADE_OFFER", Integer.class);
            
            USE_OLD_NEGOTIATOR = p("USE_OLD_NEGOTIATOR");
            USE_OLD_NEGOTIATOR_COUNTEROFFER = p("USE_OLD_NEGOTIATOR_COUNTEROFFER");
            NP_AGGREGATE_TRADE_OFFERS = p("NP_AGGREGATE_TRADE_OFFERS");
            NP_DONT_MAKE_PARTIAL_OFFERS = p("NP_DONT_MAKE_PARTIAL_OFFERS");
            NP_DONT_MAKE_DISJUNCTIVE_OFFERS = p("NP_DONT_MAKE_DISJUNCTIVE_OFFERS");
            NP_MAX_DELTA_ETB_FOR_OFFER_AGGREGATION = p("NP_MAX_DELTA_ETB_FOR_OFFER_AGGREGATION", Integer.class);
            NP_MAX_NUMBER_OF_OFFERS_WITHOUT_BUILDING = p("NP_MAX_NUMBER_OF_OFFERS_WITHOUT_BUILDING", Integer.class);
            DONT_CONSIDER_OFFERING_2_RESOURCES = p("DONT_CONSIDER_OFFERING_2_RESOURCES");
            DONT_CONSIDER_ASKING_FOR_2_RESOURCES = p("DONT_CONSIDER_ASKING_FOR_2_RESOURCES");
            NP_IMPROVEMENT_OVER_BATNA_THRESHOLD = p("NP_IMPROVEMENT_OVER_BATNA_THRESHOLD", Integer.class);
            BSE_IMPROVEMENT_THRESHOLD = p("BSE_IMPROVEMENT_THRESHOLD", Integer.class);
            BSE_IMPROVEMENT_THRESHOLD_RATIO = p("BSE_IMPROVEMENT_THRESHOLD_RATIO", Double.class);
            NP_IMPROVEMENT_OVER_BATNA_THRESHOLD_RATIO = p("NP_IMPROVEMENT_OVER_BATNA_THRESHOLD_RATIO", Double.class);
            JEDI_MIND_TRICK = p("JEDI_MIND_TRICK");
            
            ROB_FOR_NEED = p("ROB_FOR_NEED");
            SOLICIT_TRADES = p("SOLICIT_TRADES");
            MAKE_SOLICITED_OFFER = p("MAKE_SOLICITED_OFFER");
            USE_ACT_R_DECLARATIVE_MEMORY = p("USE_ACT_R_DECLARATIVE_MEMORY");
            LOG_ACT_R_OUTPUT = p("LOG_ACT_R_OUTPUT");
            ACT_R_PARAMETER_RT = p("ACT_R_PARAMETER_RT", Double.class);
            BSE_ACCURATE = p("BSE_ACCURATE");
            BSE_FRACTIONAL = p("BSE_FRACTIONAL");
            BSE_USING_BELIEFS = p("BSE_USING_BELIEFS");
            
            DIALOGUE_MANAGER_USE_SDRT = p("DIALOGUE_MANAGER_USE_SDRT");
            
            //Persuaders Possible Agent Types
            PERSUADER_ALL = p("PERSUADER_ALL"); 
            PERSUADER_ALL_EXCEPT_AlwaysTrue = p("PERSUADER_ALL_EXCEPT_AlwaysTrue"); 
            PERSUADER_ImmediateBuildPlan = p("PERSUADER_ImmediateBuildPlan");
            PERSUADER_ImmediateBuildPlan_City = p("PERSUADER_ImmediateBuildPlan_City");
            PERSUADER_ImmediateBuildPlan_Settlement = p("PERSUADER_ImmediateBuildPlan_Settlement");
            PERSUADER_ImmediateBuildPlan_Road = p("PERSUADER_ImmediateBuildPlan_Road");
            PERSUADER_ImmediateBuildPlan_DevCard = p("PERSUADER_ImmediateBuildPlan_DevCard");
            PERSUADER_ImmediateTradePlan = p("PERSUADER_ImmediateTradePlan");
            PERSUADER_ImmediateTradePlanBank = p("PERSUADER_ImmediateTradePlanBank");
            PERSUADER_ImmediateTradePlanPort = p("PERSUADER_ImmediateTradePlanPort");
            PERSUADER_ResourceBased = p("PERSUADER_ResourceBased");
            PERSUADER_ResourceBasedTooManyRes = p("PERSUADER_ResourceBasedTooManyRes");
            PERSUADER_ResourceBasedCantGetRes = p("PERSUADER_ResourceBasedCantGetRes");
            PERSUADER_AlwaysTrue = p("PERSUADER_AlwaysTrue");
            PERSUADER_AlwaysTrue_RobPromise = p("PERSUADER_AlwaysTrue_RobPromise");
            PERSUADER_AlwaysTrue_PromiseRes = p("PERSUADER_AlwaysTrue_PromiseRes");
            PERSUADER_NONE = p("PERSUADER_NONE"); 
            
            //These are amendments to the robot type - a persuading robot must have one of the main types.
            Persuade_ONLY_AFTER_REJECT = p("Persuade_ONLY_AFTER_REJECT");
            Persuade_ONLY_TO_GET_ImmediateBuildPlan = p("Persuade_ONLY_TO_GET_ImmediateBuildPlan");
            Persuade_ONLY_WHEN_TRADE_LOOKS_POSSIBLE = p("Persuade_ONLY_WHEN_TRADE_LOOKS_POSSIBLE");
            Persuade_Maximum_Number_Of_Unknowns_To_Be_Assigned = p("Persuade_Maximum_Number_Of_Unknowns_To_Be_Assigned",Integer.class);
            Persuade_Choose_Own_Ordering = p("Persuade_Choose_Own_Ordering", String.class);
            Persuade_Only_With_Persuasion_That_Applies_to_Each_Recipient = p("Persuade_Only_With_Persuasion_That_Applies_to_Each_Recipient");
            
            //Persuasion Recipients Agent Types
            PERSUASION_RESP_ALL= p("PERSUASION_RESP_ALL"); 
            PERSUASION_RESP_ALL_EXCEPT_AlwaysTrue = p("PERSUASION_RESP_ALL_EXCEPT_AlwaysTrue"); 
            PERSUASION_RESP_ImmediateBuildPlan = p("PERSUASION_RESP_ImmediateBuildPlan");
            PERSUASION_RESP_ImmediateBuildPlan_City = p("PERSUASION_RESP_ImmediateBuildPlan_City");
            PERSUASION_RESP_ImmediateBuildPlan_Settlement = p("PERSUASION_RESP_ImmediateBuildPlan_Settlement");
            PERSUASION_RESP_ImmediateBuildPlan_Road = p("PERSUASION_RESP_ImmediateBuildPlan_Road");
            PERSUASION_RESP_ImmediateBuildPlan_DevCard = p("PERSUASION_RESP_ImmediateBuildPlan_DevCard");
            PERSUASION_RESP_ImmediateTradePlan = p("PERSUASION_RESP_ImmediateTradePlan");
            PERSUASION_RESP_ImmediateTradePlanBank = p("PERSUASION_RESP_ImmediateTradePlanBank");
            PERSUASION_RESP_ImmediateTradePlanPort = p("PERSUASION_RESP_ImmediateTradePlanPort");
            PERSUASION_RESP_ResourceBased = p("PERSUASION_RESP_ResourceBased");
            PERSUASION_RESP_ResourceBasedTooManyRes = p("PERSUASION_RESP_ResourceBasedTooManyRes");
            PERSUASION_RESP_ResourceBasedCantGetRes = p("PERSUASION_RESP_ResourceBasedCantGetRes");
            PERSUASION_RESP_AlwaysTrue = p("PERSUASION_RESP_AlwaysTrue");
            PERSUASION_RESP_AlwaysTrue_PromiseRes = p("PERSUASION_RESP_AlwaysTrue_PromiseRes");
            PERSUASION_RESP_AlwaysTrue_RobPromise = p("PERSUASION_RESP_AlwaysTrue_RobPromise");
            PERSUASION_RESP_NONE = p("PERSUASION_RESP_NONE"); 
        
            Persuasion_RESP_IGNORE_IF_PERSUADER_HAS_X_VPS = p("Persuasion_RESP_IGNORE_IF_PERSUADER_HAS_X_VPS", Integer.class);
            Persuasion_RESP_Accept_Consistent_Args_With_Probability = p("Persuasion_RESP_Accept_Consistent_Args_With_Probability",Double.class);
            Persuasion_RESP_ACCEPT_AlwaysTrue_With_Probability = p("Persuasion_RESP_ACCEPT_AlwaysTrue_With_Probability", Double.class);
            Persuasion_RESP_IGNORE_IF_PERSUADER_TO_GET_ImmediateBuildPlan = p("Persuasion_RESP_IGNORE_IF_PERSUADER_TO_GET_ImmediateBuildPlan");
            Persuasion_RESP_ONLY_IF_FULFILS_CURRENT_BUILD_PLAN = p("Persuasion_RESP_ONLY_IF_FULFILS_CURRENT_BUILD_PLAN");
            Persuasion_RESP_IGNORE_PERSUASIONS_OF_PLAYERS_WITH_HIGHER_VP = p("Persuasion_RESP_IGNORE_PERSUASIONS_OF_PLAYERS_WITH_HIGHER_VP");
            Persuasion_RESP_IGNORE_PERSUASIONS_OF_PLAYERS_WITH_HIGHEST_VP = p("Persuasion_RESP_IGNORE_PERSUASIONS_OF_PLAYERS_WITH_HIGHEST_VP");
            Persuasion_RESP_Use_Weight_When_Persuasion_Is_Accepted = p("Persuasion_RESP_Use_Weight_When_Persuasion_Is_Accepted",Integer.class);
            
        	persuaderTypes = new ArrayList<String>();
        	persuaderTypes.add(PERSUADER_ALL);
        	persuaderTypes.add(PERSUADER_ALL_EXCEPT_AlwaysTrue);
        	persuaderTypes.add(PERSUADER_ImmediateBuildPlan); 
        	persuaderTypes.add(PERSUADER_ImmediateBuildPlan_City); 
        	persuaderTypes.add(PERSUADER_ImmediateBuildPlan_Settlement); 
            persuaderTypes.add(PERSUADER_ImmediateBuildPlan_Road); 
            persuaderTypes.add(PERSUADER_ImmediateBuildPlan_DevCard); 
            persuaderTypes.add(PERSUADER_ImmediateTradePlan); 
            persuaderTypes.add(PERSUADER_ImmediateTradePlanBank); 
            persuaderTypes.add(PERSUADER_ImmediateTradePlanPort); 
            persuaderTypes.add(PERSUADER_ResourceBased);
            persuaderTypes.add(PERSUADER_ResourceBasedTooManyRes);
            persuaderTypes.add(PERSUADER_ResourceBasedCantGetRes);
            persuaderTypes.add(PERSUADER_AlwaysTrue); 
            persuaderTypes.add(PERSUADER_AlwaysTrue_RobPromise); 
            persuaderTypes.add(PERSUADER_AlwaysTrue_PromiseRes); 
            persuaderTypes.add(PERSUADER_NONE); 
            
            persuaderRecipientTypes = new ArrayList<String>();        	
            persuaderRecipientTypes.add(PERSUASION_RESP_ALL);
            persuaderRecipientTypes.add(PERSUASION_RESP_ALL_EXCEPT_AlwaysTrue);
            persuaderRecipientTypes.add(PERSUASION_RESP_ImmediateBuildPlan);
            persuaderRecipientTypes.add(PERSUASION_RESP_ImmediateBuildPlan_City);
            persuaderRecipientTypes.add(PERSUASION_RESP_ImmediateBuildPlan_Settlement);
            persuaderRecipientTypes.add(PERSUASION_RESP_ImmediateBuildPlan_Road);
            persuaderRecipientTypes.add(PERSUASION_RESP_ImmediateBuildPlan_DevCard);
            persuaderRecipientTypes.add(PERSUASION_RESP_ImmediateTradePlan);
            persuaderRecipientTypes.add(PERSUASION_RESP_ImmediateTradePlanBank);
            persuaderRecipientTypes.add(PERSUASION_RESP_ImmediateTradePlanPort);
            persuaderRecipientTypes.add(PERSUASION_RESP_ResourceBased);
            persuaderRecipientTypes.add(PERSUASION_RESP_ResourceBasedTooManyRes);
            persuaderRecipientTypes.add(PERSUASION_RESP_ResourceBasedCantGetRes);
            persuaderRecipientTypes.add(PERSUASION_RESP_AlwaysTrue);
            persuaderRecipientTypes.add(PERSUASION_RESP_AlwaysTrue_RobPromise);
            persuaderRecipientTypes.add(PERSUASION_RESP_AlwaysTrue_PromiseRes);
            persuaderRecipientTypes.add(PERSUASION_RESP_NONE);
        
            SUP_LEARNING_NEGOTIATOR = p( "SUP_LEARNING_NEGOTIATOR", Integer.class );
            DRL_LEARNING_NEGOTIATOR = p( "DRL_LEARNING_NEGOTIATOR", Integer.class );
            RANDOM_NEGOTIATOR = p( "RANDOM_NEGOTIATOR", Integer.class );
            RANDOM_LEGAL_NEGOTIATOR = p( "RANDOM_LEGAL_NEGOTIATOR", Integer.class );
            MDP_LEARNING_NEGOTIATOR = p( "MDP_LEARNING_NEGOTIATOR" );
            ADVERSARY_NEGOTIATOR = p( "ADVERSARY_NEGOTIATOR" );

        }
        
        protected static String p(String s) {
            TYPES.put(s, Boolean.class);
            return s;
        }
        
        protected static String p(String s, Class c) {
            TYPES.put(s,  c);
            return s;
        }
        
        public static boolean isValidType(String type) {
            return TYPES.containsKey(type);
        }
        
        public static Class getTypeClass(String type) {
            return TYPES.get(type);
        }
        
        /**
         * Code for actual instances of StacRobotType is below
         */
        
        private Map<String, Object> types;
        
        public StacRobotType() {
            this.types = new HashMap<String, Object>();
        }
        
        public void addType(String type) {
            if (!isValidType(type)) {
                throw new UnsupportedOperationException("Invalid type: " + type);
            }
            types.put(type,  Boolean.TRUE);
        }
        
        public void addType(String type, String o) {
            if (!isValidType(type)) {
                throw new UnsupportedOperationException("Invalid type: " + type);
            }
            Class c = TYPES.get(type);
            Object val = null;
            if (c == Integer.class) {
                val = Integer.parseInt(o);
            }
            else if (c == Double.class) {
                val = Double.parseDouble(o);
            }
            else if (c == String.class) {
                val = o;
            }
            else {
                // ???
            }
            types.put(type,  val);
        }
        
        public boolean isType(String type) {
            return types.containsKey(type);
        }
        
        public Object getTypeParam(String type) {
            return types.get(type);
        }
        
}
