package soc.server.database.stac;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import mcts.game.catan.Catan;
import mcts.game.catan.GameStateConstants;
import mcts.utils.Options;
import representation.FVGenerator;
import representation.FVGeneratorFactory;
import representation.NumericalFVGenerator;
import representation.NumericalFeatureVectorOffsets;
import soc.game.SOCBoard;
import soc.game.SOCGame;
import soc.game.SOCResourceConstants;
import soc.util.SettlersOdds;

/**
 * Utility for parsing a game stored in database, and return the list of state-action pairs given the state type 
 * TODO throw exceptions for each of the reading methods, or write more error reporting in case things go bad
 * @author MD
 *
 */
public class DBGameParser implements NumericalFeatureVectorOffsets,GameStateConstants{
	
	public static final int ROAD_BUILDING = 0;
	public static final int INITIAL_PLACEMENT = 1;
	public static final int NORMAL_PLAY = 2;
	public static final int BEFORE_ROLL = 3;
	public static final int DISCARD = 4;
	public static final int MOVE_ROBBER = 5;
	/** to overcome issue with double precision when checking for equality */
	private static final double EPSILON = 0.00001;
	
	StacDBHelper dbh;
	FVGenerator gen;
	
	public DBGameParser() {
		dbh = new StacDBHelper();
		gen = FVGeneratorFactory.getGenerator();
		dbh.initialize();
		dbh.connect();
	}
	
	public void close(){
		dbh.disconnect();
		gen = null;
		dbh = null;
	}
	
	public static void main(String[] args) {
		DBGameParser parser = new DBGameParser();
		
		ArrayList<Sample> samples = parser.selectSamples(1, BEFORE_ROLL);
		
		for(Sample s: samples){
			System.out.println(Arrays.toString(s.getRecord()));
		}
		
		parser.close();
	}
	
	public ArrayList<Sample> selectSamples(int gameID, int task){
		switch (task) {
		case INITIAL_PLACEMENT:
			return selectInitialPlacement(gameID);
		case ROAD_BUILDING:
			return selectRoadBuilding(gameID);
		case NORMAL_PLAY:
			return selectNormalPlay(gameID);
		case BEFORE_ROLL:
			return selectBeforeRoll(gameID);
		case DISCARD:
			return selectDiscard(gameID);
		case MOVE_ROBBER:
			return selectMoveRobber(gameID);
		default:
			throw new IllegalArgumentException("Incorrect task: " + task + " . Please specify a different value");
		}
	}
	
	
	/**
	 * For selecting all the state action pairs from the initial placement phase; i.e. the two free initial settlements placements.
	 * @param gameID
	 */
	private ArrayList<Sample> selectInitialPlacement(int gameID){
		ObsGameStateRow ogsr;
		ExtGameStateRow egsr;
		GameActionRow gar;
		double[] actionFeatures;
		double[][] poss = null;
		Catan game;
		ArrayList<Sample> listOfSamples = new ArrayList<Sample>();
		
		for(int i = 1; i < 24; i++){ //this is hardcoded since we don't want to loop over the entire game just to get the initial settlement placements
			ogsr = dbh.selectOGSR(gameID, i);
			egsr = dbh.selectEGSR(gameID, i);
			gar = dbh.selectGAR(gameID, i + 1); //action executed from this state
		
			if(ogsr.getGameState() == SOCGame.START1A || ogsr.getGameState() == SOCGame.START2A){
				if(gar.getType() != GameActionRow.ENDTURN){
					//make sure the vp's are correct
					for(int j = 0; j < 4 ; j++){
						ogsr.setTotalVP(j,ogsr.getSettlementsForPlayer(j).length);
						ogsr.setPublicVP(j,ogsr.getSettlementsForPlayer(j).length);
					}
					
					actionFeatures = generateActionVector(gameID, ogsr, egsr, gar);
					game = StacDBToCatanInterface.generateGameFromDB(ogsr, egsr, StacDBToCatanInterface.translateJSStateToSS(ogsr.getGameState()));
					Options options = game.listPossiblities(false);
					poss = listPossibleActionVectors(game,options);
					int[] state = gen.calculateStateVectorSS(game.getState(), Catan.board);
					boolean missed = true;
					
					for(int idx = 0; idx < poss.length; idx++){
						if(Arrays.equals(poss[idx], actionFeatures)){//if we can't find the option, just ignore this 
							Sample sample = new Sample(state, poss, poss[idx]);
							listOfSamples.add(sample);
							missed = false;
							break;
						}
					}
					if(missed)
						System.err.println("Missed initial placement sample in game " + gameID + " before state row id " + i);
					
				}
			}
		}
		
		return listOfSamples;
	}

	/**
	 * Select all state-action pairs, where the action was to place a FREE road
	 * TODO: not sure if I should only learn this one when RB is the only option instead of forcing it to think RB was the only option
	 * @param gameID
	 */
	private ArrayList<Sample> selectRoadBuilding(int gameID){
		ObsGameStateRow ogsr;
		ExtGameStateRow egsr;
		GameActionRow gar;
		double[] actionFeatures;
		double[][] poss;
		Catan game;
		int tableSize = dbh.getTableSize(StacDBHelper.OBSFEATURESTABLE + gameID);
		ArrayList<Sample> listOfSamples = new ArrayList<Sample>();
		
		for(int i = 1; i < tableSize; i++){
			gar = dbh.selectGAR(gameID, i + 1); //action executed from this state
			if(gar.getType() == GameActionRow.BUILDROAD){
				ogsr = dbh.selectOGSR(gameID, i);
				egsr = dbh.selectEGSR(gameID, i);
				//set the correct state
				if(ogsr.getGameState() == SOCGame.START1B)
					ogsr.setGameState(SOCGame.START1B);
				else if(ogsr.getGameState() == SOCGame.START2B)
					ogsr.setGameState(SOCGame.START2B);
				else if(dbh.selectGAR(gameID, i).getType() == GameActionRow.PLAYROAD || dbh.selectGAR(gameID, i-1).getType() == GameActionRow.PLAYROAD)
					ogsr.setGameState(SOCGame.PLACING_FREE_ROAD1);//free road 1 or free road 2 are the same thing as I am only interested in the next action
				else //if it costs to build a road, ignore as the Normal one takes care of that
					continue;
				
				actionFeatures = generateActionVector(gameID, ogsr, egsr, gar);
				game = StacDBToCatanInterface.generateGameFromDB(ogsr, egsr, StacDBToCatanInterface.translateJSStateToSS(ogsr.getGameState()));
				Options options = game.listPossiblities(false);
				poss = listPossibleActionVectors(game,options);
				int[] state = gen.calculateStateVectorSS(game.getState(), Catan.board);
				boolean missed = true;
				
				for(int idx = 0; idx < poss.length; idx++){
					if(Arrays.equals(poss[idx], actionFeatures)){//if we can't find the option, just ignore this sample
						Sample sample = new Sample(state, poss, poss[idx]);
						listOfSamples.add(sample);
						missed = false;
						break;
					}
				}
				if(missed)
					System.err.println("Missed free road building sample in game " + gameID + " before state row id " + i);
				
			}
		}
		return listOfSamples;
	}
	
