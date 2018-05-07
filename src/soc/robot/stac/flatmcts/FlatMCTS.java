package soc.robot.stac.flatmcts;

import java.awt.Toolkit;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Vector;

import soc.client.SOCPlayerClient.GameOptionServerSet;
import soc.disableDebug.D;
import soc.game.SOCBoard;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceSet;
import soc.message.SOCChoosePlayer;
import soc.message.SOCMoveRobber;
import soc.message.SOCNewGameWithOptionsRequest;
import soc.message.SOCPutPiece;
import soc.message.SOCStartGame;
import soc.robot.SOCBuildingSpeedEstimate;
import soc.robot.SOCBuildingSpeedFast;
import soc.robot.SOCPlayerTracker;
import soc.robot.SOCPossibleCity;
import soc.robot.SOCPossibleSettlement;
import soc.robot.SOCResSetBuildTimePair;
import soc.robot.SOCRobotBrain;
import soc.robot.SOCRobotClient;
import soc.robot.SOCRobotDM;
import soc.robot.SOCRobotDMImpl;
import soc.robot.SOCRobotFactory;
import soc.robot.stac.SettlementNode;
import soc.robot.stac.StacRobotBrain;
import soc.robot.stac.StacRobotBrainFlatMCTS;
import soc.robot.stac.StacRobotBrainRandom;
import soc.robot.stac.StacRobotDummyBrain;
import soc.robot.stac.StacRobotType;
import soc.robot.stac.simulation.Simulation;
import soc.server.SOCServer;
import soc.server.database.NullDBLogger;
import soc.server.genericServer.LocalStringConnection;
import soc.server.genericServer.LocalStringServerSocket;
import soc.server.genericServer.Server;
import soc.server.logger.SOCFileLogger;
import soc.server.logger.SOCLogger;
import soc.server.logger.SOCNullLogger;
import soc.util.CappedQueue;
import soc.util.CutoffExceededException;
import soc.util.DeepCopy;
import soc.util.SOCRobotParameters;
import soc.util.Timer;

/**
 * Monte Carlo Tree Search implementation. To select among options set the list of {@link FlatMctsType} accordingly:
 * <ul>
 * 	<li> Simulation Policy can be changed by modifying the value in SIMULATION_RANDOMNESS_PERCENTAGE type
 * 	<li> Action Selection policy can be changed to different types; see {@link #selectPlay()} method in this class
 * 	<li> Seeding method can vary between JSettlers, Corpus, both or none (Only first two implemented yet)
 * 	<li> If seeding method is Corpus or both, a clustering method can be chosen between state relevance, state and action relevance or none 
 * 	<li> Rewarding method is chosen from the available functions inside {@link FlatMctsRewards#getReward(String, int, int)}
 * 	<li> The action type in order to perform the correct expansion step.
 * </ul> 
 * The search can be done either by a playing robot using {@link StacRobotBrainFlatMCTS} brain type or by a human player using the normal game interface 
 * NOTE: Currently only a flat Monte Carlo method (no tree expansion further than 1-2 moves)
 * @author MD
 *
 */
public class FlatMCTS {

