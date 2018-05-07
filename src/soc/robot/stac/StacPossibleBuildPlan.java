/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package soc.robot.stac;

import java.io.Serializable;

import soc.robot.SOCBuildPlanStack;

/**
 * Class to store a possible build plan for a robot.
 * It's mainly used for storing BPs in the declarative memory for versions of the agent 
 * that consider multiple build plans, not just the Best Build Plan.
 * @author Markus Guhe
 */
public class StacPossibleBuildPlan implements Comparable<StacPossibleBuildPlan>, Serializable {

    // Types of build plans
    public static final int ROAD = 0;
    public static final int SETTLEMENT = 1;
    public static final int CITY = 2;
    public static final int CARD = 4;
    public static final int LONGEST_ROAD = 8;
    public static final int LARGEST_ARMY = 16;

    // Information stored by this class
    int type;
    SOCBuildPlanStack buildPlan;
    int buildingSpeedEstimate;
    int speedupEstimate;
    int deltaWinGameETA;

    protected StacPossibleBuildPlan(int bpType, SOCBuildPlanStack buildPlan, int buildingSpeedEst, int speedupEst, int deltaWinGameETA) {
        this.type = bpType;
        this.buildPlan = buildPlan;
        this.buildingSpeedEstimate = buildingSpeedEst;
        this.speedupEstimate = speedupEst;
        this.deltaWinGameETA = deltaWinGameETA;
    }

    int getType() {
        return type;
    }

    SOCBuildPlanStack getBuildPlan () {
        return buildPlan;
    }

    /** The expected time needed until this piece can be built (ETB). */
    int getBuildingSpeedEstimate () {
        return buildingSpeedEstimate;
    }

    /** The expected speedup as a result of building this piece (ES). */
    int getSpeedupEstimate() {
        return speedupEstimate;
    }

    /** The expected reduction in time to win the game (ETW; this is the Estimated Progress, EP). */
    int getDeltaWinGameETA() {
        return deltaWinGameETA;
    }

    public String toString() {
        String typeString = "Unknown Type";
        switch (this.type) {
            case ROAD:
                typeString = "Road";
                break;
            case SETTLEMENT:
                typeString = "Settlement";
                break;
            case CITY:
                typeString = "City";
                break;
            case CARD:
                typeString = "Development Card";
                break;
            case LONGEST_ROAD:
                typeString = "Longest Road";
                break;
            case LARGEST_ARMY:
                typeString = "Largest Army";
                break;
        };
        return "\n" + typeString + ": " + "building speed (ETB): " + buildingSpeedEstimate + 
                "; speedup (ES): " + speedupEstimate + 
                "; delta win game ETA (∆ETW): " + deltaWinGameETA + 
                "\n\t" + buildPlan;
    }
    
    @Override
    public int compareTo(StacPossibleBuildPlan p) {
        if (buildingSpeedEstimate != p.buildingSpeedEstimate) { 
            return buildingSpeedEstimate - p.buildingSpeedEstimate;
        } else {
            return p.speedupEstimate - speedupEstimate;
        }
    }
}
