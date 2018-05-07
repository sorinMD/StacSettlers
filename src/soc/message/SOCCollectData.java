package soc.message;

import java.util.StringTokenizer;
/**
 * This message is a request to collect samples of the states and their value following a heuristics agent 
 * 
 * @author MD
 *
 */
public class SOCCollectData extends SOCMessage
	implements SOCMessageForGame{

    /**
     * Name of game
     */
    private String game;
    
    /**
     * Player's number who should be either collecting or ; 
     */
    private int playerNumber;
    
    /**
     * Create a COLLECTDATA message.
     *
     * @param ga  the name of the game
     * @param pn player number
     */
    public SOCCollectData(String ga, int pn)
    {
    	playerNumber = pn;
        messageType = COLLECTDATA;
        game = ga;

    }
    
    /**
     * COLLECTDATA sep game sep2 playerNumber
     *
     * @return the command String
     */
	public String toCmd() {
		return toCmd(game, playerNumber);
	}
	
    /**
     * COLLECTDATA sep game sep2 playerNumber
     *
     * @param ga  the game name
     * @param pn  player number
     * @return    the command string
     */
    public static String toCmd(String ga,int pn)
    {

        return COLLECTDATA + sep + ga + sep2 + pn;
    }

    /**
     * Parse the command String into a COLLECTORTESTDATA message
     *
     * @param s   the String to parse
     * @return    a CollectData message, or null if the data is garbled
     */
    public static SOCCollectData parseDataStr(String s)
    {
    	String ga;
    	int pn;
    	
        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }
    	
        return new SOCCollectData(ga,pn);
    }
    
	@Override
	public String toString() {
		return "SOCCollectData:game=" + game + "|playerNumber=" + playerNumber;
	}

	@Override
	public String getGame() {
		return game;
	}

	public int getPlayerNumber(){
		return playerNumber;
	}
}
