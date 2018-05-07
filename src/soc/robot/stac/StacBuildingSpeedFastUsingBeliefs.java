package soc.robot.stac;

import soc.game.SOCBoard;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCPlayerNumbers;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.robot.SOCBuildingSpeedEstimate;
import soc.robot.SOCPossibleCard;
import soc.robot.SOCPossiblePiece;
import soc.robot.SOCResSetBuildTimePair;
import soc.util.CutoffExceededException;

/**
 * Original fast implementation of building speed estimate.
 * This was the version used in all cases in original JSettlers.
 * 
 * It simulates rolls which exactly follow the expected probability
 *  distribution, bank/port trading when possible, and returning when the target 
 *  is reached.
 *  
 * Note that due to assumptions made, the algorithm may be insensitive to
 *  resource count/income which are neither the most difficult to accrue
 *  nor the most likely to be used at bank/port.
 *  EG: Resources x 0 0 0 0
 *   Income M L H L 0
 *   Target: A road.
 *   In this case, it will be assumed that Wheat will be used to bank trade for the
 *    needed wood.  Because we have medium income of Clay, the algorithm will
 *    assume that Clay will have been accrued by the time enough Wheat is 
 *    accrued to trade for the Wood.  Thus, the algorithm will return the 
 *    same value regardless of whether x=0 or 1.
 *    
 *   This also works in the case of earning potential.  The algorithm will 
 *    return the same value whether Sheep income is L or 0, since in either
 *    case, Sheep is effectively irrelevant to the gathering of the wood.
 *    This may explain some of the observed sub-optimal build decisions.
 * 
 * @author KHO
 *
 */
public class StacBuildingSpeedFastUsingBeliefs extends SOCBuildingSpeedEstimate {

    StacRobotBrain brain;
    
    public StacBuildingSpeedFastUsingBeliefs(SOCPlayerNumbers numbers, StacRobotBrain brain) {
        super(numbers);
        this.brain = brain;
    }

    public StacBuildingSpeedFastUsingBeliefs() {
        super();
    }

