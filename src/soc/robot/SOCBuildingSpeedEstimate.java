/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
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
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.robot;

import soc.disableDebug.D;

import soc.game.SOCBoard;
import soc.game.SOCGame;
import soc.game.SOCPlayerNumbers;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;

import soc.util.CutoffExceededException;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;


/**
 * This class calculates approximately how
 * long it would take a player to build something.
 * 
 * This has been refactored to be abstract and allow implementations 
 * Note that for the sake of minimizing refactoring, functions 
 *  are referred to as "Fast" in all cases.  TBD whether it's worth 
 *  renaming.
 */
public abstract class SOCBuildingSpeedEstimate
{
    public static final int ROAD = 0;
    public static final int SETTLEMENT = 1;
    public static final int CITY = 2;
    public static final int CARD = 3;
    public static final int MIN = 0;
    public static final int MAXPLUSONE = 4;
    public static final int DEFAULT_ROLL_LIMIT = 40;
    protected static boolean recalc;
    int[] estimatesFromNothing;
    int[] estimatesFromNow;
    int[] rollsPerResource;
    SOCResourceSet[] resourcesForRoll;

    /**
     * this is a constructor
     *
     * @param numbers  the numbers that the player's pieces are touching
     */
    public SOCBuildingSpeedEstimate(SOCPlayerNumbers numbers)
    {
        estimatesFromNothing = new int[MAXPLUSONE];
        estimatesFromNow = new int[MAXPLUSONE];
        rollsPerResource = new int[SOCResourceConstants.WOOD + 1];
        recalculateRollsPerResource(numbers);
        resourcesForRoll = new SOCResourceSet[13];
        recalculateResourcesForRoll(numbers);
    }

    /**
     * this is a constructor
     */
    public SOCBuildingSpeedEstimate()
    {
        estimatesFromNothing = new int[MAXPLUSONE];
        estimatesFromNow = new int[MAXPLUSONE];
        rollsPerResource = new int[SOCResourceConstants.WOOD + 1];
        resourcesForRoll = new SOCResourceSet[13];
    }

    /**
     * @return the estimates from nothing
     *
     * @param ports  the port flags for the player
     */
    public int[] getEstimatesFromNothingFast(boolean[] ports)    
    {
        if (recalc)
        {
            estimatesFromNothing[ROAD] = DEFAULT_ROLL_LIMIT;
            estimatesFromNothing[SETTLEMENT] = DEFAULT_ROLL_LIMIT;
            estimatesFromNothing[CITY] = DEFAULT_ROLL_LIMIT;
            estimatesFromNothing[CARD] = DEFAULT_ROLL_LIMIT;

            try
            {
                estimatesFromNothing[ROAD] = calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.ROAD_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
                estimatesFromNothing[SETTLEMENT] = calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.SETTLEMENT_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
                estimatesFromNothing[CITY] = calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.CITY_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
                estimatesFromNothing[CARD] = calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.CARD_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
            }
            catch (CutoffExceededException e)
            {
                ;
            }
        }

        return estimatesFromNothing;
    }

