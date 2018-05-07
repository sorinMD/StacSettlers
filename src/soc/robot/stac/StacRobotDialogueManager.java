package soc.robot.stac;

import soc.dialogue.StacTradeMessage;
import supervised.main.BayesianSupervisedLearner;
import simpleDS.learning.SimpleAgent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.Random;
//import soc.disableDebug.D;
import soc.debug.D;
import soc.dialogue.StacDialogueManager;
import soc.dialogue.StacSDRTParser;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCTradeOffer;
import soc.game.StacTradeOffer;
import soc.robot.SOCBuildPlanStack;
import soc.robot.SOCBuildingSpeedEstimate;
import soc.robot.SOCPossiblePiece;
import soc.robot.SOCRobotNegotiator;
import soc.robot.stac.learning.JMTLearner;

/***
 * Class handling dialogue interactions between players via the chat.
 * Mainly used by the robots to send trade messages and announcements via the chat,
 * but also used by the player client to interpret chat messages and turn them into
 * StacTradeMessage objects that can be understood by the robots.
 * Interacts with the brain to determine when to share information, and what to do with information that is shared.
 * @author kho30
 */

public class StacRobotDialogueManager extends StacDialogueManager {
    
    /**
     * The brain which uses this implementation to chat with other players/robots
     */
    private final StacRobotBrain brain;
    
    /**
     * forced trade, using Jedi-Mind-Trick settings
     */
    protected JMTLearner jmt;

    /** Static fields that define type of messages, information shared etc. */
//    //Signalling end of chat or starting a turn
//    private static final String NULL_CHAT = "NULL:";
//    private static final String START_TURN = "ST";
    // Information to be shared/requested
    private static final String BUILD_PLAN = "BP";    
    private static final String RESOURCES = "RES";
    private static final String EXTRA_RES = "EXTRA";
    private static final String WANTED_RES = "WANTED";
    
    public static final String JMT_TRADE = "JTRD:";
    public static final String ANNOUNCE_DISTRUST = "ANN_DISTRUST:";
        
    /**
     * For client communication of build-plan comparison (ie what's announced vs. inferred)
     * Note that these messages are not sent to other players, only the server, for logging purposes
     * TODO: A more general framework for server-only messages
     */
    public static final String BP_COMP = "BPCOMP:";
    
    /**
     * For client communication of has-resources comparison (ie what's announced vs. inferred)
     * Note that these messages are not sent to other players, only the server, for logging purposes
     * TODO: A more general framework for server-only messages
     */
    public static final String HR_COMP = "HRCOMP:";

    // Prefixes for message types:
    private static final String REQUEST = "REQ:";
    private static final String ANNOUNCE = "ANN:";
    public static final String ANN_PARTICIPATION = "PARTICIPATE";

    public static final String EMBARGO = "EMBARGO:";
    public static final String EMBARGO_PROPOSE = "EMBARGO_PROPOSE";
    public static final String EMBARGO_COMPLY = "EMBARGO_COMPLY";
    public static final String EMBARGO_LIFT = "EMBARGO_LIFT";

//    /**Constants used when querying the XML*/
    private static final String GIVEABLE_WILDCARD = "\\$giv";
    private static final String RECEIVABLE_WILDCARD = "\\$rec";
    private static final String PLAYER_WILDCARD = "\\$pl";
    public static final String xmlQueryTradePrefix = "/statement/trade/";
    public static final String xmlQueryPersuasionPrefix = "/statement/persuasion/";
//    private static PersuasionGenerator persuasionGen;
    
    public BayesianSupervisedLearner sltrader;
    public SimpleAgent deeptrader;	


    /**
     * Class handling dialogue interactions between players via the chat.
     * Mainly used by the robots to send trade messages and announcements via the chat,
     * but also used by the player client to interpret chat messages and turn them into
     * StacTradeMessage objects that can be understood by the robots.
     * Interacts with the brain to determine when to share information, and what to do with information that is shared.
     * @param brain the robot's brain
     */
    public StacRobotDialogueManager(StacRobotBrain brain) {
        
        this.brain = brain;
        if (brain.isRobotType(StacRobotType.DIALOGUE_MANAGER_USE_SDRT))
            this.sdrtParser = new StacSDRTParser(this);
        
        if (brain.isRobotType(StacRobotType.JEDI_MIND_TRICK)) {
            jmt = JMTLearner.getLearner(brain);
            brain.registerLearner(jmt);
        }
        
		if (brain.isRobotType(StacRobotType.SUP_LEARNING_NEGOTIATOR) && sltrader == null) {
			sltrader = this.brain.getSupervisedNegotiator();
		}

		if (brain.isRobotType(StacRobotType.DRL_LEARNING_NEGOTIATOR) && deeptrader == null) {
			deeptrader = this.brain.getDeepNegotiator();
		}
    }

    @Override
    public int getPlayerNumber() {
        return brain.getPlayerNumber();
    }

    @Override
    public SOCGame getGame() {
        return brain.getGame();
    }
    
    
    /**
     * Create a REJ message to be sent to the chat (including the TRADE prefix).
     * @param sender    the sender of this message
     * @param receiver  the receiver of the REJ message
     * @param NLString  the NL string to be used in the player interface
     * @return          a REJ message in the correct format
     */
    public static String makeRejectMessage(int sender, int receiver, String NLString) {
        if (NLString == null || NLString.equals("")) {
            System.err.println("There must be an NL string specified!");
        }
        String sendString = Integer.toString(sender);
        String recString = Integer.toString(receiver);
        StacTradeMessage rejectMsg = new StacTradeMessage(sendString, recString, false, true, false, null, false, NLString);
        String rejectMsgString = rejectMsg.toMessage();
        return rejectMsgString;
    }

    /**
     * Create a REJ message when there is no NL string. The NL string is generated in this method.
     * @param receiver  the receiver of the REJ message
     * @return          a REJ message in the correct format
     */
    public String makeRejectMessage(int receiver) {
        String[] playernames = brain.getGame().getPlayerNames();
        HashMap<String,String> swapTable = new HashMap<>();
        String name = playernames[receiver];
        swapTable.put(PLAYER_WILDCARD, name);
        String rejectMsg = queryXMLFile(xmlQueryTradePrefix + "responseTrade/response[@accept=\"false\" and (@explicitAdressee=\"false\" or @explicitAdressee=\"true\")]" + xmlQueryMessageSuffix, swapTable);
        int sender = brain.getPlayerNumber();
        return makeRejectMessage(sender, receiver, rejectMsg);
    }

    /**
     * Create an ACC message to be sent to the chat (including the TRADE prefix).
     * @param sender    the sender of the message
     * @param receiver  the receiver of the ACC message
     * @param NLString  the NL string to be used in the player interface
     * @return          a ACC message in the correct format
     */
    public static String makeAcceptMessage(int sender, int receiver, String NLString) {
        String sendString = Integer.toString(sender);
        String recString = Integer.toString(receiver);
        StacTradeMessage acceptMsg = new StacTradeMessage(sendString, recString, true, false, false, null, false, NLString);
        String acceptMsgString = acceptMsg.toMessage();
        return acceptMsgString;
    }
    
    /**
     * Create an ACC message when there is no NL string. The NL string is generated in this method.
     * @param receiver  the receiver of the ACC message
     * @return          a ACC message in the correct format
     */
    public String makeAcceptMessageWithExplicitAddressee(boolean explicitAdressee, int receiver) {
        String[] playernames = brain.getGame().getPlayerNames();
        HashMap<String,String> swapTable = new HashMap<>();
        String name = playernames[receiver];
        swapTable.put(PLAYER_WILDCARD, name);
        String acceptMsg = queryXMLFile(xmlQueryTradePrefix + "responseTrade/response[@accept=\"true\" and (@explicitAdressee=\"" + Boolean.toString(explicitAdressee) + "\")]" + xmlQueryMessageSuffix, swapTable);
        int sender = brain.getPlayerNumber();
        return makeAcceptMessage(sender, receiver, acceptMsg);
    }

    /**
     * Create a no-response message to be sent to the chat (including the TRADE prefix).
     * @param receiver  the receiver of the no-response message
     * @param NLString  the NL string to be used in the player interface
     * @return          a no-response message in the correct format
     */
    public String makeNoResponseMessage(int receiver, String NLString) {
        String sender = Integer.toString(brain.getPlayerNumber());
        String recString = Integer.toString(receiver);
        StacTradeMessage noResponseMsg = new StacTradeMessage(sender, recString, false, false, true, null, false, NLString);
        String noResponseMsgString = noResponseMsg.toMessage();
        return noResponseMsgString;
    }
    
    /**
     * Create an no-response message when there is no NL string. The NL string is generated in this method.
     * @param playerNum the receiver of the no-response message
     * @return          a no-response message in the correct format
     */
    public String makeNoResponseMessage(int playerNum) {
        String[] playernames = brain.getGame().getPlayerNames();
        HashMap<String,String> swapTable = new HashMap<>();
        String name = playernames[playerNum];
        swapTable.put(PLAYER_WILDCARD, name);
        String noResponseMsg = queryXMLFile(xmlQueryTradePrefix + "responseTrade/response[@accept=\"either\" and @negotiation=\"either\"]" + xmlQueryMessageSuffix, swapTable);
        return makeNoResponseMessage(playerNum, noResponseMsg);
    }
    
    /**
     * Turns the trade message into a Natural Language Statement by generating an appropriate query
     * @param nickname          The name of the player who sent this message
     * @param playernames       the names of the players in this game, in sequence of their player number
     * @param offer             the offer made
     * @param persuasiveMove    The persuasion attempting to be implemented
     * @param context           Further constraint for specific contexts in which the offer is made
     * @return A String with the message to be printed in the chat interface
     */
    public String tradeMessageOffToString(String nickname, String[] playernames, StacTradeOffer offer, Persuasion persuasiveMove, String context) {
        HashMap<String, String> replacementMap = new HashMap<>();
        StringBuilder offMsgText = new StringBuilder();
        String giveResString = offer.getGiveSet().toFriendlyString();
        if (offer.hasDisjunctiveGiveSet())
            giveResString = giveResString.replace("and", "or");
        replacementMap.put(GIVEABLE_WILDCARD, giveResString);
        String getResString = offer.getGetSet().toFriendlyString();
        if (offer.hasDisjunctiveGetSet())
            getResString = getResString.replace("and", "or");
        replacementMap.put(RECEIVABLE_WILDCARD, getResString);
        StringBuilder temp = new StringBuilder();
        boolean toAllPlayers = true;
        boolean notFirst = false;
        for (int i = 0; i < offer.getTo().length; i++) {
            if (i == offer.getFrom()) {
                //Do nothing
            }
            else if (offer.getTo()[i]) {
                String name = playernames[i];
                if (notFirst) {
                    temp.append(", ").append(name);
                } else{ 
                    notFirst = true; temp.append(name);
                }
            }
            else {
                toAllPlayers=false;
            }
        }

        replacementMap.put(PLAYER_WILDCARD, temp.toString());
        String queryString = xmlQueryTradePrefix + 
                "initialTrade/tradeMessage[@specifyPlayer=\"" + !toAllPlayers +"\" " +
                "and @specifyGiveable=\"" + (!replacementMap.get(GIVEABLE_WILDCARD).equals("nothing")) + "\" " + //NB: The toFriendlyString() method we used above, returns "nothing" for the empty set
                "and @specifyReceivable=\"" + (!replacementMap.get(RECEIVABLE_WILDCARD).equals("nothing")) + "\" " + 
                "and @context=\"" + context + "\"]" +                 
                xmlQueryMessageSuffix;
        offMsgText.append(queryXMLFile(queryString, replacementMap));

        if (persuasiveMove.getIdentifier() != Persuasion.PersuasionIdentifiers.NullPersuasion) {
            String persuasionString = queryXMLFile(xmlQueryPersuasionPrefix + persuasiveMove.getQueryRepresentation() + xmlQueryMessageSuffix, persuasiveMove.getParameters());
            offMsgText.append(" ").append(persuasionString);
        }
        return offMsgText.toString();
    }
   
