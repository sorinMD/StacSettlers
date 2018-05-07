package soc.robot.stac.negotiationlearning;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Level;

import resources.Resources;
import soc.dialogue.StacTradeMessage;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceSet;
import soc.game.SOCTradeOffer;
import soc.game.StacTradeOffer;
import soc.message.SOCDevCard;
import soc.message.SOCPutPiece;
import soc.robot.SOCBuildPlanStack;
import soc.robot.SOCPossiblePiece;
import soc.robot.stac.StacRobotNegotiator;
import uk.ac.hw.mdps.MDP;
import uk.ac.hw.mdps.Policy;

public class LearningNegotiator {

	private static MyLogger logger;
    private static String learning_config_fname = "learning_settings.cfg";
	private static FileHandler fh;
	private static Properties config;
	
	private static String off_sel_pol_fname;
	private static String off_resp_pol_fname;
	//private static String POLICY_SAVING_DIR = "";
	private static int POLICY_SAVING_PERIOD = 5;
	private static boolean USE_POLICY_OBJECTS = false;
	//private static int AVG_SCORE_WINDOW_SIZE = 50;
	private static boolean LEGAL_OFFERS_ONLY = false;
	
	private enum MDP_Models { 
		BP_OWN_RES_1FOR1, SUMM_STATE_1FOR1
	};
	private static MDP_Models MDP_MODEL = MDP_Models.BP_OWN_RES_1FOR1;
	private static boolean INCLUDE_1FOR2_ACTIONS = true;

	private StacRobotNegotiator negotiator;
	private int player_number;
	private SOCPlayer player_data;
	
	private int num_training_dialogues;
	public int num_games_played;
	
	public SOCPossiblePiece build_plan;
	public int numVPs;
	
	//private int trade_response_counter;
	private boolean illegal_offer_made;
	private boolean offer_accepted; // at least one opponent has accepted our offer
	private boolean first_episode;
	
	private MDP off_sel_mdp;
	private MDP off_resp_mdp;
	
	/* 
	 * ROAD = 0
	 * SETTLEMENT = 1
	 * CITY = 2
	 * CARD = 4
	 * LONGEST_ROAD = 8
	 * LARGEST_ARMY = 16
	 */
	
	static final int NUM_RES_TYPES = 5;
	private static final int MAX_NR_RESOURCE = 5;
	
	// parameters for reward function
	private static float TRADE_OFFER = -1;
	private static float TRADE_SUCCESS = 5;
	private static float TRADE_FAILURE = -5;
	private static float ILLEGAL_OFFER = -3;
	private static float BUILD_PLAN_ROAD_SUCCESS = 10;
	private static float BUILD_PLAN_SETTLEMENT_SUCCESS = 25;
	private static float BUILD_PLAN_CITY_SUCCESS = 50;
	private static float BUILD_PLAN_DEVCARD_SUCCESS = 25;
	//private static float BUILD_PLAN_LONGEST_ROAD_SUCCESS = 60;
	//private static float BUILD_PLAN_LARGEST_ARMY_SUCCESS = 60;
	
	private static int[][] ACTION_SET = {
		// indices ( i, i+1 ) i = 0, 1, ..., 19
		{ 1, 0, 0, 0, 0 }, { 0, 1, 0, 0, 0 }, // 1 CLAY for 1 X
		{ 1, 0, 0, 0, 0 }, { 0, 0, 1, 0, 0 },
		{ 1, 0, 0, 0, 0 }, { 0, 0, 0, 1, 0 },
		{ 1, 0, 0, 0, 0 }, { 0, 0, 0, 0, 1 },
		{ 0, 1, 0, 0, 0 }, { 1, 0, 0, 0, 0 }, // 1 ORE for 1 X
		{ 0, 1, 0, 0, 0 }, { 0, 0, 1, 0, 0 },
		{ 0, 1, 0, 0, 0 }, { 0, 0, 0, 1, 0 },
		{ 0, 1, 0, 0, 0 }, { 0, 0, 0, 0, 1 },
		{ 0, 0, 1, 0, 0 }, { 1, 0, 0, 0, 0 }, // 1 SHEEP for 1 X
		{ 0, 0, 1, 0, 0 }, { 0, 1, 0, 0, 0 },
		{ 0, 0, 1, 0, 0 }, { 0, 0, 0, 1, 0 },
		{ 0, 0, 1, 0, 0 }, { 0, 0, 0, 0, 1 },
		{ 0, 0, 0, 1, 0 }, { 1, 0, 0, 0, 0 }, // 1 WHEAT for 1 X
		{ 0, 0, 0, 1, 0 }, { 0, 1, 0, 0, 0 },
		{ 0, 0, 0, 1, 0 }, { 0, 0, 1, 0, 0 },
		{ 0, 0, 0, 1, 0 }, { 0, 0, 0, 0, 1 },
		{ 0, 0, 0, 0, 1 }, { 1, 0, 0, 0, 0 }, // 1 WOOD for 1 X
		{ 0, 0, 0, 0, 1 }, { 0, 1, 0, 0, 0 },
		{ 0, 0, 0, 0, 1 }, { 0, 0, 1, 0, 0 },
		{ 0, 0, 0, 0, 1 }, { 0, 0, 0, 1, 0 }
	};
	
	private static int[][] ACTION_SET_1for2 = {
		// indices ( i, i+1 ) i = 0, 1, ..., 19
		{ 1, 0, 0, 0, 0 }, { 0, 1, 0, 0, 0 }, // 1 CLAY for 1 X
		{ 1, 0, 0, 0, 0 }, { 0, 0, 1, 0, 0 },
		{ 1, 0, 0, 0, 0 }, { 0, 0, 0, 1, 0 },
		{ 1, 0, 0, 0, 0 }, { 0, 0, 0, 0, 1 },
		{ 0, 1, 0, 0, 0 }, { 1, 0, 0, 0, 0 }, // 1 ORE for 1 X
		{ 0, 1, 0, 0, 0 }, { 0, 0, 1, 0, 0 },
		{ 0, 1, 0, 0, 0 }, { 0, 0, 0, 1, 0 },
		{ 0, 1, 0, 0, 0 }, { 0, 0, 0, 0, 1 },
		{ 0, 0, 1, 0, 0 }, { 1, 0, 0, 0, 0 }, // 1 SHEEP for 1 X
		{ 0, 0, 1, 0, 0 }, { 0, 1, 0, 0, 0 },
		{ 0, 0, 1, 0, 0 }, { 0, 0, 0, 1, 0 },
		{ 0, 0, 1, 0, 0 }, { 0, 0, 0, 0, 1 },
		{ 0, 0, 0, 1, 0 }, { 1, 0, 0, 0, 0 }, // 1 WHEAT for 1 X
		{ 0, 0, 0, 1, 0 }, { 0, 1, 0, 0, 0 },
		{ 0, 0, 0, 1, 0 }, { 0, 0, 1, 0, 0 },
		{ 0, 0, 0, 1, 0 }, { 0, 0, 0, 0, 1 },
		{ 0, 0, 0, 0, 1 }, { 1, 0, 0, 0, 0 }, // 1 WOOD for 1 X
		{ 0, 0, 0, 0, 1 }, { 0, 1, 0, 0, 0 },
		{ 0, 0, 0, 0, 1 }, { 0, 0, 1, 0, 0 },
		{ 0, 0, 0, 0, 1 }, { 0, 0, 0, 1, 0 },
		
		// indices ( i, i+1 ) i = 20, ..., 39
		{ 1, 0, 0, 0, 0 }, { 0, 2, 0, 0, 0 }, // 1 CLAY for 2 X
		{ 1, 0, 0, 0, 0 }, { 0, 0, 2, 0, 0 },
		{ 1, 0, 0, 0, 0 }, { 0, 0, 0, 2, 0 },
		{ 1, 0, 0, 0, 0 }, { 0, 0, 0, 0, 2 },
		{ 0, 1, 0, 0, 0 }, { 2, 0, 0, 0, 0 }, // 1 ORE for 2 X
		{ 0, 1, 0, 0, 0 }, { 0, 0, 2, 0, 0 },
		{ 0, 1, 0, 0, 0 }, { 0, 0, 0, 2, 0 },
		{ 0, 1, 0, 0, 0 }, { 0, 0, 0, 0, 2 },
		{ 0, 0, 1, 0, 0 }, { 2, 0, 0, 0, 0 }, // 1 SHEEP for 2 X
		{ 0, 0, 1, 0, 0 }, { 0, 2, 0, 0, 0 },
		{ 0, 0, 1, 0, 0 }, { 0, 0, 0, 2, 0 },
		{ 0, 0, 1, 0, 0 }, { 0, 0, 0, 0, 2 },
		{ 0, 0, 0, 1, 0 }, { 2, 0, 0, 0, 0 }, // 1 WHEAT for 2 X
		{ 0, 0, 0, 1, 0 }, { 0, 2, 0, 0, 0 },
		{ 0, 0, 0, 1, 0 }, { 0, 0, 2, 0, 0 },
		{ 0, 0, 0, 1, 0 }, { 0, 0, 0, 0, 2 },
		{ 0, 0, 0, 0, 1 }, { 2, 0, 0, 0, 0 }, // 1 WOOD for 2 X
		{ 0, 0, 0, 0, 1 }, { 0, 2, 0, 0, 0 },
		{ 0, 0, 0, 0, 1 }, { 0, 0, 2, 0, 0 },
		{ 0, 0, 0, 0, 1 }, { 0, 0, 0, 2, 0 }
	};
	
