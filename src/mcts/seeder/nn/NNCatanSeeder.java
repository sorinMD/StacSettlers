package mcts.seeder.nn;

import java.util.ArrayList;
import java.util.Arrays;
import org.deeplearning4j.nn.multilayer.CatanMlp;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import com.google.common.util.concurrent.AtomicDoubleArray;
import data.Normaliser;
import mcts.game.GameFactory;
import mcts.game.catan.Catan;
import mcts.game.catan.typepdf.UniformActionTypePdf;
import mcts.seeder.Seeder;
import mcts.tree.node.StandardNode;
import mcts.tree.node.TreeNode;
import representation.FVGenerator;
import representation.FVGeneratorFactory;
import soc.server.database.stac.DBGameParser;
import util.CatanFeatureMaskingUtil;

/**
 * Implementation that uses the Neural Network to evaluate the node and set of legal actions. 
 * It adds probabilities of selecting certain actions to the nodes based on the policy learned by the NN.
 * Only works on the observable version of the game or on the masked input.
 * 
 * @author MD
 *
 */
public class NNCatanSeeder extends Seeder{
	public static int nEvaluatedNodes = 0;
	private ArrayList<TreeNode> nodes;
	private int nNodes = 0;
	private CatanMlp model;
	private Normaliser norm;
	private NNCatanSeedTrigger trigger;
	private ArrayList<GameFactory> factories;
	private boolean maskInput = false;
	
	public NNCatanSeeder(ArrayList<TreeNode> batch, CatanMlp model, Normaliser norm, NNCatanSeedTrigger trigger, ArrayList<GameFactory> factories, boolean maskInput) {
		nodes = batch;
		for(TreeNode n : batch){
			nNodes+=((StandardNode)n).getChildren().size();
		}
		this.model = model;
		this.norm = norm;
		this.trigger = trigger;
		this.factories = factories;
		this.maskInput = maskInput;
	}
	
