package supervised.main;

import supervised.util.CharacterUtil;
import supervised.util.JavaBayesUtil;
import supervised.util.HashMapUtil;

import simpleDS.util.IOUtil;
import simpleDS.util.StringUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

public class BayesianSupervisedLearner {
	private ArrayList<String> mainResources;
	private ArrayList<String> otherResources;
	private ArrayList<String> trainingData_combinedOffer;
	private ArrayList<String> trainingData_combinedAcceptReject;
	private ArrayList<String> trainingData_combinedCounterofferReject;
	private ArrayList<String> featureList;
	private ArrayList<String> listDAT;
	private ArrayList<String> gameList;
	private HashMap<String,Integer> userList;
	private HashMap<String,Integer> datCounts;
	private HashMap<String,ArrayList<String>> trainingData_combinedOffer_perResource;
	private HashMap<String,ArrayList<String>> tradePreferences;
	private CharacterUtil characterUtil;
	public JavaBayesUtil bn;
	public RandomForestLearner rf;
	public RandomForestLearner eduLabellerRF;
	//public trader crf;
	public boolean withPreferences;
	public int counter=0;
	public int counterFullOffers=0;
	public int counterDishonest=0;
	private final static int MAX_NUMERIC_VALUE = 7;

	public BayesianSupervisedLearner(String filePath, String execMode, boolean withPreferences) {
		mainResources = new ArrayList<String>();
		otherResources = new ArrayList<String>();
		trainingData_combinedOffer = new ArrayList<String>();
		trainingData_combinedAcceptReject = new ArrayList<String>();
		trainingData_combinedCounterofferReject = new ArrayList<String>();
		featureList = new ArrayList<String>();
		listDAT = new ArrayList<String>();
		gameList = new ArrayList<String>();
		userList = new HashMap<String,Integer>();
		datCounts = new HashMap<String,Integer>();
		trainingData_combinedOffer_perResource = new HashMap<String,ArrayList<String>>();
		tradePreferences = new HashMap<String,ArrayList<String>>();
		characterUtil = new CharacterUtil();
		this.withPreferences = false;//withPreferences;

		// initialize a vector of resources
		mainResources.add("clay");
		mainResources.add("ore");
		mainResources.add("sheep");
		mainResources.add("wheat");
		mainResources.add("wood");

		otherResources.add("roads");
		otherResources.add("settlements");
		otherResources.add("cities");

		// initialize list of features
		for (String resource : mainResources) {
			featureList.add("has"+resource.toUpperCase());
		}
		//featureList.add("hasKNIGHTS");
		//featureList.add("hasDEVCARDS");
		featureList.add("hasROADS");
		featureList.add("hasSETTLEMENTS");
		featureList.add("hasCITIES");
		for (String resource : mainResources) {
			featureList.add("rec"+resource.toUpperCase());
		}
		for (String resource : mainResources) {
			featureList.add("giv"+resource.toUpperCase());
		}

		if (this.withPreferences) {
			for (String resource : mainResources) {
				featureList.add("prep"+resource.toUpperCase());
			}
			for (String resource : mainResources) {
				featureList.add("preo"+resource.toUpperCase());
			}
		}

		// load Bayesian network
		if (!execMode.equals("training")) {
			if (execMode.equals("simulation")) {
				featureList.add("prevDAT");
				featureList.add("currDAT");
				String simulationFolder  = filePath;//(withPreferences) ? "simulation2" : "simulation";
				rf = new RandomForestLearner(simulationFolder+"/combinedOffer");
				bn = new JavaBayesUtil(simulationFolder, "combinedOffer_readable.bif");
				//crf = null;//new trader();

			} else if (execMode.equals("trading")) {
				featureList.add("DAT");
				bn = null;
				rf = new RandomForestLearner("simulation/combinedAcceptReject.arff");

			} else if (execMode.equals("counteroffering")) {
				//featureList.add("legalMove");
				featureList.add("DAT");
				bn = null;//new JavaBayesUtil("simulation", "combinedCounterofferReject_readable.bif");	
				rf = new RandomForestLearner("simulation/combinedCounterofferReject.arff");

			} else {
				System.out.println("ERROR: unknown execMode="+execMode);
				System.exit(0);
			}
			IOUtil.printArrayList(featureList, "featureList="+featureList.size());

			if (bn != null) {
				System.out.println("->bn.name="+bn.name);
				System.out.println("->bn.existentBN="+bn.existentBN);
				System.out.println("->filePath="+filePath);
				System.out.println("->execMode="+execMode);
			}
		}	
	}

