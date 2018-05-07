package soc.robot.stac.negotiationlearning;

import simpleDS.util.IOUtil;
import simpleDS.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import soc.game.StacTradeOffer;

public class RewardFunction {
	private HashMap<String,Float> rewards = new HashMap<String,Float>();
	private HashMap<String,Integer> rankList = new HashMap<String,Integer>();
	private HashMap<String,String> tradeMap = new HashMap<String,String>();
	private HashMap<String,String> tradeIDs = new HashMap<String,String>();
	private ArrayList<String> tradeList = new ArrayList<String>();
	public HashMap<String,StacTradeOffer> offerList = new HashMap<String,StacTradeOffer>();
	public HashMap<String,String> rewardList = new HashMap<String,String>();
	private ArrayList<String> lastAllowedActions = new ArrayList<String>();
	private final int MAX_GIVABLES = 2;
	private final int MAX_RECEIVABLES = 2;

	public RewardFunction() {
		initialisation();
	}

	public void reset() {
		rankList = new HashMap<String,Integer>();
		tradeList = new ArrayList<String>();
		offerList = new HashMap<String,StacTradeOffer>();
		rewardList = new HashMap<String,String>();
	}

	public void initialisation() {
		ArrayList<String> resources = new ArrayList<String>();
		ArrayList<String> resources_base = new ArrayList<String>();
		resources.add("clay");
		resources.add("ore");
		resources.add("sheep");
		resources.add("wheat");
		resources.add("wood");
		resources_base.addAll(resources);

		ArrayList<String> resourcesX2 = new ArrayList<String>();
		for (String givable : resources) {
			for (String receivable : resources) {
				String trade = givable+""+receivable;
				String trade2 = receivable+""+givable;
				if (resourcesX2.contains(trade2)) {
					continue;
				} 
				resourcesX2.add(trade);
			}
		}
		resources.addAll(resourcesX2);
		//Collections.sort(resources);
		//resources.add("null");

		for (String givable : resources) {
			for (String receivable : resources_base) {
				if (givable.equals(receivable)) {
					continue;
				} else if (givable.equals(receivable+receivable) ||
						receivable.equals(givable+givable)) {
					continue;
				}
				//if (givable.length()>5) {
				//	continue;
				//}
				if (validTrade(givable, receivable, resources)) {
					String trade = givable+"4"+receivable;
					//if (validTrade(givable, "", resources_base)) {
					//	String trade = givable;
					if (!rewards.containsKey(trade)) {
						rewards.put(trade, new Float(0));
						tradeIDs.put(trade, ""+tradeIDs.size());
						tradeList.add(trade);
					}
					trade = receivable+"4"+givable;
					if (!rewards.containsKey(trade)) {
						rewards.put(trade, new Float(0));
						tradeIDs.put(trade, ""+tradeIDs.size());
						tradeList.add(trade);
					}
				}
			}
		}
		//IOUtil.printHashMap(rewards, "rewards");
		//IOUtil.printArrayList(tradeList, "tradeList");
		/*IOUtil.printHashMap(tradeIDs, "tradeIDs");
		//IOUtil.printArrayList(resourcesX2, "resourcesX2");
		System.out.println("|rewards|="+rewards.size());
		System.out.println("|tradeIDs|="+tradeIDs.size());
		System.out.println("|tradeList|="+tradeList.size());*/
	}

	public boolean validTrade(String givable, String receivable, ArrayList<String> resources) {
		boolean result = true;

		for (String resource : resources) {
			if (givable.indexOf(resource)>=0 && receivable.indexOf(resource)>=0) {
				return false;
			}
		}

		return result;
	}

	public void setRewards(String giv, String rec, int listSize, StacTradeOffer offer, String weight) {
		//ArrayList<String> givList = new ArrayList<String>();
		//ArrayList<String> recList = new ArrayList<String>();

		int rank = rankList.size();
		String rawTradeID = "trade"+tradeMap.size();
		tradeMap.put(rawTradeID, "GIV:"+giv+"|GET:"+rec);

		String trade = getTradeFromLists(giv, rec);
		if (trade != null && offer != null) {
			//if (rewards.containsKey(trade)) {
			float reward = 1 - (float) (rank+1)/listSize;
			reward = (listSize == 1) ? 1 : reward;
			//if (!offerList.containsKey(trade)) {
			rewards.put(trade, reward);
			rankList.put(trade, new Integer(rank));
			String tradeID = tradeIDs.get(trade);
			//System.out.println("tradeID="+tradeID + " trade="+trade + " offer="+offer);
			offerList.put(tradeID, offer);

			//double penalty = givesMoreThanReceives(giv, rec) ? -0.1 : 0;
			double bonus = 1-(double) Double.parseDouble(weight)/100;
			rewardList.put(tradeID, ""+bonus);
			//System.out.println("Known trade="+trade + " for giv="+giv+" rec="+rec + " bonus="+bonus + " listSize="+listSize);// + " offer="+offer.toString());
			//} else {
			//System.out.println("UNKNOWN Trade for giv="+giv+" rec="+rec + " listSize="+listSize);// + " offer="+offer.toString());
		}

		//IOUtil.printHashMap(rewards, "rewards*");
	}

