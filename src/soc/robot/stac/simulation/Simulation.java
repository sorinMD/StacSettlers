package soc.robot.stac.simulation;

import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import mcts.listeners.TimedIterationListener;
import representation.FVGeneratorFactory;
import resources.Resources;
import soc.client.SOCPlayerClient.GameOptionServerSet;
import soc.disableDebug.D;
import soc.message.SOCNewGameWithOptionsRequest;
import soc.message.SOCStartGame;
import soc.robot.FactoryDescr;
import soc.robot.SOCDefaultRobotFactory;
import soc.robot.SOCRobotBrain;
import soc.robot.SOCRobotFactory;
import soc.robot.stac.MCTSRobotFactory;
import soc.robot.stac.MCTSRobotType;
import soc.robot.stac.OriginalSSRobotFactory;
import soc.robot.stac.StacRobotBrain;
import soc.robot.stac.StacRobotBrainFlatMCTS;
import soc.robot.stac.StacRobotBrainRandom;
import soc.robot.stac.StacRobotFactory;
import soc.robot.stac.StacRobotType;
import soc.robot.stac.learning.Learner;
import soc.server.SOCServer;
import soc.server.database.LearningLogger;
import soc.server.database.stac.StacDBHelper;
import soc.server.database.stac.StateValueRow;
import soc.server.genericServer.LocalStringConnection;
import soc.server.genericServer.LocalStringServerSocket;
import soc.server.genericServer.Server;
import soc.server.logger.SOCConditionalLogger;
import soc.server.logger.SOCFileLogger;
import soc.server.logger.SOCLogger;
import soc.server.logger.SOCNullLogger;
import soc.util.Timer;

/**
 * Class to run tests to compare different AI agents.
 * @author kho30
 *
 */
public class Simulation {

	/**
	 * flag changed only by StacMCTS class if any simulations fail in order to repeat the game and throw the results away.
	 */
	public static boolean failedMctsSimulation = false;
	//following flags are used for deciding the configuration for the start game command. The default values below are used for a normal start of game.
	/**
	 * set this flag to true so the players will have the same position on the board for each game based on their order in config.txt
	 */
	private boolean dontShufflePlayers = false;
	/**
	 * change this to choose which player position on the board starts the game (0 = blue player, 1 = red, 2 = white and 3 = orange)
	 */
	private int playerToStart = -1;
	/**
	 * set this flag to true in order to play the games from a saved game state. Remember to mention the folder name.
	 */
	private boolean load = false;
	/**
	 * string containing the folder name and path
	 */
	private String folderName = "";
	/**
	 * int value for the max number of turns a game should play. (if 0, the games are played until all victory conditions are satisfied)
	 */
	private int noTurns = 0;
	/**
	 * flag for deciding if the games should use a saved board configuration. NOTE: this configuration should be stored in : “saves/board/soc.game.SOCBoard.dat”
	 */
	private boolean loadBoard = false;
	/**
	 * flag for turning the debug on or off
	 */
	private boolean debug = false;
	/**
	 * flag for turning chatNegotiations on or off
	 */
	private boolean chatNegotiations = false;
	/**
	 * Flag for deciding whether the SOCServer on which the simulations are being run should call the Toulouse parser.
	 */
	private boolean useParser = false;
	/**
	 * Flag for deciding if the hidden information (i.e. resources or unplayed dev cards) is revealed to all players.
	 */
	private boolean fullyObservable = false;
	/**
	 * Flag for deciding if drawing victory point cards is observable or not.
	 */
	private boolean observableVP = false;
	
	//for collection of linear function approximation of the agent's value function
	public static int collectionID = -1;//the table id in the database, also acts as a flag
	public static StacDBHelper dbh; 
	public static Object lock = new Object();
    private SOCServer practiceServer;
    private LearningLogger resultsLogger = new LearningLogger();
    private SOCFileLogger fileLogger = new SOCFileLogger(SOCServer.LOG_DIR);
    private SOCLogger gameLogger = new SOCNullLogger();
    private int numberOfGames = 10000;
    private String testName;
    private int learnGames;
    private List<FactoryDescr> factories;
    private List<String> fullConfig;
    private boolean isRoundRobin = false;
    private boolean isVarParam = false;
    private double vpInitial = 0;
    private double vpStep = 0;
    private int vpNumSteps = 0;
    private String varParam = null;
    private boolean isValidAgentConfiguration;