	public BayesianSupervisedLearner(String filePath, String outputFileOffer, String outputFileAcceptReject, String outputFileCounterofferReject, String execMode, boolean withPreferences) {
		this(filePath, execMode, withPreferences);
		int counter = 0;

		// load data for semi-supervised learning -- uncomment to generate data
		/* PROCEDURE TO RUN the next block:
		   1) comment the line that creates "eduLabellerRF"
		   2) train the automatic labeller (edu_ato_labelling.arff)
		   3) uncomment the line that creates "eduLabellerRF"
		   4) run this class to generate the files within data/rawfeatures/automatic_labels
		   5) change the input parameter to take the automatic labels into account (otherwise it would use the manual ones)
		 */
		
		// from EDUCE-based extracted raw features
		if (execMode.equals("training")) {
			String[] dataFiles = (new File(filePath + "/")).list();
			for (String fileID : dataFiles) {
				if (fileID.startsWith("rawfeatures") && fileID.endsWith(".txt")) {
					System.out.println("PROCESSING FILE="+fileID);
					ArrayList<String> lines = new ArrayList<String>();
					IOUtil.readArrayList(filePath + "/" + fileID, lines);

					//String prevDAT = "Other";
					for (String line : lines) {
						if (line.indexOf(" units ")>0 && (line.endsWith("GOLD") || line.endsWith("SILVER") || line.endsWith("BRONZE"))) {
							if (tradePreferences.size()>0) {
								//IOUtil.printHashMapArrayList(tradePreferences, "tradePreferences");
								tradePreferences = new HashMap<String,ArrayList<String>>();
								//System.exit(0);
							}
							System.out.println("IGNORED: "+line);
							String gameName = line.substring(line.indexOf(":")+1, line.indexOf("["));
							if (!gameList.contains(gameName)) {
								gameList.add(gameName);
							}
							continue;
						} else {
							line = replaceApostrophe(line, "n't", "nnot");
							line = replaceApostrophe(line, "'ll", " will");
							line = replaceApostrophe(line, "'lkl", " will");
							line = replaceApostrophe(line, "'m", " am");
							line = replaceApostrophe(line, "'m", " am");
							line = replaceApostrophe(line, "re's", "re is");
							line = replaceApostrophe(line, "'d", " would");
							line = replaceApostrophe(line, "N'T", "NNOT");
							line = replaceApostrophe(line, "'ve", " have");
							line = replaceApostrophe(line, "'re", " are");
							line = replaceApostrophe(line, "y'", "you ");
							line = replaceApostrophe(line, "one's", "ones");
							line = replaceApostrophe(line, "it's", "it is");
							line = replaceApostrophe(line, "he's", "he is");
							line = replaceApostrophe(line, "hat's", "hat is");
							line = replaceApostrophe(line, "c's", "c s");
							line = replaceApostrophe(line, "d's", "d s");
							line = replaceApostrophe(line, "m's", "m s");
							line = replaceApostrophe(line, "n's", "n s");
							line = replaceApostrophe(line, "t's", "t us");
							line = line.replace("[", " ");
							line = line.replace("]", " ");
							line = line.trim();
						}

						ArrayList<String> listOfRawFeatures = new ArrayList<String>();
						ArrayList<String> tempo = StringUtil.getArrayListFromString(line, "'");
						for (String item : tempo) {
							item = item.trim();
							if (item.indexOf("[")>=0 || item.equals(", u") || item.length()==1 || item.indexOf("]")>=0) {
								continue;
							} 
							System.out.println("item="+item);
							listOfRawFeatures.add(item);
						}

						// Extract users
						String player = listOfRawFeatures.get(1);
						player = player.replace(",", " ");
						player = player.trim();

						// record players taken into account
						HashMapUtil.incrementHashTable(userList, player, 1);

						System.out.println("line="+line);
						String resources = listOfRawFeatures.get(listOfRawFeatures.size()-2)+" "+ listOfRawFeatures.get(listOfRawFeatures.size()-1);
						//String beingAsked = (line.indexOf("Offer")>0) ? "F" : "T";
						HashMap<String,String> featureValues = getFeatureVectorFromRawFeatures(resources);
						featureValues.putAll(getGivablesReceivablesFromRawFeatures(listOfRawFeatures, featureValues));

						// givables in the current turn by player in turn
						HashMap<String,String> localGivables = new HashMap<String,String>();
						for (String resource : mainResources) {
							String value = featureValues.get("giv"+resource.toUpperCase());
							//String valPrep = featureValues.get("prep"+resource.toUpperCase());
							if (value != null && !value.equals("0")) {
								localGivables.put("giv"+resource, value);								
							}
						}

						//IOUtil.printHashMapArrayList(tradePreferences, "-tradePreferences, player="+player);
						featureValues.putAll(getGivablesReceivablesFromPreferencesOfOpponents(player));
						//IOUtil.printHashMapArrayList(tradePreferences, "+tradePreferences, player="+player);
						//featureValues.put("Receivable", receivable);
						//featureValues.put("Givable", givable);
						//featureValues.put("beingAsked", beingAsked);
						String dat = getDialogueActType(listOfRawFeatures.get(2));
						featureValues.put("DAT", dat);
						System.out.println(line + " => " + featureValues + " dat="+dat);
						counter++;
						//System.exit(0);

						String legalMove = featureValues.get("legalMove");
						//legalMove = (legalMove==null) ? "no" : legalMove;

						if (legalMove.equals("no")) {
							counterDishonest++;
						}

						System.out.println(">featureList="+featureList);
						System.out.println(">featureValues="+featureValues);

						for (String resource : mainResources) {
							String valueREC = featureValues.get("rec"+resource.toUpperCase());
							String valueGIV = featureValues.get("giv"+resource.toUpperCase());
							String valuePREP = featureValues.get("prep"+resource.toUpperCase());
							if (valueREC != null && valuePREP != null && !valuePREP.equals("0") && valueREC.equals("0")) {
								System.out.println("WARNING: valueREC="+valueREC + " valuePREP="+valuePREP);
							}
							if (valueGIV != null && valuePREP != null && !valueGIV.equals("0") && valuePREP.equals("0")) {
								featureValues.put("prep"+resource.toUpperCase(), "1");
							}
						}

						String label = null;
						for (String feature : this.featureList) {
							String value = featureValues.get(feature);

							if (value != null && feature.startsWith("giv")) {
								label = feature.substring(3).toUpperCase();
								//int prevDATint = getDialogueActTypeINT(prevDAT);
								String instance = getInstanceFromFeatures(featureList, featureValues);
								String myInstance = instance + "," + label;
								System.out.println("*myInstance="+myInstance);
								trainingData_combinedOffer.add(myInstance);
								counterFullOffers++;
								break;
							}									
						}

						dat = (dat == null) ? "Other" : dat;
						if (!listDAT.contains(dat)) {
							listDAT.add(dat);
						}
					}
				}
			}
			printTrainingData_combined(outputFileOffer, outputFileAcceptReject, outputFileCounterofferReject);
			printTrainingData_combined_binary(trainingData_combinedOffer);
			System.out.println("counter="+counter);
			System.out.println("counterFullOffers="+counterFullOffers);
			System.out.println("counterDishonest="+counterDishonest);
			System.out.println("counterGames="+gameList.size());
			System.out.println("counterPlayers="+userList.size());
			IOUtil.printArrayList(listDAT, "listDAT*");
			IOUtil.printHashMap(datCounts, "datCounts");
		}
	}

