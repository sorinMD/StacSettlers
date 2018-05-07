package soc.robot.stac.negotiationlearning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Vector;

import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCTradeOffer;
import soc.game.SOCResourceSet;
import simpleDS.util.StringUtil;
import soc.robot.SOCBuildPlanStack;
import soc.robot.SOCPossiblePiece;


public class BoardGameState {

	public static String getBasicRepresentation(SOCPlayer ourPlayerData) {
		int numDevCards = ourPlayerData.getDevCards().getTotal();
		//int numRoads = ourPlayerData.getRoads().size();
		int numSettlements = ourPlayerData.getSettlements().size();
		int numCities = ourPlayerData.getCities().size();
		//int totalPoints = ourPlayerData.getTotalVP();

		String longestRoad = ourPlayerData.hasLongestRoad() ? "1" : "0";
		String largestArmy = ourPlayerData.hasLargestArmy() ? "1" : "0";
		String buildups = "settlements="+numSettlements + "|cities="+numCities + "|longestRoad="+longestRoad + "|largestArmy="+largestArmy + "|devCards="+numDevCards;
		String resources = ourPlayerData.getResources().toString();

		String basicRepresentation = resources+"|"+buildups;
		ArrayList<String> list = StringUtil.getArrayListFromString(basicRepresentation, "|");
		//System.out.println("*S(basic)="+basicRepresentation + " size="+list.size());

		return basicRepresentation;
	}

	public static String getBoardRepresentation(SOCPlayer ourPlayerData, SOCGame game, SOCTradeOffer socOffer, SOCBuildPlanStack ourBuildingPlan) {
		String boardRepresentation = "";
		
		// get number of resources available
		String resources = ourPlayerData.getResources().toString();

		// get configuration of hexes on the board game 
		String hexes = getHexesState(game);

		// get configuration of nodes (intersections) on the board game
		String nodes = getNodesState(game, ourPlayerData);

		// get configuration of edges on the board game 
		String edges = getEdgesState(game, ourPlayerData);

		// get configuration of edges on the board game 
		String robber = getRobberState(game);

		// get number of turns of the game in turn
		String turns = "turns="+(float)game.getTurnCount()/10;
		//System.out.println("turns="+turns);

		//String piece = "piece="+getTargetPiece(ourBuildingPlan);
		
		//String offer = getGivablesReceivables(socOffer);

		// key-value-based representation
		boardRepresentation = resources+"|"+hexes+"|"+nodes+"|"+edges+"|"+robber+"|"+turns;//+"|"+piece;//+"|"+_offer;
		//boardRepresentation = resources+"|"+hexes+"|"+nodes+"|"+edges+"|"+robber+"|"+"|"+piece;
		//boardRepresentation = resources+"|"+hexes+"|"+nodes+"|"+edges+"|"+robber+"|"+turns+"|"+offer;
		//boardRepresentation = resources+"|"+hexes+"|"+nodes+"|"+edges+"|"+robber+"|"+piece+"|"+offer;
		
		//System.out.println("resources="+resources);
		/*System.out.println("hexes="+hexes);
		System.out.println("nodes="+nodes);
		System.out.println("edges="+edges);
		System.out.println("robber="+robber);
		System.out.println("turns="+turns);
		System.out.println("piece="+piece);
		ArrayList<String> list = StringUtil.getArrayListFromString(boardRepresentation, "|");
		System.out.println("*S(board)="+boardRepresentation + " size="+list.size());
		System.out.println();*/
		
		// numerical-based representation
		String stateRepresentation = "";
		int maxres = 10;
		int counter = 0;
		for (String pair : StringUtil.getArrayListFromString(boardRepresentation, "|")) {
			if (pair.startsWith("unknown")) continue;
			String key = pair.substring(0, pair.indexOf("="));
			String val = pair.substring(pair.indexOf("=")+1);
			float value = Float.parseFloat(val);
			float newValue = (value>maxres) ? 1 : (float)value/maxres;
			stateRepresentation += (stateRepresentation.equals("")) ? ""+newValue : ","+newValue;
			counter++;
		}
		//System.out.println("*S(board)="+stateRepresentation + " size="+counter);

		return stateRepresentation;
	}

