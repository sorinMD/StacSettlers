package soc.client;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import javax.swing.JFrame;
import javax.swing.JTextArea;


import aima.core.util.MockRandomizer;
import mcts.game.catan.Catan;
import representation.FVGenerator;
import representation.FVGeneratorFactory;
import representation.NumericalFeatureVectorOffsets;
import soc.client.SOCReplayClient.DumbStringConn;
import soc.client.SOCReplayClient.FileToQueue;
import soc.game.SOCBoard;
import soc.game.SOCCity;
import soc.game.SOCDevCardConstants;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCRoad;
import soc.game.SOCSettlement;
import soc.message.SOCBoardLayout;
import soc.message.SOCChangeFace;
import soc.message.SOCDevCard;
import soc.message.SOCDevCardCount;
import soc.message.SOCDiceResult;
import soc.message.SOCGameState;
import soc.message.SOCGameTextMsg;
import soc.message.SOCJoinGameAuth;
import soc.message.SOCLargestArmy;
import soc.message.SOCLoadGame;
import soc.message.SOCLongestRoad;
import soc.message.SOCMoveRobber;
import soc.message.SOCNewGameWithOptions;
import soc.message.SOCPlayerElement;
import soc.message.SOCPutPiece;
import soc.message.SOCSetPlayedDevCard;
import soc.message.SOCSitDown;
import soc.message.SOCTurn;
import soc.server.database.stac.ExtGameStateRow;
import soc.server.database.stac.GameActionRow;
import soc.server.database.stac.ObsGameStateRow;
import soc.server.database.stac.StacDBHelper;
import soc.server.database.stac.StacDBToCatanInterface;
import soc.util.LogParser;
import soc.util.Version;

/**
 * Test class for checking information is stored correctly in the db from the logs. It just replays the game by using the observable state features
 * and game actions. It doesn't check against the extracted game features and it doesn't display the dialog as the chat is not saved in the db.
 * Note: To make sure it makes sense compare against a normal replay of the game and let me know if you spot any bugs.
 * 
 * @author MD
 *
 */
public class StacDBReplayClient extends SOCReplayClient{

	StacDBHelper dbh;
	JTextArea textName;
	int gameID;
	
	public StacDBReplayClient() {dbh = new StacDBHelper();}

	public StacDBReplayClient(boolean cp) {
		super(cp);
		dbh = new StacDBHelper();
	}
	
	// don't bother with server connection - we're faking it.
	public synchronized void connect() {}
	
	private DBToQueue dtq = null; //the equivalent of file to queue
	
	private static final String START = "START";
	
	private Frame parentFrame;
	
	public void startPracticeGame(String practiceGameName, Hashtable gameOpts, boolean mainPanelIsActive)
    {
		prCli = new DumbStringConn();
		
        // local server will support per-game options //what is this for?
        if (so != null)
        	so.setEnabled(true);
		
        dtq = new DBToQueue(this, dbh, gameID);            
        
    	// May take a while to start server & game.
        // The new-game window will clear this cursor.
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    
        // Do some basic messages to start the window, which aren't in the log file
        SOCNewGameWithOptions newGame = new SOCNewGameWithOptions(practiceGameName, (String) null, -1);
        treat(newGame, true);
        SOCJoinGameAuth auth = new SOCJoinGameAuth(practiceGameName, true);
        treat(auth, true);
        //also for sitting everyone down etc
        int[] playerIds;
        if(gameID == 0){
        	playerIds = new int[]{0,0,0,0};
        }else{
        	playerIds = dbh.getPlayersIDsFromGame(gameID);
        }
        for(int i =0; i < 4; i++){
        	if(playerIds[i] == 0 ){
        		SOCSitDown sit2 = new SOCSitDown(practiceGameName, "Player_" + i, i, false);
        		treat(sit2, true);
        		//6 player Element messages for each rss including unknown
        		for(int j = 1; j < 7; j++){
        		SOCPlayerElement elem = new SOCPlayerElement(practiceGameName, i, 100, j, 0);
        			treat(elem, true);
        		}
        		SOCChangeFace face = new SOCChangeFace(practiceGameName, i, 1);//don't care about the face they used, everyone will have the same one
        		treat(face, true);
        		SOCGameState state = new SOCGameState(practiceGameName, 0);
        		treat(state, true);
        	}
        	else if(playerIds[i] != -1){ // we never get -1 ...poor choice of variables :(
        		SOCSitDown sit2 = new SOCSitDown(practiceGameName, dbh.getPlayerNameByID(playerIds[i]), i, false);
        		treat(sit2, true);
        		//6 player Element messages for each rss including unknown
        		for(int j = 1; j < 7; j++){
        		SOCPlayerElement elem = new SOCPlayerElement(practiceGameName, i, 100, j, 0);
        			treat(elem, true);
        		}
        		SOCChangeFace face = new SOCChangeFace(practiceGameName, i, 1);//don't care about the face they used, everyone will have the same one
        		treat(face, true);
        		SOCGameState state = new SOCGameState(practiceGameName, 0);
        		treat(state, true);
        	} 
        }
        
        // Now start the simulator        
        new Thread(dtq).start(); 
        
    }
	
