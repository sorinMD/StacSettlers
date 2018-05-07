package soc.robot.stac.negotiationlearning;

import java.util.logging.Level;

public class PreferenceModel {

	private static MyLogger logger = new MyLogger(""); // AdversaryNegotiator.logger;
	
	/*
	 * Preference modelling:
	 *   OP: opponent preferences
	 *   CT: considered trade (their trade offer or their response to our offer)
	 *   AT: actual trade taken place
	 */
	
	String agent_name;
	int obj_num;
	
	/** probabilities of the opponent requesting each of the resources in the next trade under negotiation */
	double[] preferences;     // P( OP )            ( 5 - 1 )            =   4 parameters
	
	private double[][] considered_trade_model; // P( CT | OP )       ( 5 * 4 - 1 ) * 5    =  95 parameters (75 when ruling out offering desired resource)
	private double[][] pref_transition_model;  // P( OP' | OP, AT )  ( 5 -1 ) * 5 * 20    = 400 parameters (160 when only considering relevant OP-AT combi's)
	
	// auxiliary variables
	private int num_res_types;
	private int num_trades;
	private int num_pref_trade_combis;

	/** Initialise opponent preference model with handcrafted probabilities */
	public PreferenceModel( String agt_nm, int n ) {
		
		agent_name = agt_nm;
		obj_num = n;

		// auxiliary variables
		num_res_types = 5; //LearningNegotiator.NUM_RES_TYPES;
		num_trades = num_res_types * num_res_types; //( num_res_types - 1 );
		num_pref_trade_combis = num_res_types * num_trades;
		double epsilon = 0.001;
		
		// (prior) distribution over resources (opponent preferences)
		preferences = new double[num_res_types];
		for ( int i = 0; i < num_res_types; i++ )
			preferences[i] = (double) 1 / num_res_types; // prior: uniform distribution over resource types
		logger.logf( Level.INFO, "[%d] " + agent_name + "> Prior opponent pref's: %s\n", obj_num, getPrefString() );
		
		// considered trade model
		double lp_ctm1 = 0.02;
		double lp_ctm2 = 0.03;
		double hp_ctm = ( 1 - 4 * ( 3 * lp_ctm2 + lp_ctm1 ) ) / 4;
		considered_trade_model = new double[num_res_types][num_trades];
		for ( int i = 0; i < num_res_types; i++ ) {
			double tot_prob = 0;
			for ( int j = 0; j < num_trades; j++ ) {
				int g = j / num_res_types;
				int r = j % num_res_types;
				if ( g == r )
					considered_trade_model[i][j] = -1; // marked to be ignored
				else {
					if ( r != i ) // receivable does not correspond to preferred resource --> assign lower probability
						if ( g == i ) // giveable corresponds to preferred resource
							considered_trade_model[i][j] = lp_ctm1;
						else
							considered_trade_model[i][j] = lp_ctm2;
					else  // receivable corresponds to preferred resource --> assign higher probability
						if ( g != i ) // giveable corresponds to preferred resource
							considered_trade_model[i][j] = hp_ctm;
					tot_prob += considered_trade_model[i][j];
				}				
			}
			if ( Math.abs(1-tot_prob) > epsilon )
				System.out.printf( "ERROR: considered_trade_model prob'ies don't sum to 1: %.3f\n", tot_prob );
		}
		
		//
		double lp_ptm = 0.18;
		double hp_ptm = ( 1 - lp_ptm ) / 4;
		pref_transition_model = new double[num_pref_trade_combis][num_res_types];
		for ( int k = 0; k < num_pref_trade_combis; k++ ) {
			int d = k / num_trades;
			int t = k % num_trades;
			int g = t / num_res_types;
			int r = t % num_res_types;
			double tot_prob = 0;
			for ( int l = 0; l < num_res_types; l++ ) {
				if ( g == r )
					pref_transition_model[k][l] = -1; // marked to be ignored
				else {
					if ( d != g && d != r ) { // actual trade not relevant to preferred resource
						if ( l == d )
							pref_transition_model[k][l] = 1; // maintain probability of preferred resource after trade
					} else if ( d == r ) { // desired resource received
						if ( l == d )
							pref_transition_model[k][l] = lp_ptm;
						else
							pref_transition_model[k][l] = hp_ptm;
					} else { // if d == g
						if ( l == d )
							pref_transition_model[k][l] = 1; //(double) 1 / num_res_types;
						else
							pref_transition_model[k][l] = 0;
					}
					tot_prob += pref_transition_model[k][l];
				}
			}
			if ( tot_prob > epsilon && Math.abs(1-tot_prob) > epsilon )
				System.out.printf( "ERROR: pref_transition model prob'ies don't sum to 1: %.3f (k:%d,d:%d,g:%d,r:%d)\n", tot_prob, k, d, g, r );
		}
		
	}
	
