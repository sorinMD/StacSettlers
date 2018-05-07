package soc.robot.stac;
import java.util.HashMap;
import soc.disableDebug.D;

/** Object which represents a persuasive move (As an attachment to a trade message)
 * 
 * @author s1132305
 *
 */
public class Persuasion {

    //Identifiers of different types of persuasive moves. Should be easy to add to.
    public static enum PersuasionIdentifiers { 
    	/** Persuasion build on the recipient of the offer being able to build a road where they couldnt before. */ 
    	IBPRoad, 
    	/** Persuasion build on the recipient of the offer being able to build a settlement where they couldnt before. */ 
    	IBPSettlement, 
    	/** Persuasion build on the recipient of the offer being able to build a city where they couldnt before */ 
    	IBPCity, 
    	/** Persuasion build on the recipient of the offer being able to buy a development card where they couldnt before */ 
    	IBPDevCard, 
    	/** Persuasion based on the recipient being able to make a trade with a port or bank they couldnt before */
    	ITP, 
    	/** Persuasion based on the recipient having too many resources (over 7) and will have less after the trade */
    	RBTooManyRes, 
    	/** Persuasion based on the recipient not currently having access to a res (which is offered as part of the trade) */
    	RBCantGetRes, 
    	/** Persuasion promising to give a resource to the recipient when the persuader can */
    	ATPromiseResource, 
    	/** Persuasion promising not to rob the recipient next time they get the chance */
    	ATRobPromise, 
    	/** NullPersuasion = no persuasive move */
    	NullPersuasion}

    /**
     * Allow a mapping from the persuasive move to the String representation
     */
    public final static HashMap<PersuasionIdentifiers, String> MAP_FROM_PersuasionId_To_String;
    public final static HashMap<String, PersuasionIdentifiers> MAP_FROM_String_To_PersuasionId;
    static {
        MAP_FROM_PersuasionId_To_String = new HashMap<PersuasionIdentifiers, String>();
        MAP_FROM_PersuasionId_To_String.put(PersuasionIdentifiers.IBPRoad, "IBPRoad");
        MAP_FROM_PersuasionId_To_String.put(PersuasionIdentifiers.IBPSettlement, "IBPSettlement");
        MAP_FROM_PersuasionId_To_String.put(PersuasionIdentifiers.IBPCity, "IBPCity");
        MAP_FROM_PersuasionId_To_String.put(PersuasionIdentifiers.IBPDevCard, "IBPDevCard");
        MAP_FROM_PersuasionId_To_String.put(PersuasionIdentifiers.ITP, "ITP");
        MAP_FROM_PersuasionId_To_String.put(PersuasionIdentifiers.RBTooManyRes, "RBTooManyRes");
        MAP_FROM_PersuasionId_To_String.put(PersuasionIdentifiers.RBCantGetRes, "RBCantGetRes");
        MAP_FROM_PersuasionId_To_String.put(PersuasionIdentifiers.ATPromiseResource, "ATPromiseResource");
        MAP_FROM_PersuasionId_To_String.put(PersuasionIdentifiers.ATRobPromise, "ATRobPromise");
        MAP_FROM_PersuasionId_To_String.put(PersuasionIdentifiers.NullPersuasion, "NullPersuasion");
        MAP_FROM_PersuasionId_To_String.put(PersuasionIdentifiers.NullPersuasion, "");
        MAP_FROM_PersuasionId_To_String.put(PersuasionIdentifiers.NullPersuasion, null);

        MAP_FROM_String_To_PersuasionId = new HashMap<String, PersuasionIdentifiers>();
        MAP_FROM_String_To_PersuasionId.put("IBPRoad", PersuasionIdentifiers.IBPRoad);
        MAP_FROM_String_To_PersuasionId.put("IBPSettlement", PersuasionIdentifiers.IBPSettlement);
        MAP_FROM_String_To_PersuasionId.put("IBPCity", PersuasionIdentifiers.IBPCity);
        MAP_FROM_String_To_PersuasionId.put("IBPDevCard", PersuasionIdentifiers.IBPDevCard);
        MAP_FROM_String_To_PersuasionId.put("ITP", PersuasionIdentifiers.ITP);
        MAP_FROM_String_To_PersuasionId.put("RBTooManyRes", PersuasionIdentifiers.RBTooManyRes);
        MAP_FROM_String_To_PersuasionId.put("RBCantGetRes", PersuasionIdentifiers.RBCantGetRes);
        MAP_FROM_String_To_PersuasionId.put("ATPromiseResource", PersuasionIdentifiers.ATPromiseResource);
        MAP_FROM_String_To_PersuasionId.put("ATRobPromise", PersuasionIdentifiers.ATRobPromise);
        MAP_FROM_String_To_PersuasionId.put("NullPersuasion", PersuasionIdentifiers.NullPersuasion);
        MAP_FROM_String_To_PersuasionId.put("", PersuasionIdentifiers.NullPersuasion);
        MAP_FROM_String_To_PersuasionId.put(null, PersuasionIdentifiers.NullPersuasion);
    }

