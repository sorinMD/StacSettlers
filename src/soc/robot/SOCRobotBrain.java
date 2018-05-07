/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2011 Jeremy D Monin <jeremy@nand.net>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * The maintainer of this program can be reached at jsettlers@nand.net 
 **/
package soc.robot;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Stack;
import java.util.Vector;

import soc.client.SOCDisplaylessPlayerClient;
import soc.disableDebug.D;
import soc.game.SOCBoard;
import soc.game.SOCCity;
import soc.game.SOCDevCardConstants;
import soc.game.SOCDevCardSet;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCPlayerNumbers;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCRoad;
import soc.game.SOCSettlement;
import soc.game.SOCTradeOffer;
import soc.message.SOCAcceptOffer;
import soc.message.SOCCancelBuildRequest;
import soc.message.SOCChoosePlayerRequest;
import soc.message.SOCClearOffer;
import soc.message.SOCCollectData;
import soc.message.SOCDevCard;
import soc.message.SOCDevCardCount;
import soc.message.SOCDiceResult;
import soc.message.SOCDiscardRequest;
import soc.message.SOCFirstPlayer;
import soc.message.SOCGameState;
import soc.message.SOCGameStats;
import soc.message.SOCGameTextMsg;
import soc.message.SOCMakeOffer;
import soc.message.SOCMessage;
import soc.message.SOCMoveRobber;
import soc.message.SOCParseResult;
import soc.message.SOCPlayerElement;
import soc.message.SOCPotentialSettlements;
import soc.message.SOCPutPiece;
import soc.message.SOCRejectOffer;
import soc.message.SOCResourceCount;
import soc.message.SOCSetPlayedDevCard;
import soc.message.SOCSetTurn;
import soc.message.SOCTurn;
import soc.robot.stac.StacRobotBrain;
import soc.robot.stac.StacRobotDeclarativeMemory;
import soc.robot.stac.simulation.Simulation;
import soc.server.SOCServer;
import soc.server.database.stac.ExtGameStateRow;
import soc.server.database.stac.StacDBHelper;
import soc.util.CappedQueue;
import soc.util.DebugRecorder;
import soc.util.Queue;
import soc.util.SOCRobotParameters;


/**
 * AI for playing Settlers of Catan.
 * Represents a robot player within 1 game.
 * 
 * Refactored partially.  This class should handle message processing and game state
 * tracking, while implementations and DM objects handle actual agent decisions.
 *
 * @author Robert S Thomas
 */
public abstract class SOCRobotBrain<DM extends SOCRobotDM<BP>, N extends SOCRobotNegotiator<BP>, BP extends SOCBuildPlan> extends Thread
{

	int[] stateVector;
	/**
	 * flag for allowing only one save per brain
	 */
	public boolean saved = false;
	
    /**
     * The robot parameters
     */
    protected SOCRobotParameters robotParameters;

    /**
     * Random number generator
     */
    protected Random rand = new Random();

    /**
     * The client we are hooked up to
     * TODO: This should really be private, logic shouldn't depend on this.  Debugging does, though
     */
    protected SOCRobotClient client;

    /**
     * Dummy player for cancelling bad placements
     */
    protected SOCPlayer dummyCancelPlayerData;

    /**
     * The queue of game messages
     */
    protected CappedQueue gameEventQ;
    
    /**
     * the thing that determines what we want to build next
     */
    protected DM decisionMaker;

    /**
     * the thing that determines how we negotiate
     */
    protected N negotiator;
    
    /**
     * a thread that sends ping messages to this one
     */
    protected SOCRobotPinger pinger;

    /**
     * an object for recording debug information that can
     * be accessed interactively
     */
    private DebugRecorder[] dRecorder;

    /**
     * keeps track of which dRecorder is current
     */
    private int currentDRecorder; 
      
    /**
     * our player tracker
     */
    protected SOCPlayerTracker ourPlayerTracker;

    /**
     * trackers for all players (one per player, including this robot)
     */
    protected HashMap<Integer, SOCPlayerTracker> playerTrackers;

    /**
     * This is our current building plan, a stack of {@link SOCPossiblePiece}.
     */
    protected BP buildingPlan;

    /**
     * The game we are playing
     */
    protected SOCGame game;
    
    /**
     * Our player data
     */
    protected SOCPlayer ourPlayerData;  
    
////Fields for controlling the "finite state machine" in the run loop //////   

    /**
     * The {@link #game} we're playing is on the 6-player board.
     * @since 1.1.08
     */
    final protected boolean gameIs6Player;

    /**
     * A counter used to measure passage of time
     */
    protected int counter;

    /**
     * During this turn, which is another player's turn,
     * have we yet decided whether to do the Special Building phase
     * (for the 6-player board)?
     * @since 1.1.08
     */
    protected boolean decidedIfSpecialBuild;

    /**
     * true when we're waiting for our requested Special Building phase
     * (for the 6-player board).
     * @since 1.1.08
     */
    protected boolean waitingForSpecialBuild;

    /**
     * This is what we want to build
     */
    protected SOCPlayingPiece whatWeWantToBuild;

    /**
     * This is what we tried building this turn,
     * but the server said it was an illegal move
     * (due to a bug in our robot).
     * 
     * @see #whatWeWantToBuild
     * @see #failedBuildingAttempts
     */
    protected SOCPlayingPiece whatWeFailedToBuild;

    /**
     * Track how many illegal placement requests we've
     * made this turn.  Avoid infinite turn length, by
     * preventing robot from alternately choosing two
     * wrong things when the server denies a bad build.
     * 
     * @see #whatWeFailedToBuild
     * @see #MAX_DENIED_BUILDING_PER_TURN
     */
    protected int failedBuildingAttempts;
    
    /**
     * If, during a turn, we make this many illegal build
     * requests that the server denies, stop trying.
     * 
     * @see #failedBuildingAttempts
     */
    public static int MAX_DENIED_BUILDING_PER_TURN = 3;
    
    /**
     * flag to check if the brain has received load/save msg
     */
    protected boolean suspended = false;
    
    /**
     * Flag for whether or not we're alive
     */
    protected boolean alive;

    /**
     * Flag for whether or not it is our turn
     */
    protected boolean ourTurn;
    
    /**
     * {@link #pause(int) Pause} for less time;
     * speeds up response in 6-player games.
     * @since 1.1.09
     */
    private boolean pauseFaster;

    // If any new expect or waitingFor fields are added,
    // please update debugPrintBrainStatus().

    /**
     * true if we're expecting the START1A state
     */
    protected boolean expectSTART1A;

    /**
     * true if we're expecting the START1B state
     */
    protected boolean expectSTART1B;

    /**
     * true if we're expecting the START2A state
     */
    protected boolean expectSTART2A;

    /**
     * true if we're expecting the START2B state
     */
    protected boolean expectSTART2B;

    /**
     * true if we're expecting the PLAY state
     */
    protected boolean expectPLAY;

    /**
     * true if we're expecting the PLAY1 state
     */
    protected boolean expectPLAY1;

    /**
     * true if we're expecting the PLACING_ROAD state
     */
    protected boolean expectPLACING_ROAD;

    /**
     * true if we're expecting the PLACING_SETTLEMENT state
     */
    protected boolean expectPLACING_SETTLEMENT;

    /**
     * true if we're expecting the PLACING_CITY state
     */
    protected boolean expectPLACING_CITY;

    /**
     * true if we're expecting the PLACING_ROBBER state
     */
    protected boolean expectPLACING_ROBBER;

    /**
     * true if we're expecting the PLACING_FREE_ROAD1 state
     */
    protected boolean expectPLACING_FREE_ROAD1;

    /**
     * true if we're expecting the PLACING_FREE_ROAD2 state
     */
    protected boolean expectPLACING_FREE_ROAD2;

    /**
     * true if were expecting a PUTPIECE message after
     * a START1A game state
     */
    protected boolean expectPUTPIECE_FROM_START1A;

    /**
     * true if were expecting a PUTPIECE message after
     * a START1B game state
     */
    protected boolean expectPUTPIECE_FROM_START1B;

    /**
     * true if were expecting a PUTPIECE message after
     * a START1A game state
     */
    protected boolean expectPUTPIECE_FROM_START2A;

    /**
     * true if were expecting a PUTPIECE message after
     * a START1A game state
     */
    protected boolean expectPUTPIECE_FROM_START2B;

    /**
     * true if we're expecting a DICERESULT message
     */
    protected boolean expectDICERESULT;

    /**
     * true if we're expecting a DISCARDREQUEST message
     */
    protected boolean expectDISCARD;

    /**
     * true if we're expecting to have to move the robber
     */
    protected boolean expectMOVEROBBER;

    /**
     * true if we're expecting to pick two resources
     */
    protected boolean expectWAITING_FOR_DISCOVERY;

    /**
     * true if we're expecting to pick a monopoly
     */
    protected boolean expectWAITING_FOR_MONOPOLY;

    // If any new expect or waitingFor fields are added,
    // please update debugPrintBrainStatus().

    /**
     * true if we're waiting for a GAMESTATE message from the server.
     * This is set after a robot action or requested action is sent to server,
     * or just before ending our turn (which also sets waitingForOurTurn == true).
     */
    protected boolean waitingForGameState;

    /**
     * true if we're waiting for a TURN message from the server
     * when it's our turn
     */
    protected boolean waitingForOurTurn;

    /**
     * true when we're waiting for the results of a trade
     */
    protected boolean waitingForTradeMsg;

    /**
     * true when we're waiting to receive a dev card
     */
    protected boolean waitingForDevCard;

    /**
     * true when the robber will move because a seven was rolled
     */
    public boolean moveRobberOnSeven;

    /**
     * true if we're waiting for a response to our trade message
     */
    protected boolean waitingForTradeResponse;

    // If any new expect or waitingFor fields are added,
    // please update debugPrintBrainStatus().

    /**
     * true if we're done trading
     */
    protected boolean doneTrading;

    /**
     * true if we are waiting for a trade response from a given player.  if so, we cannot
     *  proceed to further trade offers to that player or other actions, in order to avoid
     *  synchronization conflicts.
     */
    protected boolean[] waitingForTradeResponsePlayer; 
    
    /**
     * true if our most recent trade offer was accepted
     */
    protected boolean tradeAccepted;

    /**
     * the game state before the current one
     */
    protected int oldGameState;

    /**
     * During START states, coordinate of our most recently placed road or settlement.
     * Used to avoid repeats in {@link #cancelWrongPiecePlacement(SOCCancelBuildRequest)}.
     * @since 1.1.09
     */
    private int lastStartingPieceCoord;

    /**
     * During START1B and START2B states, coordinate of the potential settlement node
     * towards which we're building, as calculated by {@link #placeInitRoad()}.
     * Used to avoid repeats in {@link #cancelWrongPiecePlacementLocal(SOCPlayingPiece)}.
     * @since 1.1.09
     */
    private int lastStartingRoadTowardsNode;

    /**
     * keeps track of the last thing we bought for debugging purposes
     */
    protected SOCPossiblePiece lastMove;

    /**
     * keeps track of the last thing we wanted for debugging purposes
     */
    protected SOCPossiblePiece lastTarget;
    
    /**
     * A static variable to control the amount of time to pause at certain points (eg after placing a settlement).  This is done for the benefit of human players, but 
     * may not be required if we are playing robot vs. robot.  Likewise, it may need to be extended for human players - 1.5s isn't a lot of time, and actions are easy
     * to overlook.
     * The default is 500.  However, this is often multiplied by a factor in the code.  
     * The exception for this is when waiting for a trade offer - in that case, a longer delay should occur, whether or not there is a human player.  In the case
     * of partial offers, it may take a second or two to assess.
     * 
     * TODO: This should be refactored properly, but this is a good start for now (architecture makes it tough to do things right!)
     * 
     */
    private static int delayTime = 500;
    public static void setDelayTime(int t) {
    	delayTime = t;
    }
    public static int getDelayTime(){
    	return delayTime;
    }
    
    /**
     * Static variable to turn off trading.  This should be handled in parameters, but the way those are passed is awful as it is, so it's not worth the effort.
     * Trading is the slowest aspect of a robots-only game, as the manner in which robots wait to hear back on trades is broken TODO: Fix trade reply waiting logic.
     * Turning off trades allows us to simulate games more quickly in order to do basic strategy comparison.
     * 
     */
    private static boolean disableTrades = false;
    public static void setTradesEnabled(boolean t) {
    	disableTrades = !t;
    }
    
    /**
     * We're using the number of messages the robot receives as a crude way to keep track of 
     * the time that has elapsed in the current game.
     */
    protected int numberOfMessagesReceived = 0;
    
    /**
     * Create a robot brain to play a game.
     *<P>
     * Depending on {@link SOCGame#getGameOptions() game options},
     * constructor might copy and alter the robot parameters
     * (for example, to clear {@link SOCRobotParameters#getTradeFlag()}).
     *
     * @param rc  the robot client
     * @param params  the robot parameters
     * @param ga  the game we're playing
     * @param mq  the message queue
     */
    public SOCRobotBrain(SOCRobotClient rc, SOCRobotParameters params, SOCGame ga, CappedQueue mq)
    {
        client = rc;
        robotParameters = params.copyIfOptionChanged(ga.getGameOptions());
        game = ga;
        gameIs6Player = (ga.maxPlayers > 4);
        pauseFaster = gameIs6Player;
        gameEventQ = mq;
        numberOfMessagesReceived = 0;
        alive = true;
        counter = 0;
        expectSTART1A = true;
        expectSTART1B = false;
        expectSTART2A = false;
        expectSTART2B = false;
        expectPLAY = false;
        expectPLAY1 = false;
        expectPLACING_ROAD = false;
        expectPLACING_SETTLEMENT = false;
        expectPLACING_CITY = false;
        expectPLACING_ROBBER = false;
        expectPLACING_FREE_ROAD1 = false;
        expectPLACING_FREE_ROAD2 = false;
        expectPUTPIECE_FROM_START1A = false;
        expectPUTPIECE_FROM_START1B = false;
        expectPUTPIECE_FROM_START2A = false;
        expectPUTPIECE_FROM_START2B = false;
        expectDICERESULT = false;
        expectDISCARD = false;
        expectMOVEROBBER = false;
        expectWAITING_FOR_DISCOVERY = false;
        expectWAITING_FOR_MONOPOLY = false;
        ourTurn = false;
        oldGameState = game.getGameState();
        waitingForGameState = false;
        waitingForOurTurn = false;
        waitingForTradeMsg = false;
        waitingForDevCard = false;
        waitingForSpecialBuild = false;
        decidedIfSpecialBuild = false;
        moveRobberOnSeven = false;
        waitingForTradeResponse = false;
        doneTrading = false;
        waitingForTradeResponsePlayer = new boolean[game.maxPlayers];
        for (int i = 0; i < game.maxPlayers; i++)
        {
            waitingForTradeResponsePlayer[i] = false;
        }

        buildingPlan = createBuildPlan();
        pinger = new SOCRobotPinger(gameEventQ, client.getNickname() + "-" + game.getName());
        dRecorder = new DebugRecorder[2];
        dRecorder[0] = new DebugRecorder();
        dRecorder[1] = new DebugRecorder();
        currentDRecorder = 0;
    }

    /**
     * 
     * @return true if brain suspended, false otherwise
     */
    public boolean isSuspended(){
    	return suspended;
    }
    /**
     * @return the robot parameters
     */
    public SOCRobotParameters getRobotParameters()
    {
        return robotParameters;
    }

    /**
     * @return the player client
     */
    public SOCRobotClient getClient()
    {
        return client;
    }

   

    /**
     * A player has sat down and been added to the game,
     * during game formation. Create a PlayerTracker for them.
     *<p>
     * Called when SITDOWN received from server; one SITDOWN is
     * sent for every player, and our robot player might not be the
     * first or last SITDOWN.
     *<p>
     * Since our playerTrackers are initialized when our robot's
     * SITDOWN is received (robotclient calls setOurPlayerData()),
     * and seats may be vacant at that time (because SITDOWN not yet
     * received for those seats), we must add a PlayerTracker for
     * each SITDOWN received after our player's.
     *
     * @param pn Player number
     */
    public void addPlayerTracker(int pn)
    {
        if (null == playerTrackers)
        {
            // SITDOWN hasn't been sent for our own player yet.
            // When it is, playerTrackers will be initialized for
            // each non-vacant player, including pn.

            return;
        }
        if (null == playerTrackers.get(Integer.valueOf(pn)))
        {
            SOCPlayerTracker tracker = new SOCPlayerTracker(game.getPlayer(pn), this);
            playerTrackers.put(Integer.valueOf(pn), tracker);
        }
    }

    /**
     * @return the decision maker
     */
    public DM getDecisionMaker()
    {
        return decisionMaker;
    }

    /**
     * turns the debug recorders on
     */
    public void turnOnDRecorder()
    {
        dRecorder[0].turnOn();
        dRecorder[1].turnOn();
    }

    /**
     * turns the debug recorders off
     */
    public void turnOffDRecorder()
    {
        dRecorder[0].turnOff();
        dRecorder[1].turnOff();
    }

    /**
     * @return the debug recorder
     */
    public DebugRecorder getDRecorder()
    {
        return dRecorder[currentDRecorder];
    }

    /**
     * @return the old debug recorder
     */
    public DebugRecorder getOldDRecorder()
    {
        return dRecorder[(currentDRecorder + 1) % 2];
    }

    /**
     * @return the last move we made
     */
    public SOCPossiblePiece getLastMove()
    {
        return lastMove;
    }

    /**
     * @return our last target piece
     */
    public SOCPossiblePiece getLastTarget()
    {
        return lastTarget;
    }

    /**
     * Find our player data using our nickname
     */
    public void setOurPlayerData()
    {
        ourPlayerData = game.getPlayer(client.getNickname());
        ourPlayerTracker = new SOCPlayerTracker(ourPlayerData, this);
        int opn = ourPlayerData.getPlayerNumber();
        playerTrackers = new HashMap<Integer, SOCPlayerTracker>();
        playerTrackers.put(Integer.valueOf(opn), ourPlayerTracker);

        for (int pn = 0; pn < game.maxPlayers; pn++)
        {
            if ((pn != opn) && ! game.isSeatVacant(pn))
            {
                SOCPlayerTracker tracker = new SOCPlayerTracker(game.getPlayer(pn), this);
                playerTrackers.put(Integer.valueOf(pn), tracker);
            }
        }

        decisionMaker = createDM();
        negotiator = createNegotiator();
        dummyCancelPlayerData = new SOCPlayer(-2, game);

        // Verify expected face (fast or smart robot)
        int faceId;
        switch (getRobotParameters().getStrategyType())
        {
        case SOCRobotDMImpl.SMART_STRATEGY:
            faceId = -1;  // smarter robot face
            break;

        default:
            faceId = 0;   // default robot face
        }
        
        Random r = new Random();
        faceId = r.nextInt(74) - 1; //allow the smarter robot face also
        
        if (ourPlayerData.getFaceId() != faceId)
        {
            ourPlayerData.setFaceId(faceId);
            // robotclient will handle sending it to server
        }
    }

    /**
     * Sets the SOCPlayer object that represents our player data
     * @param pl
     */
    public void setOurPlayerData(SOCPlayer pl){
    	ourPlayerData = pl;
    }
    
    /**
     * Get the number of messages the robot has received, which we are using
     * as a crude way to track the time that has elapsed in this game.
     * @author Markus Guhe
     */
    public int getNumberOfMesagesReceived() {
        return numberOfMessagesReceived;
    }
    
