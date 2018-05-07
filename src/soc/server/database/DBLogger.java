package soc.server.database;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import soc.dialogue.StacTradeMessage;
import soc.disableDebug.D;
import soc.game.SOCGame;

import soc.game.SOCPlayer;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCTradeOffer;
import soc.game.StacTradeOffer;
import soc.message.SOCAcceptOffer;
import soc.message.SOCBankTrade;
import soc.message.SOCMakeOffer;
import soc.message.SOCRejectOffer;
import soc.robot.stac.Persuasion;
import soc.robot.stac.simulation.Simulation;
import soc.util.SOCRobotParameters;

public class DBLogger implements DBHelper {

    public DBLogger() {
    	//make the results folder
		File dir = new File(RESULTS_DIR);
        if(!dir.exists())
        	dir.mkdirs();
        this.offerStats = new HashMap<String, SOCPlayerOfferStats>();
    }

    protected int numGamesDone = 0;
    private int numTurnsInSimulation = 0; //overall number of turns (in the simulation)
    private boolean offerHasBeenMadeInThisTurn = false;
    
    protected int numGamesDiscarded = 0;
    
    
    private BufferedWriter output = null;
    private BufferedWriter offerOut = null;
    //private BufferedWriter summaryOut = null;

    protected final String RESULTS_DIR = "results/";

    private final String RESULTS_FILENAME = "/results";
    private final String OFFER_FILENAME = "/offers";
    private final String SUMMARY_FILENAME = "/summary";
    private final String BPP_SUMMARY_FILENAME = "/bppSummary";
    private final String HAS_RES_PREDICT_SUMMARY_FILENAME = "/hasResPredictSummary";
    private final String CONFIG_FILENAME = "/config.txt";

    private static final String DELIM = "\t";
    private static final String DELIM_SUMM = "\t";

    private String summaryFileName;
    private String bppSummaryFileName;
    private String hasResPredictSummaryFileName;

    // The performance stats, with basic trading stats
    protected final Map<String, SOCPlayerStats> stats = new HashMap<String, SOCPlayerStats>(); 
    
    // Distribution of offers per turn per player
    private final Map<String, SOCPlayerOfferStats> offerStats;

    // Count of correct and incorrect build plan predictions - may not be used 
    private final Map<String, int[]> buildPlanPredStats = new HashMap<String, int[]>();
    private boolean bppUsed = false;

    // Count of correct and incorrect resource holding predictions - may not be used 
    private final Map<String, int[]> hasResPredStats = new HashMap<String, int[]>();
    private boolean hasResPredictUsed = false;

    private String curPlayerTurn;

    private static final boolean OUTPUT_OFFER_DISTRIB = false;

    private Date runStartDate;

    protected String dirName;

    public void resetGamesDone() {
        numGamesDone = 0;
        numTurnsInSimulation = 0;
        offerHasBeenMadeInThisTurn = false;
        stats.clear();
        offerStats.clear();
        buildPlanPredStats.clear();
        bppUsed = false;
        hasResPredStats.clear();
        hasResPredictUsed = false;
    }

    @Override
    public int getNumGamesDone() {
        return numGamesDone;
    }

    public int getNumGamesDiscarded() {
        return numGamesDiscarded;
    }
    /**
	 * Number of games done plus the number of games discarded
     * @return total number of games
	 */
    public int getTotalNumGames(){
    	return numGamesDone + numGamesDiscarded;
    }
    
    @Override
    public void initialize(String user, String pswd, Properties props) throws SQLException {
    }

    public void startRun(String gameName, List<String> config) {
        runStartDate = new Date();
        String ds = "_" + runStartDate.toString().replace(':','_').replace(' ','_');
        ds = ds + "_" + UUID.randomUUID();
        try {    	    
            // probably need to construct directory first...
            dirName = RESULTS_DIR + gameName + ds;
            File dir = new File(dirName);  
            dir.mkdir();

            BufferedWriter configFile = new BufferedWriter(new FileWriter(new File(dirName + CONFIG_FILENAME)));
            for (String s : config) {
                configFile.write(s);
                configFile.newLine();
            }

            configFile.close();

            output = new BufferedWriter(new FileWriter(new File(dirName + RESULTS_FILENAME + ".txt" )));
            printFirstLine();
            summaryFileName = dirName  + SUMMARY_FILENAME + ".txt";
            bppSummaryFileName = dirName + BPP_SUMMARY_FILENAME + ".txt";
            hasResPredictSummaryFileName = dirName + HAS_RES_PREDICT_SUMMARY_FILENAME + ".txt";
            //summaryOut = new BufferedWriter(new FileWriter(new File(dirName  + SUMMARY_FILENAME + ".txt" )));
            if (OUTPUT_OFFER_DISTRIB) {
                offerOut = new BufferedWriter(new FileWriter(new File(dirName + OFFER_FILENAME  + ".txt")));
            }
        }
        catch (IOException ex) {
            // throw a SQLException?
            ex.printStackTrace();
        }
    }