    /**
     * Process a chat message from an opponent.  Return a list of any chat messages to be sent in response. 
     * @param playerNum  the sender's player number 
     * @param message    the chat message
     * @return           list of messages to send as response
     */
    public List<String> handleChat(int playerNum, String message) {
        D.ebugPrintlnINFO(brain.getPlayerName() + "(" + brain.getPlayerNumber() + ") - " +  playerNum + ": Chat message: " + message);
        
        if (sdrtParser != null) {
            sdrtParser.parseTextMessageIntoSDRT(playerNum, message);
        }
        
        List<String> ret = new ArrayList<String>();
//        if(!((message.startsWith(NULL_CHAT))||(message.equals(ANN_PARTICIPATION)))){
//    	System.out.println(playerNum+": "+message);
//        }
        try {
            if (message.equals(ANN_PARTICIPATION)) {
                brain.getMemory().setParticipating(playerNum, true);
            }
            else if (message.startsWith(REQUEST)) {
                ret.addAll(handleRequest(message.substring(REQUEST.length())));
            }
            else if (message.startsWith(ANNOUNCE)) {
                ret.addAll(handleAnnounce(playerNum, message.substring(ANNOUNCE.length())));
            }
            else if (message.startsWith(NULL_CHAT)) {
            	brain.getMemory().stopWaiting(playerNum, message.substring(NULL_CHAT.length()));
            }
            else if (message.startsWith(StacTradeMessage.TRADE)) {
                ret.addAll(handleTradeMessage(playerNum, message.substring(StacTradeMessage.TRADE.length())));
            }
            else if (message.startsWith(JMT_TRADE)) {
                ret.addAll(handleJMTMessage(playerNum, message.substring(JMT_TRADE.length())));
            }
            else if (message.startsWith(ANNOUNCE_DISTRUST)) {
                // no need for reply, just block them
                int distrustPlayerNum = Integer.parseInt(message.substring(ANNOUNCE_DISTRUST.length()));
                // NB: We may be distrusting ourselves here, but that has no impact anyway                
                brain.getNegotiator().blockTrades(distrustPlayerNum);
                
                // If someone else announced we used JMT, remember this.  This is probably the easiest way
                //  to do this, since it's possible to attempt to use JMT but have no player able to make the trade
                if (distrustPlayerNum == brain.getPlayerNumber() && jmt!=null) {
                    jmt.setUsed();
                }
            }
            else if (message.startsWith(EMBARGO)) {
                //message format: "EMBARGO:1:INIT:3"
                String p[] = message.split(":");
                if (p[2].equals(EMBARGO_PROPOSE)) {
                    int proposer = Integer.parseInt(p[1]);
                    int embargoTarget = Integer.parseInt(p[3]);
                    brain.getNegotiator().embargoProposed(proposer, embargoTarget);
                }
                if (p[2].equals(EMBARGO_COMPLY)) {
                    //observing that another player complies with an init-embargo move; no action required
                }
                if (p[2].equals(EMBARGO_LIFT)) {
                    //observing that another player lifts an embargo; no action required
                }
            }
        }
        catch (Exception e) {
            ret.add("Exception: " + message);
            System.err.println(brain.getPlayerName() + "(" + brain.getPlayerNumber() + ") - " + brain.getGame().getName() + "(" + brain.getGame().getTurnCount() + ") - Exception for " + message + " - sender: " + playerNum);
            System.err.println(e.toString());
            e.printStackTrace(System.err);
            brain.getMemory().stopWaiting();
        }
        
        return ret;
    }
    
    /**
     * Handle a request for information
     * @param message the message containing the request
     * @return
     */
    private List<String> handleRequest( String message) {
        List<String> ret = new ArrayList<String>();
        if (message.startsWith(BUILD_PLAN)) {
            ret.addAll(respBPRequest());
        }
        else if (message.startsWith(RESOURCES)) {
            ret.addAll(respResRequest());
        }     
        else if (message.startsWith(EXTRA_RES)) {
            ret.addAll(respExtraRequest());
        }
        else if (message.startsWith(WANTED_RES)) {
            ret.addAll(respWantedRequest());
        }
        else if (message.startsWith(StacTradeMessage.TRADE)) {
            ret.addAll(respTradeRequest());
        }
        return ret;
    }    
    
    /**
     * Respond to a solicitation for trades
     * @return a list containing the possible responses or only the null response
     */
    private List<String> respTradeRequest() {
        List<String> ret = new ArrayList<String>();
        
        if (brain.isRobotType(StacRobotType.MAKE_SOLICITED_OFFER) ) {
            SOCBuildPlanStack bp = brain.getBuildingPlan();
            String trd = null;
            if (!bp.isEmpty()) {            
                trd = negotiateTrade(bp);
            }
            if (trd!=null) {
                ret.add(trd);
            }
            else {
                ret.add(NULL_CHAT + StacTradeMessage.TRADE);
            }
        }      
        else {
            ret.add(NULL_CHAT + StacTradeMessage.TRADE);
        }
        return ret;
    }
    
    /**
     * Respond to a request for build plan
     * @return a list containing the possible responses or only the null response
     */
    private List<String> respBPRequest() {
        List<String> ret = new ArrayList<String>();
        
        if (brain.isRobotType(StacRobotType.SHARE_BUILD_PLAN)) {
            ret.addAll(announceBuildPlan());
        }      
        else {
            ret.add(NULL_CHAT + BUILD_PLAN);
        }
        return ret;
    }    
    
    /**
     * Respond to a resource announce request
     * @return a list containing the possible responses or only the null response
     */
    private List<String> respResRequest() {
        List<String> ret = new ArrayList<String>();
        
        if (brain.isRobotType(StacRobotType.SHARE_RESOURCES)) {
            ret.addAll(announceResources());
        }      
        else {
            ret.add(NULL_CHAT + RESOURCES);
        }
        return ret;
    }   
    
    /**
     * Respond to extra resources request
     * @return a list containing the possible responses or only the null response
     */
    private List<String> respExtraRequest() {
        List<String> ret = new ArrayList<String>();
        
        if (brain.isRobotType(StacRobotType.SHARE_EXTRA_RES)) {
            ret.addAll(announceExtraResources());
        }      
        else {
            ret.add(NULL_CHAT + EXTRA_RES);
        }
        return ret;
    } 
    
    /**
     * Responds to announce wanted resources request
     * @return a list containing the possible responses or only the null response
     */
    private List<String> respWantedRequest() {
    	List<String> ret = new ArrayList<String>();
        
        if (brain.isRobotType(StacRobotType.SHARE_WANTED_RES)) {
            ret.addAll(announceWantedResources());
        }      
        else {
            ret.add(NULL_CHAT + WANTED_RES);
        }
        return ret;
    } 
    
    private String lastBPAnnouncement;
    
    /**
     * Builds the build plan announcement message
     * @return a list containing the message(s) describing the piece we are aiming to build
     */
    protected List<String> announceBuildPlan() {
        List<String> ret = new ArrayList<String>();

        SOCPossiblePiece p = null;
        
        //if we're lying about our build plan, we announce our best-1 plan
        if (brain.isRobotType(StacRobotType.LYING_ABOUT_BUILD_PLAN)) {
        	SOCBuildPlanStack bp = new SOCBuildPlanStack();
        	StacRobotDM dm = new StacRobotDM(brain, bp);
            SOCPlayer myData = brain.getPlayerData();
            SOCBuildingSpeedEstimate currentBSE = brain.getEstimator(myData.getNumbers());
            int currentBuildingETAs[] = currentBSE.getEstimatesFromNowFast(myData.getResources(), myData.getPortFlags());
            bp = dm.dumbFastGameStrategyNBest(currentBuildingETAs, 1);
            if (bp.size() > 0) {
                p = bp.getPlannedPiece(0);
            }
        } else {
        	SOCBuildPlanStack plan = brain.getBuildingPlan();
            p =  (plan.isEmpty() ? null : plan.peek());
        }

        String textMsg = ANNOUNCE + BUILD_PLAN + ":";
        if (p == null) {
            textMsg += "NULL";
        } else {
            // Replace | with a different character (~) to prevent problems parsing the message (| is message delimiter as well as object delimiter)
            // TODO: Rework to use character constants
            textMsg += toMessage(p.toString());
        }
        if (textMsg.equals(lastBPAnnouncement))
            return new ArrayList<>();
            
        lastBPAnnouncement = textMsg;
        ret.add(textMsg);
        return ret;
    }
    /**
     * Builds the resource announcement message
     * @return the list containing the message(s) describing what resources we have in our hand
     */
    private List<String> announceResources() {
        List<String> ret = new ArrayList<String>();
        SOCResourceSet rs;

        //if we're lying about our resources, we claim to have 0 of everything
        if (brain.isRobotType(StacRobotType.LYING_ABOUT_RESOURCES)) {
            SOCResourceSet newRs = new SOCResourceSet(brain.getMemory().getResources().getTotal(), 0, 0, 0, 0, 0);
            //SOCResourceSet newRs = new SOCResourceSet();
            rs = newRs;
        } else if (brain.isRobotType(StacRobotType.LYING_ABOUT_RESOURCES_HAVE_1)) {
            SOCResourceSet newRs = new SOCResourceSet(1, 1, 1, 1, 1, 0);
            rs = newRs;            
        } else if (brain.isRobotType(StacRobotType.LYING_ABOUT_RESOURCES_HAVE_2)) {
            SOCResourceSet newRs = new SOCResourceSet(2, 2, 2, 2, 2, 0);
            rs = newRs;            
        }
        else {
            rs = brain.getPlayerData().getResources();
        }
        
        ret.add(ANNOUNCE + RESOURCES + ":" + toMessage(rs.toString()));
        return ret;
    }
    
    /**
     * Builds the extra resource announcement message
     * @return a list containing the message(s) describing our extra resources
     */
    private List<String> announceExtraResources() {
    	List<String> ret = new ArrayList<String>();
  
        SOCResourceSet target = SOCResourceSet.EMPTY_SET;
        SOCBuildPlanStack plan = brain.getBuildingPlan();
        if (!plan.isEmpty()) {
            target = plan.peek().getResourceCost();
        }
        
        SOCResourceSet current = brain.getPlayerData().getResources();
        SOCResourceSet extra = current.copy();
        extra.subtract(target);
        
        ret.add(ANNOUNCE + EXTRA_RES + ":" + toMessage(extra.toString()));
        return ret;
    }
    
