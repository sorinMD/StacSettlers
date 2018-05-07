package soc.robot.stac;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import soc.debug.D;
import soc.game.SOCBoard;
import soc.game.SOCPlayer;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCRoad;
import soc.game.SOCSettlement;
import soc.game.SOCTradeOffer;
import soc.robot.SOCBuildPlanStack;
import soc.robot.SOCBuildingSpeedEstimate;
import soc.robot.SOCPlayerTracker;
import soc.robot.SOCPossiblePiece;
import soc.robot.SOCPossibleRoad;
import soc.robot.SOCPossibleSettlement;
import soc.robot.stac.Persuasion.PersuasionIdentifiers;

/** Class to deal with the inclusion of a possible Persuasion when reacting to a trade or instantiating a trade.
 * Extends {@link StacRobotNegotiator}
 * @see StacRobotNegotiator
 * @author s1132305
 *
 */
public class PersuasionStacRobotNegotiator extends StacRobotNegotiator {
	
	/** Boolean to quickly decided if the negotiator is a persuader */
	private boolean persuaderType = false;
	
	/** Boolean to quickly decided if the negotiator is a recipient persuader type */
	private boolean persuasionRecipientType = false;;

	/** Preference over persuasions moves that this robot would like to apply each element of the list is a set of persuasion values that the robot considers the same weight*/
	private ArrayList<HashSet<Persuasion.PersuasionIdentifiers>> preferenceOverPersuasions = new ArrayList<HashSet<Persuasion.PersuasionIdentifiers>>();
	
	/** Persuasion Negotiator to generate and respond to persuasive trades
	 * @param br
	 * @param fullPlan
	 */
	public PersuasionStacRobotNegotiator(StacRobotBrain br, boolean fullPlan) {
		super(br, fullPlan);
		//Assign whether or not this Negotiator will deal with Persuader methods, or non Persuader methods
       	for(String a : StacRobotType.persuaderTypes){
    		if(brain.isRobotType(a)){
    			persuaderType = true;
    		}
    	}
    	for(String a : StacRobotType.persuaderRecipientTypes){
    		if(brain.isRobotType(a)){
    			persuasionRecipientType = true;
    		}
    	}
    	generateOrderingOfPersuasions();
    }
	
	/**
	 * Whether the negotiator has a persuader type associated with it
	 * @return
	 */
	public boolean getPersuaderType(){
		return persuaderType;
	}
	
	/**
	 * Whether the negotiator has a persuader recipient type associated with it
	 * @return
	 */
	public boolean getPersuasionRecipientType(){
		return persuasionRecipientType;
	}

    /** 
     *  Generate the ordering of persuasions used when considering which persuasion move to make
     */
    private void generateOrderingOfPersuasions(){
    	preferenceOverPersuasions = new ArrayList<HashSet<Persuasion.PersuasionIdentifiers>>();
	    if(brain.isRobotType(StacRobotType.Persuade_Choose_Own_Ordering)){
        	String ordering = (String)brain.getTypeParam(StacRobotType.Persuade_Choose_Own_Ordering);
        	String[] parts = ordering.split("->");
        	for (String p : parts){
    			HashSet<Persuasion.PersuasionIdentifiers> tempSet = new HashSet<Persuasion.PersuasionIdentifiers>();
        		String[] pparts = p.split("-");
        		for (String pref : pparts){
	    			if(pref.equals("itp")){
	        			tempSet.add(Persuasion.PersuasionIdentifiers.ITP);
	        		}
	        		else if(pref.equals("road")){
	        			tempSet.add(Persuasion.PersuasionIdentifiers.IBPRoad);
	        		}
	        		else if(pref.equals("devcard")){
	        			tempSet.add(Persuasion.PersuasionIdentifiers.IBPDevCard);
	        		}
	        		else if(pref.equals("settlement")){
	        			tempSet.add(Persuasion.PersuasionIdentifiers.IBPSettlement);
	        		}
	        		else if(pref.equals("city")){
	        			tempSet.add(Persuasion.PersuasionIdentifiers.IBPCity);
	        		}
	        		else if(pref.equals("atpr")){
	        			tempSet.add(Persuasion.PersuasionIdentifiers.ATPromiseResource);
	        		}
	        		else if(pref.equals("atrp")){
	        			tempSet.add(Persuasion.PersuasionIdentifiers.ATRobPromise);
	        		}
	        		else if(pref.equals("rbcgr")){
	        			tempSet.add(Persuasion.PersuasionIdentifiers.RBCantGetRes);
	        		}
	        		else if(pref.equals("rbtmr")){
	        			tempSet.add(Persuasion.PersuasionIdentifiers.RBTooManyRes);
	        		}
	        		else if(pref.equals("ibp")){
	        			tempSet.add(Persuasion.PersuasionIdentifiers.IBPRoad);
	        			tempSet.add(Persuasion.PersuasionIdentifiers.IBPCity);
	        			tempSet.add(Persuasion.PersuasionIdentifiers.IBPDevCard);
	        			tempSet.add(Persuasion.PersuasionIdentifiers.IBPSettlement);
	        		}
	        		else if(pref.equals("rb")){
	        			tempSet.add(Persuasion.PersuasionIdentifiers.RBCantGetRes);
	        			tempSet.add(Persuasion.PersuasionIdentifiers.RBTooManyRes);
	        		}
	        		else if(pref.equals("at")){
	        			tempSet.add(Persuasion.PersuasionIdentifiers.ATPromiseResource);
	        			tempSet.add(Persuasion.PersuasionIdentifiers.ATRobPromise);
	        		}
	        		else{
	        			D.ebugERROR(brain.getPlayerNumber()+" - Unrecognised value in persuasion: "+pref);
	        		}
        		}
        		
        		if(tempSet.size()>0){
        			preferenceOverPersuasions.add(tempSet);
        		}
        	}	
	    }
	}

    
    /*************************************************************************************************/
    /******* PERSUASION MOVE GENERATION SECTION OF CLASS *********************************************/
    /*************************************************************************************************/
	
    /**
	 * Account for rejections before generating move through other method generatePersuasionMove(SOCTradeOffer offer)
	 * @param offer
	 * @param rejections
	 * @return
	 */
	protected Persuasion generatePersuasionMove(SOCTradeOffer offer, boolean[] rejections){
        if (brain.isRobotType(StacRobotType.Persuade_ONLY_AFTER_REJECT)) {
            if (rejections == null) {
                return new Persuasion();
            }
            boolean rejected = false;
            for (boolean r : rejections) {
                rejected |= r;
            }
            if (!rejected) {
                return new Persuasion();
            }
        }
        return generatePersuasionMove(offer);
	}
	
	/**
	 * Generate a persuasive move based on the robot type
	 * @param offer the trade offer that this robot is about to make
	 * @return the best persuasion move that applies 
	 */
	protected Persuasion generatePersuasionMove(SOCTradeOffer offer){
		
		//Return no persuasion if the robot type is not a persuader or is a persuader who can use no persuasion
		if((!persuaderType) || brain.isRobotType(StacRobotType.PERSUADER_NONE)){
    		return new Persuasion();
    	}
       
		//If the robot type requires the move offer to appear possible based on their understanding on the resource states
		if(brain.isRobotType(StacRobotType.Persuade_ONLY_WHEN_TRADE_LOOKS_POSSIBLE)){
	        boolean persuade = false;
	        boolean to[] = offer.getTo();
	        for (int p = 0; p < game.getPlayers().length; p++) {
	            if (to[p]) {
	                SOCResourceSet opponentRes = brain.getMemory().getOpponentResources(p);
	                SOCResourceSet getRes = offer.getGetSet();
	                if (opponentRes.contains(getRes)) {
	                	persuade = true;
	                    break;
	                }
	            }
	        }
	        // we believe, no opponent can make the trade, so we're done
	        if (!persuade) {
	            return new Persuasion();
	        }
		}
        
        /** Only commit do doing a persuasion iff the persuader can complete the build plan. */
        if (brain.isRobotType(StacRobotType.Persuade_ONLY_TO_GET_ImmediateBuildPlan)) {
    		//Establish the build plan
     		SOCBuildPlanStack buildPlan = brain.getBuildingPlan();
            if (buildPlan == null || buildPlan.getPlanDepth() == 0) {
                brain.getDecisionMaker().planStuff();
                buildPlan = brain.getBuildingPlan();
            }
            
            SOCResourceSet targetResources = getResourcesForPlan(buildPlan);
            SOCResourceSet currentResources = brain.getMemory().getResources();
            
            SOCResourceSet getResources = offer.getGetSet();
            SOCResourceSet giveResources = offer.getGiveSet();
            SOCResourceSet resourcesAfterTrade = currentResources.copy();
            
            resourcesAfterTrade.add(getResources);
            resourcesAfterTrade.subtract(giveResources);
            if (!resourcesAfterTrade.contains(targetResources)) {
                // I can't build immediately after this trade, so I'm done
                return new Persuasion();
            }
        }
		
		int maxNumberOfUnknownsToAssign = 0;
		int numberOfUnknownsToAssign = 0;
		boolean finished=false;
		
		if(brain.isRobotType(StacRobotType.Persuade_Maximum_Number_Of_Unknowns_To_Be_Assigned)){
			maxNumberOfUnknownsToAssign = (Integer)brain.getTypeParam(StacRobotType.Persuade_Maximum_Number_Of_Unknowns_To_Be_Assigned);
        }
		
		//Try and generate a persuasion an iteratively increase the number of unknowns which are assigned
		while(!finished){
			Persuasion bestPersuasion = generatePersuasionMove(offer, numberOfUnknownsToAssign);
			if(bestPersuasion.getIdentifier()!=Persuasion.PersuasionIdentifiers.NullPersuasion){
				return bestPersuasion;
			}
			numberOfUnknownsToAssign++;
			if(numberOfUnknownsToAssign>maxNumberOfUnknownsToAssign){
				finished=true;
			}
		}
		return new Persuasion();
	}
	