	public boolean sentenceHasResource(String sentence) {
		for (String resource : this.mainResources) {
			if (sentence.indexOf(resource)>=0) {
				return true;
			}
		}

		return false;
	}

	private String getInstanceFromFeatures(ArrayList<String> featureList, HashMap<String,String> featureValues) {
		String instance = "";
		boolean anyReceivable = false;
		for (String attribute : this.featureList) {
			// comment condition if givables are required
			if (attribute.startsWith("giv")) {
				continue;
			}
			String val = featureValues.get(attribute);
			if (val == null) {
				val ="0";
			} else {
				if (attribute.startsWith("rec")) {
					anyReceivable = !val.equals("0") ? true : anyReceivable;
				}
			}
			instance += (instance.equals("")) ? val : ","+val;
		}
		System.out.println("*instance="+instance);

		return instance;
	}

	private HashMap<String,String> getFeatureVectorFromRawFeatures(String rawFeatures) {
		HashMap<String,String> featureValues = new HashMap<String,String>();
		ArrayList<String> hasItems = StringUtil.getArrayListFromString(rawFeatures, "; ");
		System.out.println("=>hasItems="+hasItems);
		for (String item : hasItems) {
			item = item.replace("'", " ");
			item = item.trim();
			System.out.println("item:"+item);
			
			if (item.indexOf("=")==-1) continue;
			String key = item.substring(0, item.indexOf("="));
			key = key.equals("brick") ? "clay" : key;
			String value = item.substring(item.indexOf("=")+1);
			int valueInt = Integer.parseInt(value);
			value = (valueInt>MAX_NUMERIC_VALUE) ? ""+MAX_NUMERIC_VALUE : value;

			if (mainResources.contains(key) || otherResources.contains(key)) {
				key = key.replace("-", "");
				System.out.println("*key="+key + " value="+value);
				featureValues.put("has"+key.toUpperCase(), value);
			}
		}

		return featureValues;
	}

	private String getPlayerName(String rawName) {
		String name = rawName;
		name = name.replace(",", " ");
		name = name.trim();
		return name;
	}

