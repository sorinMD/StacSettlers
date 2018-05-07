package soc.robot.stac.negotiationlearning;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import soc.dialogue.StacTradeMessage;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCTradeOffer;
import soc.robot.SOCBuildPlanStack;
import soc.robot.SOCPossiblePiece;

public class AdversaryNegotiator {
	
	private static int COUNT = 0;
	private final int obj_num;
	
	private static boolean one_response = true;
	private static boolean always_reject = false;
	private static boolean always_empty = true; // if true, sets adversary to back off to jsettlers bot when responding to trade message
	
	static MyLogger logger = null;
	private static FileHandler fh;
	
	private static String[] resource_types = { "clay", "ore", "sheep", "wheat", "wood" };
	
	private String name;
	private int number;
	private SOCBuildPlanStack build_plan;
	
	private SOCResourceSet our_resources;
	private SOCTradeOffer last_offer;
	private PreferenceModel opponent_preferences;
		
	public AdversaryNegotiator( String nm, SOCBuildPlanStack buildingPlan ) {
		obj_num = COUNT;
		COUNT++;
		if ( logger == null )
			setup_logging();
		name = nm;
		build_plan = buildingPlan;
		number = -1;
		our_resources = SOCResourceSet.EMPTY_SET;
		last_offer = null;
		opponent_preferences = new PreferenceModel( name, obj_num );
	}
	
	public void setPlayerNameAndNumber( String nm, int playerNum ) {
		name = nm;
		number = playerNum;
		opponent_preferences.agent_name = name;
	}
	
	public void handleGameTxtMsg( String msg ) {
		logger.logf( Level.INFO, "[%d] %s (%d)> handleGameTxtMsg( %s )\n", obj_num, name, number, msg );
		// Example msg: Adversary_2 traded 1 clay for 1 ore from Adversary_1.
		String trade_regex = "(\\S+) traded (\\d) (\\S+) for (\\d) (\\S+) from (\\S+)";
		Pattern trade_patt = Pattern.compile( trade_regex );
		Matcher trade_mat = trade_patt.matcher( msg );
		if ( trade_mat.find() ) {
			String player1 = trade_mat.group( 1 );
			int give_num = Integer.parseInt( trade_mat.group(2) );
			String give_res = trade_mat.group( 3 );
			int give_res_ind = getResourceIndex( give_res );
			int rec_num = Integer.parseInt( trade_mat.group(4) );
			String rec_res = trade_mat.group( 5 );
			int rec_res_ind = getResourceIndex( rec_res );
			String player2 = trade_mat.group( 6 );
			if ( give_num != 1 || rec_num != 1 )
				logger.log( Level.INFO, "WARNING: more than 1 unit of a resource involved!" );
			if ( give_res_ind != -1 && rec_res_ind != -1 ) {
				// update preferences using transition probs (note that we assume 1 for 1 trades only!)
				if ( player1.equals(name) && player2.startsWith("MDP") ) {
					// our offer: reverse give and receive for updating opponent preferences
					logger.log( Level.INFO, "ADV> our trade made with MDP" );
					opponent_preferences.transitionUpdate( rec_res_ind, give_res_ind );
				} else if ( player2.equals(name) && player1.startsWith("MDP") ) {
					// opponent offer: update opponent preferences; no reversal of give and receive needed
					logger.log( Level.INFO, "ADV> MDP trade made with us" );
					opponent_preferences.transitionUpdate( give_res_ind, rec_res_ind );
				}
			}
		}
	}
    
