package soc.robot.stac.flatmcts;

import java.lang.reflect.Array;
import java.util.Arrays;

import soc.disableDebug.D;
/**
 * A simple class for gathering stats during simulations and returning a reward based on the MCTS type. 
 * It contains various reward functions (NOTE: some are tested some not yet).
 * @author MD
 */
public class FlatMctsRewards {
	
    /**
     * epsilon value
     */
	static double epsilon = 1e-6;
	/**
	 * vp for each player before simulation started.
	 */
	private int[] initialVPs;
	/**
	 * vp when simulation has finished.
	 */
	private int[] endVPs; 
	/**
	 * Estimated Time to Win before placing the robber.
	 */
	private int[] initialETWs;
	/**
	 * Estimated Time to Win after simulation.
	 */
	private int[] endETWs;
	/**
	 * Average estimated Time to build right after placing the robber and before running simulations. 
	 */
	private int[] immediateETBs;
	/**
	 * Average estimated Time to build without taking into account the robber. 
	 */
	private int[] initialETBs;
	/**
	 * how many resources have we prevented from dropping from that hex for each player. 
	 * (when applying MCTS for the robber action)
	 */
	private int[] rssBlocked;
	/**
	 * maximum number of resources that can be blocked per turn for each player. (Not sure it will be useful) If yes, used as a normalising value in case we need to keep the rewards between 0-1. 
	 * (when applying MCTS for the robber action)
	 */
	private int[] maxRssBlocked; 
	/**
	 * max number of resources that can be blocked per turn for all players. (Not sure I will use this...possibly good for normalising in case we keep rewards between 0-1)
	 * (when applying MCTs for the robber action)
	 */
	private int maxTotalRssBlocked; 
	
	
	//list of flags used in the logic in RANDOMBRAIN for collecting very specific information
	/**
	 * if true continue counting rss, else stop.
	 */
	public boolean countRss = true;
	/**
	 * if true calculate ETW immediately after placing the robber, else don't. 
	 */
    public boolean immediateRewardMoveRobber = true;
	/**
	 * if true calculate ETW immediately after stealing a rss, else don't. 
	 */
    public boolean immediateRewardSteal = true;
	
	public FlatMctsRewards() {
		//one slot for each player
		setInitialVPs(new int[4]);
		setEndVPs(new int[4]);
		setInitialETWs(new int[4]);
		setEndETWs(new int[4]);
		setImmediateETBs(new int[4]);
		setInitialETBs(new int[4]);
		setRssBlocked(new int[4]);
		setMaxRssBlocked(new int[4]);
		//initialise all to 0
		Arrays.fill(initialVPs, 0);
		Arrays.fill(endVPs, 0);
		Arrays.fill(initialETWs, 0);
		Arrays.fill(endETWs, 0);
		Arrays.fill(immediateETBs, 0);
		Arrays.fill(initialETBs, 0);
		Arrays.fill(rssBlocked,0);
		Arrays.fill(maxRssBlocked, 0);
		setMaxTotalRssBlocked(0);
		//and set the flags
		countRss = true;
		immediateRewardMoveRobber = true;
		immediateRewardSteal = true;
	}
	/**
	 * Set everything to 0;
	 */
	public void clearStats(){
		Arrays.fill(initialVPs, 0);
		Arrays.fill(endVPs, 0);
		Arrays.fill(initialETWs, 0);
		Arrays.fill(endETWs, 0);
		Arrays.fill(immediateETBs, 0);
		Arrays.fill(initialETBs, 0);
		Arrays.fill(rssBlocked, 0);
		Arrays.fill(maxRssBlocked, 0);
		setMaxTotalRssBlocked(0);
		//also reset flags
		countRss = true;
		immediateRewardMoveRobber = true;
		immediateRewardSteal = true;
	}
	/**
	 * print out in the console the values for each player for quick debugging.
	 */
	public void display(){
		System.out.println("Player 0: " + "|InitialVP=" + initialVPs[0] + "|EndVP=" + endVPs[0] + "|InitialETW=" + initialETWs[0] + "|EndETW=" + endETWs[0] + "|ImmediateETB=" + immediateETBs[0] + "|InitialETB=" + initialETBs[0] + "|Rssblocked=" + rssBlocked[0] + "|MaxRssBlocked=" + maxRssBlocked[0] + "|MaxTotalRssBlocked=" + getMaxTotalRssBlocked() + "|");
		System.out.println("Player 1: " + "|InitialVP=" + initialVPs[1] + "|EndVP=" + endVPs[1] + "|InitialETW=" + initialETWs[1] + "|EndETW=" + endETWs[1] + "|ImmediateETB=" + immediateETBs[1] + "|InitialETB=" + initialETBs[1] + "|Rssblocked=" + rssBlocked[1] + "|MaxRssBlocked=" + maxRssBlocked[1] + "|MaxTotalRssBlocked=" + getMaxTotalRssBlocked() + "|");
		System.out.println("Player 2: " + "|InitialVP=" + initialVPs[2] + "|EndVP=" + endVPs[2] + "|InitialETW=" + initialETWs[2] + "|EndETW=" + endETWs[2] + "|ImmediateETB=" + immediateETBs[2] + "|InitialETB=" + initialETBs[2] + "|Rssblocked=" + rssBlocked[2] + "|MaxRssBlocked=" + maxRssBlocked[2] + "|MaxTotalRssBlocked=" + getMaxTotalRssBlocked() + "|");
		System.out.println("Player 3: " + "|InitialVP=" + initialVPs[3] + "|EndVP=" + endVPs[3] + "|InitialETW=" + initialETWs[3] + "|EndETW=" + endETWs[3] + "|ImmediateETB=" + immediateETBs[3] + "|InitialETB=" + initialETBs[3] + "|Rssblocked=" + rssBlocked[3] + "|MaxRssBlocked=" + maxRssBlocked[3] + "|MaxTotalRssBlocked=" + getMaxTotalRssBlocked() + "|");
	}
	