	/**
	 * This value is equal to 10 mins. Its used to stop the algorithm in case it gets stuck.
	 */
	private static long maxSimulationTime = 600000;
	/**
	 * only for 4 player games
	 */
	private static int numPlayers = 4; 
	/**
	 * name for agents playing during the simulations
	 */
	private static String agentName = "Stac0-best";
    /**
     * small random number to break ties randomly
     */
	static Random r = new Random(); 
    /**
     * epsilon value for avoiding N/A
     */
	static double epsilon = 1e-6;
	/**
	 * Name of game to differentiate in the logs.
	 */
    private static String testName = "Simulation";
    /**
     * The root node. Will always be null in terms of messages/actions but will contain the list of children, thus forming
     * a tree-like structure.
     */
    private TreeNode root;
    /**
     * Dummy results logger (we don't want it to write anything, but we need it for game stats)
     */
    private NullDBLogger logger;
    /**
     * The file logger.
     */
    private SOCLogger gameLogger;
    /**
     * list of factories. (later to be used for storing factories creating opponent models)
     */
    private List<FactoryDescr> factories;
    /**
     * The server
     */
    private SOCServer server;
    /**
     * The connection to the server;
     */
    private LocalStringConnection prCli;
    /**
     * Smth which should be useful, but I don't know what for.
     */
    private GameOptionServerSet gOpts;
    /**
     * The player number for the robot using this implementation of MCTS.
     */
    private int ourPlayerNumber;
    /**
     * The number of turns for a rollout;
     */
    private int simulationDepth;
    /**
     * the queue through which it will communicate the actions to the robot(both during MCTS execution and the results).
     */
    private CappedQueue queue;
    /**
     * A timer for keeping track how long this execution takes
     */
    private Timer timer;
    /**
     * The pause in the robot brain before simulations have started. In order to reset it to the correct value afterwards
     */
    private int robotPause;
    /**
     * Containing different parameters for setting different metrics in the algorithm and choosing on which action to apply the search.
     */
    private FlatMctsType type;
    /**
     * The container for transmitting the results of a simulation
     */
    private FlatMctsRewards results;
    /**
     * A reference to the brain using this implementations in order to have access to the information in the memory;
     */
    public SOCRobotBrain brain;
    /**
     * The seeder object which will be decided upon based on the mcts type;
     */
    private Seeder seeder;
    /**
     * A flag mentioning that the simulations were stopped due to a timeout, probably caused by the robot getting stuck,
     * which at its turn could have been caused by a failed game save. It happens very rare, but when it does, 
     * the best approach is to execute the parent's logic instead of follow the decision made by the search algorithm.
     */
    private boolean failedSimulations = false;
    
    /**
     * Constructor
     * @param toLog if we want to keep logs (most likely not as it would make simulations slow)
     * @param mType the type of the mcts algorithm
     * @param mBrain access to the brain is required for extracting beliefs and other game information
     */
    public FlatMCTS(boolean toLog, FlatMctsType mType, StacRobotBrainFlatMCTS mBrain) {
	    logger = new NullDBLogger();
	    SOCLogger nullLogger = new SOCNullLogger();
	    SOCLogger fileLogger = new SOCFileLogger(SOCServer.LOG_DIR);
        gameLogger = nullLogger;// Default: don't log
        if (toLog) {
        	gameLogger = fileLogger;
        }
        queue = new CappedQueue();
        results = new FlatMctsRewards();
        factories = new ArrayList<FactoryDescr>();
        type = mType;
        brain = mBrain;
        ourPlayerNumber = -1;//it will be set when run is being called as there isn't one yet (this code gets executed before sitDown)
        String seedMethod = (String) type.getTypeParam(FlatMctsType.SEED_METHOD);
        if(seedMethod.equals("CORPUS")){
        	int clusterType = (Integer) type.getTypeParam(FlatMctsType.CLUSTERING_TYPE);
        	seeder = new CorpusSeeder(brain, true, clusterType);
        }else if(seedMethod.equals("JSETTLERS")){
        	seeder = new JSettlersSeeder(brain);
        }else if(seedMethod.equals("CORPUS_AND_JSETTLERS")){
        	//not supported yet
        }else if(seedMethod.equals("NONE")){
        	seeder = new JSettlersSeeder(brain); //use JSettlers seeder for expansion only
        }else{
        	D.ebugERROR("Undefined seeder type");     	
        	seeder = null; //undefined seeder type so ignore the seeding step
        }       
    }
    
