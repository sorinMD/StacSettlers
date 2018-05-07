package representation;

import java.awt.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import mcts.game.catan.Board;
import soc.game.SOCBoard;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.server.database.stac.ExtGameStateRow;
import soc.server.database.stac.GameActionRow;
import soc.server.database.stac.ObsGameStateRow;
import soc.server.database.stac.StacDBHelper;

/**
 * Class for selecting/extracting (both steps are done simultaneously) numerical features relevant to a specific game context.
 * @author MD
 */
public class NumericalFVGenerator extends FeatureVectorGenerator implements NumericalFeatureVectorOffsets{
    //types of feature vectors for state
	public static final int _1ST_IMPLEMENTATION_STATE_VECTOR_TYPE = 0;
	/** If this is used, only the PARTIAL_STATE_DIFFERENCE_ACTION_VECTOR or FULL_STATE_DIFFERENCE_ACTION_VECTOR types are available*/
    public static final int CURRENT_STATE_VECTOR_TYPE = 1;
    /** If this is used, only the OLD_ACTION_VECTOR or FULL_STATE_DIFFERENCE_ACTION_VECTOR types are available*/
    public static final int OLD_STATE_VECTOR_TYPE = 2;
    //types of feature vectors for actions
    public static final int FULL_STATE_DIFFERENCE_ACTION_VECTOR_TYPE = 0;
    /**This is also computed via vector difference, but only the features that can be modified by a player's action are kept to reduce dimensionality. */
    public static final int OLD_ACTION_VECTOR_TYPE = 1;
    public static final int PARTIAL_STATE_DIFFERENCE_ACTION_VECTOR_TYPE = 2;
    //sizes
    public static final int SIZE_CURRENT_STATE_VECTOR = STATE_VECTOR_SIZE; // value taken from the offsets interface
    public static final int SIZE_CURRENT_ACTION_VECTOR = ACTION_VECTOR_SIZE; // value taken from the offsets interface
    public static final int SIZE_OLD_STATE_VECTOR = 235;
    public static final int SIZE_1ST_STATE_VECTOR = 110;
    public static final int SIZE_OLD_ACTION_VECTOR = 44;
    //Default values, modify the call to initialiseGenerator inside the FVGeneratorFactory if these need to be changed
    public static int STATE_VECTOR_TYPE = CURRENT_STATE_VECTOR_TYPE;
    
    /**
     * Sets the correct types and corresponding sizes of the feature vectors
     * @param stateVectorType
     * @param actionVetorType
     * @param includeActionTypeVector
     */
    public static void initialiseGenerator(int stateVectorType){
    	STATE_VECTOR_TYPE = stateVectorType;
    	
    	if(STATE_VECTOR_TYPE == CURRENT_STATE_VECTOR_TYPE){
    		REWARD_FUNCTION_SIZE = SIZE_CURRENT_STATE_VECTOR;
    	}else if(STATE_VECTOR_TYPE == OLD_STATE_VECTOR_TYPE){
    		REWARD_FUNCTION_SIZE = SIZE_OLD_STATE_VECTOR;
    	}else{ //OLD_STATE_VECTOR_TYPE
    		REWARD_FUNCTION_SIZE = SIZE_1ST_STATE_VECTOR;
    	}
    	
    }
    
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
	
	public NumericalFVGenerator(){}
	
	/**
	 * NB: First implementation. Do not use unless trying to reproduce the initial results on biasing the initial placement with corpus.
	 * Describes a given state based on the observable and extracted features.
	 * For the first or second settlement placement in the initial setup phase.
	 * @param ogsr the observable features of the before state
	 * @return the feature vector describing the before state
	 */
	public int[] calculateVector1(ObsGameStateRow ogsr, ExtGameStateRow egsr){
		int[] vector = new int[SIZE_1ST_STATE_VECTOR];
		Arrays.fill(vector, 0);//fill it up with zeros as it is the default value for most features(excluding the distances) and we will encounter a lot of null players
		
		vector[0] = ogsr.getGameState(); //state
		
		int cpn = ogsr.getCurrentPlayer();
		int spn = ogsr.getStartingPlayer();
		//logic for calculating the player's position in turn based on board position and starting player
		if(spn == cpn)
			vector[1] = 1; //the first in a round
		else{
			int pos = 2;
			for(int i = spn + 1; i != spn; i++){
				//check if we passed max of 4 players
				if(i == 4)
					i = 0; //start from the beginning
				if(ogsr.getPlayerID(i)!=-1)//not a null player
					if(i == cpn){
						vector[1] = pos;
						break; //found him so we can break
					}else{
						pos++;
					}
			}
		}
		
		int players = 0;
		for(int i = 0; i < 4; i++) //only for 4 player game
			if(ogsr.getPlayerID(i)!=-1)
				players++;
		vector[2] = players; //number of players
		
		//pieces on board of each type
		Integer[][] pob = ogsr.getPiecesOnBoard();

        //also keep track of settlements and cities as we will need to check for them later
        for(Integer[] p : pob){
        	if(p[0] == SOCPlayingPiece.ROAD){
        		vector[3]++;
        	}else if(p[0] == SOCPlayingPiece.SETTLEMENT){
        		vector[4]++;
        	}else if(p[0] == SOCPlayingPiece.CITY){
        		vector[5]++;
        	}//otherwise we ignore
        }
		
		//set the playing order of the player numbers from our pn onwards
		int[] playingOrder = new int[4];//starts with current player number and ends with previous player number
		playingOrder[0] = cpn;
		for(int i = 1; i < 4; i++){
			playingOrder[i] = cpn + i;
			if(playingOrder[i] > 3)
				playingOrder[i] = playingOrder[i] - 4;
		}
		
		//create a int[4][25] (all info for each player excluding the similarity value)
		//fill them up with info from the ogsr following the playing order decided on above
		int[][] playerVector = new int[4][25];
		for(int i=0; i < 4; i++){
			Arrays.fill(playerVector[i], 0); //fill as we want to ignore the null players when taking info from the ogsr
		}
		
		for(int i=0; i < 4; i++){
			int pn = playingOrder[i];
			//only fill with info for the players that exist the info for the others leave it to 0
			if(ogsr.getPlayerID(pn)!=-1){
		
				playerVector[pn][0] = ogsr.getTotalVP(pn);
				playerVector[pn][1] = ogsr.getLALabel(pn);
				playerVector[pn][2] = ogsr.getLRLabel(pn);
				playerVector[pn][3] = ogsr.getRoadsForPlayer(pn).length;
				int[] settCoords = ogsr.getSettlementsForPlayer(pn);
				playerVector[pn][4] = settCoords.length;
				int[] cityCoords = ogsr.getCitiesForPlayer(pn);
				playerVector[pn][5] = cityCoords.length;
				
		        //create the array that will measure how much access we have to a rss type; i.e. sett = 1; city = 2
		        /**
		         * NOTE: this doesn't really check if the robber blocks any of these rss, maybe do this later in the development
		         */
				int[] trt = new int[5];
				trt = StacDBHelper.transformToIntArr(egsr.getRssTypeAndNumber(pn));
				
				//copy the values from rss type array into the playersVector
				for(int k = 0; k < 5; k++){
					playerVector[pn][6 + k] = trt[k];
				}
								
				int[] tpt = ogsr.getTouchingPortTypes(pn);
				for(int k = 0; k < tpt.length; k++){
					playerVector[pn][11+k] = tpt[k];
				}
				
				int[] numbers = ogsr.getJustNumbersForPlayer(pn);
				//transform all in just numbers from 2-6 (as we consider desert/water == 0, we just ignore them here; also multiple settlements/cities bring a number twice, but there is no difference between a city and a settlement here)
				for(int k = 0; k < numbers.length; k++){
					if(numbers[k] == 8)
						numbers[k] = 6;
					else if(numbers[k] == 9)
						numbers[k] = 5;
					else if(numbers[k] == 10)
						numbers[k] = 4;
					else if(numbers[k] == 11)
						numbers[k] = 3;
					else if(numbers[k] == 12)
						numbers[k] = 2;
					//and add it to the sum
					playerVector[pn][17] = playerVector[pn][17] + numbers[k];
				}
				
				playerVector[pn][18] = egsr.getTerritoryConnected(pn);//territory connected
				playerVector[pn][19] = egsr.getTerritoryIsolated(pn);//territory isolated
				playerVector[pn][20] = egsr.getLongestRoads(pn);//longest road 
				playerVector[pn][21] = egsr.getLongestPossibleRoad(pn);//longest possible road 
				playerVector[pn][22] = egsr.getDistanceToOpp(pn); //opponent's sett/city
				playerVector[pn][23] = egsr.getDistanceToPort(pn); //port
				playerVector[pn][24] = egsr.getDistanceToNextLegalLoc(pn); //next legal action to build (again may be easier to calc using a pt and a real board)
			
			}
		}
		
		/*
		 * NB: There is a mistake in here; the way these are copied back ignores the order of play which may have affected the performance
		 * of the original implementation; Not sure though as the same mistake was done for both the info in the corpus and the one in the 
		 * real game; Keeping this as it is for future references;
		*/
		//then one by one copy them to the real vector
		for(int i= 0; i < 4; i++){
			int index = 6 + (i*26);//the index at which this player info starts (each player info is of size 26 including the similarity feature)
			//copy all the info now
			for(int pos = 0; pos < playerVector[i].length; pos ++)
				vector[index + pos] = playerVector[i][pos];
			
			//now add the similarity feature
			for(int j= 0; j < 4; j++){
				if(i!=j){
					double sim = SimilarityCalculator.cosineSimilarity(playerVector[i], playerVector[j]);
					if(sim > 0.80) //this threshold may change
						vector[index + 25] = 1;
				}
			}
		}
		return vector;
	}
	
