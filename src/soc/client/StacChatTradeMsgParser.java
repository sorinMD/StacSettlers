package soc.client;

import java.io.StringWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import soc.dialogue.StacDialogueManager;

//import soc.disableDebug.D;
import soc.debug.D;
import soc.dialogue.StacTradeMessage;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.StacTradeOffer;
import soc.message.SOCBankTrade;
import soc.message.SOCGameTextMsg;
import soc.robot.SOCPossiblePiece;
import soc.robot.stac.Persuasion;


public class StacChatTradeMsgParser {
	
    /**Reject a trade*/
    public static final String REJECT = "rej";
    /**Accept a trade. */
    public static final String ACCEPT = "acc";
    
    /**If offer is made to everyone */
    public static final String TO_ALL = "all";


    /** 
     * Needs to be initialized to read in the XML file only once.
     * This is all done in the StacRobotDialogueManager now.
     */
    public StacChatTradeMsgParser(){
//    	persuasionGen = new PersuasionGenerator("nlTemplates.xml");
//    	
//    	//This is done to make sure the values are consistent across the code
//        xmlQueryTradePrefix = StacRobotDialogueManager.xmlQueryTradePrefix;
////        negotiationPrefix = PersuasionGenerator.negotiationPrefix;
//        xmlQueryPersuasionPrefix = StacRobotDialogueManager.xmlQueryPersuasionPrefix;
//        xmlQueryMessageSuffix = StacRobotDialogueManager.xmlQueryMessageSuffix;
    }

    /**Map for linking player numbers to player colours; NOTE: only 4 player games for now*/
    public static final Map<String,Integer> players = new HashMap<String,Integer>();
    static{
    	players.put("blue", 0);
    	players.put("red", 1);
    	players.put("white", 2);
    	players.put("orange", 3);
    }
    
    /**
     * Translate a player colour into a key that can be used for accessing the "players" Map.
     * @param playerColour (partial) colour string specifying a player as defined in the formal language
     * @return String with a player's colour
     */
    private static String keyFromPlayerColour(String playerColour) {
        if (playerColour.matches(BLUE)) {
            return "blue";
        } else if (playerColour.matches(RED)) {
            return "red";
        } else if (playerColour.matches(WHITE)) {
            return "white";
        } else if (playerColour.matches(ORANGE)){
            return "orange";
        }
        return "";
    }

    /**
     * Return the player number for the colour. (The colour can be a partial term as defined in the formal language.)
     * @param col a player's colour
     * @return player number
     */
    private static Integer playerNumberForColour(String col) {
        return players.get(keyFromPlayerColour(col));
    }

    /**Map for linking resource names to integer defined in {@link SOCResourceConstants}*/
    public static final Map<String, Integer> resources = new HashMap<String,Integer>();
    static{
    	resources.put("clay",SOCResourceConstants.CLAY);
    	resources.put("ore",SOCResourceConstants.ORE);
    	resources.put("sheep",SOCResourceConstants.SHEEP);
    	resources.put("wheat",SOCResourceConstants.WHEAT);
    	resources.put("wood",SOCResourceConstants.WOOD);
    	resources.put("unknown",SOCResourceConstants.UNKNOWN);
    }
    
    /**
     * Get resource constant from the (partial) string of the formal language.
     * @param typeString    (partial) resource string as defined in the formal language
     * @return              value from SOCResourceConstants
     */
    private static Integer resourceTypeFromExpression(String typeString) {
        String key = "unknown";
        if (typeString.matches(CLAY)) {
            key = "clay";
        } else if (typeString.matches(ORE)) {
            key = "ore";
        } else if (typeString.matches(SHEEP)) {
            key = "sheep";
        } else if (typeString.matches(WHEAT)) {
            key = "wheat";
        } else if (typeString.matches(WOOD)) {
            key = "wood";
        }
        return resources.get(key);
    }

    ////Regular expressions patterns
    /**Trade with the bank */
    private static final String BANK = "(b|(ba)|(ban)|(bank))";
    /**Make an offer/counter-offer; also describes the to part of the message */
    private static final String TO = "(t|(tr)|(tra)|(trad)|(trade))";
    /**The offer part of the message containing the offered resources */
    private static final String OFFER = "(o|(of)|(off)|(offe)|(offer))";
    /**The receive part of the message containing the resources requested */
    private static final String RECEIVE = "(r|(re)|(rec)|(rece)|(recei)|(receiv)|(receive))";
    /**The persuasion made with the message*/
    private static final String PERSUASION = "(p|(pe)|(per)|(pers)|(persuasion))";
    /**Pattern for matching the correct format of an accept */
    public static final String ACCEPT_PATTERN = "(?i)" + ACCEPT + "[:]((\\d)|([A-Za-z]{1,6}))";
    /**Pattern for matching the correct format of a reject */
    public static final String REJECT_PATTERN = "(?i)" + REJECT + "[:]((\\d)|([A-Za-z]{1,6}))";
    /**Pattern for matching the correct format of the array describing to whom this offer is intended */
    private static final String TO_ARRAY_PATERN = "(((\\d[,]){1,2}\\d?[,]?)|(all)|(\\d)|(([A-Za-z]{1,6}[,]){1,2}([A-Za-z]{1,6})?[,]?)|([A-Za-z]{1,6}))";
    /**Pattern for matching the correct format of the array describing the resource set*/
    private static final String RSS_ARRAY_PATERN = "(((\\d+[A-Za-z]{1,5}[,]){1,3}(\\d+[A-Za-z]{1,5}[,]?)?)|(\\d+[A-Za-z]{1,5}))";
    /**Pattern for any alphanumeric String as I want to keep persuasive moves fairly unrestricted here**/
    private static final String PER_ARRAY_PATTERN = "[a-zA-Z_]*([\\^]([a-zA-Z]*[,])+[a-zA-Z]*)?([\\^][a-zA-Z]*[,]([a-zA-Z]*[=][a-zA-Z0-9]*)+)?";
    /**Allows trailing comas(e.g. t:1,2,;o:....). Matches against both full and partial offer structure. TODO:Add ? after each block if we allow partial offers and uncomment the code in {@link StacChatTradeMsgParser#isTradeMsgFormat(String)}*/
    public static final String OFFER_PATTERN = "(?i)"+ TO +"[:]" + TO_ARRAY_PATERN + "([;]" + OFFER + "[:]" + RSS_ARRAY_PATERN +")([;]" + RECEIVE + "[:]" + RSS_ARRAY_PATERN +")([;]" + PERSUASION + "[:]"+PER_ARRAY_PATTERN+")?[;]?";   	
    /**Matches bank trades, which must be fully specified */
    public static final String BANK_TRADE_PATTERN = "(?i)"+ BANK +"[:]" + OFFER + "[:]" + RSS_ARRAY_PATERN +"[;]" + RECEIVE + "[:]" + RSS_ARRAY_PATERN + "[;]?";
    