    /**
     * Special constructor for manual tests. It creates a dummy brain that acts only as a container for the later logic of MCTS
     * @param toLog if we want to keep logs (most likely not as it would make simulations slow)
     * @param mType the type of the mcts algorithm
     * @param pn our player number
     */
    public FlatMCTS(boolean toLog, FlatMctsType mType, int pn, SOCGame ga) {
	    logger = new NullDBLogger();
	    SOCLogger nullLogger = new SOCNullLogger();
	    SOCLogger fileLogger = new SOCFileLogger(SOCServer.LOG_DIR);
        gameLogger = nullLogger;// Default: don't log
        if (toLog) {
        	gameLogger = fileLogger;
        }
        queue = new CappedQueue();
        results = new FlatMctsRewards();
        factories = new ArrayList<FactoryDescr>();
        type = mType;
        //allow the special case of running simulations manually
        ourPlayerNumber = pn;
        
        //read game object and array of trackers from file
        SOCGame game = (SOCGame) DeepCopy.copy(ga); //readFromFile(DeepCopy.SAVES_DIR + "robot/server_soc.game.SOCGame");
        
        HashMap<Integer, SOCPlayerTracker> playerTrackers = new HashMap<>();
     	ArrayList trackersList = (ArrayList) DeepCopy.readFromFile(DeepCopy.SAVES_DIR + "robot/" + 1 + "_" + ArrayList.class.getName()); //when manual testing we will always be player 0 so 1 is just fine (assume a 4 player game always)
	    Object[] pt =  trackersList.toArray();
	    for(int i = 0; i< 4; i++){
	      	 playerTrackers.put(i, (SOCPlayerTracker) pt[i]);
	    }

        //Steps: create a brain to act as a container for trackers/estimator/game object and because trackers and SOCPieces need a brain 
        brain = new StacRobotDummyBrain(new SOCRobotClient(null, testName, agentName, "", null), new SOCRobotParameters(300, 500, 0f, 0f, 0f, 0f, 0f, SOCRobotDMImpl.FAST_STRATEGY, 0), game, new CappedQueue(),pn);
        //recreate the links exactly as in client, but do not update, just replace the nonexistent trackers
        int n = game.maxPlayers;
    	for(int i = 0; i < n; i++){
			SOCPlayer p = game.getPlayer(i);
			if(i==pn){
				p.setRobotFlagUnsafe(true);//make sure we are known as a robot so our turn can be ended
			}
			p.setGame(game); //restore reference to this game in the player objects
		}
        for(int i = 0; i < n; i++){
			SOCPlayerTracker pti = playerTrackers.get(i);
			pti.setBrain(brain);
			//update the reference to the correct brain in PossibleCities and PossibleSettlements objects
			Iterator posCitiesIter = pti.getPossibleCities().values().iterator();
			while (posCitiesIter.hasNext())
	        {
	            SOCPossibleCity posCity = (SOCPossibleCity) posCitiesIter.next();
	            posCity.setBrain(brain);
	        }
			Iterator posSettlIter = pti.getPossibleSettlements().values().iterator();
			while (posSettlIter.hasNext())
	        {
	            SOCPossibleSettlement posSettl = (SOCPossibleSettlement) posSettlIter.next();
	            posSettl.setBrain(brain);
	        }
			pti.setPlayer(game.getPlayer(i));
		}
        ((StacRobotDummyBrain)brain).setPlayerTrackers(playerTrackers);
        
        //should also add our player tracker and our player data
        ((StacRobotDummyBrain)brain).setOurPlayerData(game.getPlayer(pn));
        ((StacRobotDummyBrain)brain).setOurPlayerTracker(playerTrackers.get(pn));
        
        String seedMethod = (String) type.getTypeParam(FlatMctsType.SEED_METHOD);
        if(seedMethod.equals("CORPUS")){
        	int clusterType = (Integer) type.getTypeParam(FlatMctsType.CLUSTERING_TYPE);
        	seeder = new CorpusSeeder(brain, true, clusterType);
        }else if(seedMethod.equals("JSETTLERS")){
        	seeder = new JSettlersSeeder(brain);
        }else if(seedMethod.equals("CORPUS_AND_JSETTLERS")){
        	//not supported yet
        }else if(seedMethod.equals("NONE")){
        	seeder = new JSettlersSeeder(brain); //use JSettlers seeder for expansion only
        }else{
        	D.ebugERROR("Undefined seeder type");     	
        	seeder = null; //undefined seeder type so ignore the seeding step
        }  
    }
    
