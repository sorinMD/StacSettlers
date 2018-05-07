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
public class SOCSpeakingQueueChanged extends SOCMessageTemplate1s
	implements SOCMessageForGame
{
    /**
     * Create a SpeakingQueueChangedToSpeak message.
     *
     * @param newSpeakingQueue  the new state of the speaking queue
     */
    public SOCSpeakingQueueChanged(String ga, String newSpeakingQueue)
    {
    	super (SPEAKINGQUEUECHANGED, ga, newSpeakingQueue);
    }

    /**
     * @return the state of the queue
     */
    public String getSpeakingQueue()
    {
        return p1;
    }

    /**
     * SPEAKINGQUEUECHANGED sep game sep2 newSpeakingQueue
     *
     * @param ga  the name of the game
     * @param sq  speaking queue
     * @return the command string
     */
    public static String toCmd(String ga, String sq)
    {
        return SPEAKINGQUEUECHANGED + sep + ga + sep2 + sq;
    }

    /**
     * Parse the command String into a SpeakingQueueChanged message
     *
     * @param s   the String to parse: SPEAKINGQUEUECHANGED sep game sep2 newSpeakingQueue
     * @return    a SpeakingQueueChanged message, or null if the data is garbled
     */
    public static SOCSpeakingQueueChanged parseDataStr(String s)
    {
        String ga; // the game name
        String sq; // the new state of the speaking queue 

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            sq = st.nextToken();
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCSpeakingQueueChanged(ga, sq);
    }

}
