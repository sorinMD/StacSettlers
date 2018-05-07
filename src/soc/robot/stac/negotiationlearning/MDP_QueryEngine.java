package soc.robot.stac.negotiationlearning;

import java.io.IOException;
import java.util.logging.FileHandler;

import uk.ac.hw.mdps.MDP;
import uk.ac.hw.mdps.Policy;
import uk.ac.hw.utils.MyLogger;

public class MDP_QueryEngine {

	static MyLogger logger;
	
	static int NUM_RES_TYPES = 5;
	static int MAX_NR_RESOURCE = 3;
	static String[] resourceString = { "CLAY", "ORE", "SHEEP", "WHEAT", "WOOD" };

	static int NUM_STATE_FEATURES = 1 + NUM_RES_TYPES;
	
	static int BUILD_PLAN = 2;
	
	static boolean TEST_OFFERS = true;
	
	static String[] test_queries_city = {
		"2 SHEEP, 3 ORE, 1 WHEAT",
		"1 SHEEP, 3 ORE, 1 WHEAT",
		"1 SHEEP, 3 ORE, 1 WHEAT", "1 WOOD",
		"1 CLAY, 3 ORE, 1 WHEAT",
		"1 CLAY, 2 ORE, 2 WHEAT",
		"1 CLAY, 2 ORE, 2 WHEAT", "1 WOOD",
		"2 SHEEP, 2 ORE, 2 WHEAT",
		"1 WHEAT, 1 WOOD",
		"1 ORE, 1 SHEEP, 2 WHEAT",
		"2 SHEEP, 2 WHEAT"
	};
	
	static String[] test_offers = {
		"SHEEP for WHEAT",
		"CLAY for ORE",
		"SHEEP for ORE"
	};

	public static void main( String[] args ) {
		
		if ( args.length > 2 ) {
			System.out.println( "USAGE: java PolicyQueryingEngine [ <policy-file> [<query>] ]" );
			System.exit( 0 );
		}
		
		loadConfig();
		
		if ( args.length == 0 ) {
			for ( int i = 0; i < test_offers.length; i++ )
				handleOffer( test_offers[i] );
		} else {
			String policy_file = args[0];
			System.out.printf( "Policy: %s\n", policy_file );
			MDP mdp = createPolicy( policy_file );
			//System.out.printf( " ... policy loaded.  Expl.rate: %.2f\n", Policy.exploration_rate );
			if ( args.length == 1 ) {
				if ( TEST_OFFERS )
					for ( int i = 0; i < test_queries_city.length; i++ )
						handleQuery( mdp, test_queries_city[i] );
				else {
					int ai = 0;
//				TODO: adapt to new mdp library...
//						int[] sf = new int[mdp.num_features];
//						System.out.printf( "Number of states: %d\n", mdp.num_states );
//						for ( int i = 0; i < mdp.num_states; i++ ) {
//							if ( mdp.stateVisited(i) ) {
//								sf = mdp.getStateFeatures( i );
//								ai = mdp.getNextActionIndex( sf );
//								int res_offered = getOfferedIndex( ai );
//								System.out.printf( "state: %d = %d %d %d %d %d %d -> action:%d, offered:%d\n", 
//										i, sf[0], sf[1], sf[2], sf[3], sf[4], sf[5], ai, res_offered );
//								if ( sf[res_offered] == 0 )
//									System.out.printf( "ILLEGAL TRADE! (state-action encountered %d times)\n", policy.state_action_visits[i][ai] );
//							}
//						}
				}
			} else
				handleQuery( mdp, args[1] );
		}

	}
	
	private static int getOfferedIndex( int ai ) {
		return ( ai - 1 ) / ( NUM_RES_TYPES - 1 );
	}
	
	private static int getAskedIndex( int ai, int oi ) {
		int res_asked1 = ( ai - 1 ) % ( NUM_RES_TYPES - 1 );
		return getAsked( oi, res_asked1 );
	}
	
	private static void handleQuery( MDP mdp, String query ) {
		System.out.printf( "Query: %s\n", query );
		int[] state_features = getStateFeatures( query );
		int action_index = mdp.getNextActionIndex( state_features );
		if ( action_index == 0 )
			System.out.println( "NO OFFER" );
		else
			System.out.println( "OFFER: " + getTradeOffer( action_index ) );
		System.out.println();
	}