    public Simulation() {
        SOCServer.GAME_NAME_MAX_LENGTH = 100;
        //when a robot is replacing a human player this field blocks the creation of more than 5 games
        SOCServer.CLIENT_MAX_CREATE_GAMES = -1;
    }

	public static void main(String[] args) throws Exception {
	    FVGeneratorFactory.initialise();
		String configName = args.length > 0 ? args[0] : null;
        Simulation simulation = new Simulation();
        System.out.println("configName="+configName);

        BufferedReader config;
        if(configName != null)
        	config = new BufferedReader(new FileReader(new File(configName)));
        else{
        	URL url = Resources.class.getResource(Resources.configName);
        	InputStream is = url.openStream();
        	config = new BufferedReader(new InputStreamReader(is));
        }
        simulation.loadConfig(config);
        config.close();
        simulation.start();
    }
	
	/**
	 * Parses the configuration and runs simulations accordingly.
	 * @param configLines the lines already read from the config file in order
	 * @throws Exception
	 */
	public static void parseConfigAndRunSimulations(List<String> configLines) throws Exception {
		Simulation simulation = new Simulation();
		simulation.parseConfig(configLines);
		if (simulation.isValidAgentConfiguration)
			simulation.start();
	}

	/**
	 * Reads the file and parses the configuration.
	 * @param config the reader that reads the input stream with the configuration
	 * @throws Exception
	 */
    public void loadConfig(BufferedReader config) throws Exception {
    	List<String> fullConfig = new ArrayList<String>();
        String nextLine = config.readLine();
        while (nextLine != null) {
            fullConfig.add(nextLine);
            nextLine = config.readLine();
            System.out.println("nextLine="+nextLine);
        }
        parseConfig(fullConfig);
    }

    /**
	 * Parses the configuration.
	 * @param configLines the lines already read from the config file in order
     */
    public void parseConfig(List<String> configLines) {
        fullConfig = configLines;
        Iterator<String> configIt = fullConfig.iterator();
        parseSuiteConfig(configIt);
        readAgentConfiguration(configIt);
        //Decide if we want debug on or off; Default is off
        if(debug)
        	D.ebug_enable();
        else
        	D.ebug_disable();
        practiceServer = new SOCServer(SOCServer.PRACTICE_STRINGPORT, 30, resultsLogger, null, null, gameLogger, useParser);
    }

    /**
     * Decides if it is a normal, round robin or with variable parameter simulation and runs it.
     * @throws Exception
     */
    public void start() throws Exception {
        if (isValidAgentConfiguration)
            startSimulations();
    }

