package soc.robot;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.Vector;

import soc.disableDebug.D;
import soc.game.SOCBoard;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCTradeOffer;
import soc.util.CutoffExceededException;

/**
 * Default implementation of negotiator decisions.  Refactored from
 * SOCRobotNegotiator
 * @author kho30
 *
 */
public class SOCRobotNegotiatorImpl extends SOCRobotNegotiator<SOCBuildPlanStack> {

    protected boolean[][] isSellingResource;

    protected SOCPossiblePiece[] targetPieces = new SOCPossiblePiece[4];
    
    protected Vector offersMade;
    
    protected boolean[][] wantsAnotherOffer;
    
    /** ---MG
     * keep track of the last offer we completed as part of responding to a partial offer
     */
    protected SOCTradeOffer bestCompletedOffer = null;

    public SOCRobotNegotiatorImpl(SOCRobotBrain<?, ?, SOCBuildPlanStack> br) {
        super(br);
    
        offersMade = new Vector();
        isSellingResource = new boolean[game.maxPlayers][SOCResourceConstants.MAXPLUSONE];
        resetIsSelling();
        
        wantsAnotherOffer = new boolean[game.maxPlayers][SOCResourceConstants.MAXPLUSONE];
        resetWantsAnotherOffer();

    }

    protected void resetIsSelling()
    {
        D.ebugPrintlnINFO("*** resetIsSelling (true for every resource the player has) ***");

        for (int rsrcType = SOCResourceConstants.CLAY;
                rsrcType <= SOCResourceConstants.WOOD; rsrcType++)
        {
            for (int pn = 0; pn < game.maxPlayers; pn++)
            {
                if (( ! game.isSeatVacant(pn)) &&
                        (game.getPlayer(pn).getResources().getAmount(rsrcType) > 0))
                {
                    isSellingResource[pn][rsrcType] = true;
                }
            }
        }
    }

    protected void markAsNotSelling(int pn, int rsrcType)
    {
        D.ebugPrintlnINFO("*** markAsNotSelling pn=" + pn + " rsrcType=" + rsrcType);
        isSellingResource[pn][rsrcType] = false;
    }

    protected void markAsSelling(int pn, int rsrcType)
    {
        D.ebugPrintlnINFO("*** markAsSelling pn=" + pn + " rsrcType=" + rsrcType);
        isSellingResource[pn][rsrcType] = true;
    }

    public SOCTradeOffer makeOffer(SOCBuildPlanStack buildPlan)	
    {
        SOCPossiblePiece targetPiece = buildPlan.peek();
        D.ebugPrintlnINFO("***** MAKE OFFER *****");

        if (targetPiece == null)
        {
            return null;
        }

        SOCTradeOffer offer = null;

        SOCResourceSet targetResources = targetPiece.getResourceCost();
        SOCResourceSet ourResources = ourPlayerData.getResources();

        D.ebugPrintlnINFO("*** targetResources = " + targetResources);
        D.ebugPrintlnINFO("*** ourResources = " + ourResources);

        if (ourResources.contains(targetResources))
        {
            return offer;
        }

        if (ourResources.getAmount(SOCResourceConstants.UNKNOWN) > 0)
        {
            D.ebugPrintlnINFO("AGG WE HAVE UNKNOWN RESOURCES !!!! %%%%%%%%%%%%%%%%%%%%%%%%%%%%");

            return offer;
        }

        SOCTradeOffer batna = getOfferToBank(buildPlan);
        D.ebugPrintlnINFO("*** BATNA = " + batna);

        SOCBuildingSpeedEstimate estimate = brain.getEstimator(ourPlayerData.getNumbers());

        SOCResourceSet giveResourceSet = new SOCResourceSet();
        SOCResourceSet getResourceSet = new SOCResourceSet();

        int batnaBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

        D.ebugPrintlnINFO("*** batnaBuildingTime = " + batnaBuildingTime);

        if (batna != null)
        {
            batnaBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, batna.getGiveSet(), batna.getGetSet(), estimate);
        }

        D.ebugPrintlnINFO("*** batnaBuildingTime = " + batnaBuildingTime);

        ///
        /// Seperate resource types into needed and not-needed.  Sort
        /// groups by frequency, most to least.  Start with most frequent 
        /// not-needed resources.  Trade for least frequent needed resources.
        ///
        int[] rollsPerResource = estimate.getRollsPerResource();
        int[] neededRsrc = new int[5];
        int[] notNeededRsrc = new int[5];
        int neededRsrcCount = 0;
        int notNeededRsrcCount = 0;

        for (int rsrcType = SOCResourceConstants.CLAY;
                rsrcType <= SOCResourceConstants.WOOD; rsrcType++)
        {
            if (targetResources.getAmount(rsrcType) > 0)
            {
                neededRsrc[neededRsrcCount] = rsrcType;
                neededRsrcCount++;
            }
            else
            {
                notNeededRsrc[notNeededRsrcCount] = rsrcType;
                notNeededRsrcCount++;
            }
        }

        for (int j = neededRsrcCount - 1; j >= 0; j--)
        {
            for (int i = 0; i < j; i++)
            {
                //D.ebugPrintln("j="+j+" i="+i);
                //D.ebugPrintln("neededRsrc[i]="+neededRsrc[i]+" "+rollsPerResource[neededRsrc[i]]);
                if (rollsPerResource[neededRsrc[i]] > rollsPerResource[neededRsrc[i + 1]])
                {
                    //D.ebugPrintln("swap with "+neededRsrc[i+1]+" "+rollsPerResource[neededRsrc[i+1]]);
                    int tmp = neededRsrc[i];
                    neededRsrc[i] = neededRsrc[i + 1];
                    neededRsrc[i + 1] = tmp;
                }
            }
        }

        if (D.ebugOn)
        {
            for (int i = 0; i < neededRsrcCount; i++)
            {
                D.ebugPrintlnINFO("NEEDED RSRC: " + neededRsrc[i] + " : " + rollsPerResource[neededRsrc[i]]);
            }
        }

        for (int j = notNeededRsrcCount - 1; j >= 0; j--)
        {
            for (int i = 0; i < j; i++)
            {
                //D.ebugPrintln("j="+j+" i="+i);
                //D.ebugPrintln("notNeededRsrc[i]="+notNeededRsrc[i]+" "+rollsPerResource[notNeededRsrc[i]]);
                if (rollsPerResource[notNeededRsrc[i]] > rollsPerResource[notNeededRsrc[i + 1]])
                {
                    //D.ebugPrintln("swap with "+notNeededRsrc[i+1]+" "+rollsPerResource[notNeededRsrc[i+1]]);
                    int tmp = notNeededRsrc[i];
                    notNeededRsrc[i] = notNeededRsrc[i + 1];
                    notNeededRsrc[i + 1] = tmp;
                }
            }
        }

        if (D.ebugOn)
        {
            for (int i = 0; i < notNeededRsrcCount; i++)
            {
                D.ebugPrintlnINFO("NOT-NEEDED RSRC: " + notNeededRsrc[i] + " : " + rollsPerResource[notNeededRsrc[i]]);
            }
        }

        ///
        /// make a list of what other players are selling
        ///
        boolean[] someoneIsSellingResource = new boolean[SOCResourceConstants.MAXPLUSONE];

        for (int rsrcType = SOCResourceConstants.CLAY;
                rsrcType <= SOCResourceConstants.WOOD; rsrcType++)
        {
            someoneIsSellingResource[rsrcType] = false;

            for (int pn = 0; pn < game.maxPlayers; pn++)
            {
                if ((pn != ourPlayerData.getPlayerNumber()) && (isSellingResource[pn][rsrcType]))
                {
                    someoneIsSellingResource[rsrcType] = true;
                    D.ebugPrintlnINFO("*** player " + pn + " is selling " + rsrcType);

                    break;
                }
            }
        }

        ///
        /// figure out which resources we don't have enough of
        /// that someone is selling
        ///
        int getRsrcIdx = neededRsrcCount - 1;

        while ((getRsrcIdx >= 0) && ((ourResources.getAmount(neededRsrc[getRsrcIdx]) >= targetResources.getAmount(neededRsrc[getRsrcIdx])) || (!someoneIsSellingResource[neededRsrc[getRsrcIdx]])))
        {
            getRsrcIdx--;
        }

