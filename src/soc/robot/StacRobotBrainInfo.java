package soc.robot;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCTradeOffer;
import soc.message.SOCCancelBuildRequest;
import soc.robot.stac.SettlementNode;
import soc.robot.stac.StacRobotBrain;
import soc.robot.stac.StacRobotBrainRandom;
import soc.robot.stac.StacRobotType;
import soc.util.CappedQueue;
import soc.util.DebugRecorder;
import soc.util.SOCRobotParameters;

public class StacRobotBrainInfo implements Serializable{
	
    /**
     * Flag for whether or not it is our turn
     */
    public boolean ourTurn;

    /**
     * A counter used to measure passage of time
     */
    public int counter;

    /**
     * This is what we want to build
     */
    public SOCPlayingPiece whatWeWantToBuild;

    /**
     * This is our current building plan, a stack of {@link SOCPossiblePiece}.
     */
    public SOCBuildPlan buildingPlan;

    /**
     * This is what we tried building this turn,
     * but the server said it was an illegal move
     * (due to a bug in our robot).
     * 
     * @see #whatWeWantToBuild
     * @see #failedBuildingAttempts
     */
    public SOCPlayingPiece whatWeFailedToBuild;
    
    /**
     * these are the two resources that we want
     * when we play a discovery dev card
     */
    public SOCResourceSet resourceChoices;

    /**
     * this is the resource we want to monopolize
     */
    public int monopolyChoice;

    /**
     * true if we're done trading
     */
    public boolean doneTrading;

    /**
     * true if our most recent trade offer was accepted
     */
    public boolean tradeAccepted;

    /**
     * the game state before the current one
     */
    public int oldGameState;

    /**
     * keeps track of the last thing we bought for debugging purposes
     */
    public SOCPossiblePiece lastMove;

    /**
     * keeps track of the last thing we wanted for debugging purposes
     */
    public SOCPossiblePiece lastTarget;
	
    /**
     * We're using the number of messages the robot receives as a crude way to keep track of 
     * the time that has elapsed in the current game.
     */
    public int numberOfMessagesReceived;
    
    // Ensure this is updated, and incomes adjusted when we build settlements/cities
    public List<SettlementNode> ourSettlements = new ArrayList<SettlementNode>();
	
	/**
     * true if we're expecting the START1A state
     */
    public boolean expectSTART1A;

    /**
     * true if we're expecting the START1B state
     */
    public boolean expectSTART1B;

    /**
     * true if we're expecting the START2A state
     */
    public boolean expectSTART2A;

    /**
     * true if we're expecting the START2B state
     */
    public boolean expectSTART2B;

    /**
     * true if we're expecting the PLAY state
     */
    public boolean expectPLAY;

    /**
     * true if we're expecting the PLAY1 state
     */
    public boolean expectPLAY1;

    /**
     * true if we're expecting the PLACING_ROAD state
     */
    public boolean expectPLACING_ROAD;

    /**
     * true if we're expecting the PLACING_SETTLEMENT state
     */
    public boolean expectPLACING_SETTLEMENT;

    /**
     * true if we're expecting the PLACING_CITY state
     */
    public boolean expectPLACING_CITY;

    /**
     * true if we're expecting the PLACING_ROBBER state
     */
    public boolean expectPLACING_ROBBER;

    /**
     * true if we're expecting the PLACING_FREE_ROAD1 state
     */
    public boolean expectPLACING_FREE_ROAD1;

    /**
     * true if we're expecting the PLACING_FREE_ROAD2 state
     */
    public boolean expectPLACING_FREE_ROAD2;

    /**
     * true if were expecting a PUTPIECE message after
     * a START1A game state
     */
    public boolean expectPUTPIECE_FROM_START1A;

    /**
     * true if were expecting a PUTPIECE message after
     * a START1B game state
     */
    public boolean expectPUTPIECE_FROM_START1B;

    /**
     * true if were expecting a PUTPIECE message after
     * a START1A game state
     */
    public boolean expectPUTPIECE_FROM_START2A;

    /**
     * true if were expecting a PUTPIECE message after
     * a START1A game state
     */
    public boolean expectPUTPIECE_FROM_START2B;

    /**
     * true if we're expecting a DICERESULT message
     */
    public boolean expectDICERESULT;

    /**
     * true if we're expecting a DISCARDREQUEST message
     */
    public boolean expectDISCARD;

    /**
     * true if we're expecting to have to move the robber
     */
    public boolean expectMOVEROBBER;

    /**
     * true if we're expecting to pick two resources
     */
    public boolean expectWAITING_FOR_DISCOVERY;

    /**
     * true if we're expecting to pick a monopoly
     */
    public boolean expectWAITING_FOR_MONOPOLY;

    // If any new expect or waitingFor fields are added,
    // please update debugPrintBrainStatus().

    /**
     * true if we're waiting for a GAMESTATE message from the server.
     * This is set after a robot action or requested action is sent to server,
     * or just before ending our turn (which also sets waitingForOurTurn == true).
     */
    public boolean waitingForGameState;