	private HashMap<String,String> getGivablesReceivablesFromRawFeatures(ArrayList<String> listOfRawFeatures, HashMap<String,String> availableResources) {
		HashMap<String,String> featureValues = new HashMap<String,String>();
		String prevDAT = "Other";
		String currDAT = "Other";
		String player = getPlayerName(listOfRawFeatures.get(1));

		featureValues.put("legalMove", "yes");

		IOUtil.printArrayList(listOfRawFeatures, "listOfRawFeatures");

		for (int i=3; i<listOfRawFeatures.size()-2; i++) {
			String exchangable = listOfRawFeatures.get(i);
			exchangable = (exchangable.startsWith(", u ")) ? exchangable.substring(4) : exchangable;
			exchangable = (exchangable.indexOf("/")==-1 && mainResources.contains(exchangable)) ? exchangable+"/Other" : exchangable;
			System.out.println("[exchangable]="+exchangable);
			String key = exchangable.substring(0, exchangable.indexOf("/"));
			String val = exchangable.substring(exchangable.indexOf("/")+1);
			System.out.println("*exchangable="+exchangable + " key="+key + " val="+val + " player="+player);
			
			HashMapUtil.incrementHashTable(datCounts, val, 1);

			if (val.startsWith("Givable")) {
				key = key.equals("brick") ? "clay" : key;
				String resource = key.toUpperCase();
				featureValues.put("giv"+resource, "1");

				// check if there is a resource to give (legal/illegal move)
				String hasResource = availableResources.get("has"+resource);
				System.out.println("*hasResource="+hasResource + " resource="+resource);
				if (hasResource != null && hasResource.equals("0")) {
					featureValues.put("legalMove", "no");
				}
				//}
				prevDAT = "Givable";

			} else if (val.startsWith("Receivable")) {
				key = key.equals("brick") ? "clay" : key;
				featureValues.put("rec"+key.toUpperCase(), "1");
				currDAT = "Receivable";

				// update preference records
				putTradePreferences(player, key);

			} else {
				// do nothing
			}
		}

		featureValues.put("prevDAT", prevDAT);
		featureValues.put("currDAT", currDAT);

		return featureValues;		
	}

	private HashMap<String,String> getGivablesReceivablesFromPreferencesOfOpponents(String player) {
		HashMap<String,String> results = new HashMap<String,String>();

		for (String resource : mainResources) {
			results.put("prep"+resource.toUpperCase(), "0");
			results.put("preo"+resource.toUpperCase(), "0");
		}

		for (String opponent : tradePreferences.keySet()) {
			// preferences of opponents
			if (!opponent.equals(player)) {
				ArrayList<String> resourcesPreferred = tradePreferences.get(opponent);
				for (String resource : resourcesPreferred) {
					results.put("preo"+resource.toUpperCase(), "1");
				}
				// preferences of player
			} else {
				ArrayList<String> resourcesPreferred = tradePreferences.get(player);
				for (String resource : resourcesPreferred) {
					results.put("prep"+resource.toUpperCase(), "1");
				}
			}
		}

		return results;
	}

	private String replaceApostrophe(String line, String source, String target) {
		int index = line.indexOf(source);
		System.out.println("source="+source + " target="+target + " line="+line);
		if (index>0) {
			String newLine = line.substring(0, index) + target + line.substring(index+source.length());
			if (newLine.indexOf("'")>=0 && newLine.indexOf("\"")>=0) {
				newLine = newLine.replace("\"", " ");
			} else {
				newLine = newLine.replace("\"", "'");
			}
			System.out.println("***line="+newLine);
			return newLine;

		} else {
			return line;
		}
	}

	private String getDialogueActType(String rawFeature) {
		rawFeature = rawFeature.trim();
		if (rawFeature.equals("Offer") || 
				rawFeature.equals("Accept") ||
				rawFeature.equals("Counteroffer")) {
			return rawFeature;
		} else if (rawFeature.equals("Refusal")) {
			return "Reject";
		} else {
			return null;//"Other";
		}
	}

	private int getDialogueActTypeINT(String dat) {
		if (dat.equals("Offer")){
			return 0;
		} else if (dat.equals("Counteroffer")) {
			return 1;
		} else if (dat.equals("Accept")) {
			return 2;
		} else if (dat.equals("Reject")) {
			return 3;
		} else {
			return 4;
		}
	}