    /**
     * Parses the strings from the iterator until it encounters the separator 
     * @param configIt the iterator with the lines of configuration.
     */
    private void parseSuiteConfig(Iterator<String> configIt) {
        String nextLine = configIt.next();
        while (!nextLine.startsWith("~")) {
            if (nextLine.startsWith("Games")) {
                String p[] = nextLine.split("=");
                numberOfGames = Integer.parseInt(p[1]);
            }
            else if (nextLine.startsWith("Log")) {
                String p[] = nextLine.split("=");
                boolean log = Boolean.parseBoolean(p[1]);
                if (!log) {
                    // Is it an explicit false?
                    if (!"false".equalsIgnoreCase(p[1])) {
                        gameLogger = new SOCConditionalLogger(SOCServer.LOG_DIR, p[1]);
                    }
                }
                else {
                    gameLogger = fileLogger;
                }
            }
            else if (nextLine.startsWith("ChatNeg")) {
                String p[] = nextLine.split("=");
                boolean chatNegotiation = Boolean.parseBoolean(p[1]);
                if (chatNegotiation) {
                	chatNegotiations = chatNegotiation;
                    StacRobotBrain.setChatNegotiation(chatNegotiation);
                }
            }
            else if (nextLine.startsWith("NoShuffle")) {
                String p[] = nextLine.split("=");
                boolean c = Boolean.parseBoolean(p[1]);
                if (c) {
                    dontShufflePlayers = c;
                }
            }
            else if (nextLine.startsWith("PlayerToStart")) {
                String p[] = nextLine.split("=");
                int c = Integer.parseInt(p[1]);
                if (c >= -1 && c < 4) {
                    playerToStart = c;
                }
            }
            else if (nextLine.startsWith("NoTurns")) {
                String p[] = nextLine.split("=");
                int c = Integer.parseInt(p[1]);
                if (c >= 0) {
                    noTurns = c;
                }
            }
            else if (nextLine.startsWith("Load")) {
                String p[] = nextLine.split("=");
                boolean c = Boolean.parseBoolean(p[1]);
                if (c) {
                    load = c;
                }
            }
            else if (nextLine.startsWith("BoardLoad")) {
                String p[] = nextLine.split("=");
                boolean c = Boolean.parseBoolean(p[1]);
                if (c) {
                    loadBoard = c;
                }
            }
            else if (nextLine.startsWith("FolderName")) {
                String p[] = nextLine.split("=");
                if (!p[1].isEmpty() || !p[1].equals("")) {
                    folderName = p[1];
                }
            }
            else if (nextLine.startsWith("Debug")) {
                String p[] = nextLine.split("=");
                boolean c = Boolean.parseBoolean(p[1]);
                if (c) {
                    debug = c;
                }
            }
            else if (nextLine.startsWith("UseParser")) {
                String p[] = nextLine.split("=");
                boolean c = Boolean.parseBoolean(p[1]);
                if (c) {
                    useParser = c;
                }
            }
            else if (nextLine.startsWith("ForceEndTurns")) {
                String p[] = nextLine.split("=");
                boolean c = Boolean.parseBoolean(p[1]);
                if (!c) {
                	SOCServer.FORCE_END_TURNS = c;
                }
            }
            else if (nextLine.startsWith("FullyObservable")) {
                String p[] = nextLine.split("=");
                boolean c = Boolean.parseBoolean(p[1]);
                if (c) {
                    fullyObservable = c;
                }
            }
            else if (nextLine.startsWith("ObservableVP")) {
                String p[] = nextLine.split("=");
                boolean c = Boolean.parseBoolean(p[1]);
                if (c) {
                    observableVP = c;
                }
            }
            else if (nextLine.startsWith("CollectValueFunctionApproxWithID")) {
            	dbh = new StacDBHelper();
        		dbh.initialize();
        		dbh.connect();
                String p[] = nextLine.split("=");
                int c = Integer.parseInt(p[1]);
                collectionID = c;
                if(dbh.isConnected() && !dbh.tableExists(StacDBHelper.VALUETABLE + collectionID)){
                	dbh.createValueTable(collectionID);
                	 SOCServer.COLLECT_VALUE_FUNCTION_APPROX = true;//server should send msgs only if everything is ok
                }else{
                	System.out.println("table exists or no connection; if former, closing connection to avoid overwrites");//debugging
                	dbh.disconnect();
                }
            }
            else if (nextLine.startsWith("CollectValueFunctionApprox")) {
                String p[] = nextLine.split("=");
                boolean c = Boolean.parseBoolean(p[1]);
                if (c) {
                   SOCServer.COLLECT_VALUE_FUNCTION_APPROX = c;
                }
            }
            else if (nextLine.startsWith("CollectFullGameplay")) {
                String p[] = nextLine.split("=");
                boolean c = Boolean.parseBoolean(p[1]);
                if (c) {
                   SOCServer.COLLECT_FULL_GAMEPLAY = c;
                }
            }
            nextLine = configIt.next();
        }
    }