	//getters and setters for each value
	public int[] getInitialVPs() {
		return initialVPs;
	}
	public void setInitialVPs(int[] initialVPs) {
		this.initialVPs = initialVPs;
	}
	public int[] getEndVPs() {
		return endVPs;
	}
	public void setEndVPs(int[] endVPs) {
		this.endVPs = endVPs;
	}
	public int[] getInitialETWs() {
		return initialETWs;
	}
	public void setInitialETWs(int[] initialETWs) {
		this.initialETWs = initialETWs;
	}
	public int[] getEndETWs() {
		return endETWs;
	}
	public void setEndETWs(int[] endETWs) {
		this.endETWs = endETWs;
	}
	public int[] getImmediateETBs() {
		return immediateETBs;
	}
	public void setImmediateETBs(int[] immediateETBs) {
		this.immediateETBs = immediateETBs;
	}
	public int[] getInitialETBs() {
		return initialETBs;
	}
	public void setInitialETBs(int[] initialETBs) {
		this.initialETBs = initialETBs;
	}
	public int[] getRssBlocked() {
		return rssBlocked;
	}
	public void setRssBlocked(int[] rssBlocked) {
		this.rssBlocked = rssBlocked;
	}
	public int[] getMaxRssBlocked() {
		return maxRssBlocked;
	}
	public void setMaxRssBlocked(int[] maxRss) {
		this.maxRssBlocked = maxRss;
	}
	public int getMaxTotalRssBlocked() {
		return maxTotalRssBlocked;
	}
	public void setMaxTotalRssBlocked(int idealRssBlocked) {
		this.maxTotalRssBlocked = idealRssBlocked;
	}
	