	/**
	 * Select the state-action pairs in which the action executed was a discard. 
	 * @param gameID
	 */
	private ArrayList<Sample> selectDiscard(int gameID){
		
		ObsGameStateRow ogsr;
		ExtGameStateRow egsr;
		GameActionRow gar;
		double[] actionFeatures;
		double[][] poss;
		Catan game;
		int tableSize = dbh.getTableSize(StacDBHelper.ACTIONSTABLE + gameID);
		ArrayList<Sample> listOfSamples = new ArrayList<Sample>();
		int[] brss;
		int[] arss;
		
		for(int i = 1; i < tableSize; i++){
			gar = dbh.selectGAR(gameID, i); //action executed from this state
		
			if(gar.getType() == GameActionRow.DISCARD){
				//get the state it was executed from
				ogsr = dbh.selectOGSR(gameID, i-1);
				egsr = dbh.selectEGSR(gameID, i-1);
				
				for(int j = 0; j < 4 ; j++){
					//for each player check if the rss hand was modified
					brss = ogsr.getResources(j);
					arss = dbh.selectOGSR(gameID, i).getResources(j);
					
					if(!Arrays.equals(brss, arss)){
						ogsr.setCurrentPlayer(j);//set the current player the one that executed the action so we can generate the list of possible options
						ogsr.setGameState(SOCGame.WAITING_FOR_DISCARDS);
						
						actionFeatures = generateActionVector(gameID, ogsr, egsr, gar);
						game = StacDBToCatanInterface.generateGameFromDB(ogsr, egsr, StacDBToCatanInterface.translateJSStateToSS(ogsr.getGameState()));
						Options options = game.listPossiblities(false);
						poss = listPossibleActionVectors(game,options);
						int[] state = gen.calculateStateVectorSS(game.getState(), Catan.board);
						boolean missed = true;
						for(int idx = 0; idx < poss.length; idx++){
							if(Arrays.equals(poss[idx], actionFeatures)){//if we can't find the option, just ignore this sample
								Sample sample = new Sample(state, poss, poss[idx]);
								listOfSamples.add(sample);
								missed = false;
								break;
							}
						}
						if(missed)
							System.err.println("Missed discard sample in game " + gameID + " before state row id " + (i-1));
					}
				}
			}
		}
		return listOfSamples;
	}
	
	/**
	 * Selects the state-action pairs in which the action was to move the robber and steal resources.
	 * @param gameID
	 */
	private ArrayList<Sample> selectMoveRobber(int gameID){
		ObsGameStateRow ogsr;
		ExtGameStateRow egsr;
		GameActionRow gar;
		double[] actionFeatures;
		double[][] poss;
		Catan game;
		int tableSize = dbh.getTableSize(StacDBHelper.ACTIONSTABLE + gameID);
		ArrayList<Sample> listOfSamples = new ArrayList<Sample>();
		
		for(int i = 1; i < tableSize; i++){
			gar = dbh.selectGAR(gameID, i); //action executed from this state
		
			if(gar.getType() == GameActionRow.MOVEROBBER){
				//get the state it was executed from
				ogsr = dbh.selectOGSR(gameID, i-1);
				egsr = dbh.selectEGSR(gameID, i-1);
				ogsr.setGameState(SOCGame.PLACING_ROBBER);
				
				actionFeatures = generateActionVector(gameID, ogsr, egsr, gar);
				//Note: there are some errors due to bugs in JSettlers logging, just ignore the samples with errors but try to gather as much as possible of the remaining ones
				try {
					game = StacDBToCatanInterface.generateGameFromDB(ogsr, egsr, StacDBToCatanInterface.translateJSStateToSS(ogsr.getGameState()));
				} catch (Exception e) {
					e.printStackTrace();
					continue;
				}
				Options options = game.listPossiblities(false);
				poss = listPossibleActionVectors(game,options);
				int[] state = gen.calculateStateVectorSS(game.getState(), Catan.board);
				boolean missed = true;
				for(int idx = 0; idx < poss.length; idx++){
					if(Arrays.equals(poss[idx], actionFeatures)){//if we can't find the option, just ignore this sample
						Sample sample = new Sample(state, poss, poss[idx]);
						listOfSamples.add(sample);
						missed = false;
						break;
					}
				}
				if(missed){
					System.err.println("Missed move robber sample in game " + gameID + " before state row id " + (i-1));
				}
			}//there are cases where the MOVEROBBER is missed (i.e. included in choosePlayer)
			else if(gar.getType() == GameActionRow.CHOOSEPLAYER && dbh.selectGAR(gameID, i-1).getType() != GameActionRow.MOVEROBBER){
				//get the state it was executed from
				ogsr = dbh.selectOGSR(gameID, i-1);
				egsr = dbh.selectEGSR(gameID, i-1);
				ogsr.setGameState(SOCGame.PLACING_ROBBER);
				
				actionFeatures = generateActionVector(gameID, ogsr, egsr, gar);
				//Note: there are some errors due to bugs in JSettlers logging, just ignore the samples with errors but try to gather as much as possible of the remaining ones
				try {
					game = StacDBToCatanInterface.generateGameFromDB(ogsr, egsr, StacDBToCatanInterface.translateJSStateToSS(ogsr.getGameState()));
				} catch (Exception e) {
					e.printStackTrace();
					continue;
				}
				Options options = game.listPossiblities(false);
				poss = listPossibleActionVectors(game,options);
				int[] state = gen.calculateStateVectorSS(game.getState(), Catan.board);
				boolean missed = true;
				for(int idx = 0; idx < poss.length; idx++){
					if(Arrays.equals(poss[idx], actionFeatures)){//if we can't find the option, just ignore this sample
						Sample sample = new Sample(state, poss, poss[idx]);
						listOfSamples.add(sample);
						missed = false;
						break;
					}
				}
				if(missed){
					System.err.println("Missed move robber sample in game " + gameID + " before state row id " + (i-1));
				}
			}
		}
		return listOfSamples;
	}
	
