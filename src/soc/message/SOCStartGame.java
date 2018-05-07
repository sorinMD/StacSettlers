/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2010 Jeremy D Monin <jeremy@nand.net>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.message;

import java.util.StringTokenizer;

import javax.naming.NoInitialContextException;


/**
 * This message means that a player wants to start the game
 *
 * @author Robert S. Thomas
 */
public class SOCStartGame extends SOCMessage
    implements SOCMessageForGame
{
    /**
     * Name of game
     */
    private String game;

    /**
     * flag for stopping the order of the robots being randomized.
     */
    private boolean noShuffle;

    /**
     * flag for loading a game.
     */
    private boolean load;
    
    /**
     * name of the folder containing the files with the saved game data.
     */
    private String folderName;
    
    /**
     * How many turns to run the simulations for.
     */
    private int noTurns;
    
    /**
     * player number to start the game or -1 if randomize
     */
    private int playerToStart;
    
    /**
     * a flag telling us if we want to start a new game on a saved board layout which should be found in saves/board/soc.game.SOCBoard.dat
     */
    private boolean loadBoard;
    
    /**
     * a flag for telling the server if the trading in this game will be done via the chat or the old trade interface
     */
    private boolean chatNegotiations;
    
    /**
     * a flag for telling the server if the game is fully observable
     */
    private boolean fullyObservable;
    
    /**
     * a flag for telling the server if drawing VP cards is an observable action.
     */
    private boolean observableVP;
    
    /**
     * Create a StartGame message.
     *
     * @param ga the name of the game
     * @param ns flag for deciding whether to shuffle the robot's position or not
     * @param l flag for deciding whether to load or start a new game
     * @param fn pathname to the folder containing the save files e.g. "saves/robot"
     * @param t the number of turns this game is allowed to run for.
     * @param pts the player number(or board position) of the player to start the game
     * @param lb flag for deciding whether to create a new board or load a saved configuration
     * @param fo flag for deciding if the game is fully observable or has hidden information
     * @param ov flag for deciding if drawing vp cards is an observable action
     */
    public SOCStartGame(String ga, boolean ns, boolean l, String fn, int t, int pts, boolean lb, boolean cn, boolean fo, boolean ov)
    {
        messageType = STARTGAME;
        game = ga;
        noShuffle = ns;
        load = l;
        String name = fn;
        if(!(name.contains("@"))){ 
    		name = "@" + fn;
    	}
        folderName = name; //add a character to avoid nullpointer
        noTurns = t;
        playerToStart = pts;
        loadBoard = lb;
        chatNegotiations = cn;
        fullyObservable = fo;
        observableVP = ov;
    }

    /**
     * @return observable vp flag
     */
    public boolean getObservableVPFlag(){
    	return observableVP;
    }
    
    /**
     * @return fully observable flag
     */
    public boolean getFullyObservableFlag(){
    	return fullyObservable;
    }
    
    /**
     * @return how are trades performed
     */
    public boolean getChatNegotiationsFlag(){
    	return chatNegotiations;
    }
    
    /**
     * @return the player number of the player to start the game or -1 if random
     */
    public int getStartingPlayer(){
    	return playerToStart;
    }
    
    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the load flag
     */
    public boolean getLoadFlag()
    {
        return load;
    }
    
    /**
     * @return the loadBoard flag
     */
    public boolean getLoadBoardFlag()
    {
        return loadBoard;
    }
    
    /**
     * @return the noShuffle flag
     */
    public boolean getShuffleFlag()
    {
        return noShuffle;
    }
    
    /**
     * @return the name of the folder containing the saved game files
     */
    public String getFolder()
    {
    	String name = folderName.replace("@", "");//get rid of the special character
        return name;
    }
	/**
	 * @return the number of turns until the game is stopped
	 */
    public int getTurnNo(){
		return noTurns;
	}
    
    /**
     * STARTGAME sep game
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, noShuffle, load, folderName, noTurns, playerToStart, loadBoard, chatNegotiations, fullyObservable, observableVP);
    }

    /**
     * STARTGAME sep game
     *
     * @param ga  the name of the game
     * @param ns flag for deciding whether to shuffle the robot's position or not
     * @param l flag for deciding whether to load or start a new game
     * @param fn pathname to the folder containing the save files e.g. "saves/robot"
     * @param t the number of turns this game is allowed to run for.
     * @param pts the player number(or board position) of the player to start the game
     * @param lb flag for deciding whether to create a new board or load a saved configuration
     * @param fo flag for deciding if the game is fully observable or has hidden information
     * @param ov flag for deciding if drawing a vp card is an observable action
     * @return the command string
     */
    public static String toCmd(String ga, boolean ns, boolean l, String fn, int t, int pts, boolean lb, boolean cn, boolean fo, boolean ov)
    {
        String name = fn;
        if(!(name.contains("@"))){ 
    		name = "@" + fn;
    	}
        return STARTGAME + sep + ga + sep2 + ns + sep2 + l + sep2 + name + sep2 + t + sep2 + pts + sep2 + lb + sep2 + cn + sep2 + fo + sep2 + ov;
    }

    /**
     * Parse the command String into a StartGame message
     *
     * @param s   the String to parse
     * @return    a StartGame message, or null of the data is garbled
     */
    public static SOCStartGame parseDataStr(String s)
    {
        String ga;
        boolean ns;
        boolean l;
        String fn;
        int t;
        int pts;
        boolean lb;
        boolean cn;
        boolean fo;
        boolean ov;
        
        StringTokenizer st = new StringTokenizer(s, sep2);
        
        try
        {
            ga = st.nextToken();
            ns = Boolean.parseBoolean(st.nextToken());
            l = Boolean.parseBoolean(st.nextToken());
            fn = st.nextToken();
            t = Integer.parseInt(st.nextToken());
            pts = Integer.parseInt(st.nextToken());
            lb = Boolean.parseBoolean(st.nextToken());
            cn = Boolean.parseBoolean(st.nextToken());
            fo = Boolean.parseBoolean(st.nextToken());
            ov = Boolean.parseBoolean(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }
        
        return new SOCStartGame(ga, ns, l, fn, t, pts, lb, cn, fo, ov);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCStartGame:game=" + game + "|noShuffle=" + noShuffle + "|load=" + load + "|folderName=" + folderName + "|turns=" + noTurns + "|PlayerToStart=" + playerToStart + "|LoadBoard=" + loadBoard + "|ChatNegotiations=" + chatNegotiations + "|FullyObservable=" + fullyObservable + "|ObservableVp=" + observableVP;
    }
}