    /**
     * Print brain variables and status for this game to {@link System#err}.
     * Includes all of the expect and waitingFor fields (<tt>expectPLAY</tt>,
     * <tt>waitingForGameState</tt>, etc.)
     * @since 1.1.13
     */
    public void debugPrintBrainStatus()
    {
        System.err.println("Robot internal state: " + client.getNickname() + " in game " + game.getName() + ":");
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

    /**
     * Here is the run method.  Just keep receiving game events
     * and deal with each one.
     * Remember that we're sent a {@link SOCGameTextMsg}(<tt>"*PING*"</tt>) once per second.
     */
    public void run()
    {
        // Thread name for debug
        try
        {
            Thread.currentThread().setName("robotBrain-" + client.getNickname() + "-" + game.getName());
        }
        catch (Throwable th) {}

        if (pinger != null)
        {
            pinger.start();

            try
            {
                /** Our player number */
                final int ourPN = ourPlayerData.getPlayerNumber();

                boolean hasStartedTurn = true; // Variable to track whether turn-start actions have been taken
                
                //
                // Along with actual game events, the pinger sends a SOCGameTextMsg
                // once per second, to aid the robot's timekeeping counter.
                //

                while (alive)
                {
                	if(Thread.currentThread().isInterrupted())
                		break; //exit the while loop so we can clean this thread
                	
                    SOCMessage mes;

                    //if (!gameEventQ.empty()) {
                    mes = (SOCMessage) gameEventQ.get();  // Sleeps until message received

                    //} else {
                    //mes = null;
                    //}
                    final int mesType;
                    
                    
                    if (mes != null)
                    {
                        mesType = mes.getType();
                        numberOfMessagesReceived++; // this is our crude internal clock
                        //D.ebugPrintln("mes - " + mes); //---MG
                        //---MG
//                        if (mesType != SOCMessage.GAMETEXTMSG) {
//                        	D.ebugPrintln("mes - " + mes);
//                        }
                        // Debug aid: when looking at message contents: avoid pings:
                        // check here for (mesType != SOCMessage.GAMETEXTMSG).
                    }
                    else
                    {
                        mesType = -1;
                    }
                    
                    if(mesType==SOCMessage.COLLECTDATA){
                    	//check the brain is of the correct type and if the message is for us
                    	if(this.getClass().getName().equals(StacRobotBrain.class.getName()) && ((SOCCollectData) mes).getPlayerNumber() == ourPN){
                    		StacRobotBrain br = (StacRobotBrain) this;
                    		if(Simulation.dbh!= null && Simulation.dbh.isConnected()){// !!!
                    			//active game with at least one of our piece on the board and our turn
		                    	if(game.getGameState()>=SOCGame.START1A && game.getGameState()<=SOCGame.OVER 
		                    			&& game.getPlayer(ourPN).getPieces().size()>0 && game.getCurrentPlayerNumber()==ourPN){
		                    		//check if vector has changed since last computed to avoid duplicates
		                    		int[] vector = br.createStateVector();
		                    		if(stateVector==null || !Arrays.equals(stateVector, vector)){
		                    			Simulation.writeToDb(vector, br.calculateStateValue());
		                    			stateVector = vector;
		                    		}
		                    	}
	                    	}
                    	}
                    	continue;//do not go through all the logic below as this message does not influence the game (or should it be treated as a ping ?)
                    }
                    
                    //this should work as a brain suspend mechanism when loading/saving
                    if(mesType == SOCMessage.LOADGAME || mesType == SOCMessage.GAMECOPY){
                    	//wait for the loading/saving to finish before carrying on
                    	suspended = true;
                    	while(suspended){
                    		Thread.sleep(10);//sleep for a while
                    	}
                    	//now just follow the below logic as if the two msgs were just a simple ping msg
                    }
                    
                    if (waitingForTradeMsg && (counter > 10))
                    {
                    	/*
                    	 * NOTE: there is a weird timing issue with the two parameters for waiting trade response and waiting trade 
                    	 * confirmation as well as the waiting fields in the StacDialogue manager, therefore we only reset this field
                    	 * if the trades are via the old trade interface.
                    	 */
                    	if(this.getClass() != StacRobotBrain.class && !StacRobotBrain.isChatNegotiation()){
                    		waitingForTradeMsg = false;
                    	}

                        counter = 0;
                    }

                    if (waitingForTradeResponse && (counter > 100))
                    {
                        // Remember other players' responses, call client.clearOffer,
                        // clear waitingForTradeResponse and counter.
                        
                    	tradeStopWaitingClearOffer();
                    }

                    if (waitingForGameState && (counter > 10000))
                    {
                        //D.ebugPrintln("counter = "+counter);
                        //D.ebugPrintln("RESEND");
                        counter = 0;
                        client.resend();
                    }

                    if (mesType == SOCMessage.GAMESTATE)
                    {
                        waitingForGameState = false;
                        oldGameState = game.getGameState();
                        int newState = ((SOCGameState) mes).getState();
                        // Special handling for legacy state update.  Allow legacy agents to 
                        //  treat this as they originally did, so we can contrast performance.  Non-legacy
                        //  agents should ignore this game state.
                        if (newState == SOCGame.PLAY1_LEGACY){
                            if (isLegacy()) {
                                game.setGameState(SOCGame.PLAY1);                                
                            }
                        }
                        else {
                            game.setGameState(newState);
                        }
                        if (game.getGameState() == SOCGame.PLAY) {
                            // probably need to restrict - currently will call this after every action within a turn.  Set a flag when TURN is issued, unset here
                            hasStartedTurn = false;                            
                        }
                        else if (hasStartedTurn == false && game.getGameState() == SOCGame.PLAY1){
                            startTurnActions(game.getCurrentPlayerNumber());
                            hasStartedTurn = true;
                        }
                        handleGAMESTATE((SOCGameState) mes);
                        // depending on type of robot, set to play1 either when you see play1 or play2...
                    }
                    else if (mesType == SOCMessage.FIRSTPLAYER)
                    {
                        game.setFirstPlayer(((SOCFirstPlayer) mes).getPlayerNumber());
                    }

                    else if (mesType == SOCMessage.SETTURN)
                    {
                        game.setCurrentPlayerNumber(((SOCSetTurn) mes).getPlayerNumber());
                    }

                    else if (mesType == SOCMessage.TURN)
                    {
                        game.setCurrentPlayerNumber(((SOCTurn) mes).getPlayerNumber());
                        game.updateAtTurn();

                        //
                        // remove any expected states
                        //
                        expectPLAY = false;
                        expectPLAY1 = false;
                        expectPLACING_ROAD = false;
                        expectPLACING_SETTLEMENT = false;
                        expectPLACING_CITY = false;
                        expectPLACING_ROBBER = false;
                        expectPLACING_FREE_ROAD1 = false;
                        expectPLACING_FREE_ROAD2 = false;
                        expectDICERESULT = false;
                        expectDISCARD = false;
                        expectMOVEROBBER = false;
                        expectWAITING_FOR_DISCOVERY = false;
                        expectWAITING_FOR_MONOPOLY = false;

                        //
                        // reset the selling flags and offers history
                        //
                        if (robotParameters.getTradeFlag() == 1)
                        {
                            doneTrading = false;
                        }
                        else
                        {
                            doneTrading = true;
                        }

                        waitingForTradeMsg = false;
                        waitingForTradeResponse = false;
                        for (int i=0; i<waitingForTradeResponsePlayer.length; i++) {
                            waitingForTradeResponsePlayer[i] = false;
                        }
                        negotiator.resetIsSelling();
                        negotiator.resetOffersMade();
                        negotiator.resetTradesMade();

                        //
                        // check or reset any special-building-phase decisions
                        //
                        decidedIfSpecialBuild = false;
                        if (game.getGameState() == SOCGame.SPECIAL_BUILDING)
                        {
                            if (waitingForSpecialBuild && ! getBuildingPlan().isEmpty())
                            {
                                // Keep the building plan.
                                // Will ask during loop body to build.
                            } else {
                                // We have no plan, but will call planBuilding()
                                // during the loop body.  If buildingPlan still empty,
                                // bottom of loop will end our Special Building turn,
                                // just as it would in gamestate PLAY1.  Otherwise,
                                // will ask to build after planBuilding.
                            }
                        } else {
                            //
                            // reset any plans we had
                            //
                        	resetBuildingPlan();
                        }
                        negotiator.resetTargetPieces();
                    }
                    else if (mesType == SOCMessage.GAMESTATS)
                    {
                        handleGAMESTATS((SOCGameStats) mes);
                    }

                    if (game.getCurrentPlayerNumber() == ourPN)
                    {
                        ourTurn = true;
                        waitingForSpecialBuild = false;
                    }
                    else
                    {
                        ourTurn = false;
                    }

                    if ((mesType == SOCMessage.TURN) && (ourTurn))
                    {
                        //useful for debugging trade interactions
                        //if (ourPlayerData.getName().contains("partial") && ourTurn) {
                        //    System.err.println(numberOfMessagesReceived + ": ** Our Turn ** " + mes.toString());
                        //}

                        waitingForOurTurn = false;

                        // Clear some per-turn variables.
                        // For others, find the code which calls game.updateAtTurn().
                        whatWeFailedToBuild = null;
                        failedBuildingAttempts = 0;
                    }

                    /**
                     * Handle some message types early.
                     */
                    switch (mesType)
                    {
                    case SOCMessage.PLAYERELEMENT:
                        {
                        handlePLAYERELEMENT((SOCPlayerElement) mes);

                        // If this during the PLAY state, also updates the
                        // negotiator's is-selling flags.

                        // If our player is losing a resource needed for the buildingPlan, 
                        // clear the plan if this is for the Special Building Phase (on the 6-player board).
                        // In normal game play, we clear the building plan at the start of each turn.
                        }
                        break;

                    case SOCMessage.RESOURCECOUNT:
                        {
                        SOCPlayer pl = game.getPlayer(((SOCResourceCount) mes).getPlayerNumber());

                        if (((SOCResourceCount) mes).getCount() != pl.getResources().getTotal())
                        {
                            SOCResourceSet rsrcs = pl.getResources();

                            if (D.ebugOn)
                            {
                                client.sendText(game, ">>> RESOURCE COUNT ERROR FOR PLAYER " + pl.getPlayerNumber() + ": " + ((SOCResourceCount) mes).getCount() + " != " + rsrcs.getTotal());
                            }

                            //
                            //  fix it
                            //
                            if (pl.getPlayerNumber() != ourPN)
                            {
                                rsrcs.clear();
                                rsrcs.setAmount(((SOCResourceCount) mes).getCount(), SOCResourceConstants.UNKNOWN);
                            }
                        }
                        }
                        break;

                    case SOCMessage.DICERESULT:
                        handleDICERESULT((SOCDiceResult) mes);
                    	break;

                    case SOCMessage.PUTPIECE:
                        handlePUTPIECE_updateGameData((SOCPutPiece) mes);
                        // For initial roads, also tracks their initial settlement in SOCPlayerTracker.
                        break;

                    case SOCMessage.CANCELBUILDREQUEST:
                        handleCANCELBUILDREQUEST((SOCCancelBuildRequest) mes);
                        break;

                    case SOCMessage.MOVEROBBER:
                        {
                        //
                        // Note: Don't call ga.moveRobber() because that will call the 
                        // functions to do the stealing.  We just want to set where 
                        // the robber moved, without seeing if something was stolen.
                        // MOVEROBBER will be followed by PLAYERELEMENT messages to
                        // report the gain/loss of resources.
                        //
                        handleMOVEROBBER((SOCMoveRobber) mes);
                        }
                        break;

                    case SOCMessage.MAKEOFFER:
                        if (robotParameters.getTradeFlag() == 1) {
                            handleMAKEOFFER((SOCMakeOffer) mes);
                            //useful for debugging trade interactions
                            //if (ourPlayerData.getName().contains("partial") && ourTurn) {
                            //    SOCMakeOffer om = (SOCMakeOffer)mes;
                            //    boolean[] to = om.getOffer().getTo();
                            //    String printString = "From:" + om.getOffer().getFrom() + " To:"; // + to[0] + "|" + to[1] + "|" + to[2] + "|" + to[3];
                            //    if (to[0])
                            //        printString += "T";
                            //    else
                            //        printString += "F";
                            //    for (int i = 1; i < to.length; i++) {
                            //        if (to[i])
                            //            printString += ("," + "T");
                            //        else
                            //            printString += ("," + "F");
                            //    }
                            //    if (om.getOffer().getFrom() == ourPN) {
                            //        //our offer
                            //        printString += (" Give=" + om.getOffer().getGiveSet() + " Get=" + om.getOffer().getGetSet());
                            //        if (om.getOffer().getGiveSet().getTotal() == 0) {
                            //            System.err.println(numberOfMessagesReceived + ": Partl: " + printString); //mes.toString());
                            //        } else {
                            //            System.err.println(numberOfMessagesReceived + ": Offer: " + printString); //mes.toString());
                            //        }
                            //    } else {
                            //        //somebody else's offer
                            //        printString += (" Give=" + om.getOffer().getGetSet() + " Get=" + om.getOffer().getGiveSet());
                            //        System.err.println(numberOfMessagesReceived + ": RetOf: " + printString); //mes.toString());
                            //    }
                            //}
                        }
                        break;

                    case SOCMessage.CLEAROFFER:
                        if (robotParameters.getTradeFlag() == 1)
                        {
                            final int pn = ((SOCClearOffer) mes).getPlayerNumber();
                            if (pn != -1)
                            {
                                game.getPlayer(pn).setCurrentOffer(null);
                            } else {
                                for (int i = 0; i < game.maxPlayers; ++i)
                                    game.getPlayer(i).setCurrentOffer(null);
                            }
//attempt to fix hanging robot after accepting a counteroffer 
//                            clearTradingFlags();
                        }
                        break;

                    case SOCMessage.ACCEPTOFFER:
                        if (waitingForTradeResponse && (robotParameters.getTradeFlag() == 1))
                        {
                            if (((SOCAcceptOffer) mes).getOfferingNumber() == ourPN)
                            {
                                handleTradeResponse(((SOCAcceptOffer) mes).getAcceptingNumber(), true);
                            }
                            //useful for debugging trade interactions
                            //if (ourPlayerData.getName().contains("partial") && ourTurn) {
                            //    System.err.println(numberOfMessagesReceived + ": Accept: " + mes.toString());
                            //}
                        }
                        break;

                    case SOCMessage.REJECTOFFER:
                        if (robotParameters.getTradeFlag() == 1)
                            handleREJECTOFFER((SOCRejectOffer) mes);
                        //useful for debugging trade interactions
                        //if (ourPlayerData.getName().contains("partial") && ourTurn) {
                        //    System.err.println(numberOfMessagesReceived + ": Reject: " + mes.toString());
                        //}
                        break;

                    case SOCMessage.DEVCARDCOUNT:
                        game.setNumDevCards(((SOCDevCardCount) mes).getNumDevCards());
                        break;

                    case SOCMessage.DEVCARD:
                        handleDEVCARD((SOCDevCard) mes);
                        break;

                    case SOCMessage.SETPLAYEDDEVCARD:
                        {
                        SOCPlayer player = game.getPlayer(((SOCSetPlayedDevCard) mes).getPlayerNumber());
                        player.setPlayedDevCard(((SOCSetPlayedDevCard) mes).hasPlayedDevCard());
                        }
                        break;

                    case SOCMessage.POTENTIALSETTLEMENTS:
                        {
                        SOCPlayer player = game.getPlayer(((SOCPotentialSettlements) mes).getPlayerNumber());
                        player.setPotentialSettlements(((SOCPotentialSettlements) mes).getPotentialSettlements());
                        }
                        break;
                    case SOCMessage.GAMETEXTMSG:
                        // Let the brain handle inter-agent text communication (possibly vacuously)
                        SOCGameTextMsg gtm = (SOCGameTextMsg) mes;
                        String sender = gtm.getNickname();
                        // Ignore messages which come from the server, the pinger, or yourself
                        if (!sender.equals("Server")
                                && !sender.equals("*PING*")
                                && !sender.equals(client.getNickname())) {
                            handleChat(gtm);
                        }
                        else {
                        	handleGameTxtMsg(gtm);
                        }
                        break;
                    case SOCMessage.PARSERESULT:
                        handleParseResult((SOCParseResult)mes);
                        break;
                    }  // switch(mesType)

                    debugInfo();

                    if ((game.getGameState() == SOCGame.PLAY) && (!waitingForGameState))
                    {
                        rollOrPlayKnightOrExpectDice();

                        // On our turn, ask client to roll dice or play a knight;
                        // on other turns, update flags to expect dice result.
                        // Clears expectPLAY to false.
                        // Sets either expectDICERESULT, or expectPLACING_ROBBER and waitingForGameState.
                    }

                    if ((game.getGameState() == SOCGame.PLACING_ROBBER) && (!waitingForGameState))
                    {
                        

                        if ((!waitingForOurTurn) && (ourTurn))
                        {
                            if (!((expectPLAY || expectPLAY1) && (counter < 4000)))
                            {
                            	//MOVEROBBER: save
//                            	if(!saved){
//                            		saveGame();
//                            	}
                            	moveRobber(); //moving this here from the end of the method shouldn't modify the logic in any way, but will help us save the correct brain state
                                if (moveRobberOnSeven == true)
                                {
                                    moveRobberOnSeven = false;
                                    waitingForGameState = true;
                                    counter = 0;
                                    expectPLAY1 = true;
                                }
                                else
                                {
                                    waitingForGameState = true;
                                    counter = 0;

                                    if (oldGameState == SOCGame.PLAY)
                                    {
                                        expectPLAY = true;
                                    }
                                    else if (oldGameState == SOCGame.PLAY1)
                                    {
                                        expectPLAY1 = true;
                                    }
                                }

                                counter = 0;
                                
                            }
                        }
                        expectPLACING_ROBBER = false; //nor moving this here
                    }

                    if ((game.getGameState() == SOCGame.WAITING_FOR_DISCOVERY) && (!waitingForGameState))
                    {
                        if ((!waitingForOurTurn) && (ourTurn))
                        {
                            if (!(expectPLAY1) && (counter < 4000))
                            {
                            	//Choose discovery save
//                            	if(!saved){
//                            		saveGame();
//                            	}
                                waitingForGameState = true;
                                expectPLAY1 = true;
                                counter = 0;
                                client.discoveryPick(game, decisionMaker.resourceChoices);
                                pause(3);
                            }
                        }
                        expectWAITING_FOR_DISCOVERY = false;
                    }

                    if ((game.getGameState() == SOCGame.WAITING_FOR_MONOPOLY) && (!waitingForGameState))
                    {
                        if ((!waitingForOurTurn) && (ourTurn))
                        {
                            if (!(expectPLAY1) && (counter < 4000))
                            {
                            	//Choose monopoly save
//                            	if(!saved){
//                            		saveGame();
//                            	}
                                waitingForGameState = true;
                                expectPLAY1 = true;
                                counter = 0;
                                client.monopolyPick(game, decisionMaker.monopolyChoice);
                                pause(3);
                            }
                        }
                        expectWAITING_FOR_MONOPOLY = false;
                    }

                    if (waitingForTradeMsg && (mesType == SOCMessage.GAMETEXTMSG) && (((SOCGameTextMsg) mes).getNickname().equals(SOCServer.SERVERNAME)))
                    {
                        String text = ((SOCGameTextMsg) mes).getText();
                        //
                        // This might be the trade message we've been waiting for
                        //  Check for both directions of the trade - this could be either us doing a bank trade, or us having accepted a counter-offer.
                        //  NB: It would be preferable to check for the actual appropriate message type, but unfortunately bank trade messages are processed
                        //      prior to the processing of the associated resource exchange.
                        //
                        if (text.startsWith(client.getNickname() + " traded ")
                            || (text.contains(" traded ") && text.endsWith(" from " + client.getNickname() + "."))
                            || text.startsWith(SOCServer.MSG_ILLEGAL_TRADE)
                            || text.startsWith(SOCServer.MSG_REJECTED_TRADE_CONFIRMATION))
                        {
                            clearTradingFlags(text);
                        }
                    }

                    if (waitingForDevCard && (mesType == SOCMessage.GAMETEXTMSG) && (((SOCGameTextMsg) mes).getNickname().equals(SOCServer.SERVERNAME)))
                    {
                        //
                        // This might be the dev card message we've been waiting for
                        //
                        if (((SOCGameTextMsg) mes).getText().equals(client.getNickname() + " bought a development card."))
                        {
                            waitingForDevCard = false;
                        }
                    }

                    if (! isWaiting() && ((game.getGameState() == SOCGame.PLAY1) || (game.getGameState() == SOCGame.SPECIAL_BUILDING))
                        && (!waitingForGameState) && (!waitingForTradeMsg) && (!waitingForTradeResponse) && (!waitingForDevCard)
                        && (!expectPLACING_ROAD) && (!expectPLACING_SETTLEMENT) && (!expectPLACING_CITY) && (!expectPLACING_ROBBER) && (!expectPLACING_FREE_ROAD1) && (!expectPLACING_FREE_ROAD2) && (!expectWAITING_FOR_DISCOVERY) && (!expectWAITING_FOR_MONOPOLY))
                    {
                        // Time to decide to build, or take other normal actions.                        
                        expectPLAY1 = false;

                        // 6-player: check Special Building Phase
                        // during other players' turns.
                        if ((! ourTurn) && waitingForOurTurn && gameIs6Player
                             && (! decidedIfSpecialBuild) && (!expectPLACING_ROBBER))
                        {
                            decidedIfSpecialBuild = true;

                            /**
                             * It's not our turn.  We're not doing anything else right now.
                             * Gamestate has passed PLAY, so we know what resources to expect.
                             * Do we want to Special Build?  Check the same conditions as during our turn.
                             * Make a plan if we don't have one,
                             * and if we haven't given up building
                             * attempts this turn.
                             */

                            if ((getBuildingPlan().isEmpty()) && (ourPlayerData.getResources().getTotal() > 1) && (failedBuildingAttempts < MAX_DENIED_BUILDING_PER_TURN))
                            {
                            	resetBuildingPlan();
                                planBuilding();

                                /*
                                 * planBuilding takes these actions:
                                 *
                                decisionMaker.planStuff(robotParameters.getStrategyType());

                                if (!buildingPlan.empty())
                                {
                                    lastTarget = (SOCPossiblePiece) buildingPlan.peek();
                                    negotiator.setTargetPiece(ourPlayerData.getPlayerNumber(), (SOCPossiblePiece) buildingPlan.peek());
                                }
                                 */
                                BP plan = getBuildingPlan();
                                if ( ! plan.isEmpty())
                                {
                                    // Do we have the resources right now?
                                    final SOCPossiblePiece targetPiece = (SOCPossiblePiece) plan.getPlannedPiece(0);
                                    final SOCResourceSet targetResources = SOCPlayingPiece.getResourcesToBuild(targetPiece.getType());

                                    if ((ourPlayerData.getResources().contains(targetResources)))
                                    {
                                        // Ask server for the Special Building Phase.
                                        // (TODO) if FAST_STRATEGY: Maybe randomly don't ask?
                                        waitingForSpecialBuild = true;
                                        client.buildRequest(game, -1);
                                        pause(1); // TODO: This was 100, which I didn't find prior to refactoring the pause functionality.  Whoops.
                                    }
                                }
                            }
                        }

                        if ((!waitingForOurTurn) && (ourTurn))
                        {
                            if (!(expectPLAY && (counter < 4000)))
                            {
                                counter = 0;

                                //D.ebugPrintln("DOING PLAY1");
                                if (D.ebugOn)
                                {
                                    client.sendText(game, "================================");

                                    // for each player in game:
                                    //    sendText and debug-prn game.getPlayer(i).getResources()
                                    printResources();
                                }

                                // TODO: Game logic starts here...
                                getActionForPLAY1();
                                
                            }
                        }
                    }

                    /**
                     * Placement: Make various putPiece calls; server has told us it's OK to buy them.
                     * Call client.putPiece.
                     * Works when it's our turn and we have an expect flag set
                     * (such as expectPLACING_SETTLEMENT, in these game states:
                     * START1A - START2B
                     * PLACING_SETTLEMENT, PLACING_ROAD, PLACING_CITY
                     * PLACING_FREE_ROAD1, PLACING_FREE_ROAD2
                     */
                    if (! waitingForGameState)
                    {
                        placeIfExpectPlacing();
                    }

                    /**
                     * End of various putPiece placement calls.
                     */

                    /*
                       if (game.getGameState() == SOCGame.OVER) {
                       client.leaveGame(game);
                       alive = false;
                       }
                     */

                    /**
                     * Handle various message types here at bottom of loop.
                     */
                    switch (mesType)
                    {
                    case SOCMessage.SETTURN:
                        game.setCurrentPlayerNumber(((SOCSetTurn) mes).getPlayerNumber());
                        break;

                    case SOCMessage.PUTPIECE:
                        /**
                         * this is for player tracking
                         */
                        handlePUTPIECE_updateTrackers((SOCPutPiece) mes);

                        // For initial placement of our own pieces, also checks
                        // and clears expectPUTPIECE_FROM_START1A,
                        // and sets expectSTART1B, etc.  The final initial putpiece
                        // clears expectPUTPIECE_FROM_START2B and sets expectPLAY.

                        break;

                    case SOCMessage.DICERESULT:
                        if (expectDICERESULT)
                        {
                            expectDICERESULT = false;
    
                            if (((SOCDiceResult) mes).getResult() == 7)
                            {
                                moveRobberOnSeven = true;
    
                                if (ourPlayerData.getResources().getTotal() > 7)
                                    expectDISCARD = true;

                                else if (ourTurn)
                                    expectPLACING_ROBBER = true;
                            }
                            else
                            {
                                expectPLAY1 = true;
                            }
                        }
                        break;

                    case SOCMessage.DISCARDREQUEST:
                    	expectDISCARD = false;

                        /**
                         * If we haven't recently discarded...
                         */

                        //	if (!((expectPLACING_ROBBER || expectPLAY1) &&
                        //	      (counter < 4000))) {
                        if ((game.getCurrentDice() == 7) && (ourTurn))
                        {
                            expectPLACING_ROBBER = true;
                        }
                        else
                        {
                            expectPLAY1 = true;
                        }

                        counter = 0;
                        discard(((SOCDiscardRequest) mes).getNumberOfDiscards());

                        //	}
                        break;

                    case SOCMessage.CHOOSEPLAYERREQUEST:
                        chooseRobberVictim(((SOCChoosePlayerRequest) mes).getChoices());
                        break;

                    case SOCMessage.ROBOTDISMISS:
                        if ((!expectDISCARD) && (!expectPLACING_ROBBER))
                        {
                            client.leaveGame(game, "dismiss msg", false);
                            alive = false;
                        }
                        break;

                    case SOCMessage.GAMETEXTMSG:
                        if (((SOCGameTextMsg) mes).getText().equals("*PING*"))
                        {
                            // Once-per-second message from the pinger thread
                            counter++;
                        }
                        break;

                    }  // switch (mesType) - for some types, at bottom of loop body

                    if (counter > 15000)
                    {
                        // We've been waiting too long, commit suicide.
                    	// TODO: Debug here?
                        client.leaveGame(game, "counter 15000", false);
                        alive = false;
                    }

                    if ((failedBuildingAttempts > (2 * MAX_DENIED_BUILDING_PER_TURN))
                        && game.isInitialPlacement())
                    {
                        // Apparently can't decide where we can initially place:
                        // Leave the game.
                    	// TODO: Debug here?
                        client.leaveGame(game, "failedBuildingAttempts at start", false);
                        alive = false;
                    }

                    /*
                       if (D.ebugOn) {
                       if (mes != null) {
                       debugInfo();
                       D.ebugPrintln("~~~~~~~~~~~~~~~~");
                       }
                       }
                     */
                    yield();
                }
            }catch(InterruptedException e){
            	//do nothing as the clean up is executed after exiting the catch block 
            }
            catch (Throwable e)
            {
                // Ignore errors due to game reset in another thread
                if (alive && ((game == null) || (game.getGameState() != SOCGame.RESET_OLD)))
                {
                    D.ebugPrintlnINFO("*** Robot caught an exception - " + e);
                    System.err.println("*** Robot caught an exception - " + e);
                    e.printStackTrace();
                }
            }
        }
        else
        {
            System.err.println("AGG! NO PINGER!");
        }

        //D.ebugPrintln("STOPPING AND DEALLOCATING");
        gameEventQ = null;
        client.addCleanKill();
        client = null;
        game = null;
        ourPlayerData = null;
        dummyCancelPlayerData = null;
        whatWeWantToBuild = null;
        whatWeFailedToBuild = null;
        ourPlayerTracker = null;
        playerTrackers = null;
        pinger.stopPinger();
        pinger = null;
    }

    /**
     * Plan what to do during PLAY1 game state
     * NOTE: method required for SmartSettlers agent to override
     */
	protected void getActionForPLAY1() {
		/**
         * if we haven't played a dev card yet, and we have a knight, 
         * decide if we should play it for the purpose of acquiring largest army
         */
        if ((game.getGameState() == SOCGame.PLAY1) && ! ourPlayerData.hasPlayedDevCard() && ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT) > 0)
        {         
        	if (decisionMaker.shouldPlayKnightForLA()) {
        		/**
                 * play a knight card
                 */
                expectPLACING_ROBBER = true;
                waitingForGameState = true;
                counter = 0;
                client.playDevCard(game, SOCDevCardConstants.KNIGHT);
                pause(3);
        	}                                   
        }

        /**
         * make a plan if we don't have one,
         * and if we haven't given up building
         * attempts this turn.
         */
        if (!expectPLACING_ROBBER && (getBuildingPlan().isEmpty()) && (ourPlayerData.getResources().getTotal() > 1) && (failedBuildingAttempts < MAX_DENIED_BUILDING_PER_TURN))
        {
        	resetBuildingPlan();
            planBuilding();

            /*
             * planBuilding takes these actions:
             *
            decisionMaker.planStuff(robotParameters.getStrategyType());

            if (!buildingPlan.empty())
            {
                lastTarget = (SOCPossiblePiece) buildingPlan.peek();
                negotiator.setTargetPiece(ourPlayerData.getPlayerNumber(), (SOCPossiblePiece) buildingPlan.peek());
            }
             */
            
            //---MG
            //debugPrintBrainStatus();
        }

        //D.ebugPrintln("DONE PLANNING");
        BP plan = getBuildingPlan();
        if (!expectPLACING_ROBBER && !plan.isEmpty())
        {
            // Time to build something.

            // Either ask to build a piece, or use trading or development
            // cards to get resources to build it.  See javadoc for flags set.
            buildOrGetResourceByTradeOrCard(plan);
        }

        /**
         * see if we're done with our turn
         */
        if (!(isWaiting() || expectPLACING_SETTLEMENT || expectPLACING_FREE_ROAD1 || expectPLACING_FREE_ROAD2 || expectPLACING_ROAD || expectPLACING_CITY || expectWAITING_FOR_DISCOVERY || expectWAITING_FOR_MONOPOLY || expectPLACING_ROBBER || waitingForTradeMsg || waitingForTradeResponse || waitingForDevCard))
        {
            boolean finishTurnNow = endTurnActions();
            if (finishTurnNow) {

                waitingForGameState = true;
                counter = 0;
                expectPLAY = true;
                waitingForOurTurn = true;

                if (robotParameters.getTradeFlag() == 1)
                {
                    doneTrading = false;
                }
                else
                {
                    doneTrading = true;
                }

                //D.ebugPrintln("!!! ENDING TURN !!!");
                negotiator.resetIsSelling();
                negotiator.resetOffersMade();
                negotiator.resetTradesMade();
                resetBuildingPlan();
                negotiator.resetTargetPieces();
                pause(3);
                client.endTurn(game);
            }
        }
		
	}
	/**
     * We need this method to override it in children classes. 
     * It doesn't do anything here
     * @param mes
     */
    protected void handleGAMESTATE(SOCGameState mes) {
    	//Do nothing. This is needed for the children classes
	}
	