    /**
     * All the attributes associated with a persuasive move.
     */
    private final PersuasionIdentifiers identifier;
    private final String queryRepresentation;


    /** Set to a single type of resource string - "wood", "ore", "clay", "sheep" or "wheat" */
    public final static String resWildcard = "\\$res";

    /** "equal", "get" or "keep" */
    public final static String largestArmyTypeWildcard = "\\$LAType";
    /** "equal", "get" or "keep" */
    public final static String longestRoadTypeWildcard = "\\$LRType";
    /** "equal", "get" or "keep" */
    public final static String victoryPointsTypeWildcard = "\\$VPType";
    /** "port" or "bank */
    public final static String tradeTypeWildcard = "\\$TType";

    public final static String longestRoadConstraint = "longestRoad";
    public final static String newSettlementConstraint = "newSettlement";
    public final static String accessNewResourceConstraint = "accessNewRes";
    public final static String victoryPointsConstraint = "VPs";
    public final static String largestArmyConstraint = "largestArmy";
    public final static String parametersDefault = "DEFAULT";
    
    /** Parameters list that is passed with the Persuasion type and used by robots. */
    private HashMap<String,String> parameters = new HashMap<String, String>();

    /** Constraints that can are used to find the exact required persuasion  */
    private HashMap<String,String> constraints = new HashMap<String, String>();

    /**
     * Null Persuasion move, to have a quick method of generating a trade with no Persuasion
     */
    public Persuasion(){
        this(PersuasionIdentifiers.NullPersuasion,new String[]{}, new String[]{});
    }


    /** Used to generate a "Blank" Persuasion - pre-constraints - using only the Persuasion ID
     *
     * @param id
     */
    public Persuasion(PersuasionIdentifiers id){
        this(id, new String[]{}, new String[]{});
    }


