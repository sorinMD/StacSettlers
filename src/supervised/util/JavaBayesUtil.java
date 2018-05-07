package supervised.util;

import java.util.*;

import InferenceGraphs.InferenceGraph;
import InferenceGraphs.InferenceGraphNode;
import JavaBayes.JavaBayes;


/**
 * @version 0.1 Implements generic methods for manipulating Bayesian networks
 */
public class JavaBayesUtil {
	private JavaBayes jb;
	private InferenceGraph ig;
	private HashMap<String,Integer> nodeIndexes;
	private String jbFileName;
	private int col;
	private int row;
	public String name;
	public boolean existentBN = false;
	public CharacterUtil characterUtil;

	/**
	 * main method used to instantiate this class
	 */
	public static void main(String argv[]) {	
		new JavaBayesUtil(argv[0], argv[1]);
	}

	/**
	 * constructor
	 */
	public JavaBayesUtil(String directory, String fileName) {
		name = fileName;
		jbFileName = directory + "/" + fileName;
		jb = new JavaBayes(jbFileName);
		col = 100;
		row = 200;
		characterUtil = new CharacterUtil();

		// loads or initializes the Bayesian network
		if (jb.open(jbFileName)) {
			System.out.println("JavaBayesUtil: Reading file " + jbFileName);
			ig = jb.editorFrame.get_inference_graph();
			existentBN = true;

		} else {
			nodeIndexes = new HashMap<String,Integer>();
			jb.clear();
			ig = new InferenceGraph();
			ig = jb.editorFrame.get_inference_graph();
			existentBN = false;
			this.resetBayesNet();
		}
		System.out.println("JavaBayesUtil.name="+name);
		System.out.println("JavaBayesUtil.existentBN="+existentBN);
	}

	/**
	 * return a set of key-value pairs from querying a random variable
	 */
	public boolean OpenBayesNet(String directory, String fileName) {
		return jb.open(directory + "/" + fileName);
	}

	/**
	 * return a set of key-value pairs from querying a random variable
	 */
	public HashMap<String,String> queryRandomVariable(String variable, String value) {
		HashMap<String,String> results = new HashMap<String,String>();

		InferenceGraphNode node = getBayesNetNode(variable, null);
		if (node == null) return null;

		jb.editorFrame.set_query_mode();
		InferenceGraph ig = new InferenceGraph();
		ig = jb.editorFrame.get_inference_graph();
		jb.editorFrame.process_query(ig, variable, results);

		return results;
	}

	/**
	 * return a set of key-value pairs from querying a random variable
	 */
	public String sampleRandomVariable(String variable) {
		ArrayList<String> parentVarValPairs = new ArrayList<String>();

		InferenceGraph ig = new InferenceGraph();
		ig = jb.editorFrame.get_inference_graph();

		// sample the children random variables
		for (Enumeration e = ig.elements(); e.hasMoreElements();) {
			InferenceGraphNode node = (InferenceGraphNode)(e.nextElement());
			String childVariable = node.get_name();

			if (!variable.equals(childVariable)) {
				HashMap<String,String> results = new HashMap<String,String>();
				jb.editorFrame.process_query(ig, childVariable, results);
				String sampledValue = getSampleFromDistribution(results);
				node.set_observation_value(sampledValue.substring(0, sampledValue.indexOf(":")));
				parentVarValPairs.add(childVariable + "=" + sampledValue);
			}
		}

		// query the BN with the just sample `variable-value pairs'
		HashMap<String,String> distribution = new HashMap<String,String>();
		jb.editorFrame.process_query(ig, variable, distribution);
		String sampledValue = getSampleFromDistribution(distribution);

		// clear the observation status for all random variables
		for (Enumeration e = ig.elements(); e.hasMoreElements();) {
			InferenceGraphNode node = (InferenceGraphNode)(e.nextElement());
			node.clear_observation();
		}

		return sampledValue;
	}

