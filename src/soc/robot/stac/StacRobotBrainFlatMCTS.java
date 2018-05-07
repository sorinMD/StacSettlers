package soc.robot.stac;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;

import soc.disableDebug.D;
import soc.game.SOCGame;
import soc.message.SOCChoosePlayer;
import soc.message.SOCDiceResult;
import soc.message.SOCGameCopy;
import soc.message.SOCMoveRobber;
import soc.message.SOCPutPiece;
import soc.message.SOCRobotFlag;
import soc.robot.SOCBuildPlanStack;
import soc.robot.SOCRobotBrain;
import soc.robot.SOCRobotClient;
import soc.robot.SOCRobotFactory;
import soc.robot.stac.StacRobotBrainRandom.SOCRobotDMRandom;
import soc.robot.stac.flatmcts.CorpusSeeder;
import soc.robot.stac.flatmcts.FlatMCTS;
import soc.robot.stac.flatmcts.FlatMctsType;
import soc.util.CappedQueue;
import soc.util.CutoffExceededException;
import soc.util.SOCRobotParameters;
/**
 * The brain of the robot taking decisions using flat MCTS with domain knowledge extracted from the corpus or from the original JSettlers logic.
 * This class contains the definition of the flat MCTS algorithm used with all the options set here. It also works as the main branching point of
 * whether to use the parent logic or the flat MCTS search as the decision for specific actions.
 * @author MD
 *
 */
public class StacRobotBrainFlatMCTS extends StacRobotBrain {
	/**
	 * The instance that actually contains the MCTS logic. 
	 */
	FlatMCTS mcts;
	/**
	 * How many rollouts. (this might vary based on when in the game we perform the search, i.e. initial placement, final stages of the game etc)
	 */
    static int noSimulations = 1000;
    /**
     * How many turns per simulations. (this might vary based on when in the game we perform the search, i.e. initial placement, final stages of the game etc)
     */
    static int depth = 0; //all the way to the end
    /**
     * don't log when simulating in MCTS as it creates way too many log files
     */
    static boolean log = false;
	
	public StacRobotBrainFlatMCTS(SOCRobotClient rc, SOCRobotParameters params,
            SOCGame ga, CappedQueue mq, StacRobotType robotType) {
        super(rc, params, ga, mq, false, robotType, new HashMap<String,ArrayList<String>>() );
        //decide what type of MCTS we want
        FlatMctsType mType = new FlatMctsType();
        mType.addType(FlatMctsType.REWARD_FUNCTION, "OBSERVABLE");
        mType.addType(FlatMctsType.SIMULATION_RANDOMNESS_PERCENTAGE, "20");
        mType.addType(FlatMctsType.ACTION_SELECTION_POLICY, "MAX_AVERAGE");
        //also decide on the seeder type (corpus, Jsettlers, later could be both or none)
        mType.addType(FlatMctsType.SEED_METHOD, "JSETTLERS");
        mType.addType(FlatMctsType.CLUSTERING_TYPE, "" + CorpusSeeder.NO_CLUSTERING);
        this.mcts = new FlatMCTS(log, mType, this); //here we create the MCTS instance
//        mcts.initialize(); //we will want to initialize just here but there is a bug with the controlling queue preventing us from doing this
    }
	
	@Override
	public void kill(){
		super.kill(); //do the same logic as the parent
		mcts.brain = null; //get rid of the reference to itself so this thread will be cleaned
		mcts = null;
//		mcts.tearDown(); //but also get rid of the running server prepared for simulations
	}
	
    @Override
    protected StacRobotDMFlatMCTS createDM() {
        return new StacRobotDMFlatMCTS(this, buildingPlan);
    }
    
    @Override
    public void recreateDM(){
    	this.decisionMaker = createDM();
    }
    
    @Override
    protected void handleDICERESULT(SOCDiceResult mes){
    	super.handleDICERESULT(mes);
    	if(mes.getResult() == 7){
    		//reset the past decision of robbing before making a new one
    		((StacRobotDMFlatMCTS)decisionMaker).robberLocation = -1; 
    		((StacRobotDMFlatMCTS)decisionMaker).playerToRob = -1;
    	}
    }
	
	public static class StacRobotDMFlatMCTS extends StacRobotDM {
		FlatMCTS mcts;
		
        /*a bug forces us to plan stuff twice as msgs are not processed by the server so it prevents us from doing the normal MCTS logic
         * as a result memorise the locations decided upon during the first search and the second time only resend the msg*/
        protected int robberLocation = -1;
        protected int playerToRob = -1;
        protected int secondSettlementLocation = -1;
        protected int firstSettlementLocation = -1;
		
		public StacRobotDMFlatMCTS(StacRobotBrain br, SOCBuildPlanStack plan) {
			super(br, plan);
			this.mcts = ((StacRobotBrainFlatMCTS) br).mcts;
		}
		
        @Override
        public int selectMoveRobber(int robberHex) {
        	//change the type to the correct next action
        	FlatMctsType mType = mcts.getType();
        	mType.addType(FlatMctsType.GAME_ACTION, "MOVE_ROBBER");
        	mcts.setType(mType);
        	
//        	if(robberLocation != -1){
//        		System.out.println("Remind decision to move robber to " + robberLocation); //debug
//        		return robberLocation;
//        	}
//        	
//        	saveAndSearch();
//        	
//        	if(!mcts.hasFailedSimulations()){
//        		CappedQueue q = mcts.selectPlay();  //get best move and will automatically remember second move if any
//        		SOCMoveRobber msg = (SOCMoveRobber) q.get();
//        		System.out.println("Decided to move the robber at " + msg.getCoordinates());//debug
//        		return msg.getCoordinates();
//        	}else{//parent decision logic
//        		q.clear();//clear the queue so no further actions from MCTS will be taken (might need to rethink this, when extending to other actions of the game)
        		return super.selectMoveRobber(robberHex);
//        	}
        }
        
