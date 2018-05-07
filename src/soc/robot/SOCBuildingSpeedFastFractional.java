package soc.robot;

import soc.game.SOCBoard;
import soc.game.SOCPlayerNumbers;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.util.CutoffExceededException;

/**
 * Reworked version of original fast implementation of building speed estimate.
 * 
 * Rework resource accrual to use fractions of resources, earning 1/n per
 *  turn instead of the 1 every n bursty estimate originally used, which
 *  was likely to result in many non-equal scenarios giving the same result
 *  due to a single dominating resource need, or even giving misleading results
 *  (eg scenario 1 gives us exactly the target set on turn 5, while scenario 2 gives
 *   3 resources more than the target set on turn 6 - in this case, scenario 2 might
 *   be preferable).
 * Rework estimates of bank/port trades.  Instead of doing this discretely,
 *   allow fractions of resources to be ported.  This will ensure that all
 *   resource holdings and income contribute positively to completion, preventing income
 *   holdings and sources from being dominated and deemed irrelevant.
 *   This will also allow us to do something with opponent UNKNOWN resources.
 *   Caveat: Don't do this on the 0th turn, use the current trade estimation.
 *     This is because one of the most likely scenarios involves us trying
 *     to trade on our own turn, in which case we may want to aim for a trade
 *     which allows us to bank/port for immediate building.
 * Instead of simulating bank/port trades on the fly, keep a running tally
 *   of how much bank/port resource we have access to and return when this
 *   exceeds the amount we are short of the target resource set.  This 
 *   corrects an issue in the previous implementation where it might simulate
 *   bank trading for the wrong resource.
 * 
 * @author KHO
 *
 */
public class SOCBuildingSpeedFastFractional extends SOCBuildingSpeedEstimate {

    public SOCBuildingSpeedFastFractional(SOCPlayerNumbers numbers) {
        super(numbers);
    }

    public SOCBuildingSpeedFastFractional() {
        super();
    }

    @Override
    public SOCResSetBuildTimePair calculateRollsFast(
            SOCResourceSet startingResources, SOCResourceSet targetResources,
            int cutoff, boolean[] ports) throws CutoffExceededException 
    {
        // No need to copy, since we don't touch the actual resource counts
        SOCResourceSet ourResources = startingResources;
        int rolls = 0;
        
        if (!ourResources.contains(targetResources))
        {        
            // What is the best ratio we can trade a given resource at?
            double[] tradeRatios = new double[6];
            double worstTR = ports[SOCBoard.MISC_PORT] ? 3 : 4;
            
            double totalNeeded = 0;
            double[] needed = new double[6];
            double totalTradable = 0;            
            double totalFutureTradable = 0;
            
            double[] resourcesPerRoll = new double[6];
            
            for (int i = SOCResourceConstants.CLAY;
                    i <= SOCResourceConstants.WOOD;
                    i++)
            {
                if (rollsPerResource[i] == 0) {
                    // ?  This shouldn't really be possible, but might happen due to rounding
                    resourcesPerRoll[i] = 1;
                }
                else {
                    resourcesPerRoll[i] = 1.0 / (double) rollsPerResource[i];
                }
                /**
                 * find the ratio at which we can trade
                 */
                tradeRatios[i] = ports[i] ? 2 : worstTR;

                // do we need more of this resource?
                int target = targetResources.getAmount(i);
                int current = ourResources.getAmount(i);
                if (current <= target) {
                    // if so, set needed amount and increase total needed (may be 0 - no problem)
                    needed[i] = target - current;
                    totalNeeded+= target - current;
                }
                else {
                    // if we don't need more, can we trade any surplus for resources
                    //  we do need?
                    totalTradable += (current - target) / (int) tradeRatios[i];
                    
                    // Track future tradable resources - these are resources
                    //  which might become port tradable subsequently, but aren't
                    //  on the 0th turn because we don't have enough for a complete trade
                    totalFutureTradable += (double) ((current - target) % tradeRatios[i]) / tradeRatios[i];
                }               
            }
            // Track future tradability of UNKNOWN resources
            totalFutureTradable += ((double)ourResources.getAmount(SOCResourceConstants.UNKNOWN)) / worstTR;
        
            if (totalNeeded > totalTradable) {
                totalTradable += totalFutureTradable;  
                rolls++;
                while (totalNeeded > totalTradable)
                {
                    if (rolls > cutoff)
                    {
                        //D.ebugPrintln("startingResources="+startingResources+"\ntargetResources="+targetResources+"\ncutoff="+cutoff+"\nourResources="+ourResources);
                        throw new CutoffExceededException();
                    }
        
                    for (int i = SOCResourceConstants.CLAY;
                            i <= SOCResourceConstants.WOOD; i++)
                    {
                        double earned = resourcesPerRoll[i];
                        // If we need this resource, use as much of it 
                        //  as possible/needed to pay down our shortfall
                        if (needed[i]>0) {
                            double used = Math.min(needed[i], earned);
                            needed[i]-=used;
                            totalNeeded-=used;
                            earned-=used;
                        }
                        // Whatever is unneeded can be ported
                        totalTradable += earned / tradeRatios[i];
                    }                    
                    rolls++;
                }
            }
        }

        return (new SOCResSetBuildTimePair(ourResources, rolls));
    }

}