	private void printTrainingData_combined(String outputFileOffer, String outputFileAcceptReject, String outputFileCounterofferReject) {
		ArrayList<String> data = new ArrayList<String>();
		ArrayList<String> dataOffer = new ArrayList<String>();
		ArrayList<String> dataAcceptReject = new ArrayList<String>();
		ArrayList<String> dataCounterofferReject = new ArrayList<String>();

		data.add("@relation stac_combined");
		data.add("");

		for (String feature : featureList) {
			if (feature.startsWith("giv")) continue;
			if (feature.startsWith("has")) {
				data.add("@attribute " + feature + "{0,1,2,3,4,5,6,7}");//{T, F}"); 
			} else {
				data.add("@attribute " + feature + "{0,1}"); 
			}
		}

		dataOffer.addAll(data);
		dataOffer.add("@attribute GIVABLE {CLAY, ORE, SHEEP, WHEAT, WOOD}");

		dataAcceptReject.addAll(data);
		dataAcceptReject.add("@attribute DAT {Accept, Reject, Counteroffer}"); 

		dataCounterofferReject.addAll(data);
		dataCounterofferReject.add("@attribute DAT {Counteroffer, Reject}"); 

		dataOffer.add("\n@data");
		dataAcceptReject.add("\n@data");
		dataCounterofferReject.add("\n@data");

		dataOffer.addAll(trainingData_combinedOffer);
		dataAcceptReject.addAll(trainingData_combinedAcceptReject);
		dataCounterofferReject.addAll(trainingData_combinedCounterofferReject);
		IOUtil.writeArrayList(outputFileOffer, dataOffer, "training data - combinedOffer");
		IOUtil.writeArrayList(outputFileAcceptReject, dataAcceptReject, "training data - combinedAcceptReject");
		IOUtil.writeArrayList(outputFileCounterofferReject, dataCounterofferReject, "training data - combinedCounterOfferReject");

		for (String resource : trainingData_combinedOffer_perResource.keySet()) {
			ArrayList<String> dataPerResource = new ArrayList<String>();
			ArrayList<String> dataPerResourceTuples = trainingData_combinedOffer_perResource.get(resource);
			dataPerResource.addAll(data);
			dataPerResource.add("@attribute TRADE {0,1}");
			dataPerResource.add("\n@data");
			dataPerResource.addAll(dataPerResourceTuples);
			String fileName = outputFileOffer.substring(0, outputFileOffer.indexOf(".")) + "_" + resource + ".arff";
			IOUtil.writeArrayList(fileName, dataPerResource, "training data - combinedOffer:"+resource);
		}
	}

	public void printTrainingData_combined_binary(ArrayList<String> trainingData_combinedOffer) {
		ArrayList<String> resourcesAndBuildups = new ArrayList<String>();
		ArrayList<String> features = new ArrayList<String>();
		ArrayList<String> data = new ArrayList<String>();

		resourcesAndBuildups.addAll(mainResources);
		resourcesAndBuildups.addAll(otherResources);

		data.add("@relation stac_combined");
		data.add("");

		for (String resource : resourcesAndBuildups) {
			for (int i=0; i<=BayesianSupervisedLearner.MAX_NUMERIC_VALUE; i++) {
				String feature = "has"+i+resource.toUpperCase();
				features.add(feature);
			}
		}
		for (String resource : mainResources) {
			String feature = "rec"+resource.toUpperCase();
			features.add(feature);
		}
		for (String resource : mainResources) {
			String feature = "giv"+resource.toUpperCase();
			features.add(feature);
		}

		for (String feature : features ) {
			data.add("@attribute " + feature + " NUMERIC");
		}
		data.add("@attribute GIVABLE {CLAY, ORE, SHEEP, WHEAT, WOOD}");
		data.add("\n@data");

		for (String instance : trainingData_combinedOffer) {
			ArrayList<String> observedFeatures = new ArrayList<String>();
			ArrayList<String> featureValues = StringUtil.getArrayListFromString(instance, ",");

			// get observed features for resources and buildups
			for (int i=0; i<featureValues.size(); i++) {
				String featureValue = featureValues.get(i);
				if (i<8) { // resources and buildups ONLY
					String resource = resourcesAndBuildups.get(i);//featureValueIndex);
					String observedFeature = "has"+featureValue+resource.toUpperCase();
					System.out.println("i="+i + " featureValues="+featureValues + " featureValue="+ featureValue + " resource="+resource + " observedFeature="+observedFeature);
					observedFeatures.add(observedFeature);
				} else {
					// do nothing with these features yet
				}
			}

			// get resources for receivables
			for (int i=0; i<mainResources.size(); i++) {
				if (featureValues.get(i+8).equals("1")) {
					String resource = mainResources.get(i);
					String observedFeature = "rec"+resource.toUpperCase();
					observedFeatures.add(observedFeature);
				}
			}

			// set binary features
			String featureVector = "";
			for (String binFeature : features) {
				if (observedFeatures.contains(binFeature)) {
					featureVector += "1,";
				} else {
					featureVector += "0,";
				}
			}
			featureVector += featureValues.get(featureValues.size()-1);
			data.add(featureVector);

			System.out.println("featureValues="+featureValues);
			System.out.println("observedFeatures="+observedFeatures);
			//System.exit(0);
		}
		//data.addAll(trainingData_combinedOffer);

		String outputFile = "simulation/combinedOffer_bin.arff";
		IOUtil.writeArrayList(outputFile, data, "training data - combinedOffer - BINARY");
	}

