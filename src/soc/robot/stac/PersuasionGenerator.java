package soc.robot.stac;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import soc.debug.D;
import soc.robot.stac.Persuasion.PersuasionIdentifiers;


/** This class should act as the level between the Persuasion Negotiator and the Persuasion class itself. It also performs the XML querys
 * 
 * @author s1132305
 *
 */
public class PersuasionGenerator {

    /** Maintain the list of all constraints so that I can get appropriate error messages if one is missed at some point */
    public static final Set<String> allConstraints;
    static {
        allConstraints = new HashSet<String>();
        for (Persuasion.PersuasionIdentifiers pID : PersuasionIdentifiers.values()){
            Persuasion tempPersuasion = new Persuasion(pID);
            allConstraints.addAll(tempPersuasion.getConstraints().keySet());
        }
    }
    
	/** Maintain the list of all parameters so that I can get appropriate error messages if one is missed at some point */
    public static final Set<String> allParameters;
    static {
    	allParameters = new HashSet<String>();
        for (Persuasion.PersuasionIdentifiers pID : PersuasionIdentifiers.values()){
            Persuasion tempPersuasion = new Persuasion(pID);
            allParameters.addAll(tempPersuasion.getParameters().keySet());
        }
    }
	
    /** 
     * Allow a mapping from the persuasive move to the String representation
     */
    private final static HashMap<String, PersuasionIdentifiers> MAP_FROM_NEGOTIATOR_PERSUASION_NAME_TO_PERSUASIONID;
    static {
    	MAP_FROM_NEGOTIATOR_PERSUASION_NAME_TO_PERSUASIONID = new HashMap<String, PersuasionIdentifiers>();
    	MAP_FROM_NEGOTIATOR_PERSUASION_NAME_TO_PERSUASIONID.put(StacRobotType.PERSUADER_ImmediateBuildPlan_Road, PersuasionIdentifiers.IBPRoad);
    	MAP_FROM_NEGOTIATOR_PERSUASION_NAME_TO_PERSUASIONID.put(StacRobotType.PERSUADER_ImmediateBuildPlan_Settlement, PersuasionIdentifiers.IBPSettlement);
    	MAP_FROM_NEGOTIATOR_PERSUASION_NAME_TO_PERSUASIONID.put(StacRobotType.PERSUADER_ImmediateBuildPlan_City, PersuasionIdentifiers.IBPCity);
    	MAP_FROM_NEGOTIATOR_PERSUASION_NAME_TO_PERSUASIONID.put(StacRobotType.PERSUADER_ImmediateBuildPlan_DevCard, PersuasionIdentifiers.IBPDevCard);
    	MAP_FROM_NEGOTIATOR_PERSUASION_NAME_TO_PERSUASIONID.put(StacRobotType.PERSUADER_ImmediateTradePlanBank, PersuasionIdentifiers.ITP);
    	MAP_FROM_NEGOTIATOR_PERSUASION_NAME_TO_PERSUASIONID.put(StacRobotType.PERSUADER_ImmediateTradePlanPort, PersuasionIdentifiers.ITP);
    	MAP_FROM_NEGOTIATOR_PERSUASION_NAME_TO_PERSUASIONID.put(StacRobotType.PERSUADER_ResourceBasedTooManyRes, PersuasionIdentifiers.RBTooManyRes);
    	MAP_FROM_NEGOTIATOR_PERSUASION_NAME_TO_PERSUASIONID.put(StacRobotType.PERSUADER_ResourceBasedCantGetRes, PersuasionIdentifiers.RBCantGetRes);
    	MAP_FROM_NEGOTIATOR_PERSUASION_NAME_TO_PERSUASIONID.put(StacRobotType.PERSUADER_AlwaysTrue_RobPromise, PersuasionIdentifiers.ATRobPromise);
    	MAP_FROM_NEGOTIATOR_PERSUASION_NAME_TO_PERSUASIONID.put(StacRobotType.PERSUADER_AlwaysTrue_PromiseRes, PersuasionIdentifiers.ATPromiseResource);
    }
	
	public PersuasionGenerator(){
	    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	    factory.setNamespaceAware(true);
	}

   
   /** Generate a new Persuasion Object given the Persuasion Name the Negotiator deals with
    * 
    * @param argument
    * @return
    */
   public static Persuasion getPersuasion(String argument){
	   try{
		   Persuasion tempPersuasion = new Persuasion(MAP_FROM_NEGOTIATOR_PERSUASION_NAME_TO_PERSUASIONID.get(argument));
		   HashMap<String, String> parameters = tempPersuasion.getParameters();
		   if(argument.equals(StacRobotType.PERSUADER_ImmediateTradePlanBank)){
			   parameters.put(Persuasion.tradeTypeWildcard,"bank"); 
		   }
		   else if(argument.equals(StacRobotType.PERSUADER_ImmediateTradePlanPort)){
			   parameters.put(Persuasion.tradeTypeWildcard,"port"); 			   
		   }
		   return tempPersuasion;
	   }
	   catch (NullPointerException e){
		   D.ebugERROR("The argument: \""+argument+"\" is not identifiable by the PersuasionGenerator.");
		   return new Persuasion();
	   }
   }
   
}
