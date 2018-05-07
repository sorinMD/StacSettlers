package soc.robot.stac;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import mcts.MCTS;
import mcts.MCTSConfig;
import mcts.game.Game;
import mcts.game.GameFactory;
import mcts.game.catan.Board;
import mcts.game.catan.Catan;
import mcts.game.catan.CatanConfig;
import mcts.game.catan.GameStateConstants;
import mcts.game.catan.HexTypeConstants;
import mcts.game.catan.ResourceSet;
import mcts.game.catan.belief.PlayerResourceModel;
import mcts.game.catan.typepdf.HumanActionTypePdf;
import mcts.game.catan.belief.Action;
import mcts.game.catan.belief.CatanFactoredBelief;
import mcts.listeners.SearchListener;
import mcts.seeder.nn.NNCatanSeedTrigger;
import mcts.seeder.pdf.CatanTypePDFSeedTrigger;
import mcts.tree.selection.PUCT;
import mcts.tree.selection.RAVE;
import mcts.tree.selection.UCTAction;
import mcts.tree.update.ActionUpdater;
import mcts.tree.update.StateUpdater;
import mcts.utils.Timer;
import soc.debug.D;
import soc.dialogue.StacTradeMessage;
import soc.game.SOCBoard;
import soc.game.SOCCity;
import soc.game.SOCDevCardConstants;
import soc.game.SOCDevCardSet;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCRoad;
import soc.game.SOCSettlement;
import soc.game.SOCTradeOffer;
import soc.game.StacTradeOffer;
import soc.message.SOCDevCard;
import soc.message.SOCGameState;
import soc.message.SOCGameTextMsg;
import soc.message.SOCPlayerElement;
import soc.message.SOCPutPiece;
import soc.message.SOCRobotFlag;
import soc.robot.SOCBuildPlanStack;
import soc.robot.SOCPlayerTracker;
import soc.robot.SOCPossibleCard;
import soc.robot.SOCPossibleCity;
import soc.robot.SOCPossiblePiece;
import soc.robot.SOCPossibleRoad;
import soc.robot.SOCPossibleSettlement;
import soc.robot.SOCRobotClient;
import soc.util.CappedQueue;
import soc.util.SOCRobotParameters;

public class MCTSRobotBrain extends StacRobotBrain implements GameStateConstants,HexTypeConstants{
	/**
	 * The number offers per turn. Default set to unlimited.
	 */
	private int N_MAX_OFFERS = Integer.MAX_VALUE;
	/**
	 * Number of offers made this turn
	 */
	private int offers_made = 0;
	private MCTS mcts;
	private GameFactory gameFactory;
    /**
     * location of the last initial placed settlement, required for planning the free initial roads
     */
    public int lastSettlement;
    /**
     * used in planning from whom to steal
     */
	private int robberVictim = -1;
    /**
     * used in planning where to put our first settlement
     */
    private int firstSettlement = -1;
    /**
     * used in planning where to put our second settlement
     */
    private int secondSettlement = -1;
    /**
     * used in remembering where to place the robber after planning knight card
     */
    private int robberHexFromKnight = -1;
	/**
	 * The list of trades that have a higher value than any actions that can be executed with the available resources
	 */
	private LinkedList<MCTSAction> acceptableTrades = new LinkedList<MCTSAction>();
    /**
     * The factored belief model tracking resources and dev cards.
     * This model is only used with the MCTS agents.
     */
    private CatanFactoredBelief beliefModel;
	/**
	 * Indicating we are currently in a monopoly phase such that the belief update is correct
	 */
	boolean monopolyPhase = false;
    
//	////// some static fields for storing information about the imperfect information games /////
//	////// Note: there should be a single brain of this type in order for this to work fine
//	static int nDecisions = 0;
//	static int nDecisionsWhenAtLeastOneHandUnk = 0;
//	static int nDecisionsWhenNextPlayerHandUnk = 0;
//	static ArrayList<Integer> nPlayersWithUnkHand = new ArrayList<>();
//	static double maxPlayerEntropy = 0.0;
//	static double minPlayerEntropy = Double.MAX_VALUE;
//	static double maxAvgEntropy = 0.0;
//	static double minAvgEntropy = Double.MAX_VALUE;
//	static ArrayList<Double> nHands = new ArrayList<>();
//	static int maxHands = 0;
//	static ArrayList<Double> playerEntropies = new ArrayList<>();
//	static ArrayList<Double> avgEntropies = new ArrayList<>();
//	
//	////saving flag info///
//	static int nDecisionsGame = 0;
//	static int nDecisionsWhenSave = 0;
	
	public MCTSRobotBrain(SOCRobotClient rc, SOCRobotParameters params, SOCGame ga, CappedQueue mq, boolean fullPlan, StacRobotType robotType, HashMap<String,ArrayList<String>> tradePreferences ) {
        super(rc, params, ga, mq, fullPlan, robotType, tradePreferences);
        if (robotType.isType(MCTSRobotType.MCTS_FACTORED_BELIEF)) {
            this.beliefModel = new CatanFactoredBelief(ga.maxPlayers);
        }
        
        //set the params in the correct containers that will be passed to the mcts implementation
        MCTSConfig mctsConfig = new MCTSConfig();
        if(isRobotType(MCTSRobotType.MCTS_PUCT)){
        	mctsConfig.selectionPolicy = new PUCT();
        }else if(isRobotType(MCTSRobotType.MCTS_UCT_RAVE)){
        	mctsConfig.selectionPolicy = new RAVE((int) robotType.getTypeParam(MCTSRobotType.MCTS_UCT_RAVE));
        }
    	boolean expectedRet = isRobotType(MCTSRobotType.MCTS_EXPECTED_RETURN);
    	boolean everyVisit = isRobotType(MCTSRobotType.MCTS_EVERY_VISIT);
    	mctsConfig.updatePolicy = new StateUpdater(expectedRet,everyVisit);
    	if(isRobotType(MCTSRobotType.MCTS_NO_AFTERSTATES)){
    		mctsConfig.afterstates = false;
    		mctsConfig.selectionPolicy = new UCTAction();
    		mctsConfig.updatePolicy = new ActionUpdater(expectedRet,everyVisit);
    	}
    	mctsConfig.selectionPolicy.ismcts = isRobotType(MCTSRobotType.MCTS_ISMCTS);
    	
        if(isRobotType(MCTSRobotType.MCTS_MINVISITS)){
        	mctsConfig.selectionPolicy.MINVISITS = (int) robotType.getTypeParam(MCTSRobotType.MCTS_MINVISITS);
        }
        if(isRobotType(MCTSRobotType.MCTS_Cp)){
        	mctsConfig.selectionPolicy.C0 = (double) robotType.getTypeParam(MCTSRobotType.MCTS_Cp);
        }
        mctsConfig.selectionPolicy.weightedSelection = (isRobotType(MCTSRobotType.MCTS_UNWEIGHTED_SELECTION)) ? false : true;
        
        if(isRobotType(MCTSRobotType.MCTS_MAX_TREE_SIZE)){
        	mctsConfig.treeSize = (int) robotType.getTypeParam(MCTSRobotType.MCTS_MAX_TREE_SIZE);
        }
        if(isRobotType(MCTSRobotType.MCTS_MAX_TREE_DEPTH)){
        	mctsConfig.maxTreeDepth = (int) robotType.getTypeParam(MCTSRobotType.MCTS_MAX_TREE_DEPTH);
        }
        if(isRobotType(MCTSRobotType.MCTS_ITERATIONS)){
        	mctsConfig.nIterations = (int) robotType.getTypeParam(MCTSRobotType.MCTS_ITERATIONS);
        }
        if(isRobotType(MCTSRobotType.MCTS_THREADS)){
        	mctsConfig.nThreads = (int) robotType.getTypeParam(MCTSRobotType.MCTS_THREADS);
        }
        if(isRobotType(MCTSRobotType.MCTS_TIME_LIMIT_MS)){
        	mctsConfig.timeLimit = (int) robotType.getTypeParam(MCTSRobotType.MCTS_TIME_LIMIT_MS);
        }
        if(isRobotType(MCTSRobotType.MCTS_NN_SEEDING)){
        	mctsConfig.trigger = new NNCatanSeedTrigger();
        	if(isRobotType(MCTSRobotType.MCTS_NN_SEEDING_BATCH_SIZE)){
        		((NNCatanSeedTrigger)mctsConfig.trigger).batchSize = (int) robotType.getTypeParam(MCTSRobotType.MCTS_NN_SEEDING_BATCH_SIZE);
        	}
        	if(isRobotType(MCTSRobotType.MCTS_NN_SEED_MASS)){
        		((NNCatanSeedTrigger)mctsConfig.trigger).seedMass = (double) robotType.getTypeParam(MCTSRobotType.MCTS_NN_SEED_MASS);
        	}
        	if(isRobotType(MCTSRobotType.MCTS_NN_SEED_PERCENTAGE)){
        		((NNCatanSeedTrigger)mctsConfig.trigger).seedPercentage = (double) robotType.getTypeParam(MCTSRobotType.MCTS_NN_SEED_PERCENTAGE);
        	}
        	if(isRobotType(MCTSRobotType.MCTS_NN_SOFTMAX_TEMPERATURE)){
        		((NNCatanSeedTrigger)mctsConfig.trigger).temperature = (double) robotType.getTypeParam(MCTSRobotType.MCTS_NN_SOFTMAX_TEMPERATURE);
        	}
        	if(isRobotType(MCTSRobotType.MCTS_NN_DATA_TYPE)){
        		((NNCatanSeedTrigger)mctsConfig.trigger).data_type = (String) robotType.getTypeParam(MCTSRobotType.MCTS_NN_DATA_TYPE);
        	}
        	if(isRobotType(MCTSRobotType.MCTS_NN_MODEL_TYPE)){
        		((NNCatanSeedTrigger)mctsConfig.trigger).model_type = (String) robotType.getTypeParam(MCTSRobotType.MCTS_NN_MODEL_TYPE);
        	} 
        	if(isRobotType(MCTSRobotType.MCTS_NN_N_SAMPLES)){
        		((NNCatanSeedTrigger)mctsConfig.trigger).nSamples = (int) robotType.getTypeParam(MCTSRobotType.MCTS_NN_N_SAMPLES);
        	}
        	if(isRobotType(MCTSRobotType.MCTS_NN_LAPLACE_ALPHA)){
        		((NNCatanSeedTrigger)mctsConfig.trigger).laplace_alpha = (double) robotType.getTypeParam(MCTSRobotType.MCTS_NN_LAPLACE_ALPHA);
        	}
        	((NNCatanSeedTrigger)mctsConfig.trigger).maskInputForOpponent = isRobotType(MCTSRobotType.MCTS_NN_MASK_INPUT);
        	
        	if(isRobotType(MCTSRobotType.MCTS_NN_MIXTURE_LAMBDA))
        		((NNCatanSeedTrigger)mctsConfig.trigger).lambda = (double) robotType.getTypeParam(MCTSRobotType.MCTS_NN_MIXTURE_LAMBDA);
        	if(isRobotType(MCTSRobotType.MCTS_NN_HUMAN_ACTION_TYPE_PDF_MIXTURE)) {
        		((NNCatanSeedTrigger)mctsConfig.trigger).pdf = HumanActionTypePdf.readFromFile();
        		if(isRobotType(MCTSRobotType.MCTS_NN_HUMAN_ACTION_TYPE_PDF_MIXTURE_TEMPERATURE))
        			((HumanActionTypePdf)((NNCatanSeedTrigger)mctsConfig.trigger).pdf).temperature = (double) robotType.getTypeParam(MCTSRobotType.MCTS_NN_HUMAN_ACTION_TYPE_PDF_MIXTURE_TEMPERATURE);
        	}else if(isRobotType(MCTSRobotType.MCTS_NN_HUMAN_ACTION_TYPE_PDF_CONDITIONED_MIXTURE)) {
        		((NNCatanSeedTrigger)mctsConfig.trigger).pdf = HumanActionTypePdf.readFromFile();
        		((HumanActionTypePdf)((NNCatanSeedTrigger)mctsConfig.trigger).pdf).conditionOnLegalTypes = true;
        		if(isRobotType(MCTSRobotType.MCTS_NN_HUMAN_ACTION_TYPE_PDF_MIXTURE_TEMPERATURE))
        			((HumanActionTypePdf)((NNCatanSeedTrigger)mctsConfig.trigger).pdf).temperature = (double) robotType.getTypeParam(MCTSRobotType.MCTS_NN_HUMAN_ACTION_TYPE_PDF_MIXTURE_TEMPERATURE);
        	}
        	
        } else if(isRobotType(MCTSRobotType.MCTS_HUMAN_ACTION_TYPE_PDF_SEEDING))  {
        	mctsConfig.trigger = new CatanTypePDFSeedTrigger();
        	((CatanTypePDFSeedTrigger)mctsConfig.trigger).pdf = HumanActionTypePdf.readFromFile();
        	if(isRobotType(MCTSRobotType.MCTS_HUMAN_ACTION_TYPE_PDF_SEEDING_TEMPERATURE))
        		((HumanActionTypePdf)((CatanTypePDFSeedTrigger)mctsConfig.trigger).pdf).temperature = (double) robotType.getTypeParam(MCTSRobotType.MCTS_HUMAN_ACTION_TYPE_PDF_SEEDING_TEMPERATURE);
        }  else if(isRobotType(MCTSRobotType.MCTS_HUMAN_ACTION_TYPE_PDF_CONDITIONED_SEEDING))  {
        	mctsConfig.trigger = new CatanTypePDFSeedTrigger();
        	((CatanTypePDFSeedTrigger)mctsConfig.trigger).pdf = HumanActionTypePdf.readFromFile();
        	((HumanActionTypePdf)((CatanTypePDFSeedTrigger)mctsConfig.trigger).pdf).conditionOnLegalTypes = true;
        	if(isRobotType(MCTSRobotType.MCTS_HUMAN_ACTION_TYPE_PDF_SEEDING_TEMPERATURE))
        		((HumanActionTypePdf)((CatanTypePDFSeedTrigger)mctsConfig.trigger).pdf).temperature = (double) robotType.getTypeParam(MCTSRobotType.MCTS_HUMAN_ACTION_TYPE_PDF_SEEDING_TEMPERATURE);
        }     
    	if(isRobotType(MCTSRobotType.MCTS_ACTIVE_SEEDERS)){
    		mctsConfig.trigger.maxSeederCount = (int) robotType.getTypeParam(MCTSRobotType.MCTS_ACTIVE_SEEDERS);
    	}
    	
        mctsConfig.observableRollouts = isRobotType(MCTSRobotType.MCTS_OBSERVABLE_ROLLOUTS);
        if(isRobotType(MCTSRobotType.MCTS_NROLLOUTS_PER_ITERATION)){
        	mctsConfig.nRolloutsPerIteration = (int) robotType.getTypeParam(MCTSRobotType.MCTS_NROLLOUTS_PER_ITERATION);
        }
        mctsConfig.averageRolloutsResults = isRobotType(MCTSRobotType.MCTS_AVERAGE_ROLLOUTS_RESULTS);
        mctsConfig.pomcp = isRobotType(MCTSRobotType.MCTS_POMCP);
        mctsConfig.weightedReturn = isRobotType(MCTSRobotType.MCTS_WEIGHTED_RETURN);
         
        if(isRobotType(MCTSRobotType.MCTS_N_ROOT_SMOOTHING_ACTION_PROBS)) {
        	mctsConfig.nRootActProbSmoothing = (int) robotType.getTypeParam(MCTSRobotType.MCTS_N_ROOT_SMOOTHING_ACTION_PROBS);
        }
        
        if(isRobotType(MCTSRobotType.MCTS_N_ROOT_SMOOTHING_STATE_PROB)) {
        	mctsConfig.nRootStateProbSmoothing = (int) robotType.getTypeParam(MCTSRobotType.MCTS_N_ROOT_SMOOTHING_STATE_PROB);
        }
        
        //also create a game config from the stac parameters NOTE: two of these are set differently in the mcts package than in the StacSettlers one, i.e. trades and negotiations.
        CatanConfig gameConfig = new CatanConfig();
        gameConfig.TRADES = (isRobotType(StacRobotType.NO_TRADES)) ? false : true;
        gameConfig.SAMPLE_FROM_DISTRIBUTION_OVER_TYPES_IN_ROLLOUTS = isRobotType(MCTSRobotType.MCTS_TYPED_ROLLOUTS);
        gameConfig.NEGOTIATIONS = (isRobotType(MCTSRobotType.MCTS_NO_NEGOTIATIONS)) ? false : true;
        gameConfig.ALLOW_COUNTEROFFERS = isRobotType(MCTSRobotType.MCTS_ALLOW_COUNTEROFFERS_IN_TREE);
        gameConfig.ABSTRACT_BELIEF = isRobotType(MCTSRobotType.MCTS_ABSTRACT_BELIEF);
        gameConfig.UNIFORM_BELIEF_CHANCE_EVENTS = isRobotType(MCTSRobotType.MCTS_UNIFORM_BELIEF_CHANCE_EVENTS);
        gameConfig.OBS_DISCARDS_IN_ROLLOUTS = isRobotType(MCTSRobotType.MCTS_OBS_DISCARDS_IN_ROLLOUTS);
        gameConfig.LIST_POMS = isRobotType(MCTSRobotType.MCTS_LIST_POMS);
        gameConfig.OBSERVABLE_POM_EFFECTS = isRobotType(MCTSRobotType.MCTS_OBSERVABLE_POM_EFFECTS);
        gameConfig.ENFORCE_UNIFORM_TYPE_DIST_IN_BELIEF_ROLLOUTS = isRobotType(MCTSRobotType.MCTS_ENFORCE_UNIFORM_TYPE_DIST_IN_BELIEF);
        gameConfig.OBSERVABLE_VPS = isRobotType(MCTSRobotType.MCTS_OBSERVABLE_VP);
        
        if(isRobotType(MCTSRobotType.MCTS_N_MAX_RSS_STEAL)){
        	gameConfig.N_MAX_RSS_STEAL = (int) robotType.getTypeParam(MCTSRobotType.MCTS_N_MAX_RSS_STEAL);
        }
        
        if(isRobotType(MCTSRobotType.MCTS_MAX_N_DISCARDS)){
        	gameConfig.N_MAX_DISCARD = (int) robotType.getTypeParam(MCTSRobotType.MCTS_MAX_N_DISCARDS);
        }

        if(isRobotType(MCTSRobotType.MCTS_OFFERS_LIMIT)){
        	gameConfig.OFFERS_LIMIT = (int) robotType.getTypeParam(MCTSRobotType.MCTS_OFFERS_LIMIT);
        	N_MAX_OFFERS = (int) robotType.getTypeParam(MCTSRobotType.MCTS_OFFERS_LIMIT);
        }
        if(isRobotType(MCTSRobotType.MCTS_HUMAN_ACTION_TYPE_DIST_ROLLOUTS)){
        	gameConfig.rolloutTypeDist = HumanActionTypePdf.readFromFile();
        	if(isRobotType(MCTSRobotType.MCTS_HUMAN_ACTION_TYPE_DIST_ROLLOUTS_TEMPERATURE))
        		((HumanActionTypePdf)gameConfig.rolloutTypeDist).temperature = (double) robotType.getTypeParam(MCTSRobotType.MCTS_HUMAN_ACTION_TYPE_DIST_ROLLOUTS_TEMPERATURE);
        }
        if(isRobotType(MCTSRobotType.MCTS_HUMAN_ACTION_TYPE_CONDITIONED_DIST_ROLLOUTS)) {
        	gameConfig.rolloutTypeDist = HumanActionTypePdf.readFromFile();
        	((HumanActionTypePdf)gameConfig.rolloutTypeDist).conditionOnLegalTypes = true;
        	if(isRobotType(MCTSRobotType.MCTS_HUMAN_ACTION_TYPE_DIST_ROLLOUTS_TEMPERATURE))
        		((HumanActionTypePdf)gameConfig.rolloutTypeDist).temperature = (double) robotType.getTypeParam(MCTSRobotType.MCTS_HUMAN_ACTION_TYPE_DIST_ROLLOUTS_TEMPERATURE);
        }
        
        //use the same belief model
        gameFactory = new GameFactory(gameConfig, this.beliefModel);
        mcts = new MCTS(mctsConfig, gameFactory);
        
        try {    
	        ObjectMapper mapper = new ObjectMapper();	
	        mapper.enable(SerializationFeature.INDENT_OUTPUT);
	        mapper.writeValue(new File("mcts.json"), mctsConfig);
	        mapper.writeValue(new File("game.json"), gameConfig);
	    } 
	    catch (JsonParseException e) { e.printStackTrace(); }
	    catch (JsonMappingException e) { e.printStackTrace(); }
	    catch (IOException e) { e.printStackTrace(); }
	}
	
