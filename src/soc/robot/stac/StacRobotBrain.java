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
package soc.robot.stac;

import supervised.main.BayesianSupervisedLearner;
import simpleDS.learning.SimpleAgent;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import representation.FVGenerator;
import representation.FVGeneratorFactory;
import soc.client.SOCDisplaylessPlayerClient;
import soc.dialogue.StacDialogueManager;
import soc.dialogue.StacTradeMessage;
import soc.disableDebug.D;
import soc.game.SOCBoard;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCPlayerNumbers;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCSettlement;
import soc.game.SOCTradeOffer;
import soc.game.StacTradeOffer;
import soc.message.SOCDevCard;
import soc.message.SOCGameCopy;
import soc.message.SOCGameStats;
import soc.message.SOCGameTextMsg;
import soc.message.SOCParseResult;
import soc.message.SOCPlayerElement;
import soc.message.SOCPutPiece;
import soc.robot.SOCBuildPlanStack;
import soc.robot.SOCBuildingSpeedEstimate;
import soc.robot.SOCBuildingSpeedFast;
import soc.robot.SOCBuildingSpeedFastFractional;
import soc.robot.SOCBuildingSpeedProbabilistic;
import soc.robot.SOCPlayerTracker;
import soc.robot.SOCPossiblePiece;
import soc.robot.SOCRobotBrain;
import soc.robot.SOCRobotClient;
import soc.robot.SOCRobotNegotiatorImpl;
import soc.robot.stac.learning.Learner;
import soc.robot.stac.negotiationlearning.RewardFunction;
import soc.robot.stac.negotiationlearning.LearningNegotiator;
import soc.robot.stac.negotiatorpolicies.AlwaysAcceptPolicy;
import soc.robot.stac.negotiatorpolicies.AlwaysExpectAcceptPolicy;
import soc.robot.stac.negotiatorpolicies.AlwaysRejectPolicy;
import soc.robot.stac.negotiatorpolicies.GreedyAcceptPolicy;
import soc.server.SOCServer;
import soc.server.database.stac.ExtGameStateRow;
import soc.server.database.stac.ObsGameStateRow;
import soc.server.database.stac.StacDBHelper;
import soc.util.CappedQueue;
import soc.util.SOCRobotParameters;


/**
 * AI for playing Settlers of Catan.
 * STAC version
 * 
 * @author Kevin O'Connor, Markus Guhe
 */
public class StacRobotBrain extends SOCRobotBrain<StacRobotDM, PersuasionStacRobotNegotiator, SOCBuildPlanStack>
{   
	/**
	 * Static field for enabling/disabling chat negotiations for all of the robots using this type of brain
	 */
    protected static boolean chatNegotiation = true;//;false;
    
    /**
     * @return true if the robots can negotiate via the chat interface, false otherwise
     */
    public static boolean isChatNegotiation() {
        return chatNegotiation;
    }

    /**
     * for enabling/disabling chat negotiations during simulations
     * @param c
     */
    public static void setChatNegotiation(boolean c) {
        chatNegotiation = c;
    }
    
    /**
     * Our declarative memory that holds all declarative knowledge.
     */
    private final StacRobotDeclarativeMemory declarativeMemory;

    /**
     *  Should we consider the full build plan when evaluating trade offers?
     */
    protected final boolean fullPlan;

    /**
     * Define the robot type and parameters.
     * e.g. should the robot make and process partial offers?
     */
    protected final StacRobotType robotType;
    
    /**
     *  Dialogue Manager.  Need some work on configuring this - for now it will just use the brain type to determine behaviour
     */
    protected final StacRobotDialogueManager dialogueManager;

    /**
     *  Allow an associated set of learners
     */
    protected final Set<Learner> learners = new HashSet<Learner>();    
    public void registerLearner(Learner l) {
        learners.add(l);
    }
    
    /**
     * For the n-best agent, i.e. the TRY_N_BEST_BUILD_PLANS option, this stores the best-n BP that the DM should choose as its 'best' build plan.
     */
    protected int nOfPossibleBuildPlanToTry;    

    /** 
     * For robot type NP_MAX_NUMBER_OF_OFFERS_WITHOUT_BUILDING: store the number of offers without that a building action is tried.
     */
    private int nOfOffersWithoutBuildingAction;
    
    /**
     * When the decision maker ranks BPs by ETB - s * ES - w * EP, this is the s factor.
     */
    double speedupEstimateDiscountFactor;

    /**
     * When the decision maker ranks BPs by ETB - s * ES - w * EP, this is the w factor.
     */
    double deltaWinGameETADiscountFactor;

    // PREFERENCES OF ALL PLAYERS
    public HashMap<String,ArrayList<String>> tradePreferences;
    public HashMap<String,Integer> pointsTracker_rec;
    public HashMap<String,Integer> pointsTracker_old;
    public HashMap<String,Integer> agentDistribution;
    public int prevPointsPlayer;
    public RewardFunction rewards;

