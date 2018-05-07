package soc.client;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import soc.debug.D;
import soc.dialogue.StacDialogueManager;
import soc.dialogue.StacSDRTParser;
import soc.dialogue.StacTradeMessage;
import soc.game.SOCGame;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCTradeOffer;
import soc.game.SOCPlayer;
import soc.game.StacTradeOffer;
import soc.message.SOCGameTextMsg;
import soc.robot.stac.Persuasion;
import soc.robot.stac.StacRobotDialogueManager;

/**
 * Handling chat trading with NL strings of the player client.
 * @author markus
 */
public class StacPlayerDialogueManager extends StacDialogueManager {

    /** My player client */
    private final SOCPlayerClient client;

    /** Game for this dialogue manager */
    private SOCGame game;
    
    /**
     * Map for keeping track of negotiations made via the chat for each game.
     */
    private StacTradeMessage[] tradeResponses;

    /**
     * Keep track of how many and what offers we have rejected if we are a human player and make sure to clear "Waiting for response." msg
     */
    private Set<Integer> numberOfRejects = new HashSet<Integer>();
    
    /**
     * Map for storing trade messages sent via the chat for each game.
     */
    private final ArrayList<StacTradeMessage> lastTradeMessages;

    private static final String SENDER_WILDCARD = "\\$se";
    private static final String RECEIVER_WILDCARD = "\\$re";
    private static final String RESOURCE_TYPE_WILDCARD = "\\$rs";
	public static final String NEGOTIATION_PREFIX = "/statement/negotiation/";

    /**
     * Default constructor.
     * @param client    my SOCPlayerClient
     * @param game      game in which this dialogue manager operates
     */
    public StacPlayerDialogueManager(SOCPlayerClient client, SOCGame game) {        
        this.client = client;
        this.game = game;
        this.sdrtParser = new StacSDRTParser(this);
        
        tradeResponses = new StacTradeMessage[game.maxPlayers];
        lastTradeMessages = new ArrayList<StacTradeMessage>();
        
        D.ebug_enable();
    }
    

    @Override
    public int getPlayerNumber() {
        SOCPlayerInterface pi = (SOCPlayerInterface) client.playerInterfaces.get(game.getName());
        int pn = pi.getClientPlayerNumber(); //this player's number (position on board)
        return pn;
    }
    
    @Override
    public SOCGame getGame() {
        return game;
    }

    /**
     * Set the game object. 
     * Intended only for use after loading a game.
     * @param g     the SOCGame object
     */
    protected void setGame(SOCGame g) {
        game = g;
    }
    
    /**
     * Take the actions when a new turn starts.
     */
    protected void handleStartTurn() {
        D.ebugPrintlnINFO("StacPlayerDialogueManager: Clearing all trade messages - (handleStartTurn)");
        clearTradeResponses();
        D.ebugPrintlnINFO("StacPlayerDialogueManager: Clearing record of previous trade messages - (handleStartTurn)");
        clearLastTradeMessages();
        if (sdrtParser != null) {
            D.ebugPrintlnINFO(sdrtParser.currentSDRTString());
            sdrtParser.sdrtNewConversation = true;
        }
    }

    /**
     * Get the tradeResponses array
     * @return StacTradeMessage[] with the current responses
     */
    protected StacTradeMessage[] getTradeResponses() {
        return tradeResponses;
    }

    /**
     * Clear the history of negotiations (in chat negotiations). 
     * This resets tradeResponses.
     */
    protected void clearTradeResponses() {
        tradeResponses = new StacTradeMessage[game.maxPlayers];
        numberOfRejects.clear();
    }

    /**
     * Store the trade response for the player in the game.
     * @param player    player number
     * @param tm        StacTradeMessage to store
     */
    protected void setTradeResponse(int player, StacTradeMessage tm) {
        tradeResponses[player] = tm;
        D.ebugPrintlnINFO("*** Updated trade response for player " + player + tradeResponsesString());
    }
    
    /**
     * Return a formatted string with the contents of the tradeResponses for this game.
     * @param gaName    name of the game
     * @return          formatted string for console output
     */
    private String tradeResponsesString() {
        String ret = "\n=== Trade responses in the player dialogue manager for game " + game.getName();
        if (tradeResponses == null || tradeResponses.length == 0) {
            return "\n    - Trade responses not initialised or empty";
        }
        
        String[] playernames = game.getPlayerNames();
        for (int i = 0; i < tradeResponses.length; i++) {
            String str = "";
            if (tradeResponses[i] == null) {
                str += "NULL";
            } else {
                str += "[";
                str += tradeResponses[i].isAccept() ? "A" : "";
                str += tradeResponses[i].isReject()? "R" : "";
                str += tradeResponses[i].isNoResponse()? "N" : "";
                str += tradeResponses[i].isForced()? "F" : "";
                str += tradeResponses[i].isBlock()? "B" : "";
                str += tradeResponses[i].isBlockComply()? "C" : "";
                str += "] " + tradeResponses[i].toString();
            }
            ret += "\n    " + i + " - " + playernames[i] + ": " + str;
        }
        return ret;
    }
   
    /**
     * Clear the record of the previous trade messages (in chat negotiations). 
     * This resets lastTradeMessages.
     */
    protected void clearLastTradeMessages() {
        lastTradeMessages.clear(); // = .put(gameName, new ArrayList<TradeMessage>());
//        lastTradeMessages.put(game.getName(), new ArrayList<TradeMessage>());
//        ArrayList ltm = lastTradeMessages.get(gameName);
//        if (ltm.size() > 20) {
//            lastTradeMessages.put(gameName, new ArrayList(ltm.subList(ltm.size()-20, ltm.size())));
//        }
    }

    protected void storeTradeMessage(StacTradeMessage tm) {
        lastTradeMessages.add(tm);
        System.err.println("Last trade messages: " + lastTradeMessages.toString());
    }

    /**
     * Take the appropriate actions when the dialogue manager receives a trade message
     * @param tm    the StacTradeMessage
     */
    protected void handleTradeMessage(StacTradeMessage tm) {
        storeTradeMessage(tm);
        sdrtParser.handleDialogueAct(tm);
        String sdrt = sdrtParser.currentSDRTString();
        String gv = sdrtParser.currentSDRTGraphvizString();
        client.logger.logDiscourseStructure(game.getName(), sdrt, gv);
    }
    
    /**
     * Get the last trade message, ignoring no-response messages
     */
     private StacTradeMessage lastTradeMessage() {
        ListIterator li = lastTradeMessages.listIterator(lastTradeMessages.size());
        while(li.hasPrevious()) {
            StacTradeMessage tm = (StacTradeMessage) li.previous();
            if (!tm.isNoResponse())
                return tm;
        }
        
        return null;
     }
     
    /**
     * Get last trade message sent by the player.
     * @param playerNumber  the player whose message we want
     * @return              the last trade message by the player
     */
    protected StacTradeMessage lastTradeMessageFromPlayer(int playerNumber) {
        ListIterator li = lastTradeMessages.listIterator(lastTradeMessages.size());
        while(li.hasPrevious()) {
            StacTradeMessage tm = (StacTradeMessage) li.previous();
            if (tm.getSenderInt() == playerNumber)
                return tm;
        }
        
        return null;
    }

