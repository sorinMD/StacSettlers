package soc.server.database;

import java.util.ArrayList;
import java.util.HashMap;

import soc.disableDebug.D;
import soc.game.SOCResourceSet;
import soc.robot.SOCPossiblePiece;
import soc.robot.stac.Persuasion;
import soc.robot.stac.StacPossibleBuildPlan;

/**
 * A class to store statistics for a given player.  Store the number of games played, won, and the total number of victory points.
 * Getters for these, as well as averages.
 * @author kho30
 *
 */
public class SOCPlayerStats {
	
    private int gamesPlayed, wins, losses, victoryPoints, loserVictoryPoints;
    // TODO: Refactor to separate trades we made which were accepted, and trades we accepted
    private int numOffers, numTradesMadeAccepter, numTradesMadeOfferer, numCounterOffers, numInitialOffers;
    private int numOffersOurTurn, numAccepterOurTurn, numOffererOurTurn;

    // Stats for the current game
    private int numOffersCurrent, numSuccessfulOffersCurrent, numTradesCurrent, numResourcesReceivedByTradingCurrent, numResourcesReceivedByDiceCurrent;

    // Stats for build plans
    private int numBuildPlans, numBuildPlansRoad, numBuildPlansSettlement, numBuildPlansCity, numBuildPlansCard, numBuildPlansLA, numBuildPlansLR;

    // Stats for build actions
    private int numPiecesBuiltTotal, numRoadsBuilt, numSettlementsBuilt, numCitiesBuilt, numDevCardsBought;
    
    // Stats for badges
    private int numLAGained, numLRGained;

    // Stats for resources
    private int numRescourcesReceivedByTrading, numRescourcesReceivedByDice;
    
    // Stats for force-accept moves
    private int numForceAcceptsMade, numForceAcceptsCompliedWith;
    
    // Stats for disjunctive and partial offers
    private int numDisjunctiveGive, numDisjunctiveGet, numPartialGiveEmpty, numPartialGetEmpty;
    
    // Stats for the Persuasion Moves (Needs to be independent - hence having 1 entry for each specific persuasion)
    // Super Hacky
    public static String[] stringPersuasions = {"IBPRoad,","IBPRoad,longestRoad,","IBPRoad,longestRoad,newSettlement,","IBPRoad,newSettlement,",
		"IBPSettlement,", "IBPSettlement,accessNewRes,", "IBPSettlement,VPs,", "IBPSettlement,accessNewRes,VPs,",
		"IBPCity,","IBPCity,VPs,",
		"IBPDevCard,","IBPDevCard,largestArmy,",
		"ITP,",
		"RBTooManyRes,","RBCantGetRes,",
		"ATPromiseResource,","ATRobPromise,"};
    
    private HashMap<String,Integer> persuasionsMadeStatisticsCounters, persuasionsAcceptedStatisticsCounters;
    private HashMap<String, ArrayList<Integer>> persuasionsMadeStatisticsTimer;
	private HashMap<String,ArrayList<Double>> persuasionsMadeStatisticsTimerPercentage;
    
    // Stats for trade embargoes
    private int numEmbargoesProposed, numEmbargoesCompliedWith;

    // Stats for blocking trades
    private int numBlocksProposed, numBlocksCompliedWith;

    public int getNumOffersCurrent() {
        return numOffersCurrent;
    }

    public int getNumSuccessfulOffersCurrent() {
        return numSuccessfulOffersCurrent;
    }

    public int getNumTradesCurrent() {
        return numTradesCurrent;
    }
    
    public int getNumResourcesReceivedByTradingCurrent() {
        return numResourcesReceivedByTradingCurrent;
    }
    
    public int getNumResourcesReceivedByDiceCurrent() {
        return numResourcesReceivedByDiceCurrent;
    }