    /**
     * Create a new robot brain for the STAC robot.
     * @param rc the SOCRobotClient
     * @param params the SOCRobotParameters
     * @param ga the game
     * @param mq 
     * @param fullPlan flag specifying whether to use fullPlan
     * @param robotType the StacRobotType
     */
    public StacRobotBrain(SOCRobotClient rc, SOCRobotParameters params, SOCGame ga, CappedQueue mq, boolean fullPlan, StacRobotType robotType, 
    		HashMap<String,ArrayList<String>> tradePreferences) {
        super(rc, params, ga, mq);
        
	initialiseDDataStructures(tradePreferences);

        this.fullPlan = fullPlan;
        this.robotType = robotType;

        if (robotType.isType(StacRobotType.USE_ACT_R_DECLARATIVE_MEMORY)) {
            this.declarativeMemory = new StacRobotDeclarativeMemoryACTR(this, ga);
        } else {
            this.declarativeMemory = new StacRobotDeclarativeMemory(this, ga);
        }
        
        this.dialogueManager = new StacRobotDialogueManager(this);
        
        this.nOfPossibleBuildPlanToTry = 0;
        this.nOfOffersWithoutBuildingAction = 0;
        
        // Set the ranking discount factors
        speedupEstimateDiscountFactor = (double) 0;
        if (robotType.isType(StacRobotType.RANK_BPS_TRADE_OFF_ES_FACTOR)) {
            speedupEstimateDiscountFactor = (double) robotType.getTypeParam(StacRobotType.RANK_BPS_TRADE_OFF_ES_FACTOR);
        }
        deltaWinGameETADiscountFactor = (double) 0;
        if (robotType.isType(StacRobotType.RANK_BPS_TRADE_OFF_EP_FACTOR)) {
            deltaWinGameETADiscountFactor = (double) robotType.getTypeParam(StacRobotType.RANK_BPS_TRADE_OFF_EP_FACTOR);
        }

        // Strategies and values for Q-learning
        // TODO: Experimental - Make proper implementation of Q-learning of strategies.
        for (int i = 0; i < QValue.length; i++) {
            QValue[i] = (double)((double)1/(double)QValue.length);
            gamesPlayedWithStrategy[i] = 0;
            gamesWonWithStrategy[i] = 0;
        }
        strategies[0][0] = (double) 0.5; strategies[0][1] = 0;
        strategies[1][0] = (double) 0.5; strategies[1][1] = (double) 0.01;
        strategies[2][0] = (double) 0.5; strategies[2][1] = (double) 0.5;
        strategies[3][0] = (double) 0.2; strategies[3][1] = (double) 0.2;
    }	
    
    /**
     * Constructor to copy a brain.
     * Intended usage: creating a temporary brain to be used by the negotiator when computing opponent response to a trade offer.
     * @param brain      the brain to copy (by reference)
     * @param robotType  the new robot type
     */
    public StacRobotBrain(StacRobotBrain brain, StacRobotType robotType) {
        super(brain.client, brain.robotParameters, brain.game, new CappedQueue());

	initialiseDDataStructures(new HashMap<String,ArrayList<String>>());

    	this.client = brain.client;
        this.fullPlan = brain.fullPlan;
        this.robotType = robotType;

        this.declarativeMemory = brain.declarativeMemory;        
        this.dialogueManager = brain.dialogueManager;
        
        this.nOfPossibleBuildPlanToTry = brain.nOfPossibleBuildPlanToTry;
    }

    public void initialiseDDataStructures(HashMap<String,ArrayList<String>> tradePreferences) {
	//System.out.println( "StacRobotBrain.initialiseDDataStructures()> call from constructor" );
	this.tradePreferences = tradePreferences;
	//this.agentDistribution = agentDistribution;
	this.pointsTracker_rec = new HashMap<String,Integer>();
	this.pointsTracker_old = new HashMap<String,Integer>();
	this.prevPointsPlayer = 0;
	this.rewards = new RewardFunction();
    }

    @Override
    protected StacRobotDM createDM() {
        return new StacRobotDM(this, buildingPlan);
    }
    
    @Override
    public void recreateDM(){
    	this.decisionMaker = createDM();
    }
    
    @Override
    protected PersuasionStacRobotNegotiator createNegotiator() {
        if (isRobotType(StacRobotType.NP_ALWAYS_ACCEPT)) {
            return new AlwaysAcceptPolicy(this, fullPlan);
        }
        else if (isRobotType(StacRobotType.NP_ALWAYS_REJECT)) {
            return new AlwaysRejectPolicy(this, fullPlan);
        }
        else if (isRobotType(StacRobotType.NP_GREEDY_ACCEPT)) {
            return new GreedyAcceptPolicy(this, fullPlan);
        }
        else if (isRobotType(StacRobotType.NP_ALWAYS_EXPECT_ACCEPT)) {
            return new AlwaysExpectAcceptPolicy(this, fullPlan);
        }
        else {
            return new PersuasionStacRobotNegotiator(this, fullPlan); //, robotType);
        }
    }

    /**
     * Access to our game data within the Stac package.
     * @return SOCGame object with our game data
     */
//    protected SOCGame getGameData() {
//        return declarativeMemory.getGameData();
//    }
    
    /**
     * Ask whether we're of a particular robot type.
     * (Robot types can be combinations of StacRobotType values; that's why we need a dedicated method.)
     * 
     * @param rt The StacRobotType to test.
     * @return true if the robot is of the tested type
     */
    public boolean isRobotType(String rt) {
        return (robotType.isType(rt));
    }
    
    /**
     * 
     * @return The type of this robot.
     */
    protected StacRobotType getRobotType() {
        return robotType;
    }
    
    public Object getTypeParam(String rt) {
        return robotType.getTypeParam(rt);
    }
    
    /**
     * Method intended for accessing the memory for cloning or updating
     * 
     * @return this brain's memory
     */
    public StacRobotDeclarativeMemory getMemory(){
    	return this.declarativeMemory;
    }
     
                    
    /**
     * Access to our player data within the Stac package.
     * @return SOCPlayer object with our player data
     */
    protected SOCPlayer getPlayerData() {
        return declarativeMemory.getPlayer(client.getNickname());//playerData;
    }
    
