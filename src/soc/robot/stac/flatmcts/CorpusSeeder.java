package soc.robot.stac.flatmcts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import representation.NumericalFVGenerator;
import representation.SimilarityCalculator;
import soc.disableDebug.D;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCSettlement;
import soc.message.SOCPutPiece;
import soc.robot.SOCPlayerTracker;
import soc.robot.SOCPossibleCity;
import soc.robot.SOCPossibleSettlement;
import soc.robot.SOCRobotBrain;
import soc.robot.SOCRobotClient;
import soc.robot.SOCRobotDMImpl;
import soc.robot.stac.SettlementNode;
import soc.robot.stac.StacRobotDummyBrain;
import soc.server.database.stac.ExtGameStateRow;
import soc.server.database.stac.GameActionRow;
import soc.server.database.stac.ObsGameStateRow;
import soc.server.database.stac.StacDBHelper;
import soc.util.CappedQueue;
import soc.util.DeepCopy;
import soc.util.SOCRobotParameters;

/**
 * Implementation of a particle filter which acts as a seeder mechanism by treating the actions taken in the corpus as particles and the similarity 
 * of the states and actions as weights. The value of an action becomes the weighted average and is seeded into the tree node corresponding 
 * to the after-state.
 * @author MD
 *
 */
public class CorpusSeeder implements Seeder{
	
	//variables for some decision on clustering method
	public static int NO_CLUSTERING = 1;
	public static int STATE_RELEVANCE_CLUSTERING = 2;
	public static int STATE_AND_ACTION_RELEVANCE_CLUSTERING = 3;
	
	/**
	 * everything that we need will be contained in this object
	 */
	private SOCRobotBrain brain;
	/**
	 * The interface to the pstgres db that contains all the methods for looking up data 
	 */
	StacDBHelper dbh;
	/**
	 * Feature selector
	 */
	NumericalFVGenerator sfs;
	/**
	 * Similarity calculator
	 */
	SimilarityCalculator calc;
	
	/**
	 * Flag for deciding whether to seed the weighted average of the actions in the corpus 
	 * or the sum of the weights as value and total actions as visits;
	 * default=true as we don't want to seed in very large numbers as total visits
	 */
	private boolean seedAverage = true;
	
	/**
	 * Do we want to cluster in 3 groups based on relevance values? 
	 * If yes based on whose relevance do we want to cluster?(see static fields)
	 */
	private int clusterType;
	
	/**
	 * Constructor
	 * @param b the brain using the MCTS which is using this seeder(acts only as a container for the game/player trackers and data)
	 * @param seedAverage do we want to seed a value based on the weighted average or just seed in the whole sums and let MCTS to perform the average
	 * @param clusterType what type of clustering do we want to use? (see the static fields of this class)
	 */
	public CorpusSeeder(SOCRobotBrain b, boolean seedAverage, int clusterType){
		brain = b;
		dbh = new StacDBHelper();
		dbh.initialize();
		sfs = new NumericalFVGenerator();
		calc = new SimilarityCalculator();
		this.seedAverage = seedAverage;
		this.clusterType = clusterType;
	}
	
	@Override
	public void seed(TreeNode root, String actionType) {
		switch (actionType) {
		case "INITIAL_SETTLEMENT":
			D.ebugPrintlnINFO("Seeding For Initial Placement");
			seedForInitialPlacement(root);
			break;
		case "SECOND_SETTLEMENT":
			D.ebugPrintlnINFO("Seeding For Initial Placement");
			seedForInitialPlacement(root);
			break;
		case "MOVE_ROBBER":
			D.ebugPrintlnINFO("Seeding For Robber Action");
			seedForRobberAction(root);
			break;
		default:
			break; //do not seed
		}
	}
	
	/**
	 * Not implemented yet as we need methods for looking up this type of action inside the db
	 * @param root the parent node of the nodes to be seeded
	 */
	private void seedForRobberAction(TreeNode root) {
		// TODO Auto-generated method stub (NOT Implemented yet so no seeding taking place here)
		
	}