    public SOCPlayerStats() {
        gamesPlayed = 0;
        wins = 0;
        losses = 0;
        victoryPoints = 0;
        loserVictoryPoints = 0;
        numOffers = 0;
        numTradesMadeAccepter = 0;
        numTradesMadeOfferer = 0;
        numCounterOffers = 0; // todo - not sure how easy it is to track this
        numOffersOurTurn = 0;
        numAccepterOurTurn = 0;
        numOffererOurTurn = 0;
        numInitialOffers = 0;

        numOffersCurrent = 0;
        numSuccessfulOffersCurrent = 0;
        numTradesCurrent = 0;
        numResourcesReceivedByTradingCurrent = 0;
        numResourcesReceivedByDiceCurrent = 0;
        
        numBuildPlans = 0;
        numBuildPlansRoad = 0;
        numBuildPlansSettlement = 0;
        numBuildPlansCity = 0;
        numBuildPlansCard = 0;
        numBuildPlansLA = 0;
        numBuildPlansLR = 0;
        
        numLAGained = 0;
        numLRGained = 0;
        
        numRescourcesReceivedByTrading = 0;
        numRescourcesReceivedByDice = 0;
        
        numForceAcceptsMade = 0;
        numForceAcceptsCompliedWith = 0;

        numEmbargoesProposed = 0;
        numEmbargoesCompliedWith = 0;
        
        numBlocksProposed = 0;
        numBlocksCompliedWith = 0;
        
       
    	persuasionsMadeStatisticsCounters = new HashMap<String,Integer>();
    	persuasionsAcceptedStatisticsCounters = new HashMap<String,Integer>();
    	persuasionsMadeStatisticsTimer = new HashMap<String,ArrayList<Integer>>();
    	persuasionsMadeStatisticsTimerPercentage = new HashMap<String,ArrayList<Double>>();
    	
    	for (String s : stringPersuasions){
        	persuasionsMadeStatisticsCounters.put(s,0);
        	persuasionsAcceptedStatisticsCounters.put(s,0);
        	persuasionsMadeStatisticsTimer.put(s, new ArrayList<Integer>());
        	persuasionsMadeStatisticsTimerPercentage.put(s, new ArrayList<Double>());
    	}
        
        numDisjunctiveGive = 0;
        numDisjunctiveGet = 0;
        numPartialGiveEmpty = 0;
        numPartialGetEmpty = 0;
    }

    public int getNumOffers() {
        return numOffers;
    }

    public int getNumTradesMadeAccepter() {
        return numTradesMadeAccepter;
    }

    public int getNumTradesMadeOfferer() {
        return numTradesMadeOfferer;
    }

    public int getNumCounterOffers() {
        return numCounterOffers;
    }

    public int getNumOffersOurTurn() {
        return numOffersOurTurn;
    }

    public int getNumAccepterOurTurn() {
        return numAccepterOurTurn;
    }

    public int getNumOffererOurTurn() {
        return numOffererOurTurn;
    }


    public void incOffers(boolean ourTurn, boolean initial) { 
        numOffers++;
        numOffersCurrent++;
        if (ourTurn) {
            numOffersOurTurn++;
        }
        if (initial) {
            numInitialOffers++;
        }
    }

    public void incTradesMadeAccept(boolean ourTurn) { 
        numTradesMadeAccepter++;
        numTradesCurrent++;
        if (ourTurn) {
            numAccepterOurTurn++;
        }
    }

    public void incTradesMadeOffer(boolean ourTurn) { 
        numTradesMadeOfferer++; 
        numSuccessfulOffersCurrent++;
        numTradesCurrent++;
        if (ourTurn) {
            numOffererOurTurn++;
        }
    }

    /**
     * Record a build plan generated by the robot.
     * @param type Type of the biuld plan as defined in StacPossibleBuildPlan
     */
    public void incBuildPlans(int type) {
        numBuildPlans++;
        switch (type) {
            case StacPossibleBuildPlan.ROAD:
                numBuildPlansRoad++;
                break;
            case StacPossibleBuildPlan.SETTLEMENT:
                numBuildPlansSettlement++;
                break;
            case StacPossibleBuildPlan.CITY:
                numBuildPlansCity++;
                break;
            case StacPossibleBuildPlan.CARD:
                numBuildPlansCard++;
                break;
            case StacPossibleBuildPlan.LARGEST_ARMY:
                numBuildPlansLA++;
                break;
            case StacPossibleBuildPlan.LONGEST_ROAD:
                numBuildPlansLR++;
                break;
        }
    }