    @Override
    /**
     * Override to ensure we update the values in declarative memory as well as the values in game.player.resources (maintained for use by client)
     * Also use the more precise accounting of robbing.
     */
    protected void handleResources(int action, SOCPlayer player, int resourceType, int amount) {
        
        // Handling of game.player.resources.  left behind for legacy reasons - may be used by client, etc, but should not be used by DM or Negotiator
        if (isRobotType(StacRobotType.IGNORANT) && player.getPlayerNumber()!=ourPlayerData.getPlayerNumber()) {
            resourceType = SOCResourceConstants.UNKNOWN;
        }
        else if (action==SOCPlayerElement.LOSE && resourceType == SOCResourceConstants.UNKNOWN){
            SOCResourceSet rs = player.getResources();           
            for (int i = SOCResourceConstants.MIN;
                    i < SOCResourceConstants.MAXPLUSONE; i++)
            {
                int curAmt = rs.getAmount(i);
                int lost = Math.min(curAmt,  amount);
                rs.subtract(lost, i);
                rs.add(lost, SOCResourceConstants.UNKNOWN);
            }
            rs.subtract(amount, resourceType);          
            
        }
        else {
            // The existing handling of known resource adding/subtracting/setting is suitable for use.      
            SOCPlayerElement mes = new SOCPlayerElement(null, player.getPlayerNumber(), action, resourceType, amount);
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
            (mes, player, resourceType);
        }          
        

        // Update the Declarative Memory resource beliefs/observations as well
        if (action == SOCPlayerElement.LOSE) {
            declarativeMemory.subtractOpponentResources(player.getPlayerNumber(), resourceType, amount);
        }
        else if (action == SOCPlayerElement.GAIN) {
            declarativeMemory.addOpponentResourcesObserved(player.getPlayerNumber(), resourceType, amount);
        }
        else if (action == SOCPlayerElement.SET) {
            // Apart from network situations (eg a robot joins a game in progress), this happens only for our own resources, as a sanity check.  Ignore this for now. 
        }
        
        /** 
         * Verification of compatibility.  Ensure we haven't accidentally ended up with a belief inconsistent with observations, w.r.t. size
         */
        SOCResourceSet dmRS = declarativeMemory.getOpponentResources(player.getPlayerNumber());
        SOCResourceSet gameRS = game.getPlayer(player.getPlayerNumber()).getResources();
        if (dmRS.getTotal() != gameRS.getTotal()) {
            sendText(StacDialogueManager.toMessage("Incorrect count for " + player.getName() + ":"
                    + dmRS.toString() 
                    + " vs "
                    + gameRS.toString()));
        }
        
    }



    public List<SettlementNode> getLegalSettlements() {
        return getLegalSettlements(declarativeMemory.getPlayer(0));
    }

    public List<SettlementNode> getLegalSettlements(SOCPlayer p) {
        List<Integer> legalSettlements = p.getLegalSettlements();

        List<SettlementNode> ret = new ArrayList<SettlementNode>(legalSettlements.size());
        // Iterate through settlements.  Doesn't matter which player we use, all have 
        //  equivalent values for this.  Should really track this in board instead...
        for (Integer node : legalSettlements) {
            SettlementNode n = new SettlementNode(node, declarativeMemory.getBoard());
            //n.setIncome(1);
            //n.setScore(getScore(n, considerCurrent));
            ret.add(n);
        }
        return ret;
    }

    /**
     * Override this method - we need to track some stuff of our own, do the default behavior and then make our own changes
     * @param mes   the SOCPutPiece message
     */
    @Override
    protected void handlePUTPIECE_updateGameData(SOCPutPiece mes) {		
        super.handlePUTPIECE_updateGameData(mes);
        
        //if ( isRobotType(StacRobotType.MDP_LEARNING_NEGOTIATOR) && isOurTurn() )
        //	negotiator.mdp_negotiator.handlePUTPIECE( mes );
        
        SOCPlayer p = game.getPlayer(mes.getPlayerNumber());
        //if it is the second road, track the resources from the previous placed settlement,
        //so we can handle the situation when a player cancels a settlement
        SOCResourceSet rs = new SOCResourceSet();
        if ((mes.getPieceType() == SOCPlayingPiece.ROAD)
                && p.getRoads().size() == 2
                && p.getSettlements().size() == 2 
                && p.getCities().isEmpty() ) {
        	
            SOCPlayerTracker tr = (SOCPlayerTracker) playerTrackers.get
                    (Integer.valueOf(mes.getPlayerNumber()));
                SOCSettlement se = tr.getPendingInitSettlement();
                
            Enumeration hexes = SOCBoard.getAdjacentHexesToNode(se.getCoordinates()).elements();
            while (hexes.hasMoreElements())
            {
                Integer hex = (Integer) hexes.nextElement();
                int type = game.getBoard().getHexTypeFromCoord(hex.intValue());
                if (type>=SOCResourceConstants.CLAY && type <= SOCResourceConstants.WOOD) { 
                    declarativeMemory.addOpponentResourcesObserved(mes.getPlayerNumber(), type, 1);
                    rs.add(1, type);
                }
            }
        }
        
    }
    
    @Override
    protected void buildRequestPlannedPiece(SOCPossiblePiece targetPiece, SOCBuildPlanStack plan) {
        if ( isRobotType(StacRobotType.MDP_LEARNING_NEGOTIATOR) && isOurTurn() )
        	negotiator.mdp_negotiator.buildRequestPlannedPiece( targetPiece, plan );

    	super.buildRequestPlannedPiece( targetPiece, plan );
    }

    /** Keeping track of the last offer we completed as part of responding to a partial offer.
     */
//    protected SOCTradeOffer bestCompletedOffer = null;

    /***
     * Override make offer to have it generate a chat message instead of a direct trade offer.
     * @return  true if a trade offer iff it makes an offer (as in super)
     */    
    @Override
    protected boolean makeOffer(SOCBuildPlanStack buildPlan) {
        if (isRobotType(StacRobotType.NP_MAX_NUMBER_OF_OFFERS_WITHOUT_BUILDING)) {
            if (nOfOffersWithoutBuildingAction >= (Integer) getTypeParam(StacRobotType.NP_MAX_NUMBER_OF_OFFERS_WITHOUT_BUILDING))
                return false;
        }
        
        boolean makeOffer;
        if (chatNegotiation) {
            String tradeMsg =  dialogueManager.negotiateTrade(buildPlan);  
            if (tradeMsg!=null) {
                sendText(tradeMsg);     
        	}
            makeOffer = tradeMsg != null;
        }
        else {
            makeOffer = super.makeOffer(buildPlan);
        }
        
        if (makeOffer)
            nOfOffersWithoutBuildingAction++;
        return makeOffer;
    }
    