	////////////////////////////METHODS FOR SAVE/LOAD UTILITY/////////////////////
	
	/**
	 * required for loading a game with a missing belief model.
	 */
	public void reinitBeliefModel() {
		
		if (!robotType.isType(MCTSRobotType.MCTS_FACTORED_BELIEF))
			return;

        SOCResourceSet[] sets = new SOCResourceSet[getGame().maxPlayers];
        for(int i= 0; i < getGame().maxPlayers; i++){
        	SOCPlayer pl = getGame().getPlayer(i);
        	if(pl != null){
        		sets[i] = pl.getResources();
        	}else{
        		sets[i] = new SOCResourceSet();
        	}
        }
        
        //computes all the possible resource hands of the opponents from the SOCResourceSet
        HashMap<ResourceSet,Double>[] possHands = new HashMap[sets.length];
        for(int i = 0; i < sets.length; i++){
			//for each set passed create the list of possible worlds
			//then update the player hand models
			possHands[i] = new HashMap<ResourceSet, Double>();
			if(sets[i].getAmount(SOCResourceConstants.UNKNOWN) == 0){
				possHands[i].put(socToSSRssSet(sets[i]), 1.0);
			}else{
				int nUnknowns = sets[i].getAmount(SOCResourceConstants.UNKNOWN);				
				ResourceSet unkSet = new ResourceSet(); //the unknown part of the hand
				for (int j = 0; j < ResourceSet.NRESOURCES; j++){
					unkSet.add(nUnknowns, j); //assume the unknowns can be any rss type as JSettlers does
				}
				sets[i].setAmount(0, SOCResourceConstants.UNKNOWN);
				ResourceSet kSet = socToSSRssSet(sets[i]); //the known part of the hand
				List<ResourceSet> unkHands = unkSet.getSubsets(nUnknowns, true); //get all possible combinations
				//to each unknown hand add the known part to create a complete hand
				List<ResourceSet> hands = new ArrayList<ResourceSet>();
				for(ResourceSet s : unkHands){
					s.add(kSet); 
					hands.add(s);
				}
				for(ResourceSet s : hands){
					if(possHands[i].containsKey(s))
						possHands[i].put(s, possHands[i].get(s).doubleValue() + 1.0/hands.size());
					else
						possHands[i].put(s, 1.0/hands.size());
				}
			}
		}
                
        beliefModel = new CatanFactoredBelief(getGame().maxPlayers, possHands);
        //now update the dev cards;
        int[] playedDevCards = getGame().getPlayedDevCardAmount();
        int totalPlayed = playedDevCards[0] + playedDevCards[1] + playedDevCards[2] + playedDevCards[3] + playedDevCards[4];
        int totalUnplayed;
        if(getGame().maxPlayers == 4)
        	totalUnplayed = 25 - totalPlayed;
        else
        	totalUnplayed = 34 - totalPlayed;
        beliefModel.reinitialiseDevCardChances(totalUnplayed, playedDevCards[0], playedDevCards[1], playedDevCards[2], playedDevCards[3], playedDevCards[4]);

	}
	
	public CatanFactoredBelief getBelief() {
		return beliefModel.copy();
	}
	
	public void setBelief(CatanFactoredBelief belief) {
		beliefModel = belief;
		GameFactory factory = new GameFactory(mcts.getGameFactory().getConfig(), this.beliefModel);
		mcts.setGameFactory(factory);
		gameFactory = factory;
	}
	
	////////////////////////////SOC MODIFIED METHODS/////////////////////
	/**
	 * We never make offers via the old trade interface or even via the normal route
	 */
	protected boolean makeOffer(SOCBuildPlanStack buildPlan) {
		return false;
	}
	