	/**
	 * mechanism to generate a persuasion move that can be applied to a subset of the recipients of the offer
	 * @param offer
	 * @return
	 */
	protected Persuasion generatePersuasionMove(SOCTradeOffer offer, int numberOfUnknownsToAssign){
		
		//Try and find a persuasion for all recipients of the offer
		List<Persuasion> persuasionsToUse = new ArrayList<Persuasion>();
		persuasionsToUse.addAll(findAnAppropriatePersuasionMove(offer, numberOfUnknownsToAssign));
		if(persuasionsToUse.size()>0){
			return findOptimalPersuasion(persuasionsToUse);
		}
		
		//Otherwise try to find a persuasion which is applicable for a subset of the recipients
		//TODO stop this being hard coded for 4 players 
		if(!brain.isRobotType(StacRobotType.Persuade_Only_With_Persuasion_That_Applies_to_Each_Recipient)){
			
			SOCTradeOffer tempOffer = new SOCTradeOffer(offer);
			ArrayList<Integer> addressees = new ArrayList<Integer>();
			for(int i = 0;i<tempOffer.getTo().length;i++){
				//Trade is directed to i, but not from i (i is the playerNumber)
				if(tempOffer.getTo()[i] && i!=tempOffer.getFrom()){
					addressees.add(i);
				}
			}
			
			//Randomise the addressees list so as not to automatically give persuasions to certain player numbers more often than others
			ArrayList<Integer> tempAddressees = new ArrayList<Integer>();
			while (addressees.size()>0){
				int val = RANDOM.nextInt(addressees.size());
				tempAddressees.add(addressees.get(val));
				addressees.remove(val);
			}
			addressees = tempAddressees;
			
			if(addressees.size()>3){
				D.ebugERROR("Attempting to generate a persuasion move for more than 3 players - this will not work with the current mechanism");
				return new Persuasion();
			}
			//If addressees ==2 or ==3
			if(addressees.size()>1){
				//Try setting one recipient to false, and generate a persuasion
				for(int i = 0;i<addressees.size();i++){
					tempOffer.getTo()[addressees.get(i)]=false;
					persuasionsToUse.addAll(findAnAppropriatePersuasionMove(tempOffer, numberOfUnknownsToAssign));
					tempOffer.getTo()[addressees.get(i)]=true;
				}
				//If you get a persuasion randomly select (Random applies here)
				if(persuasionsToUse.size()>0){
					return findOptimalPersuasion(persuasionsToUse);
				}
			}
			//if there are three addressees then it might still be worthwhile making a persuasion that applies to one recipient
			if(addressees.size()==3){
				for(int i =0 ;i<tempOffer.getTo().length;i++){
					tempOffer.getTo()[i] = false;
				}
				for(int i =0; i<addressees.size();i++){
					tempOffer.getTo()[addressees.get(i)]=true;
					persuasionsToUse.addAll(findAnAppropriatePersuasionMove(tempOffer, numberOfUnknownsToAssign));
					tempOffer.getTo()[addressees.get(i)]=false;
				}
				if(persuasionsToUse.size()>0){
					return findOptimalPersuasion(persuasionsToUse);
				}
			}
		}
		return new Persuasion();
	}
    
	
    /** 
     * Use the preference over persuasions to return the optimal (or one of the optimal) persuasions associated with the persuasion
     * @param possiblePersuasions
     * @return
     */
    private Persuasion findOptimalPersuasion(List<Persuasion> possiblePersuasionsPreFilter){
    	
    	//Safety measure to ensure NullPersuasions do not get included as a possible persuasion when there are legit persuasions in the list
    	ArrayList<Persuasion> possiblePersuasions = new ArrayList<Persuasion>();    	
    	for(Persuasion p : possiblePersuasionsPreFilter){
    		if(p.getIdentifier()!=Persuasion.PersuasionIdentifiers.NullPersuasion){
    			possiblePersuasions.add(p);
    		}
    		else{
    			D.ebugERROR("Logic for generating persuasions is incorrect - tried to find optimal persuasion with NullPersuasion included.");
    		}
    	}
    	
    	//If there is no persuasion then there is no optimal - This doesnt occur but is here as a safety measure
    	if(possiblePersuasions.isEmpty()){
    		return new Persuasion();
    		
    	}
    	//If there is only persuasion it is automatically optimal
    	else if(possiblePersuasions.size()==1){
    		return possiblePersuasions.get(0);
    	}
    	
    	//For each Persuasion ID which is considered at the same level (starting with the best) If no preference is set it will move to the next part
		//This player type allows for reversing the partial order (to attempt to use the weak persuasions first - and then backtrack
    	if(brain.isRobotType(StacRobotType.Persuade_Choose_Own_Ordering)){
			for(int i =0;i<preferenceOverPersuasions.size();i++){
				HashSet<Persuasion.PersuasionIdentifiers> currentBest = preferenceOverPersuasions.get(i);
				ArrayList<Persuasion> bestPersuasions = new ArrayList<Persuasion>();
				for(Persuasion p : possiblePersuasions){
					if(currentBest.contains(p.getIdentifier())){
						bestPersuasions.add(p);
					}
				}
				if(bestPersuasions.size()>0){
			    	return bestPersuasions.get(RANDOM.nextInt(bestPersuasions.size()));
				}
			}
	    }
		
    	//If no preference is set for the possible persuasions just randomly choose one
    	return possiblePersuasions.get(RANDOM.nextInt(possiblePersuasions.size()));
    }
    
	
    /**
     * Find all persuasions which can be made with this offer and this number of unknowns which can be assigned
     * @param offer 
     * @param numberOfUnknownsToAssign
     * @return
     */
    private ArrayList<Persuasion> findAnAppropriatePersuasionMove(SOCTradeOffer offer, int numberOfUnknownsToAssign) {
    	ArrayList<Persuasion> possiblePersuasions = new ArrayList<Persuasion>();
		
    	//If not a persuader return null persuasion - shouldnt happen
		if(brain.isRobotType(StacRobotType.PERSUADER_NONE) || !persuaderType){
			return possiblePersuasions;
		}
    	
    	//Immediate Build Plan - City
		if(brain.isRobotType(StacRobotType.PERSUADER_ALL) || 
				brain.isRobotType(StacRobotType.PERSUADER_ALL_EXCEPT_AlwaysTrue) ||
				brain.isRobotType(StacRobotType.PERSUADER_ImmediateBuildPlan) ||
				brain.isRobotType(StacRobotType.PERSUADER_ImmediateBuildPlan_City)){
			possiblePersuasions.addAll(getPossiblePersuasion(offer, StacRobotType.PERSUADER_ImmediateBuildPlan_City, numberOfUnknownsToAssign));
		}
		
		//Immediate Build Plan - Settlement 
		if(brain.isRobotType(StacRobotType.PERSUADER_ALL) || 
				brain.isRobotType(StacRobotType.PERSUADER_ALL_EXCEPT_AlwaysTrue) ||
				brain.isRobotType(StacRobotType.PERSUADER_ImmediateBuildPlan) ||
				brain.isRobotType(StacRobotType.PERSUADER_ImmediateBuildPlan_Settlement)){    			
			possiblePersuasions.addAll(getPossiblePersuasion(offer, StacRobotType.PERSUADER_ImmediateBuildPlan_Settlement, numberOfUnknownsToAssign));
		}
		 
		//Immediate Build Plan - Road
		if(brain.isRobotType(StacRobotType.PERSUADER_ALL) || 
				brain.isRobotType(StacRobotType.PERSUADER_ALL_EXCEPT_AlwaysTrue) ||
				brain.isRobotType(StacRobotType.PERSUADER_ImmediateBuildPlan) ||
				brain.isRobotType(StacRobotType.PERSUADER_ImmediateBuildPlan_Road)){    			
			possiblePersuasions.addAll(getPossiblePersuasion(offer, StacRobotType.PERSUADER_ImmediateBuildPlan_Road, numberOfUnknownsToAssign));
		}
		
		//Immediate Build Plan - DevCard
		if(brain.isRobotType(StacRobotType.PERSUADER_ALL) || 
				brain.isRobotType(StacRobotType.PERSUADER_ALL_EXCEPT_AlwaysTrue) ||
				brain.isRobotType(StacRobotType.PERSUADER_ImmediateBuildPlan) ||
				brain.isRobotType(StacRobotType.PERSUADER_ImmediateBuildPlan_DevCard)){    			
			possiblePersuasions.addAll(getPossiblePersuasion(offer, StacRobotType.PERSUADER_ImmediateBuildPlan_DevCard, numberOfUnknownsToAssign));
		}
		
		//Immediate Trade Plan - Port
		if(brain.isRobotType(StacRobotType.PERSUADER_ALL) || 
				brain.isRobotType(StacRobotType.PERSUADER_ALL_EXCEPT_AlwaysTrue) ||
				brain.isRobotType(StacRobotType.PERSUADER_ImmediateTradePlan) ||
				brain.isRobotType(StacRobotType.PERSUADER_ImmediateTradePlanPort)){    			
			possiblePersuasions.addAll(getPossiblePersuasion(offer, StacRobotType.PERSUADER_ImmediateTradePlanPort, numberOfUnknownsToAssign));
		}
		
		//Immediate Trade Plan - Bank
		if(brain.isRobotType(StacRobotType.PERSUADER_ALL) || 
				brain.isRobotType(StacRobotType.PERSUADER_ALL_EXCEPT_AlwaysTrue) ||
				brain.isRobotType(StacRobotType.PERSUADER_ImmediateTradePlan) ||
				brain.isRobotType(StacRobotType.PERSUADER_ImmediateTradePlanBank)){    			
			possiblePersuasions.addAll(getPossiblePersuasion(offer, StacRobotType.PERSUADER_ImmediateTradePlanBank, numberOfUnknownsToAssign));
		}
		
		//Resource Based - TooManyRes
		if(brain.isRobotType(StacRobotType.PERSUADER_ALL) || 
				brain.isRobotType(StacRobotType.PERSUADER_ALL_EXCEPT_AlwaysTrue) ||
				brain.isRobotType(StacRobotType.PERSUADER_ResourceBased) ||
				brain.isRobotType(StacRobotType.PERSUADER_ResourceBasedTooManyRes)){    			
			possiblePersuasions.addAll(getPossiblePersuasion(offer, StacRobotType.PERSUADER_ResourceBasedTooManyRes, numberOfUnknownsToAssign));
		}
		
		//Resource Based - CantGetRes
		if(brain.isRobotType(StacRobotType.PERSUADER_ALL) || 
				brain.isRobotType(StacRobotType.PERSUADER_ALL_EXCEPT_AlwaysTrue) ||
				brain.isRobotType(StacRobotType.PERSUADER_ResourceBased) ||
				brain.isRobotType(StacRobotType.PERSUADER_ResourceBasedCantGetRes)){    			
			possiblePersuasions.addAll(getPossiblePersuasion(offer, StacRobotType.PERSUADER_ResourceBasedCantGetRes, numberOfUnknownsToAssign));
		}
		
		//Always True
		if(brain.isRobotType(StacRobotType.PERSUADER_ALL) || 
				brain.isRobotType(StacRobotType.PERSUADER_AlwaysTrue) || 
				brain.isRobotType(StacRobotType.PERSUADER_AlwaysTrue_RobPromise)){	
			possiblePersuasions.addAll(getPossiblePersuasion(offer, StacRobotType.PERSUADER_AlwaysTrue_RobPromise, numberOfUnknownsToAssign)); 
		}
		
		if(brain.isRobotType(StacRobotType.PERSUADER_ALL) || 
				brain.isRobotType(StacRobotType.PERSUADER_AlwaysTrue) || 
				brain.isRobotType(StacRobotType.PERSUADER_AlwaysTrue_PromiseRes)){	
			possiblePersuasions.addAll(getPossiblePersuasion(offer, StacRobotType.PERSUADER_AlwaysTrue_PromiseRes, numberOfUnknownsToAssign)); 
		}
		
		
		return possiblePersuasions;
    }
    
    /**
     * Return a list of possible persuasions given the argument 
     * @param offer
     * @param argument
     * @param numberOfUnknownsToAssign
     * @return
     */
    private ArrayList<Persuasion> getPossiblePersuasion(SOCTradeOffer offer, String argument, int numberOfUnknownsToAssign){
    	//For all players recieving in the trade, the persuasion must hold
    	//TODO it would be hugely beneficial to use probability for unknown resources here
    	//The current protocol is to assign the next unknown for the player with the most unknowns unassigned currently
    	
    	//Generate the lists of resources before and after the trade for the players involved in the offer
    	HashMap<Integer, SOCResourceSet> mapPnToResAfterTrade = new HashMap<Integer, SOCResourceSet>();
    	HashMap<Integer, SOCResourceSet> mapPnToResBeforeTrade = new HashMap<Integer, SOCResourceSet>();
       	for(int i = 0; i<offer.getTo().length;i++){
       		//If the offer is too i, and not from i add to the data structures
    		if(offer.getTo()[i] && offer.getFrom()!=i){
    			mapPnToResBeforeTrade.put(i,brain.getMemory().getOpponentResources(i).copy());
    			mapPnToResAfterTrade.put(i,opponentResourcesAfterTrade(offer, i).copy());
    		}
    	}
       	
       	ArrayList<Persuasion> results = new ArrayList<Persuasion>();
       	//Maps from player number to a list of current resource types which have been set up
   		HashMap<Integer, ArrayList<Integer>> assignedValues = new HashMap<Integer, ArrayList<Integer>>();
   		if(initializeAssignedValues(numberOfUnknownsToAssign, assignedValues, mapPnToResAfterTrade,mapPnToResBeforeTrade)){
   	   		boolean keepGoing = true;
   	   		//This will stop when a result is achieved due to the fact that this process is exponential
   	   		while(keepGoing && results.size()==0){
   	   			//Try all combinations before deciding the optimal result
   	   			results.addAll(getPossiblePersuasion(offer, argument, mapPnToResAfterTrade,mapPnToResBeforeTrade));
   	   			keepGoing = getNextUnknownAssignments(assignedValues, mapPnToResAfterTrade,mapPnToResBeforeTrade);
   	       	}
   		}
       	
       	return results;
    }

    
    /**
     * Initialize numberToAssign assignments to be Resource number 1 {@link SOCResourceConstants.MIN} for the players with the most unknowns
     * @param numberToAssign
     * @param assignedValues
     * @param mapPnToResAfterTrade
     * @param mapPnToResBeforeTrade
     * @return
     */
     private boolean initializeAssignedValues(int numberToAssign, HashMap<Integer, ArrayList<Integer>> assignedValues, HashMap<Integer, SOCResourceSet> mapPnToResAfterTrade, HashMap<Integer, SOCResourceSet> mapPnToResBeforeTrade){
     	for (int i =0;i<numberToAssign;i++){
     		int playerNo = playerWithMostUnknownsLeft(mapPnToResAfterTrade);
     		if(playerNo!=-1){
 	    		swapResTypeToAnotherResType(mapPnToResAfterTrade, playerNo, SOCResourceConstants.UNKNOWN, SOCResourceConstants.MIN);
 	    		swapResTypeToAnotherResType(mapPnToResBeforeTrade, playerNo, SOCResourceConstants.UNKNOWN, SOCResourceConstants.MIN);
 	    		
 	    		//Use an arraylist to keep track of which values are currently assigned as they must be iterated through in order to make sure all combinations are attempted
 	    		if(assignedValues.containsKey(playerNo)){
 	    			//Have clay as the initial assignment as it is the int value 1
 	    			assignedValues.get(playerNo).add(SOCResourceConstants.MIN);
 	    		}
 	    		else{
 	    			ArrayList<Integer> valuesTakingThePlaceOfUnknowns = new ArrayList<Integer>();
 	    			valuesTakingThePlaceOfUnknowns.add(SOCResourceConstants.MIN);
 	    			assignedValues.put(playerNo, valuesTakingThePlaceOfUnknowns);
 	    		}
     		}
     		else{
     			return false;
     			//Dont let the system try to generate a set of persuasions when it has already tried for all the current UNKNOWN values
     		}
     	}
     	return true;
     }
     
     
     /**
      * Find player with the most unknown valuse in the hashmap mappign player numbers to resource set
      * @param mapPnToResAfterTrade
      * @return
      */
     private int playerWithMostUnknownsLeft(HashMap<Integer, SOCResourceSet> mapPnToResAfterTrade){
     	int playerWithMostUnknowns = -1;
     	int noOfUnknowns = 0;
     	for(int i : mapPnToResAfterTrade.keySet()){
     		if(mapPnToResAfterTrade.get(i).getAmount(SOCResourceConstants.UNKNOWN)>noOfUnknowns){
     			playerWithMostUnknowns = i;
     			noOfUnknowns = mapPnToResAfterTrade.get(i).getAmount(SOCResourceConstants.UNKNOWN);
     		}
     	}
     	return playerWithMostUnknowns;
     }
     
     
	/**
	 * Iterate to the next combination of the assigned unknown values
	 * @param assignedValues
	 * @param mapPnToResAfterTrade
	 * @param mapPnToResBeforeTrade
	 * @return whether or not the cycle has succeeded (TRUE) or finished (FALSE) 
	 */
     private boolean getNextUnknownAssignments(HashMap<Integer, ArrayList<Integer>> assignedValues, HashMap<Integer, SOCResourceSet> mapPnToResAfterTrade, HashMap<Integer, SOCResourceSet> mapPnToResBeforeTrade){
    	 
    	 Iterator itor = assignedValues.keySet().iterator();
    	 
    	 boolean keepAdding = true;
    	 while(itor.hasNext()){
			int currentPlayer = (Integer)itor.next();
			for(int i =0;i<assignedValues.get(currentPlayer).size();i++){
				if(keepAdding){
					if(assignedValues.get(currentPlayer).get(i) != SOCResourceConstants.WOOD){
	    				keepAdding = false;
	    				assignedValues.get(currentPlayer).set(i, assignedValues.get(currentPlayer).get(i)+1);
	    	    		swapResTypeToAnotherResType(mapPnToResBeforeTrade, currentPlayer, assignedValues.get(currentPlayer).get(i)-1, assignedValues.get(currentPlayer).get(i));
	    	    		swapResTypeToAnotherResType(mapPnToResAfterTrade, currentPlayer, assignedValues.get(currentPlayer).get(i)-1, assignedValues.get(currentPlayer).get(i));
	    			}
	    			else{
	    	    		swapResTypeToAnotherResType(mapPnToResBeforeTrade, currentPlayer, SOCResourceConstants.WOOD, SOCResourceConstants.MIN);
	    	    		swapResTypeToAnotherResType(mapPnToResAfterTrade, currentPlayer, SOCResourceConstants.WOOD, SOCResourceConstants.MIN);
	    				assignedValues.get(currentPlayer).set(i, SOCResourceConstants.MIN);
	    			}
				}
			}
			if(!keepAdding){
				//Success - you have added to the correct values
				return true;
			}
		}
		//If you are supposed to add another - then getting the next value has failed
    	return !keepAdding;
	}
    
    
    /**
     * Swap a particular resource type for another in this representation of the res set 
     * @param mapToResSet
     * @param playerNo
     * @param resourceFrom
     * @param resourceTo
     */
    private void swapResTypeToAnotherResType(HashMap<Integer,SOCResourceSet> mapToResSet, int playerNo, int resourceFrom, int  resourceTo){
    	if(mapToResSet.get(playerNo).getAmount(resourceFrom)<1){
    		D.ebugERROR("Error with assigning unknown values to players resource set when considering generating a persuasion. ");
    	}
    	mapToResSet.get(playerNo).subtract(1, resourceFrom);
    	mapToResSet.get(playerNo).add(1, resourceTo);
    }