    /**
     * Main Persuasion
     * @param rep the PersuasionIdentifiers which identifies this type
     * @param constraintsString an array of constraints which hold true. Non listed constraints are assumed false
     * @param params the list of parameters and the mapping between them. The format should be wildcard=value, with each separated by a space
     */
    public Persuasion(PersuasionIdentifiers rep, String[] constraintsString, String[] params) {
        identifier = rep;

        //Switch on the formal representation of the persuasion
        switch (rep){
            //The persuasion is based on an Immediate Build Plan (for the opponent) to build a road.
            case IBPRoad:
                queryRepresentation = "IBP/road";
                constraints.put(longestRoadConstraint,"false");
                constraints.put(newSettlementConstraint,"false"); //Adds to the number of settlements that can currently be built
                parameters.put(longestRoadTypeWildcard,parametersDefault); //Only needs to be assigned if longestRoad="true"
                break;

            //The persuasion is based on an Immediate Build Plan (for the opponent) to build a settlement.
            case IBPSettlement:
                queryRepresentation = "IBP/settlement";
                constraints.put(accessNewResourceConstraint,"false");
                constraints.put(victoryPointsConstraint,"false");
                parameters.put(victoryPointsTypeWildcard,parametersDefault); //Assign if VPs="true"
                parameters.put(resWildcard,parametersDefault);  //Assign if accessNewRes="true"
                break;

            //The persuasion is based on an Immediate Build Plan (for the opponent) to build a city.
            case IBPCity:
                queryRepresentation = "IBP/city";
                constraints.put(victoryPointsConstraint,"false");
                parameters.put(victoryPointsTypeWildcard,parametersDefault); //Assign if VPs="true"
                break;

            //The persuasion is based on an Immediate Build Plan (for the opponent) to buy a dev card.
            case IBPDevCard:
                queryRepresentation = "IBP/devCard";
                constraints.put(largestArmyConstraint,"false");
                parameters.put(largestArmyTypeWildcard,parametersDefault); //Assign if largestArmy="true"
                break;

            //The persuasion is based on an Immediate Trade Plan (for the opponent).	
            case ITP:
                queryRepresentation = "ITP";
                parameters.put(tradeTypeWildcard,parametersDefault); //Must always be assigned
                parameters.put(resWildcard,parametersDefault);  //Must always be assigned
                break;

            //The persuasion Resource Based on too many resources (for the opponent).	
            case RBTooManyRes:
                queryRepresentation = "ResourceBased/tooManyRes";
                break;

            case RBCantGetRes:
                queryRepresentation = "ResourceBased/cantGetRes";
                parameters.put(resWildcard,parametersDefault); //Must always be assigned
                break;

            //Always true persuasions
            case ATPromiseResource:
                queryRepresentation = "AlwaysTrue/promiseResource";
                parameters.put(resWildcard,parametersDefault); //Must always be assigned
                break;

            case ATRobPromise:
                queryRepresentation = "AlwaysTrue/robPromise";
                break;

            //The persuasion is the Null Persuasion - ie no persuasive move. Badly formatted input will assume this is the case.
            case NullPersuasion:
                queryRepresentation = "";
                break;

            //Shouldn't be possible
            default:
                D.ebugERROR("Attempted to use persuasion identifier: "+rep.toString());
                queryRepresentation = "";
                break;
        }

        //If there are constraints set those values to be true - If input here is not a real constraint it will just be ignored
        if(constraintsString.length>0){
            for (String p : constraintsString){
                String[] parts = p.split("=");
                if(constraints.containsKey(parts[0])){
                    constraints.put(parts[0],parts[1]);
                }
                else if(!parts[0].equals("")){
                    D.ebugERROR("The constraint being added to the constraint list isn't known: "+p+" in the persuasive move: "+constraints.toString() + " in "+rep + " trade.");
                }
            }
        }

        //If there are params, format them properly and set the values.
        if(params.length>0){
            for(String p : params){
                String[] parts = p.split("=");
                if(parameters.containsKey("\\$"+parts[0])){
                    parameters.put("\\$"+parts[0],parts[1]);
                }
                else if(!parts[0].equals("")){
                    D.ebugERROR("The parameters being added to the parameters list isn't known: "+p+" in the persuasive move: "+constraints.toString() + " in "+rep + " trade.");
                }
            }
        }
    }

    /**
     * Method to generate a Persuasive move using human input
     * @param unformattedInput This just means it is of the designed format for user input currently
     * @return the Persuasive move generated by the input
     */
    public static Persuasion parsePersuasion(String unformattedInput){
        //Not a complete Persuasive Move - empty string
        if(unformattedInput.equals("")){
            return new Persuasion();
        }

        String[] constraints = new String[0];
        String[] params = new String[0];

        //Add the parameters of the persuasion to the two lists by splitting the correct part of the input
        String pParts [] = unformattedInput.split("\\|");

        for(int i = 1; i<pParts.length; i++){
            if(pParts[i].startsWith("constraints=")){
                pParts[i] = pParts[i].substring("constraints=".length());
                constraints = pParts[i].split("\\,");
            }
            else if(pParts[i].startsWith("params=")){
                pParts[i] = pParts[i].substring("params=".length());
                params = pParts[i].split(",");
            }
            else if(pParts[i].startsWith("constraints")||pParts[i].startsWith("params")){
                //ie - no arguments passed in these areas.
            }
            else{
                D.ebugERROR("Expected either \"params\" or \"constraints\" in " + unformattedInput);
                return new Persuasion();
            }

        }
        PersuasionIdentifiers p_arg = MAP_FROM_String_To_PersuasionId.get(pParts[0]);
        return new Persuasion(p_arg,constraints, params);
    }