    ////patterns for player colours
    private static final String BLUE  = "(b|(bl)|(blu)|(blue))";
    private static final String RED  = "(r|(re)|(red))";
    private static final String ORANGE  = "(o|(or)|(ora)|(oran)|(orang)|(orange))";
    private static final String WHITE  = "(w|(wh)|(whi)|(whit)|(white))";

    ////patterns for resources
    private static final String CLAY  = "(c|(cl)|(cla)|(clay))";
    private static final String ORE  = "(o|(or)|(ore))";
    private static final String SHEEP  = "(s|(sh)|(she)|(shee)|(sheep))";
    private static final String WHEAT  = "((wh)|(whe)|(whea)|(wheat))";
    private static final String WOOD  = "((wo)|(woo)|(wood)|(wd))";
    ////Regular expressions patterns
    
    /**
     * Examples:
     * acc -- Accept
     * 
     * rej -- Reject
     * 
     * t:0,1,2;o:1wd;r:1sh OR t:all;o:1wd;r:1sh OR t:blue,red,white;o:1wood;r:1sheep
     * -- current player (in this case pn=3) offers (or counter-offers with) 1 wood for one sheep to all players
     * 
     * t:orange;o:1wd,1sh;r:1ore -- current player offers 1 wood and 1 sheep for one ore to the orange player;
     * 
     * t:0;o:1cl --partial offer from current player to player 0(or blue) of 1 clay;(could mean: "What do you have to offer for one clay?")
     * 
     * t:all;r:1or --partial offer from current player to everyone requesting 1 ore;(could mean: "I need ore")
     * 
     * @param msg the message typed by a human player
     * @param gameName the name of the game needed for creating the SOCTradeOffer object
     * @param ourPn the player number of the player sending the messsage
     * @param output a buffer containing any error messages to be printed out in the game message interface
     * @return a map entry containing the tradeMessage linked to the player number (or null if a normal offer) to whom the message is intended (in case of an acc/rej) or null if the string cannot be parsed
     */
    public static Map.Entry<StacTradeMessage,Integer> parseTradeMsg(String msg, String gameName, int ourPn, StringWriter output) {        
    	Map.Entry<StacTradeMessage,Integer> res; 
    	StacTradeMessage trdMsg;
    	Integer receiver = null;
        Persuasion persuasion = new Persuasion(); 
    	if(msg.matches(ACCEPT_PATTERN)){
            String[] pieces = msg.split(":");
            if(pieces[1].matches("\\d")){//if a digit just make a new Integer(s)
                receiver = new Integer(pieces[1]);
            }else{//else match if its blue,red,orange,white and get the corresponding integer from the map;
                receiver = playerNumberForColour(pieces[1]); //if incorrect this will return null and the error will be reported later
            }
            if(!(receiver != null && receiver >= 0 && receiver <= 3)){
                if(pieces[1].equals(TO_ALL))
                    output.write("Can only use 'all' in a new offer");
                else
                    output.write("Incorrect player number or colour: " + pieces[1]);
                return null;
            }
            String sender = Integer.toString(ourPn);
            String receivers = Integer.toString(receiver);
            trdMsg = new StacTradeMessage(sender, receivers, true, false, false, false, null, false, msg);
    	}else if(msg.matches(REJECT_PATTERN)){
            String[] pieces = msg.split(":");
            if(pieces[1].matches("\\d")){//if a digit just make a new Integer(s)
                receiver = new Integer(pieces[1]);
            }else{//else match if its blue,red,orange,white and get the corresponding integer from the map;
//                String key = keyFromPlayerColour(pieces[1]);
                receiver = playerNumberForColour(pieces[1]); //if incorrect this will return null and the error will be reported later
            }
            if(!(receiver != null && receiver >= 0 && receiver <= 3)){
                if(pieces[1].equals(TO_ALL))
                    output.write("Can only use 'all' in a new offer");
                else
                    output.write("Incorrect player number or colour: " + pieces[1]);
                return null;
            }
            String sender = Integer.toString(ourPn);
            String receivers = Integer.toString(receiver);
            trdMsg = new StacTradeMessage(sender, receivers, false, true, false, false, null, false, msg);
    	}else{
            //construct the offer
            boolean[] to = {false,false,false,false};//new boolean[4];
            SOCResourceSet give = new SOCResourceSet();
            SOCResourceSet get = new SOCResourceSet();
            //the message should contain ";"
            String[] parts = msg.split(";");
            //each part should be formed of two separated by ":";
            for(String p : parts){
                String[] pieces = p.split(":");
                //three options
                if (pieces[0].matches(TO)){
                    //generate the to array
                    String[] toArray;
                    if(pieces[1].contains(",")){//in case there are multiple recipients
                        toArray = pieces[1].split(",");

                    }else{//only one recipient
                        toArray = new String[1];
                        if(pieces[1].equals(TO_ALL)){
                            //we have to handle the all case here as we want to avoid offers like t:blue,all,red;
                            Arrays.fill(to, true);
                            to[ourPn] = false;//we are not offering to ourselves
                            toArray[0] = null;//set to null so we won't output an error in the next for loop
                        }else{							
                            toArray[0] = pieces[1];
                        }
                    }
                    for(String s : toArray){
                        if(s!=null && s.length()>0){//ignore trailing ","
                            Integer pn;
                            //in here match to is either a digit or a word;
                            if(s.matches("\\d")){//if a digit just make a new Integer(s)
                                pn = new Integer(s);
                            }else{//else match if its blue,red,orange,white and get the corresponding integer from the map;
                                pn = playerNumberForColour(pieces[1]); //if incorrect this will return null and the error will be reported later
                            }
                            if(pn != null && pn >= 0 && pn <= 3)
                                to[pn] = true;
                            else{
                                if(s.equals(TO_ALL))
                                    output.write("Can only use 'all' on its own");
                                else
                                    output.write("Incorrect player number or colour: " + s);
                                return null;
                            }
                        }
                    }
                    if(to[ourPn]){
                        output.write("Cannot offer the trade to yourself");//in case the initiator meant a different player
                        return null;
                    }

                }else if(pieces[0].matches(OFFER) || pieces[0].matches(RECEIVE)){
                    //generate the give array
                    String[] offerParts;
                    if(pieces[1].contains(",")){//in case there are multiple resource types offered
                        offerParts = pieces[1].split(",");
                    }
                    else{//only one resource type offered
                        offerParts = new String[1];
                        offerParts[0] = pieces[1];
                    }
                    //iterate over them and add them to the resource set
                    for(String s : offerParts){
                        if(s != null && s.length()>0){//ignore trailing ","
                            String secondChar = "" + s.charAt(1);
                            int quantity;
                            String typeString;
                            if(secondChar.matches("\\d")){
                                //this doesn't really happen but we allow selling/requesting more than 10 resources
                                quantity = Integer.parseInt(s.substring(0, 2));
                                typeString = s.substring(2);
                            }else{
                                quantity = Integer.parseInt(s.substring(0, 1));
                                typeString = s.substring(1);
                            }
                            Integer type;
                            //we match against the 5 types and the first one to match gets the corresponding type from the map
                            type = resourceTypeFromExpression(typeString);

                            //check if the user has spelled the rss type correctly
                            if(type == null){
                                output.write("Unknown resource type: " + typeString);
                                return null;
                            }
                            if(quantity<0){
                                //this won't actually happen as the regex doesn't match the '-' symbol
                                output.write("Cannot offer or ask for a negative amount: " + quantity + " of type " +  typeString);
                                return null;
                            }
								
                            //add to the give or get set 
                            if(pieces[0].matches(OFFER))
                                give.add(quantity, type);
                            else
                                get.add(quantity, type);
                        }
                    }

                }else if(pieces[0].matches(PERSUASION)){
                    //Format the persuasion and generate the Object
                    //TODO There is no real input validation on this -- should be added
                    persuasion = Persuasion.parsePersuasion(Persuasion.formatPersuasion(pieces[1]));
                }

                else{
                    output.write("Unknown message part: " + pieces[0]);
                    return null;//stop parsing of message if an unknown field was encountered
                }
    		
            }
            //check if we are offering and asking for the same resource type
            for (int i = SOCResourceConstants.CLAY;i <= SOCResourceConstants.WOOD; i++){
            	if(get.getAmount(i)>0 && give.getAmount(i)>0){
                    output.write("Cannot offer and ask for the same resource type: " + SOCResourceConstants.resName(i));
                    return null;
            	}
            }
            String sender = Integer.toString(ourPn);
            String receivers = StacTradeMessage.getToAsString(to);
            trdMsg = new StacTradeMessage(sender, receivers, new StacTradeOffer(gameName, ourPn, to, give, false, get, false), false, persuasion, msg);
    	}

    	res = new AbstractMap.SimpleEntry<StacTradeMessage, Integer>(trdMsg, receiver);
    	return res;
    }
    