    /**
     * Return a list of the persuasion move(s) applicable to this particular string argument defined by specific persuasion type string representations in {@link StacRobotType}
     * @param offer
     * @param argument
     * @param mapPnToResAfterTrade
     * @param mapPnToResBeforeTrade
     * @return
     */
    private ArrayList<Persuasion> getPossiblePersuasion(SOCTradeOffer offer, String argument, HashMap<Integer, SOCResourceSet> mapPnToResAfterTrade, HashMap<Integer, SOCResourceSet> mapPnToResBeforeTrade){    	    	
    	ArrayList<Persuasion> possiblePersuasionMoves = new ArrayList<Persuasion>();

       	//Safety measure to filter offers which cannot have a persuasion - shouldn't happen.
       	if(mapPnToResAfterTrade.size()<=0 || mapPnToResBeforeTrade.size()<=0){
       		return possiblePersuasionMoves;
       	}
    	
       	final int ROADNO = 0;
       	final int SETTLEMENTNO = 1;
       	final int CITYNO = 2;
        final int DEVCARDNO = 3;
				
        //////////////////////////////////////////////////////////////////////////////
		//Immediate Build Plans
    	if(argument.equals(StacRobotType.PERSUADER_ImmediateBuildPlan_City) || 
    			argument.equals(StacRobotType.PERSUADER_ImmediateBuildPlan_Settlement) || 
    			argument.equals(StacRobotType.PERSUADER_ImmediateBuildPlan_Road) || 
    			argument.equals(StacRobotType.PERSUADER_ImmediateBuildPlan_DevCard)){
    		int resNumber = -1;
    		if(argument.equals(StacRobotType.PERSUADER_ImmediateBuildPlan_City)){
    			resNumber = CITYNO;
    		}
    		else if(argument.equals(StacRobotType.PERSUADER_ImmediateBuildPlan_Settlement)){
    			resNumber = SETTLEMENTNO;
    		}
    		else if(argument.equals(StacRobotType.PERSUADER_ImmediateBuildPlan_Road)){
    			resNumber = ROADNO;
    		}
    		else{
    			resNumber = DEVCARDNO;
    		}
    		boolean possible = true;
    		//For each player i
    		for(Integer i : mapPnToResAfterTrade.keySet()){
    			boolean playerCanBuild = checkIfPlayerCanBuild(argument, i); //Player can physically perform the build given the worldstate
    			boolean[] piecesAvailbleBeforeTrade = piecesAvailableToBuildWithResources(mapPnToResBeforeTrade.get(i));
    			boolean[] piecesAvailbleAfterTrade = piecesAvailableToBuildWithResources(mapPnToResAfterTrade.get(i));
   				if(!(piecesAvailbleAfterTrade[resNumber] && !piecesAvailbleBeforeTrade[resNumber] && playerCanBuild)){
   					possible = false;
   				}
    		}
    		if(possible){
    			possiblePersuasionMoves.addAll(setConstraintsAndParamsIBP(mapPnToResBeforeTrade, mapPnToResAfterTrade, PersuasionGenerator.getPersuasion(argument)));
    		}
    	}
    	
        //////////////////////////////////////////////////////////////////////////////
    	//Immediate Trade Plans
		else if(argument.equals(StacRobotType.PERSUADER_ImmediateTradePlanBank) || 
				argument.equals(StacRobotType.PERSUADER_ImmediateTradePlanPort)){

			//When bank is false -> we are discussing an immediate port trade
			boolean bank = false;
			
			//Whatever the trade Ratio is the player is still eligible to perform the bank trade
			int maxRatio = 3;
			
			if(argument.equals(StacRobotType.PERSUADER_ImmediateTradePlanBank)){ 
				bank = true; 
				maxRatio = 4;
			}
			
	    	HashMap<Integer,Boolean> canMakePersuasionOnITP = new HashMap<Integer,Boolean>();
			for(int res = SOCResourceConstants.CLAY; res <= SOCResourceConstants.WOOD; res++){
				canMakePersuasionOnITP.put(res, true);
			}
			for(Integer pn : mapPnToResAfterTrade.keySet()){
				for(Integer res : canMakePersuasionOnITP.keySet()){
					if(canMakePersuasionOnITP.get(res)){
		                int tradeRatio = tradeRatioForResource(pn, res);
		                if(tradeRatio<=maxRatio && mapPnToResAfterTrade.get(pn).getAmount(res)>=tradeRatio && 
		                		mapPnToResBeforeTrade.get(pn).getAmount(res)<tradeRatio){
		                	//Do nothing
		                }
		                else{
		                	canMakePersuasionOnITP.put(res,false);
		                }
					}
				}
			}
			//If any value in the hash table remains true, then it must be the case that this argument CAN be made
			for(int res : canMakePersuasionOnITP.keySet()){
				if(canMakePersuasionOnITP.get(res)){
					Persuasion tempPersuasion = new Persuasion(Persuasion.PersuasionIdentifiers.ITP);
					HashMap<String,String> parameters = tempPersuasion.getParameters();
					
					parameters.put(Persuasion.resWildcard, mapResourceNumberToString(res));
					
					if(bank){
						parameters.put(Persuasion.tradeTypeWildcard, "bank");
					}
					else{
						parameters.put(Persuasion.tradeTypeWildcard, "port");
					}
					possiblePersuasionMoves.add(tempPersuasion);
				}
			}
		}
    	
        //////////////////////////////////////////////////////////////////////////////
    	//Resource Based
		else if(argument.equals(StacRobotType.PERSUADER_ResourceBasedTooManyRes)){
			//This rarely occurs because the logic to decide a trade doesnt account for this persuasion. Most often the trade is (1-1) or (2-1) at best
			boolean persuasionPossible = true;
			for(Integer pn : mapPnToResAfterTrade.keySet()){
				if(!(mapPnToResBeforeTrade.get(pn).getTotal()>7 && mapPnToResAfterTrade.get(pn).getTotal()<=7)){
					persuasionPossible = false;
				}
			}
			if(persuasionPossible){
				possiblePersuasionMoves.add(PersuasionGenerator.getPersuasion(argument));
			}
		}
    	
		else if(argument.equals(StacRobotType.PERSUADER_ResourceBasedCantGetRes)){
			//Make a persuasion stating that a player cannot currently access a particular resource
	    	HashMap<Integer,Boolean> canMakePersuasionOnRB = new HashMap<Integer,Boolean>();
			for(int res = SOCResourceConstants.CLAY; res <= SOCResourceConstants.WOOD; res++){
				if(offer.getGiveSet().getAmount(res)>0){
					canMakePersuasionOnRB.put(res, true);
				}
				else{
					canMakePersuasionOnRB.put(res, false);					
				}
			}
			
			for(Integer pn : mapPnToResAfterTrade.keySet()){
				SOCPlayerTracker currentPlayer = brain.getMemory().getPlayerTrackers().get(pn);
				checkIfAnyResourceCannotBeRecievedCurrently(canMakePersuasionOnRB, currentPlayer.getPlayer(), true);
			}
			
			for(int res = SOCResourceConstants.CLAY; res <= SOCResourceConstants.WOOD; res++){
				if(canMakePersuasionOnRB.get(res)){
					Persuasion tempPersuasion = new Persuasion(Persuasion.PersuasionIdentifiers.RBCantGetRes);
					HashMap<String,String> parameters = tempPersuasion.getParameters();
					parameters.put(Persuasion.resWildcard, mapResourceNumberToString(res));
					possiblePersuasionMoves.add(tempPersuasion);
				}
			}
		}
        
        //////////////////////////////////////////////////////////////////////////////
    	//Always True
        else if(argument.equals(StacRobotType.PERSUADER_AlwaysTrue_RobPromise)){
			//For the sake of clarity - these can always occur
    		Persuasion tempPersuasion = new Persuasion(Persuasion.PersuasionIdentifiers.ATRobPromise);
    		possiblePersuasionMoves.add(tempPersuasion);
		}
		else if(argument.equals(StacRobotType.PERSUADER_AlwaysTrue_PromiseRes)){
			//For the sake of clarity - these can always occur
			//Randomly promise to give them some resource
    		Persuasion tempPersuasion = new Persuasion(Persuasion.PersuasionIdentifiers.ATPromiseResource);
			HashMap<String,String> parameters = tempPersuasion.getParameters();
			parameters.put(Persuasion.resWildcard, mapResourceNumberToString(RANDOM.nextInt(5)+1));
    		possiblePersuasionMoves.add(tempPersuasion);
		}
		else{
    		D.ebugERROR("Incorrect Robot Type : "+argument);
		}
    	return possiblePersuasionMoves;
    }
    