	/**
     * We need this method to override it in children classes. 
     * All it does is to set the dice result in the SOCGame object.
     * @param mes
     */
    protected void handleDICERESULT(SOCDiceResult mes) {
    	game.setCurrentDice(mes.getResult());
		
	}
	/**
     * We need this method to override it in children classes. 
     * All it does is to move the robber on the board inside the game object and to reset the moveRobberOnSeven flag.
     * @param mes
     */
    protected void handleMOVEROBBER(SOCMoveRobber mes) {
        moveRobberOnSeven = false;
        game.getBoard().setRobberHex(((SOCMoveRobber) mes).getCoordinates(), true);
		
	}
	/**
     * Stop waiting for responses to a trade offer.
     * Remember other players' responses,
     * Call {@link SOCRobotClient#clearOffer(SOCGame) client.clearOffer},
     * clear {@link #waitingForTradeResponse} and {@link #counter}.
     * @since 1.1.09
     */
    protected void tradeStopWaitingClearOffer()
    {
        ///
        /// record which players said no by not saying anything
        ///
        SOCTradeOffer ourCurrentOffer = ourPlayerData.getCurrentOffer();

        if (ourCurrentOffer != null)
        {
            negotiator.recordResourcesFromNoResponse(ourCurrentOffer);

            pause(3);
            client.clearOffer(game);
            pause(1);
        }

        counter = 0;
        for (int i=0; i<waitingForTradeResponsePlayer.length; i++) {
            waitingForTradeResponsePlayer[i] = false;
        }
        waitingForTradeResponse = false;
    }

    /**
     * If it's our turn and we have an expect flag set
     * (such as {@link #expectPLACING_SETTLEMENT}), then
     * call {@link SOCRobotClient#putPiece(SOCGame, SOCPlayingPiece) client.putPiece}.
     *<P>
     * Looks for one of these game states:
     *<UL>
     * <LI> {@link SOCGame#START1A} - {@link SOCGame#START2B}
     * <LI> {@link SOCGame#PLACING_SETTLEMENT}
     * <LI> {@link SOCGame#PLACING_ROAD}
     * <LI> {@link SOCGame#PLACING_CITY}
     * <LI> {@link SOCGame#PLACING_FREE_ROAD1}
     * <LI> {@link SOCGame#PLACING_FREE_ROAD2}
     *</UL>
     * @since 1.1.09
     */
    protected void placeIfExpectPlacing()
    {
        if (waitingForGameState)
            return;

        switch (game.getGameState())
        {
            case SOCGame.PLACING_SETTLEMENT:
            {
                if ((ourTurn) && (!waitingForOurTurn) && (expectPLACING_SETTLEMENT))
                {
                	//placing settlement save
//                	if(!saved){
//                		saveGame();
//                	}
                    expectPLACING_SETTLEMENT = false;
                    waitingForGameState = true;
                    counter = 0;
                    expectPLAY1 = true;
    
                    //D.ebugPrintln("!!! PUTTING PIECE "+whatWeWantToBuild+" !!!");
                    pause(1);
                    client.putPiece(game, whatWeWantToBuild);
                    pause(2);
                }
            }
            break;

            case SOCGame.PLACING_ROAD:
            {
                if ((ourTurn) && (!waitingForOurTurn) && (expectPLACING_ROAD))
                {
                	//placing road save
//                	if(!saved){
//                		saveGame();
//                	}
                    expectPLACING_ROAD = false;
                    waitingForGameState = true;
                    counter = 0;
                    expectPLAY1 = true;

                    pause(1);
                    client.putPiece(game, whatWeWantToBuild);
                    pause(2);
                }
            }
            break;

            case SOCGame.PLACING_CITY:
            {
                if ((ourTurn) && (!waitingForOurTurn) && (expectPLACING_CITY))
                {
                	//placing city save
//                	if(!saved){
//                		saveGame();
//                	}
                    expectPLACING_CITY = false;
                    waitingForGameState = true;
                    counter = 0;
                    expectPLAY1 = true;

                    pause(1);
                    client.putPiece(game, whatWeWantToBuild);
                    pause(2);
                }
            }
            break;

            case SOCGame.PLACING_FREE_ROAD1:
            {
                if ((ourTurn) && (!waitingForOurTurn) && (expectPLACING_FREE_ROAD1))
                {
                	//placing first free road save
//                	if(!saved){
//                		saveGame();
//                	}
                    expectPLACING_FREE_ROAD1 = false;
                    waitingForGameState = true;
                    counter = 0;
                    expectPLACING_FREE_ROAD2 = true;
                    // D.ebugPrintln("!!! PUTTING PIECE 1 " + whatWeWantToBuild + " !!!");
                    pause(1);
                    client.putPiece(game, whatWeWantToBuild);
                    pause(2);
                }
            }
            break;

            case SOCGame.PLACING_FREE_ROAD2:
            {
                if ((ourTurn) && (!waitingForOurTurn) && (expectPLACING_FREE_ROAD2))
                {
                	//placing second free road save
//                	if(!saved){
//                		saveGame();
//                	}
                    expectPLACING_FREE_ROAD2 = false;
                    waitingForGameState = true;
                    counter = 0;
                    expectPLAY1 = true;
    
                    BP plan = getBuildingPlan(); //TODO: this is dangerous as we expect the plan not to be empty, but this may change in memory over time 
                    SOCPossiblePiece posPiece = plan.getPlannedPiece(0); 
                    plan.advancePlan();
                    //(SOCPossiblePiece) buildingPlan.pop();
    
                    if (posPiece.getType() == SOCPossiblePiece.ROAD)
                    {
                        // D.ebugPrintln("posPiece = " + posPiece);
                        whatWeWantToBuild = new SOCRoad(ourPlayerData, posPiece.getCoordinates(), null);
                        // D.ebugPrintln("$ POPPED OFF");
                        // D.ebugPrintln("!!! PUTTING PIECE 2 " + whatWeWantToBuild + " !!!");
                        pause(1);
                        client.putPiece(game, whatWeWantToBuild);
                        pause(2);
                    }
                }
            }
            break;
            
            //same as with placing robber: do the action first and then change states for the following cases
            case SOCGame.START1A:
            {	
                if ((!waitingForOurTurn) && (ourTurn) && (!(expectPUTPIECE_FROM_START1A && (counter < 4000))))
                {
                	//START1A: save
//                	if(!saved){
//                		saveGame();
//                	}
                    decisionMaker.planInitialSettlements();
                    placeFirstSettlement();
                    expectPUTPIECE_FROM_START1A = true;
                    waitingForGameState = true;
                    counter = 0;
                }
                expectSTART1A = false;
            }
            break;

            case SOCGame.START1B:
            {
    
                if ((!waitingForOurTurn) && (ourTurn) && (!(expectPUTPIECE_FROM_START1B && (counter < 4000))))
                {
                	//START1B: save
//                	if(!saved){
//                		saveGame();
//                	}
                    placeInitRoad();
                    expectPUTPIECE_FROM_START1B = true;
                    counter = 0;
                    waitingForGameState = true;
                    pause(3);
                }
                expectSTART1B = false;
            }
            break;

            case SOCGame.START2A:
            {
                if ((!waitingForOurTurn) && (ourTurn) && (!(expectPUTPIECE_FROM_START2A && (counter < 4000))))
                {
                	//START2A: save
//                	if(!saved){
//            			saveGame();
//                	}
                	decisionMaker.planSecondSettlement();
                    placeSecondSettlement();
                    expectPUTPIECE_FROM_START2A = true;
                    counter = 0;
                    waitingForGameState = true;
                }
                expectSTART2A = false;
            }
            break;

            case SOCGame.START2B:
            {
                if ((!waitingForOurTurn) && (ourTurn) && (!(expectPUTPIECE_FROM_START2B && (counter < 4000))))
                {
                	//START2B: save
//                	if(!saved){
//                		saveGame();
//                	}
                	placeInitRoad();
                    expectPUTPIECE_FROM_START2B = true;
                    counter = 0;
                    waitingForGameState = true;
                    pause(3);
                }
                expectSTART2B = false;
            }
            break;

        }
    }