    /**
     * To be called before trying to call parseMsg in order to check for the correct structure of the msg.
     * @param msg the message to be matched against the patterns
     * @return true if it matches one of the patterns, false otherwise
     */
    public static boolean isTradeMsgFormat(String msg){
    	if(msg.matches(ACCEPT_PATTERN))
    		return true;
    	if(msg.matches(REJECT_PATTERN))
    		return true;
    	if(msg.matches(OFFER_PATTERN)){
    		//before returning true make sure it has at least two parts (so at least one of the set of resources) to be a legal full/partial offer
//    		String[] parts = msg.split(";");
//    		if(parts.length <= 1)
//    			return false;
//    		if(parts[1].equals(null))
//    			return false;
//    		if(parts[1].length() < 3) //smallest a part can be: o:1c or r:1c
//    			return false;
    		//TODO: uncomment when we are allowing partial offers again;
    		return true;
    	}
    	return false;
    }
    
    public static boolean isBankTradeMsgFormat(String msg){
    	return (msg.matches(BANK_TRADE_PATTERN));
    }

    /**
     * Parses a message following the formal trade language rules into a bank trade message
     * @param msg the message to be parsed
     * @param gameName the name of the game this message will be sent to
     * @param output a buffer containing any error messages to be printed out in the game message interface
     * @return null or the SOCBankTrade object to be sent to the server
     */
    public static SOCBankTrade parseBankTradeMsg(String msg, String gameName, StringWriter output){
    	//cut the beginning b: or ba: or ban: or bank:
    	int index = msg.indexOf(":");
    	msg = msg.substring(index+1);
        SOCResourceSet give = new SOCResourceSet();
        SOCResourceSet get = new SOCResourceSet();
    	//the message should contain ";"
        String[] parts = msg.split(";");
        //each part should be formed of two separated by ":";
        for(String p : parts){
            String[] pieces = p.split(":");
            //three options
            if (pieces[0].matches(TO)){
                output.write("Cannot offer a bank trade to someone");
                return null;

            }else if(pieces[0].matches(OFFER)||pieces[0].matches(RECEIVE)){
                //generate the give array
                String[] offerParts;
                if(pieces[1].contains(",")){//in case there are multiple resource types offered
                    offerParts = pieces[1].split(",");
                }
                else{//only one resource type offered
                    offerParts = new String[1];
                    offerParts[0] = pieces[1];
                }
                //iterate over them and add them to the resource set
                for(String s : offerParts){
                    if(s != null && s.length()>0){//ignore trailing ","
                        String secondChar = "" + s.charAt(1);
                        int quantity;
                        String typeString;
                        if(secondChar.matches("\\d")){
                            //this doesn't really happen but we allow selling/requesting more than 10 resources
                            quantity = Integer.parseInt(s.substring(0, 2));
                            typeString = s.substring(2);
                        }else{
                            quantity = Integer.parseInt(s.substring(0, 1));
                            typeString = s.substring(1);
                        }
                        Integer type;
                        //we match against the 5 types and the first one to match gets the corresponding type from the map
                        type = resourceTypeFromExpression(typeString);

                        //check if the user has spelled the rss type correctly
                        if(type == null){
                            output.write("Unknown resource type: " + typeString);
                            return null;
                        }
                        if(quantity<0){
                            //this won't actually happen as the regex doesn't match the '-' symbol
                            output.write("Cannot offer or ask for a negative amount: " + quantity + " of type " +  typeString);
                            return null;
                        }

                        //add to the give or get set 
                        if(pieces[0].matches(OFFER))
                            give.add(quantity, type);
                        else
                            get.add(quantity, type);
                    }
                }

            }else{
                output.write("Unknown message part: " + pieces[0]);
                return null;//stop parsing of message if an unknown field was encountered
            }

        }
		//check if we are offering and asking for the same resource type
        for (int i = SOCResourceConstants.CLAY;i <= SOCResourceConstants.WOOD; i++){
            if(get.getAmount(i)>0 && give.getAmount(i)>0){
                output.write("Cannot offer and ask for the same resource type: " + SOCResourceConstants.resName(i));
                return null;
            }
        }
    	//build the bank msg and return it
        return new SOCBankTrade(gameName, give, get);
    	
    }
    