	/**
	 * Select the state-action pair from the normal game play: every turn after rolling dice.
	 * @param gameID
	 */
	private ArrayList<Sample> selectNormalPlay(int gameID){
		ObsGameStateRow ogsr;
		ExtGameStateRow egsr;
		GameActionRow gar;
		double[] actionFeatures;
		double[][] poss;
		Catan game;
		int startIndex = 1;
		int tableSize = dbh.getTableSize(StacDBHelper.OBSFEATURESTABLE + gameID);
		ArrayList<Sample> listOfSamples = new ArrayList<Sample>();
		
		for(int i = 1; i < dbh.getTableSize(StacDBHelper.ACTIONSTABLE + gameID); i++){
			gar = dbh.selectGAR(gameID, i);
			if(gar.getType() == GameActionRow.ROLL){
				startIndex = i; //start from the next state following on the first roll
				break;
			}
		}
		
		for(int i = startIndex; i < tableSize; i++){
			gar = dbh.selectGAR(gameID, i + 1); //action executed from this state
			ogsr = dbh.selectOGSR(gameID, i);
			egsr = dbh.selectEGSR(gameID, i);
			
			if(gar.getType() == GameActionRow.ENDTURN || 
					(gar.getType() >= GameActionRow.BUYDEVCARD && gar.getType() < GameActionRow.WIN && ogsr.getDiceResult() > 0)||
					gar.getType() == GameActionRow.TRADE ||
					(gar.getType() > GameActionRow.BUILD && gar.getType() < GameActionRow.MOVEROBBER)){
				
				ogsr.setGameState(SOCGame.PLAY1);
				actionFeatures = generateActionVector(gameID, ogsr, egsr, gar);
				//Note: there are some errors due to bugs in JSettlers logging, just ignore the samples with errors but try to gather as much as possible of the remaining ones
				try {
					game = StacDBToCatanInterface.generateGameFromDB(ogsr, egsr, StacDBToCatanInterface.translateJSStateToSS(ogsr.getGameState()));
				} catch (Exception e) {
					e.printStackTrace();
					continue;
				}
				
				Options options = game.listPossiblities(false);
				poss = listPossibleActionVectors(game,options);
				int[] state = gen.calculateStateVectorSS(game.getState(), Catan.board);
				boolean missed = true;
				
				for(int idx = 0; idx < poss.length; idx++){
					if(Arrays.equals(poss[idx], actionFeatures)){//if we can't find the option, just ignore this sample
						Sample sample = new Sample(state, poss, poss[idx]);
						listOfSamples.add(sample);
						missed = false;
						break;
					}
				}
				if(missed && gar.getType() != GameActionRow.BUILDROAD){//there will be quite a few samples missed with free roads as those are handled somewhere else, so don't report these
					for(int idx = 0; idx < poss.length; idx++){
						if(checkDoubleArrayEquals(poss[idx], actionFeatures)){
							Sample sample = new Sample(state, poss, poss[idx]);
							listOfSamples.add(sample);
							missed = false;
							break;
						}
					}
				}//ignores multiple trades with the bank/port or human-human trades as these are not an option in SmartSettlers at the moment
				//doesn't report the cases where freeroads are missed as these are handled elsewhere
				if(missed&& gar.getType() != GameActionRow.BUILDROAD){
					System.err.println("Missed normal sample in game " + gameID + " before state row id " + i + "; this could be caused by trades that are not handled by the model or data corruption when the samples were collected");
				}
			}
		}
		return listOfSamples;
	}
	
	/**
	 * Selects all the state-action pairs from the before roll period of each turn.
	 * @param gameID
	 */
	private ArrayList<Sample> selectBeforeRoll(int gameID){
		ObsGameStateRow ogsr;
		ExtGameStateRow egsr;
		GameActionRow gar;
		double[] actionFeatures;
		double[][] poss;
		Catan game;
		int startIndex = 1;
		int tableSize = dbh.getTableSize(StacDBHelper.ACTIONSTABLE + gameID);
		ArrayList<Sample> listOfSamples = new ArrayList<Sample>();
		
		for(int i = 1; i < dbh.getTableSize(StacDBHelper.ACTIONSTABLE + gameID); i++){
			gar = dbh.selectGAR(gameID, i);
			if(gar.getType() == GameActionRow.ROLL){
				startIndex = i - 1; //start before the first roll
				ogsr = dbh.selectOGSR(gameID, startIndex);
				egsr = dbh.selectEGSR(gameID, startIndex);
				ogsr.setGameState(SOCGame.PLAY);
				
				actionFeatures = generateActionVector(gameID, ogsr, egsr, gar);
				//Note: there are some errors due to bugs in JSettlers logging, just ignore the samples with errors but try to gather as much as possible of the remaining ones
				try {
					game = StacDBToCatanInterface.generateGameFromDB(ogsr, egsr, StacDBToCatanInterface.translateJSStateToSS(ogsr.getGameState()));
				} catch (Exception e) {
					e.printStackTrace();
					continue;
				}
				Options options = game.listPossiblities(false);
				poss = listPossibleActionVectors(game,options);
				int[] state = gen.calculateStateVectorSS(game.getState(), Catan.board);
				if(poss.length == 1)//ignore the one action option as we are trying to learn when we have a decision to make
					continue;
				
				for(int idx = 0; idx < poss.length; idx++){
					if(Arrays.equals(poss[idx], actionFeatures)){//if we can't find the option, just ignore this sample
						Sample sample = new Sample(state, poss, poss[idx]);
						listOfSamples.add(sample);
						break;
					}
				}
				break;
			}
		}
		
		for(int i = startIndex; i < tableSize; i++){
			gar = dbh.selectGAR(gameID, i); //action executed
			if(gar.getType() == GameActionRow.ENDTURN){
				//get the following state 
				ogsr = dbh.selectOGSR(gameID, i);
				egsr = dbh.selectEGSR(gameID, i);
				if(dbh.selectGAR(gameID, i+1).getType() <= GameActionRow.PLAYKNIGHT){//ignore playing other dev cards in that moment
					ogsr.setGameState(SOCGame.PLAY);
					actionFeatures = generateActionVector(gameID, ogsr, egsr, dbh.selectGAR(gameID, i + 1));
					//Note: there are some errors due to bugs in JSettlers logging, just ignore the samples with errors but try to gather as much as possible of the remaining ones
					try {
						game = StacDBToCatanInterface.generateGameFromDB(ogsr, egsr, StacDBToCatanInterface.translateJSStateToSS(ogsr.getGameState()));
					} catch (Exception e) {
						e.printStackTrace();
						continue;
					}
					Options options = game.listPossiblities(false);
					poss = listPossibleActionVectors(game,options);
					int[] state = gen.calculateStateVectorSS(game.getState(), Catan.board);
					if(poss.length == 1)//ignore the one action option as we are trying to learn when we have a decision to make
						continue;
					boolean missed = true;
					for(int idx = 0; idx < poss.length; idx++){//if we can't find the option, just ignore this sample
						if(Arrays.equals(poss[idx], actionFeatures)){
							Sample sample = new Sample(state, poss, poss[idx]);
							listOfSamples.add(sample);
							missed = false;
							break;
						}
					}
					if(missed){
						//try again but this time by checking against a threshold, rather than exact equals
						for(int idx = 0; idx < poss.length; idx++){//if we can't find the option, just ignore this sample
							if(checkDoubleArrayEquals(poss[idx], actionFeatures)){
								Sample sample = new Sample(state, poss, poss[idx]);
								listOfSamples.add(sample);
								missed = false;
								break;
							}
						}
						
						if(missed){
							System.err.println("Warning: Missed before roll sample in game " + gameID + " before state row id " + i);
						}
					}
				}
			}
		}
		return listOfSamples;
	}
	
