package soc.server.database.stac;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;

import soc.game.SOCPlayingPiece;

/**
 * Container class for easy storing and passing of raw data collected from logs. Unfortunately, both this class and ExtGameStateRow
 * need to have fields as Integer[] instead of int[] as methods in java.sql only accept Object[] and int is not an object.
 * 
 * @author MD
 *
 */
public class ObsGameStateRow implements Serializable{
	//columns
	/**
	 * Row's unique ID.
	 */
	private int ID; 
	/**
	 * Game name.
	 */
	private String gameName;
	/**
	 * Hex layout in terms of resource types and ports; exactly as it is stored in a SOCBoard instance.
	 */
	private Integer[] hexLayout;
	/**
	 * Numbers layout; exactly as it is stored in a SOCBoard instance.
	 */
	private Integer[] numberLayout;
	/**
	 * Robber's location
	 */
	private int robberHex;
	/**
	 * Current game state.
	 */
	private int gameState;
	/**
	 * How many dev cards are left in the deck.
	 */
	private int devCardsLeft;
	/**
	 * Current Dice result or 0 if current player hasn't rolled yet
	 */
	private int diceResult; 
	/**
	 * First player 
	 */
	private int startingPlayer; 
	/**
	 * Current player
	 */
	private int currentPlayer;
	/**
	 * Has the current player played a development card yet?
	 */
	private boolean playedDevCard; 
	/**
	 * all the pieces on the board an nx3 array where the second part contains details on coordinate, type of piece and owner's player number
	 */
	private Integer[][] piecesOnBoard; 
	/**
	 * A 4x37 array containing all the data for each player in the following order: player's ID (from the db), public vp, total vp, largest army,
	 * longest road, total number of development cards in hand, number of dev cards which represent a vp, an array[5] of all the unplayed dev cards,
	 * an array[5] of all the newly bought dev cards, number of played knights, an array[6] containing the number of each resource(including unknowns),
	 * an array[5] of which resource types the player is touching, an array[5] of which port types the player is touching, an array[3] of all the 
	 * pieces the player can still build. All booleans (LA,LR labels, touching stuff are represented in 1for true or 0 for false).
	 */
	private Integer[][] players;
	/**
	 * A 4x5xn array, containing a list of all the numbers for each resource touched by each player. 
	 * (Do not want to include this in the above array as it complicates things)
	 */
	private Integer[][][] touchingNumbers;
	
	public ObsGameStateRow(int id, String n){
		setID(id);
		setGameName(n);
	}

	public int getID() {
		return ID;
	}

	public void setID(int iD) {
		ID = iD;
	}

	public String getGameName() {
		return gameName;
	}

	public void setGameName(String name) {
		this.gameName = name;
	}

	public Integer[] getHexLayout() {
		return hexLayout;
	}

	public void setHexLayout(Integer[] hl) {
		hexLayout=hl;
	}
	
	public Integer[] getNumberLayout() {
		return numberLayout;
	}
	
	public void setNumberLayout(Integer[] nl) {
			numberLayout = nl;
	}

	public int getRobberHex() {
		return robberHex;
	}

	public void setRobberHex(int robberHex) {
		this.robberHex = robberHex;
	}

	public int getGameState() {
		return gameState;
	}

	public void setGameState(int gameState) {
		this.gameState = gameState;
	}

	public int getDevCardsLeft() {
		return devCardsLeft;
	}

	public void setDevCardsLeft(int devCardsLeft) {
		this.devCardsLeft = devCardsLeft;
	}

	public int getDiceResult() {
		return diceResult;
	}

	public void setDiceResult(int diceResult) {
		this.diceResult = diceResult;
	}

	public int getStartingPlayer() {
		return startingPlayer;
	}

	public void setStartingPlayer(int startingPlayer) {
		this.startingPlayer = startingPlayer;
	}

	public int getCurrentPlayer() {
		return currentPlayer;
	}