    @Override
    public String getUserPassword(String sUserName) throws SQLException {
        return null;
    }

    @Override
    public boolean getTradeReminderSeen(String sUserName) throws SQLException {
        return false;
    }
    
    @Override
    public boolean getUserMustDoTrainingGame(String sUserName) throws SQLException {
        return false;
    }

    @Override
    public String getUserFromHost(String host) throws SQLException {
        return null;
    }

    @Override
    public boolean createAccount(String userName, String host, String password, String email, long time) throws SQLException {
        return false;
    }

    @Override
    public boolean recordLogin(String userName, String host, long time) throws SQLException {
        return true;
    }

    @Override
    public boolean updateLastlogin(String userName, long time) throws SQLException {
        return true;
    }

    @Override
    public boolean updateShowTradeReminder(String userName, boolean flag) throws SQLException {
        return true;
    }

    @Override
    public boolean updateUserMustDoTrainingGame(String userName, boolean flag) throws SQLException {
        return true;
    }

    /*
    public boolean saveGameScores(SOCGame game) {
            synchronized (output) {
            try {
                    output.write(game.getName() + ", " + game.getRoundCount() + ", " + game.getFirstPlayer());
                    output.newLine();
                    if (OUTPUT_OFFER_DISTRIB) {
                        offerOut.write(numGamesDone + 1);
                offerOut.newLine();
                    }
                    for (int i=0; i<4; i++) {
                            SOCPlayer p = game.getPlayer(i);
                            updateStats(p.getName(), p.getTotalVP());
                            printStats(game.getName(), p.getName(), p.getTotalVP());
                            if (OUTPUT_OFFER_DISTRIB) {
                                printOfferStats(p.getName());
                            }
                    }
                    printCumulativeStats();
                    output.newLine();    		    		
                    output.flush();
                    if (OUTPUT_OFFER_DISTRIB) {
                        offerOut.newLine();
                        offerOut.flush();
                    }

                    numGamesDone++;

            }
            catch (IOException ex) {
                    ex.printStackTrace();
                    return false;
            }
    }
    return true;
    }
    */

    /**
     *
     * @param ga
     * @return
     * @throws SQLException
     */
    @Override
    public boolean saveGameScores(SOCGame ga) throws SQLException {

        try {
        	int roundCount = ga.getRoundCount();
            String gameName = ga.getName();
            String player1 = ga.getPlayer(0).getName();
            String player2 = ga.getPlayer(1).getName();
            String player3 = ga.getPlayer(2).getName();
            String player4 = ga.getPlayer(3).getName();
            short score1 = (short) ga.getPlayer(0).getTotalVP();
            short score2 = (short) ga.getPlayer(1).getTotalVP();
            short score3 = (short) ga.getPlayer(2).getTotalVP();
            short score4 = (short) ga.getPlayer(3).getTotalVP();
            
            //resources by dice roll
            int[] resources = new int[4];
            for (int i = 0; i < 4; i++) {
                int[] resourceStats = ga.getPlayer(i).getResourceRollStats();  //this array does not use its 0-entry
                resources[i] = resourceStats[SOCResourceConstants.CLAY]
                    + resourceStats[SOCResourceConstants.ORE]
                    + resourceStats[SOCResourceConstants.SHEEP]
                    + resourceStats[SOCResourceConstants.WHEAT]
                    + resourceStats[SOCResourceConstants.WOOD];
            }
            Date startTime = ga.getStartTime();
//            boolean regularFlag = ga.isGameOptionSet("RG");

            // Make sure all players have had stats objects created - prior to the first call of this method, stats will only have been generated if they have participated in a trade
            //add the number of resources by dice at the same time
            getStats(player1).setNumResourcesReceivedByDice(resources[0]);
            getStats(player2).setNumResourcesReceivedByDice(resources[1]);
            getStats(player3).setNumResourcesReceivedByDice(resources[2]);
            getStats(player4).setNumResourcesReceivedByDice(resources[3]);

            if(!Simulation.failedMctsSimulation){ //do not gather stats if the game is thrown away due to a failed simulation
	            synchronized (output) {
	                output.write(gameName + DELIM + startTime.toString() + DELIM);
	                for (String name : stats.keySet()) {
	                    // ugh...
	                    int score;
	                    if (name.equals(player1)) {
	                        score = score1;
	                    } else if (name.equals(player2)) {
	                        score = score2;
	                    } else if (name.equals(player3)) {
	                        score = score3;
	                    } else {
	                        score = score4;
	                    }
	                    printStats(gameName, name, score);
	                }
	                output.newLine();                        
	                output.flush();
	
	                updateStats(player1, score1, roundCount);
	                updateStats(player2, score2, roundCount);
	                updateStats(player3, score3, roundCount);
	                updateStats(player4, score4, roundCount);
	                printCumulativeStats();   			    		
	
	                if (OUTPUT_OFFER_DISTRIB) {
	                    offerOut.write(Integer.toString(numGamesDone + 1));
	                    offerOut.newLine();
	                    String players[] = new String[] {player1, player2, player3, player4};
	                    for (String p : players) {
	                        printOfferStats(p);
	                    }
	                    offerOut.newLine();
	                    offerOut.flush();
	                }
	                numGamesDone++;
	            }
            }else
            	numGamesDiscarded++;
        }
        catch (IOException ex) {
            ex.printStackTrace();
            return false;
        }
        return true;
    }