	public String getPrefString() {
		String result = "";
		for ( int i = 0; i < num_res_types; i++ )
			result += String.format( " %.3f", preferences[i] );
		result += "\n";
		return result;
	}
	
	public void showPreferences() {
		for ( int i = 0; i < num_res_types; i++ )
			System.out.printf( " %.3f", preferences[i] );
		System.out.println();
	}
	
	public void showPreferenceModel() {
		
		System.out.println( "Opponent preferences:" );
		showPreferences();
		
		System.out.println();

		System.out.println( "Considered trade observation model:" );
		for ( int i = 0; i < num_res_types; i++ ) {
			System.out.printf( " %d:", i );
			for ( int j = 0; j < num_trades; j++ ) {
				if ( j % num_res_types == 0 )
					System.out.print( " |" );
				System.out.printf( " %.3f", considered_trade_model[i][j] );
			}
			System.out.println();
		}

		System.out.println();
		
		System.out.println( "Preference transition model:" );
		for ( int k = 0; k < num_pref_trade_combis; k++ ) {
			int d = k / num_trades;
			int t = k % num_trades;
			if ( t == 0 )
				System.out.printf( "--- d:%d ----------------------------------------\n", d );
			System.out.printf( " %d:", k );
			for ( int l = 0; l < num_res_types; l++ )
				System.out.printf( " %.3f", pref_transition_model[k][l] );
			System.out.println();
		}
		
	}
	
	/**
	 * Bayesian update: P( OP | CT=ct(g,r) ) = P( CT=ct(g,r) | OP ) * P( OP )
	 */
	public void observationUpdate( int give, int receive ) {
		observationUpdate( give, receive, false );
	}
	
	public void observationUpdate( int give, int receive, boolean reject ) {
		int trade_index = give * num_res_types + receive;
		double sum = 0;
		for ( int i = 0; i < num_res_types; i++ ) {
			if ( reject )
				preferences[i] = preferences[i] * ( 1 - considered_trade_model[i][trade_index] );
			else
				preferences[i] = preferences[i] * considered_trade_model[i][trade_index];
			sum += preferences[i];
		}
		for ( int i = 0; i < num_res_types; i++ )
			preferences[i] /= sum;
		
		logger.logf( Level.INFO, "[%d] " + agent_name + "> Opponent pref's after obs. update: %s\n", obj_num, getPrefString() );
		//logPreferences();
	}
	
	/**
	 * Transition update: P( OP' | AT=at(g,r) ) = sum_d P( OP' | OP=d, AT=at(g,r) ) * P( OP=d ) 
	 */
	public void transitionUpdate( int give, int receive ) {
		double[] new_preferences = new double[num_res_types];
		int trade_index = give * num_res_types + receive;
		for ( int i = 0; i < num_res_types; i++ ) {
			double sum = 0;
			//int pref_trade_combi_index = i * num_trades + trade_index;
			for ( int j = 0; j < num_res_types; j++ ) {
				int pref_trade_combi_index = j * num_trades + trade_index;
				sum += pref_transition_model[pref_trade_combi_index][i] * preferences[j];
			}
			new_preferences[i] = sum;
		}
		preferences = new_preferences;
		
		logger.logf( Level.INFO, "[%d] " + agent_name + "> Opponent pref's after trans. update: %s\n", obj_num, getPrefString() );
	}
	
	/** Main method for testing preference model */
    public static void main ( String[] args ) {
    	PreferenceModel pref_model = new PreferenceModel( "MyAgent", 0 );
    	//pref_model.showPreferenceModel();
    	///*
    	pref_model.showPreferences();
    	
    	pref_model.observationUpdate( 1, 2 ); // opponent offers ore for sheep
    	pref_model.showPreferences();
    	
    	pref_model.transitionUpdate( 1, 2 ); // accepted opponent offer and made trade
    	pref_model.showPreferences();
    	//*/
    }

}