    /**
     * Return the number of the player with pname.
     * Also matches pname as a prefix against the player names.
     * Note: If multiple players have names that start with the same prefix, the first matching player number is returned.
     * @param pname the string to be matched against player names
     * @param playerNames array of the names of the players in the game
     * @return the player number of 99 if no match could be found
     */
    private static int playerNumberForName(String pname, String[] playerNames) {
        String p = pname.toLowerCase();
        for (int i=0; i<=3; i++) {
            String n =  playerNames[i].toLowerCase();
            if (p.length() > 0 && n.startsWith(p)) {
                return i;
            }
        }
        return 99;
    }

    //String for regular expressions to be used for the NL parsing
    public static final String NL_ADDRESSEES = "([\\w,;:\\s]*)";
    public static final String NL_ALL_ADDRESSEES = "(?i)(all|anybody|anyone|everyone|everybody)";
    
//    public static final String NL_ACCEPT_PATTERN = "(?i)" + "ok\\s*(\\w*)?[,.;:'!?\"\\s]*";
//    public static final String NL_REJECT_PATTERN = "(?i)" + "no\\s*(\\w*)?[,.;:'!?\"\\s]*";
    public static final String NL_SEPARATORS = "[,.;:'\\s]*";
    public static final String NL_PUNCTUATION = "[,.;:'!?\"\\s]*";
    public static final String NL_ACCEPT_PHRASES = "(?:ok|okay|yes|yeah|yep|sure|go on then|yeah, sure|alright)";
    public static final String NL_ACCEPT_PATTERN_ADDRESSEE_LAST = NL_ACCEPT_PHRASES + NL_SEPARATORS + "(\\w*)?" + NL_PUNCTUATION;
    public static final String NL_ACCEPT_PATTERN_ADDRESSEE_FIRST = "(\\w*)?" + NL_PUNCTUATION + NL_ACCEPT_PHRASES + NL_PUNCTUATION;
    public static final String NL_ACCEPT_PATTERN = "(?i)(?:" + NL_ACCEPT_PATTERN_ADDRESSEE_FIRST + "|" + NL_ACCEPT_PATTERN_ADDRESSEE_LAST + ")";
        
    public static final String NL_REJECT_PATTERN = "(?i)" + "(?:no[,\\s*]thanks|no[,\\s*]sorry|no\\s*way|nope|no|sorry|maybe\\s*later|i'm\\s*not\\s*interested|not\\s*interested|not\\s*right\\s*now|not\\s*now|i\\s*don't\\s*have\\s*any|talk\\s*to\\s*the\\s*hand)[,.;:'\\s]*(\\w*)?[,.;:'!?\"\\s]*";   

    private static final String NL_RSS_QUANTITY_PATTERN = "(\\d+|a|an|one|two|three|four|five|six|seven|eight|nine|ten)?";
    private static final String NL_RSS_NAME_PATTERN = "(clay|cla|cl|c|ore|or|o|sheep|shee|she|sh|s|wheat|whea|whe|wh|wood|woo|wo|wd)?";
    private static final String NL_RSS_EXPRESSION_PATTERN = NL_RSS_QUANTITY_PATTERN + "\\s*" + NL_RSS_NAME_PATTERN;

    private static final String NL_RSS_SEPARATOR = "[,\\s]*(or)?[,\\s|and|&]*";
    private static final String NL_RSS_ARRAY_PATTERN = 
            NL_RSS_EXPRESSION_PATTERN + NL_RSS_SEPARATOR + 
            NL_RSS_EXPRESSION_PATTERN + "{0,1}" + NL_RSS_SEPARATOR +
            NL_RSS_EXPRESSION_PATTERN + "{0,1}" + NL_RSS_SEPARATOR;
    
    public static final String NL_OFFER_TERMS = "\\s*(i\\s*have|" + 
            "i\\s*can\\s*offer\\s*you|i\\s*can\\s*offer|i\\s*offer\\s*you|i\\s*offer|i'm\\s*offering\\s*you|i'm\\s*offering|" + 
            "i\\s*would\\s*give\\s*you|i'd\\s*give\\s*you|i'd\\s*give|" +
            "i\\s*give\\s*you|i\\s*give|i\\s*will\\s*give\\s*you|i\\s*will\\s*give|i'll\\s*give\\s*you|i'll\\s*give|" + 
            "i\\s*can\\s*give\\s*away|" + 
            "i\\s*could\\s*give\\s*you|i\\s*can\\s*give\\s*you|i\\s*could\\s*give|i\\s*can\\s*give|" +
            "i\\s*am\\s*looking\\s*to\\s*trade|" +
            "does\\s*anyone\\s*want|does\\s*anybody\\s*want|" +
            "does\\s*anybody\\s*need|does\\s*anybody\\s*need|" +
            "i\\s*have\\s*spare|" +
            "i\\s*have|" + 
            "i've\\s*got|i\\s*have\\s*got|" +
            "i\\s*can\\s*give|" +
            "does\\s*anybody\\s*have|" +
            "would\\s*somebody\\s*give\\s*me|" +
            "does\\s*anybody\\s*have" +
            "do\\s*you\\s*want\\s*to\\s*get" + 
            "do\\s*you\\s*want|" + 
            "do\\s*you\\s*need|" + 
            "would\\s*you\\s*like" + 
            ")\\s*";
    public static final String NL_OFFER_PATTERN_GIVE_GET = "(?i)" + NL_ADDRESSEES + NL_OFFER_TERMS + NL_RSS_ARRAY_PATTERN + "\\s*(for)\\s*" + NL_RSS_ARRAY_PATTERN + ".*";
    public static final String NL_OFFER_PATTERN_GIVE = "(?i)" + NL_ADDRESSEES + NL_OFFER_TERMS + NL_RSS_ARRAY_PATTERN + ".*";
    
