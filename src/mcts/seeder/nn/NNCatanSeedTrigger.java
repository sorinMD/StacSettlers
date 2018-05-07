package mcts.seeder.nn;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.multilayer.CatanMlp;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions.LossFunction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.util.concurrent.AtomicDoubleArray;

import data.Normaliser;
import mcts.MCTS;
import mcts.game.GameFactory;
import mcts.game.catan.Catan;
import mcts.game.catan.CatanConfig;
import mcts.game.catan.GameStateConstants;
import mcts.game.catan.typepdf.ActionTypePdf;
import mcts.game.catan.typepdf.UniformActionTypePdf;
import mcts.seeder.SeedTrigger;
import mcts.seeder.Seeder;
import mcts.tree.node.StandardNode;
import mcts.tree.node.TreeNode;
import model.CatanMlpConfig;
import util.CatanFeatureMaskingUtil;
import util.ModelUtils;

/**
 * NN seeder trigger that can only handle the observable version of the game.
 * 
 * @author sorinMD
 *
 */
public class NNCatanSeedTrigger extends SeedTrigger implements GameStateConstants{
	@JsonIgnore
	private BlockingQueue<TreeNode>[] nodesQueue = new BlockingQueue[6];
	@JsonIgnore
	private BlockingQueue<GameFactory>[] factoriesQueue = new BlockingQueue[6];
	//NOTE: there are only 6 tasks, but we keep index 6 for the single model case
	@JsonIgnore
	private CatanMlp[] models = new CatanMlp[7];
	@JsonIgnore
	private Normaliser[] normalisers = new Normaliser[7];
	//same fields but for the case where we have an opponent model
	@JsonIgnore
	private BlockingQueue<TreeNode>[] oppNodesQueue = new BlockingQueue[6];
	@JsonIgnore
	private BlockingQueue<GameFactory>[] oppFactoriesQueue = new BlockingQueue[6];
	//NOTE: there are only 6 tasks, but we keep index 6 for the single model case
	@JsonIgnore
	private CatanMlp[] oppModels = new CatanMlp[7];
	@JsonIgnore
	private Normaliser[] oppNormalisers = new Normaliser[7];
	@JsonIgnore
	private CatanConfig gameConfig;
	public int batchSize = 10;
	/**
	 * Percentage of total actions that should be biased. Acts as a flag in
	 * deciding what policy to use for modifying the probability distribution. 
	 * If > 0 && < 100, it turns the seedMass into a uniform distribution over the
	 * top % actions.
	 * If = 100 it uses the raw (or with temperature) distribution from the classifier 
	 * If = 0 or > 100, it leaves the standard uniform distribution set in the node.
	 * Note: the number of actions is rounded down
	 */
	public double seedPercentage = 100;
	/**
	 * The probability mass as a value in [0,1] allocated to the top percentage actions.
	 */
	public double seedMass = 0.5;
	/**
	 * The temperature of the softmax output layer of the classifier
	 */
	public double temperature = 1;
	/**
	 * On what data was the model trained: synth vs human
	 */
	public String data_type = "synth";
	/**
	 * Model type: MOE vs SINGLE vs TL
	 */
	public String model_type = "MOE";
	/**
	 * Number of samples. Only used if the game is the imperfect information version
	 */
	public int nSamples = 4;
	/**
	 * Laplace smoothing value
	 */
	public double laplace_alpha = 0.1;
	/**
	 * Should we mask the input to hide what should be hidden information
	 */
	public boolean maskInputForOpponent = false;
	/**
	 * Needed to differentiate between the opponent data and our data.
	 */
	public int ourPlayerNumber = -1;
	/**
	 * PDF used for mixture
	 */
	public ActionTypePdf pdf = new UniformActionTypePdf();
	/**
	 * Interpolation parameter in range [0.0,1.0]. 1.0 means we only use the NN output. 
	 */
	public double lambda = 1.0;
	
