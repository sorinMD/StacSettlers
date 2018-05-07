package soc.robot.stac.flatmcts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import soc.disableDebug.D;
import soc.game.SOCBoard;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.message.SOCChoosePlayer;
import soc.message.SOCMoveRobber;
import soc.robot.SOCBuildingSpeedEstimate;
import soc.robot.SOCPlayerTracker;
import soc.robot.SOCRobotBrain;
import soc.robot.stac.Hex;
import soc.robot.stac.SettlementNode;

/**
 * Implementation of a seeder that uses JSettlers heuristics for taking decisions and adds a value to the the corresponding tree nodes.
 * @author MD
 *
 */
public class JSettlersSeeder implements Seeder{

	/**
	 * everything that we need will be contained in this object
	 */
	private SOCRobotBrain brain;
	/**
	 * Constructor. All we need is the brain
	 */
	public JSettlersSeeder(SOCRobotBrain b){
		brain = b;
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
	 * Seeds the tree with values based on JSettlers logic for the Robber action. The values are equal to the ETB Difference (immediate-initial) for each choice of location,
	 * while times visited is equal to the maximum one, without propagating up the tree. 
	 * (In an ideal case we would like to have done this during the same time as the expansion part of the algorithm TODO optimise it)
	 */
	private void seedForRobberAction(TreeNode node){
		
		SOCGame game = brain.getGame();
		int ourPlayerNumber = brain.getOurPlayerData().getPlayerNumber();
		
		int[] winGameETAs = new int[game.maxPlayers];
	    for (int i = game.maxPlayers - 1; i >= 0; --i)
	    	winGameETAs[i] = 100;
	    Map<Integer, SOCPlayerTracker> playerTrackers = new HashMap<>();
	    playerTrackers = brain.getPlayerTrackers();
	     
	    Iterator trackersIter = playerTrackers.values().iterator();
	    while (trackersIter.hasNext()){
	        SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();
	        try{
	        	winGameETAs[tracker.getPlayer().getPlayerNumber()] = tracker.getWinGameETA();//It should have been stored before saving the game
	        }catch (NullPointerException e){
	             winGameETAs[tracker.getPlayer().getPlayerNumber()] = 500;
	        }
	    }
	    int victimNum = -1;
	    for (int pnum = 0; pnum < game.maxPlayers; pnum++){
	    	if (! game.isSeatVacant(pnum)){
	    		if ((victimNum < 0) && (pnum != ourPlayerNumber)){
	    			// The first pick
	                victimNum = pnum;
	    		}else if ((pnum != ourPlayerNumber) && (winGameETAs[pnum] < winGameETAs[victimNum])){
	    			// A better pick
	    			victimNum = pnum;
	    		}
	        }
	    }
	    SOCPlayer victim = game.getPlayer(victimNum);
		
		//get the initial ETB for the player
		int initialETB = 0;
		SOCBuildingSpeedEstimate estimator = brain.getEstimator();
		estimator.recalculateEstimates(victim.getNumbers());
	    int[] speeds = estimator.getEstimatesFromNothingFast(victim.getPortFlags());
	    for (int j = SOCBuildingSpeedEstimate.MIN;j < SOCBuildingSpeedEstimate.MAXPLUSONE; j++){
	    	initialETB += speeds[j];
	    }
	
	    //get the hexes list and their estimated value (immediateETB-initialETB), while remembering the highest value
	    int robberHex = game.getBoard().getRobberHex();
	    int bestValue = 0;
	    SOCPlayer ourPlayer = game.getPlayer(ourPlayerNumber);
	    final boolean skipDeserts = game.isGameOptionSet("RD");  // can we move robber to desert?
	    SOCBoard gboard = (skipDeserts ? game.getBoard() : null);
	    int[] hexes = game.getBoard().getHexLandCoords();
	    Map<Integer,Integer> choices = new HashMap<>();
	    
	    for (int i = 0; i < hexes.length; i++){
	        /**
	         * only check hexes that we're not touching,
	         * and not the robber hex, and possibly not desert hexes as per JSettlers logic
	         */
	        if ((hexes[i] != robberHex)&& ourPlayer.getNumbers().getNumberResourcePairsForHex(hexes[i]).isEmpty()
	                && ! (skipDeserts && (gboard.getHexTypeFromCoord(hexes[i]) == SOCBoard.DESERT_HEX ))){
	            
	        	estimator.recalculateEstimates(victim.getNumbers(), hexes[i]);
	            speeds = estimator.getEstimatesFromNothingFast(victim.getPortFlags());
	            int immediateETB = 0;
	            int value = 0;
	
	            for (int j = SOCBuildingSpeedEstimate.MIN;j < SOCBuildingSpeedEstimate.MAXPLUSONE; j++){
	                immediateETB += speeds[j];
	            }
	            value = immediateETB - initialETB;
	            
	            //remember the choice and its value
	            choices.put(hexes[i], value);
	            
	            //also if this is the best one, remember it
	            if (value > bestValue){
	                bestValue = value;
	            }
	        }
	    }
	    
	    //normalise as the values sometimes are too big and there is a lack of exploration
	    int factor = (int) ((bestValue/10) + 1); //keep everything under 10
		
	    //take the root of the tree and for each children check if there is a correspondent in the map. if yes update the times the node has been visited = bestValue; and value = the correspondents value
	    //if the actual value = 0, do not initialise
	    TreeNode[] nodes = node.children;
	    for(TreeNode n : nodes){
	    	if(choices.containsKey(((SOCMoveRobber)n.message).getCoordinates())){
	    		int value = choices.get(((SOCMoveRobber)n.message).getCoordinates());
	    		if(value > 0){//if the actual value = 0, do not initialise
	    			n.setVisits(bestValue/(double)factor);
	    			n.setValue(value/(double)factor);
	    			//also check if it has multiple options of players to rob from
	    			if(!n.isLeaf()){
	    				TreeNode[] children = n.children;
	    				for(TreeNode c : children){
	    					// the child that has the choice message with the victim number gets the same values/visits as the parent
	    					if(((SOCChoosePlayer)c.message).getChoice() == victimNum){
	    						c.setVisits(bestValue/(double)factor);
	    						c.setValue(value/(double)factor);
	    					}
	    				}
	    			}
	    		}
	    	}
	    }
	}
		
	/**
	 * @return a list of all the legal settlement nodes given the game state
	 */
    public List<SettlementNode> getLegalSettlements() {
        SOCGame game = brain.getGame();
    	SOCPlayer p = game.getPlayer(brain.getOurPlayerData().getPlayerNumber()); //apparently it doesn't matter which player number
    	List<Integer> legalSettlements = p.getLegalSettlements();

    	List<SettlementNode> possibleSettlements = new ArrayList<SettlementNode>(legalSettlements.size());
        for (Integer node : legalSettlements) {
            SettlementNode n = new SettlementNode(node, game.getBoard());
            possibleSettlements.add(n);
        }
        rankSettlements(possibleSettlements, true); //rank them here to have the same order when seeding
        return possibleSettlements;
    }
    
    /**
     * A method for ranking the settlements following the JSettlers logic
     * @param nodes the legal locations to place a settlement
     * @param considerCurrent of course we want to consider current....
     */
    private void rankSettlements(List<SettlementNode> nodes, boolean considerCurrent) {
        for (SettlementNode node : nodes) {
            node.setScore(getScore(node, considerCurrent));
        }		
        Collections.sort(nodes);
    }
    
    /**
     * 
     * @param node the settlement t assess the value for
     * @param considerCurrent if we want to consider its utility in regards to the previous settlements
     * @return a value
     */
    private double getScore(SettlementNode node, boolean considerCurrent) {
    	SOCGame game = brain.getGame();
    	List<SettlementNode> nodes = new ArrayList<SettlementNode>();
        nodes.add(node);
        if (considerCurrent) {
        	for(Object o : game.getPlayer(brain.getOurPlayerData().getPlayerNumber()).getSettlements()){
        		SettlementNode n = new SettlementNode(((SOCPlayingPiece)o).getCoordinates(), game.getBoard());
        		nodes.add(n);
        	}
        }
        return getScore(nodes);		
    }	
    
    /**
     * Same logic as in STACRobotDM.
     * @param nodes
     * @return
     */
    private double getScore(List<SettlementNode> nodes) {
        int[] numPerRoll = new int[13];  
        Map<Integer, Integer> numPerHex = new HashMap<Integer, Integer>();
        for (SettlementNode node : nodes) {
            // Note: if we are evaluating a node without a settlement, it's a potential settlement, so treat it as one here
            int m = Math.max(node.getIncome(), 1);
            for (Hex hex : node.getHexes()) {
                numPerRoll[hex.getRoll()] += m;
                Integer nph = numPerHex.get(Integer.valueOf(hex.getCoord()));
                if (nph==null) {
                    nph = Integer.valueOf(m);
                }
                else {
                    nph = Integer.valueOf(nph + m);
                }
                numPerHex.put(Integer.valueOf(hex.getCoord()), nph);
            }
        }		

        // discount factors - to be experimented with.
        double doubleRollDiscount = 0.95;
        double doubleHexDiscount = 0.7; // Note that double hex is also a double roll by definition

        double[] resourcePer36 = new double[6]; // robber is zero		
        double totalResPer36 =0;

        boolean[] port = new boolean[6];
        // Now loop through again and increment
        for (SettlementNode node : nodes) {
            // Note: if we are evaluating a node without a settlement, it's a potential settlement, so treat it as one here
            int m = Math.max(node.getIncome(), 1);
            for (Hex hex : node.getHexes()) {
                double mult = m;
                if (numPerRoll[hex.getRoll()] > 1) {
                    mult *= doubleRollDiscount;
                    if (numPerHex.get(hex.getCoord()).intValue() > 1) {
                        mult *= doubleHexDiscount;
                    }
                }
                resourcePer36[hex.getType()] += mult * hex.getRollsPer36();
                if (hex.getType()>0) {
                    totalResPer36 += mult * hex.getRollsPer36();
                }
            }

            if (node.getPortType() >= 0) {
                port[node.getPortType()] = true;
            }
        }

        // now calculate score...
        double score = 0;

        // Add basic scores
        score += resourcePer36[SOCResourceConstants.CLAY];
        score += resourcePer36[SOCResourceConstants.WOOD];
        score += resourcePer36[SOCResourceConstants.WHEAT];
        score += resourcePer36[SOCResourceConstants.SHEEP];
        score += resourcePer36[SOCResourceConstants.ORE];

        //  The number of resources of a given type we can expect to get through bank/port trades
        double[] portRes = new double[6];
        double[] portOrRollPer36 = new double[6];	
        for (int i=1; i<6; i++) {
            for (int j=1; j<6; j++) {
                if (j!=i) {
                    // Determine the exchange rate based on whether ports are owned
                    if (port[j]) {
                        portRes[i] += resourcePer36[j] / 2.0;
                    }
                    else if (port[0]) {
                        portRes[i] += resourcePer36[j] / 3.0;
                    }
                    else {
                        portRes[i] += resourcePer36[j] / 4.0;
                    }
                }
            }
            portOrRollPer36[i] = resourcePer36[i] + portRes[i];
        }
        // ROAD
        double favour_roads_factor = 0.5;
        score += 0.5 * Math.pow(portOrRollPer36[SOCResourceConstants.CLAY] 
                * portOrRollPer36[SOCResourceConstants.WOOD]
                        , favour_roads_factor);

        // SETTLMENT
        double favour_settlements_factor = 0.25;
        score += Math.pow(portOrRollPer36[SOCResourceConstants.CLAY] 
                * portOrRollPer36[SOCResourceConstants.WOOD]
                        * portOrRollPer36[SOCResourceConstants.WHEAT]
                                * portOrRollPer36[SOCResourceConstants.SHEEP]
                                        , favour_settlements_factor);

        // CITY
        double favour_cities_factor = 0.2;
        score += Math.pow(Math.pow(portOrRollPer36[SOCResourceConstants.ORE], 3) 
                * Math.pow(portOrRollPer36[SOCResourceConstants.WHEAT], 2)
                , favour_cities_factor);
                  
        // DEV CARD
        double favour_dev_cards_factor = 0.3333;
        score += 0.25 * Math.pow(portOrRollPer36[SOCResourceConstants.ORE] 
                * portOrRollPer36[SOCResourceConstants.SHEEP]
                        * portOrRollPer36[SOCResourceConstants.WHEAT]
                                , favour_dev_cards_factor);
        return score;
    }
    /**
     * This assumes that the tree expansion has been done and that a ranking has been performed so the children array is already ordered.
     * @param root the parent node or current state
     */
    private void seedForInitialPlacement(TreeNode root){
    	List<SettlementNode> possibleSettlements = getLegalSettlements();
//    	rankSettlements(possibleSettlements, true); //already done in getLegalSettlements
    	//get the best as the first
    	double bestScore = (int) possibleSettlements.get(0).getScore();
    	//choose normalisation factor based on the range of the highest value
    	int factor = (int) ((bestScore/10) + 1); //keep everything under 10
    	//loop through all the children and seed in with the corresponding values
    	TreeNode[] nodes = root.children;
        for (int i=0; i < possibleSettlements.size(); i++) {
        	nodes[i].setVisits(bestScore/(double)factor);
        	nodes[i].setValue(possibleSettlements.get(i).getScore()/(double)factor);
        }
    }
    /**
     * Only used for manual simulations atm.
     * @return the coordinate for the settlement considered best by JSettlers
     */
    public int getNextInitialSettlement() { 
        List<SettlementNode> legalSettlements = getLegalSettlements();
//        rankSettlements(legalSettlements, true);
        return legalSettlements.get(0).getNode();
    }

}
