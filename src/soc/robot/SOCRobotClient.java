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

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Random;
import java.util.Vector;

import mcts.game.catan.belief.CatanFactoredBelief;
import soc.client.SOCDisplaylessPlayerClient;
import soc.disableDebug.D;
import soc.game.SOCCity;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCRoad;
import soc.game.SOCSettlement;
import soc.message.SOCAcceptOffer;
import soc.message.SOCAdminPing;
import soc.message.SOCAdminReset;
import soc.message.SOCBoardLayout;
import soc.message.SOCBoardLayout2;
import soc.message.SOCCancelBuildRequest;
import soc.message.SOCChangeFace;
import soc.message.SOCChoosePlayerRequest;
import soc.message.SOCClearOffer;
import soc.message.SOCClearTradeMsg;
import soc.message.SOCCollectData;
import soc.message.SOCDeleteGame;
import soc.message.SOCDevCard;
import soc.message.SOCDevCardCount;
import soc.message.SOCDiceResult;
import soc.message.SOCDiscardRequest;
import soc.message.SOCFirstPlayer;
import soc.message.SOCGameCopy;
import soc.message.SOCGameMembers;
import soc.message.SOCGameState;
import soc.message.SOCGameStats;
import soc.message.SOCGameTextMsg;
import soc.message.SOCImARobot;
import soc.message.SOCJoinGame;
import soc.message.SOCJoinGameAuth;
import soc.message.SOCJoinGameRequest;
import soc.message.SOCLargestArmy;
import soc.message.SOCLeaveAll;
import soc.message.SOCLeaveGame;
import soc.message.SOCLoadGame;
import soc.message.SOCLongestRoad;
import soc.message.SOCMakeOffer;
import soc.message.SOCMessage;
import soc.message.SOCMessageForGame;
import soc.message.SOCMoveRobber;
import soc.message.SOCParseResult;
import soc.message.SOCPlayerElement;
import soc.message.SOCPotentialSettlements;
import soc.message.SOCPutPiece;
import soc.message.SOCRejectConnection;
import soc.message.SOCRejectOffer;
import soc.message.SOCResetBoardAuth;
import soc.message.SOCResourceCount;
import soc.message.SOCRobotDismiss;
import soc.message.SOCRobotFlag;
import soc.message.SOCServerPing;
import soc.message.SOCSetPlayedDevCard;
import soc.message.SOCSetTurn;
import soc.message.SOCSitDown;
import soc.message.SOCStartGame;
import soc.message.SOCStatusMessage;
import soc.message.SOCTurn;
import soc.message.SOCUpdateRobotParams;
import soc.message.SOCVersion;
import soc.robot.stac.MCTSRobotBrain;
import soc.robot.stac.OriginalSSRobotBrain;
import soc.robot.stac.StacRobotBrain;
import soc.robot.stac.StacRobotBrainFlatMCTS;
import soc.robot.stac.StacRobotBrainRandom;
import soc.robot.stac.StacRobotDeclarativeMemory;
import soc.robot.stac.StacRobotType;
import soc.robot.stac.flatmcts.FlatMctsRewards;
import soc.robot.stac.negotiationlearning.LearningNegotiator;
import soc.server.SOCServer;
import soc.server.genericServer.LocalStringServerSocket;
import soc.server.genericServer.Server;
import soc.util.CappedQueue;
import soc.util.CutoffExceededException;
import soc.util.DeepCopy;
import soc.util.SOCRobotParameters;
import soc.util.Version;
import supervised.main.BayesianSupervisedLearner;
import simpleDS.learning.SimpleAgent;

/**
 * This is a client that can play Settlers of Catan.
 *
 * @author Robert S Thomas
 */
public class SOCRobotClient extends SOCDisplaylessPlayerClient
{
    private SOCRobotFactory factory;

    /**
     * Information that pass on from one STAC robot brain to its next incarnation, 
     * ie to the brain created for the next game (meant for sequences of games in a simulation).
     */
    private HashMap persistentBrainInformation;

    /**
     * Output stream for the learned distribution of strategies.
     */
    public BufferedWriter strategiesOut;

    /**
     * constants for debug recording
     */
    public static final String CURRENT_PLANS = "CURRENT_PLANS";
    public static final String CURRENT_RESOURCES = "RESOURCES";
    
    public static boolean saved = false;//for debugging purposes; remove later (please ignore for now)
    
    /**
     * For debugging/regression testing, randomly pause responding
     * for several seconds, to simulate a "stuck" robot brain.
     *<P>
     *<b>Note:</b> This debugging tool is not scalable to many simultaneous games,
     * because it delays all messages, not just ones for a specific game / brain,
     * and it won't be our turn in each of those games.
     * @see #DEBUGRANDOMPAUSE_FREQ
     * @see #debugRandomPauseActive
     * @since 1.1.11
     */
    private static boolean debugRandomPause = false;  // set true to use this debug type

    /**
     * Is {@link #debugRandomPause} currently in effect for this client?
     * If so, store messages into {@link #debugRandomPauseQueue} instead of
     * sending them to {@link #robotBrains} immediately.
     * The pause goes on until {@link #debugRandomPauseUntil} arrives.
     * This is all handled within {@link #treat(SOCMessage)}.
     * @since 1.1.11
     */
    private boolean debugRandomPauseActive = false;

    /**
     * When {@link #debugRandomPauseActive} is true, store incoming messages
     * from the server into this queue until {@link #debugRandomPauseUntil}.
     * Initialized in {@link #treat(SOCMessage)}.
     * @since 1.1.11
     */
    private Vector debugRandomPauseQueue = null;

    /**
     * When {@link #debugRandomPauseActive} is true, resume at this time;
     * same format as {@link System#currentTimeMillis()}.
     * @see #DEBUGRANDOMPAUSE_SECONDS
     * @since 1.1.11
     */
    private long debugRandomPauseUntil;

    /**
     * When {@link #debugRandomPause} is true but not {@link #debugRandomPauseActive},
     * frequency of activating it; checked for each non-{@link SOCGameTextMsg}
     * message received during our own turn. 
     * @since 1.1.11
     */
    private static final double DEBUGRANDOMPAUSE_FREQ = .04;  // 4%

    /**
     * When {@link #debugRandomPauseActive} is activated, pause this many seconds
     * before continuing.
     * @see #debugRandomPauseUntil
     */
    private static final int DEBUGRANDOMPAUSE_SECONDS = 12;

    /**
     * the thread the reads incoming messages
     */
    private Thread reader;

    /**
     * the current robot parameters for robot brains
     */
    private SOCRobotParameters currentRobotParameters;

    /**
     * the robot's "brains", 1 for each game this robot is currently playing.
     * @see SOCDisplaylessPlayerClient#games
     */
    private Hashtable robotBrains = new Hashtable();

    /**
     * the message queues for the different brains
     */
    private Hashtable brainQs = new Hashtable();

    /**
     * a table of requests from the server to sit at games
     */
    private Hashtable seatRequests = new Hashtable();

    /**
     * options for all games on the server we've been asked to join.
     * Some games may have no options, so will have no entry here,
     * although they will have an entry in {@link #games} once joined.
     * Key = game name, Value = hashtable of {@link SOCGameOption}.
     * Entries are added in {@link #handleJOINGAMEREQUEST(SOCJoinGameRequest)}.
     * Since the robot and server are the same version, the
     * set of "known options" will always be in sync.
     */
    private Hashtable gameOptions = new Hashtable();

    /**
     * number of games this bot has played
     */
    protected int gamesPlayed;

    /**
     * number of games finished
     */
    protected int gamesFinished;

    /**
     * number of games this bot has won
     */
    protected int gamesWon;

    /**
     * number of clean brain kills
     */
    protected int cleanBrainKills;

    /**
     * start time
     */
    protected long startTime;

//    /**
//     * used to maintain connection
//     */
//    SOCRobotResetThread resetThread;
// not used ---MD

    /**
     * Have we printed the initial welcome msg from server?
     * Suppress further ones (disconnect-reconnect).
     * @since 1.1.06
     */
    boolean printedInitialWelcome = false;
    
    // HWU negotiators (their states should persist across games)

    /** Supervised learning model for trade negotiation */ 
    BayesianSupervisedLearner sltrader = null;
    
    /** MDP learning model for trade negotiation */
    LearningNegotiator mdp_negotiator = null;
    
    /** Deep reinforcement learning model for trade negotiation */ 
    SimpleAgent deeptrader = null;