	public NNCatanSeedTrigger() {
		for(int i = 0; i < nodesQueue.length; i++){
			//TODO: even though it is highly unlikely that this will go over it is better to make sure and find an appropriate number
			nodesQueue[i] = new ArrayBlockingQueue<TreeNode>(500000);
			factoriesQueue[i] = new ArrayBlockingQueue<GameFactory>(500000);
			oppNodesQueue[i] = new ArrayBlockingQueue<TreeNode>(500000);
			oppFactoriesQueue[i] = new ArrayBlockingQueue<GameFactory>(500000);
		}
	}
	/**
	 * Adds node to queue and launches if the number of nodes is equal to the batch size.
	 * TODO: probably a better approach would be to have the finished seeder launch a new set of seeding if the queues contain over a certain number of samples.
	 * @param node
	 */
	public void addNode(TreeNode node, GameFactory gameFactory){
		int task = getTaskIDFromState(node);
		if(task == -1)
			return;
		
		if(task == 2 && !(pdf instanceof UniformActionTypePdf))
			setProbFromTypePDF(node, gameFactory);
		
		if(maskInputForOpponent && ourPlayerNumber != node.getCurrentPlayer()) {
			if(task == 4) {
				if(mcts.getMCTSConfig().pomcp)//all discard options are listed in pomcp, but we don't have a model so skip it.
					return;
				System.err.println("Why are we asked to seed in discard task for opponent???");
			}
			//we must make this synchronized to have the correct node -> factory mapping and to not evaluate nodes multiple times
			synchronized (oppNodesQueue[task]) {
				if(node.isEvaluated())
					return;
				node.setEvaluated(true);
				//the below check may seem futile, but it is not!
				//Due to the way the expansion works, we may end up with the same node as a different object.
				if(!oppNodesQueue[task].contains(node)) {
					oppNodesQueue[task].add(node);
					oppFactoriesQueue[task].add(gameFactory);
				}
				if(oppNodesQueue[task].size() >= batchSize){
					if(aliveSeeders.size() < maxSeederCount){
						ArrayList<TreeNode> batch = new ArrayList<TreeNode>();
						ArrayList<GameFactory> factories = new ArrayList<GameFactory>();
						oppNodesQueue[task].drainTo(batch, batchSize);
						oppFactoriesQueue[task].drainTo(factories, batchSize);
						CatanMlp modelToUse;
						Normaliser normaliserToUse;
						if(model_type.equals("SINGLE")){
							modelToUse = oppModels[6];
							normaliserToUse = oppNormalisers[6];
						}else{
							modelToUse = oppModels[task];
							normaliserToUse = oppNormalisers[task];
						}
						Seeder s = new NNCatanSeeder(batch, modelToUse, normaliserToUse, this, factories, true);
						aliveSeeders.add(s);
						mcts.execute(s);
					}
				}
			}
		}else {
			//we must make this synchronized to have the correct node -> factory mapping and to not evaluate nodes multiple times
			synchronized (nodesQueue[task]) {
				if(node.isEvaluated())
					return;
				node.setEvaluated(true);
				//the below check may seem futile, but it is not!
				//Due to the way the expansion works, we may end up with the same node as a different object.
				if(!nodesQueue[task].contains(node)) {
					nodesQueue[task].add(node);
					factoriesQueue[task].add(gameFactory);
				}
				if(nodesQueue[task].size() >= batchSize){
					if(aliveSeeders.size() < maxSeederCount){
						ArrayList<TreeNode> batch = new ArrayList<TreeNode>();
						ArrayList<GameFactory> factories = new ArrayList<GameFactory>();
						nodesQueue[task].drainTo(batch, batchSize);
						factoriesQueue[task].drainTo(factories, batchSize);
						CatanMlp modelToUse;
						Normaliser normaliserToUse;
						if(model_type.equals("SINGLE")){
							modelToUse = models[6];
							normaliserToUse = normalisers[6];
						}else{
							modelToUse = models[task];
							normaliserToUse = normalisers[task];
						}
						Seeder s;
						if(gameFactory.getBelief() != null)
							s = new SampleNNCatanSeeder(batch, modelToUse, normaliserToUse, this, factories, nSamples, laplace_alpha);
						else
							s = new NNCatanSeeder(batch, modelToUse, normaliserToUse, this, factories,false);
						aliveSeeders.add(s);
						mcts.execute(s);
					}
				}
			}
		}
	}
	
	protected void notifySeederFinished(Seeder seeder){
		aliveSeeders.remove(seeder);
	}
	