	@Override
	public void run() {
		//TODO: need to optimise this somehow since most of this stuff is already computed in the tree...
		//feature selection/extraction and code for translating to a format that the networks can understand
		INDArray output = null;
		INDArray actionSetSize = null;
	    INDArray input = null;
	    try {
		actionSetSize = Nd4j.create(nodes.size(), 1);
		ArrayList<INDArray> inputs = new ArrayList<INDArray>();
		int idx = 0;
		GameFactory factory;
		TreeNode n;
		for(int j = 0; j < nodes.size(); j++){
			n = nodes.get(j);
			factory = factories.get(j);
			int[] state = n.getState();
			FVGenerator gen = FVGeneratorFactory.getGenerator();
			double[] stateVector = Arrays.stream(gen.calculateStateVectorSS(state, Catan.board)).asDoubleStream().toArray();
			Catan game = (Catan)factory.getGame(state);
			double[][] options = DBGameParser.listPossibleActionVectors(game, game.listPossiblities(false));
			int nActions = options.length;
			double[][] in = new double[nActions][];
			for(int i = 0; i < nActions; i++){
				in[i] = removeBiasNConcat(stateVector, options[i]);
			}
			inputs.add(Nd4j.create(in));
			INDArray nLegalActions = Nd4j.create(1).assign(Double.valueOf(nActions));
			actionSetSize.putRow(idx, nLegalActions);
			idx++;
		}
		//use inputs as placeholder for labels as we are not computing any loss or backprop anyway
		input = Nd4j.vstack(inputs.toArray(new INDArray[0]));
		
		if(maskInput)
			input = CatanFeatureMaskingUtil.maskConcatenatedArrays(input);
		
		DataSet data = new DataSet(input, input);
		norm.normalizeZeroMeanUnitVariance(data);
			
		synchronized (model) {//prevents two seeder threads using the same model at the same time
			output = model.output(data, actionSetSize);
		}
		
		//check for NaNs (sometimes happens on the synthetic data due to overfitting to a single player type)
		for(int i = 0; i < output.length(); i++){
			if(Double.isNaN(output.getDouble(i))){
				return;
			}
		}
		
		idx = 0;
		int outIdx = 0;
		for(int j = 0; j < nodes.size(); j++){
			n = nodes.get(j);
			AtomicDoubleArray dist = ((StandardNode)n).pValue;
			double[] arr = new double[dist.length()];
			double[] test = new double[dist.length()];
			double sumP = 0;
			for(int i = 0; i < arr.length; i++){
				arr[i] = output.getDouble(outIdx);
				test[i] = output.getDouble(outIdx);
				if(trigger.temperature > 1){
					arr[i] = Math.pow(arr[i], 1.0/trigger.temperature);
					sumP += arr[i];
				}
				outIdx++;
			}
			if(trigger.temperature > 1)
				for(int i = 0; i < arr.length; i++){
						arr[i]/=sumP;
				}
			
			if(trigger.seedPercentage > 0 && trigger.seedPercentage < 100){//Note: this doesn't always work with a prior!!!
				//number of actions to bias towards
				int nActions =  (int) (dist.length()*trigger.seedPercentage/100);
				nActions = Math.max(nActions, 1);
				if(nActions < dist.length()){
					double smallest = selectKth(arr.clone(), arr.length - nActions);
					//iterate once to find the actual number of actions as there could be multiple with equal probability
					nActions = 0;
					for(int i = 0; i < arr.length; i++){
						if(arr[i] >= smallest){
							nActions++;
						}
					}
					//compute the two uniform distributions
					double seededProb = trigger.seedMass / nActions;
					double restProb = (1-trigger.seedMass) / (dist.length() - nActions);
					for(int i = 0; i < arr.length; i++){
						if(arr[i] < smallest){
							dist.set(i, restProb);
						}else{
							dist.set(i, seededProb);
						}
					}
				}else{
					if(!(trigger.pdf instanceof UniformActionTypePdf)) {
						double sum = 0;
						for(int i = 0; i < arr.length; i++){
							arr[i] *= dist.get(i);
							sum += arr[i];
						}
						//normalise
						for(int i = 0; i < arr.length; i++){
							arr[i] /= sum;
						}
					}
					
					((StandardNode)n).pValue = new AtomicDoubleArray(arr);
				}
			}else if(trigger.seedPercentage == 100){
				
				if(trigger.lambda < 1.0) {
					double sum = 0;
					double[] newArr = new double[arr.length];
					for(int i = 0; i < arr.length; i++){
						newArr[i] = trigger.lambda * arr[i] + (1-trigger.lambda)* dist.get(i);
						sum += newArr[i];
					}
					//normalise
					for(int i = 0; i < newArr.length; i++){
						newArr[i] /= sum;
					}
					arr = newArr;
				}
					
				((StandardNode)n).pValue = new AtomicDoubleArray(arr);
			}
			idx++;
		}
		nEvaluatedNodes += nNodes;
		} catch (Exception e) {
			e.printStackTrace();
			
		}finally {
			trigger.notifySeederFinished(this);
		}

	}

	/**
	 * The feature selection/extraction process adds a bias and so does dl4j internally.
	 * This method removes the biases and builds the correct input for dl4j
	 * @param state
	 * @param action
	 * @return
	 */
	private double[] removeBiasNConcat(double[] state, double[] action) {
		int stateLen = state.length - 1;
		int actionLen = action.length - 1;
		double[] c = new double[stateLen + actionLen];
		System.arraycopy(state, 1, c, 0, stateLen);
		System.arraycopy(action, 1, c, stateLen, actionLen);
		return c;
	}
	
	/**
	 * Selection algorithm. Returns the kth element ordered natural (min to max)
	 * @param arr
	 * @param k
	 * @return
	 */
	private double selectKth(double[] arr, int k) {
		if (arr == null || arr.length <= k){
			System.out.println(arr.length + " " + k);
			throw new Error();
		}
			
		int from = 0, to = arr.length - 1;

		// if from == to we reached the kth element
		while (from < to) {
			int r = from, w = to;
			double mid = arr[(r + w) / 2];

			// stop if the reader and writer meets
			while (r < w) {

				if (arr[r] >= mid) { // put the large values at the end
					double tmp = arr[w];
					arr[w] = arr[r];
					arr[r] = tmp;
					w--;
				} else { // the value is smaller than the pivot, skip
					r++;
				}
			}

			// if we stepped up (r++) we need to step one down
			if (arr[r] > mid)
				r--;

			// the r pointer is on the end of the first k elements
			if (k <= r) {
				to = r;
			} else {
				from = r + 1;
			}
		}
		return arr[k];
	}
	
	
}