	/**
	 * The method to be called from MCTS algorithm for returning a reward at the end of a simulation.
	 * @param rewardType
	 * @param ourPlayerNumber
	 * @param simulationDepth
	 * @return a value based on the reward type 
	 */
	public double getReward(String rewardType, int ourPlayerNumber, int simulationDepth){
		if(rewardType.equals("OBSERVABLE")){
        	return observable(ourPlayerNumber);
        }else if(rewardType.equals("OBSERVABLE_RANKING")){
        	return observableRanking(ourPlayerNumber);
        }else if(rewardType.equals("ETW")){
        	return etw(ourPlayerNumber);
        }else if(rewardType.equals("ETW_RANKING")){
        	return etwRanking(ourPlayerNumber);
        }else if(rewardType.equals("OBSERVABLE_ETW")){
        	return observableAndETW(ourPlayerNumber);
        }else if(rewardType.equals("OBSERVABLE_ETW_RANKING")){
        	return observableAndETWRanking(ourPlayerNumber);
        }else if(rewardType.equals("SCORE_PERSONAL_IMPROVEMENT")){
        	return scorePersonalImprovement(ourPlayerNumber);
        }else if(rewardType.equals("SCORE_LEADERSHIP_IMPROVEMENT")){
        	return scoreLeadershipImprovement(ourPlayerNumber); 
        }else if(rewardType.equals("SCORE_AVERAGE_IMPROVEMENT")){
        	return scoreAverageImprovement(ourPlayerNumber);
        }else if(rewardType.equals("ETW_PERSONAL_IMPROVEMENT")){
        	return eTWPersonalImprovement(ourPlayerNumber);
        }else if(rewardType.equals("ETW_LEADERSHIP_IMPROVEMENT")){
        	return eTWLeadershipImprovement(ourPlayerNumber);
        }else if(rewardType.equals("ETW_AVERAGE_IMPROVEMENT")){
        	return eTWAverageImprovement(ourPlayerNumber);
        }else if(rewardType.equals("COMBINED_PERSONAL_IMPROVEMENT")){
        	return allPersonalImprovementAndJSettlers(ourPlayerNumber, simulationDepth);
        }else if(rewardType.equals("COMBINED_LEADERSHIP_IMPROVEMENT")){
        	return allLeadershipImprovementAndJSettlers(ourPlayerNumber, simulationDepth);
        }else if(rewardType.equals("COMBINED_AVERAGE_IMPROVEMENT")){
        	return allAverageImprovementAndJSettlers(ourPlayerNumber, simulationDepth);
        }else if(rewardType.equals("COMBINED_SCORE_RSS")){
        	return allScoreImprovementAndJSettlers(ourPlayerNumber, simulationDepth);
        }else if(rewardType.equals("COMBINED_ETW_RSS")){
        	return allETWImprovementAndJSettlers(ourPlayerNumber, simulationDepth);
        }else if(rewardType.equals("PERSONAL_IMPROVEMENT_NORSS")){
        	return allETWImprovementAndJSettlers(ourPlayerNumber, simulationDepth);
        }else if(rewardType.equals("BLOCKED_RSS_NORMALISED")){
        	return blockedRssRewardNormalised(ourPlayerNumber, simulationDepth);
        }else if(rewardType.equals("JSETTLERS_WITH_BLOCKED_RSS")){
            return jSettlersRssReward(ourPlayerNumber);
        }else if(rewardType.equals("BLOCKED_RSS_UNNORMALISED")){
        	return blockedRssReward(ourPlayerNumber);
        }else if(rewardType.equals("JSETTLERS_RSS_VP_UNNORMALISED")){
        	return jSettlersRssVPReward(ourPlayerNumber);
        }else{
        	D.ebugERROR("undefined reward function, returning based on observable reward");
        	return observable(ourPlayerNumber);
        }
	}
	
	//reward functions from here onwards
	/**
	 * Aimed at estimating how much the player has improved based on its VP's
	 * @param playerNumber
	 * @return a value between [0,1]
	 */
	private double scorePersonalImprovement(int playerNumber){
		double reward = (getEndVPs()[playerNumber]-getInitialVPs()[playerNumber])/3;
    	if(getEndVPs()[playerNumber]>=10)//we want to encourage winning quicker if possible
    		return 1;
    	else if(reward < 0) //we don't care how bad we did as there is no way to lose more than 2 vp and that shouldn't affect the robber placement decision
    		return 0;
    	else if(reward >= 1)
    		return 1;
    	else return reward;
	}
	/**
	 * Aimed at estimating how much the player has improved with respect to the leading player or the follow up based on their VP's
	 * @param playerNumber
	 * @return a value between [0,1]
	 */
	private double scoreLeadershipImprovement(int playerNumber){
    	double initialOurVP = getInitialVPs()[playerNumber];
    	double initialOpponentVP = 0;
    	for(int i=0; i < 4; i++)
    		if(i != playerNumber)
    			if(initialOpponentVP<=getInitialVPs()[i])
    				initialOpponentVP=getInitialVPs()[i];
    	
    	double endOurVP = getEndVPs()[playerNumber];
    	double endOpponentVP = 0;
    	for(int i=0; i < 4; i++)
    		if(i != playerNumber)
    			if(endOpponentVP<=getEndVPs()[i])
    				endOpponentVP=getEndVPs()[i];
    	
    	double reward = (endOurVP - endOpponentVP) - (initialOurVP - initialOpponentVP);
    	if(reward <= 0) //it doesn't tell us how bad we did, just that its bad
    		return 0;
    	else
    		return reward/3; //but it tells us how good we did
	}
	