	/**
	 * This method generates the action representation from the database;
	  	//if roll or buy dev card, do normal, clear rss in hand and add 11111 for the moment in the correct location
		//if end turn add the -1 only
		//if MoveRobber go two steps ahead instead of 1 and also clear rss in hand modification and add 11111 to all
		//else do normal 1 step ahead
	 * If it is a stochastic action, it computes the features accordingly (this is not taken care of in the database)
	 * @param gameID
	 * @param ogsr
	 * @param egsr
	 * @param gar
	 * @return
	 */
	private double[] generateActionVector(int gameID, ObsGameStateRow ogsr, ExtGameStateRow egsr, GameActionRow gar){
		int[] bstateFeatures;
		int[] astateFeatures;
		int[] actionFeatures;
		double[] ret = new double[NumericalFeatureVectorOffsets.ACTION_VECTOR_SIZE];
		
		if(gar.getType() == GameActionRow.ROLL){
			bstateFeatures = gen.calculateStateVectorJS(ogsr, egsr);
			astateFeatures = gen.calculateStateVectorJS(dbh.selectOGSR(gameID, ogsr.getID() + 1), dbh.selectEGSR(gameID, egsr.getID() + 1));
			actionFeatures = gen.computeActionVector(bstateFeatures, astateFeatures);
			//turn it to double representation
			for(int i= 0; i < actionFeatures.length; i++){
				ret[i] = actionFeatures[i];
			}
			
			SOCBoard b = new SOCBoard(null, 4);
			b.makeNewBoard(null, false);//don't care about ports or options here
			b.setHexLayout(StacDBHelper.transformToIntArr(ogsr.getHexLayout()));
			b.setNumberLayout(StacDBHelper.transformToIntArr(ogsr.getNumberLayout()));
			double[] expectedRss = new double[5];
			int[] setts = ogsr.getSettlementsForPlayer(ogsr.getCurrentPlayer());//check if this is the correct player
			for(int s : setts){
				Vector hexes = b.getAdjacentHexesToNode(s);
				for(Object h : hexes){
					if((int)h != ogsr.getRobberHex() && b.getHexTypeFromCoord((int)h) < SOCBoard.WATER_HEX && b.getHexTypeFromCoord((int)h) > SOCBoard.DESERT_HEX){
						expectedRss[b.getHexTypeFromCoord((int)h) - 1] += SettlersOdds.getRollsProb(b.getNumberOnHexFromCoord((int)h));
					}
				}
			}
			int[] cities = ogsr.getCitiesForPlayer(ogsr.getCurrentPlayer());//check if this is the correct player
			for(int c : cities){
				Vector hexes = b.getAdjacentHexesToNode(c);
				for(Object h : hexes){
					if((int)h != ogsr.getRobberHex() && b.getHexTypeFromCoord((int)h) < SOCBoard.WATER_HEX && b.getHexTypeFromCoord((int)h) > SOCBoard.DESERT_HEX){
						expectedRss[b.getHexTypeFromCoord((int)h) - 1] += 2*SettlersOdds.getRollsProb(b.getNumberOnHexFromCoord((int)h));
					}
				}
			}
			
			//update the build possibilities
			double[] temp = NumericalFVGenerator.updateBuildPossibilities(bstateFeatures, expectedRss);
			
			//finally, add all the info to the action vector
			for(int i = 0 ; i < 5; i ++){
				ret[NumericalFeatureVectorOffsets.OFS_ACT_RSSINHAND + i] = temp[NumericalFeatureVectorOffsets.OFS_CLAYINHAND + i] - bstateFeatures[NumericalFeatureVectorOffsets.OFS_CLAYINHAND + i];
				ret[NumericalFeatureVectorOffsets.OFS_ACT_CANBUYCARD + i] = temp[NumericalFeatureVectorOffsets.OFS_CANBUYCARD + i] - bstateFeatures[NumericalFeatureVectorOffsets.OFS_CANBUYCARD + i];
			}
			ret[NumericalFeatureVectorOffsets.OFS_ACT_OVER7CARDS] = temp[NumericalFeatureVectorOffsets.OFS_OVER7CARDS] - bstateFeatures[NumericalFeatureVectorOffsets.OFS_OVER7CARDS];
			ret[NumericalFeatureVectorOffsets.OFS_ACT_DICE] = 0;
			
		}else if(gar.getType() == GameActionRow.BUYDEVCARD){
			//TODO: test this once I the implementation that handles trades is finished
			bstateFeatures = gen.calculateStateVectorJS(ogsr, egsr);
			astateFeatures = gen.calculateStateVectorJS(dbh.selectOGSR(gameID, ogsr.getID() + 1), dbh.selectEGSR(gameID, egsr.getID() + 1));
			actionFeatures = gen.computeActionVector(bstateFeatures, astateFeatures);
			//turn it to double
			for(int i= 0; i < actionFeatures.length; i++){
				ret[i] = actionFeatures[i];
			}
			
			int[] remainingCards = new int[6];
			//initialise the set to the full one
			remainingCards[0] = 14;
			remainingCards[1] = 2;
			remainingCards[2] = 2;
			remainingCards[3] = 2;
			remainingCards[4] = 5;
			remainingCards[5] = 0;
			
			for(int i = 0; i < 4; i++){
				remainingCards[0] -= ogsr.getPlayedKnights(i);
				remainingCards[1] -= ogsr.getPlayedRB(i);
				remainingCards[2] -= ogsr.getPlayedDisc(i);
				remainingCards[3] -= ogsr.getPlayedMono(i);
				if(i == ogsr.getCurrentPlayer()){
					//also get the unplayed and new ones for this player
					remainingCards[0] -= ogsr.getUnplayedDevCards(i)[0];
					remainingCards[1] -= ogsr.getUnplayedDevCards(i)[1];
					remainingCards[2] -= ogsr.getUnplayedDevCards(i)[2];
					remainingCards[3] -= ogsr.getUnplayedDevCards(i)[3];
					remainingCards[0] -= ogsr.getNewDevCards(i)[0];
					remainingCards[1] -= ogsr.getNewDevCards(i)[1];
					remainingCards[2] -= ogsr.getNewDevCards(i)[2];
					remainingCards[3] -= ogsr.getNewDevCards(i)[3];
					remainingCards[4] -= ogsr.getVictoryDevCards(i);
				}
			}
			remainingCards[5] = remainingCards[0] + remainingCards[1] + remainingCards[2] + remainingCards[3] + remainingCards[4];
			double[] chances = SettlersOdds.getProbabilityOfKnownDevCardsJS(remainingCards);
			//add the chances of drawing each to the features by replacing the existing info about gained dev cards
			for(int i = 0 ; i < 5; i ++){ //the vp cards is the last index
				ret[NumericalFeatureVectorOffsets.OFS_ACT_NEWDEVCARDSINHAND + i] = chances[i];
			}
		}else if(gar.getType() == GameActionRow.CHOOSEPLAYER){
			bstateFeatures = gen.calculateStateVectorJS(ogsr, egsr);
			ObsGameStateRow afterOgsr = dbh.selectOGSR(gameID, ogsr.getID() + 1);
			astateFeatures = gen.calculateStateVectorJS(afterOgsr, dbh.selectEGSR(gameID, egsr.getID() + 1));
			actionFeatures = gen.computeActionVector(bstateFeatures, astateFeatures);
			//turn it to double
			for(int i= 0; i < actionFeatures.length; i++){
				ret[i] = actionFeatures[i];
			}
			
			int victim = -1;
			for(int i = 0; i < 4; i++){
				if(i != ogsr.getCurrentPlayer())
					if(!Arrays.equals(ogsr.getResources(i),afterOgsr.getResources(i)))
						victim = i;
			}
			if(victim != -1){
				int[] victimRss = new int[6]; 
				int total = 0;
				int[] tempRss = ogsr.getResources(victim);
				for(int i = 0 ; i < 5; i++){
					victimRss[i] = tempRss[i];
					total+= tempRss[i];
				}
				victimRss[5] = total;
				double[] chances = SettlersOdds.getResourceProbJS(victimRss);
				//add these chances to the representation by replacing the existing info about gained resources and update the build possibilities also
				double[] temp = NumericalFVGenerator.updateBuildPossibilities(bstateFeatures, chances);
				
				//finally, add all the info to the action vector
				for(int i = 0 ; i < 5; i ++){
					ret[NumericalFeatureVectorOffsets.OFS_ACT_RSSINHAND + i] = 0;//temp[NumericalFeatureVectorOffsets.OFS_CLAYINHAND + i] - bstateFeatures[NumericalFeatureVectorOffsets.OFS_CLAYINHAND + i];
					ret[NumericalFeatureVectorOffsets.OFS_ACT_CANBUYCARD + i] = 0;//temp[NumericalFeatureVectorOffsets.OFS_CANBUYCARD + i] - bstateFeatures[NumericalFeatureVectorOffsets.OFS_CANBUYCARD + i];
				}
				ret[NumericalFeatureVectorOffsets.OFS_ACT_OVER7CARDS] = temp[NumericalFeatureVectorOffsets.OFS_OVER7CARDS] - bstateFeatures[NumericalFeatureVectorOffsets.OFS_OVER7CARDS];
				ret[NumericalFeatureVectorOffsets.OFS_ACT_DICE] = 0;
			}
			
		}else if(gar.getType() == GameActionRow.MOVEROBBER){
			int ind = gar.getID(); //either the next one or the one after, depending if the database contains the choose player or not action?
			for(int j = gar.getID() + 1; j < gar.getID() + 2; j++){
				if(dbh.selectGAR(gameID, j).getType() == GameActionRow.CHOOSEPLAYER){
					ind = j;
					break;
				}
			}
			
			bstateFeatures = gen.calculateStateVectorJS(ogsr, egsr);
			ObsGameStateRow afterOgsr = dbh.selectOGSR(gameID, ind);
			astateFeatures = gen.calculateStateVectorJS(afterOgsr, dbh.selectEGSR(gameID, ind));
			actionFeatures = gen.computeActionVector(bstateFeatures, astateFeatures);
			//turn it to double
			for(int i= 0; i < actionFeatures.length; i++){
				ret[i] = actionFeatures[i];
			}
			
			int victim = -1;
			for(int i = 0; i < 4; i++){
				if(i != ogsr.getCurrentPlayer())
					if(!Arrays.equals(ogsr.getResources(i),afterOgsr.getResources(i)))
						victim = i;
			}
			if(victim != -1){
				int[] victimRss = new int[6]; 
				int total = 0;
				int[] tempRss = ogsr.getResources(victim);
				for(int i = 0 ; i < 5; i++){
					victimRss[i] = tempRss[i];
					total+= tempRss[i];
				}
				victimRss[5] = total;
				double[] chances = SettlersOdds.getResourceProbJS(victimRss);
				//add these chances to the representation by replacing the existing info about gained resources and update the build possibilities also
				double[] temp = NumericalFVGenerator.updateBuildPossibilities(bstateFeatures, chances);
				
				//finally, add all the info to the action vector
				for(int i = 0 ; i < 5; i ++){
					ret[NumericalFeatureVectorOffsets.OFS_ACT_RSSINHAND + i] = 0;//temp[NumericalFeatureVectorOffsets.OFS_CLAYINHAND + i] - bstateFeatures[NumericalFeatureVectorOffsets.OFS_CLAYINHAND + i];
					ret[NumericalFeatureVectorOffsets.OFS_ACT_CANBUYCARD + i] = 0;//temp[NumericalFeatureVectorOffsets.OFS_CANBUYCARD + i] - bstateFeatures[NumericalFeatureVectorOffsets.OFS_CANBUYCARD + i];
				}
				ret[NumericalFeatureVectorOffsets.OFS_ACT_OVER7CARDS] = temp[NumericalFeatureVectorOffsets.OFS_OVER7CARDS] - bstateFeatures[NumericalFeatureVectorOffsets.OFS_OVER7CARDS];
				ret[NumericalFeatureVectorOffsets.OFS_ACT_DICE] = 0;
			}
		}else if (gar.getType() == GameActionRow.PLAYKNIGHT){
			//treat the knight card play differently, since we are modelling both the knight and the move robber and player choice together as one action
			//look for the choose player action in the next two steps to get the correct after-state
			int ind = gar.getID() + 1;
			for(int j = gar.getID() + 1; j < gar.getID() + 3; j++){
				if(dbh.selectGAR(gameID, j).getType() == GameActionRow.CHOOSEPLAYER){
					ind = j;
					break;
				}
			}
			
			ObsGameStateRow afterOgsr = dbh.selectOGSR(gameID, ind);
			bstateFeatures = gen.calculateStateVectorJS(ogsr, egsr);
			astateFeatures = gen.calculateStateVectorJS(afterOgsr, dbh.selectEGSR(gameID, ind));
			actionFeatures = gen.computeActionVector(bstateFeatures, astateFeatures);
			//turn it to double
			for(int i= 0; i < actionFeatures.length; i++){
				ret[i] = actionFeatures[i];
			}
			
			int victim = -1;
			for(int i = 0; i < 4; i++){
				if(i != ogsr.getCurrentPlayer())
					if(!Arrays.equals(ogsr.getResources(i),afterOgsr.getResources(i)))
						victim = i;
			}
			if(victim != -1){
				int[] victimRss = new int[6]; 
				int total = 0;
				int[] tempRss = ogsr.getResources(victim);
				for(int i = 0 ; i < 5; i++){
					victimRss[i] = tempRss[i];
					total+= tempRss[i];
				}
				victimRss[5] = total;
				double[] chances = SettlersOdds.getResourceProbJS(victimRss);
				//add these chances to the representation by replacing the existing info about gained resources and update the build possibilities also
				double[] temp = NumericalFVGenerator.updateBuildPossibilities(bstateFeatures, chances);
				
				//finally, add all the info to the action vector
				for(int i = 0 ; i < 5; i ++){
					ret[NumericalFeatureVectorOffsets.OFS_ACT_RSSINHAND + i] = 0;//temp[NumericalFeatureVectorOffsets.OFS_CLAYINHAND + i] - bstateFeatures[NumericalFeatureVectorOffsets.OFS_CLAYINHAND + i];
					ret[NumericalFeatureVectorOffsets.OFS_ACT_CANBUYCARD + i] = 0;//temp[NumericalFeatureVectorOffsets.OFS_CANBUYCARD + i] - bstateFeatures[NumericalFeatureVectorOffsets.OFS_CANBUYCARD + i];
				}
				ret[NumericalFeatureVectorOffsets.OFS_ACT_OVER7CARDS] = temp[NumericalFeatureVectorOffsets.OFS_OVER7CARDS] - bstateFeatures[NumericalFeatureVectorOffsets.OFS_OVER7CARDS];
				ret[NumericalFeatureVectorOffsets.OFS_ACT_DICE] = 0;
			}
			
		}else if(gar.getType() == GameActionRow.ENDTURN){
    		ret[NumericalFeatureVectorOffsets.OFS_ACT_CURRENTTURN] = -1;//ended turn, nothing else changed
		}else{
			bstateFeatures = gen.calculateStateVectorJS(ogsr, egsr);
			ObsGameStateRow aogsr = dbh.selectOGSR(gameID, gar.getID());
			ExtGameStateRow aegsr = dbh.selectEGSR(gameID, gar.getID());
			aogsr.setCurrentPlayer(ogsr.getCurrentPlayer());//this is important for the initial phase when turns change at random
			astateFeatures = gen.calculateStateVectorJS(aogsr, aegsr);
			actionFeatures = gen.computeActionVector(bstateFeatures, astateFeatures);
			//turn it to double
			for(int i= 0; i < actionFeatures.length; i++){
				ret[i] = actionFeatures[i];
			}
		}
		
		return ret;
	}
	