	/**
     * Copied directly from SOCPlayerClient, except the constructor of the client itself
     */
    public static void main(String[] args)
    {    
    	StacDBReplayClient client;
        boolean withConnectOrPractice = true;
        client = new StacDBReplayClient(withConnectOrPractice);
        client.dbh.initialize();
        
        //try connecting to the db in here
        client.dbh.connect();
        if(!client.dbh.isConnected()){
        	System.err.println("Cannot connect to the database, exiting");
        	System.exit(0);
        }
        
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
        frame.setSize(300, 120);        
        frame.setLayout(new FlowLayout());
        
        Button b = new Button("Start game");
        b.setPreferredSize(new Dimension(300, 50));
        client.textName = new JTextArea();
        client.textName.setPreferredSize(new Dimension(300, 30));
        frame.add(b);
        frame.add(client.textName);
        b.setActionCommand(START);
        b.addActionListener(client);
        frame.setVisible(true);
    }
    
    public void actionPerformed(ActionEvent e) {
    	if(textName.getText().equals("Experiment")){
    		gameID = 1;//22, but I think is due to the way things are stored in the db :((
    		String name = dbh.getGameNameByID(gameID);
    		startPracticeGame(name, null, true);
//    		gameID = 0;
//    		startPracticeGame(textName.getText(), null, true);
    	}else if(!textName.getText().isEmpty() && dbh.getIDfromGameName(textName.getText()) != -1){
    		gameID = dbh.getIDfromGameName(textName.getText());
    		System.out.println("Replaying game with name:" + this.textName.getText() + " with ID:" + gameID);
    		startPracticeGame(textName.getText(), null, true);
    	}else if(!textName.getText().isEmpty() && dbh.getIDfromSimGameName(textName.getText()) != -1){
    		gameID = dbh.getIDfromSimGameName(textName.getText());
    		System.out.println("Replaying game with name:" + this.textName.getText() + " with ID:" + gameID);
    		startPracticeGame(textName.getText(), null, true);
    	}else
    		System.err.println("Game name not found in DB or you passed and empty String");
    }
	
	public void pause() {
		dtq.state = DBToQueue.PAUSE;		
	}
	public void play() {
		dtq.state = DBToQueue.PLAY;
	}
	public void toText() {
		dtq.state = DBToQueue.FASTFORWARD;
	}
	public void toBreakPoint(String breakText) {
	    dtq.state = DBToQueue.TO_END;
		//breaktext not needed as we are breaking based on actions not on msgs in the logs
	}
    
	 /** 
     * Class to act as a fake server - just iterate through the rows in the game actions table and observable state features table
     * @author MD
     *
     */
	protected static class DBToQueue implements Runnable {
		
		private final SOCReplayClient cl;
		private final StacDBHelper dbh;
		
		private static final int PLAY = 1;
		private static final int PAUSE = 2;
		private static final int FASTFORWARD = 3;
		private static final int TO_GAME = 4;
		private static final int TO_END = 5;
		
		private int idCounter;		 // the counter representing the id of rows from the two tables in the db
		private int gameID;
		private String gameName;
		
		private FVGenerator gen = FVGeneratorFactory.getGenerator();
		
		private int state = PAUSE; 
		
		public DBToQueue(SOCReplayClient cl, StacDBHelper dbh, int gi) {				
			this.cl = cl;
			this.dbh = dbh;
			idCounter = 0; //always start from initial start of game
			gameID = gi;
			if(gameID ==0)
				gameName = "Experiment";
			else
				if(gameID < 100){
					gameName = dbh.getGameNameByID(gameID);
				}else
					gameName = dbh.getSimGameNameByID(gameID);
		}
		