	/**
	 * Aimed at estimating how much the player has improved with respect to the average VP's
	 * @param playerNumber
	 * @return a value between [0,1]
	 */
	private double scoreAverageImprovement(int playerNumber){

    	double initialAverage = 0; 
    	for(int i = 0; i < 4; i++)
    		initialAverage += getInitialVPs()[i];
    	initialAverage/=4;
    	
    	double endAverage = 0; 
       	for(int i = 0; i < 4; i++)
    		endAverage += getEndVPs()[i];
    	endAverage/=4;
    	
    	double reward = (getEndVPs()[playerNumber]- endAverage) - (getInitialVPs()[playerNumber]-initialAverage);
    	if(reward<=0)//again no quantification of the bad scenario
    		return 0;
    	else
    		return reward/3;
	}
	
	/**
	 * Aimed at estimating how much the player has improved based on its ETW
	 * @param playerNumber
	 * @return a value between [0,1]
	 */
	private double eTWPersonalImprovement(int playerNumber){

    	double reward = getInitialETWs()[playerNumber]-getEndETWs()[playerNumber];
    	if(reward <= 0)//there should always be a way of getting closer to finishing
    		return 0;
    	else if(reward/30 >=1)//30 is a good improvement for only 4 turns, so we shouldn't go over too often
    		return 1;
    	else
    		return reward/30;
	}
	
	/**
	 * Aimed at estimating how much the player has improved with respect to the leading player or the follow up based on their ETW's
	 * @param playerNumber
	 * @return a value between [0,1]
	 */
	private double eTWLeadershipImprovement(int playerNumber){
		double initialOurETW = getInitialETWs()[playerNumber];
    	double initialOpponentETW = Double.MAX_VALUE;//smaller is better
    	for(int i=0; i < 4; i++)
    		if(i != playerNumber)
    			if(initialOpponentETW>=getInitialETWs()[i])
    				initialOpponentETW=getInitialETWs()[i];
    	
    	double endOurETW = getEndETWs()[playerNumber];
    	double endOpponentETW = Double.MAX_VALUE;//smaller is better
    	for(int i=0; i < 4; i++)
    		if(i != playerNumber)
    			if(endOpponentETW>=getEndETWs()[i])
    				endOpponentETW=getEndETWs()[i];
    	
    	double reward = ((initialOurETW - initialOpponentETW) - (endOurETW - endOpponentETW)); 
    	if((initialOurETW - initialOpponentETW) < (endOurETW - endOpponentETW))
    		return 0; //meaning we have not improved as much as they have; //again no quantification of the bad scenario
    	else if (reward >=1)//shouldn't happen but just in case
    		return 1;
    	else return reward;//else return a value based on how much we have improved
	}
	
	/**
	 * Aimed at estimating how much the player has improved with respect to the average ETW
	 * @param playerNumber
	 * @return a value between [0,1]
	 */
	private double eTWAverageImprovement(int playerNumber){
    	double initialOurETW = getInitialETWs()[playerNumber];
    	double initialAverage = 0; 
    	for(int i = 0; i < 4; i++)
    		initialAverage += getInitialETWs()[i];
    	initialAverage/=4;
    	
    	double endOurETW = getEndETWs()[playerNumber];
    	double endAverage = 0; 
       	for(int i = 0; i < 4; i++)
    		endAverage += getEndETWs()[i];
    	endAverage/=4;
    	
    	double reward = ((initialOurETW - initialAverage) - (endOurETW - endAverage) )/10;
    	if((initialOurETW - initialAverage) < (endOurETW - endAverage))
    		return 0; //meaning we have not improved as much as they have; not quantified
    	else if (reward>=1)
    		return 1;
    	else return reward;//else return a value based on how much have we improved
	}