	/**
	 * Starts server and logger, connects to server and also sets up the 4 robots that will play the game.
	 */
	public void initialize(){
            SOCServer.GAME_NAME_MAX_LENGTH = 100;
            SOCServer.CLIENT_MAX_CREATE_GAMES = -1; //disable a check for max number of created games by the client to avoid stopping after 5 simulations
            logger.startRun(testName);
            //start a server and get new default options
            //don't use the Toulouse parser
            server = new SOCServer(SOCServer.SIMULATION_STRINGPORT, 30, logger, null, null, gameLogger, false);
//        server.setPriority(5); //we don't need this priority do we?  
        server.start();
        Server.trackThread(server, null);
        gOpts =  new GameOptionServerSet();
        
        try {
			prCli = LocalStringServerSocket.connectTo(SOCServer.SIMULATION_STRINGPORT);
		} catch (ConnectException e) {
			D.ebugFATAL(e, "Cannot connect to the simulation stringport");
//			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			D.ebugFATAL(e, "Cannot connect to the simulation stringport");
//			e.printStackTrace();
		}
        setUpRobots();
	}
	/**
	 * Stops the server and the logger.
	 */
	public void tearDown(){
        //always stop the server and deallocate
        prCli.disconnect();
        prCli = null;
        server.stopServer();
        server = null;	
        logger.endRun();
	}
	
	/**
	 * As the name suggests. It creates the robots based on the simulation policy selected. 
	 * Also it passes a reference to the queue which will act as a controlling queue and a reference to the container for gathering results
	 * 
	 */
	private void setUpRobots(){
        //we want to have the 0-best as a part of the simulation policy
		StacRobotType sType = new StacRobotType();
        sType.addType(StacRobotType.ORIGINAL_ROBOT);
        sType.addType(StacRobotType.CHOOSE_BEST_MINUS_N_BUILD_PLAN,"0");
        //for now just get how random to have the robots from the MCTS type;
        int randomPercentage = (Integer) type.getTypeParam(FlatMctsType.SIMULATION_RANDOMNESS_PERCENTAGE);
        //TODO later in development we will also choose whether to use this agent's belief over the other robots
        //now define the robots that will play as a percentage JSettlers and rest random
        SOCRobotFactory factory;
        factories.clear();//clear factories before adding new ones
        for(int i = 0; i < 4; i++){ //a factory for each robot as each brain will have a different controlling queue and only one of them will be gathering the stats/results;
        	factory = new StacRobotBrainRandom.SOCRobotRandomFactory(false, false, randomPercentage, sType); 
        	if(i == ourPlayerNumber){
        		((StacRobotBrainRandom.SOCRobotRandomFactory)factory).setQueue(queue); //pass a reference to the main queue
        		((StacRobotBrainRandom.SOCRobotRandomFactory)factory).setStatsContainer(results); //and a reference to the results
        	}
        	factories.add(new FactoryDescr(factory, agentName + "_" +  i, 1));
        }
        //create the robots on the server
        for (FactoryDescr f : factories) {
            server.setupLocalRobots(f.factory, f.name, f.count);
        }
	}
	/**
	 * Caution: the simulation policy type cannot be modified as it is fixed when we initially setup the robots;
	 * Neither can the seeding method.
	 * @param t the new list of types
	 */
	public void setType(FlatMctsType t){
		type = t;
	}

    public FlatMctsType getType(){
    	return type;
    }
	
