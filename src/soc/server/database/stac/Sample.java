package soc.server.database.stac;

/**
 * This is one sample containing the size of the set of legal actions as the first position, the state, the label action and the set of possible actions
 * 
 * @author MD
 *
 */
public class Sample{

	private int[] state;
	private double[] actionLabel;
	private double[][] possActions;
	private int totalLegalActions;
	private int length;
	
	public Sample() {
	}
	
	public Sample(int[] s, double[][] actions, double[] label) {
		state = s;
		possActions = actions;
		actionLabel = label;
		length = state.length + actionLabel.length + possActions.length*possActions[0].length + 1;
		totalLegalActions = possActions.length;
	}
	
	public int getTotalLegalActions(){
		return totalLegalActions;
	}
	
	/**
	 * 
	 * @return an integer array containing all the information as state, set of legal actions and index of the label action
	 */
	public double[] getRecord(){
		double[] record= new double[length];
		record[0] = totalLegalActions;
		int idx = 1;
		for(int i = 0; i< state.length; i++){
			record[idx] = state[i];
			idx++;
		}
		
		for(int i = 0; i< actionLabel.length; i++){
			record[idx] = actionLabel[i];
			idx++;
		}
		
		for(int i = 0; i< possActions.length; i++){
			for(int j = 0; j < possActions[i].length; j++){
				record[idx] = possActions[i][j];
				idx++;
			}
		}
		
		return record;
	}
}