    /**
     *
     * @return the identifier of the object
     */
    public PersuasionIdentifiers getIdentifier() {
        return identifier;
    }

    /**
     * Method to get the correct query value (Before appending a prefix and suffix defined in the StacChatTradeMsgParser
     * @return a part of an XPath query to retrieve the appropriate query results
     */
    public String getQueryRepresentation(){

        //If there are no constraints
        if(constraints.size()==0){
            return queryRepresentation + "/constraints";
        }

        else{
            //Begin to list the constraints
            StringBuffer sb = new StringBuffer("/constraints[");
            boolean includeAnd = false;

            for (String c : constraints.keySet()){

                //Only list all the values - therefore getting the most
                if(includeAnd){
                    sb.append(" and ");
                }

                else{
                    includeAnd=true;
                }

                //This is the format requred by XPath to query using attributes
                sb.append("@"+c+"=\""+constraints.get(c)+"\"");
            }

            sb.append("]");

            //Keeping the square brackets is not allowed if there are no attributes listed
            if(sb.toString().equals("/constraints[]")){
                return queryRepresentation + "/constraints";
            }

            else{
                return queryRepresentation + sb.toString();
            }
        }
    }

    public HashMap<String,String> getConstraints(){
        return constraints;
    }

    public HashMap<String,String> getParameters(){
        return parameters;
    }

    /**
     * Method to get the String to print to the log file
     * @return the String to print to the log file
     */
    public String toString()
    {
        if(identifier!=Persuasion.PersuasionIdentifiers.NullPersuasion){
            StringBuffer sb = new StringBuffer("|persuasion="+identifier.toString()+"~constraints=");
            boolean notFirstElem = false;
            for (String cons : constraints.keySet()){
                if(notFirstElem){
                    sb.append(",");
                }
                else{ notFirstElem = true; }
                sb.append(cons +"=" + constraints.get(cons));
            }
            sb.append("~params=");
            for(String key : parameters.keySet()){
                if(!parameters.get(key).equals("")){
                    sb.append(key.substring(2,key.length())+"="+parameters.get(key)+",");
                }
            }
            return sb.substring(0,sb.length()-1).toString();
        }
        return "";
    }

    /** FROM input style ("ibp_road^constraints,longestRoad^params,LRType=get") to log/internal style ()
     *
     * @param formalInput
     * @return
     */
    public static String formatPersuasion(String formalInput){
        String[] parts = formalInput.split("\\^");
        PersuasionIdentifiers p_arg = MAP_FROM_String_To_PersuasionId.get(parts[0]);
        StringBuffer newInput = new StringBuffer(p_arg.toString());
        for(int i =1; i<parts.length;i++){
            String[] pParts = parts[i].split("\\,");
            if(pParts[0].equals("constraints")){
                newInput.append("|constraints=");
                boolean notFirst = false;
                for(int j =1; j<pParts.length;j++){
                    if(notFirst){
                        newInput.append(",");
                    }
                    else{
                        notFirst=true;
                    }
                    newInput.append(pParts[j]+"=true");
                }
            }
            else if(pParts[0].equals("params")){
                newInput.append("|params=");
                boolean notFirst = false;
                for(int j =1; j<pParts.length;j++){
                    if(notFirst){
                        newInput.append(",");
                    }
                    else{
                        notFirst=true;
                    }
                    String[] ppParts = pParts[j].split("\\=");
                    newInput.append(ppParts[0]+ "="+ppParts[1]);
                }
            }
        }
        return newInput.toString();
    }

}