    /**
     * Reads the agent configuration.
     * @param configIt the iterator containing the lines of the agent configuration.
     */
    private void readAgentConfiguration(Iterator<String> configIt) {
        String nextLine = configIt.hasNext() ? configIt.next() : null;
        if (nextLine == null) {
            isValidAgentConfiguration = false;
        } else {
            testName = nextLine;

            factories = new ArrayList<FactoryDescr>();
            List<String> controlParams = new ArrayList<String>();

            isValidAgentConfiguration = true;

            nextLine = configIt.hasNext() ? configIt.next() : null;
            while (nextLine != null && !nextLine.startsWith("~")) {
                String p[] = nextLine.split("=");
                if (p[0].equals("Trades")) {
                    SOCRobotBrain.setTradesEnabled(Boolean.parseBoolean(p[1]));
                } else if (p[0].equals("RoundRobin")) {
                    isRoundRobin = true;
                } else if (p[0].equals("Learn")) {
                    String[] learnParams = p[1].split(",");
                    resultsLogger.setPerspective(learnParams[0]);
                    learnGames = Integer.parseInt(learnParams[1]);
                    resultsLogger.setFrequency(Integer.parseInt(learnParams[2]));
                    resultsLogger.setTestGames(Integer.parseInt(learnParams[3]));
                } else if (p[0].equals("VarParam")) {
                    isVarParam = true;
                    p = p[1].split("\\|");
                    varParam = p[0];
                    vpInitial = Double.parseDouble(p[1]);
                    vpStep = Double.parseDouble(p[2]);
                    vpNumSteps = Integer.parseInt(p[3]);
                } else if (p[0].equals("Control")) {
                    p = p[1].split("\\|");
                    for (String pp : p) {
                        controlParams.add(pp);
                    }
                    // TODO: Add support of control flags with variable parameters
                } else if (p[0].equals("Agent")) {
                    p = p[1].split(",");
                    int numAgents = Integer.parseInt(p[0]);
                    String agentName = p[1];

                    SOCRobotFactory factory = null;
                    if (p[2].contains("flatMCTS")) {
                        factory = new StacRobotBrainFlatMCTS.StacRobotFlatMCTSFactory(new StacRobotType());
                    } else if(p[2].contains("originalSS")){
                        factory = new OriginalSSRobotFactory(true, new StacRobotType());              	
                    } else if(p[2].contains("mcts")){
                        factory = new MCTSRobotFactory(true, new MCTSRobotType());              	
                    } else if (p[2].contains("stac")) {
                        factory = new StacRobotFactory(true, new StacRobotType());
                    } else if (p[2].contains("jsettlers")) {
                        factory = new SOCDefaultRobotFactory();
                    } else if (p[2].contains("random")) {
                        factory = new StacRobotBrainRandom.SOCRobotRandomFactory(true, true, -1, new StacRobotType());
                    } else {
                        System.err.println("Invalid robot factory: " + p[2]);
                        isValidAgentConfiguration = false;
                    }

                    // Can have agents without types now - make sure type is supplied
                    if (factory != null) {
                        if (p.length > 3) {
                            String typeFlags[] = p[3].split("\\|");
                            for (String t : typeFlags) {
                                try {
                                    if (t.contains(":")) {
                                        String tp[] = t.split(":");
                                        factory.setTypeFlag(tp[0], tp[1]);
                                    } else {
                                        factory.setTypeFlag(t);
                                    }
                                } catch (Exception ex) {
                                    System.err.println("Invalid flag: " + t);
                                    isValidAgentConfiguration = false;
                                }

                            }
                        }
                        for (String t : controlParams) {
                            if (t.contains(":")) {
                                String tp[] = t.split(":");
                                factory.setTypeFlag(tp[0], tp[1]);
                            }
                            else {
                                factory.setTypeFlag(t);
                            }
                        }


                    }
                    
                    factories.add(new FactoryDescr(factory, agentName, numAgents));
                }
                nextLine = configIt.hasNext() ? configIt.next() : null;
            }
        }
    }

    // Read configurations from reader and start a simulation
    private void startSimulations() throws Exception {

        if (isValidAgentConfiguration) {
        
            if (isRoundRobin && isVarParam) {
                System.err.println("Round robin and Var Param are mutually exclusive");
            }        
            else if (isRoundRobin) {
                runRoundRobin();
            }
            else if (isVarParam) {
                runVarParam();
            }
            else {
                runSimulation();
            }
        }
    }