    public void incBuildPiece(int piece) {
        numPiecesBuiltTotal++;
        switch (piece) {
            case SOCPossiblePiece.ROAD:
                numRoadsBuilt++;
                break;
            case SOCPossiblePiece.SETTLEMENT:
                numSettlementsBuilt++;
                break;
            case SOCPossiblePiece.CITY:
                numCitiesBuilt++;
                break;
            case SOCPossiblePiece.CARD:
                numDevCardsBought++;
                break;
        }
    }
    
    public void incLA_LRPlayerChanged(String badge) {
        if (badge.equals("LA")) {
            numLAGained++;
        } else if (badge.equals("LR")) {
            numLRGained++;
        }
    }

    public void incRescourcesReceivedByTrading(SOCResourceSet resources) { //(int clay, int ore, int sheep, int wheat, int wood) {
        numResourcesReceivedByTradingCurrent += resources.getTotal();
        numRescourcesReceivedByTrading += resources.getTotal(); //clay + ore + sheep + wheat + wood;
    }

    /** Store the number of resources the robot received by dice throw in the game just finished. */
    public void setNumResourcesReceivedByDice(int resources) {
        numResourcesReceivedByDiceCurrent = resources;
        numRescourcesReceivedByDice += resources;
    }
    
    public void incNumForceAcceptsMade() {
        numForceAcceptsMade++;
    }
            
    public void incNumForceAcceptsCompliedWith() {
        numForceAcceptsCompliedWith++;
    }
    
    /**
     * Method to store all the different kinds of persuasions made
     * @param persuasiveArgument
     * @param roundOccurred The round the move occurred (to find out the average time the move occurs)
     */
    public void incNumPersuasionsMade(Persuasion persuasiveArgument, int roundOccurred) {
    	if(persuasiveArgument.getIdentifier()!=Persuasion.PersuasionIdentifiers.NullPersuasion){
	        StringBuffer strRepresentationOfPersuasion = new StringBuffer();
	        strRepresentationOfPersuasion.append(persuasiveArgument.getIdentifier().toString()+",");
	        HashMap<String,String> constraints = persuasiveArgument.getConstraints();
	        for(String s : constraints.keySet()){
	        	if(constraints.get(s).equals("true")){
	        		strRepresentationOfPersuasion.append(s+",");
	        	}
	        }
	    	persuasionsMadeStatisticsCounters.put(strRepresentationOfPersuasion.toString(), 
	    			persuasionsMadeStatisticsCounters.get(strRepresentationOfPersuasion.toString())+1);
	    	ArrayList<Integer> currentTimes = persuasionsMadeStatisticsTimer.get(strRepresentationOfPersuasion.toString());
	    	currentTimes.add(roundOccurred);
    	}
    }
            
    /**
     * Method to store the number of persuasion moves accepted
     * @param persuasiveArgument
     */
    public void incNumPersuasionsCompliedWith(Persuasion persuasiveArgument) {
    	if(persuasiveArgument.getIdentifier()!=Persuasion.PersuasionIdentifiers.NullPersuasion){
	        StringBuffer strRepresentationOfPersuasion = new StringBuffer();
	        strRepresentationOfPersuasion.append(persuasiveArgument.getIdentifier().toString()+",");
	        HashMap<String,String> constraints = persuasiveArgument.getConstraints();
	        for(String s : constraints.keySet()){
	        	if(constraints.get(s).equals("true")){
	        		strRepresentationOfPersuasion.append(s+",");
	        	}
	        }
	        persuasionsAcceptedStatisticsCounters.put(strRepresentationOfPersuasion.toString(), 
	        		persuasionsAcceptedStatisticsCounters.get(strRepresentationOfPersuasion.toString())+1);
    	}    	
    }
    
    public void incNumEmbargoesProposed() {
        numEmbargoesProposed++;
    }
            
    public void incNumEmbargoesCompliedWith() {
        numEmbargoesCompliedWith++;
    }
    
    public void incNumBlocksProposed() {
        numBlocksProposed++;
    }
            
    public void incNumBlocksCompliedWith() {
        numBlocksCompliedWith++;
    }
    
    public void incNumDisjunctiveGive() {
        numDisjunctiveGive++;
    }
    