    /** 
     * For all the possible types of constraints, assign them as true or false, and adjust the associated parameters when doing so
     * Just for IBP persuasions
     * @param mapPnToResBeforeTrade 
     * @param mapPnToResAfterTrade
     * @param originalPersuasion
     * @return
     */
    private ArrayList<Persuasion> setConstraintsAndParamsIBP(HashMap<Integer,SOCResourceSet> mapPnToResBeforeTrade, HashMap<Integer,SOCResourceSet> mapPnToResAfterTrade, Persuasion originalPersuasion){
    	ArrayList<Persuasion> listOfPossiblePersuasions = new ArrayList<Persuasion>();
    	HashMap<String,String> constraints = originalPersuasion.getConstraints();
    	HashMap<String,String> parameters = originalPersuasion.getParameters();
    	for(String cons : constraints.keySet()){
    		constraints.put(cons, "true");
	    	for(Integer pn : mapPnToResBeforeTrade.keySet()){
	    		//If it the constraint is still possibly set to true
	    		if(constraints.get(cons).equals("true")){
	    			SOCPlayerTracker playerTracker = brain.getMemory().getPlayerTrackers().get(pn);
	    			if(!constraintApplies(cons, playerTracker, parameters)){
	    				constraints.put(cons, "false");
	    			}
	    		}
	    	}
    	}
    	
    	//This is currently as a special case as these two constraints are dependent
    	if((constraints.containsKey(Persuasion.longestRoadConstraint) && constraints.containsKey(Persuasion.newSettlementConstraint))&&
    			(constraints.get(Persuasion.longestRoadConstraint).equals("true") && constraints.get(Persuasion.newSettlementConstraint).equals("true"))){
    		boolean canPerformBothStatementsTogether = true;
    		for(Integer pn : mapPnToResBeforeTrade.keySet()){
    			SOCPlayerTracker playerTracker = brain.getMemory().getPlayerTrackers().get(pn);
    			int N = 0;
            	for(int node : playerTracker.getPlayer().getLegalSettlements()){
            		if(playerTracker.getPlayer().isPotentialSettlement(node)){
            			N++;
            		}
            	}
        	    ArrayList<Integer> potentialRoads = new ArrayList<Integer>();
                for (int i = game.getBoard().getMinNode(); i <= SOCBoard.MAXNODE; i++){
                	if(playerTracker.getPlayer().isPotentialRoad(i)){
                		potentialRoads.add(i);
                	}
                }  
                
    			SOCPlayer playerWithLongestRoad = playerTracker.getPlayer().getGame().getPlayerWithLongestRoad();
	    		int currentLongestRoad = -1;
	    		if(playerWithLongestRoad!=null){
	    			currentLongestRoad = playerWithLongestRoad.getLongestRoadLength();
	    		}
	    		int oldLRLength = playerTracker.getPlayer().getLongestRoadLength();

        		for(int co : potentialRoads){
        			boolean doesAllowBothArgs = false;
        			SOCPlayer dummy = new SOCPlayer(playerTracker.getPlayer());
        	        SOCPossibleRoad spb = new SOCPossibleRoad(dummy, co, new Vector());
        	        SOCRoad tempRoad = new SOCRoad(dummy, spb.getCoordinates(), game.getBoard());
        	        dummy.putPiece(tempRoad);
        	    	int M = 0;
        	    	for(int node : dummy.getLegalSettlements()){
        	    		if(dummy.isPotentialSettlement(node)){
        	    			M++;
        	    		}
        	    	}	      
        	    	if(M>N){
        	    		int newLRLength = dummy.calcLongestRoad2();
        	    		        	    		
        	    		if(parameters.get(Persuasion.longestRoadTypeWildcard).equals("keep")){
        	    			if(playerWithLongestRoad!=null && playerWithLongestRoad.getPlayerNumber()==dummy.getPlayerNumber() && newLRLength>oldLRLength){
        	    				doesAllowBothArgs = true;
        	    			}
        	    		}
        	    		else if(parameters.get(Persuasion.longestRoadTypeWildcard).equals("get")){
        	    			if((playerWithLongestRoad==null && newLRLength>=5 && newLRLength>oldLRLength)||(playerWithLongestRoad!=null && playerWithLongestRoad.getPlayerNumber()!=dummy.getPlayerNumber() && newLRLength>currentLongestRoad)){
        	    				doesAllowBothArgs = true;
        	    			}
        	    		}
        	    		else if(parameters.get(Persuasion.longestRoadTypeWildcard).equals("equal")){
        	    			if(playerWithLongestRoad!=null && currentLongestRoad==newLRLength && newLRLength>oldLRLength){
        	    				doesAllowBothArgs = true;
        	    			}
        	    		}
        	    	}
          	        dummy.removePiece(tempRoad);
    		        dummy.destroyPlayer();
            		if(doesAllowBothArgs){
            			canPerformBothStatementsTogether = true;
            		}
            	}
    		}
    		
    		//If the two moves cannot apply together then split them
    		if(!canPerformBothStatementsTogether){
    			//They can both still be done individually
    			Persuasion tempPersuasion1 = new Persuasion();
    			Persuasion tempPersuasion2 = new Persuasion();
    			HashMap<String, String> constraints1 = tempPersuasion1.getConstraints();
    			HashMap<String, String> constraints2 = tempPersuasion1.getConstraints();
    			HashMap<String, String> parameters1 = tempPersuasion1.getParameters();
    			HashMap<String, String> parameters2 = tempPersuasion1.getParameters();
    			constraints1.put(Persuasion.longestRoadConstraint, constraints.get(Persuasion.longestRoadConstraint));
    			constraints2.put(Persuasion.newSettlementConstraint, constraints.get(Persuasion.newSettlementConstraint));
    			for(String p : parameters.keySet()){
    				parameters1.put(p, parameters.get(p));
    				parameters2.put(p, parameters.get(p));
    			}
    			listOfPossiblePersuasions.add(tempPersuasion1);
    			listOfPossiblePersuasions.add(tempPersuasion2);
    		}
    	}
    	
    	//The way the parameters and constraints are set does not guarantee the this constraint is set accurately.
    	//It means that all the recipients have at least one resource that this persuasion is applicable for
    	if(constraints.keySet().contains(Persuasion.accessNewResourceConstraint) && 
    			constraints.get(Persuasion.accessNewResourceConstraint).equals("true")&& 
    			mapPnToResBeforeTrade.size()>1){
    		HashMap<Integer,Boolean> makeArgumentOnResType = new HashMap<Integer,Boolean>();
			for(int res = SOCResourceConstants.CLAY; res <= SOCResourceConstants.WOOD; res++){
				makeArgumentOnResType.put(res, true);
			}
			
			//Check if each recipient shares a resource type which the argument is applicable for
			for(int pn : mapPnToResBeforeTrade.keySet()){
				SOCPlayerTracker currentPlayer = brain.getMemory().getPlayerTrackers().get(pn);
				checkIfAnyResourceCannotBeRecievedCurrently(makeArgumentOnResType, currentPlayer.getPlayer(), false);
			}
			boolean addedAtLeastOne = false;
			//If there is such a resource then make the persuasion and add it to the list
			for(int i : makeArgumentOnResType.keySet()){
				if(makeArgumentOnResType.get(i)){
					Persuasion tempPersuasion = new Persuasion(Persuasion.PersuasionIdentifiers.IBPSettlement);
					HashMap<String,String> tempConstraints = tempPersuasion.getConstraints();
					tempConstraints = constraints;
					HashMap<String,String> tempParameters = tempPersuasion.getParameters();
					tempParameters = parameters;
					tempParameters.put(Persuasion.resWildcard, mapResourceNumberToString(i));
					listOfPossiblePersuasions.add(tempPersuasion);
					addedAtLeastOne = true;
				}
			}
			if(!addedAtLeastOne){
				constraints.put(Persuasion.accessNewResourceConstraint, "false");
				//Will be automatically added later on
			}
    	}
    	
    	//If no stronger persuasion can be added to the list then just use the original persuasion.
    	if(listOfPossiblePersuasions.size()==0){
    		listOfPossiblePersuasions.add(originalPersuasion);
    	}
    	
    	return listOfPossiblePersuasions;
    }
    
    /** Consider whether or not the constraint applies in the current game state
     * 
     * @param pn the player number the recipient
     * @param constraintID unique ID of the constraint
     * @param currentPlayer player tracker of recipient of persuasion
     * @param parameters parameters for the persuasion
     * @return
     */
    private boolean constraintApplies(String constraintID, SOCPlayerTracker currentPlayer, HashMap<String,String> parameters){
    	switch (constraintID){
    	//Constraint checking if the recipient will attain/equal or get the longest road card if they build a road
    	case Persuasion.longestRoadConstraint:
    		SOCPlayer playerWithLongestRoad = brain.getGame().getPlayerWithLongestRoad();
    		int currentLongestRoad = -1;
    		if(playerWithLongestRoad!=null){
    			currentLongestRoad = playerWithLongestRoad.getLongestRoadLength();
    		}
    		
    		int oldLRLength = currentPlayer.getPlayer().getLongestRoadLength();
    		int newLRLength = calculateMaxLengthOfRoadAfterAddingOne(currentPlayer.getPlayer());
   
    		int LONGESTROADBASELINE = 5;
    		
    		boolean currentHolder = ((currentLongestRoad!=-1) && playerWithLongestRoad.getPlayerNumber()==currentPlayer.getPlayer().getPlayerNumber());
    		
    		//Either no one has LR now and the new road will be length 5 or larger
    		//OR !currentHolder, but someone is, and the new road length>currentLongestRoad
    		//OR currentHolder and growing road length by adding a new road
    		boolean willBeHolderAfterNewRoad = ((currentLongestRoad==-1 && newLRLength>=LONGESTROADBASELINE)||
    				(!currentHolder && currentLongestRoad!=-1 && newLRLength>currentLongestRoad) || 
    				(currentHolder && newLRLength>oldLRLength));
    		
    		if(currentHolder && willBeHolderAfterNewRoad){
    			//Must be directed at one player
    			if(parameters.get(Persuasion.longestRoadTypeWildcard).equals(Persuasion.parametersDefault)){
    				parameters.put(Persuasion.longestRoadTypeWildcard, "keep");
    				return true;
    			}
    		}
    		else if(!currentHolder && willBeHolderAfterNewRoad){
    			//This can be possible for more than 1 player
    			if(parameters.get(Persuasion.longestRoadTypeWildcard).equals(Persuasion.parametersDefault) ||
    					parameters.get(Persuasion.longestRoadTypeWildcard).equals("get")){
    				parameters.put(Persuasion.longestRoadTypeWildcard, "get");
    				return true;
    			}
    		}
    		else if(!currentHolder && playerWithLongestRoad!=null && newLRLength==currentLongestRoad){
    			if(parameters.get(Persuasion.longestRoadTypeWildcard).equals(Persuasion.parametersDefault) ||
    					parameters.get(Persuasion.longestRoadTypeWildcard).equals("equal")){
    				parameters.put(Persuasion.longestRoadTypeWildcard, "equal");
    				return true;
    			}
    		}
    		return false;
    		
    	//It is possible that returning true for all recipients for this constraint - the constraint might still not apply due to requiring an agreeable resource type
    	case Persuasion.accessNewResourceConstraint:
    		HashMap<Integer,Boolean> makeArgumentOnResType = new HashMap<Integer,Boolean>();
    		for(int res = SOCResourceConstants.CLAY; res <= SOCResourceConstants.WOOD; res++){
    			makeArgumentOnResType.put(res, true);
    		}
    		//Third argument is false because the claim is that the person has access to a new resource they havent had yet
    		//I imagine it will only apply near the start of the game.
    		checkIfAnyResourceCannotBeRecievedCurrently(makeArgumentOnResType, currentPlayer.getPlayer(), false);
    		
    		//Generate a list of potential settlements - one which can be built without any other buildings
            ArrayList<Integer> potentialSettlements = new ArrayList<Integer>();
            for (int i = game.getBoard().getMinNode(); i <= SOCBoard.MAXNODE; i++){
            	if(currentPlayer.getPlayer().isPotentialSettlement(i)){
            		potentialSettlements.add(i);
            	}
            }  
            //Cycle through the list of possible settlements until you find a settlement which enables this extension. 
    		for(int co : potentialSettlements){
    			//Create a new dummy player that is the replica of the current players
    	        SOCPlayer dummy = new SOCPlayer(currentPlayer.getPlayer());
    	        
    	        //Generate and place this potentialSettlement
    	        SOCPossibleSettlement sps = new SOCPossibleSettlement(dummy, co, new Vector());
    	        SOCSettlement tempSettlement = new SOCSettlement(dummy, sps.getCoordinates(), game.getBoard());
    	        dummy.putPiece(tempSettlement);
    	        
    	        //Check which resources the player now has access to and if there is a new one, then accessNewRes is a valid constraint.
    	    	HashMap<Integer,Boolean> makeArgumentOnResTypeAFTER = new HashMap<Integer,Boolean>();
    			for(int res = SOCResourceConstants.CLAY; res <= SOCResourceConstants.WOOD; res++){
    				makeArgumentOnResTypeAFTER.put(res, true);
    			}
    			checkIfAnyResourceCannotBeRecievedCurrently(makeArgumentOnResTypeAFTER, dummy, false);
    			
    	        dummy.removePiece(tempSettlement);
		        dummy.destroyPlayer();
    			
		        for(int res : makeArgumentOnResTypeAFTER.keySet()){
    				if(!makeArgumentOnResTypeAFTER.get(res) && makeArgumentOnResType.get(res)){
    					parameters.put(Persuasion.resWildcard, mapResourceNumberToString(res));
    					return true;
    				}
    			}
    		}
    		return false;
    		
    	//Constraint when the recipient can get the largest army if they get a dev card which is a knight
    	case Persuasion.largestArmyConstraint:
    		SOCPlayer playerWithLargestArmy = brain.getGame().getPlayerWithLargestArmy();
    		int currentLargestArmy = -1;
    		if(playerWithLargestArmy!=null){
    			currentLargestArmy = playerWithLargestArmy.getNumKnights();
    		}
    		boolean currentLAHolder = ((currentLargestArmy!=-1) && (currentPlayer.getPlayer().getPlayerNumber() == playerWithLargestArmy.getPlayerNumber()));
    		
    		int oldLALength = currentPlayer.getPlayer().getNumKnights();
    		int newLALength = oldLALength+1; //Suggesting that if they get knight they will get closer to the longest army card
   
    		if(currentLAHolder){
    			//Must be directed at only 1 player
    			if(parameters.get(Persuasion.largestArmyTypeWildcard).equals(Persuasion.parametersDefault)){
    				parameters.put(Persuasion.largestArmyTypeWildcard, "keep");
    				return true;
    			}
    		}
    		else if(!currentLAHolder && newLALength>oldLALength && newLALength>=3){
    			//This can be possible for more than 1 player
    			if(parameters.get(Persuasion.largestArmyTypeWildcard).equals(Persuasion.parametersDefault) ||
    					parameters.get(Persuasion.largestArmyTypeWildcard).equals("get")){
    				parameters.put(Persuasion.largestArmyTypeWildcard, "get");
    				return true;
    			}
    		}
    		else if(!currentLAHolder && newLALength==currentLargestArmy){
    			if(parameters.get(Persuasion.largestArmyTypeWildcard).equals(Persuasion.parametersDefault) ||
    					parameters.get(Persuasion.largestArmyTypeWildcard).equals("equal")){
    				parameters.put(Persuasion.largestArmyTypeWildcard, "equal");
    				return true;
    			}
    		}
    		return false;
    		
    	case Persuasion.victoryPointsConstraint:
    		int oldTotalVPs = currentPlayer.getPlayer().getPublicVP();
    		int newTotalVPs = oldTotalVPs+1; //Under the assumption this move gives the opponent 1 VP
    		
    		int highestVPs = 0;
    		SOCPlayer[] currentVPOfAllPlayers = currentPlayer.getPlayer().getGame().getPlayers();
    		for(SOCPlayer sp : currentVPOfAllPlayers){
    			if(currentPlayer.getPlayer().getPlayerNumber() != sp.getPlayerNumber()){
    				if(sp.getPublicVP()>highestVPs){
    					highestVPs = sp.getPublicVP();
    				}
    			}
    		}
    		if(oldTotalVPs>highestVPs && parameters.get(Persuasion.victoryPointsTypeWildcard).equals(Persuasion.parametersDefault)){
    			parameters.put(Persuasion.victoryPointsTypeWildcard, "keep");
    			return true;
    		}
    		else if(newTotalVPs>highestVPs && (parameters.get(Persuasion.victoryPointsTypeWildcard).equals(Persuasion.parametersDefault) || 
    				parameters.get(Persuasion.victoryPointsTypeWildcard).equals("get"))){
    			parameters.put(Persuasion.victoryPointsTypeWildcard, "get");
    			return true;
    		}
    		else if(newTotalVPs==highestVPs && (parameters.get(Persuasion.victoryPointsTypeWildcard).equals(Persuasion.parametersDefault) || 
    				parameters.get(Persuasion.victoryPointsTypeWildcard).equals("equal"))){
    			parameters.put(Persuasion.victoryPointsTypeWildcard, "equal");
    			return true;
    		}
    		return false;
    		
    	case Persuasion.newSettlementConstraint:
        	int N = 0;
        	for(int node : currentPlayer.getPlayer().getLegalSettlements()){
        		if(currentPlayer.getPlayer().isPotentialSettlement(node)){
        			N++;
        		}
        	}
    	    ArrayList<Integer> potentialRoads = new ArrayList<Integer>();
            for (int i = game.getBoard().getMinNode(); i <= SOCBoard.MAXNODE; i++){
            	if(currentPlayer.getPlayer().isPotentialRoad(i)){
            		potentialRoads.add(i);
            	}
            }  
    		for(int co : potentialRoads){
    			SOCPlayer dummy = new SOCPlayer(currentPlayer.getPlayer());
    	        SOCPossibleRoad spb = new SOCPossibleRoad(dummy, co, new Vector());
    	        SOCRoad tempRoad = new SOCRoad(dummy, spb.getCoordinates(), game.getBoard());
    	        dummy.putPiece(tempRoad);
    	    	int M = 0;
    	    	for(int node : dummy.getLegalSettlements()){
    	    		if(dummy.isPotentialSettlement(node)){
    	    			M++;
    	    		}
    	    	}	        if(M>N){
    	        	return true;
    	        }
    	        dummy.removePiece(tempRoad);
		        dummy.destroyPlayer();
    		}
    	    return false;
            
    	default:
    		D.ebugERROR("Attempted to generate a constraint for a non existing constraint type: "+constraintID);
    		break;
    	}
    	return false;
    	
    }
    
    
    /********************************************************************
     * Consider offer Half of PersuasionStacRobotNegotiator
     */
    
    
    @Override
    public int considerOffer(SOCTradeOffer offer, int receiverNum)
    { 
    	if(persuasionRecipientType){
            return considerOfferPERSUASION(offer, receiverNum, new Persuasion());
       	}
    	else{
            return considerOfferNONPERSUASION(offer, receiverNum, false);
    	}
    }
    