	public void clearTradingFlags(String txt) {
		getMemory().setMyOffer(null);
		super.clearTradingFlags(txt);
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
    protected void handleChat(SOCGameTextMsg gtm) {
    	/*very rarely the robots start chating before their player information is being set during the sitdown action. 
    	Just catch the exception and print out the error in case this starts hapenning more often*/
    	try {
    		List<String> msgs = new ArrayList<String>();
    		if (gtm.getText().startsWith(StacTradeMessage.TRADE)) {
    	        String convertedMessage = dialogueManager.fromMessage(gtm.getText());
    	        StacTradeMessage tm = StacTradeMessage.parse(convertedMessage);
    	        StacTradeOffer off = tm.getOffer();
    	        
    	        //make sure to still keep track of all the symbolic belief model stuff
    	        if (isRobotType(MCTSRobotType.MCTS_FACTORED_BELIEF)) {
    	        	if (off != null) {
    		            //check if player's offer things they do not have and check otherwise
    		            SOCResourceSet s = rsToSocRssSet(beliefModel.getMaxAmountForEachType(off.getFrom()));
    		            if(!s.contains(off.getGiveSet()))
    		            	D.ebugWARNING(" player: " + off.getFrom() + " has offered " + off.getGiveSet().toShortString() + " and could have a maximum of " + s.toShortString());
    		            else{
    		            	//full offers always announce owning a specific resource so update the belief model...this is false now, but leave the code for reference
//    		            	if(off.getGiveSet() != null && off.getGiveSet().getTotal() > 0){
//    		            		//now check the possible worlds against this information; NOTE:set==check
//    		            		brain.getMemory().beliefModel.updateResurceBelief(off.getGiveSet(), off.getFrom(), SOCPlayerElement.SET);
//    		            	}
    		            }
    	        	}
    	        }
    	        
    	        if(tm.isNoResponse()){
					/*
					 * ignore no responses, we are not keeping track of them at
					 * all and it will only make the below logic harder to read
					 */
        			return;
        		}
    	        if (tm.getReceivers().contains(Integer.toString(getPlayerNumber()))) {
    	        	//for now, this brain only handles 1 player to 1 player offers
    	        	if(getMemory().getMyOffer()!= null && getPlayerNumber() == game.getCurrentPlayerNumber()){
    	        		if(tm.isAccept()){
    	        			//accept it again so the trade will go through
    	        			msgs.add(getDialogueManager().makeAcceptMessageWithExplicitAddressee(true, tm.getSenderInt()));
    	                    setWaitingForTradeMsg();
    	                    getMemory().setMyNegotiatedOffer(getMemory().getMyOffer());
    	                    acceptableTrades.clear(); //clear the acceptable trades
    	        		}else if(off!=null){
    	        			//turn it into the lightweight Catan format
    	        			int[] act = new int[ACTIONSIZE];
    	        			Arrays.fill(act, -1);
    	        			if(((CatanConfig) gameFactory.getConfig()).NEGOTIATIONS){
    	        				act[0] = A_OFFER;
    	        			}else{
    	        				act[0] = A_TRADE;
    	        			}
    	        			act[1] = off.getFrom();
    	        			SOCPlayer player = getPlayerData();
    	        			//if partial, disjunctive or over what we can do, counter with our next option or reject
							if (off.isPartialOffer() || off.hasDisjunctiveGetSet() || off.hasDisjunctiveGiveSet()
									|| off.get.getTotal() > 2 || off.give.getTotal() > 2
									|| (!player.getResources().contains(off.getGetSet()))) {
    	        				//clear the waiting flags first
	    	        			waitingForTradeResponse = false;
	    	    	        	clearTradingFlags("");
	    	    	        	getMemory().setMyOffer(null);
    	        				if(!sendNewOffer()){
    	        					//if no more good trades possible just reject and do a different action when asked to plan for play1
        	    	        		msgs.add(dialogueManager.makeRejectMessage(tm.getSenderInt()));
    	        				}
    	        			}else{
    	        				//turn it to ss format and check if it is among our acceptable trades
        	        			SOCResourceSet give = off.getGiveSet();
        	        			SOCResourceSet get = off.getGetSet();
    	        				// reverse the offer so it will be from our perspective, i.e. what the opponent gets is what this player gives give
        	        			int index = 3;
        	        			for(int i = 0 ; i < SOCResourceConstants.MAXPLUSONE; i++){
        	        				if(get.getAmount(i) > 0){
        	        					act[index] = get.getAmount(i);
        	        					act[index + 1] = translateResToSmartSettlers(i);
        	        					index+=2;
        	        				}
        	        			}
        	        			index = 7;
        	        			for(int i = 0 ; i < SOCResourceConstants.MAXPLUSONE; i++){
        	        				if(give.getAmount(i) > 0){
        	        					act[index] = give.getAmount(i);
        	        					act[index + 1] = translateResToSmartSettlers(i);
        	        					index+=2;
        	        				}
        	        			}
        	        			act[2] = off.getFrom();
        	        			act[1] = getPlayerNumber();
        	        			
        	        			if(acceptableTrades.contains(new MCTSAction(act))){
            	        			msgs.add(getDialogueManager().makeAcceptMessageWithExplicitAddressee(true, tm.getSenderInt()));
            	                    setWaitingForTradeMsg();
            	                    getMemory().setMyNegotiatedOffer(getMemory().getMyOffer());
            	                    acceptableTrades.clear(); //clear the acceptable trades
            	                    waitingForTradeResponse = false;
        	        			}else{
        	        				//clear the waiting flags first
    	    	        			waitingForTradeResponse = false;
    	    	    	        	clearTradingFlags("");
    	    	    	        	getMemory().setMyOffer(null);
        	        				if(!sendNewOffer()){
        	        					//if no more good trades possible just reject and do a different action when asked to plan for play1
            	    	        		msgs.add(dialogueManager.makeRejectMessage(tm.getSenderInt()));
            	    	        	}
        	        			}
        	        			
    	        			}

    	        		}else if(tm.isReject()){
    	        			//clear the waiting flags
    	        			waitingForTradeResponse = false;
    	    	        	clearTradingFlags("");
    	    	        	getMemory().setMyOffer(null);
    	    	        	if(!sendNewOffer()){}
    	        		}
    	        	}else{
    	        		//if this agent doesn't trade, just reject every offer we receive
    	        		if(isRobotType(StacRobotType.NO_TRADES)){
    	        			msgs.add(dialogueManager.makeRejectMessage(tm.getSenderInt()));
    	        		}else{
    	        			SOCPlayer player = getPlayerData();
	    	        		//if partial, disjunctive or anything else that this brain can't handle, reject it (this agent does not compute counter offers, which may be a disadvantage)
							if (off.isPartialOffer() || off.hasDisjunctiveGetSet() || off.hasDisjunctiveGiveSet()
									|| off.get.getTotal() > 2 || off.give.getTotal() > 2
									|| (!player.getResources().contains(off.getGetSet()))) {
								msgs.add(dialogueManager.makeRejectMessage(tm.getSenderInt()));
		        			}else{
	    	        			int[] act = new int[ACTIONSIZE];
	    	        			Arrays.fill(act, -1);
	    	        			act[0] = A_OFFER;
	    	        			act[1] = off.getFrom();
	    	        			act[2] = getPlayerNumber();
	    	        			SOCResourceSet give = off.getGiveSet();
	    	        			SOCResourceSet get = off.getGetSet();
	    	        			int index = 3;
	    	        			for(int i = 0 ; i < SOCResourceConstants.MAXPLUSONE; i++){
	    	        				if(give.getAmount(i) > 0){
	    	        					act[index] = give.getAmount(i);
	    	        					act[index + 1] = translateResToSmartSettlers(i);
	    	        					index+=2;
	    	        				}
	    	        			}
	    	        			index = 7;
	    	        			for(int i = 0 ; i < SOCResourceConstants.MAXPLUSONE; i++){
	    	        				if(get.getAmount(i) > 0){
	    	        					act[index] = get.getAmount(i);
	    	        					act[index + 1] = translateResToSmartSettlers(i);
	    	        					index+=2;
	    	        				}
	    	        			}
	    	        			
		        		        Game g = generateGame(S_NEGOTIATIONS,act);
		        				mcts.newTree(g);
		        				SearchListener listener = mcts.search();
		        				listener.waitForFinish();
		        				int[] action = g.listPossiblities(false).getOptions().get(mcts.getNextActionIndex());
	    	        			
		        				if(action[0]==A_ACCEPT){
		        					getMemory().setMyNegotiatedOffer(tm.getOffer());
			    	        		msgs.add(getDialogueManager().makeAcceptMessageWithExplicitAddressee(true, tm.getSenderInt()));
		        					
		        				}else{
		        					//reject only for now, TODO: add counter-offers in the future
		        					msgs.add(dialogueManager.makeRejectMessage(tm.getSenderInt()));
		        				}
		        			}
    	        		}
    	        	}
    	        	
    	        }else{
    	        	//need to send a no response to all other trade messages since this agent is a robot from the opponents' perspective
    	        	msgs.add(dialogueManager.makeNoResponseMessage(tm.getSenderInt()));
    	        }
    		
    		}
            for (String s : msgs) {
                sendText(s);
            }
    	} catch(NullPointerException ne){
            ne.printStackTrace();
    	}
    	
    }
    
    /**
     * Based on the list of acceptable trades it sends the next best offer
     * @return true if an offer was sent, false otherwise.
     */
    private boolean sendNewOffer(){
    	//the last action included is a different action to be executed in the real game, so ignore it
    	if(acceptableTrades.size()>1){
    		int[] action = acceptableTrades.poll().description;
    		if(action[0] != A_TRADE && action[0] != A_OFFER)
    			D.ebugERROR("Player " + getPlayerNumber() + " the next acceptable trade is not a trade action");
    		
        	//create the offer in the JSettlers format
        	SOCResourceSet giveSet = new SOCResourceSet();
        	giveSet.add(action[3], translateResToJSettlers(action[4]));
        	if(action[5] > -1)
        		giveSet.add(action[5], translateResToJSettlers(action[6]));
        	
        	SOCResourceSet getSet = new SOCResourceSet();
        	getSet.add(action[7], translateResToJSettlers(action[8]));
        	if(action[9] > -1)
        		getSet.add(action[9], translateResToJSettlers(action[10]));
        	
        	boolean[] receivers = new boolean[4];
        	receivers[action[2]] = true;
        	
        	StacTradeOffer offer = new StacTradeOffer(game.getName(), getPlayerNumber(), receivers, giveSet, false, getSet, false);
        	//compose message to be sent via the chat (this should set all the required flags)
        	Persuasion persuasiveArgument = new Persuasion();
        	String nlChatString = getDialogueManager().tradeMessageOffToString(getPlayerName(), getGame().getPlayerNames(), offer, persuasiveArgument , "");
            String result = getDialogueManager().makeTradeOffer(offer, "", persuasiveArgument, nlChatString);
        	
            //set one more flag that is required for not coming in here again until the trade is executed
            waitingForTradeResponse = true;
            offers_made++;
            //send it
            sendText(result);
            return true;
    	}
    	
    	return false;
    }
    
    
	protected void rollOrPlayKnightOrExpectDice() {
		expectPLAY = false;
		if ((!waitingForOurTurn) && (ourTurn)) {
			if (!expectPLAY1 && !expectDISCARD && !expectPLACING_ROBBER && !(expectDICERESULT && (counter < 4000))) {
				getActionForPLAY();
			}
		} else {
			/**
			 * not our turn
			 */
			expectDICERESULT = true;
		}
	}
	
	protected void placeIfExpectPlacing() {
		if (waitingForGameState)
			return;

		switch (game.getGameState()) {
		case SOCGame.PLACING_SETTLEMENT: {
			if ((ourTurn) && (!waitingForOurTurn) && (expectPLACING_SETTLEMENT)) {
				expectPLACING_SETTLEMENT = false;
				waitingForGameState = true;
				counter = 0;
				expectPLAY1 = true;

				pause(1);
				client.putPiece(game, whatWeWantToBuild);
				pause(2);
			}
		}
			break;

		case SOCGame.PLACING_ROAD: {
			if ((ourTurn) && (!waitingForOurTurn) && (expectPLACING_ROAD)) {
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

		case SOCGame.PLACING_CITY: {
			if ((ourTurn) && (!waitingForOurTurn) && (expectPLACING_CITY)) {
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

		case SOCGame.PLACING_FREE_ROAD1: {
			if ((ourTurn) && (!waitingForOurTurn) && (expectPLACING_FREE_ROAD1)) {
				expectPLACING_FREE_ROAD1 = false;
				waitingForGameState = true;
				counter = 0;
				expectPLACING_FREE_ROAD2 = true;
				getActionForFREEROAD(SOCGame.PLACING_FREE_ROAD1);
			}
		}
			break;

		case SOCGame.PLACING_FREE_ROAD2: {
			if ((ourTurn) && (!waitingForOurTurn) && (expectPLACING_FREE_ROAD2)) {
				expectPLACING_FREE_ROAD2 = false;
				waitingForGameState = true;
				counter = 0;
				expectPLAY1 = true;
				getActionForFREEROAD(SOCGame.PLACING_FREE_ROAD2);
			}
		}
			break;

		// same as with placing robber: do the action first and then change
		// states for the following cases
		case SOCGame.START1A: {
			if ((!waitingForOurTurn) && (ourTurn) && (!(expectPUTPIECE_FROM_START1A && (counter < 4000)))) {
				placeInitialSettlement(S_SETTLEMENT1);
				expectPUTPIECE_FROM_START1A = true;
				waitingForGameState = true;
				counter = 0;
			}
			expectSTART1A = false;
		}
			break;

		case SOCGame.START1B: {

			if ((!waitingForOurTurn) && (ourTurn) && (!(expectPUTPIECE_FROM_START1B && (counter < 4000)))) {
				placeInitialRoad(S_ROAD1);
				expectPUTPIECE_FROM_START1B = true;
				counter = 0;
				waitingForGameState = true;
				pause(3);
			}
			expectSTART1B = false;
		}
			break;

		case SOCGame.START2A: {
			if ((!waitingForOurTurn) && (ourTurn) && (!(expectPUTPIECE_FROM_START2A && (counter < 4000)))) {
				placeInitialSettlement(S_SETTLEMENT2);
				expectPUTPIECE_FROM_START2A = true;
				counter = 0;
				waitingForGameState = true;
			}
			expectSTART2A = false;
		}
			break;

		case SOCGame.START2B: {
			if ((!waitingForOurTurn) && (ourTurn) && (!(expectPUTPIECE_FROM_START2B && (counter < 4000)))) {
				placeInitialRoad(S_ROAD2);
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
	 * plan and place first settlement also set the starting player and our
	 * player number in the board layout as this is the first action of the game
	 * for this player
	 */
	protected void placeInitialSettlement(int state) {
		//try to avoid replanning if possible
		if ((firstSettlement != -1 && state == S_SETTLEMENT1) || (secondSettlement != -1 && state == S_SETTLEMENT2)) {
			D.ebugERROR("Player " + getPlayerNumber() + "was asked to place initial settlement multiple times in state " + state);
			client.putPiece(game, new SOCSettlement(ourPlayerData, lastSettlement, null));
			return;
		}
		// make the server believe we are a human player so we won't get
		// interrupted by the force end turn thread
		client.put(SOCRobotFlag.toCmd(getGame().getName(), false, getPlayerNumber()));
		Game g = generateGame(state,null);
		mcts.newTree(g);
		Timer t = new Timer();
		SearchListener listener = mcts.search();
    	listener.waitForFinish();
    	int[] action = g.listPossiblities(false).getOptions().get(mcts.getNextActionIndex());
    	
		String s = String.format("Player " + getPlayerNumber() + " chose initial settlement action: [%d %d %d %d %d]", action[0], action[1], action[2],
				action[3], action[4]);
		D.ebugPrintlnINFO(s);
		lastSettlement = translateVertexToJSettlers(action[1]);

		D.ebugPrintlnINFO("Player " + getPlayerNumber() + " BUILD REQUEST FOR FIRST SETTLEMENT AT " + Integer.toHexString(lastSettlement));
		pause(2);
		client.putPiece(game, new SOCSettlement(ourPlayerData, lastSettlement, null));
		pause(1);
		//NOTE: JSettlers contains a bug which may result in a plannign agent being asked twice to place the initial settlement;
		if(state == S_SETTLEMENT1){
			firstSettlement = lastSettlement;
		}else{
			secondSettlement = lastSettlement;
		}
		
		// we are a robot again
		client.put(SOCRobotFlag.toCmd(getGame().getName(), true, getPlayerNumber()));
	}
	
	
	/**
	 * place a road attached to the first initial settlement
	 */
	private void placeInitialRoad(int state) {
		D.ebugPrintINFO("Player " + getPlayerNumber() + " planning initial road in state: " + state);
		// make the server believe we are a human player so we won't get
		// interrupted by the force end turn thread
		client.put(SOCRobotFlag.toCmd(getGame().getName(), false, getPlayerNumber()));
		Game g = generateGame(state,null);
		mcts.newTree(g);
		SearchListener listener = mcts.search();
		listener.waitForFinish();
		int[] action = g.listPossiblities(false).getOptions().get(mcts.getNextActionIndex());
		String s = String.format("Player " + getPlayerNumber() + " chose first road action: [%d %d %d %d %d]", action[0], action[1], action[2], action[3],
				action[4]);
		D.ebugPrintlnINFO(s);
		int road = translateEdgeToJSettlers(action[1]);

		D.ebugPrintlnINFO("Player " + getPlayerNumber() + " PUTTING INIT ROAD at " + Integer.toHexString(road));
		pause(2);
		client.putPiece(game, new SOCRoad(ourPlayerData, road, null));
		lastSettlement = -1;
		pause(1);
		// we are a robot again
		client.put(SOCRobotFlag.toCmd(getGame().getName(), true, getPlayerNumber()));

	}
	
	@Override
	protected void moveRobber() {
		D.ebugPrintINFO("Player " + getPlayerNumber() + " call to moveRobber method");
		
		// if the choice was made when planning to play the knight card
		if (robberHexFromKnight != -1) {
			D.ebugPrintINFO("Player " + getPlayerNumber() + " moving robber after planning with knight card");
			client.moveRobber(game, ourPlayerData, robberHexFromKnight);
			robberHexFromKnight = -1; // reset the flag for any future calls to move robber
			return;
		}
		D.ebugPrintINFO("Player " + getPlayerNumber() + " planning move robber");
		// make the server believe we are a human player so we won't get
		// interrupted by the force end turn thread
		client.put(SOCRobotFlag.toCmd(getGame().getName(), false, getPlayerNumber()));
		boolean illegal = true;
		int[] action = null;
		while (illegal) {
			illegal = false;
			Game g = generateGame(S_ROBBERAT7,null);
			mcts.newTree(g);
			SearchListener listener = mcts.search();
			listener.waitForFinish();
			action = g.listPossiblities(false).getOptions().get(mcts.getNextActionIndex());

			String s = String.format("Player " + getPlayerNumber() + " chose robber action: [%d %d %d %d %d]", action[0], action[1], action[2], action[3],
					action[4]);
			D.ebugPrintlnINFO(s);

			// Check if it is legal; the only illegal actions are if the robber
			// is moved outside of the land or on the same location or no plan
			// was made
			int tempHex = translateHexToJSettlers(action[1], Catan.board);
			if (tempHex == -1) {
				illegal = true;
				D.ebugERROR("Illegal attempt to place the robber - no plan; Player " + getPlayerNumber());
			} else if (tempHex == game.getBoard().getRobberHex()) {
				illegal = true;
				D.ebugERROR("Illegal attempt to place the robber - placing back in the same location; Player"
						+ getPlayerNumber());
			} else if (game.getBoard().getHexTypeFromCoord(tempHex) > SOCBoard.MAX_LAND_HEX) {
				illegal = true;
				D.ebugERROR("Illegal attempt to place the robber - no land hex; Player" + getPlayerNumber());
			}
		}

		int robberHex = translateHexToJSettlers(action[1], Catan.board);
		robberVictim = action[2];
		D.ebugPrintlnINFO("Player " + getPlayerNumber() + " MOVING ROBBER ");
		client.moveRobber(game, ourPlayerData, robberHex);
		int xn = (int) Catan.board.hextiles[action[1]].pos.x;
		int yn = (int) Catan.board.hextiles[action[1]].pos.y;

		D.ebugPrintlnINFO("Player " + getPlayerNumber() + " MOVE robber to hex " + robberHex + "( hex " + action[1] + ", coord: " + xn + "," + yn
				+ "), steal from" + robberVictim);
		pause(2);
		//we are a robot again
		client.put(SOCRobotFlag.toCmd(getGame().getName(), true, getPlayerNumber()));
	}
	
	@Override
	protected void chooseRobberVictim(boolean[] choices) {
		D.ebugPrintINFO("Player " + getPlayerNumber() + " choosing victim " + robberVictim);
		pause(1);
		client.choosePlayer(game, robberVictim);
		pause(1);
	}
	
    @Override
    protected void discard(int numDiscards) {
    	SOCResourceSet discards = new SOCResourceSet();
    	if(numDiscards >= ((CatanConfig) gameFactory.getConfig()).N_MAX_DISCARD){//it would take too long if we have to compute all combinations over this
    		 SOCGame.discardPickRandom(ourPlayerData.getResources(), numDiscards, discards, rand);
    		 D.ebugPrintlnINFO("Player " + getPlayerNumber() + " random discards due to large amount of resources");//I want to see how often this happens
    	}else{
    		//we use MCTS to decide
    		client.put(SOCRobotFlag.toCmd(getGame().getName(), false, getPlayerNumber()));
    		boolean illegal = true;
    		int counter = 0;
    		while(illegal){
        		counter ++;
        		if(counter >5){
        			D.ebugERROR("Player " + getPlayerNumber() + " Planning discards using MCTS failed 5 times, discarding at random");
        			SOCGame.discardPickRandom(ourPlayerData.getResources(), numDiscards, discards, rand);
        			break;
        		}
        		
    			illegal = false;
    	        pause(1);
    	        Game g = generateGame(S_PAYTAX,null);
    			mcts.newTree(g);
    			SearchListener listener = mcts.search();
    			listener.waitForFinish();
    			int[] action = g.listPossiblities(false).getOptions().get(mcts.getNextActionIndex());
    	        String s = String.format("Player " + getPlayerNumber() + " chose discard action: [%d %d %d %d %d %d]", action[0], action[1], action[2], action[3], action[4], action[5]);
    	        D.ebugPrintlnINFO(s);      
    	        
        		for(int i=1; i < 6; i++){//jump the action description and only look at the first 6
        			discards.add(action[i], translateResToJSettlers(i-1));//position is type, contents is amount
        		}
        		
        		//check if its legal
        		if(discards.getTotal() != numDiscards){
    	        	D.ebugERROR("Player " + getPlayerNumber() + " planned to discard a different ammount ");
    	        	illegal = true;
    	        	continue;
        		}
        		if(!ourPlayerData.getResources().contains(discards)){
    	        	D.ebugERROR("Player " + getPlayerNumber() + " planned to discard a set of resources it doesn't have");
    	        	illegal = true;
    	        	continue;
        		}
    		}
    		client.put(SOCRobotFlag.toCmd(getGame().getName(), true, getPlayerNumber()));
    	}
    	client.discard(game, discards);
    }
	
    protected void getActionForPLAY1()    
    {
    	D.ebugPrintlnINFO("Player " + getPlayerNumber() + " call to getActionForPLAY1 method");
    	if(waitingForTradeResponse || isWaiting() || waitingForTradeMsg || getMemory().getMyOffer() != null){
    		D.ebugERROR("Player " + getPlayerNumber() + " being asked to plan for PLAY1 while still waiting for a trade response");
    		return;
    	}
    	
    	if(acceptableTrades.size() > 1){
    		D.ebugWARNING("Player " + getPlayerNumber() + " asked to plan when it hasn't finished negotiating. Clearing list and replanning");
    		acceptableTrades.clear();
    	}
    	
    	//make the server believe we are a human player so we won't get interrupted by the force end turn thread
    	client.put(SOCRobotFlag.toCmd(getGame().getName(), false, getPlayerNumber()));
    	boolean illegal = true;
    	int count = 0;
    	while(illegal){
    		count++;
	    	illegal = false;
	    	int pn = getPlayerNumber();
	    	int[] action;
	        //if the trading has failed, the agent should try to execute the next best action, before replanning
	    	if(!acceptableTrades.isEmpty()){
	    		D.ebugPrintlnINFO("Player " + getPlayerNumber() + " executing already planned action");
	    		action = acceptableTrades.poll().description;
	    		acceptableTrades.clear();//it should be empty, but just to be sure
	    	}
	    	else{
	    		D.ebugPrintlnINFO("Player " + getPlayerNumber() + " planning normal task");
		        Game g = generateGame(S_NORMAL,null);
				mcts.newTree(g);
				SearchListener listener = mcts.search();
				listener.waitForFinish();
				action = g.listPossiblities(false).getOptions().get(mcts.getNextActionIndex());
	    	}		        
		    
	        int coord;
	        SOCPossiblePiece targetPiece;
	        switch (action[0])
	        {
	            case A_BUILDROAD:
	                coord = translateEdgeToJSettlers(action[1]);
	                targetPiece = new SOCPossibleRoad(getPlayerData(), coord, new Vector());
	                lastMove = targetPiece;
	                waitingForGameState = true;
	                counter = 0;
	                expectPLACING_ROAD = true;
	                whatWeWantToBuild = new SOCRoad(getPlayerData(), targetPiece.getCoordinates(), null);
	                //check for resources and location
	                if(ourPlayerData.isPotentialRoad(whatWeWantToBuild.getCoordinates()) && game.couldBuildRoad(pn)){
	                	D.ebugPrintlnINFO("Player " + getPlayerNumber() + " BUILD REQUEST FOR A ROAD AT " + Integer.toHexString(targetPiece.getCoordinates()));
	                	client.buildRequest(game, SOCPlayingPiece.ROAD);
	                }else{
	                	D.ebugERROR("Illegal Road for Play1 attempt; Player " + getPlayerNumber());
	                	illegal = true;
	                }
	                break;
	            case A_BUILDSETTLEMENT:
	                coord = translateVertexToJSettlers(action[1]);
	                targetPiece = new SOCPossibleSettlement(getPlayerData(), coord, new Vector());
	                lastMove = targetPiece;
	                waitingForGameState = true;
	                counter = 0;
	                expectPLACING_SETTLEMENT = true;
	                whatWeWantToBuild = new SOCSettlement(getPlayerData(), targetPiece.getCoordinates(), null);
	                //check for resources and location
	                if(ourPlayerData.isPotentialSettlement(whatWeWantToBuild.getCoordinates()) && game.couldBuildSettlement(pn)){
	                	D.ebugPrintlnINFO("Player " + getPlayerNumber() + " BUILD REQUEST FOR A SETTLEMENT " + Integer.toHexString(targetPiece.getCoordinates()));
	                	client.buildRequest(game, SOCPlayingPiece.SETTLEMENT);
	                }else{
	                	D.ebugERROR("Illegal Settlement for Play1 attempt; Player " + getPlayerNumber());
	                	illegal = true;
	                }
	            	
	                break;
	            case A_BUILDCITY:
	                coord = translateVertexToJSettlers(action[1]);
	                targetPiece = new SOCPossibleCity(this, getPlayerData(), coord);
	                lastMove = targetPiece;
	                waitingForGameState = true;
	                counter = 0;
	                expectPLACING_CITY = true;
	                whatWeWantToBuild = new SOCCity(getPlayerData(), targetPiece.getCoordinates(), null);
	                //check for resources and location
	                if(ourPlayerData.isPotentialCity(whatWeWantToBuild.getCoordinates()) && game.couldBuildCity(pn)){
	                	D.ebugPrintlnINFO("Player " + getPlayerNumber() + " BUILD REQUEST FOR A CITY " + Integer.toHexString(targetPiece.getCoordinates()));
	                	client.buildRequest(game, SOCPlayingPiece.CITY);
	                }else{
	                	D.ebugERROR("Illegal City for Play1 attempt; Player " + getPlayerNumber());
	                	illegal = true; 
	                }
	                
	               break;
	            case A_BUYCARD:
	            	targetPiece = new SOCPossibleCard(getPlayerData(), 1);
	                lastMove = targetPiece;
	                client.buyDevCard(game);
	                waitingForDevCard = true;
	                D.ebugPrintlnINFO("Player " + getPlayerNumber() + " buy development card request ");
	                
	                break;
	            case A_PLAYCARD_MONOPOLY:
	                decisionMaker.monopolyChoice = translateResToJSettlers(action[1]);
	                expectWAITING_FOR_MONOPOLY = true;
	                waitingForGameState = true;
	                counter = 0;
	                client.playDevCard(game, SOCDevCardConstants.MONO);
	                D.ebugPrintlnINFO("Player " + getPlayerNumber() + " MONOPOLY played on "+ action[1] );
	                break;
	            case A_PLAYCARD_FREERESOURCE:
	                int a1 = action[1];
	                int a2 = action[2];
	                int cl = ((a1==RES_CLAY ) ?1:0)  + ((a2==RES_CLAY ) ?1:0);
	                int or = ((a1==RES_STONE) ?1:0)  + ((a2==RES_STONE) ?1:0);
	                int sh = ((a1==RES_SHEEP) ?1:0)  + ((a2==RES_SHEEP) ?1:0);
	                int wh = ((a1==RES_WHEAT) ?1:0)  + ((a2==RES_WHEAT) ?1:0);
	                int wo = ((a1==RES_WOOD ) ?1:0)  + ((a2==RES_WOOD ) ?1:0);
	                
	                decisionMaker.resourceChoices = new SOCResourceSet(cl, or, sh, wh, wo, 0);
	                expectWAITING_FOR_DISCOVERY = true;
	                waitingForGameState = true;
	                counter = 0;
	                client.playDevCard(game, SOCDevCardConstants.DISC);
	                D.ebugPrintlnINFO("Player " + getPlayerNumber() + " FREE RESOURCE to get" + action[1] + " , " + action[2]);
	                break;
	            case A_PLAYCARD_FREEROAD:
	                waitingForGameState = true;
	                counter = 0;
	                //if we only have one road left to build or only one potential we should expect the second state
	                if(ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) > 1 && ourPlayerData.hasTwoPotentialRoads())
	                	expectPLACING_FREE_ROAD1 = true;
	                else
	                	expectPLACING_FREE_ROAD2 = true;
	                D.ebugPrintlnINFO("Player " + getPlayerNumber() + " PLAYING ROAD BUILDING CARD");
	                client.playDevCard(game, SOCDevCardConstants.ROADS);
	                
	                break;
	            case A_PLAYCARD_KNIGHT:
	                expectPLACING_ROBBER = true;
	                waitingForGameState = true;
	                counter = 0;
	                D.ebugPrintlnINFO("Player " + getPlayerNumber() + " PLAYING KNIGHT CARD");
	                client.playDevCard(game, SOCDevCardConstants.KNIGHT);
	                pause(1);
	                
	                break;
	            case A_PORTTRADE:
	                boolean[] to = new boolean[SOCGame.MAXPLAYERS];
	                for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
	                    to[i] = false;
	                SOCResourceSet give = new SOCResourceSet();
	                SOCResourceSet get = new SOCResourceSet();
	                give.add(action[1], translateResToJSettlers(action[2]));
	                get.add(action[3], translateResToJSettlers(action[4]));
	                SOCTradeOffer bankTrade = 
	                        new SOCTradeOffer(game.getName(), getPlayerData().getPlayerNumber(), to, give, get);
	                //check if its legal
	                //first of all, does the player actually have the amount offered?
	                if(!ourPlayerData.getResources().contains(give)){
	                	D.ebugERROR("Player " + getPlayerNumber() + " Illegal bank trade for Play1 attempt; player doesn't have the resources for bank trade");
	                	illegal = true;
	                }
	                //are the resources offered of the same type?
	                for (int i = SOCResourceConstants.MIN; i < SOCResourceConstants.MAXPLUSONE; i++){
	                	if(give.pickResource() != i && give.getAmount(i) > 0){
	                		D.ebugERROR("Player " + getPlayerNumber() + " Illegal bank trade for Play1 attempt; trying to offer multiple types of resources; Player " + getPlayerNumber());
	                		illegal = true;
	                	} 
	                }
	                //if 3rss, does the player have access to a misc port?
	                if(give.getTotal() == 3 && !ourPlayerData.getPortFlag(SOCBoard.MISC_PORT)){
	                	D.ebugERROR("Player " + getPlayerNumber() + " Illegal bank trade for Play1 attempt; player doesn't have access to misc port; Player " + getPlayerNumber());
	                	illegal = true;
	                }	
	                //if 2rss, does the player have access to a special port?
	                if(give.getTotal() == 2 && !ourPlayerData.getPortFlag(give.pickResource())){
	                	D.ebugERROR("Player " + getPlayerNumber() + " Illegal bank trade for Play1 attempt; player doesn't have access to port of type " + give.pickResource() + "; Player " + getPlayerNumber());
	                	illegal = true;
	                }
	                //finally is the player requesting more than one resource or none?
	                if(get.getTotal()!=1){
	                	D.ebugERROR("Player " + getPlayerNumber() + " Illegal bank trade for Play1 attempt; player asks for a different amount of resources than 1; Player " + getPlayerNumber());
	                	illegal = true;
	                }
	                if(!illegal){
	                	counter = 0;
		                waitingForTradeMsg = true;
		                D.ebugPrintlnINFO("Player " + getPlayerNumber() + " PORT TRADING ");
		                client.bankTrade(game, bankTrade.getGiveSet(), bankTrade.getGetSet());
		                pause(2);
		                
	                }
	                break;
	            case A_TRADE:
	            case A_OFFER:
	            	//create the offer in the JSettlers format
	            	SOCResourceSet giveSet = new SOCResourceSet();
	            	giveSet.add(action[3], translateResToJSettlers(action[4]));
	            	if(action[5] > -1)
	            		giveSet.add(action[5], translateResToJSettlers(action[6]));
	            	
	            	SOCResourceSet getSet = new SOCResourceSet();
	            	getSet.add(action[7], translateResToJSettlers(action[8]));
	            	if(action[9] > -1)
	            		getSet.add(action[9], translateResToJSettlers(action[10]));
	            	
	            	boolean[] receivers = new boolean[4];
	            	receivers[action[2]] = true;
	            	
	            	StacTradeOffer offer = new StacTradeOffer(game.getName(), getPlayerNumber(), receivers, giveSet, false, getSet, false);
	            	
	            	//compose message to be sent via the chat (this should set all the required flags)
	            	Persuasion persuasiveArgument = new Persuasion();
	            	String nlChatString = getDialogueManager().tradeMessageOffToString(getPlayerName(), getGame().getPlayerNames(), offer, persuasiveArgument , "");
	                String result = getDialogueManager().makeTradeOffer(offer, "", persuasiveArgument, nlChatString);
	            	
	                //set one more flag that is required for not coming in here again until the trade is executed
	                waitingForTradeResponse = true;
	                //send it
	                sendText(result);
	                //remove the first option from the list of acceptable trades so we won't resend it
	                listAcceptableTrades();
	                acceptableTrades.poll();
	                offers_made++;
	                
	            	break;
	            case A_ENDTURN:
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
	
	                D.ebugPrintlnINFO("Player " + getPlayerNumber() + "  ENDING TURN ");
	                negotiator.resetIsSelling();
	                negotiator.resetOffersMade();
                    negotiator.resetTradesMade();;
	                buildingPlan.clear();
	                negotiator.resetTargetPieces();
	                offers_made = 0;
	                pause(1);
	                client.endTurn(game);
	                break;
	            default:
	        }
	        
	        //allow replanning 5 times then break and force end turn as it means it can't find a plan
	        if(count == 5){
	        	D.ebugERROR("Forcing end turn MCTS robot; unable to find plan in PLAY1 state; Player " + getPlayerNumber());
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

                D.ebugERROR("!!! FORCING ENDING TURN !!!");
                negotiator.resetIsSelling();
                negotiator.resetOffersMade();
                buildingPlan.clear();
                negotiator.resetTargetPieces();
                acceptableTrades.clear();
                offers_made = 0;
                pause(1);
                client.endTurn(game);                
                break;
	        }
    	}
    	client.put(SOCRobotFlag.toCmd(getGame().getName(), true, getPlayerNumber()));//we are a robot again
    }
    
    @Override
    protected void handleDEVCARD(SOCDevCard mes){
    	super.handleDEVCARD(mes);
    	
    	if (isRobotType(MCTSRobotType.MCTS_FACTORED_BELIEF)) {
    		switch (mes.getAction())
	        {
	        case SOCDevCard.DRAW:
	            if(mes.getPlayerNumber() == getPlayerNumber() || //in this case we observe the card type we drew
	            (mes.getCardType() >= SOCDevCardConstants.CAP && mes.getCardType() < SOCDevCardConstants.UNKNOWN)){ // when drawing vp is observable
	            	getGame().devCardPlayed(mes.getCardType());
	            }
	            beliefModel.updateDevCardModel(socToSSDevCard(mes.getCardType()), Action.GAIN, mes.getPlayerNumber());
	            if(mes.getCardType() != SOCDevCardConstants.UNKNOWN) {
	            	beliefModel.reviseDevCardModel();
	            }
	            break;
	
	        case SOCDevCard.PLAY:
	            if(mes.getPlayerNumber() != getPlayerNumber()){//only if we are not playing it so we don't update twice;
	            	getGame().devCardPlayed(mes.getCardType());
	            }
	            beliefModel.updateDevCardModel(socToSSDevCard(mes.getCardType()), Action.LOSE, mes.getPlayerNumber());
	            break;
	
	        }
    	}
    }
    
    protected void handleGAMESTATE(SOCGameState mes) {
    	if(isRobotType(MCTSRobotType.MCTS_FACTORED_BELIEF) && game.getCurrentPlayerNumber() != -1){
    		if(game.getGameState() != SOCGame.OVER && game.getGameState() != SOCGame.PLACING_ROBBER && game.getGameState() != SOCGame.WAITING_FOR_CHOICE) {
    			int pn = game.getCurrentPlayerNumber();
    			if(getPlayerNumber() != pn) {
    				//include the "revealed" VP that are sometimes inferred by the belief model when revisions are performed
	    			int score = game.getPlayer(pn).getPublicVP() + beliefModel.getRevealedVP(pn);
	    			if(score >= 5) {
		    			beliefModel.updateNonVPDevCards(score, pn);
		    			if(beliefModel.getTotalRemainingDevCards() > 0)
		    				beliefModel.reviseDevCardModel();
	    			}
    			}
    		}
    	}
    }
    
    
    @Override
    public void handleGameTxtMsg(SOCGameTextMsg gtm) {
    	super.handleGameTxtMsg(gtm);
        
        //TODO: The below handling of stealing creates issues if the game is fully observable as stealing will be reported twice...fix it by checking if the game is observable or not...how? this is a server parameter...
        if (isRobotType(MCTSRobotType.MCTS_FACTORED_BELIEF) && getGame().getGameState() != SOCGame.OVER) {
            String mesText = gtm.getText();
            if(mesText.contains(" stole a resource from ")){
	            SOCGame ga = getGame();
	            //update the belief model for the gain from robbery
	            ResourceSet set = new ResourceSet(1);
	            String[] nicks = mesText.split(" stole a resource from "); //there is no trailing full stop suprisingly
	            int fromPn = ga.getPlayer(nicks[1]).getPlayerNumber();
	            int pn = ga.getPlayer(nicks[0]).getPlayerNumber();
	            beliefModel.updateResourceBelief(set, pn, SOCPlayerElement.GAIN, fromPn); //gain first
	            beliefModel.updateResourceBelief(set, fromPn, SOCPlayerElement.LOSE); //lose after so we know what we can gain
	            
            }else if(mesText.contains("played a Monopoly card")) {
            	monopolyPhase = true;
            }
        }
    }
    
    @Override
    protected void handleResources(int action, SOCPlayer player, int resourceType, int amount) {
    	super.handleResources(action, player, resourceType, amount);
    	
    	//also if the robot is of a specific type update the symbolic belief model
        if (isRobotType(MCTSRobotType.MCTS_FACTORED_BELIEF)) {
        	//update belief model if we are observing a gain/lose of resources
        	//and ignore the set action as this only takes place when the dice is rolled and for the current player
        	if(action != SOCPlayerElement.SET){
        		//ignore the special case of gaining/losing from robbing someone as this requires knowledge of both players involved ad a specific order to be executed
        		if(!(resourceType == SOCPlayerElement.UNKNOWN && amount == 1)){
        			ResourceSet set;
        			if(resourceType == SOCPlayerElement.UNKNOWN){
        				set = new ResourceSet(amount);
        			}else if(monopolyPhase && action == SOCPlayerElement.LOSE){
        				set = new ResourceSet(amount, translateResToSmartSettlers(resourceType));
        			}else{
        				set = new ResourceSet();
        				set.add(amount, translateResToSmartSettlers(resourceType));
        			}
        			//TODO: I don't like that we are relying on GAIN being last here, but as long as nobody changes SOCServer.handleMonopoly we are safe
        			if(monopolyPhase) {
    					if(action == SOCPlayerElement.LOSE) {
    						if(player.getPlayerNumber() != game.getCurrentPlayerNumber())//don't do the update for the current player
    							beliefModel.updateResourceBelief(set, player.getPlayerNumber(), Action.LOSEMONO);
    					}else
    						beliefModel.updateResourceBelief(set, player.getPlayerNumber(), Action.GAINMONO);
        				if(action == SOCPlayerElement.GAIN) {
        					monopolyPhase = false;
        				}
        			}else {
        				beliefModel.updateResourceBelief(set, player.getPlayerNumber(), action);
        				
        			}
        			//NOTE: this should cover every update scenario except the offer and the gain/lose from robbery
        			//ensure compatibility after update
                	for(int i =0; i < 4; i ++) {
                		SOCPlayer pl = game.getPlayer(i);
                		compareBeliefWithJSettlersModel(pl.getResources(), i);
                	}
                	//and consistency
                	beliefModel.modelChecking();
        		}
        	}
        }
    }
    
    @Override
    protected void handlePUTPIECE_updateGameData(SOCPutPiece mes) {
    	super.handlePUTPIECE_updateGameData(mes);
    	
    	//update the belief model with info from the second settlement placement
    	if (isRobotType(MCTSRobotType.MCTS_FACTORED_BELIEF)) {
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
	                    rs.add(1, type);
	                }
	            }
	            
	            beliefModel.updateResourceBelief(socToSSRssSet(rs), mes.getPlayerNumber() , SOCPlayerElement.GAIN);
	        }
        }
    
    }
    
////////////////////////////SMARTSETTLERS METHODS/////////////////////
    
	private void getActionForPLAY() {
		
		/*
		 * if there is a single option just roll
		 */
		if (ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT) > 0){
			expectDICERESULT = true;
			counter = 0;
			D.ebugPrintlnINFO("Player " + getPlayerNumber() + " rolling dice");
			client.rollDice(game);
			return;
		}
		
		
		D.ebugPrintlnINFO("Player " + getPlayerNumber() + " planning before dice");
		// make the server believe we are a human player so we won't get
		// interrupted by the force end turn thread
		client.put(SOCRobotFlag.toCmd(getGame().getName(), false, getPlayerNumber()));
		Game g = generateGame(S_BEFOREDICE,null);
		mcts.newTree(g);
		SearchListener listener = mcts.search();
		listener.waitForFinish();
		int[] action = g.listPossiblities(false).getOptions().get(mcts.getNextActionIndex());
		String s = String.format("Player " + getPlayerNumber() + " chose action: [%d %d %d %d %d]", action[0], action[1], action[2],
				action[3], action[4]);
		D.ebugPrintlnINFO(s);
		
		switch (action[0]) {
		case A_PLAYCARD_KNIGHT:
			// remember where we want to play the robber and who to steal from,
			// otherwise JSettlers will ask us again
			robberHexFromKnight = translateHexToJSettlers(action[1],Catan.board);
			robberVictim = action[2];

			expectPLACING_ROBBER = true;
			waitingForGameState = true;
			counter = 0;
			D.ebugPrintlnINFO("Player " + getPlayerNumber() + " playing knight card");
			client.playDevCard(game, SOCDevCardConstants.KNIGHT);
			pause(1);

			break;
		case A_THROWDICE:
			expectDICERESULT = true;
			counter = 0;

			D.ebugPrintlnINFO("Player " + getPlayerNumber() + " rolling dice");
			client.rollDice(game);
			break;
		}
		// we are a robot again
		client.put(SOCRobotFlag.toCmd(getGame().getName(), true, getPlayerNumber()));
																						
	}
	
	
	private void getActionForFREEROAD(int state) {
		D.ebugPrintlnINFO("Player " + getPlayerNumber() + " planning free road");
		int st;
		if (state == SOCGame.PLACING_FREE_ROAD1)
			st = S_FREEROAD1;
		else
			st = S_FREEROAD2;

		// make the server believe we are a human player so we won't get
		// interrupted by the force end turn thread
		client.put(SOCRobotFlag.toCmd(getGame().getName(), false, getPlayerNumber()));

		boolean illegal = true;
		while (illegal) {
			illegal = false;
			Game g = generateGame(st,null);
			mcts.newTree(g);
			SearchListener listener = mcts.search();
			listener.waitForFinish();
			int[] action = g.listPossiblities(false).getOptions().get(mcts.getNextActionIndex());

			String s = String.format("Player " + getPlayerNumber() + " chose action: [%d %d %d %d %d]", action[0], action[1], action[2], action[3],
					action[4]);
			D.ebugPrintlnINFO(s);

			int coord;
			SOCPossiblePiece targetPiece;
			coord = translateEdgeToJSettlers(action[1]);
			targetPiece = new SOCPossibleRoad(getPlayerData(), coord, new Vector());
			whatWeWantToBuild = new SOCRoad(getPlayerData(), targetPiece.getCoordinates(), null);

			if (!ourPlayerData.isPotentialRoad(whatWeWantToBuild.getCoordinates())) {
				D.ebugERROR("Illegal free road attempt; Player " + getPlayerNumber());
				illegal = true;
			}
		}
		D.ebugPrintlnINFO("Player " + getPlayerNumber() + " placing free road at: " + whatWeWantToBuild.getCoordinates() + "    "
				+ Integer.toHexString(whatWeWantToBuild.getCoordinates()));
		pause(1);
		client.putPiece(game, whatWeWantToBuild);
		pause(1);
		// we are a robot again
		client.put(SOCRobotFlag.toCmd(getGame().getName(), true, getPlayerNumber()));
	}
	
	/**
	 * Fills the list of acceptable trades including the last non-trade action.
	 */
	private void listAcceptableTrades(){
		ArrayList<int[]> actions = mcts.getOrderedActionList();
		acceptableTrades.clear();
		int counter = 0;
		for(int[] a : actions){
			if(a[0] == A_TRADE || a[0] == A_OFFER){
				if(counter + offers_made < N_MAX_OFFERS){
					acceptableTrades.add(new MCTSAction(a));
					counter++;
				}
			}else{
				acceptableTrades.add(new MCTSAction(a));
				break;
			}
		}		
	}
	
	@Override
	public void kill() {
		super.kill();
    	if (isRobotType(MCTSRobotType.MCTS_FACTORED_BELIEF)) {
//    		System.out.println("Total decisions: " + nDecisions);
//    		System.out.println("Decisions when at least one player hand is not obs: " + nDecisionsWhenAtLeastOneHandUnk);
//    		System.out.println("Decisions when next player hand is not obs: " + nDecisionsWhenNextPlayerHandUnk);
//    		
//    		double avgNPlayersWithUnkHand = 0;
//    		for(Integer i : nPlayersWithUnkHand) {
//    			avgNPlayersWithUnkHand += i;
//    		}
//    		avgNPlayersWithUnkHand /= nPlayersWithUnkHand.size();
//    		double median = 0.0;
//    		Collections.sort(nPlayersWithUnkHand);
//    		if(nPlayersWithUnkHand.size() > 0) {
//	    		if(nPlayersWithUnkHand.size() % 2 != 0)
//	    			median = nPlayersWithUnkHand.get(nPlayersWithUnkHand.size()/2);
//	    		else
//	    			median = ((double)nPlayersWithUnkHand.get(nPlayersWithUnkHand.size()/2) + (double)nPlayersWithUnkHand.get(nPlayersWithUnkHand.size()/2 -1))/2;
//    		}
//    		System.out.println("When not obs, avg n players with unk rss: " + avgNPlayersWithUnkHand);
//    		System.out.println("When not obs, median n players with unk rss: " + median); //??? it will be 1 or 1.5 at most... why am I doing this?? :|
//    		
//    		System.out.println("Max entropy for player: " + maxPlayerEntropy);
//    		System.out.println("Min entropy for player: " + minPlayerEntropy);
//    		System.out.println("Max entropy avg over players : " + maxAvgEntropy);
//    		System.out.println("Min entropy avg over player: " + minAvgEntropy);
//    		
//    		double meanHands = 0;
//    		double variance = 0;
//    		for(Double d : nHands) {
//    			meanHands += d;
//    		}
//    		meanHands /= nHands.size();
//    		for(Double d : nHands) {
//    			variance += Math.pow(d - meanHands,2);
//    		}
//    		variance /= nHands.size();
//    		
//    		median = 0.0;
//    		Collections.sort(nHands);
//    		if(nHands.size() > 0) {
//	    		if(nHands.size() % 2 != 0)
//	    			median = nHands.get(nHands.size()/2);
//	    		else
//	    			median = ((double)nHands.get(nHands.size()/2) + (double)nHands.get(nHands.size()/2 -1))/2;
//    		}
//    		
//    		System.out.println("Avg number of possible hands when not everything is known: " + meanHands);
//    		System.out.println("Median number of possible hands when not everything is known: " + median);
//    		System.out.println("Variance for number of possible hands when not everything is known: " + variance);
//    		System.out.println("Std for number of possible hands when not everything is known: " + Math.sqrt(variance));
//    		System.out.println("Max number of possible hands when not everything is known:" + maxHands);
//    		
//    		double meanEntropy = 0;
//    		variance = 0;
//    		for(Double d : playerEntropies) {
//    			meanEntropy += d;
//    		}
//    		meanEntropy /= playerEntropies.size();
//    		for(Double d : playerEntropies) {
//    			variance += Math.pow(d - meanEntropy,2);
//    		}
//    		variance /= playerEntropies.size();
//    		Collections.sort(playerEntropies);
//    		median = 0.0;
//    		if(playerEntropies.size() > 0) {
//	    		if(playerEntropies.size() % 2 != 0)
//	    			median = playerEntropies.get(playerEntropies.size()/2);
//	    		else
//	    			median = (playerEntropies.get(playerEntropies.size()/2) + playerEntropies.get(playerEntropies.size()/2 -1))/2;
//    		}
//    		System.out.println("Player entropy mean: " + meanEntropy);
//    		System.out.println("Player entropy variance: " + variance);
//    		System.out.println("Player entropy std: " + Math.sqrt(variance));
//    		System.out.println("Player entropy median: " + median);
//    		
//    		meanEntropy = 0;
//    		variance = 0;
//    		for(Double d : avgEntropies) {
//    			meanEntropy += d;
//    		}
//    		meanEntropy /= avgEntropies.size();
//    		for(Double d : avgEntropies) {
//    			variance += Math.pow(d - meanEntropy,2);
//    		}
//    		variance /= avgEntropies.size();
//    		median = 0.0;
//    		Collections.sort(avgEntropies);
//    		if(avgEntropies.size() > 0) {
//	    		if(avgEntropies.size() % 2 != 0)
//	    			median = avgEntropies.get(avgEntropies.size()/2);
//	    		else
//	    			median = (avgEntropies.get(avgEntropies.size()/2) + avgEntropies.get(avgEntropies.size()/2 - 1))/2;
//    		}
//    		
//    		System.out.println("Average entropy mean: " + meanEntropy);
//    		System.out.println("Average entropy variance: " + variance);
//    		System.out.println("Average entropy std: " + Math.sqrt(variance));
//    		System.out.println("Average entropy median: " + median);
//    		
//    		System.out.println("Number of decisions this game: " + nDecisionsGame);
//    		if(nDecisionsWhenSave > 0)
//    			System.exit(0);
//    		//reset per game fields;
//    		nDecisionsGame = 0;
//    		
//    		System.out.println("Number of win/continue game chance nodes " + CatanWithBelief.chanceCount);
//    		System.out.println("Number of failed win/continue game chance nodes " + CatanWithBelief.chanceCount1);
    		
    		beliefModel.destroy();
    		beliefModel = null;
    	}
		mcts.shutdownNow(false);
	}
	
	public void debugPrintBrainStatus() {
        super.debugPrintBrainStatus();
        
        //print out the belief model to check if this caused any issues
        if (isRobotType(MCTSRobotType.MCTS_FACTORED_BELIEF)) {
        	System.err.println(beliefModel.toString());
        }
	}
	
    ///////////////////TRANSLATION METHODS FROM SSCLIENT//////////////
    //TODO: some of these are duplicated in StacDBToCatanInterface, try to eliminate this duplication when there is time
    
	 /**
     * @param GAMESTATE the current state
     * @param currentOffer the offer made to us by another player
     * @return 
     */
    private Game generateGame(int GAMESTATE, int[] currentOffer)
    {
    	//hacky but the whole of JSettlers is hacky...this should be set in the constructor but there is no player number at that point
    	if(isRobotType(MCTSRobotType.MCTS_NN_SEEDING))
    		((NNCatanSeedTrigger)mcts.getMCTSConfig().trigger).ourPlayerNumber = this.getPlayerNumber();
    	//basic code for checking entropy of belief distribution over player hands
//    	if(isRobotType(MCTSRobotType.MCTS_FACTORED_BELIEF)) {
//    		int nextPn = getPlayerNumber() + 1;
//    		if(nextPn == NPLAYERS)
//    			nextPn = 0;
//    		boolean notObs = false;
//    		int nPlayersWithUnk = 0;
//    		double curEntropy = 0.0;
//    		double avgNHands = 0;
//    		double[] plEnt = new double[4];
//    		int[] plHandSize = new int[4];
//    		int[] plUnkDevCards = new int[4];
//    		int playerNumberForUNK = -1;
////    		boolean saveGame = true; //1 or 2
//    		boolean saveGame = false; //3
//    		
//    		PlayerResourceModel[] rssModels = this.beliefModel.getPlayerHandsModel();
//    		for(int i =0; i < NPLAYERS; i++) {
//    			if(i == getPlayerNumber()) 
//    				continue;
//    			if(!rssModels[i].isFullyObservable() /*&& rssModels[i].possibleResSets.size() > 10*/) {
//					plUnkDevCards[i] = this.beliefModel.getDevCardModel().getTotalUnknownCards(i);
//    				nPlayersWithUnk ++;
//    				notObs = true;
//					//compute entropy:
//					double e = 0.0;
//					for(Entry<ResourceSet,Double> entry: rssModels[i].possibleResSets.entrySet()) {
//						e += entry.getValue() * Math.log(entry.getValue());
//					}
//					if(maxPlayerEntropy < - e)
//						maxPlayerEntropy = - e;
//					if(minPlayerEntropy > - e)
//						minPlayerEntropy = - e;
//					curEntropy = curEntropy - e;
//					
//					plEnt[i] = -e;
//					plHandSize[i] = rssModels[i].possibleResSets.size();
//					playerEntropies.add(-e);
//					if(rssModels[i].possibleResSets.size() > maxHands)
//						maxHands = rssModels[i].possibleResSets.size();
//					avgNHands += rssModels[i].possibleResSets.size();
//					
//					if( i == nextPn) {
//						nDecisionsWhenNextPlayerHandUnk ++;
//						if(plUnkDevCards[i] > 1) {
//							playerNumberForUNK = i;
////							saveGame = true; //2
//						}
//					}else {
//						if(plUnkDevCards[i] > 1) {
//							playerNumberForUNK = i;
////							saveGame = true; //1
//						}
//					}
//					
//					if(plUnkDevCards[i] != 0 && saveGame) { //a
////						saveGame = false;
//					}
//					
////					if(plUnkDevCards[i] < 1 && saveGame) { //c
////						saveGame = false;
////					}
//					
//    			}
//    			
//    		}
//    		if(nPlayersWithUnk != 3 && saveGame)
//    			saveGame = false;
//    		//entropy check for 3 (multiplayer) ... it is too much and it is almost impossible to find scenarios for this
////			for(int i =0; i < 4; i++) {
////				if(i == getPlayerNumber())
////					continue;
////				double maxEnt = Math.log(plHandSize[i]);
////				//if(maxEnt - plEnt[i] > 0.2 && saveGame) //x
////				if(maxEnt - plEnt[i] < 0.5 && saveGame) //y
////					saveGame = false;
////			}
//    		
//    		if(notObs) {
//    			nDecisionsWhenAtLeastOneHandUnk ++;
//	    		//average entropy over players
//	    		curEntropy /= nPlayersWithUnk; 
//				if(maxAvgEntropy < curEntropy)
//					maxAvgEntropy = curEntropy;
//				if(minAvgEntropy > curEntropy)
//					minAvgEntropy = curEntropy;
//	    		avgEntropies.add(curEntropy);
//				
//				avgNHands /= nPlayersWithUnk; 
//	    		nHands.add(avgNHands);
//
//	    		
//	    		nPlayersWithUnkHand.add(nPlayersWithUnk);
//	    		
//				//save test
////				if(GAMESTATE == S_NORMAL) {
////					if(saveGame && nDecisionsWhenSave == 0) {
//	    		
////						//TODO: comment out accordingly
////						//multiple players tracking
////			        	SOCRobotClient cl = getClient();
////			        	//send the game copy request 
////			        	cl.put(SOCGameCopy.toCmd(getGame().getName(), "robot", getPlayerNumber()));
////			        	//save for this robot
////			        	cl.unsynckedGameCopy(new SOCGameCopy(getGame().getName(), "saves/robot", -1));
////			        	nDecisionsWhenSave = nDecisionsGame + 1;
////						System.out.println("Saving game when all players' hands are unk");
////						System.out.println("Decision index: " + nDecisionsWhenSave);
////						//print out some statistics
////						for(int i =0; i < 4; i++) {
////							if(i == getPlayerNumber())
////								continue;
////							System.out.println("Player " + i);
////							System.out.println("Player hand size: " + plHandSize[i]);
////							System.out.println("Player hidden Dev card count: " + plUnkDevCards[i]);
////							System.out.println("Max entropy: " + Math.log(plHandSize[i]));
////							System.out.println("Player entropy: " + plEnt[i]);
////						}
//						
////						//single player tracking
////						double maxEnt = Math.log(plHandSize[playerNumberForUNK]);
//////						if(maxEnt - plEnt[playerNumberForUNK] > 0.5) {
////						if(maxEnt - plEnt[playerNumberForUNK] < 0.2) {
////				        	SOCRobotClient cl = getClient();
////				        	//send the game copy request 
////				        	cl.put(SOCGameCopy.toCmd(getGame().getName(), "robot", getPlayerNumber()));
////				        	//save for this robot
////				        	cl.unsynckedGameCopy(new SOCGameCopy(getGame().getName(), "saves/robot", -1));
////				        	nDecisionsWhenSave = nDecisionsGame + 1;
////							System.out.println("Saving game for at least one player with large poss hand");
////							System.out.println("Player hand size: " + plHandSize[playerNumberForUNK]);
////							System.out.println("Player hidden Dev card count: " + plUnkDevCards[playerNumberForUNK]);
////							System.out.println("Max entropy: " + maxEnt);
////							System.out.println("Player entropy: " + plEnt[playerNumberForUNK]);
////							System.out.println("Decision index: " + nDecisionsWhenSave);
////						}
//						
////					}
////				        	
////				}
//	    		
//	    	}
//	    		nDecisionsGame++;
//	    		nDecisions++;
//    	}
    	
    	
    	D.ebugPrintlnINFO("Player " + getPlayerNumber() + " generating Catan game representation ");
        int[] st = new int[STATESIZE];
        int val, fsmlevel;
        Vector v;
        Enumeration pEnum;
        int indo, indn;
        
        st[OFS_OUR_PLAYER] = getPlayerNumber();//always this player's perspective as we are using this player's belief when planning
        st[OFS_NUMBER_OF_OFFERS] = offers_made;
        st[OFS_TURN] = game.getTurnCount();
    	st[OFS_STARTING_PLAYER] = game.getFirstPlayer();
        val = game.getBoard().getRobberHex();
        st[OFS_ROBBERPLACE] = translateHexToSmartSettlers(val,Catan.board);
        if (lastSettlement !=-1)
        {
            st[OFS_LASTVERTEX] = translateVertexToSmartSettlers(lastSettlement);
        }
        
        val = game.getCurrentDice(); 
		if (val == -1) {
			st[OFS_DICE] = 0;
		} else {
			st[OFS_DICE] = val;
		}
        
        v = game.getBoard().getSettlements();
        pEnum = v.elements();
        while (pEnum.hasMoreElements())
        {
            SOCSettlement p = (SOCSettlement) pEnum.nextElement();
            
            indn = translateVertexToSmartSettlers(p.getCoordinates());
            val = p.getPlayer().getPlayerNumber();
            st[OFS_VERTICES+indn] = VERTEX_HASSETTLEMENT + val;
        }
        v = game.getBoard().getCities();
        pEnum = v.elements();
        while (pEnum.hasMoreElements())
        {
            SOCCity p = (SOCCity) pEnum.nextElement();
            
            indn = translateVertexToSmartSettlers(p.getCoordinates());
            val = p.getPlayer().getPlayerNumber();
            st[OFS_VERTICES+indn] = VERTEX_HASCITY + val;
        }
        int i, j;
        for (i=0; i<N_VERTICES; i++)
        {
            boolean islegal = true;
            if (st[OFS_VERTICES + i] >= VERTEX_HASSETTLEMENT)
                continue;
            for (j=0; j<6; j++)
            {
                indn = Catan.board.neighborVertexVertex[i][j];
                if ((indn!=-1) && (st[OFS_VERTICES + indn] >= VERTEX_HASSETTLEMENT))
                {
                    islegal = false;
                    break;
                }
            }
            if (!islegal)
                st[OFS_VERTICES + i] = VERTEX_TOOCLOSE;
        }

        v = game.getBoard().getRoads();
        pEnum = v.elements();
        while (pEnum.hasMoreElements())
        {
            SOCRoad p = (SOCRoad) pEnum.nextElement();
            
            indn = translateEdgeToSmartSettlers(p.getCoordinates());
            val = p.getPlayer().getPlayerNumber();
            st[OFS_EDGES+indn] = EDGE_OCCUPIED + val;
        }
        
        
        if (game.getPlayerWithLargestArmy() == null)
            val = -1;
        else
            val = game.getPlayerWithLargestArmy().getPlayerNumber();
        st[OFS_LARGESTARMY_AT] = val;
        if (game.getPlayerWithLongestRoad() == null)
            val = -1;
        else
            val = game.getPlayerWithLongestRoad().getPlayerNumber();
        st[OFS_LONGESTROAD_AT] = val;
        st[OFS_NCARDSGONE] = NCARDS-game.getNumDevCards();
        int pl;        
        
        for (pl=0; pl<NPLAYERS; pl++)
        {
        	SOCPlayer p = game.getPlayer(pl);
        	SOCDevCardSet ds = new SOCDevCardSet(p.getDevCards());
        	//JSettlers updates new cards at beginning of a player's turn, Catan/CatanWithBelief updates these at end of turn. Update them for all players except the current one
        	if(pl != game.getCurrentPlayerNumber())
        		ds.newToOld();
        	SOCResourceSet rs = p.getResources();
        	boolean hasports[] = p.getPortFlags();
        	
            st[OFS_PLAYERDATA[pl] + OFS_NSETTLEMENTS] = 5-p.getNumPieces(SOCPlayingPiece.SETTLEMENT);
            st[OFS_PLAYERDATA[pl] + OFS_NCITIES] = 4-p.getNumPieces(SOCPlayingPiece.CITY);
            st[OFS_PLAYERDATA[pl] + OFS_NROADS] = 15-p.getNumPieces(SOCPlayingPiece.ROAD);
            st[OFS_PLAYERDATA[pl] + OFS_PLAYERSLONGESTROAD] = p.getLongestRoadLength();
            if(pl == game.getCurrentPlayerNumber())
            	st[OFS_PLAYERDATA[pl] + OFS_HASPLAYEDCARD] = p.hasPlayedDevCard() ?1:0;
            
            st[OFS_PLAYERDATA[pl] + OFS_ACCESSTOPORT + PORT_CLAY-1] = hasports[SOCBoard.CLAY_PORT] ?1:0;
            st[OFS_PLAYERDATA[pl] + OFS_ACCESSTOPORT + PORT_WOOD-1] = hasports[SOCBoard.WOOD_PORT] ?1:0;
            st[OFS_PLAYERDATA[pl] + OFS_ACCESSTOPORT + PORT_STONE-1]= hasports[SOCBoard.ORE_PORT] ?1:0;
            st[OFS_PLAYERDATA[pl] + OFS_ACCESSTOPORT + PORT_SHEEP-1]= hasports[SOCBoard.SHEEP_PORT] ?1:0;
            st[OFS_PLAYERDATA[pl] + OFS_ACCESSTOPORT + PORT_WHEAT-1] = hasports[SOCBoard.WHEAT_PORT] ?1:0;
            st[OFS_PLAYERDATA[pl] + OFS_ACCESSTOPORT + PORT_MISC-1] = hasports[SOCBoard.MISC_PORT] ?1:0;
            
            st[OFS_PLAYERDATA[pl] + OFS_USEDCARDS + CARD_KNIGHT] = p.getNumKnights();
            st[OFS_PLAYERDATA[pl] + OFS_USEDCARDS + CARD_FREERESOURCE] = p.numDISCCards;
            st[OFS_PLAYERDATA[pl] + OFS_USEDCARDS + CARD_FREEROAD] = p.numRBCards;
            st[OFS_PLAYERDATA[pl] + OFS_USEDCARDS + CARD_MONOPOLY] = p.numMONOCards;
            st[OFS_PLAYERDATA[pl] + OFS_USEDCARDS + CARD_ONEPOINT] = 0;//these are never played
            
            if(isRobotType(MCTSRobotType.MCTS_FACTORED_BELIEF)) {
            	PlayerResourceModel phm = beliefModel.getPlayerHandsModel()[pl];
            	//the following are not always known, hence the min is used. The min matches the real one when the hand is known
                st[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_CLAY ] = phm.rssAbs[PlayerResourceModel.MIN + RES_CLAY];
                st[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WOOD ] = phm.rssAbs[PlayerResourceModel.MIN + RES_WOOD];
                st[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_STONE] = phm.rssAbs[PlayerResourceModel.MIN + RES_STONE];
                st[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_SHEEP] = phm.rssAbs[PlayerResourceModel.MIN + RES_SHEEP];
                st[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT] = phm.rssAbs[PlayerResourceModel.MIN + RES_WHEAT];
                st[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES] = phm.getTotalResources();
                
                //the following should be set to 0 if the player in question is not this one, apart from the total that contains the unknowns
                st[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_KNIGHT] = 
                        ds.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.KNIGHT);
                st[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_FREEROAD] = 
                        ds.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.ROADS);
                st[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_FREERESOURCE] = 
                        ds.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.DISC);
                st[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_MONOPOLY] = 
                        ds.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.MONO);
                st[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_ONEPOINT] = 0;
                st[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + N_DEVCARDTYPES] = ds.getNumNewCards();

                //the following should be set to 0 if the player in question is not this one, apart from the total that contains the unknowns
                st[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_KNIGHT] = 
                        ds.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT);
                st[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREEROAD] = 
                        ds.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.ROADS);
                st[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREERESOURCE] = 
                        ds.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.DISC);
                st[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_MONOPOLY] = 
                        ds.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.MONO);
                st[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_ONEPOINT] = ds.getNumVPCards();
                st[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + N_DEVCARDTYPES] = ds.getNumOldCards();
                
            }else {
	            st[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_CLAY ] = rs.getAmount(SOCResourceConstants.CLAY);
	            st[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WOOD ] = rs.getAmount(SOCResourceConstants.WOOD);
	            st[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_STONE] = rs.getAmount(SOCResourceConstants.ORE);
	            st[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_SHEEP] = rs.getAmount(SOCResourceConstants.SHEEP);
	            st[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT] = rs.getAmount(SOCResourceConstants.WHEAT);
	            
	            st[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_KNIGHT] = 
	                    ds.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.KNIGHT);
	            st[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_FREEROAD] = 
	                    ds.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.ROADS);
	            st[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_FREERESOURCE] = 
	                    ds.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.DISC);
	            st[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_MONOPOLY] = 
	                    ds.getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.MONO);
	            st[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_ONEPOINT] = 0;
	
	            st[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_KNIGHT] = 
	                    ds.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT);
	            st[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREEROAD] = 
	                    ds.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.ROADS);
	            st[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREERESOURCE] = 
	                    ds.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.DISC);
	            st[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_MONOPOLY] = 
	                    ds.getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.MONO);
	            st[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_ONEPOINT] = ds.getNumVPCards();
            }
            
            
        }
        
        //finally set the correct state, level and player for the fsm
    	//remember the current player in this turn no matter what the current state is
    	fsmlevel = 0;
    	st[OFS_FSMPLAYER+fsmlevel] = game.getCurrentPlayerNumber();
    	st[OFS_DISCARD_FIRST_PL] = -1;//always -1 unless during discard
        if(GAMESTATE == S_PAYTAX){
        	//TODO: discard is probably problematic, since other players might have already discarded and we already know their state in a fully-observable game :(
        	//it also means there is a very small risk that our planning will force them to discard again
        	//finally the opponents might learn a response to this player's discard and this player might learn a response to the response (min-max behaviour of MCTS)
			//nonetheless, we always start with this player (even if it is not the current one), as the Server will only ask a player if it must discard
			//this is a race condition that needs to be solved soon
        	fsmlevel = 1;
			st[OFS_FSMLEVEL] = fsmlevel;
			st[OFS_FSMSTATE + fsmlevel] = GAMESTATE;
			st[OFS_FSMPLAYER + fsmlevel] = getPlayerNumber(); 
			st[OFS_DISCARD_FIRST_PL] = getPlayerNumber(); 
        }else{
	        if (GAMESTATE == S_ROBBERAT7){
	            fsmlevel = 1;
	        	st[OFS_FSMPLAYER+fsmlevel] = game.getCurrentPlayerNumber();
	        }
	        if(GAMESTATE == S_NEGOTIATIONS){//should be an offer made to us
	        	for(i = 0; i < ACTIONSIZE; i++)
	        		st[OFS_CURRENT_OFFER + i] = currentOffer[i];
	            fsmlevel = 1;
	        	st[OFS_FSMPLAYER+fsmlevel] = getPlayerNumber();
	        }
	        st[OFS_FSMLEVEL] = fsmlevel;
	        st[OFS_FSMSTATE + fsmlevel] = GAMESTATE;
        }
        D.ebugPrintlnINFO("Player " + getPlayerNumber() + " Catan game representation is: \n " + Arrays.toString(st));
        Game g = gameFactory.getGame(st);
        return g;
    }
	
	/**
	 * Sending the board layout and initialising all translation stuff NOTE: the
	 * board layout will be sent across by each robot possibly at the same time,
	 * therefore there is no need to be synchronised. It would be more efficient to update it
	 * only once, but JSettlers architecture makes it difficult to do this.
	 */
	public void generateBoard() {
		D.ebugPrintlnINFO("Player " + getPlayerNumber() + " generating Catan board representation ");
		Board bl = new Board();
		bl.InitBoard();
		int xo, yo;
		int xn, yn;
		int indo, indn;
		int coordo;
		int to, tn;

		SOCBoard bo = game.getBoard();
		for (xn = 0; xn < Board.MAXX; xn++)
			for (yn = 0; yn < Board.MAXY; yn++) {
				if ((xn + yn < 3) || (xn + yn > 9))
					continue;
				indn = bl.hexatcoord[xn][yn];

				xo = 2 * xn + 1;
				yo = 2 * (xn + yn) - 5;
				coordo = 16 * xo + yo;
				to = bo.getHexTypeFromCoord(coordo);

				if ((to >= 0) && (to <= 5)) {
					switch (to) {
					case 0:
						tn = LAND_DESERT;
						break;
					case 1:
						tn = LAND_CLAY;
						break;
					case 2:
						tn = LAND_STONE;
						break;
					case 3:
						tn = LAND_SHEEP;
						break;
					case 4:
						tn = LAND_WHEAT;
						break;
					case 5:
						tn = LAND_WOOD;
						break;
					default:
						tn = -1; // should cause error
					}
					bl.hextiles[indn].subtype = tn;
					bl.hextiles[indn].type = TYPE_LAND;
					bl.hextiles[indn].productionNumber = bo.getNumberOnHexFromCoord(coordo);

				} else if ((to >= 7) && (to <= 12)) {
					switch (to) {
					case SOCBoard.MISC_PORT_HEX:
						tn = PORT_MISC;
						break;
					case SOCBoard.CLAY_PORT_HEX:
						tn = PORT_CLAY;
						break;
					case SOCBoard.ORE_PORT_HEX:
						tn = PORT_STONE;
						break;
					case SOCBoard.SHEEP_PORT_HEX:
						tn = PORT_SHEEP;
						break;
					case SOCBoard.WHEAT_PORT_HEX:
						tn = PORT_WHEAT;
						break;
					case SOCBoard.WOOD_PORT_HEX:
						tn = PORT_WOOD;
						break;
					default:
						tn = PORT_MISC;
					}
					bl.hextiles[indn].subtype = tn;
					bl.hextiles[indn].type = TYPE_PORT;
				} else {
					bl.hextiles[indn].type = TYPE_SEA;
					bl.hextiles[indn].subtype = SEA;
					bl.hextiles[indn].orientation = -1;
				}
			}
		initTranslationTables(bl);
		Catan.board = bl;
	}
    
	private int translateHexToSmartSettlers(int indo, Board bl) {
		if (indo == -1)
			return -1;
		int xo = indo / 16;
		int yo = indo % 16;

		int xn = (xo - 1) / 2;
		int yn = (yo + 5) / 2 - xn;

		return bl.hexatcoord[xn][yn];
	}
    
	private int translateHexToJSettlers(int indn, Board bl) {
		if (indn == -1)
			return -1;

		int xn = (int) bl.hextiles[indn].pos.x;
		int yn = (int) bl.hextiles[indn].pos.y;

		int xo = 2 * xn + 1;
		int yo = 2 * (xn + yn) - 5;

		return xo * 16 + yo;
	}

	private int[] vertexToSS;
	private int[] edgeToSS;
	private int[] vertexToJS;
	private int[] edgeToJS;

	private int translateVertexToSmartSettlers(int indo) {
		if (vertexToSS == null)
			return 0;
		return vertexToSS[indo];
	}

	private int translateEdgeToSmartSettlers(int indo) {
		return edgeToSS[indo];
	}

	private int translateVertexToJSettlers(int indo) {
		return vertexToJS[indo];
	}

	private int translateEdgeToJSettlers(int indo) {
		return edgeToJS[indo];
	}
    
	private int translateResToJSettlers(int ind) {
		switch (ind) {
		case RES_WOOD:
			return SOCResourceConstants.WOOD;
		case RES_CLAY:
			return SOCResourceConstants.CLAY;
		case RES_SHEEP:
			return SOCResourceConstants.SHEEP;
		case RES_WHEAT:
			return SOCResourceConstants.WHEAT;
		case RES_STONE:
			return SOCResourceConstants.ORE;
		default:
			return -1;
		}
	}
    
	private int translateResToSmartSettlers(int ind) {
		switch (ind) {
		case SOCResourceConstants.WOOD:
			return RES_WOOD;
		case SOCResourceConstants.CLAY:
			return RES_CLAY;
		case SOCResourceConstants.SHEEP:
			return RES_SHEEP;
		case SOCResourceConstants.WHEAT:
			return RES_WHEAT;
		case SOCResourceConstants.ORE:
			return RES_STONE;
		default:
			return -1;
		}
	}
    
	private void initTranslationTables(Board bl) {
		SOCBoard bo = game.getBoard();
		int vo, vn;
		int eo, en;
		int ho, hn, j;
		vertexToSS = new int[SOCBoard.MAXNODE + 1];
		edgeToSS = new int[SOCBoard.MAXEDGE_V1 + 1];
		vertexToJS = new int[N_VERTICES];
		edgeToJS = new int[N_EDGES];

        int[] numToHexID = 
        {
            0x17, 0x39, 0x5B, 0x7D,
            0x15, 0x37, 0x59, 0x7B, 0x9D,
            0x13, 0x35, 0x57, 0x79, 0x9B, 0xBD,
            0x11, 0x33, 0x55, 0x77, 0x99, 0xBB, 0xDD,
            0x31, 0x53, 0x75, 0x97, 0xB9, 0xDB,
            0x51, 0x73, 0x95, 0xB7, 0xD9,
            0x71, 0x93, 0xB5, 0xD7
        };

		for (j = 0; j < numToHexID.length; j++)
		{
			ho = numToHexID[j];
			if (bo.getHexTypeFromCoord(ho) >= SOCBoard.WATER_HEX)
				continue;
			hn = translateHexToSmartSettlers(ho, bl);
			int i = 0;
			Vector vlist = SOCBoard.getAdjacentNodesToHex(ho);
			Vector elist = SOCBoard.getAdjacentEdgesToHex(ho);
			for (i = 0; i < 6; i++) {
				vo = (Integer) vlist.get(i);
				vn = bl.neighborHexVertex[hn][i];
				vertexToSS[vo] = vn;
				vertexToJS[vn] = vo;
				eo = (Integer) elist.get(i);
				en = bl.neighborHexEdge[hn][i];
				edgeToSS[eo] = en;
				edgeToJS[en] = eo;
			}
		}
	}
	
	/**
	 * Note: there is no direct translation! Make sure the SOCResource set doesn't contain any unknowns, otherwise these are ignored
	 * @param set
	 * @return
	 */
	public static ResourceSet socToSSRssSet(SOCResourceSet set){
		int[] resources = new int[NRESOURCES + 1];
	    resources[RES_CLAY]    = set.getAmount(SOCResourceConstants.CLAY);
	    resources[RES_STONE]   = set.getAmount(SOCResourceConstants.ORE);
	    resources[RES_SHEEP]   = set.getAmount(SOCResourceConstants.SHEEP);
	    resources[RES_WHEAT]   = set.getAmount(SOCResourceConstants.WHEAT);
	    resources[RES_WOOD]    = set.getAmount(SOCResourceConstants.WOOD);
	    for(int i = 0; i < NRESOURCES; i++)
	    	resources[NRESOURCES]  += resources[i]; //TOTAL
	    return new ResourceSet(resources);
	}

	/**
	 * 
	 * @param set
	 * @return
	 */
	public static SOCResourceSet rsToSocRssSet(ResourceSet set){
		int[] resources = set.getResourceArrayClone();
		int[] rss = new int[6];
	    rss[SOCResourceConstants.CLAY - 1]    = resources[RES_CLAY];
	    rss[SOCResourceConstants.ORE - 1]     = resources[RES_STONE];
	    rss[SOCResourceConstants.SHEEP - 1]   = resources[RES_SHEEP];
	    rss[SOCResourceConstants.WHEAT - 1]   = resources[RES_WHEAT];
	    rss[SOCResourceConstants.WOOD - 1]    = resources[RES_WOOD];
		return new SOCResourceSet(rss);
	}
	
	public static int socToSSDevCard(int type){
		switch (type) {
		case SOCDevCardConstants.KNIGHT:
			return CARD_KNIGHT;
		case SOCDevCardConstants.MONO:
			return CARD_MONOPOLY;
		case SOCDevCardConstants.DISC:
			return CARD_FREERESOURCE;
		case SOCDevCardConstants.ROADS:
			return CARD_FREEROAD;
		case SOCDevCardConstants.CAP:
			return CARD_ONEPOINT;
		case SOCDevCardConstants.TEMP:
			return CARD_ONEPOINT;
		case SOCDevCardConstants.TOW:
			return CARD_ONEPOINT;
		case SOCDevCardConstants.LIB:
			return CARD_ONEPOINT;
		case SOCDevCardConstants.UNIV:
			return CARD_ONEPOINT;
		case SOCDevCardConstants.UNKNOWN:
			return N_DEVCARDTYPES;
		default:
			System.err.println("Unknown dev card type");
			return -1;//should cause a failure later
		}
	}
    
	
	/**
	 * This also acts as another model checking method as it makes sure that the new model is consistent with the beliefs of the old JSettlers model 
	 * @param s the set describing the beliefs of the old model
	 * @param pn the player number of the player whose hand we are checking this on
	 */
	public void compareBeliefWithJSettlersModel(SOCResourceSet s, int pn){
		PlayerResourceModel phm = beliefModel.getResourceModel().getPlayerHandModel(pn);
		if(s.getAmount(SOCResourceConstants.UNKNOWN) > 0){
			if(phm.isFullyObservable()){
				ResourceSet fromJSettlers = socToSSRssSet(s);
				ResourceSet fromModel = new ResourceSet(phm.getHand().getResourceArrayClone());
				fromModel.subtract(fromJSettlers);
				int [] rss = fromModel.getResourceArrayClone();
				for(int i = 0; i < ResourceSet.NRESOURCES; i++) {
					if (rss[i] < 0)
						System.err.println("Resource set in model not consistent with the JSettlers model");
				}
			}else {
				ResourceSet fromJSettlers = socToSSRssSet(s);
				for(ResourceSet fm: phm.possibleResSets.keySet()) {
					ResourceSet fromModel = new ResourceSet(fm.getResourceArrayClone());
					fromModel.subtract(fromJSettlers);
					int [] rss = fromModel.getResourceArrayClone();
					for(int i = 0; i < ResourceSet.NRESOURCES; i++) {
						if (rss[i] < 0)
							System.err.println("Resource set in model not consistent with the JSettlers model");
					}
				}
			}
			
			if(phm.getTotalResources() != s.getTotal())
				System.err.println("Resource sets totals are different: " + phm.getTotalResources() + " soc " + s.getTotal());
		}else{//this means it is fully observable
			if(phm.getTotalResources() != s.getTotal())
				System.err.println("Resource sets totals are different: " + phm.getTotalResources() + " soc " + s.getTotal());
			if(!phm.isFullyObservable())
				System.err.println("New model not exact when old model is");
			SOCResourceSet fromModel = rsToSocRssSet(phm.getHand());
			if(!s.equals(fromModel))
				System.err.println("The two models are different when they shouldn't be " + s.toShortString() + "   " + fromModel.toShortString());
		}
	}
}