	/**
	 * ADDITION of all Personal improvements plus the resources blocked
	 * @param playerNumber
	 * @param simulationDepth
	 * @return
	 */
	private double allPersonalImprovementAndJSettlers(int playerNumber, int simulationDepth){
		double reward = scorePersonalImprovement(playerNumber) + eTWPersonalImprovement(playerNumber) + blockedRssRewardNormalised(playerNumber, simulationDepth);
		if(reward >= 1)
			return 1;
		else
			return reward;
	}
	/**
	 * ADDITION of all average improvements plus the resources blocked
	 * @param playerNumber
	 * @param simulationDepth
	 * @return
	 */
	private double allAverageImprovementAndJSettlers(int playerNumber, int simulationDepth){
		double reward = scoreAverageImprovement(playerNumber) + eTWAverageImprovement(playerNumber) + blockedRssRewardNormalised(playerNumber, simulationDepth);
		if(reward >= 1)
			return 1;
		else
			return reward;
	}
	/**
	 * ADDITION of all leadership improvements plus the resources blocked
	 * @param playerNumber
	 * @param simulationDepth
	 * @return
	 */
	private double allLeadershipImprovementAndJSettlers(int playerNumber, int simulationDepth){
		double reward = scoreLeadershipImprovement(playerNumber) + eTWLeadershipImprovement(playerNumber) + blockedRssRewardNormalised(playerNumber, simulationDepth);
		if(reward >= 1)
			return 1;
		else
			return reward;
	}
	/**
	 * ADDITION of all score improvements plus the resources blocked; no ETW.
	 * @param playerNumber
	 * @param simulationDepth
	 * @return
	 */
	private double allScoreImprovementAndJSettlers(int playerNumber, int simulationDepth){
		double reward = scorePersonalImprovement(playerNumber) + scoreLeadershipImprovement(playerNumber) + blockedRssRewardNormalised(playerNumber, simulationDepth);
		if(reward >= 1)
			return 1;
		else
			return reward;
	}
	/**
	 * ADDITION of all ETWs improvements plus the resources blocked; no score.
	 * @param playerNumber
	 * @param simulationDepth
	 * @return
	 */
	private double allETWImprovementAndJSettlers(int playerNumber, int simulationDepth){
		double reward = eTWPersonalImprovement(playerNumber) + eTWLeadershipImprovement(playerNumber) + blockedRssRewardNormalised(playerNumber, simulationDepth);
		if(reward >= 1)
			return 1;
		else
			return reward;
	}
	/**
	 * ADDITION of all personal improvements, without resources blocked.
	 * @param playerNumber
	 * @param simulationDepth
	 * @return
	 */
	private double allPersonalImprovement(int playerNumber){
		double reward = scorePersonalImprovement(playerNumber) + eTWPersonalImprovement(playerNumber);
		if(reward >= 1)
			return 1;
		else
			return reward;
	}
	
	/**
	 * Deciding on the leading player based on their VP and only afterwards on their ETW
	 * Reward based just on number of blocked resources for each player, but normalised to be in the [0,1] range.
	 * Not aimed at being in the [0,1] range
	 * @param ourPlayerNumber
	 * @return
	 */
	private double blockedRssReward(int ourPlayerNumber){
		//get the leading player aside from us based on ETW
		int leaderPN = -1;
		int score = Integer.MIN_VALUE;
		for(int i = 0; i < 4; i++){
			if(score < initialVPs[i] && i!= ourPlayerNumber){
				score = initialVPs[i];
				leaderPN = i;
			}
		}
		//check if there are multiple players with the same score (this could happen most often in the initial or late stage of the game)
		boolean recalculateLeaderWithETW = false;
		for(int i = 0; i < 4; i++){
			if(score == initialVPs[i] && i != leaderPN){
				recalculateLeaderWithETW = true;
			}
		}
		//if yes, attack the one with the lowest ETW
		if(recalculateLeaderWithETW){
			leaderPN = -1;
			int etw = Integer.MAX_VALUE;
			for(int i = 0; i < 4; i++){
				if(etw > initialETWs[i] && i != ourPlayerNumber){
					etw = initialETWs[i];
					leaderPN = i;
				}
			}
		}
		
		//calculate a new reward function subtracting our rss lost and weighing the leader's rss lost more
		double reward = 0;
		for(int i = 0; i < 4; i++){
			if(i == leaderPN){
				reward = reward + 2 * rssBlocked[leaderPN]; //encourage affecting the leading player
			}else if(i == ourPlayerNumber){
				reward = reward - 2 * rssBlocked[ourPlayerNumber]; //discourage affecting us
			}else
				reward =+ rssBlocked[i]; //give some points for affecting other players
		}
		if(reward > 0)
			return reward;
		else
			return 0;
	}
	
