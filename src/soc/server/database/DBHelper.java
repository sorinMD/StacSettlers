package soc.server.database;

import java.sql.SQLException;
import java.util.Properties;
import soc.dialogue.StacTradeMessage;
import soc.game.SOCGame;

import soc.game.SOCPlayer;
import soc.game.SOCResourceSet;
import soc.message.SOCAcceptOffer;
import soc.message.SOCBankTrade;
import soc.message.SOCMakeOffer;
import soc.message.SOCRejectOffer;
import soc.robot.stac.Persuasion;
import soc.util.SOCRobotParameters;

/**
 * Interface for DB helper class.  This will allow us to use either an actual DB or a simple logger, in the 
 *  event we are running simulations and don't care about user authentication, etc.
 * @author kho30
 *
 */
public interface DBHelper {
    
    public int getNumGamesDone();

    public void initialize(String user, String pswd, Properties props) throws SQLException;

    public String getUserPassword(String sUserName) throws SQLException;	    

    public boolean getTradeReminderSeen(String sUserName) throws SQLException;

    public boolean getUserMustDoTrainingGame(String sUserName) throws SQLException;

    public String getUserFromHost(String host) throws SQLException;

    public boolean createAccount(String userName, String host, String password, String email, long time) throws SQLException;

    public boolean recordLogin(String userName, String host, long time) throws SQLException;

    public boolean updateLastlogin(String userName, long time) throws SQLException;

    public boolean updateShowTradeReminder(String userName, boolean flag) throws SQLException;

    public boolean updateUserMustDoTrainingGame(String userName, boolean flag) throws SQLException;

//	    public boolean saveGameScores(String gameName, String player1, String player2, String player3, String player4, short score1, short score2, short score3, short score4, java.util.Date startTime, boolean regularFlag) throws SQLException;
    public boolean saveGameScores(SOCGame ga) throws SQLException;

    public SOCRobotParameters retrieveRobotParams(String robotName) throws SQLException;

    public void cleanup() throws SQLException;	  

    // TODO: Augment this with some additional information (eg the resources currently held).  Note
    //  some of this depends on opponent resource tracking - it would be ideal to know what an offerer
    //  thinks his opponent has, but this would need to be logged by the brain/negotiator
    public void logChatTradeOffer(SOCPlayer p, StacTradeMessage tm, int turn, boolean isInitial);
    public void logChatTradeOffer(SOCPlayer p, StacTradeMessage tm, int turn, boolean isInitial, Persuasion persuasionMove, int roundNo);
    public void logTradeEvent(SOCPlayer p, SOCMakeOffer offer, int turn, boolean isInitial, boolean isForced);
    public void logTradeEvent(SOCPlayer accepter, SOCPlayer offerer, SOCAcceptOffer accept, int turn, boolean isForced);
    public void logTradeEvent(SOCPlayer p, SOCMakeOffer offer, int turn, boolean isInitial, Persuasion persuasionMove, int roundNo);
    public void logTradeEvent(SOCPlayer accepter, SOCPlayer offerer, SOCAcceptOffer accept, int turn, Persuasion persuasionMove);
    public void logTradeEvent(SOCPlayer p, SOCRejectOffer reject, int turn);

    public void logBankTradeEvent(SOCPlayer player, SOCBankTrade mes);

    public void newTurn(String gameName, String playerName);

    // Log the result of a player's build plan prediction
    public void logBPPrediction(SOCPlayer p, boolean nullEquiv, boolean correctType, boolean fullEquality);

    // Log the result of a player's has-resources prediction
    public void logHasResourcesPrediction(SOCPlayer p, boolean beliefCorrect, boolean observedCorrect, boolean subsetCorrect, boolean afterRollOfSeven);

    // Log a player's build plan
    public void logBuildPlan(String player, int bpType);

    // Log that a player has build/bought something
    public void logBuildAction(String player, int piece);

    // Log that the player holding a badge (LR/LA) has changed
    public void logLA_LRPlayerChanged(String player, String badge);

    /** Log that a player has received resources */
    public void logResourcesReceivedByTrading(String player, SOCResourceSet resources);// int clay, int ore, int sheep, int wheat, int wood);

    public int EMBARGO_PROPOSE = 0;
    public int EMBARGO_COMPLY = 1;
    public int EMBARGO_LIFT = 2;

    /** Log a change in an embargo for the specified player */
    public void logEmbargoAction(String player, int action);
    
    public int BLOCK = 0;
    public int BLOCK_COMPLY = 1;
    
    /** Log an action related to blocking trades for the specified player */
    public void logBlockingAction(String player, int action);
}