	public double getFeatureScoreFromTrainedBayesNet(JavaBayesUtil bayesNet, HashMap<String,String> features, String randVar2Query, String varValue) {
		String feature = "";
		String value = "";

		// replace apostrophe for html notation (inherithed from Weka+JavaBayes)
		if (varValue.indexOf("\'")>0) {
			String prefix = varValue.substring(0, varValue.indexOf("\'")-1);
			String rest = varValue.substring(varValue.indexOf("\'")+1);
			varValue = prefix + "&apos;" + rest;
		}

		String codedRandVar2Query = characterUtil.convertStringToASCII(randVar2Query);
		String codedVariableValue = characterUtil.convertStringToASCII(varValue);
		double score = 0;

		if (varValue.equals("null") || varValue.equals("empty")) return 1;

		try {
			Set<Entry<String, String>> entries = features.entrySet();
			Iterator<Entry<String, String>> iter = entries.iterator();
			while (iter.hasNext()) {
				Map.Entry<String, String> entry = (Map.Entry<String, String>) iter.next();
				feature = (String) entry.getKey();
				value = (String) entry.getValue();
				feature = characterUtil.convertStringToASCII(feature);
				value = characterUtil.convertStringToASCII(value);
				bayesNet.setObservedNode(feature, value);
			}

			score = bayesNet.getProbFromVariableValuePairs(codedRandVar2Query, codedVariableValue, new ArrayList<String>());

		} catch (Exception e) {
			System.out.println("No BayesNet for randVar2Query=[" + randVar2Query + "] varValue=[" + varValue +"]");
			e.printStackTrace();
			return 1;
		}

		return score;
	}

	public double getScoredOffer(String resources, String buildups, String give, String get, HashMap<String,ArrayList<String>> tradePreferences) {
		HashMap<String,String> evidence = new HashMap<String,String>();

		ArrayList<String> listResources = StringUtil.getArrayListFromString(resources, "|");
		for (String pair : listResources) {
			String feature = pair.substring(0, pair.indexOf("="));
			String value = pair.substring(pair.indexOf("=")+1);
			feature = "has"+feature.toUpperCase();

			if (!feature.endsWith("UNKNOWN")) {
				evidence.put(feature, value);
			}
		}

		ArrayList<String> listBuildups = StringUtil.getArrayListFromString(buildups, "|");
		for (String pair : listBuildups) {
			String feature = pair.substring(0, pair.indexOf("="));
			String value = pair.substring(pair.indexOf("=")+1);
			feature = "has"+feature.toUpperCase();

			if (!feature.endsWith("UNKNOWN")) {
				evidence.put(feature, value);
			}
		}

		String givables = "";
		String prevDAT = "Other";
		ArrayList<String> listGive = StringUtil.getArrayListFromString(give, "|");
		for (String pair : listGive) {
			String feature = pair.substring(0, pair.indexOf("="));
			String value = pair.substring(pair.indexOf("=")+1);
			String featureID = feature.toUpperCase();
			feature = "giv"+feature.toUpperCase();

			if (!feature.endsWith("UNKNOWN")) {
				prevDAT = !value.equals("0") ? "Givable" : prevDAT;

				if (!value.equals("0")) {
					givables += (givables.equals("")) ? featureID : ","+featureID;
					evidence.put("prep"+featureID, "1"); // EXPRESS A PREFERENCE FROM CURRENT GIVABLES
				}
			}
		}

		String currDAT = "Other";
		ArrayList<String> listGet = StringUtil.getArrayListFromString(get, "|");
		for (String pair : listGet) {
			String feature = pair.substring(0, pair.indexOf("="));
			String value = pair.substring(pair.indexOf("=")+1);
			feature = "rec"+feature.toUpperCase();
			if (!feature.endsWith("UNKNOWN")) {
				currDAT = !value.equals("0") ? "Receivable" : currDAT;
				evidence.put(feature, value);
			}
		}

		int prevDATint = getDialogueActTypeINT(prevDAT);
		evidence.put("prevDAT", ""+prevDATint);

		for (String item : evidence.keySet()) {
			String value = evidence.get(item);
			if (item.startsWith("has")) {
				int intValue = Integer.parseInt(value);
				if (intValue > MAX_NUMERIC_VALUE) {
					evidence.put(item, ""+MAX_NUMERIC_VALUE);
				}
			}
			if (item.startsWith("giv")) {
				int intValue = Integer.parseInt(value);
				if (intValue > 1) {
					evidence.put(item, "1");
				}
			}
		}

		if (withPreferences) {
			// set preferences of current player and opponents
			for (String player : tradePreferences.keySet()){
				ArrayList<String> preferenceList = tradePreferences.get(player);

				if (player.equals("SUPERVISED_BAYESIAN")) {
					for (String resource : mainResources) {
						if (preferenceList.contains(resource)) {
							evidence.put("prep"+resource.toUpperCase(), "1");
						}
					}

				} else {
					for (String resource : mainResources) {
						if (preferenceList.contains(resource)) {
							evidence.put("preo"+resource.toUpperCase(), "1");
						}
					}			
				}
			}

			// initialise values if they are unknown
			for (String resource : mainResources) {
				if (evidence.get("prep"+resource.toUpperCase()) == null) {
					evidence.put("prep"+resource.toUpperCase(), "0");
				}
				if (evidence.get("preo"+resource.toUpperCase()) == null) {
					evidence.put("preo"+resource.toUpperCase(), "0");
				}
			}
		}

		String classLabel = "GIVABLE";//"TRADE";//
		double score = 0;
		if (bn == null) {
			System.out.println("Bayes Net was not loaded!");
		} else if (rf == null) {
			System.out.println("Random Forest was not loaded!");
		//} else if (crf == null) {
		//	System.out.println("CRF was not loaded!");			

		} else {
			ArrayList<String> myGivables = StringUtil.getArrayListFromString(givables, ",");
			//System.out.println("myGivables="+myGivables + "evidence="+evidence);
			for (int i=0; i<myGivables.size(); i++) {
				String myGivable = myGivables.get(i);

				// best classifiers in predicting human tradings
				double tempScore = 0;
				if (withPreferences) {
					tempScore = this.getFeatureScoreFromTrainedBayesNet(bn, evidence, classLabel, myGivable);
				} else {
					tempScore = rf.getFeatureScoreFromRandomFores(evidence, classLabel, myGivable);
					//tempScore = this.getFeatureScoreFromTrainedBayesNet(bn, evidence, classLabel, myGivable);
					//tempScore = crf.getFeatureScoreFromCRF(evidence, classLabel, myGivable, withPreferences);
				}

				if (tempScore>score) {
					score = tempScore;
				}
			}
		}

		return score;
	}