     /**
     * Runs simulations with agents that have variable parameters.
     * @throws Exception
     */
    private void runVarParam() throws Exception {
        
        //get the name of the robot factory that creates the robots with the varibale parameter
        //and store it for future use
        String varFactoryBaseName = "UnnamedRobot";
        for (FactoryDescr f : factories) {
            if (f.factory.isType(varParam)) {
                varFactoryBaseName = f.name;
            }
        }
        
        for (int i=0; i<vpNumSteps; i++) {
            double v = vpInitial + (vpStep * i);
            DecimalFormat twoDec = new DecimalFormat("0.000");
            String vStr = twoDec.format(v);
            // trim trailing .0 in case this is an integer
            if (vStr.endsWith(".000")) {
                vStr = vStr.substring(0, vStr.length()-4);
            }
            
            for (FactoryDescr f : factories) {
                if (f.factory.isType(varParam)) {
                    f.factory.setTypeFlag(varParam, vStr);
                    f.name = varFactoryBaseName + "_" + varParam + "_" + vStr;
                }
            }
            
            runSimulation();
        }
    }
	/**
	 * Runs round robin of 4 modified agents, one simulation with all the modified agents, one for each modified agent against 3 original
	 * and one for each modified agent where 1 original plays against 3 modified of the same type.
	 * @throws Exception
	 */
    private void runRoundRobin() throws Exception {
        // Assumption: first agent listed is the control agent, there are four experimental agents
        // Factory list for each iteration
        List<FactoryDescr> allFactories = new ArrayList<>(factories);

        // First run: all experimental agents
        for (int i=1; i<5; i++) {
            factories.add(allFactories.get(i));
        }

        runSimulation();

        // Second set of runs: 3 orig vs each experimental
        for (int i=1; i<5; i++) {
            factories.clear();
            FactoryDescr f = allFactories.get(0).copy();
            f.count = 3;
            factories.add(f);
            f = allFactories.get(i).copy();
            f.count = 1;
            factories.add(f);
            runSimulation();
        }

        // Third set of runs: each combo of 3 experimental vs original
        for (int i=1; i<5; i++) {
            factories.clear();
            for (int j=0; j<5; j++) {
                // exclude the ith agent
                if (i!=j) {
                    FactoryDescr f = allFactories.get(j).copy();
                    f.count = 1;
                    factories.add(f);
                }
            }            
            runSimulation();
        }
    }
    