	/**
	 * method for seeding in a value based on how many people have played an action and how similar the action is to the set of legal ones 
	 * and how similar the state was to the current one. It also depends on the seedAverage flag and on the clustering type selected in MCTSType 
	 * @param root the parent node of the nodes to be seeded
	 */
	private void seedForInitialPlacement(TreeNode root) {
		dbh.connect();
		D.ebugPrintlnINFO("Connected");
		//current state
		ObsGameStateRow targetOGSR = brain.getGame().turnCurrentStateIntoOGSR();
		ExtGameStateRow targetEGSR = brain.turnStateIntoEGSR();
		int[] targetStateFeatures = sfs.calculateVector1(targetOGSR, targetEGSR); //there si only one target state the current one
		
		//list of all legal actions from the current state (need to find a way to have the same order during both the expansion and seeding logic)
		SOCPlayer player = brain.getOurPlayerData();
    	List<Integer> legalActions = player.getLegalSettlements(); //there is a set of legal actions
		
    	//calculate feature vectors for each legal action
    	Map<Integer, int[]> legalActionsFeatures = new HashMap<Integer, int[]>();
    	Map<Integer, Double> legalActionsVisits = new HashMap<Integer, Double>();
    	Map<Integer, Double> legalActionsValues = new HashMap<Integer, Double>();
    	
    	//in order to calculate features for each legal action we need a temp brain for each action and execute all the updates for an action
    	for(Integer action : legalActions){
    		SOCRobotBrain copy = copyBrainInfo(brain);
    		SOCGame ga = copy.getGame();
    		D.ebugPrintlnINFO("copied brain and game");
    		ga.putPiece(new SOCSettlement(ga.getPlayer(copy.getOurPlayerData().getPlayerNumber()),action, ga.getBoard()));
    		D.ebugPrintlnINFO("put piece in game");
    		copy.handlePUTPIECE_updateTrackers( new SOCPutPiece(ga.getName(), copy.getOurPlayerData().getPlayerNumber(), SOCPlayingPiece.SETTLEMENT, action));
    		D.ebugPrintlnINFO("put piece in brain");
    		ObsGameStateRow afterOGSR = ga.turnCurrentStateIntoOGSR();
    		D.ebugPrintlnINFO("turned into ogsr");
    		ExtGameStateRow afterEGSR = copy.turnStateIntoEGSR();
    		D.ebugPrintlnINFO("turned into egsr");
    		//the actions features is a difference of the before and after state features;
    		legalActionsFeatures.put(action, calc.vectorDifference(targetStateFeatures, sfs.calculateVector1(afterOGSR, afterEGSR)));
    		
    		//is this one of the points where we can feed in the values from JSettlers???
    		//initialise the visits and values
    		legalActionsVisits.put(action, new Double(0.0));
    		legalActionsValues.put(action, new Double(0.0));
    	}
    	D.ebugPrintlnINFO("Finished calculating features for all legal actions");
    	
		//containers for the info from the corpus
		Map<ObsGameStateRow, GameActionRow> stateAction = new HashMap<ObsGameStateRow, GameActionRow>();	
		Map<ObsGameStateRow, int[]> stateFeature = new HashMap<ObsGameStateRow, int[]>();
		Map<GameActionRow, int[]> actionFeature = new HashMap<GameActionRow,int[]>();
		Map<ObsGameStateRow, Double> stateRelevance = new HashMap<ObsGameStateRow, Double>();
		
		//current game state for getting all states of the same kind
		int gameState = brain.getGame().getGameState();
		
		//get all similar states and actions from the corpus
		for(int gameID = 1; gameID < 51; gameID ++){
			ObsGameStateRow[] states = dbh.getAllObsStatesOfAKind(gameID, gameState);
		
			//as end turn action does not really exists inside the initial placement, we need to keep only the states after the virtual end turn action :(
			Vector beforeStates = new Vector<ObsGameStateRow>();
			for(ObsGameStateRow ogsr : states){
				GameActionRow gar = dbh.selectGAR(gameID, ogsr.getID());
				if(gar.getType() == GameActionRow.ENDTURN)
					beforeStates.add(ogsr);
			}
			//and what a suprise we need to do something about the first second settlement placement action as that is another special case...
			if(gameState ==10){
				int i = 1;
				while(true){
					ObsGameStateRow og = dbh.selectOGSR(gameID, i);
					if(og.getGameState() == gameState){
						beforeStates.add(og);
						break;
					}
					i++;
				}
			}

			for(Object ogsr : beforeStates){
				GameActionRow gar = dbh.selectGAR(gameID, ((ObsGameStateRow)ogsr).getID() + 1); //get the action taken from the state
				ExtGameStateRow beforeEGSR = dbh.selectEGSR(gameID, ((ObsGameStateRow)ogsr).getID());
				ObsGameStateRow afterOGSR = dbh.selectOGSR(gameID, gar.getID());//because garID is equal to the after state
				ExtGameStateRow afterEgsr = dbh.selectEGSR(gameID, gar.getID());
			
				int[] bStateFeatures = sfs.calculateVector1((ObsGameStateRow)ogsr, beforeEGSR);
				int[] actionFeatures = calc.vectorDifference(bStateFeatures, sfs.calculateVector1(afterOGSR,afterEgsr)); //here do the vector difference
				
				stateAction.put((ObsGameStateRow)ogsr, gar);
				actionFeature.put(gar, actionFeatures);
				stateFeature.put((ObsGameStateRow)ogsr, bStateFeatures);
			
				//also once we pass a state and action to compare with we will assess the relevance in here correctly
				stateRelevance.put((ObsGameStateRow) ogsr, (Double)calc.cosineSimilarity(bStateFeatures, targetStateFeatures));
			
			}
		}
		D.ebugPrintlnINFO("Finished gathering all of a kind from the db");
		Map<ObsGameStateRow, Object> highClass = new HashMap<ObsGameStateRow, Object>();
		Map<ObsGameStateRow, Object> lowClass = new HashMap<ObsGameStateRow, Object>();
		//in here perform the clustering if needed
		if(clusterType != NO_CLUSTERING){
			//we want to remove the low similarity
			if(clusterType == STATE_RELEVANCE_CLUSTERING){
				System.out.println("state only clustering");
				for(ObsGameStateRow ogsr : stateAction.keySet()){
					double stateRel = stateRelevance.get(ogsr).doubleValue();
					if(stateRel < 0.75){
						lowClass.put(ogsr,null);//keep track of the lowest class
					}else if(stateRel > 0.9){
						highClass.put(ogsr, null);//keep track of the highest relevance
					}
				}
			}else if(clusterType == STATE_AND_ACTION_RELEVANCE_CLUSTERING){
				D.ebugPrintlnINFO("state and action clustering");
				for(ObsGameStateRow ogsr : stateAction.keySet()){
					double stateRel = stateRelevance.get(ogsr).doubleValue();
					//get action relevance
					GameActionRow gar = stateAction.get(ogsr);
					double actionRel = Double.NEGATIVE_INFINITY;//smallest possible value 
					//find the highest relevance
					for(Integer action : legalActions){
						double rel = calc.cosineSimilarity(actionFeature.get(gar), legalActionsFeatures.get(action));
						if(rel > actionRel){
							actionRel = rel;
						}
					}
					if((stateRel + actionRel)/2 < 0.75){
						lowClass.put(ogsr,null);//keep track of the lowest class
					}else if((stateRel + actionRel)/2 > 0.9){
						highClass.put(ogsr, null);//keep track of the highest relevance
					}
				}
			}//else there must have been a mistake so we treat as if no clustering type defined
		}
		else
			D.ebugPrintlnINFO("no clustering");
		D.ebugPrintlnINFO("finished the clustering");
		//assign values and visits for each legal action following the corpus (the particle filter)
		for(ObsGameStateRow ogsr : stateAction.keySet()){
			GameActionRow gar = stateAction.get(ogsr);
			double actionRel = Double.NEGATIVE_INFINITY;//smallest possible value 
			Integer mostRelAction = new Integer(-1);//absurd coordinate
			//find the most relevant legal action by calculating the similarity to each of the legal actions
			for(Integer action : legalActions){
				double rel = calc.cosineSimilarity(actionFeature.get(gar), legalActionsFeatures.get(action));
				if(rel > actionRel){
					mostRelAction = action;
					actionRel = rel;
				}
			}
			//add one to the visit
			double visits = legalActionsVisits.get(mostRelAction);
			legalActionsVisits.put(mostRelAction, new Double(visits + 1.0));
				
			//and add the weights average(state and action relevance) to the value
			double value = legalActionsValues.get(mostRelAction);
			double stateRel = stateRelevance.get(ogsr);
			if(clusterType != NO_CLUSTERING){
				if(highClass.keySet().contains(ogsr)){
//					D.ebugPrintlnINFO("high sim");
					legalActionsValues.put(mostRelAction,  new Double(value + 1));//we ignore the weights for this group
				}else if(!lowClass.keySet().contains(ogsr)){
//					D.ebugPrintlnINFO("med sim");
					legalActionsValues.put(mostRelAction,  new Double(value + ((actionRel + stateRel)/2))); //for medium do the usual particle filter
				}//low similarities will be ignored in this approach 
//					D.ebugPrintlnINFO("low sim");
			}else{
				legalActionsValues.put(mostRelAction,  new Double(value + ((actionRel + stateRel)/2))); //usual particle filter
			}
		}
		
		//do the actual seeding here
		TreeNode[] nodes = root.children;
		for(Integer action : legalActions){
			//find the corresponding node in the tree if the value is not 0 (we never want to seed in 0 values)
			if((double)legalActionsValues.get(action) != 0.0)
			for(TreeNode n : nodes){
				if(((SOCPutPiece)n.message).getCoordinates() == action.intValue()){
					double val = legalActionsValues.get(action);
					double visits = legalActionsVisits.get(action);
					if(seedAverage){
						val = (val/visits) * 10.0;
						visits = 10.0;
					}
						
					n.setValue(val);
					n.setVisits(visits);
				}
			}
		}
		dbh.disconnect();
		D.ebugPrintlnINFO("Disconnected");
	}
	
