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


/**
 * This message means that the player is allowed to join the game
 * this client to join a game
 *
 * @author Robert S Thomas
 */
public class SOCJoinGameAuth extends SOCMessage
    implements SOCMessageForGame
{
    /**
     * Name of game
     */
    private String game;

    /**
     * ---MG
     * Show Dialog to remind users about trading
     */
    private boolean showDialog;
    
    /**
     * Create a JoinGameAuth message.
     *
     * @param ga  name of game
     * @param sd  determine whether to Show the Dialog about trading 
     */
    public SOCJoinGameAuth(String ga, boolean sd)
    {
        messageType = JOINGAMEAUTH;
        game = ga;
        showDialog = sd;
    }

    /**
     * @return the game name
     */
    public String getGame()
    {
        return game;
    }

    /**
     * ---MG
     * @return whether to show the trade reminder dialog
     */
    public boolean getShowDialog()
    {
    	return showDialog;
    }
    
    /**
     * JOINGAMEAUTH sep game
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(game, showDialog);
    }

    /**
     * JOINGAMEAUTH sep game
     *
     * @param ga  the game name
     * @return    the command string
     */
    public static String toCmd(String ga, boolean sd)
    {
        return JOINGAMEAUTH + sep + ga + sep2 + (sd ? "true" : "false");
    }

    /**
     * Parse the command String into a JoinGameAuth message
     *
     * @param s   the String to parse
     * @return    a JoinGameAuth message, or null of the data is garbled
     */
    public static SOCJoinGameAuth parseDataStr(String s)
    {
//        return new SOCJoinGameAuth(s);
    	//---MG
        StringTokenizer st = new StringTokenizer(s, sep2);

        String ga;
        String sd;
        
        try
        {
            ga = st.nextToken();
            sd = st.nextToken();
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCJoinGameAuth(ga, sd.equals("true"));
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCJoinGameAuth:game=" + game + "|showDialog=" + (showDialog ? "true" : "false");

        return s;
    }
}