    public SOCPlayerStats getStats(String player) {
        SOCPlayerStats s = stats.get(player);
        if (s==null) {
            s=new SOCPlayerStats();
            stats.put(player, s);
        }
        return s;
    }

    //TODO: offerStats should be properly initialised for every player in the game, so that we don't need to check for s==null each time this is called
    private SOCPlayerOfferStats getOfferStats(String player) {
        SOCPlayerOfferStats s = offerStats.get(player);
        if (s==null) {
            s=new SOCPlayerOfferStats();
            offerStats.put(player, s);
        }
        return s;
    }

    protected void updateStats(String player, int victoryPoints)  {
        getStats(player).addGame(victoryPoints);
    }
    
    protected void updateStats(String player, int victoryPoints, int roundCount)  {
        getStats(player).addGame(victoryPoints,roundCount);
    }
    
    private void printCumulativeStats() throws IOException {
        BufferedWriter summaryOut = new BufferedWriter(new FileWriter(new File(summaryFileName)));
        Date d = new Date();
        // Output the number of games completed
        SOCPlayerStats first = stats.values().iterator().next();        
        summaryOut.write("Games=" + first.getGamesPlayed());
        summaryOut.newLine();
        summaryOut.write("Seconds=" + (d.getTime() - runStartDate.getTime()) / 1000);
        summaryOut.newLine();
        summaryOut.write("Turns per game=" + ((double) numTurnsInSimulation / (double)first.getGamesPlayed()));
        summaryOut.newLine();
        summaryOut.newLine();
    	// Output cumulative stats...
        summaryOut.write("name" + DELIM_SUMM + "win prob" + DELIM_SUMM + "Z(win prob)" + DELIM_SUMM + 
                "VP" + DELIM_SUMM + "loser VP" + DELIM_SUMM +
                "offers" + DELIM_SUMM + "successful offers" + DELIM_SUMM + "total trades" + DELIM_SUMM + 
                "offers our turn" + DELIM_SUMM + "successful offers our turn" + DELIM_SUMM + "others' offers accepted our turn" + DELIM_SUMM + 
                "resources by trade" + DELIM_SUMM + "resources by dice" + DELIM_SUMM +
                "BPs" + DELIM_SUMM + "settlement BPs" + DELIM_SUMM + "city BPs" + DELIM_SUMM + "card BPs" + DELIM_SUMM + "LA BPs" + DELIM_SUMM + "LR BPs" + DELIM_SUMM + 
                "pieces built" + DELIM_SUMM + "roads built" + DELIM_SUMM + "settlements built" + DELIM_SUMM + "cities built" + DELIM_SUMM + "cards bought" + DELIM_SUMM + 
                "LA gained" + DELIM_SUMM + "LR gained" + DELIM_SUMM +
                "force-accepts made" + DELIM_SUMM + "force-accepts complied with" + DELIM_SUMM +
                "embargoes proposed" + DELIM_SUMM + "embargoes complied with" + DELIM_SUMM +
                "blocks proposed" + DELIM_SUMM + "blocks complied with" + DELIM_SUMM +
                "disjunctive give offers" + DELIM_SUMM + "disjunctive get offers" + DELIM_SUMM +
                "partial offers empty give" + DELIM_SUMM + "partial offers empty get" +
                "\n");
        for (String name : stats.keySet()) {
            SOCPlayerStats s = stats.get(name);
            SOCPlayerOfferStats os = getOfferStats(name);
            
            summaryOut.write(name + DELIM_SUMM  
                            + s.getWinProbability() + DELIM_SUMM 					
                            + s.getWinProbZ() + DELIM_SUMM 
                            + s.getAverageVP() + DELIM_SUMM 
                            + s.getAverageLoserVP() + DELIM_SUMM 
                            + s.getAverageOffers() + DELIM_SUMM 
//                            + s.getAverageTradesMadeAccepter() + DELIM_SUMM 
                            + s.getAverageTradesMadeOfferer() + DELIM_SUMM 
                            + (s.getAverageTradesMadeOfferer() + s.getAverageTradesMadeAccepter()) + DELIM_SUMM
                            + s.getAverageOffersOurTurn() + DELIM_SUMM 
                            + s.getAverageTradesMadeOffererOurTurn() + DELIM_SUMM 
                            + s.getAverageTradesMadeAccepterOurTurn() + DELIM_SUMM 
//                            + s.getAverageInitialTradeOfferers() + DELIM_SUMM 
                            + s.getAverageNumRescourcesReceivedByTrading() + DELIM_SUMM
                            + s.getAverageNumRescourcesReceivedByDice() + DELIM_SUMM
//                            + ((double) os.getNumTurnInitialOffers() / (double) numGamesDone) + DELIM_SUMM
//                            + ((double) os.getNumTradesMatchingTurnInitialOffer() / (double) numGamesDone) + DELIM_SUMM
//                            + ((double) os.getNumTradesNotMatchingTurnInitialOffer() / (double) numGamesDone) + DELIM_SUMM
//                            + ((double) os.getNumTradesWithoutTurnInitialOffer() / (double) numGamesDone) + DELIM_SUMM
//                            + ((double) os.getNumTradesWithBankAfterTurnInitialOffer() / (double) numGamesDone) + DELIM_SUMM
//                            + ((double) os.getNumTradesWithPortAfterTurnInitialOffer() / (double) numGamesDone) + DELIM_SUMM
//                            + ((double) os.getNumTradesWithBankWithoutTurnInitialOffer() / (double) numGamesDone) + DELIM_SUMM
//                            + ((double) os.getNumTradesWithPortWithoutTurnInitialOffer() / (double) numGamesDone)
                            + s.getAverageNumBuildPlans() + DELIM_SUMM
//                            + s.getAverageNumBuildPlansRoad() / s.getAverageNumBuildPlans() + DELIM_SUMM
                            + s.getAverageNumBuildPlansSettlement() / s.getAverageNumBuildPlans() + DELIM_SUMM
                            + s.getAverageNumBuildPlansCity() / s.getAverageNumBuildPlans() + DELIM_SUMM
                            + s.getAverageNumBuildPlansCard() / s.getAverageNumBuildPlans() + DELIM_SUMM
                            + s.getAverageNumBuildPlansLA() / s.getAverageNumBuildPlans() + DELIM_SUMM
                            + s.getAverageNumBuildPlansLR() / s.getAverageNumBuildPlans() + DELIM_SUMM
                            + s.getAverageNumPiecesBuiltTotal() + DELIM_SUMM
                            + s.getAverageNumRoadsBuilt() / s.getAverageNumPiecesBuiltTotal() + DELIM_SUMM
                            + s.getAverageNumSettlementsBuilt() / s.getAverageNumPiecesBuiltTotal() + DELIM_SUMM
                            + s.getAverageNumCitiesBuilt() / s.getAverageNumPiecesBuiltTotal() + DELIM_SUMM
                            + s.getAverageNumCardsBought() / s.getAverageNumPiecesBuiltTotal() + DELIM_SUMM
                            + s.getAverageNumLAGained() + DELIM_SUMM
                            + s.getAverageNumLRGained() + DELIM_SUMM
                            + s.getAverageNumForceAcceptsMade() + DELIM_SUMM
                            + s.getAverageNumForceAcceptsCompliedWith() + DELIM_SUMM
                            + s.getAverageNumEmbargoesProposed() + DELIM_SUMM
                            + s.getAverageNumEmbargoesCompliedWith() + DELIM_SUMM
                            + s.getAverageNumBlocksProposed() + DELIM_SUMM
                            + s.getAverageNumBlocksCompliedWith() + DELIM_SUMM
                            + s.getAverageNumDisjunctiveGive() + DELIM_SUMM
                            + s.getAverageNumDisjunctiveGet() + DELIM_SUMM
                            + s.getAverageNumPartialGiveEmpty()+ DELIM_SUMM
                            + s.getAverageNumPartialGetEmpty()
            );
            summaryOut.newLine();			
        }
        summaryOut.newLine();         
        summaryOut.flush();
        
        /*******************************************************/
        /*** Persuasion section ********************************/
        summaryOut.newLine();
        String[] persuasionKeys = SOCPlayerStats.stringPersuasions;
        StringBuffer PERSUASIONTITLEBLOCK = new StringBuffer();
        for(String str : persuasionKeys){
    		PERSUASIONTITLEBLOCK.append(DELIM_SUMM+str.replace(",", "_")+"made");
    		PERSUASIONTITLEBLOCK.append(DELIM_SUMM+str.replace(",", "_")+"accepted");
    		PERSUASIONTITLEBLOCK.append(DELIM_SUMM+str.replace(",", "_")+"timing");
        }
        summaryOut.write("name"+PERSUASIONTITLEBLOCK.toString());
        summaryOut.newLine();
        for (String name : stats.keySet()) {
            SOCPlayerStats s = stats.get(name);

            HashMap<String,Double> averagePersuasionsMade = s.getPersuasionsMadeCounters();
            HashMap<String,Double> averagePersuasionsAccepted = s.getPersuasionsAcceptedCounters();
            HashMap<String,Double> averagePersuasionsTiming = s.getAverageTimePersuasionWasUsed();
            
            StringBuffer specificPERSUASIONBLOCK = new StringBuffer();
            for(String str : persuasionKeys){
            	specificPERSUASIONBLOCK.append(DELIM_SUMM+averagePersuasionsMade.get(str));
            	specificPERSUASIONBLOCK.append(DELIM_SUMM+averagePersuasionsAccepted.get(str));
            	specificPERSUASIONBLOCK.append(DELIM_SUMM+averagePersuasionsTiming.get(str));
            }
            summaryOut.write(name+specificPERSUASIONBLOCK);
            summaryOut.newLine();
        }
        summaryOut.newLine();
        summaryOut.flush();
        /*******************************************************/

        summaryOut.close();
        if (bppUsed) {
            printBPPSummary();
        }
        if (hasResPredictUsed) {
           printHasResourcesPrediction();
        }
    }
    