	public void setCurrentPlayer(int currentPlayer) {
		this.currentPlayer = currentPlayer;
	}

	public boolean hasPlayedDevCard() {
		return playedDevCard;
	}

	public void setPlayedDevCard(boolean playedDevCard) {
		this.playedDevCard = playedDevCard;
	}

	public Integer[][] getPiecesOnBoard() {
		return piecesOnBoard;
	}

	public void setPiecesOnBoard(Integer[][] piecesOnBoard) {
		this.piecesOnBoard = piecesOnBoard;
	}

	public Integer[][] getPlayers() {
		return players;
	}

	public void setPlayers(Integer[][] players) {
		this.players = players;
	}

	public Integer[][][] getTouchingNumbers() {
		return touchingNumbers;
	}

	public void setTouchingNumbers(Integer[][][] touchingNumbers) {
		this.touchingNumbers = touchingNumbers;
	}
	
//////////////Helper methods for setting and getting specific information from arrays////////
	public int getPlayerID(int pn){
		return players[pn][0];
	}
	
	public void setPlayerID(int pn, int ID){
		players[pn][0] = ID;
	}
	
	public int getPublicVP(int pn){
		return players[pn][1];
	}
	
	public void setPublicVP(int pn, int v){
		players[pn][1] = v;
	}
	
	public int getTotalVP(int pn){
		return players[pn][2];
	}
	
	public void setTotalVP(int pn, int v){
		players[pn][2] = v;
	}
	
	public int getLALabel(int pn){
		return players[pn][3];
	}
	
	public int getLRLabel(int pn){
		return players[pn][4];
	}
	
	public void setLALabel(int pn, int b){
		players[pn][3]=b;
	}
	
	public void setLRLabel(int pn, int b){
		players[pn][4]=b;
	}
	
	public int getDevTotalCards(int pn){
		return players[pn][5]; 
	}
	
	public void setDevTotalCards(int pn, int tdc){
		players[pn][5] = tdc; 
	}
	
	public int getVictoryDevCards(int pn){
		return players[pn][6]; 
	}
	
	public void setVictoryDevCards(int pn, int vdc){
		players[pn][6] = vdc; 
	}
	
	public int[] getUnplayedDevCards(int pn){
		int[] dc = new int[5];
		for(int i = 0; i < 5; i++)
			dc[i] = players[pn][i+7]; //5 positions from index 7(including)
		return dc; 
	}
	
	public void setUnplayedDevCards(int pn, int[] udc){
		for(int i = 0; i < 5; i++)
		players[pn][i+7] = udc[i]; 
	}
	
	public int[] getNewDevCards(int pn){
		int[] dc = new int[5];
		for(int i = 0; i < 5; i++)
			dc[i] = players[pn][i+12]; //5 positions from index 12(including)
		return dc; 
	}
	
	public void setNewDevCards(int pn, int[] ndc){
		for(int i = 0; i < 5; i++)
		players[pn][i+12] = ndc[i]; 
	}
	
	public int getPlayedKnights(int pn){
		return players[pn][17]; 
	}
	
	public void setPlayedKnights(int pn, int pk){
		players[pn][17] = pk; 
	}
	
	public int[] getResources(int pn){
		int[] rss = new int[6];
		for(int i = 0; i < 6; i++)
			rss[i] = players[pn][i+18]; //6 positions from index 18(including)
		return rss; 
	}
	
	public void setResources(int pn, int[] rss){
		for(int i = 0; i < 6; i++)
		players[pn][i+18] = rss[i]; 
	}
	
	public int[] getTouchingResourceTypes(int pn){
		int[] trt = new int[5];
		for(int i = 0; i < 5; i++)
			trt[i] = players[pn][i+24]; //5 positions from index 24(including)
		return trt; 
	}
	
	public void setTouchingResourceTypes(int pn, int[] trt){
		for(int i = 0; i < 5; i++)
		players[pn][i+24] = trt[i]; 
	}
	
