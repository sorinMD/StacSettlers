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
 * This message signals a player's request to write in the chat text input field
 * or to withdraw the request.
 *
 * @author Markus Guhe
 */
public class SOCRequestToSpeak extends SOCMessageTemplate1s1b
	implements SOCMessageForGame
{
    /**
     * Create a RequestToSpeak message.
     *
     * @param nickname	the nickname of the player sending the request
     * @param withdraw  the flag whether this is a request to withdraw the request
     */
    public SOCRequestToSpeak(String ga, String nickname, boolean withdraw)
    {
    	super (REQUESTTOSPEAK, ga, nickname, withdraw);
    }

    /**
     * @return the name of the player
     */
    public String getNickname()
    {
        return p1;
    }

    /**
     * @return the state of the withdraw flag
     */
    public boolean getWithdrawFlag()
    {
        return p2;
    }

    /**
     * REQUESTTOSPEAK sep game sep2 nickname sep2 withdraw
     *
     * @param ga  the name of the game
     * @param nn  nickname
     * @param wd  the withdraw flag
     * @return the command string
     */
    public static String toCmd(String ga, String nn, boolean wd)
    {
        return REQUESTTOSPEAK + sep + ga + sep2 + nn + sep2 + wd;
    }

    /**
     * Parse the command String into a RequestToSpeak message
     *
     * @param s   the String to parse: REQUESTTOSPEAK sep game sep2 nickname sep2 withdraw
     * @return    a RequestToSpeak message, or null if the data is garbled
     */
    public static SOCRequestToSpeak parseDataStr(String s)
    {
        String ga; // the game name
        String nn; // the player's nickname 
        boolean wd; // the dice result

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            nn = st.nextToken();
            wd = Boolean.parseBoolean(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCRequestToSpeak(ga, nn, wd);
    }

}