    private void printBPPSummary() throws IOException {
        BufferedWriter summaryOut = new BufferedWriter(new FileWriter(new File(bppSummaryFileName)));
        // Output cumulative stats...
        for (String name : buildPlanPredStats.keySet()) {
            int[] s = buildPlanPredStats.get(name);
            summaryOut.write(name + DELIM_SUMM  
                    + s[0] + DELIM_SUMM
                    + s[1] + DELIM_SUMM
                    + ( ((double) s[1]) / (double) (s[0])) + DELIM_SUMM
                    + s[2] + DELIM_SUMM
                    + ( ((double) s[2]) / (double) (s[0])) + DELIM_SUMM
                    + s[3] + DELIM_SUMM
                    + ( ((double) s[3]) / (double) (s[0]))
                    );
            summaryOut.newLine();           
        }
        summaryOut.newLine();         
        summaryOut.flush();
        summaryOut.close();
    }
    
    private void printHasResourcesPrediction() throws IOException {
        BufferedWriter summaryOut = new BufferedWriter(new FileWriter(new File(hasResPredictSummaryFileName)));
        // Output cumulative stats...
        for (String name : hasResPredStats.keySet()) {
            int[] s = hasResPredStats.get(name);
            summaryOut.write(name + DELIM_SUMM  
                    + s[0] + DELIM_SUMM
                    + s[1] + DELIM_SUMM
                    + ( ((double) s[1]) / (double) (s[0])) + DELIM_SUMM
                    + s[2] + DELIM_SUMM
                    + ( ((double) s[2]) / (double) (s[0])) + DELIM_SUMM
                    + s[3] + DELIM_SUMM
                    + ( ((double) s[3]) / (double) (s[0])) + DELIM_SUMM
                    + s[4] + DELIM_SUMM
                    + s[5] + DELIM_SUMM
                    + ( ((double) s[5]) / (double) (s[4])) + DELIM_SUMM
                    + s[6] + DELIM_SUMM
                    + ( ((double) s[6]) / (double) (s[4])) + DELIM_SUMM
                    + s[7] + DELIM_SUMM
                    + ( ((double) s[7]) / (double) (s[4]))
                    );
            summaryOut.newLine();           
        }
        summaryOut.newLine();         
        summaryOut.flush();
        summaryOut.close();
    }

