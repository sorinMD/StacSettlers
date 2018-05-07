package soc.robot.stac;

import java.util.Arrays;
import mcts.game.catan.GameStateConstants;

/**
 * Wrapper class for comparing actions stored in a list on the {@link MCTSRobotBrain} side. 
 * @author MD
 *
 */
public class MCTSAction implements GameStateConstants{
	/**
	 * The same description as in the mcts package.
	 */
	public int[] description = new int[ACTIONSIZE];
    
	public MCTSAction( int[] d) {
		description = d;
	}
    
    public static int getHashCode(int[] a){
        int [] a2 = a.clone();
        return(Arrays.hashCode(a2));
    }
    
	@Override
	public boolean equals(Object obj) {
		if(obj.getClass() == MCTSAction.class){
			if(((MCTSAction) obj).description.length == this.description.length){
				for(int i = 0; i < this.description.length; i++){
					if(((MCTSAction) obj).description[i] != this.description[i]){
						return false;
					}
				}
				return true;
			}
			
		}
		return false;
	}
    
}
