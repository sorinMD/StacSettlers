package representation;

import mcts.game.catan.Board;
import soc.server.database.stac.ExtGameStateRow;
import soc.server.database.stac.GameActionRow;
import soc.server.database.stac.ObsGameStateRow;

public interface FVGenerator{
	
	public int[] calculateStateVectorSS(int[] s, Board bl);
	
	public int[] calculateStateVectorJS(ObsGameStateRow ogsr, ExtGameStateRow egsr);
	
	public int[] computeActionVector(int[] beforeState, int[] afterState);
	
}