        @Override
        public int choosePlayerToRob(){
//        	if(playerToRob != -1){
//        		System.out.println("Remind choose player to rob: " + playerToRob + " for player "+ brain.getPlayerNumber()); //debug
//        		return playerToRob;
//        	}
//        	//in here just take the next message in the queue and execute if any exists; else just do parent logic
//        	CappedQueue q = mcts.getQueue();
//        	if(!q.empty()){ //check if there is a choice to make if there are more than 1 possible victims
//        		SOCChoosePlayer msg = (SOCChoosePlayer) q.get();
//        		System.out.println("Decided to rob player " + msg.getChoice());//debug
//        		return msg.getChoice();
//        	}else
        		return super.choosePlayerToRob();
        }
        
        @Override
        public void planInitialSettlements() {
        	//change the type to the correct next action
        	FlatMctsType mType = mcts.getType();
        	mType.addType(FlatMctsType.GAME_ACTION, "INITIAL_SETTLEMENT");
        	mcts.setType(mType);
        	
        	//if we have planned before do not plan again, just remind the server which location we decided on
//        	if(firstSettlementLocation!= -1){
//        		firstSettlement = firstSettlementLocation;
//        		System.out.println("Remind decision to place first settlement at " + firstSettlementLocation);//debug
//        		return;
//        	}
//        	saveAndSearch();
//        	
//        	if(!mcts.hasFailedSimulations()){
//        		CappedQueue q = mcts.selectPlay();  //get best move and will automatically remember second move if any
//        		SOCPutPiece msg = (SOCPutPiece) q.get();
//        		firstSettlement = msg.getCoordinates();
//        		firstSettlementLocation = msg.getCoordinates();
//        		System.out.println("Decided to place first settlement at " + msg.getCoordinates());//debug
//        	}else{//parent decision logic
        		super.planInitialSettlements();
//        	}
        }

        @Override
        public void planSecondSettlement() {
        	//change the type to the correct next action
        	FlatMctsType mType = mcts.getType();
        	mType.addType(FlatMctsType.GAME_ACTION, "SECOND_SETTLEMENT");
        	mcts.setType(mType);
        	
        	//if we have planned before do not plan again, just remind the server which location we decided on
        	if(secondSettlementLocation!= -1){
        		secondSettlement = secondSettlementLocation;
//        		System.out.println("Remind to place second settlement at " + secondSettlementLocation);//debug
        		return;
        	}
        	saveAndSearch();
        	
        	if(!mcts.hasFailedSimulations()){
        		mcts.getQueue().empty();//empty before adding any moves
        		CappedQueue q = mcts.selectPlay();  
        		SOCPutPiece msg = (SOCPutPiece) q.get();
        		secondSettlementLocation = msg.getCoordinates();
        		secondSettlement = msg.getCoordinates();
//        		System.out.println("Decided to place second settlement at " + msg.getCoordinates());//debug
        	}else{//parent decision logic
        		super.planSecondSettlement();
        	}
        }
        
        /**
         * This method takes care of saving the game and running the MCTS search. In the meantime it forces the server not to end its turn
         * by telling it its not a robot and resets the flag after the search. It also suspends the debug and cleans after itself by deleting the saved info.
         */
        private void saveAndSearch(){
        	SOCRobotClient cl = brain.getClient();
        	//lie to the server that we are not a robot so it won't end my turn
        	cl.put(SOCRobotFlag.toCmd(brain.getGame().getName(), false, brain.getPlayerNumber()));
        	//send the request 
        	cl.put(SOCGameCopy.toCmd(brain.getGame().getName(), "robot", brain.getPlayerNumber()));
        	//create necessary directories
        	File dir = new File("saves/robot");
        	if(!dir.exists())
        		dir.mkdirs();
        	//execute the saving procedure for this robot
        	cl.unsynckedGameCopy(new SOCGameCopy(brain.getGame().getName(), "saves/robot", -1));
        	
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
        	
        	final boolean debug = D.ebugIsEnabled();
        	if(debug)
        		D.ebug_disable();
        	mcts.run(depth, noSimulations);//run a search
        	if(debug)
        		D.ebug_enable();//enable it back only if needed

        	//delete contents of robot folder without folder
        	File[] files = dir.listFiles();
        	for(File f : files)
        		f.delete();
        	
        	//remind the server that we are a robot so the game won't get stuck later //this doesn't always seem to work
        	cl.put(SOCRobotFlag.toCmd(brain.getGame().getName(), true, brain.getPlayerNumber()));
        }
	}
	
	public static class StacRobotFlatMCTSFactory  implements SOCRobotFactory {
        private final StacRobotType robotType;
		
		public StacRobotFlatMCTSFactory(StacRobotType robotType) {  
            this.robotType = robotType;
        }
		
        public SOCRobotBrain getRobot(SOCRobotClient cl, SOCRobotParameters params, SOCGame ga,
                CappedQueue mq) {
            return new StacRobotBrainFlatMCTS(cl, params, ga, mq, robotType);
        }

        public boolean isType(String type) {
            return robotType.isType(type);
        }

        public void setTypeFlag(String type, String param) {
            robotType.addType(type,  param);
        }

        public void setTypeFlag(String type) {
            robotType.addType(type);
        }
		
	}
	
}