	/**
	 * return a sampled value from the given probability distribution
	 */
	public String getSampleFromDistribution(HashMap<String,String> distribution) {
		float randomProb = (float) Math.random();
		float cumulativeProb = 0;

		Set entries = distribution.entrySet();
		Iterator iter = entries.iterator();
		while (iter.hasNext()) {
			Map.Entry entry = (Map.Entry) iter.next();
			String key = (String) entry.getKey();
			key = (key.startsWith("_")) ? key.substring(1) : key;
			String value = (String) entry.getValue();
			cumulativeProb += new Float(value).floatValue();
			
			if (randomProb <= cumulativeProb) {
				key = characterUtil.convertCodesToChars(key);
				return key + ":" + randomProb;
			}
		}

		return null;
	}

	/**
	 * return a sampled value from the given probability distribution
	 */
	public static String getSampleFromDiscreteDistribution(HashMap<String,Float> distribution) {
		float randomProb = (float) Math.random();
		float cumulativeProb = 0;
		String key = "";
		float probability = 0;

		try {
			Set entries = distribution.entrySet();
			Iterator iter = entries.iterator();
			while (iter.hasNext()) {
				Map.Entry entry = (Map.Entry) iter.next();
				key = (String) entry.getKey();
				key = (key.startsWith("_")) ? key.substring(1) : key;
				probability = ((Float) entry.getValue()).floatValue();
				cumulativeProb += probability;
				if (randomProb <= cumulativeProb) {
					return key + ":" + probability;
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return key + ":" + probability; 
	}

	/**
	 * return a sampled value from the given probability distribution
	 */
	public static String getSampleFromProbabilityDistribution(HashMap<String,String> distribution) {
		float randomProb = (float) Math.random();
		float cumulativeProb = 0;
		String key = "";
		float probability = 0;

		Set entries = distribution.entrySet();
		Iterator iter = entries.iterator();
		while (iter.hasNext()) {
			Map.Entry entry = (Map.Entry) iter.next();
			key = (String) entry.getKey();
			key = (key.startsWith("_")) ? key.substring(1) : key;
			String value = (String) entry.getValue();
			probability = new Float(value).floatValue();
			cumulativeProb += probability;
			if (randomProb <= cumulativeProb) {
				return key + ":" + probability;
			}
		}

		return key + ":" + probability;
	}

	/**
	 * return the best entry of the given probability distribution
	 */
	public static String getBestEntryFromDistribution(HashMap<String,String> distribution,
			ArrayList<String> actionsAvailable, CharacterUtil characterUtil) {
		float cumulativeProb = 0;
		String bestKey = "";
		float bestValue = 0;

		if (distribution == null) return bestKey;

		Set entries = distribution.entrySet();
		Iterator iter = entries.iterator();
		while (iter.hasNext()) {
			Map.Entry entry = (Map.Entry) iter.next();
			String key = (String) entry.getKey();
			key = (key.startsWith("_")) ? key.substring(1) : key;
			key = characterUtil.convertCodesToChars(key);

			// proceeds if there are no constraints or if the action is valid
			if (actionsAvailable == null || actionsAvailable != null && actionsAvailable.contains(key)) {

				String value = (String) entry.getValue();
				//System.out.println("\t\t\tkey=" + key + " value=" + value);
				float probability = new Float(value).floatValue();
				if (probability>bestValue) {
					bestKey = key;
					bestValue = probability; 
				}
				cumulativeProb += probability;
			}
		}

		return bestKey + ":" + bestValue;
	}

	/**
	 * return a sample sampled value from the given probability distribution and value to ignore
	 */
	public String getSampleFromEqualDistribution(HashMap<String,String> distribution, String valueToIgnore) {
		ArrayList<String> list = new ArrayList<String>();

		list.addAll(distribution.keySet());

		String result = valueToIgnore;
		int counter = 0;

		while (result.equals(valueToIgnore) && counter <= list.size()) {
			int index = (int) Math.floor(Math.random() * list.size());
			result = list.get(index);
			counter++;
		}

		return result;
	}

	/**
	 * return a probability from a given variable-value pair
	 * note: the list "parentVarValPairs" has key-value pairs with the format "key=value"
	 */
	public double getProbFromVariableValuePairs(String childVar, String childVal, ArrayList<String> parentVarValPairs) {
		double prob = -1;
		
		try {
			if (parentVarValPairs.size() == 0) {
				HashMap<String, String> results = queryRandomVariable(childVar, childVal);
				//System.out.println("results=" + results);
				String result = results.get(childVal);
				result = (result == null && childVal.startsWith("_")) ? results.get(childVal.substring(1)) : result; // TODO: revise this underscore
				prob = new Double(result).doubleValue();

			} else {
				InferenceGraphNode node = getBayesNetNode(childVar, null);
				String[][] variable_value_pairs = new String[parentVarValPairs.size()+1][2];
				variable_value_pairs[0][0] = childVar;
				variable_value_pairs[0][1] = childVal;

				for (int i=0; i<parentVarValPairs.size(); i++) {
					String parentVarVal = parentVarValPairs.get(i);
					String parentVar = parentVarVal.substring(0, parentVarVal.indexOf("="));
					String parentVal = parentVarVal.substring(parentVarVal.indexOf("=")+1);
					variable_value_pairs[i+1][0] = parentVar;
					variable_value_pairs[i+1][1] = parentVal;
				}
				prob = node.get_function_value(variable_value_pairs, 0);
			}

		} catch(Exception e) {
			e.printStackTrace();
			System.out.println("Error in JavaBayesUtil.getProbFromVariableValuePairs(): Coudln't get probability for " +
					childVar + "=" + childVal + " parentVarValPairs=" + parentVarValPairs);
		}

		return prob;
	}

	/**
	 * return the structure of the given graph node
	 */
	public InferenceGraphNode getBayesNetNode(String name, String newName) {
		int index = 0;

		for (Enumeration e = ig.elements(); e.hasMoreElements(); index++) {
			InferenceGraphNode node = (InferenceGraphNode)(e.nextElement());
			if (name.equals(node.get_name())) {
				if (newName != null) {
					nodeIndexes.put(newName, new Integer(index));
				}
				return node;
			}
		}

		return null;
	}

	/**
	 * return the structure of the current Bayesian network
	 */
	public void resetBayesNet() {
		int index = 0;

		for (Enumeration e = ig.elements(); e.hasMoreElements(); index++) {
			InferenceGraphNode node = (InferenceGraphNode)(e.nextElement());
			ig.delete_node(node);
		}
	}

	/**
	 * save the structure of the current Bayesian network
	 */
	public void saveBayesNet() {
		jb.xml_format_action();
		jb.save(jbFileName);
	}

	/**
	 * return the structure of the given graph node
	 */
	public boolean setObservedNode(String name, String value) {
		for (Enumeration e = ig.elements(); e.hasMoreElements();) {
			InferenceGraphNode node = (InferenceGraphNode)(e.nextElement());
			//System.out.println("name=" + name + " node.get_name()=" + node.get_name());
			if (name.equals(node.get_name())) {
				node.set_observation_value(value);
				return true;
			}
		}

		return false;
	}

	/**
	 * clear the observation of the given graph node
	 */
	public void clearObservedNode(String name) {
		for (Enumeration e = ig.elements(); e.hasMoreElements();) {
			InferenceGraphNode node = (InferenceGraphNode)(e.nextElement());
			if (name.equals(node.get_name())) {
				node.clear_observation();
			}
		}
	}

	/**
	 * create a random variable in the current Bayesian network
	 */
	public void createRandomVariable(String name, HashMap<String, Float> distribution, int level) {
		row = 100 * level;

		try {
			// set name of random variable
			String nodeName = ig.create_node(col, row);
			InferenceGraphNode node = getBayesNetNode(nodeName, name);
			node.set_name(name);
			String[] names = new String[distribution.size()]; 
			int index = 0;

			// set distribution for random variable
			Set entries = distribution.entrySet();
			Iterator iter = entries.iterator();
			while (iter.hasNext()) {
				Map.Entry entry = (Map.Entry) iter.next();
				String variable = (String) entry.getKey();
				variable = (Character.isDigit(variable.charAt(0))) ? "_" + variable : variable;
				names[index] = variable.replace(".", "_");
				index++;
			}
			node.set_values(names);

			col += (level == 1) ? 100 : 0;

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * create a random variable in the current Bayesian network
	 */
	public void createDependenciesOfRandomVariables(String variable, ArrayList<String> dependencies) {
		InferenceGraphNode childNode = getBayesNetNode(variable, null);
		if (childNode == null) System.out.println("Error in JavaBayesUtil.createDependenciesOfRandomVariables(): Couldn't find variable=" + variable);

		for (int i=0; i<dependencies.size(); i++) {
			String dependency = (String) dependencies.get(i);
			InferenceGraphNode parentNode = getBayesNetNode(dependency, null);
			if (childNode == null) System.out.println("Error in JavaBayesUtil.createDependenciesOfRandomVariables(): Couldn't find dependency=" + dependency);
			ig.create_arc(parentNode, childNode);
		}
	}

	/**
	 * create the node corresponding to the given prior probability (scored list)
	 */
	public void setSimpleConditionalProbabilityTable(String name, HashMap<String, Float> distribution) {
		InferenceGraphNode node = getBayesNetNode(name, null);
		String[] nodeNames = node.get_values();
		double[] nodeValues = new double[nodeNames.length];
		int index = 0;

		Set entries = distribution.entrySet();
		Iterator iter = entries.iterator();
		while (iter.hasNext()) {
			Map.Entry entry = (Map.Entry) iter.next();
			String variable = (String) entry.getKey();
			variable = (Character.isDigit(variable.charAt(0))) ? "_" + variable : variable;
			nodeNames[index] = variable.replace(".", "_");
			Float value = (Float) entry.getValue();
			nodeValues[index] = (double) value.floatValue(); 
			index++;
		}

		node.set_values(nodeNames);
		node.set_function_values(nodeValues);
	}

	/**
	 * create the node corresponding to the given prior conditional probability table (CPT)
	 */
	public String setGenericConditionalProbabilityTable(String name, HashMap<String, HashMap<String, Float>> cpt) {
		InferenceGraphNode node = getBayesNetNode(name, null);
		String bestState = "";
		String variable = "";
		String value = "";
		double valueBestState = 0;

		Set entries = cpt.entrySet();
		Iterator iter = entries.iterator();
		while (iter.hasNext()) {
			Map.Entry entry = (Map.Entry) iter.next();
			String relationalState = (String) entry.getKey();
			HashMap<String, Float> distribution = (HashMap<String, Float>) entry.getValue();

			ArrayList<String> variables = new ArrayList<String>();
			ArrayList<String> values = new ArrayList<String>();
			StringTokenizer tokens = new StringTokenizer(relationalState, "$");
			while (tokens.hasMoreTokens()) {
				String token = tokens.nextToken();

				if (token.endsWith("_true_")) {
					variable = token.substring(0, token.indexOf("_true_"));
					value = "true";
				} else if (token.endsWith("_false_")) {
					variable = token.substring(0, token.indexOf("_false_"));
					value = "false";
				} else {
					variable = token.substring(0, token.indexOf("_"));
					value = token.substring(token.indexOf("_")+1);
					value = value.substring(0,value.indexOf("_"));
				}

				variables.add(variable);
				values.add(value);
			}

			Set entries2 = distribution.entrySet();
			Iterator iter2 = entries2.iterator();
			while (iter2.hasNext()) {
				Map.Entry entry2 = (Map.Entry) iter2.next();
				String nodeName = (String) entry2.getKey();
				nodeName = nodeName.replace(".", "_");
				double prob = (double) ((Float) entry2.getValue()).floatValue();

				String[][] variable_value_pairs = new String[variables.size()+1][2];
				variable_value_pairs[0][0] = node.get_name();
				variable_value_pairs[0][1] = nodeName;
				for (int i=0; i<variables.size(); i++) {
					variable = (String) variables.get(i);
					value = (String) values.get(i);
					variable_value_pairs[i+1][0] = variable;
					variable_value_pairs[i+1][1] = value.replace(".", "_");
				}
				node.set_function_value(variable_value_pairs, prob, 0);
				bestState = (valueBestState < prob) ? nodeName : bestState;
			}
		}

		return bestState;
	}
}