	/*public void setRewards(String giv, String rec, int rank, float score) {
		ArrayList<String> givList = new ArrayList<String>();
		ArrayList<String> recList = new ArrayList<String>();

		//String trade = getTradeFromLists(givList, recList);
		String trade = getTradeFromLists(giv, rec);
		if (rewards.containsKey(trade) && trade != null) {
			rewards.put(trade, score);
			rankList.put(trade, new Integer(rank));

			//} else {
			//	System.out.println("WARNING: trade="+trade+" NOT FOUND!");
		}
	}*/

	public String getTradeFromLists(String giv, String rec) {

		ArrayList<String> givList = new  ArrayList<String>();
		ArrayList<String> recList  = new  ArrayList<String>();

		int givCount = 0;
		for (String givRes : StringUtil.getArrayListFromString(giv, "|")) {
			if (givRes.startsWith("unknown") || givRes.endsWith("=0")) {
				continue;
			} else {
				givList.add(givRes);
				givCount++;
			}
		}

		int recCount = 0;
		for (String recRes : StringUtil.getArrayListFromString(rec, "|")) {
			if (recRes.startsWith("unknown") || recRes.endsWith("=0")) {
				continue;
			} else {
				recList.add(recRes);
				recCount++;
			}
		}

		// filters valid trades -- change condition if needed!!!
		boolean valid = (givCount <= MAX_GIVABLES && recCount <= MAX_RECEIVABLES) ? true : false;
		if (valid == false) return null;

		String givValue = "";
		if (givList.size()==0) {
			givValue = "null";
			System.out.println("WARNING:>givList="+givList);
		} else if (givList.size()==1) {
			String givPair = givList.get(0);
			String givKey = givPair.substring(0, givPair.indexOf("="));
			givValue = (givPair.endsWith("=2")) ? givKey+givKey : givKey;
		} else if (givList.size()==2) {
			String givPair1 = givList.get(0);
			String givPair2 = givList.get(1);
			String givKey1 = givPair1.substring(0, givPair1.indexOf("="));
			String givKey2 = givPair2.substring(0, givPair2.indexOf("="));
			givValue = givKey1+givKey2;
		}

		String recValue = "";
		if (recList.size()==0) {
			recValue = "null";
			System.out.println("WARNING:>recList="+recList);
		} else if (recList.size()==1) {
			String recPair = recList.get(0);
			String recKey = recPair.substring(0, recPair.indexOf("="));
			recValue = (recPair.endsWith("=2")) ? recKey+recKey : recKey;			
		} else if (recList.size()==2) {
			String recPair1 = recList.get(0);
			String recPair2 = recList.get(1);
			String recKey1 = recPair1.substring(0, recPair1.indexOf("="));
			String recKey2 = recPair2.substring(0, recPair2.indexOf("="));
			recValue = recKey1+recKey2;
		}

		/*System.out.println("giv="+giv + " givList="+givList);
		System.out.println("rec="+rec + " recList="+recList);
		System.out.println("TRADE="+givValue+"4"+recValue+"\n");*/

		//return recValue+"4"+givValue;
		return givValue+"4"+recValue;
		//return givValue;
	}

	public int getNumResources(String resources) {
		int count = 0;
		for (String res : StringUtil.getArrayListFromString(resources, "|")) {
			if (res.startsWith("unknown") || res.endsWith("=0")) {
				continue;
			} else {
				count++;
			}
		}

		return count;
	}

	public boolean givesMoreThanReceives(String giv, String rec) {
		int givCount = getNumResources(giv);//0;
		/*for (String givRes : StringUtil.getArrayListFromString(giv, "|")) {
			if (givRes.startsWith("unknown") || givRes.endsWith("=0")) {
				continue;
			} else {
				givCount++;
			}
		}*/

		int recCount = getNumResources(rec);//0;
		/*for (String recRes : StringUtil.getArrayListFromString(rec, "|")) {
			if (recRes.startsWith("unknown") || recRes.endsWith("=0")) {
				continue;
			} else {
				recCount++;
			}
		}*/

		if (givCount > recCount) return true;
		else return false;
	}

	/*public String getRewards() {
		String vector = "";
		for (String trade : tradeList) {
			String reward = rewards.get(trade).toString();
			vector += vector.equals("") ? reward : ","+reward;
		}
		//System.out.println("===>vector="+vector);
		return vector;
	}*/

	public double getBestReward() {
		//String bestTrade = "";
		double bestReward = 0;
		for (String tradeID : rewardList.keySet()) {
			double reward = Double.parseDouble(rewardList.get(tradeID));
			//System.out.println("getBestReward():tradeID="+tradeID + " reward="+reward);
			if (bestReward == 0 || reward > bestReward) {
				//bestTrade = trade;
				bestReward = reward;
			}
		}
		return bestReward;
	}

	public double getAvgReward() {
		//String bestTrade = "";
		double cumReward = 0;
		int counter = 0;
		for (String tradeID : rewardList.keySet()) {
			cumReward += Double.parseDouble(rewardList.get(tradeID));
			counter++;
		}
		return (double)cumReward/counter;
	}

