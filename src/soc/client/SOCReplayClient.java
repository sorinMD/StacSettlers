package soc.client;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;
import java.util.Vector;
import java.util.List;

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
import soc.game.StacGameParameters;
import soc.message.SOCAcceptOffer;
import soc.message.SOCBoardLayout;
import soc.message.SOCChoosePlayer;
import soc.message.SOCClearGameHistory;
import soc.message.SOCDevCard;
import soc.message.SOCDiceResult;
import soc.message.SOCGameCopy;
import soc.message.SOCGameState;
import soc.message.SOCGameTextMsg;
import soc.message.SOCJoinGameAuth;
import soc.message.SOCLeaveGame;
import soc.message.SOCLoadGame;
import soc.message.SOCMessage;
import soc.message.SOCMoveRobber;
import soc.message.SOCNewGameWithOptions;
import soc.message.SOCPlayerElement;
import soc.message.SOCPutPiece;
import soc.message.SOCSitDown;
import soc.message.SOCStartGame;
import soc.message.SOCTurn;
import soc.robot.SOCBuildingSpeedEstimate;
import soc.robot.SOCBuildingSpeedFast;
import soc.robot.SOCPlayerTracker;
import soc.robot.SOCPossibleCity;
import soc.robot.SOCPossibleSettlement;
import soc.robot.SOCRobotClient;
import soc.robot.SOCRobotDMImpl;
import soc.robot.stac.StacRobotBrainRandom;
import soc.robot.stac.StacRobotDummyBrain;
import soc.robot.stac.StacRobotType;
import soc.server.database.stac.ExtGameStateRow;
import soc.server.database.stac.GameActionRow;
import soc.server.database.stac.ObsGameStateRow;
import soc.server.database.stac.StacDBHelper;
import soc.server.genericServer.StringConnection;
import soc.util.CappedQueue;
import soc.util.DeepCopy;
import soc.util.LogParser;
import soc.util.Queue;
import soc.util.SOCRobotParameters;
import soc.util.Version;

/**
 * Client to simulate observing a game based on a log file.  Extends SOCPlayerClient
 *  for UI elements, but overrides the practice server functionality.
 * @author kho30
 *
 */
public class SOCReplayClient extends SOCPlayerClient {
    
    /**
     * Should we write an augmented log file or not?
     */
    private static boolean AUG_LOG = false;
    
    //NOTE: If canExtract is set to false and extractOnly is set to true, while collect is set to true then the client will only collect the total bbp and trades
    //the logic later in the code will force this to happen if the values of the total bbp and trades are null; !!We should never have collect set to true while the other two set to false!!
    /**
     * Should we collect data from logs and store inside the db? 
     * NOTE: make sure you can connect to a postgres db which contains the overall tournament data (seasons, leagues, games and players tables)
     */
    private static boolean collect = false;
    /**
     * Maybe we have already gathered the observed raw data and we are only interested in extracting features. Requires collect and extract set to true.
     */
    private static boolean extractOnly = false;
    /**
     * Can we extract? This gets set when checking if the total number of bbps and trades are available for the game.
     */
    private static boolean canExtract = false; 
    
    private boolean repeatedDevCardMessage = false;
    
	public SOCReplayClient() {}

	public SOCReplayClient(boolean cp) {
		super(cp);
	}
    
	// don't bother with server connection - we're faking it.
	public synchronized void connect() { }
    
	private FileToQueue ftq = null;
	
	private static final String LOAD = "LOAD";
	
	private Frame parentFrame;
	
	/**
	 * 
	 */
	public void startPracticeGame(String practiceGameName, Hashtable gameOpts, boolean mainPanelIsActive)
    {
		String logName = practiceGameName;       
         
        prCli = new DumbStringConn();                   
       
        // local server will support per-game options
        if (so != null)
        	so.setEnabled(true);
        
        LogParser lp = LogParser.getParser(logName, AUG_LOG);
        
        if (lp != null) {
            ftq = new FileToQueue(this, lp);            
            practiceGameName = ftq.getGameName();
            
            //Act as if the game used chat negotiations. We really only want the side effect: 
            //This hides the trade offer panel in the SOCHandPanels and we're providing extra fields (total cards & resources)
            gamesParams.put(practiceGameName, new StacGameParameters(false, "", 0, -1, false, true, false, false));
            
        	// May take a while to start server & game.
            // The new-game window will clear this cursor.
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        
	        // Do some basic messages to start the window, which aren't in the log file
	        SOCNewGameWithOptions newGame = new SOCNewGameWithOptions(practiceGameName, (String) null, -1);
	        treat(newGame, true);
	        SOCJoinGameAuth auth = new SOCJoinGameAuth(practiceGameName, true);
	        treat(auth, true);
	        
	        // Now start the simulator        
	        new Thread(ftq).start();       
        }
        else {
        	// Error message?
        	status.setText("Unable to find log: " + logName);
        }
    }
	
	public void pause() {
		ftq.state = FileToQueue.PAUSE;		
	}
	public void play() {
		ftq.state = FileToQueue.PLAY;
		ftq.maxPause = 2000;
	}
	public void toText() {
		ftq.state = FileToQueue.TO_TEXT;
		ftq.maxPause = 0;
	}
	public void toTurn() {
		ftq.state = FileToQueue.TO_TURN;
		ftq.maxPause = 0;
	}
	public void toBreakPoint(String breakText) {
		ftq.breakText = breakText;
	    ftq.state = FileToQueue.TO_BREAK;
		ftq.maxPause = 0;
		
	}
	
