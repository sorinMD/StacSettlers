package soc.util;

import mcts.game.catan.GameStateConstants;
import soc.game.SOCDevCardConstants;
import soc.game.SOCResourceConstants;

/**
 * Basic utility class for making sure the same values are used across the 3 different game models.
 * 
 */
public class SettlersOdds implements GameStateConstants {

	/**
	 * 
	 * @param result the result of the dice
	 * @return the probability associated with it
	 * 
	 *	odds for 7			6/36 =0.167
	 *	odds for 6 and 8 	5/36 =0.139
	 *	odds for 5 and 9	4/36 =0.111
	 *	odds for 4 and 10	3/36 =0.083
	 *	odds for 3 and 11	2/36 =0.056
	 *	odds for 2 and 12	1/36 =0.028
	 */
	public static double getRollsProb(int result){
		result = reduceNumberRange(result);
		switch (result) {
			case 7:
				return .167;
			case 6:
				return .139;
			case 5:
				return .111;
			case 4:
				return .083;
			case 3:
				return .056;
			case 2:
				return .028;
			default:
				break;
		}
		return .0;//not possible as the result cannot be anything else
	}
	
	/**
	 * What are the chances of stealing a specific resource from the opponent's hand;
	 * Both the argument and the returning arrays must respect the following order of elements (index):
	 * 0.Clay;
	 * 1.Ore;
	 * 2.Sheep;
	 * 3.Wheat;
	 * 4.Wood;
	 * 5.Total (only for the argument array).
	 * @param setOfVictim the set of resources in the player's hand
	 * @return an array of doubles with the probability 
	 */
	public static double[] getResourceProbJS(int[] set){
		double[] chances = new double[5];
		if(set[5] == 0)//don't divide by 0 to avoid NaN
			return chances;
		chances[SOCResourceConstants.CLAY-1] = (double)set[SOCResourceConstants.CLAY-1]/(double)set[5];
		chances[SOCResourceConstants.ORE-1] = (double)set[SOCResourceConstants.ORE-1]/(double)set[5];
		chances[SOCResourceConstants.SHEEP-1] = (double)set[SOCResourceConstants.SHEEP-1]/(double)set[5];
		chances[SOCResourceConstants.WHEAT-1] = (double)set[SOCResourceConstants.WHEAT-1]/(double)set[5];
		chances[SOCResourceConstants.WOOD-1] = (double)set[SOCResourceConstants.WOOD-1]/(double)set[5];
		return chances;
	}
	
	/**
	 * What are the chances of stealing a specific resource from the opponent's hand;
	 * Both the argument and the returning arrays must respect the following order of elements (index):
	 * 0.Sheep;
	 * 1.Wood;
	 * 2.Clay;
	 * 3.Wheat;
	 * 4.Stone;
	 * 5.Total (only for the argument array).
	 * @param setOfVictim the set of resources in the player's hand
	 * @return an array of doubles with the probability 
	 */
	public static double[] getResourceProbSS(int[] set){
		double[] chances = new double[5];
		if(set[5] == 0)//don't divide by 0 to avoid NaN
			return chances;
		chances[RES_CLAY] = (double)set[RES_CLAY]/(double)set[5];
		chances[RES_STONE] = (double)set[RES_STONE]/(double)set[5];
		chances[RES_SHEEP] = (double)set[RES_SHEEP]/(double)set[5];
		chances[RES_WHEAT] = (double)set[RES_WHEAT]/(double)set[5];
		chances[RES_WOOD] = (double)set[RES_WOOD]/(double)set[5];
		return chances;
	}
	
	/**
	 * What are the chances of drawing a specific dev card knowing the deck of remaining cards;
	 * Both the argument and the returning arrays must respect the following order of elements (index):
	 * 0.Knights;
	 * 1.Road Building;
	 * 2.Discovery;
	 * 3.Monopoly;
	 * 4.Victory Points;
	 * 5.Total (only for the argument array).
	 * @param set the deck of remaining dev cards
	 * @return an array of doubles representing the probabilities for each type of card
	 */
	public static double[] getProbabilityOfKnownDevCardsJS(int[] set){
		double[] chances = new double[5];
		if(set[5] == 0)//don't divide by 0 to avoid NaN
			return chances;
		chances[SOCDevCardConstants.KNIGHT] = (double)set[SOCDevCardConstants.KNIGHT]/(double)set[5];
		chances[SOCDevCardConstants.ROADS] = (double)set[SOCDevCardConstants.ROADS]/(double)set[5];
		chances[SOCDevCardConstants.DISC] = (double)set[SOCDevCardConstants.DISC]/(double)set[5];
		chances[SOCDevCardConstants.MONO] = (double)set[SOCDevCardConstants.MONO]/(double)set[5];
		chances[SOCDevCardConstants.CAP] = (double)set[SOCDevCardConstants.CAP]/(double)set[5]; //equal to VPDEVCARD
		return chances;
	}
	
