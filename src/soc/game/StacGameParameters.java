package soc.game;

/**
 * Class for storing information on games (e.g. loading info) if multiple games run simultanously
 * @author MD
 *
 */
public class StacGameParameters{
	/**
	 * flag for deciding whether to load or start a new game
	 */
	public boolean load = false;
	/**
	 * value required in case we are loading when starting a new game
	 */
	public String folderName = "";
	/**
	 * value to limit the number of turns in games
	 */
	public int simulationDepth = 0;
	/**
	 * -1 means randomize
	 */
	public int playerToStart = -1;
	/**
	 * default create a new board
	 */
	public boolean loadBoard = false;
	/**
	 * default use the old trade interface
	 */
	public boolean chatNegotiations = false;
	/**
	 * default contains hidden information
	 */
	public boolean fullyObservable = false;
	/**
	 * by default drawing vp cards is not observable
	 */
	public boolean observableVP = false;
	
	public StacGameParameters(boolean l, String fn, int sd, int pts, boolean lb, boolean cn, boolean fo, boolean ov){
		load = l;
		folderName = fn;
		simulationDepth = sd;
		playerToStart = pts;
		loadBoard = lb;
		chatNegotiations = cn;
		fullyObservable = fo;
		observableVP = ov;
	}
	
}