	/**
	 * This method will run the simulation code up to a specific depth
	 * @param tn the tree node from where the simulation will be ran
	 * @return  a double value which is the reward/utility value
	 */
	private double rollOut(TreeNode tn) throws Exception {
		logger.resetGamesDone();//just to make sure we start from 0 ;)
		prCli.put(SOCNewGameWithOptionsRequest.toCmd("simulation-master", "", "localhost", testName, gOpts.optionSet));
		//we want the robots' order not to get shuffled and load from the saved state when starting; pass the simdepth to stop the game earlier; don't care about loading a saved board
        prCli.put(SOCStartGame.toCmd(testName,true,true, "saves/robot", simulationDepth, -1, false, StacRobotBrain.isChatNegotiation(), false, false)); 
        //hack for waiting for the game to stop
        int counter = 0; //1 min = 60000 ms, so maxCounter = 60000/20 = 3000
        while(logger.getNumGamesDone() == 0 && counter <= 3000){//sometimes games get stuck and never finish so only allow a min max per game (this may be too much)
        	Thread.sleep(20);  // Wait a little bit until we see if the game is finished;
        	counter++;
        }
        logger.resetGamesDone();
        // destroy the last game - it's finished or has failed
        server.destroyGame(testName, false);
        // Clean out contents of prCli
        while (prCli.isInputAvailable()) {
            prCli.readNext();
        }
        return reward();
        
    }
	/**
	 * It contains the logic for rewarding at a terminal state based on the reward type selected.
	 * @return a double value in the range [0,1] if normalised, else a double value
	 */
	private double reward(){
        String rewardType = (String) type.getTypeParam(FlatMctsType.REWARD_FUNCTION);
        /*
         * This is a horrible thing to do, but I have no other option. When the game finishes, the final stats are not communicated to every player
         * so our brain won't have access to the true final VP's when it is trying to collect them in handleGameStats method (as it is expected).
         * For now hack this around in the following way: all information is collected by the random brain, but get the endVP from the DBLogger.
         * Later we would want the stats to be communicated differently, e.g. through the intended method broadcast maybe? TODO
         */
        for(int i = 0; i < 4; i++){
        	results.getEndVPs()[i] = logger.getStats(agentName + "_" + i).getVictoryPoints();
        	logger.clearVictoryPoints(agentName + "_" + i);
        }
        if(rewardType.equals("0_1_END")){ // returns a 0 or a 1 depending on vp >=10 using DBLogger (no need to use the rewards class) 
        	/**
        	 * NOTE: If you are planning to use just this rewarding method comment the above for loop
        	 */
        	int vp = logger.getStats(agentName + "_" + (ourPlayerNumber)).getVictoryPoints();
	      	 for(int j = 0; j < numPlayers; j++){
	        		logger.clearVictoryPoints(agentName + "_" + (j));
	        	 }
	        if(vp  >= 10){
	        	return 1;
	        }else
	        	return 0;
        	
        }else
        	return results.getReward(rewardType, ourPlayerNumber, simulationDepth);
	}
	
	/**
	 * This is the method that contains the actual MCTS logic (i.e. the order the 4 steps of the algorithm aside from the expansion part)
	 * @param noSimulations number of total roll-outs.
	 */
	public void execute(int noSimulations) {
        // turn off the pause in the robotbrain, but remember the pause length as it needs to be reset after simulations if there is a human playing;
        robotPause = SOCRobotBrain.getDelayTime();
		SOCRobotBrain.setDelayTime(0);
		Timer t = new Timer();
		for (int i=0; i<noSimulations; i++) {
			queue.clear();  //empty queue of messages before adding new actions
			results.clearStats();//also clear the results in preparation for the next rollout
			List<TreeNode> visited = new LinkedList<TreeNode>(); //list for keeping track of the visited nodes for backpropagation
	        TreeNode cur = root; //always start from root
	        visited.add(root);
	        while (!cur.isLeaf()) {							
	            cur = selectUCT(cur);				
	            visited.add(cur);
	        }
	        double value = 0;
	        try {
				value = rollOut(cur);
			} catch (Exception e) {
				e.printStackTrace();
			}	
	        //propagate results back up the tree
	        for (TreeNode node : visited) {
	            // would need extra logic for a greater expansion policy as settlers is a n-player game
	            node.updateStats(value);				
	        }
//	        results.display();//for debugging purposes
	        if(t.elapsed() > maxSimulationTime){
	        	failedSimulations = true; // if we needed to stop the simulation let the robot know;
	        	Simulation.failedMctsSimulation = true; // and the process starting new games
	        	break;
	        }
	    }//finished
		queue.clear();// clear the queue
		SOCRobotBrain.setDelayTime(robotPause);//set the delay back to the original value;
	}
	  