    public String handleTradeMessage( int ourNum, int senderNum, String msg ) { //, boolean msg_from_la ) {
    	
    	logger.logf( Level.INFO, "[%d] %s (%d)> handleTradeMessage( %d, %d, %s )\n", obj_num, name, number, ourNum, senderNum, msg );
    	
    	if ( always_empty )
    		return "";
    	
    	if ( ourNum == -1 )
    		return "";
    	
    	if ( number == -1 ) {
    		System.out.printf( "Setting our player number to %d\n", ourNum );
    		number = ourNum;
    	}
    	
    	// TRD:2,:game=MDP-3ADV_0~from=3~to=false,false,true,false~give=clay=0~ore=0~sheep=0~wheat=1~wood=0~unknown=0~get=clay=0~ore=0~sheep=1~wheat=0~wood=0~unknown=0
    	
		String message = msg.substring( StacTradeMessage.TRADE.length() );
		//System.out.println( "AdvNeg> stripped message: " + message );

		// Determine the start of the actual message and generate the TradeMessage
        int sepIdx = message.indexOf( ':' );
        String recips = message.substring( 0, sepIdx );
        System.out.printf( "%s (%d)> recipients: %s\n", name, number, recips );
        if ( recips.contains( Integer.toString(number) ) ) { // message sent to us
        	
            message = soc.dialogue.StacDialogueManager.fromMessage( message.substring(sepIdx+1) );
            StacTradeMessage tm = StacTradeMessage.parse( message );
            
            SOCTradeOffer offer = tm.getOffer();
            if ( offer != null ) {
            	//System.out.println( "ADV> Processing offer: " + offer.toString() );
            	
            	// updating opponent preferences
            	SOCResourceSet giveables = offer.getGiveSet();
            	SOCResourceSet receivables = offer.getGetSet();
            	int give = getResourceIndex( giveables );
            	int receive = getResourceIndex( receivables );
            	if ( give < 0 || receive < 0 )
            		System.out.println( "WARNING: negative resource index!" );
            	else
            		opponent_preferences.observationUpdate( give, receive );
            	
                // constructing possible responses
            	String resp_prefix = StacTradeMessage.TRADE + offer.getFrom() + ":"; 
            	String rejStr = resp_prefix + StacTradeMessage.REJECT;
            	String accStr = resp_prefix + StacTradeMessage.ACCEPT;
            	
            	if ( one_response ) {
            		if ( always_reject ) {
                    	System.out.println( "returning rejStr: " + rejStr );
            			return rejStr;
            		}
                	System.out.println( "returning accStr: " + accStr );
            		return accStr;
            	}
            	
            	// decide whether to accept or reject this offer
        		if ( !our_resources.contains(receivables) ) // if we don't have the requested resource, reject this offer 
        			return rejStr;
            	if ( build_plan.isEmpty() ) { // no build plan, so we only care about going against our opponent's preferences
            		// accept with probability (1-opponent_preferences[receive])
            		if ( Math.random() >= opponent_preferences.preferences[receive] )
            			return accStr;
            		// reject otherwise
            		return rejStr;
            	}
        		SOCPossiblePiece bp = build_plan.pop();
        		SOCResourceSet req_res = bp.getResourceCost();
        		if ( our_resources.contains(req_res) ) // we already have the resources for the build plan (unlikely to happen)
        			return rejStr;
        		SOCResourceSet needed_res = req_res.copy();
        		needed_res.subtract( our_resources ); // required resources for BP that we don't have 
        		if ( needed_res.contains(giveables) && Math.random() >= opponent_preferences.preferences[receive] ) // offered resource is one we need for BP
        			return accStr;

        		return rejStr;
            	
            } else {
            	if ( last_offer == null ) {
            		System.out.printf( "%s (%d)> WARNING: no last offer\n", name, number );
            		//System.exit( 0 );
            	} else {
	            	SOCResourceSet giveables = last_offer.getGiveSet();
	            	SOCResourceSet receivables = last_offer.getGetSet();
	            	int give = getResourceIndex( receivables ); // reverse give and receive for opponent's perspective
	            	int receive = getResourceIndex( giveables ); //   idem
	            	if ( tm.isAccept() ) {
	            		//System.out.println( "Player accepted our offer ..." );
	            		// updating opponent preferences
	            		opponent_preferences.observationUpdate( give, receive );
	            	} else if ( tm.isReject() ) {
	            		//System.out.println( "Player rejected our offer ..." );
	            		// updating opponent preferences
	            		opponent_preferences.observationUpdate( give, receive, true );
	                } //else if ( tm.isNoResponse() )
//	                	//System.out.println( "No response to our offer from this player ..." );
//	                else
//	                	//System.out.println( "Some other trade message ..." );

            	}
            }
            
            //TODO ...
            
        }

    	return "";

    }
    
    private int getResourceIndex( SOCResourceSet resources ) {
    	int res_ind = SOCResourceConstants.CLAY;
    	while ( res_ind < SOCResourceConstants.UNKNOWN ) {
    		if ( resources.getAmount(res_ind) > 0 )
    			return res_ind - 1;
    		res_ind++;
    	}
		return -1;
	}
    
    private int getResourceIndex( String res ) {
    	for ( int i = 0; i < resource_types.length; i++ )
    		if ( resource_types[i].equals(res) )
    			return i;
    	return -1;
    }

	// NOT called when using setting ChatNegotiations=true !
	public int considerOffer( SOCTradeOffer offer ) {		
		// TODO respond to this offer based on current preferences
		//System.out.println( "AdversaryNegotiator> considering offer (TODO): " + offer.toString() );
		return 0;
	}
	
    public SOCTradeOffer makeOffer( SOCBuildPlanStack bldPl, SOCResourceSet ourResources, String game, int playerNumber ) {
    	//TODO construct offer based on own resources and possibly a preference model
		System.out.printf( "[%d] %s (%d)> make offer (TODO) passed pl.nr.: %d\n", obj_num, name, number, playerNumber );
		
    	SOCTradeOffer offer = null;
    	our_resources = ourResources;
    	//TODO decide on offer
    	// ... currently always returning null, effectively backing off to jsettlers bot 
    	if ( last_offer != null )
    		System.out.printf( "%s (%d)> WARNING: overwriting non-null last offer!\n", name, number );
    	last_offer = offer;
    	return offer;
    }
    
    public void setOffer( SOCTradeOffer offer ) {
    	System.out.printf( "%s (%d)> updating last offer: %s\n", name, number, offer.toString() );
    	last_offer = offer;
    }
    
    public void updateResources( int resourceType, int amount ) {
    	our_resources.setAmount( amount, resourceType );
    }
    
    public static void setup_logging() {
		// set up logging
		logger = new MyLogger( "soc.robot.stac.adversary" );
		try {
			fh = new FileHandler( "logs/adversary_log.txt" );
			logger.addHandler( fh );
		} catch ( SecurityException e ) {
			e.printStackTrace();
		} catch ( IOException e ) {
			e.printStackTrace();
		}
    }

}