    /**
     * Constructor for connecting to the specified host, on the specified port
     *
     * @param factory a RobotFactory to generate the robot-brain
     * @param h  host
     * @param p  port
     * @param nn nickname for robot
     * @param pw password for robot
     */
    public SOCRobotClient(SOCRobotFactory factory, String h, int p, String nn, String pw)
    {
    	this.factory = factory;
        gamesPlayed = 0;
        gamesFinished = 0;
        gamesWon = 0;
        cleanBrainKills = 0;
        startTime = System.currentTimeMillis();
        host = h;
        port = p;
        nickname = nn;
        password = pw;
        strSocketName = null;

        //THIS SHOULD REALLY BE HANDLED AS PART OF THE PERSISTENT INFORMATION IN THE ROBOT BRAIN
//        try {
//            Date runStartDate = new Date();
//            String ds = "_" + runStartDate.toString().replace(':','_').replace(' ','_');
//            strategiesOut = new BufferedWriter(new FileWriter(new File("Q-values" + ds + ".txt")));
//            //write the column headers
//            String outString = "game number" + "\t" + 
//                    "won" + "\t" +
////                    strategies[0][0] + ", " + strategies[0][1] + "\t" +
////                    strategies[1][0] + ", " + strategies[1][1] + "\t" +
////                    strategies[2][0] + ", " + strategies[2][1] + "\t" +
////                    strategies[3][0] + ", " + strategies[3][1] + "\t" +
//                    "strategy 0" + "\t" + "strategy 1" + "\t" + "strategy 2" + "\t" + "strategy 3" + "\t" +
//                    "Sum";
//            strategiesOut.write(outString);
//            strategiesOut.newLine();
//            strategiesOut.flush();
//
//        } catch (IOException ex) {
//            Logger.getLogger(StacRobotBrain.class.getName()).log(Level.SEVERE, null, ex);
//        }
    }

    /**
     * Constructor for connecting to a local game (practice) on a local stringport.
     *
     * @param factory a RobotFactory to generate the robot-brain
     * @param s    the stringport that the server listens on
     * @param nn   nickname for robot
     * @param pw   password for robot
     */
    public SOCRobotClient(SOCRobotFactory factory, String s, String nn, String pw, SimpleAgent deeptrader)
    {
        this(factory, null, 0, nn, pw);
        strSocketName = s;
        
        if (nn.startsWith("Deep")) {
        	this.deeptrader = deeptrader;
        }
    }
    
    /**
     * Initialize the robot player; connect to server, send first messages
     */
    public void init()
    {
        try
        {
            if (strSocketName == null)
            {
                s = new Socket(host, port);
                s.setSoTimeout(300000);
                in = new DataInputStream(s.getInputStream());
                out = new DataOutputStream(s.getOutputStream());
            }
            else
            {
                sLocal = LocalStringServerSocket.connectTo(strSocketName);
            }               
            connected = true;
            reader = new Thread(this);
            reader.start();
            
            Server.trackThread(reader, null);

            //resetThread = new SOCRobotResetThread(this);
            //resetThread.start();
            put(SOCVersion.toCmd(Version.versionNumber(), Version.version(), Version.buildnum()));
            put(SOCImARobot.toCmd(nickname, SOCImARobot.RBCLASS_BUILTIN)); 
        }
        catch (Exception e)
        {
            ex = e;
            System.err.println("Could not connect to the server: " + ex);
        }
    }

    /**
     * disconnect and then try to reconnect.
     * If the reconnect fails, {@link #ex} is set. Otherwise ex is null.
     */
    public void disconnectReconnect()
    {
        D.ebugPrintlnINFO("(*)(*)(*)(*)(*)(*)(*) disconnectReconnect()");
        ex = null;

        try
        {
            connected = false;
            if (strSocketName == null)
            {
                s.close();
                s = new Socket(host, port);
                in = new DataInputStream(s.getInputStream());
                out = new DataOutputStream(s.getOutputStream());
            }
            else
            {
                sLocal.disconnect();
                sLocal = LocalStringServerSocket.connectTo(strSocketName);
            }
            connected = true;
            reader = new Thread(this);
            reader.start();
            Server.trackThread(reader, this);

            //resetThread = new SOCRobotResetThread(this);
            //resetThread.start();
            put(SOCVersion.toCmd(Version.versionNumber(), Version.version(), Version.buildnum()));
            put(SOCImARobot.toCmd(nickname, SOCImARobot.RBCLASS_BUILTIN));
        }
        catch (Exception e)
        {
            ex = e;
            // Comment this out - happens predictably at the end of a test run
            //System.err.println("disconnectReconnect error: " + ex);
        }
    }