	private static String getGivablesReceivables(SOCTradeOffer offer) {
		if (offer == null) {
			//String givables = "clay=0|ore=0|sheep=0|wheat=0|wood=0";
			//String receivables = "clay=0|ore=0|sheep=0|wheat=0|wood=0";
			String givables = "giv1=0|giv2=0";
			String receivables = "rec1=0|rec2=0";
			String trade = givables + "|" + receivables;
			return trade;

		} else {
			ArrayList<String> resources = new ArrayList<String>();
			resources.add("null");
			resources.add("clay");
			resources.add("ore");
			resources.add("sheep");
			resources.add("wheat");
			resources.add("wood");

			String givables = offer.getGiveSet().toString();
			String receivables = offer.getGetSet().toString();

			ArrayList<String> givList = getPairOfResources(givables, resources);
			ArrayList<String> recList  = getPairOfResources(receivables, resources);
			
			String giv1 = "giv1="+givList.get(0);
			String giv2 = (givList.size()>1) ? "giv2="+givList.get(1) : "giv2=0";
			String rec1 = "rec1="+recList.get(0);
			String rec2 = (recList.size()>1) ? "rec2="+recList.get(1) : "rec2=0";
			
			String trade = giv1 + "|" + giv2 + "|" + rec1 + "|" + rec2;
			//System.out.println("givables="+givables + " receivables="+receivables + " T="+trade);

			return trade;
		}
	}
	
	private static ArrayList<String> getPairOfResources(String resStr, ArrayList<String> resBase) {
		ArrayList<String> resultList = new ArrayList<String>();
		for (String givRes : StringUtil.getArrayListFromString(resStr, "|")) {
			if (givRes.startsWith("unknown") || givRes.endsWith("=0")) {
				continue;
				
			} else {
				String key = givRes.substring(0, givRes.indexOf("="));
				String val = givRes.substring(givRes.indexOf("=")+1);
				int index = resBase.indexOf(key);
				if (val.equals("0")) {
					continue;
				} else if (val.equals("1")) {
					resultList.add(""+index);
				} else {
					resultList.add(""+index);
					resultList.add(""+index);
				}
			}
		}

		return resultList;
	}

	private static String getGivablesReceivables_old(SOCTradeOffer offer) {
		if (offer == null) {
			String givables = "clay=0|ore=0|sheep=0|wheat=0|wood=0";
			String receivables = "clay=0|ore=0|sheep=0|wheat=0|wood=0";
			String trade = givables + "|" + receivables;
			return trade;

		} else {
			String givables = offer.getGiveSet().toString();
			int givIndex = givables.indexOf("|unknown");
			givables = givIndex>0 ? givables.substring(0, givIndex) : givables;

			String receivables = offer.getGetSet().toString();
			int recIndex = receivables.indexOf("|unknown");
			receivables = recIndex>0 ? receivables.substring(0, recIndex) : receivables;

			String trade = givables + "|" + receivables;
			//System.out.println("givables="+givables + " receivables="+receivables + " trade="+trade);

			return trade;
		}
	}

	private static String getHexesState(SOCGame game) {
		int[] landCoords = game.getBoard().getHexLandCoords();
		ArrayList<String> coords = new ArrayList<String>();
		for (int i=0; i<landCoords.length; i++) {
			int coord = landCoords[i];
			coords.add(""+coord);
		}
		Collections.sort(coords);
		String hexes = "";
		for (String elem : coords) {
			int coord = Integer.parseInt(elem);
			int type = game.getBoard().getHexTypeFromCoord(coord);
			String pair = "hex"+coord + "=" + type;
			hexes += (hexes.equals("")) ? pair : "|" + pair;
		}
		return hexes;
	}

