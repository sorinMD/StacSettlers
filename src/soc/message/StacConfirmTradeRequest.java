/**
 * Created by STAC project, University of Edinburgh.
 */

package soc.message;

import java.util.StringTokenizer;
import soc.game.SOCResourceSet;

/**
 * Message sent by the server to SOCPlayerClient to confirm that the specified trade between the two players should be executed.
 * @author Markus Guhe <m.guhe@ed.ac.uk>
 */
public class StacConfirmTradeRequest extends SOCMessageTemplate2s implements SOCMessageForGame {
    
    /**
     * Create a new message. Both parameters are mandatory.
     *
     * @param ga  Name of game this message is for
     * @param p1   player number and SOCResourceSet specifying the give set of the trade for this player, separated by ":"
     * @param p2   player number and SOCResourceSet specifying the give set of the trade for this player, separated by ":"
     */
    public StacConfirmTradeRequest(String ga, String p1, String p2) {
        super(CONFIRMTRADETREQUEST, ga, p1, p2);
    }

    /**
     * @return the string for the first player
     */
    public String getP1String() {
        return p1;
    }

    /**
     * @return the string for the second player
     */
    public String getP2String() {
        return p2;
    }

    public int getPlayer1() {
        int p1Num = Integer.parseInt(p1.split(":")[0]);
        return p1Num;
    }
    
    public SOCResourceSet getPlayer1Resources() {
        String p1ResString = p1.split(":")[1];
        SOCResourceSet resSet = SOCResourceSet.parse(p1ResString.replace("~", SOCMessage.sep));
        return resSet;
    }

    public int getPlayer2() {
        int p2Num = Integer.parseInt(p2.split(":")[0]);
        return p2Num;
    }
    
    public SOCResourceSet getPlayer2Resources() {
        String p2ResString = p2.split(":")[1];
        SOCResourceSet resSet = SOCResourceSet.parse(p2ResString.replace("~", SOCMessage.sep));
        return resSet;
    }

    /**
     * Parse the command String into a StacConfirmTradeRequest message
     *
     * @param s   the String to parse
     * @return    a StacConfirmTradeRequest message, or null if parsing errors
     * */
    public static StacConfirmTradeRequest parseDataStr(String s) {
        String ga; // the game name
        String p1; // the player number of the first player plus the give set for the trade
        String p2; // the player number of the first player plus the give set for the trade

        StringTokenizer st = new StringTokenizer(s, sep2);

        try {
            ga = st.nextToken();
            p1 = st.nextToken();
            p2 = st.nextToken();
        } catch (Exception e) {
            return null;
        }

        return new StacConfirmTradeRequest(ga, p1, p2);
    }
}