	private static int[][] ACTION_SET_1for2_1for11 = {
		// indices ( i, i+1 ) i = 0, 1, ..., 19
		{ 1, 0, 0, 0, 0 }, { 0, 1, 0, 0, 0 }, // 1 CLAY for 1 X
		{ 1, 0, 0, 0, 0 }, { 0, 0, 1, 0, 0 },
		{ 1, 0, 0, 0, 0 }, { 0, 0, 0, 1, 0 },
		{ 1, 0, 0, 0, 0 }, { 0, 0, 0, 0, 1 },
		{ 0, 1, 0, 0, 0 }, { 1, 0, 0, 0, 0 }, // 1 ORE for 1 X
		{ 0, 1, 0, 0, 0 }, { 0, 0, 1, 0, 0 },
		{ 0, 1, 0, 0, 0 }, { 0, 0, 0, 1, 0 },
		{ 0, 1, 0, 0, 0 }, { 0, 0, 0, 0, 1 },
		{ 0, 0, 1, 0, 0 }, { 1, 0, 0, 0, 0 }, // 1 SHEEP for 1 X
		{ 0, 0, 1, 0, 0 }, { 0, 1, 0, 0, 0 },
		{ 0, 0, 1, 0, 0 }, { 0, 0, 0, 1, 0 },
		{ 0, 0, 1, 0, 0 }, { 0, 0, 0, 0, 1 },
		{ 0, 0, 0, 1, 0 }, { 1, 0, 0, 0, 0 }, // 1 WHEAT for 1 X
		{ 0, 0, 0, 1, 0 }, { 0, 1, 0, 0, 0 },
		{ 0, 0, 0, 1, 0 }, { 0, 0, 1, 0, 0 },
		{ 0, 0, 0, 1, 0 }, { 0, 0, 0, 0, 1 },
		{ 0, 0, 0, 0, 1 }, { 1, 0, 0, 0, 0 }, // 1 WOOD for 1 X
		{ 0, 0, 0, 0, 1 }, { 0, 1, 0, 0, 0 },
		{ 0, 0, 0, 0, 1 }, { 0, 0, 1, 0, 0 },
		{ 0, 0, 0, 0, 1 }, { 0, 0, 0, 1, 0 },
		
		// indices ( i, i+1 ) i = 20, ..., 39
		{ 1, 0, 0, 0, 0 }, { 0, 2, 0, 0, 0 }, // 1 CLAY for 2 X
		{ 1, 0, 0, 0, 0 }, { 0, 0, 2, 0, 0 },
		{ 1, 0, 0, 0, 0 }, { 0, 0, 0, 2, 0 },
		{ 1, 0, 0, 0, 0 }, { 0, 0, 0, 0, 2 },
		{ 0, 1, 0, 0, 0 }, { 2, 0, 0, 0, 0 }, // 1 ORE for 2 X
		{ 0, 1, 0, 0, 0 }, { 0, 0, 2, 0, 0 },
		{ 0, 1, 0, 0, 0 }, { 0, 0, 0, 2, 0 },
		{ 0, 1, 0, 0, 0 }, { 0, 0, 0, 0, 2 },
		{ 0, 0, 1, 0, 0 }, { 2, 0, 0, 0, 0 }, // 1 SHEEP for 2 X
		{ 0, 0, 1, 0, 0 }, { 0, 2, 0, 0, 0 },
		{ 0, 0, 1, 0, 0 }, { 0, 0, 0, 2, 0 },
		{ 0, 0, 1, 0, 0 }, { 0, 0, 0, 0, 2 },
		{ 0, 0, 0, 1, 0 }, { 2, 0, 0, 0, 0 }, // 1 WHEAT for 2 X
		{ 0, 0, 0, 1, 0 }, { 0, 2, 0, 0, 0 },
		{ 0, 0, 0, 1, 0 }, { 0, 0, 2, 0, 0 },
		{ 0, 0, 0, 1, 0 }, { 0, 0, 0, 0, 2 },
		{ 0, 0, 0, 0, 1 }, { 2, 0, 0, 0, 0 }, // 1 WOOD for 2 X
		{ 0, 0, 0, 0, 1 }, { 0, 2, 0, 0, 0 },
		{ 0, 0, 0, 0, 1 }, { 0, 0, 2, 0, 0 },
		{ 0, 0, 0, 0, 1 }, { 0, 0, 0, 2, 0 },

		// indices ( i, i+1 ) i = 40, ..., 69
		{ 1, 0, 0, 0, 0 }, { 0, 1, 1, 0, 0 }, // 1 CLAY for 1 X1 and 1 X2
		{ 1, 0, 0, 0, 0 }, { 0, 1, 0, 1, 0 },
		{ 1, 0, 0, 0, 0 }, { 0, 1, 0, 0, 1 },
		{ 1, 0, 0, 0, 0 }, { 0, 0, 1, 1, 0 },
		{ 1, 0, 0, 0, 0 }, { 0, 0, 1, 0, 1 },
		{ 1, 0, 0, 0, 0 }, { 0, 0, 0, 1, 1 },
		{ 0, 1, 0, 0, 0 }, { 1, 0, 1, 0, 0 }, // 1 ORE for 1 X1 and 1 X2
		{ 0, 1, 0, 0, 0 }, { 1, 0, 0, 1, 0 },
		{ 0, 1, 0, 0, 0 }, { 1, 0, 0, 0, 1 },
		{ 0, 1, 0, 0, 0 }, { 0, 0, 1, 1, 0 },
		{ 0, 1, 0, 0, 0 }, { 0, 0, 1, 0, 1 },
		{ 0, 1, 0, 0, 0 }, { 0, 0, 0, 1, 1 },
		{ 0, 0, 1, 0, 0 }, { 1, 1, 0, 0, 0 }, // 1 SHEEP for 1 X1 and 1 X2
		{ 0, 0, 1, 0, 0 }, { 1, 0, 0, 1, 0 },
		{ 0, 0, 1, 0, 0 }, { 1, 0, 0, 0, 1 },
		{ 0, 0, 1, 0, 0 }, { 0, 1, 0, 1, 0 },
		{ 0, 0, 1, 0, 0 }, { 0, 1, 0, 0, 1 },
		{ 0, 0, 1, 0, 0 }, { 0, 0, 0, 1, 1 },
		{ 0, 0, 0, 1, 0 }, { 1, 1, 0, 0, 0 }, // 1 WHEAT for 1 X1 and 1 X2
		{ 0, 0, 0, 1, 0 }, { 1, 0, 1, 0, 0 },
		{ 0, 0, 0, 1, 0 }, { 1, 0, 0, 0, 1 },
		{ 0, 0, 0, 1, 0 }, { 0, 1, 1, 0, 0 },
		{ 0, 0, 0, 1, 0 }, { 0, 1, 0, 0, 1 },
		{ 0, 0, 0, 1, 0 }, { 0, 0, 1, 0, 1 },
		{ 0, 0, 0, 0, 1 }, { 1, 1, 0, 0, 0 }, // 1 WOOD for 1 X1 and 1 X2
		{ 0, 0, 0, 0, 1 }, { 1, 0, 1, 0, 0 },
		{ 0, 0, 0, 0, 1 }, { 1, 0, 0, 1, 0 },
		{ 0, 0, 0, 0, 1 }, { 0, 1, 1, 0, 0 },
		{ 0, 0, 0, 0, 1 }, { 0, 1, 0, 1, 0 },
		{ 0, 0, 0, 0, 1 }, { 0, 0, 1, 1, 0 }
	};
	