    /**
     * Treat the incoming messages.
     * Messages of unknown type are ignored (mes will be null from {@link SOCMessage#toMsg(String)}).
     *
     * @param mes    the message
     */
    public void treat(SOCMessage mes)
    {
        if (mes == null)
            return;  // Message syntax error or unknown type
        
        // Using debugRandomPause?
        if (debugRandomPause && (! robotBrains.isEmpty())
            && (mes instanceof SOCMessageForGame)
            && ! (mes instanceof SOCGameTextMsg)
            && ! (mes instanceof SOCTurn))
        {
            final String ga = ((SOCMessageForGame) mes).getGame();
            if (ga != null)
            {
                SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(ga);
                if (brain != null)
                {
                    if (! debugRandomPauseActive)
                    {
                        // random chance of doing so
                        if ((Math.random() < DEBUGRANDOMPAUSE_FREQ)
                            && ((debugRandomPauseQueue == null)
                                || (debugRandomPauseQueue.isEmpty())))
                        {
                            SOCGame gm = (SOCGame) games.get(ga);
                            final int cpn = gm.getCurrentPlayerNumber();
                            SOCPlayer rpl = gm.getPlayer(nickname);
                            if ((rpl != null) && (cpn == rpl.getPlayerNumber())
                                && (gm.getGameState() >= SOCGame.PLAY))
                            {
                                // we're current player, pause us
                                debugRandomPauseActive = true;
                                debugRandomPauseUntil = System.currentTimeMillis()
                                    + (1000L * DEBUGRANDOMPAUSE_SECONDS);
                                if (debugRandomPauseQueue == null)
                                    debugRandomPauseQueue = new Vector();
                                System.err.println("L379 -> do random pause: " + nickname);
                                sendText(gm,
                                    "debugRandomPauseActive for " + DEBUGRANDOMPAUSE_SECONDS + " seconds");
                            }
                        }
                    }
                }
            }
        }

        if (debugRandomPause && debugRandomPauseActive)
        {
            if ((System.currentTimeMillis() < debugRandomPauseUntil)
                && ! (mes instanceof SOCTurn))
            {
                // time hasn't arrived yet, and still our turn:
                //   Add message to queue (even non-game and SOCGameTextMsg)
                debugRandomPauseQueue.addElement(mes);

                return;  // <--- Early return: debugRandomPauseActive ---
            }
            else
            {
                // time to resume the queue
                debugRandomPauseActive = false;
                while (! debugRandomPauseQueue.isEmpty())
                {
                    // calling ourself is safe, because
                    //  ! queue.isEmpty; thus won't decide
                    //  to set debugRandomPauseActive=true again.
                    treat((SOCMessage) debugRandomPauseQueue.firstElement());
                    debugRandomPauseQueue.removeElementAt(0);
                }

                // Don't return from this method yet,
                // we still need to process mes.
            }
        }

        D.ebugPrintlnINFO("IN - " + mes);

        try
        {
            switch (mes.getType())
            {
            /**
             * status message
             */
            case SOCMessage.STATUSMESSAGE:
                handleSTATUSMESSAGE((SOCStatusMessage) mes);

                break;

            /**
             * server ping
             */
            case SOCMessage.SERVERPING:
                handleSERVERPING((SOCServerPing) mes);

                break;

            /**
             * admin ping
             */
            case SOCMessage.ADMINPING:
                handleADMINPING((SOCAdminPing) mes);

                break;

            /**
             * admin reset
             */
            case SOCMessage.ADMINRESET:
                handleADMINRESET((SOCAdminReset) mes);

                break;

            /**
             * update the current robot parameters
             */
            case SOCMessage.UPDATEROBOTPARAMS:
                handleUPDATEROBOTPARAMS((SOCUpdateRobotParams) mes);

                break;

            /**
             * join game authorization
             */
            case SOCMessage.JOINGAMEAUTH:
                handleJOINGAMEAUTH((SOCJoinGameAuth) mes);

                break;

            /**
             * someone joined a game
             */
            case SOCMessage.JOINGAME:
                handleJOINGAME((SOCJoinGame) mes);

                break;

            /**
             * someone left a game
             */
            case SOCMessage.LEAVEGAME:
                handleLEAVEGAME((SOCLeaveGame) mes);

                break;

            /**
             * game has been destroyed
             */
            case SOCMessage.DELETEGAME:
                handleDELETEGAME((SOCDeleteGame) mes);

                break;

            /**
             * list of game members
             */
            case SOCMessage.GAMEMEMBERS:
                handleGAMEMEMBERS((SOCGameMembers) mes);

                break;

            /**
             * game text message
             */
            case SOCMessage.GAMETEXTMSG:
                handleGAMETEXTMSG((SOCGameTextMsg) mes);

                break;

            /**
             * parse result
             */
            case SOCMessage.PARSERESULT:
                handlePARSERESULT((SOCParseResult) mes);

                break;

            /**
             * someone is sitting down
             */
            case SOCMessage.SITDOWN:
                handleSITDOWN((SOCSitDown) mes);

                break;

            /**
             * receive a board layout
             */
            case SOCMessage.BOARDLAYOUT:
                handleBOARDLAYOUT((SOCBoardLayout) mes);  // in soc.client.SOCDisplaylessPlayerClient
                break;

            /**
             * receive a board layout (new format, as of 20091104 (v 1.1.08))
             */
            case SOCMessage.BOARDLAYOUT2:
                handleBOARDLAYOUT2((SOCBoardLayout2) mes);  // in soc.client.SOCDisplaylessPlayerClient
                break;

            /**
             * message that the game is starting
             */
            case SOCMessage.STARTGAME:
                handleSTARTGAME((SOCStartGame) mes);

                break;

            /**
             * update the state of the game
             */
            case SOCMessage.GAMESTATE:
                handleGAMESTATE((SOCGameState) mes);

                break;

            /**
             * set the current turn
             */
            case SOCMessage.SETTURN:
                handleSETTURN((SOCSetTurn) mes);

                break;

            /**
             * set who the first player is
             */
            case SOCMessage.FIRSTPLAYER:
                handleFIRSTPLAYER((SOCFirstPlayer) mes);

                break;

            /**
             * update who's turn it is
             */
            case SOCMessage.TURN:
                handleTURN((SOCTurn) mes);

                break;

            /**
             * receive player information
             */
            case SOCMessage.PLAYERELEMENT:
                handlePLAYERELEMENT((SOCPlayerElement) mes);

                break;

            /**
             * receive resource count
             */
            case SOCMessage.RESOURCECOUNT:
                handleRESOURCECOUNT((SOCResourceCount) mes);

                break;

            /**
             * the latest dice result
             */
            case SOCMessage.DICERESULT:
                handleDICERESULT((SOCDiceResult) mes);

                break;

            /**
             * a player built something
             */
            case SOCMessage.PUTPIECE:
                handlePUTPIECE((SOCPutPiece) mes);

                break;

            /**
             * the current player has cancelled an initial settlement
             */
            case SOCMessage.CANCELBUILDREQUEST:
                handleCANCELBUILDREQUEST((SOCCancelBuildRequest) mes);

                break;

            /**
             * the robber moved
             */
            case SOCMessage.MOVEROBBER:
                handleMOVEROBBER((SOCMoveRobber) mes);

                break;

            /**
             * the server wants this player to discard
             */
            case SOCMessage.DISCARDREQUEST:
                handleDISCARDREQUEST((SOCDiscardRequest) mes);

                break;

            /**
             * the server wants this player to choose a player to rob
             */
            case SOCMessage.CHOOSEPLAYERREQUEST:
                handleCHOOSEPLAYERREQUEST((SOCChoosePlayerRequest) mes);

                break;

            /**
             * a player has made an offer
             */
            case SOCMessage.MAKEOFFER:
                handleMAKEOFFER((SOCMakeOffer) mes);

                break;

            /**
             * a player has cleared her offer
             */
            case SOCMessage.CLEAROFFER:
                handleCLEAROFFER((SOCClearOffer) mes);

                break;

            /**
             * a player has rejected an offer
             */
            case SOCMessage.REJECTOFFER:
                handleREJECTOFFER((SOCRejectOffer) mes);

                break;

            /**
             * a player has accepted an offer
             */
            case SOCMessage.ACCEPTOFFER:
                handleACCEPTOFFER((SOCAcceptOffer) mes);

                break;

            /**
             * the trade message needs to be cleared
             */
            case SOCMessage.CLEARTRADEMSG:
                handleCLEARTRADEMSG((SOCClearTradeMsg) mes);

                break;

            /**
             * the current number of development cards
             */
            case SOCMessage.DEVCARDCOUNT:
                handleDEVCARDCOUNT((SOCDevCardCount) mes);

                break;

            /**
             * a dev card action, either draw, play, or add to hand
             */
            case SOCMessage.DEVCARD:
                handleDEVCARD((SOCDevCard) mes);

                break;

            /**
             * set the flag that tells if a player has played a
             * development card this turn
             */
            case SOCMessage.SETPLAYEDDEVCARD:
                handleSETPLAYEDDEVCARD((SOCSetPlayedDevCard) mes);

                break;

            /**
             * get a list of all the potential settlements for a player
             */
            case SOCMessage.POTENTIALSETTLEMENTS:
                handlePOTENTIALSETTLEMENTS((SOCPotentialSettlements) mes);

                break;

            /**
             * the server is requesting that we join a game
             */
            case SOCMessage.JOINGAMEREQUEST:
                handleJOINGAMEREQUEST((SOCJoinGameRequest) mes);

                break;

            /**
             * message that means the server wants us to leave the game
             */
            case SOCMessage.ROBOTDISMISS:
                handleROBOTDISMISS((SOCRobotDismiss) mes);

                break;

            /**
             * handle the reject connection message - JM TODO: placement within switch? (vs displaylesscli, playercli) 
             */
            case SOCMessage.REJECTCONNECTION:
                handleREJECTCONNECTION((SOCRejectConnection) mes);

                break;

            /**
             * handle board reset (new game with same players, same game name, new layout).
             */
            case SOCMessage.RESETBOARDAUTH:
                handleRESETBOARDAUTH((SOCResetBoardAuth) mes);

                break;
           
            
            /** 
             * Game stats: Pass them to the brain to learn if it's so inclined
             * 
             */
            case SOCMessage.GAMESTATS:
                handleGenericMessage((SOCGameStats) mes);
                break;
             
            /**
             * handle clone of game data request.
             */
            case SOCMessage.GAMECOPY:
                handleGAMECOPY((SOCGameCopy) mes);
                break;

            /**
             * handle load game request.
             */
            case SOCMessage.LOADGAME:
                handleLOADGAME((SOCLoadGame) mes);
                break;
            
            /**
             * handle robot flag change message.
             */
            case SOCMessage.ROBOTFLAGCHANGE:
            	handleROBOTFLAGCHANGE ((SOCRobotFlag) mes);
            	break;
                        
            /**
             * handle robot flag change message.
             */
            case SOCMessage.COLLECTDATA:
            	handleCollectData((SOCCollectData) mes);
            	break;
            }
                
        }
        catch (Throwable e)
        {
            System.err.println("SOCRobotClient treat ERROR - " + e + " " + e.getMessage());
            e.printStackTrace();
            while (e.getCause() != null)
            {
                e = e.getCause();
                System.err.println(" -> nested: " + e.getClass());
                e.printStackTrace();
            }
            System.err.println("-- end stacktrace --");
        }
    }
    