	public int[] getTouchingPortTypes(int pn){
		int[] tpt = new int[6];
		for(int i = 0; i < 6; i++)
			tpt[i] = players[pn][i+29]; //5 positions from index 29(including)
		return tpt; 
	}
	
	public void setTouchingPortTypes(int pn, int[] tpt){
		for(int i = 0; i < 6; i++)
		players[pn][i+29] = tpt[i]; 
	}
	
	public int getRoadsLeftToBuild(int pn){
		return players[pn][35];
	}
	
	public int getSettlementsLeftToBuild(int pn){
		return players[pn][36];
	}
	
	public int getCitiesLeftToBuild(int pn){
		return players[pn][37];
	}
	
	public void setRoadsLeftToBuild(int pn, int rltb){
		players[pn][35] = rltb;
	}
	
	public void setSettlementsLeftToBuild(int pn, int sltb){
		players[pn][36] = sltb;
	}
	
	public void setCitiesLeftToBuild(int pn, int cltb){
		players[pn][37] = cltb;
	}
	
	public int getPlayedRB(int pn){
		return players[pn][38];
	}
	
	public int getPlayedMono(int pn){
		return players[pn][39];
	}
	
	public int getPlayedDisc(int pn){
		return players[pn][40];
	}
	
	public void setPlayedRB(int pn, int rltb){
		players[pn][38] = rltb;
	}
	
	public void setPlayedMono(int pn, int sltb){
		players[pn][39] = sltb;
	}
	
	public void setPlayedDisc(int pn, int cltb){
		players[pn][40] = cltb;
	}
	
	public int[][] getPiecesOnBoard(int pn){
		int size = 0;
		for(int i = 0; i < piecesOnBoard.length; i++){
			if(piecesOnBoard[i][2]==pn)
				size++;
		}
		if(size == 0){
			return null; //if no piece yet return null; remember to check for null
		}
		int[][] pob = new int[size][2];
		int j = 0;
		for(int i = 0; i < piecesOnBoard.length; i++){
			if(piecesOnBoard[i][2]==pn){
				pob[j][0] = piecesOnBoard[i][0];
				pob[j][1] = piecesOnBoard[i][1];
				j++;
			}
		}
		return pob;
	}
	
	public Integer[][] getTouchingNumbers(int pn){
		return touchingNumbers[pn];
	}

	public void setTouchingNumbers(int pn, int[][] tn){
		for(int i = 0; i < tn.length; i++){
			touchingNumbers[pn][i] = new Integer[tn[i].length];
			for(int j = 0; j < tn[i].length; j++){
				touchingNumbers[pn][i][j] = tn[i][j];
			}
		}
	}
	
	public void setHexLayout(int[] hl) {
		this.hexLayout = new Integer[hl.length]; 
		for(int i = 0; i < hl.length; i++)
			this.hexLayout[i] = hl[i];
	}
	
	public void setNumberLayout(int[] nl) {
		this.numberLayout = new Integer[nl.length]; 
		for(int i = 0; i < nl.length; i++)
			this.numberLayout[i] = nl[i];
	}
	
	/**
	 * The Touching numbers array needs to have all the containing arrays of the same dimensionality and should not be empty,
	 * otherwise postgres won't accept it. This method fills all the arrays with 0's until they are all of the same size, 
	 * or just adds one 0 to all if they are empty.
	 */
	public void resolveNumbersDimensionality(){
		int maxSize = 0;
		//loop through all to get the max size;
		for(int i=0; i<4; i++){
			for(int j=0; j<5; j++)
				if(touchingNumbers[i][j].length > maxSize)
					maxSize=touchingNumbers[i][j].length; 
		}
		
		if(maxSize == 0){
			//add one 0 to all
			for(int i=0; i<4; i++){
				for(int j=0; j<5; j++)
					touchingNumbers[i][j] = new Integer[]{0};
			}
		}else{
			//bring all arrays to the same size by adding 0s to the end
			for(int i=0; i<4; i++){
				for(int j=0; j<5; j++){
					if(touchingNumbers[i][j].length < maxSize){
						Integer[] values = touchingNumbers[i][j]; 
						touchingNumbers[i][j] = new Integer[maxSize];
						for(int k=0; k < values.length; k++){
							touchingNumbers[i][j][k] = values[k];
						}
						for(int k=values.length; k < maxSize; k++){
							touchingNumbers[i][j][k] = 0;
						}
					}
				}
			}
		}
	}
	