	/**
	 * Describes a given state based on the observable and extracted features. This is an approximate description, 
	 * so it excludes coordinates or exact board description. 
	 * It improves the previous implementation in the sense that it adds info to the vector based on the order of play
	 * It doesn't contain any JSettlers heuristics or difficult to compute heuristics as it will take to long to 
	 * compute these during SS simulations, but these can be easily added;
	 * @param ogsr the observable features of the before state
	 * @return the feature vector describing the before state containing numerical values
	 */
	private int[] calculateVectorOld(ObsGameStateRow ogsr, ExtGameStateRow egsr){
		int[] vector = new int[SIZE_OLD_STATE_VECTOR];
		Arrays.fill(vector, 0);//fill it up with zeros
		
		int players = 0;
		for(int i = 0; i < 4; i++) //maximum of 4 player game for now
			if(ogsr.getPlayerID(i)!=-1)
				players++;
		vector[0] = players; //number of players
		
		//current pieces on board of each type
		Integer[][] pob = ogsr.getPiecesOnBoard();

        //this reflects how crowded the board is
        for(Integer[] p : pob){
        	if(p[0] == SOCPlayingPiece.ROAD){
        		vector[1]++;
        	}else if(p[0] == SOCPlayingPiece.SETTLEMENT){
        		vector[2]++;
        	}else if(p[0] == SOCPlayingPiece.CITY){
        		vector[3]++;
        	}//otherwise we ignore (we shouldn't get here!)
        }
		
        //compute the richness of each resource type;
        int[] hexLayout = StacDBHelper.transformToIntArr(ogsr.getHexLayout()); 
        int[] numLayout = StacDBHelper.transformToIntArr(ogsr.getNumberLayout());
        int val;
        for(int i = 0; i < hexLayout.length; i++){
        	//reduce the range of the numbers on board to 2-6
        	val = reduceNumberRange(numLayout[i]);
        	
			//(4-clay, 5-ore, 6-sheep, 7-wheat, 8-wood => SOCResourceConstant + 3)
        	switch (hexLayout[i]) {
			case SOCResourceConstants.CLAY:
				vector[SOCResourceConstants.CLAY + 3]+=val;
				break;
			
			case SOCResourceConstants.ORE:
				vector[SOCResourceConstants.ORE + 3]+=val;
				break;
			
			case SOCResourceConstants.SHEEP:
				vector[SOCResourceConstants.SHEEP + 3]+=val;
				break;

			case SOCResourceConstants.WHEAT:
				vector[SOCResourceConstants.WHEAT + 3]+=val;
				break;
			
			case SOCResourceConstants.WOOD:
				vector[SOCResourceConstants.WOOD + 3]+=val;
				break;
			
			default:
				//this is the dessert, ignore
				break;
			}
        }
         
		//has the current player played a dev card?
		if(ogsr.hasPlayedDevCard())
			vector[9] = 1;

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
        
        //logic for calculating the player's position in turn based on board position and starting player
		int cpn = ogsr.getCurrentPlayer();//the current player is the one that executes the next action (only when trading this rule is broken)
		int spn = ogsr.getStartingPlayer();
		if(spn == cpn)
			vector[10] = 1; //the first in a round
		else{
			int pos = 2;
			for(int i = spn + 1; i != spn; i++){
				if(i == 4)
					i = 0; 
				if(ogsr.getPlayerID(i)!=-1)//not a null player
					if(i == cpn){
						vector[10] = pos;
						break; //found him so we can break
					}else{
						pos++;
					}
			}
		}
		
		//dice result
		vector[11] = ogsr.getDiceResult();
		//number of cards left in the deck
		vector[12] = ogsr.getDevCardsLeft();
		
		vector[13] = robberHexType;
		vector[14] = reduceNumberRange(robberHexNumber);
		
		//remember the player numbers based on the playing order from the current pn onwards
		int[] playingOrderPn = new int[4];//starts with current player number and ends with previous player number
		playingOrderPn[0] = cpn;
		for(int i = 1; i < 4; i++){
			playingOrderPn[i] = cpn + i;
			if(playingOrderPn[i] > 3)
				playingOrderPn[i] = playingOrderPn[i] - 4;
		}
        
		//vector to store information regarding each player 
		//NB: it may be quicker to store the info straight into the vector to be returned, but it is easier to follow this way
		int[][] playerVector = new int[4][55];
		for(int i=0; i < 4; i++){
			Arrays.fill(playerVector[i], 0); //fill with 0 as we want to ignore the null players
		}
		
		for(int i=0; i < 4; i++){
			int pn = playingOrderPn[i];//in order to get the information of the next player based on the order of play;
			if(ogsr.getPlayerID(pn)!=-1){//only fill with info for the players that exist in the game;
		
				playerVector[i][0] = ogsr.getTotalVP(pn);
				playerVector[i][1] = ogsr.getLALabel(pn);
				playerVector[i][2] = ogsr.getLRLabel(pn);
				playerVector[i][3] = ogsr.getRoadsForPlayer(pn).length;
				int[] settCoords = ogsr.getSettlementsForPlayer(pn);
				playerVector[i][4] = settCoords.length;
				int[] cityCoords = ogsr.getCitiesForPlayer(pn);
				playerVector[i][5] = cityCoords.length;
				
		        //5 positions for resources in hand 
		        int[] rss = ogsr.getResources(pn);
		        if(rss[5]!=0){ //we shouldn't have unknowns stored in db; 
		        	System.err.println("Unknown rss in player hand");//quick debugging TBR soon
		        }		        	
				for(int j = 0; j < 5; j++){
					playerVector[i][6 + j] = rss[j];
				}
		        
				//6 positions for what port types is this player touching
				int[] tpt = ogsr.getTouchingPortTypes(pn);
				for(int k = 0; k < tpt.length; k++){
					playerVector[i][11+k] = tpt[k];
				}
				
				//5 positions for getting the sum of numbers on hexes that this player touches attached to each rss type 
				int[][] tn = StacDBHelper.transformToIntArr2(ogsr.getTouchingNumbers(pn));
				int num = 0;
				for(int j = 0; j < 5; j++){
					for(int k : tn[j]){
						num = reduceNumberRange(k);
						playerVector[i][17 + j] += num;
					}
				}
								
				//get the array that contains how much access this player has to a rss type in terms of adjacent number of settlements and cities
				int[] rtn = new int[5];
				rtn = StacDBHelper.transformToIntArr(egsr.getRssTypeAndNumber(pn));
				for(int j = 0; j < 5; j++){
					playerVector[i][22 + j] = rtn[j];
				}
				
				
				//10 positions with the above, but affected by the robber this time;
				//copy the values from the previous positions to the current ones;
				for(int j = 0; j < 10; j++){
					playerVector[i][27 + j] = playerVector[i][17 + j];
				}
				
				//check if any of this player's pieces are on the adj coords nodes
				//loop over its pieces and add to a prod number which represents production based on settlements and cities (sett=1,city=2)
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
				//then based on what rss type was stolen subtract from the vector
				if(affectedByRobber)
				switch (robberHexType) {
				case SOCResourceConstants.CLAY:
					playerVector[i][27]-=(robberHexNumber*prod);
					playerVector[i][32]-=prod;
					break;
				
				case SOCResourceConstants.ORE:
					playerVector[i][28]-=(robberHexNumber*prod);
					playerVector[i][33]-=prod;
					break;
				
				case SOCResourceConstants.SHEEP:
					playerVector[i][29]-=(robberHexNumber*prod);
					playerVector[i][34]-=prod;
					break;
				
				case SOCResourceConstants.WHEAT:
					playerVector[i][30]-=(robberHexNumber*prod);
					playerVector[i][35]-=prod;
					break;

				case SOCResourceConstants.WOOD:
					playerVector[i][31]-=(robberHexNumber*prod);
					playerVector[i][36]-=prod;
					break;
				
				default:
					System.err.println("unknown blocked resource type");
					break;
				}
				
				//one position for the robber affecting this player or not (this is clear from previous info, but only to underline it)
				if(affectedByRobber)
					playerVector[i][37] = 1;
				
				//one position for the number of knights played;
				playerVector[i][38] = ogsr.getPlayedKnights(pn);
				
				//current longest current road 
				playerVector[pn][39] = egsr.getLongestRoads(pn);
				
				//5 positions for old unplayed dev cards in hand;
		        int[] dc = ogsr.getUnplayedDevCards(pn);
				for(int j = 0; j < 5; j++){
					playerVector[i][40 + j] = dc[j];
				}
				//the last position is unknown cards which should be changed to victory point cards
				playerVector[i][44] = ogsr.getVictoryDevCards(pn);
				
				//5 positions for unplayed new dev cards
				int[] ndc = ogsr.getNewDevCards(pn);
				for(int j = 0; j < 5; j++){
					playerVector[i][45 + j] = ndc[j];
				}
				//the last position is unknown cards which should be always 0 as SmartSettlers is fully observable
				playerVector[i][49] = 0;
				
				//one position for describing if this player can do a bank trade;
				int cardsRequired = 4;
				if(playerVector[i][11]==1)
					cardsRequired = 3;
				for(int j = 6; j<11; j++){
					//if it has a port of this type and more than 2 rss
					if(playerVector[i][j+6]==1 && playerVector[i][j]>=2){
						playerVector[i][50] = 1;
						break;
					}else if(playerVector[i][j]>=cardsRequired){
						playerVector[i][50] = 1;
						break;
					}
				}
		        //one position for describing if this player has over 7 resources;
				int sum = playerVector[i][10] + playerVector[i][9] + playerVector[i][8] + playerVector[i][7] + playerVector[i][6];
				if(sum > 7)
					playerVector[i][51] = 1;
				//distances to next legal location, to port, to opponent
				if(egsr.getDistanceToNextLegalLoc(pn)==2)
					playerVector[i][52] = 1;
				if(egsr.getDistanceToPort(pn)==2)
					playerVector[i][53] = 1;
				if(egsr.getDistanceToOpp(pn)==2)
					playerVector[i][54] = 1;

				//misc: various simple heuristics (modify size of vectors and position in vectors before uncommenting these)
//				playerVector[i][55] = egsr.getTerritoryConnected(pn);//territory connected
//				playerVector[i][56] = egsr.getTerritoryIsolated(pn);//territory isolated
//				playerVector[i][57] = egsr.getLongestPossibleRoad(pn);//longest possible road 
//				playerVector[i][58] = egsr.getDistanceToOpp(pn); //opponent's sett/city
//				playerVector[i][59] = egsr.getDistanceToPort(pn); //port
//				playerVector[i][60] = egsr.getDistanceToNextLegalLoc(pn); //next legal action to build (again may be easier to calc using a pt and a real board)
//				
//				//another 5 position for the ETB heuristic from JSettlers; [1] means including the robber effect
//				int[] avgETB = StacDBHelper.transformToIntArr(egsr.getAvgETBs(pn));
//				playerVector[i][61] = avgETB[1];
//				playerVector[i][62] = StacDBHelper.transformToIntArr(egsr.getRoadETBs(pn))[1];
//				playerVector[i][63] = StacDBHelper.transformToIntArr(egsr.getSettETBs(pn))[1];
//				playerVector[i][64] = StacDBHelper.transformToIntArr(egsr.getCityETBs(pn))[1];
//				playerVector[i][65] = StacDBHelper.transformToIntArr(egsr.getDevETBs(pn))[1];
//				//(ignore the ETW as it is continuously modified in the project)
				
			}
		}

		//copy everything to the main vector; due to the way the playerVector has been created, the playing order is maintained in its structure
		for(int i= 0; i < 4; i++){
			int index = 15 + (i*55);//the index at which a player info starts (15 positions for general board info + 55 for a player)
			//copy all the info now
			for(int pos = 0; pos < playerVector[i].length; pos ++)
				vector[index + pos] = playerVector[i][pos];			
		}
		return vector;
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
		//when this method is called we always set this, but when end of turn action is performed, instead of recalculating this we just unset this feature
		vector[OFS_CURRENTTURN] = 1;
		int dice = ogsr.getDiceResult();
//		if(!(dice <= 0)){
		//else leave it to 0
		if(dice == 7)
			vector[OFS_DICERESULT] = 1;
//			else
//				vector[OFS_DICERESULT] = reduceNumberRange(dice);
//		}
		
		//current pieces on board of each type
		Integer[][] pob = ogsr.getPiecesOnBoard();

//		//a list of roads coords if we want to compute the expansion possibility...NOTE: too expensive, but I will keep the code for now
		int cpn = ogsr.getCurrentPlayer();//the current player is the one that executes the next action (only when trading this rule is broken)
		Set allCoords = new HashSet<Integer>();
		Set allRoads = new HashSet<Integer>();
		Set roads = new HashSet<Integer>();
        //this reflects how crowded the board is
        for(Integer[] p : pob){
        	if(p[0] == SOCPlayingPiece.ROAD){
        		vector[OFS_TOTALROADS]++;
        		allRoads.add(p[1]);
        		if(p[2] == cpn)
        			roads.add(p[1]);
        	}else if(p[0] == SOCPlayingPiece.SETTLEMENT){
        		vector[OFS_TOTALSETTLEMENTS]++;
        		allCoords.add(p[1]);
        	}else if(p[0] == SOCPlayingPiece.CITY){
        		vector[OFS_TOTALCITIES]++;
        		allCoords.add(p[1]);
        	}//otherwise we ignore (we shouldn't get here!)
        }
		
        //has the current player played a dev card?
		if(ogsr.hasPlayedDevCard())
			vector[OFS_HASPLAYEDDEVCARD] = 1;

		
        //logic for calculating the player's position in turn based on board position and starting player
		int spn = ogsr.getStartingPlayer();
		if(spn == cpn)
			vector[OFS_BOARDPOSITION] = 1; //the first in a round
		else{
			int pos = 2;
			for(int i = spn + 1; i != spn; i++){
				if(i == 4)
					i = 0; 
				if(ogsr.getPlayerID(i)!=-1)//not a null player
					if(i == cpn){
						vector[OFS_BOARDPOSITION] = pos;
						break; //found him so we can break
					}else{
						pos++;
					}
			}
		}
		
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
		
		switch (robberHexType) {
	        case SOCResourceConstants.CLAY:
	        	vector[OFS_RESOURCEBLOCKED + SOCResourceConstants.CLAY-1] = 1;
					break;
			case SOCResourceConstants.ORE:
				vector[OFS_RESOURCEBLOCKED + SOCResourceConstants.ORE-1] = 1;
				break;
			case SOCResourceConstants.SHEEP:
				vector[OFS_RESOURCEBLOCKED + SOCResourceConstants.SHEEP-1] = 1;
				break;
			case SOCResourceConstants.WHEAT:
				vector[OFS_RESOURCEBLOCKED + SOCResourceConstants.WHEAT-1] = 1;
				break;
			case SOCResourceConstants.WOOD:
				vector[OFS_RESOURCEBLOCKED + SOCResourceConstants.WOOD-1] = 1;
				break;
			default:
				//this is the desert, ignore
				break;
		}
		vector[OFS_NUMBERBLOCKED] = robberHexNumber;
		
		//remember the player numbers based on the playing order from the current pn onwards
		int[] playingOrderPn = new int[4];//starts with current player number and ends with previous player number
		playingOrderPn[0] = cpn;
		for(int i = 1; i < 4; i++){
			playingOrderPn[i] = cpn + i;
			if(playingOrderPn[i] > 3)
				playingOrderPn[i] = playingOrderPn[i] - 4;
		}
        
		int pn = playingOrderPn[0];//this player
		vector[OFS_PLAYERSCORE] = ogsr.getTotalVP(pn);
		vector[OFS_LA] = ogsr.getLALabel(pn);
		vector[OFS_LR] = ogsr.getLRLabel(pn);
		vector[OFS_ROADS] = ogsr.getRoadsForPlayer(pn).length;//roads.size();
		int[] settCoords = ogsr.getSettlementsForPlayer(pn);
		vector[OFS_SETTLEMENTS] = settCoords.length;
		int[] cityCoords = ogsr.getCitiesForPlayer(pn);
		vector[OFS_CITIES] = cityCoords.length;
		
        //5 positions for resources in hand 
        int[] rss = ogsr.getResources(pn);
        if(rss[5]!=0){ //we shouldn't have unknowns stored in db; 
        	System.err.println("Unknown rss in player hand");//quick debugging TBR soon
        }		        	
		for(int j = 0; j < 5; j++){
			vector[OFS_CLAYINHAND + j] = rss[j];
		}
        
		//6 positions for what port types is this player touching
		int[] tpt = ogsr.getTouchingPortTypes(pn);
		for(int k = 0; k < tpt.length; k++){
			vector[OFS_TOUCHING_PORTS + k] = tpt[k];
		}
		
		//5 positions for getting the sum of numbers on hexes that this player touches attached to each rss type 
		int[][] tn = StacDBHelper.transformToIntArr2(ogsr.getTouchingNumbers(pn));
		int num = 0;
		for(int j = 0; j < 5; j++){
			for(int k : tn[j]){
				num = reduceNumberRange(k);
				vector[OFS_CLAYPRODUCTION + j] += num;
			}
		}
						
		//get the array that contains how much access this player has to a rss type in terms of adjacent number of settlements and cities
		int[] rtn = new int[5];
		rtn = StacDBHelper.transformToIntArr(egsr.getRssTypeAndNumber(pn));
		for(int j = 0; j < 5; j++){
			vector[OFS_CLAYACCESS + j] = rtn[j];
		}
		
		
		//check if any of this player's pieces are on the adj coords nodes
		//loop over its pieces and add to a prod number which represents production based on settlements and cities (sett=1,city=2)
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
    		vector[OFS_NPIECESAFFECTED] = prod;
        }
		