	/**
	 * What are the chances of drawing a specific dev card knowing the deck of remaining cards;
	 * Both the argument and the returning arrays must respect the following order of elements (index):
	 * 0.Knights;
	 * 1.Victory Points
	 * 2.Road Building;
	 * 3.Discovery;
	 * 4.Monopoly;
	 * 5.Total (only for the argument array).
	 * @param set the deck of remaining dev cards
	 * @return an array of doubles representing the probabilities for each type of card
	 */
	public static double[] getProbabilityOfKnownDevCardsSS(int[] set){
		double[] chances = new double[5];
		if(set[5] == 0)//don't divide by 0 to avoid NaN
			return chances;
		chances[CARD_KNIGHT] = (double)set[CARD_KNIGHT]/(double)set[5];
		chances[CARD_FREEROAD] = (double)set[CARD_FREEROAD]/(double)set[5];
		chances[CARD_FREERESOURCE] = (double)set[CARD_FREERESOURCE]/(double)set[5];
		chances[CARD_MONOPOLY] = (double)set[CARD_MONOPOLY]/(double)set[5];
		chances[CARD_ONEPOINT] = (double)set[CARD_ONEPOINT]/(double)set[5]; //equal to VPDEVCARD
		return chances;
	}
	
	
	/**
	 * What are the chances of drawing a specific card from a full dev card deck.
	 * The returning arrays respects the following order of elements (index):
	 * 0.Knights;
	 * 1.Road Building;
	 * 2.Discovery;
	 * 3.Monopoly;
	 * 4.Victory Points;
	 * @return an array of doubles representing the probabilities for each type of card
	 */
	public static double[] getProbabilityOfDevDeckJS(){
		double[] chances = new double[5];
		chances[SOCDevCardConstants.KNIGHT] = 14./25.;
		chances[SOCDevCardConstants.ROADS] = 2./25.;
		chances[SOCDevCardConstants.DISC] = 2./25.;
		chances[SOCDevCardConstants.MONO] = 2./25.;
		chances[SOCDevCardConstants.CAP] = 5./25.;
		return chances;
	}
	
	/**
	 * What are the chances of drawing a specific card from a full dev card deck.
	 * The returning arrays respects the following order of elements (index):
	 * 0.Knights;
	 * 1.Victory Points
	 * 2.Road Building;
	 * 3.Discovery;
	 * 4.Monopoly;
	 * @return an array of doubles representing the probabilities for each type of card
	 */
	public static double[] getProbabilityOfDevDeckSS(){
		double[] chances = new double[5];
		chances[CARD_KNIGHT] = 14./25.;
		chances[CARD_FREEROAD] = 2./25.;
		chances[CARD_FREERESOURCE] = 2./25.;
		chances[CARD_MONOPOLY] = 2./25.;
		chances[CARD_ONEPOINT] = 5./25.;
		return chances;
	}
	
	/**
	 * 
	 * @param n the actual number on the hex
	 * @return a normalised value between 2-6
	 */
	private static int reduceNumberRange(int n){
		int num = n; 
		//reduce range
		if(num == 8 || num == 6)
			num = 6;
		else if(num == 9 || num == 5)
			num = 5;
		else if(num == 10 || num == 4)
			num = 4;
		else if(num == 11 || num == 3)
			num = 3;
		else if(num == 12 || num == 2)
			num = 2;
		else
			num = 0; //for desert or wrong number range
		
		return num;
	}
	
	
	public static boolean isActionDeterministicSS(int actionType){
		if(actionType == A_PLACEROBBER)
			return false;
		if(actionType == A_THROWDICE)
			return false;
		if(actionType == A_BUYCARD)
			return false;
		return true;
	}
	
	
}