    /**
     * Builds the resources wanted announcement message
     * @return a list containing the message(s) describing the resources we want/need for executing our current build plan
     */
    private List<String> announceWantedResources() {
        List<String> ret = new ArrayList<String>();
      
        SOCResourceSet target = SOCResourceSet.EMPTY_SET;
        SOCBuildPlanStack plan = brain.getBuildingPlan();
        if (!plan.isEmpty()) {
            target = plan.peek().getResourceCost().copy();
        }
        
        SOCResourceSet current = brain.getPlayerData().getResources();
        target.subtract(current);
        
        ret.add(ANNOUNCE + WANTED_RES + ":" + toMessage(target.toString()));
        return ret;
    }

    /**
     * Builds the embargo proposal message
     * @param pn the player number of the player against whom we are proposing an embargo
     * @return a string containing the embargo message
     */
    protected String makeProposeEmbargoAgainstPlayerMessage(int pn) {
        String embargoMes = EMBARGO + brain.getPlayerNumber() + ":" + EMBARGO_PROPOSE + ":" + pn;
        return embargoMes;
    }
    
    /**
     * Builds and sends the embargo proposal
     * @param pn the player number of the player against whom we are proposing an embargo
     */
    protected void sendProposeEmbargoAgainstPlayer(int pn) {
        String embargoMes = makeProposeEmbargoAgainstPlayerMessage(pn);
        brain.sendText(embargoMes);
    }

    /**
     * Builds and sends the embargo acceptance msg
     * @param receiver the player number of the player against whom we are accepting an embargo
     */
    protected void sendComplyingWithEmbargoAgainstPlayer(int receiver) {
        String sender = Integer.toString(brain.getPlayerNumber());
        String recString = Integer.toString(receiver);
        StacTradeMessage noResponseTM = new StacTradeMessage(sender, recString, false, false, false, false, null, true, "I'm complying with embargo against player" + brain.getMemory().getGame().getPlayerNames()[receiver]);
        String noResponseMes = noResponseTM.toMessage();
        brain.sendText(noResponseMes);
        
        //EMBARGO and EMBARGO_COMPLY are not TradeMessages but their own message type; 
        //TODO: Consider moving EMBARGO etc. into the StacTradeMessage class
        String embargoMes = EMBARGO + brain.getPlayerNumber() + ":" + EMBARGO_COMPLY + ":" + receiver;
        brain.sendText(embargoMes);
    }

    /**
     * Builds and sends the embargo lifting msg
     * @param pn the player number of the player against whom we are lifting an embargo
     */
    protected void sendLiftEmbargoAgainstPlayer(int pn) {
        String embargoMes = EMBARGO + brain.getPlayerNumber() + ":" + EMBARGO_LIFT + ":" + pn;
        brain.sendText(embargoMes);
    }
    
    /**
     * Handle an announcement of information (assume it was requested)
     * @param playerNum
     * @param message
     * @return
     */
    private List<String> handleAnnounce(int playerNum, String message) {
    	List<String> ret = new ArrayList<String>();
        if (message.startsWith(BUILD_PLAN)) {
        	ret.addAll(handleAnnounceBuildPlans(playerNum, message));
        }
        else if (message.startsWith(RESOURCES)) {
        	ret.addAll(handleAnnounceResources(playerNum, message));
        }
        else if (message.startsWith(EXTRA_RES)) {
        	handleAnnounceExtraResources(playerNum, message);
        }
        else if (message.startsWith(WANTED_RES)) {
        	handleAnnounceWantedResources(playerNum, message);
        }
        return ret;
    }
    
    /**
     * Handles the announcement of current build plans
     * @param playerNum representing the player who sent the message
     * @param message the announcement message
     * @return a list containing the build plan or an empty list if the robot is not of specific type
     */
    private List<String> handleAnnounceBuildPlans(int playerNum, String message){
    	List<String> ret = new ArrayList<String>();
    	brain.getMemory().stopWaiting(playerNum, BUILD_PLAN);
        if (brain.isRobotType(StacRobotType.LISTEN_BUILD_PLAN)) {
            SOCPossiblePiece p = SOCPossiblePiece.parse(fromMessage(message));
            
            // Determine what we would have predicted, and compare it to what's announced
            SOCBuildPlanStack predicted;
            // Code for guessing of BBP (to establish baselines)
            if (brain.isRobotType(StacRobotType.BBPPP_GUESS)) {
                predicted = new SOCBuildPlanStack();
                int guess = ((Integer)brain.getRobotType().getTypeParam(StacRobotType.BBPPP_GUESS));

                // NB: Need to use SOCPossiblePiece.parse, since actual constructors invoke a bunch of calculations we don't want to do       
                String s = "type=" + guess + "|player=-1|coord=-1";
                SOCPossiblePiece gp = SOCPossiblePiece.parse(s);                    
                predicted.add(gp);                        
            } else {
                predicted = brain.createNegotiator().predictBuildPlan(playerNum);
            }

            boolean nullEquiv, correctType, fullEquality;
            if (predicted.isEmpty() && p==null) {
                nullEquiv = true;
                correctType = true;
                fullEquality = true;
            }
            else if (predicted.isEmpty() || p==null) {
                nullEquiv = false;
                correctType = false;
                fullEquality = false;
            }
            else {
                // Both are non-null, we can safely compare them now
                nullEquiv = true;
                if (predicted.peek().getType() == p.getType()) {
                    correctType = true;
                    fullEquality = predicted.peek().getCoordinates() == p.getCoordinates();
                }
                else{
                    correctType = false;
                    fullEquality = false;
                }
            }
            // Announce the result of your prediction to the server
            ret.add(BP_COMP + Boolean.toString(nullEquiv)
                    + "," + Boolean.toString(correctType)
                    + "," + Boolean.toString(fullEquality)); 
            
            //save the announced target piece for this agent
            brain.getNegotiator().setTargetPiece(playerNum, p);
        }
        return ret;
    }
    
    /**
     * Handles the announcement of resources in hand
     * @param playerNum representing the player who sent the message
     * @param message the announcement message
     * @return a list containing the announcements or an empty list if the robot is not of specific types
     */
    private List<String> handleAnnounceResources(int playerNum, String message){
    	List<String> ret = new ArrayList<String>();
    	brain.getMemory().stopWaiting(playerNum, RESOURCES);

        // Determine what we would have predicted, and compare it to what's announced
        if (brain.isRobotType(StacRobotType.HRP_GUESS)) {
            SOCResourceSet believedRes = brain.getMemory().getOpponentResourcesBelieved(playerNum);
            SOCResourceSet observedRes = brain.getMemory().getOpponentResourcesObserved(playerNum);
            
            String rString = fromMessage(message.substring(RESOURCES.length()+1));
            SOCResourceSet announcedRes = SOCResourceSet.parse(rString);

            //evaluate prediction precision
            boolean believedCorrect = true;
            boolean observedCorrect = true;
            boolean subsetCorrect = true;
            for (int i = SOCResourceConstants.CLAY; i < SOCResourceConstants.WOOD; i++) {
                if (announcedRes.getAmount(i) != believedRes.getAmount(i)) {
                    believedCorrect = false;
                }
                if (announcedRes.getAmount(i) != observedRes.getAmount(i)) {
                    observedCorrect = false;
                }
                if (announcedRes.getAmount(i) <  believedRes.getAmount(i)) {
                    subsetCorrect = false;
                }
            }

            // Announce the result of the prediction to the server
            ret.add(HR_COMP + Boolean.toString(believedCorrect)
                    + "," + Boolean.toString(observedCorrect)
                    + "," + Boolean.toString(subsetCorrect)); 
        }

        if (brain.isRobotType(StacRobotType.LISTEN_RESOURCES)) {
            // Get the game object held by the server         
            String rString = fromMessage(message.substring(RESOURCES.length()+1));
            SOCResourceSet announcedRS  = SOCResourceSet.parse(rString);
            brain.getMemory().announcedOpponentResources(playerNum, announcedRS);
            
            // Sanity check - is this what the server believes?
            /*  This check always passes.  Commented out in case it needs to be revisited (Server.getGame also commented out)
            SOCGame ga = SOCServer.getGame(game.getName());
            SOCPlayer p = ga.getPlayer(playerNum);
            SOCResourceSet trueRs = p.getResources();                
            if (!trueRs.equals(announcedRS)) {
                System.err.println("Falsehood:" + trueRs.getTotal() + " vs " + announcedRS.getTotal());
            }
            */
        }
        return ret;
    }
    
    /**
     * Handles the announcement of extra resources that players may be willing to trade
     * TODO: should marking as selling logic be moved in the negotiator class?
     * @param playerNum representing the player who sent the message
     * @param message the announcement message
     */
    private void handleAnnounceExtraResources(int playerNum, String message){
    	brain.getMemory().stopWaiting(playerNum, EXTRA_RES);
        if (brain.isRobotType(StacRobotType.LISTEN_EXTRA_RES)) {
            String rString = fromMessage(message.substring(RESOURCES.length()+1));
            SOCResourceSet announcedRS  = SOCResourceSet.parse(rString);
            brain.getMemory().announcedOpponentResources(playerNum, announcedRS);
            for (int i=SOCResourceConstants.CLAY; i<=SOCResourceConstants.WOOD; i++) {
                if (announcedRS.getAmount(i)>0) {
                    brain.getNegotiator().markAsSelling(playerNum, i);
                }
            }
        }
    }
    
    /**
     * Handles the announcement of what resources players want 
     * TODO: should marking as not selling/not wanting another offer logic be moved in the negotiator class?
     * @param playerNum representing the player who sent the message
     * @param message the announcement message
     */
    private void handleAnnounceWantedResources(int playerNum, String message){
    	brain.getMemory().stopWaiting(playerNum, WANTED_RES);
        if (brain.isRobotType(StacRobotType.LISTEN_WANTED_RES)) {
            String rString = fromMessage(message.substring(RESOURCES.length()+1));
            SOCResourceSet announcedRS  = SOCResourceSet.parse(rString);
            for (int i=SOCResourceConstants.CLAY; i<=SOCResourceConstants.WOOD; i++) {
                if (announcedRS.getAmount(i)>0) {
                    brain.getNegotiator().markAsNotSelling(playerNum, i);
                    brain.getNegotiator().markAsNotWantingAnotherOffer(playerNum, i);
                }
            }
        }
    }

    /**
     * Request the build plan of other players - set isWaiting to true
     * @return a list containing the request message
     */
    private List<String> request(String reqType) {
        List<String> ret = new ArrayList<String>();
        brain.getMemory().waitAll(reqType);
        ret.add(REQUEST + reqType);
        return ret;
    }
    
    /**
     * Announce its participation in the chat interface
     * @return a list containing the announce participation message
     */
    public List<String> startGameChat() {
        List<String> ret = new ArrayList<String>();
       
        ret.add(ANN_PARTICIPATION);
        return ret;
    }
    