    /**
     * @return the estimates from nothing
     *
     * @param ports  the port flags for the player
     */
    public int[] getEstimatesFromNothingFast(boolean[] ports, int limit)
    {
        if (recalc)
        {
            estimatesFromNothing[ROAD] = limit;
            estimatesFromNothing[SETTLEMENT] = limit;
            estimatesFromNothing[CITY] = limit;
            estimatesFromNothing[CARD] = limit;

            try
            {
                estimatesFromNothing[ROAD] = calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.ROAD_SET, limit, ports).getRolls();
                estimatesFromNothing[SETTLEMENT] = calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.SETTLEMENT_SET, limit, ports).getRolls();
                estimatesFromNothing[CITY] = calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.CITY_SET, limit, ports).getRolls();
                estimatesFromNothing[CARD] = calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.CARD_SET, limit, ports).getRolls();
            }
            catch (CutoffExceededException e)
            {
                ;
            }
        }

        return estimatesFromNothing;
    }

    /**
     * @return the estimates from now
     *
     * @param resources  the player's current resources
     * @param ports      the player's port flags
     */
    public int[] getEstimatesFromNowFast(SOCResourceSet resources, boolean[] ports)
    {
        estimatesFromNow[ROAD] = DEFAULT_ROLL_LIMIT;
        estimatesFromNow[SETTLEMENT] = DEFAULT_ROLL_LIMIT;
        estimatesFromNow[CITY] = DEFAULT_ROLL_LIMIT;
        estimatesFromNow[CARD] = DEFAULT_ROLL_LIMIT;

        try
        {
            estimatesFromNow[ROAD] = calculateRollsFast(resources, SOCGame.ROAD_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
            estimatesFromNow[SETTLEMENT] = calculateRollsFast(resources, SOCGame.SETTLEMENT_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
            estimatesFromNow[CITY] = calculateRollsFast(resources, SOCGame.CITY_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
            estimatesFromNow[CARD] = calculateRollsFast(resources, SOCGame.CARD_SET, DEFAULT_ROLL_LIMIT, ports).getRolls();
        }
        catch (CutoffExceededException e)
        {
            ;
        }

        return estimatesFromNow;
    }

    /**
     * recalculate both rollsPerResource and resourcesPerRoll
     */
    public void recalculateEstimates(SOCPlayerNumbers numbers)
    {
        recalculateRollsPerResource(numbers);
        recalculateResourcesForRoll(numbers);
    }

    /**
     * recalculate both rollsPerResource and resourcesPerRoll
     * using the robber information
     */
    public void recalculateEstimates(SOCPlayerNumbers numbers, int robberHex)
    {
        recalculateRollsPerResource(numbers, robberHex);
        recalculateResourcesForRoll(numbers, robberHex);
    }

    /**
     * calculate the estimates
     *
     * @param numbers  the numbers that the player is touching
     */
    private void recalculateRollsPerResource(SOCPlayerNumbers numbers)
    {
        //D.ebugPrintln("@@@@@@@@ recalculateRollsPerResource");
        //D.ebugPrintln("@@@@@@@@ numbers = "+numbers);
        recalc = true;

        /**
         * figure out how many resources we get per roll
         */
        for (int resource = SOCResourceConstants.CLAY;
                resource <= SOCResourceConstants.WOOD; resource++)
        {
            //D.ebugPrintln("resource: "+resource);
            float totalProbability = 0.0f;

            Enumeration numbersEnum = numbers.getNumbersForResource(resource).elements();

            while (numbersEnum.hasMoreElements())
            {
                Integer number = (Integer) numbersEnum.nextElement();
                totalProbability += SOCNumberProbabilities.FLOAT_VALUES[number.intValue()];
            }

            //D.ebugPrintln("totalProbability: "+totalProbability);
            if (totalProbability != 0.0f)
            {
                rollsPerResource[resource] = Math.round(1.0f / totalProbability);
            }
            else
            {
                rollsPerResource[resource] = 55555;
            }

            //D.ebugPrintln("rollsPerResource: "+rollsPerResource[resource]);
        }
    }

    /**
     * calculate the estimates assuming that the robber is working
     *
     * @param numbers    the numbers that the player is touching
     * @param robberHex  where the robber is
     */
    private void recalculateRollsPerResource(SOCPlayerNumbers numbers, int robberHex)
    {
        D.ebugPrintlnINFO("@@@@@@@@ recalculateRollsPerResource");
        D.ebugPrintlnINFO("@@@@@@@@ numbers = " + numbers);
        D.ebugPrintlnINFO("@@@@@@@@ robberHex = " + Integer.toHexString(robberHex));
        recalc = true;

        /**
         * figure out how many resources we get per roll
         */
        for (int resource = SOCResourceConstants.CLAY;
                resource <= SOCResourceConstants.WOOD; resource++)
        {
            D.ebugPrintlnINFO("resource: " + resource);

            float totalProbability = 0.0f;

            Enumeration numbersEnum = numbers.getNumbersForResource(resource, robberHex).elements();

            while (numbersEnum.hasMoreElements())
            {
                Integer number = (Integer) numbersEnum.nextElement();
                totalProbability += SOCNumberProbabilities.FLOAT_VALUES[number.intValue()];
            }

            D.ebugPrintlnINFO("totalProbability: " + totalProbability);

            if (totalProbability != 0.0f)
            {
                rollsPerResource[resource] = Math.round(1.0f / totalProbability);
            }
            else
            {
                rollsPerResource[resource] = 55555;
            }

            D.ebugPrintlnINFO("rollsPerResource: " + rollsPerResource[resource]);
        }
    }

    /**
     * calculate what resources this player will get on each
     * die roll
     *
     * @param numbers  the numbers that the player is touching
     */
    private void recalculateResourcesForRoll(SOCPlayerNumbers numbers)
    {
        //D.ebugPrintln("@@@@@@@@ recalculateResourcesForRoll");
        //D.ebugPrintln("@@@@@@@@ numbers = "+numbers);
        recalc = true;

        for (int diceResult = 2; diceResult <= 12; diceResult++)
        {
            Vector resources = numbers.getResourcesForNumber(diceResult);

            if (resources != null)
            {
                SOCResourceSet resourceSet;

                if (resourcesForRoll[diceResult] == null)
                {
                    resourceSet = new SOCResourceSet();
                    resourcesForRoll[diceResult] = resourceSet;
                }
                else
                {
                    resourceSet = resourcesForRoll[diceResult];
                    resourceSet.clear();
                }

                Enumeration resourcesEnum = resources.elements();

                while (resourcesEnum.hasMoreElements())
                {
                    Integer resourceInt = (Integer) resourcesEnum.nextElement();
                    resourceSet.add(1, resourceInt.intValue());
                }

                //D.ebugPrintln("### resources for "+diceResult+" = "+resourceSet);
            }
        }
    }

    /**
     * calculate what resources this player will get on each
     * die roll taking the robber into account
     *
     * @param numbers  the numbers that the player is touching
     */
    private void recalculateResourcesForRoll(SOCPlayerNumbers numbers, int robberHex)
    {
        //D.ebugPrintln("@@@@@@@@ recalculateResourcesForRoll");
        //D.ebugPrintln("@@@@@@@@ numbers = "+numbers);
        //D.ebugPrintln("@@@@@@@@ robberHex = "+Integer.toHexString(robberHex));
        recalc = true;

        for (int diceResult = 2; diceResult <= 12; diceResult++)
        {
            Vector resources = numbers.getResourcesForNumber(diceResult, robberHex);

            if (resources != null)
            {
                SOCResourceSet resourceSet;

                if (resourcesForRoll[diceResult] == null)
                {
                    resourceSet = new SOCResourceSet();
                    resourcesForRoll[diceResult] = resourceSet;
                }
                else
                {
                    resourceSet = resourcesForRoll[diceResult];
                    resourceSet.clear();
                }

                Enumeration resourcesEnum = resources.elements();

                while (resourcesEnum.hasMoreElements())
                {
                    Integer resourceInt = (Integer) resourcesEnum.nextElement();
                    resourceSet.add(1, resourceInt.intValue());
                }

                //D.ebugPrintln("### resources for "+diceResult+" = "+resourceSet);
            }
        }
    }

    /**
     * @return the rolls per resource results
     */
    public int[] getRollsPerResource()
    {
        return rollsPerResource;
    }

    /**
     * this figures out how many rolls it would take this
     * player to get the target set of resources given
     * a starting set
     *
     * @param startingResources   the starting resources
     * @param targetResources     the target resources
     * @param cutoff              throw an exception if the total speed is greater than this
     * @param ports               a list of port flags
     *
     * @return the number of rolls
     */
    public abstract SOCResSetBuildTimePair calculateRollsFast(SOCResourceSet startingResources, SOCResourceSet targetResources, int cutoff, boolean[] ports) throws CutoffExceededException;

}