	private static String getNodesState(SOCGame game, SOCPlayer ourPlayerData) {
		// get cities
		HashMap<String,Integer> myCities = getMapOfResources(game.getBoard().getCities());
		HashMap<String,Integer> myOwnCities = getMapOfResources(ourPlayerData.getCities());
		//System.out.println("myCities="+myCities + " myOwnCities="+myOwnCities);

		// get settlements
		HashMap<String,Integer> mySettlements = getMapOfResources(game.getBoard().getSettlements());
		HashMap<String,Integer> myOwnSettlements = getMapOfResources(ourPlayerData.getSettlements());
		//System.out.println("mySettlements="+mySettlements + " myOwnSettlements="+myOwnSettlements);

		// get nodes
		ArrayList<String> nodes = new ArrayList<String>();
		for (Object node : game.getBoard().nodesOnBoard.keySet()) {
			int nodeID = ((Integer) node).intValue();
			nodes.add(""+nodeID);
		}
		Collections.sort(nodes);
		String intersections = "";
		for (String elem : nodes) {
			int nodeID = Integer.parseInt(elem);
			int type = 0;
			if (mySettlements.containsKey(""+nodeID)) {
				type = (myOwnSettlements.containsKey(""+nodeID)) ? 3 : 1;
				//System.out.println("Seetlement found @ nodeID="+nodeID);
			} else if (myCities.containsKey(""+nodeID)) {
				type = (myOwnCities.containsKey(""+nodeID)) ? 4 : 2;
				//System.out.println("City found @ nodeID="+nodeID);
			}

			String pair = "node"+nodeID + "=" + type;
			intersections += (intersections.equals("")) ? pair : "|" + pair;
		}

		return intersections;
	}

	private static String getEdgesState(SOCGame game, SOCPlayer ourPlayerData) {
		// get roads
		HashMap<String,Integer> myRoads = getMapOfResources(game.getBoard().getRoads());
		HashMap<String,Integer> myOwnRoads = getMapOfResources(ourPlayerData.getRoads());
		//System.out.println("myRoads="+myRoads + " myOwnRoads="+myOwnRoads);

		// get edges
		ArrayList<String> edges = new ArrayList<String>();
		for (Object node : game.getBoard().nodesOnBoard.keySet()) {
			int nodeID = ((Integer) node).intValue();
			Vector list = game.getBoard().getAdjacentNodesToNode(nodeID);
			for (int j=0; j<list.size(); j++) {
				int nodeID2 = ((Integer) list.get(j)).intValue();
				if (nodeID == nodeID2) continue;
				int edgeNum = game.getBoard().getEdgeBetweenAdjacentNodes(nodeID, nodeID2);
				if (!edges.contains(""+edgeNum)) {
					//boolean adjacent = game.getBoard().isEdgeAdjacentToNode(nodeID, nodeID2);
					edges.add(""+edgeNum);
				}
			}
		}
		Collections.sort(edges);
		//IOUtil.printArrayList(edges, "|edges|:"+edges.size());
		String paths = "";
		for (String elem : edges) {
			int nodeID = Integer.parseInt(elem);
			int type = 0;
			if (myRoads.containsKey(""+nodeID)) {
				type = (myOwnRoads.containsKey(""+nodeID)) ? 2 : 1;
				//System.out.println("Road found @ nodeID="+nodeID);
			}

			String pair = "road"+nodeID + "=" + type;
			paths += (paths.equals("")) ? pair : "|" + pair;
		}

		return paths;
	}

	private static String getRobberState(SOCGame game) {
		int robberHex = game.getBoard().getRobberHex();
		int robberHexType = game.getBoard().getHexTypeFromCoord(robberHex);

		// 0=dessert, 1=clay, 2=ore, 3=sheep, 4=wheat, 5=wood
		String robber = "robber="+robberHexType;
		//String robber = (robberHexType == 0) ? "0" : "1";

		return robber;
	}

	private static HashMap<String,Integer> getMapOfResources(Vector vector) {
		HashMap<String,Integer> map = new HashMap<String,Integer>();
		for (int i=0; i<vector.size(); i++) {
			SOCPlayingPiece piece = (SOCPlayingPiece) vector.get(i);
			int id = piece.getCoordinates();
			int type = piece.getType();
			map.put(""+id, new Integer(type));
		}
		//IOUtil.printHashMap(map, "map");
		return map;
	}
	
	private static String getTargetPiece(SOCBuildPlanStack ourBuildingPlan) {
		if (ourBuildingPlan == null || ourBuildingPlan.size()==0) {
			return "0";
		} else {
			SOCPossiblePiece plannedPiece = ourBuildingPlan.getPlannedPiece(0);
			return ""+(1+plannedPiece.getType());
		}
	}
	
	/*private static String getOffer(SOCTradeOffer offer) {
		String trade = null;
		
		if (offer == null) {
			for (int i=0; i<10; i++) {
				String resid = "res_offer"+i+"=0";
				trade += (i==0) ? resid : "|"+resid; 
			}
		} else {
			trade = getGivablesReceivables(offer);
		}
		
		return trade;
	}*/
}