    /**
     * The actual code for executing {@link #considerOffer(SOCTradeOffer, int)} with Persuasive Robots
     * @param offer
     * @param receiverNum
     * @param forced whether the trade is declared as forced
     * @param persuasiveArgument
     * @return
     */
    public int considerOfferPERSUASION(SOCTradeOffer offer, int receiverNum, Persuasion persuasiveArgument)
    {	    	
        // Use different functions to evaluate our own responses and to guess our opponent's response.  Call the wrapper function,
        //  which checks legal trade, then passes to a (possibly overridden) evaluating function.
        if (receiverNum == brain.getPlayerNumber()) {
            if (brain.getMemory().isBlockingTrades(offer.getFrom())) {
                return REJECT_OFFER;
            }
            else {
                return considerOfferToMeWrapperPERSUASION(offer, persuasiveArgument);
            }
        }
        else {
            return guessOpponentResponseWrapper(offer, receiverNum);
        }
    }
    
    /**
     *  Simple wrapper - check we have resources, then call the implementation of the trade consideration function
     *  with Persuasion
     * @param offer
     * @param forced
     * @param persuasiveArgument
     * @return
     */
    private int considerOfferToMeWrapperPERSUASION(SOCTradeOffer offer, Persuasion persuasiveArgument) {		
    	//update buildplan
 		SOCBuildPlanStack buildPlan = brain.getBuildingPlan();
        if (buildPlan == null || buildPlan.getPlanDepth() == 0) {
            brain.getDecisionMaker().planStuff();
            buildPlan = brain.getBuildingPlan();
        }

        
        SOCResourceSet myCurrentResources = brain.getMemory().getResources().copy();
        
        // determine my resources after the trade - if I think I cant do the trade ignore the persuasion (shouldnt happen)
        SOCResourceSet resourcesAfterTrade = myCurrentResources.copy();
        resourcesAfterTrade.add(offer.getGiveSet());
        resourcesAfterTrade.subtract(offer.getGetSet());
    	
        //Consider whether the trade itself is legitimate to even consider
    	if (!isLegal(offer, brain.getPlayerNumber(), true) || brain.getMemory().isPlayerEmbargoed(offer.getFrom())) {
            return REJECT_OFFER;
        }
    	
    	//When a player cannot generate a persuasive move then a nullPersuasion will be send, and this must be considered as not applying a persuasive move
        if(persuasiveArgument.getIdentifier().equals(Persuasion.PersuasionIdentifiers.NullPersuasion)){
        	return considerOfferToMePERSUASION(offer, persuasiveArgument);
        }
        

        //Consider whether the persuader has a VP higher than threshold - if so, ignore the persuasion and consider the trade independently
        if(brain.isRobotType(StacRobotType.Persuasion_RESP_IGNORE_IF_PERSUADER_HAS_X_VPS)){
        	if((Integer)brain.getTypeParam(StacRobotType.Persuasion_RESP_IGNORE_IF_PERSUADER_HAS_X_VPS)<=brain.getPlayerTrackers().get(offer.getFrom()).getPlayer().getPublicVP()){
            	return considerOfferToMePERSUASION(offer, persuasiveArgument);
        	}
        } 
         
        double acceptATPersuasions = (double)1; //Set to 1, and is only considered if the robot type is one which will consider AT persuasions
        if(brain.isRobotType(StacRobotType.Persuasion_RESP_ACCEPT_AlwaysTrue_With_Probability)){
        	acceptATPersuasions = (Double)brain.getTypeParam(StacRobotType.Persuasion_RESP_ACCEPT_AlwaysTrue_With_Probability);
        }
        
        //Robot type which allows for a random decision to simply ignore a persuasive move.
        //Probability 1 will always consider the persuasion (DEFAULT)
        //Probability X will consider the persuasion if the random number generated is less than or equal to X
        if(brain.isRobotType(StacRobotType.Persuasion_RESP_Accept_Consistent_Args_With_Probability)){
        	double acceptConsistentArgs = (Double)brain.getTypeParam(StacRobotType.Persuasion_RESP_Accept_Consistent_Args_With_Probability);
            if(acceptConsistentArgs < RANDOM.nextDouble()){
            	//Ignore the persuasion and consider the offer alone
            	return considerOfferToMePERSUASION(offer, persuasiveArgument);
            }
        }
        
        //If the robot type only considers persuasion iff it is about current build plan
        if(brain.isRobotType(StacRobotType.Persuasion_RESP_ONLY_IF_FULFILS_CURRENT_BUILD_PLAN)){
            if(!( (SOCPossiblePiece.CARD==buildPlan.firstElement().getType() && persuasiveArgument.getIdentifier()==Persuasion.PersuasionIdentifiers.IBPDevCard) ||
            		 (SOCPossiblePiece.CITY==buildPlan.firstElement().getType() && persuasiveArgument.getIdentifier()==Persuasion.PersuasionIdentifiers.IBPCity) ||
            		 (SOCPossiblePiece.ROAD==buildPlan.firstElement().getType() && persuasiveArgument.getIdentifier()==Persuasion.PersuasionIdentifiers.IBPRoad) ||
            		 (SOCPossiblePiece.SETTLEMENT==buildPlan.firstElement().getType() && persuasiveArgument.getIdentifier()==Persuasion.PersuasionIdentifiers.IBPSettlement) )){
            	return considerOfferToMePERSUASION(offer, persuasiveArgument);
            }	
        }
        
        //If the offerer has higher visible VP than own Total VPs then automatically ignore the persuasion (still consider the trade)
        if(brain.isRobotType(StacRobotType.Persuasion_RESP_IGNORE_PERSUASIONS_OF_PLAYERS_WITH_HIGHER_VP) && 
        		brain.getGame().getPlayer(offer.getFrom()).getPublicVP()>brain.getOurPlayerData().getPublicVP()){
        	return considerOfferToMePERSUASION(offer, persuasiveArgument);
        }
        
        
        //If the offerer has highest visible VP then automatically ignore the persuasion (still consider the trade)
        if(brain.isRobotType(StacRobotType.Persuasion_RESP_IGNORE_PERSUASIONS_OF_PLAYERS_WITH_HIGHEST_VP)){
	        int max = brain.getGame().getPlayer(brain.getPlayerNumber()).getPublicVP();
        	for (int p = 0; p < game.getPlayers().length; p++) {
        		if(brain.getGame().getPlayer(p).getPublicVP()>max){
        			max = brain.getGame().getPlayer(p).getPublicVP();
        		}
        	}
        	if(brain.getGame().getPlayer(offer.getFrom()).getPublicVP() == max){
        		return considerOfferToMePERSUASION(offer, persuasiveArgument);
        	}
        }
        
        
        //Ignore persuasion if opponent gets an immediate build benefit.
        if(brain.isRobotType(StacRobotType.Persuasion_RESP_IGNORE_IF_PERSUADER_TO_GET_ImmediateBuildPlan)){
        	boolean[] piecesAvailbleBeforeTrade = piecesAvailableToBuildWithResources(brain.getMemory().getOpponentResources(offer.getFrom()).copy());
        	boolean[] piecesAvailbleAfterTrade = piecesAvailableToBuildWithResources(opponentResourcesAfterTrade(offer, offer.getFrom()).copy());
        	for(int i =0;i<piecesAvailbleBeforeTrade.length;i++){
        		if(piecesAvailbleAfterTrade[i] && !piecesAvailbleBeforeTrade[i]){
                	return considerOfferToMePERSUASION(offer, persuasiveArgument);
        		}
        	}
        }
               
        final int ROADNO = 0;
        final int SETTLEMENTNO = 1;
        final int CITYNO = 2;
        final int DEVCARDNO = 3;
        
        ////////////////////////////////////////////////////////////////////////////////////
        //Immediate Build Plan persuasions
        if (persuasiveArgument.getIdentifier().equals(Persuasion.PersuasionIdentifiers.IBPRoad)||
        		persuasiveArgument.getIdentifier().equals(Persuasion.PersuasionIdentifiers.IBPSettlement)||
        		persuasiveArgument.getIdentifier().equals(Persuasion.PersuasionIdentifiers.IBPCity)||
        		persuasiveArgument.getIdentifier().equals(Persuasion.PersuasionIdentifiers.IBPDevCard)) {
        	
        	int IBPNO;
        	boolean possible = checkIfICanBuild(persuasiveArgument.getIdentifier());
 
            boolean[] piecesAvailbleBeforeTrade = piecesAvailableToBuildWithResources(myCurrentResources);
            boolean[] piecesAvailbleAfterTrade = piecesAvailableToBuildWithResources(resourcesAfterTrade);
        	
        	if(persuasiveArgument.getIdentifier().equals(Persuasion.PersuasionIdentifiers.IBPRoad)){
        		IBPNO = ROADNO;
        	}
        	else if(persuasiveArgument.getIdentifier().equals(Persuasion.PersuasionIdentifiers.IBPSettlement)){
        		IBPNO = SETTLEMENTNO;
        	}
        	else if(persuasiveArgument.getIdentifier().equals(Persuasion.PersuasionIdentifiers.IBPCity)){
        		IBPNO = CITYNO;
        	}
        	else{
        		IBPNO = DEVCARDNO;
        	}
    		possible = possible && piecesAvailbleAfterTrade[IBPNO] && !piecesAvailbleBeforeTrade[IBPNO];
    		if(brain.isRobotType(StacRobotType.PERSUASION_RESP_ALL) || 
        			brain.isRobotType(StacRobotType.PERSUASION_RESP_ALL_EXCEPT_AlwaysTrue) || 
        			brain.isRobotType(StacRobotType.PERSUASION_RESP_ImmediateBuildPlan)){
    			if(possible && checkIfPersuasionConstraintsHolds(persuasiveArgument)){
        			return processAcceptedPersuasion(offer,true);
    			}
    		}
    		else if((brain.isRobotType(StacRobotType.PERSUASION_RESP_ImmediateBuildPlan_Road) && IBPNO == ROADNO)||
    				(brain.isRobotType(StacRobotType.PERSUASION_RESP_ImmediateBuildPlan_Settlement) && IBPNO == SETTLEMENTNO)||
    				(brain.isRobotType(StacRobotType.PERSUASION_RESP_ImmediateBuildPlan_City) && IBPNO == CITYNO)||
    				(brain.isRobotType(StacRobotType.PERSUASION_RESP_ImmediateBuildPlan_DevCard) && IBPNO == DEVCARDNO)){
     			if(possible && checkIfPersuasionConstraintsHolds(persuasiveArgument)){
        			return processAcceptedPersuasion(offer,true);
    			}
    		}
        }
        
        ////////////////////////////////////////////////////////////////////////////////////
        //Immediate Trade Plan persuasions
        else if (persuasiveArgument.getIdentifier().equals(Persuasion.PersuasionIdentifiers.ITP)) {
        	
        	HashMap<String, String> parameters = persuasiveArgument.getParameters();
        	
			int res = mapStringToResourceNumber(parameters.get(Persuasion.resWildcard));
			
			//If the resource is properly established
			if(res!=-1){
				int tradeRatioForRes = tradeRatioForResource(brain.getPlayerNumber(),res);
				
				if((parameters.get(Persuasion.tradeTypeWildcard).equals("bank") && tradeRatioForRes<=4)||
					(parameters.get(Persuasion.tradeTypeWildcard).equals("port") && tradeRatioForRes<=3)){
					if(myCurrentResources.getAmount(res)<tradeRatioForRes && resourcesAfterTrade.getAmount(res)>=tradeRatioForRes){
			        	if(brain.isRobotType(StacRobotType.PERSUASION_RESP_ALL) || 
			        			brain.isRobotType(StacRobotType.PERSUASION_RESP_ALL_EXCEPT_AlwaysTrue) || 
			        			brain.isRobotType(StacRobotType.PERSUASION_RESP_ImmediateTradePlan)){
		        			return processAcceptedPersuasion(offer,true);
			        	}
			        	else if((brain.isRobotType(StacRobotType.PERSUASION_RESP_ImmediateTradePlanBank) && tradeRatioForRes<=4)||
			        			(brain.isRobotType(StacRobotType.PERSUASION_RESP_ImmediateTradePlanPort) && tradeRatioForRes<=3)){
		        			return processAcceptedPersuasion(offer,true);
			        	}
					}
				}
			}
        }
        
        /////////////////////////////////////////////////////////////////////////////////////////////////////
        //Resource Based arguments
        else if(persuasiveArgument.getIdentifier().equals(Persuasion.PersuasionIdentifiers.RBTooManyRes)){
        	int totalBefore = myCurrentResources.getTotal();
        	int totalAfter = resourcesAfterTrade.getTotal();
			if(brain.isRobotType(StacRobotType.PERSUASION_RESP_ResourceBasedTooManyRes) || 
        			brain.isRobotType(StacRobotType.PERSUASION_RESP_ResourceBased) ||
        			brain.isRobotType(StacRobotType.PERSUASION_RESP_ALL_EXCEPT_AlwaysTrue) || 
        			brain.isRobotType(StacRobotType.PERSUASION_RESP_ALL)){
				if(totalBefore>7 && totalAfter<=7){
        			return processAcceptedPersuasion(offer,true);
				}
			}
        }
        
        else if(persuasiveArgument.getIdentifier().equals(Persuasion.PersuasionIdentifiers.RBCantGetRes)){
        	int res = mapStringToResourceNumber(persuasiveArgument.getParameters().get(Persuasion.resWildcard));
        	if(res!=-1){
        		HashMap<Integer,Boolean> canIGetThisResource = new HashMap<Integer,Boolean>();
        		canIGetThisResource.put(res, true);
        		checkIfAnyResourceCannotBeRecievedCurrently(canIGetThisResource, brain.getOurPlayerData(), true);
        		if(canIGetThisResource.get(res)){
        			if(brain.isRobotType(StacRobotType.PERSUASION_RESP_ResourceBasedCantGetRes) || 
                			brain.isRobotType(StacRobotType.PERSUASION_RESP_ResourceBased) || 
                			brain.isRobotType(StacRobotType.PERSUASION_RESP_ALL_EXCEPT_AlwaysTrue) || 
                			brain.isRobotType(StacRobotType.PERSUASION_RESP_ALL)){
            			return processAcceptedPersuasion(offer,true);
        			}
        		}
        	}
        }
        
        /////////////////////////////////////////////////////////////////////////////////////////////////////
        //Always True arguments
        else if (persuasiveArgument.getIdentifier().equals(Persuasion.PersuasionIdentifiers.ATRobPromise)) {
        	//When acceptATPersuasions is 1, this will always accept as R.nextDouble is up to 1 (exclusive).
        	if(acceptATPersuasions > RANDOM.nextDouble()){ 
	        	if(brain.isRobotType(StacRobotType.PERSUASION_RESP_AlwaysTrue_RobPromise) || 
	        			brain.isRobotType(StacRobotType.PERSUASION_RESP_AlwaysTrue) || 
	        			brain.isRobotType(StacRobotType.PERSUASION_RESP_ALL)){
        			return processAcceptedPersuasion(offer,true);
	        	}
        	}
        }
        else if (persuasiveArgument.getIdentifier().equals(Persuasion.PersuasionIdentifiers.ATPromiseResource)) {
        	if(acceptATPersuasions > RANDOM.nextDouble()){ 
        		if(brain.isRobotType(StacRobotType.PERSUASION_RESP_AlwaysTrue_PromiseRes) || 
        			brain.isRobotType(StacRobotType.PERSUASION_RESP_AlwaysTrue) || 
        			brain.isRobotType(StacRobotType.PERSUASION_RESP_ALL)){
        			return processAcceptedPersuasion(offer,true);
        		}
        	}
        }
        
        if (brain.isRobotType(StacRobotType.PARTIALISING_COMPLETE_OFFERS_100_PERCENT) || brain.isRobotType(StacRobotType.PARTIALISING_COMPLETE_OFFERS_50_PERCENT)) {
            return considerOfferToMePartialisingRobot(offer);
        }
        
        return considerOfferToMePERSUASION(offer, persuasiveArgument);
    }


