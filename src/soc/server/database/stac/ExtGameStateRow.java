package soc.server.database.stac;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Container class for easy storing and passing of extracted features from the raw data. These features require extra calculation. 
 * Unfortunately, both this class and ObsGameStateRow need to have the array fields as Integer[] instead of int[] as methods in java.sql 
 * only accept Object[].
 * 
 * @author MD
 *
 */
public class ExtGameStateRow implements Serializable{
	/**
	 * Row's unique ID
	 */
	private int ID;
	/**
	 * Game name
	 */
	private String gameName;
	/**
	 * Usually a 4x4 array for keeping track of all the successful and complete past trades for each player with each opponent. 
	 * The diagonal represents trades with the bank/port.
	 */
	private Integer[][] pastTrades;
	/**
	 * 4x4 array holding information regarding the possibility of each player to have gone for one of the build plan types 
	 * based on their previous actions. Hence the name Possible Build Plan = PBP
	 */
	private Integer[][] pastPBP;
	/**
	 * Usually a 4x4 array for keeping track of all the successful and complete future trades for each player with each opponent. 
	 * The diagonal represents trades with the bank/port.
	 */
	private Integer[][] futureTrades; 
	/**
	 * 4x4 array holding information regarding the possibility of each player to have gone for one of the build plan types 
	 * based on their future actions. Hence the name Possible Build Plan = PBP
	 */
	private Integer[][] futurePBP;
	/**
	 * Array holding the Estimated Time to Win values for each player.
	 */
	private Integer[] etw;
	/**
	 * Array holding the average etb value for each player.
	 */
	private Integer[][] avgETB;
	/**
	 * Estimated time to build a settlement for each player. Usually a 4x2 array as it keeps information considering the robber and ignoring it.
	 * position 0 is ignoring it, while position 1 is considering it.
	 */
	private Integer[][] setETB;
	/**
	 * Estimated time to build a road for each player. Usually a 4x2 array as it keeps information considering the robber and ignoring it.
	 * position 0 is ignoring it, while position 1 is considering it.
	 */
	private Integer[][] roadETB;
	/**
	 * Estimated time to build a city for each player. Usually a 4x2 array as it keeps information considering the robber and ignoring it.
	 * position 0 is ignoring it, while position 1 is considering it.
	 */
	private Integer[][] cityETB;
	/**
	 * Estimated time to buy a development card for each player. Usually a 4x2 array as it keeps information considering the robber and ignoring it.
	 * position 0 is ignoring it, while position 1 is considering it.
	 */
	private Integer[][] devETB;
	/**
	 * Array holding a binary value for each player, 1 if all their territory is connected, 0 otherwise.
	 */
	private Integer[] territoryConnected;
	/**
	 * Array holding a binary value for each player, 0 if part of their territory (1 settlement) is completely isolated from the other part, 1 otherwise.
	 */
	private Integer[] territoryNotIsolated;
	/**
	 * Array holding a decimal value for each player representing the current longest road (I forgot to add this to the OGSR).
	 */
	private Integer[] longestRoads;
	/**
	 * Array holding a decimal value for each player representing the longest road they could reach for achieving LR.
	 */
	private Integer[] longestPossibleRoads;
	/**
	 * Array holding a value between 2-3, representing how close a player is to an opponent (later we may want to change this to a larger range?).
	 */
	private Integer[] distanceToOpponents;
	/**
	 * Array holding a value between 2-3, representing how close a player is to a FREE port (later we may want to change this to a larger range?).
	 */
	private Integer[] distanceToPort;
	/**
	 * Array holding a value between 2-3, representing how close a player is to a FREE location on the map (later we may want to change this to a larger range?).
	 */
	private Integer[] distanceToNextLegalLoc;
	/**
	 * Typically a 4x5 array representing how many rss a player could get of a specific type from one roll (given that the correct number gets rolled).
	 * NOTE: It doesn't show the max number, but rather how many settlements/cities are adjacent to a specific rss (e.g. 1=sett, 2=city or 2 sett)
	 */
	private Integer[][] rssTypeAndNumber;
	
