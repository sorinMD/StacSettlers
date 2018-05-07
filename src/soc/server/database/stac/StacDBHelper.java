package soc.server.database.stac;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.net.URL;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import representation.FVGeneratorFactory;
import representation.FeatureVectorGenerator;
import resources.Resources;

/**
 * Can only use a Postgresql db, so it needs to be different to DBHelper and SOCDBHelper.
 * Implements the interface necessary for collecting, extracting and storing all the information from the saved SOC leagues logs. 
 * Also contains some utilities used by both ogsr and egsr instances.
 * @author MD
 *
 */
public class StacDBHelper{
	//private fields for supporting localhost dbs or other server locations/users/pswd etc 
	private Connection conn = null;
	private String dbURL = null;
	private String dbUser = null;
	private String dbPass = null;
	//driver should be fixed, we are only allowing postgreSQL
	private static final String driver = "org.postgresql.Driver";
	//just a variable to know if we are connected or not
	private boolean connected = false;
	//sql commands/statements
	Statement stmt = null;
    //table names;
    public static final String OBSFEATURESTABLE = "obsgamestates_";
    public static final String EXTFEATURESTABLE = "extgamestates_";
    public static final String ACTIONSTABLE = "gameactions_";
    public static final String GAMESTABLE = "games";
    public static final String PLAYERSTABLE = "players";
    public static final String LEAGUESTABLE = "leagues";
    public static final String SEASONSTABLE = "seasons";
    public static final String VALUETABLE = "statevalue_";
    public static final String SIMGAMESTABLE = "simulation_games";
	//id of human games start from 1, simulation games start from 100
    public static final int SIMGAMESSTARTID = 100;
    
    /**
     * for quick testing
     * @param args
     */
    public static void main(String[] args) {
		StacDBHelper dbh = new StacDBHelper();
		dbh.initialize();
		dbh.connect();
		dbh.disconnect();
	}
    