		//one position for the number of knights played;
		vector[OFS_NPLAYEDKNIGHTS] = ogsr.getPlayedKnights(pn);
		
		//current longest current road 
		vector[OFS_CURRENTLONGESTROAD] = egsr.getLongestRoads(pn);
		
		//3 positions for used special dev cards in hand;
		vector[OFS_PLAYEDDEVCARDSINHAND] = ogsr.getPlayedRB(pn);
		vector[OFS_PLAYEDDEVCARDSINHAND + 1] = ogsr.getPlayedDisc(pn);
		vector[OFS_PLAYEDDEVCARDSINHAND + 2] = ogsr.getPlayedMono(pn);
		
		//5 positions for old unplayed dev cards in hand;
        int[] dc = ogsr.getUnplayedDevCards(pn);
		for(int j = 0; j < 4; j++){
			vector[OFS_OLDDEVCARDSINHAND + j] = dc[j];
		}
		//the last position is unknown cards which should be changed to victory point cards
		vector[OFS_VPCARDS] = ogsr.getVictoryDevCards(pn);
		
		//5 positions for unplayed new dev cards
		int[] ndc = ogsr.getNewDevCards(pn);
		for(int j = 0; j < 4; j++){
			vector[OFS_NEWDEVCARDSINHAND + j] = ndc[j];
		}
		
		//one position for describing if this player can do a bank trade;
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
		if(vector[OFS_CLAYINHAND]>0)
			clay = true;
		if(vector[OFS_WOODINHAND]>0)
			wood = true;
		if(vector[OFS_SHEEPINHAND]>0)
			sheep = true;
		if(vector[OFS_WHEATINHAND]>0)
			wheat = true;
		if(vector[OFS_OREINHAND]>0)
			ore = true;
		if(vector[OFS_WHEATINHAND]>1)
			wheat2 = true;
		if(vector[OFS_OREINHAND]>2)
			ore3 = true;
		
		if(clay && wood)
			vector[OFS_CANBUILDROAD] = 1;
		if(clay && wood && wheat && sheep)
			vector[OFS_CANBUILDSETTLEMENT] = 1;		
		if(ore && sheep && wheat)
			vector[OFS_CANBUYCARD] = 1;
		if(ore3 && wheat2)
			vector[OFS_CANBUILDCITY] = 1;

		int sum = 0;
		for(int j = 0; j < 5; j++){
			sum+=rss[j];
		}
		if(sum > 7)
			vector[OFS_OVER7CARDS] = 1;
		
//		//distances to next legal location, to port, to opponent
//		if(egsr.getDistanceToNextLegalLoc(pn)==2)
//			vector[OFS_DISTANCETOLEGAL] = 1;
//		if(egsr.getDistanceToPort(pn)==2)
//			vector[OFS_DISTANCETOPORT] = 1;
//		if(egsr.getDistanceToOpp(pn)==2)
//			vector[OFS_DISTANCETOOPP] = 1;
		
