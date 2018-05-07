package soc.message;

import java.util.StringTokenizer;
import java.util.Vector;
/**
 * This message represents a request for loading a saved game from the information cloned and stored inside saves or load folder
 * (depending if its a robot(saves) or user(load) request. In this way, a user could load any game it is interested in, while a robot
 * will not need to move files around, thus less processing time).
 * 
 * @author MD
 *
 */
public class SOCLoadGame extends SOCMessage
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
     * Create a LOADGAME message.
     *
     * @param ga  the name of the game
     */
    public SOCLoadGame(String ga, String fn)
    {
        messageType = LOADGAME;
        game = ga;
    	String name = fn;
    	if(!(name.contains("@"))){
    		name = "@" + fn;
    	}
        folderName = name;
    }
    
    /**
     * LOADGAME sep game sep2 isRobot
     *
     * @return the command String
     */
	public String toCmd() {
		return toCmd(game, folderName);
	}
	
    /**
     * LOADGAME sep game
     *
     * @param ga  the game name
     * @return    the command string
     */
    public static String toCmd(String ga, String fn)
    {
    	String name = fn;
    	if(!(name.contains("@"))){
    		name = "@" + fn;
    	}
        return LOADGAME + sep + ga + sep2 + name;
    }

    /**
     * Parse the command String into a StartGame message
     *
     * @param s   the String to parse
     * @return    a StartGame message, or null of the data is garbled
     */
    public static SOCLoadGame parseDataStr(String s)
    {
    	String ga;
    	String fn;
        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            fn = st.nextToken();
        }
        catch (Exception e)
        {
            return null;
        }
       return new SOCLoadGame(ga,fn);
    }
    
	@Override
	public String toString() {
		return "SOCLoadGame:game=" + game + "|folderName=" + folderName;
	}

	@Override
	public String getGame() {
		return game;
	}
	
	public String getFolder(){
		String name = folderName.replace("@", "");
		return name;
	}

}