    /** Check if a Persuasion's constraints hold (if one doesn't return false)
     * 
     * @param p
     * @return
     */
    private boolean checkIfPersuasionConstraintsHolds(Persuasion p){
    	HashMap<String, String> constraints = p.getConstraints();
    	HashMap<String, String> parameters = p.getParameters();
    	for(String constraint : constraints.keySet()){
    		if(constraints.get(constraint).equals("true")){
    			if(!checkIfConstraintsApplies(constraint, parameters)){
    				return false;
    			}
    		}
    	}
    	
    	if(constraints.containsKey(Persuasion.longestRoadConstraint) && constraints.containsKey(Persuasion.newSettlementConstraint) &&
    			constraints.get(Persuasion.longestRoadConstraint).equals("true") && constraints.get(Persuasion.newSettlementConstraint).equals("true")){
		    int N = 0;
		    
		    String LRType = parameters.get(Persuasion.longestRoadTypeWildcard);
		    SOCPlayer playerWithLongestRoad = brain.getPlayerData().getGame().getPlayerWithLongestRoad();
			int currentLongestRoad = -1;
			if(playerWithLongestRoad!=null){
				currentLongestRoad = playerWithLongestRoad.getLongestRoadLength();
			}
			int myLongestRoad = brain.getPlayerData().calcLongestRoad2();
			
			for(int node : brain.getPlayerData().getLegalSettlements()){
				if(brain.getPlayerData().isPotentialSettlement(node)){
					N++;
				}
			}   
		    ArrayList<Integer> potentialRoads = new ArrayList<Integer>();
		    for (int i = game.getBoard().getMinNode(); i <= SOCBoard.MAXNODE; i++){
		    	if(brain.getPlayerData().isPotentialRoad(i)){
		    		potentialRoads.add(i);
		    	}
		    }
			for(int co : potentialRoads){
				SOCPlayer dummy = new SOCPlayer(brain.getPlayerData());
		        SOCPossibleRoad spb = new SOCPossibleRoad(dummy, co, new Vector());
		        SOCRoad tempRoad = new SOCRoad(dummy, spb.getCoordinates(), game.getBoard());
		        dummy.putPiece(tempRoad);
		    	int M = 0;
		    	for(int node : dummy.getLegalSettlements()){
		    		if(dummy.isPotentialSettlement(node)){
		    			M++;
		    		}
		    	}	        
		    	if(M>N){
		        	int newLRLength = dummy.calcLongestRoad2();
					if((LRType.equals("keep") && playerWithLongestRoad!=null && playerWithLongestRoad.getPlayerNumber()==brain.getPlayerNumber() && newLRLength>myLongestRoad)||
							(LRType.equals("get") && ((playerWithLongestRoad==null && newLRLength==5) || (newLRLength>currentLongestRoad && (playerWithLongestRoad!=null && playerWithLongestRoad.getPlayerNumber()!=brain.getPlayerNumber()))))||
							(LRType.equals("equal") && playerWithLongestRoad!=null && playerWithLongestRoad.getPlayerNumber()!=brain.getPlayerNumber() && newLRLength==currentLongestRoad) ){
						return true;
					}
		        }
		        dummy.removePiece(tempRoad);
		        dummy.destroyPlayer();
			}
		    return false;
		}
    	return true;
    }
    