    /**
     * true if we're waiting for a TURN message from the server
     * when it's our turn
     */
    public boolean waitingForOurTurn;

    /**
     * true when we're waiting for the results of a trade
     */
    public boolean waitingForTradeMsg;

    /**
     * true when we're waiting to receive a dev card
     */
    public boolean waitingForDevCard;

    /**
     * true when the robber will move because a seven was rolled
     */
    public boolean moveRobberOnSeven;

    /**
     * true if we're waiting for a response to our trade message
     */
    public boolean waitingForTradeResponse; 
    
    /**
     * Try to construct the robot's brain state from the game object
     * @param game 
     * @param ourPn the player number of the robot which is going to update its internal state using this info
     */
    public StacRobotBrainInfo(SOCGame game, int ourPn){
    	//these are by default set to these values
    	buildingPlan = new SOCBuildPlanStack();
        numberOfMessagesReceived = 0;
        counter = 0;
		resourceChoices = new SOCResourceSet();
        resourceChoices.add(2, SOCResourceConstants.CLAY);
        monopolyChoice = SOCResourceConstants.SHEEP;
        //get the old game state
        oldGameState = game.getOldGameState();
        
        //now try to treat specific cases
        if(ourPn != game.getCurrentPlayerNumber()){
	        if(game.getGameState()==SOCGame.PLAY || game.getGameState()==SOCGame.PLAY1 || 
	        		game.getGameState()==SOCGame.WAITING_FOR_DISCOVERY || game.getGameState()==SOCGame.WAITING_FOR_MONOPOLY){
	        	waitingForOurTurn = true;
	        	expectDICERESULT = true;//this field is set to true if this is not the current player, even if the dice were rolled
	        }else if(game.getGameState()==SOCGame.PLACING_ROBBER){
	        	waitingForOurTurn = true;
	        	if(game.getCurrentDice() == 7)
					moveRobberOnSeven= true;
				else
					expectDICERESULT = true;
	        }else if(game.getGameState()==SOCGame.START1A || game.getGameState()==SOCGame.START1B){
	        	if(game.getPlayer(ourPn).getSettlements().size()>0){
	        		//this player has placed the first settlement and/or the first road as the order is START1A-START1B-START2A-START2B
	        		//but it executes the first two for each player, and than it moves to the next pair
	        		expectSTART2A = true;
	        	}
	        }else if(game.getGameState()==SOCGame.START2A){
	        	if(game.getPlayer(ourPn).getSettlements().size()==1){
	        		//this player hasn't placed its second settlement yet
	        		expectSTART2A = true;
	        	}
	        }
        
        //specific flags for the current player only
        }else{
			waitingForOurTurn = false;//make sure this is false!!!
			ourTurn = true;//and this must be true!!!
			if(game.getGameState()==SOCGame.PLAY){
				expectDICERESULT = false;
				expectPLAY = true;
			}else if(game.getGameState()==SOCGame.PLAY1){
				expectDICERESULT = false;
			}else if(game.getGameState()==SOCGame.PLACING_ROBBER){
				expectPLACING_ROBBER = true;
				if(game.getCurrentDice() == 7)
					moveRobberOnSeven= true;
			}else if(game.getGameState()==SOCGame.WAITING_FOR_DISCOVERY){
				expectWAITING_FOR_DISCOVERY=true;
				//also not sure if last target and move should be set to a dev card or this would work as it is no matter what
				lastMove = new SOCPossibleCard(game.getPlayer(ourPn), 0);
				lastTarget = new SOCPossibleCard(game.getPlayer(ourPn), 0);
			}else if(game.getGameState()==SOCGame.WAITING_FOR_MONOPOLY){
				expectWAITING_FOR_MONOPOLY=true;
				//also not sure if last target and move should be set to a dev card or this would work as it is no matter what
				lastMove = new SOCPossibleCard(game.getPlayer(ourPn), 0);
				lastTarget = new SOCPossibleCard(game.getPlayer(ourPn), 0);
			}else if(game.getGameState()==SOCGame.START1A){
				expectSTART1A = true;//it's set to false after calculating where to put the settlement so I assume it is needed
			}else if(game.getGameState()==SOCGame.START2A){
				expectSTART2A = true;//it's set to false after calculating where to put the settlement so I assume it is needed
			}else if(game.getGameState()==SOCGame.START1B){
				expectSTART1B = true;//it's set to false after calculating where to put the settlement so I assume it is needed
	        }else if(game.getGameState()==SOCGame.START2B){
				expectSTART2B = true;//it's set to false after calculating where to put the settlement so I assume it is needed
	        }		
		}
    }
    