	/**
	 * Constructor
	 * 
	 * @param neg negotiator this learning agent is part of
	 */
	public LearningNegotiator( StacRobotNegotiator neg, int playerNum ) {
		negotiator = neg;
		player_number = playerNum;
		SOCPlayer player_data = neg.getPlayerData();
		init();
	}
	
	public LearningNegotiator( StacRobotNegotiator neg ) {
		negotiator = neg;
		init();
	}
	
	private void init() {
		loadConfig();
		num_training_dialogues = 0;
		num_games_played = 0;
		build_plan = null;
		numVPs = 0;
		//trade_response_counter = -1;
		illegal_offer_made = false;
		offer_accepted = false;
		first_episode = true;
		
		// set up MDP models
		switch ( MDP_MODEL ) {
			case BP_OWN_RES_1FOR1 : 
				init_BP_OWN_RES_1FOR1( INCLUDE_1FOR2_ACTIONS );
				break;
			case SUMM_STATE_1FOR1 :
				init_SUMM_STATE_1FOR1( INCLUDE_1FOR2_ACTIONS );
				break;
			default:
				logger.log( Level.SEVERE, "Unexpected MDP model type" );
				break;
		}

	}
	
	/** Initialise MDP models using a (nearly) full representation of the agent's own resources */
	private void init_BP_OWN_RES_1FOR1( boolean oneForTwoActions ) {
		// ----------------------------------------------------------
		// MDP FOR OFFER SELECTION
		
		// state space
		int num_state_feats = NUM_RES_TYPES + 1; // features for build plan and 5 types of own resources 
		int[] state_dims = new int[num_state_feats];
		state_dims[0] = 4; // ROAD, SETTLEMENT, CITY, DEV-CARD
		for ( int i = 1; i < num_state_feats; i++ )
			state_dims[i] = 6; // 0 - 4, or >= 5 resources of each type
		
		// action space
		int num_acts = 1 + ( oneForTwoActions ? ACTION_SET_1for2.length / 2 : ACTION_SET.length / 2 );
		off_sel_mdp = new MDP( "off_sel", num_state_feats, state_dims, num_acts );
		setActionSpace( oneForTwoActions );
		if ( off_sel_pol_fname != null && !off_sel_pol_fname.isEmpty() ) {
			logger.logf( Level.INFO, "Loading offer selection policy from file %s\n", off_sel_pol_fname );
			//off_sel_mdp.loadPolicy( off_sel_pol_fname, USE_POLICY_OBJECTS );
			try {
		    	URL url = Resources.class.getResource( off_sel_pol_fname );
		    	InputStream pol_is = url.openStream();
				off_sel_mdp.loadPolicy( pol_is );
			} catch ( IOException ioe ) {
				logger.logf( Level.SEVERE, "Could not load offer selection policy %s\n", off_sel_pol_fname );
			}
		}
		
		// ----------------------------------------------------------
		// MDP FOR RESPONDING TO OFFERS
		
		// state space
		int num_state_feats_resp = NUM_RES_TYPES + 2; // features for the opponent's offer to respond to and our own resources 
		int[] state_dims_resp = new int[num_state_feats_resp];
		//state_dims_resp[0] = NUM_RES_TYPES * ( NUM_RES_TYPES - 1 ); // 1-for-1 offers only
		state_dims_resp[0] = 121; // now includes 1-2 and 2-1 offers (see method getOfferIndex for calculation) //NUM_RES_TYPES * ( NUM_RES_TYPES - 1 ); // 1-for-1 offers only
		state_dims_resp[1] = 4; // ROAD, SETTLEMENT, CITY, DEV-CARD
		for ( int i = 2; i < num_state_feats_resp; i++ )
			state_dims_resp[i] = 6; // 0 - 4, or >= 5 resources of each type

		// action space
		int num_acts_resp = 3; // accept, reject, counter-offer
		off_resp_mdp = new MDP( "off_resp", num_state_feats_resp, state_dims_resp, num_acts_resp );
		if ( off_resp_pol_fname != null && !off_resp_pol_fname.isEmpty() ) {
			logger.logf( Level.INFO, "Loading offer response policy from file %s\n", off_resp_pol_fname );
			//off_resp_mdp.loadPolicy( off_resp_pol_fname, USE_POLICY_OBJECTS );
			try {
		    	URL url = Resources.class.getResource( off_resp_pol_fname );
		    	InputStream pol_is = url.openStream();
				off_sel_mdp.loadPolicy( pol_is );
			} catch ( IOException ioe ) {
				logger.logf( Level.SEVERE, "Could not load response policy %s\n", off_resp_pol_fname );
			}
			
		}
		
	}
	
	/** Determine set of legal actions for each state */
	private void setActionSpace( boolean oneForTwoActions ) {
		ArrayList<SOCResourceSet> give_res_list = new ArrayList<SOCResourceSet>();
		for ( int j = 1; j < off_sel_mdp.getNumActions(); j++ ) {
			int k = (j-1) * 2;
			give_res_list.add( new SOCResourceSet( oneForTwoActions?ACTION_SET_1for2[k]:ACTION_SET[k]) );
		}
		int[] state_features;
		SOCResourceSet resources;
		for ( int i = 0; i < off_sel_mdp.getNumStates(); i++ ) {
			state_features = off_sel_mdp.getStateFeatures( i );
			//resources = new SOCResourceSet( state_features[3], state_features[4], state_features[5], state_features[6], state_features[7], 0 );
			resources = new SOCResourceSet( state_features[1], state_features[2], state_features[3], state_features[4], state_features[5], 0 );
			ArrayList<Integer> remove_list = new ArrayList<Integer>();
			for ( Integer act_ind : off_sel_mdp.getStateActionSet(i) ) {
				if ( act_ind > 0 && !resources.contains( give_res_list.get(act_ind-1) ) )
					remove_list.add( act_ind );
			}
			off_sel_mdp.removeActions( i, remove_list );
			logger.logf( Level.FINEST, "State %d: %d legal actions (out of %d [%d])\n", i, off_sel_mdp.getStateActionSet(i).size(), give_res_list.size(), off_sel_mdp.getNumActions() );
		}
	}
	