    private boolean checkIfConstraintsApplies(String constraint, HashMap<String, String> parameters){
    	switch (constraint){
		case Persuasion.longestRoadConstraint:
			String LRType = parameters.get(Persuasion.longestRoadTypeWildcard);
			SOCPlayer playerWithLongestRoad = brain.getPlayerData().getGame().getPlayerWithLongestRoad();
			int currentLongestRoad = -1;
			if(playerWithLongestRoad!=null){
				currentLongestRoad = playerWithLongestRoad.getLongestRoadLength();
			}
			int myLongestRoad = brain.getPlayerData().calcLongestRoad2();
			int newLRLength = calculateMaxLengthOfRoadAfterAddingOne(brain.getPlayerData());
			if((LRType.equals("keep") && playerWithLongestRoad!=null && playerWithLongestRoad.getPlayerNumber()==brain.getPlayerNumber() && newLRLength>myLongestRoad)||
					(LRType.equals("get") && ((playerWithLongestRoad==null && newLRLength==5) || (newLRLength>currentLongestRoad && (playerWithLongestRoad!=null && playerWithLongestRoad.getPlayerNumber()!=brain.getPlayerNumber()))))||
					(LRType.equals("equal") && playerWithLongestRoad!=null && playerWithLongestRoad.getPlayerNumber()!=brain.getPlayerNumber() && newLRLength==currentLongestRoad) ){
				return true;
			}
		    return false;

		case Persuasion.accessNewResourceConstraint:
			int res = mapStringToResourceNumber(parameters.get(Persuasion.resWildcard));
			HashMap<Integer,Boolean> makeArgumentOnResType = new HashMap<Integer,Boolean>();
			makeArgumentOnResType.put(res,true);
			checkIfAnyResourceCannotBeRecievedCurrently(makeArgumentOnResType, brain.getPlayerData(), false);
			
			//If this player doesnt have access to res
			if(makeArgumentOnResType.get(res)){
				//Generate a lost of potentialSettlements which can be built 
				ArrayList<Integer> potentialSettlements = new ArrayList<Integer>();
	            for (int i = game.getBoard().getMinNode(); i <= SOCBoard.MAXNODE; i++){
	            	if(brain.getPlayerData().isPotentialSettlement(i)){
	            		potentialSettlements.add(i);
	            	}
	            }  
	            //Cycle through the list of possible settlements until you find a settlement which enables this extension. 
	    		for(int co : potentialSettlements){
	    	        SOCPlayer dummy = new SOCPlayer(brain.getPlayerData());
	    	        SOCPossibleSettlement spb = new SOCPossibleSettlement(dummy, co, new Vector());
	    	        SOCSettlement tempSettlement = new SOCSettlement(dummy, spb.getCoordinates(), game.getBoard());
	    	        dummy.putPiece(tempSettlement);
	    	    	HashMap<Integer,Boolean> makeArgumentOnResTypeAFTER = new HashMap<Integer,Boolean>();
	    			makeArgumentOnResType.put(res,true);
	    			checkIfAnyResourceCannotBeRecievedCurrently(makeArgumentOnResTypeAFTER, dummy, false);
	    	        dummy.removePiece(tempSettlement);
			        dummy.destroyPlayer();
	    			if(!makeArgumentOnResType.get(res)){
	    				//Now has access to res
	    				return true;
	    			}
	    		}
			}
			return false;

			
		case Persuasion.largestArmyConstraint:
			SOCPlayer currentPlayerWithLargestArmy = brain.getPlayerData().getGame().getPlayerWithLargestArmy();
			int currentLargestArmy = 0;
			if(currentPlayerWithLargestArmy!=null){
				currentLargestArmy = currentPlayerWithLargestArmy.getNumKnights();
			}
			
			int oldOwnLANo = brain.getPlayerData().getNumKnights();;
			int newLANo = oldOwnLANo+1;//This persuasion is based on the assumption you get one.

			String LAType = parameters.get(Persuasion.largestArmyTypeWildcard);
			if((LAType.equals("keep") && currentPlayerWithLargestArmy.getPlayerNumber()==brain.getPlayerNumber())||
					(LAType.equals("get") && ((currentPlayerWithLargestArmy==null && newLANo==3)||(currentPlayerWithLargestArmy!=null && newLANo>currentLargestArmy && currentPlayerWithLargestArmy.getPlayerNumber()!=brain.getPlayerNumber()) ))||
					(LAType.equals("equal") && currentPlayerWithLargestArmy.getPlayerNumber()!=brain.getPlayerNumber() && newLANo==currentLargestArmy) ){
				return true;
			}
		    return false;

		    
		case Persuasion.victoryPointsConstraint:
			int oldTotalVPs = brain.getPlayerData().getPublicVP();
			int newTotalVPs = oldTotalVPs+1; //Under the assumption this move gives the opponent 1 VP
			
			int highestVPs = 0;
			for(SOCPlayer sp : brain.getPlayerData().getGame().getPlayers()){
				if(brain.getPlayerNumber() != sp.getPlayerNumber()){
					if(sp.getPublicVP()>highestVPs){
						highestVPs = sp.getPublicVP();
					}
				}
			}
			String VPType = parameters.get(Persuasion.victoryPointsTypeWildcard);
			if((VPType.equals("keep") && oldTotalVPs>highestVPs)||
					(VPType.equals("get") && newTotalVPs>highestVPs && oldTotalVPs<=highestVPs)||
					(VPType.equals("equal") && newTotalVPs==highestVPs && oldTotalVPs<highestVPs)){
				return true;
			}
			return false;

		case Persuasion.newSettlementConstraint:
		    int N = 0;
			for(int node : brain.getPlayerData().getLegalSettlements()){
				if(brain.getPlayerData().isPotentialSettlement(node)){
					N++;
				}
			}   
		    ArrayList<Integer> potentialRoads = new ArrayList<Integer>();
		    for (int i = game.getBoard().getMinNode(); i <= SOCBoard.MAXNODE; i++){
		    	if(brain.getPlayerData().isPotentialRoad(i)){
		    		potentialRoads.add(i);
		    	}
		    }
			for(int co : potentialRoads){
				SOCPlayer dummy = new SOCPlayer(brain.getPlayerData());
		        SOCPossibleRoad spb = new SOCPossibleRoad(dummy, co, new Vector());
		        SOCRoad tempRoad = new SOCRoad(dummy, spb.getCoordinates(), game.getBoard());
		        dummy.putPiece(tempRoad);
		    	int M = 0;
		    	for(int node : dummy.getLegalSettlements()){
		    		if(dummy.isPotentialSettlement(node)){
		    			M++;
		    		}
		    	}	        
		    	if(M>N){
		        	return true;
		        }
		        dummy.removePiece(tempRoad);
		        dummy.destroyPlayer();
			}
		    return false;
		    
		default:
			D.ebugERROR("Attempted to consider constraint: "+constraint+" which doesnt exist.");
			return false;
		}
    }
    
    /** 
     * Consider an offer that this agent considers valid
     * @param offer the offer made
     * @param persuasionUsed if the persuasion is not NullPersuasion
     * @return
     */
    private int processAcceptedPersuasion(SOCTradeOffer offer, boolean persuasionUsed){
    	int receiverNum = brain.getPlayerNumber();
    	int strengthOfPersuasion = 0;
        if(brain.isRobotType(StacRobotType.Persuasion_RESP_Use_Weight_When_Persuasion_Is_Accepted)){
        	strengthOfPersuasion = (Integer)brain.getTypeParam(StacRobotType.Persuasion_RESP_Use_Weight_When_Persuasion_Is_Accepted);
            return considerOfferOldMethod(offer, receiverNum, strengthOfPersuasion, persuasionUsed);
        }
        else{
        	return ACCEPT_OFFER;
        }
    }
    
    
    protected int considerOfferToMePERSUASION(SOCTradeOffer offer, Persuasion persuasiveArgument) {
//    	int accepted = considerOfferOld(offer, brain.getPlayerNumber(), true);
//    	if(persuasiveArgument.getIdentifier()!=Persuasion.PersuasionIdentifiers.NullPersuasion){
//	    	if(accepted==1){
//	    		System.out.println("OLD:"+brain.getPlayerNumber()+" accepted persuasion: "+persuasiveArgument.toString());
//	    	}
//	    	else if(accepted==0){
//	    		System.out.println("OLD:"+brain.getPlayerNumber()+" rejected persuasion: "+persuasiveArgument.toString());
//	    	}
//    	}
//        return accepted;

    	return considerOfferOld(offer, brain.getPlayerNumber());
    }
   
    
    /** Altered considerOfferOld which gives an additional weight to offers which have valid persuasions that the recipient accepts
     *  
     * @param offer 	the offer made
     * @param receiverNum	the recieverNumber (should be this player)
     * @param strengthOfPersuasion	the strength of accepting the validity of a persuasion
     * @param forced	(persuasion used) whether or not a legitmate persuasion move was used (NullPersuasion does not apply)
     * @return
     */
    private int considerOfferOldMethod(SOCTradeOffer offer, int receiverNum, int strengthOfPersuasion, boolean forced){
    	if(strengthOfPersuasion==0){
    		return considerOfferOld(offer, receiverNum);
    	}
    	else {
    		return considerOfferWithPersusaionWeight(offer, receiverNum, strengthOfPersuasion);
    	}
    }
    
    /** Slightly altered version of the {@link considerOfferOld}}
     * The old considerOffer method, which was used both to determine if an opponent would possibly accept, and also to decide whether we should accept
     * trade offers sent to us.  These are very different decisions (eg we may want to be optimistic and always assume someone will accept, we may
     * be more cautious with opponents near winning than we expect our opponents to be, etc).
     * This is still the default behavior for both decisions, unless overridden.  Make it protected in case other implementations want to use this under certain conditions
     * @param offer        the trade offer
     * @param receiverNum  the opponent we're considering making the trade offer to
     * @param strengthOfPersuasion       weight assigned when the persuasion move is valid
     * @return             the decision (REJECT_OFFER, ACCEPT_OFFER, COUNTER_OFFER, COMPLETE_OFFER)
     */
    protected int considerOfferWithPersusaionWeight(SOCTradeOffer offer, int receiverNum, int strengthOfPersuasion) {
        if (brain.isRobotType(StacRobotType.NO_TRADES)) {
            return REJECT_OFFER;
        }        
      
        SOCResourceSet rsrcsOut = offer.getGetSet();
        SOCResourceSet rsrcsIn = offer.getGiveSet();
        
        return considerOfferWithPersusaionWeight(offer.getFrom(), receiverNum, rsrcsOut, rsrcsIn, strengthOfPersuasion);
        
    }
    
    /** Slightly altered version of the {@link considerOfferOld}}
     * Implementation of actual logic, which can be used with just resource sets and doesn't require the offer object, which may not exist
     * @param senderNum
     * @param receiverNum
     * @param rsrcsOut
     * @param rsrcsIn
     * @param strengthOfPersuasion
     * @return
     */
    protected int considerOfferWithPersusaionWeight(int senderNum, int receiverNum, SOCResourceSet rsrcsOut, SOCResourceSet rsrcsIn, int strengthOfPersuasion) {
        ///
        /// This version should be faster
        ///
        D.ebugPrintlnINFO("***** CONSIDER OFFER 2 *****");

        int response = REJECT_OFFER;

        SOCPlayer receiverPlayerData = game.getPlayer(receiverNum);
        SOCResourceSet receiverResources = brain.getMemory().getOpponentResources(receiverNum);

        //
        // if the receiver doesn't have what's asked for, they'll reject
        //
        if ((receiverResources.getAmount(SOCResourceConstants.UNKNOWN) == 0) && (!receiverResources.contains(rsrcsOut)))
        {
            D.ebugPrintlnINFO("Reject offer; receiver does not have resource asked for.");
            return response;
        }

        D.ebugPrintlnINFO("senderNum = " + senderNum);
        D.ebugPrintlnINFO("receiverNum = " + receiverNum);
        D.ebugPrintlnINFO("rsrcs from receiver = " + rsrcsOut);
        D.ebugPrintlnINFO("rsrcs to receiver = " + rsrcsIn);

        SOCPossiblePiece receiverTargetPiece = brain.getMemory().getTargetPiece(receiverNum);

        D.ebugPrintlnINFO("targetPieces[" + receiverNum + "] = " + receiverTargetPiece);

        SOCPlayerTracker receiverPlayerTracker = (SOCPlayerTracker) playerTrackers.get(Integer.valueOf(receiverNum));
        SOCPlayerTracker senderPlayerTracker = (SOCPlayerTracker) playerTrackers.get(Integer.valueOf(senderNum));

        if (receiverPlayerTracker == null){
            D.ebugPrintlnINFO("Reject offer; receiverPlayerTracker == null");
            return response;
        }

        if (senderPlayerTracker == null){
            D.ebugPrintlnINFO("Reject offer; senderPlayerTracker == null");
            return response;
        }

        SOCBuildPlanStack receiverBuildingPlan;
        if (receiverTargetPiece == null)            
        {
            receiverBuildingPlan = predictBuildPlan(receiverNum);            

            if (receiverBuildingPlan.empty())
            {
                D.ebugPrintlnINFO("Reject offer; receiverBuildingPlan is empty");
                return response;
            }

            receiverTargetPiece = (SOCPossiblePiece) receiverBuildingPlan.peek();
            brain.getMemory().setTargetPieceUnannounced(receiverNum, receiverTargetPiece);
        }
        else {
            receiverBuildingPlan  = new SOCBuildPlanStack();
            receiverBuildingPlan.push(receiverTargetPiece);         
        }

        D.ebugPrintlnINFO("receiverTargetPiece = " + receiverTargetPiece);

        SOCPossiblePiece senderTargetPiece = brain.getMemory().getTargetPiece(senderNum);

        D.ebugPrintlnINFO("targetPieces[" + senderNum + "] = " + senderTargetPiece);

        if (senderTargetPiece == null)
        {
            SOCBuildPlanStack senderBuildingPlan = predictBuildPlan(senderNum);            

            if (senderBuildingPlan.empty())
            {
                D.ebugPrintlnINFO("Reject offer; senderBuildingPlan is empty");
                return response;
            }

            senderTargetPiece = (SOCPossiblePiece) senderBuildingPlan.peek();
            brain.getMemory().setTargetPieceUnannounced(senderNum, senderTargetPiece);
        }

        D.ebugPrintlnINFO("senderTargetPiece = " + senderTargetPiece);

        int senderWGETA = senderPlayerTracker.getWinGameETA();

        if (senderWGETA > WIN_GAME_CUTOFF)
        {
            //
            //  see if the sender is in a race with the receiver
            //
            boolean inARace = false;

            if ((receiverTargetPiece.getType() == SOCPossiblePiece.SETTLEMENT) || (receiverTargetPiece.getType() == SOCPossiblePiece.ROAD))
            {
                Enumeration threatsEnum = receiverTargetPiece.getThreats().elements();

                while (threatsEnum.hasMoreElements())
                {
                    SOCPossiblePiece threat = (SOCPossiblePiece) threatsEnum.nextElement();

                    if ((threat.getType() == senderTargetPiece.getType()) && (threat.getCoordinates() == senderTargetPiece.getCoordinates()))
                    {
                        inARace = true;

                        break;
                    }
                }

                if (inARace)
                {
                    D.ebugPrintlnINFO("inARace == true (threat from sender)");
                }
                else if (receiverTargetPiece.getType() == SOCPossiblePiece.SETTLEMENT)
                {
                    Enumeration conflictsEnum = ((SOCPossibleSettlement) receiverTargetPiece).getConflicts().elements();

                    while (conflictsEnum.hasMoreElements())
                    {
                        SOCPossibleSettlement conflict = (SOCPossibleSettlement) conflictsEnum.nextElement();

                        if ((senderTargetPiece.getType() == SOCPossiblePiece.SETTLEMENT) && (conflict.getCoordinates() == senderTargetPiece.getCoordinates()))
                        {
                            inARace = true;

                            break;
                        }
                    }

                    if (inARace)
                    {
                        D.ebugPrintlnINFO("inARace == true (conflict with sender)");
                    }
                }
            }

            if (!inARace)
            {
                ///
                /// see if this is good for the receiver
                ///
                SOCResourceSet targetResources = getResourcesForPlan(receiverBuildingPlan) ;                    

                SOCBuildingSpeedEstimate estimate = brain.getEstimator(receiverPlayerData.getNumbers());

                SOCTradeOffer receiverBatna = getOfferToBank(targetResources, receiverPlayerData.getResources());
                D.ebugPrintlnINFO("*** receiverBatna = " + receiverBatna);

                int batnaBuildingTime = getETAToTargetResources(receiverPlayerData, targetResources, SOCResourceSet.EMPTY_SET, SOCResourceSet.EMPTY_SET, estimate);

                D.ebugPrintlnINFO("*** batnaBuildingTime = " + batnaBuildingTime);

                int offerBuildingTime = getETAToTargetResources(receiverPlayerData, targetResources, rsrcsOut, rsrcsIn, estimate);
                
                D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);

                
                //Allow the validity of the persuasion move to alter perception of weight of move
                
                offerBuildingTime -= strengthOfPersuasion;
                if(offerBuildingTime<0){
                	offerBuildingTime=0;
                }
                
                //---MG
                //store the last offer building time so that we can make a meta evaluation for partial offers
                //but only if this building time is better than the BATNA, otherwise take 1000 as the usual worst case value
                //(the idea is that if there is a BATNA, then we shouldn't accept the trade: it will be better to trade with bank/port or not to trade) 
                if (offerBuildingTime <= batnaBuildingTime) {
                    lastOfferETA = offerBuildingTime; 
                } else {
                    lastOfferETA = 1000;
                }

                //
                // only accept offers that are better than BATNA
                //
                if (offerBuildingTime <= batnaBuildingTime)
                {
                    //                  //---MG
                    //                  //if we want to accept the offer, but the sender does not specify resources, we'll make a counteroffer
                    //                  if (offer.getGiveSet().getTotal() == 0 || offer.getGetSet().getTotal() == 0) {
                    //                      D.ebugPrintln("Counter offer because sender did not specify `give' or `get' resources");
                    //                      response = COUNTER_OFFER;
                    //                  }
                    //
                    D.ebugPrintlnINFO("Accept offer");
                    response = ACCEPT_OFFER;
                }
                else
                {
                    D.ebugPrintlnINFO("Counter offer");
                    response = COUNTER_OFFER;
                }
            }
        }