	public ExtGameStateRow(int id, String n){
		setID(id);
		setGameName(n);
	}

	public int getID() {
		return ID;
	}

	public void setID(int iD) {
		ID = iD;
	}

	public String getGameName() {
		return gameName;
	}

	public void setGameName(String name) {
		this.gameName = name;
	}

	public Integer[][] getPastTrades() {
		return pastTrades;
	}

	public void setPastTrades(Integer[][] pastTrades) {
		this.pastTrades = pastTrades;
	}

	public Integer[][] getPastPBPs() {
		return pastPBP;
	}

	public void setPastPBPs(Integer[][] pastPBP) {
		this.pastPBP = pastPBP;
	}

	public Integer[] getETWs() {
		return etw;
	}

	public void setETWs(Integer[] etw) {
		this.etw = etw;
	}

	public Integer[][] getAvgETBs() {
		return avgETB;
	}

	public void setAvgETBs(Integer[][] avgETB) {
		this.avgETB = avgETB;
	}

	public Integer[][] getFutureTrades() {
		return futureTrades;
	}

	public void setFutureTrades(Integer[][] futureTrades) {
		this.futureTrades = futureTrades;
	}

	public Integer[][] getFuturePBPs() {
		return futurePBP;
	}

	public void setFuturePBPs(Integer[][] futurePBP) {
		this.futurePBP = futurePBP;
	}
	
	public Integer[][] getSettETBs() {
		return setETB;
	}

	public void setSettETBs(Integer[][] setETB) {
		this.setETB = setETB;
	}

	public Integer[][] getRoadETBs() {
		return roadETB;
	}

	public void setRoadETBs(Integer[][] roadETB) {
		this.roadETB = roadETB;
	}

	public Integer[][] getCityETBs() {
		return cityETB;
	}

	public void setCityETBs(Integer[][] cityETB) {
		this.cityETB = cityETB;
	}

	public Integer[][] getDevETBs() {
		return devETB;
	}

	public void setDevETBs(Integer[][] devETB) {
		this.devETB = devETB;
	}
	
	public Integer[] getTerritoryConnected() {
		return territoryConnected;
	}

	public void setTerritoryConnected(Integer[] territoryConnected) {
		this.territoryConnected = territoryConnected;
	}

	public Integer[] getTerritoryIsolated() {
		return territoryNotIsolated;
	}

	public void setTerritoryIsolated(Integer[] territoryIsolated) {
		this.territoryNotIsolated = territoryIsolated;
	}

	public Integer[] getLongestRoads() {
		return longestRoads;
	}

	public void setLongestRoads(Integer[] longestPossibleRoad) {
		this.longestRoads = longestPossibleRoad;
	}

	public Integer[] getDistanceToOpponents() {
		return distanceToOpponents;
	}

	public void setDistanceToOpponents(Integer[] distanceToOpponents) {
		this.distanceToOpponents = distanceToOpponents;
	}

	public Integer[] getDistanceToPort() {
		return distanceToPort;
	}

	public void setDistanceToPort(Integer[] distanceToPort) {
		this.distanceToPort = distanceToPort;
	}

	public Integer[] getDistanceToNextLegalLoc() {
		return distanceToNextLegalLoc;
	}

	public void setDistanceToNextLegalLoc(Integer[] distanceToNextLegalLoc) {
		this.distanceToNextLegalLoc = distanceToNextLegalLoc;
	}

	public Integer[][] getRssTypeAndNumber() {
		return rssTypeAndNumber;
	}

	public void setRssTypeAndNumber(Integer[][] rssTypeAndNumber) {
		this.rssTypeAndNumber = rssTypeAndNumber;
	}
	
	public Integer[] getLongestPossibleRoads() {
		return longestPossibleRoads;
	}