	public boolean isGivableWantedByOpponents(HashMap<String,ArrayList<String>> tradePreferences, String givable) {		
		for (String player : tradePreferences.keySet()) {
			if (player.equals("SUPERVISED_BAYESIAN")) { 
				continue;
			} else {
				givable = givable.toLowerCase();
				ArrayList<String> resources = tradePreferences.get(player);
				if (resources.contains(givable)) {
					return true;
				}
			}
		}

		return false;
	}

	public HashMap<String,String> getEvidenceFromData(String resources, String buildups, String give, String get) {
		HashMap<String,String> evidence = new HashMap<String,String>();

		HashMap<String,Integer> hasResources = new HashMap<String,Integer>();
		boolean givResources = false;
		boolean recResources = false;

		ArrayList<String> listResources = StringUtil.getArrayListFromString(resources, "|");
		for (String pair : listResources) {
			String feature = pair.substring(0, pair.indexOf("="));
			String value = pair.substring(pair.indexOf("=")+1);
			int valueInt = Integer.parseInt(value);
			value = (valueInt>MAX_NUMERIC_VALUE) ? ""+MAX_NUMERIC_VALUE : value;

			feature = "has"+feature.toUpperCase();
			if (!feature.endsWith("UNKNOWN")) {
				hasResources.put(feature, new Integer(value));

				value = value.equals("0") ? "F" : "T";
				evidence.put(feature, value);
			}
		}

		ArrayList<String> listBuildups = StringUtil.getArrayListFromString(buildups, "|");
		for (String pair : listBuildups) {
			String feature = pair.substring(0, pair.indexOf("="));
			String value = pair.substring(pair.indexOf("=")+1);
			feature = "has"+feature.toUpperCase();
			int valueInt = Integer.parseInt(value);
			value = (valueInt>MAX_NUMERIC_VALUE) ? ""+MAX_NUMERIC_VALUE : value;

			if (!feature.endsWith("UNKNOWN")) {
				hasResources.put(feature, new Integer(value));
				value = value.equals("0") ? "F" : "T";
				evidence.put(feature, value);
			}
		}

		String prevDAT = "Other";
		ArrayList<String> listGive = StringUtil.getArrayListFromString(give, "|");
		for (String pair : listGive) {
			String feature = pair.substring(0, pair.indexOf("="));
			String value = pair.substring(pair.indexOf("=")+1);
			int valueInt = Integer.parseInt(value);
			value = (valueInt>MAX_NUMERIC_VALUE) ? ""+MAX_NUMERIC_VALUE : value;

			feature = "giv"+feature.toUpperCase();
			if (!feature.endsWith("UNKNOWN")) {
				value = value.equals("0") ? "F" : "T";
				prevDAT = !value.equals("0") ? "Givable" : prevDAT;
				evidence.put(feature, value);
				givResources = value.equals("T") ? true : givResources;				
			}
		}

		String currDAT = "Other";
		ArrayList<String> listGet = StringUtil.getArrayListFromString(get, "|");
		for (String pair : listGet) {
			String feature = pair.substring(0, pair.indexOf("="));
			String value = pair.substring(pair.indexOf("=")+1);
			feature = "rec"+feature.toUpperCase();
			int valueInt = Integer.parseInt(value);
			value = (valueInt>MAX_NUMERIC_VALUE) ? ""+MAX_NUMERIC_VALUE : value;

			if (!feature.endsWith("UNKNOWN")) {
				value = value.equals("0") ? "F" : "T";
				currDAT = !value.equals("0") ? "Receivable" : currDAT;
				evidence.put(feature, value);
				recResources = value.equals("T") ? true : recResources;
			}
		}

		evidence.put("prevDAT", prevDAT);
		evidence.put("currDAT", currDAT);

		return evidence;
	}