        return response;
    }  
    
    
    /**
     * Filter the moves which this robot expects to fail (if persuasion/forced can be used then assume it will succeed)
     */
    @Override
    protected void filterLikely(final List<TradeOfferWithStats> trades, SOCBuildPlanStack buildPlan) {
        List<TradeOfferWithStats> remove = new ArrayList<TradeOfferWithStats>();
        for (TradeOfferWithStats t : trades) {
            boolean hasLikely = false;
            for (int p=0; p<4; p++) {
                if (t.to[p]) {
                    // Is "isSelling" true for any requested resource?                    
                    for (int r=SOCResourceConstants.CLAY; r<=SOCResourceConstants.WOOD; r++) {
                        if (t.get.getAmount(r)>0 && ! brain.getMemory().isSellingResource(p, r)) {
                            t.to[p] = false;
                            break;
                        }
                    }
                   
                    // If the above didn't disqualify, check whether we think they'd accept
                    if (t.to[p]) {
                        int offerResponse = ACCEPT_OFFER;
                        if (!brain.isRobotType(StacRobotType.BUILD_PLAN_INDIFFERENT)) {

                            // try to guess the opponent's response
                            boolean[] receiver = new boolean[game.maxPlayers];
                            receiver[p] = true;
                            SOCTradeOffer offerToTest = new SOCTradeOffer(game.getName(), brain.getPlayerNumber(), receiver, t.give, t.get);

                            // see if we will/can Persuade/force other robots to accept the offer
                            if(getPersuaderType()){
                            	Persuasion persuasionToUse = generatePersuasionMove(offerToTest, null);
                                offerResponse = guessOpponentResponse(offerToTest, p, persuasionToUse);
                            }
                            else{
                                boolean forced = shouldOfferBeForced(offerToTest, buildPlan, null);
                                
                                offerResponse = guessOpponentResponse(offerToTest, p);
                            }
                        }    
    
                        if (offerResponse == ACCEPT_OFFER) {
                            hasLikely = true;
                        }
                        // Do not set to=false if we don't think the player would accept -
                        //  they may surprise us or make a fruitful counter-offer
                    }
                }
            }
            if (!hasLikely) {
                remove.add(t);
            }    
        }
        trades.removeAll(remove);
    }
    
    
    protected int guessOpponentResponse(SOCTradeOffer offer, int receiverNum, Persuasion persuasiveArgument) {
    	//TODO might be valuable to filter out poor persuasions here
        if (persuasiveArgument.getIdentifier()!=Persuasion.PersuasionIdentifiers.NullPersuasion){
        	return ACCEPT_OFFER;
        }
        //Cannot assume a strength to a persuasion move here 
        return considerOfferOldMethod(offer, receiverNum, 0, (persuasiveArgument.getIdentifier()!=Persuasion.PersuasionIdentifiers.NullPersuasion));
    }
    
    /*********************************************************************
     * Helper Used in the generating and handling protocols for persuasion
     */
    
    
    /** Returns the maximum length of the longest road for the player after building 1 road
     * 
     * @param tracker
     * @return
     */
    private int calculateMaxLengthOfRoadAfterAddingOne(SOCPlayer player){
    	
        ArrayList<Integer> potentialRoads = new ArrayList<Integer>();
        for (int i = game.getBoard().getMinNode(); i <= SOCBoard.MAXNODE; i++){
        	if(player.isPotentialRoad(i)){
        		potentialRoads.add(i);
        	}
        }  
        int maximum=0;
		for(int co : potentialRoads){
	        SOCPlayer dummy = new SOCPlayer(player);
	        SOCPossibleRoad spb = new SOCPossibleRoad(dummy, co, new Vector());
	        SOCRoad tempRoad = new SOCRoad(dummy, spb.getCoordinates(), game.getBoard());
	        dummy.putPiece(tempRoad);
	        int temp = dummy.calcLongestRoad2();
	        if(temp>maximum){
	        	maximum=temp;
	        }
	        dummy.removePiece(tempRoad);
	        dummy.destroyPlayer();
		}
		return maximum;
    }
    
    
    /** 
     * This will change the makeArgumentOnResType to false if the associated player can currently get that resource somewhere
     * @param makeArgumentOnResType 
     * @param currentPlayer
     */
    private static void checkIfAnyResourceCannotBeRecievedCurrently(HashMap<Integer,Boolean> makeArgumentOnResType, SOCPlayer currentPlayer, boolean accountForRobber){
    	for(Integer res : makeArgumentOnResType.keySet()){
   			Enumeration resourceVector = currentPlayer.getNumbers().getNumbersForResource(res).elements();
    		if(accountForRobber){
       			resourceVector = currentPlayer.getNumbers().getNumbersForResource(res,currentPlayer.getGame().getBoard().getRobberHex()).elements();
    		}
    		
    		//The values in the enumeration appear to be the values which need to be rolled to get the resource
    		if(makeArgumentOnResType.get(res) && resourceVector.hasMoreElements()){
   				makeArgumentOnResType.put(res, false);
    		}
    	}
    }
    
    
    /** Calculate whether in the current game state, player pn can build a particular thing. 
     * To build a road, pn must have access to a place to build. 
     * To build a settlement there must exist a space for the settlement space where there is also a road to it.
     * To build a city there must exist a settlement owned by the 
     * To buy a Dev Card there must still be spare development cards. (They have not all already been taken).
     *  
     * {@link checkIfICanBuild} should be used to check if self can build
     * @param argument limited input values: {StacRobotType.PERSUADER_ImmediateBuildPlan_Road, StacRobotType.PERSUADER_ImmediateBuildPlan_Settlement, StacRobotType.PERSUADER_ImmediateBuildPlan_City, StacRobotType.PERSUADER_ImmediateBuildPlan_DevCard}
     * @param pn player number for the check
     * @return
     */
    private boolean checkIfPlayerCanBuild(String argument, int pn){
		SOCPlayerTracker playerTracker = brain.getMemory().getPlayerTrackers().get(pn);
    	if(argument.equals(StacRobotType.PERSUADER_ImmediateBuildPlan_Road)){
    		if(playerTracker.getPlayer().hasPotentialRoad()){
    			return true;
    		}
    	}
    	else if(argument.equals(StacRobotType.PERSUADER_ImmediateBuildPlan_Settlement)){
    		if(playerTracker.getPlayer().hasPotentialSettlement()){
    			return true;
    		}
    	}
    	else if(argument.equals(StacRobotType.PERSUADER_ImmediateBuildPlan_City)){
    		if(playerTracker.getPlayer().hasPotentialCity()){
    			return true;
    		}
    	}
    	else if(argument.equals(StacRobotType.PERSUADER_ImmediateBuildPlan_DevCard)){
    		if(playerTracker.getPlayer().getGame().getNumDevCards()>0){
    			return true;
    		}
    	}
    	else{
    		D.ebugERROR("Attempted to do a check on the argument: "+argument+" that isn't possible.");
    	}
    	return false;
    }
    
    
    /** Calculate whether in the current game state I can build one of these:
     * To build a road, I must have access to a place to build. 
     * To build a settlement there must exist a space for the settlement space where there is also a road to it.
     * To build a city there must exist a settlement owned to upgrade
     * To buy a Dev Card there must still be spare development cards. (They have not all already been taken).
     *  
     * {@link checkIfPlayerCanBuild} should be used to check if another player can build
     *  
     * @param persuasiveArgument has to be an IBP persuasion identifier.
     * @return
     */
    private boolean checkIfICanBuild(PersuasionIdentifiers persuasiveArgument){
    	if(persuasiveArgument==Persuasion.PersuasionIdentifiers.IBPRoad){
    		if(brain.getPlayerData().hasPotentialRoad()){
    			return true;
    		}
    	}
    	else if(persuasiveArgument==Persuasion.PersuasionIdentifiers.IBPSettlement){
    		if(brain.getPlayerData().hasPotentialSettlement()){
    			return true;
    		}
    	}
    	else if(persuasiveArgument==Persuasion.PersuasionIdentifiers.IBPCity){
    		if(brain.getPlayerData().hasPotentialCity()){
    			return true;
    		}
    	}
    	else if(persuasiveArgument==Persuasion.PersuasionIdentifiers.IBPDevCard){
    		if(brain.getGame().getNumDevCards()>0){
    			return true;
    		}
    	}
    	else{
    		D.ebugERROR("Attempted to do a build check on the argument: "+persuasiveArgument.toString()+" that isn't possible.");
    	}
    	return false;
    }
    
    
    /** Helper because this calculation is done a few times
     * 
     * @param res
     * @return
     */
    private static String mapResourceNumberToString(int res){
		if(res==SOCResourceConstants.CLAY){
			return "clay";
		}else if(res==SOCResourceConstants.ORE){
			return "ore";
		}else if(res==SOCResourceConstants.SHEEP){
			return "sheep";
		}else if(res==SOCResourceConstants.WHEAT){
			return "wheat";
		}else if(res==SOCResourceConstants.WOOD){
			return "wood";
		}
		else{
			D.ebugERROR("Attempt to get string representation of: "+res+ " when this value should be 1-5.");
			return "";
		}
    }
    
    
    /** Map from res name to integer representation, ie - "wood" -> SOCResourceConstants.WOOD etc.
     * Returns -1 if there is no mapping
     * @param stringRes
     * @return
     */
    private static int mapStringToResourceNumber(String stringRes){
		if(stringRes.equals("clay")){
			return SOCResourceConstants.CLAY;
		}else if(stringRes.equals("ore")){
			return SOCResourceConstants.ORE;
		}else if(stringRes.equals("sheep")){
			return SOCResourceConstants.SHEEP;
		}else if(stringRes.equals("wheat")){
			return SOCResourceConstants.WHEAT;
		}else if(stringRes.equals("wood")){
			return SOCResourceConstants.WOOD;
		}
		else{
			D.ebugERROR("Attempt to get string representation of: "+stringRes+ " when this value should be \"clay\", \"ore\", \"sheep\", \"wheat\" or \"wood\".");
			return -1;
		}
    }
    
}