	private static void handleOffer( String offer ) {
		// "SHEEP for WHEAT" -> action index
		// index 3, index 4 -> 1 + 3 * ( 5 - 1 ) + 4 = 16
		System.out.printf( "TRADE: %s\n", offer );
		String sepStr = "for";
		int for_index = offer.indexOf( sepStr );
		String offdStr = offer.substring( 0, for_index ).trim();
		int offdInd = getResourceIndex( offdStr );
		String askdStr = offer.substring( for_index + sepStr.length() ).trim();
		int askdInd = getResourceIndex( askdStr );
		int askdInd_new = getAsked_new( offdInd, askdInd );
		System.out.printf( "\tOFFERED: %s -> %d\n", offdStr, offdInd );
		System.out.printf( "\tASKED: %s -> %d -> %d\n", askdStr, askdInd, askdInd_new );
		int act_ind = 1 + offdInd * ( NUM_RES_TYPES - 1 ) + askdInd_new;
		String tradeOffer = getTradeOffer( act_ind );
		System.out.printf( "ACTION INDEX: %d -> %s\n", act_ind, tradeOffer );
		System.out.println();
	}

	private static int[] getStateFeatures( String query ) {
		int[] state = new int[NUM_STATE_FEATURES];
		state[0] = BUILD_PLAN;
		String[] res_elts = query.split( "," );
		int state_feature_index = -1;
		for ( int i = 0; i < res_elts.length; i++ ) {
			String pair = res_elts[i].trim();
			int sepInd = pair.indexOf( ' ' );
			String quantStr = pair.substring( 0, sepInd ).trim();
			int quant = Integer.parseInt( quantStr );
			String res = pair.substring( sepInd + 1 ).trim();
			state_feature_index = 1 + getResourceIndex( res );
			System.out.printf( "   %d units of %s; state feature index: %d\n", quant, res, state_feature_index );
			if ( state_feature_index >= 0 )
				state[ state_feature_index ] = quant;
		}
		return state;
	}
	
	private static int getResourceIndex( String res_str ) {
		int i = 0;
		while ( i < resourceString.length ) {
			if ( resourceString[i].equalsIgnoreCase(res_str) )
				return i;
			i++;
		}
		return -1;
	}
	
	private static String getTradeOffer( int act_ind ) {
		int res_offered = getOfferedIndex( act_ind );
		int res_asked = getAskedIndex( act_ind, res_offered );
		System.out.printf( "policy: action index: %d resource offered: %d resource asked: %d\n", act_ind, res_offered, res_asked );
		
		String offd = resourceString[ res_offered ];
		String askd = resourceString[ res_asked ];
		String trade_str = "I'm offering 1 " + offd + " for 1 " + askd;
		
		return trade_str;
	}

	private static int getAsked( int ro, int ra ) {
		if ( ra >= ro )
			return ra + 1;
		return ra;
	}
	
	private static int getAsked_new( int ro, int ra ) {
		if ( ra > ro )
			return ra - 1;
		return ra;
	}

	private static MDP createPolicy( String policy_file ) {
		int[] state_dims = new int[NUM_STATE_FEATURES];
		state_dims[0] = 3; // city, settlement, other
		for ( int i = 1; i < NUM_STATE_FEATURES; i++ )
			state_dims[i] = MAX_NR_RESOURCE + 1;
		int num_acts = 1 + NUM_RES_TYPES * ( NUM_RES_TYPES - 1 );
		MDP mdp = new MDP( "", NUM_STATE_FEATURES, state_dims, num_acts );
		Policy.exploration_rate = 0;
		mdp.loadPolicy( policy_file );
		return mdp;
	}

	private static void loadConfig() {
		logger = new MyLogger( "soc.robot.stac.learning" );
		try {
			FileHandler fh = new FileHandler( "target/logs/query_log.txt" );
			logger.addHandler( fh );
		} catch ( SecurityException e ) {
			e.printStackTrace();
		} catch ( IOException e ) {
			e.printStackTrace();
		}
	}
		
}
