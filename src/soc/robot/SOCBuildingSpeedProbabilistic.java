package soc.robot;

import java.util.Enumeration;
import java.util.Hashtable;

import soc.disableDebug.D;
import soc.game.SOCBoard;
import soc.game.SOCPlayerNumbers;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.util.CutoffExceededException;

/**
 * Probabilistic "Accurate" implementation provided with JSettlers 
 *  but never used.
 * 
 * Simulates every possible combination of dice rolls on a per-turn basis,
 *  computing probabilities for each possible resource combination.  Returns
 *  the estimate when the probability of having the target resources reaches
 *  50%.
 *  
 * @author KHO
 *
 */
public class SOCBuildingSpeedProbabilistic extends SOCBuildingSpeedEstimate {

    public SOCBuildingSpeedProbabilistic(SOCPlayerNumbers numbers) {
        super(numbers);
    }

    public SOCBuildingSpeedProbabilistic() {
        super();
    }

    @Override
    public SOCResSetBuildTimePair calculateRollsFast(SOCResourceSet startingResources, SOCResourceSet targetResources, int cutoff, boolean[] ports) throws CutoffExceededException
    {
        D.ebugPrintlnINFO("calculateRollsAccurate");
        D.ebugPrintlnINFO("  start: " + startingResources);
        D.ebugPrintlnINFO("  target: " + targetResources);

        SOCResourceSet ourResources = startingResources.copy();
        int rolls = 0;
        Hashtable[] resourcesOnRoll = new Hashtable[2];
        resourcesOnRoll[0] = new Hashtable();
        resourcesOnRoll[1] = new Hashtable();

        int lastRoll = 0;
        int thisRoll = 1;

        resourcesOnRoll[lastRoll].put(ourResources, new Float(1.0));

        boolean targetReached = ourResources.contains(targetResources);
        SOCResourceSet targetReachedResources = null;
        float targetReachedProb = (float) 0.0;

        while (!targetReached)
        {
            if (D.ebugOn)
            {
                D.ebugPrintlnINFO("roll: " + rolls);
                D.ebugPrintlnINFO("resourcesOnRoll[lastRoll]:");

                Enumeration roltEnum = resourcesOnRoll[lastRoll].keys();

                while (roltEnum.hasMoreElements())
                {
                    SOCResourceSet rs = (SOCResourceSet) roltEnum.nextElement();
                    Float prob = (Float) resourcesOnRoll[lastRoll].get(rs);
                    D.ebugPrintlnINFO("---- prob:" + prob);
                    D.ebugPrintlnINFO("---- rsrcs:" + rs);
                    D.ebugPrintlnINFO();
                }

                D.ebugPrintlnINFO("targetReachedProb: " + targetReachedProb);
                D.ebugPrintlnINFO("===================================");
            }

            rolls++;

            if (rolls > cutoff)
            {
                D.ebugPrintlnINFO("startingResources=" + startingResources + "\ntargetResources=" + targetResources + "\ncutoff=" + cutoff + "\nourResources=" + ourResources);
                throw new CutoffExceededException();
            }

            //
            //  get our resources for the roll
            //
            for (int diceResult = 2; diceResult <= 12; diceResult++)
            {
                SOCResourceSet gainedResources = resourcesForRoll[diceResult];
                float diceProb = SOCNumberProbabilities.FLOAT_VALUES[diceResult];

                //
                //  add the resources that we get on this roll to 
                //  each set of resources that we got on the last
                //  roll and multiply the probabilities
                //
                Enumeration lastResourcesEnum = resourcesOnRoll[lastRoll].keys();

                while (lastResourcesEnum.hasMoreElements())
                {
                    SOCResourceSet lastResources = (SOCResourceSet) lastResourcesEnum.nextElement();
                    Float lastProb = (Float) resourcesOnRoll[lastRoll].get(lastResources);
                    SOCResourceSet newResources = lastResources.copy();
                    newResources.add(gainedResources);

                    float newProb = lastProb.floatValue() * diceProb;

                    if (!newResources.contains(targetResources))
                    {
                        //
                        // do any possible trading with the bank/ports
                        //
                        for (int giveResource = SOCResourceConstants.CLAY;
                                giveResource <= SOCResourceConstants.WOOD;
                                giveResource++)
                        {
                            if ((newResources.getAmount(giveResource) - targetResources.getAmount(giveResource)) > 1)
                            {
                                //
                                // find the ratio at which we can trade
                                //
                                int tradeRatio;

                                if (ports[giveResource])
                                {
                                    tradeRatio = 2;
                                }
                                else if (ports[SOCBoard.MISC_PORT])
                                {
                                    tradeRatio = 3;
                                }
                                else
                                {
                                    tradeRatio = 4;
                                }

                                //
                                // get the target resources
                                //
                                int numTrades = (newResources.getAmount(giveResource) - targetResources.getAmount(giveResource)) / tradeRatio;

                                //D.ebugPrintln("))) ***");
                                //D.ebugPrintln("))) giveResource="+giveResource);
                                //D.ebugPrintln("))) tradeRatio="+tradeRatio);
                                //D.ebugPrintln("))) newResources="+newResources);
                                //D.ebugPrintln("))) targetResources="+targetResources);
                                //D.ebugPrintln("))) numTrades="+numTrades);
                                for (int trades = 0; trades < numTrades;
                                        trades++)
                                {
                                    // 
                                    // find the most needed resource by looking at 
                                    // which of the resources we still need takes the
                                    // longest to aquire
                                    //
                                    int mostNeededResource = -1;

                                    for (int resource = SOCResourceConstants.CLAY;
                                            resource <= SOCResourceConstants.WOOD;
                                            resource++)
                                    {
                                        if (newResources.getAmount(resource) < targetResources.getAmount(resource))
                                        {
                                            if (mostNeededResource < 0)
                                            {
                                                mostNeededResource = resource;
                                            }
                                            else
                                            {
                                                if (rollsPerResource[resource] > rollsPerResource[mostNeededResource])
                                                {
                                                    mostNeededResource = resource;
                                                }
                                            }
                                        }
                                    }

                                    //
                                    // make the trade
                                    //
                                    //D.ebugPrintln("))) want to trade "+tradeRatio+" "+giveResource+" for a "+mostNeededResource);
                                    if ((mostNeededResource != -1) && (newResources.getAmount(giveResource) >= tradeRatio))
                                    {
                                        //D.ebugPrintln("))) trading...");
                                        newResources.add(1, mostNeededResource);

                                        if (newResources.getAmount(giveResource) < tradeRatio)
                                        {
                                            System.err.println("@@@ rsrcs=" + newResources);
                                            System.err.println("@@@ tradeRatio=" + tradeRatio);
                                            System.err.println("@@@ giveResource=" + giveResource);
                                            System.err.println("@@@ target=" + targetResources);
                                        }

                                        newResources.subtract(tradeRatio, giveResource);

                                        //D.ebugPrintln("))) newResources="+newResources);
                                    }

                                    if (newResources.contains(targetResources))
                                    {
                                        break;
                                    }
                                }

                                if (newResources.contains(targetResources))
                                {
                                    break;
                                }
                            }
                        }
                    }

                    //
                    //  if this set of resources is already in the list
                    //  of possible outcomes, add this probability to
                    //  that one, else just add this to the list
                    //
                    Float probFloat = (Float) resourcesOnRoll[thisRoll].get(newResources);
                    float newProb2 = newProb;

                    if (probFloat != null)
                    {
                        newProb2 = probFloat.floatValue() + newProb;
                    }

                    //
                    //  check to see if we reached our target
                    //
                    if (newResources.contains(targetResources))
                    {
                        D.ebugPrintlnINFO("-----> TARGET HIT *");
                        D.ebugPrintlnINFO("newResources: " + newResources);
                        D.ebugPrintlnINFO("newProb: " + newProb);
                        targetReachedProb += newProb;

                        if (targetReachedResources == null)
                        {
                            targetReachedResources = newResources;
                        }

                        if (targetReachedProb >= 0.5)
                        {
                            targetReached = true;
                        }
                    }
                    else
                    {
                        resourcesOnRoll[thisRoll].put(newResources, new Float(newProb2));
                    }
                }
            }

            //
            //  copy the resourcesOnRoll[thisRoll] table to the
            //  resourcesOnRoll[lastRoll] table and clear the
            //  resourcesOnRoll[thisRoll] table
            //
            int tmp = lastRoll;
            lastRoll = thisRoll;
            thisRoll = tmp;
            resourcesOnRoll[thisRoll].clear();
        }

        if (D.ebugOn)
        {
            float probSum = (float) 0.0;
            D.ebugPrintlnINFO("**************** TARGET REACHED ************");
            D.ebugPrintlnINFO("targetReachedResources: " + targetReachedResources);
            D.ebugPrintlnINFO("targetReachedProb: " + targetReachedProb);
            D.ebugPrintlnINFO("roll: " + rolls);
            D.ebugPrintlnINFO("resourcesOnRoll[lastRoll]:");

            Enumeration roltEnum = resourcesOnRoll[lastRoll].keys();

            while (roltEnum.hasMoreElements())
            {
                SOCResourceSet rs = (SOCResourceSet) roltEnum.nextElement();
                Float prob = (Float) resourcesOnRoll[lastRoll].get(rs);
                probSum += prob.floatValue();
                D.ebugPrintlnINFO("---- prob:" + prob);
                D.ebugPrintlnINFO("---- rsrcs:" + rs);
                D.ebugPrintlnINFO();
            }

            D.ebugPrintlnINFO("probSum = " + probSum);
            D.ebugPrintlnINFO("===================================");
        }

        return (new SOCResSetBuildTimePair(targetReachedResources, rolls));
    }

}
