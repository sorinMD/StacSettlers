package soc.robot.stac;

import soc.dialogue.StacTradeMessage;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

//import soc.disableDebug.D;
import soc.debug.D;
import soc.game.SOCBoard;
import soc.game.SOCDevCardSet;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCTradeOffer;
import soc.game.StacTradeOffer;
import soc.robot.SOCBuildPlanStack;
import soc.robot.SOCPlayerTracker;
import soc.robot.SOCPossiblePiece;

/**
 * Representation of the robot's declarative knowledge.
 * 
 * This class has two main purposes:
 * 
 * 1. provide a unified interface to the robot's declarative knowledge via getter methods,
 *    so that we have a clear and simple interface for procedural knowledge components 
 *    (whether it's an ACT-R-like procedural module or an AI-type reasoning engine), 
 *    which ensures that we know what knowledge a robot has available for making decisions,
 * 
 * 2. provide a class that can be sub-classed if we, e.g., want to implement a cognitive model of
 *    human associative memory, or if we want to create a robot model that selectively forgets 
 *    which player has the longest road.
 * 
 * This class will provide the standard implementation of this interface that to a large extent 
 * just provides unified access to information/knowledge that is already available in other classes. 
 * In this respect, another purposes of this class is to provide the one point of access to all of 
 * the robot's knowledge.
 * 
 * It provides three (not necessarily distinct) types of knowledge:
 * 1. "perceptual": observable knowledge/information about the game state and the robot itself 
 *    (like the robot's hand)
 * 2. observed and inferred knowledge about other players
 * 3. remembered information like past trades
 * 
 * This class generally adopts the convention that accessor methods for information that is 
 * certain use set~, get~ and is~ prefixes, information that may be 
 * uncertain/stochastic they use remember~, retrieve and check~ (to suggest cognitive processes).
 * 
 * @author
 * Markus Guhe
 */

public class StacRobotDeclarativeMemory implements Serializable{
    /**
     * Helper method that converts a Collection (intended for Vector) into an ArrayList
     * @param   original the original collection
     * @returns the ArrayList the Collection has been convert into
     */
    private ArrayList toArrayList(Collection original) {
        ArrayList arList = new ArrayList();
        arList.addAll(original);
        return arList;
    }

    /**
     * Helper method to get the string "unknown" for SOCResourceConstants.UNKNOWN instead of NULL.
     * @param type
     * @return a string representing the specified resource type
     */
    private String resourceNameForResourceType(int rsrcType) {
        if (rsrcType == SOCResourceConstants.UNKNOWN) {
            return "unknown";
        } else {
            return SOCResourceConstants.resName(rsrcType);
        }
    }
    
    /**
     * the brain we're a `part of'
     */
    transient protected final StacRobotBrain brain;
    
    /**
     * Random number generator for stochastic decisions or processes.
     */
    protected static final Random RANDOM = new Random();

    /**
     * Create the Declarative Memory for our brain.
     * @param brain 
     * @param game  the game this brain is playing
     */
    public StacRobotDeclarativeMemory(StacRobotBrain brain, SOCGame game) {
        this.brain = brain;
//        playerData = brain.getPlayerData(); // the playerData object is not yet initialised in the brain

        this.pastTradeOffers = new ArrayList<StacTradeOffer>();
        this.pastTrades = new ArrayList<Trade>();
        this.responses = new StacTradeMessage[game.maxPlayers];
        this.playerOffers = new SOCTradeOffer[game.maxPlayers];
        this.blockTrades = new boolean[game.maxPlayers];
        this.targetAnnounced = new boolean[game.maxPlayers];
        this.targetPieces = new SOCPossiblePiece[game.maxPlayers];
        this.playerTrusted = new boolean[game.maxPlayers];
        this.playerResourcesBelieved = new SOCResourceSet[game.maxPlayers];
        this.playerResourcesObserved = new SOCResourceSet[game.maxPlayers];

        settlementBuildPlans = new ArrayList();
        cityBuildPlans = new ArrayList();
        cardBuildPlan = null;
        largestArmyBuildPlan = null;
        longestRoadBuildPlan = null;

        isSellingResource = new boolean[game.maxPlayers][SOCResourceConstants.MAXPLUSONE];
        resetIsSelling();
        
        wantsAnotherOffer = new boolean[game.maxPlayers][SOCResourceConstants.MAXPLUSONE];
        resetWantsAnotherOffer();
        
        initResources();
        resetTargetPieces();
        
        //other players' behaviours
        numGamesPlayed = 0;
        numOpponentTradeOffers = new HashMap();
        numOpponentTradeOfferRejected = new HashMap();
        numOpponentTradeOfferAccepted = new HashMap();
        
        //behaviours towards other player
        playersWithTradeEmbargo = new int[game.getPlayers().length];
        numEmbargoesProposed = 0;
        numEmbargoesCompliedWith = 0;
        numForceAcceptMoves = 0;
        
        blockedResources = new SOCResourceSet();
        numBlockMoves = 0;
        turnBlockStarted = 0;
        
        isWaiting = new Set[game.maxPlayers];
        isParticipating = new boolean[game.maxPlayers];
        for (int i=0; i<game.maxPlayers; i++) {
            isWaiting[i] = new HashSet<String>();
            isParticipating[i] = false;
        }
        
        D.setLevel(D.WARNING);
    }
    
    /**
     * Method for resetting/reinitialising memory in case of a load with missing memory information. After this method is called, this will be an exact memory
     * i.e. observed=believed rss. Plus all players will be trusted again and all previously known information will be reset.
     */
    public void reinitMemory(){
        //forget build plans and past offers
    	forgetAllBuildPlans();
        forgetAllTradeOffers();
        resetPastTrades();
        
        //get all observable and reinitialise the believed resources from the game object
        initResources();
        for (int pn = 0; pn < brain.getGame().maxPlayers; pn++) {
            SOCPlayer player = brain.getGame().getPlayer(pn);
            SOCResourceSet set =  player.getResources();
            //update to the correct number of resources
            for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.UNKNOWN; i++) {
                playerResourcesBelieved[pn].setAmount(set.getAmount(i), i);
                playerResourcesObserved[pn].setAmount(set.getAmount(i), i);
            }
        }
        
        //reset all previous information regarding trades, offers, isSelling, trade logic, blocks and embargoes
        resetIsSelling();
        resetWantsAnotherOffer();
        resetMyNegotiatedOffer();
        resetMyOffer();
        resetResponses();
        resetTargetPieces();
        resetPlayerOffers();
        resetBestCompletedTradeOffer();
        resetOriginalBestCompleteTradeOffer();
        resetBlockTrades();
        resetBlockedResources();
        Arrays.fill(playersWithTradeEmbargo, 0);
        
        //reset information on number of embargoes, of blocked moves, of forced trades
        numEmbargoesProposed = 0;
        numEmbargoesCompliedWith = 0;
        numForceAcceptMoves = 0;
        numBlockMoves = 0;
        turnBlockStarted = 0;
        
