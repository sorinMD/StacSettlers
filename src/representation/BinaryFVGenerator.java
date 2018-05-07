package representation;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import org.apache.logging.log4j.core.pattern.AbstractStyleNameConverter.White;

import mcts.game.catan.Board;
import mcts.game.catan.GameStateConstants;
import soc.game.SOCBoard;
import soc.game.SOCGame;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.server.database.stac.ExtGameStateRow;
import soc.server.database.stac.GameActionRow;
import soc.server.database.stac.ObsGameStateRow;
import soc.server.database.stac.StacDBHelper;


/**
 * Class for generating the feature vector from a state description for both JSettlers and Smartsettlers. It has the same functionality 
 * as the {@link NumericalFVGenerator} class, only that the resulting vector contains binary features.
 * @author MD
 */
public class BinaryFVGenerator extends FeatureVectorGenerator implements BinaryFeatureVectorOffsets,GameStateConstants {
    
	/**
     * Taken from SOCBoard in order to find the index in the layout arrays
     */
	private final int[] numToHexID = 
    {
        0x17, 0x39, 0x5B, 0x7D,
        
        0x15, 0x37, 0x59, 0x7B, 0x9D,
        
        0x13, 0x35, 0x57, 0x79, 0x9B, 0xBD,
        
        0x11, 0x33, 0x55, 0x77, 0x99, 0xBB, 0xDD,
        
        0x31, 0x53, 0x75, 0x97, 0xB9, 0xDB,
        
        0x51, 0x73, 0x95, 0xB7, 0xD9,
        
        0x71, 0x93, 0xB5, 0xD7
    };
    
    
    /**
     * The list of states for which this class can provide specific vectors
     */
	public static final int[] states ={SOCGame.START1A, SOCGame.START1B, SOCGame.START2A, SOCGame.START2B, 
		SOCGame.PLAY, SOCGame.PLAY1, SOCGame.WAITING_FOR_DISCARDS, SOCGame.WAITING_FOR_CHOICE, //waiting for choice may be together with placing robber
		SOCGame.PLACING_FREE_ROAD1, SOCGame.PLACING_FREE_ROAD2, SOCGame.PLACING_ROBBER, SOCGame.WAITING_FOR_MONOPOLY, 
		SOCGame.WAITING_FOR_DISCOVERY}; 
    
    /**
     * Sets the correct types and corresponding sizes of the feature vectors
     */
    public static void initialiseGenerator(){
    	REWARD_FUNCTION_SIZE = STATE_VECTOR_SIZE;
    }
	