		//possibility for expansion or blocked player? higher is better NOTE: too expensive, but the code might be useful in the future
		if(vector[OFS_ROADS] > 0){
			//for each road find if it is an end of road
			Set edgeOfRoads = new HashSet<Integer>();
			Vector v;
			for(Object i : roads){
				v = SOCBoard.getAdjacentEdgesToEdge_v1((int)i);
				if(v.size() > 0){
					//add to list if it has at least one adjacent edge empty
					int counter = 0;
					for(Object r: v){
						if(!allRoads.contains((int)r))
							counter++;	
					}
					if(counter > 0)
						edgeOfRoads.add(i);
				}
			}
			
			int expValue = 0;
			//for each edge of road find the expansion value and add (for each possible expansion add a 10)
			Vector[] vec;
			int firstEdge;
			int secondEdge;
			for(Object i : edgeOfRoads){
				vec = SOCBoard.get2AdjacentEdgesToEdge_v1((int)i);
				for(int j=0; j < 4; j++){
					if(vec[j].size() > 1){
						firstEdge = (int) vec[j].get(0);
						//check if the edge is free
						boolean free = true;
						if(allRoads.contains(firstEdge))
							free = false; //there is a road there
						//check that the opponent doesn't have a piece adjacent to it, if yes we can't expand 2 as wished.
						//TODO: we can't do this in SS easily, so I would rather not
//						int[] nodes = SOCBoard.getAdjacentNodesToEdge_arr((int) i);
//						if(!SOCBoard.isNodeOnBoard_v1(nodes[0]) || !allCoords.contains(nodes[0]))
//							free= false;
//						if(!SOCBoard.isNodeOnBoard_v1(nodes[1]) || !allCoords.contains(nodes[1]))
//							free= false;
							
						if(free){
							//next edge
							secondEdge = (int) vec[j].get(1);
							if(allRoads.contains(secondEdge))
								free = false; //there is a road there
							if(free){
								expValue +=10;
								break;//we are done with this edge, so we can move to the next
							}
							
							//there could be a second option
							free = true;//make sure the flag is true as the previous one set it to false otherwise we wouldn't be here
							if(vec[j].size() > 2){
								secondEdge = (int) vec[j].get(2);
								if(allRoads.contains(secondEdge))
									free = false; //there is a road there
								if(free){
									expValue +=10;
									break;//we are done with this edge, so we can move to the next
								}
							}
						}
					}
				}
			}
			
			//finally add to the vector the value divided by the number of roads
			expValue/=vector[OFS_ROADS];
        	vector[OFS_EXPPOSSIBILITY] = expValue;
			
			//to check it is legal, get all nodes1 away and check if they are empy
			//keep a counter of all legal locations processed
			//loop over the roads again and check if the two adjacent vertices are legal location
			//add these to a set 
			//get neighbours edges (unoccupied as if they are ours we will go over them anyway, else we are blocked)
			//for each check neighbour vertices that are legal location then add to the set.
			//For each legal in the set
			//if they are legal locations get production and if these are a port and add to a counter
			int[] prodNPort = new int[11];
			Set legalLocations = new HashSet<Integer>();
			
			int[] nodes;
			int[] neighbours1Away;
			for(Object i : roads){
				nodes = SOCBoard.getAdjacentNodesToEdge_arr((int)i);
				//check if these are on board and if these are legal location (i.e. empty and no adjacent settlement)
				if(SOCBoard.isNodeOnBoard_v1(nodes[0]) && !allCoords.contains(nodes[0])){
					neighbours1Away = SOCBoard.getAdjacentNodesToNode_arr_v1(nodes[0]);
					for(int k : neighbours1Away){
						if(allCoords.contains(k))
							nodes[0] =-1; //do not consider this a viable location
					}
				}else
					nodes[0] =-1;
				if(SOCBoard.isNodeOnBoard_v1(nodes[1]) && !allCoords.contains(nodes[1])){
					neighbours1Away = SOCBoard.getAdjacentNodesToNode_arr_v1(nodes[1]);
					for(int k : neighbours1Away){
						if(allCoords.contains(k))
							nodes[1] =-1; //do not consider this a viable location
					}
				}else
					nodes[1] =-1;
				
				if(nodes[0] != -1)
					legalLocations.add(nodes[0]);
				if(nodes[1] != -1)
					legalLocations.add(nodes[1]);
				
				v = SOCBoard.getAdjacentEdgesToEdge_v1((int)i);
				for(Object r: v){
					//if there is a road there already ignore as we are taking care of our own in the logic above, while we can't go over the opponents roads
					if(allRoads.contains(r))
						continue;
					nodes = SOCBoard.getAdjacentNodesToEdge_arr((int)r);
					//check if these are on board and if these are legal location
					if(SOCBoard.isNodeOnBoard_v1(nodes[0]) && !allCoords.contains(nodes[0])){
						neighbours1Away = SOCBoard.getAdjacentNodesToNode_arr_v1(nodes[0]);
						for(int k : neighbours1Away){
							if(allCoords.contains(k))
								nodes[0] =-1; //do not consider this a viable location
						}
					}else
						nodes[0] =-1;
					if(SOCBoard.isNodeOnBoard_v1(nodes[1]) && !allCoords.contains(nodes[1])){
						neighbours1Away = SOCBoard.getAdjacentNodesToNode_arr_v1(nodes[1]);
						for(int k : neighbours1Away){
							if(allCoords.contains(k))
								nodes[1] =-1; //do not consider this a viable location
						}
					}else
						nodes[1] =-1;
					if(nodes[0] != -1)
						legalLocations.add(nodes[0]);
					if(nodes[1] != -1)
						legalLocations.add(nodes[1]);
				}
				
			}
			int type = 0;
			int number = 0;
			/*setting a new hex layout, seems to update the port information also, though not the portlayout which seems to be only used 
			in the creation of a new board not when getting information about a boad...confusing */
			SOCBoard board = SOCBoard.createBoard(null, 4);
			board.setHexLayout(StacDBHelper.transformToIntArr(ogsr.getHexLayout()));
			board.setNumberLayout(StacDBHelper.transformToIntArr(ogsr.getNumberLayout()));
			
			//iterate over all legal locations that this player can reach and add production or if it is a port
			for(Object l : legalLocations){
				v = SOCBoard.getAdjacentHexesToNode((int) l);
				for(Object h : v){
					//get type and number
					type = board.getHexTypeFromCoord((int)h);
					number = reduceNumberRange(board.getNumberOnHexFromCoord((int)h));
					//based on type add to counter index
					switch (type) {
			        case SOCResourceConstants.CLAY:
//			        	System.out.println("Clay" + number);
			        	prodNPort[0] += number;
						break;
					case SOCResourceConstants.ORE:
//						System.out.println("Ore" + number);
						prodNPort[1] += number;
						break;
					case SOCResourceConstants.SHEEP:
//						System.out.println("Sheep" + number);
						prodNPort[2] += number;
						break;
					case SOCResourceConstants.WHEAT:
//						System.out.println("Wheat" + number);
						prodNPort[3] += number;
						break;
					case SOCResourceConstants.WOOD:
//						System.out.println("wood" + number);
						prodNPort[4] += number;
						break;
					default:
						//this is the desert, ignore
						break;
					}
				}
				
				int val = board.getPortTypeFromNodeCoord((int)l);
				if(val > -1)
					prodNPort[5 + val]++;
				
			}
			//(optional)finally normalise and add the results to the vector; 
			//NOTE: not really a good idea as it may hide very good locations and access to ports if there are many bad locations among the set
			int nlegal = legalLocations.size();
			if(nlegal > 0)
			for(int i = 0; i < 11; i++){
				vector[OFS_NEXTLEGALPRODUCTIVITY + i] = prodNPort[i];///nlegal;
			}
		}
		
		for(int i=0; i < 3; i++){
			pn = playingOrderPn[i+1];//in order to get the information of the next player based on the order of play;
			if(ogsr.getPlayerID(pn)!=-1){//only fill with info for the players that exist in the game;	
				vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_SCORE] = ogsr.getTotalVP(pn);
				vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_NPLAYEDKNIGHTS] = ogsr.getPlayedKnights(pn);
				vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_CURRENTLONGESTROAD] = egsr.getLongestRoads(pn);
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
	            	vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_AFFECTEDBYROBBER] = 1;
	            
	    		vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_LA] = ogsr.getLALabel(pn);
	    		vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_LR] = ogsr.getLRLabel(pn);
	    		
	            int sdev = 0;
				
	            int[] oppNdc = ogsr.getNewDevCards(pn);
	            int[] oppDc = ogsr.getUnplayedDevCards(pn);
	    		for(int j = 0; j < 4; j++){
	    			sdev += oppDc[j] + oppNdc[j];
	    		}
	    		sdev += ogsr.getVictoryDevCards(pn); 
	    		
	            vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_HASDEVCARDS] = (sdev > 0) ? 1 : 0;
	    		
	            rss = ogsr.getResources(pn);
	    		for(int j = 0; j < 5; j++){
	    			vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_TOTALRSS] += rss[j];
	    		}
	            
	    		vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_ROADS] = ogsr.getRoadsForPlayer(pn).length;//roads.size();
	    		settCoords = ogsr.getSettlementsForPlayer(pn);
	    		vector[OFS_OPPPLAYERDATA[i] + OFS_SETTLEMENTS] = settCoords.length;
	    		cityCoords = ogsr.getCitiesForPlayer(pn);
	    		vector[OFS_OPPPLAYERDATA[i] + OFS_CITIES] = cityCoords.length;
	    		
	    		//6 positions for what port types is this player touching
	    		tpt = ogsr.getTouchingPortTypes(pn);
	    		for(int k = 0; k < tpt.length; k++){
	    			vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_TOUCHING_PORTS + k] = tpt[k];
	    		}
	    		
	    		//5 positions for getting the sum of numbers on hexes that this player touches attached to each rss type 
	    		tn = StacDBHelper.transformToIntArr2(ogsr.getTouchingNumbers(pn));
	    		num = 0;
	    		for(int j = 0; j < 5; j++){
	    			for(int k : tn[j]){
	    				num = reduceNumberRange(k);
	    				vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_CLAYPRODUCTION + j] += num;
	    			}
	    		}
	    						
	    		//get the array that contains how much access this player has to a rss type in terms of adjacent number of settlements and cities
	    		rtn = new int[5];
	    		rtn = StacDBHelper.transformToIntArr(egsr.getRssTypeAndNumber(pn));
	    		for(int j = 0; j < 5; j++){
	    			vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_CLAYACCESS + j] = rtn[j];
	    		}
			}
    	}
		
    	int naffected = 0;
    	naffected += vector[OFS_AFFECTEDBYROBBER] + vector[OFS_OPPPLAYERDATA[0] + OFS_OPP_AFFECTEDBYROBBER] + 
    			vector[OFS_OPPPLAYERDATA[1] + OFS_OPP_AFFECTEDBYROBBER] + vector[OFS_OPPPLAYERDATA[2] + OFS_OPP_AFFECTEDBYROBBER];
    	vector[OFS_NPLAYERSAFFECTED] = naffected;
    	
		return vector;
	}
	
///////METHODS THAT CAN BE USED BY BOTH ENVIRONMENTS//////	
	
	/**
	 * @param before
	 * @param after
	 * @return
	 */
	private int[] calculateActionVectorFromOld(int[] before, int[] after){
		int[] vector = new int[44];
		vector[0] = after[15] - before[15];// victory points
		vector[1] = after[1] - before[1];//roads
		vector[2] = after[2] - before[2];//settlements
		vector[3] = after[3] - before[3];//cities
		vector[4] = after[16] - before[16];//LA
		vector[5] = after[17] - before[17];//LR
		vector[6] = after[13] - before[13];//type of resource blocked
		vector[7] = after[14] - before[14];//number on the hex of the resource blocked
		vector[8] = after[54] - before[54];//current longest road
		//effect on rss in hand
		for(int j = 0; j < 5; j++){
			vector[9 + j] = after[21 + j] - before[21 + j];
		}
		vector[14] = after[65] - before[65];//can do a bank trade
		vector[15] = after[66] - before[66];//over 7 resources
		//resource production
		for(int j = 0; j < 10; j++){
			vector[16 + j] = after[32 + j] - before[32 + j];
		}
		//access to port types
		for(int j = 0; j < 6; j++){
			vector[26 + j] = after[26 + j] - before[26 + j];
		}
		//effect on dev cards in hand
		for(int j = 0; j < 4; j++){
			vector[32 + j] = after[55 + j] + after[60 + j] - before[55 + j] - before[60 + j]; //take into account both new and old to be sure
		}
		//distances to opponent, port or legal location
		for(int j = 0; j < 3; j++){
			vector[36 + j] = after[67 + j] - before[67 + j];
		}
		//difference in which players are affected by the robber
		int noPlayersAffectedBefore = 0;
		int noPlayersAffectedAfter = 0;
		for(int j = 0; j < 4; j++){
			vector[39 + j] = after[52 + 55*j] - before[52 + 55*j];
			if(after[52 + 55*j] > 0)
				noPlayersAffectedAfter++;
			if(before[52 + 55*j] > 0)
				noPlayersAffectedBefore++;
		}
		//difference in no of players affected by the robber 
		vector[43] = noPlayersAffectedAfter - noPlayersAffectedBefore;
		return vector;
	}
	
