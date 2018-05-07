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
import mcts.game.catan.CatanWithBelief;
import mcts.game.catan.belief.CatanDeterminizationSampler;
import mcts.game.catan.typepdf.UniformActionTypePdf;
import mcts.seeder.Seeder;
import mcts.tree.node.StandardNode;
import mcts.tree.node.TreeNode;
import mcts.utils.GameSample;
import mcts.utils.HashMapList;
import mcts.utils.Options;
import representation.FVGenerator;
import representation.FVGeneratorFactory;
import soc.server.database.stac.DBGameParser;

/**
 * Implementation that uses the Neural Network to evaluate the node and set of legal actions. 
 * It adds probabilities of selecting certain actions to the nodes based on the policy learned by the NN.
 * It handles the imperfect information by sampling n observable states according to the belief and averaging the result.
 * 
 * @author sorinMD
 *
 */
public class SampleNNCatanSeeder extends Seeder{
	public static int nEvaluatedNodes = 0;
	private ArrayList<TreeNode> nodes;
	private int nNodes = 0;
	private CatanMlp model;
	private Normaliser norm;
	private NNCatanSeedTrigger trigger;
	private ArrayList<GameFactory> factories;
	private int nSamples;
	private double alpha;
	
	public SampleNNCatanSeeder(ArrayList<TreeNode> batch, CatanMlp model, Normaliser norm, NNCatanSeedTrigger trigger, ArrayList<GameFactory> factories, int nSamples, double alpha) {
		nodes = batch;
		for(TreeNode n : batch){
			nNodes+=((StandardNode)n).getChildren().size();
		}
		this.model = model;
		this.norm = norm;
		this.trigger = trigger;
		this.factories = factories;
		this.nSamples = nSamples;
		this.alpha = alpha;
	}
	
	@Override
	public void run() {
		//TODO: need to optimise this somehow since most of this stuff is already computed in the tree...
		//feature selection/extraction and code for translating to a format that the networks can understand
	    try {
	    	HashMapList<TreeNode, Double> probsList = new HashMapList<>();
	    	HashMapList<TreeNode, ArrayList<int[]>> actionLists = new HashMapList<>();
	    	CatanDeterminizationSampler stateSampler = new CatanDeterminizationSampler();
	    	INDArray actionSetSize = Nd4j.create(nodes.size()*nSamples, 1);
			ArrayList<INDArray> inputs = new ArrayList<INDArray>();
			int idx = 0;
			GameFactory factory;
			TreeNode n;
			for(int j = 0; j < nodes.size(); j++){
				n = nodes.get(j);
				factory = factories.get(j);
				GameSample[] stateSamples = stateSampler.sampleObservableStates(nSamples, factory.getGame(n.getState()), factory);
				for(GameSample sample : stateSamples) {
					probsList.put(n, sample.getProb());
					Options opts = sample.getGame().listPossiblities(false);
					actionLists.put(n, opts.getOptions());
					int[] state = sample.getGame().getState();
					FVGenerator gen = FVGeneratorFactory.getGenerator();
					double[] stateVector = Arrays.stream(gen.calculateStateVectorSS(state, CatanWithBelief.board)).asDoubleStream().toArray();
					double[][] options = DBGameParser.listPossibleActionVectors((Catan)sample.getGame(),opts);
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
			}
			//use inputs as placeholder for labels as we don't care about the loss or backprop anyway
			INDArray input = Nd4j.vstack(inputs.toArray(new INDArray[0]));
			DataSet data = new DataSet(input, input);
			norm.normalizeZeroMeanUnitVariance(data);
			
			INDArray output = null;
			synchronized (model) {//prevents two seeder threads using the same model at the same time
				output = model.output(data, actionSetSize);
			}
			
			//check for NaNs (happens only with the model trained on the synthetic data due to overfitting to a single player type)
			for(int i = 0; i < output.length(); i++){
				if(Double.isNaN(output.getDouble(i))){
					return;
				}
			}
			
			int outIdx = 0;
			for(int j = 0; j < nodes.size(); j++){
				n = nodes.get(j);
				AtomicDoubleArray dist = ((StandardNode)n).pValue;
				double[] total = new double[dist.length()];
				//add a uniform prior to smooth it out if we miss actions during sampling
				Arrays.fill(total, alpha/dist.length()); 
				ArrayList<int[]> beliefActions = ((StandardNode)n).getActions();
				ArrayList<Double> probs = probsList.get(n);
				Double p;
				//compute the action value as weighted average across the samples
				for(int k = 0; k < nSamples; k++) {
					p = probs.get(k);
					ArrayList<int[]> sampleActions = actionLists.get(n).get(k);
					double[] current = new double[sampleActions.size()];
					double sumP = 0;
					for(int i = 0; i < current.length; i++){
						current[i] = output.getDouble(outIdx);
						if(trigger.temperature > 1){
							current[i] = Math.pow(current[i], 1.0/trigger.temperature);
							sumP += current[i];
						}
						outIdx++;
					}
					if(trigger.temperature > 1)
						for(int i = 0; i < current.length; i++){
							current[i]/=sumP;
						}
					//add to the correct index in the total
					for(int i = 0; i < current.length; i++){
						for(int l = 0; l < total.length; l++) {
							if(Arrays.equals(beliefActions.get(l), sampleActions.get(i))) {
								total[l] += current[i] * p.doubleValue();
								break;
							}
						}
					}
				}
				//average it
				for(int i = 0; i < total.length; i++)
					total[i] /= (nSamples + alpha);
				
				if(trigger.seedPercentage > 0 && trigger.seedPercentage < 100){//Note: this doesn't always work with a prior!!!
					//number of actions to bias towards
					int nActions =  (int) (dist.length()*trigger.seedPercentage/100);
					nActions = Math.max(nActions, 1);
					if(nActions < dist.length()){
						double smallest = selectKth(total.clone(), total.length - nActions);
						//iterate once to find the actual number of actions as there could be multiple with equal probability
						nActions = 0;
						for(int i = 0; i < total.length; i++){
							if(total[i] >= smallest){
								nActions++;
							}
						}
						//compute the two uniform distributions
						double seededProb = trigger.seedMass / nActions;
						double restProb = (1-trigger.seedMass) / (dist.length() - nActions);
						for(int i = 0; i < total.length; i++){
							if(total[i] < smallest){
								dist.set(i, restProb);
							}else{
								dist.set(i, seededProb);
							}
						}
					}else{
						if(!(trigger.pdf instanceof UniformActionTypePdf)) {
							double sum = 0;
							for(int i = 0; i < total.length; i++){
								total[i] *= dist.get(i);
								sum += total[i];
							}
							//normalise
							for(int i = 0; i < total.length; i++){
								total[i] /= sum;
							}
						}
						((StandardNode)n).pValue = new AtomicDoubleArray(total);
					}
				}else if(trigger.seedPercentage == 100){
					
					if(trigger.lambda < 1.0) {
						double sum = 0;
						double[] newArr = new double[total.length];
						for(int i = 0; i < total.length; i++){
							newArr[i] = trigger.lambda * total[i] + (1-trigger.lambda)* dist.get(i);
							sum += newArr[i];
						}
						//normalise
						for(int i = 0; i < newArr.length; i++){
							newArr[i] /= sum;
						}
						total = newArr;
					}
					((StandardNode)n).pValue = new AtomicDoubleArray(total);
				}
									
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