	public String getInstanceFromData(String resources, String buildups, String give, String get) {
		HashMap<String,String> evidence = getEvidenceFromData(resources, buildups, give, get);

		String instance = "";
		for (String feature : this.featureList) {
			String value = evidence.get(feature);
			//System.out.println(feature +"="+ value);
			if (value == null) {
				value = "0";//"F";
			}
			instance += (instance.equals("")) ? value : ","+value;
		}

		return instance;
	}

	public double getTopScoredTrade(String resources, String buildups, String give, String get, String featureToQuery, String valueToQuery) {
		HashMap<String,String> evidence = new HashMap<String,String>();
		HashMap<String,Integer> hasResources = new HashMap<String,Integer>();
		boolean givResources = false;
		boolean recResources = false;

		ArrayList<String> listResources = StringUtil.getArrayListFromString(resources, "|");
		for (String pair : listResources) {
			String feature = pair.substring(0, pair.indexOf("="));
			String value = pair.substring(pair.indexOf("=")+1);
			int valueInt = Integer.parseInt(value);
			value = (valueInt>MAX_NUMERIC_VALUE) ? ""+MAX_NUMERIC_VALUE : value;

			feature = "has"+feature.toUpperCase();
			if (!feature.endsWith("UNKNOWN")) {
				hasResources.put(feature, new Integer(value));
				evidence.put(feature, value);
			}
		}

		ArrayList<String> listBuildups = StringUtil.getArrayListFromString(buildups, "|");
		for (String pair : listBuildups) {
			String feature = pair.substring(0, pair.indexOf("="));
			String value = pair.substring(pair.indexOf("=")+1);
			feature = "has"+feature.toUpperCase();
			int valueInt = Integer.parseInt(value);
			value = (valueInt>MAX_NUMERIC_VALUE) ? ""+MAX_NUMERIC_VALUE : value;

			if (!feature.endsWith("UNKNOWN")) {
				hasResources.put(feature, new Integer(value));
				evidence.put(feature, value);
			}
		}

		String prevDAT = "Other";
		ArrayList<String> listGive = StringUtil.getArrayListFromString(give, "|");
		for (String pair : listGive) {
			String feature = pair.substring(0, pair.indexOf("="));
			String findResource = "has"+feature.toUpperCase();
			String value = pair.substring(pair.indexOf("=")+1);
			int valueInt = Integer.parseInt(value);
			value = (valueInt>MAX_NUMERIC_VALUE) ? ""+MAX_NUMERIC_VALUE : value;

			feature = "giv"+feature.toUpperCase();
			if (!feature.endsWith("UNKNOWN")) {
				prevDAT = !value.equals("0") ? "Givable" : prevDAT;
				evidence.put(feature, value);
				givResources = value.equals("T") ? true : givResources;				
			}
		}

		String currDAT = "Other";
		ArrayList<String> listGet = StringUtil.getArrayListFromString(get, "|");
		for (String pair : listGet) {
			String feature = pair.substring(0, pair.indexOf("="));
			String value = pair.substring(pair.indexOf("=")+1);
			feature = "rec"+feature.toUpperCase();
			int valueInt = Integer.parseInt(value);
			value = (valueInt>MAX_NUMERIC_VALUE) ? ""+MAX_NUMERIC_VALUE : value;

			if (!feature.endsWith("UNKNOWN")) {
				currDAT = !value.equals("0") ? "Receivable" : currDAT;
				evidence.put(feature, value);
				recResources = value.equals("T") ? true : recResources;
			}
		}

		double score = 0;
		if (bn != null) {
			score = this.getFeatureScoreFromTrainedBayesNet(bn, evidence, featureToQuery, valueToQuery);
		} else {
			score = rf.getFeatureScoreFromRandomFores(evidence, featureToQuery, valueToQuery);
		}

		return score;
	}

	public void resetTradePreferences() {
		tradePreferences = new HashMap<String,ArrayList<String>>();
	}

	public void putTradePreferences(String player, String preference) {
		ArrayList<String> localPreferences = tradePreferences.get(player);
		if (localPreferences == null) {
			localPreferences = new ArrayList<String>();
		}
		if (!localPreferences.contains(preference) && mainResources.contains(preference)) {					
			localPreferences.add(preference);
		}
		tradePreferences.put(player, localPreferences);
	}

	public HashMap<String,ArrayList<String>> getTradePreferences() {
		return tradePreferences;
	}

	public void setTradePreferences(HashMap<String,ArrayList<String>> tradePreferences) {
		this.tradePreferences = tradePreferences;
		//IOUtil.printHashMapArrayList(this.tradePreferences, "this.tradePreferences");
	}

	public void getInfo() {
		String sample = bn.sampleRandomVariable("curr_act");
		System.out.println("->sample=" + sample);
	}

	public static void main(String[] args) {
		new BayesianSupervisedLearner(args[0], args[1], args[2], args[3], args[4], true);
	}
}