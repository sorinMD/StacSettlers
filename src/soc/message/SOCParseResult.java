/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2008-2010 Jeremy D Monin <jeremy@nand.net>
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
 * This message contains the result of calling the natural language parser.
 * The server calls the parser and uses this message 
 * to forward the result returned from the parser to the robot clients.
 *
 * @author Markus Guhe
 */
public class SOCParseResult extends SOCMessage
    implements SOCMessageForGame
{
    /**
     * our token separator; not the normal {@link SOCMessage#sep2}
     */
    private static String sep2 = "" + (char) 0;

    /**
     * Name of game
     */
    private String game;

    /**
     * Nickname of sender
     */
    private String nickname;

    /**
     * Text message
     */
    private String text;

    /**
     * Create a ParseResult message.
     *
     * @param ga  name of game
     * @param nn  nickname of sender
     * @param pr  text message
     */
    public SOCParseResult(String ga, String nn, String pr) {
        messageType = PARSERESULT;
        game = ga;
        nickname = nn;
        text = pr;
    }

    /**
     * @return the name of the game
     */
    public String getGame() {
        return game;
    }

    /**
     * @return the nickname (should always be the server)
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * @return the parse result
     */
    public String getParseResult() {
        return text;
    }

    /**
     * PARSERESULT sep game sep2 nickname sep2 text
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(game, nickname, text);
    }

    /**
     * PARSERESULT sep game sep2 nickname sep2 text
     *
     * @param ga  the game name
     * @param nn  the nickname
     * @param pr  the parse result
     * @return    the command string
     */
    public static String toCmd(String ga, String nn, String pr) {
        return PARSERESULT + sep + ga + sep2 + nn + sep2 + pr;
    }

    /**
     * Parse the command String into a parse result message
     *
     * @param s   the String to parse
     * @return    a parse result message, or null of the data is garbled
     */
    public static SOCParseResult parseDataStr(String s)
    {
        String ga;
        String nn;
        String ps;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try {
            ga = st.nextToken();
            nn = st.nextToken();
            ps = st.nextToken();
        } catch (Exception e) {
            return null;
        }

        return new SOCParseResult(ga, nn, ps);
    }

    /**
     * @return a human readable form of the message
     */
    @Override
    public String toString() {
        String s = "SOCParseResult:game=" + game + "|nickname=" + nickname + "|parseresult=" + text;

        return s;
    }
    
    // This uses special separators, presumably to handle special chars in the message itself.  Only replace the first two commas - everything after that is in the message
    public static String stripAttribNames(String str) {
    	String ret = SOCMessage.stripAttribNames(str);
    	for (int i=0; i<2; i++) {
    		ret = ret.replaceFirst(SOCMessage.sep2, sep2);
    	}
    	return ret;
    }
}