    /**
     * Let the manager know that a turn has started, and return a list of any chat messages to be sent (eg announcements, requests, complaining about robbery...).
     * TODO: This should probably return a NULL to let others know we're done, but this will cause headaches for now.  Unsolicited announcements therefore may not be 
     *  received prior to commencement of negotiation/
     * @param turnPlayerNum the player number of the player whose turn it is (eg to check if it's our turn)
     * @return a list containing all the announcements and requests followed by a null message
     */
    public List<String> startTurnChat(int turnPlayerNum) {
        lastBPAnnouncement = "";
        
        // Might be extra work, but make sure we have an up to date build plan
        brain.resetBuildingPlan();//throw the old plan away to avoid duplicates
    	brain.getDecisionMaker().planStuff();
        
        // Make sure we reset the negotiator (should be handled, but be safe)        
        resetMyNegotiatedOffer();
        setMyOffer(null);
        resetResponses(); // reset the trade responses
        
        // Anything we were waiting for from last turn is long gone
        brain.getMemory().stopWaiting();
        
        List<String> ret = new ArrayList<String>();
        if (turnPlayerNum == getPlayerNumber()) {
            if (brain.isRobotType(StacRobotType.LISTEN_RESOURCES)) {
                ret.addAll(request(RESOURCES));
            }
            if (brain.isRobotType(StacRobotType.LISTEN_BUILD_PLAN)) {
                ret.addAll(request(BUILD_PLAN));
            }            
            if (brain.isRobotType(StacRobotType.LISTEN_EXTRA_RES)) {
                ret.addAll(request(EXTRA_RES));
            }
            if (brain.isRobotType(StacRobotType.LISTEN_WANTED_RES)) {
                ret.addAll(request(WANTED_RES));
            }
            
            if (brain.isRobotType(StacRobotType.SHARE_RESOURCES)) {
                ret.addAll(announceResources());
            }            
            if (brain.isRobotType(StacRobotType.SHARE_EXTRA_RES)) {                
                ret.addAll(announceExtraResources());
            }
            if (brain.isRobotType(StacRobotType.SHARE_WANTED_RES)) {
                ret.addAll(announceWantedResources());
            }
            if (brain.isRobotType(StacRobotType.SHARE_BUILD_PLAN)) {
                //this is already handled in the call to planStuff()
//                lastBPAnnouncement = "";
//                ret.addAll(announceBuildPlan());
            }
            
            if (brain.isRobotType(StacRobotType.SOLICIT_TRADES)) {
                ret.addAll(request(StacTradeMessage.TRADE));
            }
            
        }
        ret.add(NULL_CHAT + START_TURN);
        if (sdrtParser != null)
            sdrtParser.sdrtNewConversation  = true;
        brain.getMemory().waitAll(START_TURN);
        return ret;
    }
    
    /**
     * Section for handling of trade negotiations
     */

    /**
     * Begin negotiations, aiming for a specific build plan
     * @param bp the goal build plan
     * @return the message to send to kick off a trade, or null (should use a list here as elsewhere)
     */
    public String negotiateTrade(SOCBuildPlanStack bp) {
        setMyOffer(null);
        resetMyNegotiatedOffer();
        String result = null;

        PersuasionStacRobotNegotiator neg = brain.getNegotiator();
        
        StacTradeOffer offer = null;
        boolean useJMT = false;
        
        // If we have Jedi capabilities, decide if we're going to use them
        if (jmt!=null) {
            boolean used = jmt.isJmtUsed();
            // Will the JMT yield a trade which allows instant building of build plan?
            boolean productive = false;
            
            if (!used) {
                // First pass to see if we have a productive trade
                offer = neg.makeOffer(bp, false, null, null);
                if (offer!=null) {
//                    if (offer instanceof TradeOffer) {
//                        OfferWithStats ows = (OfferWithStats) offer;
//                        productive = ows.getEta() == 0;                        
//                    }
                    int eta = brain.getNegotiator().getETAToTargetResources(brain.getPlayerData(), bp.totalResourcesForBuidPlan(), offer.getGiveSet(), offer.getGetSet(), brain.getEstimator());
                    productive = eta == 0;
                    // TODO: add productivity param
                    useJMT = jmt.useJMT(brain, productive);
                    
                    //If we're not using it, rerun negotiator with filter applied
                    // TODO: Optimize?
                    if (useJMT==false) {
                        offer = neg.makeOffer(bp, true, null, null);
                    }                    
                }
                else {
                    // TODO: Should we call useJMT for the side effect, or just let the next
                    //  state handle it?
                    jmt.useJMT(brain, false);
                }
            }
            else {
             // NB: We have to call this function even if there's no chance it's true as this
                //  has side-effects w.r.t. learning
                jmt.useJMT(brain, false);
            }
        }        
        else {
            // Filter likely iff we are not using a JMT persuasive move
            offer = neg.makeOffer(bp, true, null, null);
        }
        if (offer!=null) { 
            // If it's not our turn, narrow the recipients to only the player whose turn it is
            // May need to verify we still have a recipient - I believe makeOffer will assume it's our turn and may behave inappropriately
            boolean hasValidRecip = false;
            if (brain.getMemory().getCurrentPlayerNumber()!=getPlayerNumber()) {
                boolean[] to = offer.getTo();
                for (int i=0; i<4; i++) {
                    if (brain.getMemory().getCurrentPlayerNumber() != i) {
                        to[i] = false;
                    }
                    else if (to[i]) {
                        hasValidRecip = true;
                    }
                }
            }
            else {
                hasValidRecip = true;
            }
            if (!hasValidRecip) {
                return null;
            }
            
            //If the negotiator is of type persuasion
            String tradeMessageType = "";
            Persuasion persuasiveArgument = new Persuasion();
            if(brain.getNegotiator().getPersuaderType()){
            	persuasiveArgument = brain.getNegotiator().generatePersuasionMove(offer);
            }
            else{
                boolean forced = brain.getNegotiator().shouldOfferBeForced(offer);
            	if(forced){
                    brain.getMemory().incNumForceAcceptMoves();
                    tradeMessageType = StacTradeMessage.FORCE_ACCEPT;            		
            	}
            }
            
            String nlChatString = tradeMessageOffToString(brain.getPlayerName(), brain.getGame().getPlayerNames(), offer, persuasiveArgument, "");
            result = makeTradeOffer(offer, tradeMessageType, persuasiveArgument, nlChatString);

            // Jedi-mind-trick:
            if (useJMT) {                
                // if we're using it, change the prefix
                result = result.replace(StacTradeMessage.TRADE, JMT_TRADE);
            } 
            neg.resetWantsAnotherOffer();
        }
        
        return result;
    }
    
    /**
     * Determine the resources that we want to prevent an opponent from getting by issuing a block.
     * This depends on our guess of the opponent's BBP.
     * @param playerNum  the player to be prevented from getting resources
     * @param offer      the offer to which our response is a BLOCK
     * @return SOCResourceSet with the resources for the type of piece in the BBP
     */
    private SOCResourceSet resourcesToBeBlocked(int playerNum, SOCTradeOffer offer) {
        
        if (brain.isRobotType(StacRobotType.NP_BLOCK_RESOURCES_ASKED_FOR)) {
            return offer.getGetSet();
        }
        
        //get the offerer's BBP
        SOCBuildPlanStack predicted = brain.getNegotiator().predictBuildPlan(playerNum);
        
        // we may not come up with a BBP for that player
        if (predicted == null || predicted.size() < 1) {
            return null;
        }
        
        SOCResourceSet resourcesToBlock = new SOCResourceSet();
        SOCPossiblePiece predictedPiece = predicted.getPlannedPiece(0);

        switch (predictedPiece.getType()) {
            case SOCPossiblePiece.ROAD:
                resourcesToBlock = SOCGame.ROAD_SET;
                break;
            case SOCPossiblePiece.SETTLEMENT:
                resourcesToBlock = SOCGame.SETTLEMENT_SET;
                break;
            case SOCPossiblePiece.CITY:
                resourcesToBlock = SOCGame.CITY_SET;
                break;
            case SOCPossiblePiece.CARD:
                resourcesToBlock = SOCGame.CARD_SET;
                break;                                    
        }
        return resourcesToBlock;
    }
    
    /**
     * Helper function to create a string for a trade offer.
     * @param offer             the trade offer to make
     * @param tradeMessageType  the type of message (forced)
     * @param persuasiveArgument  Persuasive argument - only if the move is "forced"
     * @return a String of the trade offer
     */    
    private String composeTradeOfferString(StacTradeOffer offer, String tradeMessageType, Persuasion persuasiveArgument, String nlString) {
        if (nlString == null || nlString.equals("")) {
            String out = "The TradeMessage must contain an NL string here!\n";
            for (StackTraceElement trace : Thread.currentThread().getStackTrace()) {
                out += "\t" + trace + "\n";
            }
            System.err.println(out);
        }
        
        // Add recepients of the trade offer
        boolean offerTo[] = offer.getTo();
        for (int i=0; i < offerTo.length; i++) {
            // we're expecting a response from EVERRYBODY now, not just the players we're making the offer to
            brain.getMemory().wait(i, StacTradeMessage.TRADE);
            setPlayerResponse(i, null);
            if (offerTo[i]) {
                //if offer was also to the human players, force the agent to wait for a response from them
                SOCPlayer pl = brain.getGame().getPlayer(i);
                if(!pl.isRobot()){
                    brain.getMemory().waitForHumanPlayer(i, StacTradeMessage.TRADE);
                }
            }
        }
        
        String sender = Integer.toString(brain.getPlayerNumber());
        String receivers = StacTradeMessage.getToAsString(offerTo);
        
        // Is this a force accept message?
        boolean isForceAccept = tradeMessageType.equals(StacTradeMessage.FORCE_ACCEPT);
                
        StacTradeMessage tm = new StacTradeMessage(sender, receivers, offer, isForceAccept, persuasiveArgument, nlString);
        String result = tm.toMessage();
        return result;
    }
    
    /**
     * Helper function, called both from initial offer and counter-offer.
     * Turn a trade offer into a message string
     * Update negotiator and brain variables accordingly
     * Set waiting flags accordingly
     * @param offer
     * @param tradeMessageType  the type of message (forced, block, don't block)
     * @param persuasiveArgument  only applied if type of message is forced
     * @return a String of the trade offer
     */
    public String makeTradeOffer(StacTradeOffer offer, String tradeMessageType, Persuasion persuasiveArgument, String nlString) {
        // we're actually making the offer
        setMyOffer(offer);
        // Consider this offer made right away, don't wait for rejections.
        PersuasionStacRobotNegotiator neg = brain.getNegotiator();
        neg.addToOffersMade(offer);
        brain.getPlayerData().setCurrentOffer(offer);        

        String result = composeTradeOfferString(offer, tradeMessageType, persuasiveArgument, nlString);
//        brain.printMess("Making offer: " + result);
//        brain.printMess("State: " + stateString());

        // store whether this offer was forced for future reference
        
        //Making a persuasion is equivalent (in this sense) to making a force accept
        boolean wasLastOfferForced = (tradeMessageType.equals(StacTradeMessage.FORCE_ACCEPT) || (persuasiveArgument.getIdentifier()!=Persuasion.PersuasionIdentifiers.NullPersuasion));
        brain.getMemory().setLastOfferForced(wasLastOfferForced);
        
        return result;
    }