    /**
     * Selects next(best) action using UCT (UCB for trees so this is actually just UCB);
     * 
     * @return the child node that gives promising results or needs exploring
     */
    private TreeNode selectUCT(TreeNode current) {
        TreeNode selected = null;
        double bestValue = Double.MIN_VALUE;
        TreeNode[] children = current.children;
        
        for (TreeNode c : children) {
            double uctValue =
                    c.totValue / (c.nVisits + epsilon) + 
                            Math.sqrt(Math.log(current.nVisits+1) / (c.nVisits + epsilon)) +
                            r.nextDouble() * epsilon;
            if (uctValue > bestValue) {
                selected = c;
                bestValue = uctValue;
            }
        }
    	try {
			queue.put(selected.message); //add action to the queue
		} catch (CutoffExceededException e) {
			D.ebugFATAL(e, "Cannot add action to the controlling queue");
//			e.printStackTrace(); 
		}
        return selected;
    }
	
    /**
     * 
     * @param current current state;
     * @return the node with the maximum value/times pulled ratio
     */
    private TreeNode selectMaxAverage(TreeNode current){
        TreeNode selected = null;
        double bestValue = Double.MIN_VALUE;
        TreeNode[] children = current.children;
        
        for (TreeNode c : children) {
            double ratioValue = c.totValue / c.nVisits  + r.nextDouble() * epsilon;
            if (ratioValue > bestValue) {
                selected = c;
                bestValue = ratioValue;
            }
        }
    	try {
			queue.put(selected.message); //add action to the queue
		} catch (CutoffExceededException e) {
			D.ebugFATAL(e, "Cannot add action to the controlling queue");
//			e.printStackTrace(); 
		}
        return selected;
    }
    
    /**
     * 
     * @param current current state;
     * @return the node with the maximum value/times pulled ratio
     */
    private TreeNode selectMostExplored(TreeNode current){
        TreeNode selected = null;
        double bestValue = Double.MIN_VALUE;
        TreeNode[] children = current.children;
        
        for (TreeNode c : children) {
            double exploredValue = c.nVisits  + r.nextDouble() * epsilon;
            if (exploredValue > bestValue) {
                selected = c;
                bestValue = exploredValue;
            }
        }
    	try {
			queue.put(selected.message); //add action to the queue
		} catch (CutoffExceededException e) {
			D.ebugFATAL(e, "Cannot add action to the controlling queue");
//			e.printStackTrace();  
		}
        return selected;
    }
    
    /**
     * 
     * @param current current state;
     * @return the node with the maximum value
     */
    private TreeNode selectMaxValue(TreeNode current){
        TreeNode selected = null;
        double bestValue = Double.MIN_VALUE;
        TreeNode[] children = current.children;
        
        for (TreeNode c : children) {
            double value = c.totValue + r.nextDouble() * epsilon;
            if (value > bestValue) {
                selected = c;
                bestValue = value;
            }
        }
    	try {
			queue.put(selected.message); //add action to the queue
		} catch (CutoffExceededException e) {
			D.ebugFATAL(e, "Cannot add action to the controlling queue");
//			e.printStackTrace(); 
		}
        return selected;
    }
    
    /**
     * Expands the current node by adding the reachable states to the list of children based on which action is next to take.
     * This is required due to not having a method that returns all legal actions based on the state. Hopefully this will change in the future
     */
	private void expand(TreeNode n){
		String actionType = (String) type.getTypeParam(FlatMctsType.GAME_ACTION);
		switch (actionType) {
		case "INITIAL_SETTLEMENT":
			expandForInitialPlacement(n);
			break;
		case "SECOND_SETTLEMENT":
			expandForInitialPlacement(n);
			break;
		case "MOVE_ROBBER":
			expandForRobberAction(n);
			break;
		default:
			break; //do not expand if we don't recognise the action type 
		}
	}
    
