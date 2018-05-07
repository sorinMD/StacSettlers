/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
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
 * This message reports that a player started registering a trade.
 * This is used to write the info on the interaction history so the other players know what's happening.
 *
 * @author Markus Guhe
 */
public class SOCPlayerStartsTrading extends SOCMessageTemplate1s
	implements SOCMessageForGame
{
    /**
     * Create a PlayerStartsTrading message.
     *
     * @param ga  the name of the game
     * @param dr  the dice result
     */
    public SOCPlayerStartsTrading(String ga, String pl)
    {
        super (PLAYERSTARTSTRADING , ga, pl);
    }

    /**
     * @return the player's name
     */
    public String getPlayer()
    {
        return p1;
    }

    /**
     * PLAYERSTARTSTRADING sep game sep2 player
     *
     * @param ga  the name of the game
     * @param pl  the name of the player
     * @return the command string
     */
    public static String toCmd(String ga, String pl)
    {
        return PLAYERSTARTSTRADING  + sep + ga + sep2 + pl;
    }

    /**
     * Parse the command String into a PlayerStartsTrading message
     *
     * @param s   the String to parse: PLAYERSTARTSTRADING  sep game sep2 player
     * @return    a PlayerStartsTrading message, or null if the data is garbled
     */
    public static SOCPlayerStartsTrading parseDataStr(String s)
    {
        String ga; // the game name
        String pl; // the player name

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pl = st.nextToken();
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCPlayerStartsTrading(ga, pl);
    }

}