//////METHODS FOR SMARTSETTLERS ENVIRONMENT//////	
	
	/**
	 * Same method as the one it overloads, but for the SmartSettlers environment
	 * @param s the state vector
	 * @param bl the board layout
	 * @return
	 */
	private int[] calculateVectorOld(int[] s, Board bl){
        int fsmlevel    = s[OFS_FSMLEVEL];
        int cpn          = s[OFS_FSMPLAYER+fsmlevel]; //this current player is dangerous as JSettlers doesn't change the current player for the discard action, so I need to fix that
		int i, j, l, ind;
		int playerStateSize = 55;
		int[] vector = new int[SIZE_OLD_STATE_VECTOR];
		Arrays.fill(vector, 0);//fill it up with zeros
		vector[0] = 4;//always 4 number of players, at least for now
	 
		//current pieces on board of each type
		for(i = 0; i < NPLAYERS; i++){
			vector[1] += s[OFS_PLAYERDATA[i]+OFS_NROADS];
			vector[2] += s[OFS_PLAYERDATA[i]+OFS_NSETTLEMENTS];
			vector[3] += s[OFS_PLAYERDATA[i]+OFS_NCITIES];
		}
	 
		//resource richness
		 for (i=0; i<N_HEXES; i++)
         {
             int val = reduceNumberRange(bl.hextiles[i].productionNumber);
             switch (bl.hextiles[i].subtype) {
	            case LAND_CLAY:
	 				vector[SOCResourceConstants.CLAY + 3]+=val;
	 				break;
		 			
				case LAND_STONE:
					vector[SOCResourceConstants.ORE + 3]+=val;
					break;
				
				case LAND_SHEEP:
					vector[SOCResourceConstants.SHEEP + 3]+=val;
					break;
		
				case LAND_WHEAT:
					vector[SOCResourceConstants.WHEAT + 3]+=val;
					break;
				
				case LAND_WOOD:
					vector[SOCResourceConstants.WOOD + 3]+=val;
					break;
				
				default:
					//this is the desert, ignore
					break;
		     }
         }
	 
		//if the current player has played a dev card
		vector[9] = s[OFS_PLAYERDATA[cpn] + OFS_HASPLAYEDCARD]; //this may cause issues with discards scenarios when the current player number changes
	 
        //logic for calculating the player's position in turn based on board position and starting player
		int spn = s[OFS_STARTING_PLAYER];
		if(spn == cpn)
			vector[10] = 1; //the first in a round
		else{
			int pos = 2;
			for(i = spn + 1; i != spn; i++){
				if(i == 4)
					i = 0; 
				if(i == cpn){
					vector[10] = pos;
					break; //found him so we can break
				}else{
					pos++;
				}
			}
		}
		 
		//dice result
		vector[11] = s[OFS_DICE];
		//number of cards left in the deck
		vector[12] = NCARDS - s[OFS_NCARDSGONE];
		
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
	        	vector[13] = SOCResourceConstants.CLAY;
				break;
	 			
			case LAND_STONE:
				vector[13] = SOCResourceConstants.ORE;
				break;
			
			case LAND_SHEEP:
				vector[13] = SOCResourceConstants.SHEEP;
				break;
	
			case LAND_WHEAT:
				vector[13] = SOCResourceConstants.WHEAT;
				break;
			
			case LAND_WOOD:
				vector[13] = SOCResourceConstants.WOOD;
				break;
			
			default:
				//this is the desert leave it to 0
				break;
    	}
    	vector[14] = reduceNumberRange(robberNumber);
		
    	Set allPieces;
    	Set nodes2Away;
    	for(i=0; i < 4; i++){//for each player based on the playing order
			int pos = 15 + playerStateSize * i;
			int pn = playingOrderPn[i];//in order to get the information of the next player based on the order of play;
			vector[pos] = s[OFS_PLAYERDATA[pn] + OFS_SCORE];
			if(s[OFS_LARGESTARMY_AT]==pn)
				vector[pos+1] = 1;
			if(s[OFS_LONGESTROAD_AT]==pn)
				vector[pos+2] = 1; 
			vector[pos+3] = s[OFS_PLAYERDATA[pn]+OFS_NROADS];
			vector[pos+4] = s[OFS_PLAYERDATA[pn]+OFS_NSETTLEMENTS];
			vector[pos+5] = s[OFS_PLAYERDATA[pn]+OFS_NCITIES];
			
	        //5 positions for resources in hand (need to order these in the same way JSettlers does)
	        vector[pos+6] = s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_CLAY];
	        vector[pos+7] = s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_STONE];	
	        vector[pos+8] = s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_SHEEP];
	        vector[pos+9] = s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_WHEAT];
	        vector[pos+10] = s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_WOOD];
	        
	        // 6 positions for what port types is this player touching
	        vector[pos+11] = s[OFS_PLAYERDATA[pn] + OFS_ACCESSTOPORT + PORT_MISC -1];
	        vector[pos+12] = s[OFS_PLAYERDATA[pn] + OFS_ACCESSTOPORT + PORT_CLAY -1];
	        vector[pos+13] = s[OFS_PLAYERDATA[pn] + OFS_ACCESSTOPORT + PORT_STONE -1];
	        vector[pos+14] = s[OFS_PLAYERDATA[pn] + OFS_ACCESSTOPORT + PORT_SHEEP -1];
	        vector[pos+15] = s[OFS_PLAYERDATA[pn] + OFS_ACCESSTOPORT + PORT_WHEAT -1];
	        vector[pos+16] = s[OFS_PLAYERDATA[pn] + OFS_ACCESSTOPORT + PORT_WOOD -1];
	        
	        //5 positions for getting the sum of numbers on hexes that this player touches attached to each rss type 
	        //from the indices get the neighbour hexes; if it is a sett index we add the amount once else twice to the correct type
	        //5 positions get the array that contains how much access this player has to a rss type in terms of adjacent number of settlements and cities
	        //from the indices get the neighbour hexes; if it is a sett index we add 1 else 2 to the correct type
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
			            	vector[pos+SOCResourceConstants.CLAY + 16]+=number;
			            	vector[pos+SOCResourceConstants.CLAY + 21]++;
			 				break;
				 			
						case LAND_STONE:
							vector[pos+SOCResourceConstants.ORE + 16]+=number;
							vector[pos+SOCResourceConstants.ORE + 21]++;
							break;
						
						case LAND_SHEEP:
							vector[pos+SOCResourceConstants.SHEEP + 16]+=number;
							vector[pos+SOCResourceConstants.SHEEP + 21]++;
							break;
				
						case LAND_WHEAT:
							vector[pos+SOCResourceConstants.WHEAT + 16]+=number;
							vector[pos+SOCResourceConstants.WHEAT + 21]++;
							break;
						
						case LAND_WOOD:
							vector[pos+SOCResourceConstants.WOOD + 16]+=number;
							vector[pos+SOCResourceConstants.WOOD + 21]++;
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
			            	vector[pos+SOCResourceConstants.CLAY + 16]+=2*number;
			            	vector[pos+SOCResourceConstants.CLAY + 21]+=2;
			 				break;
				 			
						case LAND_STONE:
							vector[pos+SOCResourceConstants.ORE + 16]+=2*number;
							vector[pos+SOCResourceConstants.ORE + 21]+=2;
							break;
						
						case LAND_SHEEP:
							vector[pos+SOCResourceConstants.SHEEP + 16]+=2*number;
							vector[pos+SOCResourceConstants.SHEEP + 21]+=2;
							break;
				
						case LAND_WHEAT:
							vector[pos+SOCResourceConstants.WHEAT + 16]+=2*number;
							vector[pos+SOCResourceConstants.WHEAT + 21]+=2;
							break;
						
						case LAND_WOOD:
							vector[pos+SOCResourceConstants.WOOD + 16]+=2*number;
							vector[pos+SOCResourceConstants.WOOD + 21]+=2;
							break;
						
						default:
							//this is the desert, ignore
							break;
			        }
		        }
	        }
	        
	        //copy everything to the next 10 positions
	        for(j = 0; j < 10; j++){
	        	vector[pos+27 + j] = vector[pos+17 + j];
	        }
	        
	        //10 positions with the above but affected by robber
            ind = 0;
            int prod = 0;
	        boolean affectedByRobber = false;
            for (j=0; j<6; j++)
            {
                ind = bl.neighborHexVertex[robberHex][j];
                if(setts[pn].contains(ind)){
                	prod = 1;
                	affectedByRobber = true;
                }else if(cities[pn].contains(ind)){
                	prod = 2;
                	affectedByRobber = true;
                }
                
                if(prod > 0){
                	 switch (robberType) {
			            case LAND_CLAY:
			            	vector[pos+SOCResourceConstants.CLAY + 26]-=robberNumber*prod;
			            	vector[pos+SOCResourceConstants.CLAY + 31]-=prod;
			 				break;
				 			
						case LAND_STONE:
							vector[pos+SOCResourceConstants.ORE + 26]-=robberNumber*prod;
							vector[pos+SOCResourceConstants.ORE + 31]-=prod;
							break;
						
						case LAND_SHEEP:
							vector[pos+SOCResourceConstants.SHEEP + 26]-=robberNumber*prod;
							vector[pos+SOCResourceConstants.SHEEP + 31]-=prod;
							break;
				
						case LAND_WHEAT:
							vector[pos+SOCResourceConstants.WHEAT + 26]-=robberNumber*prod;
							vector[pos+SOCResourceConstants.WHEAT + 31]-=prod;
							break;
						
						case LAND_WOOD:
							vector[pos+SOCResourceConstants.WOOD + 26]-=robberNumber*prod;
							vector[pos+SOCResourceConstants.WOOD + 31]-=prod;
							break;
						
						default:
							//this is the desert, ignore
							break;
			        }
                }
                //reset for next vertex
                prod = 0;
            }
	        
	        //is affected by robber flag
	        if(affectedByRobber)
	        	vector[pos+37] = 1;
	        
			//one position for the number of knights played;
			vector[pos+38] = s[OFS_PLAYERDATA[pn] + OFS_USEDCARDS + CARD_KNIGHT];
			
			//longest current road 
			vector[pos+39] = s[OFS_PLAYERDATA[pn] + OFS_PLAYERSLONGESTROAD];
			
			//5 positions for old unplayed dev cards in hand;
			vector[pos+40] = s[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_KNIGHT];
			vector[pos+41] = s[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_FREEROAD];
			vector[pos+42] = s[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_FREERESOURCE];
			vector[pos+43] = s[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_MONOPOLY];
			vector[pos+44] = s[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_ONEPOINT];
			
			//5 positions for unplayed new dev cards
			vector[pos+45] = s[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_KNIGHT];
			vector[pos+46] = s[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_FREEROAD];
			vector[pos+47] = s[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_FREERESOURCE];
			vector[pos+48] = s[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_MONOPOLY];
			vector[pos+49] = s[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_ONEPOINT];//this will always be 0
			
			//one position for describing if this player can do a bank trade;
			int cardsRequired = 4;
			if(vector[pos+11]==1)
				cardsRequired = 3;
			for(j = 6; j<11; j++){
				//if it has a port of this type and more than 2 rss
				if(vector[pos+j+6]==1 && vector[pos + j]>=2){
					vector[pos+50] = 1;
					break;
				}else if(vector[pos + j]>=cardsRequired){
					vector[pos+50] = 1;
					break;
				}
			}
	        //one position for describing if this player has over 7 resources;
			int sum = vector[pos+10] + vector[pos+9] + vector[pos+8] + vector[pos+7] + vector[pos+6];
			if(sum > 7)
				vector[pos+51] = 1;
		
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
	        		vector[pos+54] = 1;//opponent
	        	if(s[OFS_VERTICES + (int)v] == VERTEX_EMPTY)
	        		vector[pos+52] = 1;//legal location
	        	//check if its a port (logic taken from perform action in player class, when building a settlement)
                for (j=0; j<6; j++)
                {
                    ind = bl.neighborVertexHex[(int)v][j];
                    if ((ind != -1) && (bl.hextiles[ind].type == TYPE_PORT))
                    {
                        int k = j-2; if (k<0) k+=6;
                        if (k==bl.hextiles[ind].orientation)
                        	vector[pos+53] = 1;
                        k = j-3; if (k<0) k+=6;
                        if (k==bl.hextiles[ind].orientation)
                        	vector[pos+53] = 1;
                    }
                }
	        }
	        nodes2Away.clear();
	        allPieces.clear();
    	}
		
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
			int[] vector = new int[STATE_VECTOR_SIZE];
			Arrays.fill(vector, 0);//fill it up with zeros

			vector[OFS_BIAS_FEATURE] = 1;
			//when this method is called we always set this, but when end of turn action is performed, instead of recalculating this we just unset this feature
			vector[OFS_CURRENTTURN] = 1;
			int dice = s[OFS_DICE];