    /**
     * NOTE: these expansion steps should be something like game.getLegalActions() and for each create a node however.... 
     * When this will be improved these expand methods can be turned into one simple and quick method;
     * expands based on the locations where the robber can be placed and who we can steal from
     */
    private void expandForRobberAction(TreeNode n) {
//    	SOCGame game = (SOCGame) DeepCopy.copy(brain.getGame()); 
    	SOCGame game = (SOCGame) DeepCopy.readFromFile(DeepCopy.SAVES_DIR + "robot/server_soc.game.SOCGame"); // it doesn't affect its decision and we always need this to avoid a nullpointer when we follow the planned decision in the real game
    	int[] hexes = game.getBoard().getHexLandCoords();
    	int robberHex = game.getBoard().getRobberHex();
    	
    	ArrayList list = new ArrayList();//an array list of objects for dynamically deciding the number of children 
    	for(int i=0; i < hexes.length; i++){
            if (hexes[i] != robberHex && game.getBoard().getHexTypeFromCoord(hexes[i]) != SOCBoard.DESERT_HEX)
            {
            	list.add(hexes[i]);//not current location or desert
            }
    	}
    	Object[] hexArray = list.toArray();
    	n.children = new TreeNode[list.size()];//create the array of children in the treeNode
        for (int i=0; i<list.size(); i++) {
            n.children[i] = new TreeNode(); //initialise them
            n.children[i].message = new SOCMoveRobber(testName,ourPlayerNumber,(Integer)hexArray[i]); //add the action that takes us into the child state
        }

        //this will be the second expansion based on whom we can steal from; REMEMBER: this needs to be done on the new game state
        for(TreeNode c : n.children){
        	//create a clone of the game
        	SOCGame temp = (SOCGame) DeepCopy.copy(game);
        	//play the action described to get in the child node
        	temp.moveRobber(ourPlayerNumber, ((SOCMoveRobber) c.message).getCoordinates());
	        Vector pl = temp.getPossibleVictims();//get victims from the temp game
	    	//create ChoosePlayer message with the number
	        //only if its bigger then one, as the game logic will quickly handle the situation when <=1 
	        if(pl.size() > 1){
	        	//get the array of players that could be robbed from that location
	        	SOCPlayer[] playersArray = new SOCPlayer[pl.size()];
	        	int j = 0;
	        	for(Object o : pl){
	        		playersArray[j] = (SOCPlayer) o;
	        		j++;
	        	}
	        	
	        	c.children = new TreeNode[pl.size()];
	        	for(int i=0; i<pl.size(); i++){
	        		c.children[i] = new TreeNode(); //initiate them
	        		c.children[i].message = new SOCChoosePlayer(testName,playersArray[i].getPlayerNumber()); //add the action that takes us into the child state
	        	}
	        }
        }
    }
    
    /**
     * Expands based on all legal locations for placing our first and second settlement.
     * @param n the node to expand
     */
    private void expandForInitialPlacement(TreeNode n) {
    	//get the list of all the legal actions and create a tree with the branching factor equal to the number of legal moves
    	List<SettlementNode> possibleSettlements = seeder.getLegalSettlements();
    	n.children = new TreeNode[possibleSettlements.size()];//create the array of children for the root
    	Object[] locationsArray = possibleSettlements.toArray();
        for (int i=0; i < possibleSettlements.size(); i++) {
            n.children[i] = new TreeNode(); //initialise them
            SettlementNode node = (SettlementNode) locationsArray[i];
            int coordinate = node.getNode();
            n.children[i].message = new SOCPutPiece(testName,ourPlayerNumber,SOCPlayingPiece.SETTLEMENT,coordinate); //add the action that takes us into the child state
        }
    }
    