    public void incNumDisjunctiveGet() {
        numDisjunctiveGet++;
    }
    
    public void incNumPartialGiveEmpty() {
        numPartialGiveEmpty++;
    }
    
    public void incNumPartialGetEmpty() {
        numPartialGetEmpty++;
    }
    
    // TODO: public void incCounterOffers() { numCounterOffers++; }

    public void addGame(int victoryPoints) {
        gamesPlayed++;
        D.ebugPrintlnINFO("GAME " + gamesPlayed);
        this.victoryPoints += victoryPoints;
        if (victoryPoints>=10) {
            wins++;
        } else {
            losses++;
            loserVictoryPoints += victoryPoints;
        }
        numTradesCurrent = 0;
        numOffersCurrent = 0;
        numSuccessfulOffersCurrent = 0;
        numResourcesReceivedByTradingCurrent = 0;
        numResourcesReceivedByDiceCurrent = 0; //no need for this; this is just overwritten anyway
    }

    
    public void addGame(int victoryPoints, int roundCount) {
    	for(String pid : persuasionsMadeStatisticsTimer.keySet()){
    		ArrayList<Integer> recordedValues =  persuasionsMadeStatisticsTimer.get(pid);
    		ArrayList<Double> recordedPercentages =  persuasionsMadeStatisticsTimerPercentage.get(pid);
    		for(Integer i : recordedValues){
    			recordedPercentages.add((double)i/(double)roundCount);
    		}
    		recordedValues.clear();
    	}
    	addGame(victoryPoints);
    }

    public int getGamesPlayed() {
        return gamesPlayed;
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }
    
    public int getVictoryPoints() {
        return victoryPoints;
    }

    public int getLoserVictoryPoints() {
        return loserVictoryPoints;
    }

    public double getWinProbability() {
        return (double) wins / (double) gamesPlayed;
    }

    public double getWinProbZ() {
        double p = getWinProbability();
        double nullP = 0.25;
        return (p - nullP) / Math.sqrt(nullP * (1-nullP) / (double) gamesPlayed);
    }

    public double getAverageVP() {
        return (double) victoryPoints / (double) gamesPlayed;
    }

    public double getAverageLoserVP() {
        return (double) loserVictoryPoints / (double) losses; //we're only counting these VP if we lost a game
    }
    
    public double getAverageOffers() {
        return (double) numOffers / (double) gamesPlayed;
    }

    public double getAverageTradesMadeAccepter() {
        return (double) numTradesMadeAccepter / (double) gamesPlayed;
    }

    public double getAverageTradesMadeOfferer() {
        return (double) numTradesMadeOfferer / (double) gamesPlayed;
    }

    public double getAverageOffersOurTurn() {
        return (double) numOffersOurTurn / (double) gamesPlayed;
    }
    
    public double getAverageTradesMadeAccepterOurTurn() {
        return (double) numAccepterOurTurn / (double) gamesPlayed;
    }
    
    public double getAverageTradesMadeOffererOurTurn() {
        return (double) numOffererOurTurn / (double) gamesPlayed;
    }
    
    public double getAverageInitialTradeOfferers() {
        return (double) numInitialOffers / (double) gamesPlayed;
    }
    
    public double getAverageNumBuildPlans() {
        return (double) numBuildPlans / (double) gamesPlayed;        
    }
    
    public double getAverageNumBuildPlansRoad() {
        return (double) numBuildPlansRoad / (double) gamesPlayed;        
    }
    
    public double getAverageNumBuildPlansSettlement() {
        return (double) numBuildPlansSettlement / (double) gamesPlayed;        
    }
    
    public double getAverageNumBuildPlansCity() {
        return (double) numBuildPlansCity / (double) gamesPlayed;        
    }
    
    public double getAverageNumBuildPlansCard() {
        return (double) numBuildPlansCard / (double) gamesPlayed;        
    }
    
    public double getAverageNumBuildPlansLA(){
        return (double) numBuildPlansLA / (double) gamesPlayed;        
    }
    
    public double getAverageNumBuildPlansLR() {
        return (double) numBuildPlansLR / (double) gamesPlayed;        
    }
    
    public double getAverageNumDisjunctiveGive() {
        return (double) numDisjunctiveGive / (double) gamesPlayed;
    }
    
