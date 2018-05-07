package soc.robot.stac;

import java.awt.Toolkit;
import java.util.Enumeration;
import soc.client.SOCPlayerClient.GameOptionServerSet;
import soc.disableDebug.D;
import soc.message.SOCNewGameWithOptionsRequest;
import soc.message.SOCStartGame;
import soc.robot.SOCDefaultRobotFactory;
import soc.robot.SOCRobotBrain;
import soc.server.SOCServer;
import soc.server.database.DBHelper;
import soc.server.database.DBLogger;
import soc.server.genericServer.LocalStringConnection;
import soc.server.genericServer.LocalStringServerSocket;
import soc.server.logger.SOCConditionalLogger;
import soc.server.logger.SOCFileLogger;
import soc.server.logger.SOCLogger;
import soc.server.logger.SOCNullLogger;

/**
 * Class to run tests to compare different AI agents.
 * @author kho30
 *
 */
public class RobotTest {

	public RobotTest() {}

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
            // Create the server (copied from practice server creation)
            DBHelper db = new DBLogger();
            D.ebug_disable();
		 
            SOCLogger fileLogger = new SOCFileLogger(SOCServer.LOG_DIR);
            SOCLogger nullLogger = new SOCNullLogger();
            SOCLogger condLogger = new SOCConditionalLogger(SOCServer.LOG_DIR, "You didn't buy");
            SOCServer practiceServer = new SOCServer(SOCServer.PRACTICE_STRINGPORT, 30, db, null, null, condLogger, false);
            practiceServer.setPriority(5);  
            practiceServer.start();
            GameOptionServerSet gOpts =  new GameOptionServerSet();   

            // Create 3 "fast" default AI agents.  This seems to be a more promising approach than "smart" for now.  
            //practiceServer.setupLocalRobots(new SOCDefaultRobotFactory(), "droid ", 2);
            // create a single STAC robot
//            practiceServer.setupLocalRobots(new StacRobotFactory(true, StacRobotType.ORIGINAL_ROBOT), "original", 3);
//            practiceServer.setupLocalRobots(new StacRobotFactory(true, (StacRobotType.ORIGINAL_ROBOT | StacRobotType.FAVOUR_CITIES)), "original_favouring-cities", 1);
//            practiceServer.setupLocalRobots(new StacRobotFactory(true, (StacRobotType.ORIGINAL_ROBOT | StacRobotType.ALWAYS_ASSUMING_IS_SELLING)), "original_always-assuming-is-selling", 1);
//            practiceServer.setupLocalRobots(new StacRobotFactory(true, StacRobotType.PARTIALISING_COMPLETE_OFFERS_50_PERCENT), "partial-50", 2);
//            practiceServer.setupLocalRobots(new StacRobotFactory(true, StacRobotType.PARTIALISING_COMPLETE_OFFERS_100_PERCENT), "partial-100", 1);
//            practiceServer.setupLocalRobots(new StacRobotFactory(true, (StacRobotType.PARTIALISING_COMPLETE_OFFERS_50_PERCENT | StacRobotType.WEAK_MEMORY)), "partial-50_weak-memory", 1);
//            practiceServer.setupLocalRobots(new StacRobotFactory(true, (StacRobotType.PARTIALISING_COMPLETE_OFFERS_100_PERCENT | StacRobotType.WEAK_MEMORY)), "partial-100_weak-memory", 1);
//            practiceServer.setupLocalRobots(new StacRobotFactory(true, (StacRobotType.PARTIALISING_COMPLETE_OFFERS_50_PERCENT | StacRobotType.NO_MEMORY_OF_TRADES)), "partial-50_no-memory-of-trades", 1);
//            practiceServer.setupLocalRobots(new StacRobotFactory(true, (StacRobotType.PARTIALISING_COMPLETE_OFFERS_100_PERCENT | StacRobotType.NO_MEMORY_OF_TRADES)), "partial-100_no-memory-of-trades", 1);
//            practiceServer.setupLocalRobots(new StacRobotFactory(true, (StacRobotType.PARTIALISING_COMPLETE_OFFERS_50_PERCENT | StacRobotType.NO_MEMORY_OF_IS_SELLING)), "partial-50_no-memory-of-is-selling", 1);
//            practiceServer.setupLocalRobots(new StacRobotFactory(true, (StacRobotType.PARTIALISING_COMPLETE_OFFERS_100_PERCENT | StacRobotType.NO_MEMORY_OF_IS_SELLING)), "partial-100_no-memory-of-is-selling", 1);
//            practiceServer.setupLocalRobots(new StacRobotFactory(true, StacRobotType.SIMPLY_ASK), "simply-ask", 2);

         
            // turn off the pause in the robotbrain
            SOCRobotBrain.setDelayTime(0);
            // turn off trades to facilitate faster simulation
            //SOCRobotBrain.disableTrades();               

            // Create a connection to communicate with the server
            LocalStringConnection prCli = LocalStringServerSocket.connectTo(SOCServer.PRACTICE_STRINGPORT);

            int i=0;
            // Try to run 10k games.
            while (i<10000) {        	 
        	 // Hack: Look up how many games have been completed in the "database" to see if our current game is finished.
        	 // TODO: Add logic to track game creation time and kill any games that take longer than a reasonable allowance.  Errors
        	 //   may lead to a game never finishing.
        	 //  See robot assert failed... note
        	 if (db.getNumGamesDone()==i) {
                     if (i>0) {
                         // destroy the last game - it's finished
                         practiceServer.destroyGame("Practice " + (i-1), false);        			 

                         // Clean out contents of prCli
                         while (prCli.isInputAvailable()) {
                             prCli.readNext();
                         }        			 
                     }

                     // Create a new game with robots only and start it
                     String gameName = "Practice " + i;
                     prCli.put(SOCNewGameWithOptionsRequest.toCmd("K", "", "localhost", gameName, gOpts.optionSet));
                     prCli.put(SOCStartGame.toCmd(gameName,false,false,"",0,-1, false, false, false, false)); //normal start of game
                     i++;
        	 }
        	
        	 // Wait a little bit before we see if the game is finished
	         Thread.sleep(100);        
            }

            //destroy the last game
            practiceServer.destroyGame("Practice 9999", false);        			 

            //clean out contents of prCli
            while (prCli.isInputAvailable()) {
                prCli.readNext();
            }        			 

            //finishing up
            System.out.println("Finished all 10,000 simulations.");
            Toolkit.getDefaultToolkit().beep();
            
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
        }

}
