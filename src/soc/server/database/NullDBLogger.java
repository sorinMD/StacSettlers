package soc.server.database;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import soc.game.SOCGame;
import soc.game.SOCResourceConstants;

/**
 * Logger that keeps track only of the VPs and does not write anything in the results directory.
 * Needed for speeding up the simulations done in MCTS.
 * @author MD
 *
 */
public class NullDBLogger extends DBLogger{
	
	private String gameName;
	
	public NullDBLogger() {}
	 
	public void startRun(String gameName) {
		//do not initialise anything just keep the game name
		this.gameName = gameName;
	}
	
	/**
	 * keep track of the stats but do not print anything out
	 */
    @Override
    public boolean saveGameScores(SOCGame ga) throws SQLException {
        
        String player1 = ga.getPlayer(0).getName();
        String player2 = ga.getPlayer(1).getName();
        String player3 = ga.getPlayer(2).getName();
        String player4 = ga.getPlayer(3).getName();
        short score1 = (short) ga.getPlayer(0).getTotalVP();
        short score2 = (short) ga.getPlayer(1).getTotalVP();
        short score3 = (short) ga.getPlayer(2).getTotalVP();
        short score4 = (short) ga.getPlayer(3).getTotalVP();

          // Make sure all players have had stats objects created - prior to the first call of this method, stats will only have been generated if they have participated in a trade
        getStats(player1);
        getStats(player2);
        getStats(player3);
        getStats(player4);
        
        updateStats(player1, score1);
        updateStats(player2, score2);
        updateStats(player3, score3);
        updateStats(player4, score4);

        numGamesDone++;
    	return true; //always true as no exception could be thrown here
    }
    
    public void resetGamesDone(){
    	numGamesDone=0;
    }
    
    /**
     * do nothing just override the parent method
     */
    public void endRun(){
    	
    }
    
    /**
     * only needed for simulations
     * @param player
     */
	public void clearVictoryPoints(String player){
		stats.get(player).resetVP();
	}
    
}