	/** Initialise MDP models using a summary state representation of the agent's own resources */
	private void init_SUMM_STATE_1FOR1( boolean oneForTwoActions ) {
		// ----------------------------------------------------------
		// MDP FOR OFFER SELECTION
		
		// state space
		int num_state_feats = NUM_RES_TYPES; // features for 5 types of own resources 
		//int num_state_feats = NUM_RES_TYPES + 1; // features for 5 types of own resources and previous offer 
		int[] state_dims = new int[num_state_feats];
		for ( int i = 0; i < NUM_RES_TYPES; i++ )
			state_dims[i] = 6; // 6 summary states for each resource type
		//state_dims[ NUM_RES_TYPES ] = 42; // 2 * 5 * 4 + 1 + 1 (40 1-1 and 1-2 offers, plus 1 no-offer, plus 1 null offer)
		
		// action space
		int num_acts = 1 + ( oneForTwoActions ? ACTION_SET_1for2.length / 2 : NUM_RES_TYPES * ( NUM_RES_TYPES - 1 ) );
		//state_dims[ NUM_RES_TYPES ] = num_acts + 1; // all possible actions, plus 1 null offer
		off_sel_mdp = new MDP( "off_sel", num_state_feats, state_dims, num_acts );
		if ( off_sel_pol_fname != null && !off_sel_pol_fname.isEmpty() ) {
			logger.logf( Level.INFO, "Loading offer selection policy from file %s\n", off_sel_pol_fname );
			//off_sel_mdp.loadPolicy( off_sel_pol_fname, USE_POLICY_OBJECTS );
			try {
		    	URL url = Resources.class.getResource( off_sel_pol_fname );
		    	InputStream pol_is = url.openStream();
				off_sel_mdp.loadPolicy( pol_is );
			} catch ( IOException ioe ) {
				logger.logf( Level.SEVERE, "Could not load offer selection policy %s\n", off_sel_pol_fname );
			}
		}
		
		// ----------------------------------------------------------
		// MDP FOR RESPONDING TO OFFERS
		
		// state space
		int num_state_feats_resp = NUM_RES_TYPES + 1;
		int[] state_dims_resp = new int[num_state_feats_resp];
		//state_dims_resp[ 0 ] = NUM_RES_TYPES * ( NUM_RES_TYPES - 1 ); // 1-for-1 offers only ...
		state_dims_resp[ 0 ] = 121; // 1-1 1-2 2-1 1-11 11-1 offers only; see methods considerOffer and getOfferIndex
		for ( int i = 1; i < num_state_feats_resp; i++ )
			state_dims_resp[i] = 6;
		
		// action space
		int num_acts_resp = 3; // accept, reject, counter-offer
		off_resp_mdp = new MDP( "off_resp", num_state_feats_resp, state_dims_resp, num_acts_resp );
		if ( off_resp_pol_fname != null && !off_resp_pol_fname.isEmpty() ) {
			logger.logf( Level.INFO, "Loading offer response policy from file %s\n", off_resp_pol_fname );
			//off_resp_mdp.loadPolicy( off_resp_pol_fname, USE_POLICY_OBJECTS );
			try {
		    	URL url = Resources.class.getResource( off_resp_pol_fname );
		    	InputStream pol_is = url.openStream();
		    	off_resp_mdp.loadPolicy( pol_is );
			} catch ( IOException ioe ) {
				logger.logf( Level.SEVERE, "Could not load offer response policy %s\n", off_resp_pol_fname );
			}
		}
		
	}
	
	/** Determine set of legal actions for each state */
	private void setActionSpace_old() {
		ArrayList<SOCResourceSet> give_res_list = new ArrayList<SOCResourceSet>();
		for ( int j = 1; j < off_sel_mdp.getNumActions(); j++ )
			give_res_list.add( new SOCResourceSet( ACTION_SET[ (j-1)*2 ] ) );
		int[] state_features;
		SOCResourceSet resources;
		for ( int i = 0; i < off_sel_mdp.getNumStates(); i++ ) {
			state_features = off_sel_mdp.getStateFeatures( i );
			resources = new SOCResourceSet( state_features[3], state_features[4], state_features[5], state_features[6], state_features[7], 0 );
			//System.out.printf( "state %d: %s\n", i, resources.toFriendlyString() );
			ArrayList<Integer> remove_list = new ArrayList<Integer>(); // Efficiency issue: set of illegal actions will be the same all combinations of state features 0-2, given the resource features 3-7
			for ( Integer act_ind : off_sel_mdp.getStateActionSet(i) ) {
				if ( act_ind > 0 && !resources.contains( give_res_list.get(act_ind-1) ) )
					remove_list.add( act_ind );
			}
			//policy.state_action_sets[i].removeAll( remove_list );
			off_sel_mdp.removeActions( i, remove_list );
			logger.logf( Level.FINEST, "State %d: %d legal actions (out of %d [%d])\n", i, off_sel_mdp.getStateActionSet(i).size(), give_res_list.size(), off_sel_mdp.getNumActions() );
		}
	}
	
	public void setPlayerNumber( int playerNum ) {
		player_number = playerNum;
	}
	
	public void cancelLastAction() {
		logger.log(Level.FINEST, "Removing last state-action pair from history\n" );
		off_sel_mdp.cancelLastAction();
	}
	
	private int[] getCost( int bp_tp ) {
		int[] cost = new int[NUM_RES_TYPES];
		if ( bp_tp == SOCPossiblePiece.ROAD ) {
			cost[0] = 1;
			cost[4] = 1;
		} else if ( bp_tp == SOCPossiblePiece.SETTLEMENT ) {
			cost[0] = 1;
			cost[2] = 1;
			cost[3] = 1;
			cost[4] = 1;
		} else if ( bp_tp == SOCPossiblePiece.CITY ) {
			cost[1] = 3;
			cost[3] = 2;
		} else if ( bp_tp == SOCPossiblePiece.CARD ) {
			cost[1] = 1;
			cost[2] = 1;			
			cost[3] = 1;			
		}
		return cost;
	}

    public StacTradeOffer makeOffer( SOCBuildPlanStack bldPl, SOCResourceSet ourResources, String game, int playerNumber, SOCPlayer player_data ) {

    	SOCPossiblePiece bp_new = bldPl.get( 0 );
		build_plan = bp_new;
		int buildPlanType = build_plan.getType();
		int[] state_features = getStateFeatures_offSel( buildPlanType, ourResources );
//		int[] state_features1 = getStateFeatures_offSel( buildPlanType, ourResources );
//		int[] state_features = Arrays.copyOf( state_features1, state_features1.length + 1 );
//		int prevAct = off_sel_mdp.getLastAction();
//		state_features[ state_features1.length ] = ( prevAct == -1 ? off_sel_mdp.num_actions : prevAct );

		int action_index = -1;
		action_index = off_sel_mdp.getNextActionIndex( state_features );
		logger.logf( Level.INFO, "policy: ai:%d\n", action_index );

    	// construct offer from action_index
    	return createOffer( action_index, game, playerNumber, ourResources );

	}
    
    private int[] getStateFeatures_offSel( int buildPlanType, SOCResourceSet ourResources ) {
		switch ( MDP_MODEL ) {
			case BP_OWN_RES_1FOR1 :
				return getResourceState( buildPlanType, ourResources );
	
			case SUMM_STATE_1FOR1 :
				return getSummaryState( ourResources, new SOCResourceSet( getCost(buildPlanType) ) );
				
			default :
				return null;
		}
    }
     
    //private int[] getStateFeatures_offResp( SOCTradeOffer offer, int buildPlanType, SOCResourceSet ourResources ) {
    private int[] getStateFeatures_offResp( SOCResourceSet getSet, SOCResourceSet giveSet, int buildPlanType, SOCResourceSet ourResources ) {
    	int[] feats;
		switch ( MDP_MODEL ) {
			case BP_OWN_RES_1FOR1 :
				feats = getResourceState( buildPlanType, ourResources );
				break;
	
			case SUMM_STATE_1FOR1 :
				feats = getSummaryState( ourResources, new SOCResourceSet( getCost(buildPlanType) ) );
				break;
				
			default :
				return null;
		}
		
		int[] state_feats = new int[ feats.length + 1 ];
		//int give = offer.getGiveSet().pickResource() - 1;
		//int ask = offer.getGetSet().pickResource() - 1;
		//int offer_index = give * ( NUM_RES_TYPES - 1) + ( ask>give ? ask-1 : ask );
		int offer_index = getOfferIndex( getSet, giveSet );
		logger.logf( Level.FINEST, "Offer index: getSet:%s giveSet:%s -> %d\n", getSet.toString(), giveSet.toString(), offer_index );
		state_feats[ 0 ] = offer_index;
		for ( int k = 1; k < state_feats.length; k++ ) // copy other state features
			state_feats[ k ] = feats[ k - 1 ];
		
		return state_feats;

    }
    