    private void printOfferStats(String playerName) throws IOException {       
        SOCPlayerOfferStats s = offerStats.get(playerName);
        double[] offers = s.getProbs();
        offerOut.write(playerName + DELIM_SUMM );
        for (int i=0; i<offers.length; i++) {
            offerOut.write(offers[i] + DELIM_SUMM);  
        }

        offerOut.newLine();        
    }
    
    private void printFirstLine() throws IOException {
        output.write("Game name" + DELIM + "Date/time" + DELIM);
        for (int i=0; i<4; i++) {
            output.write("Name" + i + DELIM 
                    + "Winner" + i + DELIM
                    + "VP" + i + DELIM
                    + "Offers" + i + DELIM
                    + "SuccessfulOffers" + i + DELIM
                    + "Trades" + i + DELIM
                    + "RecourcesByTrade" + i + DELIM
                    + "RecourcesByDice" + i + DELIM);
        }
        output.newLine();
    }
    
    private void printStats(String gameName, String player, int victoryPoints) throws IOException {
        output.write(player + DELIM + (victoryPoints>=10 ? "1" : "0") + DELIM + victoryPoints + DELIM);			
        SOCPlayerStats s = stats.get(player);
        output.write(s.getNumOffersCurrent() + DELIM + s.getNumSuccessfulOffersCurrent() + DELIM + s.getNumTradesCurrent() + DELIM
                + s.getNumResourcesReceivedByTradingCurrent() + DELIM
                + s.getNumResourcesReceivedByDiceCurrent() + DELIM
                );
    }