    /**
     * Handle a trade message made by another player.
     * @param playerNum
     * @param message       the trade message as String
     * @return a list with the responses to the offer
     */
    private List<String> handleTradeMessage(int playerNum, String message) {        
        List<String> ret = new ArrayList<String>();
        // Interpret what the trade means regardless of whether it's to us (toSelling, etc)        
        updateNegotiator(playerNum, message);

        //Analyse the message
        String convertedMessage = fromMessage(message);
        StacTradeMessage tm = StacTradeMessage.parse(convertedMessage);
        
        //update belief about opponent resources
        if (brain.isRobotType(StacRobotType.SIMPLE_UPDATE_OF_OPPONENT_RESOURCES_FROM_OFFER) && tm.getOffer() != null) {
            int offerer = tm.getOffer().getFrom();
            SOCResourceSet offererResourceSet = brain.getMemory().getOpponentResources(offerer);
            //if there are unknown resoureces
            if (offererResourceSet.getAmount(SOCResourceConstants.UNKNOWN) > 0) {
                SOCResourceSet giveSet = tm.getOffer().getGiveSet();
                for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD; i++) {
                    int resDifference = giveSet.getAmount(i) - offererResourceSet.getAmount(i);
                    if (resDifference > 0) {
                        brain.getMemory().addOpponentResourcesObserved(offerer, i, resDifference);
                        brain.getMemory().addOpponentResourcesObserved(offerer, SOCResourceConstants.UNKNOWN, -resDifference);
                    }
                }
            }
        }

//        brain.printMess("Receiving trade message: " + message + " - sender: " + playerNum);
//        brain.printMess("State: " + brain.getMemory().stateString());
//        brain.printMess("Normal processing of message: " + message + " - sender: " + playerNum);

        // See if we are a listed recipient of this message:
        if (tm.getReceivers().contains(Integer.toString(getPlayerNumber()))) {
            // Is this a response to something we sent?  If so, it should be an accept, reject, block or counter-offer

            if (brain.getMemory().isWaiting(playerNum, StacTradeMessage.TRADE)) {
//                brain.printMess("I'm currently waiting for: " + brain.getMemory().stateString());
                
//                if (tm.isNoResponse()) {
//                    brain.printMess("This is a no-response I'm waiting for.");
//                }

                setPlayerResponse(playerNum, tm);
                brain.getMemory().stopWaiting(playerNum, StacTradeMessage.TRADE);
//                brain.printMess("Received a response I'm waiting for: " + brain.getMemory().stateString());

                if (!brain.getMemory().isWaiting(StacTradeMessage.TRADE)) {
//                    brain.printMess("This is a response we're waiting for. Calling handleNegotiations(). State: " + brain.getMemory().stateString());
    
                    // If this was the last response we were waiting for, handle the responses now
                    ret.addAll(handleNegotiations());
                }
            }
            // this may be an unsolicited block
            else if (tm.isBlock()) {
//                brain.printMess("Received block from " + playerNum);
                if (!brain.getMemory().isWaiting(StacTradeMessage.TRADE)) {
//                    brain.printMess("This is an unsolicited block for me, but I'm not waiting for anything. Just ignoring it. " + stateString());
                } else {
                    setPlayerResponse(playerNum, tm);
//                    brain.printMess("This is an unsolicited block for me, so I'm just recording it: " + stateString());
                }
            }
            // this may be an unsolicited no-response message
            else if (tm.isNoResponse()) {
                if (!brain.getMemory().isWaiting(StacTradeMessage.TRADE)) {
//                    brain.printMess("This is a no-response I'm not waiting for, but I'm not waiting for anything." + brain.getMemory().stateString());
                } else {
                    setPlayerResponse(playerNum, tm);
//                    brain.printMess("This is an unsolicited no-response for me, so I'm just recording it: " + brain.getMemory().stateString());
                }
            }
            else {
                // This must be a new offer to us...
                // Error check - make sure it's not an ACC/REJ that accidentally crossed turn boundaries (these no longer seem to happen, but be careful)
                // TODO this error check is very confusing... what exception are we catching?
            	//(EDIT: only Integer.parse in SOCTradeOffer can throw an exception, so then this checks for unsupported message formats rather than unexpected)
            	// and why are we checking if get class is null? if the response is null then that will throw a nullpointer and we won't get into handleNegotiations(); getClass will never return null otherwise
//            	boolean unexpected = false;
//                try { 
//                	setPlayerResponse(playerNum, StacTradeMessage.parse(message));
//                }
//                catch (Exception ex) {
//                    unexpected = true;                    
//                }
//                
//                if (getPlayerResponse(playerNum).getClass()==null) {
//                    unexpected = true;
//                }
//                
//                if (unexpected) {
//                    ret.add("Unexpected trade message: " + message);
//                    System.err.println("Unexpected trade message: " + message);
//                }                
//                brain.printMess("This is a new message to us. Calling handleNegotiations(). State: " + stateString());
                
                // handle the trade message
                if(tm.isAccept() || tm.isReject() || tm.isNoResponse()){
                    D.ebugWARNING("Player " + getPlayerNumber() + ": Unexpected message from: " + playerNum + "\n" + tm.toString());
                    
//                    //see if the SDRT strcuture gives us a clue of what happens here
//                    if (tm.isAccept()) {
//                        SDRTNode messageNode = sdrt.get(sdrt.size()-1);
//                        System.err.println("++++++ SDRT node: " + messageNode.toString());
//                    }
                    
                    //unexpected msg type so we do nothing about it we just ignore them
            	}else{
//                    brain.printMess("This is a new trade offer to us: " + brain.getMemory().stateString());
                    setPlayerResponse(playerNum, tm);
                    ret.addAll(handleNegotiations());
            	}
            }
        } 
        // so, we're not a recipient of this message, 
        // but we can try to interfere with the normal proceedings by issuing a block
        else {
//            brain.printMess("We're not a recipient of this message: " + brain.getMemory().stateString());
            SOCTradeOffer offer = tm.getOffer();
            if (offer != null) {
            	ret.addAll(tryBlockOrEmbargo(offer, false));//we are not a recipient
            	// NOTE: ret list should be empty until this point according to the above logic
                // we're not an agent that can send BLOCK moves or embargo
                // so we have to tell the sender that we're not responding 
                if (ret.isEmpty()) {
                    ret.add(makeNoResponseMessage(offer.getFrom()));
                }
            } 
        }
//        brain.printMess("Sending result of normal processing: " + ret.toString() + " - State: " + stateString());
            