	/**
	 * Slower method than Arrays.equals, but checks equality against an epsilon to handle double imprecision similar to how Nd4j does
	 * @param a1
	 * @param a2
	 * @return
	 */
	private boolean checkDoubleArrayEquals(double[] a1, double[] a2){
		if(a1.length != a2.length)
            return false;
        for(int i = 0; i < a1.length; i++) {
            double eps = Math.abs(a1[i] - a2[i]);
            if(eps > EPSILON)
                return false;
        }
		
		return true;
	}
	
	/**
	 * Counts the number of times a certain representation was legal and what action type was selected in each.
	 * @param gameID
	 */
	public Map<ArrayList<Integer>, ArrayList<Integer>> selectActionTypesCounts(int gameID){
		ObsGameStateRow ogsr;
		ObsGameStateRow aogsr;
		ExtGameStateRow egsr;
		GameActionRow gar;
		Catan game;
		int type = 0;
		int startIndex = 1;
		int tableSize = dbh.getTableSize(StacDBHelper.OBSFEATURESTABLE + gameID);
		Map<ArrayList<Integer>, ArrayList<Integer>> stats = new HashMap<>();
		
		for(int i = 1; i < dbh.getTableSize(StacDBHelper.ACTIONSTABLE + gameID); i++){
			gar = dbh.selectGAR(gameID, i);
			if(gar.getType() == GameActionRow.ROLL){
				startIndex = i; //start from the next state following on the first roll
				break;
			}
		}
		
		for(int i = startIndex; i < tableSize; i++){
			gar = dbh.selectGAR(gameID, i + 1); //action executed from this state
			ogsr = dbh.selectOGSR(gameID, i);
			egsr = dbh.selectEGSR(gameID, i);
			
			if(gar.getType() == GameActionRow.ENDTURN || 
					(gar.getType() >= GameActionRow.BUYDEVCARD && gar.getType() < GameActionRow.WIN && ogsr.getDiceResult() > 0)||
					gar.getType() == GameActionRow.TRADE ||
					(gar.getType() > GameActionRow.BUILD && gar.getType() < GameActionRow.MOVEROBBER)){
				
				ogsr.setGameState(SOCGame.PLAY1);
				//Note: there are some errors due to bugs in JSettlers logging, just ignore the samples with errors but try to gather as much as possible of the remaining ones
				try {
					game = StacDBToCatanInterface.generateGameFromDB(ogsr, egsr, StacDBToCatanInterface.translateJSStateToSS(ogsr.getGameState()));
				} catch (Exception e) {
					e.printStackTrace();
					continue;
				}
				
				ArrayList<Integer> types = game.listNormalActionTypes();
				type = translateGARTypeToSSType(gar.getType());
				if(type == A_TRADE) {
					//find out if it is port trade or trade with an opponent
					int cpn = ogsr.getCurrentPlayer();
					aogsr = dbh.selectOGSR(gameID, i + 1); 
					boolean modified = false;
					for(int n = 0; n < 4; n++) {
						if(n == cpn)
							continue;
						int[] brss = ogsr.getResources(n);
						int[] arss = aogsr.getResources(n);
						if(!Arrays.equals(brss, arss)) {
							modified = true;
							break;
						}
					}
					if(!modified)
						type = A_PORTTRADE;
				}
				
				//if the chosen type is not among the legal types, report an error and skip
				if(!types.contains(type)) {
					System.err.println("Missed normal sample in game " + gameID + " before state row id " + i + "; for type : " + type + " out of legal types: " + types.toString());
					continue;
				}
				
				if(stats.containsKey(types)) {
					ArrayList<Integer> chosen = stats.get(types);
					chosen.add(type);
				}else {
					ArrayList<Integer> chosen = new ArrayList<>();
					chosen.add(type);
					stats.put(types, chosen);
				}
				
			}
		}
		return stats;
	}
	
