package supervised.main;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.URL;
import java.util.HashMap;

//import com.google.common.io.Resources;
//import resources.Resources;


import resources.Resources;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

public class RandomForestLearner {
	private Instances instances;
	private RandomForest forest;

	public RandomForestLearner( String trainingFile ) {
		try {
			String traFile = trainingFile + ".arff";
			String modFile = trainingFile + ".model";
			System.out.println( "RandomForestLearner> Reading training file: " + traFile );
			URL url_tra = Resources.class.getResource( traFile );
			InputStream is_tra = url_tra.openStream(); 
			loadData( is_tra );
			System.out.println( "RandomForestLearner> Loading model file: " + modFile );
			URL url_mod = Resources.class.getResource( modFile );
			loadModel( url_mod.openStream() );
			//trainModel(trainingFile);
			queryModel();
		} catch ( IOException e) {
			e.printStackTrace();
		}
	}

	public void loadData(String trainingFile) {
		try {
			System.out.println( "Reading " + trainingFile );
			loadData( new FileInputStream(trainingFile) );
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	public void loadData( InputStream tra_is ) {
		try {
			System.out.println( "RandomForestLearner> DEBUG: loadData..." );
			instances = DataSource.read(tra_is);
			instances.setClassIndex(instances.numAttributes() - 1);
			System.out.println("Data loaded!");

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void loadModel(String modelFile) {
		try {
			System.out.println( "Loading " + modelFile );
			loadModel( new FileInputStream(modelFile) );
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	public void loadModel( InputStream is ) {
		try {
			//forest = (RandomForest) weka.core.SerializationHelper.read(trainingFile);
			//@SuppressWarnings("resource")
			ObjectInputStream ois = new ObjectInputStream( is );
			forest = (RandomForest) ois.readObject();
			System.out.println("Model loaded!");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void trainModel(String trainingFile) {
		try {
			forest = new RandomForest();
			forest.setNumTrees(100);
			forest.buildClassifier(instances);
			System.out.println("Model created!");

			/*for (int i=0; i<instances.numAttributes(); i++) {
				Attribute attrib = instances.attribute(i);
				System.out.println("attrib("+i+")="+attrib.name());
				for (int j=0; j<attrib.numValues(); j++) {
					System.out.println("\t ["+j+"]="+attrib.value(j));
				}
			}*/

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void queryModel() {
		try {
			// double score = this.getFeatureScoreFromTrainedBayesNet(bn, evidence, "currDAT", "Receivable");
			Attribute label = instances.attribute(instances.numAttributes() - 1);
			double[] dist = forest.distributionForInstance(instances.firstInstance());
			for (int i=0; i<dist.length; i++) {
				String key = label.value(i);
				double val = dist[i];
				System.out.println("key="+key + " val="+val);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public double getFeatureScoreFromRandomFores(HashMap<String,String> evidence, String label, String value) {
		double score = 0;

		try {
			evidence.put(label, value);
			//System.out.println("evidence="+evidence);//.size());
			//System.out.println("instances="+instances.numInstances());

			// create instance
			//String[] values = new String[instances.numAttributes()];
			Instance firstInstance = (Instance) instances.firstInstance().copy();
			//Instance firstInstance = new DenseInstance(instances.numAttributes());//(Instance) instances.firstInstance();
			//String elemsStr = ""+firstInstance.toString();
			//ArrayList<String> elems = StringUtil.getArrayListFromString(elemsStr, ",");
			//System.out.println("The first instance: " + firstInstance); 
			//System.out.println("elems: " + elems); 
			//Instance newInstance = firstInstance;//new Instance(firstInstance.numAttributes());
			for (int i=0; i<instances.numAttributes()-1; i++) {
				Attribute attribute = (Attribute) instances.attribute(i);
				//Enumeration<Attribute> enumerator = firstInstance.enumerateAttributes();
				//while (enumerator.hasMoreElements()) {
				//Attribute attribute = enumerator.nextElement();
				if (attribute.isNumeric()) {
					//System.out.println("ATT="+attribute.name());
					int val = Integer.parseInt(evidence.get(attribute.name()));
					//System.out.println("ATT="+attribute.name() + " val="+val + " type="+attribute.toString());
					firstInstance.setValue(attribute, val);
				} else {
					String val = evidence.get(attribute.name());
					//System.out.println("ATT="+attribute.name() + " val="+val + " type="+attribute.toString());
					firstInstance.setValue(attribute, val);					
				}
			}
			//Instance instance = new Instance(1.0, values);

			// probabilistic inference
			Attribute attribute = firstInstance.attribute(instances.numAttributes()-1);
			double[] dist = forest.distributionForInstance(firstInstance);
			for (int i=0; i<dist.length; i++) {
				String attValue = attribute.value(i);
				//System.out.println("*value="+value + " attValue="+attValue);
				if (value.equals(attValue)) {
					score = dist[i];
					//System.out.println("attValue="+attValue + " score="+score);
				}
			}
			//System.out.println("bestScore="+score + "\n");


		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}

		return score;
	}

	public static void main(String[] args) {
		//new RandomForestLearner("simulation/combinedOffer.arff");
		new RandomForestLearner("simulation/combinedOffer");
	}
}