    @Override
    public SOCResSetBuildTimePair calculateRollsFast(SOCResourceSet startingResources, SOCResourceSet targetResources, int cutoff, boolean[] ports) throws CutoffExceededException {
        //D.ebugPrintln("calculateRolls");
        //D.ebugPrintln("  start: "+startingResources);
        //D.ebugPrintln("  target: "+targetResources);
        SOCResourceSet ourResources = startingResources.copy();
        int rolls = 0;

        if (!ourResources.contains(targetResources)) {
            /**
             * do any possible trading with the bank/ports
             */
            for (int giveResource = SOCResourceConstants.CLAY; giveResource <= SOCResourceConstants.WOOD; giveResource++) {
                /**
                 * find the ratio at which we can trade
                 */
                int tradeRatio;

                if (ports[giveResource]) {
                    tradeRatio = 2;
                } else if (ports[SOCBoard.MISC_PORT]) {
                    tradeRatio = 3;
                } else {
                    tradeRatio = 4;
                }

                /**
                 * get the target resources
                 */
                int numTrades = (ourResources.getAmount(giveResource) - targetResources.getAmount(giveResource)) / tradeRatio;

                //D.ebugPrintln("))) ***");
                //D.ebugPrintln("))) giveResource="+giveResource);
                //D.ebugPrintln("))) tradeRatio="+tradeRatio);
                //D.ebugPrintln("))) ourResources="+ourResources);
                //D.ebugPrintln("))) targetResources="+targetResources);
                //D.ebugPrintln("))) numTrades="+numTrades);
                for (int trades = 0; trades < numTrades; trades++) {
                    /**
                     * find the most needed resource by looking at
                     * which of the resources we still need takes the
                     * longest to aquire
                     */
                    int mostNeededResource = -1;

                    for (int resource = SOCResourceConstants.CLAY; resource <= SOCResourceConstants.WOOD; resource++) {
                        if (ourResources.getAmount(resource) < targetResources.getAmount(resource)) {
                            if (mostNeededResource < 0) {
                                mostNeededResource = resource;
                            } else {
                                int[] rollsPerResource = getRollsPerResource();
                                if (rollsPerResource[resource] > rollsPerResource[mostNeededResource]) {
                                    mostNeededResource = resource;
                                }
                            }
                        }
                    }

                    /**
                     * make the trade
                     */

                    //D.ebugPrintln("))) want to trade "+tradeRatio+" "+giveResource+" for a "+mostNeededResource);
                    if ((mostNeededResource != -1) && (ourResources.getAmount(giveResource) >= tradeRatio)) {
                        //D.ebugPrintln("))) trading...");
                        ourResources.add(1, mostNeededResource);

                        if (ourResources.getAmount(giveResource) < tradeRatio) {
                            System.err.println("@@@ rsrcs=" + ourResources);
                            System.err.println("@@@ tradeRatio=" + tradeRatio);
                            System.err.println("@@@ giveResource=" + giveResource);
                            System.err.println("@@@ target=" + targetResources);
                        }

                        ourResources.subtract(tradeRatio, giveResource);

                        //D.ebugPrintln("))) ourResources="+ourResources);
                    }

                    if (ourResources.contains(targetResources)) {
                        break;
                    }
                }

                if (ourResources.contains(targetResources)) {
                    break;
                }
            }
        }

        while (!ourResources.contains(targetResources)) {
            //D.ebugPrintln("roll: "+rolls);
            //D.ebugPrintln("resources: "+ourResources);
            rolls++;

            if (rolls > cutoff) {
                //D.ebugPrintln("startingResources="+startingResources+"\ntargetResources="+targetResources+"\ncutoff="+cutoff+"\nourResources="+ourResources);
                throw new CutoffExceededException();
            }

            for (int resource = SOCResourceConstants.CLAY; resource <= SOCResourceConstants.WOOD; resource++) {
                //D.ebugPrintln("resource: "+resource);
                //D.ebugPrintln("rollsPerResource: "+rollsPerResource[resource]);

                /**
                 * get our resources for the roll
                 */
                int[] rollsPerResource = getRollsPerResource();
                if ((rollsPerResource[resource] == 0) || ((rolls % rollsPerResource[resource]) == 0)) {
                    ourResources.add(1, resource);
                }
            }

            if (!ourResources.contains(targetResources)) {
                /**
                 * do any possible trading with the bank/ports
                 */
                for (int giveResource = SOCResourceConstants.CLAY; giveResource <= SOCResourceConstants.WOOD; giveResource++) {
                    /**
                     * find the ratio at which we can trade
                     */
                    int tradeRatio;

                    if (ports[giveResource]) {
                        tradeRatio = 2;
                    } else if (ports[SOCBoard.MISC_PORT]) {
                        tradeRatio = 3;
                    } else {
                        tradeRatio = 4;
                    }

                    /**
                     * get the target resources
                     */
                    int numTrades = (ourResources.getAmount(giveResource) - targetResources.getAmount(giveResource)) / tradeRatio;

                    //D.ebugPrintln("))) ***");
                    //D.ebugPrintln("))) giveResource="+giveResource);
                    //D.ebugPrintln("))) tradeRatio="+tradeRatio);
                    //D.ebugPrintln("))) ourResources="+ourResources);
                    //D.ebugPrintln("))) targetResources="+targetResources);
                    //D.ebugPrintln("))) numTrades="+numTrades);
                    for (int trades = 0; trades < numTrades; trades++) {
                        /**
                         * find the most needed resource by looking at
                         * which of the resources we still need takes the
                         * longest to aquire
                         */
                        int mostNeededResource = -1;

                        for (int resource = SOCResourceConstants.CLAY; resource <= SOCResourceConstants.WOOD; resource++) {
                            if (ourResources.getAmount(resource) < targetResources.getAmount(resource)) {
                                if (mostNeededResource < 0) {
                                    mostNeededResource = resource;
                                } else {
                                    int[] rollsPerResource = getRollsPerResource();
                                    if (rollsPerResource[resource] > rollsPerResource[mostNeededResource]) {
                                        mostNeededResource = resource;
                                    }
                                }
                            }
                        }

                        /**
                         * make the trade
                         */

                        //D.ebugPrintln("))) want to trade "+tradeRatio+" "+giveResource+" for a "+mostNeededResource);
                        if ((mostNeededResource != -1) && (ourResources.getAmount(giveResource) >= tradeRatio)) {
                            //D.ebugPrintln("))) trading...");
                            ourResources.add(1, mostNeededResource);

                            if (ourResources.getAmount(giveResource) < tradeRatio) {
                                System.err.println("@@@ rsrcs=" + ourResources);
                                System.err.println("@@@ tradeRatio=" + tradeRatio);
                                System.err.println("@@@ giveResource=" + giveResource);
                                System.err.println("@@@ target=" + targetResources);
                            }

                            ourResources.subtract(tradeRatio, giveResource);

                            //D.ebugPrintln("))) ourResources="+ourResources);
                        }

                        if (ourResources.contains(targetResources)) {
                            break;
                        }
                    }

                    if (ourResources.contains(targetResources)) {
                        break;
                    }
                }
            }
        }
        
        //now see if our beliefs about other players can improve our estimate
        SOCPlayer[] players = brain.getGame().getPlayers();
        for (int p = 0; p < players.length; p++) {
            if (p == brain.getPlayerNumber()) {
                continue;
            }
            
            //get the opponent's target piece
            SOCPossiblePiece[] targetPieces = brain.getMemory().getAllTargetPieces();
            SOCPossiblePiece opponentTargetPiece = targetPieces[p];
            if (opponentTargetPiece == null) {
                //we don't know what the opponent wants to build
                continue;
            }
//222            System.err.println("Player: " + p + " - target BP: " + opponentTargetPiece);

            SOCResourceSet resourcesNeedeByOpponentForBP = new SOCResourceSet();
            switch (opponentTargetPiece.getType()) {
                case SOCPossiblePiece.ROAD:
                    resourcesNeedeByOpponentForBP = SOCGame.ROAD_SET;
                    break;
                case SOCPossiblePiece.SETTLEMENT:
                    resourcesNeedeByOpponentForBP = SOCGame.SETTLEMENT_SET;
                    break;
                case SOCPossiblePiece.CITY:
                    resourcesNeedeByOpponentForBP = SOCGame.CITY_SET;
                    break;
                case SOCPossiblePiece.CARD:
                    resourcesNeedeByOpponentForBP = SOCGame.CARD_SET;
                    break;
            }

            //get the opponent's resources & spare resources
            SOCResourceSet opponentResources = brain.getMemory().getOpponentResources(p);
//222            System.err.println("Player: " + p + " - resources: " + opponentResources);
            SOCResourceSet spareOpponentResources = opponentResources.copy();
            spareOpponentResources.subtract(resourcesNeedeByOpponentForBP);
            
            int totalAmountOfUsefulSpareResourcesHeldByOpponents = 0;
            for (int r = SOCResourceConstants.CLAY; r <= SOCResourceConstants.WOOD; r++) {
                //check if we need this resource for our target resources
                int neededAmount = targetResources.getAmount(r) - startingResources.getAmount(r);
                if (neededAmount > 0) {
                    int extraAmountHeldByOpponent = spareOpponentResources.getAmount(r);
                    totalAmountOfUsefulSpareResourcesHeldByOpponents += extraAmountHeldByOpponent;
                }
            }
            
//            //make sure that we don't set the rolls to 0
//            if (rolls - totalAmountOfUsefulSpareResourcesHeldByOpponents > 0) {
//                rolls -= totalAmountOfUsefulSpareResourcesHeldByOpponents;
//            }
            //            if (rolls < 0) {
//                rolls = 0;
//            }
//            if (totalAmountOfUsefulSpareResourcesHeldByOpponents > 0) {
//                rolls++;
//            }
            //make sure that we don't set the rolls to 0
            if (totalAmountOfUsefulSpareResourcesHeldByOpponents > 0 && rolls > 1) {
                rolls--;
                totalAmountOfUsefulSpareResourcesHeldByOpponents--;
                if (totalAmountOfUsefulSpareResourcesHeldByOpponents > 1 && rolls > 1) {
                    rolls--;
                    if (totalAmountOfUsefulSpareResourcesHeldByOpponents > 2 && rolls > 1) {
                        rolls--;
                    }
                }
            }
        }

        return (new SOCResSetBuildTimePair(ourResources, rolls));
    }

}
