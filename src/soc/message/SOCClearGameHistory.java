/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010-2011 Jeremy D Monin <jeremy@nand.net>
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
 * The maintainer of this program can be reached at jsettlers@nand.net
 **/
package soc.message;

import java.util.StringTokenizer;


/**
 * This message means that the server wants the clients to clear their game interaction history.
 *
 * @author Markus Guhe
 */
public class SOCClearGameHistory extends SOCMessageTemplate0
	implements SOCMessageForGame
{
	 private int playerNumber;
	
    /**
     * Create a SOCClearGameHistory message.
     *
     * @param ga  the name of the game
     */
    public SOCClearGameHistory(String ga, int pn)
    {
        super (CLEARGAMEHISTORY, ga);
        playerNumber = pn;
    }

    /**
     * CLEARGAMEHISTORY sep game sep2 playerNumber
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, playerNumber);
    }
    
    /**
     * CLEARGAMEHISTORY sep game
     *
     * @param ga  the name of the game
     * @return the command string
     */
    public static String toCmd(String ga, int pn)
    {
        return CLEARGAMEHISTORY + sep + ga + sep2 + pn;
    }

    /**
     * Parse the command String into a ClearGameHistory message
     *
     * @param s   the String to parse: CLEARGAMEHISTORY sep game
     * @return    a ClearGameHistory message, or null if the data is garbled
     */
    public static SOCClearGameHistory parseDataStr(String s)
    {
        String ga; // the game name
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

        return new SOCClearGameHistory(ga, pn);
    }
    
    /**
     * @return the seat number
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }
    
    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return getClassNameShort() + ":game=" + game + "|playerNumber=" + playerNumber;
    }

}