    /**
     * Send the negotiated offer using the actual trade interface.
     * @param offer
     */
    protected void sendOffer(SOCTradeOffer offer) {
//        printMess("Sending negotiated offer: " + offer.toString());
        
        tradeAccepted = false;
        ///
        ///  reset the offerRejections flag
        ///
        boolean[]to = offer.getTo();
        int numSentTo=0;
        for (int i = 0; i < game.maxPlayers; i++)
        {
            if (to[i]) {
                waitingForTradeResponsePlayer[i] = true;
                numSentTo++;
            }
        }
        // Sanity check that the offer was fully specified
        if (numSentTo != 1) {
//            System.err.println("Offer sent to " + numSentTo);
            sendText("Offer sent to " + numSentTo);
        }
        else {
            waitingForTradeResponse = true;
            counter = 0;
            client.offerTrade(game, offer);
        }
    }
    
    
    @Override
    protected int considerOffer(SOCTradeOffer offer)
    {
        // Check if this is something we already negotiated
        boolean[] offeredTo = offer.getTo();

        if (chatNegotiation && offeredTo[ourPlayerData.getPlayerNumber()]) {
            if (offer.equals(dialogueManager.getMyNegotiatedOffer())) {
                // We are processing the finalization right now, so it is safe to inform the dialogue manager this trade is finished
                dialogueManager.resetMyNegotiatedOffer();
                return SOCRobotNegotiatorImpl.ACCEPT_OFFER;
            }                
           
            // We should never be here, but there were occasional issues.  Add to logs and std-err
            //  for debugging purposes (remove soon, hopefully!)
            D.ebugERROR("Non-negotiated offer...");
            if (dialogueManager.getMyNegotiatedOffer()==null) {
                // This scenario should be resolved
            	D.ebugERROR("NuLL");
                sendText("Null offer neg");
            }
            else {
                // I have not yet seen this scenario
                sendText("NEQ offer neg");
                D.ebugERROR(offer.toString() + "\n" + dialogueManager.getMyNegotiatedOffer().toString());
            }
        }        
        
        int response = -1;

        SOCPlayer offeringPlayer = declarativeMemory.getPlayer(offer.getFrom());

        if ((offeringPlayer.getCurrentOffer() != null) && (offer == offeringPlayer.getCurrentOffer()))
        {
            if (offeredTo[ourPlayerData.getPlayerNumber()])
            {
                //---MG
                if (offer.getGiveSet().getTotal() == 0) //check whether it's a partial offer in which what we are getting is unspecified
                {
                    response = negotiator.handlePartialOffer(offer);
                }
                else {            	
                    response = negotiator.considerOffer(offer, ourPlayerData.getPlayerNumber());
                }
            }
        }

        return response;
    }

    @Override
    protected SOCBuildPlanStack createBuildPlan() {
        return new SOCBuildPlanStack();
    }    
    
    // Override this to allow agent to take information from text messages  
    @Override
    protected void handleChat(SOCGameTextMsg gtm) {
    	/*very rarely the robots start chating before their player information is being set during the sitdown action. 
    	Just catch the exception and print out the error in case this starts hapenning more often*/
    	try {
            List<String> msgs = dialogueManager.handleChat(game.getPlayer(gtm.getNickname()).getPlayerNumber(), gtm.getText());
            for (String s : msgs) {
                sendText(s);
            }
    	} catch(NullPointerException ne){
            ne.printStackTrace();
    	}
    	
    }
    
    @Override
    public void handleGameTxtMsg(SOCGameTextMsg gtm) {
        String text = gtm.getText();
        if ((text.startsWith(client.getNickname() + " traded ") && !text.endsWith(" bank.") && !text.endsWith("port."))
                || (text.contains(" traded ") && text.endsWith(" from " + client.getNickname() + "."))) {
        	//TODO capture trade for preference model
            if (declarativeMemory.getMyNegotiatedOffer() == null)
                System.err.println("negotiated offer is null");
            declarativeMemory.negotiatedOfferWasExecuted(); //.addPastTrade(declarativeMemory.getMyNegotiatedOffer());
        }
    }
 