        //if we were in the middle of trading and we just loaded a new game, reset all chat waiting logic
        stopWaiting();
        Arrays.fill(isParticipating, false); //also reset who is participating as this will be announced in the chat again, after loading
    }

    ///////////////////////////////////////////////////////////////////////////////////
    // OBSERVABLE INFORMATION
    ///////////////////////////////////////////////////////////////////////////////////
    
    /* The observable game state is represented in the SOCGame object available via the StacRobotBrain,
     * so we just wrap around that to provide consistent access to the relevant information.
     * 
     * The idea is that subclasses can override these methods, e.g. we might want to enable 
     * a (silly) `cognitive' robot to forget the name of the game.
     */

    /**
     * Direct access to the SOCGame object for the robot player.
     * Needed, for example, to create dummy players for planning.
     * 
     * WARNING: should be used only in exceptions, because it gives direct access to knowledge we are managing in this class!
     * 
     * @return SOCGame object the we are playing
     */
    protected SOCGame getGame() {
        return brain.getGame();
    }
    
    /**
     * @return name of the game
     */
    protected String getGameName() {
        return brain.getGame().getName();
    }

    /**
     * @return the name of the owner (creator) of the game.
     */
    protected String getOwnerName() {
        return brain.getGame().getOwner();
    }

    /**
     * @return game board
     */
    protected SOCBoard getBoard() {
        return brain.getGame().getBoard();
    }

    /**
     * the players; never contains a null element, use {@link #isSeatVacant(int)}
     * to see if a position is occupied.  Length is {@link #maxPlayers}.
     * 
     * @return array of SOCPLayer objects
     */
    protected SOCPlayer[] getPlayers() {
        return brain.getGame().getPlayers();
    }

    /**
     * The player object with the specified nickname.
     * 
     * @param nick          String identifying the player's nickname
     * @return SOCPlayer    object with player data
     */
    protected SOCPlayer getPlayer(String nick) {
        return brain.getGame().getPlayer(nick);
    }
    
    /**
     * The player object with the specified player number.
     * 
     * @param playernumber  Number identifying the player
     * @return SOCPlayer    object with player data
     */
    protected SOCPlayer getPlayer(int playernumber) {
        return brain.getGame().getPlayer(playernumber);
    }
    
    /**
     * @return states of the players' seats
     */
    protected boolean isSeatVacant(int pn) {
        return brain.getGame().isSeatVacant(pn);
    }

    /**
     * @return states if the locks for the players' seats
     */
    protected boolean isSeatLocked(int pn) {
        return brain.getGame().isSeatLocked(pn);
    }

    /**
     * @return number of the current player
     */
    protected int getCurrentPlayerNumber() {
        return brain.getGame().getCurrentPlayerNumber();
    }

    /**
     * @return number of the first player to place a settlement
     */
    protected int getFirstPlayerNumber() {
        return brain.getGame().getFirstPlayer();
    }

    /**
     * @return current dice result. -1 at start of game, 0 during player's turn before roll (state {@link #PLAY}).
     */
    protected int getCurrentDice() {
        return brain.getGame().getCurrentDice();
    }

    /**
     * @return current game state
     */
    protected int getGameState() {
        return brain.getGame().getGameState();
    }

    /**
     * @return player with the largest army (or null if there's none)
     */
    protected SOCPlayer getPlayerWithLargestArmy() {
        return brain.getGame().getPlayerWithLargestArmy();
    }

    /**
     * @return player with the longest road (or null if there's none)
     */
    protected SOCPlayer getPlayerWithLongestRoad() {
        return brain.getGame().getPlayerWithLongestRoad();
    }

    /**
     * @return player declared winner, if gamestate == OVER; otherwise null
     */
    protected SOCPlayer getPlayerWithWin() {
        return brain.getGame().getPlayerWithWin();
    }

    /**
     * @return number of development cards left
     */
    protected int getNumDevCards() {
        return brain.getGame().getNumDevCards();
    }

    /**
     * @return time at the game was created
     */
    protected Date getStartTime() {
        return brain.getGame().getStartTime();
    }

    /**
     * @return expiration time for this game in milliseconds.
     */
    protected long getExpiration() {
        return brain.getGame().getExpiration();
    }

    /**
     * @return number of normal rounds (each player has 1 turn per round, after initial placements), including this round.
     */
    protected int getRoundCount() {
        return brain.getGame().getRoundCount();
    }

    ///////////////////////////////////////////////////////////////////////////////////
    // INFORMATION ABOUT OURSELF OR PLAYER TRACKERS
    ///////////////////////////////////////////////////////////////////////////////////
    
    /**
     * Number of games this robot has played. 
     * To be used a persistent information across multiple games/brains.
     */
    private int numGamesPlayed;
    
    protected void incNumGamesPlayed() {
        numGamesPlayed++;
    }
    
    protected void setNumGamesPlayed (int ng) {
        numGamesPlayed = ng;
    }

    protected int getNumGamesPlayed () {
        return numGamesPlayed;
    }
    
    /**
     * @return name of the player
     */ 
    protected String getName() {
        return getPlayerData().getName();
    }

    /**
     * @return integer id for this player (0 to n-1)
     */
    protected int getPlayerNumber() {
        return getPlayerData().getPlayerNumber();
    }

    /**
     * @return our player tracker
     */
    public SOCPlayerTracker getOurPlayerTracker()
    {
        return brain.getOurPlayerTracker();
    }	
    
    /**
     * 
     * @return the player trackers (one per player, including this robot)
     */
    public HashMap<Integer, SOCPlayerTracker> getPlayerTrackers()
    {
        return brain.getPlayerTrackers();
    }
        
    ///////////////////////////////////////////////////////////////////////////////////
    // OUR OWN HAND
    ///////////////////////////////////////////////////////////////////////////////////
    
    private SOCPlayer getPlayerData() {
        return brain.getPlayerData();
    }
    
    /**
     * @return Vector of the player's pieces in play
     */
    protected ArrayList getPiecesInPlay() {
        return toArrayList(getPlayerData().getPieces());
    }

    /**
     * @return list of this player's roads in play
     */
    protected ArrayList getRoadsInPlay() {
        return toArrayList(getPlayerData().getRoads());
    }

    /**
     * @return list of this player's settlements in play
     */
    protected ArrayList getSettlements() {
        return toArrayList(getPlayerData().getSettlements());
    }

    /**
     * @return list of this player's cities in play
     */
    protected ArrayList getCities() {
        return toArrayList(getPlayerData().getCities());
    }

    /**
     * @return coordinates of our most recent settlement
     */
    protected int getLastSettlementCoord() {
        return getPlayerData().getLastSettlementCoord();
    }

    /**
     * @retrun coordinates of our most recent road
     */
    protected int getLastRoadCoord() {
        return getPlayerData().getLastRoadCoord();
    }

    /**
     * @return length of the longest road for this player
     */
    protected int getLongestRoadLength() {
        return getPlayerData().getLongestRoadLength();
    }

    /**
     * @return list of longest paths
     */
    protected ArrayList getLrPaths() {
        return toArrayList(getPlayerData().getLRPaths());
    }

    /**
     * @return our current resources
     */
    protected SOCResourceSet getResources() {
        return getPlayerData().getResources();
    }

    /**
     * @return our current development cards
     */
    protected SOCDevCardSet getDevCards() {
        return getPlayerData().getDevCards();
    }

    /**
     * @return how many knights we have in play
     */
    protected int getNumKnights() {
        return getPlayerData().getNumKnights();
    }

    /**
     * @return true if we need to discard
     */
    protected boolean getNeedToDiscard() {
        return getPlayerData().getNeedToDiscard();
    }

    /**
     * @return all nodes that our roads touch
     */
    protected ArrayList getRoadNodes() {
        return toArrayList(getPlayerData().getRoadNodes());
    }

    /**
     * @param edge  the coordinates of an edge on the board. Accepts -1 for edge 0x00.
     * @return      true if this edge is a legal road
     */
    protected boolean isLegalRoad(int edge) {
        return getPlayerData().isLegalRoad(edge);
    }

    /**
     * @param edge  the coordinates of an edge on the board. Accepts -1 for edge 0x00.
     * @return      true if this edge is a potential road
     */
    protected boolean isPotentialRoad(int edge) {
        return getPlayerData().isPotentialRoad(edge);
    }
    
    /**
     * @return list of nodes where it is legal to place a settlement.
     * @see SOCPlayer#potentialSettlements
     * @see SOCBoard#nodesOnBoard
     */
    protected List<Integer> getLegalSettlements() {
        return getPlayerData().getLegalSettlements();
    }

    /**
     * @return true  if this node is a potential settlement
     * @param node   the coordinates of a node on the board
     */
    public boolean isPotentialSettlement(int node) {
        return getPlayerData().isPotentialSettlement(node);
    }
    
    /**
     * @return true  if this node is a potential city
     * @param node   the coordinates of a node on the board
     */
    protected boolean isPotentialCity(int node) {
        return getPlayerData().isPotentialCity(node);
    }

    /**
     * @return that we're a robot
     */
    public boolean isRobot() {
        return true;
    }

    /**
     * @return that we're not the built-in JSettlers robot
     */
    protected boolean getBuiltInRobotFlag() {
        return false;
    }

    /**
     * @return current trade offer that we are making (null if none)
     */
    protected SOCTradeOffer getCurrentOffer() {
        return getPlayerData().getCurrentOffer();
    }

    /**
     * @return the face image we are using
     */
    protected int getFaceId() {
        return getPlayerData().getFaceId();
    }
    
    ///////////////////////////////////////////////////////////////////////////////////
    // BUILD PLANS
    ///////////////////////////////////////////////////////////////////////////////////

    /**
     * @return the building plan currently being followed by this robot. This is the field called buildingPlan used in {@link StacRobotBrain#}
     * to execute actions in the game.
     */
    public SOCBuildPlanStack getCurrentBuildPlan(){
    	//NOTE: this should be the only location this method is called!!!
    	return brain.getBuildingPlanDirect();
    }
    
    /**
     * The build plans we have at the moment.
     */
    private ArrayList<StacPossibleBuildPlan> settlementBuildPlans;
    private ArrayList<StacPossibleBuildPlan> cityBuildPlans;
    private StacPossibleBuildPlan cardBuildPlan;
    private StacPossibleBuildPlan largestArmyBuildPlan;
    private StacPossibleBuildPlan longestRoadBuildPlan;
    
    /**
     * Remembering the 'plan' to build a possible piece.
     * @param bpType                a descriptive string for the type of build plan (intended for debugging)
     * @param posCity               the potential city that we could build
     * @param buildingSpeedEstimate the estimated time it takes until we can build
     * @param speedupEstimate       the estimated speedup (ES) that building would mean
     * @param deltaWinGameETA       the estimated reduction in ETW (Estimated Progress, EP)
     * @author Markus Guhe
     */
    void rememberBuildPlan(int bpType, SOCBuildPlanStack buildPlan, int buildingSpeedEstimate, int speedupEstimate, int deltaWinGameETA) {
        StacPossibleBuildPlan possBP = new StacPossibleBuildPlan(bpType, buildPlan, buildingSpeedEstimate, speedupEstimate, deltaWinGameETA);
        switch (bpType) {
            case StacPossibleBuildPlan.SETTLEMENT:
                settlementBuildPlans.add(possBP);
                break;
            case StacPossibleBuildPlan.CITY:
                cityBuildPlans.add(possBP);
                break;
            case StacPossibleBuildPlan.CARD:
                cardBuildPlan = possBP;
                break;
            case StacPossibleBuildPlan.LARGEST_ARMY:
                largestArmyBuildPlan = possBP;
                break;
            case StacPossibleBuildPlan.LONGEST_ROAD:
                longestRoadBuildPlan = possBP;
                break;
            default:
                D.ebugERROR("Could not store potential build plan. Unknown type.");
                break;
        }
    }

    /**
     * Reset the remembered possible build plans.
     * Intended to be called before the possible build plans are recomputed.
     */
    void forgetAllBuildPlans() {
        settlementBuildPlans.clear();
        cityBuildPlans.clear();
        cardBuildPlan = null;
        largestArmyBuildPlan = null;
        longestRoadBuildPlan = null;
    }
    
    /**
     * Get all build plans stored in the declarative memory.
     * @return ArrayList with StacPossibleBuildPlan objects
     */
    ArrayList<StacPossibleBuildPlan> getBuildPlans() {
        ArrayList<StacPossibleBuildPlan> possibleBuildPlans = new ArrayList();
        if (settlementBuildPlans != null) {
            possibleBuildPlans.addAll(settlementBuildPlans);
        }
        if (cityBuildPlans != null) {
            possibleBuildPlans.addAll(cityBuildPlans);
        }
        if (cardBuildPlan != null) {
            possibleBuildPlans.add(cardBuildPlan);
        }
        if (largestArmyBuildPlan != null) {
            possibleBuildPlans.add(largestArmyBuildPlan);
        }
        if (longestRoadBuildPlan != null) {
            possibleBuildPlans.add(longestRoadBuildPlan);
        }
        
        return possibleBuildPlans;
    }

    /**
     * Get the Settlement build plans.
     * @return ArrayList with StacPossibleBuildPlan objects
     */
    ArrayList<StacPossibleBuildPlan> getSettlementBuildPlans() {
        return settlementBuildPlans;
    }

    /**
     * Get the City build plans.
     * @return ArrayList with StacPossibleBuildPlan objects
     */
    ArrayList<StacPossibleBuildPlan> getCityBuildPlans() {
        return cityBuildPlans;
    }

    /**
     * Get the Development Card build plan.
     * @return StacPossibleBuildPlan object
     */
    StacPossibleBuildPlan getDevCardBuildPlan() {
        return cardBuildPlan;
    }

    /**
     * Get the Longest Road build plan.
     * @return StacPossibleBuildPlan object
     */
    StacPossibleBuildPlan getLongestRoadBuildPlan() {
        return longestRoadBuildPlan;
    }

    /**
     * Get the Largest Army build plan.
     * @return StacPossibleBuildPlan object
     */
    StacPossibleBuildPlan getLargestArmyBuildPlan() {
        return largestArmyBuildPlan;
    }


    ///////////////////////////////////////////////////////////////////////////////////
    // PAST AND CURRENT TRADING
    ///////////////////////////////////////////////////////////////////////////////////

    /**
     * The partialising agent uses this to store the complete offer it wanted to make initially.
     */
    private SOCTradeOffer originalBestCompleteTradeOffer = null;
    
    /**
     * Remembering the full offer the partialising agent wanted to make initially.
     * @param offer 
     */
    protected void setOriginalBestCompleteTradeOffer(SOCTradeOffer offer) {
        if (offer == null) {
            this.originalBestCompleteTradeOffer = null;
        } else {
            this.originalBestCompleteTradeOffer = new SOCTradeOffer(offer);
        }
    }
    
    /**
     * Get the full offer the partialising agent wanted to make initially.
     * @return the original complete offer
     */
    protected SOCTradeOffer getOriginalBestCompleteTradeOffer() {
        return this.originalBestCompleteTradeOffer;
    }
    
    private void resetOriginalBestCompleteTradeOffer(){
    	originalBestCompleteTradeOffer = null;
    }
    
    /**
     * Remember the current best trade offer when we received a partial offer 
     * and are considering possible completions.
     */
    private StacTradeOffer bestCompletedTradeOffer = null;
    
    private int bestCompletedTradeOfferETA = 1000;
    private int bestCompletedTradeOfferGlobalETA = 1000;

    protected void rememberBestCompletedTradeOffer(StacTradeOffer bestCompletedTradeOffer, int eta, int globalETA) {
        this.bestCompletedTradeOffer = bestCompletedTradeOffer;
        this.bestCompletedTradeOfferETA = eta;
        this.bestCompletedTradeOfferGlobalETA = globalETA;
    }
    
    /**
     * Retrieve the best completed trade offer that we can remember.
     * 
     * @return SOCTradeOffer that we can remember as best completed trade offer (so far)
     */
    protected StacTradeOffer retrieveBestCompletedTradeOffer() {
        if (brain.isRobotType(StacRobotType.WEAK_MEMORY)) {
            if (RANDOM.nextBoolean()) {
                return null;
            }
        }

        return bestCompletedTradeOffer; 
    }
    
    protected int retrieveBestCompletedTradeOfferETA() {
        return bestCompletedTradeOfferETA;
    }
    
    protected int retrieveBestCompletedTradeOfferGlobalETA() {
        return bestCompletedTradeOfferGlobalETA;
    }
    
    protected void resetBestCompletedTradeOffer(){
    	bestCompletedTradeOffer = null;
        bestCompletedTradeOfferETA = 1000;
    }
    
    /**
     * Array to store past trade offers.
     * 
     * Trade offers are stored in the sequence in which they are recorded 
     * (which should be the same sequence in which offers are made).
     */
    private ArrayList<StacTradeOffer> pastTradeOffers;
    
    /**
     * Remember a trade offer.
     * 
     * @param offer  a SOCTradeOffer object; if offer is null, nothing is stored
     */
    protected void rememberTradeOffer(StacTradeOffer offer) {
        if (offer == null)
            return;

        //We may already have made this offer before as a counteroffer when disjunctive offers are involved
        //That's a rare case, so we just store it twice rather than check for duplication every time
//        if (pastTradeOffers.contains(offer))
//            return;
        
        pastTradeOffers.add(offer);
    }

    /**
     * Forget all trade offers.
     */
    protected void forgetAllTradeOffers() {
        pastTradeOffers.clear();
    }
    
    /**
     * Retrieve all past trade offers from memory.
     * @return list of all trade offers stored in the declarative memory.
     */
    protected ArrayList<StacTradeOffer> retrieveAllTradeOffers() {
        //this only means that the robot will make the same trade offer over and over again
        if (brain.isRobotType(StacRobotType.NO_MEMORY_OF_TRADES))
            return new ArrayList<StacTradeOffer>();
        
        if (brain.isRobotType(StacRobotType.WEAK_MEMORY))
            if (RANDOM.nextBoolean())
                return new ArrayList<StacTradeOffer>();

        return pastTradeOffers;
    }

    /**
     * Test whether there is a past trade offer with equal give and get sets.
     * <ul>
     * <li> Disjunctive offers: The resource sets of the past offer are tested whether they contain a disjunctive resource sets; 
     * a more specific set in the offerToCheck will return true.</li>
     * <li> Partial offers: An empty resource set matches any more specific (ie nonempty) resource set.</li>
     * </ul>
     * @param offerToCheck  the offer to test
     * @return              true if there is a past offer with equal or more general give and get sets
     */
    //TODO: It can actually be quite interesting to receive a more specific offer after a partial one has been rejected!
    boolean pastTradeOfferExists(StacTradeOffer offerToCheck) {
        for (StacTradeOffer pastOffer : retrieveAllTradeOffers()) {
            boolean giveSetMatches = false;
            if (pastOffer.hasDisjunctiveGiveSet()) {
                if ((offerToCheck.hasDisjunctiveGiveSet() || offerToCheck.getGiveSet().numberOfResourceTypes() == 1) && offerToCheck.getGiveSet().isSubsetOf(pastOffer.getGiveSet()))
                    giveSetMatches = true;
            } else if (pastOffer.getGiveSet().isEmptySet())
                giveSetMatches = true;
            else if (pastOffer.getGiveSet().equals(offerToCheck.getGiveSet()))
                giveSetMatches = true;
            if (giveSetMatches) {
                if (pastOffer.hasDisjunctiveGetSet()) {
                    if ((offerToCheck.hasDisjunctiveGetSet() || offerToCheck.getGetSet().numberOfResourceTypes() == 1) && offerToCheck.getGetSet().isSubsetOf(pastOffer.getGetSet()))
                        return true;
                } else if (pastOffer.getGetSet().isEmptySet())
                    return true;
                else if (pastOffer.getGetSet().equals(offerToCheck.getGetSet()))
                    return true;
            }
        }
        return false;
    }

    /**
     * Keeping track of what player is selling what resource.
     */
    private boolean[][] isSellingResource;

    /**
     * mark a player as not selling a resource
     *
     * @param pn         the number of the player
     * @param rsrcType   the type of resource
     */
    public void markAsNotSelling(int pn, int rsrcType) {
        D.ebugPrintlnINFO("*** markAsNotSelling pn=" + pn + " rsrcType=" + rsrcType);
        isSellingResource[pn][rsrcType] = false;
    }

    /**
     * mark a player as willing to sell a resource
     *
     * @param pn         the number of the player
     * @param rsrcType   the type of resource
     */
    public void markAsSelling(int pn, int rsrcType) {
        D.ebugPrintlnINFO("*** markAsSelling pn=" + pn + " rsrcType=" + rsrcType);
        isSellingResource[pn][rsrcType] = true;
    }

    /**
     * reset the isSellingResource array so that
     * if the player has the resource, then he is selling it
     */
    protected void resetIsSelling() {
        D.ebugPrintlnINFO("*** resetIsSelling (true for every resource the player has) ***");

        for (int rsrcType = SOCResourceConstants.CLAY; rsrcType <= SOCResourceConstants.WOOD; rsrcType++) {
            for (int pn = 0; pn < brain.getGame().maxPlayers; pn++) {
                if (( ! brain.getGame().isSeatVacant(pn)) 
                     && (getOpponentResources(pn).getAmount(rsrcType) > 0
                         || getOpponentResources(pn).getAmount(SOCResourceConstants.UNKNOWN) > 0 )){
                    isSellingResource[pn][rsrcType] = true;
                 }
            }
        }
    }
    
    /**
     * Retrieve whether somebody is selling the specified resource.
     * 
     * @param playerNumber the number of the player under consideration
     * @param resourceType the type of resource we're interested in
     * @return true if the player is selling the resource
     */
    protected boolean isSellingResource(int playerNumber, int resourceType) {
        if (brain.isRobotType(StacRobotType.NO_MEMORY_OF_IS_SELLING)) {
            return false;
        }
        
        if (brain.isRobotType(StacRobotType.ALWAYS_ASSUMING_IS_SELLING)) {
            return true;
        }
        
        if (brain.isRobotType(StacRobotType.WEAK_MEMORY)) {
            if (RANDOM.nextBoolean()) {
                return false;
            }
        }

        return isSellingResource[playerNumber][resourceType];
    }
    
    /**
     * array keeping track if a player wants a different resource in exchange for a resource type they are selling
     */
    protected boolean[][] wantsAnotherOffer;
    
    /**
     * reset the wantsAnotherOffer array to all false
     */
    protected void resetWantsAnotherOffer()
    {
        D.ebugPrintlnINFO("*** resetWantsAnotherOffer (all false) ***");

        for (int rsrcType = SOCResourceConstants.CLAY;
                rsrcType <= SOCResourceConstants.WOOD; rsrcType++)
        {
            for (int pn = 0; pn < brain.getGame().maxPlayers; pn++)
            {
                wantsAnotherOffer[pn][rsrcType] = false;
            }
        }
    }
    
    /**
     * mark a player as not wanting another offer
     * @param pn         the number of the player
     * @param rsrcType   the type of resource
     */
    public void markAsNotWantingAnotherOffer(int pn, int rsrcType)
    {
        D.ebugPrintlnINFO("*** markAsNotWantingAnotherOffer pn=" + pn + " rsrcType=" + rsrcType);
        wantsAnotherOffer[pn][rsrcType] = false;
    }

    /**
     * mark a player as wanting another offer
     * @param pn         the number of the player
     * @param rsrcType   the type of resource
     */
    public void markAsWantsAnotherOffer(int pn, int rsrcType)
    {
        D.ebugPrintlnINFO("*** markAsWantsAnotherOffer pn=" + pn + " rsrcType=" + rsrcType);
        wantsAnotherOffer[pn][rsrcType] = true;
    }
    
    /**
     * @return true if the player is marked as wanting a better offer
     * @param pn         the number of the player
     * @param rsrcType   the type of resource
     */
    public boolean wantsAnotherOffer(int pn, int rsrcType)
    {
        return wantsAnotherOffer[pn][rsrcType];
    }
    
    /**  Store the offer we made. TODO: Refactor to an array so we can negotiate different trades in parallel*/
    private StacTradeOffer myOffer = null;
    /**
     * Remember an offer we've made.  
     * Be sure to set this in playerOffers as well, since we won't process our own chat to do this.
     * @param offer  our offer
     */
    protected void setMyOffer(StacTradeOffer offer) {
        myOffer = offer;
        setPlayerOffer(getPlayerNumber(), offer);
    }
    /**
     * gets the offer from memory
     * @return my current proposed offer
     */
    protected StacTradeOffer getMyOffer(){
    	return myOffer;
    }
    
    private void resetMyOffer(){
    	myOffer = null;
    }

    /** When an offer is agreed upon, store it here (should be only a fully specified offer, including unique recipient) */
    private SOCTradeOffer myNegotiatedOffer = null;
    /**
     * Return the offer the Dialogue Manager has negotiated, if any.
     * @return SOCTradeOffer of the negotiated offer
     */
    protected SOCTradeOffer getMyNegotiatedOffer() {
        return myNegotiatedOffer;
    }
    /**
     * resets my negotiated offer by setting it to null 
     */
    protected void resetMyNegotiatedOffer() {
        myNegotiatedOffer = null;
    }
    /**
     * sets my negotiated offer
     * @param offer the offer to set it to
     */
    protected void setMyNegotiatedOffer(SOCTradeOffer offer){
    	 myNegotiatedOffer = offer;
    }

    protected final ArrayList<Trade> pastTrades;

    /**
     * Forget the past trades we made.
     */
    protected void resetPastTrades() {
        pastTrades.clear();
    }
    
    /**
     * Get the trades that we have made in this turn.
     * @return List of Trade objects
     */
    protected ArrayList<Trade> getPastTrades() {
        return pastTrades;
    }
    
    /**
     * Our negotiated trade was executed by the server. So we can store this as a pastTrade now.
     */
    protected void negotiatedOfferWasExecuted() {
        //get addressee from the negotiated offer; exactly one field should be set to true
        if (myNegotiatedOffer == null) {
            System.err.println("Error when recording execution of negotiated offer - negotiated offer is null!");
            return;
        }
        int from = myNegotiatedOffer.getFrom();
        int to = 99;
        for (int p = 0; p < getGame().maxPlayers; p++) {
            if (myNegotiatedOffer.getTo()[p]) {
                if (to != 99)
                    D.ebugERROR("It seems a trade was executed with more than one opponent!");
                else if (p == from)
                    D.ebugERROR("It seems a trade was executed with the same sender and receiver!");
                else
                    to = p;
            }
        }
        
        Trade t = new Trade(myNegotiatedOffer.getFrom(), to, myNegotiatedOffer.getGiveSet(), myNegotiatedOffer.getGetSet());
        pastTrades.add(t);
    }
    

    /**
     * The current trade messages during a negotiation.
     */
    private StacTradeMessage[] responses;   

    /**
     * @return the array containing all the responses
     */
    protected StacTradeMessage[] getResponses(){
    	return responses;
    }
    
    /**
     * @param playerNum the player number corresponding to the player whose response we are interested in
     * @return the response of a specific player
     */
    protected StacTradeMessage getPlayerResponse(int playerNum){
    	return responses[playerNum];
    }
    
    /**
     * set the whole array, could be used for reseting purposes
     * @param resp
     */
    protected void setResponses(StacTradeMessage[] resp){
    	responses = resp;
    }
    
    /**
     * set the response of a specific player
     * @param playerNum
     * @param msg
     */
    protected void setPlayerResponse(int playerNum, StacTradeMessage msg){
    	responses[playerNum] = msg;
    }
    
    protected void resetResponses(){
    	responses = new StacTradeMessage[getGame().maxPlayers];
    }

    private SOCTradeOffer[] playerOffers;    
    /**
     * @param playerNum the player who made this offer
     * @param offer the new offer
     */
    protected void setPlayerOffer(int playerNum, SOCTradeOffer offer){
    	playerOffers[playerNum] = offer;
    }
    
    /**
     * @param playerNum
     * @return the offer made by the player
     */
    protected SOCTradeOffer getPlayerOffer (int playerNum){
    	return playerOffers[playerNum];
    }
    
    /** Store whether the last offer was forced. */
    private boolean lastOfferWasForced = false;
    
    protected boolean wasLastOfferForced(){
    	return lastOfferWasForced;
    }
    
    protected void setLastOfferForced(boolean b){
    	lastOfferWasForced = b;
    }
    
    private void resetPlayerOffers(){
    	playerOffers = new SOCTradeOffer[getGame().maxPlayers];
    }
    
    ///////////////////////////////////////////////////////////////////////////////////
    // HANDLING CHAT LOGIC
    ///////////////////////////////////////////////////////////////////////////////////
    
    /**
     * Assumption (for now) - you're only waiting for one thing at any given time.  Could be broken by sequences involving unsolicited announcements.
     * TODO: refactor into a list of things we're waiting for, and remove them when we are given them.  
     * Requires changing NULL to be a specified null (eg ANN:BP:NULL - but be careful to distinguish from actual NULL bps)
     */
    private final Set<String> isWaiting[];
    
    /**
     * Who is participating in the negotiations
     */
    private final boolean isParticipating[];
    
    /**
     * Is the player with the player number participating in chat negotiations?
     * @param pn
     * @return
     */
    protected boolean isParticipating(int pn){
    	return isParticipating[pn];
    }
    
    /**
     * Set the is participating flag of the player with player number to b
     * @param pn
     * @param b
     */
    protected void setParticipating(int pn, boolean b){
    	isParticipating[pn] = b;
    }
    
    /**
     * Return true if the manager is currently expecting an announcement or response from any opponents
     * Also return true if we have negotiated an offer and are waiting for the trade to be finalized
     * @return
     */
    protected boolean isWaiting() {
        // NB: Easier way to do this loop, but the id allows for better debugging.
        for (int i = 0; i < getGame().maxPlayers; i++) {
            Set<String> w = isWaiting[i];
        
            if (!w.isEmpty()) {
                return true;
            }
        }
        return getMyNegotiatedOffer()!=null;
    }
    
    /**
     * Debug method: return a list of all items which are currently in waiting state.
     * playerNumber,WaitKey
     * myNegotiatedOffer also triggers wait - add that at the end if applicable
     * Return empty list if none
     * @return
     */
    protected List<String> getWaiting() {
        List<String> ret = new ArrayList<String>();
        for (int i = 0; i < getGame().maxPlayers; i++) {
            Set<String> w = isWaiting[i];
        
            for (String s : w) {
                ret.add(i + "," + s);
            }
        }
        if (getMyNegotiatedOffer()!=null) {
            ret.add("negotiated offer: " + getMyNegotiatedOffer().toString());
        }
        return ret;
    }
    
    /**
     * Debug function: print the isWaiting and responses arrays.
     * @return a descriptive string for isWaiting[] and responses[]
     */
    protected String stateString() {
        String one = "waiting for: ";
        for (int i = 0; i < getGame().maxPlayers; i++) {
            one += i + ": ";
            if (isWaiting[i] != null) {
                one += isWaiting[i].toString();
            }
            one += "; ";
        }
        one += " *** responses: ";
        for (int i = 0; i < getResponses().length; i++) {
            one += i + ": ";
            StacTradeMessage tm = getPlayerResponse(i);
            if (tm != null) {
                one += tm.toString();
            }
            one += "; ";
        }
        return one;
    }        
    
    /**
     * Return true if the manager is waiting for a specific response from any player
     * @param type
     * @return 
     */
    protected boolean isWaiting(String type) {
        // NB: Easier way to do this loop, but the id allows for better debugging.
        for (int i=0; i<getGame().maxPlayers; i++) {
            Set<String> w = isWaiting[i];
        
            if (w.contains(type)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Return true if the manager is waiting for a specific response from a specific player
     */
    protected boolean isWaiting(int pn, String type) {
    	Set<String> w = isWaiting[pn];
        
    	if (w.contains(type)) {
    		return true;
    	}
    	return false;
    }
    
    /**
     * Start waiting for a player
     * @param playerNum
     * @param waitKey
     */
    protected void wait(int playerNum, String waitKey) {
        if (isParticipating[playerNum]) {
            isWaiting[playerNum].add(waitKey);
        }
    }
    
    /**
     * As human players do not announce their participation in chat negotiations, we need to force a wait for them during trades
     * @param playerNum
     * @param waitKey
     */
    protected void waitForHumanPlayer(int playerNum, String waitKey) {
        isWaiting[playerNum].add(waitKey);
    }
    
    /**
     * Start waiting for all opponents
     * @param waitKey
     */
    protected void waitAll(String waitKey) {
        for (int i=0; i<getGame().maxPlayers; i++) {
            if (i != getPlayerNumber()) {
                wait(i, waitKey);
            }
        }
    }
    
    /**
     * Stop waiting for a specific player
     * @param playerNum
     * @param waitKey
     */
    protected void stopWaiting(int playerNum, String waitKey) {
        isWaiting[playerNum].remove(waitKey);
    }
    
    /**
     * Stop Waiting for all opponents
     */
    protected void stopWaiting() {
        for (int i=0; i<getGame().maxPlayers; i++) {
            isWaiting[i].clear();
        }
    }

    private SOCResourceSet[] playerResourcesObserved;
    private SOCResourceSet[] playerResourcesBelieved;
    private boolean[] playerTrusted;
    
    /**
     * Keeping track of when a resource was 'used' for the last time. 
     * 'Using' here means adding to or subtracting from the quantity but not simply accessing the information (which would count in ACT-R).
     * First index is the player number, second index the resource number.
     * The time used is the message count from the brain.
     */
    private int[][] lastTimeResourceUsed;

    private void initResources() {
        for (int i=0; i<getGame().maxPlayers; i++) {
            playerResourcesObserved[i] = new SOCResourceSet();
            playerResourcesBelieved[i] = new SOCResourceSet();
            playerTrusted[i] = true;
        }
        lastTimeResourceUsed = new int[brain.getGame().maxPlayers][SOCResourceConstants.MAXPLUSONE];
    }
        
    /**
     * Get the resources believed to be held by an opponent
     * @param playerNumber
     * @return
     */
    public SOCResourceSet getOpponentResources(int playerNumber) {
        D.ebugPrintlnINFO("get opp res general -- " + brain.getPlayerName() + "(" + brain.getPlayerNumber() + ") for player " + brain.getGame().getPlayer(playerNumber).getName() + "(" + playerNumber + ")");
        
        //model being ignorant about or having forgotten other players' resources
        int ourPlayerNumber = getPlayerNumber();
        if (playerNumber != ourPlayerNumber) {
            if (brain.isRobotType(StacRobotType.IGNORANT_OPTIMIST)) {
                return new SOCResourceSet(2, 2, 2, 2, 2, 0);
            }
            if (brain.isRobotType(StacRobotType.IGNORANT_PESSIMIST)) {
                return new SOCResourceSet(0, 0, 0, 0, 0, 0);
            }
            if (brain.isRobotType(StacRobotType.FORGETTING_RESOURCES_PESSIMIST)) {
                int forgettingLatency = (Integer)brain.getTypeParam(StacRobotType.FORGETTING_RESOURCES_PESSIMIST);
                int brainTime = brain.getNumberOfMesagesReceived();
                for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.UNKNOWN; i++) {
                    if (lastTimeResourceUsed[playerNumber][i] < (brainTime - forgettingLatency)) {
                        playerResourcesBelieved[playerNumber].setAmount(0, i);
                        playerResourcesObserved[playerNumber].setAmount(0, i);
                    }
                }
            }
            if (brain.isRobotType(StacRobotType.FORGETTING_RESOURCES_OPTIMIST)) {
                int forgettingLatency = (Integer)brain.getTypeParam(StacRobotType.FORGETTING_RESOURCES_OPTIMIST);
                int brainTime = brain.getNumberOfMesagesReceived();
                for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.UNKNOWN; i++) {
                    if (lastTimeResourceUsed[playerNumber][i] < (brainTime - forgettingLatency)) {
                        playerResourcesBelieved[playerNumber].setAmount(2, i);
                        playerResourcesObserved[playerNumber].setAmount(2, i);
                    }
                }
            }
            if (brain.isRobotType(StacRobotType.FORGETTING_RESOURCES_RANDOM)) {
                int forgettingLatency = (Integer)brain.getTypeParam(StacRobotType.FORGETTING_RESOURCES_RANDOM);
                int brainTime = brain.getNumberOfMesagesReceived();
                for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.UNKNOWN; i++) {
                    if (lastTimeResourceUsed[playerNumber][i] < (brainTime - forgettingLatency)) {
                        playerResourcesBelieved[playerNumber].setAmount(RANDOM.nextInt(2), i);
                        playerResourcesObserved[playerNumber].setAmount(RANDOM.nextInt(2), i);
                    }
                }
            }
        }
        
        //normal version
        return playerTrusted[playerNumber] ? playerResourcesBelieved[playerNumber] : playerResourcesObserved[playerNumber];
    }

    /**
     * Get the resources the opponent is believed to have without considering trust.
     * @param playerNumber
     * @return 
     */
    public SOCResourceSet getOpponentResourcesBelieved(int playerNumber) {
        D.ebugPrintlnINFO("get opp res believed -- " + brain.getPlayerName() + "for player " + playerNumber);
        return playerResourcesBelieved[playerNumber];
    }

    /**
     * Get the resources the opponent is observed to have without considering trust.
     * @param playerNumber
     * @return 
     */
    public SOCResourceSet getOpponentResourcesObserved(int playerNumber) {
        D.ebugPrintlnINFO("get opp res observed -- " + brain.getPlayerName() + "for player " + playerNumber);
        return playerResourcesObserved[playerNumber];
    }
    
    /**
     * Get the total number of resources an opponent holds.
     * This is observable information.
     * @param playerNumber
     * @return the number of resources held by the player
     */
    public int getOpponentResourcesTotal(int playerNumber) {
        SOCPlayer player = brain.getGame().getPlayer(playerNumber);
        return player.getResources().getTotal();
    }
    
    /**
     * Add resources to an opponent's hand.  This is called when resources are observed, so should affect both the observed and believed
     * @param playerNumber  affected player
     * @param addType       resource type to be modified
     * @param addAmt        the new amount
     */
    public void addOpponentResourcesObserved(int playerNumber, int addType, int addAmt) {
        playerResourcesBelieved[playerNumber].add(addAmt, addType);
        playerResourcesObserved[playerNumber].add(addAmt, addType);

        lastTimeResourceUsed[playerNumber][addType] = brain.getNumberOfMesagesReceived();
    }
    
    /** 
     * Handle a resource announcement by an opponent.  Only do anything if we trust them.  This could be either their full hand,
     *  or a subset (eg resources they're willing to sell).   
     * TODO: Different trust models
     * TODO: Should we verify consistency with observed?
     * @param playerNumber
     * @param announced
     */
    public void announcedOpponentResources(int playerNumber, SOCResourceSet announced) {
        SOCResourceSet prb = playerResourcesBelieved[playerNumber];
        SOCResourceSet pro = playerResourcesObserved[playerNumber];

        //if we're gullible, we just believe what has been announced
        if (brain.isRobotType(StacRobotType.NP_ANNOUNCEMENT_GULLIBLE)) {
            pro.clear();
            pro.add(announced);
        } else {
            if (announced.getTotal() == prb.getTotal()) {
                // Just clear the believed and add the new ones
                // TODO: Verify consistency with observed?
                prb.clear();
                prb.add(announced);
            }
            else if (announced.getTotal() < prb.getTotal()) {
                // They have announced a subset of their resources (eg "I'll trade a clay")
                // Loop through each type.  If they announced more than we believe them to hold, take from UNKNOWN
                int unknown = prb.getAmount(SOCResourceConstants.UNKNOWN);
                for (int i=SOCResourceConstants.CLAY; i<SOCResourceConstants.UNKNOWN; i++) {
                    int a = announced.getAmount(i);
                    int b = prb.getAmount(i);
                    if (a>b) {
                        unknown -= (a-b);
                        prb.setAmount(a, i);
                    }
                }
                if (unknown<0) {
                    playerTrusted[playerNumber] = false;
                    prb.clear();
                    prb.add(pro);
                }
                else {
                    prb.setAmount(unknown, SOCResourceConstants.UNKNOWN);
                }            
            }
            else {
                // Problematic - 
                D.ebugERROR("Announced greater than size of believed: " + pro.toString() + " vs " + prb.toString() + " vs " + announced.toString() + "\n" + brain.getGame().getPlayer(playerNumber).getResources());

            }
        }
    }
    
    /**
     * Subtract resources from an opponent - these are observed subtractions, so should come from both believed and observed.
     *  If this yields an inconsistency in believed, we've been deceived - reset believed, and stop trusting that opponent.
     * @param playerNumber
     * @param sub
     */
    public void subtractOpponentResources(int playerNumber, int subType, int subAmt) {
        // Use the same logic to subtract from observed and believed
        for (SOCResourceSet rs : new SOCResourceSet[] {playerResourcesObserved[playerNumber], playerResourcesBelieved[playerNumber]}) {
            if (subType != SOCResourceConstants.UNKNOWN) {
                rs.subtract(subAmt, subType);
            } else {            
                for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD; i++) {
                    int curAmt = rs.getAmount(i);
                    int lost = Math.min(curAmt,  subAmt);
                    rs.subtract(lost, i);
                    rs.add(lost, SOCResourceConstants.UNKNOWN);
                }
                rs.subtract(subAmt, subType);    
            }
        }

        if (!brain.isRobotType(StacRobotType.NP_ANNOUNCEMENT_GULLIBLE)) {
            // It this yielded an inconsistency in believed, wipe the believed and stop trusting
            if (playerResourcesBelieved[playerNumber].getAmount(SOCResourceConstants.UNKNOWN) < 0) {
                //but if we're forgetting the resources anyway, we have no basis for distrust
                if (!brain.isRobotType(StacRobotType.FORGETTING_RESOURCES_PESSIMIST) && 
                        !brain.isRobotType(StacRobotType.FORGETTING_RESOURCES_OPTIMIST) && 
                        !brain.isRobotType(StacRobotType.FORGETTING_RESOURCES_RANDOM)) {
                    System.err.println("Distrust");
                    playerTrusted[playerNumber] = false;
                    playerResourcesBelieved[playerNumber].clear();
                    playerResourcesBelieved[playerNumber].add(playerResourcesObserved[playerNumber]);
                }
            }
        }
        lastTimeResourceUsed[playerNumber][subType] = brain.getNumberOfMesagesReceived();
    }

    private SOCPossiblePiece[] targetPieces;

    private boolean[] targetAnnounced;
    
    /**
     * Resets both the target pieces and if these were announced
     */
    protected void resetTargetPieces() {
        targetPieces = new SOCPossiblePiece[getGame().maxPlayers];
        targetAnnounced = new boolean[getGame().maxPlayers];
    }
    
    /**
     * @param playerNum
     * @return
     */
    protected boolean wasTargetPieceAnnounced(int playerNum){
    	return targetAnnounced[playerNum];
    }
    
    /**
     * @param playerNum
     * @return 
     */
    protected SOCPossiblePiece getTargetPiece(int playerNum){
    	return targetPieces[playerNum];
    }
    
    /**
     * if it was announced
     * @param playerNum
     * @param piece
     */
    protected void setTargetPieceAnnounced(int playerNum, SOCPossiblePiece piece){
    	targetAnnounced[playerNum] = true;
    	targetPieces[playerNum] = piece;
    }
    
    /**
     * if we computed it
     * @param playerNum
     * @param piece
     */
    protected void setTargetPieceUnannounced(int playerNum, SOCPossiblePiece piece){
    	targetAnnounced[playerNum] = false;
    	targetPieces[playerNum] = piece;
    }
    
    /**
     * @return the target pieces array
     */
    protected SOCPossiblePiece[] getAllTargetPieces(){
    	return targetPieces;
    }
    
    ///////////////////////////////////////////////////////////////////////////////////
    // OTHER PLAYERS' BEHAVIOUR
    ///////////////////////////////////////////////////////////////////////////////////
    
    /**
     * Trade offers by opponents.
     */

    private HashMap numOpponentTradeOffers, numOpponentTradeOfferRejected, numOpponentTradeOfferAccepted;
    
    protected void incNumOpponentTradeOffer(String opponentName) {
        Integer numOff = (Integer) numOpponentTradeOffers.get(opponentName);
        if (numOff == null) {
            numOff = new Integer(0);
        } else {
            numOff++;
        }
        numOpponentTradeOffers.put(opponentName, numOff);
    }
    
    protected Integer getNumOpponentTradeOffers(String opponentName) {
        Integer numOff = (Integer) numOpponentTradeOffers.get(opponentName);
        if (numOff == null) {
            return 0;
        }
        return numOff;
    }
    
    protected HashMap getNumOpponentTradeOffers() {
        return numOpponentTradeOffers;
    }

    protected void setNumOpponentTradeOffers(HashMap offers) {
        numOpponentTradeOffers = offers;
    }
    
    protected void incNumOpponentTradeOfferRejects(String opponentName) {
        Integer numRej = (Integer) numOpponentTradeOfferRejected.get(opponentName);
        if (numRej == null) {
            numRej = new Integer(0);
        } else {
            numRej++;
        }
        numOpponentTradeOfferRejected.put(opponentName, numRej);
    }
    
    protected Integer getNumOpponentTradeOfferRejects(String opponentName) {
        Integer numRej = (Integer) numOpponentTradeOfferRejected.get(opponentName);
        if (numRej == null) {
            return 0;
        }
        return numRej;
    }

    protected HashMap getNumOpponentTradeOfferRejects() {
        return numOpponentTradeOfferRejected;
    }

    protected void setNumOpponentTradeOfferRejects(HashMap rejects) {
        numOpponentTradeOfferRejected = rejects;
    }
    
    protected void incNumOpponentTradeOfferAccepts(String opponentName) {
        Integer numAcc = (Integer) numOpponentTradeOfferAccepted.get(opponentName);
        if (numAcc == null) {
            numAcc = new Integer(0);
        } else {
            numAcc++;
        }
        numOpponentTradeOfferAccepted.put(opponentName, numAcc);
    }
    
    protected Integer getNumOpponentTradeOfferAccepts(String opponentName) {
        Integer numAcc = (Integer) numOpponentTradeOfferAccepted.get(opponentName);
        if (numAcc == null) {
            return 0;
        }
        return numAcc;
    }
    
    protected HashMap getNumOpponentTradeOfferAccepts() {
        return numOpponentTradeOfferAccepted;
    }

    protected void setNumOpponentTradeOfferAccepts(HashMap accepts) {
        numOpponentTradeOfferAccepted = accepts;
    }
    
    
    ///////////////////////////////////////////////////////////////////////////////////
    // BEHAVIOURS TOWARDS OTHER PLAYERS
    ///////////////////////////////////////////////////////////////////////////////////

    /**
     * Keep track of the players that we don't trade with.
     * The value stored is the turn number in which the embargo started. 0 means the player is not embargoed
     */
    private int[] playersWithTradeEmbargo;
    
    private int numEmbargoesProposed;
    private int numEmbargoesCompliedWith;
    
    /**
     * Start a trade embargo against a player.
     * @param pn      player number
     * @param embargo boolean whether to start or revoke an embargo
     */
    protected void setEmbargoAgainstPlayer(int pn, boolean embargo) {
        if (embargo) {
            playersWithTradeEmbargo[pn] = brain.getGame().getTurnCount();
        } else {
            playersWithTradeEmbargo[pn] = 0;
        }
    }

    protected boolean isPlayerEmbargoed(int pn) {
        return playersWithTradeEmbargo[pn] > 0;
    }
    
    protected int getTurnEmbargoStarted(int pn) {
        return playersWithTradeEmbargo[pn];
    }

    protected int getNumOfEmbargoedPlayers() {
        int ret = 0;
        for (int i = 0; i < brain.getGame().getPlayers().length; i++) {
            if (playersWithTradeEmbargo[i] > 0) {
                ret++;
            }
        }
        return ret;
    }
    
    protected void incNumEmbargoesProposed() {
        numEmbargoesProposed++;
    }
    
    protected int getNumEmbargoesProposed() {
        return numEmbargoesProposed;
    }
    
    protected void incNumEmbargoesCompliedWith() {
        numEmbargoesCompliedWith++;
    }
    
    protected int getNumEmbargoesCompliedWith() {
        return numEmbargoesCompliedWith;
    }
    
    
    /**
     * The resources we have been blocked from making further offers to.
     */
    private SOCResourceSet blockedResources;
    private int numBlockMoves;
    private int turnBlockStarted; // because I'm blocking myself, I have to remember when I started the block
    
    protected void setBlockedResources(SOCResourceSet res) {
        if (blockedResources != null) {
            blockedResources = res;
        } else {
            blockedResources = new SOCResourceSet(); //res;
        }
    }
    
    protected void addBlockedResources(SOCResourceSet res) {
        if (blockedResources != null) {
            blockedResources.add(res);
        } else {
            blockedResources = new SOCResourceSet(); //res;
        }
    }
    
    protected SOCResourceSet getBlockedResources() {
        return blockedResources;
    }
    
    private void resetBlockedResources(){
    	setBlockedResources(null);
    }
    
    protected void incNumBlockMoves() {
        numBlockMoves++;
    }
    
    protected int getNumBlockMoves() {
        return numBlockMoves;
    }
    
    protected void setTurnBlockStarted(int turn) {
        turnBlockStarted = turn;
    }
    
    protected int getTurnBlockStarted() {
        return turnBlockStarted;
    }
    
    /**
     * The number of force-accept moves that the agent executed in this game.
     * This is regardless of whether they were successful.
     */
    private int numForceAcceptMoves;
    
    protected void incNumForceAcceptMoves() {
        numForceAcceptMoves++;
    }
    
    protected int getNumForceAcceptMoves() {
        return numForceAcceptMoves;
    }
    
    /**
     * For NP_FORCE_ACCEPT_PROPOSER agents return how many force-accept moves the agent can still make in this game.
     * Checks for limited number of these moves as set in NP_NUM_FORCE_ACCEPT_MOVES.
     * @return 
     */
    protected int getNumForceAcceptMovesLeft() {
        //make sure we can make force-accept move
        if (!brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_PROPOSER)) {
            return 0;
        }
        
        //check whether the number of moves is limited
        int movesAvailable = 1000;
        if (brain.isRobotType(StacRobotType.NP_NUM_FORCE_ACCEPT_MOVES)) {
            movesAvailable = (Integer)brain.getTypeParam(StacRobotType.NP_NUM_FORCE_ACCEPT_MOVES);
            return (movesAvailable - numForceAcceptMoves);
        }
        
        return movesAvailable;
    }

    private boolean[] blockTrades;
    
    /**
     * Block trading with a given player.
     * NB: moved from the negotiator; Part of Kevin's implementation! 
     * @param playerNum the player number corresponding to the player we are blocking trades from now on
     */
    protected void blockTrades(int playerNum) {
        blockTrades[playerNum] = true;
    }
    
    /**
     * Check if trades are being blocked with a given player number
     * NB: moved from the negotiator; Part of Kevin's implementation!
     * @param playerNum
     * @return 
     */
    protected boolean isBlockingTrades(int playerNum) {
        return blockTrades[playerNum];
    }
    
    private void resetBlockTrades(){
    	blockTrades = new boolean[getGame().maxPlayers];
    }
    
    
    ///////////////////////////////////////////////////////////////////////////////////
    // DIALOGUE INFORMATION
    ///////////////////////////////////////////////////////////////////////////////////
    
    /**
     * The last string that the NL parser returned.
     */
    protected String lastParseResult;
    
    /**
     * Store the last result of the NL parser.
     * @param lpr 
     */
    void setLastParseResult(String lpr) {
        lastParseResult = lpr;
    }

    
    ///////////////////////////////////////////////////////////////////////////////////
    // MISC
    ///////////////////////////////////////////////////////////////////////////////////
    
    
    /**
     * Replaces all of the existing information with the one inside the clone. It keeps the reference to the current brain.
     * NOTE: it also takes the number of embargoes/blocks/force accepts/trades and number of games played moves from the clone.
     * @param memory the cloned memory object from a saved game.
     */
    public void partialUpdateFromMemory(StacRobotDeclarativeMemory memory){
    	bestCompletedTradeOffer = memory.bestCompletedTradeOffer;
    	isSellingResource = memory.isSellingResource;
    	wantsAnotherOffer = memory.wantsAnotherOffer;
    	lastTimeResourceUsed = memory.lastTimeResourceUsed;
    	pastTradeOffers = memory.pastTradeOffers;
    	playerResourcesBelieved = memory.playerResourcesBelieved;
    	playerResourcesObserved = memory.playerResourcesObserved;
    	playerTrusted = memory.playerTrusted;
    	blockTrades = memory.blockTrades;
    	blockedResources = memory.blockedResources;
    	lastOfferWasForced = memory.lastOfferWasForced;
    	myOffer = memory.myOffer;
    	myNegotiatedOffer = memory.myNegotiatedOffer;
    	numBlockMoves = memory.numBlockMoves;
    	numEmbargoesCompliedWith = memory.numEmbargoesCompliedWith;
    	numEmbargoesProposed = memory.numEmbargoesProposed;
    	numForceAcceptMoves = memory.numForceAcceptMoves;
    	numGamesPlayed = memory.numGamesPlayed;
    	numOpponentTradeOffers = memory.numOpponentTradeOffers;
    	numOpponentTradeOfferAccepted = memory.numOpponentTradeOfferAccepted;
    	numOpponentTradeOfferRejected = memory.numOpponentTradeOfferRejected;
    	originalBestCompleteTradeOffer = memory.originalBestCompleteTradeOffer;
    	playerOffers = memory.playerOffers;
    	playersWithTradeEmbargo = memory.playersWithTradeEmbargo;
    	responses = memory.responses;
    	targetAnnounced = memory.targetAnnounced;
    	targetPieces = memory.targetPieces;
    	turnBlockStarted = memory.turnBlockStarted;
    	cardBuildPlan = memory.cardBuildPlan;
    	cityBuildPlans = memory.cityBuildPlans;
    	largestArmyBuildPlan = memory.largestArmyBuildPlan;
    	longestRoadBuildPlan = memory.longestRoadBuildPlan;
    	settlementBuildPlans = memory.settlementBuildPlans;
    	
    	//replace the is waiting and is participating in chat values
    	for(int i = 0 ; i < getGame().maxPlayers; i++){
    		isWaiting[i].clear();
    		isWaiting[i].addAll(memory.isWaiting[i]);
    		//if a human replaces a robot after saving, we need to make sure this robot is not trying to negotiate with that player via the chat interface
    		isParticipating[i] = false; //set all to false as the robots will announce their participation again after the loading is finished
    	}
    }

    //POTENTIAL
    //Robot's plans
    //Other players' plans
    //Robot's preferences
    //Other players' preferences

    /**
     * Print statistics about the lifetime of this declarative memory.
     * NOTE: This is very fragile: it assumes a certain convention for game names as well as the existence of directory "results/" relative to the current directory.
     * @author Markus Guhe
     */
    protected void printStats() {
        //intended to be overridden by the ACT-R DM version
    }

    /**
     * Helper class for representing a trade that was actually executed.
     */
    protected static class Trade {

        int from;
        int to;
        SOCResourceSet giveSet, getSet;
        
        private Trade(int from, int to, SOCResourceSet giveSet, SOCResourceSet getSet) {
            this.from = from;
            this.to = to;
            this.giveSet = giveSet;
            this.getSet = getSet;
        }
        
        @Override
        public String toString() {
            return "from=" + from + "|to=" + to + "|give=" + giveSet.toString() + "|get=" + getSet.toString();
        }
    }
}

    ///INFO AVAILABLE IN SOCGame THAT WE DON'T GIVE ACCESS TO AS PART OF THE DECLARATIVE MEMORY
    //SOCGame variables that we don't provide access to, in particular because they may be special for use by the server or a 6-player game
    //private boolean active; //true if this game is ACTIVE
    //public boolean isPractice; //true if the game's network is local for practice.
    //public boolean hasOldClients; //For use at server; are there clients connected which aren't at the latest version?
    //public int clientVersionLowest, clientVersionHighest; //For use at server; lowest and highest version of connected clients.
    //private int clientVersionMinRequired; //For use at server; lowest version of client which can connect to this game 
    //private boolean debugFreePlacement; //Are we in the 'free placement' debug mode?
    //private boolean debugFreePlacementStartPlaced; //Have we placed pieces in {@link #debugFreePlacement} during initial placement?
    //private boolean isFromBoardReset; //true if the game came from a board reset
    //public transient SOCGameBoardReset boardResetOngoingInfo; //For the server's use, if a reset is in progress, this holds the reset data until all robots have left 
    //private int boardResetVoteRequester; //If a board reset vote is active, player number who requested the vote.
    //private int boardResetVotes[]; //If a board reset vote is active, votes are recorded here.
    //private int boardResetVotesWaiting; //If a board reset vote is active, we're waiting to receive this many more votes.
    //private Hashtable opts; //the game options ({@link SOCGameOption}), or null
    //private int lastPlayerNumber; //number of the last player to place the first settlement
    //public final int maxPlayers; //maxPlayers is 4 for the standard game,
    //private int oldGameState; //the old game state
    //private boolean askedSpecialBuildPhase; //If true, it's a 6-player board and at least one player has requested to build during the Special Building Phase that occurs between turns.
    //private int specialBuildPhase_afterPlayerNumber; //For the 6-player board's Special Building Phase, the player number whose normal turn (roll, place, etc) has just ended.
    //private boolean placingRobberForKnightCard; //If true, and if state is {@link #PLACING_ROBBER}, the robber is being moved because a knight card has just been played.
    //private boolean forcingEndTurn; //If true, this turn is being ended. Controller of game (server) should call {@link #endTurn()}
    //private int oldPlayerWithLargestArmy; //To remember last {@link #playerWithLargestArmy} during {@link #saveLargestArmyState()} / {@link #restoreLargestArmyState()}.
    //Stack oldPlayerWithLongestRoad; //used to restore the LR player
    //boolean allOriginalPlayers; //used to track if there were any player subs
    //public long lastActionTime; //last time when a game action happened; can be used to check for game inactivity.
    //private boolean lastActionWasBankTrade; //Used at server; was the most recent player action a bank trade?
    //private int turnCount; //The number of normal turns (not rounds, not initial placements), including this turn.


    ///INFO AVAILABLE IN SOCPlayer THAT WE DON'T GIVE ACCESS TO AS PART OF THE DECLARATIVE MEMORY
    //SOCResourceSet lastActionBankTrade_give, lastActionBankTrade_get; //For use at server by SOCGame, if the player's previous action this turn was a bank trade, the resources involved.
    //private int[] resourceStats; //server-only total count of how many of each known resource the player has received this game from dice rolls.
    //private int buildingVP; //the number of victory points for settlements and cities
    //private int finalTotalVP; //the final total score (pushed from server at end of game),
    //boolean[][] roadNodeGraph; //a graph of what nodes are connected by this player's roads
    //boolean[] legalRoads; //list of edges where it is legal to place a road.
    //boolean askedSpecialBuild; //In 6-player mode, is the player asking to build during the Special Building Phase?
    //boolean hasSpecialBuiltThisTurn; //In 6-player mode, has the player already built during the Special Building Phase?
    //SOCPlayerNumbers ourNumbers; //the numbers that our settlements are touching
    //boolean playedDevCard; //this is true if the player played a development card this turn
    //boolean boardResetAskedThisTurn; //this is true if the player asked to reset the board this turn    
    //boolean[] potentialRoads; //a list of edges where a road could be placed on the next turn.
    //boolean[] potentialSettlements; //list of nodes where a settlement could be placed on the next turn
    //boolean[] potentialCities; //list of nodes where a city could be placed on the next turn.
    //boolean[] ports; //boolean array stating whether this player is touching a particular kind of port.