    public static final String NL_REQUEST_TERMS = 
            "\\s*(" + 
            "i\\s*would\\s*want|i'd\\s*want|i\\s*want|i\\s*would\\s*need|i'd\\s*need|i\\s*need|" + 
            "i\\s*would\\s*take|i'd\\s*take|i\\s*take|" +
            "if\\s*you\\s*have|if\\s*you\\s*give\\s*me|if\\s*you\\s*give|" +
            "do\\s*you\\s*have|do\\s*you\\s*give\\s*me|would\\s*you\\s*give\\s*me|" +
            "if\\s*you\\s*give\\s*me|" + 
            "can\\s*someone\\s*give\\s*me|" +
            "does\\s*anyone\\s*have\\s*spare|" +
            "does\\s*anyone\\s*have\\s*any|" +
            "does\\s*anyone\\s*have|" +
            "somebody\\s*got|someone\\s*got|" + 
            "anyone\\s*got|anybody\\s*got" +
            "do\\s*you\\s*have\\s*spare|" + 
            "can\\s*you\\s*give\\s*me|" + 
            "i\\s*would\\s*like|" + 
            "would\\s*you\\s*give\\s*me|" + 
            "if\\s*you\\s*give\\s*me" + 
            ")\\s*";

    public static final String NL_REQUEST_PATTERN_GET_GIVE = "(?i)" + NL_ADDRESSEES + NL_REQUEST_TERMS + NL_RSS_ARRAY_PATTERN + "\\s*(for)\\s*" + NL_RSS_ARRAY_PATTERN + ".*";
    public static final String NL_REQUEST_PATTERN_GET = "(?i)" + NL_ADDRESSEES + NL_REQUEST_TERMS + NL_RSS_ARRAY_PATTERN + ".*"; 

    public static final String NL_OFFER_QUANTITY = "(?i)" + NL_ADDRESSEES + "\\s*i\\s*(?:could|would)?\\s*(give\\s*(?:you)?|offer|have)\\s*(\\d)" + ".*"; 
    public static final String NL_REQUEST_QUANTITY = "(?i)" + NL_ADDRESSEES + "\\s*i\\s*(?:would)?\\s*(want|need)\\s*(\\d)\\s*(?:in\\s*return|in\\s*exchange|for\\s*that)?" + ".*"; 
    
    public static final String[] NL_LEGAL_REGEX = {NL_OFFER_PATTERN_GIVE_GET, NL_OFFER_PATTERN_GIVE, 
                                                    NL_REQUEST_PATTERN_GET_GIVE, NL_REQUEST_PATTERN_GET,
                                                    NL_OFFER_QUANTITY,NL_REQUEST_QUANTITY,
                                                    NL_ACCEPT_PATTERN, NL_REJECT_PATTERN};

    public static final String NL_BANK_TRADE_ADDRESSEE = "(b|ba|ban|bank|p|po|por|port)[,:]*";
    public static final String NL_BANK_TRADE = "(?i)" + "\\s*" +
            NL_BANK_TRADE_ADDRESSEE +
            "\\s*(i\\s*trade|i\\s*swap|i\\s*exchange)?\\s*" +
            NL_RSS_ARRAY_PATTERN + "\\s*(for)\\s*" + NL_RSS_ARRAY_PATTERN;