    /**
     * Puts the next few moves the current player should play in the queue (up to two levels). 
     * It chooses them based on the action selection policy selected.
     * To be called after the MCTS algorithm has finished its search.
     * @return the queue containing the next actions
     */
    public CappedQueue selectPlay(){
    	queue.clear();// clear the queue before
    	String asPolicy = (String) type.getTypeParam(FlatMctsType.ACTION_SELECTION_POLICY);
    	if(asPolicy.equals("UCT")){
	    	TreeNode selected = selectUCT(root); //robber placement
	    	if(!selected.isLeaf()){//if there are multiple victims
	    		selected = selectUCT(selected); //player to steal from;
	    	}
    	}else if(asPolicy.equals("MAX_VALUE")){
	    	TreeNode selected = selectMaxValue(root); //robber placement
	    	if(!selected.isLeaf()){//if there are multiple victims
	    		selected = selectMaxValue(selected); //player to steal from;
	    	}
	    }else if(asPolicy.equals("MAX_AVERAGE")){
	    	TreeNode selected = selectMaxAverage(root); //robber placement
	    	if(!selected.isLeaf()){//if there are multiple victims
	    		selected = selectMaxAverage(selected); //player to steal from;
	    	}
	    }else if(asPolicy.equals("MOST_EXPLORED")){
	    	TreeNode selected = selectMostExplored(root); //robber placement
	    	if(!selected.isLeaf()){//if there are multiple victims
	    		selected = selectMostExplored(selected); //player to steal from;
	    	}
	    }else{
    		D.ebugERROR("Undefined policy: returning based on UCT");
	    	TreeNode selected = selectUCT(root); //robber placement
	    	if(!selected.isLeaf()){//if there are multiple victims
	    		selected = selectUCT(selected); //player to steal from;
	    	}
	    }
    	return queue;
    }
    /**
     * @return the flag telling us if we failed simulations
     */
    public boolean hasFailedSimulations(){
    	return failedSimulations;
    }
    
    /**
     * Gives access to the queue.
     * @return the queue containing the messages for the next actions.
     */
    public CappedQueue getQueue(){
    	return queue;
    }

    public Seeder getSeeder(){
    	return seeder;
    }
    
    /**
	 * The method to be called on when wanting to execute the search. It takes care of all the initialisation and cleaning up.
	 * It doesn't return a result(best choice). In order to achieve that, call selectPlay()
	 * Handles both manual i.e. brain == null and robot use. 
	 * 
	 * @param pn The player using this MCTS implementation (the reason for passing this value here is that when constructing this instance the player number may have not been decided yet)
	 * @param depth Number of turns for each simulation
	 * @param noSimulations how many rollouts in total
	 */
	public void run(int depth, int noSimulations){
		if(ourPlayerNumber == -1)//only if the brain is real and not a dummy
			ourPlayerNumber = ((StacRobotBrainFlatMCTS) brain).getPlayerNumber();
		root = new TreeNode(); //create a new tree by creating a new root
		simulationDepth = depth; //the number of turns for each simulation
		failedSimulations = false; //before running simulations we want to reset this flag
		//no need to start/stop a new server if we are not running simulations
		if(noSimulations > 0)
			initialize();
	    
		expand(root);							//as we are learning one or two actions max, we perform the full expansion of the tree just before executing MCTS 
	    if(!((String) type.getTypeParam(FlatMctsType.SEED_METHOD)).equals("NONE"))
	    	seed(root);							//use JSettlers strategy, the corpus or both to seed 
	    execute(noSimulations);					//run MCTS (Flat case, i.e. no further expansions)
	    
	    if(noSimulations > 0)
	    	tearDown();								
	}
	
	/**
	 * Calls the seeding mechanism on the children of a node.
	 * @param n the parent node
	 */
	private void seed(TreeNode n){
		String actionType = (String) type.getTypeParam(FlatMctsType.GAME_ACTION);
		seeder.seed(n, actionType);
	}
	
    /**
     * Start timer.
     */
    public void startTimer(){
    	timer = new Timer();
    }
    
    /**
     * Prints out the time it took and shows the game tree. (Do not call during robot execution of MCTS)
     */
    public void display(){
    	if(timer != null){
    		System.out.println(timer);
    	}
        TreeView tv = new TreeView(root);
        tv.showTree("Game Tree");
    }
     
	private static class FactoryDescr {
	    int count;
	    String name;
	    SOCRobotFactory factory;	
	    
	    public FactoryDescr(SOCRobotFactory f, String n, int c) {
	        factory = f;
	        name = n;
	        count = c;
	    }
	}

}