	/**
	 * This is the current feature vector implementation
	 * Describes a given state based on the observable and extracted features. This is an approximate description, 
	 * so it excludes coordinates or exact board description. 
	 * It improves the previous implementation in the sense that it adds info to the vector based on the order of play
	 * It doesn't contain any JSettlers heuristics or difficult to compute heuristics as it will take to long to 
	 * compute these during SS simulations, but these can be easily added;
	 * @param ogsr the observable features of the before state
	 * @return the feature vector describing the before state containing numerical values
	 */
	private int[] calculateStateVector(ObsGameStateRow ogsr, ExtGameStateRow egsr){
		int[] vector = new int[STATE_VECTOR_SIZE];
		Arrays.fill(vector, 0);//fill it up with zeros
		
		vector[OFS_BIAS_FEATURE] = 1;
		int dice = ogsr.getDiceResult();
		if(!(dice <= 0)){
			if(dice == 7)
				vector[OFS_DICERESULT] = 1;
			else
				vector[OFS_DICERESULT + reduceNumberRange(dice) - 1] = 1;
		}
		
		//current pieces on board of each type
		Integer[][] pob = ogsr.getPiecesOnBoard();

		int rd = 0, se = 0, ct = 0;
        //this reflects how crowded the board is
        for(Integer[] p : pob){
        	if(p[0] == SOCPlayingPiece.ROAD){
        		rd++;
        	}else if(p[0] == SOCPlayingPiece.SETTLEMENT){
        		se++;
        	}else if(p[0] == SOCPlayingPiece.CITY){
        		ct++;
        	}//otherwise we ignore (we shouldn't get here!)
        }
		int total = rd+se+ct;
		int prodPieces = se + ct;
		
		
		//max 20 settlements, 16 cities, 60 roads, total 96, production pieces 36 and we want 5 classes of each
		se/=5;
		rd/=15;
		ct/=4;
		total/=24;
		prodPieces/=9;		
		
		vector[OFS_TOTALSETTLEMENTS + se] = 1;
		vector[OFS_TOTALCITIES + ct] = 1;
		vector[OFS_TOTALROADS + rd] = 1;
		
		/*
		 * Alternatively we just have 5 classes for prod pieces and 5 for roads
		 * NOTE: if this is uncommented you need to select the right offsets for these values
		 * vector[idx + prodPieces] = 1;
		 * idx +=5;
		 * vector[idx + rd] = 1;
		 * idx +=5;
		 * 
		 * or just 5 overall,
		 * vector[idx + total] = 1;
		 * idx +=5;
		 * 
		 */
		
        //has the current player played a dev card?
		if(ogsr.hasPlayedDevCard())
			vector[OFS_HASPLAYEDDEVCARD] = 1;

		
        //logic for calculating the player's position in turn based on board position and starting player
		int cpn = ogsr.getCurrentPlayer();//the current player is the one that executes the next action (only when trading this rule is broken)
		int spn = ogsr.getStartingPlayer();
		int pos;
		if(spn == cpn)
			pos = 1; //the first in a round
		else{
			pos = 2;
			for(int i = spn + 1; i != spn; i++){
				if(i == 4)
					i = 0; 
				if(i == cpn){
					break; //found him so we can break
				}else{
					pos++;
				}
			}
		}
		vector[OFS_BOARDPOSITION + pos-1] = 1;
		
		//are there anymore cards left in the deck
		if(ogsr.getDevCardsLeft()>0)
			vector[OFS_ANYDEVCARDSLEFT] = 1;
		
        /*
         * For the robber effect;
		 * from the robber coordinate get index for hexLayout and NumLayout
		 * from the index the number and hex contents; 
		 * if its on the desert ignore the following
		 * make a list of adjacent nodes coord;
		 */
        int robberHex = ogsr.getRobberHex();
        int idx = -1;
        for(int i = 0; i < numToHexID.length; i++){
        	if(robberHex == numToHexID[i]){
        		idx = i;
        		break;
        	}
        }
        int robberHexType = 0;
        int robberHexNumber = 0;
        if(idx!=-1){
        	robberHexType = StacDBHelper.transformToIntArr(ogsr.getHexLayout())[idx];
        	robberHexNumber = reduceNumberRange(StacDBHelper.transformToIntArr(ogsr.getNumberLayout())[idx]);
        }else{
        	System.err.println("Cannot find robber hex, maybe wrong coord value?"); // quick debugging
        }
        int[] adjNodes = null;
        if(robberHexType != SOCBoard.DESERT_HEX){
        	adjNodes = SOCBoard.getAdjNodesToHex(robberHex);
        }
		
		if(robberHexNumber > 0){
			vector[OFS_RESOURCEBLOCKED + robberHexType - 1] = 1;
			vector[OFS_NUMBERBLOCKED + robberHexNumber - 2] = 1;
		
		}
		//remember the player numbers based on the playing order from the current pn onwards
		int[] playingOrderPn = new int[4];//starts with current player number and ends with previous player number
		playingOrderPn[0] = cpn;
		for(int i = 1; i < 4; i++){
			playingOrderPn[i] = cpn + i;
			if(playingOrderPn[i] > 3)
				playingOrderPn[i] = playingOrderPn[i] - 4;
		}
		
		int pn = playingOrderPn[0];//this player
	
		int score = ogsr.getTotalVP(pn);
		if(score > 0)//for 0 we don't set any features
			if(score <= 10){
				vector[OFS_PLAYERSCORE + score - 1] = 1;
			}else
				vector[OFS_PLAYERSCORE + 9] = 1;//the 11 and 12 vp cases
		
		vector[OFS_LA] = ogsr.getLALabel(pn);
		vector[OFS_LR] = ogsr.getLRLabel(pn);

		int[] settCoords = ogsr.getSettlementsForPlayer(pn);
		int[] cityCoords = ogsr.getCitiesForPlayer(pn);
		if(ogsr.getRoadsForPlayer(pn).length < 15)
			vector[OFS_ROADS + ogsr.getRoadsForPlayer(pn).length/3] = 1;
		else
			vector[OFS_ROADS + N_PLANES - 1] = 1;
		if(settCoords.length > 0)
			vector[OFS_SETTLEMENTS + settCoords.length -1] = 1;
		if(cityCoords.length > 0)
			vector[OFS_CITIES + cityCoords.length -1] = 1;
		
        //5 positions for resources in hand 
        int[] rss = ogsr.getResources(pn);
        if(rss[5]!=0){ //we shouldn't have unknowns stored in db; 
        	System.err.println("Unknown rss in player hand");//quick debugging TBR soon
        }		        	
		for(int j = 0; j < 5; j++){
			if(rss[j] > 0)
				if(rss[j] < 5){
		        	vector[OFS_CLAYINHAND + j*5 + rss[j] - 1] = 1;
				}else
					vector[OFS_CLAYINHAND + j*5 + 4] = 1;
		}
        
		//6 positions for what port types is this player touching
		int[] tpt = ogsr.getTouchingPortTypes(pn);
		for(int k = 0; k < tpt.length; k++){
			vector[OFS_TOUCHING_PORTS+k] = tpt[k];
		}
		
        int[] numbers = new int[5];
        int[] access = new int[5];
		
		//5 positions for getting the sum of numbers on hexes that this player touches attached to each rss type 
		int[][] tn = StacDBHelper.transformToIntArr2(ogsr.getTouchingNumbers(pn));
		int num = 0;
		for(int j = 0; j < 5; j++){
			for(int k : tn[j]){
				num = reduceNumberRange(k);
				numbers[j] += num;
			}
		}
						
		//get the array that contains how much access this player has to a rss type in terms of adjacent number of settlements and cities
		int[] rtn = new int[5];
		rtn = StacDBHelper.transformToIntArr(egsr.getRssTypeAndNumber(pn));
		for(int j = 0; j < 5; j++){
			access[j] = rtn[j];
		}
		
        for(int j = 0; j < 5; j++){
        	if(numbers[j] > 0)
	        	if(numbers[j] <= 40){//let's say a maximum of 40
	        		vector[OFS_CLAYPRODUCTION + j + numbers[j]/5] = 1;
	        	}else
	        		vector[OFS_CLAYPRODUCTION + j + 8] = 1;
        	if(access[j] > 0)
	        	if(access[j] <= 8){//let's say a maximum of 8
	        		vector[OFS_CLAYACCESS + j + access[j] - 1] = 1;
	        	}else
	        		vector[OFS_CLAYACCESS + j + 8 - 1] = 1;
        }
		
     	//number of production pieces affected by robber
		int prod = 0;
		boolean affectedByRobber = false;
		if(adjNodes!=null){
			for(int sc : settCoords){
				for(int c : adjNodes){
					if(sc==c){
						affectedByRobber = true;
						prod++;
					}
				}
			}
			for(int cc : cityCoords){
				for(int c : adjNodes){
					if(cc==c){
						affectedByRobber = true;
						prod+=2;
					}
				}
			}
		}

        //is affected by robber flag
        if(prod > 0){
        	vector[OFS_AFFECTEDBYROBBER] = 1;
    		vector[OFS_NPIECESAFFECTED + prod - 1] = 1;
        }
		
		//the number of knights played;
        int playedKnights = ogsr.getPlayedKnights(pn);
        if(playedKnights > 0)
        	if(playedKnights < 5){
        		vector[OFS_NPLAYEDKNIGHTS + playedKnights - 1] = 1;
        	}else
        		vector[OFS_NPLAYEDKNIGHTS + 4] = 1;
		
        //longest current road
        int longestRoad = egsr.getLongestRoads(pn);
        if(longestRoad > 0)
        	vector[OFS_CURRENTLONGESTROAD + longestRoad -1] = 1;
        
		//3 positions for used special dev cards in hand;
        int playedRb = ogsr.getPlayedRB(pn);
        int playedDisc = ogsr.getPlayedDisc(pn);
        int playedMono = ogsr.getPlayedMono(pn);
        
        if(playedRb > 0)
        	vector[OFS_PLAYEDDEVCARDSINHAND + playedRb -1] = 1; 
        if(playedDisc > 0)
        	vector[OFS_PLAYEDDEVCARDSINHAND + 1 + playedDisc -1] = 1;
        if(playedMono > 0)
        	vector[OFS_PLAYEDDEVCARDSINHAND + 2 + playedMono -1] = 1;
        
        //4 positions for old unplayed dev cards in hand;
        int[] dc = ogsr.getUnplayedDevCards(pn);
		for(int j = 0; j < 4; j++){
			vector[OFS_OLDDEVCARDSINHAND + j] = dc[j] > 0 ? 1 : 0;
		}
		
		//4 positions for new dev cards
		int[] ndc = ogsr.getNewDevCards(pn);
		for(int j = 0; j < 4; j++){
			vector[OFS_NEWDEVCARDSINHAND +j] = ndc[j] > 0 ? 1 : 0;
		}
		//5 positions for VP cards which can't be played
		int vpCards = ogsr.getVictoryDevCards(pn) + ndc[4];
		if(vpCards > 0)
			vector[OFS_VPCARDS + vpCards -1] = 1;
		
		int cardsRequired = 4;
		if(vector[OFS_TOUCHING_PORTS]==1)
			cardsRequired = 3;
		for(int j = 0; j<5; j++){
			//if it has a port of this type and more than 2 rss
			if(vector[OFS_TOUCHING_PORTS + j + 1]==1 && vector[OFS_CLAYINHAND + j]>=2){
				vector[OFS_CANBANKORPORTTRADE] = 1;
				break;
			}else if(vector[OFS_CLAYINHAND + j]>=cardsRequired){
				vector[OFS_CANBANKORPORTTRADE] = 1;
				break;
			}
		}
		
		//to describe that enough rss exist
		boolean clay = false, ore = false, sheep = false, wheat = false, wood = false, wheat2 = false, ore3 = false;
		for(int k = 0; k < N_PLANES; k++){
			if(vector[OFS_CLAYINHAND + k]>0)
				clay = true;
			if(vector[OFS_WOODINHAND + k]>0)
				wood = true;
			if(vector[OFS_SHEEPINHAND + k]>0)
				sheep = true;
			if(vector[OFS_WHEATINHAND + k]>0)
				wheat = true;
			if(vector[OFS_OREINHAND + k]>0)
				ore = true;
			if(vector[OFS_WHEATINHAND + k]>0 && k >= 1)
				wheat2 = true;
			if(vector[OFS_OREINHAND + k]>0 && k >= 2)
				ore3 = true;
		}
		
		if(clay && wood)
			vector[OFS_CANBUILDROAD] = 1;
		if(clay && wood && wheat && sheep)
			vector[OFS_CANBUILDSETTLEMENT] = 1;		
		if(ore && sheep && wheat)
			vector[OFS_CANBUYCARD] = 1;
		if(ore3 && wheat2)
			vector[OFS_CANBUILDCITY] = 1;
		
        //one position for describing if this player has over 7 resources;
		int sum = 0;
		for(int j = 0; j < 5; j++){
			sum+=rss[j];
		}
		if(sum > 7)
			vector[OFS_OVER7CARDS] = 1;
		//distances to next legal location, to port, to opponent
		if(egsr.getDistanceToNextLegalLoc(pn)==2)
			vector[OFS_DISTANCETOLEGAL] = 1;
		if(egsr.getDistanceToPort(pn)==2)
			vector[OFS_DISTANCETOPORT] = 1;
		if(egsr.getDistanceToOpp(pn)==2)
			vector[OFS_DISTANCETOOPP] = 1;
		
		//only an approximation for opponents stuff to avoid even higher dimensionality
		for(int i=0; i < 3; i++){
			
			pn = playingOrderPn[i+1];//in order to get the information of the next player based on the order of play;
			if(ogsr.getPlayerID(pn)!=-1){//only fill with info for the players that exist in the game;	
				
				score = ogsr.getTotalVP(pn);
				if(score < 4)
					vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_SCORE] = 1;
				else if(score < 7)
					vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_SCORE + 1] = 1;	
				else if(score < 10)
					vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_SCORE + 2] = 1;
				else
					vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_SCORE + 3] = 1;
				
				if(ogsr.getPlayedKnights(pn) > 0){
					num = ogsr.getPlayedKnights(pn) < 3 ? 0 : 1;
					vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_NPLAYEDKNIGHTS + num] = 1;
				}
				
	    		vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_LA] = ogsr.getLALabel(pn);
	    		vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_LR] = ogsr.getLRLabel(pn);
				
		        //longest current road
		        if(egsr.getLongestRoads(pn) > 0)
		        	if((egsr.getLongestRoads(pn) < 7))
		        		vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_CURRENTLONGESTROAD + (egsr.getLongestRoads(pn) - 1) / 2] = 1;
		        	else
		        		vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_CURRENTLONGESTROAD + 3] = 1;
				
		        affectedByRobber = false;
				int[] setts = ogsr.getSettlementsForPlayer(pn);
				int[] cities = ogsr.getCitiesForPlayer(pn);
				
				if(adjNodes!=null){
					for(int sc : setts){
						if(affectedByRobber)
							break;
						for(int c : adjNodes){
							if(sc==c){
								affectedByRobber = true;
								break;
							}
						}
					}
					for(int cc : cities){
						if(affectedByRobber)
							break;
						for(int c : adjNodes){
							if(cc==c){
								affectedByRobber = true;
								break;
							}
						}
					}
				}
				
	            if(affectedByRobber)
	            	vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_AFFECTEDBYROBBER] = 1;
	            
	            int sdev = 0;
	            dc = ogsr.getUnplayedDevCards(pn);
	    		for(int j = 0; j < 4; j++){
	    			sdev += dc[j];
	    		}
	    		ndc = ogsr.getNewDevCards(pn);
	    		for(int j = 0; j < 4; j++){
	    			sdev += ndc[j];
	    		}
	    		sdev += ogsr.getVictoryDevCards(pn) + ndc[4];
	            vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_HASDEVCARDS] = (sdev > 0) ? 1 : 0;
	            
			}
    	}
		
    	int naffected = 0;
    	naffected += vector[OFS_AFFECTEDBYROBBER] + vector[OFS_OPPPLAYERDATA[0] + OFS_OPP_AFFECTEDBYROBBER] + 
    			vector[OFS_OPPPLAYERDATA[1] + OFS_OPP_AFFECTEDBYROBBER] + vector[OFS_OPPPLAYERDATA[2] + OFS_OPP_AFFECTEDBYROBBER];
    	if(naffected > 0 && naffected < 4)//shouldn't be 4, but just in case
    		vector[OFS_NPLAYERSAFFECTED + naffected - 1] = 1;
		
		return vector;
	}
	
	/**
	 * Same method as the one it overloads, but for the SmartSettlers environment
	 * @param s the state vector
	 * @param bl the board layout
	 * @return
	 */
	private int[] calculateStateVector(int[] s, Board bl){
	        int fsmlevel    = s[OFS_FSMLEVEL];
	        int cpn          = s[OFS_FSMPLAYER+fsmlevel]; //this current player is dangerous as JSettlers doesn't change the current player for the discard action, so I need to fix that
			int i, j, l, ind;
			int playerStateSize = 55;
			int[] vector = new int[STATE_VECTOR_SIZE];
			Arrays.fill(vector, 0);//fill it up with zeros
			
			vector[OFS_BIAS_FEATURE] = 1;
			int dice = s[OFS_DICE];
			if(!(dice <= 0)){
				if(dice == 7)
					vector[OFS_DICERESULT] = 1;
				else
					vector[OFS_DICERESULT + reduceNumberRange(dice) - 1] = 1;
			}
			
			//current pieces on board of each type
			int rd = 0, se = 0, ct = 0;
			
			for(i = 0; i < NPLAYERS; i++){
				rd += s[OFS_PLAYERDATA[i]+OFS_NROADS];
				se += s[OFS_PLAYERDATA[i]+OFS_NSETTLEMENTS];
				ct += s[OFS_PLAYERDATA[i]+OFS_NCITIES];
			}
			
			int total = rd+se+ct;
			int prodPieces = se + ct;
			//max 20 settlements, 16 cities, 60 roads, total 96, prodpieces 36 and we want 5 classes of each
			se/=5;
			rd/=15;
			ct/=4;
			total/=24;
			prodPieces/=9;		
			
			vector[OFS_TOTALSETTLEMENTS + se] = 1;
			vector[OFS_TOTALCITIES + ct] = 1;
			vector[OFS_TOTALROADS + rd] = 1;
			
			/*
			 * Alternatively we just have 5 classes for prod pieces and 5 for roads
			 * 
			 * vector[idx + prodPieces] = 1;
			 * idx +=5;
			 * vector[idx + rd] = 1;
			 * idx +=5;
			 * 
			 * or just 5 overall,
			 * vector[idx + total] = 1;
			 * idx +=5;
			 * 
			 */
			
			//if the current player has played a dev card
			vector[OFS_HASPLAYEDDEVCARD] = s[OFS_PLAYERDATA[cpn] + OFS_HASPLAYEDCARD];
		 
	        //logic for calculating the player's position in turn based on board position and starting player
			int spn = s[OFS_STARTING_PLAYER];
			int pos;
			if(spn == cpn)
				pos = 1; //the first in a round
			else{
				pos = 2;
				for(i = spn + 1; i != spn; i++){
					if(i == 4)
						i = 0; 
					if(i == cpn){
						break; //found him so we can break
					}else{
						pos++;
					}
				}
			}
			vector[OFS_BOARDPOSITION + pos-1] = 1;
			 
			//are there anymore cards left in the deck
			if(NCARDS - s[OFS_NCARDSGONE]>0)
				vector[OFS_ANYDEVCARDSLEFT] = 1;
			
			
			//remember the player numbers based on the playing order from the current pn onwards
			int[] playingOrderPn = new int[4];//starts with current player number and ends with previous player number
			playingOrderPn[0] = cpn;
			for(i = 1; i < 4; i++){
				playingOrderPn[i] = cpn + i;
				if(playingOrderPn[i] > 3)
					playingOrderPn[i] = playingOrderPn[i] - 4;
			}
	        
			//loop through the vertices to get a vector of settlements vertices and a vector of cities vertices for each player
			Vector[] setts = new Vector[4];
			Vector[] cities = new Vector[4];
			for(i = 0; i < 4; i++){
				setts[i] = new Vector();
				cities[i] = new Vector();
			}
			for (i=0; i<N_VERTICES; i++)
	        {
				if (s[OFS_VERTICES + i] >= VERTEX_HASCITY){
					cities[s[OFS_VERTICES + i]-VERTEX_HASCITY].add(i);
				}else if((s[OFS_VERTICES + i] >= VERTEX_HASSETTLEMENT)){
					setts[s[OFS_VERTICES + i]-VERTEX_HASSETTLEMENT].add(i);
				}
	        }
			
	        int robberHex = s[OFS_ROBBERPLACE];
	    	int robberType = bl.hextiles[robberHex].subtype;
	    	int robberNumber = reduceNumberRange(bl.hextiles[robberHex].productionNumber);
			
	    	//the number and the type of resource blocked by the robber
	    	switch (robberType) {
		        case LAND_CLAY:
		        	vector[OFS_RESOURCEBLOCKED] = SOCResourceConstants.CLAY;
					break;
		 			
				case LAND_STONE:
					vector[OFS_RESOURCEBLOCKED + 1] = SOCResourceConstants.ORE;
					break;
				
				case LAND_SHEEP:
					vector[OFS_RESOURCEBLOCKED + 2] = SOCResourceConstants.SHEEP;
					break;
		
				case LAND_WHEAT:
					vector[OFS_RESOURCEBLOCKED + 3] = SOCResourceConstants.WHEAT;
					break;
				
				case LAND_WOOD:
					vector[OFS_RESOURCEBLOCKED + 4] = SOCResourceConstants.WOOD;
					break;
				
				default:
					//this is the desert leave it to 0
					break;
	    	}
	    	
	    	if(robberNumber > 0)
	    		vector[OFS_NUMBERBLOCKED + robberNumber - 2] = 1;
			
	    	Set allPieces;
	    	Set nodes2Away;
			pos = 8;
			int pn = playingOrderPn[0];//this player
			int score = s[OFS_PLAYERDATA[pn] + OFS_SCORE];
			if(score > 0)//for 0 we don't set any features
				if(score <= 10){
					vector[OFS_PLAYERSCORE + score - 1] = 1;
				}else
					vector[OFS_PLAYERSCORE + 9] = 1;//the 11 and 12 vp cases
			
			if(s[OFS_LARGESTARMY_AT]==pn)
				vector[OFS_LA] = 1;
			if(s[OFS_LONGESTROAD_AT]==pn)
				vector[OFS_LR] = 1;
			
			if(s[OFS_PLAYERDATA[pn]+OFS_NROADS] < 15)
				vector[OFS_ROADS + s[OFS_PLAYERDATA[pn]+OFS_NROADS]/3] = 1;
			else
				vector[OFS_ROADS + N_PLANES -1] = 1;
			if(s[OFS_PLAYERDATA[pn]+OFS_NSETTLEMENTS] > 0)
				vector[OFS_SETTLEMENTS + s[OFS_PLAYERDATA[pn]+OFS_NSETTLEMENTS] -1] = 1;
			if(s[OFS_PLAYERDATA[pn]+OFS_NCITIES] > 0)
				vector[OFS_CITIES + s[OFS_PLAYERDATA[pn]+OFS_NCITIES] -1] = 1;
			
	        //5 positions for resources in hand (need to order these in the same way JSettlers does)
	        if(s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_CLAY] > 0)
				if(s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_CLAY] < 5){
		        	vector[OFS_CLAYINHAND + s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_CLAY]-1] = 1;
				}else
					vector[OFS_CLAYINHAND + 4] = 1;
	        if(s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_STONE] > 0)
				if(s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_STONE] < 5){
		        	vector[OFS_OREINHAND + s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_STONE]-1] = 1;
				}else
					vector[OFS_OREINHAND + 4] = 1;
	        if(s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_SHEEP] > 0)
				if(s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_SHEEP] < 5){
		        	vector[OFS_SHEEPINHAND + s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_SHEEP]-1] = 1;
				}else
					vector[OFS_SHEEPINHAND + 4] = 1;
	        if(s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_WHEAT] > 0)
				if(s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_WHEAT] < 5){
		        	vector[OFS_WHEATINHAND + s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_WHEAT]-1] = 1;
				}else
					vector[OFS_WHEATINHAND + 4] = 1;
	        if(s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_WOOD] > 0)
				if(s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_WOOD] < 5){
		        	vector[OFS_WOODINHAND + s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_WOOD]-1] = 1;
				}else
					vector[OFS_WOODINHAND + 4] = 1;
	        
	        // 6 positions for what port types is this player touching
	        vector[OFS_TOUCHING_PORTS] = s[OFS_PLAYERDATA[pn] + OFS_ACCESSTOPORT + PORT_MISC -1];
	        vector[OFS_TOUCHING_PORTS + 1] = s[OFS_PLAYERDATA[pn] + OFS_ACCESSTOPORT + PORT_CLAY -1];
	        vector[OFS_TOUCHING_PORTS + 2] = s[OFS_PLAYERDATA[pn] + OFS_ACCESSTOPORT + PORT_STONE -1];
	        vector[OFS_TOUCHING_PORTS + 3] = s[OFS_PLAYERDATA[pn] + OFS_ACCESSTOPORT + PORT_SHEEP -1];
	        vector[OFS_TOUCHING_PORTS + 4] = s[OFS_PLAYERDATA[pn] + OFS_ACCESSTOPORT + PORT_WHEAT -1];
	        vector[OFS_TOUCHING_PORTS + 5] = s[OFS_PLAYERDATA[pn] + OFS_ACCESSTOPORT + PORT_WOOD -1];
	        
	        //5 positions for getting the sum of numbers on hexes that this player touches attached to each rss type 
	        //from the indices get the neighbour hexes; if it is a sett index we add the amount once else twice to the correct type
	        //5 positions get the array that contains how much access this player has to a rss type in terms of adjacent number of settlements and cities
	        //from the indices get the neighbour hexes; if it is a sett index we add 1 else 2 to the correct type
	        
	        int[] numbers = new int[5];
	        int[] access = new int[5];
	        for(Object sett : setts[pn]){
	        	int vind = (int)sett;
		        int[] hexInd = new int[3];
		        int k=0;
		        for(j = 0; j < 6; j++){
		        	if (bl.neighborVertexHex[vind][j]!=-1){
		        		hexInd[k] = bl.neighborVertexHex[vind][j];
		        		k++;
		        	}
		        }
		        for(j = 0; j < 3; j++){
		        	int type = bl.hextiles[hexInd[j]].subtype;
		        	int number = reduceNumberRange(bl.hextiles[hexInd[j]].productionNumber);
		        
			        switch (type) {
			            case LAND_CLAY:
			            	numbers[SOCResourceConstants.CLAY-1]+=number;
			            	access[SOCResourceConstants.CLAY-1]++;
			 				break;
				 			
						case LAND_STONE:
			            	numbers[SOCResourceConstants.ORE-1]+=number;
			            	access[SOCResourceConstants.ORE-1]++;
							break;
						
						case LAND_SHEEP:
			            	numbers[SOCResourceConstants.SHEEP-1]+=number;
			            	access[SOCResourceConstants.SHEEP-1]++;
							break;
				
						case LAND_WHEAT:
			            	numbers[SOCResourceConstants.WHEAT-1]+=number;
			            	access[SOCResourceConstants.WHEAT-1]++;
							break;
						
						case LAND_WOOD:
			            	numbers[SOCResourceConstants.WOOD-1]+=number;
			            	access[SOCResourceConstants.WOOD-1]++;
							break;
						
						default:
							//this is the desert, ignore
							break;
			        }
		        }
	        }
	        
	        for(Object city : cities[pn]){
	        	int vind = (Integer)city;
		        int[] hexInd = new int[3];
		        int k=0;
		        for(j = 0; j < 6; j++){
		        	if (bl.neighborVertexHex[vind][j]!=-1){
		        		hexInd[k] = bl.neighborVertexHex[vind][j];
		        		k++;
		        	}
		        }
		        for(j = 0; j < 3; j++){
		        	int type = bl.hextiles[hexInd[j]].subtype;
		        	int number = reduceNumberRange(bl.hextiles[hexInd[j]].productionNumber);
		        
			        switch (type) {
			            case LAND_CLAY:
			            	numbers[SOCResourceConstants.CLAY -1]+=2*number;
			            	access[SOCResourceConstants.CLAY -1]+=2;
			 				break;
				 			
						case LAND_STONE:
			            	numbers[SOCResourceConstants.ORE -1]+=2*number;
			            	access[SOCResourceConstants.ORE -1]+=2;
							break;
						
						case LAND_SHEEP:
			            	numbers[SOCResourceConstants.SHEEP -1]+=2*number;
			            	access[SOCResourceConstants.SHEEP -1]+=2;
							break;
				
						case LAND_WHEAT:
			            	numbers[SOCResourceConstants.WHEAT -1]+=2*number;
			            	access[SOCResourceConstants.WHEAT -1]+=2;
							break;
						
						case LAND_WOOD:
			            	numbers[SOCResourceConstants.WOOD -1]+=2*number;
			            	access[SOCResourceConstants.WOOD -1]+=2;
							break;
						
						default:
							//this is the desert, ignore
							break;
			        }
		        }
	        }
	        
	        for(j = 0; j < 5; j++){
	        	if(numbers[j] > 0)
		        	if(numbers[j] <= 40){//let's say a maximum of 40
		        		vector[OFS_CLAYPRODUCTION + j + numbers[j]/5] = 1;
		        	}else
		        		vector[OFS_CLAYPRODUCTION + j + 8] = 1;
	        	if(access[j] > 0)
		        	if(access[j] <= 8){//let's say a maximum of 8
		        		vector[OFS_CLAYACCESS + j + access[j] - 1] = 1;
		        	}else
		        		vector[OFS_CLAYACCESS + j + 8 - 1] = 1;
	        }
	        //number of production pieces affected by robber
            ind = 0;
            int prod = 0;
            for (j=0; j<6; j++)
            {
                ind = bl.neighborHexVertex[robberHex][j];
                if(setts[pn].contains(ind)){
                	prod += 1;
                }else if(cities[pn].contains(ind)){
                	prod += 2;
                }
            }
	        
	        //is affected by robber flag
	        if(prod > 0){
	        	vector[OFS_AFFECTEDBYROBBER] = 1;
        		vector[OFS_NPIECESAFFECTED + prod - 1] = 1;
	        }
	        
			//the number of knights played;
	        int playedKnights = s[OFS_PLAYERDATA[pn] + OFS_USEDCARDS + CARD_KNIGHT];
	        if(playedKnights > 0)
	        	if(playedKnights < 5){
	        		vector[OFS_NPLAYEDKNIGHTS + playedKnights - 1] = 1;
	        	}else
	        		vector[OFS_NPLAYEDKNIGHTS + 4] = 1;
	        
	        //longest current road
	        int longestRoad = s[OFS_PLAYERDATA[pn] + OFS_PLAYERSLONGESTROAD];
	        if(longestRoad > 0)
	        	vector[OFS_CURRENTLONGESTROAD + longestRoad -1] = 1;
			
			//3 positions for used special dev cards in hand;
	        int playedRb = s[OFS_PLAYERDATA[pn] + OFS_USEDCARDS + CARD_FREEROAD];
	        int playedDisc = s[OFS_PLAYERDATA[pn] + OFS_USEDCARDS + CARD_FREERESOURCE];
	        int playedMono = s[OFS_PLAYERDATA[pn] + OFS_USEDCARDS + CARD_MONOPOLY];
	        
	        if(playedRb > 0)
	        	vector[OFS_PLAYEDDEVCARDSINHAND + playedRb -1] = 1; 
	        if(playedDisc > 0)
	        	vector[OFS_PLAYEDDEVCARDSINHAND + 1 + playedDisc -1] = 1;
	        if(playedMono > 0)
	        	vector[OFS_PLAYEDDEVCARDSINHAND + 2 + playedMono -1] = 1;
	        
			//4 positions for old unplayed dev cards in hand;
			vector[OFS_OLDDEVCARDSINHAND] = s[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_KNIGHT] > 0 ? 1 : 0;
			vector[OFS_OLDDEVCARDSINHAND +1] = s[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_FREEROAD] > 0 ? 1 : 0;
			vector[OFS_OLDDEVCARDSINHAND +2] = s[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_FREERESOURCE] > 0 ? 1 : 0;
			vector[OFS_OLDDEVCARDSINHAND +3] = s[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_MONOPOLY] > 0 ? 1 : 0;
			
			//4 positions for unplayed new dev cards
			vector[OFS_NEWDEVCARDSINHAND] = s[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_KNIGHT] > 0 ? 1 : 0;
			vector[OFS_NEWDEVCARDSINHAND + 1] = s[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_FREEROAD] > 0 ? 1 : 0;
			vector[OFS_NEWDEVCARDSINHAND + 2] = s[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_FREERESOURCE] > 0 ? 1 : 0;
			vector[OFS_NEWDEVCARDSINHAND + 3] = s[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_MONOPOLY] > 0 ? 1 : 0;
			
			//5 positions for VP cards
			int vpCards = s[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_ONEPOINT] + 
					s[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_ONEPOINT];
			if(vpCards > 0)
				vector[OFS_VPCARDS + vpCards -1] = 1;
			
			//to describe that enough rss exist
			boolean clay = false, ore = false, sheep = false, wheat = false, wood = false, wheat2 = false, ore3 = false;
			for(int k = 0; k < N_PLANES; k++){
				if(vector[OFS_CLAYINHAND + k]>0)
					clay = true;
				if(vector[OFS_WOODINHAND + k]>0)
					wood = true;
				if(vector[OFS_SHEEPINHAND + k]>0)
					sheep = true;
				if(vector[OFS_WHEATINHAND + k]>0)
					wheat = true;
				if(vector[OFS_OREINHAND + k]>0)
					ore = true;
				if(vector[OFS_WHEATINHAND + k]>0 && k >= 1)
					wheat2 = true;
				if(vector[OFS_OREINHAND + k]>0 && k >= 2)
					ore3 = true;
			}
			
			if(clay && wood)
				vector[OFS_CANBUILDROAD] = 1;
			if(clay && wood && wheat && sheep)
				vector[OFS_CANBUILDSETTLEMENT] = 1;		
			if(ore && sheep && wheat)
				vector[OFS_CANBUYCARD] = 1;
			if(ore3 && wheat2)
				vector[OFS_CANBUILDCITY] = 1;
					
			//one position for describing if this player can do a bank trade;
			int cardsRequired = 4;
			if(vector[OFS_TOUCHING_PORTS]==1)
				cardsRequired = 3;
			for(j = 0; j<5; j++){
				//if it has a port of this type and more than 2 rss
				if(vector[OFS_TOUCHING_PORTS + j + 1]==1 && vector[OFS_CLAYINHAND + j]>=2){
					vector[OFS_CANBANKORPORTTRADE] = 1;
					break;
				}else if(vector[OFS_CLAYINHAND + j]>=cardsRequired){
					vector[OFS_CANBANKORPORTTRADE] = 1;
					break;
				}
			}
	        //one position for describing if this player has over 7 resources;
			
			int sum = s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_CLAY] + s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_SHEEP] + s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_WOOD] + s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_WHEAT] + s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_STONE];
			if(sum > 7)
				vector[OFS_OVER7CARDS] = 1;
		
			//these distance calculations will probably be slow
			allPieces = new HashSet();
	        for(Object city : cities[pn]){
	        	allPieces.add(city);
	        }
	        for(Object sett : setts[pn]){
	        	allPieces.add(sett);
	        }
	        nodes2Away = new HashSet();
	        for(Object v: allPieces){
	        	for (l=0; l<6; l++){
	        		ind = bl.neighborVertexVertex[(int)v][l];
	        		if(ind != -1)
	        			for (j=0; j<6; j++){
	        				if(bl.neighborVertexVertex[ind][j] != -1)
	        					nodes2Away.add(bl.neighborVertexVertex[ind][j]);//this contains this player's initial pieces
	        			}
	            }
	        }
	        //remove this player's pieces
	        nodes2Away.removeAll(allPieces);
	        //check for opponents, legal locations and ports;
	        for(Object v : nodes2Away){
	        	if(s[OFS_VERTICES + (int)v] > VERTEX_TOOCLOSE)
	        		vector[OFS_DISTANCETOOPP] = 1;//opponent
	        	if(s[OFS_VERTICES + (int)v] == VERTEX_EMPTY)
	        		vector[OFS_DISTANCETOLEGAL] = 1;//legal location
	        	//check if its a port (logic taken from perform action in player class, when building a settlement)
                for (j=0; j<6; j++)
                {
                    ind = bl.neighborVertexHex[(int)v][j];
                    if ((ind != -1) && (bl.hextiles[ind].type == TYPE_PORT))
                    {
                        int k = j-2; if (k<0) k+=6;
                        if (k==bl.hextiles[ind].orientation)
                        	vector[OFS_DISTANCETOPORT] = 1;
                        k = j-3; if (k<0) k+=6;
                        if (k==bl.hextiles[ind].orientation)
                        	vector[OFS_DISTANCETOPORT] = 1;
                    }
                }
	        }
	        nodes2Away.clear();
	        allPieces.clear();
			
	        //only an approximation for opponents stuff to avoid even higher dimensionality
	    	for(i=0; i < 3; i++){
				pn = playingOrderPn[i+1];//in order to get the information of the next player based on the order of play;
				score =  s[OFS_PLAYERDATA[pn] + OFS_SCORE];
				if(score < 4)
					vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_SCORE] = 1;
				else if(score < 7)
					vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_SCORE + 1] = 1;	
				else if(score < 10)
					vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_SCORE + 2] = 1;
				else
					vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_SCORE + 3] = 1;
				
				
				if(s[OFS_PLAYERDATA[pn] + OFS_USEDCARDS + CARD_KNIGHT] > 0){
					int num = s[OFS_PLAYERDATA[pn] + OFS_USEDCARDS + CARD_KNIGHT] < 3 ? 0 : 1;
					vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_NPLAYEDKNIGHTS + num] = 1;
				}
				
		        //longest current road
		        if(s[OFS_PLAYERDATA[pn] + OFS_PLAYERSLONGESTROAD] > 0)
		        	if((s[OFS_PLAYERDATA[pn] + OFS_PLAYERSLONGESTROAD] < 7))
		        		vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_CURRENTLONGESTROAD + (s[OFS_PLAYERDATA[pn] + OFS_PLAYERSLONGESTROAD] - 1) / 2] = 1;
		        	else
		        		vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_CURRENTLONGESTROAD + 3] = 1;
				
				boolean affectedByRobber = false;
	            for (j=0; j<6; j++)
	            {
	                ind = bl.neighborHexVertex[robberHex][j];
	                if(setts[pn].contains(ind)){
	                	affectedByRobber = true;
	                	break;
	                }else if(cities[pn].contains(ind)){
	                	affectedByRobber = true;
	                	break;
	                }
	            }
	            if(affectedByRobber)
	            	vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_AFFECTEDBYROBBER] = 1;
	            
	            int sdev = s[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_KNIGHT] + s[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_FREEROAD]
	            		+ s[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_FREERESOURCE] + s[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_MONOPOLY] 
	            		+ s[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_KNIGHT] + s[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_FREEROAD] 
	            		+ s[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_FREERESOURCE] + s[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_MONOPOLY]
	            		+ s[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_ONEPOINT] + s[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_ONEPOINT];
				
	            vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_HASDEVCARDS] = (sdev > 0) ? 1 : 0;
	            
				if(s[OFS_LARGESTARMY_AT]==pn)
					vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_LA] = 1;
				if(s[OFS_LONGESTROAD_AT]==pn)
					vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_LR] = 1; 
	    	}
	    	
	    	int naffected = 0;
	    	naffected += vector[OFS_AFFECTEDBYROBBER] + vector[OFS_OPPPLAYERDATA[0] + OFS_OPP_AFFECTEDBYROBBER] + 
	    			vector[OFS_OPPPLAYERDATA[1] + OFS_OPP_AFFECTEDBYROBBER] + vector[OFS_OPPPLAYERDATA[2] + OFS_OPP_AFFECTEDBYROBBER];
	    	if(naffected > 0 && naffected < 4)//shouldn't be 4, but just in case
	    		vector[OFS_NPLAYERSAFFECTED + naffected - 1] = 1;
	    	
			return vector;
		
	}
	
	///methods to be called from outside this class for consistency///

	/**
	 * Computed via vector difference between the after and before states, then the dimensionality is reduced by removing the features
	 * that cannot be modified by players' actions.
	 * @param beforeState
	 * @param afterState
	 * @return
	 */
	@Override
	public int[] computeActionVector(int[] beforeState, int[] afterState){
		int[] fullVector = SimilarityCalculator.vectorDifference(afterState, beforeState);
		int[] vector = new int[ACTION_VECTOR_SIZE];
		//first just write the ones that occupy one index in the vector
		vector[OFS_ACT_BIAS_FEATURE] = 1;
		vector[OFS_ACT_PLAYERSCORE] = fullVector[OFS_PLAYERSCORE]; 
		vector[OFS_ACT_DICE] = fullVector[OFS_DICERESULT];
		vector[OFS_ACT_ROADS] = fullVector[OFS_ROADS];
		vector[OFS_ACT_SETTS] = fullVector[OFS_SETTLEMENTS];
		vector[OFS_ACT_CITIES] = fullVector[OFS_CITIES];
		vector[OFS_ACT_AFFECTEDBYROBBER] = fullVector[OFS_ACT_AFFECTEDBYROBBER];
		vector[OFS_ACT_NUMBERBLOCKED] = fullVector[OFS_NUMBERBLOCKED];
		vector[OFS_ACT_PRODBLOCKED] = fullVector[OFS_NPIECESAFFECTED];
		vector[OFS_ACT_NOPLAYERSAFFECTED] = fullVector[OFS_NPLAYERSAFFECTED];
		vector[OFS_ACT_VPCARDS] = fullVector[OFS_VPCARDS];
		vector[OFS_ACT_NPLAYEDKNIGHTS] = fullVector[OFS_NPLAYEDKNIGHTS];
		vector[OFS_ACT_CURRENTLONGESTROAD] = fullVector[OFS_CURRENTLONGESTROAD];
		vector[OFS_ACT_LR] = fullVector[OFS_LR];
		vector[OFS_ACT_LA] = fullVector[OFS_LA];
		vector[OFS_ACT_CANBUYCARD] = fullVector[OFS_CANBUYCARD];
		vector[OFS_ACT_CANBUILDROAD] = fullVector[OFS_CANBUILDROAD];
		vector[OFS_ACT_CANBUILDSETTLEMENT] = fullVector[OFS_CANBUILDSETTLEMENT];
		vector[OFS_ACT_CANBUILDCITY] = fullVector[OFS_CANBUILDCITY];
		vector[OFS_ACT_CANBANKORPORTTRADE] = fullVector[OFS_CANBANKORPORTTRADE];
		vector[OFS_ACT_OVER7CARDS] = fullVector[OFS_OVER7CARDS];
		vector[OFS_ACT_DISTANCETOPORT] = fullVector[OFS_DISTANCETOPORT];
		vector[OFS_ACT_DISTANCETOLEGAL] = fullVector[OFS_DISTANCETOLEGAL];
		vector[OFS_ACT_DISTANCETOOPP] = fullVector[OFS_DISTANCETOOPP];
		//now add all the ones that require multiple positions
		for(int i=0; i < 5; i++)
			vector[OFS_ACT_RSSINHAND + i] = fullVector[OFS_CLAYINHAND + i];
		for(int i=0; i < 5; i++)
			vector[OFS_ACT_RSSPROD + i] = fullVector[OFS_CLAYPRODUCTION + i];
		for(int i=0; i < 5; i++)
			vector[OFS_ACT_RSSACCESS + i] = fullVector[OFS_CLAYACCESS + i];
		for(int i=0; i < 6; i++)
			vector[OFS_ACT_TOUCHINGPORTS + i] = fullVector[OFS_TOUCHING_PORTS + i];
		for(int i=0; i < 5; i++)
			vector[OFS_ACT_RSSBLOCKED + i] = fullVector[OFS_RESOURCEBLOCKED+ i];
		for(int i=0; i < 3; i++)
			vector[OFS_ACT_PLAYEDDEVCARDSINHAND + i] = fullVector[OFS_PLAYEDDEVCARDSINHAND+ i];
		for(int i=0; i < 4; i++)
			vector[OFS_ACT_OLDDEVCARDSINHAND + i] = fullVector[OFS_OLDDEVCARDSINHAND+ i];
		for(int i=0; i < 4; i++)
			vector[OFS_ACT_NEWDEVCARDSINHAND + i] = fullVector[OFS_NEWDEVCARDSINHAND+ i];
		
		return vector;
	}
	
		public int[] calculateStateVectorSS(int[] s, Board bl){
			return calculateStateVector(s, bl);
		}
		
		public int[] calculateStateVectorJS(ObsGameStateRow ogsr, ExtGameStateRow egsr){
			return calculateStateVector(ogsr,egsr);
		}
	
	/**
	 * 
	 * @param n the actual number on the hex
	 * @return a normalised value between 2-6
	 */
	private int reduceNumberRange(int n){
		int num = n;
		//reduce range
		if(num == 8)
			num = 6;
		else if(num == 9)
			num = 5;
		else if(num == 10)
			num = 4;
		else if(num == 11)
			num = 3;
		else if(num == 12)
			num = 2;
		
		return num;
	}

}
