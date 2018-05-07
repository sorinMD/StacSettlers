package soc.message;

import java.util.StringTokenizer;
/**
 * A request to change from robot to human player or vice-versa.
 * 
 * @author MD
 *
 */
public class SOCRobotFlag extends SOCMessage
	implements SOCMessageForGame{

    /**
     * Name of game
     */
    private String game;
    
    /**
     * The value to set it to.
     */
    private boolean flag;
    
    /**
     * The player number to set it for. 
     */
    private int playerNumber;
    
    /**
     * Create a GAMECOPY message.
     *
     * @param ga  the name of the game
     */
    public SOCRobotFlag(String ga, boolean f, int pn)
    {
    	
        messageType = ROBOTFLAGCHANGE;
        game = ga;
        flag = f;
        playerNumber = pn;
    }
    
    /**
     * GAMECOPY sep game sep2 playerNumber
     *
     * @return the command String
     */
	public String toCmd() {
		return toCmd(game, flag, playerNumber);
	}
	
    /**
     * GAMECOPY sep game sep2 playerNumber
     *
     * @param ga  the game name
     * @return    the command string
     */
    public static String toCmd(String ga, boolean f, int pn)
    {
        return ROBOTFLAGCHANGE + sep + ga + sep2 + f + sep2 + pn;
    }

    /**
     * Parse the command String into a StartGame message
     *
     * @param s   the String to parse
     * @return    a GameCopy message, or null if the data is garbled
     */
    public static SOCRobotFlag parseDataStr(String s)
    {
    	String ga;
    	boolean f;
    	int pn;
    	
        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            f = Boolean.parseBoolean(st.nextToken());
            pn = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }
    	
        return new SOCRobotFlag(ga,f,pn);
    }
    
	@Override
	public String toString() {
		return "SOCGameCopy:game=" + game + "|flag=" + flag + "|playerNumber=" + playerNumber;
	}

	@Override
	public String getGame() {
		return game;
	}

	public boolean getFlag(){
		return flag;
	}
	public int getPlayerNumber(){
		return playerNumber;
	}
}