//			if(!(dice <= 0)){
			//else leave it to 0
			if(dice == 7)
				vector[OFS_DICERESULT] = 1;
//				else
//					vector[OFS_DICERESULT] = reduceNumberRange(dice);
//			}
			
			//current pieces on board of each type
			for(i = 0; i < NPLAYERS; i++){
				vector[OFS_TOTALROADS] += s[OFS_PLAYERDATA[i]+OFS_NROADS];
				vector[OFS_TOTALSETTLEMENTS] += s[OFS_PLAYERDATA[i]+OFS_NSETTLEMENTS];
				vector[OFS_TOTALCITIES] += s[OFS_PLAYERDATA[i]+OFS_NCITIES];
			}
		 
			//if the current player has played a dev card
			vector[OFS_HASPLAYEDDEVCARD] = s[OFS_PLAYERDATA[cpn] + OFS_HASPLAYEDCARD]; //this may cause issues with discards scenarios when the current player number changes
		 
	        //logic for calculating the player's position in turn based on board position and starting player
			int spn = s[OFS_STARTING_PLAYER];
			if(spn == cpn)
				vector[OFS_BOARDPOSITION] = 1; //the first in a round
			else{
				int pos = 2;
				for(i = spn + 1; i != spn; i++){
					if(i == 4)
						i = 0;
					//TODO: check against empty players if we ever want to test against less than 4 players...for now SS doesn't support that
					if(i == cpn){
						vector[OFS_BOARDPOSITION] = pos;
						break; //found him so we can break
					}else{
						pos++;
					}
				}
			}
			 
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
			int pn = playingOrderPn[0];//this player
			
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
			
			//make a list of roads for this player and overall
			Set roads = new HashSet<Integer>();
			Set allRoads = new HashSet<Integer>();
			for (i=0; i<N_EDGES; i++){
				if(s[OFS_EDGES + i] >= EDGE_OCCUPIED){
					allRoads.add(Integer.valueOf(i));
					if(s[OFS_EDGES + i] == EDGE_OCCUPIED + pn)
						roads.add(Integer.valueOf(i));
				}
			}
			
	        int robberHex = s[OFS_ROBBERPLACE];
	    	int robberType = bl.hextiles[robberHex].subtype;
	    	int robberNumber = reduceNumberRange(bl.hextiles[robberHex].productionNumber);
			
			switch (robberType) {
	            case LAND_CLAY:
	            	vector[OFS_RESOURCEBLOCKED + SOCResourceConstants.CLAY-1] = 1;
	 				break;
				case LAND_STONE:
					vector[OFS_RESOURCEBLOCKED + SOCResourceConstants.ORE-1] = 1;
					break;
				case LAND_SHEEP:
					vector[OFS_RESOURCEBLOCKED + SOCResourceConstants.SHEEP-1] = 1;
					break;
				case LAND_WHEAT:
					vector[OFS_RESOURCEBLOCKED + SOCResourceConstants.WHEAT-1] = 1;
					break;
				case LAND_WOOD:
					vector[OFS_RESOURCEBLOCKED + SOCResourceConstants.WOOD-1] = 1;
					break;
				default:
					//this is the desert, ignore
					break;
			}
			
			vector[OFS_NUMBERBLOCKED] = robberNumber;
	    	
			vector[OFS_PLAYERSCORE] = s[OFS_PLAYERDATA[pn] + OFS_SCORE];
			if(s[OFS_LONGESTROAD_AT]==pn)
				vector[OFS_LR] = 1; 
			if(s[OFS_LARGESTARMY_AT]==pn)
				vector[OFS_LA] = 1;
			vector[OFS_ROADS] = s[OFS_PLAYERDATA[pn]+OFS_NROADS];
			vector[OFS_SETTLEMENTS] = s[OFS_PLAYERDATA[pn]+OFS_NSETTLEMENTS];
			vector[OFS_CITIES] = s[OFS_PLAYERDATA[pn]+OFS_NCITIES];
			
	        //5 positions for resources in hand (need to order these in the same way JSettlers does)
	        vector[OFS_CLAYINHAND] = s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_CLAY];
	        vector[OFS_OREINHAND] = s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_STONE];	
	        vector[OFS_SHEEPINHAND] = s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_SHEEP];
	        vector[OFS_WHEATINHAND] = s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_WHEAT];
	        vector[OFS_WOODINHAND] = s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_WOOD];
	        
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
			            	vector[OFS_CLAYPRODUCTION]+=number;
			            	vector[OFS_CLAYACCESS]++;
			 				break;
				 			
						case LAND_STONE:
							vector[OFS_OREPRODUCTION]+=number;
							vector[OFS_OREACCESS]++;
							break;
						
						case LAND_SHEEP:
							vector[OFS_SHEEPPRODUCTION]+=number;
							vector[OFS_SHEEPACCESS]++;
							break;
				
						case LAND_WHEAT:
							vector[OFS_WHEATPRODUCTION]+=number;
							vector[OFS_WHEATACCESS]++;
							break;
						
						case LAND_WOOD:
							vector[OFS_WOODPRODUCTION]+=number;
							vector[OFS_WOODACCESS]++;
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
			            	vector[OFS_CLAYPRODUCTION]+=2*number;
			            	vector[OFS_CLAYACCESS]+=2;
			 				break;
				 			
						case LAND_STONE:
							vector[OFS_OREPRODUCTION]+=2*number;
							vector[OFS_OREACCESS]+=2;
							break;
						
						case LAND_SHEEP:
							vector[OFS_SHEEPPRODUCTION]+=2*number;
							vector[OFS_SHEEPACCESS]+=2;
							break;
				
						case LAND_WHEAT:
							vector[OFS_WHEATPRODUCTION]+=2*number;
							vector[OFS_WHEATACCESS]+=2;
							break;
						
						case LAND_WOOD:
							vector[OFS_WOODPRODUCTION]+=2*number;
							vector[OFS_WOODACCESS]+=2;
							break;
						
						default:
							//this is the desert, ignore
							break;
			        }
		        }
	        }
	        
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
	        if(prod > 0 && robberType != LAND_DESERT){ //similar to JSettlers, do not add these if desert
	        	vector[OFS_AFFECTEDBYROBBER] = 1;
        		vector[OFS_NPIECESAFFECTED] = prod;
	        }
	        
			//one position for the number of knights played;
			vector[OFS_NPLAYEDKNIGHTS] = s[OFS_PLAYERDATA[pn] + OFS_USEDCARDS + CARD_KNIGHT];
			
			//longest current road 
			vector[OFS_CURRENTLONGESTROAD] = s[OFS_PLAYERDATA[pn] + OFS_PLAYERSLONGESTROAD];
			
			//3 positions for used special dev cards in hand;
			vector[OFS_PLAYEDDEVCARDSINHAND] = s[OFS_PLAYERDATA[pn] + OFS_USEDCARDS + CARD_FREEROAD];
			vector[OFS_PLAYEDDEVCARDSINHAND + 1] = s[OFS_PLAYERDATA[pn] + OFS_USEDCARDS + CARD_FREERESOURCE];
			vector[OFS_PLAYEDDEVCARDSINHAND + 2] = s[OFS_PLAYERDATA[pn] + OFS_USEDCARDS + CARD_MONOPOLY];
			
			//4 positions for old unplayed dev cards in hand;
			vector[OFS_OLDDEVCARDSINHAND] = s[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_KNIGHT];
			vector[OFS_OLDDEVCARDSINHAND + 1] = s[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_FREEROAD];
			vector[OFS_OLDDEVCARDSINHAND + 2] = s[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_FREERESOURCE];
			vector[OFS_OLDDEVCARDSINHAND + 3] = s[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_MONOPOLY];
			
			//4 positions for unplayed new dev cards
			vector[OFS_NEWDEVCARDSINHAND] = s[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_KNIGHT];
			vector[OFS_NEWDEVCARDSINHAND + 1] = s[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_FREEROAD];
			vector[OFS_NEWDEVCARDSINHAND + 2] = s[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_FREERESOURCE];
			vector[OFS_NEWDEVCARDSINHAND + 3] = s[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_MONOPOLY];
			
			vector[OFS_VPCARDS] += s[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_ONEPOINT];
			vector[OFS_VPCARDS] += s[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_ONEPOINT];//this will always be 0, but just to be sure
			
			//to describe that enough rss exist to do specific actions
			boolean clay = false, ore = false, sheep = false, wheat = false, wood = false, wheat2 = false, ore3 = false;
			if(vector[OFS_CLAYINHAND]>0)
				clay = true;
			if(vector[OFS_WOODINHAND]>0)
				wood = true;
			if(vector[OFS_SHEEPINHAND]>0)
				sheep = true;
			if(vector[OFS_WHEATINHAND]>0)
				wheat = true;
			if(vector[OFS_OREINHAND]>0)
				ore = true;
			if(vector[OFS_WHEATINHAND]>1)
				wheat2 = true;
			if(vector[OFS_OREINHAND]>2)
				ore3 = true;
			
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
		
			//old distance calculations