	/**
	 * 
	 * @param game
	 * @return
	 */
	public static double[][] listPossibleActionVectors(Catan game, Options options) {
		FVGenerator gen = FVGeneratorFactory.getGenerator();
		int[] bsvector = gen.calculateStateVectorSS(game.getState(), Catan.board);
		int[] asvector;
		int[] state = game.getState();
		int fsmlevel = state[OFS_FSMLEVEL];
		int pl = state[OFS_FSMPLAYER + fsmlevel];
		int fsmstate = state[OFS_FSMSTATE + fsmlevel];
		
		ArrayList<int[]> possibilities = options.getOptions();
		
		double[][] ret = new double[possibilities.size()][];
		
		Catan gameClone;
		int[] action;
		for (int i = 0; i < possibilities.size(); i++) {
			gameClone = (Catan) game.copy();
			action = possibilities.get(i);
			if (action[0] == A_ENDTURN) {
				ret[i] = new double[NumericalFeatureVectorOffsets.ACTION_VECTOR_SIZE];
				ret[i][NumericalFeatureVectorOffsets.OFS_ACT_CURRENTTURN] = -1;// ended turn, nothing else changed
			} else {
				gameClone.performAction(action, false);
				state = gameClone.getState();
				//since in the database we have complete trade actions and no offers, we need to change the offer into a trade action
				//simplest way is to actually execute the action here
				if(action[0] == A_OFFER){
		        	//execute the trade by swapping the resources;
		        	state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + action[4]] -= action[3];
		        	if(action[5] > -1)
		        		state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + action[6]] -= action[5];
		        	state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + action[8]] += action[7];
		        	if(action[9] > -1)
		        		state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + action[10]] += action[9];
		        	