		@Override
		public void run() {
			//TODO maybe displaying some gtms as the game advances (just for feedback, but not necessary)
			//first state is a special one as it is the start of game so we need to pass some more special msgs to the client
			ObsGameStateRow ogsr = dbh.selectOGSR(gameID, 1);
			GameActionRow gar = dbh.selectGAR(gameID, 1);
			ExtGameStateRow egsr = dbh.selectEGSR(gameID, 1);
			
			SOCBoardLayout sbl = new SOCBoardLayout(gameName, StacDBHelper.transformToIntArr(ogsr.getHexLayout())
					, StacDBHelper.transformToIntArr(ogsr.getNumberLayout()), ogsr.getRobberHex());
			cl.treat(sbl, true);
			if(gameName.equals("Experiment"))
				updatePiecesOnBoard(ogsr.getPiecesOnBoard());
			for(int i = 0; i < 4; i++){
        		//3 player Element messages for each rss including unknown
        		cl.treat(new SOCPlayerElement(gameName, i, 100, 10, 15), true);
        		cl.treat(new SOCPlayerElement(gameName, i, 100, 11, 5), true);
        		cl.treat(new SOCPlayerElement(gameName, i, 100, 12, 4), true);
        		SOCSetPlayedDevCard spdc = new SOCSetPlayedDevCard(gameName, i, false);
        		cl.treat(new SOCSetPlayedDevCard(gameName, i, false), true);
			}
			//read the first row and get the starting player or his this taken care of when the first turn msg is passed
			cl.treat(new SOCGameTextMsg(gameName, "Server", "Randomly picking a starting player..."), true); //feedback
			cl.treat(new SOCDevCardCount(gameName, ogsr.getDevCardsLeft()),true);
			cl.treat(new SOCGameState(gameName, ogsr.getGameState()),true);
			//from here onwards we should be able to follow the db in terms of changes and updates
			while(true){
				if (state != PAUSE) {
					idCounter++;
					gar = dbh.selectGAR(gameID, idCounter);
					
					ogsr = dbh.selectOGSR(gameID, idCounter);
					egsr = dbh.selectEGSR(gameID, idCounter);
					
					//now depending on the action type update the game object in the client;
					double actionType = gar.getType();
					if(actionType==GameActionRow.TRADE){
						updatePlayersRss(ogsr);
					}else if(actionType==GameActionRow.ENDTURN){
						updateGameState(ogsr);
						cl.treat(new SOCTurn(gameName, ogsr.getCurrentPlayer()), true);
						updatePlayedDevCard(ogsr);
					}else if(actionType==GameActionRow.ROLL){
						updateGameState(ogsr);
						cl.treat(new SOCDiceResult(gameName, ogsr.getDiceResult()), true);
						updatePlayersRss(ogsr);
					}else if(actionType==GameActionRow.BUILDROAD){
						if(ogsr.getLRLabel(ogsr.getCurrentPlayer())==1)
							cl.treat(new SOCLongestRoad(gameName, ogsr.getCurrentPlayer()),true);
						updatePiecesOnBoard(ogsr.getPiecesOnBoard());
						updateGameState(ogsr);
						updatePlayersRss(ogsr);
						updateVPForAll(ogsr);
					}else if(actionType==GameActionRow.BUILDSETT){
						updatePiecesOnBoard(ogsr.getPiecesOnBoard());
						updateGameState(ogsr);
						updatePlayersRss(ogsr);
						updateVPForAll(ogsr);
					}else if(actionType==GameActionRow.BUILDCITY){
						if(gameName.equals("Experiment")){
							SOCBoardLayout sbl1 = new SOCBoardLayout(gameName, StacDBHelper.transformToIntArr(ogsr.getHexLayout())
									, StacDBHelper.transformToIntArr(ogsr.getNumberLayout()), ogsr.getRobberHex());
							cl.treat(sbl1, true);
						}
						updatePiecesOnBoard(ogsr.getPiecesOnBoard());
						updateGameState(ogsr);
						updatePlayersRss(ogsr);
						updateVPForAll(ogsr);
					}else if(actionType==GameActionRow.MOVEROBBER){ 
						cl.treat(new SOCMoveRobber(gameName, ogsr.getCurrentPlayer(), ogsr.getRobberHex()), true);
						updateGameState(ogsr);
					}else if(actionType==GameActionRow.CHOOSEPLAYER){
						cl.treat(new SOCMoveRobber(gameName, ogsr.getCurrentPlayer(), ogsr.getRobberHex()), true);
						updateGameState(ogsr);
						updatePlayersRss(ogsr); //MoveRobber action is sometimes included in this one, not the other way around
					}else if(actionType==GameActionRow.DISCARD){
						updateGameState(ogsr);
						updatePlayersRss(ogsr);
					}else if(actionType==GameActionRow.BUYDEVCARD){
						updatePlayersRss(ogsr);
						cl.treat(new SOCDevCardCount(gameName, ogsr.getDevCardsLeft()), true); //don't really care as it is not displayed but do it anyway:)
						//in here we will need to compare with the old osgr to find out what changed in order to be able to display what card has been bought
						int ct = compareOGSRsForDevCards(ogsr, dbh.selectOGSR(gameID, (idCounter-1)));
						cl.treat(new SOCDevCard(gameName, ogsr.getCurrentPlayer(), SOCDevCard.DRAW, ct), true);
						updateVPForAll(ogsr);
					}else if(actionType==GameActionRow.PLAYKNIGHT){
						updatePlayedDevCard(ogsr);
						updateGameState(ogsr);
						cl.treat(new SOCDevCard(gameName, ogsr.getCurrentPlayer(), SOCDevCard.PLAY, SOCDevCardConstants.KNIGHT), true);
						if(ogsr.getLALabel(ogsr.getCurrentPlayer())==1)
							cl.treat(new SOCLargestArmy(gameName, ogsr.getCurrentPlayer()),true);
						updateVPForAll(ogsr);
					}else if(actionType==GameActionRow.PLAYMONO){
						updatePlayedDevCard(ogsr);
						updatePlayersRss(ogsr);
						updateGameState(ogsr);
						cl.treat(new SOCDevCard(gameName, ogsr.getCurrentPlayer(), SOCDevCard.PLAY, SOCDevCardConstants.MONO), true);
					}else if(actionType==GameActionRow.PLAYDISC){
						updatePlayedDevCard(ogsr);
						updatePlayersRss(ogsr);
						updateGameState(ogsr);
						cl.treat(new SOCDevCard(gameName, ogsr.getCurrentPlayer(), SOCDevCard.PLAY, SOCDevCardConstants.DISC), true);
					}else if(actionType==GameActionRow.PLAYROAD){
						updatePlayedDevCard(ogsr);
						updateGameState(ogsr);
						cl.treat(new SOCDevCard(gameName, ogsr.getCurrentPlayer(), SOCDevCard.PLAY, SOCDevCardConstants.ROADS), true);
					}else if(actionType==GameActionRow.WIN){
						updateGameState(ogsr);
						updateVPForAll(ogsr);
						break; //the current player has won so it is over now
					}
					
					//turn into a feature vector and write the vector highlighting the new features
					try {
						
						//TODO: need to be careful to do this when creating the module that selects all states of a kind
						if(ogsr.getGameState() == SOCGame.WAITING_FOR_DISCARDS){
							//find out who is the next player to play/update current player for special case of discards
							//get the next ogsr and compare which player has fewer rss :p why do I need to handle games that break the turn based pattern :(
							ObsGameStateRow nogsr = dbh.selectOGSR(gameID, idCounter + 1);
							for(int pn = 0; pn < 4; pn++){
								if(!Arrays.equals(ogsr.getResources(pn),nogsr.getResources(pn))){
									ogsr.setCurrentPlayer(pn);
								}
							}
						}
						
						int [] featState = gen.calculateStateVectorJS(ogsr, egsr);
						System.out.println("Current player" + ogsr.getCurrentPlayer());
						System.out.println("Full Array: " + Arrays.toString(featState));
						System.out.println("New features: "  +Arrays.toString(Arrays.copyOfRange(featState, NumericalFeatureVectorOffsets.OFS_NEXTLEGALPRODUCTIVITY, NumericalFeatureVectorOffsets.OFS_EXPPOSSIBILITY + 1)));
						System.out.println("Include: "  +  featState[NumericalFeatureVectorOffsets.OFS_EXPPOSSIBILITY]);
						System.out.println("");
						
						//display the board layout for debugging
						int gstate = StacDBToCatanInterface.translateJSStateToSS(ogsr.getGameState());
						Catan game = StacDBToCatanInterface.generateGameFromDB(ogsr, egsr, gstate);
//						//list all possible actions according to the SS model
//						if(gstate != -1)
//							bl.listPossibleActionVectors(true);
						
						int [] ssFeatState = gen.calculateStateVectorSS(game.getState(), Catan.board);
						System.out.println("Current player" + ogsr.getCurrentPlayer());
						System.out.println("Full Array: " + Arrays.toString(ssFeatState));
						System.out.println("New features: "  +Arrays.toString(Arrays.copyOfRange(featState, NumericalFeatureVectorOffsets.OFS_NEXTLEGALPRODUCTIVITY, NumericalFeatureVectorOffsets.OFS_EXPPOSSIBILITY + 1)));
						System.out.println("Include: "  +  ssFeatState[NumericalFeatureVectorOffsets.OFS_EXPPOSSIBILITY]);
						System.out.println("");
						
						if(!Arrays.equals(featState, ssFeatState)){
							System.out.println("$$$ Are not the same");
							System.out.println("ss: " + Arrays.toString(ssFeatState));
							System.out.println("js: " + Arrays.toString(featState));
						}
						
					} catch (Exception e) {
						e.printStackTrace();
					}

					//logic for play speed
					if(state == FASTFORWARD){ 
						try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}else if(state == TO_END){
						//no pause we want to finish the game as quickly as possible (given the parsing and redrawing it will still take a few seconds :|)
					}else{ //normal play give it a half second so it can be viewable
						try {
							Thread.sleep(500);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}else{
					// we're paused: wait for the user to press a button
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
			System.out.println("Finished");
		}
		
		//methods for doing the actual game object update based on the info in the ogsr (only the ones that repeat, the rest of the logic is handled in the run loop)
		
		private void updatePlayersRss(ObsGameStateRow ogsr){
			for(int i = 0; i < 4; i++){
				int[] noRss = ogsr.getResources(i);
        		for(int j = 1; j < 7; j++){
        			cl.treat(new SOCPlayerElement(gameName, i, 100, j, noRss[j-1]), true); //this should be enough
        		}
			}
		}
		
		private void updateGameState(ObsGameStateRow ogsr){
			cl.treat(new SOCGameState(gameName, ogsr.getGameState()), true);
		}
		
		private void updatePlayedDevCard(ObsGameStateRow ogsr){
			for(int i =0; i< 4; i++)
				cl.treat(new SOCSetPlayedDevCard(gameName, i, ogsr.hasPlayedDevCard()), true);
		}
		
		private int compareOGSRsForDevCards(ObsGameStateRow newOgsr,ObsGameStateRow oldOgsr){
			//TODO:this is problematic as someone might want to buy multiple cards in one turn (but this compares the before and after ogsr when executing one action, so we should be fine)
			int pn = newOgsr.getCurrentPlayer();//we are interested in the current player
			if(newOgsr.getVictoryDevCards(pn) > oldOgsr.getVictoryDevCards(pn))
				return SOCDevCardConstants.UNIV; //a random vp card as we don't care which one
			int[] oldDevCards = oldOgsr.getNewDevCards(pn);
			int[] newDevCards = newOgsr.getNewDevCards(pn);
			if(newDevCards[SOCDevCardConstants.KNIGHT] > oldDevCards[SOCDevCardConstants.KNIGHT])
				return SOCDevCardConstants.KNIGHT;
			else if(newDevCards[SOCDevCardConstants.DISC] > oldDevCards[SOCDevCardConstants.DISC])
				return SOCDevCardConstants.DISC;
			else if(newDevCards[SOCDevCardConstants.MONO] > oldDevCards[SOCDevCardConstants.MONO])
				return SOCDevCardConstants.MONO;
			else 
				return SOCDevCardConstants.ROADS;
		}
		
		/**
	     * I just cannot understand the messages and the game logic so I will just redraw everything :) from ogsr
	     * @param pob the Integer[][] array stored in the ogsr
	     */
		private void updatePiecesOnBoard(Integer[][] pob){
			SOCPlayerInterface pi = (SOCPlayerInterface) cl.playerInterfaces.get(gameName);
            SOCGame ga = pi.getGame();
	        SOCBoard board = ga.getBoard();
	        board.emptyPiecesVectors();
            //go through pob array and add to board;
	        for(Integer[] p : pob){
	        	if(p[0] == SOCPlayingPiece.ROAD){
	        		SOCRoad road = new SOCRoad(ga.getPlayer(p[2]), p[1], board);
	        		board.putPiece(road);
	        	}else if(p[0] == SOCPlayingPiece.CITY){
	        		SOCCity city = new SOCCity(ga.getPlayer(p[2]), p[1], board);
	        		board.putPiece(city);
	        	}else if(p[0] == SOCPlayingPiece.SETTLEMENT){
	        		SOCSettlement settlement = new SOCSettlement(ga.getPlayer(p[2]), p[1], board);
	        		board.putPiece(settlement);
	        	}//otherwise ignore.... this shouldn't happen
	        }
	        //now I need to redraw the board panel
			pi.boardPanel.validate();
			pi.boardPanel.flushBoardLayoutAndRepaint();
	    }
		
		private void updateVPForAll(ObsGameStateRow ogsr){
			SOCPlayerInterface pi = (SOCPlayerInterface) cl.playerInterfaces.get(gameName);
			for (int i = 0; i < 4; i++)
	        {
	            pi.getPlayerHandPanel(i).updateJustVP(ogsr.getTotalVP(i));
	        }
		}
		
	}
}