	public int getNumTrades() {
		return tradeMap.size();
	}

	public String getTrades() {
		String vector = "";
		for (String tradeID : tradeMap.keySet()) {
			String givget = tradeMap.get(tradeID);
			givget = givget.replace("|", "/");
			givget = givget.replace("=", "@");
			vector += vector.equals("") ? givget : ","+givget;
		}
		//System.out.println("===>vector="+vector);
		return vector;
	}

	public String getAllowedActions(String defaultTrade) {
		boolean isDefaultTradeIncluded = false;
		double bestReward = getBestReward();
		double avgReward = getAvgReward();
		//System.out.println("getAllowedActions():bestReward="+bestReward);
		lastAllowedActions = new ArrayList<String>();
		String set = "";
		//for (String trade : rankList.keySet()) {
		//	String index = tradeIDs.get(trade);
		//	double reward = Double.parseDouble(rewardList.get(index));
		int counter = 0;
		for (String tradeID : rewardList.keySet()) {
			double reward = Double.parseDouble(rewardList.get(tradeID));
			//System.out.println(">getAllowedActions():tradeID="+tradeID + " reward="+reward + " bestReward="+bestReward);
			//if (index == null || reward != bestReward) continue;
			if (reward > avgReward) {
				set += (set.equals("")) ? tradeID : ","+tradeID;
				lastAllowedActions.add(tradeID);
				if (tradeID.equals(defaultTrade)) isDefaultTradeIncluded = true;
				//counter++;
				//if (counter>=N) break;
			//	System.out.println("IN_: tradeID="+tradeID + " reward="+reward + " bestReward="+bestReward + " avgReward=" + avgReward + " counter="+counter);
			//} else {
			//	System.out.println("OUT: tradeID="+tradeID + " reward="+reward + " bestReward="+bestReward + " avgReward=" + avgReward + " counter="+counter);
			}
		}
		//System.out.println();

		if (isDefaultTradeIncluded == false && defaultTrade != null) {
			set += (set.equals("")) ? defaultTrade : ","+defaultTrade;
			lastAllowedActions.add(defaultTrade);
			//System.out.println("*defaultTrade="+defaultTrade);
		} 
		//System.out.println("isDefaultTradeIncluded="+isDefaultTradeIncluded);

		//System.out.println("getAllowedActions():set="+set + " bestReward="+bestReward);
		return set;
	}

	/*public String getRewardsOfAllowedActions(double bonus) {
		String set = "";
		//for (String trade : rankList.keySet()) {
		//	String tradeID = tradeIDs.get(trade);
		//	String reward = rewardList.get(tradeID);
		for (String tradeID : rewardList.keySet()) {
			double reward = Double.parseDouble(rewardList.get(tradeID));
			//reward += bonus;
			set += (set.equals("")) ? ""+reward : ","+reward;
		}
		return set;
	}*/

	public boolean hasAction(String tradeID) {
		//System.out.println("hasAction(): tradeID="+tradeID + " lastAllowedActions="+lastAllowedActions);
		if (tradeID != null && lastAllowedActions.contains(tradeID)) {
			return true;
		} else {
			return false;
		}
	}

	public String getVectorOfRewards(String actions, double bonus, String defaultTrade) {
		//double bReward = getBestReward();
		//double weight = 0.1;
		String vector = "";
		for (String action : StringUtil.getArrayListFromString(actions, ",")) {
			//StacTradeOffer t = offerList.get(action);
			//boolean penalty = givesMoreThanReceives(t.give.toString(), t.get.toString());
			//double rewardWithPenalty = (penalty) ? reward-weight : reward;
			//vector += (vector.equals("")) ? rewardWithPenalty : ","+rewardWithPenalty;
			//double reward = Double.parseDouble(rewardList.get(action));// + bonus;
			//reward = (reward>=defaultReward) ? 1 : reward;
			//double cReward = (bonus>0) ? (double) (reward+bonus)/2 : reward;
			//double relevance = ((defaultTrade != null && action.equals(defaultTrade)) || reward >= bReward) ? 1 : 0;
			double tReward = (defaultTrade != null && action.equals(defaultTrade)) ? bonus : 0;
			//System.out.println("a="+action + " defaultTrade="+defaultTrade + " r="+reward + " bonus="+bonus + " cReward="+cReward + " weight="+weight + " tReward="+tReward);
			vector += (vector.equals("")) ? tReward : ","+tReward;
		}
		//System.out.println();

		return vector;
	}

	/*public String getVectorOfRewards(String actions, String defaultTrade) {
		String vector = "";
		for (String action : StringUtil.getArrayListFromString(actions, ",")) {
			String reward = rewardList.get(action);
			reward = (!action.equals(defaultTrade)) ? "0" : reward;
			vector += (vector.equals("")) ? reward : ","+reward;
		}

		return vector;
	}*/

	public String getTradeFromGivablesReceivables(String giv, String rec) {
		String trade = getTradeFromLists(giv, rec);
		String tradeID = tradeIDs.get(trade);
		return tradeID;
	}

	public static void main(String[] args) {
		new RewardFunction();
	}
}
