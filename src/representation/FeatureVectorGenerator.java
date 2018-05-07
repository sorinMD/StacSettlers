package representation;

import mcts.game.catan.GameStateConstants;
import mcts.game.catan.HexTypeConstants;
import mcts.game.catan.VectorConstants;
import soc.server.database.stac.GameActionRow;

public abstract class FeatureVectorGenerator implements GameStateConstants, HexTypeConstants, VectorConstants, FVGenerator{

    /**
     * Usually equal to the state vector size.
     * Reward and value function size.
     */
    public static int REWARD_FUNCTION_SIZE; 

    /**
     * This is fixed as there are only 14 types of actions a player can do in Settlers of Catan
     */
    public static int ACTION_TYPE_VECTOR_SIZE = 14;
	
    /**
	 * @param actionType
	 * @return
	 */
	protected int[] createActionTypeVectorFromJS(double actionType){
		int[] vector = new int[ACTION_TYPE_VECTOR_SIZE];
		int index = 0;
		
		if(actionType == GameActionRow.TRADE){
			index = 1;
		}else if(actionType == GameActionRow.ENDTURN){
			index = 2;
		}else if(actionType == GameActionRow.ROLL){
			index = 3;
		}else if(actionType == GameActionRow.BUILDROAD){
			index = 4;
		}else if(actionType == GameActionRow.BUILDSETT){
			index = 5;
		}else if(actionType == GameActionRow.BUILDCITY){
			index = 6;
		}else if(actionType == GameActionRow.MOVEROBBER){
			index = 7;
		}else if(actionType == GameActionRow.CHOOSEPLAYER){
			index = 8;
		}else if(actionType == GameActionRow.DISCARD){
			index = 9;
		}else if(actionType == GameActionRow.BUYDEVCARD){
			index = 10;
		}else if(actionType == GameActionRow.PLAYKNIGHT){
			index = 11;
		}else if(actionType == GameActionRow.PLAYMONO){
			index = 12;
		}else if(actionType == GameActionRow.PLAYDISC){
			index = 13;
		}else if(actionType == GameActionRow.PLAYROAD){
			index = 14;
		}
		if(index>0)
			vector[index-1] = 1;//all the other are 0
		return vector;
	}

	/**
	 * @param actionType
	 * @return
	 */
	protected int[] createActionTypeVectorFromSS(int actionType){
		int[] vector = new int[ACTION_TYPE_VECTOR_SIZE];
		int index = 0;
		
		if(actionType == A_PORTTRADE){
			index = 1;
		}else if(actionType == A_ENDTURN){
			index = 2;
		}else if(actionType == A_THROWDICE){
			index = 3;
		}else if(actionType == A_BUILDROAD){
			index = 4;
		}else if(actionType == A_BUILDSETTLEMENT){
			index = 5;
		}else if(actionType == A_BUILDCITY){
			index = 6;
		}else if(actionType == A_PLACEROBBER){
			index = 7;
		}else if(actionType == A_PLACEROBBER){///NOTE: moving robber and choosing player are the same action in SmartSettlers, while in JSettlers are two different actions
			index = 8;
		}else if(actionType == A_PAYTAX){
			index = 9;
		}else if(actionType == A_BUYCARD){
			index = 10;
		}else if(actionType == A_PLAYCARD_KNIGHT){
			index = 11;
		}else if(actionType == A_PLAYCARD_MONOPOLY){
			index = 12;
		}else if(actionType == A_PLAYCARD_FREERESOURCE){
			index = 13;
		}else if(actionType == A_PLAYCARD_FREEROAD){
			index = 14;
		}
		if(index>0)
			vector[index-1] = 1;//all the other are 0
		return vector;
	}
}