    /**
     * Determine whether this is an NL string that contains a trade message that we recognise.
     * @param msg the chat message
     * @return boolean whether this is an NL trade message
     */
    public static boolean isNLTradeMsgFormat(String msg) {
        String normalisedMsg = msg.toLowerCase();
        for (String ptrn : NL_LEGAL_REGEX) {
            if (normalisedMsg.matches(ptrn)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determine whether the NL string contains an explicit 'all' addressee.
     * @param msg   the NL input string
     * @return      flag whether an 'all' addressee is present
     */
    public static boolean hasExplicitAllAddressee(String msg) {
        String normalisedMsg = msg.toLowerCase();
        for (String ptrn : NL_LEGAL_REGEX) {
            if (normalisedMsg.matches(ptrn)) {
                Pattern pattern = Pattern.compile(ptrn);
                Matcher matcher = pattern.matcher(normalisedMsg);
                boolean matches = matcher.matches(); //execute the match, so we can get the groups
                if (matches && matcher.groupCount() > 0) {
                    String[] receivers = matcher.group(1).split("[,:\\s]");
                    for (String r : receivers) {
                        if (r.matches(NL_ALL_ADDRESSEES)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Determine whether the NL string contains explicit addressees.
     * @param msg   the NL input string
     * @return      flag whether an 'all' addressee is present
     */
    public static boolean hasExplicitAddressees(String msg) {
        String normalisedMsg = msg.toLowerCase();
        for (String ptrn : NL_LEGAL_REGEX) {
            if (normalisedMsg.matches(ptrn)) {
                Pattern pattern = Pattern.compile(ptrn);
                Matcher matcher = pattern.matcher(normalisedMsg);
                boolean matches = matcher.matches(); //execute the match, so we can get the groups
                if (matches && matcher.groupCount() > 0) {
                    String[] receivers = matcher.group(1).split("[,:\\s]");
                    if (receivers.length > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * Extract the receivers from a substring of an NL trade message.
     * @param receiverName
     * @param ourPn
     * @param playerNames
     * @param output
     * @return the "to" array for a SOCTradeOffer
     */
   private static boolean[] getReceiversFromString(String receiverName, int ourPn, String[] playerNames, StringWriter output) {
        boolean[] to = new boolean[playerNames.length]; // {false,false,false,false};
        boolean[] toAllButMe = new boolean[playerNames.length];
        Arrays.fill(toAllButMe, Boolean.TRUE);
        toAllButMe[ourPn] = false;

        if (receiverName.equals("")) {
            //no receiver specified, so we're offering to all opponents
            return toAllButMe;
        } else {
            String[] receivers = receiverName.split("[,:\\s]");
            for (String r : receivers) {
                if (r.length() == 0)
                    continue;
                if (r.matches(NL_ALL_ADDRESSEES)) {
                    return toAllButMe;
                }
                int addressee = playerNumberForName(r, playerNames);
                if (addressee == 99) {
                    output.append("*** There is no player " + r + " -- not included in the offer.");
                } else {
                    to[addressee] = true;
                }
            }
        }
        return to;
    }
   
    /**
    * Parsing of an NL resource expression of the general form "1 wood sep 1 clay sep 3 sheep sep".
    * "sep" can be a general (conjunctive) separator or "or" for disjunctive trade offers.
    * A resource expression can either be normal (conjunctive) or disjunctive, but not a combination of both.
    * @param groups a String[] containing the expressions from the regex matching
    * @return       pair of SOCResourceSet and boolean whether it's a disjunctive set
    */
    private static Map.Entry<SOCResourceSet, Boolean> parseNLResourceExpression(String[] groups) {
        //first parse the resource terms into resource sets        
        SOCResourceSet resSet = new SOCResourceSet();
        for (int i = 0; i < 3; i++) {
            int g = i * 3; //we're testing the strings at the indexes 0, 3, 6
            int amount = 1;
            String amntStr = groups[g];
            String resName = groups[g+1];
            if ((amntStr == null || amntStr.equals("")) &&
                    (resName == null || resName.equals(""))) {
                continue;
            }
            if (amntStr != null && !amntStr.equals("")) {
                //test if the string contains digits before parsing
                if (amntStr.matches("\\d+"))
                    amount = Integer.parseInt(amntStr);
                else if (amntStr.matches("a|an|one"))
                    amount = 1;
                else if (amntStr.matches("two"))
                    amount = 2;
                else if (amntStr.matches("three"))
                    amount = 3;
                else if (amntStr.matches("four"))
                    amount = 4;
                else if (amntStr.matches("five"))
                    amount = 5;
                else if (amntStr.matches("six"))
                    amount = 6;
                else if (amntStr.matches("seven"))
                    amount = 7;
                else if (amntStr.matches("eight"))
                    amount = 8;
                else if (amntStr.matches("nine"))
                    amount = 9;
                else if (amntStr.matches("ten"))
                    amount = 10;
            }
//            if (resName != null && !resName.equals("")) {
            if (resName == null || resName.equals("")) {
                resName = "unknown";
            }
            Integer resType = resourceTypeFromExpression(resName);
            if (resType != null) {
                if (resSet.getAmount(resType) > 0)
                    D.ebugWARNING("Resource '" + SOCResourceConstants.resName(resType) + "' occurs multiple times - the resulting resource set contains the quantity of the last expression");
                resSet.setAmount(amount, resType);
            } else {
                //we can't identify what resource type was specified in the expression, so we abort
                D.ebugWARNING("Can't parse resource expression " + Arrays.toString(groups));
                return null;
            }
        }
        
        //check that we actually have understood something that we can put into the resource set
        if (resSet.equals(new SOCResourceSet())) {
            D.ebugWARNING("Parsing resource expression resulted in the empty set: " + Arrays.toString(groups));
            return null;
        }
        
        //is this a disjunctive expression?
        //we're ignoring the last group for this
        boolean isDisjunctiveSet = 
                  ((groups[2] != null && groups[2].matches("or"))
                || (groups[5] != null && groups[5].matches("or")));
        if (isDisjunctiveSet) {
            D.ebugPrintINFO("This is a disjunctive resource set.");
        }
        
        Map.Entry<SOCResourceSet, Boolean> retVal = new AbstractMap.SimpleEntry(resSet, isDisjunctiveSet);
        return retVal;
    }

   /**
    * Parse a string that conforms to the regular expressions defining NL trade strings.
    * @param msg            the string with the NL message
    * @param gameName       name of the game
    * @param playerNames    nicknames of all players
    * @param ourPn          number of this player
    * @param output         stream to write information to the game interface, e.g. about failed identification of opponent names
    * @return               Map with trade messages as keys and addresses as values
    */
    public static Map.Entry<StacTradeMessage,Integer> parseNLTradeMsg(String msg, String gameName, String[] playerNames, int ourPn, StringWriter output) {
        String normalisedMsg = msg.toLowerCase();

        Map.Entry<StacTradeMessage,Integer> res; 
    	StacTradeMessage trdMsg = null;
    	Integer receiver = null;
        res = new AbstractMap.SimpleEntry<StacTradeMessage, Integer>(trdMsg, receiver);
        Persuasion persuasion = new Persuasion(); 
        
        //First, check the longer patterns; otherwiese, a problem is, for example, if a player is called 'Noah', typing 'No, I give 1 wheat' would be parsed as rejection.
        
        //OFFER - "I give 1 wood for 1 sheep"
        if (normalisedMsg.toLowerCase().matches(NL_OFFER_PATTERN_GIVE_GET) || normalisedMsg.toLowerCase().matches(NL_REQUEST_PATTERN_GET_GIVE)) {
            
            //establish the case we're treating here
            String patternToUse;
            int indexOffsetGive = 3, indexOffsetGet = 13;
            if (normalisedMsg.toLowerCase().matches(NL_OFFER_PATTERN_GIVE_GET)) {
                patternToUse = NL_OFFER_PATTERN_GIVE_GET;
            } else {
                patternToUse = NL_REQUEST_PATTERN_GET_GIVE;
                indexOffsetGive = 13;
                indexOffsetGet = 3;
            }

            //Match the message against the accept pattern
            Pattern pattern = Pattern.compile(patternToUse);
            Matcher matcher = pattern.matcher(normalisedMsg);
            boolean matches = matcher.matches(); //execute the match, so we can get the groups
            
            if (matches && matcher.groupCount() > 0) {
                //receiver
                boolean[] to = getReceiversFromString(matcher.group(1), ourPn, playerNames, output);
                
                //give set
                String[] groups = new String[9];
                for (int i = 0; i < 9; i++) {
                    groups[i] = matcher.group(i+indexOffsetGive);
                }
                Map.Entry<SOCResourceSet, Boolean> parseRes = parseNLResourceExpression(groups);
                if (parseRes == null) {
                    output.append("*** Can't parse the expression: '" + msg + "'");
                    return null;
                }
                SOCResourceSet give = parseRes.getKey();
                boolean isDisjunctiveGiveSet = parseRes.getValue();

                //get set
                groups = new String[9];
                for (int i = 0; i < 9; i++) {
                    groups[i] = matcher.group(i+indexOffsetGet);
                }
                parseRes = parseNLResourceExpression(groups);
                if (parseRes == null) {
                    output.append("*** Can't parse the expression: '" + msg + "'");
                    return null;
                }
                SOCResourceSet get = parseRes.getKey();
                boolean isDisjunctiveGetSet = parseRes.getValue();
                
                //prepare return value
                String sender = Integer.toString(ourPn);
                String receivers = StacTradeMessage.getToAsString(to);
                StacTradeOffer offer = new StacTradeOffer(gameName, ourPn, to, give, isDisjunctiveGiveSet, get, isDisjunctiveGetSet);
                D.ebugPrintlnINFO("Parsed offer (loc 1): " + offer.toString());
                trdMsg = new StacTradeMessage(sender, receivers, offer, false, persuasion, msg);
                res = new AbstractMap.SimpleEntry<StacTradeMessage, Integer>(trdMsg, receiver); //receiver should still be null here
                
                return res;
            }
        }
        
        //OFFER REQUEST & HAVE
        //These are the patterns "I have wood" and "I want sheep"
        //Both cases specify just one resource set, so we treat then together
        if (normalisedMsg.toLowerCase().matches(NL_REQUEST_PATTERN_GET) || normalisedMsg.toLowerCase().matches(NL_OFFER_PATTERN_GIVE)) {
            boolean requestPattern = false;
            if (normalisedMsg.toLowerCase().matches(NL_REQUEST_PATTERN_GET)) {
                requestPattern = true;
            }
            
            //Match the message against the accept pattern
            String patternToUse = requestPattern ? NL_REQUEST_PATTERN_GET : NL_OFFER_PATTERN_GIVE;
            Pattern pattern = Pattern.compile(patternToUse);
            Matcher matcher = pattern.matcher(normalisedMsg);
            boolean matches = matcher.matches(); //execute the match, so we can get the groups

            if (matches && matcher.groupCount() > 0) {
                //receivers
                boolean[] to = getReceiversFromString(matcher.group(1), ourPn, playerNames, output);

                //andlyse the resource expression
                String[] groups = new String[9];
                //String[] Arrays.copyOfRange(matcher...., 0, 9);
                for (int i = 0; i < 9; i++) {
                    groups[i] = matcher.group(i+3);
                }
                Map.Entry<SOCResourceSet, Boolean> parseRes = parseNLResourceExpression(groups);
                if (parseRes == null) {
                    output.append("*** Can't parse the expression: '" + msg + "'");
                    return null;
                }
                SOCResourceSet resSet = parseRes.getKey();
                boolean disjunctiveSet = parseRes.getValue();
                
                //assign the sets the right 'role'
                SOCResourceSet give, get;
                boolean disjGiveSet, disjGetSet;
                if (requestPattern) {
                    give = SOCResourceSet.EMPTY_SET;
                    get = resSet;
                    disjGiveSet = false;
                    disjGetSet = disjunctiveSet;
                } else {
                    give = resSet;
                    get = SOCResourceSet.EMPTY_SET;
                    disjGiveSet = disjunctiveSet;
                    disjGetSet = false;
                }
                
                //prepare return value
                String sender = Integer.toString(ourPn);
                String receivers = StacTradeMessage.getToAsString(to);
                StacTradeOffer offer = new StacTradeOffer(gameName, ourPn, to, give, disjGiveSet, get, disjGetSet);
                D.ebugPrintlnINFO("Parsed offer (loc 2): " + offer.toString());
                trdMsg = new StacTradeMessage(sender, receivers, offer, false, persuasion, msg);
                res = new AbstractMap.SimpleEntry<StacTradeMessage, Integer>(trdMsg, receiver); //receiver should still be null here
                
                return res;
            }
        }
        
        //JUST QUANTITY SPECIFIED
        //(special pattern for counteroffers)
        if (normalisedMsg.toLowerCase().matches(NL_OFFER_QUANTITY) || normalisedMsg.toLowerCase().matches(NL_REQUEST_QUANTITY)) {
            boolean requestPattern = false;
            if (normalisedMsg.toLowerCase().matches(NL_REQUEST_QUANTITY)) {
                requestPattern = true;
            }
            
            //Match the message against the accept pattern
            String patternToUse = requestPattern ? NL_REQUEST_QUANTITY : NL_OFFER_QUANTITY;
            Pattern pattern = Pattern.compile(patternToUse);
            Matcher matcher = pattern.matcher(normalisedMsg);
            boolean matches = matcher.matches(); //execute the match, so we can get the groups

            if (matches && matcher.groupCount() > 0) {
                //receivers & quantity
                boolean[] to = getReceiversFromString(matcher.group(1), ourPn, playerNames, output);
                int quant = Integer.parseInt(matcher.group(3));
                
                //assign the sets the right 'role'
                SOCResourceSet give, get;
                if (requestPattern) {
                    give = SOCResourceSet.EMPTY_SET;
                    get = new SOCResourceSet(0, 0, 0, 0, 0, quant);
                } else {
                    give = new SOCResourceSet(0, 0, 0, 0, 0, quant);
                    get = SOCResourceSet.EMPTY_SET;
                }
                
                //prepare & send return value
                String sender = Integer.toString(ourPn);
                String receivers = StacTradeMessage.getToAsString(to);
                
                StacTradeOffer offer = new StacTradeOffer(gameName, ourPn, to, give, false, get, false);
                D.ebugPrintlnINFO("Parsed offer (loc 3): " + offer.toString());
                trdMsg = new StacTradeMessage(sender, receivers, offer, false, persuasion, msg);
                res = new AbstractMap.SimpleEntry<StacTradeMessage, Integer>(trdMsg, receiver); //receiver should still be null here
                
                return res;
            }
        }
        
        //REJECT
        if (normalisedMsg.toLowerCase().matches(NL_REJECT_PATTERN)) {
            receiver = 99;

            //Match the message against the accept pattern
            Pattern pattern = Pattern.compile(NL_REJECT_PATTERN);
            Matcher matcher = pattern.matcher(normalisedMsg);
            boolean matches = matcher.matches(); //execute the match, so we can get the groups
            
            if (matches && matcher.groupCount() > 0) {
                String pname = matcher.group(1);
                if (pname != null && !pname.equals("")) {
                    receiver = playerNumberForName(pname, playerNames);
                }
            }
            
            String sender = Integer.toString(ourPn);
            String receivers = Integer.toString(receiver);
            trdMsg = new StacTradeMessage(sender, receivers, false, true, false, false, null, false, msg);
            
            //prepare return value
            res = new AbstractMap.SimpleEntry<StacTradeMessage, Integer>(trdMsg, receiver);
                
            return res;
        }

        //ACCEPT
        if (normalisedMsg.toLowerCase().matches(NL_ACCEPT_PATTERN)) {
            //see if there's a receiver of the message
            receiver = 99;

            //Match the message against the accept pattern
            Pattern pattern = Pattern.compile(NL_ACCEPT_PATTERN);
            Matcher matcher = pattern.matcher(normalisedMsg);
            boolean matches = matcher.matches(); //execute the match, so we can get the groups
            
            if (matches && matcher.groupCount() > 0) {
                String pname = "";
                if (normalisedMsg.toLowerCase().matches(NL_ACCEPT_PATTERN_ADDRESSEE_LAST)) {
                    pname = matcher.group(2);
                } else if (normalisedMsg.toLowerCase().matches(NL_ACCEPT_PATTERN_ADDRESSEE_FIRST)) {
                    pname = matcher.group(1);
                }
                if (pname != null && !pname.equals("")) {
                    receiver = playerNumberForName(pname, playerNames);
                }
            }

            //prepare return value
            String sender = Integer.toString(ourPn);
            String receivers = Integer.toString(receiver);
            trdMsg = new StacTradeMessage(sender, receivers, true, false, false, false, null, false, msg);
            res = new AbstractMap.SimpleEntry<StacTradeMessage, Integer>(trdMsg, receiver);
                
            return res;
        }
        
    	return res;
    }

    /**
     * Parses a message following the formal trade language rules into a bank trade message
     * @param msg the message to be parsed
     * @param gameName the name of the game this message will be sent to
     * @param output a buffer containing any error messages to be printed out in the game message interface
     * @return null or the SOCBankTrade object to be sent to the server
     */
    public static SOCBankTrade parseNLBankTradeMsg(String msg, String gameName, StringWriter output) {
        String normalisedMsg = msg.toLowerCase();
 
        if (normalisedMsg.toLowerCase().matches(NL_BANK_TRADE)) {
            
            //establish the case we're treating here
            int indexOffsetGive = 3, indexOffsetGet = 13;

            //Match the message against the accept pattern
            Pattern pattern = Pattern.compile(NL_BANK_TRADE);
            Matcher matcher = pattern.matcher(normalisedMsg);
            boolean matches = matcher.matches(); //execute the match, so we can get the groups
            
            if (matches && matcher.groupCount() > 0) {                
                //give set
                String[] groups = new String[9];
                for (int i = 0; i < 9; i++) {
                    groups[i] = matcher.group(i+indexOffsetGive);
                }
                Map.Entry<SOCResourceSet, Boolean> parseRes = parseNLResourceExpression(groups);
                if (parseRes == null) {
                    output.append("*** Can't parse the expression: '" + msg + "'");
                    return null;
                }
                SOCResourceSet give = parseRes.getKey();
                if (parseRes.getValue()) {
                    output.append("*** Can't use a disjunctive set in a bank/port trade: '" + msg + "'");
                    return null;
                }

                //get set
                groups = new String[9];
                for (int i = 0; i < 9; i++) {
                    groups[i] = matcher.group(i+indexOffsetGet);
                }
                parseRes = parseNLResourceExpression(groups);
                if (parseRes == null) {
                    output.append("*** Can't parse the expression: '" + msg + "'");
                    return null;
                }
                SOCResourceSet get = parseRes.getKey();
                if (parseRes.getValue()) {
                    output.append("*** Can't use a disjunctive set in a bank/port trade: '" + msg + "'");
                    return null;
                }
                
                //prepare return value
                return new SOCBankTrade(gameName, give, get);
            }
        }
        
        return null;
    }
        
    /**
     * Turns the build plan announcement into a friendly human readable string
     * @param msg the SOCGameTextMsg containing the announcement
     * @return
     */
    public static StringBuffer annMessageToString(SOCGameTextMsg msg) {
    	//ANN:BP:SOCPossiblePiece:type=2~player=soc.game.SOCPlayer@1e3b5f92~coord=78]
    	String split[] = msg.getText().split(":");
    	if(split.length < 4){
            D.ebugERROR(msg.getText() + " from player " + msg.getNickname() + " cannot be parsed due to wrong format");
            return new StringBuffer(msg.getText());
    	}
        String annString = StacDialogueManager.fromMessage(split[3]);
        SOCPossiblePiece pp = SOCPossiblePiece.parse(annString);
        StringBuffer annMsgText = new StringBuffer(msg.getNickname());
        switch (pp.getType()) {
            case 0: annMsgText.append(": I want to build a road at "); break;
            case 1: annMsgText.append(": I want to build a settlement at "); break;
            case 2: annMsgText.append(": I want to build a city at "); break;
            case 4: annMsgText.append(": I want to buy a development card"); break;
        }
        if (pp.getType() != 4) {
            annMsgText.append(Integer.toHexString(pp.getCoordinates()));
        }
        annMsgText.append(".");
        return annMsgText;
    }
    
    /**
     * Turns the request message into a friendly human readable string
     * @param mes the SOCGameTextMsg containing the request
     * @return
     */
	public static String reqMessageToString(SOCGameTextMsg mes) {
		String reqMsg;
		if(mes.getText().contains("REQ:BP")){
			reqMsg = mes.getNickname() + ": " + "Please announce your build plan";
    	}else if(mes.getText().contains("REQ:RES")){
    		reqMsg =mes.getNickname() + ": " + "Please announce your resources";
    	}else if(mes.getText().contains("REQ:EXTRA")){
    		reqMsg =mes.getNickname() + ": " + "Please announce your extra resources";
    	}else if(mes.getText().contains("REQ:WANTED")){
    		reqMsg =mes.getNickname() + ": " + "Please announce the resources you want";
    	}else if(mes.getText().contains("REQ:TRD:")){
    		reqMsg =mes.getNickname() + ": " + "Does anyone have any offers for me?";
    	}else
    		reqMsg =mes.getNickname() + ": " + mes.getText(); // undefined request type, just print in the chat as a normal msg
		return reqMsg;
	}    
    ////to friendly messages///
    

//    /**
//     * quick debugging of patterns and/or parsing
//     * @param args
//     * @throws IOException
//     */
//    public static void main(String[] args) throws IOException{
//    	BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
//        Pattern pattern = 
//        Pattern.compile(OFFER_PATTERN);//replace with the pattern intended for testing
//    	while (true) {
//            
//            System.out.println("Enter input string to search: ");
//            String msg = br.readLine();
//            Matcher matcher = 
//            pattern.matcher(msg);
// 
//            if(isBankTradeMsgFormat(msg))
//            	System.out.println("bank trade");
//            
//            if(isTradeMsgFormat(msg)){ //for checking both the format and the translation
//            	StringWriter output = new StringWriter();
//            	Map.Entry<TradeMessage,Integer> entry = parseTradeMsg(msg, "test", 0, output);//we are player 0 or blue in this test
//            	System.err.println(output.toString());
//            	output.close();//don't forget to close
//            	if(entry!=null){
//            		System.out.println("result: " + composeTradeMessageString(entry.getKey(), entry.getValue()));
//            	}
//            }
//            
//            //it will try to match to the pattern defined initially anyway
//            boolean found = false;
//            while (matcher.find()) {
//                System.out.printf("I found the text" +
//                    " \"%s\" starting at " +
//                    "index %d and ending at index %d.%n",
//                    matcher.group(),
//                    matcher.start(),
//                    matcher.end());
//                found = true;
//            }
//            if(!found){
//                System.out.println("No match found.");
//            }
//        }
//    }
}
