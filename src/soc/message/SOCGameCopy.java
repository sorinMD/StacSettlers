package soc.message;

import java.util.StringTokenizer;
/**
 * This message is a request to clone all required information(e.g. SOCGame, SOCPlayer, StacRobotDeclarativeMemory etc.) 
 * for restarting the game from the current state. This has to be received by all the clients + server before loading is possible.
 * 
 * @author MD
 *
 */
public class SOCGameCopy extends SOCMessage
	implements SOCMessageForGame{

    /**
     * Name of game
     */
    private String game;
    
    /**
     * Name of folder
     */
    private String folderName;
    
    /**
     * Player's number who initiated the request, to avoid duplicate saves; 
     */
    private int playerNumber;
    
    /**
     * Create a GAMECOPY message.
     *
     * @param ga  the name of the game
     */
    public SOCGameCopy(String ga, String fn, int pn)
    {
    	playerNumber = pn;
        messageType = GAMECOPY;
        game = ga;
        String name = fn;
        if(!(name.contains("@"))){ 
    		name = "@" + fn;
    	}
        folderName = name;
    }
    
    /**
     * GAMECOPY sep game sep2 playerNumber
     *
     * @return the command String
     */
	public String toCmd() {
		return toCmd(game, folderName, playerNumber);
	}
	
    /**
     * GAMECOPY sep game sep2 playerNumber
     *
     * @param ga  the game name
     * @return    the command string
     */
    public static String toCmd(String ga, String fn, int pn)
    {
    	String name = fn;
    	if(!(name.contains("@"))){
    		name = "@" + fn;
    	}
        return GAMECOPY + sep + ga + sep2 + name + sep2 + pn;
    }

    /**
     * Parse the command String into a StartGame message
     *
     * @param s   the String to parse
     * @return    a GameCopy message, or null if the data is garbled
     */
    public static SOCGameCopy parseDataStr(String s)
    {
    	String ga;
    	String fn;
    	int pn;
    	
        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            fn = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }
    	
        return new SOCGameCopy(ga,fn,pn);
    }
    
	@Override
	public String toString() {
		return "SOCGameCopy:game=" + game + "|folderName=" + folderName + "|playerNumber=" + playerNumber;
	}

	@Override
	public String getGame() {
		return game;
	}

	public String getFolder(){
		String name = folderName.replace("@", "");
		return name;
	}
	public int getPlayerNumber(){
		return playerNumber;
	}
}
