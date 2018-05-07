package soc.dialogue;

import soc.game.SOCResourceSet;
import soc.game.StacTradeOffer;
import soc.robot.stac.Persuasion;

/**
 * Object to hold messages relating to trades:
 * - Notice of rejection
 * - Notice of acceptance
 * - Notice of block or block comply
 * - Notice of no response
 * - Notice of forced trade
 * - Resources set if it is a block
 * - (Counter) offer
 * @author Kevin O'Connor
 *
 * The strings now have the following format: 
 * TRD:{sender}:{recipients}:[FRC_ACC:]{offer|BLOCK#resourceString|BLOCK_COMPLY|NO_RESPONSE}|nlchatstring={NL string}[|persuasion={persuasion string}]
 * 
 * So, the parts are:
 * - TRD:
 * - {sender}:
 * - {recipients}:
 * - [FRC_ACC:]
 * - {offer|BLOCK#resourceString|BLOCK_COMPLY|NO_RESPONSE}
 *   + |nlchatstring={NL string}
 *   + [|persuasion={persuasion string}]
 */
public class StacTradeMessage extends StacDialogueAct {

    private final boolean isReject;
    private final boolean isAccept;
    private final boolean isForced;
    private final boolean isBlock;
    private final boolean isBlockComply;
    private final boolean isNoResponse;
    
    private final StacTradeOffer offer;
    private final SOCResourceSet resources; // a resource set, in particular one that is specified with a BLOCK message
    private final Persuasion persuasiveMove;

    public static final String TRADE = "TRD:";
    
    /**Could be used to reject a trade or a block. */
    public static final String REJECT = "REJ";
    /**Accept a trade. */
    public static final String ACCEPT = "ACC";

    /** Force accept of the trade; requires a compliant agent on the other side, eg an agent using NP_GULLIBLE */
    public static final String FORCE_ACCEPT = "FRC_ACC:";

    /** Disjunctive resource sets in the trade offer */
    public static final String DISJUNCTIVE_GIVE = "DISJ_GIVE:";
    public static final String DISJUNCTIVE_GET = "DISJ_GET:";
    public static final String DISJUNCTIVE_BOTH = "DISJ_BOTH:";

    /** Block or don't block a trade offer. */
    public static final String BLOCK = "BLOCK";
    /** Confirm that we comply with an issued block. */
    public static final String BLOCK_COMPLY = "BLOCK_COMPLY";
    
    /** Sending a no-response response. */
    public static final String NO_RESPONSE = "NO_RESPONSE";
    
    public static final String IMPLICIT_REJECT_NL_STRING = "Implicit reject message";
    
    /** Clarification messages that only the client receives for illegal offers/trades */
    public static final String FAKE_CLARIFICATION = "FAKE:";

    /**
     * Designated constructor.
     * @param sender                sender of the message
     * @param receivers             receivers of the message
     * @param offer                 a trade offer
     * @param forced                flag whether the offer is marked as 'forced'
     * @param accepted              flag: this is an accept message (ACC)
     * @param rejected              flag: this is a reject message (REJ)
     * @param blocked               flag: this is block message (BLOCK) (the receiver won't make any more offers asking for the blocked resource)
     * @param blockComply           flag: this is a comply block message (BLOCK_COMPLY) (I won't make any more offers asking for the blocked resource)
     * @param res                   SOCResourceSet with a resource (for BLOCK messages)
     * @param noResponse            flag: this is a no response message (the sender acknowledges a message by another player but does not make a contribution to the dialogue)
     * @param nlString              the message in natural language that can be printed to the player client's chat
     * @param pers                  Persuasion object for holding the persuasion argument
     */
    public StacTradeMessage(String sender, String receivers, 
                        StacTradeOffer offer, boolean forced, 
                        boolean accepted, boolean rejected, boolean blocked, boolean blockComply, SOCResourceSet res, boolean noResponse, 
                        String nlString, Persuasion pers) {
        super(sender, receivers, nlString);

        isAccept = accepted;
        isReject = rejected;
        isBlock = blocked;
        isBlockComply = blockComply;
        isForced = forced;
        isNoResponse = noResponse;

        this.offer = offer;
        resources = res;
        persuasiveMove = pers;
    }
    
