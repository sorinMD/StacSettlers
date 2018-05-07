package soc.robot;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import soc.client.SOCDisplaylessPlayerClient;
import soc.disableDebug.D;
import soc.game.SOCBoard;
import soc.game.SOCDevCardConstants;
import soc.game.SOCDevCardSet;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCPlayerNumbers;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCSettlement;
import soc.game.SOCTradeOffer;
import soc.message.SOCGameStats;
import soc.message.SOCGameTextMsg;
import soc.message.SOCParseResult;
import soc.message.SOCPlayerElement;
import soc.util.CappedQueue;
import soc.util.CutoffExceededException;
import soc.util.SOCRobotParameters;

/**
 * Default implementation, refactored from SOCRobotBrain
 * @author kho30
 *
 * @param <DM>
 * @param <N>
 */
public class SOCRobotBrainImpl extends SOCRobotBrain<SOCRobotDMImpl, SOCRobotNegotiatorImpl, SOCBuildPlanStack> {

	public SOCRobotBrainImpl(SOCRobotClient rc, SOCRobotParameters params,
			SOCGame ga, CappedQueue mq) {
		super(rc, params, ga, mq); 
	}

	@Override
	protected void handleResources(int action, SOCPlayer player,
			int resourceType, int amount) {
		// Wrapper - don't like how this is done, move the interleaving here and leave our own version free to 
		//  rework as desired
		SOCPlayerElement mes = new SOCPlayerElement(null, player.getPlayerNumber(), action, resourceType, amount);
		SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
        (mes, player, resourceType);
	}

	@Override
	protected SOCRobotDMImpl createDM() {
		return new SOCRobotDMImpl(this, robotParameters.getStrategyType(), buildingPlan);
	}

	@Override
	protected SOCRobotNegotiatorImpl createNegotiator() {
		return new SOCRobotNegotiatorImpl(this);
	}

	@Override
	protected SOCBuildPlanStack createBuildPlan() {
		return new SOCBuildPlanStack();
	}

	@Override
	public void recreateDM() {
		this.decisionMaker = new SOCRobotDMImpl(this, robotParameters.getStrategyType(), buildingPlan);
	}

	@Override
    public SOCBuildingSpeedEstimate getEstimator() {
        return new SOCBuildingSpeedFast();
    }
    
	@Override
    public SOCBuildingSpeedEstimate getEstimator(SOCPlayerNumbers numbers) {
        return new SOCBuildingSpeedFast(numbers);
    }
	
	@Override
    // Default behaviour: ignore all chat messages
    protected void handleChat(SOCGameTextMsg gtm) { }

	@Override
    // Default behaviour: no special action at beginning of turn
    protected void startTurnActions(int player) { }

	@Override
    // Default behaviour: brain never forces waiting
    protected boolean isWaiting() { return false; }

	@Override
    //This is the legacy implementation
    protected boolean isLegacy() { return true; }
	
	@Override
    protected void handleGAMESTATS(SOCGameStats message) { }
    
	@Override
    protected int considerOffer(SOCTradeOffer offer)
    {
        int response = -1;

        SOCPlayer offeringPlayer = game.getPlayer(offer.getFrom());

        if ((offeringPlayer.getCurrentOffer() != null) && (offer == offeringPlayer.getCurrentOffer()))
        {
            boolean[] offeredTo = offer.getTo();

            if (offeredTo[ourPlayerData.getPlayerNumber()])
            {
            	//---MG
            	if (offer.getGiveSet().getTotal() == 0) //check whether it's a partial offer in which what we are getting is unspecified
            	{
            		response = negotiator.handlePartialOffer(offer);
            	}
            	else {            	
            		response = negotiator.considerOffer(offer, ourPlayerData.getPlayerNumber());
            	}
            }
        }

        return response;
    } 
	
	@Override
	//Default behaviour: always end turn as this is the old brain type
	protected boolean endTurnActions(){ return true; }
	
	public void startGameChat(){
            //do nothing; soc robot brain cannot participate in game chat
	}
	
	public void saveGame(){
            //do nothing as we will only use the stac type brain to initiate a save
	}

	@Override
	public void clearTradingFlags(String Txt) {
		waitingForTradeMsg = false;
	}

	@Override
	public void handleGameTxtMsg(SOCGameTextMsg gtm) {
            // nothing as this brain doesn't have a symbolic belief model
	}
        
        @Override
        public void handleParseResult(SOCParseResult mes) {
            //nothing -- this brain does not use the parse results sent by the server
        }
}