    public double getAverageNumDisjunctiveGet() {
        return (double) numDisjunctiveGet / (double) gamesPlayed;
    }
    
    public double getAverageNumPartialGiveEmpty() {
        return (double) numPartialGiveEmpty / (double) gamesPlayed;
    }
            
    public double getAverageNumPartialGetEmpty() {
        return (double) numPartialGetEmpty / (double) gamesPlayed;
    }

    /**
     * To be used only for MCTS simulations
     */
    public void resetVP(){
    	victoryPoints = 0;
    }
    public double getAverageNumRoadsBuilt() {
        return (double) numRoadsBuilt / (double) gamesPlayed;
    }

    public double getAverageNumSettlementsBuilt() {
        return (double) numSettlementsBuilt / (double) gamesPlayed;
    }
    public double getAverageNumCitiesBuilt() {
        return (double) numCitiesBuilt / (double) gamesPlayed;
    }
    
    public double getAverageNumCardsBought() {
        return (double) numDevCardsBought / (double) gamesPlayed;
    }

    public double getAverageNumPiecesBuiltTotal() {
        return (double) numPiecesBuiltTotal / (double) gamesPlayed;
    }

    public double getAverageNumLAGained() {
        return (double) numLAGained / (double) gamesPlayed;
    }

    public double getAverageNumLRGained() {
        return (double) numLRGained / (double) gamesPlayed;
    }

    public double getAverageNumRescourcesReceivedByTrading() {
        return (double) numRescourcesReceivedByTrading / (double) gamesPlayed;
    }

    public double getAverageNumRescourcesReceivedByDice() {
        return (double) numRescourcesReceivedByDice / (double) gamesPlayed;
    }

    public double getAverageNumForceAcceptsMade() {
        return (double) numForceAcceptsMade / (double) gamesPlayed;
    }

    public double getAverageNumForceAcceptsCompliedWith() {
        return (double) numForceAcceptsCompliedWith / (double) gamesPlayed;
    }
    
    /**
     * Implement system to return the important values
     * Need to get the number of rounds at the end of the game to find out the average timing of a move
     */    
    public HashMap<String,Double> getPersuasionsMadeCounters(){
    	HashMap<String,Double> averageResults = new HashMap<String,Double>();
    	for (String str : persuasionsMadeStatisticsCounters.keySet()){
    		averageResults.put(str, (double)persuasionsMadeStatisticsCounters.get(str)/(double) gamesPlayed);
    	}
    	return averageResults;
    }
    
    public HashMap<String,Double> getPersuasionsAcceptedCounters(){
    	HashMap<String,Double> averageResults = new HashMap<String,Double>();
    	for (String str : persuasionsAcceptedStatisticsCounters.keySet()){
    		averageResults.put(str, (double)persuasionsAcceptedStatisticsCounters.get(str)/(double) gamesPlayed);
    	}
    	return averageResults;
    }
    
    public HashMap<String, Double> getAverageTimePersuasionWasUsed(){
    	HashMap<String,Double> percentageTimes = new HashMap<String,Double>();
    	for(String pid: persuasionsMadeStatisticsTimerPercentage.keySet()){
    		ArrayList<Double> times = persuasionsMadeStatisticsTimerPercentage.get(pid);
    		double total = 0.0;
    		int counter = times.size();
    		if(counter!=0){
	    		for(Double t : times){
	    			total += t;
	    		}
    			percentageTimes.put(pid, (total/(double)counter));
    		}
    		else{
    			percentageTimes.put(pid, (double)0);
    		}
    	}
    	return percentageTimes;
    }
	
    public double getAverageNumEmbargoesProposed() {
        return (double) numEmbargoesProposed / (double) gamesPlayed;
    }

    public double getAverageNumEmbargoesCompliedWith() {
        return (double) numEmbargoesCompliedWith / (double) gamesPlayed;
    }

    public double getAverageNumBlocksProposed() {
        return (double) numBlocksProposed / (double) gamesPlayed;
    }

    public double getAverageNumBlocksCompliedWith() {
        return (double) numBlocksCompliedWith / (double) gamesPlayed;
    }
}