	public void setLongestPossibleRoads(Integer[] longestPossibleRoads) {
		this.longestPossibleRoads = longestPossibleRoads;
	}
	
//////////////Helper methods for setting and getting specific information from arrays////////
	public void setETWs(int[] e) {
		this.etw = new Integer[e.length]; 
		for(int i = 0; i < e.length; i++)
			this.etw[i] = e[i];
	}
	
	public int getETW(int pn){
		return etw[pn];
	}
	
	public Integer[] getAvgETBs(int pn){
		return avgETB[pn];
	}
	
	public Integer[] getPastTrades(int pn){
		return pastTrades[pn];
	}

	public Integer[] getFutureTrades(int pn){
		return futureTrades[pn];
	}
	
	public Integer[] getPastBPPs(int pn){
		return pastPBP[pn];
	}

	public Integer[] getFutureBPPs(int pn){
		return futurePBP[pn];
	}
	
	public Integer[] getSettETBs(int pn){
		return setETB[pn];
	}
	
	public Integer[] getRoadETBs(int pn){
		return roadETB[pn];
	}
	
	public Integer[] getCityETBs(int pn){
		return cityETB[pn];
	}
	
	public Integer[] getDevETBs(int pn){
		return devETB[pn];
	}
	
	public int getDistanceToPort(int pn){
		return distanceToPort[pn];
	}
	
	public int getDistanceToOpp(int pn){
		return distanceToOpponents[pn];
	}
	
	public int getDistanceToNextLegalLoc(int pn){
		return distanceToNextLegalLoc[pn];
	}
	
	public int getLongestRoads(int pn){
		return longestRoads[pn];
	}
	
	public int getTerritoryConnected(int pn){
		return territoryConnected[pn];
	}
	
	public int getTerritoryIsolated(int pn){
		return territoryNotIsolated[pn];
	}
	
	public Integer[] getRssTypeAndNumber(int pn){
		return rssTypeAndNumber[pn];
	}
	
	public int getLongestPossibleRoad(int pn){
		return longestPossibleRoads[pn];
	}
	
////////////////////////////////////////////////////////////////////////////////////////////
	/**
	 * For debugging purposes
	 */
	public String toString(){
		return "Extracted feature row: ID=" + ID + "|gameName" + gameName + "|pastTrades=" + arrToString(pastTrades) + "|pastPBP=" +
				arrToString(pastPBP) + "|futureTrades=" + arrToString(futureTrades) + "|futurePBP=" + arrToString(futurePBP) + "|etw=" + 
				Arrays.toString(etw) + "|avgETB=" + arrToString(avgETB) + "|SettlementETB=" + arrToString(setETB)+ "|roadETB=" +
				arrToString(roadETB) + "|CityETB=" + arrToString(cityETB) + "|DevCardETB=" + arrToString(devETB) + "|TerritoryConnected=" + 
				Arrays.toString(territoryConnected) + "|TerritoryIsolated=" + Arrays.toString(territoryNotIsolated) + "|DistancesToPorts=" + 
				Arrays.toString(distanceToPort) + "|DistancesToOpp=" + Arrays.toString(distanceToOpponents) + "|DistancesToNextLegalLocs=" + 
				Arrays.toString(distanceToNextLegalLoc) + "|RssTypesNNumbers=" + arrToString(rssTypeAndNumber) + "|LongestRoads=" + 
				Arrays.toString(longestRoads) + "|LongestPossibleRoads=" + Arrays.toString(longestPossibleRoads);		
	}
	
	/**
	 * A small utility to convert a bidimensional array into a readable string form.
	 * @param a the Integer[][] object to convert to string
	 * @return
	 */
	public String arrToString(Integer[][] a){
		String string = "{";
		for(int i = 0; i < a.length;i ++){
			string = string + Arrays.toString(a[i]);
			if(i < a.length - 1)
				string = string + ",";
		}
		return string + "}";
	}
}