	private void handleCollectData(SOCCollectData mes) {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintlnINFO("CutoffExceededException" + exc);
            }
        }
		
	}

	//---MD Methods for handling messages for SAVE/LOAD function
    /**
     * updates the robot flag in the player object from the game object.
     * @param mes the received message
     */
    private void handleROBOTFLAGCHANGE(SOCRobotFlag mes){
    	SOCGame game = (SOCGame) games.get(mes.getGame());//in theory this should be the same object as the ones in the brain etc
    	SOCPlayer player = game.getPlayer(mes.getPlayerNumber());
		player.setRobotFlagUnsafe(mes.getFlag());
    }
    
    /**
     * Loads a previous game.
     * Reads the saved game and robot states (i.e. {@link SOCGame}, {@link SOCPlayer},an array of {@link SOCPlayerTracker}, 
     * {@link StacRobotDeclarativeMemory} and{@link StacRobotBrainInfo}). Then it follows through these steps(in this order):
     * <ul>
     * 	<li>Suspends the brain;
     * 	<li>Updates the new game obj with the old game name and old player names;
     * 	<li>Restores the reference to the correct game object for each player obj inside the players array contained by the game obj;
     * 	<li>Replaces the old game data with the cloned data inside the client's list of games;
     * 	<li>Update the reference to the brain inside each possibleCity for each cloned player tracker;
     * 	<li>Update each of the existing tracker in the brain from the corresponding cloned tracker;
     * 	<li>Update the reference in each existing tracker to the corresponding cloned player objects;
     * 	<li>Also update the parameters ourPlayerData/Tracker in the brain;
     * 	<li>Restore the game reference in the brain to the cloned one;
     *  <li>Update the brain state and parameters from the saved container({@link StacRobotBrainInfo});
     *  <li>Update the information in the declarative memory using the information inside the cloned one;
     *  <li>recreate/update DM/DialogueMgr/Negotiator;
     *  <li>Calculates some initial results for the MCTS logic, in case we are running simulations;
     *  <li>Wakes the brain up.
     * <ul>
     * 
     * NOTE: this piece of code looks horrible and is extremely brittle as it needs to handle the existing spaghetti code in the baseline; 
     * 		thus it needs to update all the references between the objects and update some of the information in a specific order (i.e. 
     * 		update all the information inside the brain first and then recreate/update DM/DialogueMgr/Negotiator)
     * @param mes the received message
     */
    private void handleLOADGAME(SOCLoadGame mes) {
        //get some required info from old state
        String prefix = mes.getFolder() + "/"; //the reason for doing this here is that when calling getDirectory the "/" is not added
    	String brainType = this.robotBrains.get(mes.getGame()).getClass().getName(); //get the brain type
    	SOCRobotBrain rb = (SOCRobotBrain) this.robotBrains.get(mes.getGame()); //get a reference to the brain
    	SOCGame originalGame = (SOCGame) games.get(mes.getGame()); //get a reference to the old game obj in order to get the right player names
    	int pn = rb.getOurPlayerData().getPlayerNumber(); //get this player's number
    	
    	//suspend brain
    	CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());
        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintlnINFO("CutoffExceededException" + exc);
            }
        }
        // and wait for it to be suspended
        while(!rb.isSuspended()){
        	try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
        }
    	
    	String fileName = prefix + pn + "_" + SOCGame.class.getName();
    	SOCGame gameClone = (SOCGame) DeepCopy.readFromFile(fileName); //read the SOCGame object
    	gameClone.setName(originalGame.getName()); //keep the old name for safety reasons
    	gameClone.updatePlayerNames(originalGame.getPlayerNames()); //keep the old player names
    	gameClone.resetTimes();
    	int n = gameClone.maxPlayers; //the number of players participating in the game
    	for(int i = 0; i < n; i++){
			SOCPlayer p = gameClone.getPlayer(i);
			if(i==pn){
				//make sure we are known as a robot so our turn can be ended
				p.setRobotFlagUnsafe(true);
				put(SOCRobotFlag.toCmd(originalGame.getName(), true, pn));
			}
			p.setGame(gameClone); //restore reference to this game in the player objects
		}
    	//replace the old game obj with the new one in this client's list of games
    	this.games.remove(originalGame.getName());
		this.games.put(gameClone.getName(), gameClone);
		
    	fileName = prefix + pn + "_" + ArrayList.class.getName();
    	ArrayList trackersList = (ArrayList) DeepCopy.readFromFile(fileName);  //read the SOCPlayerTrackers for this player
    	if (trackersList == null){//logic for handling the case one human player is replaced by a robot
    		for(int i=0; i < gameClone.maxPlayers; i++){
    			fileName = prefix + i + "_" + ArrayList.class.getName();
    			trackersList = (ArrayList) DeepCopy.readFromFile(fileName);
    			if(trackersList != null)
    				break;
    		}
    	}
    	//if at least one robot was playing in the original game use their trackers
    	if (trackersList != null){
	    	Object[] pt =  trackersList.toArray(); 
			//update all playerTrackers in the brain (also extra logic for our playerData and tracker)
			for(int i = 0; i < n; i++){
				SOCPlayerTracker pti = (SOCPlayerTracker)pt[i];
				//update the reference to the correct brain in PossibleCities and PossibleSettlements objects
				Iterator posCitiesIter = pti.getPossibleCities().values().iterator();
				while (posCitiesIter.hasNext())
		        {
		            SOCPossibleCity posCity = (SOCPossibleCity) posCitiesIter.next();
		            posCity.brain = rb;
		        }
				Iterator posSettlIter = pti.getPossibleSettlements().values().iterator();
				while (posSettlIter.hasNext())
		        {
		            SOCPossibleSettlement posSettl = (SOCPossibleSettlement) posSettlIter.next();
		            posSettl.brain = rb;
		        }
				SOCPlayerTracker tracker = (SOCPlayerTracker) rb.getPlayerTrackers().get(i);
				tracker.partialUpdateFromTracker(pti); //update the playerTracker from the cloned one
				tracker.setPlayer(gameClone.getPlayer(i)); //restore the references to the correct player objects
				
				//if this is the current robot's tracker extra logic is required
				if (i == pn){
					rb.getOurPlayerTracker().partialUpdateFromTracker(pti);// update ourPlayerTracker
					SOCPlayer ourPlayer = gameClone.getPlayer(i);
					rb.getOurPlayerTracker().setPlayer(ourPlayer);//don't forget the player object in this playerTracker
					rb.setOurPlayerData(ourPlayer); // also ourPlayerData
				}
			}
    	}else{//only human players were participating in the original game, need to recreate the trackers
    		//first reestablish the links
    		for(int i = 0; i < gameClone.maxPlayers ; i++){
    			SOCPlayerTracker pt = (SOCPlayerTracker) rb.getPlayerTrackers().get(i);
    			SOCPlayer player = gameClone.getPlayer(i);
    			pt.setPlayer(player);
    			if (i == pn){
    				rb.getOurPlayerTracker().setPlayer(player);
    				rb.setOurPlayerData(player);
    				rb.getOurPlayerTracker().reinitTracker();
    			}
    			//and reinitialise the trackers
    			pt.reinitTracker();
    		}
    		
    		//try to recreate the treeMaps of possible pieces from the game object, by re-tracking everything;
    		//start with settlements;
    		Vector v = gameClone.getBoard().getSettlements();
    		for(Object o : v){
    			rb.trackNewSettlement((SOCSettlement)o, false);
    		}
    		//continue with roads;
    		v = gameClone.getBoard().getRoads();
    		for(Object o : v){
    			rb.trackNewRoad((SOCRoad)o, false);
    		}
    		//finish with cities
    		v = gameClone.getBoard().getCities();
    		for(Object o : v){
    			rb.trackNewCity((SOCCity)o, false);
    		}
    		//finally recalculate all ETA's
    		for(int i = 0; i < gameClone.maxPlayers ; i++){
    			((SOCPlayerTracker) rb.getPlayerTrackers().get(i)).recalculateAllEtas();
    			if (i == pn){
    				rb.getOurPlayerTracker().recalculateAllEtas();
    			}
    		}
    	}
		rb.game = gameClone; //for SOCRobotBrain we need to reference to the correct game object inside the brain
    	fileName = prefix + pn + "_" + StacRobotBrainInfo.class.getName();
    	StacRobotBrainInfo brainInfoClone = (StacRobotBrainInfo) DeepCopy.readFromFile(fileName); //read the brain info bytes
    	if(brainInfoClone != null){ //by ignoring this step I expect this loading mechanism to work only in a fraction of cases for now;
    		brainInfoClone.waitingForGameState = false; //there is absolutely no way we were waiting for the game state when saving  (how could this happen??)
    	}else{
    		//else we need to try and recreate the brain info from the game object;
    		brainInfoClone = new StacRobotBrainInfo(gameClone, pn);
    	}
    	rb.partialUpdateFromInfo(brainInfoClone); //update the brain's state and parameters
    	
    	//update dialogue mgr and declarative memory only if it is a Stac or a StacRandom brain type
    	if(brainType.equals(StacRobotBrain.class.getName()) || brainType.equals(StacRobotBrainRandom.class.getName())
    			|| brainType.equals(StacRobotBrainFlatMCTS.class.getName()) || brainType.equals(MCTSRobotBrain.class.getName())){
			fileName = prefix + pn + "_" + StacRobotDeclarativeMemory.class.getName();
	    	StacRobotDeclarativeMemory memoryClone = (StacRobotDeclarativeMemory) DeepCopy.readFromFile(fileName);  //read the DeclarativeMemory object
	    	if(memoryClone != null)
	    		((StacRobotBrain) rb).getMemory().partialUpdateFromMemory(memoryClone);//update the memory's info
	    	else{
	    		((StacRobotBrain) rb).getMemory().reinitMemory();//try and reinit the memory
	    	}
	    	if(brainType.equals(MCTSRobotBrain.class.getName())) {
	    		fileName = prefix + pn + "_" + CatanFactoredBelief.class.getName();
	    		CatanFactoredBelief beliefClone = (CatanFactoredBelief) DeepCopy.readFromFile(fileName);
		    	if(beliefClone != null)
		    		((MCTSRobotBrain) rb).setBelief(beliefClone);//update the memory's info
		    	else
		    		((MCTSRobotBrain) rb).reinitBeliefModel();
	    	}
	    	
	    	rb.startGameChat();//also announce chat participation again
    	}
    	
    	rb.recreateDM();       	//after finishing with updating all the info in the brain, recreate the DM from the new info
    	rb.negotiator.update();	//update the negotiator from the new info in the brain

    	//final piece of code required by the MCTS logic to gather results during simulations (TODO: (maybe) in here add some logic for shuffling the dev card stack and redistribute dev cards)
    	if(brainType.equals(StacRobotBrainRandom.class.getName()) && ((StacRobotBrainRandom)rb).getResults()!=null){
    		FlatMctsRewards stats = ((StacRobotBrainRandom)rb).getResults();
    		for(int i= 0; i< 4; i++){
    			stats.getInitialVPs()[i] = gameClone.getPlayer(i).getPublicVP(); //vp when loading
    			//recalculate estimates and ETW
    			SOCBuildingSpeedEstimate estimator = rb.getEstimator();
    			estimator.recalculateEstimates(gameClone.getPlayer(i).getNumbers());
                int[] speeds = estimator.getEstimatesFromNothingFast(gameClone.getPlayer(i).getPortFlags());
                int totalSpeed = 0;

                for (int j = SOCBuildingSpeedEstimate.MIN;
                        j < SOCBuildingSpeedEstimate.MAXPLUSONE; j++)
                {
                    totalSpeed += speeds[j];
                }
                
                stats.getInitialETBs()[i] = totalSpeed;
    			
//    			rb.getEstimator().recalculateEstimates(gameClone.getPlayer(i).getNumbers(), gameClone.getBoard().getRobberHex());
    			((SOCPlayerTracker) rb.getPlayerTrackers().get(i)).recalcWinGameETA();
    			stats.getInitialETWs()[i] = ((SOCPlayerTracker) rb.getPlayerTrackers().get(i)).getWinGameETA(); //etw when loading
    			stats.setMaxTotalRssBlocked(gameClone.getTotalPossibleBlockedRss()); //how many rss can be blocked per turn
    		}
    	}
    		
    	if(brainType.equals(MCTSRobotBrain.class.getName())) {
    		((MCTSRobotBrain)rb).generateBoard();
    	}
    	
    	//wake the brain up
    	rb.awaken();