    /**
     * On our turn, ask client to roll dice or play a knight;
     * on other turns, update flags to expect dice result.
     *<P>
     * Call when gameState {@link SOCGame#PLAY} && ! {@link #waitingForGameState}.
     *<P>
     * Clears {@link #expectPLAY} to false.
     * Sets either {@link #expectDICERESULT}, or {@link #expectPLACING_ROBBER} and {@link #waitingForGameState}.
     *
     * @since 1.1.08
     */
    protected void rollOrPlayKnightOrExpectDice()
    {
        expectPLAY = false;

        if ((!waitingForOurTurn) && (ourTurn))
        {
            if (!expectPLAY1 && !expectDISCARD && !expectPLACING_ROBBER && !(expectDICERESULT && (counter < 4000)))
            {
            	
            	//collect data at each turn start, before making the first action of rolling or playing knight
            	if(this.getClass().getName().equals(StacRobotBrain.class.getName())){
            		StacRobotBrain br = (StacRobotBrain) this;
            		if(Simulation.dbh!= null && Simulation.dbh.isConnected()){
            			//active game with at least one of our piece on the board and our turn
                    	if(game.getGameState()>=SOCGame.START1A && game.getGameState()<=SOCGame.OVER 
                    			&& game.getPlayer(ourPlayerData.getPlayerNumber()).getPieces().size()>0 && game.getCurrentPlayerNumber()==ourPlayerData.getPlayerNumber()){
                    		//check if vector has changed since last computed to avoid duplicates
                    		int[] vector = br.createStateVector();
                    		if(stateVector==null || !Arrays.equals(stateVector, vector)){
                    			Simulation.writeToDb(vector, br.calculateStateValue());
                    			stateVector = vector;
                    		}
                    	}
                	}
            	}
            	
                /**
                 * if we have a knight card and the robber
                 * is on one of our numbers, play the knight card
                 */
            	if (ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT) > 0 && decisionMaker.shouldPlayKnight(false)) {            	
                	//PLAYB save, but this robot assumes we should play a knight and we have one
//                	if(!saved){
//                		expectPLAY = true;
//                		saveGame();
//                		expectPLAY = false;
//                	}
            		// TODO: I assume the hasplayedcard flag is set when we handle the message?
            		expectPLACING_ROBBER = true;
                    waitingForGameState = true;
                    counter = 0;
                    client.playDevCard(game, SOCDevCardConstants.KNIGHT);
                    pause(3);
                }
                else
                {
                	//PLAYA save
//                	if(!saved){
//                		expectPLAY = true;
//                		saveGame();
//                		expectPLAY = false;
//                	}
                    expectDICERESULT = true;
                    counter = 0;

                    //D.ebugPrintln("!!! ROLLING DICE !!!");
                    client.rollDice(game);
                }
            }
        }
        else
        {
            /**
             * not our turn
             */
            expectDICERESULT = true;
        }
    }
    

    /**
     * Either ask to build a piece, or use trading or development cards to get resources to build it.
     * Examines {@link #buildingPlan} for the next piece wanted.
     *<P>
     * Call when these conditions are all true:
     * <UL>
     *<LI> gameState {@link SOCGame#PLAY1} or {@link SOCGame#SPECIAL_BUILDING}
     *<LI> <tt>waitingFor...</tt> flags all false ({@link #waitingForGameState}, etc) except possibly {@link #waitingForSpecialBuild}
     *<LI> <tt>expect...</tt> flags all false ({@link #expectPLACING_ROAD}, etc)
     *<LI> ! {@link #waitingForOurTurn}
     *<LI> {@link #ourTurn}
     *<LI> ! ({@link #expectPLAY} && (counter < 4000))
     *<LI> ! {@link #buildingPlan}.empty()
     *</UL>
     *<P>
     * May set any of these flags:
     * <UL>
     *<LI> {@link #waitingForGameState}, and {@link #expectWAITING_FOR_DISCOVERY} or {@link #expectWAITING_FOR_MONOPOLY}
     *<LI> {@link #waitingForTradeMsg} or {@link #waitingForTradeResponse} or {@link #doneTrading}
     *<LI> {@link #waitingForDevCard}, or {@link #waitingForGameState} and {@link #expectPLACING_SETTLEMENT} (etc).
     *</UL>
     * @param plan the building plan to follow when deciding, as this may change in the memory and is risky to call it repeatedly
     *
     * @since 1.1.08
     */
    protected void buildOrGetResourceByTradeOrCard(BP plan)
    {
    	//PLAY1 save
//    	if(!saved){
//    		saveGame();
//    	}
        D.ebugPrintlnINFO("TRY BUILDING/TRADING - " + ourPlayerData.getName() + " -- plan: " + plan);
        /**
         * If we're in SPECIAL_BUILDING (not PLAY1),
         * can't trade or play development cards.
         */
        final boolean gameStatePLAY1 = (game.getGameState() == SOCGame.PLAY1);

        /**
         * check to see if this is a Road Building plan
         */
        boolean roadBuildingPlan = false;

        if (gameStatePLAY1 && (! ourPlayerData.hasPlayedDevCard()) && (ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) >= 2) && (ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.ROADS) > 0))
        {
        	// TODO: Move isRoadBuildingPlan into DM class.  The existing implementation is iffy because it assumes
        	//  the road building involves the first two steps - it may be that we want to build a road to allow a settlement,
        	//  and then another road to build towards our next settlement.  It may also be the case that it's worth playing the RB#
        	//  card even though we only really need one road - a most extreme example is where we only need one road to 
        	//  take LR and win the game - current implementation won't consider using a road building card for that.
        	// This would also allow us to rework the building plan interface, as this is the only place that looks ahead in the stack 
        	//  with the current implementation.
        	
            //D.ebugPrintln("** Checking for Road Building Plan **");
            SOCPossiblePiece topPiece = (SOCPossiblePiece) plan.getPlannedPiece(0); 
            // NB: In lieu of a pop with a conditional push, I use a peek with a conditional pop

            //D.ebugPrintln("$ POPPED "+topPiece);
            if ((topPiece != null) && (topPiece.getType() == SOCPossiblePiece.ROAD) && plan.getPlanDepth()>1)
            {
                SOCPossiblePiece secondPiece = (SOCPossiblePiece) plan.getPlannedPiece(1);

                //D.ebugPrintln("secondPiece="+secondPiece);
                if ((secondPiece != null) && (secondPiece.getType() == SOCPossiblePiece.ROAD))
                {
                    roadBuildingPlan = true;
                    whatWeWantToBuild = new SOCRoad(ourPlayerData, topPiece.getCoordinates(), null);
                    if (! whatWeWantToBuild.equals(whatWeFailedToBuild))
                    {
                    	if (decisionMaker.shouldPlayRoadbuilding()) {
	                        waitingForGameState = true;
	                        counter = 0;
	                        expectPLACING_FREE_ROAD1 = true;
	
	                        //D.ebugPrintln("!! PLAYING ROAD BUILDING CARD");
	                        client.playDevCard(game, SOCDevCardConstants.ROADS);
                    	}
                    } else {
                        // We already tried to build this.
                        roadBuildingPlan = false;
                        cancelWrongPiecePlacementLocal(whatWeWantToBuild);
                        // cancel sets whatWeWantToBuild = null;
                    }
                    plan.advancePlan();
                }
            }
        }

        if (! roadBuildingPlan)
        {
            // Defer this - we want the ability for the negotiator to change plans on the fly based on results of trading
            //SOCPossiblePiece targetPiece = (SOCPossiblePiece) buildingPlan.getPlannedPiece(0);
            //SOCResourceSet targetResources = SOCPlayingPiece.getResourcesToBuild(targetPiece.getType());

            //D.ebugPrintln("^^^ targetPiece = "+targetPiece);
            //D.ebugPrintln("^^^ ourResources = "+ourPlayerData.getResources());

            negotiator.setTargetPiece(ourPlayerData.getPlayerNumber(), plan);

            ///
            /// if we have a 2 free resources card and we need
            /// at least 2 resources, play the card
            ///
            if (gameStatePLAY1 && (! ourPlayerData.hasPlayedDevCard()) && (ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.DISC) > 0) && decisionMaker.shouldPlayDiscovery())
            {                
                decisionMaker.chooseFreeResources(plan);

                ///
                /// play the card
                ///
                expectWAITING_FOR_DISCOVERY = true;
                waitingForGameState = true;
                counter = 0;
                client.playDevCard(game, SOCDevCardConstants.DISC);
                pause(3);                
            }

            if (!expectWAITING_FOR_DISCOVERY)
            {
                ///
                /// if we have a monopoly card, play it
                /// and take what there is most of
                ///
                if (gameStatePLAY1 && (! ourPlayerData.hasPlayedDevCard()) && (ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.MONO) > 0) && decisionMaker.chooseMonopoly())
                {
                    ///
                    /// play the card
                    ///
                	// TODO: Consider the possibility of making trades and then playing monopoly (eg offer a 2 for 1 and then grab it back)
                    expectWAITING_FOR_MONOPOLY = true;
                    waitingForGameState = true;
                    counter = 0;
                    client.playDevCard(game, SOCDevCardConstants.MONO);
                    pause(3);
                }

                if (!expectWAITING_FOR_MONOPOLY)
                {
                    if (gameStatePLAY1 && (!doneTrading)) { 
                    //  Remove this condition - we may want to trade regardless of our current ability 
                    //  to build.  Default implementation checks this anyway
                    // && (!ourPlayerData.getResources().contains(targetResources)))
                    
                        waitingForTradeResponse = false;

                        if (robotParameters.getTradeFlag() == 1
                        		&& !disableTrades)
                        {
                            D.ebugPrintlnINFO("TRY OFFERING");
                            makeOffer(plan);
                            // makeOffer will set waitingForTradeResponse or doneTrading.
                            // NB: With some implementations, makeOffer may instruct the brain to wait
                            // In that case, return from this function
                            if (isWaiting() ) {
                                return;
                            }
                        }
                    }

                    if (!isWaiting() && gameStatePLAY1 && !waitingForTradeResponse && !waitingForTradeMsg)
                    {
                        /**
                         * trade with the bank/ports
                         */
                        D.ebugPrintlnINFO("TRY BANK TRADE");
                        if (tradeWithBank(plan))
                        {
                            counter = 0;
                            waitingForTradeMsg = true;
                            pause(3);
                        }
                    }

                    ///
                    /// build if we can
                    ///
                    SOCPossiblePiece targetPiece = (SOCPossiblePiece) plan.getPlannedPiece(0);
                    SOCResourceSet targetResources = SOCPlayingPiece.getResourcesToBuild(targetPiece.getType());
                    if (!isWaiting() && !waitingForTradeMsg && !waitingForTradeResponse && ourPlayerData.getResources().contains(targetResources))
                    {
                        // Calls buildingPlan.pop().
                        // Checks against whatWeFailedToBuild to see if server has rejected this already.
                        // Calls client.buyDevCard or client.buildRequest.
                        // Sets waitingForDevCard, or waitingForGameState and expectPLACING_SETTLEMENT (etc).

                        D.ebugPrintlnINFO("MAKE BUILD REQUEST - " + targetPiece);
                        buildRequestPlannedPiece(targetPiece, plan);
                    }
                }
            }
        }
    }

    /**
     * Handle a PUTPIECE for this game, by updating game data.
     * For initial roads, also track their initial settlement in SOCPlayerTracker.
     * In general, most tracking is done a bit later in {@link #handlePUTPIECE_updateTrackers(SOCPutPiece)}.
     * @since 1.1.08
     */
    protected void handlePUTPIECE_updateGameData(SOCPutPiece mes)
    {
        final SOCPlayer pl = game.getPlayer(mes.getPlayerNumber());
        final int coord = mes.getCoordinates();

        switch (mes.getPieceType())
        {
        case SOCPlayingPiece.ROAD:

            if ((game.getGameState() == SOCGame.START1B) || (game.getGameState() == SOCGame.START2B))
            {
                //
                // Before processing this road, track the settlement that goes with it.
                // This was deferred until road placement, in case a human player decides
                // to cancel their settlement and place it elsewhere.
                //
                SOCPlayerTracker tr = (SOCPlayerTracker) playerTrackers.get
                    (Integer.valueOf(mes.getPlayerNumber()));
                SOCSettlement se = tr.getPendingInitSettlement();
                if (se != null)
                    trackNewSettlement(se, false);
            }
            SOCRoad rd = new SOCRoad(pl, coord, null);
            game.putPiece(rd);
            break;

        case SOCPlayingPiece.SETTLEMENT:

            SOCSettlement se = new SOCSettlement(pl, coord, null);
            game.putPiece(se);
            break;

        case SOCPlayingPiece.CITY:

            SOCCity ci = new SOCCity(pl, coord, null);
            game.putPiece(ci);
            break;
        }
    }

    /**
     * Handle a CANCELBUILDREQUEST for this game.
     *<P>
     *<b> During game startup</b> (START1B or START2B): <BR>
     *    When sent from server to client, CANCELBUILDREQUEST means the current
     *    player wants to undo the placement of their initial settlement.
     *<P>
     *<b> During piece placement</b> (PLACING_ROAD, PLACING_CITY, PLACING_SETTLEMENT,
     *                         PLACING_FREE_ROAD1, or PLACING_FREE_ROAD2): <BR>
     *    When sent from server to client, CANCELBUILDREQUEST means the player
     *    has sent an illegal PUTPIECE (bad building location). 
     *    Humans can probably decide a better place to put their road,
     *    but robots must cancel the build request and decide on a new plan.
     *
     * @since 1.1.08
     */
    protected void handleCANCELBUILDREQUEST(SOCCancelBuildRequest mes)
    {
        final int gstate = game.getGameState();
        switch (gstate)
        {
        case SOCGame.START1A:
        case SOCGame.START2A:
            if (ourTurn)
            {
                cancelWrongPiecePlacement(mes);
            }
            break;

        case SOCGame.START1B:
        case SOCGame.START2B:
            if (ourTurn)
            {
                cancelWrongPiecePlacement(mes);
            }
            else
            {
                //
                // human player placed, then cancelled placement.
                // Our robot wouldn't do that, and if it's ourTurn,
                // the cancel happens only if we try an illegal placement.
                //
                final int pnum = game.getCurrentPlayerNumber();
                SOCPlayer pl = game.getPlayer(pnum);
                SOCSettlement pp = new SOCSettlement(pl, pl.getLastSettlementCoord(), null);
                game.undoPutInitSettlement(pp);
                //
                // "forget" to track this cancelled initial settlement.
                // Wait for human player to place a new one.
                //
                SOCPlayerTracker tr = (SOCPlayerTracker) playerTrackers.get
                    (Integer.valueOf(pnum));
                tr.setPendingInitSettlement(null);
            }
            break;

        case SOCGame.PLAY1:  // asked to build, hasn't given location yet -> resources
        case SOCGame.PLACING_ROAD:        // has given location -> is bad location
        case SOCGame.PLACING_SETTLEMENT:
        case SOCGame.PLACING_CITY:
        case SOCGame.PLACING_FREE_ROAD1:  // JM TODO how to break out?
        case SOCGame.PLACING_FREE_ROAD2:  // JM TODO how to break out?
        case SOCGame.SPECIAL_BUILDING:
            //
            // We've asked for an illegal piece placement.
            // (Must be a bug.) Cancel and invalidate this
            // planned piece, make a new plan.
            //
            // Can also happen in special building, if another
            // player has placed since we requested special building.
            // If our PUTPIECE request is denied, server sends us
            // CANCELBUILDREQUEST.  We need to ask to cancel the
            // placement, and also set variables to end our SBP turn.
            //
            cancelWrongPiecePlacement(mes);
            break;

        default:
            if (game.isSpecialBuilding())
            {
                cancelWrongPiecePlacement(mes);
            } else {
                // Should not occur
                D.ebugPrintlnINFO("Unexpected CANCELBUILDREQUEST at state " + gstate);
            }

        }  // switch (gameState)
    }

    /**
     * Note that a player has replied to our offer.  Determine whether to keep waiting
     *  for responses, and update negotiator appropriately
     * @param playerNum
     * @param accept
     */
    protected void handleTradeResponse(int playerNum, boolean accept) {
        waitingForTradeResponsePlayer[playerNum] = false;
        tradeAccepted |= accept;
        
        boolean everyoneResponded = true;
        D.ebugPrintlnINFO("ourPlayerData.getCurrentOffer() = " + ourPlayerData.getCurrentOffer());

        for (int i = 0; i < game.maxPlayers; i++)
        {
            D.ebugPrintlnINFO("waiting for Responses[" + i + "]=" + waitingForTradeResponsePlayer[i]);

            if (waitingForTradeResponsePlayer[i]) {
                everyoneResponded = false;
                break;
            }
        }

        D.ebugPrintlnINFO("everyoneResponded=" + everyoneResponded);

        if (everyoneResponded)
        {
            if (!tradeAccepted) {
                negotiator.addToOffersMade(ourPlayerData.getCurrentOffer());
            }
            client.clearOffer(game);
            waitingForTradeResponse = false;
        }
    
    }
    
    /**
     * Handle a MAKEOFFER for this game.
     * if another player makes an offer, that's the
     * same as a rejection, but still wants to deal.
     * Call {@link #considerOffer(SOCTradeOffer)}, and if
     * we accept, clear our {@link #buildingPlan} so we'll replan it.
     * Ignore our own MAKEOFFERs echoed from server.
     * @since 1.1.08
     * TODO: Move logic of follow up into the negotiator
     */
    protected void handleMAKEOFFER(SOCMakeOffer mes)
    {
        SOCTradeOffer offer = mes.getOffer();
        game.getPlayer(offer.getFrom()).setCurrentOffer(offer);

        if ((offer.getFrom() == ourPlayerData.getPlayerNumber()))
        {
            return;  // <---- Ignore our own offers ----
        }

        negotiator.recordResourcesFromOffer(offer);
        
        if (waitingForTradeResponse)
        {
            handleTradeResponse(offer.getFrom(), false);            
        }

        ///
        /// consider the offer
        ///
        int ourResponseToOffer = considerOffer(offer);

        D.ebugPrintlnINFO("%%% ourResponseToOffer = " + ourResponseToOffer);

        if (ourResponseToOffer < 0)
            return;

        /* This needs to instead use the specifiable relative pause framework.
         * int delayLength = Math.abs(rand.nextInt() % 500) + 3500;
        if (gameIs6Player && ! waitingForTradeResponse)
        {
            delayLength *= 2;  // usually, pause is half-length in 6-player
        }*/
        pause(3); 

        switch (ourResponseToOffer) {
            case SOCRobotNegotiator.ACCEPT_OFFER:
                client.acceptOffer(game, offer.getFrom());

                // We need to process the server's notification of this trade before proceeding
                waitingForTradeMsg = true;

                /// clear our building plan, so that we replan
                resetBuildingPlan();
                negotiator.setTargetPiece(ourPlayerData.getPlayerNumber(), null);
                break;
            case SOCRobotNegotiator.REJECT_OFFER:
                if (!waitingForTradeResponse)
                    client.rejectOffer(game);
                break;
            case SOCRobotNegotiator.COUNTER_OFFER:
                if (!makeCounterOffer(offer))
                    client.rejectOffer(game);
                break;
            case SOCRobotNegotiator.COMPLETE_OFFER:
                if (!makeCompletedOffer(offer))
                    client.rejectOffer(game);
                break;
        }
    }

    /**
     * Handle a REJECTOFFER for this game.
     * watch rejections of other players' offers, and of our offers.
     * @since 1.1.08
     */
    protected void handleREJECTOFFER(SOCRejectOffer mes)
    {
        int rejector = mes.getPlayerNumber();
        if (waitingForTradeResponse){
        	handleTradeResponse(mes.getPlayerNumber(), false);//clear trading flags
            // If this is false, it means the rejected trade was accepted by another player.
            //  Since it has been cleared from the data object, it unfortunately cannot be
            //  passed to the negotiator.
            //  TODO: Rework so that we have access to this?
            negotiator.recordResourcesFromReject(rejector);
        }else
        	negotiator.recordResourcesFromRejectAlt(rejector); 
    }

    /**
     * Handle a DEVCARD for this game.
     * No brain-specific action.
     * @since 1.1.08
     */
    protected void handleDEVCARD(SOCDevCard mes)
    {
        SOCDevCardSet plCards = game.getPlayer(mes.getPlayerNumber()).getDevCards();
        final int cardType = mes.getCardType();
        SOCPlayer pl = game.getPlayer(mes.getPlayerNumber());
        
        switch (mes.getAction())
        {
        case SOCDevCard.DRAW:
            plCards.add(1, SOCDevCardSet.NEW, cardType);
            break;

        case SOCDevCard.PLAY:
            plCards.subtract(1, SOCDevCardSet.OLD, cardType);
            //handle the discovery,roadbuilding and monopoly card plays
            switch (cardType) {
			case SOCDevCardConstants.DISC:
				pl.numDISCCards++;
				break;

			case SOCDevCardConstants.MONO:
				pl.numMONOCards++;
				break;
			
			case SOCDevCardConstants.ROADS:
				pl.numRBCards++;
			break;
			
			default:
				//this is a knight so ignore
				break;
			}
            
            break;

        case SOCDevCard.ADDOLD:
            plCards.add(1, SOCDevCardSet.OLD, cardType);
            break;

        case SOCDevCard.ADDNEW:
            plCards.add(1, SOCDevCardSet.NEW, cardType);
            break;
        }
    }

    /**
     * Handle a PUTPIECE for this game, by updating {@link SOCPlayerTracker}s.
     *<P>
     * For initial placement of our own pieces, this method also checks
     * and clears expectPUTPIECE_FROM_START1A, and sets expectSTART1B, etc.
     * The final initial putpiece clears expectPUTPIECE_FROM_START2B and sets expectPLAY.
     *<P>
     * For initial settlements, won't track here:
     * Delay tracking until the corresponding road is placed,
     * in {@link #handlePUTPIECE_updateGameData(SOCPutPiece)}.
     * This prevents the need for tracker "undo" work if a human
     * player changes their mind on where to place the settlement.
     *
     * @since 1.1.08
     */
    public void handlePUTPIECE_updateTrackers(SOCPutPiece mes)
    {
        final int pn = mes.getPlayerNumber();
        final int coord = mes.getCoordinates();
        final int pieceType = mes.getPieceType();

        switch (pieceType)
        {
        case SOCPlayingPiece.ROAD:

            SOCRoad newRoad = new SOCRoad(game.getPlayer(pn), coord, null);
            trackNewRoad(newRoad, false);

            break;

        case SOCPlayingPiece.SETTLEMENT:

            SOCPlayer newSettlementPl = game.getPlayer(pn);
            SOCSettlement newSettlement = new SOCSettlement(newSettlementPl, coord, null);
            if ((game.getGameState() == SOCGame.START1B) || (game.getGameState() == SOCGame.START2B))
            {
                // Track it after the road is placed
                // (in handlePUTPIECE_updateGameData)
                SOCPlayerTracker tr = (SOCPlayerTracker) playerTrackers.get
                    (Integer.valueOf(newSettlementPl.getPlayerNumber()));
                tr.setPendingInitSettlement(newSettlement);
            }
            else
            {
                // Track it now
                trackNewSettlement(newSettlement, false);
            }                            

            break;

        case SOCPlayingPiece.CITY:

            SOCCity newCity = new SOCCity(game.getPlayer(pn), coord, null);
            trackNewCity(newCity, false);

            break;
        }

        if (D.ebugOn)
        {
            SOCPlayerTracker.playerTrackersDebug(playerTrackers);
        }

        if (pn != ourPlayerData.getPlayerNumber())
        {
            return;  // <---- Not our piece ----
        }

        /**
         * Update expect-vars during initial placement of our pieces.
         */

        if (expectPUTPIECE_FROM_START1A && (pieceType == SOCPlayingPiece.SETTLEMENT) && (mes.getCoordinates() == ourPlayerData.getLastSettlementCoord()))
        {
            expectPUTPIECE_FROM_START1A = false;
            expectSTART1B = true;
        }

        if (expectPUTPIECE_FROM_START1B && (pieceType == SOCPlayingPiece.ROAD) && (mes.getCoordinates() == ourPlayerData.getLastRoadCoord()))
        {
            expectPUTPIECE_FROM_START1B = false;
            expectSTART2A = true;
        }

        if (expectPUTPIECE_FROM_START2A && (pieceType == SOCPlayingPiece.SETTLEMENT) && (mes.getCoordinates() == ourPlayerData.getLastSettlementCoord()))
        {
            expectPUTPIECE_FROM_START2A = false;
            expectSTART2B = true;
        }

        if (expectPUTPIECE_FROM_START2B && (pieceType == SOCPlayingPiece.ROAD) && (mes.getCoordinates() == ourPlayerData.getLastRoadCoord()))
        {
            expectPUTPIECE_FROM_START2B = false;
            expectPLAY = true;
        }

    }

    /**
     * Have the client ask to build this piece, unless we've already
     * been told by the server to not build it.
     * Calls {@link #buildingPlan}.pop().
     * Checks against {@link #whatWeFailedToBuild} to see if server has rejected this already.
     * Calls <tt>client.buyDevCard()</tt> or <tt>client.buildRequest()</tt>.
     * Sets {@link #waitingForDevCard}, or {@link #waitingForGameState} and
     * {@link #expectPLACING_SETTLEMENT} (etc).
     *
     * @param targetPiece  This should be the top piece of {@link #buildingPlan}.
     * @param plan 
     * @since 1.1.08
     */
    protected void buildRequestPlannedPiece(SOCPossiblePiece targetPiece, BP plan)
    {
        D.ebugPrintlnINFO("$ POPPED " + targetPiece);
        lastMove = targetPiece;
        currentDRecorder = (currentDRecorder + 1) % 2;
        negotiator.setTargetPiece(ourPlayerData.getPlayerNumber(), plan);

        plan.advancePlan();

        switch (targetPiece.getType())
        {
        case SOCPossiblePiece.CARD:
            client.buyDevCard(game);
            waitingForDevCard = true;

            break;

        case SOCPossiblePiece.ROAD:
            waitingForGameState = true;
            counter = 0;
            expectPLACING_ROAD = true;
            whatWeWantToBuild = new SOCRoad(ourPlayerData, targetPiece.getCoordinates(), null);
            if (! whatWeWantToBuild.equals(whatWeFailedToBuild))
            {
                D.ebugPrintlnINFO("!!! BUILD REQUEST FOR A ROAD AT " + Integer.toHexString(targetPiece.getCoordinates()) + " !!!");
                client.buildRequest(game, SOCPlayingPiece.ROAD);
            } else {
                // We already tried to build this.
                cancelWrongPiecePlacementLocal(whatWeWantToBuild);
                // cancel sets whatWeWantToBuild = null;
            }
            
            break;

        case SOCPlayingPiece.SETTLEMENT:
            waitingForGameState = true;
            counter = 0;
            expectPLACING_SETTLEMENT = true;
            whatWeWantToBuild = new SOCSettlement(ourPlayerData, targetPiece.getCoordinates(), null);
            if (! whatWeWantToBuild.equals(whatWeFailedToBuild))
            {
                D.ebugPrintlnINFO("!!! BUILD REQUEST FOR A SETTLEMENT " + Integer.toHexString(targetPiece.getCoordinates()) + " !!!");
                client.buildRequest(game, SOCPlayingPiece.SETTLEMENT);
            } else {
                // We already tried to build this.
                cancelWrongPiecePlacementLocal(whatWeWantToBuild);
                // cancel sets whatWeWantToBuild = null;
            }
            
            break;

        case SOCPlayingPiece.CITY:
            waitingForGameState = true;
            counter = 0;
            expectPLACING_CITY = true;
            whatWeWantToBuild = new SOCCity(ourPlayerData, targetPiece.getCoordinates(), null);
            if (! whatWeWantToBuild.equals(whatWeFailedToBuild))
            {
                D.ebugPrintlnINFO("!!! BUILD REQUEST FOR A CITY " + Integer.toHexString(targetPiece.getCoordinates()) + " !!!");
                client.buildRequest(game, SOCPlayingPiece.CITY);
            } else {
                // We already tried to build this.
                cancelWrongPiecePlacementLocal(whatWeWantToBuild);
                // cancel sets whatWeWantToBuild = null;
            }

            break;
        }
    }

    /**
     * Plan the next building plan and target.
     * Should be called from {@link #run()} under these conditions: <BR>
     * (!expectPLACING_ROBBER && (buildingPlan.empty()) && (ourPlayerData.getResources().getTotal() > 1) && (failedBuildingAttempts < MAX_DENIED_BUILDING_PER_TURN))
     *<P>
     * Sets these fields/actions: <BR>
     *  {@link SOCRobotDM#planStuff(int)} <BR>
     *  {@link #buildingPlan} <BR>
     *  {@link #lastTarget} <BR>
     *  {@link SOCRobotNegotiator#setTargetPiece(int, SOCPossiblePiece)}
     *
     * @since 1.1.08
     * TODO: Parallel plans - how?
     */
    protected void planBuilding()
    {
        decisionMaker.planStuff();
        BP plan = getBuildingPlan();
        if (!plan.isEmpty())
        {
            // Looks like this is only for logging, so no problem if it's not our actual target
        	lastTarget =  plan.getPlannedPiece(0);
        	
            negotiator.setTargetPiece(ourPlayerData.getPlayerNumber(), plan);
        }
    }

    /**
     * Handle a PLAYERELEMENT for this game.
     * Update a player's amount of a resource or a building type.
     *<P>
     * If this during the {@link SOCGame#PLAY} state, then update the
     * {@link SOCRobotNegotiator}'s is-selling flags.
     *<P>
     * If our player is losing a resource needed for the {@link #buildingPlan}, 
     * clear the plan if this is for the Special Building Phase (on the 6-player board).
     * In normal game play, we clear the building plan at the start of each turn.
     *<P>
     * Otherwise, only the game data is updated, nothing brain-specific.
     *
     * @since 1.1.08
     */
    protected void handlePLAYERELEMENT(SOCPlayerElement mes)
    {
        SOCPlayer pl = game.getPlayer(mes.getPlayerNumber());

        switch (mes.getElementType())
        {
        case SOCPlayerElement.ROADS:

            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                (mes, pl, SOCPlayingPiece.ROAD);
            break;

        case SOCPlayerElement.SETTLEMENTS:

            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                (mes, pl, SOCPlayingPiece.SETTLEMENT);
            break;

        case SOCPlayerElement.CITIES:

            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                (mes, pl, SOCPlayingPiece.CITY);
            break;

        case SOCPlayerElement.NUMKNIGHTS:

            // PLAYERELEMENT(NUMKNIGHTS) is sent after a Soldier card is played.
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numKnights
                (mes, pl, game);
            break;

        case SOCPlayerElement.CLAY:

            handlePLAYERELEMENT_numRsrc
                (mes, pl, SOCResourceConstants.CLAY, "CLAY");
            break;

        case SOCPlayerElement.ORE:
            
            handlePLAYERELEMENT_numRsrc
                (mes, pl, SOCResourceConstants.ORE, "ORE");
            break;

        case SOCPlayerElement.SHEEP:

            handlePLAYERELEMENT_numRsrc
                (mes, pl, SOCResourceConstants.SHEEP, "SHEEP");
            break;

        case SOCPlayerElement.WHEAT:

            handlePLAYERELEMENT_numRsrc
                (mes, pl, SOCResourceConstants.WHEAT, "WHEAT");
            break;

        case SOCPlayerElement.WOOD:

            handlePLAYERELEMENT_numRsrc
                (mes, pl, SOCResourceConstants.WOOD, "WOOD");
            break;

        case SOCPlayerElement.UNKNOWN:

            /**
             * Note: if losing unknown resources, we first
             * convert player's known resources to unknown resources,
             * then remove mes's unknown resources from player.
             */
            handlePLAYERELEMENT_numRsrc
                (mes, pl, SOCResourceConstants.UNKNOWN, "UNKNOWN");
            break;

        case SOCPlayerElement.ASK_SPECIAL_BUILD:
            if (0 != mes.getValue())
            {
                try {
                    game.askSpecialBuild(pl.getPlayerNumber(), false);  // set per-player, per-game flags
                }
                catch (RuntimeException e) {}
            } else {
                pl.setAskedSpecialBuild(false);
            }
            break;

        }

        ///
        /// if this during the PLAY state, then update the is selling flags
        ///
        if (game.getGameState() == SOCGame.PLAY)
        {
            negotiator.resetIsSelling();
        }
    }

    /**
     * Update a player's amount of a resource.
     *<ul>
     *<LI> If this is a {@link SOCPlayerElement#LOSE} action, and the player does not have enough of that type,
     *     the rest are taken from the player's UNKNOWN amount.
     *<LI> If we are losing from type UNKNOWN,
     *     first convert player's known resources to unknown resources
     *     (individual amount information will be lost),
     *     then remove mes's unknown resources from player.
     *<LI> If this is a SET action, and it's for our own robot player,
     *     check the amount against {@link #ourPlayerData}, and debug print
     *     if they don't match already.
     *</ul>
     *<P>
     * If our player is losing a resource needed for the {@link #buildingPlan}, 
     * clear the plan if this is for the Special Building Phase (on the 6-player board).
     * In normal game play, we clear the building plan at the start of each turn.
     *<P>
     *
     * @param mes      Message with amount and action (SET/GAIN/LOSE)
     * @param pl       Player to update
     * @param rtype    Type of resource, like {@link SOCResourceConstants#CLAY}
     * @param rtypeStr Resource type name, for debugging
     */
    protected void handlePLAYERELEMENT_numRsrc
        (SOCPlayerElement mes, SOCPlayer pl, int rtype, String rtypeStr)
    {
        /**
         * for SET, check the amount of unknown resources against
         * what we think we know about our player.
         */
        if (D.ebugOn && (pl == ourPlayerData) && (mes.getAction() == SOCPlayerElement.SET)) 
        {
            if (mes.getValue() != ourPlayerData.getResources().getAmount(rtype))
            {
                client.sendText(game, ">>> RSRC ERROR FOR " + rtypeStr
                    + ": " + mes.getValue() + " != " + ourPlayerData.getResources().getAmount(rtype));
            }
        }

        /**
         * Update game data.
         */
        handleResources(mes.getAction(), pl, rtype, mes.getValue());        

        /**
         * Clear building plan, if we just lost a resource we need.
         * Only necessary for Special Building Phase (6-player board),
         * because in normal game play, we clear the building plan
         * at the start of each turn.
         */
        BP plan = getBuildingPlan();
        if (waitingForSpecialBuild && (pl == ourPlayerData)
            && (mes.getAction() != SOCPlayerElement.GAIN)
            && ! plan.isEmpty())
        {
        	// TODO: This is only for the expansion - TBD if this is something we need to preserve
            final SOCPossiblePiece targetPiece = (SOCPossiblePiece) plan.getPlannedPiece(0);
            final SOCResourceSet targetResources = SOCPlayingPiece.getResourcesToBuild(targetPiece.getType());

            if (! ourPlayerData.getResources().contains(targetResources))
            {
            	resetBuildingPlan();

                // The buildingPlan is clear, so we'll calculate
                // a new plan when our Special Building turn begins.
                // Don't clear decidedIfSpecialBuild flag, to prevent
                // needless plan calculation before our turn begins,
                // especially from multiple PLAYERELEMENT(LOSE),
                // as may happen for a discard.
            }
        }

    }

    public void setWaitingForTradeMsg(){
    	waitingForTradeMsg = true;
    }
    
    /**
     * Run a newly placed settlement through the playerTrackers.
     *<P>
     * During initial board setup, settlements aren't tracked when placed.
     * They are deferred until their corresponding road placement, in case
     * a human player decides to cancel their settlement and place it elsewhere.
     *
     * During normal play, the settlements are tracked immediately when placed.
     *
     * (Code previously in body of the run method.)
     * Placing the code in its own method allows tracking that settlement when the
     * road's putPiece message arrives.
     *
     * @param newSettlement The newly placed settlement for the playerTrackers
     * @param isCancel Is this our own robot's settlement placement, rejected by the server?
     *     If so, this method call will cancel its placement within the game data / robot data. 
     */
    protected void trackNewSettlement(SOCSettlement newSettlement, final boolean isCancel)
    {
        Iterator<SOCPlayerTracker> trackersIter;
        trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = trackersIter.next();
            if (! isCancel)
                tracker.addNewSettlement(newSettlement, playerTrackers);
            else
                tracker.cancelWrongSettlement(newSettlement);
        }

        trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = trackersIter.next();
            Iterator posRoadsIter = tracker.getPossibleRoads().values().iterator();

            while (posRoadsIter.hasNext())
            {
                ((SOCPossibleRoad) posRoadsIter.next()).clearThreats();
            }

            Iterator posSetsIter = tracker.getPossibleSettlements().values().iterator();

            while (posSetsIter.hasNext())
            {
                ((SOCPossibleSettlement) posSetsIter.next()).clearThreats();
            }
        }

        trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();
            tracker.updateThreats(playerTrackers);
        }

        if (isCancel)
        {
            return;  // <--- Early return, nothing else to do ---
        }

        ///
        /// see if this settlement bisected someone else's road
        ///
        int[] roadCount = { 0, 0, 0, 0, 0, 0 };  // Length should be SOCGame.MAXPLAYERS
        SOCBoard board = game.getBoard();
        Enumeration adjEdgeEnum = board.getAdjacentEdgesToNode(newSettlement.getCoordinates()).elements();

        while (adjEdgeEnum.hasMoreElements())
        {
            Integer adjEdge = (Integer) adjEdgeEnum.nextElement();
            Enumeration roadEnum = board.getRoads().elements();

            while (roadEnum.hasMoreElements())
            {
                SOCRoad road = (SOCRoad) roadEnum.nextElement();

                if (road.getCoordinates() == adjEdge.intValue())
                {
                    roadCount[road.getPlayer().getPlayerNumber()]++;

                    if (roadCount[road.getPlayer().getPlayerNumber()] == 2)
                    {
                        if (road.getPlayer().getPlayerNumber() != ourPlayerData.getPlayerNumber())
                        {
                            ///
                            /// this settlement bisects another players road
                            ///
                            trackersIter = playerTrackers.values().iterator();

                            while (trackersIter.hasNext())
                            {
                                SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();

                                if (tracker.getPlayer().getPlayerNumber() == road.getPlayer().getPlayerNumber())
                                {
                                    //D.ebugPrintln("$$ updating LR Value for player "+tracker.getPlayer().getPlayerNumber());
                                    //tracker.updateLRValues();
                                }

                                //tracker.recalcLongestRoadETA();
                            }
                        }

                        break;
                    }
                }
            }
        }
        
        int pNum = newSettlement.getPlayer().getPlayerNumber();

        ///
        /// update the speedups from possible settlements
        ///
        trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();

            if (tracker.getPlayer().getPlayerNumber() == pNum)
            {
                Iterator posSetsIter = tracker.getPossibleSettlements().values().iterator();

                while (posSetsIter.hasNext())
                {
                    ((SOCPossibleSettlement) posSetsIter.next()).updateSpeedup();
                }

                break;
            }
        }

        ///
        /// update the speedups from possible cities
        ///
        trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();

            if (tracker.getPlayer().getPlayerNumber() == pNum)
            {
                Iterator posCitiesIter = tracker.getPossibleCities().values().iterator();

                while (posCitiesIter.hasNext())
                {
                    ((SOCPossibleCity) posCitiesIter.next()).updateSpeedup();
                }

                break;
            }
        }
    }

    /**
     * Run a newly placed city through the PlayerTrackers.
     * @param newCity  The newly placed city
     * @param isCancel Is this our own robot's city placement, rejected by the server?
     *     If so, this method call will cancel its placement within the game data / robot data. 
     */
    protected void trackNewCity(SOCCity newCity, final boolean isCancel)
    {
        Iterator trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();

            if (tracker.getPlayer().getPlayerNumber() == newCity.getPlayer().getPlayerNumber())
            {
                if (! isCancel)
                    tracker.addOurNewCity(newCity);
                else
                    tracker.cancelWrongCity(newCity);

                break;
            }
        }

        if (isCancel)
        {
            return;  // <--- Early return, nothing else to do ---
        }

        ///
        /// update the speedups from possible settlements
        ///
        trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();

            if (tracker.getPlayer().getPlayerNumber() == newCity.getPlayer().getPlayerNumber())
            {
                Iterator posSetsIter = tracker.getPossibleSettlements().values().iterator();

                while (posSetsIter.hasNext())
                {
                    ((SOCPossibleSettlement) posSetsIter.next()).updateSpeedup();
                }

                break;
            }
        }

        ///
        /// update the speedups from possible cities
        ///
        trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();

            if (tracker.getPlayer().getPlayerNumber() == newCity.getPlayer().getPlayerNumber())
            {
                Iterator posCitiesIter = tracker.getPossibleCities().values().iterator();

                while (posCitiesIter.hasNext())
                {
                    ((SOCPossibleCity) posCitiesIter.next()).updateSpeedup();
                }

                break;
            }
        }
    }

    /**
     * Run a newly placed road through the playerTrackers.
     * 
     * @param newRoad  The newly placed road
     * @param isCancel Is this our own robot's road placement, rejected by the server?
     *     If so, this method call will cancel its placement within the game data / robot data. 
     */
    protected void trackNewRoad(SOCRoad newRoad, final boolean isCancel)
    {
        Iterator trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();
            tracker.takeMonitor();

            try
            {
                if (! isCancel)
                    tracker.addNewRoad(newRoad, playerTrackers);
                else
                    tracker.cancelWrongRoad(newRoad);
            }
            catch (Exception e)
            {
                tracker.releaseMonitor();
                if (alive)
                {
                    System.err.println("Exception caught - " + e);
                    e.printStackTrace();
                }
            }

            tracker.releaseMonitor();
        }

        trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();
            tracker.takeMonitor();

            try
            {
                Iterator posRoadsIter = tracker.getPossibleRoads().values().iterator();

                while (posRoadsIter.hasNext())
                {
                    ((SOCPossibleRoad) posRoadsIter.next()).clearThreats();
                }

                Iterator posSetsIter = tracker.getPossibleSettlements().values().iterator();

                while (posSetsIter.hasNext())
                {
                    ((SOCPossibleSettlement) posSetsIter.next()).clearThreats();
                }
            }
            catch (Exception e)
            {
                tracker.releaseMonitor();
                if (alive)
                {
                    System.err.println("Exception caught - " + e);
                    e.printStackTrace();
                }
            }

            tracker.releaseMonitor();
        }

        ///
        /// update LR values and ETA
        ///
        trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();
            tracker.updateThreats(playerTrackers);
            tracker.takeMonitor();

            try
            {
                if (tracker.getPlayer().getPlayerNumber() == newRoad.getPlayer().getPlayerNumber())
                {
                    //D.ebugPrintln("$$ updating LR Value for player "+tracker.getPlayer().getPlayerNumber());
                    //tracker.updateLRValues();
                }

                //tracker.recalcLongestRoadETA();
            }
            catch (Exception e)
            {
                tracker.releaseMonitor();
                if (alive)
                {
                    System.err.println("Exception caught - " + e);
                    e.printStackTrace();
                }
            }

            tracker.releaseMonitor();
        }
    }
    
    /**
     *  We've asked for an illegal piece placement.
     *  Cancel and invalidate this planned piece, make a new plan.
     *  If {@link SOCGame#isSpecialBuilding()}, will set variables to
     *  force the end of our special building turn.
     *  Also handles illegal requests to buy development cards
     *  (piece type -2 in {@link SOCCancelBuildRequest}).
     *<P>
     *  This method increments {@link #failedBuildingAttempts},
     *  but won't leave the game if we've failed too many times.
     *  The brain's run loop should make that decision.
     *
     * @param mes Cancelmessage from server, including piece type
     */
    protected void cancelWrongPiecePlacement(SOCCancelBuildRequest mes)
    {
        final boolean cancelBuyDevCard = (mes.getPieceType() == -2);
        if (cancelBuyDevCard)
        {
            waitingForDevCard = false;
        } else {
            whatWeFailedToBuild = whatWeWantToBuild;
            ++failedBuildingAttempts;
        }

        final int gameState = game.getGameState();

        /**
         * if true, server likely denied us due to resources, not due to building plan
         * being interrupted by another player's building before our special building phase.
         * (Could also be due to a bug in the chosen building plan.)
         */
        final boolean gameStateIsPLAY1 = (gameState == SOCGame.PLAY1);

        if (! (gameStateIsPLAY1 || cancelBuyDevCard))
        {
            int coord = -1;
            switch (gameState)
            {
            case SOCGame.START1A:
            case SOCGame.START1B:
            case SOCGame.START2A:
            case SOCGame.START2B:
                coord = lastStartingPieceCoord;
                break;

            default:
                if (whatWeWantToBuild != null)
                    coord = whatWeWantToBuild.getCoordinates();
            }
            if (coord != -1)
            {
                SOCPlayingPiece cancelPiece;
    
                /**
                 * First, invalidate that piece in trackers, so we don't try again to
                 * build it. If we treat it like another player's new placement, we
                 * can remove any of our planned pieces depending on this one.
                 */
                switch (mes.getPieceType())
                {
                case SOCPlayingPiece.ROAD:
                    cancelPiece = new SOCRoad(dummyCancelPlayerData, coord, null);
                    break;
    
                case SOCPlayingPiece.SETTLEMENT:
                    cancelPiece = new SOCSettlement(dummyCancelPlayerData, coord, null);
                    break;
    
                case SOCPlayingPiece.CITY:
                    cancelPiece = new SOCCity(dummyCancelPlayerData, coord, null);
                    break;
    
                default:
                    cancelPiece = null;  // To satisfy javac
                }
    
                cancelWrongPiecePlacementLocal(cancelPiece);
            }
        } else {
            /**
             *  stop trying to build it now, but don't prevent
             *  us from trying later to build it.
             */ 
            whatWeWantToBuild = null;
            resetBuildingPlan();
        }

        /**
         * we've invalidated that piece in trackers.
         * - clear whatWeWantToBuild, buildingPlan
         * - set expectPLAY1, waitingForGameState
         * - reset counter = 0
         * - send CANCEL _to_ server, so all players get PLAYERELEMENT & GAMESTATE(PLAY1) messages.
         * - wait for the play1 message, then can re-plan another piece.
         * - update javadoc of this method (TODO)
         */

        if (gameStateIsPLAY1 || game.isSpecialBuilding())
        {
            // Shouldn't have asked to build this piece at this time.
            // End our confusion by ending our current turn. Can re-plan on next turn.
            failedBuildingAttempts = MAX_DENIED_BUILDING_PER_TURN;
            expectPLACING_ROAD = false;
            expectPLACING_SETTLEMENT = false;
            expectPLACING_CITY = false;
            decidedIfSpecialBuild = true;
            if (cancelBuyDevCard)
            {
                waitingForGameState = false;  // don't wait for PLACING_ after buy dev card
            } else {
                // special building, currently in state PLACING_* ;
                // get our resources back, get state PLAY1 or SPECIALBUILD
                waitingForGameState = true;  
                client.cancelBuildRequest(game, mes.getPieceType());
            }
        }
        else if (gameState <= SOCGame.START2B)
        {
            switch (gameState)
            {
            case SOCGame.START1A:
                expectPUTPIECE_FROM_START1A = false;
                expectSTART1A = true;
                break;

            case SOCGame.START1B:
                expectPUTPIECE_FROM_START1B = false;
                expectSTART1B = true;
                break;

            case SOCGame.START2A:
                expectPUTPIECE_FROM_START2A = false;
                expectSTART2A = true;
                break;

            case SOCGame.START2B:
                expectPUTPIECE_FROM_START2B = false;
                expectSTART2B = true;
                break;
            }
            waitingForGameState = false;
            // The run loop will check if failedBuildingAttempts > (2 * MAX_DENIED_BUILDING_PER_TURN).
            // This bot will leave the game there if it can't recover.
        } else {
            expectPLAY1 = true;
            waitingForGameState = true;
            counter = 0;
            client.cancelBuildRequest(game, mes.getPieceType());
            // Now wait for the play1 message, then can re-plan another piece.
        }
    }

    /**
     * Remove our incorrect piece placement, it's been rejected by the server.
     * Take this piece out of trackers, without sending any response back to the server.
     *<P>
     * This method invalidates that piece in trackers, so we don't try again to
     * build it. Since we treat it like another player's new placement, we
     * can remove any of our planned pieces depending on this one.
     *<P>
     * Also calls {@link SOCPlayer#clearPotentialSettlement(int)},
     * clearPotentialRoad, or clearPotentialCity.
     *
     * @param cancelPiece Type and coordinates of the piece to cancel; null is allowed but not very useful.
     */
    protected void cancelWrongPiecePlacementLocal(SOCPlayingPiece cancelPiece)
    {
        if (cancelPiece != null)
        {
            final int coord = cancelPiece.getCoordinates();

            switch (cancelPiece.getType())
            {
            case SOCPlayingPiece.ROAD:
                trackNewRoad((SOCRoad) cancelPiece, true);
                ourPlayerData.clearPotentialRoad(coord);
                if (game.getGameState() <= SOCGame.START2B)
                {
                    // needed for placeInitRoad() calculations
                    ourPlayerData.clearPotentialSettlement(lastStartingRoadTowardsNode);
                }
                break;

            case SOCPlayingPiece.SETTLEMENT:
                trackNewSettlement((SOCSettlement) cancelPiece, true);
                ourPlayerData.clearPotentialSettlement(coord);
                break;

            case SOCPlayingPiece.CITY:
                trackNewCity((SOCCity) cancelPiece, true);
                ourPlayerData.clearPotentialCity(coord);
                break;
            }
        }

        whatWeWantToBuild = null;
        resetBuildingPlan();
    }

    /**
     * kill this brain
     */
    public void kill()
    {
        alive = false;
//        System.err.println(ourPlayerData.getName() + " - number of messages received while alive: " + numberOfMessagesReceived);

        try
        {
            gameEventQ.put(null);
        }
        catch (Exception exc) {}
    }

    /**
     * pause for a bit.
     *<P>
     * In a 6-player game, pause only 75% as long, to shorten the overall game delay,
     * except if {@link #waitingForTradeResponse}.
     * This is indicated by the {@link #pauseFaster} flag.
     *
     * @param msec  number of delays to apply.  by default, 500ms per delay
     */
    protected void pause(int delaySteps)
    {
    	if (delayTime > 0) {
    		int msec = delaySteps * delayTime;    	
			forcePause(msec);
		}
    }
    
    /**
     * Force an actual ms pause to wait for trade responses
     * @param msec
     */
    private void forcePause(int msec) {
    	 if (pauseFaster && ! waitingForTradeResponse)
             msec = (msec / 2) + (msec / 4);

         try
         {
             yield();
             sleep(msec);
         }
         catch (InterruptedException exc) {}
    }

    /**
     * place planned first settlement
     */
    protected void placeFirstSettlement()
    {
        //D.ebugPrintln("BUILD REQUEST FOR SETTLEMENT AT "+Integer.toHexString(firstSettlement));
        pause(1);
        lastStartingPieceCoord = decisionMaker.firstSettlement;
        client.putPiece(game, new SOCSettlement(ourPlayerData, decisionMaker.firstSettlement, null));
        pause(2);
    }

    /**
     * place planned second settlement
     */
    protected void placeSecondSettlement()
    {
        if (decisionMaker.secondSettlement == -1)
        {
            // This could mean that the server (incorrectly) asked us to
            // place another second settlement, after we've cleared the
            // potentialSettlements contents.
            System.err.println("robot assert failed: secondSettlement -1, " + ourPlayerData.getName() + " leaving game " + game.getName());
            failedBuildingAttempts = 2 + (2 * MAX_DENIED_BUILDING_PER_TURN);
            waitingForGameState = false;
            return;
        }

        //D.ebugPrintln("BUILD REQUEST FOR SETTLEMENT AT "+Integer.toHexString(secondSettlement));
        pause(1);
        lastStartingPieceCoord = decisionMaker.secondSettlement;
        client.putPiece(game, new SOCSettlement(ourPlayerData, decisionMaker.secondSettlement, null));
        pause(2);
    }

    /**
     * Plan and place a road attached to our most recently placed initial settlement,
     * in game states {@link SOCGame#START1B START1B}, {@link SOCGame#START2B START2B}.
     *<P>
     * Road choice is based on the best nearby potential settlements, and doesn't
     * directly check {@link SOCPlayer#isPotentialRoad(int) ourPlayerData.isPotentialRoad(edgeCoord)}.
     * If the server rejects our road choice, then {@link #cancelWrongPiecePlacementLocal(SOCPlayingPiece)}
     * will need to know which settlement node we were aiming for,
     * and call {@link SOCPlayer#clearPotentialSettlement(int) ourPlayerData.clearPotentialSettlement(nodeCoord)}.
     * The {@link #lastStartingRoadTowardsNode} field holds this coordinate.
     */
    public void placeInitRoad()
    {
        final int settlementNode = ourPlayerData.getLastSettlementCoord();

        int[] roadInfo = decisionMaker.planInitRoad(settlementNode);
        int roadEdge = roadInfo[0];
        int destination = roadInfo[1];

        //D.ebugPrintln("!!! PUTTING INIT ROAD !!!");
        pause(1);

        //D.ebugPrintln("Trying to build a road at "+Integer.toHexString(roadEdge));
        lastStartingPieceCoord = roadEdge;
        lastStartingRoadTowardsNode = destination;
        client.putPiece(game, new SOCRoad(ourPlayerData, roadEdge, null));
        pause(2);

    }
   
    /**
     * move the robber
     */
    protected void moveRobber()
    {
        D.ebugPrintlnINFO("%%% MOVEROBBER");

        int robberHex = game.getBoard().getRobberHex();

        int bestHex = decisionMaker.selectMoveRobber(robberHex);
        

        D.ebugPrintlnINFO("!!! MOVING ROBBER !!!");
        client.moveRobber(game, ourPlayerData, bestHex);
        pause(4);
    }

    /**
     * discard some resources
     *
     * TODO: Move this logic into DM
     * @param numDiscards  the number of resources to discard
     */
    protected void discard(int numDiscards)
    {
        //D.ebugPrintln("DISCARDING...");

        /**
         * if we have a plan, then try to keep the resources
         * needed for that plan, otherwise discard at random
         */
        SOCResourceSet discards = new SOCResourceSet();
        BP plan = getBuildingPlan();
        /**
         * make a plan if we don't have one
         */
        if (plan.isEmpty())
        {
            decisionMaker.planStuff();
        }
        plan = getBuildingPlan();
        if (!plan.isEmpty())
        {
            SOCPossiblePiece targetPiece = plan.getPlannedPiece(0);
            negotiator.setTargetPiece(ourPlayerData.getPlayerNumber(), plan);

            //D.ebugPrintln("targetPiece="+targetPiece);
            SOCResourceSet targetResources = SOCPlayingPiece.getResourcesToBuild(targetPiece.getType());

            /**
             * figure out what resources are NOT the ones we need
             */
            SOCResourceSet leftOvers = ourPlayerData.getResources().copy();

            for (int rsrc = SOCResourceConstants.CLAY;
                    rsrc <= SOCResourceConstants.WOOD; rsrc++)
            {
                if (leftOvers.getAmount(rsrc) > targetResources.getAmount(rsrc))
                {
                    leftOvers.subtract(targetResources.getAmount(rsrc), rsrc);
                }
                else
                {
                    leftOvers.setAmount(0, rsrc);
                }
            }

            SOCResourceSet neededRsrcs = ourPlayerData.getResources().copy();
            neededRsrcs.subtract(leftOvers);

            /**
             * figure out the order of resources from
             * easiest to get to hardest
             */

            //D.ebugPrintln("our numbers="+ourPlayerData.getNumbers());
            SOCBuildingSpeedEstimate estimate = getEstimator(ourPlayerData.getNumbers());
            int[] rollsPerResource = estimate.getRollsPerResource();
            int[] resourceOrder = 
            {
                SOCResourceConstants.CLAY, SOCResourceConstants.ORE,
                SOCResourceConstants.SHEEP, SOCResourceConstants.WHEAT,
                SOCResourceConstants.WOOD
            };

            for (int j = 4; j >= 0; j--)
            {
                for (int i = 0; i < j; i++)
                {
                    if (rollsPerResource[resourceOrder[i]] < rollsPerResource[resourceOrder[i + 1]])
                    {
                        int tmp = resourceOrder[i];
                        resourceOrder[i] = resourceOrder[i + 1];
                        resourceOrder[i + 1] = tmp;
                    }
                }
            }

            /**
             * pick the discards
             */
            int curRsrc = 0;

            while (discards.getTotal() < numDiscards)
            {
                /**
                 * choose from the left overs
                 */
                while ((discards.getTotal() < numDiscards) && (curRsrc < 5))
                {
                    //D.ebugPrintln("(1) dis.tot="+discards.getTotal()+" curRsrc="+curRsrc);
                    if (leftOvers.getAmount(resourceOrder[curRsrc]) > 0)
                    {
                        discards.add(1, resourceOrder[curRsrc]);
                        leftOvers.subtract(1, resourceOrder[curRsrc]);
                    }
                    else
                    {
                        curRsrc++;
                    }
                }

                curRsrc = 0;

                /**
                 * choose from what we need
                 */
                while ((discards.getTotal() < numDiscards) && (curRsrc < 5))
                {
                    //D.ebugPrintln("(2) dis.tot="+discards.getTotal()+" curRsrc="+curRsrc);
                    if (neededRsrcs.getAmount(resourceOrder[curRsrc]) > 0)
                    {
                        discards.add(1, resourceOrder[curRsrc]);
                        neededRsrcs.subtract(1, resourceOrder[curRsrc]);
                    }
                    else
                    {
                        curRsrc++;
                    }
                }
            }

            if (curRsrc == 5)
            {
                System.err.println("PROBLEM IN DISCARD - curRsrc == 5");
            }
        }
        else
        {
            /**
             *  choose discards at random
             */
            SOCGame.discardPickRandom(ourPlayerData.getResources(), numDiscards, discards, rand);
        }

        //D.ebugPrintln("!!! DISCARDING !!!");
        //D.ebugPrintln("discards="+discards);
        client.discard(game, discards);
    }

    /**
     * choose a robber victim
     *
     * @param choices a boolean array representing which players are possible victims
     */
    protected void chooseRobberVictim(boolean[] choices)
    {
        int choice = decisionMaker.choosePlayerToRob();

        if(choice != -1){ //meaning we already made our choice from DM
        	client.choosePlayer(game, choice);
        	return;
        }
        
        /**
         * choose the player with the smallest WGETA
         */
        for (int i = 0; i < game.maxPlayers; i++)
        {
            if (! game.isSeatVacant (i))
            {
                if (choices[i])
                {
                    if (choice == -1)
                    {
                        choice = i;
                    }
                    else
                    {
                        SOCPlayerTracker tracker1 = (SOCPlayerTracker) playerTrackers.get(Integer.valueOf(i));
                        SOCPlayerTracker tracker2 = (SOCPlayerTracker) playerTrackers.get(Integer.valueOf(choice));
    
                        if ((tracker1 != null) && (tracker2 != null) && (tracker1.getWinGameETA() < tracker2.getWinGameETA()))
                        {
                            //D.ebugPrintln("Picking a robber victim: pnum="+i+" VP="+game.getPlayer(i).getPublicVP());
                            choice = i;
                        }
                    }
                }
            }
        }

        /**
         * choose victim at random
         *
           do {
           choice = Math.abs(rand.nextInt() % SOCGame.MAXPLAYERS);
           } while (!choices[choice]);
         */
        client.choosePlayer(game, choice);
    }

    /**
     * make trades with the bank to get the required resources for executing a plan
     *
     * @param buildPlan  our current build plan
     * @return true if we sent a request to trade
     */
    protected boolean tradeWithBank(BP buildPlan)
    {
        /* Assume the negotiator will detect this?
         * if (ourPlayerData.getResources().contains(targetResources))
        {
            return false;
        }*/

        SOCTradeOffer bankTrade = negotiator.getOfferToBank(buildPlan, ourPlayerData.getResources());

        if ((bankTrade != null) && (ourPlayerData.getResources().contains(bankTrade.getGiveSet())))
        {
            client.bankTrade(game, bankTrade.getGiveSet(), bankTrade.getGetSet());
            pause(4);

            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * Make an offer to another player.
     * Will set {@link #waitingForTradeResponse} or {@link #doneTrading}.
     *
     * @param buildPlan  our current build plan
     * @return true if we made an offer
     */
    protected boolean makeOffer(BP buildPlan)
    {
        boolean result = false;
        SOCTradeOffer offer = negotiator.makeOffer(buildPlan);
        // Consider this offer made right away, don't wait for rejections.
        negotiator.addToOffersMade(offer);
        ourPlayerData.setCurrentOffer(offer);
        negotiator.resetWantsAnotherOffer();

        if (offer != null)
        {
            D.ebugPrintlnINFO("MAKE OFFER - " + offer);
            tradeAccepted = false;
            ///
            ///  reset the offerRejections flag
            ///
            boolean[]to = offer.getTo();
            for (int i = 0; i < game.maxPlayers; i++)
            {
                if (to[i]) {
                    waitingForTradeResponsePlayer[i] = true;
                }
            }

            waitingForTradeResponse = true;
            counter = 0;
            client.offerTrade(game, offer);
            result = true;
        }
        else
        {
            doneTrading = true;
            waitingForTradeResponse = false;
        }
        
        return result;
    }

    /**
     * make a counter offer to another player
     *
     * @param offer their offer
     * @return true if we made an offer
     */
    protected boolean makeCounterOffer(SOCTradeOffer offer)
    {
        boolean result = false;
        SOCTradeOffer counterOffer = negotiator.makeCounterOffer(offer);
        
        if (counterOffer != null)
        {
            // ensure counter-offers are only sent to the person we're countering, otherwise synchronization issues arise
            boolean[] to = counterOffer.getTo();
            for (int i=0; i<to.length; i++) {
                to[i] = false;
            }
            to[offer.getFrom()] = true;            
            
            ourPlayerData.setCurrentOffer(counterOffer);
            negotiator.addToOffersMade(counterOffer);
            ///
            ///  reset the offerRejections flag
            ///
            tradeAccepted = false;
            waitingForTradeResponsePlayer[offer.getFrom()] = true;
            waitingForTradeResponse = true;
            counter = 0;
            client.offerTrade(game, counterOffer);
            result = true;
        }
        else
        {
            // ??? This seems to cause problems...
            //  We get here if we got an offer which we don't want to accept, but may want to counter (based on considerOffer), but were unable to 
            //  come up with a counter-offer (based on makeCounterOffer).  
            //  This should be handled exactly as though we rejected the offer from the beginning.
            //doneTrading = true;
            //waitingForTradeResponse = false;
        }

        return result;
    }

    /**
     * Make a completed offer to another player.
     * 
     * Performs a check that the offer is not null, which can occur if 
     * {@link StacRobotDeclarativeMemory#retrieveBestCompletedTradeOffer()} has "forgotten" the best completed offer.
     * 
     * bestCompletedOffer may not be null when this method is called!
     *
     * @param offer the partial offer we received
     * @return true if we made an offer
     * @author Markus Guhe
     */
    protected boolean makeCompletedOffer(SOCTradeOffer offer)
    {
        boolean result = false;
        SOCTradeOffer bestCompletedOffer = negotiator.getBestCompletedOffer(); 
        //copied from makeCounterOffer
        if (offer != null && bestCompletedOffer != null)
        {
            tradeAccepted = false;
            waitingForTradeResponsePlayer[offer.getFrom()] = true;
            waitingForTradeResponse = true;
            counter = 0;
        
            //specific completedOffer stuff
            int from = ourPlayerData.getPlayerNumber();
            boolean[] toPlayer;
            toPlayer = new boolean [4];
            toPlayer[0] = false; toPlayer[1] = false; toPlayer[2] = false; toPlayer[3] = false;
            toPlayer[bestCompletedOffer.getFrom()] = true;
            SOCTradeOffer actualCounterOffer = new SOCTradeOffer(bestCompletedOffer.getGame(), from, toPlayer, bestCompletedOffer.getGetSet(), bestCompletedOffer.getGiveSet());
        
            //again copied from makeCounterOffer
            client.offerTrade(game, actualCounterOffer);
            ourPlayerData.setCurrentOffer(actualCounterOffer);
            negotiator.addToOffersMade(actualCounterOffer);

            result = true;        
        }
        else
        {
            doneTrading = true;
            waitingForTradeResponse = false;
        }

        return result;
    }


    /**
     * this is for debugging
     */
    protected void debugInfo()
    {
        /*
           if (D.ebugOn) {
           //D.ebugPrintln("$===============");
           //D.ebugPrintln("gamestate = "+game.getGameState());
           //D.ebugPrintln("counter = "+counter);
           //D.ebugPrintln("resources = "+ourPlayerData.getResources().getTotal());
           if (expectSTART1A)
           //D.ebugPrintln("expectSTART1A");
           if (expectSTART1B)
           //D.ebugPrintln("expectSTART1B");
           if (expectSTART2A)
           //D.ebugPrintln("expectSTART2A");
           if (expectSTART2B)
           //D.ebugPrintln("expectSTART2B");
           if (expectPLAY)
           //D.ebugPrintln("expectPLAY");
           if (expectPLAY1)
           //D.ebugPrintln("expectPLAY1");
           if (expectPLACING_ROAD)
           //D.ebugPrintln("expectPLACING_ROAD");
           if (expectPLACING_SETTLEMENT)
           //D.ebugPrintln("expectPLACING_SETTLEMENT");
           if (expectPLACING_CITY)
           //D.ebugPrintln("expectPLACING_CITY");
           if (expectPLACING_ROBBER)
           //D.ebugPrintln("expectPLACING_ROBBER");
           if (expectPLACING_FREE_ROAD1)
           //D.ebugPrintln("expectPLACING_FREE_ROAD1");
           if (expectPLACING_FREE_ROAD2)
           //D.ebugPrintln("expectPLACING_FREE_ROAD2");
           if (expectPUTPIECE_FROM_START1A)
           //D.ebugPrintln("expectPUTPIECE_FROM_START1A");
           if (expectPUTPIECE_FROM_START1B)
           //D.ebugPrintln("expectPUTPIECE_FROM_START1B");
           if (expectPUTPIECE_FROM_START2A)
           //D.ebugPrintln("expectPUTPIECE_FROM_START2A");
           if (expectPUTPIECE_FROM_START2B)
           //D.ebugPrintln("expectPUTPIECE_FROM_START2B");
           if (expectDICERESULT)
           //D.ebugPrintln("expectDICERESULT");
           if (expectDISCARD)
           //D.ebugPrintln("expectDISCARD");
           if (expectMOVEROBBER)
           //D.ebugPrintln("expectMOVEROBBER");
           if (expectWAITING_FOR_DISCOVERY)
           //D.ebugPrintln("expectWAITING_FOR_DISCOVERY");
           if (waitingForGameState)
           //D.ebugPrintln("waitingForGameState");
           if (waitingForOurTurn)
           //D.ebugPrintln("waitingForOurTurn");
           if (waitingForTradeMsg)
           //D.ebugPrintln("waitingForTradeMsg");
           if (waitingForDevCard)
           //D.ebugPrintln("waitingForDevCard");
           if (moveRobberOnSeven)
           //D.ebugPrintln("moveRobberOnSeven");
           if (waitingForTradeResponse)
           //D.ebugPrintln("waitingForTradeResponse");
           if (doneTrading)
           //D.ebugPrintln("doneTrading");
           if (ourTurn)
           //D.ebugPrintln("ourTurn");
           //D.ebugPrintln("whatWeWantToBuild = "+whatWeWantToBuild);
           //D.ebugPrintln("#===============");
           }
         */
    }

    /**
     * For each player in game:
     * client.sendText, and debug-print to console, game.getPlayer(i).getResources()
     */
    protected void printResources()
    {
        if (D.ebugOn)
        {
            for (int i = 0; i < game.maxPlayers; i++)
            {
                SOCResourceSet rsrcs = game.getPlayer(i).getResources();
                String resourceMessage = "PLAYER " + i + " RESOURCES: ";
                resourceMessage += (rsrcs.getAmount(SOCResourceConstants.CLAY) + " ");
                resourceMessage += (rsrcs.getAmount(SOCResourceConstants.ORE) + " ");
                resourceMessage += (rsrcs.getAmount(SOCResourceConstants.SHEEP) + " ");
                resourceMessage += (rsrcs.getAmount(SOCResourceConstants.WHEAT) + " ");
                resourceMessage += (rsrcs.getAmount(SOCResourceConstants.WOOD) + " ");
                resourceMessage += (rsrcs.getAmount(SOCResourceConstants.UNKNOWN) + " ");
                client.sendText(game, resourceMessage);
                D.ebugPrintlnINFO(resourceMessage);
            }
        }
    }
    /**
	*Wake up the brain by setting suspended flag to false
	*/
    public void awaken(){
    	suspended = false;
    }
    
    /**
     * During the loading procedure, the brain state during saving needs to be recreated.
     * @param info the brain state to update to
     */
	public void partialUpdateFromInfo(StacRobotBrainInfo info) {
    	buildingPlan = (BP) info.buildingPlan;
    	counter = info.counter;
    	doneTrading = info.doneTrading;
    	lastMove = info.lastMove;
    	lastTarget = info.lastTarget;
    	decisionMaker.monopolyChoice = info.monopolyChoice;
    	numberOfMessagesReceived = info.numberOfMessagesReceived;
    	oldGameState = info.oldGameState;
    	ourTurn = info.ourTurn;
    	decisionMaker.resourceChoices = info.resourceChoices;
    	tradeAccepted = info.tradeAccepted;
    	whatWeFailedToBuild = info.whatWeFailedToBuild;
    	whatWeWantToBuild = info.whatWeWantToBuild;
    	
    	expectSTART1A = info.expectSTART1A;
        expectSTART1B = info.expectSTART1B;
        expectSTART2A = info.expectSTART2A;
        expectSTART2B = info.expectSTART2B;
        expectPLAY = info.expectPLAY;
        expectPLAY1 = info.expectPLAY1;
        expectPLACING_ROAD = info.expectPLACING_ROAD;
        expectPLACING_SETTLEMENT = info.expectPLACING_SETTLEMENT;        
        expectPLACING_CITY = info.expectPLACING_CITY;
        expectPLACING_ROBBER = info.expectPLACING_ROBBER;
        expectPLACING_FREE_ROAD1 = info.expectPLACING_FREE_ROAD1;
        expectPLACING_FREE_ROAD2 = info.expectPLACING_FREE_ROAD2;
        expectPUTPIECE_FROM_START1A = info.expectPUTPIECE_FROM_START1A;
        expectPUTPIECE_FROM_START1B = info.expectPUTPIECE_FROM_START1B;
        expectPUTPIECE_FROM_START2A = info.expectPUTPIECE_FROM_START2A;
        expectPUTPIECE_FROM_START2B = info.expectPUTPIECE_FROM_START2B;
        expectDICERESULT = info.expectDICERESULT;
        expectDISCARD = info.expectDISCARD;
        expectMOVEROBBER = info.expectMOVEROBBER;
        expectWAITING_FOR_DISCOVERY = info.expectWAITING_FOR_DISCOVERY;
        expectWAITING_FOR_MONOPOLY = info.expectWAITING_FOR_MONOPOLY;
    	
        waitingForGameState = info.waitingForGameState;
        waitingForOurTurn = info.waitingForOurTurn;
        waitingForTradeMsg = info.waitingForTradeMsg;
        waitingForDevCard = info.waitingForDevCard;
        moveRobberOnSeven = info.moveRobberOnSeven;
        waitingForTradeResponse = info.waitingForTradeResponse;
	}
	
    /**
     * @return a container with all the flags and parameters describing the brain's current state.
     */
	public StacRobotBrainInfo getInfo() {
		return new StacRobotBrainInfo(this);
	}
	
	 /**
     * @return the game data
     */
    public SOCGame getGame()
    {
        return game;
    }

    /**
     * @return our player data
     */
    public SOCPlayer getOurPlayerData()
    {
        return ourPlayerData;
    }

    /**
     * @return the building plan
     */
    public BP getBuildingPlan()
    {
        return buildingPlan;
    }
    
    /**
     * clears the stack describing the current building plan.
     * NOTE should be called every time we planStuff as checking if the BuildingPlan is empty is not good enough if our memory is weak or decaying
     */
    public void resetBuildingPlan(){
    	buildingPlan.clear();
    }
    
    /**
     * @return the player trackers (one per player, including this robot)
     */
    public HashMap<Integer, SOCPlayerTracker> getPlayerTrackers()
    {
        return playerTrackers;
    }

    /**
     * @return our player tracker
     */
    public SOCPlayerTracker getOurPlayerTracker()
    {
        return ourPlayerTracker;
    }	
	
    // Functions which must be defined by an instantiating subclass
    
    /**
     * consider an offer made by another player
     *
     * @param offer  the offer to consider
     * @return a code that represents how we want to respond
     * note: a negative result means we do nothing
     */
    protected abstract int considerOffer(SOCTradeOffer offer);
	
    /**
     * Handle the tracking of changing resources.  Allows us to determine how accurately this is tracked 
     *   eg full tracking of unknowns vs. cognitive modelling
     * @param action  SET, GAIN, LOSE
     * @param player
     * @param resourceType
     * @param amount
     */
    protected abstract void handleResources(int action, SOCPlayer player, int resourceType, int amount);
   	
    /**
     * Creates a decision maker
     * @return the decision maker depending on the type of brain
     */
    protected abstract DM createDM();
    
    /**
     * Recreates a decision maker
     * @return the decision maker depending on the type of brain
     */
    public abstract void recreateDM();
    
    /**
     * Creates a Negotiator object
     * @return the Negotiator depending on the brain type
     */
    protected abstract N createNegotiator();
	
    /**
     * Contains the logic for handling messages received via the chat interface, such as trades or inform of resources/build plans
     * @param gtm
     */
    protected abstract void handleChat(SOCGameTextMsg gtm);

    /**
     * perform specific actions required by some of the brain types during a start of turn. THis is actually executed when the game enters Play1 state,
     * so it isn't exactly start of turn, rather after rolling the dice
     * @param player the player number representing the position of the player on the board to do the actions for
     */
    protected abstract void startTurnActions(int player);
    
    /**
     * Some robot types require specific actions just before ending their turn, which may result in continuing their turn for a little while.
     * e.g. TRY_N_BEST agent may decide to try another build plan before ending its turn
     * @return true if can end turn, false otherwise
     */
    protected abstract boolean endTurnActions();

    /**
     * Is waiting for a reply to trades via the chat interface
     * @return
     */
    protected abstract boolean isWaiting();

    /**
     * @return true if this robot is the old SOC robot or a newer Stac version
     */
    protected abstract boolean isLegacy();

    /**
     * @param numbers the current resources in hand of the player we are estimating for
     * @return an estimate of time to build something
     */
    public abstract SOCBuildingSpeedEstimate getEstimator(SOCPlayerNumbers numbers);

    /**
     * @return an estimate of time to build something
     */
    public abstract SOCBuildingSpeedEstimate getEstimator();

    /**
     * Inform the brain of the final game result.  Brain implementations may have some bookkeeping to do.
     * @param message
     */
    protected abstract void handleGAMESTATS(SOCGameStats message);

    /**
     * Creates the stack containing the steps in the buildplan
     * @return the buildingPlan stack 
     */
    protected abstract BP createBuildPlan();
    
    /**
     * Announces participation in game chat, if robot is of specific type.
     */
    public abstract void startGameChat();
    
    /**
     * Clears all flags waiting for a trade message.
     */
    public abstract void clearTradingFlags(String text);
    
    ///debug save method/////
    /**
     * initiates a save; to be called during the run loop for choosing when to save
     */
    public abstract void saveGame();
    
    /**
     * Handles messages received from the server, which announce specific actions (e.g. the execution of a robbery)
     * @param gtm
     */
    public abstract void handleGameTxtMsg(SOCGameTextMsg gtm); 

    /**
     * Handles results returned from the parser.
     * @param mes  The message containing the parse result received from the server
     */
    public abstract void handleParseResult(SOCParseResult mes);
    
    /////////////////////////
    
    //////////methods for extracting features from a current game state/////////////////////
    
    /**
     * This method needs access to the game object for computing some features which can not just be observed 
     * (e.g. does the player have a city/settlement isolated, what is the size of the players longest possible road not built yet etc)
     * @return the extracted features without the trades and bps counters as we have no way of keeping track of past ones nor measuring the future ones
     */
    public ExtGameStateRow turnStateIntoEGSR(){
    	ExtGameStateRow egsr = new ExtGameStateRow(0, getGame().getName());
    	//empty counters
		int[][] tradesCounter = new int[4][4];
		int[][] bppCounter = new int[4][6];
		//initialise the counters to 0
		Arrays.fill(tradesCounter[0],0);Arrays.fill(tradesCounter[1],0);Arrays.fill(tradesCounter[2],0);Arrays.fill(tradesCounter[3],0);
		Arrays.fill(bppCounter[0],0);Arrays.fill(bppCounter[1],0);Arrays.fill(bppCounter[2],0);Arrays.fill(bppCounter[3],0);
        egsr.setPastPBPs(StacDBHelper.transformToIntegerArr2(bppCounter));
        egsr.setPastTrades(StacDBHelper.transformToIntegerArr2(tradesCounter));
        egsr.setFuturePBPs(StacDBHelper.transformToIntegerArr2(bppCounter));
        egsr.setFutureTrades(StacDBHelper.transformToIntegerArr2(tradesCounter));
		
        /**
         * NOTE: the fact that both connected and isolated get set if a player joins without playing is annoying, but hopefully that won't be too much noise
         * as we are checking for null players again when we are selecting the features.
         */
        int[] territoryConnected = calculateAllConnectedTerr();
        int[] territoryIsolated = calculateAllIsolatedTerr();
        int[] distanceToPort = new int[4];
        int[] distanceToNextLegalLoc = new int[4];
        int[] distanceToOpp = new int[4];
        calculateAllDistances(distanceToPort, distanceToOpp, distanceToNextLegalLoc);
        int[] longestRoads = calculateAllLongestRoads();
        int[] longestPossibleRoads = calculateAllLongestPossibleRoads();
        
        //intialise arrays required for the egsr;
        int[] etws = new int[4];
        int[][] avgEtbs = new int [4][2];
        int[][] setEtbs = new int [4][2];
        int[][] cityEtbs = new int [4][2];
        int[][] roadEtbs = new int [4][2];
        int[][] devEtbs = new int [4][2];
        int[][] rssTypeNNumber = new int[4][5];
        
        //calculate all etbs and etws here
        etws = calculateAllCurrentETWs();
        calculateAllCurrentETBs(avgEtbs,setEtbs,cityEtbs,roadEtbs,devEtbs);
        
        //calculate all rssTypeNNumber here
        calculateAllRssTypeNNumber(rssTypeNNumber);
        
        //set everything for the egsr here
        egsr.setETWs(etws);
        egsr.setAvgETBs(StacDBHelper.transformToIntegerArr2(avgEtbs));
        egsr.setRoadETBs(StacDBHelper.transformToIntegerArr2(roadEtbs));
        egsr.setSettETBs(StacDBHelper.transformToIntegerArr2(setEtbs));
        egsr.setCityETBs(StacDBHelper.transformToIntegerArr2(cityEtbs));
        egsr.setDevETBs(StacDBHelper.transformToIntegerArr2(devEtbs));
        egsr.setRssTypeAndNumber(StacDBHelper.transformToIntegerArr2(rssTypeNNumber));
        egsr.setTerritoryConnected(StacDBHelper.transformToIntegerArr(territoryConnected));
        egsr.setTerritoryIsolated(StacDBHelper.transformToIntegerArr(territoryIsolated));
        egsr.setDistanceToPort(StacDBHelper.transformToIntegerArr(distanceToPort));
        egsr.setDistanceToOpponents(StacDBHelper.transformToIntegerArr(distanceToOpp));
        egsr.setDistanceToNextLegalLoc(StacDBHelper.transformToIntegerArr(distanceToNextLegalLoc));
        egsr.setLongestRoads(StacDBHelper.transformToIntegerArr(longestRoads));
        egsr.setLongestPossibleRoads(StacDBHelper.transformToIntegerArr(longestPossibleRoads));
    	return egsr;
    }
    
	/**
	 * updates the array containing the rss type and number of settlements/cities touching them for each player;
	 * NOTE: this is ignoring the robber's current location on board
	 * @param rssTypeNNumber
	 */
    protected void calculateAllRssTypeNNumber(int[][] rssTypeNNumber){
    	for (SOCPlayer p : getGame().getPlayers()) {
            int pn = p.getPlayerNumber();
            //get the player numbers
            SOCPlayerNumbers playerNumbers = p.getNumbers();
            
            rssTypeNNumber[pn][0] = playerNumbers.getNumbersForResource(SOCResourceConstants.CLAY).size();
            rssTypeNNumber[pn][1] = playerNumbers.getNumbersForResource(SOCResourceConstants.ORE).size();
            rssTypeNNumber[pn][2] = playerNumbers.getNumbersForResource(SOCResourceConstants.SHEEP).size();
            rssTypeNNumber[pn][3] = playerNumbers.getNumbersForResource(SOCResourceConstants.WHEAT).size();
            rssTypeNNumber[pn][4] = playerNumbers.getNumbersForResource(SOCResourceConstants.WOOD).size();
    	}
    }
    
    /**
	 * Updates all arrays containing the current etbs for each player both considering the robber location and without
	 * @param avgEtbs
	 * @param setEtbs
	 * @param cityEtbs
	 * @param roadEtbs
	 * @param devEtbs
	 */
    private void calculateAllCurrentETBs(int[][]avgEtbs,int[][]setEtbs,int[][]cityEtbs,int[][]roadEtbs,int[][]devEtbs){
    	int robber = getGame().getBoard().getRobberHex();
    	for (SOCPlayer p : getGame().getPlayers()) {
            int pn = p.getPlayerNumber();
            //get the player numbers
            SOCPlayerNumbers playerNumbers = p.getNumbers();

            //get the port flags
            boolean[] portFlags = p.getPortFlags();
            
			//in here calculate the etb's/etw's for the player and include in the egsr (all etb's are from nothing; maybe I should also measure from now?)
	        SOCBuildingSpeedEstimate estimator = new SOCBuildingSpeedFast();//always use the fast estimator;
	        estimator.recalculateEstimates(playerNumbers);
	        int[] speeds = estimator.getEstimatesFromNothingFast(portFlags);
		    int avg = 0;
	        for (int j = SOCBuildingSpeedEstimate.MIN;j < SOCBuildingSpeedEstimate.MAXPLUSONE; j++){
		    	avg += speeds[j];
		    }
	        //etbs ignoring the robber's effect
	        avgEtbs[pn][0] = avg/SOCBuildingSpeedEstimate.MAXPLUSONE;//avg etb's 
	        roadEtbs[pn][0] = speeds[SOCBuildingSpeedEstimate.ROAD];//road etb's
		    setEtbs[pn][0] = speeds[SOCBuildingSpeedEstimate.SETTLEMENT];//sett etb's
		    cityEtbs[pn][0] = speeds[SOCBuildingSpeedEstimate.CITY];//city etb's
		    devEtbs[pn][0] = speeds[SOCBuildingSpeedEstimate.CARD];//dev etb's
		    
	        estimator.recalculateEstimates(playerNumbers,robber);
	        int[] speedsRob = estimator.getEstimatesFromNothingFast(portFlags);
		    int avgRob = 0;
	        for (int j = SOCBuildingSpeedEstimate.MIN;j < SOCBuildingSpeedEstimate.MAXPLUSONE; j++){
		    	avgRob += speedsRob[j];
		    }
	        
	        //etbs including the robber's effect
	        avgEtbs[pn][1] = avgRob/SOCBuildingSpeedEstimate.MAXPLUSONE;//avg etb's 
	        roadEtbs[pn][1] = speedsRob[SOCBuildingSpeedEstimate.ROAD];//road etb's
		    setEtbs[pn][1] = speedsRob[SOCBuildingSpeedEstimate.SETTLEMENT];//sett etb's
		    cityEtbs[pn][1] = speedsRob[SOCBuildingSpeedEstimate.CITY];//city etb's
		    devEtbs[pn][1] = speedsRob[SOCBuildingSpeedEstimate.CARD];//dev etb's
    	}
	}
    
    /**
     * Computes the estimated time to win for each player.
	 * @return an array with the etws following players' the order on the board (based on their player numbers)
	 */
	private int[] calculateAllCurrentETWs(){
		SOCGame game = getGame();
		
		int[] winGameETAs = new int[game.maxPlayers];
	    for (int i = game.maxPlayers - 1; i >= 0; --i)
	    	winGameETAs[i] = 500;
	    Map<Integer, SOCPlayerTracker> playerTrackers = new HashMap<>();
	    playerTrackers = getPlayerTrackers();
	    
	    Iterator trackersIter = playerTrackers.values().iterator();
	    while (trackersIter.hasNext()){
	        SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();
	        try{
	        	tracker.recalcWinGameETA();
	        	winGameETAs[tracker.getPlayer().getPlayerNumber()] = tracker.getWinGameETA();
	        }catch (NullPointerException e){
	             winGameETAs[tracker.getPlayer().getPlayerNumber()] = 500;
	        }
	    }
	    return winGameETAs;
	}
	
	/**
	 * Computes the current longest road for each player (this can be argued as observed feature though..) 
	 * @return the longest roads for each player based on their position at the board (player numbers)
	 */
	protected int[] calculateAllLongestRoads(){
		SOCGame game = getGame();
		int[] posLR = new int[game.maxPlayers];
		Arrays.fill(posLR, 0);
		for (SOCPlayer p : game.getPlayers()) {
            String pName = p.getName();
            int pn = p.getPlayerNumber();
            if(pName!=null){
            	posLR[pn] = p.calcLongestRoad2();
            }
		}
		return posLR;
	}
	
	/**
	 * Calculates distances to nearest port, opponent settlement or legal place to build in number of road pieces/edges up to a maximum of 3
	 * @param toPort
	 * @param toOpp
	 * @param toLegal
	 */
	protected void calculateAllDistances(int[] toPort, int[] toOpp, int[] toLegal){
		SOCGame game = getGame();
		Arrays.fill(toPort, 0);
		Arrays.fill(toOpp, 0);
		Arrays.fill(toLegal, 0);
		SOCBoard bd = game.getBoard();
		
		//very slow and complex calculation, hence why it should rather be done while replaying than online
		for (SOCPlayer p : game.getPlayers()) {
            String pName = p.getName();
            int pn = p.getPlayerNumber();
            if(pName!=null){
            	List<Integer> nodes2Away;
            	Iterator it;
	            // for each city
	            it = p.getCities().iterator();
	            while (it.hasNext()) {
	                SOCCity c = (SOCCity) it.next();
	                //check what is there at every 2 roads away
	                nodes2Away = bd.getAdjacentNodesToNode2Away(c.getCoordinates());
	                for(Integer node : nodes2Away){
	                	if(p.isLegalSettlement(node)){
	                		toLegal[pn] = 2;
	                		if(bd.getPortTypeFromNodeCoord(node)!= -1)
	                			toPort[pn] = 2;
	                	}else if(bd.settlementAtNode(node) != null){ 
	                		//check that is not one of our settlements
	                		if(!p.isOurSetOrCityAtCoord(node))
	                			toOpp[pn] = 2;
	                	}
	                }
	            }
	            // and for each settlement
	            it = p.getSettlements().iterator();
	            while (it.hasNext()) {
	                SOCSettlement s = (SOCSettlement) it.next();
	                //check what is there at every 2 roads away
	                nodes2Away = bd.getAdjacentNodesToNode2Away(s.getCoordinates());
	                for(Integer node : nodes2Away){
	                	if(p.isLegalSettlement(node)){
	                		toLegal[pn] = 2;
	                		if(bd.getPortTypeFromNodeCoord(node)!= -1)
	                			toPort[pn] = 2;
	                	}else if(bd.settlementAtNode(node) != null){ 
	                		//check that is not one of our settlements
	                		if(!p.isOurSetOrCityAtCoord(node))
	                			toOpp[pn] = 2;
	                	}
	                }
	            }
	        //if this is not a null player and we haven't set any of these to 2 than need to set them to 3 (roads or longer)
            if(toOpp[pn] == 0)
            	toOpp[pn] = 3;
            if(toPort[pn] == 0)
            	toPort[pn] = 3;
            if(toLegal[pn] == 0)
            	toLegal[pn] = 3;
            }
		}
	}
	
	/**
	 * Computes if the players have their settlements connected via their own road pieces.
	 * @return an array containing binary values, 1 if all settlements are connected, 0 otherwise
	 */
	private int[] calculateAllConnectedTerr(){
		SOCGame game = getGame();
		int[] connT = new int[game.maxPlayers];
		Arrays.fill(connT, 1);
		for (SOCPlayer p : game.getPlayers()) {
            String pName = p.getName();
            int pn = p.getPlayerNumber();
            if(pName!=null){
            	//pick one settlement or city from the list of pieces
            	SOCPlayingPiece start = null;
            	for(Object o : p.getPieces()){
            		int t = ((SOCPlayingPiece) o).getType();
            		if(t==SOCPlayingPiece.SETTLEMENT || t==SOCPlayingPiece.CITY){
            			start = (SOCPlayingPiece) o;
            			break;
            		}
            	}
            	//if the list was not empty
            	if(start != null){
	            	//for each sett in list of sett check if connected, else set to 0
	            	Iterator it;
	            	it = p.getSettlements().iterator();
			        while (it.hasNext()) {
			        	if(connT[pn] ==0)
			        		break;//avoid doing more checks if we already know we can't get somewhere
			        	SOCSettlement s = (SOCSettlement) it.next();
			        	if(!pathAndRoadsExist(start.getCoordinates(), s.getCoordinates(), p))
			        		connT[pn] = 0;
			        }
	            	//for each city in list of cities check if its connected, else set to 0
		            it = p.getCities().iterator();
		            while (it.hasNext()) {
			        	if(connT[pn] ==0)
			        		break;//avoid doing more checks if we already know we can't get somewhere
		                SOCCity c = (SOCCity) it.next();
		            	if(!pathAndRoadsExist(start.getCoordinates(), c.getCoordinates(), p))
			        		connT[pn] = 0;
		            }	
            	}
            }else{
            	connT[pn] = 0; //we want this value to be 0 for null players
            }
		}
		return connT;
	}
	
	/**
	 * Computes if the players have one of ther settlements isolated from the rest of their territory (i.e. there is no legal path to build roads to unite these)
	 * @return an array containing binary values, 0 for an isolated settlement, 1 otherwise (as the db variable is notIsolated)
	 */
	private int[] calculateAllIsolatedTerr(){
		SOCGame game = getGame();
		int[] notIsoT = new int[game.maxPlayers];
		Arrays.fill(notIsoT, 0);
		for (SOCPlayer p : game.getPlayers()) {
            String pName = p.getName();
            int pn = p.getPlayerNumber();
            if(pName!=null){
            	boolean isolated = false;
            	//out of all the settlements and cities, if there is one isolated
            	for(Object o : p.getPieces()){
            		int t = ((SOCPlayingPiece) o).getType();
            		if(t==SOCPlayingPiece.SETTLEMENT || t==SOCPlayingPiece.CITY){
            			if(!isNotIsolated(((SOCPlayingPiece) o).getCoordinates(), p))
            				isolated = true;
            		}
            	}
            	if(!isolated) //if there is none isolated than we set it 
            		notIsoT[pn] = 1;
            }else{
            	notIsoT[pn] = 0; //we want this value to be 0 for null players
            }
        }
		return notIsoT;
	}
	
	/**
	 * A DFS implementation that only checks if there is a path to another settlement/city not the length of the path
	 * and it doesn't memorise the edges taken. Similar to pathAndRoadsExist but this one has multiple possible goals 
	 * and doesn't care if there aren't any roads on the path.
	 * @param start the start location
	 * @return true if it found a path, false otherwise
	 */
	private boolean isNotIsolated(int start, SOCPlayer p){
		SOCGame game = getGame();
		SOCBoard bd = game.getBoard();
		Vector visited = new Vector();
		Stack stack = new Stack();
		stack.add(Integer.valueOf(start));
		while(!stack.empty()){
			Integer coord = (Integer) stack.pop();
			visited.add(coord);
			if(coord.intValue()!=start && p.isOurSetOrCityAtCoord(coord.intValue()))//stop when encountering one of our pieces on a node
				return true;
			Vector adjacents = bd.getAdjacentNodesToNode(coord.intValue());
			//check if adjacents can be accessed (i.e. the edge is empty or has one of our pieces)
			for(Object n : adjacents){
				int edge = bd.getEdgeBetweenAdjacentNodes(coord.intValue(), ((Integer)n).intValue());
				boolean wasVisited = false;
				//check if it has been visited before
				for(Object v : visited){
					if(((Integer)v).intValue() == ((Integer)n).intValue())
						wasVisited = true;
				}
				//check if the node is unnoccupied
				boolean nodeUnoccupied = true;
				for(Object sett : bd.getSettlements()){
					if(((SOCPlayingPiece)sett).getCoordinates()==((Integer)n).intValue())
						nodeUnoccupied = false; //we found a piece there
				}	
				for(Object city : bd.getCities()){
					if(((SOCPlayingPiece)city).getCoordinates()==((Integer)n).intValue())
						nodeUnoccupied = false; //we found a piece there
				}
				//check that the edge is unoccupied
				boolean edgeUnoccupied = true;
				for(Object road : bd.getRoads()){
					if(((SOCPlayingPiece)road).getCoordinates()==edge)
						edgeUnoccupied = false; //we found a piece there
				}
				//we are (either connected to the next node or the edge is empty) and the next node is either free or has one of our pieces
				if((p.isOurRoadAtCoord(edge) || edgeUnoccupied) && (nodeUnoccupied || p.isOurSetOrCityAtCoord(((Integer)n).intValue()))){
					if(!wasVisited)
						stack.add(n);
				}
			}
		}
		return false;
	}
	
	/**
	 * A DFS implementation that only checks if there is a path to another settlement/city not the length of the path
	 * and it doesn't memorise the edges taken. Similar to isNotIsolated but this one has a specific finish.
	 * @param start
	 * @param finish
	 * @param p
	 * @return
	 */
	private boolean pathAndRoadsExist(int start, int finish, SOCPlayer p){
		SOCGame game = getGame();
		SOCBoard bd = game.getBoard();
		Vector visited = new Vector();
		Stack stack = new Stack();
		stack.add(Integer.valueOf(start));
		while(!stack.empty()){
			Integer coord = (Integer) stack.pop();
			visited.add(coord);
			if(coord.intValue()==finish)
				return true;
			Vector adjacents = bd.getAdjacentNodesToNode(coord.intValue());
			//check if adjacents can be accessed (i.e. the edge is empty or has one of our pieces)
			for(Object n : adjacents){
				int edge = bd.getEdgeBetweenAdjacentNodes(coord.intValue(), ((Integer)n).intValue());
				boolean wasVisited = false;
				//check if it was visited before
				for(Object v : visited){
					if(((Integer)v).intValue() == ((Integer)n).intValue())
						wasVisited = true;
				}
				//if we are connected to the next node add to stack
				if(p.isOurRoadAtCoord(edge)){
					if(!wasVisited)
						stack.add(n);
				}
			}
		}
		return false;
	}
	
	/**
	 * Computes the longest road each player could build
	 * @return array containing values representing all max depth of roads for each player, based on their board position (player numbers) 
	 */
	private int[] calculateAllLongestPossibleRoads(){
		SOCGame game = getGame();
		SOCBoard bd = game.getBoard();
		int[] pathsLengths = new int[game.maxPlayers];
		Arrays.fill(pathsLengths, 0);
		for (SOCPlayer p : game.getPlayers()) {
            String pName = p.getName();
            int pn = p.getPlayerNumber();
            if(pName!=null){
            	for(Object o : p.getPieces()){
            		int t = ((SOCPlayingPiece) o).getType();
            		if(t==SOCPlayingPiece.SETTLEMENT || t==SOCPlayingPiece.CITY){
            			int depth = calcMaxDepthWithPath(((SOCPlayingPiece) o).getCoordinates(), p);
            			if(depth > pathsLengths[pn])
            				pathsLengths[pn] = depth;
            		}
            	}
            }
        }
		return pathsLengths;
	}
	
	/**
	 * A BFS implementation that only stops once it reaches 15 road pieces linked together or runs out of options.
	 * @return the max depth any path from start could reach
	 */
	private int calcMaxDepthWithPath(int start, SOCPlayer p){
		SOCGame game = getGame();
		SOCBoard bd = game.getBoard();
		Vector visited = new Vector();
		HashMap<Integer, Integer> coordToDepth = new HashMap<Integer, Integer>();
		Queue q = new Queue();
		q.put(Integer.valueOf(start));
		int depth =0;
		coordToDepth.put(Integer.valueOf(start), Integer.valueOf(depth));//start depth
		while(!q.empty()){
			if(depth == 15)
				break; //we don't have more than 15 roads anyway
			Integer coord = (Integer) q.get();
			//as we get one from the q we update the depth to the corresponding one in the map so we always keep track of the depth
			depth = coordToDepth.get(coord).intValue();
			visited.add(coord);
			Vector adjacents = bd.getAdjacentNodesToNode(coord.intValue());
			//add to map that tracks depth
			for(Object n : adjacents){
				coordToDepth.put((Integer) n, Integer.valueOf(depth+1)); //next level so + 1
			}
			
			//check if adjacents can be accessed (i.e. the edge is empty or has one of our pieces)
			for(Object n : adjacents){
				int edge = bd.getEdgeBetweenAdjacentNodes(coord.intValue(), ((Integer)n).intValue());
				boolean wasVisited = false;
				//check if it was visited before
				for(Object v : visited){
					if(((Integer)v).intValue() == ((Integer)n).intValue())
						wasVisited = true;
				}
				boolean unoccupied = true;
				for(Object piece : bd.getPieces()){
					if(((SOCPlayingPiece)piece).getCoordinates()==((Integer)n).intValue())
						unoccupied = false; //we found a piece there
				}	
				//we are either connected to the next node or the edge is not occupied
				if(p.isOurRoadAtCoord(edge) || unoccupied){
					if(!wasVisited)
						q.put(n);
				}
			}
		}
		return depth;
	}
}