	protected SOCPlayerInterface getPlayerInterface(String gaName, SOCGame ga) {
		return new SOCReplayInterface(gaName, this, ga);
	}
	
	// allow callers to set the nickname of this client, to simulate "being" a 
	//  given player and force the hand panel to be in "Player" mode.
	public void setNickname(String name) {
		nickname = name;
	}
	
	// Handle dev card - use special handling to ensure the card is removed from the list,
	//  since every player is actively shown
	protected void handleDEVCARD(SOCDevCard mes)
    {
		//make sure the second draw message contains Unknown dev card type so we can handle the fully observable game just as a normal one
		if(repeatedDevCardMessage){
			repeatedDevCardMessage = false;
			mes.setCardType(SOCDevCardConstants.UNKNOWN);
		}else if(mes.getAction() == SOCDevCard.DRAW) {
			repeatedDevCardMessage = true; 
		}
		
		super.handleDEVCARD(mes);

		SOCPlayerInterface pi = (SOCPlayerInterface) playerInterfaces.get(mes.getGame());
		SOCHandPanel hp = pi.getPlayerHandPanel(mes.getPlayerNumber());
        hp.updateDevCards();
        hp.updateValue(SOCHandPanel.VICTORYPOINTS);

        //We're only updating our own dev cards, so they are fully known and we must subtract the UNKNOWN cards.
        //However, the server sends two SOCDevCard messages, one for the affected player, on to the other players (where the card type is UNKNOWN).
        //It seems the replay client updates the SOCDevCardSet objects for known as well as unkonwn values, causing a doubling of the cards in the player's hand.
        //The solution is to subtract the unknown value from the total.
        hp.updateValue(SOCHandPanel.NUMDEVCARDS);
        int totalDCs = hp.getPlayer().getDevCards().getTotal();
        int unknownDCs = hp.getPlayer().getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.UNKNOWN);
        unknownDCs += hp.getPlayer().getDevCards().getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.UNKNOWN);
        totalDCs -= unknownDCs;
        hp.developmentSq.setIntValue(totalDCs);
    }
	
	protected void handleGAMECOPY(SOCGameCopy mes){
		//do nothing just avoid saving the game again
	}
	
	protected void handleLOADGAME(SOCLoadGame mes){
		//we are not going through the normal start of game here so we need to make sure everyone is sit down and create the dummy brain
		String prefix = mes.getFolder() + "/";
		String fileName = prefix + "server_" + SOCGame.class.getName();
		SOCGame ga = (SOCGame)DeepCopy.readFromFile(fileName);
		ftq.dummy = new StacRobotDummyBrain(new SOCRobotClient(null, "replay", "replayAgent", "", null),
				new SOCRobotParameters(300, 500, 0f, 0f, 0f, 0f, 0f, SOCRobotDMImpl.FAST_STRATEGY, 0),
				ga,new CappedQueue(),0);//need to update the playerNumber based on who is on the board
		ftq.boardInitialized = true;
		
		for(SOCPlayer p : ga.getPlayers())
			treat(new SOCSitDown(ga.getName(), p.getName(), p.getPlayerNumber(), false), true);
		//then do the normal update
		super.handleLOADGAME(mes);
	}
	
	/**
     * Copied directly from SOCPlayerClient, except the constructor of the client itself
     */
    public static void main(String[] args)
    {    
    	//simple way of checking for options
    	//TODO: include connecting to different db with a different usr/pass also
    	if(args.length > 0){
	    	String options = "";
	    	for(String s : args)
	    		options= options + s;
	    	if(options.contains("-c")){
	    		collect = true;
	    		System.out.println("collect");
	    	}
	    	if(options.contains("-eo")){
	    		extractOnly = true;
	    		System.out.println("extract only");
	    	}
	    	if(options.contains("-al")){
	    		AUG_LOG = true;
	    		System.out.println("augment log file");
	    	}
    	}
        SOCReplayClient client;
        boolean withConnectOrPractice;

        withConnectOrPractice = true;
        client = new SOCReplayClient(withConnectOrPractice);

        System.out.println("Java Settlers Replay Client " + Version.version() +
                ", build " + Version.buildnum() + ", " + Version.copyright());

        // Invisible frame for the player client, necessary to track games list, etc
        Frame frame = new Frame("JSettlers client " + Version.version());       
        client.initVisualElements(); // after the background is set        
        frame.add(client, BorderLayout.CENTER);
        frame.setVisible(false);
        
        // Actual frame for the replay
        client.parentFrame = new Frame("JSettlers Replay client");
        frame = client.parentFrame;
        frame.setBackground(new Color(Integer.parseInt("61AF71",16)));
        frame.setForeground(Color.black);
        // Add a listener for the close event
        frame.addWindowListener(client.createWindowAdapter());        
        frame.setSize(200, 100);        
        
        Button b = new Button("Load Replay");        
        frame.add(b);
        b.setActionCommand(LOAD);
        b.addActionListener(client);
        b.setLocation(0,  0);
        b.setEnabled(true);
        b.setVisible(true);
        frame.setVisible(true);
    }
    
    public void actionPerformed(ActionEvent e) {
        // Currently only one action...
        FileDialog fd = new FileDialog(parentFrame, "Load replay file");        
        fd.setVisible(true);       
        startPracticeGame(fd.getDirectory() + fd.getFile(), null, true);
    }
	
    /** 
     * Class to act as a fake server - just parse a log file and send to the client as appropriate
     * @author kho30
     *
     */
	protected static class FileToQueue implements Runnable {
		//variables required for moving all the game data from the logs into a db
		private int idCounter = 0; //states and actions IDs
		private int[][] tradesCounter = new int[4][4];
		private int[][] tempTradesCounter = new int[4][4]; //temp value as the counter is modified before the correct number of trades is passed to the egsr
		private int[][] pbpCounter = new int[4][6];
		private int[] playersIDs;
		private int gameID;
		//information that will be selected from the games table
		private Integer[][] totalPbps;
		private Integer[][] totalTrades;
		
		//instance to help with the db interface
		private StacDBHelper dbh = new StacDBHelper();
		
		//dummy brain to help with extracting some of the information from the raw state;
		private StacRobotDummyBrain dummy;
		private boolean boardInitialized = false;
		
		private LogParser lp;
		private final SOCReplayClient cl;
		
		private static final int PLAY = 1;
		private static final int PAUSE = 2;
		private static final int TO_TEXT = 3;
		private static final int TO_TURN = 6;
		private static final int TO_GAME = 4;
		private static final int TO_BREAK = 5;
		
		private int speedupFactor = 5; // factor by which to decrease pause length
		private long maxPause = 100;  // never pause longer than this
		
		private String breakText; // chat text to advance to when super-FF is used
		
		private int state = PAUSE; 
		
		public FileToQueue(SOCReplayClient cl, LogParser lp) {				
			this.lp = lp; 		
			this.cl = cl;
			//initialise the counters to 0
			Arrays.fill(tradesCounter[0],0);Arrays.fill(tradesCounter[1],0);Arrays.fill(tradesCounter[2],0);Arrays.fill(tradesCounter[3],0);
			Arrays.fill(pbpCounter[0],0);Arrays.fill(pbpCounter[1],0);Arrays.fill(pbpCounter[2],0);Arrays.fill(pbpCounter[3],0);
		}
		
		@Override
		public void run() {
			try {				
				// Put piece messages are problematic, since they affect the score.  Only handle putPiece immediately 
				//  after a server text message indicating that something has been built.
				boolean repeatedMsg = false;
				// Robber gets printed twice.  When we handle a "ChoosePlayer", ignore the next 2 resource messages
				int ignoreNextN = 0;	
				// Track the date of the last message for the purpose of pausing.  No pause before the first message
				long lastDate = Long.MAX_VALUE; 		
				// we want to avoid sleeping until the board has been initialized.  nothing
				//  interesting to see there
				boardInitialized = false;				
			
				//connect only if we want to collect
				if(collect){
					dbh.initialize();
					dbh.connect(); //try to connect to the db
				}
				
				while (!lp.eof()) {				    
					if (state != PAUSE) {
					    SOCMessage m = lp.parseLine();
					    if (m!=null) {
					        long date = lp.getMsgDate().getTime();
					        
							// Pause if the game has started.
							if (boardInitialized) {
								Thread.sleep(Math.min(maxPause, (date - lastDate) / speedupFactor));
							}
							lastDate = date;							
							
							// Track whether this is a player text message - that may trigger a pause, depending on state
							boolean isPlayerText = false;
							
							if (m instanceof SOCGameTextMsg) {
								SOCGameTextMsg gtm = (SOCGameTextMsg) m;
								if (!gtm.getNickname().equals("Server")) {
									// Mark that we've encountered player text - this may cause 
									//  the replay to pause
									isPlayerText = true;									
								}		
								else if (gtm.getText().contains("traded")) {
									// Echo trade messages in the chat interface as well as the game interface.  Much easier to follow.
									SOCGameTextMsg chatMsg = new SOCGameTextMsg(gtm.getGame(), "Trade", gtm.getText());
									cl.treat(chatMsg, true);
									isPlayerText = true;
									
									if (gtm.getText().contains("traded")) {
	                                    lp.writeAugLog("Game State after Trade Action: ");
	                                    //we need to know who has the player traded with and update the counters here
	                        			SOCPlayerInterface pi = (SOCPlayerInterface) cl.playerInterfaces.get(gtm.getGame());
	                                    SOCGame ga = pi.getGame();
	                                  //keep a reference to the old trade numbers
	                                    for(int i = 0; i < 4; i++)
	                                    	System.arraycopy(tradesCounter[i], 0, tempTradesCounter[i], 0, tradesCounter[i].length); 
	                        	        if(gtm.getText().contains("from the bank") || gtm.getText().contains("from a port")){
	                        		        tradesCounter[ga.getCurrentPlayerNumber()][ga.getCurrentPlayerNumber()]++;
	                        	        }else{
	                        	        	String[] text = gtm.getText().split("from ");
	                        	        	text[1] = text[1].substring(0, text[1].length()-1);//remove the "."
	                        	        	// uncomment for fix for the corrupted log file of game pilot20
//	                        	        	if(ga.getPlayer(text[1]) != null)
	                        	        		tradesCounter[ga.getCurrentPlayerNumber()][ga.getPlayer(text[1]).getPlayerNumber()]++;
//	                        	        	else
//	                        	        		tradesCounter[ga.getCurrentPlayerNumber()][ga.getPlayer("dummy").getPlayerNumber()]++;
	                        	        }
										writeGameState(gtm.getGame(), new double[]{GameActionRow.TRADE});
	                                }
								}else if(gtm.getText().contains("gets")){
									lp.writeAugLog("Game State after Roll Dice action: ");
									writeGameState(gtm.getGame(),new double[]{GameActionRow.ROLL}); //capture the roll dice result
								}else if(gtm.getText().contains("will move the robber")){
									lp.writeAugLog("Game State after Discard or Roll Dice or Play Soldier actions (exclusive or): ");
									writeGameState(gtm.getGame(),new double[]{GameActionRow.ROLL,GameActionRow.DISCARD,GameActionRow.PLAYKNIGHT}); //capture the discard/roll/play knight action result
								}else if(gtm.getText().contains("stole a resource")){
									lp.writeAugLog("Game State after both Move Robber and Choose Player actions (or just Choose Player): ");
									writeGameState(gtm.getGame(),new double[]{GameActionRow.CHOOSEPLAYER});
								}else if(gtm.getText().contains("You monopolized")){
									lp.writeAugLog("Game State after Play Monopoly Action: ");
									writeGameState(gtm.getGame(),new double[]{GameActionRow.PLAYMONO});
								}else if(gtm.getText().contains("received")){
									lp.writeAugLog("Game State after Play Discovery Action: ");
									writeGameState(gtm.getGame(),new double[]{GameActionRow.PLAYDISC});
								}else if(gtm.getText().contains("bought a development card")){
									lp.writeAugLog("Game State after Buy Dev Card Action: ");
									writeGameState(gtm.getGame(),new double[]{GameActionRow.BUYDEVCARD});
								}else if(gtm.getText().contains("has won the game")){
									lp.writeAugLog("Final game state: ");
									writeGameState(gtm.getGame(),new double[]{GameActionRow.WIN});
								}
								
								if (state == TO_BREAK) {
								    if (breakText.length() > 0 && gtm.getText().contains(breakText)) {
								        state = PAUSE;
								    }
								}
							}							
							else if (m instanceof SOCGameState) {
								SOCGameState sm = (SOCGameState) m;
								if (sm.getState() == SOCGame.PLACING_ROBBER) {
									// Robber is moving - ignore the next two resource messages, since robberies
									//  are reported twice in the logs.
									ignoreNextN = 2;
								}
								else if (sm.getState() == SOCGame.PLAY1) {
									// This is in case the player doesn't actually steal resources, 
									//  either voluntarily, or because nobody has any.
									ignoreNextN = 0;
								}else if (sm.getState() == SOCGame.WAITING_FOR_DISCARDS){
									lp.writeAugLog("Game State only after Rolling a 7(roll Dice) Action: ");
									writeGameState(sm.getGame(),new double[]{GameActionRow.ROLL}); //capture the rolled a seven result just before having to discard
								}else if (sm.getState() == SOCGame.PLACING_FREE_ROAD1){
									lp.writeAugLog("Game State only after Play Road Building Card Action: ");
									writeGameState(sm.getGame(),new double[]{GameActionRow.PLAYROAD}); //capture the play road building card result
								}else if (sm.getState() == SOCGame.WAITING_FOR_CHOICE){
									lp.writeAugLog("Game State only after Moving The Robber Action: ");
									writeGameState(sm.getGame(),new double[]{GameActionRow.MOVEROBBER}); //capture the move robber result
								}
							}							
							if (m != null) {
								if (m instanceof SOCBoardLayout) {
									//also initialise the dummy brain in here as we will need it later
                        			SOCPlayerInterface pi = (SOCPlayerInterface) cl.playerInterfaces.get(((SOCBoardLayout) m).getGame());
                                    SOCGame ga = pi.getGame();
									dummy = new StacRobotDummyBrain(new SOCRobotClient(null, "replay", "replayAgent", "", null),
											new SOCRobotParameters(300, 500, 0f, 0f, 0f, 0f, 0f, SOCRobotDMImpl.FAST_STRATEGY, 0),
											(SOCGame)DeepCopy.copy(ga),new CappedQueue(),0);//need to update the playerNumber based on who is on the board
									boardInitialized = true;
									//get the game and the players IDs here
									if(dbh.isConnected()){
										gameID = dbh.getIDfromGameName(((SOCBoardLayout) m).getGame());
										if(gameID != -1){ //if we can find the game
											playersIDs = dbh.getPlayersIDsFromGame(gameID);
											//at the beginning of the game we want to decide what we can collect
											if(dbh.areAnyTotalNumbersCollected(gameID)){//and data exists in games table
												canExtract = true;
												//get it here
												totalPbps = dbh.getTotalPBPs(gameID);
												totalTrades = dbh.getTotalTrades(gameID);
											}else{
												//we shouldn't extract or collect observable until overall stats are collected
												canExtract = false;
												extractOnly = true; //these two values are in contradiction so we will only collect overall stats
											}
											
											//create tables also
											if((!extractOnly) && (!dbh.tableExists(StacDBHelper.OBSFEATURESTABLE + gameID)))
												dbh.createRawStateTable(gameID);
											if((!extractOnly) && (!dbh.tableExists(StacDBHelper.ACTIONSTABLE + gameID)))
												dbh.createActionTable(gameID);
											if((canExtract) && (!dbh.tableExists(StacDBHelper.EXTFEATURESTABLE + gameID)))
												dbh.createExtractedStateTable(gameID);
										}
										else {
											dbh.disconnect(); //do not permit any collection further as game doesn't exist in db TODO create it here?
											System.err.println("Cannot find game details inside database");
										}
									}	
								}		
								
								if (m instanceof SOCPutPiece) {						
									// Only handle every second PutPiece or second move robber, as they're duplicated in the logs,
									//  which results in doubling of score or doubling an action
									if (repeatedMsg) {
										cl.treat(m, true);
										lp.writeAugLog("Game State after Put Piece Action: ");
										//decide what type of piece we built
										double at;
										if(((SOCPutPiece) m).getPieceType() == SOCPlayingPiece.ROAD)
											at = GameActionRow.BUILDROAD;
										else if(((SOCPutPiece) m).getPieceType() == SOCPlayingPiece.SETTLEMENT)
											at = GameActionRow.BUILDSETT;
										else
											at = GameActionRow.BUILDCITY;
										//in here update the game object in the brain and update trackers and this should be it
	                        			SOCPlayerInterface pi = (SOCPlayerInterface) cl.playerInterfaces.get(((SOCPutPiece) m).getGame());
	                                    SOCGame ga = pi.getGame();
										dummy.setGame((SOCGame)DeepCopy.copy(ga));
										dummy.handlePUTPIECE_updateTrackers((SOCPutPiece)m);
										writeGameState(((SOCPutPiece) m).getGame(),new double[]{at}); //capture the result of buying and building actions
									}
								}else if (m instanceof SOCTurn){
									cl.treat(m, true);
									lp.writeAugLog("Game State after End Turn Action: ");
									writeGameState(((SOCTurn) m).getGame(),new double[]{GameActionRow.ENDTURN}); //need to capture the result of the end turn action
                                                                        if (state == TO_TURN) {
                                                                            state = PAUSE;
                                                                        }
                                                                        SOCReplayInterface pi = (SOCReplayInterface) cl.playerInterfaces.get(((SOCTurn) m).getGame());
                                                                        SOCGame ga = pi.getGame();
                                                                        pi.replayPanel.turnLab.setText("Turn: " + ga.getTurnCount());
								}else if (m instanceof SOCPlayerElement) {
									SOCPlayerElement pe = (SOCPlayerElement) m;
									// Ignore the "unknown" discard - it will be accompanied by known discard messages,
									//  which we will handle
									if (pe.getElementType() != SOCPlayerElement.UNKNOWN) {										
										int action = pe.getAction();
										if (action == SOCPlayerElement.GAIN 
											|| action == SOCPlayerElement.LOSE) {
											// determine whether we're ignoring duplicated robber messages
											if (ignoreNextN > 0) {
												ignoreNextN--;
											}
											else {
												cl.treat(m, true);
											}
										}
										else {
											// always handle 100s (Set resource- no worries about duplicates here)
											cl.treat(m, true);
										}
									}
                                                                        SOCPlayerInterface pi = (SOCPlayerInterface) cl.playerInterfaces.get(pe.getGame());
                                                                        SOCHandPanel hp = pi.getPlayerHandPanel(pe.getPlayerNumber());
                                                                        hp.updateValue(SOCHandPanel.NUMRESOURCES);
								}
								else if (m instanceof SOCClearGameHistory 
										|| m instanceof SOCLeaveGame) {								
									// we don't want to handle this - leave the game history alone in replay mode,
									// don't let people leave the game, as it clears their panel.
								}
								else {		
									// Default - treat the message if it's not a special case
									cl.treat(m, true);
								}	
								
								repeatedMsg = false;
								if (m instanceof SOCGameTextMsg) {
									SOCGameTextMsg gtm = (SOCGameTextMsg) m;
									if (gtm.getNickname().equals("Server") && gtm.getText().contains(" built a ")) {
										// Remember that the last message was a server notification of a build
										//  so that we handle the next PutPiece
										repeatedMsg = true;
									}											
								}else if(m instanceof SOCMoveRobber){
									repeatedMsg = true;//also remember that we already moved the robber so no need to write the game state again
								}	
								// Now see if we want to pause based on current state and type of message
								if (isPlayerText && state==TO_TEXT) {
									state = PAUSE;
								}
							}
						}
					}
					else {
						// we're paused: wait for the user to press a button
						Thread.sleep(100);
					}
				}
				lp.close();
			} catch (Exception e) {
				e.printStackTrace();
				lp.close();
			}
			if(dbh.isConnected())
				dbh.disconnect();
		}
		
		// Output the game-state to the augmented log file
		private void writeGameState(String gameName, double[] actionTypes) {        
			SOCPlayerInterface pi = (SOCPlayerInterface) cl.playerInterfaces.get(gameName);
            SOCGame ga = pi.getGame();
            
            // Game layout
            SOCBoard board = ga.getBoard();
            int[] hexes = board.getHexLayout();
            int[] numbers = board.getNumberLayout();
            int robber = board.getRobberHex();   
	        
            //increment idcounter first as id=0 is only the state before starting the game which purpose is to aid in tracking the start game action	        
	        idCounter++;
	        lp.writeAugLog("|Action from stateID:" + (idCounter-1) + " to stateID:" + idCounter);
	        
            //create instance for observable features table
	        ObsGameStateRow ogsr = ga.turnCurrentStateIntoOGSR();
	        ogsr.setID(idCounter);
            //create instance for game actions table
	        GameActionRow gar = new GameActionRow(idCounter);
	        //create instance for extracted features table
	        ExtGameStateRow egsr = dummy.turnStateIntoEGSR();
	        egsr.setID(idCounter);
	        
	        //print action type
	        double actionType = 0;
	        if(actionTypes.length > 0){
	        	if(actionTypes.length == 1)
		        	actionType = actionTypes[0];
	        	else{ //there is only one case(just before moving the robber) when it is difficult to determine what the previous action was
	        		if(ga.getOldGameState()==SOCGame.PLAY){
		        		if(ga.getCurrentDice() == 7)
		        			actionType = GameActionRow.ROLL;
		        		else//if dice result is 0
		        			actionType = GameActionRow.PLAYKNIGHT;
		        	}else if(ga.getOldGameState()==SOCGame.WAITING_FOR_DISCARDS)
		        		actionType = GameActionRow.DISCARD;
		        	else if(ga.getOldGameState()==SOCGame.PLAY1)
		        		actionType = GameActionRow.PLAYKNIGHT;
	        	}
	        	lp.writeAugLog("|ActionType:" + actionType);
	        }//if == 0 doesn't print anything, but it needs to be initialised at least
	        
	        //add the pastBpps to the egsr before incrementing them
	        egsr.setPastPBPs(StacDBHelper.transformToIntegerArr2(pbpCounter));
	        //add the past trades
	        egsr.setPastTrades(StacDBHelper.transformToIntegerArr2(tempTradesCounter));
	        //calculate the futureBpp by subtracting the current from the total
	        if(canExtract){
		        int[][] tempBpp = new int[4][6];
		        for(int i = 0; i < 4; i++)
		        	for(int j = 0; j < 6; j++)
		        		tempBpp[i][j] = totalPbps[i][j] - pbpCounter[i][j];
		        egsr.setFuturePBPs(StacDBHelper.transformToIntegerArr2(tempBpp));
		        int[][] tempTrades = new int[4][4];
		        for(int i = 0; i < 4; i++)
		        	for(int j = 0; j < 4; j++)
		        		tempTrades[i][j] = totalTrades[i][j] - tempTradesCounter[i][j];
		        egsr.setFutureTrades(StacDBHelper.transformToIntegerArr2(tempTrades));
	        }else{
	        	//to avoid a nullpointer when printing out
	        	egsr.setFuturePBPs(StacDBHelper.transformToIntegerArr2(pbpCounter));
	        	egsr.setFutureTrades(StacDBHelper.transformToIntegerArr2(tempTradesCounter));
	        }
	        
	        //increment bpps counter (the order is the same as in StacPossibleBuildPlan)
	        int cpn = ga.getCurrentPlayerNumber();
	        if(actionType == GameActionRow.BUILDROAD){
	        	pbpCounter[cpn][0]++;
	        	pbpCounter[cpn][4]++; //if a player builds a road we assume is or will be going for the longest road
	        }else if(actionType == GameActionRow.BUILDSETT){
	        	pbpCounter[cpn][1]++;
	        }else if(actionType == GameActionRow.BUILDCITY)
	        	pbpCounter[cpn][2]++;
	        else if(actionType == GameActionRow.BUYDEVCARD){
	        	pbpCounter[cpn][3]++;
	        }else if(actionType == GameActionRow.PLAYKNIGHT)
	        	pbpCounter[cpn][5]++; //if a player plays a knight we assume is going for the largest army 
	        
	        //output the counter values for bpp and trades :) 
	        lp.writeAugLog("|bpp counter player 0: " + Arrays.toString(pbpCounter[0]));
	        lp.writeAugLog("|bpp counter player 1: " + Arrays.toString(pbpCounter[1]));
	        lp.writeAugLog("|bpp counter player 2: " + Arrays.toString(pbpCounter[2]));
	        lp.writeAugLog("|bpp counter player 3: " + Arrays.toString(pbpCounter[3]));
	        
	        lp.writeAugLog("|trades counter player 0: " + Arrays.toString(tradesCounter[0]));
	        lp.writeAugLog("|trades counter player 1: " + Arrays.toString(tradesCounter[1]));
	        lp.writeAugLog("|trades counter player 2: " + Arrays.toString(tradesCounter[2]));
	        lp.writeAugLog("|trades counter player 3: " + Arrays.toString(tradesCounter[3]));
	        
	        //add the info to the instances as it is printed to the aug_log file;
	        gar.setType(actionType);
	        gar.setAfterState(idCounter);
	        gar.setBeforeState(idCounter - 1);
            
	        //print board layout
	        lp.writeAugLog("|" + (new SOCBoardLayout(ga.getName(), hexes, numbers, robber)).toString());
	        
	        //number of Dev cards left in the deck
	        lp.writeAugLog("|DevCardsLeftInDeck|" + ga.getNumDevCards());
	        
	        //starting player number
	        lp.writeAugLog("|StartingPlayerNumber|" + ga.getFirstPlayer());
	        
	        //current player number
	        lp.writeAugLog("|CurrentPlayerNumber|" + cpn);
	        
	        //has current player played a dev card (only allowed once per turn)
	        lp.writeAugLog("|PlayedDevCard|" + ga.getPlayer(ga.getCurrentPlayerNumber()).hasPlayedDevCard());
	        
	        //dice result
	        lp.writeAugLog("|DiceResult|" + ga.getCurrentDice());
	        
	        //Game state
	        lp.writeAugLog("|Game State|" + ga.getGameState());
	        
	        // For each player (including null players)
	        for (SOCPlayer p : ga.getPlayers()) {
	            String pName = p.getName();
	            int pn = p.getPlayerNumber();
	            
	            //in here check the players ids by finding them again in the db? (for players inexistent in the db, just add -1 for now); this allows me to check for inconsistencies
	            if(dbh.isConnected())
	            if(pName!=null){
	            	if(pName.equals(dbh.getPlayerNameByID(playersIDs[pn]))){ 
	            		//what this says is that if the name is the same as the one we have in the database than it is ok
	            		ogsr.setPlayerID(pn, playersIDs[pn]);
	            	}else if(dbh.getPlayerNameByID(playersIDs[pn]).equals("Dummy")){ //placeholder for players that have only played 1 game in total;(we won't be doing any modelling on just one game)
	            		ogsr.setPlayerID(pn, 0);
	            	}else{
	            		ogsr.setPlayerID(pn, -1);//there is an inconsistency so leave it -1 so it will stand out when I check
	            	}
	            }else
	            	ogsr.setPlayerID(pn, -1);//also null players have id of -1...
	            
	            String logMsg;
	            Iterator i;
	            // Cities
	            logMsg = pName + "|Cities|";
	            i = p.getCities().iterator();
	            while (i.hasNext()) {
	                SOCCity c = (SOCCity) i.next();
	                logMsg += Integer.toHexString(c.getCoordinates()) + ",";
	            }	            
	            lp.writeAugLog(logMsg);
	            
	            // Settlements
	            logMsg = pName + "|Settlements|";
	            i = p.getSettlements().iterator();
	            while (i.hasNext()) {
	                SOCSettlement s = (SOCSettlement) i.next();
	                logMsg += Integer.toHexString(s.getCoordinates()) + ",";
	            }
	            lp.writeAugLog(logMsg);
	            
	            // Roads
	            logMsg = pName + "|Roads|";
	            i = p.getRoads().iterator();
	            while (i.hasNext()) {
	                SOCRoad r = (SOCRoad) i.next();
	                logMsg += Integer.toHexString(r.getCoordinates()) + ",";
	            }
	            lp.writeAugLog(logMsg);
	            
	            // Dev cards total
	            SOCDevCardSet set = p.getDevCards();
	            lp.writeAugLog(pName + "|TotalDevCards|" + set.getTotal());
	            
	            //Unplayed Dev cards types and numbers (an array of integers: knights, roads, disc, mono, unknown)
	            int[] udc = new int[]{set.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT),
	            		set.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.ROADS),
	            		set.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.DISC),
	            		set.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.MONO),
	            		set.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.UNKNOWN)};
	            lp.writeAugLog(pName + "|UnplayedDevCards|" + udc[0] + "," + udc[1] + "," + udc[2] + "," + udc[3] + "," + udc[4]);
	            
	            //Newly bought Dev cards
	            int[] ndc = new int[]{set.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.KNIGHT),
	            		set.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.ROADS),
	            		set.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.DISC),
	            		set.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.MONO),
	            		set.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.UNKNOWN)};
	            lp.writeAugLog(pName + "|NewDevCards|" + ndc[0] + "," + ndc[1] + "," + ndc[2] + "," + ndc[3] + "," + ndc[4]);
	            
	            //VP Dev cards
	            lp.writeAugLog(pName + "|VPDevCards|" + set.getNumVPCards());
	            
	            // Num-knights
	            int nk = p.getNumKnights();	            
	            lp.writeAugLog(pName + "|PlayedKnights|" + nk);
	            
	            //Total VPs
	            logMsg = pName + "|TotalVictoryPoints|" + p.getTotalVP();
	            lp.writeAugLog(logMsg);
	            
	            //Public VPs
	            logMsg = pName + "|PublicVictoryPoints|" + p.getPublicVP();
	            lp.writeAugLog(logMsg);
	            
	            //labels:LA,LR
	            lp.writeAugLog(pName + "|Labels|" + ((p.hasLargestArmy()) ? 1 : 0) + ((p.hasLongestRoad()) ? 1 : 0));
	            
	            //position on the board(Player number)
	            lp.writeAugLog(pName + "|PlayerNumber|" + p.getPlayerNumber());
	            
	            //Resources currently in hand (an array of numbers: clay, ore, sheep, wheat, wood, unknown)
	            logMsg = p.getResources().toShortString();
	            lp.writeAugLog(pName + "|Resources|" + logMsg.split(":")[1]);
	            
	            //Pieces left to build (array of numbers: roads, settlements, cities) 
	            lp.writeAugLog(pName + "|PiecesLeftToBuild|" +  p.getNumPieces(SOCPlayingPiece.ROAD) + "," 
	            		+ p.getNumPieces(SOCPlayingPiece.SETTLEMENT) + "," + p.getNumPieces(SOCPlayingPiece.CITY));
	            
	            //Access to what numbers on the board
	            SOCPlayerNumbers playerNumbers = p.getNumbers();
	            lp.writeAugLog(pName + "|TouchingNumbers|" + playerNumbers.toString());
	            
	            //Access to what resources type on the board(not sure if we need this here, as it can be deduced)
	            int[] trt = new int[]{0,0,0,0,0};
	            logMsg = pName + "|TouchingResourceTypes|";
	            if(!playerNumbers.getNumbersForResource(SOCResourceConstants.CLAY).isEmpty()){
	            	logMsg = logMsg + SOCResourceConstants.CLAY + ",";
	            	trt[0]++;
	            }if(!playerNumbers.getNumbersForResource(SOCResourceConstants.ORE).isEmpty()){
	            	logMsg = logMsg + SOCResourceConstants.ORE + ",";
	            	trt[1]++;
	        	}if(!playerNumbers.getNumbersForResource(SOCResourceConstants.SHEEP).isEmpty()){
	            	logMsg = logMsg + SOCResourceConstants.SHEEP + ",";
	            	trt[2]++;
				}if(!playerNumbers.getNumbersForResource(SOCResourceConstants.WHEAT).isEmpty()){
	            	logMsg = logMsg + SOCResourceConstants.WHEAT + ",";
	            	trt[3]++;
				}if(!playerNumbers.getNumbersForResource(SOCResourceConstants.WOOD).isEmpty()){
	            	logMsg = logMsg + SOCResourceConstants.WOOD;
	            	trt[4]++;
				}
	            lp.writeAugLog(logMsg);
	            
	            //Access to what port types
	            logMsg = pName + "|TouchingPortsTypes|";
	            int[] tpt = new int[]{0,0,0,0,0,0};
	            boolean[] portFlags = p.getPortFlags();
	            for(int j = 0; j < portFlags.length - 1; j++){
	            	if(portFlags[j]){
	            		logMsg = logMsg + j + ",";
	            		tpt[j]++;
	            	}
	            }
	            if(portFlags[portFlags.length-1]){
	            	logMsg = logMsg + (portFlags.length-1);
	            	tpt[portFlags.length-1]++;
	            }
	            lp.writeAugLog(logMsg);
	         
	        }
	        
	        //debug to check that values are the same
            lp.writeAugLog(gar.toString());
            lp.writeAugLog(ogsr.toString());
            lp.writeAugLog(egsr.toString());
            
	        //then commit to the db the obs and action rows
            if(dbh.isConnected()){
            	if((!extractOnly) && canExtract){
		        dbh.insertRawState(gameID, ogsr);
		        dbh.insertAction(gameID, gar);
            	}else if(canExtract && extractOnly){
    		        dbh.insertExtractedState(gameID, egsr);
            	}
            	//otherwise do not insert either, but always update the total bpp and total trades if connected and are collecting. 
		        if(actionType == GameActionRow.WIN && collect){
		        	dbh.updateTotalPBP(gameID, pbpCounter);
		        	dbh.updateTotalTrades(gameID, tradesCounter);
		        }
            }
		}
		
		public String getGameName() {
		    if (lp.getGameName() == null) {
		        lp.parseLine();
		    }
			return lp.getGameName();
		}
		
	}
	
	/**
	 * Pretend to be a local string connection - suck in messages and do nothing with them.
	 * @author kho30
	 *
	 */
	protected static class DumbStringConn implements StringConnection {
		
		@Override
		public String host() {
			return null;
		}

		@Override
		public void put(String str) throws IllegalStateException {
		}

		@Override
		public void run() {}

		@Override
		public boolean isConnected() {
			return true;
		}

		@Override
		public boolean connect() {
			return true;
		}

		@Override
		public void disconnect() {}

		@Override
		public void disconnectSoft() {}

		@Override
		public Object getData() {
			return null;
		}

		@Override
		public Object getAppData() {
			return null;
		}

		@Override
		public void setData(Object data) {}

		@Override
		public void setAppData(Object data) {}

		@Override
		public Exception getError() {
			return null;
		}

		@Override
		public Date getConnectTime() {
			return null;
		}

		@Override
		public int getVersion() {
			return 0;
		}

		@Override
		public void setVersion(int version) {	}

		@Override
		public void setVersion(int version, boolean isKnown) {	}

		@Override
		public boolean isVersionKnown() {
			return false;
		}

		@Override
		public void setVersionTracking(boolean doTracking) {
		}

		@Override
		public boolean isInputAvailable() {
			return false;
		}

		@Override
		public boolean wantsHideTimeoutMessage() {
			return false;
		}

		@Override
		public void setHideTimeoutMessage(boolean wantsHide) {}

		public DumbStringConn() {}

	}

	

}