    /**
     * Runs the simulation.
     * @throws Exception
     */
    private void runSimulation() throws Exception {
        Timer t = new Timer();
    	// TODO: startRun should probably just reset games done itself
        // Have options to do a separate learning initial run
        int[] runNumGames;
        String[] runName;
        if (learnGames > 0) {
            runNumGames = new int[] {learnGames, numberOfGames};
            runName = new String[] {testName + "_Learn", testName};
            Learner.setLearning(true);
        }
        else {
            runNumGames = new int[] {numberOfGames};
            runName = new String[] {testName};	        
        }

        for (int j=0; j<runName.length; j++) {
            if (j>0) {
                // If we are beginning the second non-learning  phase fo a two-step test, tell the learner to stop
                resultsLogger.setLearning(false);
                Learner.setLearning(false);
            }
            else if (runName.length > 1) {
                resultsLogger.setLearning(true);
            }
        resultsLogger.resetGamesDone();
        resultsLogger.startRun(runName[j], fullConfig);

        if(SOCServer.COLLECT_FULL_GAMEPLAY){
        	practiceServer.dbh.initialize();
        	practiceServer.dbh.connect();
        }
        practiceServer.setPriority(5);  
        practiceServer.start();
        GameOptionServerSet gOpts =  new GameOptionServerSet();  

        for (FactoryDescr f : factories) {
            practiceServer.setupLocalRobots(f.factory, f.name, f.count);
        }

        // turn off the pause in the robotbrain
        SOCRobotBrain.setDelayTime(0);
        // turn off trades to facilitate faster simulation
        //SOCRobotBrain.disableTrades();               

            // Create a connection to communicate with the server
        LocalStringConnection prCli = LocalStringServerSocket.connectTo(SOCServer.PRACTICE_STRINGPORT);

        int i=0;
        // Try to run 10k games.
        while (i<runNumGames[j] + 1) {        	 
             // Hack: Look up how many games have been completed in the "database" to see if our current game is finished.
             // TODO: Add logic to track game creation time and kill any games that take longer than a reasonable allowance.  Errors
             //   may lead to a game never finishing.
             //  See robot assert failed... note
             if (resultsLogger.getTotalNumGames()==i) {
                 if (i>0) {
                     // destroy the last game - it's finished
                     practiceServer.destroyGame(runName[j] + "_" + (i-1), false);        			 

                     // Clean out contents of prCli
                     while (prCli.isInputAvailable()) {
                         prCli.readNext();
                     }
                 }
                 if(failedMctsSimulation)
                    runNumGames[j]++; //add 1 to the number of games to run; it will keep the logs, but won't gather stats or write results


                 // Create a new game with robots only and start it
                 if (i<runNumGames[j]) {
                     failedMctsSimulation = false; //reset the flag; it is set to true only in StacMCTS if simulations fail
                     String gameName = runName[j] + "_" + i;
                     prCli.put(SOCNewGameWithOptionsRequest.toCmd("simulation-master", "", "localhost", gameName, gOpts.optionSet));
                     prCli.put(SOCStartGame.toCmd(gameName, dontShufflePlayers, load, folderName, noTurns, playerToStart, loadBoard, chatNegotiations, fullyObservable, observableVP));
                 }
                 i++;
             }
             //System.out.println("i="+i + " out of "+(runNumGames[j] + 1));

             // Wait a little bit before we see if the game is finished
             Thread.sleep(100);        
        }
        System.out.println("done!");

        //destroy the last game
        practiceServer.destroyGame(testName + "_" + (i), false);        			 
        
        //clean out contents of prCli
        while (prCli.isInputAvailable()) {
            prCli.readNext();
        }        			 

        //finishing up
        System.out.println("Finished " + testName);
//        try {//depending on the machine, this might cause the process to hang, so commenting out for now.
//        	Toolkit.getDefaultToolkit().beep();
//		} catch (Exception e) {}

        //stop the server if no more games are active
        if (!practiceServer.getGameNames().hasMoreElements()) {
            System.out.println("Stopping the server.");
            practiceServer.stopServer();
        } else {
            System.out.println("Still games active on the server; keeping it running.");
            System.out.println("Active games: " );
            Enumeration gameNames = practiceServer.getGameNames();
            while (gameNames.hasMoreElements()) {
                String gm = (String) gameNames.nextElement();
                System.out.println(gm);
            }
        }
        
        if(dbh!=null && dbh.isConnected())
        	dbh.disconnect();
        
        resultsLogger.endRun();
        System.out.println("Simulations took: " + t.toString());
        
        //simple code for doing the timing of the MCTS agent;
        ArrayList<Long> times = TimedIterationListener.times;
        if(times.size() > 0) {
	        long min = Long.MAX_VALUE;
	        long max = Long.MIN_VALUE;
	        Collections.sort(times);
	        long sum = 0;
	        for(Long l : times) {
	        	sum +=l;
	        	if(l < min)
	        		min = l;
	        	if(l > max)
	        		max = l;
	        }
	        double mean = (double)sum/(double)times.size();
	        double median = 0.0;
			if(times.size() > 0) {
			if(times.size() % 2 != 0)
				median = times.get(times.size()/2);
			else
				median = ((double)times.get(times.size()/2) + (double)times.get(times.size()/2 -1))/2;
			}
	        System.out.println("Min time: " + min);
	        System.out.println("Max time: " + max);
	        System.out.println("Mean time: " + (long)mean);
	        System.out.println("Median time:" + (long)median);
        }
        
//        Server.displayTrackedRunningThreads();
        Server.killAllThreads();        
        // pause for 5 seconds to allow threads to clean themselves up
//        Thread.sleep(5000);
        }
    }
    
    /**
     * Writes the Value function to the database
     * @param vector the vector representing the state
     * @param value the value of the state
     */
    public static void writeToDb(int[] vector, double value){
    	//avoid writing to the db at the same time and also incrementing the id counter
    	synchronized (lock) {
    		StateValueRow svr = new StateValueRow(vector, value);
    		dbh.insertStateValue(collectionID, svr);
    	}
    }

    public SOCServer getPracticeServer() {
        return practiceServer;
    }
}