        ///
        /// if getRsrcIdx < 0 then we've asked for everything
        /// we need and nobody has it
        ///
        if (getRsrcIdx >= 0)
        {
            D.ebugPrintlnINFO("*** getRsrc = " + neededRsrc[getRsrcIdx]);

            getResourceSet.add(1, neededRsrc[getRsrcIdx]);

            D.ebugPrintlnINFO("*** offer should be null : offer = " + offer);

            ///
            /// consider offers where we give one unneeded for one needed
            ///
            int giveRsrcIdx = 0;

            while ((giveRsrcIdx < notNeededRsrcCount) && (offer == null))
            {
                D.ebugPrintlnINFO("*** ourResources.getAmount(" + notNeededRsrc[giveRsrcIdx] + ") = " + ourResources.getAmount(notNeededRsrc[giveRsrcIdx]));

                if (ourResources.getAmount(notNeededRsrc[giveRsrcIdx]) > 0)
                {
                    giveResourceSet.clear();
                    giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx]);
                    offer = makeOfferAux(giveResourceSet, getResourceSet, neededRsrc[getRsrcIdx]);
                    D.ebugPrintlnINFO("*** offer = " + offer);

                    int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);
                    D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
                }

                giveRsrcIdx++;
            }

            D.ebugPrintlnINFO("*** ourResources = " + ourResources);

            ///
            /// consider offers where we give one needed for one needed
            ///
            if (offer == null)
            {
                int giveRsrcIdx1 = 0;

                while ((giveRsrcIdx1 < neededRsrcCount) && (offer == null))
                {
                    D.ebugPrintlnINFO("*** ourResources.getAmount(" + neededRsrc[giveRsrcIdx1] + ") = " + ourResources.getAmount(neededRsrc[giveRsrcIdx1]));
                    D.ebugPrintlnINFO("*** targetResources.getAmount(" + neededRsrc[giveRsrcIdx1] + ") = " + targetResources.getAmount(neededRsrc[giveRsrcIdx1]));

                    if ((ourResources.getAmount(neededRsrc[giveRsrcIdx1]) > targetResources.getAmount(neededRsrc[giveRsrcIdx1])) && (neededRsrc[giveRsrcIdx1] != neededRsrc[getRsrcIdx]))
                    {
                        giveResourceSet.clear();
                        giveResourceSet.add(1, neededRsrc[giveRsrcIdx1]);

                        ///
                        /// make sure the offer is better than our BATNA
                        ///
                        int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                        if ((offerBuildingTime < batnaBuildingTime) || ((batna != null) && (offerBuildingTime == batnaBuildingTime) && (giveResourceSet.getTotal() < batna.getGiveSet().getTotal())))
                        {
                            offer = makeOfferAux(giveResourceSet, getResourceSet, neededRsrc[getRsrcIdx]);
                            D.ebugPrintlnINFO("*** offer = " + offer);
                            D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
                        }
                    }

                    giveRsrcIdx1++;
                }
            }

            D.ebugPrintlnINFO("*** ourResources = " + ourResources);

            SOCResourceSet leftovers = ourResources.copy();
            leftovers.subtract(targetResources);

            D.ebugPrintlnINFO("*** leftovers = " + leftovers);

            ///
            /// consider offers where we give two for one needed
            ///
            if (offer == null)
            {
                int giveRsrcIdx1 = 0;
                int giveRsrcIdx2 = 0;

                while ((giveRsrcIdx1 < notNeededRsrcCount) && (offer == null))
                {
                    if (ourResources.getAmount(notNeededRsrc[giveRsrcIdx1]) > 0)
                    {
                        while ((giveRsrcIdx2 < notNeededRsrcCount) && (offer == null))
                        {
                            giveResourceSet.clear();
                            giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx1]);
                            giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx2]);

                            if (ourResources.contains(giveResourceSet))
                            {
                                ///
                                /// make sure the offer is better than our BATNA
                                ///
                                int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                if ((offerBuildingTime < batnaBuildingTime) || ((batna != null) && (offerBuildingTime == batnaBuildingTime) && (giveResourceSet.getTotal() < batna.getGiveSet().getTotal())))
                                {
                                    offer = makeOfferAux(giveResourceSet, getResourceSet, neededRsrc[getRsrcIdx]);
                                    D.ebugPrintlnINFO("*** offer = " + offer);
                                    D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
                                }
                            }

                            giveRsrcIdx2++;
                        }

                        giveRsrcIdx2 = 0;

                        while ((giveRsrcIdx2 < neededRsrcCount) && (offer == null))
                        {
                            if (neededRsrc[giveRsrcIdx2] != neededRsrc[getRsrcIdx])
                            {
                                giveResourceSet.clear();
                                giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx1]);
                                giveResourceSet.add(1, neededRsrc[giveRsrcIdx2]);

                                if (leftovers.contains(giveResourceSet))
                                {
                                    ///
                                    /// make sure the offer is better than our BATNA
                                    ///
                                    int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                    if ((offerBuildingTime < batnaBuildingTime) || ((batna != null) && (offerBuildingTime == batnaBuildingTime) && (giveResourceSet.getTotal() < batna.getGiveSet().getTotal())))
                                    {
                                        offer = makeOfferAux(giveResourceSet, getResourceSet, neededRsrc[getRsrcIdx]);
                                        D.ebugPrintlnINFO("*** offer = " + offer);
                                        D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
                                    }
                                }
                            }

                            giveRsrcIdx2++;
                        }
                    }

                    giveRsrcIdx1++;
                }

                giveRsrcIdx1 = 0;
                giveRsrcIdx2 = 0;

                while ((giveRsrcIdx1 < neededRsrcCount) && (offer == null))
                {
                    if ((leftovers.getAmount(neededRsrc[giveRsrcIdx1]) > 0) && (neededRsrc[giveRsrcIdx1] != neededRsrc[getRsrcIdx]))
                    {
                        while ((giveRsrcIdx2 < notNeededRsrcCount) && (offer == null))
                        {
                            giveResourceSet.clear();
                            giveResourceSet.add(1, neededRsrc[giveRsrcIdx1]);
                            giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx2]);

                            if (leftovers.contains(giveResourceSet))
                            {
                                ///
                                /// make sure the offer is better than our BATNA
                                ///
                                int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                if ((offerBuildingTime < batnaBuildingTime) || ((batna != null) && (offerBuildingTime == batnaBuildingTime) && (giveResourceSet.getTotal() < batna.getGiveSet().getTotal())))
                                {
                                    offer = makeOfferAux(giveResourceSet, getResourceSet, neededRsrc[getRsrcIdx]);
                                    D.ebugPrintlnINFO("*** offer = " + offer);
                                    D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
                                }
                            }

                            giveRsrcIdx2++;
                        }

                        giveRsrcIdx2 = 0;

                        while ((giveRsrcIdx2 < neededRsrcCount) && (offer == null))
                        {
                            if (neededRsrc[giveRsrcIdx2] != neededRsrc[getRsrcIdx])
                            {
                                giveResourceSet.clear();
                                giveResourceSet.add(1, neededRsrc[giveRsrcIdx1]);
                                giveResourceSet.add(1, neededRsrc[giveRsrcIdx2]);

                                if (leftovers.contains(giveResourceSet))
                                {
                                    ///
                                    /// make sure the offer is better than our BATNA
                                    ///
                                    int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                    if ((offerBuildingTime < batnaBuildingTime) || ((batna != null) && (offerBuildingTime == batnaBuildingTime) && (giveResourceSet.getTotal() < batna.getGiveSet().getTotal())))
                                    {
                                        offer = makeOfferAux(giveResourceSet, getResourceSet, neededRsrc[getRsrcIdx]);
                                        D.ebugPrintlnINFO("*** offer = " + offer);
                                        D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
                                    }
                                }
                            }

                            giveRsrcIdx2++;
                        }
                    }

                    giveRsrcIdx1++;
                }
            }
        }

        ///
        /// consider offers where we give one for one unneeded we 
        /// we can use at a bank or port
        ///
        if (offer == null)
        {
            SOCResourceSet leftovers = ourResources.copy();
            leftovers.subtract(targetResources);

            D.ebugPrintlnINFO("*** leftovers = " + leftovers);

            int getRsrcIdx2 = notNeededRsrcCount - 1;

            while ((getRsrcIdx2 >= 0) && (!someoneIsSellingResource[neededRsrc[getRsrcIdx2]]))
            {
                getRsrcIdx2--;
            }

            while ((getRsrcIdx2 >= 0) && (offer == null))
            {
                getResourceSet.clear();
                getResourceSet.add(1, notNeededRsrc[getRsrcIdx2]);
                leftovers.add(1, notNeededRsrc[getRsrcIdx2]);

                ///
                /// give one unneeded 
                ///
                if (offer == null)
                {
                    int giveRsrcIdx1 = 0;

                    while ((giveRsrcIdx1 < notNeededRsrcCount) && (offer == null))
                    {
                        if ((leftovers.getAmount(notNeededRsrc[giveRsrcIdx1]) > 0) && (notNeededRsrc[giveRsrcIdx1] != notNeededRsrc[getRsrcIdx2]))
                        {
                            leftovers.subtract(1, notNeededRsrc[giveRsrcIdx1]);

                            if (getOfferToBank(targetResources, leftovers) != null)
                            {
                                giveResourceSet.clear();
                                giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx1]);

                                ///
                                /// make sure the offer is better than our BATNA
                                ///
                                int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                if (offerBuildingTime < batnaBuildingTime)
                                {
                                    offer = makeOfferAux(giveResourceSet, getResourceSet, notNeededRsrc[getRsrcIdx2]);
                                    D.ebugPrintlnINFO("*** offer = " + offer);
                                    D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
                                }
                            }

                            leftovers.add(1, notNeededRsrc[giveRsrcIdx1]);
                        }

                        giveRsrcIdx1++;
                    }
                }

                ///
                /// give one needed 
                ///
                if (offer == null)
                {
                    int giveRsrcIdx1 = 0;

                    while ((giveRsrcIdx1 < neededRsrcCount) && (offer == null))
                    {
                        if (leftovers.getAmount(neededRsrc[giveRsrcIdx1]) > 0)
                        {
                            leftovers.subtract(1, neededRsrc[giveRsrcIdx1]);

                            if (getOfferToBank(targetResources, leftovers) != null)
                            {
                                giveResourceSet.clear();
                                giveResourceSet.add(1, neededRsrc[giveRsrcIdx1]);

                                ///
                                /// make sure the offer is better than our BATNA
                                ///
                                int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                if (offerBuildingTime < batnaBuildingTime)
                                {
                                    offer = makeOfferAux(giveResourceSet, getResourceSet, notNeededRsrc[getRsrcIdx2]);
                                    D.ebugPrintlnINFO("*** offer = " + offer);
                                    D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
                                }
                            }

                            leftovers.add(1, neededRsrc[giveRsrcIdx1]);
                        }

                        giveRsrcIdx1++;
                    }
                }

                leftovers.subtract(1, notNeededRsrc[getRsrcIdx2]);
                getRsrcIdx2--;
            }
        }

        //---MG
        //make the offer partial by deleting what we're offering to give
        if (offer != null) {
            //if (offer.getFrom() == player) //we only make our own offers partial
            //offer.getGiveSet().clear();
            //offer.getGetSet().clear();

            // Make partial offers 50% of the time
            if (RANDOM.nextBoolean()) {
                offer.getGiveSet().clear(); // Is this safe???
                // I hate how resource sets are used - so many reference risks.  Would love to refactor 
                // to make ResourceSet, which is immutable, and ResourceTracker, which extends and offers the same getter functionality, 
                // but also setters.  
                D.ebugPrintlnINFO("***************************** Agent making a partial offer!");
            }

        }

        return offer;
    }

    public SOCTradeOffer makeCounterOffer(SOCTradeOffer originalOffer)
    {
        D.ebugPrintlnINFO("***** MAKE COUNTER OFFER *****");

        SOCTradeOffer counterOffer = null;

        SOCBuildPlanStack ourBuildingPlan = brain.getBuildingPlan();
        SOCPossiblePiece targetPiece = targetPieces[ourPlayerData.getPlayerNumber()];

        if (targetPiece == null)
        {
            if (ourBuildingPlan.empty())
            {
                SOCRobotDM<SOCBuildPlanStack> simulator;
                D.ebugPrintlnINFO("**** our building plan is empty ****");
                simulator = new SOCRobotDMImpl(brain.getRobotParameters(), playerTrackers, ourPlayerTracker, ourPlayerData, ourBuildingPlan, brain.getRobotParameters().getStrategyType());
                simulator.planStuff();
            }

            if (ourBuildingPlan.empty())
            {
                return counterOffer;
            }

            targetPiece = (SOCPossiblePiece) ourBuildingPlan.peek();
            targetPieces[ourPlayerData.getPlayerNumber()] = targetPiece;
        }

        SOCResourceSet targetResources = targetPiece.getResourceCost();
        SOCResourceSet ourResources = ourPlayerData.getResources();

        D.ebugPrintlnINFO("*** targetResources = " + targetResources);
        D.ebugPrintlnINFO("*** ourResources = " + ourResources);

        if (ourResources.contains(targetResources))
        {
            return counterOffer;
        }

        if (ourResources.getAmount(SOCResourceConstants.UNKNOWN) > 0)
        {
            D.ebugPrintlnINFO("AGG WE HAVE UNKNOWN RESOURCES !!!! %%%%%%%%%%%%%%%%%%%%%%%%%%%%");

            return counterOffer;
        }

        SOCTradeOffer batna = getOfferToBank(ourBuildingPlan);
        D.ebugPrintlnINFO("*** BATNA = " + batna);

        SOCBuildingSpeedEstimate estimate = brain.getEstimator(ourPlayerData.getNumbers());

        SOCResourceSet giveResourceSet = new SOCResourceSet();
        SOCResourceSet getResourceSet = new SOCResourceSet();

        int batnaBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

        if (batna != null)
        {
            batnaBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, batna.getGiveSet(), batna.getGetSet(), estimate);
        }

        D.ebugPrintlnINFO("*** batnaBuildingTime = " + batnaBuildingTime);

        ///
        /// Seperate resource types into needed and not-needed.  Sort
        /// groups by frequency, most to least.  Start with most frequent 
        /// not-needed resources.  Trade for least frequent needed resources.
        ///
        int[] rollsPerResource = estimate.getRollsPerResource();
        int[] neededRsrc = new int[5];
        int[] notNeededRsrc = new int[5];
        int neededRsrcCount = 0;
        int notNeededRsrcCount = 0;

        for (int rsrcType = SOCResourceConstants.CLAY;
                rsrcType <= SOCResourceConstants.WOOD; rsrcType++)
        {
            if (targetResources.getAmount(rsrcType) > 0)
            {
                neededRsrc[neededRsrcCount] = rsrcType;
                neededRsrcCount++;
            }
            else
            {
                notNeededRsrc[notNeededRsrcCount] = rsrcType;
                notNeededRsrcCount++;
            }
        }

        for (int j = neededRsrcCount - 1; j >= 0; j--)
        {
            for (int i = 0; i < j; i++)
            {
                //D.ebugPrintln("j="+j+" i="+i);
                //D.ebugPrintln("neededRsrc[i]="+neededRsrc[i]+" "+rollsPerResource[neededRsrc[i]]);
                if (rollsPerResource[neededRsrc[i]] > rollsPerResource[neededRsrc[i + 1]])
                {
                    //D.ebugPrintln("swap with "+neededRsrc[i+1]+" "+rollsPerResource[neededRsrc[i+1]]);
                    int tmp = neededRsrc[i];
                    neededRsrc[i] = neededRsrc[i + 1];
                    neededRsrc[i + 1] = tmp;
                }
            }
        }

        if (D.ebugOn)
        {
            for (int i = 0; i < neededRsrcCount; i++)
            {
                D.ebugPrintlnINFO("NEEDED RSRC: " + neededRsrc[i] + " : " + rollsPerResource[neededRsrc[i]]);
            }
        }

        for (int j = notNeededRsrcCount - 1; j >= 0; j--)
        {
            for (int i = 0; i < j; i++)
            {
                //D.ebugPrintln("j="+j+" i="+i);
                //D.ebugPrintln("notNeededRsrc[i]="+notNeededRsrc[i]+" "+rollsPerResource[notNeededRsrc[i]]);
                if (rollsPerResource[notNeededRsrc[i]] > rollsPerResource[notNeededRsrc[i + 1]])
                {
                    //D.ebugPrintln("swap with "+notNeededRsrc[i+1]+" "+rollsPerResource[notNeededRsrc[i+1]]);
                    int tmp = notNeededRsrc[i];
                    notNeededRsrc[i] = notNeededRsrc[i + 1];
                    notNeededRsrc[i + 1] = tmp;
                }
            }
        }

        if (D.ebugOn)
        {
            for (int i = 0; i < notNeededRsrcCount; i++)
            {
                D.ebugPrintlnINFO("NOT-NEEDED RSRC: " + notNeededRsrc[i] + " : " + rollsPerResource[notNeededRsrc[i]]);
            }
        }

        ///
        /// figure out which resources we don't have enough of
        /// that the offering player is selling
        ///
        int getRsrcIdx = neededRsrcCount - 1;

        while ((getRsrcIdx >= 0) && ((ourResources.getAmount(neededRsrc[getRsrcIdx]) >= targetResources.getAmount(neededRsrc[getRsrcIdx])) || (originalOffer.getGiveSet().getAmount(neededRsrc[getRsrcIdx]) == 0)))
        {
            getRsrcIdx--;
        }

        ///
        /// if getRsrcIdx < 0 then we've asked for everything
        /// we need and the offering player isn't selling it
        ///
        if (getRsrcIdx >= 0)
        {
            D.ebugPrintlnINFO("*** getRsrc = " + neededRsrc[getRsrcIdx]);

            getResourceSet.add(1, neededRsrc[getRsrcIdx]);

            D.ebugPrintlnINFO("*** counterOffer should be null : counterOffer = " + counterOffer);

            ///
            /// consider offers where we give one unneeded for one needed
            ///
            int giveRsrcIdx = 0;

            while ((giveRsrcIdx < notNeededRsrcCount) && (counterOffer == null))
            {
                D.ebugPrintlnINFO("*** ourResources.getAmount(" + notNeededRsrc[giveRsrcIdx] + ") = " + ourResources.getAmount(notNeededRsrc[giveRsrcIdx]));

                if (ourResources.getAmount(notNeededRsrc[giveRsrcIdx]) > 0)
                {
                    giveResourceSet.clear();
                    giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx]);
                    counterOffer = makeOfferAux(giveResourceSet, getResourceSet, neededRsrc[getRsrcIdx]);
                    D.ebugPrintlnINFO("*** counterOffer = " + counterOffer);
                }

                giveRsrcIdx++;
            }

            D.ebugPrintlnINFO("*** ourResources = " + ourResources);

            ///
            /// consider offers where we give one needed for one needed
            ///
            if (counterOffer == null)
            {
                int giveRsrcIdx1 = 0;

                while ((giveRsrcIdx1 < neededRsrcCount) && (counterOffer == null))
                {
                    D.ebugPrintlnINFO("*** ourResources.getAmount(" + neededRsrc[giveRsrcIdx1] + ") = " + ourResources.getAmount(neededRsrc[giveRsrcIdx1]));
                    D.ebugPrintlnINFO("*** targetResources.getAmount(" + neededRsrc[giveRsrcIdx1] + ") = " + targetResources.getAmount(neededRsrc[giveRsrcIdx1]));

                    if ((ourResources.getAmount(neededRsrc[giveRsrcIdx1]) > targetResources.getAmount(neededRsrc[giveRsrcIdx1])) && (neededRsrc[giveRsrcIdx1] != neededRsrc[getRsrcIdx]))
                    {
                        giveResourceSet.clear();
                        giveResourceSet.add(1, neededRsrc[giveRsrcIdx1]);

                        ///
                        /// make sure the offer is better than our BATNA
                        ///
                        int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                        if ((offerBuildingTime < batnaBuildingTime) || ((batna != null) && (offerBuildingTime == batnaBuildingTime) && (giveResourceSet.getTotal() < batna.getGiveSet().getTotal())))
                        {
                            counterOffer = makeOfferAux(giveResourceSet, getResourceSet, neededRsrc[getRsrcIdx]);
                            D.ebugPrintlnINFO("*** counterOffer = " + counterOffer);
                            D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
                        }
                    }

                    giveRsrcIdx1++;
                }
            }

            D.ebugPrintlnINFO("*** ourResources = " + ourResources);

            SOCResourceSet leftovers = ourResources.copy();
            leftovers.subtract(targetResources);

            D.ebugPrintlnINFO("*** leftovers = " + leftovers);

            ///
            /// consider offers where we give two for one needed
            ///
            if (counterOffer == null)
            {
                int giveRsrcIdx1 = 0;
                int giveRsrcIdx2 = 0;

                while ((giveRsrcIdx1 < notNeededRsrcCount) && (counterOffer == null))
                {
                    if (ourResources.getAmount(notNeededRsrc[giveRsrcIdx1]) > 0)
                    {
                        while ((giveRsrcIdx2 < notNeededRsrcCount) && (counterOffer == null))
                        {
                            giveResourceSet.clear();
                            giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx1]);
                            giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx2]);

                            if (ourResources.contains(giveResourceSet))
                            {
                                ///
                                /// make sure the offer is better than our BATNA
                                ///
                                int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                if ((offerBuildingTime < batnaBuildingTime) || ((batna != null) && (offerBuildingTime == batnaBuildingTime) && (giveResourceSet.getTotal() < batna.getGiveSet().getTotal())))
                                {
                                    counterOffer = makeOfferAux(giveResourceSet, getResourceSet, neededRsrc[getRsrcIdx]);
                                    D.ebugPrintlnINFO("*** counterOffer = " + counterOffer);
                                    D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
                                }
                            }

                            giveRsrcIdx2++;
                        }

                        giveRsrcIdx2 = 0;

                        while ((giveRsrcIdx2 < neededRsrcCount) && (counterOffer == null))
                        {
                            if (neededRsrc[giveRsrcIdx2] != neededRsrc[getRsrcIdx])
                            {
                                giveResourceSet.clear();
                                giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx1]);
                                giveResourceSet.add(1, neededRsrc[giveRsrcIdx2]);

                                if (leftovers.contains(giveResourceSet))
                                {
                                    ///
                                    /// make sure the offer is better than our BATNA
                                    ///
                                    int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                    if ((offerBuildingTime < batnaBuildingTime) || ((batna != null) && (offerBuildingTime == batnaBuildingTime) && (giveResourceSet.getTotal() < batna.getGiveSet().getTotal())))
                                    {
                                        counterOffer = makeOfferAux(giveResourceSet, getResourceSet, neededRsrc[getRsrcIdx]);
                                        D.ebugPrintlnINFO("*** counterOffer = " + counterOffer);
                                        D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
                                    }
                                }
                            }

                            giveRsrcIdx2++;
                        }
                    }

                    giveRsrcIdx1++;
                }

                giveRsrcIdx1 = 0;
                giveRsrcIdx2 = 0;

                while ((giveRsrcIdx1 < neededRsrcCount) && (counterOffer == null))
                {
                    if ((leftovers.getAmount(neededRsrc[giveRsrcIdx1]) > 0) && (neededRsrc[giveRsrcIdx1] != neededRsrc[getRsrcIdx]))
                    {
                        while ((giveRsrcIdx2 < notNeededRsrcCount) && (counterOffer == null))
                        {
                            giveResourceSet.clear();
                            giveResourceSet.add(1, neededRsrc[giveRsrcIdx1]);
                            giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx2]);

                            if (leftovers.contains(giveResourceSet))
                            {
                                ///
                                /// make sure the offer is better than our BATNA
                                ///
                                int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                if ((offerBuildingTime < batnaBuildingTime) || ((batna != null) && (offerBuildingTime == batnaBuildingTime) && (giveResourceSet.getTotal() < batna.getGiveSet().getTotal())))
                                {
                                    counterOffer = makeOfferAux(giveResourceSet, getResourceSet, neededRsrc[getRsrcIdx]);
                                    D.ebugPrintlnINFO("*** counterOffer = " + counterOffer);
                                    D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
                                }
                            }

                            giveRsrcIdx2++;
                        }

                        giveRsrcIdx2 = 0;

                        while ((giveRsrcIdx2 < neededRsrcCount) && (counterOffer == null))
                        {
                            if (neededRsrc[giveRsrcIdx2] != neededRsrc[getRsrcIdx])
                            {
                                giveResourceSet.clear();
                                giveResourceSet.add(1, neededRsrc[giveRsrcIdx1]);
                                giveResourceSet.add(1, neededRsrc[giveRsrcIdx2]);

                                if (leftovers.contains(giveResourceSet))
                                {
                                    ///
                                    /// make sure the offer is better than our BATNA
                                    ///
                                    int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                    if ((offerBuildingTime < batnaBuildingTime) || ((batna != null) && (offerBuildingTime == batnaBuildingTime) && (giveResourceSet.getTotal() < batna.getGiveSet().getTotal())))
                                    {
                                        counterOffer = makeOfferAux(giveResourceSet, getResourceSet, neededRsrc[getRsrcIdx]);
                                        D.ebugPrintlnINFO("*** counterOffer = " + counterOffer);
                                        D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
                                    }
                                }
                            }

                            giveRsrcIdx2++;
                        }
                    }

                    giveRsrcIdx1++;
                }
            }
        }

        ///
        /// consider offers where we give one for one unneeded we 
        /// we can use at a bank or port
        ///
        if (counterOffer == null)
        {
            SOCResourceSet leftovers = ourResources.copy();
            leftovers.subtract(targetResources);

            D.ebugPrintlnINFO("*** leftovers = " + leftovers);

            int getRsrcIdx2 = notNeededRsrcCount - 1;

            while ((getRsrcIdx2 >= 0) && (originalOffer.getGiveSet().getAmount(notNeededRsrc[getRsrcIdx2]) == 0))
            {
                getRsrcIdx2--;
            }

            while ((getRsrcIdx2 >= 0) && (counterOffer == null))
            {
                getResourceSet.clear();
                getResourceSet.add(1, notNeededRsrc[getRsrcIdx2]);
                leftovers.add(1, notNeededRsrc[getRsrcIdx2]);

                ///
                /// give one unneeded 
                ///
                if (counterOffer == null)
                {
                    int giveRsrcIdx1 = 0;

                    while ((giveRsrcIdx1 < notNeededRsrcCount) && (counterOffer == null))
                    {
                        if ((leftovers.getAmount(notNeededRsrc[giveRsrcIdx1]) > 0) && (notNeededRsrc[giveRsrcIdx1] != notNeededRsrc[getRsrcIdx2]))
                        {
                            leftovers.subtract(1, notNeededRsrc[giveRsrcIdx1]);

                            if (getOfferToBank(targetResources, leftovers) != null)
                            {
                                giveResourceSet.clear();
                                giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx1]);

                                ///
                                /// make sure the offer is better than our BATNA
                                ///
                                int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                if (offerBuildingTime < batnaBuildingTime)
                                {
                                    counterOffer = makeOfferAux(giveResourceSet, getResourceSet, notNeededRsrc[getRsrcIdx2]);
                                    D.ebugPrintlnINFO("*** counterOffer = " + counterOffer);
                                    D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
                                }
                            }

                            leftovers.add(1, notNeededRsrc[giveRsrcIdx1]);
                        }

                        giveRsrcIdx1++;
                    }
                }

                ///
                /// give one needed 
                ///
                if (counterOffer == null)
                {
                    int giveRsrcIdx1 = 0;

                    while ((giveRsrcIdx1 < neededRsrcCount) && (counterOffer == null))
                    {
                        if (leftovers.getAmount(neededRsrc[giveRsrcIdx1]) > 0)
                        {
                            leftovers.subtract(1, neededRsrc[giveRsrcIdx1]);

                            if (getOfferToBank(targetResources, leftovers) != null)
                            {
                                giveResourceSet.clear();
                                giveResourceSet.add(1, neededRsrc[giveRsrcIdx1]);

                                ///
                                /// make sure the offer is better than our BATNA
                                ///
                                int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                if (offerBuildingTime < batnaBuildingTime)
                                {
                                    counterOffer = makeOfferAux(giveResourceSet, getResourceSet, notNeededRsrc[getRsrcIdx2]);
                                    D.ebugPrintlnINFO("*** counterOffer = " + counterOffer);
                                }
                            }

                            leftovers.add(1, neededRsrc[giveRsrcIdx1]);
                        }

                        giveRsrcIdx1++;
                    }
                }

                leftovers.subtract(1, notNeededRsrc[getRsrcIdx2]);
                getRsrcIdx2--;
            }
        }

        ///
        /// consider offers where we give one for two unneeded we 
        /// we can use at a bank or port
        ///
        if (counterOffer == null)
        {
            SOCResourceSet leftovers = ourResources.copy();
            leftovers.subtract(targetResources);

            D.ebugPrintlnINFO("*** leftovers = " + leftovers);

            int getRsrcIdx2 = notNeededRsrcCount - 1;

            while ((getRsrcIdx2 >= 0) && (originalOffer.getGiveSet().getAmount(notNeededRsrc[getRsrcIdx2]) == 0))
            {
                getRsrcIdx2--;
            }

            while ((getRsrcIdx2 >= 0) && (counterOffer == null))
            {
                getResourceSet.clear();
                getResourceSet.add(2, notNeededRsrc[getRsrcIdx2]);
                leftovers.add(2, notNeededRsrc[getRsrcIdx2]);

                ///
                /// give one unneeded 
                ///
                if (counterOffer == null)
                {
                    int giveRsrcIdx1 = 0;

                    while ((giveRsrcIdx1 < notNeededRsrcCount) && (counterOffer == null))
                    {
                        if ((leftovers.getAmount(notNeededRsrc[giveRsrcIdx1]) > 0) && (notNeededRsrc[giveRsrcIdx1] != notNeededRsrc[getRsrcIdx2]))
                        {
                            leftovers.subtract(1, notNeededRsrc[giveRsrcIdx1]);

                            if (getOfferToBank(targetResources, leftovers) != null)
                            {
                                giveResourceSet.clear();
                                giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx1]);

                                ///
                                /// make sure the offer is better than our BATNA
                                ///
                                int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                if (offerBuildingTime < batnaBuildingTime)
                                {
                                    counterOffer = makeOfferAux(giveResourceSet, getResourceSet, notNeededRsrc[getRsrcIdx2]);
                                    D.ebugPrintlnINFO("*** counterOffer = " + counterOffer);
                                    D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
                                }
                            }

                            leftovers.add(1, notNeededRsrc[giveRsrcIdx1]);
                        }

                        giveRsrcIdx1++;
                    }
                }

                ///
                /// give one needed 
                ///
                if (counterOffer == null)
                {
                    int giveRsrcIdx1 = 0;

                    while ((giveRsrcIdx1 < neededRsrcCount) && (counterOffer == null))
                    {
                        if (leftovers.getAmount(neededRsrc[giveRsrcIdx1]) > 0)
                        {
                            leftovers.subtract(1, neededRsrc[giveRsrcIdx1]);

                            if (getOfferToBank(targetResources, leftovers) != null)
                            {
                                giveResourceSet.clear();
                                giveResourceSet.add(1, neededRsrc[giveRsrcIdx1]);

                                ///
                                /// make sure the offer is better than our BATNA
                                ///
                                int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                if (offerBuildingTime < batnaBuildingTime)
                                {
                                    counterOffer = makeOfferAux(giveResourceSet, getResourceSet, notNeededRsrc[getRsrcIdx2]);
                                    D.ebugPrintlnINFO("*** counterOffer = " + counterOffer);
                                }
                            }

                            leftovers.add(1, neededRsrc[giveRsrcIdx1]);
                        }

                        giveRsrcIdx1++;
                    }
                }

                leftovers.subtract(2, notNeededRsrc[getRsrcIdx2]);
                getRsrcIdx2--;
            }
        }

        ///
        /// consider offers where we give one for three unneeded we 
        /// we can use at a bank or port
        ///
        if (counterOffer == null)
        {
            SOCResourceSet leftovers = ourResources.copy();
            leftovers.subtract(targetResources);

            D.ebugPrintlnINFO("*** leftovers = " + leftovers);

            int getRsrcIdx2 = notNeededRsrcCount - 1;

            while ((getRsrcIdx2 >= 0) && (originalOffer.getGiveSet().getAmount(notNeededRsrc[getRsrcIdx2]) == 0))
            {
                getRsrcIdx2--;
            }

            while ((getRsrcIdx2 >= 0) && (counterOffer == null))
            {
                getResourceSet.clear();
                getResourceSet.add(3, notNeededRsrc[getRsrcIdx2]);
                leftovers.add(3, notNeededRsrc[getRsrcIdx2]);

                ///
                /// give one unneeded 
                ///
                if (counterOffer == null)
                {
                    int giveRsrcIdx1 = 0;

                    while ((giveRsrcIdx1 < notNeededRsrcCount) && (counterOffer == null))
                    {
                        if ((leftovers.getAmount(notNeededRsrc[giveRsrcIdx1]) > 0) && (notNeededRsrc[giveRsrcIdx1] != notNeededRsrc[getRsrcIdx2]))
                        {
                            leftovers.subtract(1, notNeededRsrc[giveRsrcIdx1]);

                            if (getOfferToBank(targetResources, leftovers) != null)
                            {
                                giveResourceSet.clear();
                                giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx1]);

                                ///
                                /// make sure the offer is better than our BATNA
                                ///
                                int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                if (offerBuildingTime < batnaBuildingTime)
                                {
                                    counterOffer = makeOfferAux(giveResourceSet, getResourceSet, notNeededRsrc[getRsrcIdx2]);
                                    D.ebugPrintlnINFO("*** counterOffer = " + counterOffer);
                                    D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
                                }
                            }

                            leftovers.add(1, notNeededRsrc[giveRsrcIdx1]);
                        }

                        giveRsrcIdx1++;
                    }
                }

                ///
                /// give one needed 
                ///
                if (counterOffer == null)
                {
                    int giveRsrcIdx1 = 0;

                    while ((giveRsrcIdx1 < neededRsrcCount) && (counterOffer == null))
                    {
                        if (leftovers.getAmount(neededRsrc[giveRsrcIdx1]) > 0)
                        {
                            leftovers.subtract(1, neededRsrc[giveRsrcIdx1]);

                            if (getOfferToBank(targetResources, leftovers) != null)
                            {
                                giveResourceSet.clear();
                                giveResourceSet.add(1, neededRsrc[giveRsrcIdx1]);

                                ///
                                /// make sure the offer is better than our BATNA
                                ///
                                int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                if (offerBuildingTime < batnaBuildingTime)
                                {
                                    counterOffer = makeOfferAux(giveResourceSet, getResourceSet, notNeededRsrc[getRsrcIdx2]);
                                    D.ebugPrintlnINFO("*** counterOffer = " + counterOffer);
                                }
                            }

                            leftovers.add(1, neededRsrc[giveRsrcIdx1]);
                        }

                        giveRsrcIdx1++;
                    }
                }

                leftovers.subtract(3, notNeededRsrc[getRsrcIdx2]);
                getRsrcIdx2--;
            }
        }

        return counterOffer;
    }

    /**
     * @return the offer that we'll make to the bank/ports
     *
     * @param targetResources  what resources we want
     * @param ourResources     the resources we have
     */
    public SOCTradeOffer getOfferToBank(SOCBuildPlanStack buildPlan, SOCResourceSet ourResources)
    {
        if (buildPlan==null || buildPlan.isEmpty()) {
            return null;
        }
        SOCPossiblePiece targetPiece = buildPlan.peek();
        SOCResourceSet targetResources = SOCPlayingPiece.getResourcesToBuild(targetPiece.getType());

        return getOfferToBank(targetResources, ourResources);
    }

    /**
     * Contains the actual logic for computing the offer to make to bank/ports
     * @param targetResources resources required for achieving the build plan
     * @param ourResources resource in hand
     * @return
     */
    protected SOCTradeOffer getOfferToBank(SOCResourceSet targetResources, SOCResourceSet ourResources ) {
        SOCTradeOffer bankTrade = null;

        if (ourResources.contains(targetResources))
        {
            return bankTrade;
        }

        SOCBuildingSpeedEstimate estimate = brain.getEstimator(ourPlayerData.getNumbers());
        int[] rollsPerResource = estimate.getRollsPerResource();
        boolean[] ports = ourPlayerData.getPortFlags();

        /**
         * do any possible trading with the bank/ports
         */

        ///
        /// Seperate resource types into needed and not-needed.  Sort
        /// groups by frequency, most to least.  Start with most frequent 
        /// not-needed resources.  Trade for least frequent needed resources.
        /// Loop until freq. of give resource + thresh >= get resource freq.
        /// and there is not enough of that resource to trade after 
        /// subtracting needed ammount.
        ///
        int[] neededRsrc = new int[5];
        int[] notNeededRsrc = new int[5];
        int neededRsrcCount = 0;
        int notNeededRsrcCount = 0;

        for (int rsrcType = SOCResourceConstants.CLAY;
                rsrcType <= SOCResourceConstants.WOOD; rsrcType++)
        {
            if (targetResources.getAmount(rsrcType) > 0)
            {
                neededRsrc[neededRsrcCount] = rsrcType;
                neededRsrcCount++;
            }
            else
            {
                notNeededRsrc[notNeededRsrcCount] = rsrcType;
                notNeededRsrcCount++;
            }
        }

        for (int j = neededRsrcCount - 1; j >= 0; j--)
        {
            for (int i = 0; i < j; i++)
            {
                //D.ebugPrintln("j="+j+" i="+i);
                //D.ebugPrintln("neededRsrc[i]="+neededRsrc[i]+" "+rollsPerResource[neededRsrc[i]]);
                if (rollsPerResource[neededRsrc[i]] > rollsPerResource[neededRsrc[i + 1]])
                {
                    //D.ebugPrintln("swap with "+neededRsrc[i+1]+" "+rollsPerResource[neededRsrc[i+1]]);
                    int tmp = neededRsrc[i];
                    neededRsrc[i] = neededRsrc[i + 1];
                    neededRsrc[i + 1] = tmp;
                }
            }
        }

        /*
           for (int i = 0; i < neededRsrcCount; i++) {
           //D.ebugPrintln("NEEDED RSRC: "+neededRsrc[i]+" : "+rollsPerResource[neededRsrc[i]]);
           }
         */
        for (int j = notNeededRsrcCount - 1; j >= 0; j--)
        {
            for (int i = 0; i < j; i++)
            {
                //D.ebugPrintln("j="+j+" i="+i);
                //D.ebugPrintln("notNeededRsrc[i]="+notNeededRsrc[i]+" "+rollsPerResource[notNeededRsrc[i]]);
                if (rollsPerResource[notNeededRsrc[i]] > rollsPerResource[notNeededRsrc[i + 1]])
                {
                    //D.ebugPrintln("swap with "+notNeededRsrc[i+1]+" "+rollsPerResource[notNeededRsrc[i+1]]);
                    int tmp = notNeededRsrc[i];
                    notNeededRsrc[i] = notNeededRsrc[i + 1];
                    notNeededRsrc[i + 1] = tmp;
                }
            }
        }

        /*
           for (int i = 0; i < notNeededRsrcCount; i++) {
           //D.ebugPrintln("NOT-NEEDED RSRC: "+notNeededRsrc[i]+" : "+rollsPerResource[notNeededRsrc[i]]);
           }
         */

        ///
        /// figure out which resources we don't have enough of
        ///
        int getRsrcIdx = neededRsrcCount - 1;

        while (ourResources.getAmount(neededRsrc[getRsrcIdx]) >= targetResources.getAmount(neededRsrc[getRsrcIdx]))
        {
            getRsrcIdx--;
        }

        int giveRsrcIdx = 0;

        while (giveRsrcIdx < notNeededRsrcCount)
        {
            ///
            /// find the ratio at which we can trade
            ///
            int tradeRatio;

            if (ports[notNeededRsrc[giveRsrcIdx]])
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

            if (ourResources.getAmount(notNeededRsrc[giveRsrcIdx]) >= tradeRatio)
            {
                ///
                /// make the trade
                ///
                SOCResourceSet give = new SOCResourceSet();
                SOCResourceSet get = new SOCResourceSet();
                give.add(tradeRatio, notNeededRsrc[giveRsrcIdx]);
                get.add(1, neededRsrc[getRsrcIdx]);

                //D.ebugPrintln("our resources: "+ourPlayerData.getResources());
                //D.ebugPrintln("Making bank trade:");
                //D.ebugPrintln("give: "+give);
                //D.ebugPrintln("get: "+get);
                boolean[] to = new boolean[game.maxPlayers];

                for (int i = 0; i < game.maxPlayers; i++)
                {
                    to[i] = false;
                }

                bankTrade = new SOCTradeOffer(game.getName(), ourPlayerData.getPlayerNumber(), to, give, get);

                return bankTrade;
            }
            else
            {
                giveRsrcIdx++;
            }
        }

        ///
        /// Can't trade not-needed resources.
        /// Try trading needed resources.
        ///
        giveRsrcIdx = 0;

        while (giveRsrcIdx < neededRsrcCount)
        {
            ///
            /// find the ratio at which we can trade
            ///
            int tradeRatio;

            if (ports[neededRsrc[giveRsrcIdx]])
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

            if (rollsPerResource[neededRsrc[giveRsrcIdx]] >= rollsPerResource[neededRsrc[getRsrcIdx]])
            {
                ///
                /// Don't want to trade unless we have extra of this resource
                ///
                if ((ourResources.getAmount(neededRsrc[giveRsrcIdx]) - targetResources.getAmount(neededRsrc[giveRsrcIdx])) >= tradeRatio)
                {
                    ///
                    /// make the trade
                    ///
                    SOCResourceSet give = new SOCResourceSet();
                    SOCResourceSet get = new SOCResourceSet();
                    give.add(tradeRatio, neededRsrc[giveRsrcIdx]);
                    get.add(1, neededRsrc[getRsrcIdx]);

                    //D.ebugPrintln("our resources: "+ourPlayerData.getResources());
                    //D.ebugPrintln("Making bank trade:");
                    //D.ebugPrintln("give: "+give);
                    //D.ebugPrintln("get: "+get);
                    boolean[] to = new boolean[game.maxPlayers];

                    for (int i = 0; i < game.maxPlayers; i++)
                    {
                        to[i] = false;
                    }

                    bankTrade = new SOCTradeOffer(game.getName(), ourPlayerData.getPlayerNumber(), to, give, get);

                    return bankTrade;
                }
            }
            else
            {
                ///
                /// We can trade this even though we need it because 
                /// we're betting that we'll get it by our next turn
                ///
                if (ourResources.getAmount(neededRsrc[giveRsrcIdx]) >= tradeRatio)
                {
                    ///
                    /// make the trade
                    ///
                    SOCResourceSet give = new SOCResourceSet();
                    SOCResourceSet get = new SOCResourceSet();
                    give.add(tradeRatio, neededRsrc[giveRsrcIdx]);
                    get.add(1, neededRsrc[getRsrcIdx]);

                    //D.ebugPrintln("our resources: "+ourPlayerData.getResources());
                    //D.ebugPrintln("Making bank trade:");
                    //D.ebugPrintln("give: "+give);
                    //D.ebugPrintln("get: "+get);
                    boolean[] to = new boolean[game.maxPlayers];

                    for (int i = 0; i < game.maxPlayers; i++)
                    {
                        to[i] = false;
                    }

                    bankTrade = new SOCTradeOffer(game.getName(), ourPlayerData.getPlayerNumber(), to, give, get);

                    return bankTrade;
                }
            }

            giveRsrcIdx++;
        }

        return bankTrade;
    }

   /**
     * Store the last offer building time so that StacRobotBrain can make a meta evaluation.
     * This is used for communication between considerOffer and handlePartialOffer
     * There's a lastOfferBuildingTime in the Stac version too, but that's a different variable
     */
    private int lastOfferBuildingTime; 
            
    /**
     * consider an offer made by another player
     *
     * @param offer  the offer to consider
     * @param receiverNum  the player number of the receiver
     *
     * @return if we want to accept, reject, or make a counter offer
     */
    public int considerOffer(SOCTradeOffer offer, int receiverNum)
    {
        ///
        /// This version should be faster
        ///
        D.ebugPrintlnINFO("***** CONSIDER OFFER 2 *****");

        int response = REJECT_OFFER;

        SOCPlayer receiverPlayerData = game.getPlayer(receiverNum);
        SOCResourceSet receiverResources = receiverPlayerData.getResources();

        SOCResourceSet rsrcsOut = offer.getGetSet();
        SOCResourceSet rsrcsIn = offer.getGiveSet();

        //
        // if the receiver doesn't have what's asked for, they'll reject
        //
        if ((receiverResources.getAmount(SOCResourceConstants.UNKNOWN) == 0) && (!receiverResources.contains(rsrcsOut)))
        {
            D.ebugPrintlnINFO("Reject offer; receiver does not have resource asked for.");
            return response;
        }

        int senderNum = offer.getFrom();

        D.ebugPrintlnINFO("senderNum = " + senderNum);
        D.ebugPrintlnINFO("receiverNum = " + receiverNum);
        D.ebugPrintlnINFO("rsrcs from receiver = " + rsrcsOut);
        D.ebugPrintlnINFO("rsrcs to receiver = " + rsrcsIn);

        SOCPossiblePiece receiverTargetPiece = targetPieces[receiverNum];

        D.ebugPrintlnINFO("targetPieces[" + receiverNum + "] = " + receiverTargetPiece);

        SOCPlayerTracker receiverPlayerTracker = (SOCPlayerTracker) playerTrackers.get(Integer.valueOf(receiverNum));

        if (receiverPlayerTracker == null)
        {
            D.ebugPrintlnINFO("Reject offer; receiverPlayerTracker == null");
            return response;
        }

        SOCPlayerTracker senderPlayerTracker = (SOCPlayerTracker) playerTrackers.get(Integer.valueOf(senderNum));

        if (senderPlayerTracker == null)
        {
            D.ebugPrintlnINFO("Reject offer; senderPlayerTracker == null");
            return response;
        }

        SOCRobotDM<SOCBuildPlanStack> simulator;

        if (receiverTargetPiece == null)
        {
            SOCBuildPlanStack receiverBuildingPlan = new SOCBuildPlanStack();
            // TODO: We should move this constructor and all similar ones into a function that can be overridden, so 
            //  that Stac negotiator can use Stac DM for simulations
            simulator = new SOCRobotDMImpl(brain.getRobotParameters(), playerTrackers, receiverPlayerTracker, receiverPlayerData, receiverBuildingPlan, brain.getRobotParameters().getStrategyType());

            if (receiverNum == ourPlayerData.getPlayerNumber())
            {
                simulator.planStuff();
            }
            else
            {
                simulator.planStuff();
            }

            if (receiverBuildingPlan.empty())
            {
                D.ebugPrintlnINFO("Reject offer; receiverBuildingPlan is empty");
                return response;
            }

            receiverTargetPiece = (SOCPossiblePiece) receiverBuildingPlan.peek();
            targetPieces[receiverNum] = receiverTargetPiece;           
        }

        D.ebugPrintlnINFO("receiverTargetPiece = " + receiverTargetPiece);

        SOCPossiblePiece senderTargetPiece = targetPieces[senderNum];

        D.ebugPrintlnINFO("targetPieces[" + senderNum + "] = " + senderTargetPiece);

        SOCPlayer senderPlayerData = game.getPlayer(senderNum);

        if (senderTargetPiece == null)
        {
            SOCBuildPlanStack senderBuildingPlan = new SOCBuildPlanStack();
            simulator = new SOCRobotDMImpl(brain.getRobotParameters(), playerTrackers, senderPlayerTracker, senderPlayerData, senderBuildingPlan, brain.getRobotParameters().getStrategyType());

            if (senderNum == ourPlayerData.getPlayerNumber())
            {
                simulator.planStuff();
            }
            else
            {
                simulator.planStuff();
            }

            if (senderBuildingPlan.empty())
            {
                D.ebugPrintlnINFO("Reject offer; senderBuildingPlan is empty");
                return response;
            }

            senderTargetPiece = (SOCPossiblePiece) senderBuildingPlan.peek();
            targetPieces[senderNum] = senderTargetPiece;
        }

        D.ebugPrintlnINFO("senderTargetPiece = " + senderTargetPiece);

        int senderWGETA = senderPlayerTracker.getWinGameETA();

        if (senderWGETA > WIN_GAME_CUTOFF)
        {
            //
            //  see if the sender is in a race with the receiver
            //
            boolean inARace = false;

            if ((receiverTargetPiece.getType() == SOCPossiblePiece.SETTLEMENT) || (receiverTargetPiece.getType() == SOCPossiblePiece.ROAD))
            {
                Enumeration threatsEnum = receiverTargetPiece.getThreats().elements();

                while (threatsEnum.hasMoreElements())
                {
                    SOCPossiblePiece threat = (SOCPossiblePiece) threatsEnum.nextElement();

                    if ((threat.getType() == senderTargetPiece.getType()) && (threat.getCoordinates() == senderTargetPiece.getCoordinates()))
                    {
                        inARace = true;

                        break;
                    }
                }

                if (inARace)
                {
                    D.ebugPrintlnINFO("inARace == true (threat from sender)");
                }
                else if (receiverTargetPiece.getType() == SOCPossiblePiece.SETTLEMENT)
                {
                    Enumeration conflictsEnum = ((SOCPossibleSettlement) receiverTargetPiece).getConflicts().elements();

                    while (conflictsEnum.hasMoreElements())
                    {
                        SOCPossibleSettlement conflict = (SOCPossibleSettlement) conflictsEnum.nextElement();

                        if ((senderTargetPiece.getType() == SOCPossiblePiece.SETTLEMENT) && (conflict.getCoordinates() == senderTargetPiece.getCoordinates()))
                        {
                            inARace = true;

                            break;
                        }
                    }

                    if (inARace)
                    {
                        D.ebugPrintlnINFO("inARace == true (conflict with sender)");
                    }
                }
            }

            if (!inARace)
            {
                ///
                /// see if this is good for the receiver
                ///
                SOCResourceSet targetResources = receiverTargetPiece.getResourceCost();

                SOCBuildingSpeedEstimate estimate = brain.getEstimator(receiverPlayerData.getNumbers());

                SOCTradeOffer receiverBatna = getOfferToBank(targetResources, receiverPlayerData.getResources());
                D.ebugPrintlnINFO("*** receiverBatna = " + receiverBatna);

                int batnaBuildingTime = getETAToTargetResources(receiverPlayerData, targetResources, SOCResourceSet.EMPTY_SET, SOCResourceSet.EMPTY_SET, estimate);

                D.ebugPrintlnINFO("*** batnaBuildingTime = " + batnaBuildingTime);

                int offerBuildingTime = getETAToTargetResources(receiverPlayerData, targetResources, rsrcsOut, rsrcsIn, estimate);

                D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);

                //---MG
                //store the last offer building time for meta evaluations in handleParitalOffer
                //but only if this building time is better than the BATNA
                //otherwise take 1000 as the usual worst case value
                //(the idea is that if there is a BATNA, then we shouldn't accept the trade: it will be better to trade with bank/port or not to trade) 
                lastOfferBuildingTime = offerBuildingTime < batnaBuildingTime ? offerBuildingTime : 1000;

                
                /*
                   if ((offerBuildingTime < batnaBuildingTime) ||
                       ((receiverBatna != null) &&
                        (offerBuildingTime == batnaBuildingTime) &&
                        (rsrcsOut.getTotal() < receiverBatna.getGiveSet().getTotal())) ||
                       ((receiverBatna == null) &&
                        (offerBuildingTime == batnaBuildingTime) &&
                        (rsrcsOut.getTotal() < rsrcsIn.getTotal()))) {
                 */

                //
                // only accept offers that are better than BATNA
                //
                if (offerBuildingTime < batnaBuildingTime)
                {
                    //                	//---MG
                    //                	//if we want to accept the offer, but the sender does not specify resources, we'll make a counteroffer
                    //                	if (offer.getGiveSet().getTotal() == 0 || offer.getGetSet().getTotal() == 0) {
                    //                    	D.ebugPrintln("Counter offer because sender did not specify `give' or `get' resources");
                    //                    	response = COUNTER_OFFER;
                    //                	}
                    //
                    D.ebugPrintlnINFO("Accept offer");
                    response = ACCEPT_OFFER;
                }
                else
                {
                    D.ebugPrintlnINFO("Counter offer");
                    response = COUNTER_OFFER;
                }
            }
        }

        return response;
    }  

    /**
     * another aux function
     * this one returns the number of rolls until we reach
     * the target given a possible offer
     *
     * @param player             our player data
     * @param targetResources    the resources we want
     * @param giveSet            the set of resources we're giving
     * @param getSet             the set of resources we're receiving
     * @param estimate           a SOCBuildingSpeedEstimate for our player
     */
    private int getETAToTargetResources(SOCPlayer player, SOCResourceSet targetResources, SOCResourceSet giveSet, SOCResourceSet getSet, SOCBuildingSpeedEstimate estimate)
    {
        SOCResourceSet ourResourcesCopy = player.getResources().copy();
        D.ebugPrintlnINFO("*** giveSet = " + giveSet);
        D.ebugPrintlnINFO("*** getSet = " + getSet);
        ourResourcesCopy.subtract(giveSet);
        ourResourcesCopy.add(getSet);

        int offerBuildingTime = 1000;

        try
        {
            SOCResSetBuildTimePair offerBuildingTimePair = estimate.calculateRollsFast(ourResourcesCopy, targetResources, 1000, player.getPortFlags());
            offerBuildingTime = offerBuildingTimePair.getRolls();
        }
        catch (CutoffExceededException e)
        {
            ;
        }

        D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
        D.ebugPrintlnINFO("*** ourResourcesCopy = " + ourResourcesCopy);

        return (offerBuildingTime);
    }

    /**
     * aux function for make offer
     */
    private SOCTradeOffer makeOfferAux(SOCResourceSet giveResourceSet, SOCResourceSet getResourceSet, int neededResource)
    {
        D.ebugPrintlnINFO("**** makeOfferAux ****");
        D.ebugPrintlnINFO("giveResourceSet = " + giveResourceSet);
        D.ebugPrintlnINFO("getResourceSet = " + getResourceSet);

        SOCTradeOffer offer = null;

        ///
        /// see if we've made this offer before
        ///
        boolean match = false;
        Iterator offersMadeIter = offersMade.iterator();

        while ((offersMadeIter.hasNext() && !match))
        {
            SOCTradeOffer pastOffer = (SOCTradeOffer) offersMadeIter.next();

            if ((pastOffer != null) && (pastOffer.getGiveSet().equals(giveResourceSet)) && (pastOffer.getGetSet().equals(getResourceSet)))
            {
                match = true;
            }
        }

        ///
        /// see if somone is offering this to us
        ///
        if (!match)
        {
            int opn = ourPlayerData.getPlayerNumber();
            for (int i = 0; i < game.maxPlayers; i++)
            {
                if (i != opn)
                {
                    SOCTradeOffer outsideOffer = game.getPlayer(i).getCurrentOffer();

                    if ((outsideOffer != null) && (outsideOffer.getGetSet().equals(giveResourceSet)) && (outsideOffer.getGiveSet().equals(getResourceSet)))
                    {
                        match = true;

                        break;
                    }
                }
            }
        }

        D.ebugPrintlnINFO("*** match = " + match);

        if (!match)
        {
            ///
            /// this is a new offer
            ///
            D.ebugPrintlnINFO("* this is a new offer");

            int numOfferedTo = 0;
            boolean[] offeredTo = new boolean[game.maxPlayers];
            int opn = ourPlayerData.getPlayerNumber();

            ///
            /// if it's our turn
            ///			
            if (game.getCurrentPlayerNumber() == opn)
            {
                ///
                /// only offer to players that are selling what we're asking for
                /// and aren't too close to winning
                ///
                for (int i = 0; i < game.maxPlayers; i++)
                {
                    D.ebugPrintlnINFO("** isSellingResource[" + i + "][" + neededResource + "] = " + isSellingResource[i][neededResource]);

                    if ((i != opn) && isSellingResource[i][neededResource] &&
                            (! game.isSeatVacant(i)) &&
                            (game.getPlayer(i).getResources().getTotal() >= getResourceSet.getTotal()))
                    {
                        SOCPlayerTracker tracker = (SOCPlayerTracker) playerTrackers.get(Integer.valueOf(i));

                        if ((tracker != null) && (tracker.getWinGameETA() >= WIN_GAME_CUTOFF))
                        {
                            numOfferedTo++;
                            offeredTo[i] = true;
                        }
                        else
                        {
                            offeredTo[i] = false;
                        }
                    }
                }
            }
            else
            {
                ///
                /// it's not our turn, just offer to the player who's turn it is
                ///
                int curpn = game.getCurrentPlayerNumber();

                if (isSellingResource[curpn][neededResource] && (game.getPlayer(curpn).getResources().getTotal() >= getResourceSet.getTotal()))
                {
                    D.ebugPrintlnINFO("** isSellingResource[" + curpn + "][" + neededResource + "] = " + isSellingResource[curpn][neededResource]);

                    SOCPlayerTracker tracker = (SOCPlayerTracker) playerTrackers.get(Integer.valueOf(curpn));

                    if ((tracker != null) && (tracker.getWinGameETA() >= WIN_GAME_CUTOFF))
                    {
                        numOfferedTo++;
                        offeredTo[curpn] = true;
                    }
                }
            }

            D.ebugPrintlnINFO("** numOfferedTo = " + numOfferedTo);

            if (numOfferedTo > 0)
            {
                ///
                ///  the offer
                ///
                offer = new SOCTradeOffer(game.getName(), ourPlayerData.getPlayerNumber(), offeredTo, giveResourceSet, getResourceSet);

                ///
                /// only make the offer if we think somone will take it
                ///
                boolean acceptable = false;

                for (int pn = 0; pn < game.maxPlayers; pn++)
                {
                    if (offeredTo[pn])
                    {
                        int offerResponse = considerOffer(offer, pn);
                        D.ebugPrintlnINFO("* considerOffer2(offer, " + pn + ") = " + offerResponse);

                        if (offerResponse == ACCEPT_OFFER)
                        {
                            acceptable = true;

                            break;
                        }
                    }
                }

                if (!acceptable)
                {
                    offer = null;
                }
            }
        }

        return offer;
    }

    @Override
    public void resetTargetPieces() {
        targetPieces = new SOCPossiblePiece[4];
    }

    @Override
    public void setTargetPiece(int pn, SOCBuildPlanStack buildPlan) {
        if (buildPlan!=null && buildPlan.size() > 0 ) {
            targetPieces[pn] = buildPlan.peek();
        }
        else {
            targetPieces[pn] = null;
        }
    }

    @Override
    public int handlePartialOffer(SOCTradeOffer offer)   {
        int response = -1;
        D.ebugPrintlnINFO("############################## " + ourPlayerData.getName() + ": Trying to complete a partial offer: " + offer.toString());
        //  D.ebugPrintln("############################## " + ourPlayerData.getName() + ": We're just making a counteroffer to: " + offer.toString());
        //  return SOCRobotNegotiator.COUNTER_OFFER;

        SOCResourceSet potentialResourcesOffered[] = new SOCResourceSet[20];
        potentialResourcesOffered[0] = new SOCResourceSet(2, 0, 0, 0, 0, 0);
        potentialResourcesOffered[1] = new SOCResourceSet(0, 2, 0, 0, 0, 0);
        potentialResourcesOffered[2] = new SOCResourceSet(0, 0, 2, 0, 0, 0);
        potentialResourcesOffered[3] = new SOCResourceSet(0, 0, 0, 2, 0, 0);
        potentialResourcesOffered[4] = new SOCResourceSet(0, 0, 0, 0, 2, 0);
        potentialResourcesOffered[5] = new SOCResourceSet(1, 1, 0, 0, 0, 0);
        potentialResourcesOffered[6] = new SOCResourceSet(1, 0, 1, 0, 0, 0);
        potentialResourcesOffered[7] = new SOCResourceSet(1, 0, 0, 1, 0, 0);
        potentialResourcesOffered[8] = new SOCResourceSet(1, 0, 0, 0, 1, 0);
        potentialResourcesOffered[9] = new SOCResourceSet(0, 1, 1, 0, 0, 0);
        potentialResourcesOffered[10] = new SOCResourceSet(0, 1, 0, 1, 0, 0);
        potentialResourcesOffered[11] = new SOCResourceSet(0, 1, 0, 0, 1, 0);
        potentialResourcesOffered[12] = new SOCResourceSet(0, 0, 1, 1, 0, 0);
        potentialResourcesOffered[13] = new SOCResourceSet(0, 0, 1, 0, 1, 0);
        potentialResourcesOffered[14] = new SOCResourceSet(0, 0, 0, 1, 1, 0);
        potentialResourcesOffered[15] = new SOCResourceSet(1, 0, 0, 0, 0, 0);
        potentialResourcesOffered[16] = new SOCResourceSet(0, 1, 0, 0, 0, 0);
        potentialResourcesOffered[17] = new SOCResourceSet(0, 0, 1, 0, 0, 0);
        potentialResourcesOffered[18] = new SOCResourceSet(0, 0, 0, 1, 0, 0);
        potentialResourcesOffered[19] = new SOCResourceSet(0, 0, 0, 0, 1, 0);


        SOCTradeOffer completedOfferWeWantToMake = null;
        bestCompletedOffer = null;
        int bestOfferBuildingTime = 1000; //seems to be that standard value for worst case

        // KHO: Build an array to consider the inverse of a trade - look into whether this capability is already built,
        //  or consider building it.  This is used below, but should compute it outside the loop.
        boolean[] inverseOfferTo = new boolean[4];
        for (int i=0; i<4; i++) {
            inverseOfferTo[i] = false;
        }
        inverseOfferTo[offer.getFrom()] = true;

        for (int index = 0; index < potentialResourcesOffered.length; index++) {
            SOCResourceSet proposedGiveResource = potentialResourcesOffered[index];
            //exclude trades that have the same resource as give and get
            if (!offer.getGetSet().contains(proposedGiveResource) && !proposedGiveResource.contains(offer.getGetSet())) { 
                // Before going into deep calculations of our build times, etc, we should first consider whether the 
                //  offer is even possible - ie whether the initiator has, or might have, the resources we are looking for.
                //  We can use considerOffer2 for this, in the inverse direction, which will check this among the first things it does 
                //  (not quite correctly, but in line with the rest of the code, at least).
                //  However, this goes deeper and considers whether we think the recipient would accept (or at least counter). 
                //  It may end up taking longer in some cases (typically when we can't rule out at least half the options).
                //  Additionally, we may sometimes want to come back with an offer we feel they will reject, because it will let us 
                //  negotiate towards something mutually beneficial.  Try this for now...

                SOCTradeOffer inverseOffer = new SOCTradeOffer(offer.getGame(), ourPlayerData.getPlayerNumber(), inverseOfferTo, offer.getGetSet(), proposedGiveResource);      
                // Don't make repeat offers
                if (offersMade.contains(inverseOffer)) {
                    continue;
                }
                response = considerOffer(inverseOffer, offer.getFrom());
                if (response == SOCRobotNegotiator.REJECT_OFFER) {
                    continue;
                }
                
                // assemble the completed offer
                SOCTradeOffer completedOffer = new SOCTradeOffer(offer.getGame(), offer.getFrom(), offer.getTo(), proposedGiveResource, offer.getGetSet()); 
                
                response = considerOffer(completedOffer, ourPlayerData.getPlayerNumber()); //the negotiator's response will be 1000 if the offer is worse than the BATNA
                if (response != SOCRobotNegotiator.REJECT_OFFER) {
                    if (response == SOCRobotNegotiator.ACCEPT_OFFER) {
                        D.ebugPrintlnINFO("############################### " + ourPlayerData.getName() + 
                                ": Found an acceptable completed counter offer: " + completedOffer.toString() + "; offer building time: " + lastOfferBuildingTime);
                        if (lastOfferBuildingTime < bestOfferBuildingTime) {
                            bestOfferBuildingTime = lastOfferBuildingTime;
                            bestCompletedOffer = completedOffer; //this offer is in the `wrong' direction!
                            // minor efficiency change for now - once we get a BOBT=0, we can never improve, so break
                            //  probably not the best way to break ties, but if we're going to do it, might as well catch it early
                            if (bestOfferBuildingTime == 0 ) {
                                break;
                            }
                        }
                    }
                }
            }                       
        }
        //if there's an offer we'd like to accept, return now to make this as a completed (`counter') offer
        if (bestCompletedOffer != null) {
            D.ebugPrintlnINFO("############################### " + ourPlayerData.getName() + 
                    ": The completed offer we want to make: " + bestCompletedOffer.toString() + "; build time: " + bestOfferBuildingTime + "; now returning `3'");
            return 3;
        }

        if (completedOfferWeWantToMake != null) {
            // $$$ KHO: This code is unreachable: see above.
            D.ebugPrintlnINFO("############################### " + ourPlayerData.getName() + 
                    ": Making a completed counter offer: " + completedOfferWeWantToMake.toString() + "; response: " + response);
            //          makeCounterOffer(completedOfferWeWantToMake);
            if (makeCounterOffer(completedOfferWeWantToMake) == null) {
                D.ebugPrintlnINFO("############################### " + ourPlayerData.getName() + 
                        ": makeCounterOffer returned false: we didn't make an offer");
                return SOCRobotNegotiator.REJECT_OFFER;
            } else {
                D.ebugPrintlnINFO("############################### " + ourPlayerData.getName() + 
                        ": makeCounterOffer returned true: we made an offer; now returning `4'");
                return 4; //the next constant after SOCRobotNegotiator.COUNTER_OFFER;
                //          return SOCRobotNegotiator.REJECT_OFFER;
            }
        } else {
            D.ebugPrintlnINFO("############################### " + ourPlayerData.getName() + 
                    ": there's no completed offer I want to make: rejecting offer");
            return SOCRobotNegotiator.REJECT_OFFER;
        }
    }
    
    public void addToOffersMade(SOCTradeOffer offer)
    {
        if (offer != null)
        {
            offersMade.add(offer);
        }
    }
    
    public void resetOffersMade()
    {
        offersMade.clear();
    }

    @Override
    public void resetTradesMade() {} //we're only using this in the Stac version

    public void resetWantsAnotherOffer()
    {
        D.ebugPrintlnINFO("*** resetWantsAnotherOffer (all false) ***");

        for (int rsrcType = SOCResourceConstants.CLAY;
                rsrcType <= SOCResourceConstants.WOOD; rsrcType++)
        {
            for (int pn = 0; pn < game.maxPlayers; pn++)
            {
                wantsAnotherOffer[pn][rsrcType] = false;
            }
        }
    }
    
    public void markAsNotWantingAnotherOffer(int pn, int rsrcType)
    {
        D.ebugPrintlnINFO("*** markAsNotWantingAnotherOffer pn=" + pn + " rsrcType=" + rsrcType);
        wantsAnotherOffer[pn][rsrcType] = false;
    }

    public void markAsWantsAnotherOffer(int pn, int rsrcType)
    {
        D.ebugPrintlnINFO("*** markAsWantsAnotherOffer pn=" + pn + " rsrcType=" + rsrcType);
        wantsAnotherOffer[pn][rsrcType] = true;
    }
    
    public boolean wantsAnotherOffer(int pn, int rsrcType)
    {
        return wantsAnotherOffer[pn][rsrcType];
    }

	@Override
	public void setBestCompletedOffer(SOCTradeOffer offer) {
		bestCompletedOffer = offer;
	}

	@Override
	public SOCTradeOffer getBestCompletedOffer() {
		return bestCompletedOffer;
	}
    
}