    public StacTradeMessage(String sender, String receivers, boolean accepted, boolean rejected, boolean blocked, boolean blockComply, SOCResourceSet res, boolean noResponse, String nlString) {
        this(sender, receivers, null, false, accepted, rejected, blocked, blockComply, res, noResponse, nlString, new Persuasion());
    }

    public StacTradeMessage(String sender, String receivers, StacTradeOffer offer, boolean forced, String p_arg, String nlString) {
        this(sender, receivers, offer, forced, false, false, false, false, null, false, nlString, Persuasion.parsePersuasion(p_arg));
    }

    public StacTradeMessage(String sender, String receivers, StacTradeOffer offer, boolean forced, Persuasion p_arg, String nlString) {
        this(sender, receivers, offer, forced, false, false, false, false, null, false, nlString, p_arg);
    }
    
    /** Special constructor to replace the receivers of the message.
     * @param tm    TradeMessage to copy
     * @param rec   the new receivers
     */
    public StacTradeMessage(StacTradeMessage tm, String rec) {
        this(tm.sender, rec, tm.offer, tm.isForced, tm.isAccept, tm.isReject, tm.isBlock, tm.isBlockComply, tm.resources, tm.isNoResponse, tm.NLChatString, tm.persuasiveMove);
    }
    
    /**
     * Special case: for use on the server side for keeping track of what offers are accepted/rejected/no-response to.
     * NOTE: DO NOT use in other cases!
     * @param sender
     * @param receivers
     * @param accepted
     * @param rejected
     * @param noResponse
     * @param offer
     * @param forced
     * @param nlString
     */
    public StacTradeMessage(String sender, String receivers, boolean accepted, boolean rejected, boolean noResponse, StacTradeOffer offer, boolean forced, String nlString) {
        this(sender, receivers, offer, forced, accepted, rejected, forced, false, null, noResponse, nlString, new Persuasion()); //NullPersuasion as it is a special case
    }
    
    public boolean isReject() {
        return isReject;
    }

    public boolean isAccept() {
        return isAccept;
    }

    public boolean isForced() {
        return isForced;
    }
    
    public boolean isBlock() {
        return isBlock;
    }
    
    public boolean isBlockComply() {
        return isBlockComply;
    }
    
    public boolean isNoResponse() {
        return isNoResponse;
    }
    
    public StacTradeOffer getOffer() {
        return offer;
    }
    
    public SOCResourceSet getResources() {
        return resources;
    }

    public Persuasion getPersuasiveMove() { return persuasiveMove; }

    public boolean hasPersuasiveMove(){
        return getPersuasiveMove().getIdentifier() == Persuasion.PersuasionIdentifiers.NullPersuasion;
    }
    
    @Override
    /**
     * Get the string representation of the TradeMessage to be sent along the chat as a game text message.
     * General format:
     * TRD:{sender}:{recipients}:[FRC_ACC:]{offer|BLOCK#resourceString|BLOCK_COMPLY|NO_RESPONSE}|nlchatstring={NL string}[|persuasion={persuasion string}]
     * Examples:
     * - TRD:3:1,2:REJ|nlchatstring=No.
     * - TRD:1:0,2,3:game=Practice|from=1|to=true,false,true,true|give=clay=0|ore=0|sheep=1|wheat=0|wood=1|unknown=0|get=clay=0|ore=0|sheep=0|wheat=1|wood=0|unknown=0|nlchatstring=i give wd or sh for wh
     * - TRD:1:0,2,3:FRC_ACC:game=Practice|from=1|to=true,false,true,true|give=clay=0|ore=1|sheep=0|wheat=0|wood=0|unknown=0|get=clay=0|ore=0|sheep=0|wheat=1|wood=0|unknown=0|nlchatstring=You have to give me ore for wh
     */
    public String toString() {
        String nlChatInfix = "|nlchatstring=" + NLChatString;
        String prefix = TRADE + sender + ":" + receivers + ":";
        
        if (offer != null) {
            return (prefix + offer.toString() + nlChatInfix + persuasiveMove.toString());
        }
        else if (isAccept){
            return prefix + ACCEPT + nlChatInfix;
        } 
        else if (isReject) {
            return prefix + REJECT + nlChatInfix;
        } 
        else if (isBlock) {
            String resString = "";
            if (resources != null) {
                resString = resources.toString();
            }
            return prefix + BLOCK + "#" + resString + nlChatInfix;
        }
        else if (isBlockComply) {
            return prefix + BLOCK_COMPLY + nlChatInfix;
        }
        else if (isNoResponse) {
            return prefix + NO_RESPONSE + nlChatInfix;
        }
        return "unknown";
    }