    //private int getOfferIndex( SOCTradeOffer offer ) {
    private int getOfferIndex( SOCResourceSet getSet, SOCResourceSet giveSet ) {
    	//SOCResourceSet giveSet = offer.getGiveSet();
    	boolean giveOne = ( giveSet.getTotal() == 1 );
    	boolean giveTwoOfRes = ( giveSet.numberOfResourceTypes() == 2 );
		int give = giveSet.pickResource() - 1;
		int give2 = -1;
		if ( !giveOne && !giveTwoOfRes )
			give2 = getSecondResIndex( giveSet, give );
    	
    	//SOCResourceSet getSet = offer.getGetSet();
    	boolean getOne = ( getSet.getTotal() == 1 );
    	boolean getTwoOfRes = ( getSet.numberOfResourceTypes() == 2 );
		int get = getSet.pickResource() - 1;
		int get2 = -1;
		if ( !getOne && !getTwoOfRes )
			get2 = getSecondResIndex( getSet, give );
		
		/* start1for1 = 0
		 * start1for2 = 20
		 * start2for1 = 40
		 * start1for11 = 60
		 * start11for1 = 90
		 * other_index = 120 ---> 121 values for state feature representing opponent offers
		 */
		int start1for2 = NUM_RES_TYPES * ( NUM_RES_TYPES - 1 );
		int start2for1 = 2 * start1for2;
		int start1for11 = 3 * start1for2;
		int numCombs_give1_get11 = (NUM_RES_TYPES-1) * (NUM_RES_TYPES-2) / 2; // 5-1 choose 2 = 4 * 3 / 2 = 6
		int start11for1 = start1for11 + NUM_RES_TYPES * numCombs_give1_get11; // add 5 * 6 = 30 to ref index
		int numCombs_give11_get1 = NUM_RES_TYPES * (NUM_RES_TYPES-1) * (NUM_RES_TYPES-2) / 2; // 5 choose 2 = 5 * 4 / 2 = 10
		int other_index = start11for1 + numCombs_give11_get1 * (NUM_RES_TYPES-2); // add 3 * 10 = 30 to previous ref index
		
    	if ( giveOne ) {
    		if ( getOne ) { // e.g., 1 CLAY for 1 ORE
    			return give * ( NUM_RES_TYPES - 1) + ( get>give ? get-1 : get );
    		} else if ( getTwoOfRes ) { // e.g., 1 CLAY for 2 SHEEP
    			return start1for2 + give * ( NUM_RES_TYPES - 1) + ( get>give ? get-1 : get );
    		} else { // e.g., 1 CLAY for 1 SHEEP and 1 WHEAT
    			int get_a = ( get > give ? get - 1 : get );
    			int get2_a = ( get2 > give ? get2 - 1 : get2 );
    			return start1for11 + give * numCombs_give1_get11 + getCombinationIndex( get_a, get2_a, NUM_RES_TYPES - 1 );
    		}
    		
    	} else if ( giveTwoOfRes ) {
    		if ( getOne ) { // e.g., 2 ORE for 1 WOOD
    			return start2for1 + give * ( NUM_RES_TYPES - 1) + ( get>give ? get-1 : get );
    		} else if ( getTwoOfRes ) { // e.g., 2 ORE for 2 WOOD
    			return other_index;
    		} else { // e.g., 2 ORE for 1WHEAT and 1 WOOD
    			return other_index;
    		}
    		
    	} else {
    		if ( getOne ) { // e.g., 1 CLAY and 1 ORE for 1 SHEEP
    			return start11for1 + getCombinationIndex( give, give2, NUM_RES_TYPES ) * ( NUM_RES_TYPES - 2 ) + get;
    		} else if ( getTwoOfRes ) { // e.g., 1 CLAY and 1 ORE for 2 SHEEP
    			return other_index;
    		} else { // e.g., 1 CLAY and 1 ORE for 1 SHEEP and 1 WHEAT
    			return other_index;
    		}
    	}

    }
    
    private int getCombinationIndex( int pos1, int pos2, int num_pos ) {
    	if ( pos1 >= pos2 )
    		return -1;
    	if ( pos2 > num_pos )
    		return -1;
    	int index = 0;
    	for ( int k = 0; k < pos1; k++ )
    		for ( int l = k + 1; l < pos2; l++ )
    			index++;
    	return index;
    }
    
    private int getSecondResIndex( SOCResourceSet rs, int first ) {
    	int i = first + 1;
    	while ( i < NUM_RES_TYPES ) {
    		if ( rs.getAmount(i+1) > 0  )
    			return i;
    		i++;
    	}
    	return -1;
    }
    
    private int[] getResourceState( int buildPlanType, SOCResourceSet ourResources ) {
    	int[] state_features = new int[ NUM_RES_TYPES + 1 ];
		if ( buildPlanType == SOCPossiblePiece.ROAD || buildPlanType == SOCPossiblePiece.SETTLEMENT || buildPlanType == SOCPossiblePiece.CITY )
			state_features[ 0 ] = buildPlanType; // 0, 1, or 2
		else if ( buildPlanType == SOCPossiblePiece.CARD )
			state_features[ 0 ] = 3;             // 3
		else
			return null; // build plan not supported by policy
		for ( int i = 0; i < NUM_RES_TYPES; i++ )
			state_features[ i + 1 ] = getResourceSummaryState( ourResources.getAmount(i) );
		return state_features;
    }

    public void handlePUTPIECE( SOCPutPiece mes ) {
    	int msg_tp = mes.getPieceType();
		logger.log( Level.FINEST, "LearningNegotiator> handlePUTPIECE( build plan " + msg_tp + ")" );
    	if ( build_plan != null && msg_tp == build_plan.getType() ) {
    		if ( msg_tp == SOCPossiblePiece.CITY ) {
    			logger.log( Level.FINEST, "LearningNegotiator> successfully built city: assign reward!" );
        		off_sel_mdp.recordReward( BUILD_PLAN_CITY_SUCCESS );
            	build_plan = null;
    		} else if ( msg_tp == SOCPossiblePiece.SETTLEMENT ) {
    			logger.log( Level.FINEST, "LearningNegotiator> successfully built settlement: assign reward!" );
    			off_sel_mdp.recordReward( BUILD_PLAN_SETTLEMENT_SUCCESS );
            	build_plan = null;
    		} else if ( msg_tp == SOCPossiblePiece.ROAD ) {
    			logger.log( Level.FINEST, "LearningNegotiator> successfully built road: assign reward!" );
    			off_sel_mdp.recordReward( BUILD_PLAN_ROAD_SUCCESS );
            	build_plan = null;
    		} else
    			logger.log( Level.FINEST, "LearningNegotiator> build plan not supported: no reward to assign" );
    	} else
			logger.log( Level.FINEST, "LearningNegotiator> build plan null or not matching" );
    }
    
    public void handleDEVCARD( SOCDevCard mes ) {
    	if ( mes.getAction() != SOCDevCard.DRAW ) {
    		logger.log( Level.FINEST, "LearningNegotiator> attempt to handle DEV CARD of different type than DRAW" );
    		return;
    	}
    	if ( build_plan != null && build_plan.getType() == SOCPossiblePiece.CARD ) {
    		logger.logf(Level.FINEST, "LearningNegotiator> successfully drawn development card (%d)\n", mes.getCardType() ); // see SOCDevCardConstants for card types
    		off_sel_mdp.recordReward( BUILD_PLAN_DEVCARD_SUCCESS );
    		build_plan = null;
    	}
    }
    