//	    	Set allPieces;
//	    	Set nodes2Away;
//			//these distance calculations will probably be slow
//			allPieces = new HashSet();
//	        for(Object city : cities[pn]){
//	        	allPieces.add(city);
//	        }
//	        for(Object sett : setts[pn]){
//	        	allPieces.add(sett);
//	        }
//	        nodes2Away = new HashSet();
//	        for(Object v: allPieces){
//	        	for (l=0; l<6; l++){
//	        		ind = bl.neighborVertexVertex[(int)v][l];
//	        		if(ind != -1)
//	        			for (j=0; j<6; j++){
//	        				if(bl.neighborVertexVertex[ind][j] != -1)
//	        					nodes2Away.add(bl.neighborVertexVertex[ind][j]);//this contains this player's initial pieces
//	        			}
//	            }
//	        }
//	        //remove this player's pieces
//	        nodes2Away.removeAll(allPieces);
//	        //check for opponents, legal locations and ports;
//	        for(Object v : nodes2Away){
//	        	if(s[OFS_VERTICES + (int)v] > VERTEX_TOOCLOSE)
//	        		vector[OFS_DISTANCETOOPP] = 1;//opponent
//	        	if(s[OFS_VERTICES + (int)v] == VERTEX_EMPTY)
//	        		vector[OFS_DISTANCETOLEGAL] = 1;//legal location
//	        	//check if its a port (logic taken from perform action in player class, when building a settlement)
//                for (j=0; j<6; j++)
//                {
//                    ind = bl.neighborVertexHex[(int)v][j];
//                    if ((ind != -1) && (bl.hextiles[ind].type == TYPE_PORT))
//                    {
//                        int k = j-2; if (k<0) k+=6;
//                        if (k==bl.hextiles[ind].orientation)
//                        	vector[OFS_DISTANCETOPORT] = 1;
//                        k = j-3; if (k<0) k+=6;
//                        if (k==bl.hextiles[ind].orientation)
//                        	vector[OFS_DISTANCETOPORT] = 1;
//                    }
//                }
//	        }
//	        nodes2Away.clear();
//	        allPieces.clear();
	        
	        //go over this player's roads
	        if(vector[OFS_ROADS] > 0){
		        int expValue = 0;
		        boolean blocked = true;
		        for(Object o : roads){
		        	expValue += computeExpPossibilityForSS(bl, allRoads, (int) o);
		        }
		        
				//finally add to the vector the value divided by the number of roads
				expValue/=vector[OFS_ROADS];
		        vector[OFS_EXPPOSSIBILITY] = expValue;
		        
		        //create a set of legal locations
				int[] prodNPort = new int[11];
				Set legalLocations = new HashSet<Integer>();
		        for (i=0; i<N_VERTICES; i++){
		        	//is it legal to build here?
		            if (s[OFS_VERTICES+i]==VERTEX_EMPTY){
		            	//get neighbour edges
		            	for (j=0; j<6; j++){
		            		ind = bl.neighborVertexEdge[i][j];
		                    if (ind != -1){ //is it on the board or existing edge
		                    	//is it this player's edge?
		                    	if(s[OFS_EDGES+ind]==EDGE_OCCUPIED + pn){
		                    		legalLocations.add(Integer.valueOf(i));
		                    		break;//no need to look further for this vertex
		                    	}else if(s[OFS_EDGES+ind]==EDGE_EMPTY){
		                    		//get the neighbour edges
		                    		boolean twoAway = false;
		            				for (int k=0; k<6; k++){
		            					int edge = bl.neighborEdgeEdge[ind][k];
		            					if(edge != -1){//does it exist
			            					//it must not be a neighbour of the original vertex, otherwise it is not two away
		            						for (int n=0; n<6; n++){
		            							if(edge == bl.neighborVertexEdge[i][n])
		            								continue;//it is a neighbour, move to next one
		            						}
		            						//if two edges away there is one of this player's roads, add this as a legal location
			            					if(s[OFS_EDGES + edge]==EDGE_OCCUPIED + pn){
			            						legalLocations.add(Integer.valueOf(i));
			            						twoAway = true;
			            						break;
			            					}
		            					}
		            				}
		            				if(twoAway)//we know it is a legal location so we can stop the loop
		            					break;
		                    	}//else it is someone else's road so ignore as this player can't go over it
		                    }
		            	}
		            }
		        }
		        //loop over each of the legal location
		        for(Object loc : legalLocations){
			        //get the neighbour hexes
		        	int hexInd, k;
			        for(j = 0; j < 6; j++){
			        	if (bl.neighborVertexHex[(int)loc][j]!=-1){
			        		hexInd = bl.neighborVertexHex[(int)loc][j];
			        		//check if it is a port and what port type and increment correctly
					        if(bl.hextiles[hexInd].type == TYPE_PORT){
					        	//get the port type and turn into JSettlers encoding for ports for consistency
					        	int val = bl.hextiles[hexInd].subtype; 
			                    //NOTE: this turns hex type into port type and the orientation checks if it has access to the hex
					        	switch (val)
			                    {
			                        case PORT_MISC:
			                            val = SOCBoard.MISC_PORT;
			                            break;
			                        case PORT_CLAY:
			                            val = SOCBoard.CLAY_PORT;
			                            break;
			                        case PORT_STONE:
			                            val = SOCBoard.ORE_PORT;
			                            break;
			                        case PORT_SHEEP:
			                            val = SOCBoard.SHEEP_PORT;
			                            break;
			                        case PORT_WHEAT:
			                            val = SOCBoard.WHEAT_PORT;
			                            break;
			                        case PORT_WOOD:
			                            val = SOCBoard.WOOD_PORT;
			                            break;
			                        default:
			                            //do nothing, smth went wrong
			                        	System.err.println("SS, what port type is this????");
			                    }
				        		k = j-2; if (k<0) k+=6;
		                        if (k==bl.hextiles[hexInd].orientation)
		                            prodNPort[5 + val] ++;
			                    k = j-3; if (k<0) k+=6;
			                    if (k==bl.hextiles[hexInd].orientation)
			                    	prodNPort[5 + val] ++;
			                        
					        }else{
					        	//else its a production tile
					        	int type = bl.hextiles[hexInd].subtype;
					        	int number = reduceNumberRange(bl.hextiles[hexInd].productionNumber);
					        	
						        switch (type) {
						            case LAND_CLAY:
						            	prodNPort[0] += number;
						 				break;
							 			
									case LAND_STONE:
										prodNPort[1] += number;
										break;
									
									case LAND_SHEEP:
										prodNPort[2] += number;
										break;
							
									case LAND_WHEAT:
										prodNPort[3] += number;
										break;
									
									case LAND_WOOD:
										prodNPort[4] += number;
										break;
									
									default:
										//this is the desert or water or port, ignore
										break;
						        }
					        }	
			        	}
			        }
		        }
		        
				//(optional)finally normalise and add the results to the vector; 
				//NOTE: not really a good idea as it may hide very good locations and access to ports if there are many bad locations among the set
				int nlegal = legalLocations.size();
				for( i = 0; i < 11; i++){
					vector[OFS_NEXTLEGALPRODUCTIVITY + i] = prodNPort[i];//nlegal;
				}
			}
       
	    	for(i=0; i < 3; i++){
				pn = playingOrderPn[i+1];//in order to get the information of the next player based on the order of play;
				vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_SCORE] = s[OFS_PLAYERDATA[pn] + OFS_SCORE];
				vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_NPLAYEDKNIGHTS] = s[OFS_PLAYERDATA[pn] + OFS_USEDCARDS + CARD_KNIGHT];
				vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_CURRENTLONGESTROAD] = s[OFS_PLAYERDATA[pn] + OFS_PLAYERSLONGESTROAD];
				boolean affectedByRobber = false;
	            
				if(robberType != LAND_DESERT)//only in case the desert is not the one with the robber
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
	            	vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_AFFECTEDBYROBBER] = 1;
	            
				if(s[OFS_LARGESTARMY_AT]==pn)
					vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_LA] = 1;
				if(s[OFS_LONGESTROAD_AT]==pn)
					vector[OFS_OPPPLAYERDATA[i] + OFS_OPP_LR] = 1; 
				
	            int sdev = s[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_KNIGHT] + s[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_FREEROAD]
	            		+ s[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_FREERESOURCE] + s[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_MONOPOLY] 
	            		+ s[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_KNIGHT] + s[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_FREEROAD] 
	            		+ s[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_FREERESOURCE] + s[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_MONOPOLY]
	            		+ s[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_ONEPOINT] + s[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_ONEPOINT];
				
	            vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_HASDEVCARDS] = (sdev > 0) ? 1 : 0;
	            
		        vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_TOTALRSS] = s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_CLAY] + 
		        		s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_STONE] + 
		        		s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_SHEEP] +	
		        		s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_WHEAT] + 
		        		s[OFS_PLAYERDATA[pn] + OFS_RESOURCES + RES_WOOD];
	            
				vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_ROADS] = s[OFS_PLAYERDATA[pn]+OFS_NROADS];
				vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_SETTLEMENTS] = s[OFS_PLAYERDATA[pn]+OFS_NSETTLEMENTS];
				vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_CITIES] = s[OFS_PLAYERDATA[pn]+OFS_NCITIES];
				
		        vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_TOUCHING_PORTS] = s[OFS_PLAYERDATA[pn] + OFS_ACCESSTOPORT + PORT_MISC -1];
		        vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_TOUCHING_PORTS + 1] = s[OFS_PLAYERDATA[pn] + OFS_ACCESSTOPORT + PORT_CLAY -1];
		        vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_TOUCHING_PORTS + 2] = s[OFS_PLAYERDATA[pn] + OFS_ACCESSTOPORT + PORT_STONE -1];
		        vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_TOUCHING_PORTS + 3] = s[OFS_PLAYERDATA[pn] + OFS_ACCESSTOPORT + PORT_SHEEP -1];
		        vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_TOUCHING_PORTS + 4] = s[OFS_PLAYERDATA[pn] + OFS_ACCESSTOPORT + PORT_WHEAT -1];
		        vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_TOUCHING_PORTS + 5] = s[OFS_PLAYERDATA[pn] + OFS_ACCESSTOPORT + PORT_WOOD -1];
				
		        //set the access and production for this player
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
				            	vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_CLAYPRODUCTION]+=number;
				            	vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_CLAYACCESS]++;
				 				break;
					 			
							case LAND_STONE:
								vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_OREPRODUCTION]+=number;
								vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_OREACCESS]++;
								break;
							
							case LAND_SHEEP:
								vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_SHEEPPRODUCTION]+=number;
								vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_SHEEPACCESS]++;
								break;
					
							case LAND_WHEAT:
								vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_WHEATPRODUCTION]+=number;
								vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_WHEATACCESS]++;
								break;
							
							case LAND_WOOD:
								vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_WOODPRODUCTION]+=number;
								vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_WOODACCESS]++;
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
				            	vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_CLAYPRODUCTION]+=2*number;
				            	vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_CLAYACCESS]+=2;
				 				break;
					 			
							case LAND_STONE:
								vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_OREPRODUCTION]+=2*number;
								vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_OREACCESS]+=2;
								break;
							
							case LAND_SHEEP:
								vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_SHEEPPRODUCTION]+=2*number;
								vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_SHEEPACCESS]+=2;
								break;
					
							case LAND_WHEAT:
								vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_WHEATPRODUCTION]+=2*number;
								vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_WHEATACCESS]+=2;
								break;
							
							case LAND_WOOD:
								vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_WOODPRODUCTION]+=2*number;
								vector[OFS_OPPPLAYERDATA[i]+OFS_OPP_WOODACCESS]+=2;
								break;
							
							default:
								//this is the desert, ignore
								break;
				        }
			        }
		        }
		        
	    	
	    	}
	    	
	    	int naffected = 0;
	    	naffected += vector[OFS_AFFECTEDBYROBBER] + vector[OFS_OPPPLAYERDATA[0] + OFS_OPP_AFFECTEDBYROBBER] + 
	    			vector[OFS_OPPPLAYERDATA[1] + OFS_OPP_AFFECTEDBYROBBER] + vector[OFS_OPPPLAYERDATA[2] + OFS_OPP_AFFECTEDBYROBBER];
	    	vector[OFS_NPLAYERSAFFECTED] = naffected;
	    	
			return vector;
	}
	
	/**
	 * 
	 * @param n the actual number on the hex
	 * @return a normalised value between 2-6
	 */
	private int reduceNumberRange(int n){
		int num = n; 
		//reduce range
		if(num == 8 || num == 6)
			num = 6;
		else if(num == 9 || num == 5)
			num = 5;
		else if(num == 10 || num == 4)
			num = 4;
		else if(num == 11 || num == 3)
			num = 3;
		else if(num == 12 || num == 2)
			num = 2;
		else
			num = 0; //for desert or wrong number range
		
		return num;
	}
	
	/**
	 * @param bl the board layout
	 * @param allRoads all the roads on the map
	 * @param original the starting road
	 * @return a 10 if it is possible to expand more than 2 roads for this particular road or 0 if not
	 */
	private int computeExpPossibilityForSS(Board bl, Set allRoads, int original){
        int firstEdge;
        int secondEdge;
    	//get adjacent edges
    	for (int j=0; j<6; j++){
    		firstEdge = bl.neighborEdgeEdge[original][j];
    		if(firstEdge != -1 && !allRoads.contains(firstEdge)){ //if it is not occupied and if it exists
    				for (int k=0; k<6; k++){
    					secondEdge = bl.neighborEdgeEdge[firstEdge][k];
    						if(secondEdge != -1 && !allRoads.contains(secondEdge)){ //if it is not occupied and if it exists (if it is the original, it is occupied)
    							//check if it is a neighbour of the first road as we are looking two roads ahead not one
    							boolean neighbourOfOriginal = false;
    							for (int i=0; i<6; i++){
    								if(bl.neighborEdgeEdge[secondEdge][i] == original)
    									neighbourOfOriginal = true;
    							}
    							if(!neighbourOfOriginal){
    								//if it is not a neighbour with the original we increase the value
    								return 10;
    							}
    							//if this one is a neighbour we continue with the remaining ones
    						}
    				}
    		}
    	}
    	return 0;
	}
	
	
	