	/**
	 * Logic to select the model to use for seeding. 
	 * Reports errors if seeding is called when the node is a chance node.
	 * @param n the tree node containing the state description
	 * @return
	 */
	private int getTaskIDFromState(TreeNode n){
		int[] state = n.getState();
		int fsmlevel = state[OFS_FSMLEVEL];
		int fsmstate = state[OFS_FSMSTATE + fsmlevel];
		
		switch (fsmstate) {
		case S_SETTLEMENT1:
		case S_SETTLEMENT2:
			return 1;
		case S_ROAD1:
		case S_ROAD2:
			return 0;
		case S_BEFOREDICE:
			return 3;
		case S_DICE_RESULT:
			System.err.println("Why is a chance node (dice result) being expanded and then asked to be seeded?");
			return -1;
		case S_FREEROAD1:
		case S_FREEROAD2:
			return 0;
		case S_PAYTAX:
			int pl = state[OFS_FSMPLAYER + fsmlevel];
			int val = 0;
			for (int i = 0; i < NRESOURCES; i++)
				val += state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + i];
			val = val / 2;
			if(val <= gameConfig.N_MAX_DISCARD)
				return 4;
			return -1;//don't seed random discards
		case S_ROBBERAT7:
			return 5;
		case S_STEALING:
			System.err.println("Why is a chance node (stealing a rss) being expanded and then asked to be seeded?");
			return -1;
		case S_NORMAL:
			return 2;
		case S_BUYCARD:
			System.err.println("Why is a chance node (deal dev card) being expanded and then asked to be seeded?");
			return -1;
		case S_NEGOTIATIONS:
			return -1;//TODO: don't seed negotiations yet
		}
		System.err.println("Unrecognized state type???");
		return -1;//TODO: this can't happen, perhaps stop the search if this happens?
	} 
	
	private void setProbFromTypePDF(TreeNode node, GameFactory factory) {
		Catan game = (Catan) factory.getGame(node.getState());
		ArrayList<Integer> types = game.listNormalActionTypes();
		Map<Integer,Double> dist = pdf.getDist(types);
		//trades are A_TRADE in rollouts, but could be A_OFFER in tree, so handle this aspect here
		if(((CatanConfig)factory.getConfig()).NEGOTIATIONS) {
			if(dist.containsKey(A_TRADE)) {
				dist.put(A_OFFER, dist.get(A_TRADE));
				dist.remove(A_TRADE);
			}
		}
		ArrayList<int[]> actions = game.listPossiblities(false).getOptions();
		double[] prob = new double[actions.size()];
		for(Integer t : dist.keySet()) {
			int count = 0;
			for(int[] act : actions) 
				if(act[0] == t)
					count++;
			double val = dist.get(t)/(double)count;
			for(int i = 0; i < actions.size(); i++) {
				if(actions.get(i)[0] == t)
					prob[i] = val;
			}
		}
		((StandardNode)node).pValue = new AtomicDoubleArray(prob);
	}
	
	
	public void cleanUp(){
		for(int i = 0; i < nodesQueue.length; i++){
			nodesQueue[i].clear();
			factoriesQueue[i].clear();
			oppNodesQueue[i].clear();
			oppFactoriesQueue[i].clear();
		}
		aliveSeeders.clear();
	}
	
	private void initSeedingModels(){
		//TODO: Store this configuration somehow instead of hardcoding it?...we only care about the input and output size though.
		CatanMlpConfig modelConfig = new CatanMlpConfig(230, 1, 123, 1, WeightInit.XAVIER, Updater.RMSPROP, 0.01, LossFunction.MCXENT, OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT);
		String model_path = ModelUtils.PARAMS_PATH + data_type + "/" + model_type + "/";
		String norm_path = ModelUtils.PARAMS_PATH + data_type + "/" + "normalisation";
		if(model_type.equals("SINGLE")){
			String paramPath = model_path + CatanMlp.class.getName() + "-6.bin";
			normalisers[6] = new Normaliser(norm_path,false);
			normalisers[6].loadNormalisationParameters(new File(norm_path + "/Norm-all.dat"));
			models[6] = modelConfig.init();
			//read params
			try {
	            DataInputStream dis = new DataInputStream(new FileInputStream(paramPath));
	            INDArray newParams = Nd4j.read(dis);
	            dis.close();
	            models[6].setParams(newParams);
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
		}else{
			for(int i = 0 ; i < models.length - 1; i++){
				String paramPath = model_path + CatanMlp.class.getName() + "-" + i + ".bin";
				//initialise normaliser
				normalisers[i] = new Normaliser(norm_path,false);
				if(model_type.equals("MOE")){
					normalisers[i].loadNormalisationParameters(new File(norm_path + "/Norm-" + i + ".dat"));
				}else{
					//transfer learning
					normalisers[i].loadNormalisationParameters(new File(norm_path + "/Norm-all.dat"));
				}
				models[i] = modelConfig.init();
				//read params
				try {
		            DataInputStream dis = new DataInputStream(new FileInputStream(paramPath));
		            INDArray newParams = Nd4j.read(dis);
		            dis.close();
		            models[i].setParams(newParams);
		        } catch (IOException e) {
		            e.printStackTrace();
		        }
			}
		}
		//now initialise the models for the opponents
		if(maskInputForOpponent) {
			//TODO: Store this configuration somehow instead of hardcoding it?...we only care about the input and output size though.
			modelConfig = new CatanMlpConfig(230 - CatanFeatureMaskingUtil.droppedFeaturesCount * 2, 1, 123, 1, WeightInit.XAVIER, Updater.RMSPROP, 0.01, LossFunction.MCXENT, OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT);
			model_path = ModelUtils.PARAMS_PATH + data_type + "/maskedModels/" + model_type + "/";
			norm_path = ModelUtils.PARAMS_PATH + data_type + "/" + "normalisation";
			if(model_type.equals("SINGLE")){
				String paramPath = model_path + CatanMlp.class.getName() + "-6.bin";
				oppNormalisers[6] = new Normaliser(norm_path,true);
				oppNormalisers[6].loadNormalisationParameters(new File(norm_path + "/maskedInput/Norm-all.dat"));
				oppModels[6] = modelConfig.init();
				//read params
				try {
		            DataInputStream dis = new DataInputStream(new FileInputStream(paramPath));
		            INDArray newParams = Nd4j.read(dis);
		            dis.close();
		            oppModels[6].setParams(newParams);
		        } catch (IOException e) {
		            e.printStackTrace();
		        }
			}else{
				for(int i = 0 ; i < oppModels.length - 1; i++){
					if(i == 4)
						continue; //there is no model for discard task for opponent
					String paramPath = model_path + CatanMlp.class.getName() + "-" + i + ".bin";
					//initialise normaliser
					oppNormalisers[i] = new Normaliser(norm_path,true);
					if(model_type.equals("MOE")){
						oppNormalisers[i].loadNormalisationParameters(new File(norm_path + "/maskedInput/Norm-" + i + ".dat"));
					}else{
						//transfer learning
						oppNormalisers[i].loadNormalisationParameters(new File(norm_path + "/maskedInput/Norm-all.dat"));
					}
					oppModels[i] = modelConfig.init();
					//read params
					try {
			            DataInputStream dis = new DataInputStream(new FileInputStream(paramPath));
			            INDArray newParams = Nd4j.read(dis);
			            dis.close();
			            oppModels[i].setParams(newParams);
			        } catch (IOException e) {
			            e.printStackTrace();
			        }
				}
			}
		}
	}
	
	public void setBatchSize(int bs){
		batchSize = bs;
	}
	
	public int getBatchSize(){
		return batchSize;
	}
	
	public void setMaxSeederCount(int msc){
		maxSeederCount = msc;
	}
	
	public int getMaxSeederCount(){
		return maxSeederCount;
	}
	
	@Override
	public void init(MCTS mcts) {
		gameConfig = (CatanConfig) mcts.getGameFactory().getConfig();
		initSeedingModels();
		super.init(mcts);
	}
	
	@Override
	public String toString() {
		return "[name-" + this.getClass().getName() + "; maxSeederCount-" + maxSeederCount + "; batchSize-" + batchSize + "; temperature-" + temperature 
				+ "; data_type-" + data_type + "; model_type-" + model_type + "; seedPercentage-" + seedPercentage + "; seedMass-" + seedMass + "]";
	}
	
}