    /**
     * Handle responses to trade offers.
     * @param playerNum
     * @param message       the trade message as String
     */
    public String handleTradeMessage( int playerNum, String msg ) {

    	//System.out.printf( "LearningNegotiator> handleTradeMessage( %d, %s )\n", playerNum, msg );
    	
    	if ( msg.startsWith(StacTradeMessage.TRADE) ) {
    		String message = msg.substring( StacTradeMessage.TRADE.length() );
	    	
	        // Determine the start of the actual message and generate the TradeMessage
	        int sepIdx = message.indexOf( ':' );
	        String recips = message.substring( 0, sepIdx );
	        //System.out.printf( "LearningNegotiator> recipients: %s\n", recips );
	        if ( recips.contains( Integer.toString(player_number) ) ) {
	        	
	        	//System.out.printf( "LearningNegotiator> handleTradeMessage(%d,%s) addressed to us\n", playerNum, msg, player_number );
	        	
	            message = soc.dialogue.StacDialogueManager.fromMessage( message.substring(sepIdx+1) );
	            StacTradeMessage tm = StacTradeMessage.parse( message );
	            
	            SOCTradeOffer offer = tm.getOffer();
	            if ( offer != null ) {
	            	
	            	//System.out.println( "LearningNegotiator> Processing offer: " + offer.toString() );
	            	
	                //TODO update preferences and construct response
	            	String respStr = StacTradeMessage.TRADE + player_number + ":" + StacTradeMessage.REJECT;
	            	
//	            } else if ( trade_response_counter != -1 && trade_response_counter != 3 ) {
//	            	if ( tm.isAccept() ) {
//	            		offer_accepted = true;
//	            		trade_response_counter++;
//	            	} else if ( tm.isReject() || tm.isNoResponse() )
//	            		trade_response_counter++;
//	                if ( trade_response_counter == 3 && !illegal_offer_made ) { // all responses received: handle reward
//	                	if ( offer_accepted )
//	                		policy.recordReward( TRADE_SUCCESS );
//	                	else
//	                		policy.recordReward( TRADE_FAILURE );
//	                	trade_response_counter = -1;
//	                	offer_accepted = false;
//	                	illegal_offer_made = false;
//	                }
//	                
	            }
	            
	        } //else
	          //  System.out.println( "LearningNegotiator> trade message not directed at us" );
	    	
    	}
    	
    	//System.out.println( "LearningNegotiator> handleTradeMessage -> return empty string" );
    	return "";

    }

	//public int considerOffer( SOCTradeOffer offer, String player, int pl_num, SOCPlayer pl_data ) {
	public int considerOffer( SOCResourceSet getSet, SOCResourceSet giveSet, String player, int pl_num, SOCPlayer pl_data ) {
		// possible return values:
		String[] responses = { "REJECT_OFFER", "ACCEPT_OFFER", "COUNTER_OFFER" };
		
		// MDP state: own resources
		int buildPlanType = build_plan.getType();
		SOCResourceSet ourResources = pl_data.getResources();
		
		int[] state_features = getStateFeatures_offResp( getSet, giveSet, buildPlanType, ourResources );
		
		int response_index = -1;
		response_index = off_resp_mdp.getNextActionIndex( state_features );
		logger.logf( Level.INFO, "Offer response from policy: %d (%s)\n", response_index, responses[response_index] );

		return response_index;
	}
	
	/** This player has the resources to build a piece on the board or buy a development card:
	 * assign the appropriate rewards.
	 */
	public void buildRequestPlannedPiece( SOCPossiblePiece targetPiece, SOCBuildPlanStack plan ) {
		int buildPlanType = plan.get( 0 ).getType();
		if ( build_plan != null ) {
			int bpTp = build_plan.getType();
			if ( bpTp != buildPlanType )
				logger.log( Level.WARNING, "Assigning build plan reward: mismatch with current known build plan!" );
			else {
				if ( buildPlanType == SOCPossiblePiece.ROAD ) {
					off_sel_mdp.recordReward( BUILD_PLAN_ROAD_SUCCESS );
					off_resp_mdp.recordReward( BUILD_PLAN_ROAD_SUCCESS );
					build_plan = null;
					update();
				} else if ( buildPlanType == SOCPossiblePiece.SETTLEMENT ) {
					off_sel_mdp.recordReward( BUILD_PLAN_SETTLEMENT_SUCCESS );
					off_resp_mdp.recordReward( BUILD_PLAN_SETTLEMENT_SUCCESS );
					build_plan = null;
					update();
				} else if ( buildPlanType == SOCPossiblePiece.CITY ) {
					off_sel_mdp.recordReward( BUILD_PLAN_CITY_SUCCESS );
					off_resp_mdp.recordReward( BUILD_PLAN_CITY_SUCCESS );
					build_plan = null;
					update();
				} else if ( buildPlanType == SOCPossiblePiece.CARD ) {
					off_sel_mdp.recordReward( BUILD_PLAN_DEVCARD_SUCCESS );
					off_resp_mdp.recordReward( BUILD_PLAN_DEVCARD_SUCCESS );
					build_plan = null;
					update();
				}
			}
		} else
			logger.log( Level.WARNING, "Assigning build plan reward: current known build plan null!" );
		
	}

	public void checkPieces( SOCPlayer player_data ) {
		
		System.out.printf( "Player data before update: %s\n", this.player_data.toString() );
		System.out.printf( "New player data: %s\n", player_data.toString() );

		int bpTp = build_plan.getType();
		int diff = 0;
		if ( bpTp == SOCPossiblePiece.ROAD ) {
			diff = player_data.getNumPieces( SOCPossiblePiece.ROAD ) - this.player_data.getNumPieces( SOCPossiblePiece.ROAD );
			if ( diff > 0 )
				off_sel_mdp.recordReward( BUILD_PLAN_ROAD_SUCCESS );
		} else if ( bpTp == SOCPossiblePiece.SETTLEMENT ) {
			diff = player_data.getNumPieces( SOCPossiblePiece.SETTLEMENT ) - this.player_data.getNumPieces( SOCPossiblePiece.SETTLEMENT );
			if ( diff > 0 )
				off_sel_mdp.recordReward( BUILD_PLAN_SETTLEMENT_SUCCESS );
		} else if ( bpTp == SOCPossiblePiece.CITY ) {
			diff = player_data.getNumPieces( SOCPossiblePiece.CITY ) - this.player_data.getNumPieces( SOCPossiblePiece.CITY );
			if ( diff > 0 )
				off_sel_mdp.recordReward( BUILD_PLAN_SETTLEMENT_SUCCESS );
		} else if ( bpTp == SOCPossiblePiece.CARD ) {
			diff = player_data.getNumPieces( SOCPossiblePiece.CARD ) - this.player_data.getNumPieces( SOCPossiblePiece.CARD );
			if ( diff > 0 )
				off_sel_mdp.recordReward( BUILD_PLAN_SETTLEMENT_SUCCESS );
		}
	}
	
	public void update() {
		num_training_dialogues++;
		off_sel_mdp.update();
		off_resp_mdp.update();
	}
	
	public void computeStatistics() {
		off_sel_mdp.computeStatistics();
		off_resp_mdp.computeStatistics();
	}
	
	public int getNumTrainDials() {
		return num_training_dialogues;
	}
	