	/**
	 * Deciding on the leading player based on their VP and only afterwards on their ETW
	 * Reward based just on number of blocked resources for each player, but normalised to be in the [0,1] range.
	 * @param ourPlayerNumber
	 * @param depth the number of turns
	 * @return
	 */
	private double blockedRssRewardNormalised(int ourPlayerNumber, int depth){
		//get the leading player aside from us based on ETW
		int leaderPN = -1;
		int score = Integer.MIN_VALUE;
		for(int i = 0; i < 4; i++){
			if(score < initialVPs[i] && i!= ourPlayerNumber){
				score = initialVPs[i];
				leaderPN = i;
			}
		}
		//check if there are multiple players with the same score (this could happen most often in the initial or late stage of the game)
		boolean recalculateLeaderWithETW = false;
		for(int i = 0; i < 4; i++){
			if(score == initialVPs[i] && i != leaderPN){
				recalculateLeaderWithETW = true;
			}
		}
		//if yes, attack the one with the lowest ETW
		if(recalculateLeaderWithETW){
			leaderPN = -1;
			int etw = Integer.MAX_VALUE;
			for(int i = 0; i < 4; i++){
				if(etw > initialETWs[i] && i != ourPlayerNumber){
					etw = initialETWs[i];
					leaderPN = i;
				}
			}
		}
		
		//calculate a new reward function subtracting our rss lost and weighing the leader's rss lost more
		double reward = 0;
		for(int i = 0; i < 4; i++){
			if(i == leaderPN){
				reward = reward + 4 * (rssBlocked[leaderPN]/(maxRssBlocked[leaderPN]*depth + epsilon)); //encourage affecting the leading player
			}else if(i == ourPlayerNumber){
				reward = reward - 4 * (rssBlocked[ourPlayerNumber]/(maxRssBlocked[ourPlayerNumber]*depth + epsilon)); //discourage affecting us
			}else
				reward =+ rssBlocked[i]/(maxRssBlocked[i]*depth + epsilon); //give some points for affecting the other 2 players
		}
		
		if(reward > 0)
			return reward/10; //4 from each us and leading player and 2 from the other two players
		else
			return 0;
	}
	
	
	/**
	 * Deciding on the leading player based on their ETW.
	 * Reward function based on number of resources blocked for each player and how much it affected the avg ETB
	 * Not aimed at being in the [0,1] range, but above 0;
	 * @param ourPlayerNumber
	 * @return
	 */
	private double jSettlersRssReward(int ourPlayerNumber){
//		//get the leading player aside from us based on ETW
		int leaderPN = -1;
		int etw = Integer.MAX_VALUE;
		for(int i = 0; i < 4; i++){
			if(etw > initialETWs[i] && i != ourPlayerNumber){
				etw = initialETWs[i];
				leaderPN = i;
			}
		}
		//calculate the affected ETBs
		int[] speedDifferences = new int[4];
		for(int i=0; i<4;i++){
			speedDifferences[i] = getImmediateETBs()[i]-getInitialETBs()[i];
		}
		
		//calculate a new reward function subtracting our rss lost and weighing the leader's rss lost more; same with the etb's
		double reward = 0;
		for(int i = 0; i < 4; i++){
			if(i == leaderPN){
				reward = reward + 2 * rssBlocked[i]; //encourage affecting the leading player
				reward = reward + speedDifferences[i]/2; //etbs are larger numbers on average so we don't want them to affect the decision by too much
			}else if(i == ourPlayerNumber){
				reward = reward - 2 * rssBlocked[i]; //discourage affecting us
				reward = reward - speedDifferences[i]/2;
			}else{
				reward =+ rssBlocked[i]; //give some points for affecting other players
				reward = reward + speedDifferences[i]/4;
			}
		}
		if(reward > 0)
			return reward;
		else
			return 0;
	}
	