///methods to be called from outside this class for consistency///
	
	public int[] calculateStateVectorSS(int[] s, Board bl){
		if(STATE_VECTOR_TYPE == CURRENT_STATE_VECTOR_TYPE)
			return calculateStateVector(s, bl);
		else
			return calculateVectorOld(s, bl);
	}
	
	public int[] calculateStateVectorJS(ObsGameStateRow ogsr, ExtGameStateRow egsr){
		if(STATE_VECTOR_TYPE == CURRENT_STATE_VECTOR_TYPE)
			return calculateStateVector(ogsr,egsr);
		else
			return calculateVectorOld(ogsr,egsr);
	}
	
	/**
	 * Computed via vector difference between the after and before states, then the dimensionality is reduced by removing the features
	 * that cannot be modified by players' actions.
	 * This implementation works only on the {@link #CURRENT_STATE_VECTOR_TYPE} where every feature has a corresponding offset.
	 * @param beforeState
	 * @param afterState
	 * @return
	 */
	@Override
	public int[] computeActionVector(int[] beforeState, int[] afterState){
		int[] fullVector = SimilarityCalculator.vectorDifference(afterState, beforeState);
		int[] vector = new int[ACTION_VECTOR_SIZE];
		int i;
		//first just write the ones that occupy one index in the vector
		vector[OFS_ACT_BIAS_FEATURE] = 1;
		vector[OFS_ACT_CURRENTTURN] = fullVector[OFS_CURRENTTURN];
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
		vector[OFS_ACT_EXPPOSSIBILITY] = fullVector[OFS_EXPPOSSIBILITY];
//		vector[OFS_ACT_DISTANCETOPORT] = fullVector[OFS_DISTANCETOPORT];
//		vector[OFS_ACT_DISTANCETOLEGAL] = fullVector[OFS_DISTANCETOLEGAL];
//		vector[OFS_ACT_DISTANCETOOPP] = fullVector[OFS_DISTANCETOOPP];
		//now add all the ones that require multiple positions
		for(i=0; i < 5; i++)
			vector[OFS_ACT_RSSINHAND + i] = fullVector[OFS_CLAYINHAND + i];
		for(i=0; i < 5; i++)
			vector[OFS_ACT_RSSPROD + i] = fullVector[OFS_CLAYPRODUCTION + i];
		for(i=0; i < 5; i++)
			vector[OFS_ACT_RSSACCESS + i] = fullVector[OFS_CLAYACCESS + i];
		for(i=0; i < 6; i++)
			vector[OFS_ACT_TOUCHINGPORTS + i] = fullVector[OFS_TOUCHING_PORTS + i];
		for(i=0; i < 5; i++)
			vector[OFS_ACT_RSSBLOCKED + i] = fullVector[OFS_RESOURCEBLOCKED+ i];
		for(i=0; i < 3; i++)
			vector[OFS_ACT_PLAYEDDEVCARDSINHAND + i] = fullVector[OFS_PLAYEDDEVCARDSINHAND+ i];
		for(i=0; i < 4; i++)
			vector[OFS_ACT_OLDDEVCARDSINHAND + i] = fullVector[OFS_OLDDEVCARDSINHAND+ i];
		for(i=0; i < 4; i++)
			vector[OFS_ACT_NEWDEVCARDSINHAND + i] = fullVector[OFS_NEWDEVCARDSINHAND+ i];
		for(i=0; i < 11; i++)
			vector[OFS_ACT_NEXTLEGALPRODUCTIVITY + i] = fullVector[OFS_NEXTLEGALPRODUCTIVITY+ i];
		return vector;
	}
	
	public static double[] updateBuildPossibilities(int[] fVector, double[] expectedRss){
		double[] ret = new double[fVector.length];
		for(int i = 0; i < fVector.length; i++){
			ret[i] = fVector[i];
		}
		//add the expected resources
		for(int i = 0 ; i < 5; i ++){
		ret[NumericalFeatureVectorOffsets.OFS_CLAYINHAND + i] += expectedRss[i];
		}
		//update the build possibilities
		//to describe that enough rss exist to do specific actions
		boolean clay = false, ore = false, sheep = false, wheat = false, wood = false, wheat2 = false, ore3 = false;
		if(ret[OFS_CLAYINHAND]>0)
			clay = true;
		if(ret[OFS_WOODINHAND]>0)
			wood = true;
		if(ret[OFS_SHEEPINHAND]>0)
			sheep = true;
		if(ret[OFS_WHEATINHAND]>0)
			wheat = true;
		if(ret[OFS_OREINHAND]>0)
			ore = true;
		if(ret[OFS_WHEATINHAND]>1)
			wheat2 = true;
		if(ret[OFS_OREINHAND]>2)
			ore3 = true;
		//Note:this is safe, as we can onnly increase in resources when this method is called
		if(clay && wood)
			ret[OFS_CANBUILDROAD] = 1;
		if(clay && wood && wheat && sheep)
			ret[OFS_CANBUILDSETTLEMENT] = 1;		
		if(ore && sheep && wheat)
			ret[OFS_CANBUYCARD] = 1;
		if(ore3 && wheat2)
			ret[OFS_CANBUILDCITY] = 1;
		
		
		//one position for describing if this player can do a bank trade;
		int cardsRequired = 4;
		if(ret[OFS_TOUCHING_PORTS]==1)
			cardsRequired = 3;
		for(int j = 0; j<5; j++){
			//if it has a port of this type and more than 2 rss
			if(ret[OFS_TOUCHING_PORTS + j + 1]==1 && ret[OFS_CLAYINHAND + j]>=2){
				ret[OFS_CANBANKORPORTTRADE] = 1;
				break;
			}else if(ret[OFS_CLAYINHAND + j]>=cardsRequired){
				ret[OFS_CANBANKORPORTTRADE] = 1;
				break;
			}
		}
		
		int sum = 0;
		for(int j = 0; j<5; j++){
			sum += ret[OFS_CLAYINHAND + j];
		}
		if(sum > 7)
			ret[OFS_OVER7CARDS] = 1;
		
		return ret;
	}
	
	
}