	public synchronized void savePolicies( String gname ) {
		if ( MDP.POLICY_OPTIMISATION ) {
			num_games_played++;
			if ( num_games_played % POLICY_SAVING_PERIOD == 0 ) {
				String off_sel_pol_name = gname + "_" + num_games_played + (USE_POLICY_OBJECTS?"_sel.obj":"_sel.pcy");
				String off_resp_pol_name = gname + "_" + num_games_played + (USE_POLICY_OBJECTS?"_resp.obj":"_resp.pcy");
	    		logger.logf( Level.FINEST, "Saving policies (%d games):\n\t%s\n\t%s\n", num_games_played, off_sel_pol_name, off_resp_pol_name );

	    		off_resp_mdp.writePolicy( off_resp_pol_name, USE_POLICY_OBJECTS );
	    		off_sel_mdp.writePolicy( off_sel_pol_name, USE_POLICY_OBJECTS );
			
	    		logger.logf( Level.FINEST, "Finished saving policies (%d games):\n\t%s\n\t%s\n", num_games_played, off_sel_pol_name, off_resp_pol_name );

	    		Policy.applyExplorationDiscount();
			}
		}
	}
	
	/** Compute reward from current state and send it to the policy for processing */
	public void recordReward( SOCResourceSet targRes, SOCResourceSet ourRes ) {
		
		if ( build_plan == null )
			return;
		
		int bpTp = build_plan.getType();
		if ( ourRes.contains(targRes) ) {
			if ( bpTp == SOCPossiblePiece.ROAD ) {
				off_sel_mdp.recordReward( BUILD_PLAN_ROAD_SUCCESS );
				off_resp_mdp.recordReward( BUILD_PLAN_ROAD_SUCCESS );
			} else if ( bpTp == SOCPossiblePiece.SETTLEMENT ) {
				off_sel_mdp.recordReward( BUILD_PLAN_SETTLEMENT_SUCCESS );
				off_resp_mdp.recordReward( BUILD_PLAN_SETTLEMENT_SUCCESS );
			} else if ( bpTp == SOCPossiblePiece.CITY ) {
				off_sel_mdp.recordReward( BUILD_PLAN_SETTLEMENT_SUCCESS );
				off_resp_mdp.recordReward( BUILD_PLAN_SETTLEMENT_SUCCESS );
			} else if ( bpTp == SOCPossiblePiece.CARD ) {
				off_sel_mdp.recordReward( BUILD_PLAN_SETTLEMENT_SUCCESS );
				off_resp_mdp.recordReward( BUILD_PLAN_SETTLEMENT_SUCCESS );
			}
		} else {
			float reward = 0;
			for ( int i = 0; i < NUM_RES_TYPES; i++ ) {
				int target_amount = targRes.getAmount( resourceIndexToConstant(i) );
				int our_amount = ourRes.getAmount( resourceIndexToConstant(i) ); 
				if ( target_amount > 0 && target_amount > our_amount )
					reward += our_amount - target_amount;
			}
			off_sel_mdp.recordReward( reward );
			off_resp_mdp.recordReward( reward );
		}
		
	}
	
	public void recordReward( SOCResourceSet ourRes ) {
		SOCResourceSet targRes = SOCPlayingPiece.getResourcesToBuild( build_plan.getType() );
		recordReward( targRes, ourRes );
	}
	
	/** Records a reward based on the current number of Victory Points:
	 * VPs gained in the current game turn */
	public void recordReward( int vps ) {
		off_sel_mdp.recordReward( vps - numVPs );
		off_resp_mdp.recordReward( vps - numVPs );
	}
	
	private int resourceIndexToConstant( int index ) {
		return index + 1;
	}
		
	private int getResourceSummaryState( int num_res ) {
		return ( num_res <= MAX_NR_RESOURCE ? num_res : MAX_NR_RESOURCE );
	}
	
	private int[] getSummaryState( SOCResourceSet ourRes, SOCResourceSet cost ) {
		int[] features = new int[ NUM_RES_TYPES ];
		for ( int i = 0; i < NUM_RES_TYPES; i++ ) {
			int r = ourRes.getAmount( i+1 );
			int c = cost.getAmount( i+1 );
			//System.out.printf( "STATE: feature %d ours %d cost %d", i, r, c );
			if ( c > 0 ) { // goal resource
				int d = c - r;
				if ( d == 0 )
					features[i] = 4; // goal achieved
				else if ( d < 0 )
					features[i] = 5; // resource available
				else if ( d <= 3 )
					features[i] = d; // number of resources required for goal
				else
					logger.log( Level.SEVERE, "More resources required than expected" );
			} else if ( c == 0 ) { // non-goal resource
				if ( r > 0 )
					features[i] = 5; // resource available
				else if ( r == 0 )
					features[i] = 0; // no resources
			}
			//System.out.printf( " summary %d\n", features[i] );
		}
		return features;
	}
	
	private StacTradeOffer createOffer( int action_index, String game, int pl_ind, SOCResourceSet ourResources ) {

		if ( action_index == 0 ) { // create 'empty' offer: no recipients
			return new StacTradeOffer( game, pl_ind, new boolean[4], new SOCResourceSet(), false, new SOCResourceSet(), false );
		}
		
		off_sel_mdp.recordReward( TRADE_OFFER ); // assign small penalty for each offer made
		
		int offer_index = action_index - 1;
		/*
		int num_offers_res = ( NUM_RES_TYPES - 1 ) * NUM_RES_TYPES;
		int num_asked = offer_index / num_offers_res + 1;
		int off_ask_ind = offer_index % num_offers_res;
		int res_offered = off_ask_ind / ( NUM_RES_TYPES - 1 );
		int res_asked1 = off_ask_ind % ( NUM_RES_TYPES - 1 );
		
//		int res_offered = ( action_index - 1 ) / ( NUM_RES_TYPES - 1 );
//		int res_asked1 = ( action_index - 1 ) % ( NUM_RES_TYPES - 1 );
		int res_asked = getAsked( res_offered, res_asked1 );
		logger.logf( Level.INFO, "policy: ai:%d of:%d numask:%d as1:%d as:%d\n", action_index, res_offered, num_asked, res_asked1, res_asked );
		*/
		
		/*
		int[] give_res = new int[ NUM_RES_TYPES ];
		//give_res[ resourceIndexToConstant(res_offered) ] = 1;
		give_res[ res_offered ] = 1;
//		give_res[ res_offered ] = num_offered;
		 */
		int give_index = offer_index * 2;
		int[] give_res = ( INCLUDE_1FOR2_ACTIONS ? ACTION_SET_1for2[ give_index ] : ACTION_SET[ give_index ] );
		SOCResourceSet give = new SOCResourceSet( give_res );
		
		// check if offer is legal (i.e., if this agent actually has the resources offered)
		if ( !ourResources.contains(give) ) {
			off_sel_mdp.recordReward( ILLEGAL_OFFER );
			illegal_offer_made = true; // might not need this anymore
			logger.logf( Level.INFO, "ILLEGAL_OFFER (ours: %s; offered: %s)\n", ourResources.toFriendlyString(), give.toFriendlyString() );
			if ( LEGAL_OFFERS_ONLY )
				return null;
			    //return new SOCTradeOffer( game, pl_ind, new boolean[4], new SOCResourceSet(), new SOCResourceSet() );
		}
		
		/*
		int[] ask_res = new int[ NUM_RES_TYPES ];
		//ask_res[ resourceIndexToConstant(res_asked) ] = 1;
//		ask_res[ res_asked ] = 1;
		ask_res[ res_asked ] = num_asked;
		*/
		int ask_index = offer_index * 2 + 1;
		//int[] ask_res = ACTION_SET[ ask_index ];
		int[] ask_res = ( INCLUDE_1FOR2_ACTIONS ? ACTION_SET_1for2[ ask_index ] : ACTION_SET[ ask_index ] );
		SOCResourceSet get = new SOCResourceSet( ask_res );
		
		boolean[] to = new boolean[4];
		for ( int i = 0; i < 4; i++ )
			to[i] = true; // offer to all players
		to[pl_ind] = false; // not offering to myself
		
		StacTradeOffer offer = new StacTradeOffer( game, pl_ind, to, give, false, get, false );
		int numAsked = offer.getGetSet().getTotal();
		logger.logf( Level.INFO, "Selected offer (%d res asked): %s\n", numAsked, offer.toString() );
		
		return offer;
	}
	
