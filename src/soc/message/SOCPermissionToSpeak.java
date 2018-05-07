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
 * This message signals that the server gives a player the permission to speak, 
 * i.e. to write in the chat text input field.
 *
 * @author Markus Guhe
 */
public class SOCPermissionToSpeak extends SOCMessageTemplate1s
	implements SOCMessageForGame
{
    /**
     * Create a PermissionToSpeak message.
     *
     * @param nickname	the nickname of the player given permission to speak
     */
    public SOCPermissionToSpeak(String ga, String nickname)
    {
    	super (PERMISSIONTOSPEAK, ga, nickname);
    }

    /**
     * @return the name of the player
     */
    public String getNickname()
    {
        return p1;
    }

    /**
     * PERMISSIONTOSPEAK sep game sep2 nickname
     *
     * @param ga  the name of the game
     * @param nn  nickname
     * @return the command string
     */
    public static String toCmd(String ga, String nn)
    {
        return PERMISSIONTOSPEAK + sep + ga + sep2 + nn;
    }

    /**
     * Parse the command String into a PermissionToSpeak message
     *
     * @param s   the String to parse: PERMISSIONTOSPEAK sep game sep2 nickname
     * @return    a PermissionToSpeak message, or null if the data is garbled
     */
    public static SOCPermissionToSpeak parseDataStr(String s)
    {
        String ga; // the game name
        String nn; // the player's nickname 

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            nn = st.nextToken();
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCPermissionToSpeak(ga, nn);
    }

}