	/**
	 * Only based on ETB and deciding on the leading player based on their ETW
	 * Not aimed at being in the [0,1] range, but above 0;
	 * @param ourPlayerNumber
	 * @return
	 */
	private double exactlyJSettlersReward(int ourPlayerNumber){
		//get the leading player aside from us based on ETW
		int leaderPN = -1;
		int etw = Integer.MAX_VALUE;
		for(int i = 0; i < 4; i++){
			if(etw > initialETWs[i] && i != ourPlayerNumber){
				etw = initialETWs[i];
				leaderPN = i;
			}
		}
		//calculate the affected ETBs
		int[] speedDifferences = new int[4];
		for(int i=0; i<4;i++){
			speedDifferences[i] = getImmediateETBs()[i]-getInitialETBs()[i];
		}
		
		//calculate a new reward function subtracting our rss lost and weighing the leader's rss lost more; same with the etb's
		double reward = 0;
		for(int i = 0; i < 4; i++){
			if(i == leaderPN){
				reward = reward + speedDifferences[i]/2; //etbs are larger numbers on average so we don't want them to affect the decision by too much
			}else if(i == ourPlayerNumber){
				reward = reward - speedDifferences[i]/2;
			}else{
				reward = reward + speedDifferences[i]/4;
			}
		}
		if(reward > 0)
			return reward;
		else
			return 0;
	}
	
	/**
	 * Deciding on the leading player based on their ETW.
	 * Number of resources blocked, affected ETB and also punishing if we lost the game and rewarding if we won it.
	 * Not aimed at being in the [0,1] range, but above 0;
	 * @param ourPlayerNumber
	 * @return
	 */
	private double jSettlersRssVPReward(int ourPlayerNumber){
		//get the leading player aside from us based on ETW
		int leaderPN = -1;
		int etw = Integer.MAX_VALUE;
		for(int i = 0; i < 4; i++){
			if(etw > initialETWs[i] && i != ourPlayerNumber){
				etw = initialETWs[i];
				leaderPN = i;
			}
		}
		//calculate the affected ETBs
		int[] speedDifferences = new int[4];
		for(int i=0; i<4;i++){
			speedDifferences[i] = getImmediateETBs()[i]-getInitialETBs()[i];
		}
		
		//calculate a new reward function subtracting our rss lost and weighing the leader's rss lost more; same with the etb's and punish/encourage based on end vps
		double reward = 0;
		for(int i = 0; i < 4; i++){
			if(i == leaderPN){
				reward = reward + 0.2 * rssBlocked[i]; //encourage affecting the leading player
				reward = reward + 0.1 * speedDifferences[i]; //ETBs are larger numbers on average so we don't want them to affect the decision by too much
				if(endVPs[i]>9)
					reward = reward - 5; //punish if at the end of the game this player has won
			}else if(i == ourPlayerNumber){
				reward = reward - 0.2 * rssBlocked[i]; //discourage affecting us
				reward = reward - 0.1 * speedDifferences[i];
				if(endVPs[i]>9)
					reward = reward + 5; //encourage winning the game as ultimate goal
			}else{
				reward =+ 0.05 * rssBlocked[i]; //give some points for affecting other players
				reward = reward + 0.025 *speedDifferences[i];
				if(endVPs[i]>9)
					reward = reward - 5; //punish if at the end of the game this player has won
			}
		}
		if(reward > 0)
			return reward;
		else
			return 0;
	}
	
	/**
	 * check victory points for playerNumber and if > 9 return 1; else if anyone else has VP>9 return 0; 
	 * else find out whether we have the most VP's and if yes return 1; else if anyone else has the most VP's return 0;
	 * else if we are tied for the first place check if we have the lowest ETW, if yes return 1 else 0.
	 * 
	 * @param playerNumber which player are we interested in
	 * @return 0 if has lost, else 1
	 */
	private double observableAndETW(int playerNumber){
		//to achieve this, order the list by vp + (1 - etw/1000)
		double[] value = new double[4];
		for(int i = 0; i<4; i++){
			value[i] = (double)endVPs[i] + (1.0 - (double)endETWs[i]/1000);
		}
		//sort the array
		sort(value);
		double ourValue = (double)endVPs[playerNumber] + (1.0 - (double)endETWs[playerNumber]/1000);
		if(value[3] == ourValue)
			return 1; //we have won or are tied for the first place
		else
			return 0; 
	}
	