	/*
	 * Selects resource from index ra, excluding resource corresponding to index ro,
	 * conform the offer encoding for the policy action space.
	 */
	private int getAsked( int ro, int ra ) {
		if ( ra >= ro )
			return ra + 1;
		return ra;
	}

	void loadConfig() {
		// set up logging
		logger = new MyLogger( "soc.robot.stac.negotiationlearning" );
		try {
			String logdirname = "negotiation-learning-logs";
			String logfilename = new SimpleDateFormat( "'learning_'yyyy-MM-dd-'T'HH:mm:ss:SSS'.log'" ).format( new Date() );
			new File( logdirname ).mkdirs();
			fh = new FileHandler( logdirname + "/" + logfilename );
			logger.addHandler( fh );
			logger.setUseParentHandlers( false );
		} catch ( SecurityException e ) {
			e.printStackTrace();
		} catch ( IOException e ) {
			e.printStackTrace();
		}
		// set up learning settings
        //String filename = "resources/config/learning_settings.cfg"; //TODO read filename from jsettlers config file?
    	URL config_url = Resources.class.getResource( learning_config_fname );
        try {
            //InputStream config_is = new FileInputStream( new File(filename) );
            InputStream config_is = config_url.openStream();
            config = new Properties();
            config.load( config_is );
            
            String aux_str = "";
            aux_str = config.getProperty( "PATH_TO_RES" );
            String PATH_TO_RES = aux_str.replaceAll( "\"", "" );

            // load logging level
            aux_str = config.getProperty( "LOGGING_LEVEL" );
    		String level_str = aux_str.replaceAll( "\"", "" );
    		Level newLevel = Level.parse( level_str );
            logger.setLevel( newLevel );
            System.out.printf( "LearningNegotiator> Logging level: %s\n", logger.getLevel().toString() );
            //logger.logf( Level.INFO, "Logging level: %s\n", logger.getLevel().toString() );

            MDP.loadConfig( config );
            aux_str = config.getProperty( "off_sel_pol_filename" );
            off_sel_pol_fname = "";
            if ( aux_str != null ) {
            	String fname = aux_str.replaceFirst( "PATH_TO_RES", PATH_TO_RES );
            	if ( fname != null )
            		off_sel_pol_fname = fname.replaceAll( "\"", "" );
            }
            
            aux_str = config.getProperty( "off_resp_pol_filename" );
            off_resp_pol_fname = "";
            if ( aux_str != null ) {
            	String fname = aux_str.replaceFirst( "PATH_TO_RES", PATH_TO_RES );
            	if ( fname != null )
            		off_resp_pol_fname = fname.replaceAll( "\"", "" );
            }
            
            POLICY_SAVING_PERIOD = Integer.parseInt( config.getProperty("POLICY_SAVING_PERIOD") );
            LEGAL_OFFERS_ONLY = Boolean.parseBoolean( config.getProperty("LEGAL_OFFERS_ONLY") );
            aux_str = config.getProperty( "MDP_MODEL" );
            if ( aux_str != null && !aux_str.isEmpty() ) {
            	String auxStr = aux_str.trim().replaceAll( "\"", "" ); // remove surrounding space and quotes
            	for ( MDP_Models model : MDP_Models.values() )
            		if ( auxStr.equals(model.name()) )
            			MDP_MODEL = model;
            }
            INCLUDE_1FOR2_ACTIONS = Boolean.parseBoolean( config.getProperty("INCLUDE_1FOR2_ACTIONS") );
    		
            StringBuffer logStr = new StringBuffer( "LearningNegotiator settings:\n" );
    		logStr.append( String.format( "   Offer selection policy file: %s\n", off_sel_pol_fname ) );
    		logStr.append( String.format( "   Offer response policy file: %s\n", off_resp_pol_fname ) );
    		logStr.append( String.format( "   POLICY_SAVING_PERIOD: %d\n", POLICY_SAVING_PERIOD ) );
    		logStr.append( String.format( "   LEGAL_OFFERS_ONLY: %s\n", LEGAL_OFFERS_ONLY ) );
    		logStr.append( String.format( "   INCLUDE_1FOR2_ACTIONS: %s\n", INCLUDE_1FOR2_ACTIONS ) );
    		logStr.append( String.format( "   MDP_MODEL: %s\n", MDP_MODEL.name() ) );
    		logger.log( Level.INFO, logStr.toString() );
            
            loadRewardFunction();

        } catch ( FileNotFoundException fnfe ) {
            logger.log( Level.SEVERE, "Configuration file not found: " + learning_config_fname );
        } catch ( NumberFormatException nfe ) {
        	logger.log(Level.SEVERE, "Wrong number format" );
        } catch ( IllegalArgumentException iae ) {
        	System.err.println( "Illegal argument (specified logging level string)" );
        } catch ( IOException ioe ) {
            logger.log( Level.SEVERE, "Could not load configuration from file " + learning_config_fname );
        }
        
	}
	
	private void loadRewardFunction() {
		String rs = config.getProperty( "TRADE_SUCCESS" );
		if ( rs != null ) TRADE_SUCCESS = Float.parseFloat( rs );
		rs = config.getProperty( "TRADE_FAILURE" );
		if ( rs != null ) TRADE_FAILURE = Float.parseFloat( rs );
		rs = config.getProperty( "TRADE_OFFER" );
		if ( rs != null ) TRADE_OFFER = Float.parseFloat( rs );
		rs = config.getProperty( "ILLEGAL_OFFER" );
		if ( rs != null ) ILLEGAL_OFFER = Float.parseFloat( rs );
		rs = config.getProperty( "BUILD_PLAN_ROAD_SUCCESS" );
		if ( rs != null ) BUILD_PLAN_ROAD_SUCCESS = Float.parseFloat( rs );
		rs = config.getProperty( "BUILD_PLAN_DEVCARD_SUCCESS" );
		if ( rs != null ) BUILD_PLAN_DEVCARD_SUCCESS = Float.parseFloat( rs );
		rs = config.getProperty( "BUILD_PLAN_SETTLEMENT_SUCCESS" );
		if ( rs != null ) BUILD_PLAN_SETTLEMENT_SUCCESS = Float.parseFloat( rs );
		rs = config.getProperty( "BUILD_PLAN_CITY_SUCCESS" );
		if ( rs != null ) BUILD_PLAN_CITY_SUCCESS = Float.parseFloat( rs );
		
		StringBuffer logStr = new StringBuffer( "LearningNegotiator reward function:\n" );
		logStr.append( String.format( "   TRADE_SUCCESS: %.0f\n", TRADE_SUCCESS ) );
		logStr.append( String.format( "   TRADE_FAILURE: %.0f\n", TRADE_FAILURE ) );
		logStr.append( String.format( "   TRADE_OFFER: %.0f\n", TRADE_OFFER ) );
		logStr.append( String.format( "   ILLEGAL_OFFER: %.0f\n", ILLEGAL_OFFER ) );
		logStr.append( String.format( "   BUILD_PLAN_ROAD_SUCCESS: %.0f\n", BUILD_PLAN_ROAD_SUCCESS ) );
		logStr.append( String.format( "   BUILD_PLAN_DEVCARD_SUCCESS: %.0f\n", BUILD_PLAN_DEVCARD_SUCCESS ) );
		logStr.append( String.format( "   BUILD_PLAN_SETTLEMENT_SUCCESS: %.0f\n", BUILD_PLAN_SETTLEMENT_SUCCESS ) );
		logStr.append( String.format( "   BUILD_PLAN_SETTLEMENT_SUCCESS: %.0f\n", BUILD_PLAN_SETTLEMENT_SUCCESS ) );
		logger.log( Level.INFO, logStr.toString() );
	}

}