    @Override
    public SOCRobotParameters retrieveRobotParams(String robotName) throws SQLException {
        return null;
    }

    @Override
    public void cleanup() throws SQLException {	}

    public void endRun() {
        try {
            output.close();
            //summaryOut.close();
            if (offerOut != null) {
                offerOut.close();
            }
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Log a trade offer when using chat trading.
     * If tm.getOffer() returns null, this method is undefined.
     * @param p             the player sending the message
     * @param tm            the StacTradeMessage
     * @param turn          the player whose turn it is
     * @param isInitial     is this an initial trade offer?
     */
    @Override
    public void logChatTradeOffer(SOCPlayer p, StacTradeMessage tm, int turn, boolean isInitial) {
        StacTradeOffer offer = tm.getOffer();
        if (!offerHasBeenMadeInThisTurn)
            getOfferStats(p.getName()).recordTurnInitialOffer(offer);
        offerHasBeenMadeInThisTurn = true;
        
        getStats(p.getName()).incOffers(turn == p.getPlayerNumber(), isInitial);	
        if (isInitial)
            getOfferStats(p.getName()).addOffer();
        
        SOCPlayerStats playerStats = getStats(p.getName());
        if (tm.isForced())
            playerStats.incNumForceAcceptsMade();
        if (offer.hasDisjunctiveGiveSet())
            playerStats.incNumDisjunctiveGive();
        if (offer.hasDisjunctiveGetSet())
            playerStats.incNumDisjunctiveGet();
        if (offer.getGiveSet().equals(SOCResourceSet.EMPTY_SET))
            playerStats.incNumPartialGiveEmpty();
        if (offer.getGetSet().equals(SOCResourceSet.EMPTY_SET))
            playerStats.incNumPartialGetEmpty();
    }
    
    @Override
    public void logChatTradeOffer(SOCPlayer p, StacTradeMessage tm, int turn, boolean isInitial, Persuasion persuasionMove, int roundNo) {
        logChatTradeOffer(p, tm, turn, isInitial);
        
        if (persuasionMove.getIdentifier() != Persuasion.PersuasionIdentifiers.NullPersuasion)
            getStats(p.getName()).incNumPersuasionsMade(persuasionMove, roundNo);
    }


    @Override
    //This method should be called only
    //* to log an SOCMakeOffer trade event
    //* if the game does not use chat trading
    public void logTradeEvent(SOCPlayer p, SOCMakeOffer offer, int turn, boolean isInitial, boolean isForced) {
        //--MG
        if (!offerHasBeenMadeInThisTurn) {
            getOfferStats(p.getName()).recordTurnInitialOffer(offer.getOffer());
        }
        offerHasBeenMadeInThisTurn = true;
        
        getStats(p.getName()).incOffers(turn == p.getPlayerNumber(), isInitial);	
        if (isInitial) {
            getOfferStats(p.getName()).addOffer();
        }
        
        if (isForced) {
            getStats(p.getName()).incNumForceAcceptsMade();
        }
    }

    @Override
    //--MG this one should be called when a trade offer is accepted (from SCOServer.executeTrade)
    public void logTradeEvent(SOCPlayer accepter, SOCPlayer offerer, SOCAcceptOffer offer, int turn, boolean isForced) {
        getStats(accepter.getName()).incTradesMadeAccept(turn == accepter.getPlayerNumber());
        getStats(offerer.getName()).incTradesMadeOffer(turn == offerer.getPlayerNumber());

        SOCTradeOffer offerToBeAccepted = offerer.getCurrentOffer();
        getOfferStats(offerer.getName()).evaluateAcceptedOffer(offerToBeAccepted);
        
        if (isForced) {
            getStats(accepter.getName()).incNumForceAcceptsCompliedWith();
        }
    }
    
    //When a trade offer is made and it has a persuasion attached - record success
    //MG: This is Connor's version, I guess
    @Override
    public void logTradeEvent(SOCPlayer p, SOCMakeOffer offer, int turn, boolean isInitial, Persuasion persuasionMove, int roundNo) {
        //--MG
        if (!offerHasBeenMadeInThisTurn) {
            getOfferStats(p.getName()).recordTurnInitialOffer(offer.getOffer());
        }
        offerHasBeenMadeInThisTurn = true;
        
        getStats(p.getName()).incOffers(turn == p.getPlayerNumber(), isInitial);	
        if (isInitial) {
            getOfferStats(p.getName()).addOffer();
        }
        
        if (persuasionMove.getIdentifier()!=Persuasion.PersuasionIdentifiers.NullPersuasion) {
            getStats(p.getName()).incNumPersuasionsMade(persuasionMove, roundNo);
        }
    }

    //When a trade offer is accepted and it has a persuasion attached - record success
    //MG: This is Connor's version, I guess
    @Override
    public void logTradeEvent(SOCPlayer accepter, SOCPlayer offerer, SOCAcceptOffer offer, int turn, Persuasion persuasionMove) {
        getStats(accepter.getName()).incTradesMadeAccept(turn == accepter.getPlayerNumber());
        getStats(offerer.getName()).incTradesMadeOffer(turn == offerer.getPlayerNumber());

        SOCTradeOffer offerToBeAccepted = offerer.getCurrentOffer();
        getOfferStats(offerer.getName()).evaluateAcceptedOffer(offerToBeAccepted);
        
        if (persuasionMove.getIdentifier()!=Persuasion.PersuasionIdentifiers.NullPersuasion) {
            getStats(accepter.getName()).incNumPersuasionsCompliedWith(persuasionMove);
        }
    }

    /**
     * Log a trade with the bank or a port for the stats output.
     * @param player
     * @param mes 
     */
    @Override
    public void logBankTradeEvent(SOCPlayer player, SOCBankTrade mes) {
        getOfferStats(player.getName()).evaluateBankTrade(mes.getGetSet(), mes.getGiveSet());
    }

    @Override
    public void logTradeEvent(SOCPlayer p, SOCRejectOffer offer, int turn) {
        // No sense tracking this until we handle null responses		
    }

    @Override
    /** 
     * Notification that a turn has ended.  This updates the ending player's offer distribution,
     *  and prepares the starting player's offer distribution object for the coming turn.
     * GameName is currently ignored, as we assume each player being tracked is only in a single game at any given time
     * Call with a dummy player name when a game ends to ensure the last turn is included in results (may want to move
     * that call from server into the saveStats method, which we already know is called at the end of each game)
     */
    public void newTurn(String gameName, String playerName) {
        numTurnsInSimulation++;
        D.ebugPrintlnINFO("***\nTURN " + numTurnsInSimulation + " - " + playerName);
        offerHasBeenMadeInThisTurn = false;
        
        getOfferStats(curPlayerTurn).endTurn();
        getOfferStats(playerName).startTurn();
        curPlayerTurn = playerName;
    }

    private int[] getBPPredStats(String player) {
        int[] s = buildPlanPredStats.get(player);
        if (s==null) {
            s=new int[4];
            buildPlanPredStats.put(player, s);
        }
        return s;
    }
    
    @Override
    public void logBPPrediction(SOCPlayer p, boolean nullEquiv, boolean correctType, boolean fullEquality) {
        bppUsed = true;
        int[] stats = getBPPredStats(p.getName());
        stats[0]++;
        if (nullEquiv) stats[1]++;
        if (correctType) stats[2]++;
        if (fullEquality) stats[3]++;
    }

    private int[] getHasResPredictStats(String player) {
        int[] s = hasResPredStats.get(player);
        if (s==null) {
            s=new int[8];
            hasResPredStats.put(player, s);
        }
        return s;
    }
    
    @Override
    public void logHasResourcesPrediction(SOCPlayer p, boolean beliefCorrect, boolean observedCorrect, boolean subsetCorrect, boolean afterRollOfSeven) {
        hasResPredictUsed = true;
        int[] stats = getHasResPredictStats(p.getName());
        stats[0]++;
        if (beliefCorrect) stats[1]++;
        if (observedCorrect) stats[2]++;
        if (subsetCorrect) stats[3]++;
        //before rolling a seven, tracking of resrources is perfect, so this is the interesting case
        if (afterRollOfSeven) stats[4]++;
        if (beliefCorrect && afterRollOfSeven) stats[5]++;
        if (observedCorrect && afterRollOfSeven) stats[6]++;
        if (subsetCorrect && afterRollOfSeven) stats[7]++;
    }
    
    @Override
    public void logBuildPlan(String player, int bpType) {
        SOCPlayerStats s = stats.get(player);
        if (s==null) {
            s = new SOCPlayerStats();
            stats.put(player, s);
        }
        s.incBuildPlans(bpType);
    }

    @Override
    public void logBuildAction(String player, int piece) {
        D.ebugPrintlnINFO("BUILD " + piece );
        SOCPlayerStats s = stats.get(player);
        if (s==null) {
            s = new SOCPlayerStats();
            stats.put(player, s);
        }
        s.incBuildPiece(piece);        
    }

    @Override
    public void logLA_LRPlayerChanged(String player, String badge) {
        D.ebugPrintlnINFO("BADGE CHANGED " + badge);
        SOCPlayerStats s = stats.get(player);
        if (s==null) {
            s = new SOCPlayerStats();
            stats.put(player, s);
        }
        s.incLA_LRPlayerChanged(badge);
    }

    @Override
    public void logResourcesReceivedByTrading(String player, SOCResourceSet resources) { //int clay, int ore, int sheep, int wheat, int wood) {
//        D.ebugPrintln("Resources received: " + clay + "," + ore + "," + sheep + "," + wheat + "," + wood);
        D.ebugPrintlnINFO("Resources received: " + resources.getTotal());
        SOCPlayerStats s = stats.get(player);
        if (s==null) {
            s = new SOCPlayerStats();
            stats.put(player, s);
        }
        s.incRescourcesReceivedByTrading(resources); //clay, ore, sheep, wheat, wood);
    }
    
    @Override
    public void logEmbargoAction(String player, int action) {
        SOCPlayerStats s = stats.get(player);
        if (s == null) {
            s = new SOCPlayerStats();
            stats.put(player, s);
        }

        switch (action) {
            case EMBARGO_PROPOSE: {
                s.incNumEmbargoesProposed();
                break;
            }
            case EMBARGO_COMPLY: {
                s.incNumEmbargoesCompliedWith();
            }
        }
    }
    
    @Override
    /** Log an action related to blocking trades for the specified player */
    public void logBlockingAction(String player, int action) {
        SOCPlayerStats s = stats.get(player);
        if (s == null) {
            s = new SOCPlayerStats();
            stats.put(player, s);
        }

        switch (action) {
            case BLOCK: {
                s.incNumBlocksProposed();
                break;
            }
            case BLOCK_COMPLY: {
                s.incNumBlocksCompliedWith();
            }
        }
    }

}