	/**
	 * Same as above, just return a value depending on the ranking
	 * @param playerNumber which player are we interested in
	 * @return 0 if we are on the third or forth place; 1 if we won and 0.5 otherwise
	 */
	private double observableAndETWRanking(int playerNumber){
		//to achieve this, order the list by vp + (1 - etw/1000)
		double[] value = new double[4];
		for(int i = 0; i<4; i++){
			value[i] = (double)endVPs[i] + (1.0 - (double)endETWs[i]/1000);
		}
		
		//sort the array
		sort(value);
		double ourValue = (double)endVPs[playerNumber] + (1.0 - (double)endETWs[playerNumber]/1000);
		if(value[3] == ourValue)
			return 1; //we have won or are tied for the first place
		else if(value[2] == ourValue)
			return 0.5;	//we came second or are tied for the second place
		else
			return 0; 
	}

	/*from here onwards some simple rewards based just on the observable info(VP's) or ETW calculated just by ranking each player*/
	/**
	 * Only based on the observable information, i.e. VPs. 
	 * @param playerNumber
	 * @return 0 means we lost and 1 means we won.
	 */
	private double observable(int playerNumber){
		//to achieve this, order the list just by vp
		double[] value = new double[4];
		for(int i = 0; i<4; i++){
			value[i] = endVPs[i];
		}
		//sort the array
		sort(value);
		double ourValue = endVPs[playerNumber];
		if(value[3] == ourValue)
			return 1; //we have won or are tied for the first place
		else
			return 0; 
	}
	
	/**
	 * Same as above but also return 0.5 if we finished second
	 * @param playerNumber
	 * @return 0 means we lost, 1 means we won, 0.5 means we are second
	 */
	private double observableRanking(int playerNumber){
		//to achieve this, order the list just by vp
		double[] value = new double[4];
		for(int i = 0; i<4; i++){
			value[i] = endVPs[i];
		}
		//sort the array
		sort(value);
		double ourValue = endVPs[playerNumber];
		if(value[3] == ourValue)
			return 1; //we have won or are tied for the first place
		else if(value[2] == ourValue)
			return 0.5;	//we came second or are tied for the second place
		else
			return 0; 
	}
	/**
	 * Only based on the ETW (lowest is better).
	 * @param playerNumber
	 * @return 0 means we lost and 1 means we won.
	 */
	private double etw(int playerNumber){
		//to achieve this, order the list by (1 - etw/1000)
		double[] value = new double[4];
		for(int i = 0; i < 4; i++){
			value[i] = (1.0 - (double)endETWs[i]/1000);
		}
		//sort the array
		sort(value);
		double ourValue = (1.0 - (double)endETWs[playerNumber]/1000);
		if(value[3] == ourValue)
			return 1; //we have won or are tied for the first place
		else
			return 0; 
	}
	/**
	 * Same as above, but return a 0.5 if we came second
	 * @param playerNumber
	 * @return 0 means we lost, 1 means we won, 0.5 means we are second
	 */
	private double etwRanking(int playerNumber){
		//to achieve this, order the list by vp + (1 - etw/1000)
		double[] value = new double[4];
		for(int i = 0; i<4; i++){
			value[i] = (1.0 - (double)endETWs[i]/1000);
		}
		
		//sort the array
		sort(value);
		double ourValue = (1.0 - (double)endETWs[playerNumber]/1000);
		if(value[3] == ourValue)
			return 1; //we have won or are tied for the first place
		else if(value[2] == ourValue)
			return 0.5;	//we came second or are tied for the second place
		else
			return 0; 
	}
	
	/**
	 * A simple bubble sort algorithm which orders from lowest to highest
	 * @param data array to be sorted
	 * @return the ordered array
	 */
	private double[] sort(double[] data){
		int lenD = data.length;
		double tmp = 0;
		for(int i = 0;i<lenD;i++){
			for(int j = (lenD-1);j>=(i+1);j--){
				if(data[j]<data[j-1]){
					tmp = data[j];
					data[j]=data[j-1];
					data[j-1]=tmp;
				}
			}
		}
		return data;
	}
}