    /**
     * Return this StacTradeMessage as a message string that can be sent along the channels.
     * @return string with substitutions for message
     */
    public String toMessage() {
        return StacDialogueManager.toMessage(this.toString());
    }

    /**
     * Parse a string into a trade message.
     * @param tr    the string containing a trade message
     * @return      StacTradeMessage object
     */
    public static StacTradeMessage parse(String tr) {
        String NLString = "Could not get NL string from parse!";

        //remove the TRD prefix if it is part of the string
        if (tr.startsWith(TRADE))
            tr = tr.substring(TRADE.length());
        
        //break up the tr string into its main components
        //Remove the argument sfor the NL chat string and persuasion before constructing the offer
        String[] brokenDownStatement = tr.split("(\\|nlchatstring=|\\|persuasion=)");
        String p_arg = "";
        
        switch (brokenDownStatement.length) {
            case 2:
                //message format: "offer|nlchatstring"
                NLString = brokenDownStatement[1];
                tr = brokenDownStatement[0];
                break;
            case 3:
                //message format: "offer|nlchatstring|persuasion"
                p_arg = brokenDownStatement[2];
                NLString = brokenDownStatement[1];
                tr = brokenDownStatement[0];
                break;
            default:
                String out = "This is a message without an NL chat string!\n";
                for (StackTraceElement trace : Thread.currentThread().getStackTrace()) {
                    out += "\t" + trace + "\n";
                }   System.err.println(out);
                break;
        }

        //get the sender & receivers
        //remove this part from the tr string
        String sender, receivers;
        String[] parts = tr.split(":");
        if (parts.length < 3) {
            String out = "I can't find sender and receivers for this TradeMessage!\n";
            for (StackTraceElement trace : Thread.currentThread().getStackTrace()) {
                out += "\t" + trace + "\n";
            }
            System.err.println(out);
        }
        sender = parts[0];
        receivers = parts[1];
        tr = tr.substring(tr.indexOf(":") + 1);
        tr = tr.substring(tr.indexOf(":") + 1);
        
        //parse the different message types
        if (tr.equals(ACCEPT)) {
            return new StacTradeMessage(sender, receivers, true, false, false, false, null, false, NLString);
        } else if (tr.equals(REJECT)) {
            return new StacTradeMessage(sender, receivers, false, true, false, false, null, false, NLString);
        } else if (tr.equals(BLOCK_COMPLY)) {
            return new StacTradeMessage(sender, receivers, false, false, false, true, null, false, NLString);
        } else if (tr.startsWith(BLOCK)) {
            SOCResourceSet res = new SOCResourceSet();
//            String[] parts = tr.split("#");
            parts = tr.split("#");
            // check if the blocked resources are specified and store them
            if (parts.length > 1) {
                res = SOCResourceSet.parse(parts[1]);
            }
            return new StacTradeMessage(sender, receivers, false, false, true, false, res, false, NLString);
        } else if (tr.equals(NO_RESPONSE)) {
            return new StacTradeMessage(sender, receivers, false, false, false, false, null, true, NLString);
        } else {  	        	
            //treatment of two part specifiers
            boolean frc = false;
            if (tr.startsWith(FORCE_ACCEPT)) {
                frc = true;
                tr = tr.substring(FORCE_ACCEPT.length());
            }
            StacTradeOffer offer = StacTradeOffer.parse(tr);
            return new StacTradeMessage(sender, receivers, offer, frc, p_arg, NLString);
        }
    }
    
    /**
     * Get the 'to' array like it is used in StacTradeOffer as a string in the format "1,3" that is used for the receivers attribute.
     * @param to    boolean array with the addressees of an offer/message
     * @return      String with the receivers of the offer/message in the format "1,3"
     */
    public static String getToAsString(boolean[] to) {
        String receivers = "";
        for (int p = 0; p < to.length; p++) {
            if (to[p])
                receivers += p + ",";
        }
        if (receivers.length() > 0)
            receivers = receivers.substring(0, receivers.length()-1);
        return receivers;
    }

}
