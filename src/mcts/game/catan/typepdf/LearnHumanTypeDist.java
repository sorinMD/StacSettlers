package mcts.game.catan.typepdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import soc.server.database.stac.DBGameParser;

public class LearnHumanTypeDist {
	/**
	 * Main method for computing the pdf from the database.
	 * @param args
	 */
	public static void main(String[] args) {
	    
		DBGameParser parser = new DBGameParser();
		HumanActionTypePdf pdf = new HumanActionTypePdf();
		//loop over the human games in the db
		for(int gameID = 1; gameID < 61; gameID++){
			try {
				Map<ArrayList<Integer>,ArrayList<Integer>> stats = parser.selectActionTypesCounts(gameID);
				for(Entry<ArrayList<Integer>, ArrayList<Integer>> entry : stats.entrySet()) {
					ArrayList<Integer> condition = entry.getKey();
					Collections.sort(condition);
					ArrayList<Integer> chosenTypes = entry.getValue();
					if(pdf.conditionCount.containsKey(condition)) {
						Integer count = pdf.conditionCount.get(condition) + chosenTypes.size();
						pdf.conditionCount.put(condition, count);
						Map<Integer,Integer> typeCounts = pdf.chosenTypeCountConditioned.get(condition);
						for(Integer t : chosenTypes) {
							if(typeCounts.containsKey(t))
								typeCounts.put(t, typeCounts.get(t) + 1);
							else
								typeCounts.put(t, 1);
						}
						pdf.chosenTypeCountConditioned.put(condition, typeCounts);
					}else {
						Integer count = chosenTypes.size();
						pdf.conditionCount.put(condition, count);
						Map<Integer,Integer> typeCounts = new HashMap<>();
						for(Integer t : chosenTypes) {
							if(typeCounts.containsKey(t))
								typeCounts.put(t, typeCounts.get(t) + 1);
							else
								typeCounts.put(t, 1);
						}
						pdf.chosenTypeCountConditioned.put(condition, typeCounts);
					}
					for(Integer t : condition) {
						if(pdf.typeCount.containsKey(t))
							pdf.typeCount.put(t, pdf.typeCount.get(t) + chosenTypes.size());
						else
							pdf.typeCount.put(t, chosenTypes.size());
					}
					for(Integer t : chosenTypes) {
						if(pdf.chosenTypeCount.containsKey(t))
							pdf.chosenTypeCount.put(t, pdf.chosenTypeCount.get(t) + 1);
						else
							pdf.chosenTypeCount.put(t, chosenTypes.size());
					}
					
				}
			} catch (Exception e) {
				System.out.println("exception in game: " + gameID);
				e.printStackTrace();
			}

		}
		//print contents for debugging
		System.out.println("Learned pdf: ");
		System.out.println("Chosen type count");
		for(Entry<Integer, Integer> e : pdf.chosenTypeCount.entrySet()) {
			System.out.println(e.getKey() + "  " + e.getValue());
		}
		System.out.println("Type count");
		for(Entry<Integer, Integer> e : pdf.typeCount.entrySet()) {
			System.out.println(e.getKey() + "  " + e.getValue());
		}
		
		System.out.println("Chosen type count conditioned");
		for(Entry<ArrayList<Integer>, Map<Integer, Integer>> e : pdf.chosenTypeCountConditioned.entrySet()) {
			System.out.println("Condition " + e.getKey().toString() + ": ");
			for(Entry<Integer, Integer> e1 : e.getValue().entrySet()) {
				System.out.println(e1.getKey() + "  " + e1.getValue());
			}
		}
		System.out.println("Condition count");
		for(Entry<ArrayList<Integer>, Integer> e : pdf.conditionCount.entrySet()) {
			System.out.println(e.getKey().toString() + "  " + e.getValue());
		}
		
		//save to current working dir
		pdf.copyToFile();
		//test load
		HumanActionTypePdf pdf2 = HumanActionTypePdf.readFromFile();
		
		//print contents to compare
		System.out.println("Load object:");
		System.out.println("Chosen type count");
		for(Entry<Integer, Integer> e : pdf2.chosenTypeCount.entrySet()) {
			System.out.println(e.getKey() + "  " + e.getValue());
		}
		System.out.println("Type count");
		for(Entry<Integer, Integer> e : pdf2.typeCount.entrySet()) {
			System.out.println(e.getKey() + "  " + e.getValue());
		}
		
		System.out.println("Chosen type count conditioned");
		for(Entry<ArrayList<Integer>, Map<Integer, Integer>> e : pdf2.chosenTypeCountConditioned.entrySet()) {
			System.out.println("Condition " + e.getKey().toString() + ": ");
			for(Entry<Integer, Integer> e1 : e.getValue().entrySet()) {
				System.out.println(e1.getKey() + "  " + e1.getValue());
			}
		}
		System.out.println("Condition count");
		for(Entry<ArrayList<Integer>, Integer> e : pdf2.conditionCount.entrySet()) {
			System.out.println(e.getKey().toString() + "  " + e.getValue());
		}
		
	}
}
