/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file copyright (C) 2003-2004  Robert S. Thomas
 * Portions of this file copyright (C) 2009-2011 Jeremy D Monin <jeremy@nand.net>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * The maintainer of this program can be reached at jsettlers@nand.net
 **/
package soc.robot;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Vector;

import soc.game.SOCPlayer;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;


/**
 * Moved the routines that pick what to build
 * next out of SOCRobotBrain.  Didn't want
 * to call this SOCRobotPlanner because it
 * doesn't really plan, but you could think
 * of it that way.  DM = Decision Maker
 *
 * @author Robert S. Thomas
 */

public abstract class SOCRobotDM<BP extends SOCBuildPlan> {
////Fields moved from SOCBrain//////	
    /**
     * these are the two resources that we want
     * when we play a discovery dev card
     */
    public SOCResourceSet resourceChoices;

    /**
     * this is the resource we want to monopolize
     */
    public int monopolyChoice;
	
	/**
	 *  Coordinate of the first settlement from the initial set up phase
	 */
    public int firstSettlement;
	
    /**
	 *  Coordinate of the second settlement from the initial set up phase
	 */
    public int secondSettlement;
////Fields moved from SOCBrain//////

    /*
     * Variables for affecting the decision making which can be redefined depending on the robot's type
     */
    protected int maxGameLength = 300;
    protected int maxETA = 99;
    protected float etaBonusFactor = (float)0.8;
    protected float adversarialFactor = (float)1.5;
    protected float leaderAdversarialFactor = (float)3.0;
    protected float threatMultiplier = (float)1.1;
    protected float devCardMultiplier = (float)2.0;

    /*
     * Constants for defining choices
     */
    protected static final int LA_CHOICE = 0;
    protected static final int LR_CHOICE = 1;
    protected static final int CITY_CHOICE = 2;
    protected static final int SETTLEMENT_CHOICE = 3;
    protected static final DecimalFormat df1 = new DecimalFormat("###0.00");
    
    /*
     * Fields used only internally and inherited by all subclasses. 
     * These should be private, however it is impossible to inherit private fields.
     * player, playerTrackers, ourPlayerTracker, and buildingPlan are references to the same objects stored in the Brain object
     */
    protected SOCPlayer player;
    protected HashMap playerTrackers;
    protected SOCPlayerTracker ourPlayerTracker;
    protected SOCBuildPlanStack buildingPlan;
    protected Vector threatenedRoads;
    protected Vector goodRoads;
    protected SOCPossibleRoad favoriteRoad;
    protected Vector threatenedSettlements;
    protected Vector goodSettlements;
    protected SOCPossibleSettlement favoriteSettlement;    
    protected SOCPossibleCity favoriteCity;
    protected SOCPossibleCard possibleCard;
    protected int strategy;
    
    /*
     * used for describing the depth of the search (i.e. fast is 1-ply search, smart is 2-ply)
     */
    public static final int SMART_STRATEGY = 0;
    public static final int FAST_STRATEGY = 1;
    
	public SOCRobotDM() {
        //why are these set to clay or sheep ????
		resourceChoices = new SOCResourceSet();
        resourceChoices.add(2, SOCResourceConstants.CLAY);
        monopolyChoice = SOCResourceConstants.SHEEP;
	}
	
	/**
	 * Method that performs the search and generates the buildingPlan 
	 */
	public abstract void planStuff();
	
	/**
     * Select the best hex to move the robber to
     * @param robberHex
     * @return
     */
    public abstract int selectMoveRobber(int robberHex);
    
    /**
     * Should the player play a knight for the purpose of working towards largest army? 
     * @return
     */
    public abstract boolean shouldPlayKnightForLA();
    
    /**
     * Should the player play a knight for the purpose of clearing a resource/robbing or blocking a competitor/etc
     * Potentially different behavior whether we've rolled or not (eg it's unlikely you'd want to rob if doing so puts you over 7 cards)
     * @return
     */
    public abstract boolean shouldPlayKnight(boolean hasRolled);
    
    /**
     * Should we play a roadbuilding card?  Assumes that we have a plan that requires a road.  May want to double check there isn't
     * a better card to play within this, for example.
     */
    public abstract boolean shouldPlayRoadbuilding();
    
    /**
     * Should we play a YOP card?  
     * @return
     */
    public abstract boolean shouldPlayDiscovery();
    
    /**
     * figure out where to place the two settlements
     */
    public abstract void planInitialSettlements();
    
    /**
     * Determine where to place our initial road
     * @param settlementNode
     * @return int[which road, which destination]
     * TODO: Use a structure for the road descriptor
     */
    public abstract int[] planInitRoad(int settlementNode);

    /**
     * figure out where to place the second settlement
     */
    public abstract void planSecondSettlement();
    
    /**
     * choose a resource to monopolize
     * @return true if playing the card is worth it
     */
    public abstract boolean chooseMonopoly();
    
    /**
     * When playing a YOP card, set resourceChoices
     */
    public abstract void chooseFreeResources(BP buildingPlan);
    
    /**
     * 
     * @return the player number that we want to steal from
     */
    public abstract int choosePlayerToRob();
}		