    /*
        Fragment for a method to parse an XL string.
        It's of no use, because we don't get anything from the parser.
    */
    private void parseXmlString(String xmlString){
        //get the factory
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            //Using factory to get an instance of document builder
            DocumentBuilder db = dbf.newDocumentBuilder();

            //parse using builder to get DOM representation of the XML file
            InputSource is = new InputSource(new StringReader(xmlString));
            Document doc;
            doc = db.parse(is);//
            Element topElement = doc.getDocumentElement();
            String topElementNodeName = topElement.getNodeName();
            if (topElementNodeName.equals("game_fragment")) {
                NodeList topNodeList = topElement.getChildNodes();
                for (int i = 0; i < topNodeList.getLength(); i++) {
                    Node n = topNodeList.item(i);
                    if (n.getNodeName().equals("game_event")) {
                        System.err.println(i + ": " + n.getNodeName() + " -- " + n.toString());
                    }
                }
            }
        }catch(ParserConfigurationException pce) {
                pce.printStackTrace();
        }catch(SAXException se) {
                se.printStackTrace();
        }catch(IOException ioe) {
                ioe.printStackTrace();
        }
    }
    @Override
    public void handleParseResult(SOCParseResult mes) {
        //Simply store the result of a parse in the declarative memory now; don't do any dialogue management yet
        declarativeMemory.setLastParseResult(mes.getParseResult());
        parseXmlString(mes.getParseResult());
    }
    
    @Override
    protected void handleDEVCARD(SOCDevCard mes){
    	super.handleDEVCARD(mes);
    	
    	//if ( isRobotType(StacRobotType.MDP_LEARNING_NEGOTIATOR) )
    	//	negotiator.mdp_negotiator.handleDEVCARD( mes );

    }
    
    /**
     * At the beginning of a turn, defer to the Dialogue Manager to see if they want to announce anything, set a flag to wait for other chats, etc...
     * Also check whether trade embargoes are to be proposed or lifted.
     */
    @Override
    protected void startTurnActions(int player) {
        nOfOffersWithoutBuildingAction = 0;
        
        //allow the negotiator to take turn initial actions (like proposing or lifting embargoes)
        negotiator.startTurnActions(player);

        List<String> msgs = dialogueManager.startTurnChat(player);
        for (String msg : msgs) {
            sendText(msg);
        }
    }
    
    @Override
    protected boolean endTurnActions(){

		tradePreferences = new HashMap<String,ArrayList<String>>();
    	
        if ( isRobotType(StacRobotType.MDP_LEARNING_NEGOTIATOR) )
        	negotiator.endTurnActions();
        /*
        if ( isRobotType(StacRobotType.MDP_LEARNING_NEGOTIATOR) ) {

        	// check for built pieces
        	//negotiator.mdp_negotiator.checkPieces( ourPlayerData );
        	//   or check resources??
        	SOCBuildPlanStack bp = getBuildingPlan();
        	if ( !bp.isEmpty() ) {
                SOCResourceSet targetResources = negotiator.getResourcesForPlan( bp ); 
                SOCResourceSet ourResources = ourPlayerData.getResources();
            	if ( ourResources.contains(targetResources) ) { // might not happen since we might have already built the piece?
            		System.out.println( "StacRobotBrain::endTurnActions()> we have the target resources!" );
                	negotiator.mdp_negotiator.buildRequestPlannedPiece( null, bp );
            	} else
            		negotiator.mdp_negotiator.update();
        	}
        }
        */

    	//check to see whether we want to try the next-best build plan
    	boolean finishTurnNow = true;
        if (isRobotType(StacRobotType.TRY_N_BEST_BUILD_PLANS)) {
            int n = (Integer) getTypeParam(StacRobotType.TRY_N_BEST_BUILD_PLANS);
            if (n > nOfPossibleBuildPlanToTry) {
                nOfPossibleBuildPlanToTry++;
                resetBuildingPlan();
                doneTrading = false;
                negotiator.resetTargetPieces();
                finishTurnNow = false;
            }else
                nOfPossibleBuildPlanToTry = 0;//for if we're an n-best agent: reset how far down we got in the list of possible build plans
        }else
            nOfPossibleBuildPlanToTry = 0;//for if we're an n-best agent: reset how far down we got in the list of possible build plans

        return finishTurnNow;
    }
    
    /**
     * Defer to the dialogue manager to determine if it is currently waiting for announcements or responses.
     * @return if the robot currently waiting for announcements or responses?
     */
    @Override
    protected boolean isWaiting() { 
        return declarativeMemory.isWaiting(); 
    }
    
    // Provide a protected getter for negotiator for dialogue manager to use - might want to rethink how this all works together, but this is okay for now
    protected PersuasionStacRobotNegotiator getNegotiator() {
        return negotiator;
    }
    
    @Override
    protected boolean isLegacy() {
        return isRobotType(StacRobotType.LEGACY_PLAY1);
    }
    
    @Override
    public void startGameChat(){
        List<String> msgs = dialogueManager.startGameChat();
        for (String msg : msgs) {
            sendText(msg);
        }
    }
    
    /**
     * Send a game text message.
     * NOTE: It's important to send all messages to the game through this method, so that we can keep track of the discourse!
     * @param msg the contents
     */
    public void sendText(String msg){
        if (isRobotType(StacRobotType.DIALOGUE_MANAGER_USE_SDRT)) {
            dialogueManager.getSdrtParser().parseTextMessageIntoSDRT(getPlayerNumber(), msg);
        }
        client.sendText(getGame(), msg);
    }
    
    @Override
    public void debugPrintBrainStatus() {
        super.debugPrintBrainStatus();
        List<String> waitKeys = declarativeMemory.getWaiting();
        for (String s : waitKeys) {
            printMess("DlgMgr Waiting: " + s);
            sendText("DlgMgr Waiting: " + s);
        }
        
        if (waitingForTradeMsg || waitingForTradeResponse) {
            System.err.println("Current player responses:");
            for (int p = 0; p < game.maxPlayers; p++) {
                System.err.println(p + " " + game.getPlayerNames()[p] + ": " + getDialogueManager().getPlayerResponse(p));
            }
        }
    }
    
    @Override
    public SOCBuildingSpeedEstimate getEstimator(SOCPlayerNumbers numbers) {
        if (isRobotType(StacRobotType.BSE_ACCURATE)) {
            return new SOCBuildingSpeedProbabilistic(numbers);
        }
        else if (isRobotType(StacRobotType.BSE_FRACTIONAL)) {
            return new SOCBuildingSpeedFastFractional(numbers);
        }
        else if (isRobotType(StacRobotType.BSE_USING_BELIEFS)) {
            return new StacBuildingSpeedFastUsingBeliefs(numbers, this);
        }
        else {
            return new SOCBuildingSpeedFast(numbers);
        }
    }
    
    @Override
    public SOCBuildingSpeedEstimate getEstimator() {
        if (isRobotType(StacRobotType.BSE_ACCURATE)) {
            return new SOCBuildingSpeedProbabilistic();
        }
        else if (isRobotType(StacRobotType.BSE_FRACTIONAL)) {
            return new SOCBuildingSpeedFastFractional();
        }
        else {
            return new SOCBuildingSpeedFast();
        }
    }
    
    // Debug function - send a chat message to the game to be reviewed with replay client
    public void sendChatText(String txt) {        
        sendText(StacDialogueManager.toMessage(txt));
    }
        
    // Handle the results of the game.  Update all associated learners    
    @Override
    protected void handleGAMESTATS(SOCGameStats message) {
        for (Learner l : learners) {
            l.learn(this, message);
        }

        if ( isRobotType(StacRobotType.MDP_LEARNING_NEGOTIATOR) ) {
//        	System.out.println( "StacRobotBrain> Updating policy at end of the game" );
//        	int totalVPs = getPlayerData().getTotalVP();
//        	SOCPlayer winner = getPlayerData().getGame().getPlayerWithWin();
//        	if ( winner != null && winner.getPlayerNumber() == getPlayerNumber() )
//        		negotiator.mdp_negotiator.recordReward( totalVPs + 10 ); //TODO create parameter for winning bonus (=10)
//        	else
//        		negotiator.mdp_negotiator.recordReward( totalVPs );
//        	negotiator.mdp_negotiator.update();
        	negotiator.mdp_negotiator.savePolicies( game.getName() );
        	showLearningResults();
        	negotiator.mdp_negotiator.numVPs = 0; // reset VPs for the next game
			//System.out.println( "End of MDP game" );
        }
    }
    
    /**
     * get the building plan from the memory instead of directly from here.
     * @return 
     */
    @Override
    public SOCBuildPlanStack getBuildingPlan(){
    	return getMemory().getCurrentBuildPlan();
    }
    
    /**
     * WARNING: Only to be called from the Declarative memory as it gives direct access to information modelled in there
     * @return the actual building plan
     */
    protected SOCBuildPlanStack getBuildingPlanDirect(){
    	return buildingPlan;
    }
    
    public int getPlayerNumber() {
        return getPlayerData().getPlayerNumber();
    }
    
    public String getPlayerName() {
        return client.getNickname();
    }
    
    /**
     * @return the dialogue manager linked to this brain
     */
    public StacRobotDialogueManager getDialogueManager(){
    	return dialogueManager;
    }
    
    // get and set methods for HWU negotiators
    
    public void setSupervisedNegotiator( BayesianSupervisedLearner sltrader ) {
    	client.setSupervisedNegotiator( sltrader );
    }
    
    public BayesianSupervisedLearner getSupervisedNegotiator() {
    	return client.getSupervisedNegotiator();
    }

    public void setDeepNegotiator( SimpleAgent deeptrader ) {
    	client.setDeepNegotiator( deeptrader );
    }
    
    public SimpleAgent getDeepNegotiator() {
    	return client.getDeepNegotiator();
    }

    public void setMDPNegotiator( LearningNegotiator sltrader ) {
    	client.setMDPNegotiator( sltrader );
    }
    
    public LearningNegotiator getMDPNegotiator() {
    	return client.getMDPNegotiator();
    }
        
    public void resetNumberOfOffersWithoutBuildingAction() {
        nOfOffersWithoutBuildingAction = 0;
    }
    
    /**
     * kill this brain
     */
    @Override
    public void kill() {
        if (isRobotType(StacRobotType.DIALOGUE_MANAGER_USE_SDRT)) {
            dialogueManager.getSdrtParser().printSDRTs();
        }    
        
    	declarativeMemory.printStats();
        super.kill();
    }
    
    double[] QValue = new double[4];
    int[] gamesPlayedWithStrategy = new int[4];
    int[] gamesWonWithStrategy = new int[4];
    double[][] strategies = new double[4][2];
    int selectedStrategy = 0;
    

    /**
     * Receive and store information from previous incarnations of the brain.
     * This should only be called once for the lifetime of the brain (i.e. also per game), 
     * directly after the brain is created.
     * @param preservedInfo HashMap containing the preserved info
     */
    public void setPersistentBrainInformation(HashMap preservedInfo) {
        //TODO: Take care of the initial case to choose a policy!
        if (preservedInfo == null) {
            return;
        }

        declarativeMemory.setNumGamesPlayed((int) preservedInfo.get("numGames"));
        declarativeMemory.setNumOpponentTradeOffers((HashMap) preservedInfo.get("tradeOffers"));
        declarativeMemory.setNumOpponentTradeOfferRejects((HashMap) preservedInfo.get("tradeOffersRejected"));
        declarativeMemory.setNumOpponentTradeOfferAccepts((HashMap) preservedInfo.get("tradeOffersAccepted"));

        if (!isRobotType(StacRobotType.ADAPT_TO_OPPONENT_TYPE)) {
            return;
        }

        QValue = (double[])preservedInfo.get("QValues");
        gamesPlayedWithStrategy = (int[])preservedInfo.get("gamesPlayedWithStrategy");
        gamesWonWithStrategy = (int[])preservedInfo.get("gamesWonWithStrategy");
        double draw = rand.nextFloat();
        double accumulatedPolicyValue = 0;
        double sumPolicyValue = 0;
        for (int i = 0; i < QValue.length; i++) {
            sumPolicyValue += QValue[i];
        }
        //{0.2, 0.5, 0.3}
        for (int i = 0; i < strategies.length; i++) {
//            we're not making the selection of the strategy dependent on the Q-value
//            accumulatedPolicyValue += (QValue[i] / sumPolicyValue);
            accumulatedPolicyValue += 1/(double)strategies.length;
            if (accumulatedPolicyValue >= draw) {
                speedupEstimateDiscountFactor = strategies[i][0];
                deltaWinGameETADiscountFactor = strategies[i][1];
                selectedStrategy = i;
                break;
            }
        }

//        speedupEstimateDiscountFactor = (double) preservedInfo.get("speedupEstimateDiscountFactor");
//        deltaWinGameETADiscountFactor = (double) preservedInfo.get("deltaWinGameETADiscountFactor");
//        decisionMaker.setS(speedupEstimateDiscountFactor);// = (double) preservedInfo.get("speedupEstimateDiscountFactor");
//        decisionMaker.speedupEstimateDiscountFactor = speedupEstimateDiscountFactor;
//        decisionMaker.deltaWinGameETADiscountFactor = (double) preservedInfo.get("deltaWinGameETADiscountFactor");
//        if (decisionMaker.speedupEstimateDiscountFactor == 0) decisionMaker.speedupEstimateDiscountFactor = 2;
//        if (decisionMaker.deltaWinGameETADiscountFactor == 0) decisionMaker.deltaWinGameETADiscountFactor = 2;


//        System.err.println("speedupEstimateDiscountFactor=" + speedupEstimateDiscountFactor + ", deltaWinGameETADiscountFactor=" + deltaWinGameETADiscountFactor);
    }
    
    /**
     * Return the information we would like to receive again when the next incarnation of the brain is created.
     * This should only be called just before the brain is killed.
     * @return a HashMap with the information to be preserved
     */
    public HashMap getPersistentBrainInformation() {
        //record that we finished this game
        declarativeMemory.incNumGamesPlayed();

        //collect information we want to preserve
        HashMap infoToPreserve = new HashMap();
        infoToPreserve.put("numGames", declarativeMemory.getNumGamesPlayed());
        infoToPreserve.put("tradeOffers", declarativeMemory.getNumOpponentTradeOffers());
        infoToPreserve.put("tradeOffersRejected", declarativeMemory.getNumOpponentTradeOfferRejects());
        infoToPreserve.put("tradeOffersAccepted", declarativeMemory.getNumOpponentTradeOfferAccepts());
        
        if (!isRobotType(StacRobotType.ADAPT_TO_OPPONENT_TYPE)) {
            return infoToPreserve;
        } 
        
        boolean gameWon = game.getPlayerWithWin() == ourPlayerData;
        try {
            //write the old QValue, before we do the update
            String outString = declarativeMemory.getNumGamesPlayed() + "\t" + 
                    gameWon + "\t" +
                    QValue[0] + "\t" + QValue[1] + "\t" + QValue[2] + "\t" + QValue[3] + "\t" + 
                    (QValue[0] + QValue[1] + QValue[2] + QValue[3]);
            client.strategiesOut.write(outString);
            client.strategiesOut.newLine();
            client.strategiesOut.flush();
        } catch (IOException ex) {
            Logger.getLogger(StacRobotBrain.class.getName()).log(Level.SEVERE, null, ex);
        }

        double reward = 0;
        gamesPlayedWithStrategy[selectedStrategy]++;
        if (gameWon) {
            gamesWonWithStrategy[selectedStrategy]++;
            reward = 1;
        }
        int gamesPlayed = declarativeMemory.getNumGamesPlayed();
        int sumGamesWon = 0;
        int numStrategies = gamesWonWithStrategy.length;
        double maxQ = 0;
        for (int i = 0; i < numStrategies; i++) {
            sumGamesWon += gamesWonWithStrategy[i];
            maxQ = Math.max(QValue[i], maxQ);
        }

        // Q_{t+1}(s_t, a_t) = Q_t(s_t, a_t) + \alpha_t(s_t, a_t) \times [R_{t+1} + \gamma \max_a Q_t(s_{t+1}, a_t) - Q_{t+1}(s_t, a_t)]
        double alpha = (double) 0.1;
        double gamma = (double) 0.9;
        QValue[selectedStrategy] = QValue[selectedStrategy] + alpha * (reward + (gamma * maxQ) - QValue[selectedStrategy]);
        
//        if (gamesPlayedWithStrategy[selectedStrategy] > 0) {
//            QValue[selectedStrategy] = (double)gamesWonWithStrategy[selectedStrategy] / (double)gamesPlayedWithStrategy[selectedStrategy];
//        }
        
//        double alpha = (double) 0.7;
//        final double beta = (double) 0.9;
//        QValue[selectedStrategy] = ((1-alpha)*QValue[selectedStrategy]) + (alpha * (reward + (beta * QValue[selectedStrategy])));
        
//        final double beta = (double) 0.9;
//        QValue[selectedStrategy] = reward + (beta * QValue[selectedStrategy]);
        
//        //check we have won at least on game (otherwise there'speedupEstimateDiscountFactor a division by 0)
//        if (sumGamesWon > 0) {
//            for (int i = 0; i < QValue.length; i++) {
//                double alpha = (double) 0.9;
//                double a;// = alpha;
//    //            a = (double) (alpha * ((-1 * 0.5 * declarativeMemory.getNumGamesPlayed()) + 1.5));
//                double frac = ((double)gamesWonWithStrategy[i] / (double)sumGamesWon);
//                a = (double)((double)alpha * frac);
//                if (i == selectedStrategy) {
//    //                p(t+1, i) = (1-a)p(t, i) + a*(r)
//                    double z = (double)(1-a)*QValue[i];
//                    double y = (double)(a * reward);
//                    QValue[i]= (z + y);
//                    break;
//                } //else {
//    //                QValue[i]= (double) (QValue[i] - (((double)0.3 * reward * (-0.5 * declarativeMemory.getNumGamesPlayed() + 1.5)) / (double)(QValue.length - 1)));
//    //                QValue[i]= (double) (QValue[i] + ((double)0.3 * reward * (-0.5 * declarativeMemory.getNumGamesPlayed() + 1.5)));
//    //            } else {
//    //                QValue[i]= (double) (QValue[i] - (((double)0.3 * reward * (-0.5 * declarativeMemory.getNumGamesPlayed() + 1.5)) / (double)(QValue.length - 1)));
//    //            }        
//            }
//    //        if (declarativeMemory.getNumGamesPlayed() % 10 == 0) {            
//    //            System.out.println(declarativeMemory.getNumGamesPlayed() + " - Probs: " + QValue[0] + ", " + QValue[1] + ", " + QValue[2] + " Sum=" + (QValue[0] + QValue[1] + QValue[2]));
//    //        }
//        }
        
        infoToPreserve.put("QValues", QValue);
        infoToPreserve.put("gamesPlayedWithStrategy", gamesPlayedWithStrategy);
        infoToPreserve.put("gamesWonWithStrategy", gamesWonWithStrategy);
//        if (declarativeMemory.getNumGamesPlayed() % 100 == 0) {
//            System.err.println(getPlayerName() + " - " + infoToPreserve);
//        }
        
        return infoToPreserve;
    }
    
    public void showLearningResults() {
    	if ( isRobotType(StacRobotType.MDP_LEARNING_NEGOTIATOR) )
    		negotiator.mdp_negotiator.computeStatistics();
    }
    
    /**
     * Print a string with our player ID to stderr.
     * @param mes string to be printed
     */
    protected void printMess(String mes) {
        int ourPlayerNumber = getPlayerNumber();
        String ourPlayerName = getPlayerName();
        System.err.println(getGame().getName() + "(" + getGame().getTurnCount() + ") - " + ourPlayerName + "(" + ourPlayerNumber + ") - " + mes);
    }

    /**
     * @return true if it's this robot's turn, false otherwise
     */
    protected boolean isOurTurn() {
        return (getGame().getCurrentPlayerNumber() == getPlayerNumber());
    }

    /**
     * Method for initiating a save by this robot. Should be called in the main run loop, only when this robot is the current player.
     * Use {@link StacRobotBrain#saved} flag in SOCRobotBrain to control the number of saves and avoid overwriting files in the saves/robot folder.
     * Example of where and how to call this method can be found in commented out blocks of code inside the run loop, just add conditions.
     */
    @Override
    public void saveGame(){
    	SOCRobotClient cl = getClient();
    	//send the request 
    	cl.put(SOCGameCopy.toCmd(getGame().getName(), "robot", getPlayerNumber()));
    	//create necessary directories
    	File dir = new File("saves/robot");
    	if(!dir.exists())
    		dir.mkdirs();
    	//execute the saving procedure for this robot
    	cl.unsynckedGameCopy(new SOCGameCopy(getGame().getName(), "saves/robot", -1));
    	
    	//check that all save procedures have been finished by checking that the files containing SOCGame exist as these are the last to be created 
    	String name = "soc.game.SOCGame.dat";
    	String prefix = "saves/robot/";
    	File file;
    	boolean finished = false;
    	while(!finished){
    		finished = true;
    		for(int i = 0; i < 4; i++){
    			file = new File(prefix + i + "_" + name);
    			if(!file.exists()){
    				finished = false; //need to loop for a little while longer
    			}
    		}
        	try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
			}
    	}
    	
    }

    /**
     * @return the vector representation approximating the state
     */
    public int[] createStateVector(){
    	FVGenerator generator = FVGeneratorFactory.getGenerator();
    	ObsGameStateRow ogsr = getGame().turnCurrentStateIntoOGSR();
    	ExtGameStateRow egsr = new ExtGameStateRow(0, "");//don't care about id and name here
    	int[][] rssTypeNNumber = new int[4][5];
        calculateAllRssTypeNNumber(rssTypeNNumber);
        int[] distanceToPort = new int[4];
        int[] distanceToNextLegalLoc = new int[4];
        int[] distanceToOpp = new int[4];
        calculateAllDistances(distanceToPort, distanceToOpp, distanceToNextLegalLoc);
        int[] longestRoads = calculateAllLongestRoads();
        egsr.setRssTypeAndNumber(StacDBHelper.transformToIntegerArr2(rssTypeNNumber));
        egsr.setDistanceToPort(StacDBHelper.transformToIntegerArr(distanceToPort));
        egsr.setDistanceToOpponents(StacDBHelper.transformToIntegerArr(distanceToOpp));
        egsr.setDistanceToNextLegalLoc(StacDBHelper.transformToIntegerArr(distanceToNextLegalLoc));
        egsr.setLongestRoads(StacDBHelper.transformToIntegerArr(longestRoads));
    	return generator.calculateStateVectorJS(ogsr, egsr);
    }
    
    /**
     * The value of the state as a sum of the ETW and the ETA of the best build plan for this player
     * @return
     */
    public double calculateStateValue(){
    	SOCBuildPlanStack plan = decisionMaker.planInMemory();
    	ourPlayerTracker.recalcWinGameETA();
    	double value = ourPlayerTracker.getWinGameETA();
    	if(!plan.empty()){
    		value = value + plan.get(0).getETA();
    	}
    	//we want higher is better, JS has lower is better
    	if(value > 200)
    		value = 200;
    	value = 200 - value;
    	return value;
    }
    
    
    @Override
	public void clearTradingFlags(String txt) {
    	//clear this as we are waiting for response not for the message that tells us if the trade went through
    	waitingForTradeMsg = false;
    	SOCTradeOffer failedoffer = declarativeMemory.getMyNegotiatedOffer();
    	//if it is our turn and a player tried to exploit the trade confirmation limitation or didn't realise it doesn't have enough
		//resources, resend my negotiated offer to everyone excluding the player 
    	if((txt.startsWith(SOCServer.MSG_ILLEGAL_TRADE) || txt.startsWith(SOCServer.MSG_REJECTED_TRADE_CONFIRMATION)) && isOurTurn() 
                && failedoffer != null){
    		
            declarativeMemory.stopWaiting();
            boolean[] to = declarativeMemory.getMyNegotiatedOffer().getTo().clone();
            //who was the last to make this offer
            int oldCorrespondent = -1;
            if(failedoffer.getFrom() == getPlayerNumber()){
                for(int i = 0; i < declarativeMemory.getGame().maxPlayers; i++){
                    if(to[i] && i!= getPlayerNumber()){
                        oldCorrespondent = i;
                    }
                }
            }else{
                oldCorrespondent = failedoffer.getFrom();
            }
            //decide who to send the new offer to
            Arrays.fill(to, false);
            for(int i = 0; i < declarativeMemory.getGame().maxPlayers; i++){
                if(i != getPlayerNumber() && i != oldCorrespondent){
                    to[i] = true;
                    dialogueManager.setPlayerResponse(i, null);
                    declarativeMemory.wait(i, StacTradeMessage.TRADE);	
                    if(!declarativeMemory.getGame().getPlayer(i).isRobot()){
                        declarativeMemory.waitForHumanPlayer(i, StacTradeMessage.TRADE);
                    }
                }
            }

            //TODO: use the last offer we made, not the negotiated offer;
            //use the last offer and just replace the to-field
            StacTradeOffer newOffer = new StacTradeOffer(declarativeMemory.getGame().getName(), getPlayerNumber(), to, 
    				declarativeMemory.getMyNegotiatedOffer().getGiveSet(), false, declarativeMemory.getMyNegotiatedOffer().getGetSet(), false);

            dialogueManager.setMyOffer(newOffer);
            // Consider this offer made right away, don't wait for rejections.
            PersuasionStacRobotNegotiator neg = getNegotiator();
            neg.addToOffersMade(newOffer);
            getPlayerData().setCurrentOffer(newOffer);        
            //we need to negotiate it again
            declarativeMemory.resetMyNegotiatedOffer();
    		
            //create persuasion argument and nltext
            Persuasion pers = new Persuasion();
            String nick = getPlayerName();
            String[] playerNames = declarativeMemory.getGame().getPlayerNames();
            String offMsgText = dialogueManager.tradeMessageOffToString(nick, playerNames, newOffer, pers, "");
            
    		//create the actual trade message
            String sender = Integer.toString(getPlayerNumber());
            String receivers = StacTradeMessage.getToAsString(to);
    		StacTradeMessage tm = new StacTradeMessage(sender, receivers, newOffer, false, pers , offMsgText);
            String result = tm.toMessage();
            
            //don't forget to send it
            sendText(result);
            
    	}else{
    		declarativeMemory.stopWaiting();
    		declarativeMemory.resetMyNegotiatedOffer();
    	}
    		
	}
    
}