	/**
	 * duplicates the brain information into a temporary brain object
	 * @param br
	 * @return
	 */
	private SOCRobotBrain copyBrainInfo(SOCRobotBrain br){
		SOCRobotBrain copy = new StacRobotDummyBrain(new SOCRobotClient(null, "inexistent", "dummyAgent", "", null),
				new SOCRobotParameters(300, 500, 0f, 0f, 0f, 0f, 0f, SOCRobotDMImpl.FAST_STRATEGY, 0),
				(SOCGame)DeepCopy.copy(br.getGame()),new CappedQueue(), br.getOurPlayerData().getPlayerNumber());
		//update trackers
        HashMap<Integer, SOCPlayerTracker> playerTrackers = new HashMap<>();
        HashMap<Integer, SOCPlayerTracker> original = br.getPlayerTrackers();
        for(int i = 0; i< 4; i++){
	      	 playerTrackers.put(i, (SOCPlayerTracker)DeepCopy.copy(original.get(i)));
	    }
        
        SOCGame g = copy.getGame();
        //recreate the links exactly as in client, but do not update, just replace the nonexistent trackers
        int n = g.maxPlayers;
    	for(int i = 0; i < n; i++){
			SOCPlayer p = g.getPlayer(i);
			p.setGame(g); //restore reference to this game in the player objects
		}
        for(int i = 0; i < n; i++){
			SOCPlayerTracker pti = playerTrackers.get(i);
			pti.setBrain(copy);
			//update the reference to the correct brain in PossibleCities and PossibleSettlements objects
			Iterator posCitiesIter = pti.getPossibleCities().values().iterator();
			while (posCitiesIter.hasNext())
	        {
	            SOCPossibleCity posCity = (SOCPossibleCity) posCitiesIter.next();
	            posCity.setBrain(copy);
	        }
			Iterator posSettlIter = pti.getPossibleSettlements().values().iterator();
			while (posSettlIter.hasNext())
	        {
	            SOCPossibleSettlement posSettl = (SOCPossibleSettlement) posSettlIter.next();
	            posSettl.setBrain(copy);
	        }
			pti.setPlayer(g.getPlayer(i));
		}
        ((StacRobotDummyBrain)copy).setPlayerTrackers(playerTrackers);
        
        //should also add our player tracker and our player data
        ((StacRobotDummyBrain)copy).setOurPlayerData(g.getPlayer(br.getOurPlayerData().getPlayerNumber()));
        ((StacRobotDummyBrain)copy).setOurPlayerTracker(playerTrackers.get(br.getOurPlayerData().getPlayerNumber()));
		
		return copy;
		
	}
	
	public List<SettlementNode> getLegalSettlements() {
		SOCPlayer player = brain.getOurPlayerData();
    	List<Integer> legalSettlements = player.getLegalSettlements(); //there is a set of legal actions

    	List<SettlementNode> possibleSettlements = new ArrayList<SettlementNode>(legalSettlements.size());
        for (Integer node : legalSettlements) {
            SettlementNode n = new SettlementNode(node, brain.getGame().getBoard());
            possibleSettlements.add(n);
        }
        return possibleSettlements;
    }
	
}
