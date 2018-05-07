/**
 * Created by STAC project, University of Edinburgh.
 */

package soc.message;

import java.util.StringTokenizer;

/**
 * Message sent by the server to SOCPlayerClient to confirm that the specified trade between the two players should be executed.
 * @author Markus Guhe <m.guhe@ed.ac.uk>
 */
public class StacConfirmTradeAnswer extends SOCMessage implements SOCMessageForGame {

    boolean answer;

    String game;

    /**
     * Create a new message.
     *
     * @param ga        name of the game
     * @param answer    flag indicating the answer
     */
    public StacConfirmTradeAnswer(String ga, boolean answer) {
        messageType = SOCMessage.CONFIRMTRADEANSWER;
        game = ga;
        this.answer = answer;
    }

    /**
     * @return the name of the game
     */
    @Override
    public String getGame() {
        return game;
    }

    /**
     * @return is the trade accepted of rejected?
     */
    public boolean getAnswer() {
        return answer;
    }

    /**
     * CONFIRMTRADEANSWER sep game sep2 param
     *
     * @return the command String
     */
    @Override
    public String toCmd() {
        return toCmd(game, answer);
    }

    /**
     * CONFIRMTRADEANSWER sep game sep2 param
     *
     * @param ga            the game name
     * @param answer        the answer
     * @return    the command string
     */
    public static String toCmd(String ga, boolean answer) {
        return SOCMessage.CONFIRMTRADEANSWER + sep + ga + sep2 + answer;
    }

    /**
     * Parse the command String into a StacConfirmTradeAnswer message
     *
     * @param s   the String to parse
     * @return    a LongestRoad message, or null if parsing errors
     */ 
    public static StacConfirmTradeAnswer parseDataStr(String s)
    {
        String ga; // the game name
        boolean ans; // the answer

        StringTokenizer st = new StringTokenizer(s, sep2);

        try {
            ga = st.nextToken();
            ans = Boolean.parseBoolean(st.nextToken());
        } catch (Exception e) {
            return null;
        }

        return new StacConfirmTradeAnswer(ga, ans);
    }

    /**
     * @return a human readable form of the message
     */
    @Override
    public String toString() {
        return getClassNameShort() + ":game=" + game + "|param=" + Boolean.toString(answer);
    }

}