	public int[] getRoadsForPlayer(int pn){
		int size = 0;
		for(Integer[] a: piecesOnBoard){
			if(a[0] == SOCPlayingPiece.ROAD && a[2] == pn)
				size++;
		}
		int[] array = new int[size];
		int i = 0;
		for(Integer[] a: piecesOnBoard){
			if(a[0] == SOCPlayingPiece.ROAD && a[2] == pn){
				array[i] = a[1];
				i++;
			}
		}
		return array;
	}
	
	public int[] getSettlementsForPlayer(int pn){
		int size = 0;
		if(piecesOnBoard !=null)
		for(Integer[] a: piecesOnBoard){
			if(a[0] == SOCPlayingPiece.SETTLEMENT && a[2] == pn)
				size++;
		}
		int[] array = new int[size];
		int i = 0;
		if(piecesOnBoard !=null)
		for(Integer[] a: piecesOnBoard){
			if(a[0] == SOCPlayingPiece.SETTLEMENT && a[2] == pn){
				array[i] = a[1];
				i++;
			}
		}
		return array;
	}
	
	public int[] getCitiesForPlayer(int pn){
		int size = 0;
		for(Integer[] a: piecesOnBoard){
			if(a[0] == SOCPlayingPiece.CITY && a[2] == pn)
				size++;
		}
		int[] array = new int[size];
		int i = 0;
		for(Integer[] a: piecesOnBoard){
			if(a[0] == SOCPlayingPiece.CITY && a[2] == pn){
				array[i] = a[1];
				i++;
			}
		}
		return array;
	}
	
	/**
	 * 
	 * @param pn the player number
	 * @return a list of numbers indifferent to what resources it is attached to
	 */
	public int[] getJustNumbersForPlayer(int pn){
		Integer[][] numbers = getTouchingNumbers(pn);
		Vector list = new Vector();
		for(Integer[] arr : numbers){
			for(int n : arr)
				if(n != 0) //just the real numbers
					list.add(n);
		}
		int[] result = new int[list.size()];
		int i = 0;
		for(Object n : list){
			result[i] = (int) n;
			i++;
		}
			
		return result;
	}
	
////////////////////////////////////////////////////////////////////////////////////////////
	/**
	 * For debugging purposes.
	 */
	public String toString(){
		String string = "Observable feature row: ID=" + ID + "|gameName=" + gameName + "|hexLayout=" + Arrays.toString(hexLayout) + "|numberLayout=" +
				Arrays.toString(numberLayout) + "|robberHex=" + robberHex + "|gameState=" + gameState + "|devCardsLeft=" + devCardsLeft + "|diceResult=" + diceResult + 
				"|startingPlayer=" + startingPlayer + "|currentPlayer=" + currentPlayer + "|playedDevCard=" + playedDevCard + "|piecesOnBoard=" + 
				arrToString(piecesOnBoard) + "|players=" + arrToString(players) + "|touchingNumbers={";
		
		for(int i = 0; i < touchingNumbers.length;i ++){
			string = string + arrToString(touchingNumbers[i]);
		}
		
		return string;
	}
	
	/**
	 * A small utility to convert a bidimensional array into a readable string form.
	 * @param a the Integer[][] object to convert to string
	 * @return
	 */
	public String arrToString(Integer[][] a){
		String string = "{";
		for(int i = 0; i < a.length;i ++){
			string = string + Arrays.toString(a[i]);
			if(i < a.length - 1)
				string = string + ",";
		}
		return string + "}";
	}
	
}
