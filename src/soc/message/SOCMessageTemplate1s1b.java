/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * This file Copyright (C) 2011 Markus Guhe <m.guhe@ed.ac.uk>
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

// import java.util.StringTokenizer;


/**
 * Template for per-game message types with 1 string and 1 boolean parameter.
 * You will have to write parseDataStr, because of its return
 * type and because it's static.
 *<P>
 *
 * @author Markus Guhe <m.guhe@ed.ac.uk>
 */
public abstract class SOCMessageTemplate1s1b extends SOCMessage
    implements SOCMessageForGame
{
    /**
     * Name of the game.
     */
    protected String game;

    /**
     * String parameter.
     */
    protected String p1;

    /**
     * Boolean parameter.
     */
    protected boolean p2;

    /**
     * Create a new message.
     *
     * @param id  Message type ID
     * @param ga  Name of game this message is for
     * @param p1   String parameter
     * @param p2   Boolaen parameter
     */
    protected SOCMessageTemplate1s1b(int id, String ga, String p1, boolean p2)
    {
        messageType = id;
        game = ga;
        this.p1 = p1;
        this.p2 = p2;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the first parameter
     */
    public String getParam1()
    {
        return p1;
    }

    /**
     * @return the second parameter, or null
     */
    public boolean getParam2()
    {
        return p2;
    }

    /**
     * MESSAGETYPE sep game sep2 param1 sep2 param2
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(messageType, game, p1, p2);
    }

    /**
     * MESSAGETYPE sep game sep2 param1 sep2 param2
     *
     * @param messageType The message type id
     * @param ga  the game name
     * @param param1 The first parameter
     * @param param2 The second parameter
     * @return    the command string
     */
    public static String toCmd(int messageType, String ga, String param1, boolean param2)
    {
        return Integer.toString(messageType) + sep + ga + sep2 + param1
        + sep2 + (param2 ? "true" : "false");
    }

    /**
     * Parse the command String into a MessageType message
     *
     * @param s   the String to parse
     * @return    a RejectCardName message, or null if parsing errors
    public static SOCRejectCardName parseDataStr(String s)
    {
        String ga; // the game name
        String cid; // the card id
        String cname; // the card name, or null for unknown

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            cid = st.nextToken();
            cname = st.nextToken();
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCRejectCardName(ga, cid, cname);
    }
     */

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return getClassNameShort() + ":game=" + game
            + "|param1=" + p1
            + "|param2=" + (p2 ? "true" : "false");
    }
}