	/**
	 * Initialise all variables by reading from db.config.txt; 
	 */
	public void initialize(){
		BufferedReader config = null;
		URL url = Resources.class.getResource(Resources.dbconfigName);
		try {
	    	InputStream is = url.openStream();
			config = new BufferedReader(new InputStreamReader(is));
		} catch (IOException e) {
			e.printStackTrace();
		}
		List<String> fullConfig = new ArrayList<String>();
        String nextLine;
		try {
			nextLine = config.readLine();
			while (nextLine != null) {
	            fullConfig.add(nextLine);
	            nextLine = config.readLine();
	        }
			for(String s : fullConfig){
	            if (s.startsWith("dbURL")) {
	                String p[] = s.split("=");
	                if (!p[1].isEmpty() || !p[1].equals("")) {
	                	dbURL = p[1];
	                }
	            }else if (s.startsWith("dbUser")) {
	                String p[] = s.split("=");
	                if (!p[1].isEmpty() || !p[1].equals("")) {
	                	dbUser = p[1];
	                }
	            }else if (s.startsWith("dbPass")) {
	                String p[] = s.split("=");
	                if (!p[1].isEmpty() || !p[1].equals("")) {
	                	dbPass = p[1];
	                }
	            }
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Connect to the DB.
	 */
	public void connect(){
		if(dbURL == null){
			System.err.println("DB URL was not provided");
		}
		if(dbUser == null){
			System.err.println("DB user name was not provided");
		}
		if(dbPass == null){
			System.err.println("DB password was not provided");
		}
	    try {
	        Class.forName(driver);
	        conn = DriverManager.getConnection(dbURL,dbUser,dbPass);
	        conn.setAutoCommit(true);
	        connected = true;
	    } catch (Exception e) {
	    	System.err.println("Cannot connect to the database:" + dbURL + "with user" + dbUser + "and password");
	        e.printStackTrace();
	    }
	}
	
	/**
	 * Disconnect.
	 */
	public void disconnect(){
		try {
			conn.close();
			connected = false;
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public boolean isConnected(){
		return connected;
	}
	
	/**
	 * Creates the table containing the raw features collected from the logs. The table can be found in the db by the name: ObsGameStates_{gameID}.
	 * @param gameID the ID of the game from the games table in the DB.
	 */
	public void createRawStateTable(int gameID){
		try {
			stmt = conn.createStatement();
        String sql = "CREATE TABLE ObsGameStates_" + gameID +
                     " (ID              INT PRIMARY KEY   NOT NULL, " +
                     " NAME             TEXT              NOT NULL, " +
                     " HEXLAYOUT        INT[37]           NOT NULL, " +
                     " NUMBERLAYOUT     INT[37]           NOT NULL, " +
                     " ROBBERHEX        INT               NOT NULL, " +
                     " GAMESTATE        INT               NOT NULL, " +
                     " DEVCARDSLEFT     INT               NOT NULL, " +
                     " DICERESULT       INT               NOT NULL, " +
                     " STARTINGPLAYER   INT               NOT NULL, " +
                     " CURRENTPLAYER    INT               NOT NULL, " +
                     " PLAYEDDEVCARD    BOOLEAN           NOT NULL, " + //has the current player played a dev card;
                     " PIECESONBOARD    INT[][3]          NOT NULL, " + //all the pieces on the board with TYPE, COORD and OWNER
                     " PLAYERS  	    INT[4][41]        NOT NULL, " + //for each player all personal information aside from pieces and numbers touching
                     " TOUCHINGNUMBERS  INT[4][5][]		  NOT NULL)";   //3D because: for each player and for each resource, what numbers on the board
			stmt.executeUpdate(sql);
			stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
//		 System.out.println("Table ObsGameStates_" + gameID + " created successfully");
	}
	
	/**
	 * Creates the table containing some extracted features from either the raw table or the game's replay. The IDs of the rows should correspond 
	 * to the ones in the raw state table. The table can be found in the db by the name: ExtGameStates_{gameID}.
	 * @param gameID the ID of the game from the games table in the DB. 
	 */
	public void createExtractedStateTable(int gameID){
		try {
			stmt = conn.createStatement();
        String sql = "CREATE TABLE ExtGameStates_" + gameID +
                     " (ID            INT PRIMARY KEY        NOT NULL, " +
                     " NAME           TEXT                   NOT NULL, " +
                     " PASTTRADES     INT[4][4], " +  //number of trades with each player (ourPN = bank)
                     " FUTURETRADES   INT[4][4], " +
                     " PASTPBP        INT[4][6], " +  //how many times a possible PBP type was aimed for?
                     " FUTUREPBP      INT[4][6], " +
                     " ETW      	  INT[4],    " +  //estimated time to win
                     " AVGETB         INT[4][2], " +  //average estimated time to build with and without the robber
                     " SETTLEMENTETB  INT[4][2], " +
                     " ROADETB        INT[4][2], " +
                     " CITYETB        INT[4][2], " +
                     " DEVCARDETB     INT[4][2], " +
                     " CONNTERR		  INT[4],    " +
                     " NOTISOTERR	  INT[4],    " +
                     " LONGESTROADS   INT[4],    " +
                     " LONGESTPOSROADS INT[4],    " +
                     " DISTTOOPP      INT[4],    " +
                     " DISTTOPORT     INT[4],    " +
                     " DISTTOLEGAL    INT[4],    " +
                     " RSSTYPEANDNO   INT[4][5]) "; 
        

			stmt.executeUpdate(sql);
			stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
//		 System.out.println("Table ExtGameStates_" + gameID + " created successfully");
	}
	
	/**
	 * Creates the table containing the actions taken during the game. This will act as links between the rows of the states tables.
	 * The table can be found in the db by the name: GameActions_{gameID}.
	 * @param gameID the ID of the game from the games table in the DB.
	 */
	public void createActionTable(int gameID){
		try {
			stmt = conn.createStatement();
        String sql = "CREATE TABLE GameActions_" + gameID +
                     " (ID            INT PRIMARY KEY   NOT NULL, " +
                     " TYPE           DOUBLE PRECISION  NOT NULL, " +
                     " BEFORESTATE    INT               NOT NULL, " +
                     " AFTERSTATE     INT               NOT NULL, " +
                     " VALUE      	  INT)";   //estimation of utility of an action;
			stmt.executeUpdate(sql);
			stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
//		 System.out.println("Table GameActions_" + gameID + " created successfully");
	}
	
	/**
	 * Creates the table containing the feature vector describing the state and the value of the state as evaluated by the agent
	 * @param id the id of the simulation set
	 */
	public void createValueTable(int id){
		try {
			stmt = conn.createStatement();
        String sql = "CREATE TABLE StateValue_" + id +
                " (ID          BIGINT PRIMARY KEY   NOT NULL, " +
                " STATE        INT[" + FeatureVectorGenerator.REWARD_FUNCTION_SIZE + "]          NOT NULL, " + //" STATE        INT[235]          NOT NULL, " + //old feature vector
                " VALUE	       DOUBLE PRECISION  NOT NULL) ";   //estimation of utility of an action;
			stmt.executeUpdate(sql);
			stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
//		 System.out.println("Table StateValue_" + id + " created successfully");
	}
	
	/**
	 * Inserts a row into the raw states table.
	 * @param gameID the ID of the game from the games table in the DB
	 * @param ogsr the row to insert
	 */
	public void insertRawState(int gameID, ObsGameStateRow ogsr){
		String sqlString = "INSERT INTO ObsGameStates_" + gameID + " (ID,NAME,HEXLAYOUT,NUMBERLAYOUT,ROBBERHEX,GAMESTATE,DEVCARDSLEFT,DICERESULT," +
				"STARTINGPLAYER,CURRENTPLAYER,PLAYEDDEVCARD,PIECESONBOARD,PLAYERS,TOUCHINGNUMBERS) "
				+ "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?);";
		try(PreparedStatement ps = conn.prepareStatement(sqlString)) {
			ps.setInt(1, ogsr.getID());
			ps.setString(2, ogsr.getGameName());
			ps.setArray(3, conn.createArrayOf("integer", ogsr.getHexLayout()));
			ps.setArray(4, conn.createArrayOf("integer", ogsr.getNumberLayout()));
			ps.setInt(5, ogsr.getRobberHex());
			ps.setInt(6, ogsr.getGameState());
			ps.setInt(7, ogsr.getDevCardsLeft());
			ps.setInt(8, ogsr.getDiceResult());
			ps.setInt(9, ogsr.getStartingPlayer());
			ps.setInt(10, ogsr.getCurrentPlayer());
			ps.setBoolean(11, ogsr.hasPlayedDevCard());
			ps.setArray(12, conn.createArrayOf("integer", ogsr.getPiecesOnBoard()));
			ps.setArray(13, conn.createArrayOf("integer", ogsr.getPlayers()));
			ps.setArray(14, conn.createArrayOf("integer", ogsr.getTouchingNumbers()));
			ps.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		}
//		System.out.println("Raw state row created successfully");
	}
	
	/**
	 * Inserts a row into the simulation_games table. 
	 * @param gaName the name of the game
	 * @param vp the array with the players' final victory points
	 */
	public void insertSimGameOverview(String gaName, int[] vp){
		int gameID = SIMGAMESSTARTID + Integer.parseInt(gaName.split("_")[1]);
		try {
			String sqlString = "INSERT INTO " + SIMGAMESTABLE + " (ID,NAME,PLAYER1,SCORE1,PLAYER2,SCORE2,PLAYER3,SCORE3,PLAYER4,SCORE4) "
					   + "VALUES (?,?,?,?,?,?,?,?,?,?);";
			PreparedStatement ps = conn.prepareStatement(sqlString);
			ps.setInt(1, gameID);
			ps.setString(2, gaName);
			ps.setInt(3, 0);
			ps.setInt(4, vp[0]);
			ps.setInt(5, 0);
			ps.setInt(6, vp[1]);
			ps.setInt(7, 0);
			ps.setInt(8, vp[2]);
			ps.setInt(9, 0);
			ps.setInt(10, vp[3]);
			ps.execute();
			ps.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}
//		System.out.println("Sim game row created successfully");
	}
	
	
	/**
	 * Inserts a row into the extracted states table.
	 * @param gameID the ID of the game from the games table in the DB
	 * @param egsr the row to insert
	 */
	public void insertExtractedState(int gameID, ExtGameStateRow egsr){
		String sqlString = "INSERT INTO ExtGameStates_" + gameID + " (ID,NAME,PASTTRADES,FUTURETRADES,PASTPBP,FUTUREPBP,ETW,AVGETB,SETTLEMENTETB," +
				"ROADETB,CITYETB,DEVCARDETB,CONNTERR,NOTISOTERR,LONGESTROADS,LONGESTPOSROADS,DISTTOOPP,DISTTOPORT,DISTTOLEGAL,RSSTYPEANDNO)"
				+ "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);";
		try(PreparedStatement ps = conn.prepareStatement(sqlString)) {
			ps.setInt(1, egsr.getID());
			ps.setString(2, egsr.getGameName());
			ps.setArray(3, conn.createArrayOf("integer", egsr.getPastTrades()));
			ps.setArray(4, conn.createArrayOf("integer", egsr.getFutureTrades()));
			ps.setArray(5, conn.createArrayOf("integer", egsr.getPastPBPs()));
			ps.setArray(6, conn.createArrayOf("integer", egsr.getFuturePBPs()));
			ps.setArray(7, conn.createArrayOf("integer", egsr.getETWs()));
			ps.setArray(8, conn.createArrayOf("integer", egsr.getAvgETBs()));
			ps.setArray(9, conn.createArrayOf("integer", egsr.getSettETBs()));
			ps.setArray(10, conn.createArrayOf("integer", egsr.getRoadETBs()));
			ps.setArray(11, conn.createArrayOf("integer", egsr.getCityETBs()));
			ps.setArray(12, conn.createArrayOf("integer", egsr.getDevETBs()));
			ps.setArray(13, conn.createArrayOf("integer", egsr.getTerritoryConnected()));
			ps.setArray(14, conn.createArrayOf("integer", egsr.getTerritoryIsolated()));
			ps.setArray(15, conn.createArrayOf("integer", egsr.getLongestRoads()));
			ps.setArray(16, conn.createArrayOf("integer", egsr.getLongestPossibleRoads()));
			ps.setArray(17, conn.createArrayOf("integer", egsr.getDistanceToOpponents()));
			ps.setArray(18, conn.createArrayOf("integer", egsr.getDistanceToPort()));
			ps.setArray(19, conn.createArrayOf("integer", egsr.getDistanceToNextLegalLoc()));
			ps.setArray(20, conn.createArrayOf("integer", egsr.getRssTypeAndNumber()));
			ps.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		}
//		System.out.println("Extracted state row created successfully");
	}
	
	/**
	 * Inserts a row into the actions table.
	 * @param gameID the ID of the game from the games table in the DB
	 * @param gar the row to insert
	 */
	public void insertAction(int gameID, GameActionRow gar){
		String sqlString = "INSERT INTO GameActions_" + gameID + " (ID,TYPE,BEFORESTATE,AFTERSTATE,VALUE)"
				+ "VALUES (?,?,?,?,?);";
		try(PreparedStatement ps = conn.prepareStatement(sqlString)) {
			ps.setInt(1, gar.getID());
			ps.setDouble(2, gar.getType());
			ps.setInt(3,gar.getBeforeState());
			ps.setInt(4,gar.getAfterState());
			ps.setInt(5,gar.getValue());
			ps.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		}
//		System.out.println("Action row created successfully");
	}
	
	/**
	 * Inserts a row into the value function table.
	 * @param id the id of the table
	 * @param row the row to insert
	 */
	public void insertStateValue(int id, StateValueRow row){
		
		try {
			String sqlString = "INSERT INTO StateValue_" + id + " (ID,STATE,VALUE)"
					   + "VALUES (?,?,?);";
			PreparedStatement ps = conn.prepareStatement(sqlString);
			ps.setLong(1, row.getId());
			ps.setArray(2, conn.createArrayOf("integer", StacDBHelper.transformToIntegerArr(row.getState())));
			ps.setDouble(3, row.getValue());
			ps.execute();
			ps.close();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
//		System.out.println("Value row created successfully");
	}
	
	/**
	 * Drops a table.
	 * @param tableName the name of the table to drop
	 */
	public void dropTable(String tableName){
		
		try {
			String sqlString = "DROP TABLE " + tableName + ";";
			PreparedStatement ps = conn.prepareStatement(sqlString);
			ps.execute();
			ps.close();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
//		System.out.println("Table dropped successfully");
	}
	
	/**
	 * Deletes a row in a table.
	 * @param rowID the id of the row
	 * @param tableName the name of the table containing the row
	 */
	public void deleteRowInTable(int rowID, String tableName){
		
		try {
			String sqlString = "DELETE FROM " + tableName + " WHERE id=" + rowID + ";";
			PreparedStatement ps = conn.prepareStatement(sqlString);
			ps.execute();
			ps.close();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
//		System.out.println("Row deleted successfully");
	}
	
	/**
	 * Selects the row from the extGameStates_{gameID} table with the ID = {egsrID}
	 * @param gameID the id of the table
	 * @param egsrID the id of the row
	 * @return the row as a {@link ExtGameStateRow} object
	 */
	public ExtGameStateRow selectEGSR(int gameID, int egsrID){
		ExtGameStateRow egsr = new ExtGameStateRow(egsrID, "");
		try {
			stmt = conn.createStatement();
		    ResultSet rs = stmt.executeQuery( "SELECT * FROM extgamestates_" + gameID + " WHERE ID=" + egsrID +";");
		    while ( rs.next() ) {
		    	//should only be one as ID is a unique primary key;
		    	egsr.setGameName(rs.getString("name"));
		    	egsr.setPastTrades((Integer[][]) rs.getArray("pasttrades").getArray());
		    	egsr.setFutureTrades((Integer[][]) rs.getArray("futuretrades").getArray());
		    	egsr.setPastPBPs((Integer[][]) rs.getArray("pastpbp").getArray());
		    	egsr.setFuturePBPs((Integer[][]) rs.getArray("futurepbp").getArray());
		    	egsr.setETWs((Integer[]) rs.getArray("etw").getArray());
		    	egsr.setAvgETBs((Integer[][]) rs.getArray("avgetb").getArray());
		    	egsr.setSettETBs((Integer[][]) rs.getArray("settlementetb").getArray());
		    	egsr.setRoadETBs((Integer[][]) rs.getArray("roadetb").getArray());
		    	egsr.setCityETBs((Integer[][]) rs.getArray("cityetb").getArray());
		    	egsr.setDevETBs((Integer[][]) rs.getArray("devcardetb").getArray());
		    	egsr.setTerritoryConnected((Integer[]) rs.getArray("connterr").getArray());
		    	egsr.setTerritoryIsolated((Integer[]) rs.getArray("notisoterr").getArray());
		    	egsr.setLongestRoads((Integer[]) rs.getArray("longestroads").getArray());
		    	egsr.setLongestPossibleRoads((Integer[]) rs.getArray("longestposroads").getArray());
		    	egsr.setDistanceToOpponents((Integer[]) rs.getArray("disttoopp").getArray());
		    	egsr.setDistanceToPort((Integer[]) rs.getArray("disttoport").getArray());
		    	egsr.setDistanceToNextLegalLoc((Integer[]) rs.getArray("disttolegal").getArray());
		    	egsr.setRssTypeAndNumber((Integer[][]) rs.getArray("rsstypeandno").getArray());
		    }
		    rs.close();
		    stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
		return egsr;
	}
	
	/**
	 * Selects the row from the obsGameStates_{gameID} table with the ID = {ogsrID}
	 * @param gameID the id of the table
	 * @param ogsrID the id of the row
	 * @return the row as a {@link ObsGameStateRow} object
	 */
	public ObsGameStateRow selectOGSR(int gameID, int ogsrID){
		ObsGameStateRow ogsr = new ObsGameStateRow(ogsrID, "");
		try {
			stmt = conn.createStatement();
		    ResultSet rs = stmt.executeQuery( "SELECT * FROM obsgamestates_" + gameID + " WHERE ID=" + ogsrID +";");
		    while ( rs.next() ) {
		    	//should only be one as ID is a unique primary key;
		    	ogsr.setGameName(rs.getString("name"));
		    	ogsr.setHexLayout((Integer[]) rs.getArray("hexlayout").getArray());
		    	ogsr.setNumberLayout((Integer[]) rs.getArray("numberlayout").getArray());
		    	ogsr.setRobberHex(rs.getInt("robberhex"));
		    	ogsr.setGameState(rs.getInt("gamestate"));
		    	ogsr.setDevCardsLeft(rs.getInt("devcardsleft"));
		    	ogsr.setDiceResult(rs.getInt("diceresult"));
		    	ogsr.setStartingPlayer(rs.getInt("startingplayer"));
		    	ogsr.setCurrentPlayer(rs.getInt("currentplayer"));
		    	ogsr.setPlayedDevCard(rs.getBoolean("playeddevcard"));
		    	try {
		    		ogsr.setPiecesOnBoard((Integer[][]) rs.getArray("piecesonboard").getArray());
				} catch (Exception e) {
					// if this is empty we might get cast exception as it tries to cast from single array to multi dimensional one... interesting
					Integer[][] decoy = new Integer[1][3];
					decoy[0] = new Integer[]{-1,-1,-1}; //decoy just so the toString method will not fail (remember to check for this when replacing during replay)
					ogsr.setPiecesOnBoard(decoy);
				}
		    	
		    	ogsr.setPlayers((Integer[][]) rs.getArray("players").getArray());
		    	ogsr.setTouchingNumbers((Integer[][][]) rs.getArray("touchingnumbers").getArray());
		    }
		    rs.close();
		    stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
		return ogsr;
	}
	
	/**
	 * Selects the row from the gameActions_{gameID} table with the ID = {garID}
	 * @param gameID the id of the table
	 * @param garID the id of the row
	 * @return the row as a {@link GameActionRow} object
	 */
	public GameActionRow selectGAR(int gameID, int garID){
		GameActionRow gar = new GameActionRow(garID);
		try {
			stmt = conn.createStatement();
		    ResultSet rs = stmt.executeQuery( "SELECT * FROM gameactions_" + gameID + " WHERE ID=" + garID +";");
		    while ( rs.next() ) {
		    	//should only be one as ID is a unique primary key;
		    	gar.setBeforeState(rs.getInt("beforestate"));
		    	gar.setAfterState(rs.getInt("afterstate"));
		    	gar.setType(rs.getDouble("type"));
		    	gar.setValue(rs.getInt("value"));
		    }
		    rs.close();
		    stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
		return gar;
	}
	
	/**
	 * Selects the row from the StateValue_{id} table with the ID = {svrID}
	 * @param id the id of the table
	 * @param svrID the id of the row
	 * @return the row as a {@link StateValueRow} object
	 */
	public StateValueRow selectSVR(int id, long svrID){
		StateValueRow svr = new StateValueRow(svrID, null, 0);
		try {
			stmt = conn.createStatement();
		    ResultSet rs = stmt.executeQuery( "SELECT * FROM statevalue_" + id + " WHERE ID=" + svrID +";");
		    while ( rs.next() ) {
		    	//should only be one as ID is a unique primary key;
		    	svr.setState(StacDBHelper.transformToIntArr((Integer[]) rs.getArray("state").getArray()));
		    	svr.setValue(rs.getDouble("value"));
		    }
		    rs.close();
		    stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
		return svr;//remember to check for null vector here to stop when encountering the end of the table
	}
	
	/**
	 * @param name the player's nickname
	 * @return the corresponding id or -1 if smth went wrong or nickname doesn't exist in db
	 */
	public int getIDfromPlayerName(String name){
		int id = -1;
		try {
			stmt = conn.createStatement();
		    ResultSet rs = stmt.executeQuery( "SELECT * FROM players WHERE NAME='" + name +"';");
		    while ( rs.next() ) {
		    	id = rs.getInt("id");

		    }
		    rs.close();
		    stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return id;
	}
	
	/**
	 * @param name the game's name
	 * @return the corresponding id or -1 if smth went wrong or game name doesn't exist in db
	 */
	public int getIDfromGameName(String name){
		int id = -1;
		try {
			stmt = conn.createStatement();
		    ResultSet rs = stmt.executeQuery( "SELECT * FROM games WHERE NAME='" + name +"';");
		    while ( rs.next() ) {
		    	id = rs.getInt("id");
		    }
		    rs.close();
		    stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return id;
	}
	
	/**
	 * @param name the game's name
	 * @return the corresponding id or -1 if smth went wrong or game name doesn't exist in db
	 */
	public int getIDfromSimGameName(String name){
		int id = -1;
		try {
			stmt = conn.createStatement();
		    ResultSet rs = stmt.executeQuery( "SELECT * FROM simulation_games WHERE NAME='" + name +"';");
		    while ( rs.next() ) {
		    	id = rs.getInt("id");
		    }
		    rs.close();
		    stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return id;
	}
	
	/**
	 * Adds a new column to a table.
	 * @param tableName the name of a table
	 * @param colName the name of the new column
	 * @param colType the type of the values to be stored
	 * @param notNull allow not null values or not
	 */
	public void addColToTable(String tableName, String colName, String colType, boolean notNull){
		String nn = "";
		if(notNull)
			nn = "NOT NULL";
		try {
			stmt = conn.createStatement();
        String sql = "ALTER TABLE " + tableName + " ADD COLUMN " + colName + " " + colType + " " + nn ;  
			stmt.executeUpdate(sql);
			stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Drops a column from a table.
	 * @param tableName the name of the table containing the column
	 * @param colName the name of the column to be dropped
	 */
	public void dropColFromTable(String tableName, String colName){
		try {
			stmt = conn.createStatement();
        String sql = "ALTER TABLE " + tableName + " DROP COLUMN " + colName;  
			stmt.executeUpdate(sql);
			stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Renames a column in a table.
	 * @param tableName the name of the table
	 * @param oldName the column's old name
	 * @param newName the column's new name
	 */
	public void renameColFromTable(String tableName, String oldName, String newName){
		try {
			stmt = conn.createStatement();
        String sql = "ALTER TABLE " + tableName + " RENAME COLUMN " + oldName + " TO " + newName ;  
			stmt.executeUpdate(sql);
			stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Updates the value "DISTTOPORT" of a row.
	 * @param egsr the row containing the correct distance
	 * @param gameID the id of the game 
	 */
	public void updateDistanceToPort(ExtGameStateRow egsr, int gameID){
		try {
			String sqlString = "UPDATE extgamestates_" + gameID + "set DISTTOPORT=? where ID=" + egsr.getID() + ";";
			PreparedStatement ps = conn.prepareStatement(sqlString);
			ps.setArray(1, conn.createArrayOf("integer", egsr.getDistanceToPort()));
			ps.execute();
			ps.close();
		} catch(SQLException e){
			e.printStackTrace();
		}
	}
	
	/**
	 * Updates the value "DISTTOOPP" of a row.
	 * @param egsr the row containing the correct distance
	 * @param gameID the id of the game 
	 */
	public void updateDistanceToOpp(ExtGameStateRow egsr, int gameID){
		try {
			String sqlString = "UPDATE extgamestates_" + gameID + "set DISTTOOPP=? where ID=" + egsr.getID() + ";";
			PreparedStatement ps = conn.prepareStatement(sqlString);
			ps.setArray(1, conn.createArrayOf("integer", egsr.getDistanceToOpponents()));
			ps.execute();
			ps.close();
		} catch(SQLException e){
			e.printStackTrace();
		}
	}
	
	/**
	 * Updates the value "DISTTOLEGAL" of a row.
	 * @param egsr the row containing the correct distance
	 * @param gameID the id of the game 
	 */
	public void updateDistanceToLegal(ExtGameStateRow egsr, int gameID){
		try {
			String sqlString = "UPDATE extgamestates_" + gameID + "set DISTTOLEGAL=? where ID=" + egsr.getID() + ";";
			PreparedStatement ps = conn.prepareStatement(sqlString);
			ps.setArray(1, conn.createArrayOf("integer", egsr.getDistanceToNextLegalLoc()));
			ps.execute();
			ps.close();
		} catch(SQLException e){
			e.printStackTrace();
		}
	}
	
	/**
	 * updates the action with {actionID} to the new {value} from table gameactions_{gameID}
	 * @param gameID
	 * @param actionID
	 * @param value
	 */
	public void updateActionValue(int gameID, int actionID, int value){
		try {
	        stmt = conn.createStatement();
	        String sql = "UPDATE gameactions_" + gameID + " set VALUE =" + value + " where ID=" + actionID +";";
	        stmt.executeUpdate(sql);
	        conn.commit();
	        stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Updates the value of the pbps column to {pbp} for the game with the ID = {gameID}
	 * @param gameID
	 * @param pbp
	 */
	public void updateTotalPBP(int gameID, int[][] pbp){
		try {
			String sqlString = "UPDATE games set PBPS=? where ID=" + gameID + ";";
			PreparedStatement ps = conn.prepareStatement(sqlString);
			ps.setArray(1, conn.createArrayOf("integer", pbp));
			ps.execute();
			ps.close();
		} catch(SQLException e){
			e.printStackTrace();
		}
	}
	
	/**
	 * Updates the value of the trades column to {trades} for the game with the ID = {gameID}
	 * @param gameID
	 * @param trades
	 */
	public void updateTotalTrades(int gameID, int[][] trades){
		try {
			String sqlString = "UPDATE games set TRADES=? where ID=" + gameID + ";";
			PreparedStatement ps = conn.prepareStatement(sqlString);
			ps.setArray(1, conn.createArrayOf("integer", trades));
			ps.execute();
			ps.close();
		} catch(SQLException e){
			e.printStackTrace();
		}
	}
	/**
	 * Checks if a table exists
	 * @param tableName one of the static final strings of this class
	 * @return
	 */
	public boolean tableExists(String tableName){
		try {
			DatabaseMetaData dbm = conn.getMetaData();
			ResultSet tables = dbm.getTables(null, null, tableName , null);
			if (tables.next()) {
			  return true;
			}
			else {
			  return false;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;// if an exception is thrown return false as it means we cannot look up the table
	}
	
	/**
	 * 
	 * @param tableName the name of the table
	 * @return the size of the table or -1 if it fails
	 */
	public int getTableSize(String tableName){
		int index = 0;
		try {
			stmt = conn.createStatement();
		    ResultSet rs = stmt.executeQuery( "SELECT COUNT(*) FROM " + tableName +";");
		    while ( rs.next() ) {
		    	index = rs.getInt("count");
		    }
		    rs.close();
		    stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
			return -1; //negative value for failure
		}
		return index;
	}
	
	/**
	 * 
	 * @param gameID the game's id
	 * @return the 4 ids of the players participating in the game
	 */
	public int[] getPlayersIDsFromGame(int gameID){
		int[] ids = new int[4];
		try {
			stmt = conn.createStatement();
		    ResultSet rs = stmt.executeQuery( "SELECT * FROM games WHERE ID=" + gameID +";");
		    while ( rs.next() ) { //should only be 1 :)
		    	for(int i = 0; i < 4; i++){
		    		ids[i] = rs.getInt("player" + (i+1)); //be careful! we get a 0 if position is null... :\
		    	}
		    }
		    rs.close();
		    stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return ids;
	}
	
	/**
	 * @param playerID the player's id
	 * @return empty string if it can't find it or the player's name
	 */
	public String getPlayerNameByID(int playerID){
		String name = "";
		try {
			stmt = conn.createStatement();
		    ResultSet rs = stmt.executeQuery( "SELECT * FROM players WHERE ID=" + playerID +";");
		    while ( rs.next() ) {
		    	name = rs.getString("name");
		    }
		    rs.close();
		    stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return name;
	}
	
	/**
	 * @param gameID the game's id
	 * @return empty string if it can't find it or the game's name
	 */
	public String getGameNameByID(int gameID){
		String name = "";
		try {
			stmt = conn.createStatement();
		    ResultSet rs = stmt.executeQuery( "SELECT * FROM games WHERE ID=" + gameID +";");
		    while ( rs.next() ) {
		    	name = rs.getString("name");
		    }
		    rs.close();
		    stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return name;
	}
	
	/**
	 * @param gameID the game's id
	 * @return empty string if it can't find it or the game's name
	 */
	public String getSimGameNameByID(int gameID){
		String name = "";
		try {
			stmt = conn.createStatement();
		    ResultSet rs = stmt.executeQuery( "SELECT * FROM simulation_games WHERE ID=" + gameID +";");
		    while ( rs.next() ) {
		    	name = rs.getString("name");
		    }
		    rs.close();
		    stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return name;
	}
	
	/**
	 * Check if total pbp and total trades have been collected
	 * @return
	 */
	public boolean areAnyTotalNumbersCollected(int gameID){
		if(gameID == -1)
			return false;
		else {
			if(getTotalPBPs(gameID)==null)
				return false;
			if(getTotalTrades(gameID)==null)
				return false;
			return true; //check both to make sure before returning true
		}
	}
	
	/**
	 * @param gameID for the game we want to get the total number of pbps 
	 * @return
	 */
	public Integer[][] getTotalPBPs(int gameID){
		Integer[][] pbps = null;
		try {
			stmt = conn.createStatement();
		    ResultSet rs = stmt.executeQuery( "SELECT * FROM games WHERE ID=" + gameID +";");
		    while (rs.next()) {
		    	try {
		    		pbps = (Integer[][]) rs.getArray("pbps").getArray(); 
				} catch (NullPointerException ne) {
					break; //we will always get a nullpointer if data is inexistent
				}
		    }
		    rs.close();
		    stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return pbps;
	}
	
	/**
	 * Finds all rows in the obsgamestates_gameID table wich have the same value in the gamestate column
	 * @param gameID the game's ID
	 * @param gameState the gameState value we are looking for
	 * @return an array of {@link ObsGameStateRow} objects which have the same state; may return null if an Sql exception gets thrown
	 */
	public ObsGameStateRow[] getAllObsStatesOfAKind(int gameID, int gameState){
		Vector ogsrs = new Vector();
		try {
			stmt = conn.createStatement();
		    ResultSet rs = stmt.executeQuery( "SELECT * FROM obsgamestates_" + gameID + " WHERE GAMESTATE=" + gameState +";");
		    while ( rs.next() ) {
		    	ObsGameStateRow ogsr = new ObsGameStateRow(0, ""); //ID will be reset later
		    	ogsr.setID(rs.getInt("ID"));
		    	ogsr.setGameName(rs.getString("name"));
		    	ogsr.setHexLayout((Integer[]) rs.getArray("hexlayout").getArray());
		    	ogsr.setNumberLayout((Integer[]) rs.getArray("numberlayout").getArray());
		    	ogsr.setRobberHex(rs.getInt("robberhex"));
		    	ogsr.setGameState(rs.getInt("gamestate"));
		    	ogsr.setDevCardsLeft(rs.getInt("devcardsleft"));
		    	ogsr.setDiceResult(rs.getInt("diceresult"));
		    	ogsr.setStartingPlayer(rs.getInt("startingplayer"));
		    	ogsr.setCurrentPlayer(rs.getInt("currentplayer"));
		    	ogsr.setPlayedDevCard(rs.getBoolean("playeddevcard"));
		    	try {
		    		ogsr.setPiecesOnBoard((Integer[][]) rs.getArray("piecesonboard").getArray());
				} catch (Exception e) {
					// if this is empty we might get cast exception as it tries to cast from single array to multi dimensional one... interesting
					Integer[][] decoy = new Integer[1][3];
					decoy[0] = new Integer[]{-1,-1,-1}; //decoy just so the toString method will not fail (remember to check for this when replacing during replay)
					ogsr.setPiecesOnBoard(decoy);
				}
		    	ogsr.setPlayers((Integer[][]) rs.getArray("players").getArray());
		    	ogsr.setTouchingNumbers((Integer[][][]) rs.getArray("touchingnumbers").getArray());
		    	ogsrs.add(ogsr);
		    }
		    rs.close();
		    stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
		ObsGameStateRow[] answer = new ObsGameStateRow[ogsrs.size()];
		int i = 0;
		for(Object o : ogsrs){
			answer[i] = (ObsGameStateRow) o;
			i++;
		}
		return answer;
	}

	
	/**
	 * @param gameID for the game we want to get the total number of trades 
	 * @return a bi-dimensional array with the trades
	 */
	public Integer[][] getTotalTrades(int gameID){
		Integer[][] trades = null;
		try {
			stmt = conn.createStatement();
		    ResultSet rs = stmt.executeQuery( "SELECT * FROM games WHERE ID=" + gameID +";");
		    while ( rs.next() ) {
		    	try {
		    		trades = (Integer[][]) rs.getArray("trades").getArray(); 
				} catch (NullPointerException ne) {
					break; //we will always get a nullpointer if data is inexistent
				}
		    }
		    rs.close();
		    stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return trades;
	}
	
	/**
	 * Deletes the entries in the expert simulations game table and drops the corresponding 3 tables for each game 
	 */
	public void deleteSimulations(){
		int maxID = StacDBHelper.SIMGAMESSTARTID + getTableSize(StacDBHelper.SIMGAMESTABLE);
		for(int i = StacDBHelper.SIMGAMESSTARTID; i < maxID; i++){
			dropTable(StacDBHelper.ACTIONSTABLE + i);
			dropTable(StacDBHelper.OBSFEATURESTABLE + i);
			dropTable(StacDBHelper.EXTFEATURESTABLE + i);
			deleteRowInTable(i, StacDBHelper.SIMGAMESTABLE);
		}
	}
	
	/**
	 * @param dataID the id of the state-value table
	 * @param idx the index of the state array
	 * @return the maximum value of a state feature encountered in a given table
	 */
	public int computeMaxValue(int dataID, int idx){
		int max = Integer.MIN_VALUE;
		try {
			stmt = conn.createStatement();
		    ResultSet rs = stmt.executeQuery( "SELECT max(state[" + idx +"]) from statevalue_" + dataID +";");
		    while ( rs.next() ) {
		    	try {
		    		max =  rs.getInt("max"); 
				} catch (NullPointerException ne) {
					break; //we will always get a nullpointer if data is inexistent
				}
		    }
		    rs.close();
		    stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return max;
	}
	
	/**
	 * @param dataID the id of the state-value table
	 * @param idx the index of the state array
	 * @return the standard deviation of a state feature for a given table
	 */
	public double computeStddev(int dataID, int idx){
		double stddev = Double.MIN_VALUE;
		try {
			stmt = conn.createStatement();
		    ResultSet rs = stmt.executeQuery( "SELECT stddev(state[" + idx +"]) from statevalue_" + dataID +";");
		    while ( rs.next() ) {
		    	try {
		    		stddev =  rs.getDouble("stddev"); 
				} catch (NullPointerException ne) {
					break; //we will always get a nullpointer if data is inexistent
				}
		    }
		    rs.close();
		    stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return 0;
	}
	
	/**
	 * @param dataID the id of the state-value table
	 * @param idx the index of the state array
	 * @return the mean of a state feature for a given table
	 */
	public double computeMean(int dataID, int idx){
		double mean = Double.MIN_VALUE;
		try {
			stmt = conn.createStatement();
		    ResultSet rs = stmt.executeQuery( "SELECT avg(state[" + idx +"]) from statevalue_" + dataID +";");
		    while ( rs.next() ) {
		    	try {
		    		mean =  rs.getDouble("avg"); 
				} catch (NullPointerException ne) {
					break; //we will always get a nullpointer if data is inexistent
				}
		    }
		    rs.close();
		    stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return mean;
	}
	
///////////////////////////Utilities for dealing with int[] and Integer[]////////////////////
	/**
	 * Transforms a int[][] array into a Integer[][] one
	 * @param array
	 * @return
	 */
	public static Integer[][] transformToIntegerArr2(int[][] array){
		Integer[][] objArray = new Integer[array.length][];
		for(int i = 0; i < array.length; i++){
			objArray[i] = new Integer[array[i].length];
			for(int j = 0; j < array[i].length; j++){
				objArray[i][j] = array[i][j];
			}
		}
		return objArray;
	}
	/**
	 * Transforms a Integer[][] array into a int[][] one
	 * @param array
	 * @return
	 */
	public static int[][] transformToIntArr2(Integer[][] array){
		int[][] a = new int[array.length][];
		for(int i = 0; i < array.length; i++){
			a[i] = new int[array[i].length];
			for(int j = 0; j < array[i].length; j++){
				a[i][j] = array[i][j];
			}
		}
		return a;
	}
	/**
	 * Transforms a Integer[] array into a int[] one
	 * @param array
	 * @return
	 */
	public static int[] transformToIntArr(Integer[] array){
		int[] a = new int[array.length];
		for(int i = 0; i < array.length; i++){
			a[i] = array[i];
		}
		return a;
	}
	
	/**
	 * Transforms a int[] array into a Integer[] one
	 * @param array
	 * @return
	 */
	public static Integer[] transformToIntegerArr(int[] array){
		Integer[] a = new Integer[array.length];
		for(int i = 0; i < array.length; i++){
			a[i] = array[i];
		}
		return a;
	}
	
}