        return ret;
    }

    /**
     * try to interfere with negotiations by issuing a block or an embargo if we can
     * @param offer             the offer to interfere with
     * @param weAreRecipient    if we are recipient or not
     * @return a list with the block or embargo messages or an empty list if the robot is not of a specific type
     */
    private List<String> tryBlockOrEmbargo(SOCTradeOffer offer, boolean weAreRecipient){
    	List<String> ret = new ArrayList<String>();
    	 int from = offer.getFrom();
         //this method is called twice: once when we are not included in the trades and we want to block others 
    	 //or when we are included in the trades but we want to block the offer so nobody else can accept it
         if ((brain.isRobotType(StacRobotType.NP_BLOCK_TRADES_NOT_MADE_TO_ME) && !weAreRecipient)||
        	(brain.isRobotType(StacRobotType.NP_BLOCK_TRADES_I_WOULD_REJECT) && weAreRecipient)) {
            if (brain.getNegotiator().shouldOfferBeBlocked(offer)) {
                SOCResourceSet resourcesToBlock = resourcesToBeBlocked(from, offer);
//                 if (resourcesToBlock == null) {
//                     brain.printMess("Don't know what resources to block, so this block doesn't specify any resources.");
//                 }
                String sender = Integer.toString(brain.getPlayerNumber());
                String recString = Integer.toString(from);
                StacTradeMessage blockMes = new StacTradeMessage(sender, recString, false, false, true, false, resourcesToBlock, false, "I'm blocking resources " + resourcesToBlock.toFriendlyString() + ".");
                String result = blockMes.toMessage();
//                 brain.printMess("Sending blocking message: " + result);
                brain.getMemory().incNumBlockMoves();
                ret.add(result);
            }
        }
         
        // are we considering a trade embargo for an offer for immediate build? 
        if (brain.isRobotType(StacRobotType.NP_PROPOSE_EMBARGO_AFTER_OFFER_FOR_IMMEDIATE_BUILD)) {
            if (brain.getNegotiator().shouldPlayerBeEmbargoedAfterTradeOffer(offer)) {
                String sender = Integer.toString(brain.getPlayerNumber());
                String recString = Integer.toString(from);
                StacTradeMessage noResponseTM = new StacTradeMessage(sender, recString, false, false, false, false, null, true, "Player should be embargoed after this trade offer.");
                String noResponseMes = noResponseTM.toMessage();
                ret.add(noResponseMes);                 
                String result = makeProposeEmbargoAgainstPlayerMessage(from);
                ret.add(result);
            }
        }
    	return ret;
    }
    
    /**
     *  Handle a Jedi Mind Trick trade message from another player
     * @param playerNum The player number corresponding to the player who sent the message
     * @param message the trade message
     * @return a list of responses to a JMT message
     */
    private List<String> handleJMTMessage(int playerNum, String message) {
        List<String> ret = new ArrayList<String>();
        // Intepret what the trade means regardless of whether it's to us (toSelling, etc)
        updateNegotiator(playerNum, message);

        int sepIdx = message.indexOf(':');
        // See if we are a listed recipient of this message:
        String recips = message.substring(0, sepIdx);        
        String myPlayerNumString = Integer.toString(getPlayerNumber());
        if (recips.contains(myPlayerNumString)) {
            // First, make sure we're not already blocking them
            boolean accepted = false;
            if (!brain.getNegotiator().isBlockingTrades(playerNum)) {                
                // check we can legally do that...               
                // we have no unknown resources in our own hand, so we can be 'optimistic' about unknowns
                if (brain.getNegotiator().isLegal(getPlayerOffer(playerNum), getPlayerNumber(), true)) {
                    // Slight modification of original acceptance logic.  
                    // TODO: Refactor this, get rid of duplication
                    setMyNegotiatedOffer(getPlayerOffer(playerNum));
                    boolean to[];
                    to = getMyNegotiatedOffer().getTo();
                    // Restrict to - the trade we negotiated doesn't involve the other players                
                    for (int j=0; j<4; j++) {                            
                        if (j!=getPlayerNumber()) {                                
                            to[j]=false;                                
                        }
                    }   
                    accepted = true;
                }
            }
            
            if (accepted) {
                ret.add(makeAcceptMessageWithExplicitAddressee(false, playerNum));

                // Announce the deception to the game
                ret.add(ANNOUNCE_DISTRUST + playerNum);
                // block them ourselves
                brain.getNegotiator().blockTrades(playerNum);  
            }
            else {
                ret.add(makeRejectMessage(playerNum));
            }
            tradeHandled(playerNum);            
        }
        return ret;
    }

    /**
     * Based on a trade message, update the isSelling, wantsAnotherOffer, etc in negotiator.
     * @param playerNum  the sender of the message
     * @param message the message received
     */
    private void updateNegotiator(int playerNum, String message) {
        PersuasionStacRobotNegotiator negotiator = brain.getNegotiator();
        StacTradeMessage tr = StacTradeMessage.parse(fromMessage(message));        
        String playerName = brain.getGame().getPlayer(playerNum).getName();

        if (tr.isAccept()) {
            //count for persistent info
            brain.getMemory().incNumOpponentTradeOfferAccepts(playerName);

            // Note: nothing is updated when this happens - the trade itself may affect the values.  We still need this block to rule out this message type before we attempt to parse an offer
        }            
        else if (tr.isReject()) {
            // Logic from Brain.handleREJECT
            SOCTradeOffer offer = getPlayerOffer(Integer.parseInt(tr.getReceivers().substring(0, 1)));
            
            //count for persistent info
            brain.getMemory().incNumOpponentTradeOfferRejects(playerName);
            //TODO: should this marking as not selling logic be moved in the negotiator class?
            if(offer == null)
            	D.ebugWARNING("Null offer rejected");//debugging. to be removed soon hopefully
            else{
	            SOCResourceSet getSet = offer.getGetSet();
	            for (int rsrcType = SOCResourceConstants.CLAY; rsrcType <= SOCResourceConstants.WOOD; rsrcType++) {
	                if ((getSet.getAmount(rsrcType) > 0) && (!negotiator.wantsAnotherOffer(playerNum, rsrcType)))
	                    negotiator.markAsNotSelling(playerNum, rsrcType);
	            }
            }
        }
        else if (tr.isBlock()) {
            // not doing anything here
        }
        else if (tr.isBlockComply()) {
//            brain.printMess("Receiving a block-comply message from: " + playerNum);
            if (getMyNegotiatedOffer() != null) {
                // I am just waiting for the trade to be executed, but my trade partner chickened out (probably after having made a counteroffer to me)
                // so I'm just forgetting about it
//                brain.printMess("My negotiated offer (which I'm forgetting now): " + myNegotiatedOffer.toString());
                resetMyNegotiatedOffer();
            }
//            else {
//                brain.printMess("No negotiated offer!");
//            }
        }
        else if (tr.isNoResponse()) {
            // not doing anything here
//            brain.printMess("updateNegotiator: processing no-response.");
        }
        else {
            SOCTradeOffer offer = tr.getOffer();
            
            if (offer == null) {
                brain.printMess("updateNegotiator(): offer is null!");
            } else {
                setPlayerOffer(playerNum, offer);
                //count for persistent info
                brain.getMemory().incNumOpponentTradeOffer(playerName);

                // Logic from Brain.handleMAKEOFFER - streamlined a little bit TODO: should this be moved in the negotiator
                SOCResourceSet giveSet = offer.getGiveSet();
                SOCResourceSet getSet = offer.getGetSet();
                for (int rsrcType = SOCResourceConstants.CLAY; rsrcType <= SOCResourceConstants.WOOD; rsrcType++) {
                    if (getSet.getAmount(rsrcType) > 0) {
                        negotiator.markAsNotSelling(playerNum, rsrcType);
                    }
                    if (giveSet.getAmount(rsrcType) > 0) {
                        negotiator.markAsWantsAnotherOffer(playerNum, rsrcType);
                    }
                }
            }
        }
    }

    /**
     * Indicate we have handled a player's trade action.
     * We're not waiting for them, and their response is taken care of.
     * NOTE: this method sets the player's response to null
     * @param playerNum number of the player whose trade action we handled
     */
    private void tradeHandled(int playerNum) {
    	brain.getMemory().stopWaiting(playerNum, StacTradeMessage.TRADE);
        setPlayerResponse(playerNum, null);
    }
    
    /**
     * Handle negotiations. It handles blocks, accepts, rejects, and counter-offers if we are expecting responses to our offer,
     * Otherwise it handles a new offer made by one of our opponents.
     * It also contains different behaviours for different robot types.
     * @return a list containing the responses to trade messages received
     */
    private List<String> handleNegotiations() {
        List<String> ret = new ArrayList<String>();
        boolean tradeMade = false;
        //if we started the negotiations and we are waiting for responses
        if(getMyOffer() != null){
            //map all the responses to the players that sent them and the type of response, but be careful when calling tradeHandled 
            //as these won't be cleared, but the actual responses will be cleared in the memory
            Map<Integer, StacTradeMessage> counterOffers = new HashMap<Integer, StacTradeMessage>();
            Map<Integer, StacTradeMessage> blocks = new HashMap<Integer, StacTradeMessage>();
            Map<Integer, StacTradeMessage> rejects = new HashMap<Integer, StacTradeMessage>();
            Map<Integer, StacTradeMessage> accepts = new HashMap<Integer, StacTradeMessage>();
            Map<Integer, StacTradeMessage> noResponses = new HashMap<Integer, StacTradeMessage>();
            for(int i=0; i < 4; i++){
            	StacTradeMessage resp = getPlayerResponse(i);
                if (resp != null) {
                    if(resp.isAccept())
                        accepts.put(i, resp);
                    else if(resp.isBlock())
                        blocks.put(i, resp);
                    else if(resp.isReject())
                        rejects.put(i, resp);
                    else if(resp.getOffer()!=null)
                        counterOffers.put(i, resp);
                    else if(resp.isNoResponse()){
                        noResponses.put(i, resp); //treat as if these were rejects but don't force trade on the players
                    }
                    else{/*we ignore the rest but, we shouldn't get here anyway;*/
                        //debugging. to be removed soon hopefully
                    	D.ebugERROR("Ignored trade response: \n" + resp.toString());
                    }
                }
            }
            
            //check if we were blocked
            if(!blocks.isEmpty()){
                if (brain.isRobotType(StacRobotType.NP_BLOCK_TRADES_GULLIBLE)) {
                    ret.addAll(handleBlocks(blocks, counterOffers));
                    if(!ret.isEmpty())
                        return ret; //if we have been blocked return the block comply and the reject offer msgs
                }
            }
            //look for better counter offers before going with an accept if there are any accepts and counter offers and we are capable
            if ((!counterOffers.isEmpty()) && (!accepts.isEmpty()) && brain.isRobotType(StacRobotType.NP_ACCEPT_BETTER_COUNTEROFFER)) {
                ret.addAll(checkBetterCounterOffer(counterOffers));
                if(!ret.isEmpty())
                    return ret; //we have found a better counter offer and we have done the trade so we can return now
            }
            //handle accepts if there are any
            if(!accepts.isEmpty()){
                //if we are in here we will do a trade no matter what
                tradeMade = true;
                handleAccepts(accepts);
            }
            //handle rejects even if a trade was made; 
            for(Integer pn : rejects.keySet()){
                tradeHandled(pn);
            }

            //handle noResponses even if a trade was made; 
            for(Integer pn : noResponses.keySet()){
                tradeHandled(pn);
            }

            //Handle counter-offers as if these were a normal offer
            if(!counterOffers.isEmpty()) // avoid calling method if we don't have any counter-offers to handle
                tradeMade = handleOffer(ret, tradeMade, counterOffers);

            //try to force a trade if we were rejected and its our turn
            if(!tradeMade && brain.isOurTurn())
                tradeMade = tryForceTrade(ret, tradeMade, rejects);
        	
        	
        }else{//else we are replying to a new offer so just handle it normally
            Map<Integer, StacTradeMessage> offers = new HashMap<Integer, StacTradeMessage>();
            for(int i=0; i < 4; i++){
                StacTradeMessage resp = getPlayerResponse(i);
                if (resp != null) {
                    if(resp.getOffer()!=null)
                        offers.put(i, resp);
                    else{/*we ignore the rest, but we shouldn't get here anyway;*/
                        //debugging. to be removed soon hopefully
                    	D.ebugERROR("Ignored trade response: \n" + resp.toString());
                    }
                }
            }
            //handle new offers; if we are here it means we definitely received new offers
            tradeMade = handleOffer(ret, tradeMade, offers);
        }

        // so, we check again (NOTE: what and why are we checking for? why are we setting my offer to null?
        //This is causing a lot of nullpointers when we look for our offer in the memory during processing of a reject) 
        //It also causes a lot of no response msgs to be sent and not recognised as no responses.
        //Besides this gets reset at the beginning of a turn or when making a new offer
//        if (!tradeMade) {
//            setMyOffer(null);
//        }
        return ret;
    }
    
    /**
     * Iterates over all the blocks issued by other players and complies with all and rejects any counter offers
     * @param blocks the map containing the blocks and who issued them
     * @param cOffers the map containing the counter offers and who sent them
     * @return the list of responses
     */
    private List<String> handleBlocks(Map<Integer, StacTradeMessage> blocks, Map<Integer, StacTradeMessage> cOffers) {
    	List<String> ret = new ArrayList<String>();
        //handle each block for marking of resources being blocked and when this block has started
        for(Integer pn : blocks.keySet()){
            SOCResourceSet blockedResources = blocks.get(pn).getResources();
            brain.getMemory().addBlockedResources(blockedResources);
            brain.getMemory().setTurnBlockStarted(brain.getGame().getTurnCount());
        }
        //create rejects for counter offers
        for(Integer pn : cOffers.keySet()){
            ret.add(makeRejectMessage(pn));

        }
        boolean[] to = new boolean[brain.getGame().maxPlayers];
        int ourPn = brain.getPlayerNumber();
        for (int p=0; p < brain.getGame().maxPlayers; p++) {
            if(p!=ourPn){
                to[p] = true;
                tradeHandled(p);//mark all trades as handled indifferent of what they are
            }
        }
        String sender = Integer.toString(ourPn);
        String receivers = StacTradeMessage.getToAsString(to);
        StacTradeMessage tm = new StacTradeMessage(sender, receivers, false, false, false, true, null, false, "I am complgying with the blocking request.");
        ret.add(tm.toString());//and tell everyone we are complying with a block
        return ret;
    }

    /**
     * Checks if any of the counter-offers received is better than our offer
     * NOTE: it only compares the offers based on the number of resources received/given, but not how needed these resources are. It
     * checks the counter-offer against the current batna, but not against the batna given our initial offer.
     * @param cOffers a map of counterOffers and the players who sent them
     * @return if it finds a better counter offer it adds the responses to the list, otherwise it returns an empty list
     */
    private List<String> checkBetterCounterOffer(Map<Integer, StacTradeMessage> cOffers){
    	List<String> ret = new ArrayList<String>();
    	int playerToTradeWith = 256; //just some value
	    boolean acceptCounterOffer = false;
    	for(Integer pn : cOffers.keySet()){
    		SOCTradeOffer co = cOffers.get(pn).getOffer();
    		int acceptCO = brain.getNegotiator().considerOfferToMe(co);//evaluate it
    		if (acceptCO == StacRobotNegotiator.ACCEPT_OFFER) {
    			//if its an accept check it against our initial offer
                if (getMyOffer().getGetSet().getTotal() < co.getGiveSet().getTotal() || 
                        getMyOffer().getGiveSet().getTotal() > co.getGetSet().getTotal()) {
                    playerToTradeWith = pn;
                    acceptCounterOffer = true;
                }
            }
    	}
    	//NOTE: it seems that if there are multiple co, it chooses the last one it evaluates; 
    	//TODO:choose based on a metric?or choose first encountered to eliminate the creation of a boolean, an integer and the below check
    	if(acceptCounterOffer){
            
            ret.add(makeAcceptMessageWithExplicitAddressee(true, playerToTradeWith));
            setMyNegotiatedOffer(getPlayerResponse(playerToTradeWith).getOffer());
            boolean to[] = getMyNegotiatedOffer().getTo();
            Arrays.fill(to, false);
            // Restrict to - the trade we negotiated doesn't involve the other players
            //TODO: why are we restricting to us and not to the player who made the counter-offer?
            to[brain.getPlayerNumber()] = true; //playerToTradeWith would make more sense;
            tradeHandled(playerToTradeWith);
    	}
    	
    	//TODO: do we need to reject all other offers in case there are multiple ones? when we were complying with a block or handling 
    	// offers/ counter-offers we had to reject the others, so why not now?
    	
    	return ret;
    }
    
    /**
     * Iterates over the accepts and finalises a trade if our offer was accepted
     * @param tradeMade if a trade was made before checking the accepts
     * @return true if a trade was made with one of the acceptors, false otherwise
     */
    private void handleAccepts(Map<Integer, StacTradeMessage> accepts){
    	int playerToTradeWith = 256; //just some value
        //if we're making an intelligent decision about who to give the trade to, check whether more than one accepted our offer
    	if((accepts.size()>1) && (brain.isRobotType(StacRobotType.NP_GIVE_TRADE_TO_ACCEPTER_WITH_FEWEST_VP) ||
            brain.isRobotType(StacRobotType.NP_GIVE_TRADE_TO_ACCEPTER_WITH_FEWEST_RES) ||
            brain.isRobotType(StacRobotType.NP_GIVE_TRADE_TO_ACCEPTER_WITH_MOST_RES))){
            //in here choose the accepter if there are many and we have one special capability
            //trade with the player with the fewest VPs
            if (brain.isRobotType(StacRobotType.NP_GIVE_TRADE_TO_ACCEPTER_WITH_FEWEST_VP)) {
                int fewestVP = 20;
                for(Integer pn : accepts.keySet()){
                    if (pn != brain.getPlayerNumber()) { //skip ourselves
                        int playerVP = brain.getMemory().getPlayer(pn).getTotalVP();
                        if (playerVP < fewestVP) {
                            playerToTradeWith = pn;
                            fewestVP = playerVP;
                        }
                    }
                }
            }

            //trade with the player having the fewest resources
            if (brain.isRobotType(StacRobotType.NP_GIVE_TRADE_TO_ACCEPTER_WITH_FEWEST_RES)) {
                int fewestRes = 1000;
                for(Integer pn : accepts.keySet()){
                    if (pn != brain.getPlayerNumber()) { //skip ourselves 
                        int playerRes = brain.getMemory().getPlayer(pn).getResources().getTotal();
                        if (playerRes < fewestRes) {
                            playerToTradeWith = pn;
                            fewestRes = playerRes;
                        }
                    }
                }
            }

            //trade with the player having the most resources
            if (brain.isRobotType(StacRobotType.NP_GIVE_TRADE_TO_ACCEPTER_WITH_MOST_RES)) {
                int mostRes = 0;
                for(Integer pn : accepts.keySet()){
                    if (pn != brain.getPlayerNumber()) { //skip ourselves 
                        int playerRes = brain.getMemory().getPlayer(pn).getResources().getTotal();
                        if (playerRes > mostRes) {
                            playerToTradeWith = pn;
                            mostRes = playerRes;
                        }
                    }
                }
            }
    	
    	}else{//choose randomly as we don't have any special capabilities to reason over this decision
            int[] accArr = new int[accepts.size()];
            int idx = 0;
            for(Integer pn : accepts.keySet()){
                accArr[idx] = pn;
                idx++;
            }
            playerToTradeWith = accArr[random.nextInt(accArr.length)];
    	}
    	
    	//send offer to the accepter we decided upon above if our offer is not null
        // Sanity check:
        if (getMyOffer()==null) {
            brain.printMess("Null offer accepted (when considering who to give the trade to)");
            //print error and treat this as if we have executed an offer
        }else{
            // Narrow it to a single recipient and send it via the brain
            boolean offerTo[] = getMyOffer().getTo();
            for (int j=0; j<4; j++) {
                offerTo[j] = (j == playerToTradeWith);
            }
            brain.sendText(makeAcceptMessageWithExplicitAddressee(true, playerToTradeWith));
            brain.setWaitingForTradeMsg();
            brain.getMemory().setMyNegotiatedOffer(getMyOffer());
        }
    	//mark all accepts as handled
    	for(Integer pn : accepts.keySet()){
            tradeHandled(pn);
    	}
    }
    
    /**
     * Respond to offers or counter-offers. Both are handled in the same way.  Either handle as normal, or send 
     * a reject if we've already accepted someone else's deal.
     * @param ret the list containing the responses so this method can add to it
     * @return true if a trade was made, false otherwise
     */
    private boolean handleOffer(List<String> ret, boolean tradeMade, Map<Integer, StacTradeMessage> offers){
//            System.out.println("@@@ handle trade offer " + brain.getPlayerNumber() + "(" + brain.getPlayerName() + "): " + offers.toString());
    	boolean counterFound = false;

    	//first check for partial accept responses to my disjunctive offer and accept (one at random if multiple) 
    	//without considering the offer again via the Negotiator methods
    	StacTradeOffer myOffer = brain.getMemory().getMyOffer();
    	if(myOffer != null && (myOffer.hasDisjunctiveGetSet() || myOffer.hasDisjunctiveGiveSet())){
    		//create the sets of my partial sets
            List<SOCResourceSet> myGiveSets = new ArrayList<>();
            List<SOCResourceSet> myGetSets = new ArrayList<>();
    		if (myOffer.hasDisjunctiveGiveSet())
    		for (int rtype = SOCResourceConstants.CLAY; rtype <= SOCResourceConstants.WOOD; ++rtype) {
                if (myOffer.getGiveSet().getAmount(rtype) > 0) {
                    SOCResourceSet rs = new SOCResourceSet();
                    rs.setAmount(myOffer.getGiveSet().getAmount(rtype), rtype);
                    myGiveSets.add(rs);
                }
            }
    		if (myOffer.hasDisjunctiveGetSet())
            for (int rtype = SOCResourceConstants.CLAY; rtype <= SOCResourceConstants.WOOD; ++rtype) {
                if (myOffer.getGetSet().getAmount(rtype) > 0) {
                    SOCResourceSet rs = new SOCResourceSet();
                    rs.setAmount(myOffer.getGetSet().getAmount(rtype), rtype);
                    myGetSets.add(rs);
                }
            }
    		//check if all the partial responses are to my offer and are accepting one of my disjunctive offers, 
    		//otherwise let the following logic handle the trades as  		
    		boolean responseToMyOffer = true;
    		StacTradeOffer respOffer;
    		ArrayList<Integer> responsePNs = new ArrayList<Integer>();
    		for(Integer pn : offers.keySet()){
    			responsePNs.add(pn);
    			respOffer = offers.get(pn).getOffer();
    			//if(resp.isPartialOffer()){ //If the response was partial should add this, but it is all combined beforehand so we need
    			//to make sure that these are not disjunctive and that it matches the original offer instead of being a new offer
    			if(!respOffer.hasDisjunctiveGetSet() && !respOffer.hasDisjunctiveGiveSet()){
    				if(myOffer.hasDisjunctiveGetSet()){
    					if(!myOffer.getGiveSet().equals(respOffer.getGetSet())){
    						//this can happen when a player tries to make a different exchange to what was initially asked
    						responseToMyOffer = false;
            				break;
    					}
						if(!myGetSets.contains(respOffer.getGiveSet())){
							responseToMyOffer = false;
							break;
						}
    				}else if(myOffer.hasDisjunctiveGiveSet()){
    					if(!myOffer.getGetSet().equals(respOffer.getGiveSet())){
    						//this can happen when a player tries to make a different exchange to what was initially asked
    						responseToMyOffer = false;
            				break;
    					}
						if(!myGiveSets.contains(respOffer.getGetSet())){
							responseToMyOffer = false;
							break;
						}
    				}else{
    					//System.err.println("How is this possible??? my offer is no longer disjunctive");
    					responseToMyOffer = false;
        				break;
    				}
    				
    			}else{
					//let the below logic handle partial disjunctive responses
					responseToMyOffer = false;
    				break;
				}
    		}
    		
    		if(responseToMyOffer){
    			//choose at random whose offer to accept
    			tradeMade = responseToMyOffer;
    			Random r = new Random();
    			int offerAccepted = responsePNs.get(r.nextInt(responsePNs.size()));
    			for(Integer pn : offers.keySet()){
    				if(pn.intValue() == offerAccepted){
    					respOffer = offers.get(pn).getOffer();
    					SOCGame ga = brain.getMemory().getGame();
                        if(getPlayerNumber() == ga.getCurrentPlayerNumber())
                            brain.setWaitingForTradeMsg();
    	                ret.add(makeAcceptMessageWithExplicitAddressee(true, pn));
    	                setMyNegotiatedOffer(respOffer);
                        boolean to[];
                        to = getMyNegotiatedOffer().getTo(); // Restrict to - the trade we negotiated doesn't involve the other players
                        for (int j=0; j<ga.maxPlayers; j++) {
                            if (j!=getPlayerNumber()) {
                                to[j]=false;
                            }
                        }
    	                tradeHandled(pn);
    				}else{
    	                ret.add(makeRejectMessage(getPlayerNumber(), pn, StacTradeMessage.IMPLICIT_REJECT_NL_STRING));
    	                tradeHandled(pn);
    				}
    			}
    			return tradeMade;
    		}
    	}
    	
    	
    	for(Integer pn : offers.keySet()){
    	    		
            if (tradeMade) {
                //Already made a deal, so reject their offer
                //By making the NL string of the REJ message an implicit one, the player interface won't display it
                ret.add(makeRejectMessage(getPlayerNumber(), pn, StacTradeMessage.IMPLICIT_REJECT_NL_STRING));
                tradeHandled(pn);
            }
            else {
            	StacTradeMessage resp = offers.get(pn);
                
                //TODO: This functionality must go into the negotiator, because it decides whether to accept a partial/disjunctive offer and what to counteroffer
                //check whether we received an offer 
                // - with a disjunctive resource set
                // - an empty resource set (a partial offer)
                //this can only be an original offer at the moment
                //TODO: this can be a counteroffer now too, right?
                StacTradeOffer offer = resp.getOffer();
                if (offer.isPartialOffer() || offer.hasDisjunctiveGiveSet() || offer.hasDisjunctiveGetSet()) {
                    StacTradeOffer counteroffer = brain.getNegotiator().handleDisjunctiveOrPartialOffer(offer);
                    if (counteroffer != null) {
                        String tradeMessageType = ""; //could specify a FORCE ACCEPT here
                        Persuasion persuasiveArgument = new Persuasion();
                        
                        String nick = brain.getPlayerName();
                        String[] playerNames = brain.getMemory().getGame().getPlayerNames();
                        String offMsgText = tradeMessageOffToString(nick, playerNames, counteroffer, persuasiveArgument, "");
                        if (counteroffer.getGiveSet().equals(offer.getGetSet())) {
                            StacTradeOffer offerToBeVerbalisedWithEmptyGive = new StacTradeOffer(counteroffer.getGame(), counteroffer.getFrom(), counteroffer.getTo(), 
                                    SOCResourceSet.EMPTY_SET, false, counteroffer.getGetSet(), counteroffer.hasDisjunctiveGetSet());
                            offMsgText = tradeMessageOffToString(nick, playerNames, offerToBeVerbalisedWithEmptyGive, persuasiveArgument, "completingOfferImplicitGivables");
                        } else if (counteroffer.getGetSet().equals(offer.getGiveSet())) {
                            StacTradeOffer offerToBeVerbalisedWithEmptyGet = new StacTradeOffer(counteroffer.getGame(), counteroffer.getFrom(), counteroffer.getTo(), 
                                    counteroffer.getGiveSet(), counteroffer.hasDisjunctiveGiveSet(), SOCResourceSet.EMPTY_SET, false);
                            offMsgText = tradeMessageOffToString(nick, playerNames, offerToBeVerbalisedWithEmptyGet, persuasiveArgument, "completingOfferImplicitReceivables");
                        }
                        String result = makeTradeOffer(counteroffer, tradeMessageType, persuasiveArgument, offMsgText);
                        
                        ret.add(result);
                        return true;
                    } else {
                        //There's no counteroffer to a disjunctive or partial offer we want to make, so reject the trade offer
                        ret.add(makeRejectMessage(pn));
                        return false;
                    }
                }

                // Here, we are either handling a counter-offer or an original offer.
                //  As before, the handling is identical in either case
            	int negResp;
            	
            	if(brain.getNegotiator().getPersuasionRecipientType()){
                    negResp = brain.getNegotiator().considerOfferPERSUASION(resp.getOffer(), getPlayerNumber(), resp.getPersuasiveMove());            		
            	}
            	else{
                    negResp = brain.getNegotiator().considerOfferNONPERSUASION(resp.getOffer(), getPlayerNumber(), resp.isForced());
            	}
                    
                switch (negResp) {
                    case SOCRobotNegotiator.ACCEPT_OFFER:
                        // if we see multiple acceptable offers, we'll just accept the first for now, as with ACC handling
                        SOCGame ga = brain.getMemory().getGame();
                        if(getPlayerNumber() == ga.getCurrentPlayerNumber())
                            brain.setWaitingForTradeMsg();
                        ret.add(makeAcceptMessageWithExplicitAddressee(true, pn));
                        setMyNegotiatedOffer(resp.getOffer());
                        boolean to[];
                        to = getMyNegotiatedOffer().getTo(); // Restrict to - the trade we negotiated doesn't involve the other players
                        for (int j=0; j<ga.maxPlayers; j++) {
                            if (j!=getPlayerNumber()) {
                                to[j]=false;
                            }
                        }   tradeMade = true;
                        tradeHandled(pn);
                        break;
                    case SOCRobotNegotiator.REJECT_OFFER:
                        //ret list may not be empty here so create a new reference to the list containing the blocks and embargoes
                        List<String> blocksOrEmbargos = tryBlockOrEmbargo(offer, true);//we are a recipient here;
                        if (blocksOrEmbargos.isEmpty()) {
                            ret.add(makeRejectMessage(pn));
                        }else{
                            ret.addAll(blocksOrEmbargos);
                        }   tradeHandled(pn);
                        break;
                    default:
                        // We want to counter this offer - worry about that later (may not be an issue if we accept another in the meantime)
                        counterFound = true;
                        break;
                }
            }                
    	}
        
    	//NB: we need to use the responses from memory and not from the list offers, as the following loop will modify the memory contents
    	// and the list won't get updated with references to the new most likely null responses
    	// now look through the offer responses we have in memory, which are also the ones we want to counter and counter them
    	if (counterFound) {
            for (int i=0; i<getGame().maxPlayers; i++) {
            	StacTradeMessage resp = getPlayerResponse(i);
                if (resp!=null && resp.getOffer()!=null) {
                    if (tradeMade) {
                        ret.add(makeRejectMessage(i));
                        tradeHandled(i);
                    }
                    else {
                        // Make the counteroffer
                        // TODO: We will only counter one offer, due to how I've handled myOffer. 
                    	// That needs to be an array so we can have multiple ongoing negotiation dialogues.
                    	// NOTE: it will be the player with the lowest number if there are multiple offers we want to counter
                        StacTradeOffer counterOffer = brain.getNegotiator().makeCounterOffer(resp.getOffer());
                        if (counterOffer!=null) {
                            // NB: If we are countering a counter-offer, our offer may not actually be a counter -
                            //     ie it may not include player[i] as a recipient.
                            // Therefore, use the same logic as we do for making initial offers
                            // Agents will be free to interpret this as a counter-offer to them, even if it isn't really
                            
                            String tradeMessageType = "";
                            Persuasion persuasiveArgument = new Persuasion();
                            if(brain.getNegotiator().getPersuaderType()){
                                //Persuasion has a null persuasion for when no moves apply
                                persuasiveArgument = brain.getNegotiator().generatePersuasionMove(counterOffer);
                            }
                            else if (brain.getNegotiator().shouldOfferBeForced(counterOffer)) {
                                // Force-accept: If we have special persuasion abilities, decide whether to use them
                            	brain.getMemory().incNumForceAcceptMoves();
                            	tradeMessageType = StacTradeMessage.FORCE_ACCEPT;
//                              brain.printMess("Forced counter-offer: " + forced + " - moves left: " + brain.getMemory().getNumForceAcceptMovesLeft());
                            }

                            String nick = brain.getPlayerName();
                            String[] playerNames = brain.getMemory().getGame().getPlayerNames();
                            String offMsgText = tradeMessageOffToString(nick, playerNames, counterOffer, persuasiveArgument, "");
                            String result = makeTradeOffer(counterOffer, tradeMessageType, persuasiveArgument, offMsgText);
                            ret.add(result);
                            tradeMade = true;
                        	
                            // Special case for pseudo-counter described above 
                            if (counterOffer.getTo()[i]==false) {
                                // Treat this as a rejection for this agent
                                ret.add(makeRejectMessage(i));
                                tradeHandled(i);
                            }   
                        }
                        else {
                            ret.add(makeRejectMessage(i));
                            tradeHandled(i);
                        }
                    }
                }
            }
        }
        return tradeMade;
    }
    
    /**
     * If a trade hasn't been made yet and some of our opponents have rejected our offer, we can try and force a trade if we have the capabilities
     * @param ret the list containing the responses
     * @param tradeMade if a trade was made before
     * @param playersWhoRejected the list of players who have rejected our offer
     * @return true if the offer was made as a force accept offer
     */
    private boolean tryForceTrade(List<String> ret, boolean tradeMade, Map<Integer, StacTradeMessage> rejects){
        // TODO: the target is not chosen randomly but is always the player who sent the first reject
    	
    	for(Integer pn : rejects.keySet()){
    		
            //Null persuasion is equivalent to no force accept        	
            String tradeMessageType = StacTradeMessage.FORCE_ACCEPT;  
            Persuasion persuasiveArgument = new Persuasion();
            boolean shouldOfferBeForced;
            if(brain.getNegotiator().getPersuaderType()){
                persuasiveArgument = brain.getNegotiator().generatePersuasionMove(getMyOffer());
                shouldOfferBeForced = persuasiveArgument.getIdentifier()!=Persuasion.PersuasionIdentifiers.NullPersuasion;
            	tradeMessageType="";
            }
            else{
                shouldOfferBeForced = brain.getNegotiator().shouldOfferBeForced(getMyOffer(), new boolean[4]);
            }

        	
            //TODO Not sure what the purpose of these if statements are - they do the same thing
    		if (!brain.getMemory().wasLastOfferForced() && shouldOfferBeForced) {
    			brain.getMemory().incNumForceAcceptMoves();
                
                boolean[] newTo = new boolean[4];
                newTo[pn] = true;
                StacTradeOffer myOffer = getMyOffer();
                StacTradeOffer newOffer = new StacTradeOffer(myOffer.getGame(), myOffer.getFrom(), newTo, myOffer.getGiveSet(), myOffer.hasDisjunctiveGiveSet(), myOffer.getGetSet(), myOffer.hasDisjunctiveGetSet());
                
                String result = makeTradeOffer(newOffer, tradeMessageType, persuasiveArgument, "SOME NL STRING FROM tryForceTrade");
                ret.add(result);
                    
                // so, now we made a trade (actually: a force-accept trade offer)
                setMyOffer(newOffer);
                tradeMade = true;
                    
                // we found a force-accept 'victim', so we're done
                break;
    		}
        }
        return tradeMade;
    }    

    
    /**
     * Return the offer the Dialogue Manager has negotiated, if any.
     * @return SOCTradeOffer of the negotiated offer
     */
    protected SOCTradeOffer getMyNegotiatedOffer() {
        return brain.getMemory().getMyNegotiatedOffer();
    }
    
    /**
     * resets my negotiated offer by setting it to null 
     */
    protected void resetMyNegotiatedOffer() {
        brain.getMemory().resetMyNegotiatedOffer();
    }
    
    /**
     * sets my negotiated offer
     * @param offer the offer to set it to
     */
    protected void setMyNegotiatedOffer(SOCTradeOffer offer){
    	 brain.getMemory().setMyNegotiatedOffer(offer);
    }
    
    /**
     * Remember an offer we've made.  
     * Be sure to set this in playerOffers as well, since we won't process our own chat to do this.
     * @param offer  our offer
     */
    protected void setMyOffer(StacTradeOffer offer) {
        brain.getMemory().setMyOffer(offer);
    }
    
    /**
     * gets the offer from memory
     * @return my current proposed offer
     */
    protected StacTradeOffer getMyOffer(){
    	return brain.getMemory().getMyOffer();
    }
   
    /**
     * @param playerNum the player who made this offer
     * @param offer the new offer
     */
    protected void setPlayerOffer(int playerNum, SOCTradeOffer offer){
    	brain.getMemory().setPlayerOffer(playerNum, offer);
    }
    
    /**
     * @param playerNum
     * @return the offer made by the player
     */
    protected SOCTradeOffer getPlayerOffer (int playerNum){
    	return brain.getMemory().getPlayerOffer(playerNum);
    }
    
    /**
     * @param playerNum the player number corresponding to the player whose response we are interested in
     * @return the response of a specific player
     */
    protected StacTradeMessage getPlayerResponse(int playerNum){
    	return brain.getMemory().getPlayerResponse(playerNum);
    }
    
    /**
     * Reset the array for the trade responses.
     */
    protected void resetResponses() {
        // TODO: Unnecessary memory reallocation - just set to null
    	brain.getMemory().resetResponses();//setResponses(new StacTradeMessage[brain.getGame().maxPlayers]);
    }
    
    /**
     * set the response of a specific player
     * @param playerNum
     * @param msg
     */
    protected void setPlayerResponse(int playerNum, StacTradeMessage msg){
    	brain.getMemory().setPlayerResponse(playerNum, msg);
    }

}