//    	System.out.println("Robot Client "+ pn +": received load game request"); //for quick debugging
	}
    
    /**
     * Saves all the current game information and required data for recreating this robot's state:
     * <ul>
     * 	<li>Suspends the brain;
     * 	<li>{@link SOCGame} object (including the {@link SOCPlayer} objects);
     * 	<li>an array of {@link SOCPlayerTracker} (from the brain);
     * 	<li>{@link StacRobotDeclarativeMemory} if its a stac type brain;
     * 	<li>{@link StacRobotBrainInfo} which is the container with the brain state saved;
     * 	<li>Wakes the brain up.
     * <ul>
     * @param mes the received message
     */
	private void handleGAMECOPY(SOCGameCopy mes) {
		//get some information required for the saving procedure
		SOCRobotBrain rb = (SOCRobotBrain) this.robotBrains.get(mes.getGame());
		int pn = rb.getOurPlayerData().getPlayerNumber(); //get the player number
		if(pn != mes.getPlayerNumber()){ //if we aren't the player that initiated the save, then execute the safe synchronized saving method 
			//suspend brain
			CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());
	        if (brainQ != null)
	        {
	            try
	            {
	                brainQ.put(mes);
	            }
	            catch (CutoffExceededException exc)
	            {
	                D.ebugPrintlnINFO("CutoffExceededException" + exc);
	            }
	        }
	        //wait for brain to be suspended
	        while(!rb.isSuspended()){
	        	try {
					Thread.sleep(20);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
	        }
	        //now do the actual copying
	        unsynckedGameCopy(mes);
	    	
	        //wake the brain up
	        rb.awaken();
		}//otherwise do nothing as the brain would have taken care of the saving at the right moment; it would be too late to save in here
//		System.out.println("Robot Client " + pn + ": received gameCopy request"); //for quick debugging
	}
	/**
	 * Contains the actual logic for handling the game saving, but without the check for the player number and without suspending the brain
	 * @param mes the information regarding the folder locations
	 */
	public void unsynckedGameCopy(SOCGameCopy mes){
		//get some information required for the saving procedure
		String brainType = this.robotBrains.get(mes.getGame()).getClass().getName(); //get the robot brain type
		SOCRobotBrain rb = (SOCRobotBrain) this.robotBrains.get(mes.getGame());
		rb.saved = true;//block any further saves
		int pn = rb.getOurPlayerData().getPlayerNumber(); //get the player number
		SOCGame game = (SOCGame) this.games.get(mes.getGame());
		int n = game.maxPlayers;
		
		//clone an array that contains the PlayerTracker for each player in the game from this player's perspective
		ArrayList list = new ArrayList();
		for(int i = 0; i < n; i++){
			SOCPlayerTracker pt = (SOCPlayerTracker) rb.getPlayerTrackers().get(i);
			pt.recalcLargestArmyETA();pt.recalcLongestRoadETA();pt.recalcWinGameETA(); //for storing the ETAs for the special loading case
			list.add(pt);
		}
		DeepCopy.copyToFile(list, "" + pn, mes.getFolder()); //write the object to file
		//if stac brain type clone both the brainInfo container and the declarative memory
		if(brainType.equals(StacRobotBrain.class.getName()) || brainType.equals(StacRobotBrainRandom.class.getName())
				|| brainType.equals(StacRobotBrainFlatMCTS.class.getName()) || brainType.equals(MCTSRobotBrain.class.getName())){
			StacRobotBrainInfo brainInfo = rb.getInfo();
			DeepCopy.copyToFile(brainInfo, "" + pn, mes.getFolder());
			StacRobotDeclarativeMemory memory = ((StacRobotBrain) rb).getMemory();
			DeepCopy.copyToFile(memory, "" + pn, mes.getFolder());
			if(brainType.equals(MCTSRobotBrain.class.getName())) {
				CatanFactoredBelief belief = ((MCTSRobotBrain) rb).getBelief();
				if(belief != null)
					DeepCopy.copyToFile(belief, "" + pn, mes.getFolder());
			}
		}
		else{
			//else just the brainInfo container
			StacRobotBrainInfo brainInfo = rb.getInfo();
			DeepCopy.copyToFile(brainInfo, "" + pn, mes.getFolder());
		}
		//clone the SOCGame object last so we can check that the saving procedure is finished
		DeepCopy.copyToFile(game, "" + pn, mes.getFolder()); //write the game object to file
	} 
	
	//---MD end of handling methods for Save/Load function

    /**
     * handle the server ping message.
     * Echo back to server, to ensure we're still connected.
     * (ignored before version 1.1.08)
     *
     * @param mes  the message
     */
    protected void handleSERVERPING(SOCServerPing mes)
    {
        put(mes.toCmd());
        /*
           D.ebugPrintln("(*)(*) ServerPing message = "+mes);
           D.ebugPrintln("(*)(*) ServerPing sleepTime = "+mes.getSleepTime());
           D.ebugPrintln("(*)(*) resetThread = "+resetThread);
           resetThread.sleepMore();
         */
    }

    /**
     * handle the admin ping message
     * @param mes  the message
     */
    protected void handleADMINPING(SOCAdminPing mes)
    {
        D.ebugPrintlnINFO("*** Admin Ping message = " + mes);

        SOCGame ga = (SOCGame) games.get(mes.getGame());

        //
        //  if the robot hears a PING and is in the game
        //  where the admin is, then just say "OK".
        //  otherwise, join the game that the admin is in
        //
        //  note: this is a hack because the bot never 
        //        leaves the game and the game must be 
        //        killed by the admin
        //
        if (ga != null)
        {
            sendText(ga, "OK");
        }
        else
        {
            put(SOCJoinGame.toCmd(nickname, password, host, mes.getGame()));
        }
    }

    /**
     * handle the admin reset message
     * @param mes  the message
     */
    protected void handleADMINRESET(SOCAdminReset mes)
    {
        D.ebugPrintlnINFO("*** Admin Reset message = " + mes);
//        disconnectReconnect(); //this shouldn't be executed anyway but just to be safe
    }

    /**
     * handle the update robot params message
     * @param mes  the message
     */
    protected void handleUPDATEROBOTPARAMS(SOCUpdateRobotParams mes)
    {
        currentRobotParameters = new SOCRobotParameters(mes.getRobotParameters());
        D.ebugPrintlnINFO("*** current robot parameters = " + currentRobotParameters);
    }

    /**
     * handle the "join game request" message.
     * Remember the game options, and record in {@link #seatRequests}.
     * Send a {@link SOCJoinGame JOINGAME} to server in response.
     * Server will reply with {@link SOCJoinGameAuth JOINGAMEAUTH}.
     *<P>
     * Board resets are handled similarly.
     * @param mes  the message
     *
     * @see #handleRESETBOARDAUTH(SOCResetBoardAuth)
     */
    protected void handleJOINGAMEREQUEST(SOCJoinGameRequest mes)
    {
        D.ebugPrintlnINFO("**** handleJOINGAMEREQUEST ****");
	final String gaName = mes.getGame();
	Hashtable gaOpts = mes.getOptions();
	if (gaOpts != null)
	    gameOptions.put(gaName, gaOpts);

        seatRequests.put(gaName, Integer.valueOf(mes.getPlayerNumber()));
        if (put(SOCJoinGame.toCmd(nickname, password, host, gaName)))
        {
            D.ebugPrintlnINFO("**** sent SOCJoinGame ****");
        }
    }

    /**
     * handle the "status message" message by printing it to System.err;
     * messages with status value 0 are ignored (no problem is being reported)
     * once the initial welcome message has been printed.
     * @param mes  the message
     */
    protected void handleSTATUSMESSAGE(SOCStatusMessage mes)
    {
        final int sv = mes.getStatusValue();
        if ((sv != 0) || ! printedInitialWelcome)
        {
            System.err.println("Robot " + getNickname() + ": Status "
                + sv + " from server: " + mes.getStatus());
            if (sv == 0)
                printedInitialWelcome = true;
        }
    }

    /**
     * handle the "join game authorization" message
     * @param mes  the message
     */
    protected void handleJOINGAMEAUTH(SOCJoinGameAuth mes)
    {
        gamesPlayed++;

	final String gaName = mes.getGame();

	SOCGame ga = new SOCGame(gaName, true, (Hashtable) gameOptions.get(gaName));
        games.put(gaName, ga);

        CappedQueue brainQ = new CappedQueue();
        brainQs.put(gaName, brainQ);

        SOCRobotBrain rb = factory.getRobot(this, currentRobotParameters, ga, brainQ);
        robotBrains.put(gaName, rb);
        
        //pass on the information collected by the previous STAC brains
        if (rb instanceof StacRobotBrain) {
            ((StacRobotBrain) rb).setPersistentBrainInformation(persistentBrainInformation);
        }
    }

    /**
     * handle the "join game" message
     * @param mes  the message
     */
    protected void handleJOINGAME(SOCJoinGame mes) {}

    /**
     * handle the "game members" message, which indicates the entire game state has now been sent.
     * If we have a {@link #seatRequests} for this game, sit down now.
     * @param mes  the message
     */
    protected void handleGAMEMEMBERS(SOCGameMembers mes)
    {
        /**
         * sit down to play
         */
        Integer pn = (Integer) seatRequests.get(mes.getGame());

        try
        {
            //wait(Math.round(Math.random()*1000));
        }
        catch (Exception e)
        {
            ;
        }

        if (pn != null)
        {
            put(SOCSitDown.toCmd(mes.getGame(), nickname, pn.intValue(), true));
        } else {
            System.err.println("** Cannot sit down: Assert failed: null pn for game " + mes.getGame());
        }
    }

    /**
     * handle the "game text message" message
     * @param mes  the message
     */
    protected void handleGAMETEXTMSG(SOCGameTextMsg mes)
    {
        //D.ebugPrintln(mes.getNickname()+": "+mes.getText());
        if (mes.getText().startsWith(nickname))
        {
            handleGAMETEXTMSG_debug(mes);
        }

        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
     catch (CutoffExceededException exc)
            {
                D.ebugPrintlnINFO("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * Handle the "parse result" message.
     * Forward the string with the parse result to the brain playing the corresponding game.
     * @param mes  the message
     */
    protected void handlePARSERESULT(SOCParseResult mes) {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null) {
            try {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc) {
                D.ebugPrintlnINFO("CutoffExceededException" + exc);
            }
        }
    }
    
    /**
     * Handle debug text messages to the robot, which start with
     * the robot's nickname + ":".
     * @since 1.1.12
     */
    private final void handleGAMETEXTMSG_debug(SOCGameTextMsg mes)
    {
        final int nL = nickname.length();
        try
        {
            if (mes.getText().charAt(nL) != ':')
                return;
        } catch (IndexOutOfBoundsException e) {
            return;
        }
        final String dcmd = mes.getText().substring(nL);

        if (dcmd.startsWith(":debug-off"))
        {
            SOCGame ga = (SOCGame) games.get(mes.getGame());
            SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

            if (brain != null)
            {
                brain.turnOffDRecorder();
                sendText(ga, "Debug mode OFF");
            }
        }

        else if (dcmd.startsWith(":debug-on"))
        {
            SOCGame ga = (SOCGame) games.get(mes.getGame());
            SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

            if (brain != null)
            {
                brain.turnOnDRecorder();
                sendText(ga, "Debug mode ON");
            }
        }

        else if (dcmd.startsWith(":current-plans") || dcmd.startsWith(":cp"))
        {
            SOCGame ga = (SOCGame) games.get(mes.getGame());
            SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

            if ((brain != null) && (brain.getDRecorder().isOn()))
            {
                sendRecordsText(ga, brain.getDRecorder().getRecord(CURRENT_PLANS));
            }
        }

        else if (dcmd.startsWith(":current-resources") || dcmd.startsWith(":cr"))
        {
            SOCGame ga = (SOCGame) games.get(mes.getGame());
            SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

            if ((brain != null) && (brain.getDRecorder().isOn()))
            {
                sendRecordsText(ga, brain.getDRecorder().getRecord(CURRENT_RESOURCES));
            }
        }

        else if (dcmd.startsWith(":last-plans") || dcmd.startsWith(":lp"))
        {
            SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

            if ((brain != null) && (brain.getDRecorder().isOn()))
            {
                Vector record = brain.getOldDRecorder().getRecord(CURRENT_PLANS);

                if (record != null)
                {
                    SOCGame ga = (SOCGame) games.get(mes.getGame());
                    sendRecordsText(ga, record);
                }
            }
        }

        else if (dcmd.startsWith(":last-resources") || dcmd.startsWith(":lr"))
        {
            SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

            if ((brain != null) && (brain.getDRecorder().isOn()))
            {
                Vector record = brain.getOldDRecorder().getRecord(CURRENT_RESOURCES);

                if (record != null)
                {
                    SOCGame ga = (SOCGame) games.get(mes.getGame());
                    sendRecordsText(ga, record);
                }
            }
        }

        else if (dcmd.startsWith(":last-move") || dcmd.startsWith(":lm"))
        {
            SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

            if ((brain != null) && (brain.getOldDRecorder().isOn()))
            {
                SOCPossiblePiece lastMove = brain.getLastMove();

                if (lastMove != null)
                {
                    String key = null;

                    switch (lastMove.getType())
                    {
                    case SOCPossiblePiece.CARD:
                        key = "DEVCARD";

                        break;

                    case SOCPossiblePiece.ROAD:
                        key = "ROAD" + lastMove.getCoordinates();

                        break;

                    case SOCPossiblePiece.SETTLEMENT:
                        key = "SETTLEMENT" + lastMove.getCoordinates();

                        break;

                    case SOCPossiblePiece.CITY:
                        key = "CITY" + lastMove.getCoordinates();

                        break;
                    }

                    Vector record = brain.getOldDRecorder().getRecord(key);

                    if (record != null)
                    {
                        SOCGame ga = (SOCGame) games.get(mes.getGame());
                        sendRecordsText(ga, record);
                    }
                }
            }
        }

        else if (dcmd.startsWith(":consider-move ") || dcmd.startsWith(":cm "))
        {
            SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

            if ((brain != null) && (brain.getOldDRecorder().isOn()))
            {
                String[] tokens = mes.getText().split(" ");
                String key = null;

                if (tokens[1].trim().equals("card"))
                {
                    key = "DEVCARD";
                }
                else if (tokens[1].equals("road"))
                {
                    key = "ROAD" + tokens[2].trim();
                }
                else if (tokens[1].equals("settlement"))
                {
                    key = "SETTLEMENT" + tokens[2].trim();
                }
                else if (tokens[1].equals("city"))
                {
                    key = "CITY" + tokens[2].trim();
                }

                Vector record = brain.getOldDRecorder().getRecord(key);

                if (record != null)
                {
                    SOCGame ga = (SOCGame) games.get(mes.getGame());
                    sendRecordsText(ga, record);
                }
            }
        }

        else if (dcmd.startsWith(":last-target") || dcmd.startsWith(":lt"))
        {
            SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

            if ((brain != null) && (brain.getDRecorder().isOn()))
            {
                SOCPossiblePiece lastTarget = brain.getLastTarget();

                if (lastTarget != null)
                {
                    String key = null;

                    switch (lastTarget.getType())
                    {
                    case SOCPossiblePiece.CARD:
                        key = "DEVCARD";

                        break;

                    case SOCPossiblePiece.ROAD:
                        key = "ROAD" + lastTarget.getCoordinates();

                        break;

                    case SOCPossiblePiece.SETTLEMENT:
                        key = "SETTLEMENT" + lastTarget.getCoordinates();

                        break;

                    case SOCPossiblePiece.CITY:
                        key = "CITY" + lastTarget.getCoordinates();

                        break;
                    }

                    Vector record = brain.getDRecorder().getRecord(key);

                    if (record != null)
                    {
                        SOCGame ga = (SOCGame) games.get(mes.getGame());
                        sendRecordsText(ga, record);
                    }
                }
            }
        }

        else if (dcmd.startsWith(":consider-target ") || dcmd.startsWith(":ct "))
        {
            SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

            if ((brain != null) && (brain.getDRecorder().isOn()))
            {
                String[] tokens = mes.getText().split(" ");
                String key = null;

                if (tokens[1].trim().equals("card"))
                {
                    key = "DEVCARD";
                }
                else if (tokens[1].equals("road"))
                {
                    key = "ROAD" + tokens[2].trim();
                }
                else if (tokens[1].equals("settlement"))
                {
                    key = "SETTLEMENT" + tokens[2].trim();
                }
                else if (tokens[1].equals("city"))
                {
                    key = "CITY" + tokens[2].trim();
                }

                Vector record = brain.getDRecorder().getRecord(key);

                if (record != null)
                {
                    SOCGame ga = (SOCGame) games.get(mes.getGame());
                    sendRecordsText(ga, record);
                }
            }
        }

        else if (dcmd.startsWith(":print-vars") || dcmd.startsWith(":pv"))
        {
            // TODO sendText, not print at server
            debugPrintBrainStatus(mes.getGame());
            put(SOCGameTextMsg.toCmd(mes.getGame(), nickname,
                "Internal state printed at server console."));
        }

        else if (dcmd.startsWith(":stats"))
        {
            SOCGame ga = (SOCGame) games.get(mes.getGame());
            sendText(ga, "Games played:" + gamesPlayed);
            sendText(ga, "Games finished:" + gamesFinished);
            sendText(ga, "Games won:" + gamesWon);
            sendText(ga, "Clean brain kills:" + cleanBrainKills);
            sendText(ga, "Brains running: " + robotBrains.size());

            Runtime rt = Runtime.getRuntime();
            sendText(ga, "Total Memory:" + rt.totalMemory());
            sendText(ga, "Free Memory:" + rt.freeMemory());
        }

        else if (dcmd.startsWith(":gc"))
        {
            SOCGame ga = (SOCGame) games.get(mes.getGame());
            Runtime rt = Runtime.getRuntime();
            rt.gc();
            sendText(ga, "Free Memory:" + rt.freeMemory());
        }
        
    }

    /**
     * handle the "someone is sitting down" message
     * @param mes  the message
     */
    protected void handleSITDOWN(SOCSitDown mes)
    {
        /**
         * tell the game that a player is sitting
         */
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            ga.addPlayer(mes.getNickname(), mes.getPlayerNumber());

            /**
             * set the robot flag
             */
            ga.getPlayer(mes.getPlayerNumber()).setRobotFlag(mes.isRobot(), false);

            /**
             * let the robot brain find our player object if we sat down
             */
            if (nickname.equals(mes.getNickname()))
            {
                SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

                /**
                 * retrieve the proper face for our strategy
                 */
                int faceId;
                // TODO: Move this logic into the brain itself
                switch (brain.getRobotParameters().getStrategyType())
                {
                case SOCRobotDMImpl.SMART_STRATEGY:
                    faceId = -1;  // smarter robot face
                    break;

                default:
                    faceId = 0;   // default robot face
                }

                if(brain.getClass() == StacRobotBrain.class){
                	if(((StacRobotBrain)brain).isRobotType(StacRobotType.PLAYER_ICON)){
                		faceId = (int) ((StacRobotBrain)brain).getTypeParam(StacRobotType.PLAYER_ICON);
                		if(faceId > 73 || faceId < -1){
                			faceId = -1;//smarter face in case the wrong param was used
                		}
                	}else{
                      Random r = new Random();
                      faceId = r.nextInt(74) - 1;
                	}
                }

                
                brain.setOurPlayerData();
                brain.start();
                Server.trackThread(brain, this);

                /**
                 * change our face to the robot face
                 */
                put(SOCChangeFace.toCmd(ga.getName(), mes.getPlayerNumber(), faceId));
            }
            else
            {
                /**
                 * add tracker for player in previously vacant seat
                 */
                SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

                if (brain != null)
                {
                    brain.addPlayerTracker(mes.getPlayerNumber());
                }
            }
        }
    }

    /**
     * handle the "start game" message
     * @param mes  the message
     */
    protected void handleSTARTGAME(SOCStartGame mes) {
    	StacRobotBrain.setChatNegotiation(mes.getChatNegotiationsFlag());//this is set here as the game may be played over the network and not locally
    	SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());
    	if(mes.getLoadFlag())
    		handleLOADGAME(new SOCLoadGame(mes.getGame(),mes.getFolder()));
    	else
    		brain.startGameChat();
    	
    	if(brain.getClass().getName().equals(MCTSRobotBrain.class.getName())){
    		((MCTSRobotBrain)brain).generateBoard();
    	}
    	
    	if(brain.getClass().getName().equals(OriginalSSRobotBrain.class.getName())){
    		((OriginalSSRobotBrain)brain).sendGameToSmartSettlers();
    	}
    }

    /**
     * handle the "delete game" message
     * @param mes  the message
     */
    protected void handleDELETEGAME(SOCDeleteGame mes)
    {
        SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(mes.getGame());

        if (brain != null)
        {
            SOCGame ga = (SOCGame) games.get(mes.getGame());

            if (ga != null)
            {
                if (ga.getGameState() == SOCGame.OVER)
                {
                    gamesFinished++;

                    if (ga.getPlayer(nickname).getTotalVP() >= ga.getVpWinner())
                    {
                        gamesWon++;
                        // TODO: hardcoded, assumes 10 to win (VP_WINNER)
                    }
                }

                brain.kill();
                robotBrains.remove(mes.getGame());
                brainQs.remove(mes.getGame());
                games.remove(mes.getGame());
            }
        }
    }

    /**
     * handle the "game state" message
     * @param mes  the message
     */
    protected void handleGAMESTATE(SOCGameState mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

            if (brainQ != null)
            {
                try
                {
                    brainQ.put(mes);
                }
                catch (CutoffExceededException exc)
                {
                    D.ebugPrintlnINFO("CutoffExceededException" + exc);
                }
            }
        }
        
    }

    /**
     * handle the "set turn" message
     * @param mes  the message
     */
    protected void handleSETTURN(SOCSetTurn mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintlnINFO("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "set first player" message
     * @param mes  the message
     */
    protected void handleFIRSTPLAYER(SOCFirstPlayer mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintlnINFO("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "turn" message
     * @param mes  the message
     */
    protected void handleTURN(SOCTurn mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintlnINFO("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "player element" message
     * @param mes  the message
     */
    protected void handlePLAYERELEMENT(SOCPlayerElement mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintlnINFO("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle "resource count" message
     * @param mes  the message
     */
    protected void handleRESOURCECOUNT(SOCResourceCount mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintlnINFO("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "dice result" message
     * @param mes  the message
     */
    protected void handleDICERESULT(SOCDiceResult mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintlnINFO("CutoffExceededException" + exc);
            }
        }
    	
    }

    /**
     * handle the "put piece" message
     * @param mes  the message
     */
    protected void handlePUTPIECE(SOCPutPiece mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintlnINFO("CutoffExceededException" + exc);
            }

            SOCGame ga = (SOCGame) games.get(mes.getGame());

            if (ga != null)
            {
                // SOCPlayer pl = ga.getPlayer(mes.getPlayerNumber());
                // JDM TODO - Was this in stock client?
            }
        }
    }

    /**
     * handle the rare "cancel build request" message; usually not sent from
     * server to client.
     *<P>
     * - When sent from client to server, CANCELBUILDREQUEST means the player has changed
     *   their mind about spending resources to build a piece.  Only allowed during normal
     *   game play (PLACING_ROAD, PLACING_SETTLEMENT, or PLACING_CITY).
     *<P>
     *  When sent from server to client:
     *<P>
     * - During game startup (START1B or START2B): <BR>
     *       Sent from server, CANCELBUILDREQUEST means the current player
     *       wants to undo the placement of their initial settlement.  
     *<P>
     * - During piece placement (PLACING_ROAD, PLACING_CITY, PLACING_SETTLEMENT,
     *                           PLACING_FREE_ROAD1 or PLACING_FREE_ROAD2):
     *<P>
     *      Sent from server, CANCELBUILDREQUEST means the player has sent
     *      an illegal PUTPIECE (bad building location). Humans can probably
     *      decide a better place to put their road, but robots must cancel
     *      the build request and decide on a new plan.
     *<P>
     *      Our robot client sends this to the brain to act on.
     *
     * @param mes  the message
     */
    protected void handleCANCELBUILDREQUEST(SOCCancelBuildRequest mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintlnINFO("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "move robber" message
     * @param mes  the message
     */
    protected void handleMOVEROBBER(SOCMoveRobber mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintlnINFO("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "discard request" message
     * @param mes  the message
     */
    protected void handleDISCARDREQUEST(SOCDiscardRequest mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintlnINFO("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "choose player request" message
     * @param mes  the message
     */
    protected void handleCHOOSEPLAYERREQUEST(SOCChoosePlayerRequest mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintlnINFO("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "make offer" message
     * @param mes  the message
     */
    protected void handleMAKEOFFER(SOCMakeOffer mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintlnINFO("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "clear offer" message
     * @param mes  the message
     */
    protected void handleCLEAROFFER(SOCClearOffer mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintlnINFO("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "reject offer" message
     * @param mes  the message
     */
    protected void handleREJECTOFFER(SOCRejectOffer mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintlnINFO("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "accept offer" message
     * @param mes  the message
     */
    protected void handleACCEPTOFFER(SOCAcceptOffer mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintlnINFO("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "clear trade" message
     * @param mes  the message
     */
    protected void handleCLEARTRADEMSG(SOCClearTradeMsg mes) {}

    /**
     * handle the "development card count" message
     * @param mes  the message
     */
    protected void handleDEVCARDCOUNT(SOCDevCardCount mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintlnINFO("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "development card action" message
     * @param mes  the message
     */
    protected void handleDEVCARD(SOCDevCard mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintlnINFO("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "set played development card" message
     * @param mes  the message
     */
    protected void handleSETPLAYEDDEVCARD(SOCSetPlayedDevCard mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintlnINFO("CutoffExceededException" + exc);
            }
        }
    }

    /**
     * handle the "dismiss robot" message
     * @param mes  the message
     */
    protected void handleROBOTDISMISS(SOCRobotDismiss mes)
    {
        //SOCGame ga = (SOCGame) games.get(mes.getGame());
        //CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());
        String gaName = mes.getGame();
        leaveGame(gaName, "Dismissed", false);
    }

    /**
     * handle the "potential settlements" message
     * @param mes  the message
     */
    protected void handlePOTENTIALSETTLEMENTS(SOCPotentialSettlements mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintlnINFO("CutoffExceededException" + exc);
            }
        }
    }
    
    
    
    protected void handleGenericMessage(SOCMessageForGame mes)
    {
        CappedQueue brainQ = (CappedQueue) brainQs.get(mes.getGame());

        if (brainQ != null)
        {
            try
            {
                brainQ.put(mes);
            }
            catch (CutoffExceededException exc)
            {
                D.ebugPrintlnINFO("CutoffExceededException" + exc);
            }
        }
    }
    

    /**
     * handle the "change face" message
     * @param mes  the message
     */
    protected void handleCHANGEFACE(SOCChangeFace mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            SOCPlayer player = ga.getPlayer(mes.getPlayerNumber());
            player.setFaceId(mes.getFaceId());
        }
    }

    /**
     * handle the "longest road" message
     * @param mes  the message
     */
    protected void handleLONGESTROAD(SOCLongestRoad mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            if (mes.getPlayerNumber() == -1)
            {
                ga.setPlayerWithLongestRoad((SOCPlayer) null);
            }
            else
            {
                ga.setPlayerWithLongestRoad(ga.getPlayer(mes.getPlayerNumber()));
            }
        }
    }

    /**
     * handle the "largest army" message
     * @param mes  the message
     */
    protected void handleLARGESTARMY(SOCLargestArmy mes)
    {
        SOCGame ga = (SOCGame) games.get(mes.getGame());

        if (ga != null)
        {
            if (mes.getPlayerNumber() == -1)
            {
                ga.setPlayerWithLargestArmy((SOCPlayer) null);
            }
            else
            {
                ga.setPlayerWithLargestArmy(ga.getPlayer(mes.getPlayerNumber()));
            }
        }
    }

    /**
     * handle board reset
     * (new game with same players, same game name).
     * Destroy old Game object.
     * Take robotbrain out of old game, don't yet put it in new game.
     * Let server know we've done so, by sending LEAVEGAME via {@link #leaveGame(SOCGame, String, boolean)}.
     * Server will soon send a JOINGAMEREQUEST if we should join the new game.
     *
     * @param mes  the message
     * 
     * @see soc.server.SOCServer#resetBoardAndNotify(String, int)
     * @see soc.game.SOCGame#resetAsCopy()
     * @see #handleJOINGAMEREQUEST(SOCJoinGameRequest)
     */
    protected void handleRESETBOARDAUTH(SOCResetBoardAuth mes)
    {
        D.ebugPrintlnINFO("**** handleRESETBOARDAUTH ****");

        String gname = mes.getGame();
        SOCGame ga = (SOCGame) games.get(gname);
        if (ga == null)
            return;  // Not one of our games

        SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(gname);
        if (brain != null)
            brain.kill();
        leaveGame(ga, "resetboardauth", false);  // Same as in handleROBOTDISMISS
        ga.destroyGame();
    }

    @Override
    public void putPiece(SOCGame ga, SOCPlayingPiece pp)
    {
        super.putPiece(ga, pp);
        SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(ga.getName());
        if (brain.getClass().getName().equals(StacRobotBrain.class.getName())) {
            ((StacRobotBrain)brain).resetNumberOfOffersWithoutBuildingAction();
        }
        if (pp.getType() == SOCPlayingPiece.SETTLEMENT)
        {
        	
        	if(brain.getClass().getName().equals(OriginalSSRobotBrain.class.getName())){
        		((OriginalSSRobotBrain)brain).lastSettlement = pp.getCoordinates();
        	}
        }
    }
    
    /**
     * Call sendText on each string element of record.
     * @param ga Game to sendText to
     * @param record Strings to send, or null
     */
    protected void sendRecordsText(SOCGame ga, Vector record)
    {
        if (record != null)
        {
            Enumeration renum = record.elements();

            while (renum.hasMoreElements())
            {
                String str = (String) renum.nextElement();
                sendText(ga, str);
            }
        }
    }

    /**
     * Print brain variables and status for this game to {@link System#err},
     * by calling {@link SOCRobotBrain#debugPrintBrainStatus()}.
     * @param gameName  Game name; if no brain for that game, do nothing.
     * @since 1.1.13
     */
    public void debugPrintBrainStatus(String gameName)
    {
        SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(gameName);
        if (brain == null)
            return;
        brain.debugPrintBrainStatus();
    }

    /**
     * the user leaves the given game
     *
     * @param ga   the game
     * @param leaveReason reason for leaving
     */
    public void leaveGame(SOCGame ga, String leaveReason, boolean showDebugTrace)
    {
        if (ga != null)
        {
            leaveGame(ga.getName(), leaveReason, showDebugTrace);
        }
    }
    
    protected void leaveGame(String gaName, String leaveReason, boolean showDebugTrace) {
            
        SOCRobotBrain brain = (SOCRobotBrain) robotBrains.get(gaName);

        //store the information we want to pass on to the next robot brain for the next game
        if (brain instanceof StacRobotBrain) {
            persistentBrainInformation = ((StacRobotBrain) brain).getPersistentBrainInformation();
            //((StacRobotBrain) brain).showLearningResults();
        }

        brain.kill();    
        robotBrains.remove(gaName);
        games.remove(gaName);
        gameOptions.remove(gaName);
        brainQs.remove(gaName);
        seatRequests.remove(gaName);

        D.ebugPrintlnINFO("L1833 robot " + nickname + " leaving game " + gaName + " due to " + leaveReason);            
        if (showDebugTrace)
        {
            soc.debug.D.ebugFATAL(null, "Leaving game here");
            System.err.flush();
        }
        put(SOCLeaveGame.toCmd(nickname, host, gaName));
    }

    /**
     * add one the the number of clean brain kills
     */
    public void addCleanKill()
    {
        cleanBrainKills++;
    }

    /** losing connection to server; leave all games, then try to reconnect */
    public void destroy()
    {
        SOCLeaveAll leaveAllMes = new SOCLeaveAll();
        put(leaveAllMes.toCmd());
        disconnect();
        //disconnectReconnect(); do we ever need to reconnect? only running locally for now (NOTE: uncomment if this changes, but it will cause out of memory issues)
        if (ex != null) {
            // Comment out: this happen whenever the server is shut down.  Not a problem for local tests.
            //System.err.println("Reconnect to server failed: " + ex);
        }
    }
    
    public void kill(){
    	super.kill();
    	Server.killAllThreadsCreatedBy(this);//also kill all threads started by this thread
    	SOCServer.removeClient(nickname);
    }
    
    // get and set methods for HWU negotiators
    
    public void setSupervisedNegotiator( BayesianSupervisedLearner sltrader ) {
    	this.sltrader = sltrader;
    }
    
    public BayesianSupervisedLearner getSupervisedNegotiator() {
    	return sltrader;
    }
    
	public void setDeepNegotiator(SimpleAgent deeptrader) {
		this.deeptrader = deeptrader;
	}

	public SimpleAgent getDeepNegotiator() {
		return this.deeptrader;
	}

    public void setMDPNegotiator( LearningNegotiator mdp_negotiator ) {
    	this.mdp_negotiator = mdp_negotiator;
    }
    
    public LearningNegotiator getMDPNegotiator() {
    	return mdp_negotiator;
    }
    
    /**
     * for stand-alones
     */
    public static void main(String[] args)
    {
        if (args.length < 4)
        {
            System.err.println("Java Settlers robotclient " + Version.version() +
                    ", build " + Version.buildnum());
            System.err.println("usage: java soc.robot.SOCRobotClient host port_number userid password");
            return;
        }

        SOCRobotClient ex1 = new SOCRobotClient(new SOCDefaultRobotFactory(), args[0], Integer.parseInt(args[1]), args[2], args[3]);
        ex1.init();
    }
}
