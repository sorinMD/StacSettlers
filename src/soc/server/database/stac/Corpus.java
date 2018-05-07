package soc.server.database.stac;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import representation.NumericalFVGenerator;
import representation.SimilarityCalculator;
import soc.game.SOCGame;
import soc.message.SOCGamesWithOptions;

/**
 * Class for storing the games from the corpus in such a way that the required info on these can be quickly accessed and used
 * @author MD
 *
 */
public class Corpus{

	public static StacDBHelper dbh = new StacDBHelper();
	/**
	 * To avoid multiple initialisations;
	 */
	public static boolean initialised = false;
	
	/**
	 * The container for the actual corpus information, stored as a mapping from game states to list of state-action pairs feature vectors;
	 */
	public static ConcurrentHashMap<Integer,HashMap<int[],int[]>> corpus;
	
	/**
	 * Connects to the database and performs all the required pre-computations
	 */
	public static void init(){
		System.out.println("Initialising corpus");
		if(initialised)
			return;
		dbh.initialize();
		dbh.connect();
		if(dbh.isConnected()){
			//do the actual initialisation here
			corpus = new ConcurrentHashMap<Integer,HashMap<int[],int[]>>(100);
			//here we will loop over all states in the list, but for now only for the first two
			int gameState;
			NumericalFVGenerator sfs = new NumericalFVGenerator();
			
			for(int i = 1; i<3; i++){
				gameState = 5*i; //this only works for initial state, but this is all we care about in this method for now
				HashMap<int[], int[]> stateAction = new HashMap<>(10000);//a sufficiently large map for each possible state
				//loop over all 60 games in the db;
				for(int gameID = 1; gameID < 61; gameID ++){
					ObsGameStateRow[] states = dbh.getAllObsStatesOfAKind(gameID, gameState);
					Vector beforeStates = new Vector<ObsGameStateRow>();
					
					//special treatment of the initial placement states
					if(gameState == SOCGame.START1A || gameState == SOCGame.START2A){
						//then add only the ones after the virtual end turn used by JSettlers logic
						for(ObsGameStateRow ogsr : states){
							GameActionRow gar = dbh.selectGAR(gameID, ogsr.getID());
							if(gar.getType() == GameActionRow.ENDTURN)
								beforeStates.add(ogsr);
						}
						
					}
					//for the second settlement placement get the first one which doesn't happen before due to a missing endturn before it
					if(gameState == SOCGame.START2A){
						int j = 1;
						while(true){
							ObsGameStateRow og = dbh.selectOGSR(gameID, j);
							if(og.getGameState() == gameState){
								beforeStates.add(og);
								break;
							}
							j++;
						}
					}
					//for all the other states, just add all
					if(gameState > SOCGame.START2A){
						beforeStates.addAll(Arrays.asList(states));//not sure this works...
					}
					
					//finally add to the map the state and the action taken from it
					for(Object ogsr : beforeStates){
						GameActionRow gar = dbh.selectGAR(gameID, ((ObsGameStateRow)ogsr).getID() + 1); //get the action taken from the state
						ExtGameStateRow beforeEGSR = dbh.selectEGSR(gameID, ((ObsGameStateRow)ogsr).getID());
						ObsGameStateRow afterOGSR = dbh.selectOGSR(gameID, gar.getID());//because garID is equal to the after state
						ExtGameStateRow afterEgsr = dbh.selectEGSR(gameID, gar.getID());
					
						int[] bStateFeatures = sfs.calculateStateVectorJS((ObsGameStateRow)ogsr, beforeEGSR);
						int[] actionFeatures = SimilarityCalculator.vectorDifference(sfs.calculateStateVectorJS(afterOGSR,afterEgsr),bStateFeatures); //here do the vector difference
						
						stateAction.put(bStateFeatures, actionFeatures);
					}
				}
				corpus.put(gameState, stateAction);
			}
			
			dbh.disconnect();
			initialised = true;
		}else{
			System.err.println("Initialisation failed. Cannot connect to the database");
		}
		System.out.println("Finished initialising");
		
	}
	
	public static HashMap<int[], int[]> getPreviousPlay(Integer stateType){
		//later a little bit of logic will be required here to handle specific states and following actions
		return corpus.get(stateType);
	}
	
	public static void main(String[] args) {
		Corpus.init();
		HashMap<int[], int[]> prevPlay = Corpus.getPreviousPlay(SOCGame.START1A);
		System.out.println("For Start1A; Size:"+prevPlay.size());
		for(int[] state : prevPlay.keySet()){
			System.out.println("Size:" + state.length + " State:" + Arrays.toString(state));
			System.out.println("Size: "+ prevPlay.get(state).length+ " Action:" + Arrays.toString(prevPlay.get(state)));
		}
		
		prevPlay = Corpus.getPreviousPlay(SOCGame.START2A);
		System.out.println("For Start2A; Size:"+prevPlay.size());
		for(int[] state : prevPlay.keySet()){
			System.out.println("Size:" + state.length + " State:" + Arrays.toString(state));
			System.out.println("Size: "+ prevPlay.get(state).length+ " Action:" + Arrays.toString(prevPlay.get(state)));
		}
	}
	

}