    public StacRobotBrainInfo(SOCRobotBrain brain){
    	buildingPlan = brain.buildingPlan;
    	counter = brain.counter;
    	doneTrading = brain.doneTrading;
    	lastMove = brain.lastMove;
    	lastTarget = brain.lastTarget;
    	monopolyChoice = brain.decisionMaker.monopolyChoice;
    	numberOfMessagesReceived = brain.numberOfMessagesReceived;
    	oldGameState = brain.oldGameState;
    	ourTurn = brain.ourTurn;
    	resourceChoices = brain.decisionMaker.resourceChoices;
    	tradeAccepted = brain.tradeAccepted;
    	whatWeFailedToBuild = brain.whatWeFailedToBuild;
    	whatWeWantToBuild = brain.whatWeWantToBuild;
        
    	expectSTART1A = brain.expectSTART1A;
        expectSTART1B = brain.expectSTART1B;
        expectSTART2A = brain.expectSTART2A;
        expectSTART2B = brain.expectSTART2B;
        expectPLAY = brain.expectPLAY;
        expectPLAY1 = brain.expectPLAY1;
        expectPLACING_ROAD = brain.expectPLACING_ROAD;
        expectPLACING_SETTLEMENT = brain.expectPLACING_SETTLEMENT;        
        expectPLACING_CITY = brain.expectPLACING_CITY;
        expectPLACING_ROBBER = brain.expectPLACING_ROBBER;
        expectPLACING_FREE_ROAD1 = brain.expectPLACING_FREE_ROAD1;
        expectPLACING_FREE_ROAD2 = brain.expectPLACING_FREE_ROAD2;
        expectPUTPIECE_FROM_START1A = brain.expectPUTPIECE_FROM_START1A;
        expectPUTPIECE_FROM_START1B = brain.expectPUTPIECE_FROM_START1B;
        expectPUTPIECE_FROM_START2A = brain.expectPUTPIECE_FROM_START2A;
        expectPUTPIECE_FROM_START2B = brain.expectPUTPIECE_FROM_START2B;
        expectDICERESULT = brain.expectDICERESULT;
        expectDISCARD = brain.expectDISCARD;
        expectMOVEROBBER = brain.expectMOVEROBBER;
        expectWAITING_FOR_DISCOVERY = brain.expectWAITING_FOR_DISCOVERY;
        expectWAITING_FOR_MONOPOLY = brain.expectWAITING_FOR_MONOPOLY;
    	
        waitingForGameState = brain.waitingForGameState;
        waitingForOurTurn = brain.waitingForOurTurn;
        waitingForTradeMsg = brain.waitingForTradeMsg;
        waitingForDevCard = brain.waitingForDevCard;
        moveRobberOnSeven = brain.moveRobberOnSeven;
        waitingForTradeResponse = brain.waitingForTradeResponse;
    }
    
	/**
	 * Method for printing out brain state
	 */
    public void debugBrainInfo()
    {
        System.err.println("Brain Status:");
        final String[] s = {
            "ourTurn", "doneTrading",
            "waitingForGameState", "waitingForOurTurn", "waitingForTradeMsg", "waitingForDevCard", "waitingForTradeResponse",
            "moveRobberOnSeven", "expectSTART1A", "expectSTART1B", "expectSTART2A", "expectSTART2B",
            "expectPLAY", "expectPLAY1", "expectPLACING_ROAD", "expectPLACING_SETTLEMENT", "expectPLACING_CITY",
            "expectPLACING_ROBBER", "expectPLACING_FREE_ROAD1", "expectPLACING_FREE_ROAD2",
            "expectPUTPIECE_FROM_START1A", "expectPUTPIECE_FROM_START1B", "expectPUTPIECE_FROM_START2A", "expectPUTPIECE_FROM_START2B",
            "expectDICERESULT", "expectDISCARD", "expectMOVEROBBER", "expectWAITING_FOR_DISCOVERY", "expectWAITING_FOR_MONOPOLY"
        };
        final boolean[] b = {
            ourTurn, doneTrading,
            waitingForGameState, waitingForOurTurn, waitingForTradeMsg, waitingForDevCard, waitingForTradeResponse,
            moveRobberOnSeven, expectSTART1A, expectSTART1B, expectSTART2A, expectSTART2B,
            expectPLAY, expectPLAY1, expectPLACING_ROAD, expectPLACING_SETTLEMENT, expectPLACING_CITY,
            expectPLACING_ROBBER, expectPLACING_FREE_ROAD1, expectPLACING_FREE_ROAD2,
            expectPUTPIECE_FROM_START1A, expectPUTPIECE_FROM_START1B, expectPUTPIECE_FROM_START2A, expectPUTPIECE_FROM_START2B,
            expectDICERESULT, expectDISCARD, expectMOVEROBBER, expectWAITING_FOR_DISCOVERY, expectWAITING_FOR_MONOPOLY
        };
        if (s.length != b.length)
        {
            System.err.println("L745: Internal error: array length");
            return;
        }
        int slen = 0;
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length; ++i)
        {
            if ((slen + s[i].length() + 8) > 79)
            {
                System.err.println(sb.toString());
                slen = 0;
                sb.delete(0, sb.length());
            }
            sb.append("  ");
            sb.append(s[i]);
            sb.append(": ");
            sb.append(b[i]);
            slen = sb.length();
        }
        if (slen > 0)
            System.err.println(sb.toString());
    }
    
    
}