    /**
     * Get last trade message I sent.
     * @return  my last trade message
     */
    protected StacTradeMessage lastTradeMessageFromMe() {
        return lastTradeMessageFromPlayer(getPlayerNumber());
    }

    /**
     * Get last trade offer any player made.
     * @return  my last trade offer
     */
    private SOCTradeOffer lastOffer() {
        ListIterator li = lastTradeMessages.listIterator(lastTradeMessages.size());
        while(li.hasPrevious()) {
            StacTradeMessage tm = (StacTradeMessage) li.previous();
            if (tm.getOffer() != null) {
                return tm.getOffer();
            }
        }
        return null;
    }
    
    /**
     * Get last trade offer the specified player made.
     * @param playerNumber  the player whose offer we're looking for
     * @return              last trade offer by the player
     */
    private SOCTradeOffer lastOfferFromPlayer(int playerNumber) {
        ListIterator li = lastTradeMessages.listIterator(lastTradeMessages.size());
        while(li.hasPrevious()) {
            StacTradeMessage tm = (StacTradeMessage) li.previous();
            if (tm.getOffer() != null) {
                SOCTradeOffer offer = tm.getOffer();
                if (offer.getFrom() == playerNumber) {
                    return offer;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Get last trade offer I made.
     * @return my last trade offer
     */
    private SOCTradeOffer lastOfferFromMe() {
        return lastOfferFromPlayer(getPlayerNumber());
    }
    
    /**
     * Get last trade offer that was made to me.
     * @param gaName    the name of the game
     * @return          last trade offer to me
     */
    private SOCTradeOffer lastOfferToMe() {
        int myPlayerNumber = getPlayerNumber();
        ListIterator li = lastTradeMessages.listIterator(lastTradeMessages.size());
        while(li.hasPrevious()) {
            StacTradeMessage tm = (StacTradeMessage) li.previous();
            if (tm.getOffer() != null) {
                SOCTradeOffer offer = tm.getOffer();
                if (offer.getTo()[myPlayerNumber]) {
                    return offer;
                }
            }
        }
        
        return null;
    }

    /**
     * Get the last trade offer from the interaction between me and the specified player.
     * Convert the offer, so that it is from the opponents 'perspective', i.e. if it's my offer, sender and receivers as well as give and get sets are inverted.
     * @param otherPlayerNumber the other player with which I am negotiating
     * @param gaName            the name of the game
     * @return                  the last trade offer the other player or I made from the opponent's 'perspective'
     */
    private StacTradeOffer lastOfferFromMeOrPlayer(int otherPlayerNumber) {
        int myPlayerNumber = getPlayerNumber();
        ListIterator li = lastTradeMessages.listIterator(lastTradeMessages.size());
        while(li.hasPrevious()) {
            StacTradeMessage tm = (StacTradeMessage) li.previous();
            if (tm.isAccept() && tm.getSenderInt() == otherPlayerNumber && tm.hasReceiver(myPlayerNumber)) // if the last message the other player sent us is an accept, this blocks finding a trade offer
                return null;
            StacTradeOffer offer = tm.getOffer();
            if (offer != null) {
                if (offer.getFrom() == myPlayerNumber && offer.getTo()[otherPlayerNumber]) {
                    return offer.invertedOffer(otherPlayerNumber);
                } else if (offer.getFrom() == otherPlayerNumber && offer.getTo()[myPlayerNumber]) {
                    return offer;
                }
            }
        }
        
        return null;
    }
    
    /**
     * This method is currently only intended to find an opponent offer if it is *the player's* turn!
     * @param myOffer  the partial offer for which we're trying to find an antecedent, so we can use information from that antecedent
     * @return         a HashMap with: 
     *                    "opponentOffer": the antecedent opponent offer
     *                    "to":            the inferred addressees of the offer; 
     *                    "message":       an optional message if no antecedent can be found
     */
    private HashMap<String, Object> findAntecedentOpponentOffer(SOCTradeOffer myOffer) {
        SOCTradeOffer opponentOffer = null;
        boolean[] to = new boolean[game.maxPlayers];
        String message = "";
        
        //first get the addressees of our offer and see whether they made an offer to us previously
        int numberOfAddressees = 0, numberOfAddresseesWithCurrentOffers = 0;
        int addresseePlayerNumber = 99, addresseeWithCurrentOfferPlayerNumber = 99; //used later when assembling the amendedOffer
        for (int p = 0; p < game.maxPlayers; p++) {
            if (p != getPlayerNumber()) {
                if (myOffer.getTo()[p]) {
                    addresseePlayerNumber = p;
                    numberOfAddressees++;
                }
                StacTradeOffer playerOffer = null;
                StacTradeMessage lastMsg = tradeResponses[p];
                //test if the last message sent by the player was a normal offer (what's stored in tradeResponses can be a blend of an offer and, eg, a reject)
                if (lastMsg != null) {
                    boolean specialMessage = lastMsg.isAccept() || lastMsg.isBlock() || lastMsg.isBlockComply() || lastMsg.isForced() || lastMsg.isNoResponse() || lastMsg.isReject();
                    if (!specialMessage) {
                        playerOffer = lastMsg.getOffer();
                    }
                }
                if (playerOffer != null) {
                    addresseeWithCurrentOfferPlayerNumber = p;
                    numberOfAddresseesWithCurrentOffers++;
                }
            }
        }
        
        /** Now try different methods for finding the antecedent. */
        
        //there's only one recipinent of the offer
        if (numberOfAddressees == 1) {
            to[addresseePlayerNumber] = true; //should be euqal to ourOffer.getTo()
            opponentOffer = lastOfferFromMeOrPlayer(addresseePlayerNumber);
        }
        
        //there's exactly one player for which we have a current offer on record, so he's the addresse of our partial (counter)offer
        if (opponentOffer == null && numberOfAddresseesWithCurrentOffers == 1) {
            to[addresseeWithCurrentOfferPlayerNumber] = true;
            opponentOffer = lastOfferFromMeOrPlayer(addresseeWithCurrentOfferPlayerNumber);
        }
        
        //if we have multiple addressees, so now we try some special cases
        if (opponentOffer == null && numberOfAddressees > 1 && numberOfAddresseesWithCurrentOffers > 1) {
//            boolean explicitAddressees = StacChatTradeMsgParser.hasExplicitAddressees(me);
//            if (explicitAddressees) {
//            }
            //if there is more than 1 addressee, compare the current offers between me and the addressees:
            //if all offers of the addressees have a common resource set for the one that I left empty, I can assemble a complete offer out of this
            boolean commonGiveSet = true, commonGetSet = true;
            for (int p = 0; p < game.maxPlayers; p++) {
                if (myOffer.getTo()[p]) {
                    StacTradeMessage tm = tradeResponses[p];
                    if (tm == null || tm.isAccept() || tm.isNoResponse())
                        continue;
                    SOCTradeOffer offer = tm.getOffer();
                    if (tm.isReject()) // this entry is not a counteroffer, but we still want to make this offer to somebody who rejected our offer
                        offer = offer.invertedOffer(Integer.parseInt(tm.getSender()));
                    if (offer != null) {
                        if (opponentOffer == null) {
                            opponentOffer = offer;
                            continue;
                        }
                        commonGiveSet = opponentOffer.getGiveSet().equals(offer.getGiveSet());
                        commonGetSet = opponentOffer.getGetSet().equals(offer.getGetSet());
                    } else {
                        commonGiveSet = false;
                        commonGetSet = false;
                    }    
                }
            }
            if ((commonGiveSet && myOffer.getGetSet().isEmptySet()) || 
                    (commonGetSet && myOffer.getGiveSet().isEmptySet())) {
                to = myOffer.getTo();
            } else {
                message = "*** Addressee unclear - more than 1 player made you an offer";
            }
        }
        
        //after all players rejected my offer, there is no current offer and the tradeResponses array is empty, 
        //so I'm testing a few special patterns
        //- my offer
        //- all reject
        //- my partial offer
        //TODO: This should be numberOfAddressees > 1
        if (opponentOffer == null && numberOfAddressees == 3 && numberOfAddresseesWithCurrentOffers == 0) {
            SOCTradeOffer lastOffer = lastOffer();
            int myPlayerNumber = getPlayerNumber();
            if (lastOffer != null && lastOffer.getFrom() == myPlayerNumber) {
                boolean patternViolated = false;
                for (int p = 0; p < game.maxPlayers; p++) {
                    if (p != myPlayerNumber) {
                        StacTradeMessage tm = lastTradeMessageFromPlayer(p);
                        if (tm != null && !tm.isReject()) {
                            patternViolated = true;
                            break;
                        }
                    }
                }
                if (!patternViolated) {
                    opponentOffer = lastOffer.invertedOffer(99);
                    to = lastOffer.getTo();
                }
            }
        }
        
        //- [0,0,0,0,0] - [1,0,0,0,0]                   'I want clay' 
        //- [1,0,0,0,0] - [0,0,0,2,0] (lastOfferToMe)   'I want 2 wheat in exchange' 
        //- [0,0,1,0,0] - [1,0,0,0,0] (lastOfferFromMe) 'I give 1 sheep' / 'I give 1'
        //- REJ                                         'No' 
        //- [1,0,0,0,0] - [0,0,0,2,0] (amendedOffer)    'I give 2 wheat' / '(OK) I give 2'
        if (opponentOffer == null && (numberOfAddressees == 3 || numberOfAddresseesWithCurrentOffers == 0)) {
            //Get the last offer I was involved in and update according to the parsed NL input
            if (lastTradeMessages.size() > 0) {
                StacTradeMessage lastTradeMessage = lastTradeMessage();
                //was the last trade message a REJ to me?
                if (lastTradeMessage != null && lastTradeMessage.isReject() && myOffer.getTo()[lastTradeMessage.getSenderInt()]) {
                    SOCTradeOffer lastOfferToMe = lastOfferToMe();
                    //is the sender of the reject the same player that sent me the last offer?
                    if (lastOfferToMe != null && lastOfferToMe.getFrom() == lastTradeMessage.getSenderInt()) {
                        SOCTradeOffer lastOfferFromMe = lastOfferFromMe();
                        //is the nonempty resource set of the last offers to and from me the same (crossing give/get)
                        if (lastOfferFromMe != null && 
                                ((lastOfferFromMe.getGiveSet().equals(lastOfferToMe.getGetSet()) && myOffer.getGiveSet().isEmptySet()) ||
                                (lastOfferFromMe.getGetSet().equals(lastOfferToMe.getGiveSet()) && myOffer.getGetSet().isEmptySet()))) {
                            to[lastOfferToMe.getFrom()] = true;
                            opponentOffer = lastOfferToMe;
                        }
                    }
                }
            }
        }
        
        //prepare the result and return it
        HashMap<String, Object> result = new HashMap();
        result.put("opponentOffer", opponentOffer);
        result.put("to", to);
        result.put("message", message);
        return result;
    }

    /**
     * Create a new trade offer for the case that the player only specified the quantity but not the type of the resource.
     * @param resSetWithNumberOfResources   SOCResourceSet with the quantity in the UNKNOWN slot
     * @param resSetWithTypeOfResource      SOCResourceSet with the type of resource (this is a nonzero resource, and it can only be one type of resource that is not zero)
     * @param offerToTreat                  the complete SOCTradeOffer, except that one of the resource sets only has UNKNOWN resources
     * @return                              the treated trade offer, which is now a complete offer
     */
    private StacTradeOffer treatOfferWithUnknownQuant(SOCResourceSet resSetWithNumberOfResources, SOCResourceSet resSetWithTypeOfResource, StacTradeOffer offerToTreat) {

        if (resSetWithNumberOfResources.getAmount(SOCResourceConstants.UNKNOWN) == 0) {
            D.ebugWARNING("treatOfferWithUnknownQuant() was given a resource set with 0 unknown resrouces");
            return null;
        }
        
        SOCResourceSet combinedResSet = SOCResourceSet.resourceSetFromResourceSetWithUnknown(resSetWithNumberOfResources, resSetWithTypeOfResource);
        if (combinedResSet == null) {
            SOCPlayerInterface pi = (SOCPlayerInterface) client.playerInterfaces.get(game.getName());
//            pi.print("*** Can't identify the type of resource in the trade offer");
            String sender = "";
            for (int p = 0; p < game.maxPlayers; p++) {
                if (offerToTreat.getTo()[p]) {
                    sender = game.getPlayerNames()[p];
                }
            }
            String text = sender + ": Sorry, I don't understand what type of resource you are offering!";
            pi.print(text);
            client.put(SOCGameTextMsg.toCmd(game.getName(), game.getPlayerNames()[getPlayerNumber()], StacTradeMessage.FAKE_CLARIFICATION + text), game.isPractice);
            return null;
        }
        
        boolean giveSetIsComplete = !offerToTreat.getGiveSet().equalsWithoutUnknowns(SOCResourceSet.EMPTY_SET);
        
        //check if the newly assembled resource set and the other resource set from the offer are disjoint
        //if not, use the empty set  instead
        SOCResourceSet completeResSet = giveSetIsComplete ? offerToTreat.getGiveSet() : offerToTreat.getGetSet();
        if (!completeResSet.disjoint(combinedResSet)) {
            D.ebugPrintlnINFO("The underspecified set of the NL offer and the complete set we were given are not disjoint. Substituting the empty set for the complete set instead.");
            completeResSet = SOCResourceSet.EMPTY_SET;
        }
        
        StacTradeOffer treatedOffer = new StacTradeOffer(offerToTreat.getGame(), offerToTreat.getFrom(), offerToTreat.getTo(), 
                (giveSetIsComplete ? completeResSet : combinedResSet), 
                (giveSetIsComplete ? offerToTreat.hasDisjunctiveGiveSet() : false),
                (giveSetIsComplete ? combinedResSet : completeResSet), 
                (giveSetIsComplete ? false : offerToTreat.hasDisjunctiveGetSet()));
        return treatedOffer;
    }
    
    /**
     * Method for completing a partial trade offer made by the player 
     * where the give or get sets are the empty set and the nonempty set may consists of UNKNOWN resources.
     * @param myOffer       partial offer made by the player
     * @param otherOffer    offer by another player used for completing the partial offer
     * @param gaName        name of the game
     * @param to            addressees for the completed offer
     * @return              the amended (completed) offer
     */
    private StacTradeOffer amendMyPartialOfferWithOffer(StacTradeOffer myOffer, StacTradeOffer otherOffer, boolean[] to) {
        StacTradeOffer amendedOffer = null;
        if (otherOffer != null) {
            if (myOffer.getGiveSet().isEmptySet()) {
                amendedOffer = new StacTradeOffer(game.getName(), myOffer.getFrom(), to, otherOffer.getGetSet(), otherOffer.hasDisjunctiveGetSet(), myOffer.getGetSet(), myOffer.hasDisjunctiveGetSet());
                //check if our offer did leave the get resource type unspecified (i.e. unknown resources will be > 0)
                if (amendedOffer.getGetSet().getAmount(SOCResourceConstants.UNKNOWN) > 0)
                    amendedOffer = treatOfferWithUnknownQuant(myOffer.getGetSet(), otherOffer.getGiveSet(), amendedOffer);
            } else if (myOffer.getGetSet().isEmptySet()) {
                amendedOffer = new StacTradeOffer(game.getName(), myOffer.getFrom(), to, myOffer.getGiveSet(), myOffer.hasDisjunctiveGiveSet(), otherOffer.getGiveSet(), otherOffer.hasDisjunctiveGiveSet());
                //check if our offer did leave the give resource type unspecified (i.e. unknown resources will be > 0)
                if (myOffer.getGiveSet().getAmount(SOCResourceConstants.UNKNOWN) > 0)
                    amendedOffer = treatOfferWithUnknownQuant(myOffer.getGiveSet(), otherOffer.getGetSet(), amendedOffer);
            } else {
                //this must check the give set first, because the treatOfferWithUnknownQuant() will treat the give set first
                if (myOffer.getGiveSet().getAmount(SOCResourceConstants.UNKNOWN) > 0)
                    amendedOffer = treatOfferWithUnknownQuant(myOffer.getGiveSet(), otherOffer.getGetSet(), myOffer);                
                if (myOffer.getGetSet().getAmount(SOCResourceConstants.UNKNOWN) > 0)
                    //did we already treat the give set above?
                    if (amendedOffer == null)
                        amendedOffer = treatOfferWithUnknownQuant(myOffer.getGetSet(), otherOffer.getGiveSet(), myOffer);
                    else
                        amendedOffer = treatOfferWithUnknownQuant(myOffer.getGetSet(), otherOffer.getGiveSet(), amendedOffer);
            }
            if (amendedOffer != null && !amendedOffer.getGiveSet().disjoint(amendedOffer.getGetSet())) {
                D.ebugWARNING("The resource sets of the amended offer are not disjoint; using the original partial offer:\n   My offer: " + myOffer.toString() + 
                        "\n   Other offer: " + otherOffer.toString() +
                        "\n   Amended offer: " + amendedOffer.toString());
                return myOffer;
            }
        } else {
            D.ebugWARNING("Can't amend an offer with a null offer - " + myOffer.toString());
        }
        
        return amendedOffer;
    }

    /**
     * Generate a fake response to a 
     * @param nlChatString
     * @return 
     */
    protected String getFallbackFakeClarification(String nlChatString) {
        //preparations - try to find out who this message was inteded for
        String ADDRESSEE = "([\\w]+)[,;:\\s].*";
        String normalisedMsg = nlChatString.toLowerCase();
        Pattern pattern = Pattern.compile(ADDRESSEE);
        Matcher matcher = pattern.matcher(normalisedMsg);
        boolean matches = matcher.matches(); //execute the match, so we can get the groups
        String fakeSpeaker = "";
        if (matches && matcher.groupCount() > 0) {
            String addressee = matcher.group(1);
            for (String name : game.getPlayerNames()) {
                if (name.toLowerCase().startsWith(addressee)) {
                    fakeSpeaker = name;
                    break;
                }
            }
        }
        if (fakeSpeaker.equals("")) {
            int p;
            do {                
                p = random.nextInt(game.maxPlayers);
            } while (p == getPlayerNumber());
            fakeSpeaker = game.getPlayerNames()[p];
        }

        //create the fake response
        HashMap<String,String> swapTable = new HashMap<>();
        swapTable.put(SENDER_WILDCARD, fakeSpeaker);
        swapTable.put(RECEIVER_WILDCARD, game.getPlayerNames()[getPlayerNumber()]);
        String fakeMsg = queryXMLFile(NEGOTIATION_PREFIX + "fakeOpponentResponse/generalFallback"  + xmlQueryMessageSuffix, swapTable);
        return fakeMsg;
    }
    
    /**
     * Interpreting a string typed in the chat by the player that matched the NL strings we can understand as trade messages.
     * This method handles all necessary actions like sending messages to the server. 
     * @param me    the message
     * @return      the String that is to be sent to the chat
     */
    protected String interpretNLChatInput(String me) {
        SOCPlayerInterface pi = (SOCPlayerInterface) client.playerInterfaces.get(game.getName());
        int pn = getPlayerNumber();
        
        //the actual parsing of the NL string
        StringWriter output = new StringWriter(); //this doesn't need closing according to the javadoc
        Map.Entry<StacTradeMessage,Integer> entry = StacChatTradeMsgParser.parseNLTradeMsg(me, game.getName(), game.getPlayerNames(), pn, output);
        
        if(!output.toString().equals(""))//NOTE: printing the empty string clears the waiting for message label prematurely
        	pi.print(output.toString());//in case we cannot parse the message, let the player know what the problem is
        
        //did we get a parse result?
        if (entry == null)
            return getFallbackFakeClarification(me); //"*** Can't parse the trade message";
        
        StacTradeMessage trdMsg = entry.getKey();
        Integer receiver = entry.getValue();
        
        //REJECT
        if (trdMsg.isReject()) {
            D.ebugPrintlnINFO("This is an NL reject");

            //find out whose offer we're rejecting
            int playerNumber = pi.getClientPlayerNumber();
            if (game.getCurrentPlayerNumber() != playerNumber) {
                //we're rejecting the offer from the current player (we can't trade with other players anyway)
                receiver = game.getCurrentPlayerNumber();
            } else {
                //if it's our turn, we need to do something more intelligent
                receiver = entry.getValue(); //this is the addressee from the parse
                //if there was no addressee specified in the NL string, receiver == 99
                //but we know who it is if only one opponent has made an offer or sent an accept
                if (receiver >= game.maxPlayers) {
                    boolean foundOpponentWithOffer = false;
                    for (int p = 0; p < game.maxPlayers; p++) {
                        StacTradeMessage tm = tradeResponses[p];
                        if (p != playerNumber && tm != null && (tm.getOffer() != null && !tm.isAccept() && !tm.isReject() && !tm.isNoResponse())) {
                            if (!foundOpponentWithOffer) {
                                foundOpponentWithOffer = true;
                                receiver = p;
                            } else {
                                receiver = 99; //reset to the 'unknown' value
                                break; //we're done; break out of the for loop
                            }
                        }
                    }
                }
            }
            if (!(receiver != null && receiver >= 0 && receiver <= 3)) {
                //return("*** Don't know whose offer to reject");
                int p;
                do {                
                    p = random.nextInt(game.maxPlayers);
                } while (p == getPlayerNumber());
                String fakeSpeaker = game.getPlayerNames()[p];
                return fakeSpeaker + ": Whose offer are you rejecting, " + game.getPlayerNames()[playerNumber];
            }
            
            //update the trade message with the correct receiver
            trdMsg = new StacTradeMessage(trdMsg.getSender(), receiver.toString(), false, true, false, false, null, false, trdMsg.getNLChatString());
        }

        //ACCEPT
        if (trdMsg.isAccept()) {
            D.ebugPrintlnINFO("This is an NL accept");

            //whose offer are we accepting?
            int currentPlayer = game.getCurrentPlayerNumber();
            int playerNumber = pi.getClientPlayerNumber();
            if (currentPlayer != playerNumber) {
                //we're accepting the offer from the current player (we can't trade with other players anyway)
                receiver = game.getCurrentPlayerNumber();
            } else {
                receiver = entry.getValue(); //this is the addressee from the parse
                //if there was no addressee specified in the NL string, receiver == 99
                //but we know who it is if only one opponent has made an offer or sent an accept
                if (receiver >= game.maxPlayers) {
                    boolean foundOpponentWithOfferOrAccept = false;
                    for (int p = 0; p < game.maxPlayers; p++) {
                        StacTradeMessage tm = tradeResponses[p];
                        if (p != playerNumber && tm != null && (tm.isAccept() || (tm.getOffer() != null && !tm.isReject() && !tm.isNoResponse()))) {
                            if (!foundOpponentWithOfferOrAccept) {
                                foundOpponentWithOfferOrAccept = true;
                                receiver = p;
                            } else {
                                receiver = 99; //reset to the 'unknown' value
                                break; //we're done; break out of the for loop
                            }
                        }
                    }
                }
            }
            if (!(receiver != null && receiver >= 0 && receiver < game.maxPlayers)) {
                //pick the first player, who sent a trade offer as sender of the fake clarification
                for (int p = 0; p < game.maxPlayers; p++) {
                    StacTradeMessage tm = tradeResponses[p];
                    if (tm.getOffer() != null) {
                        String name = game.getPlayerNames()[p];
                        return name + ": Whose offer are you accepting, " + game.getPlayerNames()[getPlayerNumber()] + "?";
                    }
                }
                return("*** Don't know whose offer to accept");
            }

            //accepts have extra restrictions 
            //Note that most of these cases would now be rejected by the server anyway, but we're trying to catch as much as possible before sending something to the server
            StacTradeMessage response = tradeResponses[receiver];
            if(response != null){
                StacTradeOffer offer = response.getOffer();
                if (offer != null) {
                    
                    //Accept of a partial offer
                    if (offer.isPartialOffer()) {
                        String[] playerNames = game.getPlayerNames();

                        HashMap<String,String> swapTable = new HashMap<>();
                        swapTable.put(SENDER_WILDCARD, playerNames[receiver]);
                        swapTable.put(RECEIVER_WILDCARD, playerNames[playerNumber]);
                        String emptySetAttValue = offer.getGiveSet().isEmptySet() ? "give" : "get";
                        String fakeMsg = queryXMLFile(NEGOTIATION_PREFIX
                                + "fakeOpponentResponse/incompleteOffer[@emptySet=\"" + emptySetAttValue + "\"]" 
                                + xmlQueryMessageSuffix, 
                                swapTable);
                        return(fakeMsg);
                        
                    //Accept of a disjunctive offer
                    } else if (offer.hasDisjunctiveGiveSet() || offer.hasDisjunctiveGetSet()) {
                        String[] playerNames = game.getPlayerNames();

                        HashMap<String,String> swapTable = new HashMap<>();
                        swapTable.put(SENDER_WILDCARD, playerNames[receiver]);
                        swapTable.put(RECEIVER_WILDCARD, playerNames[playerNumber]);
                        String disjSetAttValue = offer.hasDisjunctiveGiveSet() ? "give" : "get";
                        String fakeMsg = queryXMLFile(NEGOTIATION_PREFIX
                                + "fakeOpponentResponse/incompleteOffer[@disjunctiveSet=\"" + disjSetAttValue + "\"]" 
                                + xmlQueryMessageSuffix, 
                                swapTable);
                        return(fakeMsg);
                    }
                }
                
                if(response.isReject() || response.isNoResponse()){
                    //TODO: The offer can be accepted after a reject if there is an offer in the response entry
                    return("*** You cannot accept a reject or a no response");
                }
                if(!response.isAccept()){ 
                    //this is an accept to an offer
                    if(!response.getOffer().getTo()[pn]){
                        //if we are not a recipient do not allow the user to accept the offer
                        return("*** You cannot accept someone else's offer");
                    }
                    if(!game.getPlayer(pn).getResources().contains(response.getOffer().getGetSet())){
                        return("*** You can't accept an offer of what you don't have.");
                    }
                }else{ 
                    //this is the confirmation of the trade so we need to make sure we do not confirm a trade that we cannot do
                    if(!game.getPlayer(pn).getResources().contains(tradeResponses[pn].getOffer().getGiveSet())){
                        return("*** You can't confirm a trade offer of what you don't have.");
                    }
                }
            }

            //update the trade message with the correct receiver
            trdMsg = new StacTradeMessage(trdMsg.getSender(), receiver.toString(), true, false, false, false, null, false, trdMsg.getNLChatString());

            if (D.ebugIsEnabled()) {
                SOCTradeOffer offer = game.getPlayer(receiver).getCurrentOffer();
                if (offer != null) {
                    D.ebugPrintlnINFO("+++ Accepting offer: " + offer.toString());
                } else {
                    D.ebugPrintlnINFO("+++ There's no offer by " + game.getPlayer(receiver).getName() + " that I can accept");
                }
            }
            
        }

        //TRADE OFFER
        if (trdMsg.getOffer() != null) {
            D.ebugPrintlnINFO("This is an NL offer");
            
            //check if there is still a move-robber action pending; if so, inject a fake 'clarification'
            if (game.getGameState() == SOCGame.PLACING_ROBBER) {
                SOCTradeOffer o = trdMsg.getOffer();
                int fakeSender = 99;
                for (int p = 0; p < game.maxPlayers; p++)
                    if (o.getTo()[p]) {
                        fakeSender = p;
                        break;
                    }
                HashMap<String,String> swapTable = new HashMap<>();
                swapTable.put(SENDER_WILDCARD, game.getPlayerNames()[fakeSender]);
                swapTable.put(RECEIVER_WILDCARD, game.getPlayerNames()[getPlayerNumber()]);

                String fakeMsg = queryXMLFile(NEGOTIATION_PREFIX + "fakeOpponentResponse/moveRobber"  + xmlQueryMessageSuffix, swapTable);
                return(fakeMsg);
            }
            
            //check if this is a partial offer that may be a reply to/completion for an offer by another player
            //NB: one of the trade partners must be the current player
            //TODO: ANOTHER PLAYER MIGHT HAVE MADE HIS OFFER WITH A DISJUNCTIVE RESOURCE SET; THIS INFORMATION SHOULD BE PRESEVED IN THE TRADEMESSAGES -- INVESTIGATE!
            StacTradeOffer myOffer = trdMsg.getOffer();
            
            //does the message contain an explicit 'all' addressee?
            boolean explicitlyAddressedToAll = StacChatTradeMsgParser.hasExplicitAllAddressee(me);
            
            if (!explicitlyAddressedToAll && (myOffer.getGiveSet().equalsWithoutUnknowns(SOCResourceSet.EMPTY_SET) || myOffer.getGetSet().equalsWithoutUnknowns(SOCResourceSet.EMPTY_SET))) {
                int currentPlayerNumber = game.getCurrentPlayerNumber();

                //the full offer we're trying to compute based on the partial offer we just got from the NL input
                StacTradeOffer amendedOffer = null;
                boolean[] to = new boolean[game.maxPlayers]; //init all to false

                //if it's not my turn, interpret a partial offer as a counteroffer to the current offer of the current player (if there is one
                if (currentPlayerNumber != getPlayerNumber()) {
                    //check if the NL string specifies an addressee in the trade message other than the current player
                    //in that case, don't treat this message (this should hardly ever happen!)
                    boolean addresseeOtherThanCurrentPlayer = false;
                    int numberOfAddressees = 0;
                    for (int p = 0; p < game.maxPlayers; p++) {
                        if (myOffer.getTo()[p]) {
                            numberOfAddressees++;
                            if (p != currentPlayerNumber) {
                                addresseeOtherThanCurrentPlayer = true;
                            }
                        }
                    }                    
                    //check that the current player is one of the addressees or all opponents are addressees
                    if (!addresseeOtherThanCurrentPlayer || numberOfAddressees == game.maxPlayers-1) {
                        StacTradeOffer currentOffer = lastOfferFromMeOrPlayer(currentPlayerNumber);
                        to[currentPlayerNumber] = true;
                        amendedOffer = amendMyPartialOfferWithOffer(myOffer, currentOffer, to);
                    }
                }
                
                //if it is my turn, see if there is a counteroffer by one of the other players, and treat this partial offer as a response to that 
                //also check if the NL string specified an addressee, which helps disambiguating multiple opponent offers
                //otherwise, if there are no counteroffers (offers by other players), interpret this as a partial offer by me and continue as usual
                else {
                    HashMap<String, Object> antecedent = findAntecedentOpponentOffer(myOffer);
                    StacTradeOffer opponentOffer = (StacTradeOffer) antecedent.get("opponentOffer");
                    if (opponentOffer != null) {
                        to = (boolean[]) antecedent.get("to");
                        amendedOffer = amendMyPartialOfferWithOffer(myOffer, opponentOffer, to);
                    } else {
                        String message = (String) antecedent.get("message");
                        if (message.length() > 0)
                            return message;
                    }
                }
                
                //Create the trade message from the amended offer
                if (amendedOffer != null) {
                    String sender = Integer.toString(amendedOffer.getFrom());
                    String receivers = StacTradeMessage.getToAsString(to);
                    trdMsg = new StacTradeMessage(sender, receivers, amendedOffer, trdMsg.isForced(), trdMsg.getPersuasiveMove(), trdMsg.getNLChatString());
                }
                
            //it's a full offer or an offer explicity addressed to everybody (i.e., new partial offer)
            } else {
                int currentPlayerNumber = game.getCurrentPlayerNumber();
                int ourPlayerNumber = pi.getClientPlayerNumber();

                //if it's not our turn, then limit to the current player only (even if explicitlyAddressedToAll is true)
                //TODO: This does not work nicely when another player than the current player is addressed explicitly!
                if (currentPlayerNumber != ourPlayerNumber) {
                    boolean[] to = new boolean[game.maxPlayers];
                    to[currentPlayerNumber] = true;
                    StacTradeOffer amendedOffer = new StacTradeOffer(game.getName(), myOffer.getFrom(), to, myOffer.getGiveSet(), myOffer.hasDisjunctiveGiveSet(), myOffer.getGetSet(), myOffer.hasDisjunctiveGetSet());
                    String sender = Integer.toString(ourPlayerNumber);
                    String receivers = Integer.toString(currentPlayerNumber);
                    trdMsg = new StacTradeMessage(sender, receivers, amendedOffer, trdMsg.isForced(), trdMsg.getPersuasiveMove(), trdMsg.getNLChatString());
                }//otherwise previous logic handles if we specified the correct receiver or not
            }

            //inform the SDRT parser that a new negotiation is starting here
            if (explicitlyAddressedToAll || trdMsg.getOffer().isPartialOffer()) { //trdMsg must contain an offer at this point
                sdrtParser.sdrtNewConversation = true;
            }
        }
        
        if (receiver != null) {
            pi.getPlayerHandPanel(receiver).offer.setVisible(false);
        }

        //a last check that we are making a valid move
        SOCTradeOffer offer = trdMsg.getOffer();
        if (offer != null) {
            //preparations
            int fakeSender = 99;
            for (int p = 0; p < game.maxPlayers; p++)
                if (offer.getTo()[p]) {
                    fakeSender = p;
                    break;
                }
            HashMap<String,String> swapTable = new HashMap<>();
            swapTable.put(SENDER_WILDCARD, game.getPlayerNames()[fakeSender]);
            swapTable.put(RECEIVER_WILDCARD, game.getPlayerNames()[getPlayerNumber()]);
            
            //checks
            if (offer.getGiveSet().getAmount(SOCResourceConstants.UNKNOWN) > 0 && offer.getGetSet().getAmount(SOCResourceConstants.UNKNOWN) > 0) {
                String fakeMsg = queryXMLFile(NEGOTIATION_PREFIX + "fakeOpponentResponse/incompleteOffer[@unknownResources=\"both\"]"  + xmlQueryMessageSuffix, swapTable);
                return(fakeMsg);
            } else if (offer.getGiveSet().getAmount(SOCResourceConstants.UNKNOWN) > 0) {
                String fakeMsg = queryXMLFile(NEGOTIATION_PREFIX + "fakeOpponentResponse/incompleteOffer[@unknownResources=\"give\"]"  + xmlQueryMessageSuffix, swapTable);
                return(fakeMsg);
            } else if (offer.getGetSet().getAmount(SOCResourceConstants.UNKNOWN) > 0) {
                String fakeMsg = queryXMLFile(NEGOTIATION_PREFIX + "fakeOpponentResponse/incompleteOffer[@unknownResources=\"get\"]"  + xmlQueryMessageSuffix, swapTable);
                return(fakeMsg);
            } else if (!offer.getGiveSet().disjoint(offer.getGetSet())) {
                swapTable.put(RECEIVER_WILDCARD, game.getPlayerNames()[getPlayerNumber()]);
                swapTable.put(RESOURCE_TYPE_WILDCARD, SOCResourceConstants.resName(offer.getGiveSet().aCommonResource(offer.getGetSet())));
                String fakeMsg = queryXMLFile(NEGOTIATION_PREFIX + "fakeOpponentResponse/likeForLikeResources" + xmlQueryMessageSuffix, swapTable);
                return(fakeMsg);
            }
        }
        
        //keep the appropriate record for sending this trade message (store it and update SDRT)
        handleTradeMessage(trdMsg);

        //reset the status prompt
        pi.print("");            

        //if we pass the above checks we can send the message
        String trdString = trdMsg.toMessage();
        return trdString;
    }
    
    
    /**
     * This code moved here from the SOCPlayerClient, because it's more appropriate here.
     * @param mes 
     */
    protected void handleTradeGameTextMessage(SOCGameTextMsg mes) {
        String tradeString = StacDialogueManager.fromMessage(mes.getText());
        StacTradeMessage tm = StacTradeMessage.parse(tradeString);
        SOCPlayerInterface pi = (SOCPlayerInterface) client.playerInterfaces.get(game.getName());

        //we've already treated our own message when we sent it
        if (tm.getSenderInt() != getPlayerNumber()) {
            handleTradeMessage(tm);
        }

        StacTradeOffer offer = tm.getOffer();
        Persuasion persuasiveMove = tm.getPersuasiveMove();

        if (offer != null) {
            
            if (offer.getTo()[getPlayerNumber()]) {
                pi.setChatNeedsAttention(true, "Please respond to " + game.getPlayerNames()[offer.getFrom()] + "'s offer!"); //" made you an offer.");
            }

            //check if we sent the trade message; in that case it has already been written to the chat
            if (offer.getFrom() != getPlayerNumber()) {
                String offMsgText = tm.getNLChatString();
                String fromNick = game.getPlayerNames()[offer.getFrom()];
                pi.chatPrint(fromNick + ": " + offMsgText);
            }

            //display the offer in using the old trade interface but disable the buttons
            game.getPlayer(offer.getFrom()).setCurrentOffer(offer);
//            pi.getPlayerHandPanel(offer.getFrom()).updateCurrentOffer();
//            pi.getPlayerHandPanel(offer.getFrom()).offer.disableTradeButtons();
//            client.playSound("Voltage.wav", pi.clientHand.muteSound.getBoolValue());


            if (offer.getFrom() == game.getCurrentPlayerNumber()) {
                //treat counter-offers from the current player as new offers so we can clear the responses to the previous offer
                //clear the old trade bubbles when initiating negotiations for a new offer
                for (int i = 0; i < game.maxPlayers; i++)
                    if (i != game.getCurrentPlayerNumber())
                        pi.getPlayerHandPanel(i).offer.setVisible(false);

                //store this offer in the trade responses
                D.ebugPrintlnINFO("Player dialogue manager: Clearing all trade messages - (location 1)");
                clearTradeResponses();
            } else if (!offer.getTo()[game.getCurrentPlayerNumber()]) {
                D.ebugERROR("Error: offer not from or to the current player (StacPlayerDialogueManager)");
                return;
            }

            //store the offer in the tradeResponses
            setTradeResponse(offer.getFrom(), new StacTradeMessage(tm.getSender(), tm.getReceivers(), offer, tm.isForced(), persuasiveMove, tm.getNLChatString()));
            
            //when an offer is made disable the saving button
            if (pi.clientIsCurrentPlayer())
                pi.clientHand.save.setEnabled(false);

        } else {
            //ignore blocks/block comply and track noResponses/accepts and rejects;
            if(tm.isAccept() || tm.isReject() || tm.isNoResponse()){                                
                int to;
                try {
                    to = Integer.parseInt(tm.getReceivers().substring(0, 1)); //the receivers string should be only 1 char long anyway
                } catch (NumberFormatException e) {
                    //this shouldn't happen anyway as there are so many checks for the correct player number in place
                    e.printStackTrace();
                    pi.chatPrint(mes.getNickname() + ": " + tm.getNLChatString());
                    return;
                }

                SOCPlayer sender = game.getPlayer(mes.getNickname());

                if (tradeResponses[to] == null) {
                    if (Integer.parseInt(tm.getSender()) != getPlayerNumber())
                        pi.chatPrint(mes.getNickname() + ": " + tm.getNLChatString());
                    return; //this isn't an issue as no responses and rejects may be sent after a trade was executed, but we need to exit to avoid breaking the logic below
                }

                //link the responses to the offer these are a reply to then add to the response list
                if (to != game.getCurrentPlayerNumber() && sender.getPlayerNumber() != game.getCurrentPlayerNumber()) {
                    //ignore responses to other players but the current player as these are the rules of the game. send the message to the game as one of the players may be waiting for it;
                } else if(sender.getPlayerNumber() == game.getCurrentPlayerNumber() && tm.isReject()) {
                    //ignore rejects from the current player for now as this will interfere with the logic below
                } else {
                    //it can be a reject/acc or no-response to current player then check who is receiving this answer and combine with the offer in a new trade message
                    StacTradeMessage combinedTM = new StacTradeMessage(tm.getSender(), tm.getReceivers(),
                            tradeResponses[to].getOffer(), tradeResponses[to].isForced(), //tradeResponses[to].isDisjunctiveGiveSet(), tradeResponses[to].isDisjunctiveGetSet(),
                            tm.isAccept(), tm.isReject(), tm.isBlock(), tm.isBlockComply(), tm.getResources(), tm.isNoResponse(), 
                            tm.getNLChatString(), tm.getPersuasiveMove());
                    setTradeResponse(sender.getPlayerNumber(), combinedTM);
                }

                //check if everyone has replyed to offers
                boolean everyoneResponded = true;
                for(int i = 0; i < game.maxPlayers; i++){
                    if(tradeResponses[i] == null){
                        //need to handle the human special case of not responding to messages that are not intended for him
                        SOCPlayer pl = game.getPlayer(i);
                        if(!pl.isRobot()){
                            //we have to loop over all the offers and check if this player is a recipient of any
                            for(StacTradeMessage trd : tradeResponses){
                                if(trd != null){
                                    if(trd.getOffer().getTo()[i] && !trd.isAccept() && !trd.isReject() && !trd.isNoResponse()){
                                        everyoneResponded = false;//the human player is a recipient of an offer so there should have been a response from him
                                    }
                                }
                            }
                        }
                        else
                            everyoneResponded = false;
                    }
                }

                //If everyone replied check for 1 offer and no accepts and if so clear responses;
                //if a trade is executed these responses are cleared when receiving the trade confirmation from the server
                if(everyoneResponded){
                    D.ebugPrintlnINFO("Everyone responded");
                    int nAcc = 0, nOff = 0,
                        acceptingPlayer = 99, acceptedOfferPlayer = 99;
                    boolean[] offeringPlayers = new boolean[game.maxPlayers];
                    for(int p = 0; p < game.maxPlayers; p++){
                        if(tradeResponses[p]!=null){
                            if(tradeResponses[p].isAccept()){
                                nAcc++;
                                acceptingPlayer = p;
                                acceptedOfferPlayer = Integer.parseInt(tradeResponses[p].getReceivers()); //receiver of an ACC should only be one player number!
                            }else if(!tradeResponses[p].isReject() && !tradeResponses[p].isNoResponse()){
                                nOff++;
                                offeringPlayers[p] = true;
                            }
                        }
                    }
                    int myPlayerNumber = getPlayerNumber();
                    if(nAcc == 0 && nOff == 1){
                        //we only treat the case when everyone has rejected in order to clean our tracking
                        D.ebugPrintlnINFO("Player dialogue manager: Clearing all trade messages - (location 2)");
                        clearTradeResponses();
                        //clear the interface of the bubble as the offer was rejected
                        for(int i = 0; i < game.maxPlayers; i++)
                            pi.getPlayerHandPanel(i).offer.setVisible(false);
                        if (pi.clientIsCurrentPlayer())//if a trade was finished enable saving
                            pi.clientHand.save.setEnabled(true);
//                                    } else if (nAcc == 1 && nOff == 1 && acceptingPlayer != getPlayerNumber() && offeringPlayer == getPlayerNumber()) {
                    } else if (nAcc == 1 && acceptingPlayer != myPlayerNumber && acceptedOfferPlayer == myPlayerNumber && offeringPlayers[myPlayerNumber]) {
                        //we got exactly one accept for our own trade offer, so we automatically send the accept of the accept to execute the trade
                        SOCTradeOffer myOffer = tradeResponses[myPlayerNumber].getOffer();
                        //check that we actually made an offer to the accepting player
                        if (myOffer != null && myOffer.getTo()[acceptingPlayer]) {
                            //don't sent two automatic accepts in a row
                            StacTradeMessage myLastTradeMessage = null;
                                myLastTradeMessage = lastTradeMessageFromMe();
                            if (myLastTradeMessage != null && !myLastTradeMessage.isAccept() && !myLastTradeMessage.getNLChatString().equals(SOCPlayerClient.AUTOMATIC_ACCEPT_NL_STRING)) {
                                String acceptString = StacRobotDialogueManager.makeAcceptMessage(getPlayerNumber(), acceptingPlayer, SOCPlayerClient.AUTOMATIC_ACCEPT_NL_STRING);
                                client.sendText(game, acceptString);
                                for(int i = 0; i < game.maxPlayers; i++)
                                    pi.getPlayerHandPanel(i).offer.setVisible(false);
                                if (pi.clientIsCurrentPlayer())//if a trade was finished enable saving
                                    pi.clientHand.save.setEnabled(true);
                            } else {
                                D.ebugPrintlnINFO("Just sent an automatic accept of an accept - not sending another one");
                            }
                        }
                    }else if(offeringPlayers[myPlayerNumber]){
                    	if(myPlayerNumber == 0)
                    		System.out.println("I am the offering player");
                    	if(!game.getPlayer(myPlayerNumber).isRobot()){ 
                    		pi.setChatNeedsAttention(true, "Waiting for response.");
                        	if(myPlayerNumber == 0)
                        		System.out.println("I am not a robot");
                    		
                    		//check if all counter-offer(s) to this player have been rejected
                    		if(nOff > 1 && sender.getPlayerNumber() == myPlayerNumber && tm.isReject()){
                    			pi.setChatNeedsAttention(true, "Waiting for response.");//default
                            	if(myPlayerNumber == 0)
                            		System.out.println("Multiple c-offers, and I have just rejected one");
                    			
                    			//keep track of the rejects (these will be cleared when a new offer is on the table or negotiations are done)
                    			numberOfRejects.add(to);
                        		
                    			//check if this player has rejected all offers to them;
                        		boolean respondedToCounter = true;
                        		for(int i = 0; i < game.maxPlayers; i++){
                        			if(i != myPlayerNumber && tradeResponses[i].getOffer().getTo()[myPlayerNumber])
                        				if(!numberOfRejects.contains(i)){
                        					System.out.println("missing reject for: " + i);
                        					respondedToCounter = false;
                        				}
                        		}
                        		
                            	if(myPlayerNumber == 0)
                            		System.out.println("Have I responded to all: " + respondedToCounter);
                        		
                        		if(respondedToCounter){
                        			pi.print("");
                        			pi.setChatNeedsAttention(false);
                        		}
                        		
                    		}

                    		if(nAcc > 1 && sender.getPlayerNumber() == myPlayerNumber && tm.isReject()){
                    			pi.setChatNeedsAttention(true, "Waiting for response.");//default
                            	if(myPlayerNumber == 0)
                                	System.out.println("Multiple accepts, and I have just rejected one");
                    			
                    			//or if all accepts have been rejected
                    			//keep track of the rejects (these will be cleared when a new offer is on the table or negotiations are done)
                    			numberOfRejects.add(to);
                    			
                    			//check if this player has rejected all accepts to them;
                        		boolean respondedToCounter = true;
                        		for(int i = 0; i < game.maxPlayers; i++){
                        			//there is an assumption here that this player rejects accepts made to him/or not to someone else
                        			//TODO: this may be broken in human to human games 
                        			if(i != myPlayerNumber && tradeResponses[i].isAccept())
                        				if(!numberOfRejects.contains(i)){
                        					System.out.println("missing reject for: " + i);
                        					respondedToCounter = false;
                        				}
                        		}
                        		
                        		if(myPlayerNumber == 0)
                            		System.out.println("Have I responded to all: " + respondedToCounter);
                        		
                        		if(respondedToCounter){
                        			pi.print("");
                        			pi.setChatNeedsAttention(false);
                        		}
                        		
                    		} 
                    	}
                    }
                }

                //for accepts/rejects/no responses print a friendly string
                //but only if the message is not coming from us 
                //I.E. FOR THE MOMENT: if it is sent to us by somebody else, i.e. an agent)
//                                boolean addresseeIsRobot = game.getPlayer(to).isRobot();
                if (getPlayerNumber() == to && !tm.isNoResponse()) {
                    String chatMsg = (String)mes.getNickname() + ": " + tm.getNLChatString();
                    pi.chatPrint(chatMsg);
                }else if(!tm.isNoResponse() && getPlayerNumber() != sender.getPlayerNumber()){
                    //do not print no response messages or accepts/rejects from us to avoid duplication
                    String chatMsg = (String)mes.getNickname() + ": " + tm.getNLChatString();
                    pi.chatPrint(chatMsg);
                }
                //if in development mode, also print the no response messages
                if(client.devMode && tm.isNoResponse()){
                    String chatMsg = (String)mes.getNickname() + ": " + tm.getNLChatString();
                    pi.chatPrint(chatMsg);
                }
            }
            else
                pi.chatPrint(mes.getNickname() + ": " + mes.getText());
        }
    }
}