		        	//for opponent 
		        	state[OFS_PLAYERDATA[action[2]] + OFS_RESOURCES + action[4]] += action[3];
		        	if(action[5] > -1)
		        		state[OFS_PLAYERDATA[action[2]] + OFS_RESOURCES + action[6]] += action[5];
		        	state[OFS_PLAYERDATA[action[2]] + OFS_RESOURCES + action[8]] -= action[7];
		        	if(action[9] > -1)
		        		state[OFS_PLAYERDATA[action[2]] + OFS_RESOURCES + action[10]] -= action[9];
				}
				
//				// do not perform the state transition, to avoid issues with initial roads and discards
				state[OFS_FSMLEVEL] = fsmlevel;
				state[OFS_FSMPLAYER + fsmlevel] = pl;
				state[OFS_FSMSTATE + fsmlevel] = fsmstate;
//				gameClone = new Catan(state);

				asvector = gen.calculateStateVectorSS(state, Catan.board);
				int[] actionVectors = gen.computeActionVector(bsvector, asvector);
				// turn it to double
				ret[i] = new double[NumericalFeatureVectorOffsets.ACTION_VECTOR_SIZE];
				for (int j = 0; j < actionVectors.length; j++) {
					ret[i][j] = actionVectors[j];
				}

				if (action[0] == A_BUYCARD) {
					int[] remainingCards = new int[6];
					// initialise the set to the full one
					remainingCards[0] = 14;
					remainingCards[1] = 2;
					remainingCards[2] = 2;
					remainingCards[3] = 2;
					remainingCards[4] = 5;
					remainingCards[5] = 0;
					// get everything in the same order as JSettlers
					for (int pn = 0; pn < 4; pn++) {
						remainingCards[0] -= state[OFS_PLAYERDATA[pn] + OFS_USEDCARDS + CARD_KNIGHT];
						remainingCards[1] -= state[OFS_PLAYERDATA[pn] + OFS_USEDCARDS + CARD_FREEROAD];
						remainingCards[2] -= state[OFS_PLAYERDATA[pn] + OFS_USEDCARDS + CARD_FREERESOURCE];
						remainingCards[3] -= state[OFS_PLAYERDATA[pn] + OFS_USEDCARDS + CARD_MONOPOLY];
						if (pn == pl) {
							// also get the unplayed and new ones for this
							// player
							remainingCards[0] -= state[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_KNIGHT];
							remainingCards[1] -= state[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_FREEROAD];
							remainingCards[2] -= state[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_FREERESOURCE];
							remainingCards[3] -= state[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_MONOPOLY];
							remainingCards[0] -= state[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_KNIGHT];
							remainingCards[1] -= state[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_FREEROAD];
							remainingCards[2] -= state[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_FREERESOURCE];
							remainingCards[3] -= state[OFS_PLAYERDATA[pn] + OFS_NEWCARDS + CARD_MONOPOLY];
							remainingCards[4] -= state[OFS_PLAYERDATA[pn] + OFS_OLDCARDS + CARD_ONEPOINT];
						}
					}
					remainingCards[5] = remainingCards[0] + remainingCards[1] + remainingCards[2] + remainingCards[3]
							+ remainingCards[4];
					double[] chances = SettlersOdds.getProbabilityOfKnownDevCardsJS(remainingCards);
					// add the chances of drawing each to the features
					for (int j = 0; j < 5; j++) { // the vp cards is the last
													// index
						ret[i][NumericalFeatureVectorOffsets.OFS_ACT_NEWDEVCARDSINHAND + j] = chances[j];
					}

				} else if (action[0] == A_THROWDICE) {
					double[] expectedRss = new double[5];

					Vector[] setts = new Vector[4];
					Vector[] cities = new Vector[4];
					for (int j = 0; j < 4; j++) {
						setts[j] = new Vector();
						cities[j] = new Vector();
					}
					for (int j = 0; j < N_VERTICES; j++) {
						if (state[OFS_VERTICES + j] >= VERTEX_HASCITY) {
							cities[state[OFS_VERTICES + j] - VERTEX_HASCITY].add(j);
						} else if ((state[OFS_VERTICES + j] >= VERTEX_HASSETTLEMENT)) {
							setts[state[OFS_VERTICES + j] - VERTEX_HASSETTLEMENT].add(j);
						}
					}

					for (Object sett : setts[pl]) {
						int vind = (int) sett;
						int[] hexInd = new int[3];
						int k = 0;
						for (int j = 0; j < 6; j++) {
							if (Catan.board.neighborVertexHex[vind][j] != -1) {
								hexInd[k] = Catan.board.neighborVertexHex[vind][j];
								k++;
							}
						}
						for (int j = 0; j < 3; j++) {
							int type = Catan.board.hextiles[hexInd[j]].subtype;
							int number = Catan.board.hextiles[hexInd[j]].productionNumber;

							switch (type) {
							case LAND_CLAY:
								expectedRss[0] += SettlersOdds.getRollsProb(number);
								break;

							case LAND_STONE:
								expectedRss[1] += SettlersOdds.getRollsProb(number);
								break;

							case LAND_SHEEP:
								expectedRss[2] += SettlersOdds.getRollsProb(number);
								break;

							case LAND_WHEAT:
								expectedRss[3] += SettlersOdds.getRollsProb(number);
								break;

							case LAND_WOOD:
								expectedRss[4] += SettlersOdds.getRollsProb(number);
								break;

							default:
								// this is the desert, ignore
								break;
							}
						}
					}

					for (Object city : cities[pl]) {
						int vind = (Integer) city;
						int[] hexInd = new int[3];
						int k = 0;
						for (int j = 0; j < 6; j++) {
							if (Catan.board.neighborVertexHex[vind][j] != -1) {
								hexInd[k] = Catan.board.neighborVertexHex[vind][j];
								k++;
							}
						}
						for (int j = 0; j < 3; j++) {
							int type = Catan.board.hextiles[hexInd[j]].subtype;
							int number = Catan.board.hextiles[hexInd[j]].productionNumber;

							switch (type) {
							case LAND_CLAY:
								expectedRss[0] += 2 * SettlersOdds.getRollsProb(number);
								break;

							case LAND_STONE:
								expectedRss[1] += 2 * SettlersOdds.getRollsProb(number);
								break;

							case LAND_SHEEP:
								expectedRss[2] += 2 * SettlersOdds.getRollsProb(number);
								break;

							case LAND_WHEAT:
								expectedRss[3] += 2 * SettlersOdds.getRollsProb(number);
								break;

							case LAND_WOOD:
								expectedRss[4] += 2 * SettlersOdds.getRollsProb(number);
								break;

							default:
								// this is the desert, ignore
								break;
							}
						}
					}

					// update the build possibilities
					double[] temp = NumericalFVGenerator.updateBuildPossibilities(bsvector, expectedRss);

					// finally, add all the info to the action vector
					for (int j = 0; j < 5; j++) {
						ret[i][NumericalFeatureVectorOffsets.OFS_ACT_RSSINHAND
								+ j] = temp[NumericalFeatureVectorOffsets.OFS_CLAYINHAND + j]
										- bsvector[NumericalFeatureVectorOffsets.OFS_CLAYINHAND + j];
						ret[i][NumericalFeatureVectorOffsets.OFS_ACT_CANBUYCARD
								+ j] = temp[NumericalFeatureVectorOffsets.OFS_CANBUYCARD + j]
										- bsvector[NumericalFeatureVectorOffsets.OFS_CANBUYCARD + j];
					}
					ret[i][NumericalFeatureVectorOffsets.OFS_ACT_OVER7CARDS] = temp[NumericalFeatureVectorOffsets.OFS_OVER7CARDS]
							- bsvector[NumericalFeatureVectorOffsets.OFS_OVER7CARDS];
					ret[i][NumericalFeatureVectorOffsets.OFS_ACT_DICE] = 0;

				} else if (action[0] == A_PLACEROBBER || action[0] == A_PLAYCARD_KNIGHT) {

					int victim = action[2];
					if (victim != -1) {// if there are no pieces adjacent there is the chance of no victim

						// make sure these are in the same order as in JS
						int[] victimRss = new int[6];
						victimRss[0] = state[OFS_PLAYERDATA[victim] + OFS_RESOURCES + RES_CLAY];
						victimRss[1] = state[OFS_PLAYERDATA[victim] + OFS_RESOURCES + RES_STONE];
						victimRss[2] = state[OFS_PLAYERDATA[victim] + OFS_RESOURCES + RES_SHEEP];
						victimRss[3] = state[OFS_PLAYERDATA[victim] + OFS_RESOURCES + RES_WHEAT];
						victimRss[4] = state[OFS_PLAYERDATA[victim] + OFS_RESOURCES + RES_WOOD];
						for (int j = 0; j < 5; j++) {
							victimRss[5] += victimRss[j];
						}
						double[] chances = SettlersOdds.getResourceProbJS(victimRss);
						// add these chances to the representation by replacing
						// the existing info about gained resources and update
						// the build possibilities also
						double[] temp = NumericalFVGenerator.updateBuildPossibilities(bsvector, chances);

						// finally, add all the info to the action vector
						for (int j = 0; j < 5; j++) {
							ret[i][NumericalFeatureVectorOffsets.OFS_ACT_RSSINHAND + j] = 0;// temp[NumericalFeatureVectorOffsets.OFS_CLAYINHAND + j] - bsvector[NumericalFeatureVectorOffsets.OFS_CLAYINHAND + j];
							ret[i][NumericalFeatureVectorOffsets.OFS_ACT_CANBUYCARD + j] = 0;// temp[NumericalFeatureVectorOffsets.OFS_CANBUYCARD + j] - bsvector[NumericalFeatureVectorOffsets.OFS_CANBUYCARD + j];
						}
						ret[i][NumericalFeatureVectorOffsets.OFS_ACT_OVER7CARDS] = temp[NumericalFeatureVectorOffsets.OFS_OVER7CARDS]
								- bsvector[NumericalFeatureVectorOffsets.OFS_OVER7CARDS];
						ret[i][NumericalFeatureVectorOffsets.OFS_ACT_DICE] = 0;
					}
				}
			}
		}

		return ret;
	}
	
	
	private Integer translateGARTypeToSSType(double type) {
		if(type == GameActionRow.TRADE)
			return A_TRADE; //TODO: I need to differentiate between port trade and trade here somehow
		else if(type == GameActionRow.BUILDROAD)
			return A_BUILDROAD;
		else if(type == GameActionRow.BUILDSETT)
			return A_BUILDSETTLEMENT;
		else if(type == GameActionRow.BUILDCITY)
			return A_BUILDCITY;
		else if(type == GameActionRow.PLAYKNIGHT)
			return A_PLAYCARD_KNIGHT;
		else if(type == GameActionRow.PLAYDISC)
			return A_PLAYCARD_FREERESOURCE;
		else if(type == GameActionRow.PLAYMONO)
			return A_PLAYCARD_MONOPOLY;
		else if(type == GameActionRow.PLAYROAD)
			return A_PLAYCARD_FREEROAD;
		else if(type == GameActionRow.BUYDEVCARD)
			return A_BUYCARD;
		else
			return A_ENDTURN;
	}
		
}
