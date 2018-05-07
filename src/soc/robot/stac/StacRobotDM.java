package soc.robot.stac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import soc.disableDebug.D;
import soc.game.SOCBoard;
import soc.game.SOCCity;
import soc.game.SOCDevCardConstants;
import soc.game.SOCDevCardSet;
import soc.game.SOCGame;
import soc.game.SOCLRPathData;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCRoad;
import soc.game.SOCSettlement;
import soc.robot.SOCBuildPlanStack;
import soc.robot.SOCBuildingSpeedEstimate;
import soc.robot.SOCPlayerTracker;
import soc.robot.SOCPossibleCard;
import soc.robot.SOCPossibleCity;
import soc.robot.SOCPossiblePiece;
import soc.robot.SOCPossibleRoad;
import soc.robot.SOCPossibleSettlement;
import soc.robot.SOCResSetBuildTimePair;
import soc.robot.SOCRobotBrain;
import soc.robot.SOCRobotClient;
import soc.robot.SOCRobotDM;
import soc.robot.SOCRobotDMImpl;
import soc.util.CutoffExceededException;
import soc.util.NodeLenVis;
import soc.util.Pair;
import soc.util.Queue;
import soc.util.SOCRobotParameters;

public class StacRobotDM extends SOCRobotDM<SOCBuildPlanStack> {

    /**
	 * The brain object providing access to various utilities (e.g. estimators) and fields (e.g. firstSettlements)
	 */
    protected final StacRobotBrain brain;
    
    /**
     * The old decision maker used for taking the majority of the decisions
     */
    private SOCRobotDMImpl oldDM;

    public StacRobotDM(StacRobotBrain br, SOCBuildPlanStack plan) {
        super();
    	brain = br;
        this.strategy=SOCRobotDMImpl.FAST_STRATEGY;
        if (brain.isRobotType(StacRobotType.SMART_GAME_STRATEGY)) {
            this.strategy = SOCRobotDMImpl.SMART_STRATEGY;
        }
        oldDM = new SOCRobotDMImpl(br, SOCRobotDMImpl.FAST_STRATEGY, plan);
        player = brain.getMemory().getPlayer(brain.getMemory().getName()); // called ourPlayerData in SOCRobotDM(Impl)

        playerTrackers = brain.getPlayerTrackers();
        ourPlayerTracker = brain.getOurPlayerTracker();

        buildingPlan = plan; //we are giving access to the buildingPlan object as the DM shouldn't forget its plans while making them

        threatenedRoads = new Vector();
        goodRoads = new Vector();
        threatenedSettlements = new Vector();
        goodSettlements = new Vector();
        SOCRobotParameters params = brain.getRobotParameters();
        maxGameLength = params.getMaxGameLength();
        maxETA = params.getMaxETA();
        etaBonusFactor = params.getETABonusFactor();
        adversarialFactor = params.getAdversarialFactor();
        leaderAdversarialFactor = params.getLeaderAdversarialFactor();
        devCardMultiplier = params.getDevCardMultiplier();
        threatMultiplier = params.getThreatMultiplier();        
    }

    public StacRobotDM(StacRobotBrain br, HashMap plTrackers, SOCPlayerTracker plTracker, SOCPlayer pl, SOCBuildPlanStack bp) {
        this(br, bp);
        this.playerTrackers = plTrackers;
        this.ourPlayerTracker = plTracker;
        this.player = pl;
        this.buildingPlan = bp;
    }
    
    /**
     * Temporary plan stuff method that doesn't push anything to the plan used by the run loop, only to the memory
     * This method is required by the data collection process to evaluate the value of the state based on the possible actions in addition
     * to etw.
     * @return 
     */
    public SOCBuildPlanStack planInMemory(){
    	SOCBuildingSpeedEstimate currentBSE = brain.getEstimator(player.getNumbers());
        int currentBuildingETAs[] = currentBSE.getEstimatesFromNowFast(player.getResources(), player.getPortFlags());

        threatenedSettlements.removeAllElements();
        goodSettlements.removeAllElements();
        threatenedRoads.removeAllElements();
        goodRoads.removeAllElements();

        favoriteRoad = null;
        favoriteSettlement = null;    
        favoriteCity = null;
        
        int leadersCurrentWGETA = ourPlayerTracker.getWinGameETA();
        Iterator trackersIter = playerTrackers.values().iterator();
        while (trackersIter.hasNext()) {
            SOCPlayerTracker tracker = (SOCPlayerTracker)trackersIter.next();
            int wgeta = tracker.getWinGameETA();
            if (wgeta < leadersCurrentWGETA) {
                leadersCurrentWGETA = wgeta;
            }
        }
        
        ///
        /// reset scores and biggest threats for everything
        ///
        Iterator posPiecesIter;
        SOCPossiblePiece posPiece;
        posPiecesIter = ourPlayerTracker.getPossibleCities().values().iterator();
        while (posPiecesIter.hasNext()) {
            posPiece = (SOCPossiblePiece)posPiecesIter.next();
            posPiece.resetScore();
            posPiece.clearBiggestThreats();
        }
        posPiecesIter = ourPlayerTracker.getPossibleSettlements().values().iterator();
        while (posPiecesIter.hasNext()) {
            posPiece = (SOCPossiblePiece)posPiecesIter.next();
            posPiece.resetScore();
            posPiece.clearBiggestThreats();
        }
        posPiecesIter = ourPlayerTracker.getPossibleRoads().values().iterator();
        while (posPiecesIter.hasNext()) {
            posPiece = (SOCPossiblePiece)posPiecesIter.next();
            posPiece.resetScore();
            posPiece.clearBiggestThreats();
        }
        
        //generate the plans following trybest stratey
        return dumbFastGameStrategyNBest(currentBuildingETAs, 0);
    }
    
    @Override
    public void planStuff() {
        
        //long startTime = System.currentTimeMillis();
        D.ebugPrintlnINFO("PLANSTUFF");

        SOCBuildingSpeedEstimate currentBSE = brain.getEstimator(player.getNumbers());
        int currentBuildingETAs[] = currentBSE.getEstimatesFromNowFast(player.getResources(), player.getPortFlags());

        threatenedSettlements.removeAllElements();
        goodSettlements.removeAllElements();
        threatenedRoads.removeAllElements();
        goodRoads.removeAllElements();

        favoriteRoad = null;
        favoriteSettlement = null;    
        favoriteCity = null;

        //SOCPlayerTracker.playerTrackersDebug(playerTrackers);

        ///
        /// update ETAs for LR, LA, and WIN
        ///
        if ((brain != null) && (brain.getDRecorder().isOn())) {
            // clear the table
            brain.getDRecorder().eraseAllRecords();
            // record our current resources
            brain.getDRecorder().startRecording(SOCRobotClient.CURRENT_RESOURCES);
            brain.getDRecorder().record(player.getResources().toShortString());
            brain.getDRecorder().stopRecording();
            // start recording the current players' plans
            brain.getDRecorder().startRecording(SOCRobotClient.CURRENT_PLANS);
        } 

        if (strategy == SMART_STRATEGY) {
            SOCPlayerTracker.updateWinGameETAs(playerTrackers);
        }

        if ((brain != null) && (brain.getDRecorder().isOn())) {
            // stop recording
            brain.getDRecorder().stopRecording();
        } 

        int leadersCurrentWGETA = ourPlayerTracker.getWinGameETA();
        Iterator trackersIter = playerTrackers.values().iterator();
        while (trackersIter.hasNext()) {
            SOCPlayerTracker tracker = (SOCPlayerTracker)trackersIter.next();
            int wgeta = tracker.getWinGameETA();
            if (wgeta < leadersCurrentWGETA) {
                leadersCurrentWGETA = wgeta;
            }
        }

        //SOCPlayerTracker.playerTrackersDebug(playerTrackers);

        ///
        /// reset scores and biggest threats for everything
        ///
        Iterator posPiecesIter;
        SOCPossiblePiece posPiece;
        posPiecesIter = ourPlayerTracker.getPossibleCities().values().iterator();
        while (posPiecesIter.hasNext()) {
            posPiece = (SOCPossiblePiece)posPiecesIter.next();
            posPiece.resetScore();
            posPiece.clearBiggestThreats();
        }
        posPiecesIter = ourPlayerTracker.getPossibleSettlements().values().iterator();
        while (posPiecesIter.hasNext()) {
            posPiece = (SOCPossiblePiece)posPiecesIter.next();
            posPiece.resetScore();
            posPiece.clearBiggestThreats();
        }
        posPiecesIter = ourPlayerTracker.getPossibleRoads().values().iterator();
        while (posPiecesIter.hasNext()) {
            posPiece = (SOCPossiblePiece)posPiecesIter.next();
            posPiece.resetScore();
            posPiece.clearBiggestThreats();
        }

        switch (strategy) {
        case SMART_STRATEGY:
            smartGameStrategy(currentBuildingETAs);
            break;

        case FAST_STRATEGY:            
            SOCBuildPlanStack newBuildingPlan;
            if (brain.isRobotType(StacRobotType.OLD_FAST_GAME_STRATEGY)) {
                newBuildingPlan = dumbFastGameStrategyOld(currentBuildingETAs);
            } else if (brain.isRobotType(StacRobotType.CHOOSE_BEST_MINUS_N_BUILD_PLAN)) {
                int n = (Integer) brain.getRobotType().getTypeParam(StacRobotType.CHOOSE_BEST_MINUS_N_BUILD_PLAN);
                newBuildingPlan = dumbFastGameStrategyNBest(currentBuildingETAs, n);
            } else if (brain.isRobotType(StacRobotType.TRY_N_BEST_BUILD_PLANS)) {
                int n = brain.nOfPossibleBuildPlanToTry; //(Integer) brain.getTypeParam(StacRobotType.TRY_N_BEST_BUILD_PLANS);
                newBuildingPlan = dumbFastGameStrategyNBest(currentBuildingETAs, n);
            } else {
//                //THIS IS USEFUL FOR DEBUGGING
//                long start = System.nanoTime();
//                SOCBuildPlanStack newBuildingPlanOld = dumbFastGameStrategyOld(currentBuildingETAs);
//                long end = System.nanoTime();
//                System.err.println("Old Execution time: " + (end - start));
//                start = System.nanoTime();
//                SOCBuildPlanStack newBuildingPlanNew = dumbFastGameStrategy(currentBuildingETAs);
//                end = System.nanoTime();
//                System.err.println("New Execution time: " + (end - start));
//                if (newBuildingPlanNew != null && newBuildingPlanOld != null) {
//                    if (newBuildingPlanNew.getPlanDepth() < 1 && newBuildingPlanOld.getPlanDepth() < 1) {
//                        System.err.println("Both plans are of 0 Length!");
//                    } else if (newBuildingPlanNew.getPlanDepth() < 1) {
//                        System.err.println("New Plan is of 0 Length!\n\tOld Plan: " + newBuildingPlanOld);
//                    } else if (newBuildingPlanOld.getPlanDepth() < 1) {
//                        System.err.println("Old Plan is of 0 Length!\n\tNew Plan " + newBuildingPlanNew);
//                    //} else if (!newBuildingPlanOld.equals(newBuildingPlanNew)) {
//                    } else if (newBuildingPlanNew.getPlannedPiece(0).getType() != newBuildingPlanOld.getPlannedPiece(0).getType() || 
//                            newBuildingPlanNew.getPlannedPiece(0).getCoordinates() != newBuildingPlanOld.getPlannedPiece(0).getCoordinates() ||
//                            newBuildingPlanNew.getPlanDepth() != newBuildingPlanOld.getPlanDepth() ||
//                            newBuildingPlanNew.getPlannedPiece(0).getETA() != newBuildingPlanOld.getPlannedPiece(0).getETA()) {
//                            System.err.println("\n==========\n" + player.getName() + " - build plans are not equal! Victory Points: " + player.getTotalVP() + 
//                                    "\nNew ETA: " + newBuildingPlanNew.getPlannedPiece(0).getETA() + " - " + newBuildingPlanNew + 
//                                    "\nOld ETA: " + newBuildingPlanOld.getPlannedPiece(0).getETA() + " - " + newBuildingPlanOld +
//                                    "\nCurrent build plans: " + brain.getMemory().getBuildPlans());
//                    }                    
//                }
//                newBuildingPlan = newBuildingPlanNew;
                newBuildingPlan = dumbFastGameStrategy(currentBuildingETAs);
            }
            
            // buildingPlan = newBuildingPlan; //this doesn't work, presumably because it's not working on the same object that is referenced by the brain
            for (SOCPossiblePiece piece : newBuildingPlan) {
                buildingPlan.push(piece);
            }

            //print for debugging if it's our turn
            if (D.ebugOn) {
                if (brain.getGame().getCurrentPlayerNumber() == player.getPlayerNumber()) {
                    D.ebugPrintINFO("PLAN - " + player.getName() + " - ");
                    if (buildingPlan != null && buildingPlan.getPlanDepth() > 0) {
                        SOCPossiblePiece piece = buildingPlan.getPlannedPiece(0);
                        SOCBuildingSpeedEstimate estimate = brain.getEstimator(player.getNumbers());

            //                                        int batna = getETAToTargetResources(ourPlayerData, targetResources, oldOffer.getGiveSet(), oldOffer.getGetSet(), estimate);
                        SOCResourceSet ourResourcesCopy = player.getResources().copy();
                        SOCResourceSet targetResources = new SOCResourceSet();
                        int numToConsider = buildingPlan.getPlanDepth();
                        for (int i=0; i<numToConsider; i++) {
                            SOCPossiblePiece p = buildingPlan.getPlannedPiece(i);
                            targetResources.add(SOCPlayingPiece.getResourcesToBuild(p.getType()));
                        }
                        SOCResSetBuildTimePair offerBuildingTimePair = new SOCResSetBuildTimePair(targetResources, 1000);
                        try {
                            offerBuildingTimePair = estimate.calculateRollsFast(ourResourcesCopy, targetResources, 1000, player.getPortFlags());
                        } catch (CutoffExceededException ex) {
                            Logger.getLogger(SOCRobotBrain.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        int offerBuildingTime = offerBuildingTimePair.getRolls();

                        D.ebugPrintINFO(piece + " - ETB: " + piece.getETA() + " - BATNA: " + offerBuildingTime);
                    }
                }
            }
            
            //if we are of specific type announce current build plan here as we changed it
            if(brain.isRobotType(StacRobotType.SHARE_BUILD_PLAN_CHANGE)){
            	List<String> ret = brain.dialogueManager.announceBuildPlan();
                for (String msg : ret) {
                    brain.sendText(msg);
                }
            }
            
            return;
        }


        ///
        /// if we have a road building card, make sure 
        /// we build two roads first
        ///
        if ((strategy == SMART_STRATEGY) &&
                !player.hasPlayedDevCard() &&
                player.getNumPieces(SOCPlayingPiece.ROAD) >= 2 &&
                player.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.ROADS) > 0) {
            SOCPossibleRoad secondFavoriteRoad = null;
            Enumeration threatenedRoadEnum;
            Enumeration goodRoadEnum;
            D.ebugPrintlnINFO("*** making a plan for road building");

            ///
            /// we need to pick two roads
            ///
            if (favoriteRoad != null) {
                //
                //  pretend to put the favorite road down, 
                //  and then score the new pos roads
                //
                SOCRoad tmpRoad = new SOCRoad(player, favoriteRoad.getCoordinates(), null);

                HashMap trackersCopy = SOCPlayerTracker.tryPutPiece(tmpRoad, brain.getGame(), playerTrackers);
                SOCPlayerTracker.updateWinGameETAs(trackersCopy);

                SOCPlayerTracker ourPlayerTrackerCopy = (SOCPlayerTracker)trackersCopy.get(Integer.valueOf(player.getPlayerNumber()));

                int ourCurrentWGETACopy = ourPlayerTrackerCopy.getWinGameETA();
                D.ebugPrintlnINFO("ourCurrentWGETACopy = "+ourCurrentWGETACopy);

                int leadersCurrentWGETACopy = ourCurrentWGETACopy;
                Iterator trackersCopyIter = trackersCopy.values().iterator();
                while (trackersCopyIter.hasNext()) {
                    SOCPlayerTracker tracker = (SOCPlayerTracker)trackersCopyIter.next();
                    int wgeta = tracker.getWinGameETA();
                    if (wgeta < leadersCurrentWGETACopy) {
                        leadersCurrentWGETACopy = wgeta;
                    }
                }

                Enumeration newPosEnum = favoriteRoad.getNewPossibilities().elements();
                while (newPosEnum.hasMoreElements()) {
                    SOCPossiblePiece newPos = (SOCPossiblePiece)newPosEnum.nextElement();
                    if (newPos.getType() == SOCPossiblePiece.ROAD) {
                        newPos.resetScore();
                        // float wgetaScore = getWinGameETABonusForRoad((SOCPossibleRoad)newPos, currentBuildingETAs[SOCBuildingSpeedEstimate.ROAD], leadersCurrentWGETACopy, trackersCopy);


                        D.ebugPrintlnINFO("$$$ new pos road at "+Integer.toHexString(newPos.getCoordinates())+" has a score of "+newPos.getScore());

                        if (favoriteRoad.getCoordinates() != newPos.getCoordinates()) {
                            if (secondFavoriteRoad == null) {
                                secondFavoriteRoad = (SOCPossibleRoad)newPos;
                            } else {
                                if (newPos.getScore() > secondFavoriteRoad.getScore()) {
                                    secondFavoriteRoad = (SOCPossibleRoad)newPos;
                                }
                            }
                        }
                    }
                }

                threatenedRoadEnum = threatenedRoads.elements();
                while (threatenedRoadEnum.hasMoreElements()) {
                    SOCPossibleRoad threatenedRoad = (SOCPossibleRoad)threatenedRoadEnum.nextElement();
                    D.ebugPrintlnINFO("$$$ threatened road at "+Integer.toHexString(threatenedRoad.getCoordinates()));

                    //
                    // see how building this piece impacts our winETA
                    //
                    threatenedRoad.resetScore();
                    // float wgetaScore = getWinGameETABonusForRoad(threatenedRoad, currentBuildingETAs[SOCBuildingSpeedEstimate.ROAD], leadersCurrentWGETA, playerTrackers);

                    D.ebugPrintlnINFO("$$$  final score = "+threatenedRoad.getScore());

                    if (favoriteRoad.getCoordinates() != threatenedRoad.getCoordinates()) {
                        if (secondFavoriteRoad == null) {
                            secondFavoriteRoad = threatenedRoad;
                        } else {
                            if (threatenedRoad.getScore() > secondFavoriteRoad.getScore()) {
                                secondFavoriteRoad = threatenedRoad;
                            }
                        }
                    }
                }
                goodRoadEnum = goodRoads.elements();
                while (goodRoadEnum.hasMoreElements()) {
                    SOCPossibleRoad goodRoad = (SOCPossibleRoad)goodRoadEnum.nextElement();
                    D.ebugPrintlnINFO("$$$ good road at "+Integer.toHexString(goodRoad.getCoordinates()));
                    //
                    // see how building this piece impacts our winETA
                    //
                    goodRoad.resetScore();
                    // float wgetaScore = getWinGameETABonusForRoad(goodRoad, currentBuildingETAs[SOCBuildingSpeedEstimate.ROAD], leadersCurrentWGETA, playerTrackers);

                    D.ebugPrintlnINFO("$$$  final score = "+goodRoad.getScore());

                    if (favoriteRoad.getCoordinates() != goodRoad.getCoordinates()) {
                        if (secondFavoriteRoad == null) {
                            secondFavoriteRoad = goodRoad;
                        } else {
                            if (goodRoad.getScore() > secondFavoriteRoad.getScore()) {
                                secondFavoriteRoad = goodRoad;
                            }
                        }
                    }
                }

                SOCPlayerTracker.undoTryPutPiece(tmpRoad, brain.getGame());

                if (!buildingPlan.empty()) {
                    SOCPossiblePiece planPeek = (SOCPossiblePiece)buildingPlan.peek();
                    if ((planPeek == null) ||
                            (planPeek.getType() != SOCPlayingPiece.ROAD)) {
                        if (secondFavoriteRoad != null) {
                            D.ebugPrintlnINFO("### SECOND FAVORITE ROAD IS AT "+Integer.toHexString(secondFavoriteRoad.getCoordinates()));
                            D.ebugPrintlnINFO("###   WITH A SCORE OF "+secondFavoriteRoad.getScore());
                            D.ebugPrintlnINFO("$ PUSHING "+secondFavoriteRoad);
                            buildingPlan.push(secondFavoriteRoad);
                            D.ebugPrintlnINFO("$ PUSHING "+favoriteRoad);
                            buildingPlan.push(favoriteRoad);
                        }
                    } else if (secondFavoriteRoad != null) {
                        SOCPossiblePiece tmp = (SOCPossiblePiece)buildingPlan.pop();
                        D.ebugPrintlnINFO("$ POPPED OFF");
                        D.ebugPrintlnINFO("### SECOND FAVORITE ROAD IS AT "+Integer.toHexString(secondFavoriteRoad.getCoordinates()));
                        D.ebugPrintlnINFO("###   WITH A SCORE OF "+secondFavoriteRoad.getScore());
                        D.ebugPrintlnINFO("$ PUSHING "+secondFavoriteRoad);
                        buildingPlan.push(secondFavoriteRoad);
                        D.ebugPrintlnINFO("$ PUSHING "+tmp);
                        buildingPlan.push(tmp);
                    }
                }     
            } 
        } 
        //long endTime = System.currentTimeMillis();
        //System.out.println("plan time: "+(endTime-startTime));
        
        //if we are of specific type announce current build plan here as we changed it
        if(brain.isRobotType(StacRobotType.SHARE_BUILD_PLAN_CHANGE)){
        	List<String> ret = brain.dialogueManager.announceBuildPlan();
            for (String msg : ret) {
                brain.sendText(msg);
            }
        }
        
    }

    /**
     * Inform the logger about the selected build plan.
     * @param buildPlanType The StacPossibleBuildPlan type of the chosen build plan.
     */
    private void logBuildPlanChoice(int buildPlanType) {
        String s = "LOGGING:BUILD_PLAN:type=" + buildPlanType;
//        if(brain.getPlayerNumber()==0){
//        	System.out.println("Chosen build plan type= " + buildPlanType + " for player " + brain.getPlayerNumber());
//        	ArrayList<StacPossibleBuildPlan> bps = brain.getMemory().getBuildPlans();
//        	for(StacPossibleBuildPlan b : bps){
//        		System.out.println("Build Plan " + b.toString());
//        	}
//        	System.out.println(brain.getMemory().getOpponentResources(0).toString());
//        	System.out.println("Knights played:" + brain.getMemory().getPlayer(0).getNumKnights());
//        }
        brain.sendText(s);        
    }
    
    /**
     * Collect the possible build plans in the declarative memory.
     * Each entry also stores: 
     * - speed estimate (time until they can be realised) and
     * - speedup estimate (estimated speedup to win the game) if available,
     * - estimated progress (deltaWGETA; estimated progress towards the winning condition, i.e. getting 10 VPs).
     * The logic is taken from {@link dumbFastGameStrategy(int[] buildingETAs)}.
     * Build plans are generated for:
     * - Cities
     * - Settlements
     * - Development Card
     * - Largest Army
     * - Longest Road
     * Building roads is not considered as such but only as part of Longest Road or building settlements.
     * MG: This method is very long now, so it could be split up into separate methods for each piece type, 
     * but the computations for the different pieces all form pretty self-contained sections, so it seems fine for now.
     * @param buildingETAs 
     * @author Markus Guhe
     */
    private void generatePossibleBuildPlans(int[] buildingETAs) {
        long start = System.nanoTime();

        // Get our building speed estimate
        SOCBuildingSpeedEstimate ourBSE = brain.getEstimator(player.getNumbers());

        // Clear the build plans from memory
        brain.getMemory().forgetAllBuildPlans();
        
        // Check if we're an agent that uses deltaWinGameETA (ETW) for ranking BPs
        boolean generateDeltaWinGameETAs = brain.isRobotType(StacRobotType.RANK_BPS_TRADE_OFF_EP_FACTOR);
        
        // Values used for computing deltaWinGameETA (EP: Estimated Progress)
        int ourCurrentWGETA = ourPlayerTracker.getWinGameETA();
        SOCBoard board = brain.getGame().getBoard();

        // The currentWinGameETA may not have been recalculated (after the initial setup phase)
        if (generateDeltaWinGameETAs) {
            if (ourCurrentWGETA == Integer.MAX_VALUE) { 
                ourPlayerTracker.recalcWinGameETA();
                ourCurrentWGETA = ourPlayerTracker.getWinGameETA();
                // If we still don't get a sensible value, don't generate the deltaWinGameETA
                if (ourCurrentWGETA == Integer.MAX_VALUE) {
                    generateDeltaWinGameETAs = false;
                }            
            }
        }
        
        // CITIES
        // Check that there are still city pieces available
        if (player.getNumPieces(SOCPlayingPiece.CITY) > 0) {
            Iterator possibleCitiesIter = ourPlayerTracker.getPossibleCities().values().iterator();
            while (possibleCitiesIter.hasNext()) {
                SOCPossibleCity posCity = (SOCPossibleCity)possibleCitiesIter.next();

                // Compute the time it takes to build this city
                int buildingSpeedEstimate = buildingETAs[SOCBuildingSpeedEstimate.CITY]; // ETA is not set for cities ... posCity.getETA();

                // If specified, boost (i.e. reduce) the ETA by the specified parameter
                if (brain.isRobotType(StacRobotType.FAVOUR_CITIES)) {
                    double favouringCitiesFactor = (Double)brain.getTypeParam(StacRobotType.FAVOUR_CITIES);
                    double newETA = buildingSpeedEstimate - (favouringCitiesFactor * buildingSpeedEstimate);
                    buildingSpeedEstimate = (int)newETA;
                    if (buildingSpeedEstimate < 0) {
                        buildingSpeedEstimate = 0;
                    }
                }

                // Get the expected total speedup
                int speedupEstimate = posCity.getSpeedupTotal();

                // Calculate the deltaWinGameETA
                int deltaWGETA = 0;
                if (generateDeltaWinGameETAs) {
                    SOCCity tmpCity = new SOCCity(player, posCity.getCoordinates(), board);
                    HashMap trackersCopy = SOCPlayerTracker.tryPutPiece(tmpCity, brain.getGame(), playerTrackers);
                    //doing this instead of just updating it for our own player tracker (ourPlayerTrackerCopy.recalcWinGameETA();) 
                    //is computationally costly but give slightly different values (should be slightly more accurate)
                    SOCPlayerTracker.updateWinGameETAs(trackersCopy); 
                    SOCPlayerTracker ourPlayerTrackerCopy = (SOCPlayerTracker)trackersCopy.get(Integer.valueOf(player.getPlayerNumber()));
                    int ourCurrentWGETACopy = ourPlayerTrackerCopy.getWinGameETA();
                    SOCPlayerTracker.undoTryPutPiece(tmpCity, brain.getGame());
                    deltaWGETA = ourCurrentWGETA - ourCurrentWGETACopy;
                }
                
                // Create and store the build plan
                SOCBuildPlanStack possibleBuildPlan = new SOCBuildPlanStack();
                possibleBuildPlan.push(posCity);
                brain.getMemory().rememberBuildPlan(StacPossibleBuildPlan.CITY, possibleBuildPlan, buildingSpeedEstimate, speedupEstimate, deltaWGETA);
            }
        }
        
        // SETTLEMENTS
        // Check that there are still settlement pieces available
        if (player.getNumPieces(SOCPlayingPiece.SETTLEMENT) > 0) {
            // Update the scoring for our potential settlements
            scoreSettlementsForDumb(buildingETAs[SOCBuildingSpeedEstimate.SETTLEMENT], ourBSE);

            // Generate the build plans for settlements
            Iterator possibleSettlementsIter = ourPlayerTracker.getPossibleSettlements().values().iterator();
            while (possibleSettlementsIter.hasNext()) {
                SOCPossibleSettlement posSettlement = (SOCPossibleSettlement)possibleSettlementsIter.next();

                // Get the estimated building speed
                int buildingSpeedEstimate = posSettlement.getETA(); // buildingETAs[SOCBuildingSpeedEstimate.SETTLEMENT];

                // If specified, boost (i.e. reduce) the ETA by the specified parameter
                if (brain.isRobotType(StacRobotType.FAVOUR_SETTLEMENTS)) {
                    double favouringSettlementsFactor = (Double)brain.getTypeParam(StacRobotType.FAVOUR_SETTLEMENTS);
                    double newBestETA = buildingSpeedEstimate - (favouringSettlementsFactor * buildingSpeedEstimate);
                    buildingSpeedEstimate = (int)newBestETA;
                    if (buildingSpeedEstimate < 0) {
                        buildingSpeedEstimate = 0;
                    }
                }

                // Get the expected total speedup
                int speedupEstimate = posSettlement.getSpeedupTotal(); //getSpeedupTotal() is not implemented and always returns 0

                // Calculate the deltaWinGameETA
                int deltaWGETA = 0;
                if (generateDeltaWinGameETAs) {
                    SOCSettlement tmpSettlement = new SOCSettlement(player, posSettlement.getCoordinates(), board);
                    HashMap trackersCopy = SOCPlayerTracker.tryPutPiece(tmpSettlement, brain.getGame(), playerTrackers);
                    SOCPlayerTracker ourPlayerTrackerCopy = (SOCPlayerTracker)trackersCopy.get(Integer.valueOf(player.getPlayerNumber()));
                    //doing this instead of just updating it for our own player tracker (ourPlayerTrackerCopy.recalcWinGameETA();) 
                    //is computationally costly but give slightly different values (should be slightly more accurate)
                    SOCPlayerTracker.updateWinGameETAs(trackersCopy);
                    int ourCurrentWGETACopy = ourPlayerTrackerCopy.getWinGameETA();
                    SOCPlayerTracker.undoTryPutPiece(tmpSettlement, brain.getGame());
                    deltaWGETA = ourCurrentWGETA - ourCurrentWGETACopy;
                }

                // Generate the build plan, including needed roads, and store it
                SOCBuildPlanStack possibleBuildPlan = new SOCBuildPlanStack();
                possibleBuildPlan.push(posSettlement);
                // Add the required roads we need to build first
                if (!posSettlement.getNecessaryRoads().isEmpty()) {
                    Stack roadPath = posSettlement.getRoadPath();
                    while (!roadPath.empty()) {
                        SOCPossibleRoad pr = (SOCPossibleRoad)roadPath.pop();
                        possibleBuildPlan.push(pr);
                    }
                }

                // Only accept the plan if we have the required road pieces to build it
                if(posSettlement.getNecessaryRoads().isEmpty() || posSettlement.getRoadPath().size() <=  player.getNumPieces(SOCPlayingPiece.ROAD))
                    brain.getMemory().rememberBuildPlan(StacPossibleBuildPlan.SETTLEMENT, possibleBuildPlan, buildingSpeedEstimate, speedupEstimate, deltaWGETA);
            }
        }
        
        // DEVELOPMENT CARD
        if (player.getGame().getNumDevCards() > 0) {
            int buildingSpeedEstimate = buildingETAs[SOCBuildingSpeedEstimate.CARD];
            if (brain.isRobotType(StacRobotType.FAVOUR_DEV_CARDS)) {
                double favouringDevCardsFactor = (Double)brain.getTypeParam(StacRobotType.FAVOUR_DEV_CARDS);
                double newETA = buildingSpeedEstimate - (favouringDevCardsFactor * buildingSpeedEstimate);
                buildingSpeedEstimate = (int)newETA;
                if (buildingSpeedEstimate < 0) {
                    buildingSpeedEstimate = 0;
                }
            }
            SOCPossibleCard pCard = new SOCPossibleCard(player, buildingSpeedEstimate);
            SOCBuildPlanStack possibleCardBuildPlan = new SOCBuildPlanStack();
            possibleCardBuildPlan.push(pCard);
            
            // There is no speedupEstimate for cards, but the ES_CARDS parameter can set one
            int speedupEstimate = 0;
            if (brain.isRobotType(StacRobotType.ES_CARDS)) {
                speedupEstimate = (Integer)brain.getTypeParam(StacRobotType.ES_CARDS);
            }
            
            // Calculate the deltaWinGameETA
            int deltaWinGameETA = 0;
            if (generateDeltaWinGameETAs) {
                HashMap trackersCopy = SOCPlayerTracker.copyPlayerTrackers(playerTrackers);
                SOCPlayerTracker ourPlayerTrackerCopy = (SOCPlayerTracker)trackersCopy.get(Integer.valueOf(player.getPlayerNumber()));
                SOCPlayer playerCopy = new SOCPlayer(ourPlayerTrackerCopy.getPlayer());

                // There are 5 types of cards: 14 Knight, 2 Road Building, 2 Monopoly, 2 Year of Plenty, 5 VP cards
                int[] deltaWinGameETAs = new int[5]; // array with the EP for the different dev card types
                
                //KNIGHTS
                // assume we got a knight card
                playerCopy.setNumKnights(playerCopy.getNumKnights()+1);
                ourPlayerTrackerCopy.setPlayer(playerCopy);
                ourPlayerTrackerCopy.recalcLargestArmyETA();
                ourPlayerTrackerCopy.recalcWinGameETA();
                // compute the EP and store it in the array
                int ourCurrentWGETACopy = ourPlayerTrackerCopy.getWinGameETA();
                deltaWinGameETA = ourCurrentWGETA - ourCurrentWGETACopy;
                deltaWinGameETAs[0] = deltaWinGameETA;
                // 'put back' the knight card
                playerCopy.setNumKnights(playerCopy.getNumKnights()-1);

                //PROGRESS CARDS & ONE VP CARD
                // we already did knights, so we're doing the others now
                SOCDevCardSet dc = playerCopy.getDevCards();
                for (int c = SOCDevCardConstants.ROADS; c <= SOCDevCardConstants.CAP; c++) {
                    dc.setAmount(dc.getAmount(SOCDevCardSet.NEW, c)+1, SOCDevCardSet.NEW, c);
                    ourPlayerTrackerCopy.recalculateAllEtas();
                    ourCurrentWGETACopy = ourPlayerTrackerCopy.getWinGameETA();
                    int deltaWinGameETA_Progress = ourCurrentWGETA - ourCurrentWGETACopy;
                    deltaWinGameETAs[c] = deltaWinGameETA_Progress;
                    dc.setAmount(dc.getAmount(SOCDevCardSet.NEW, c)-1, SOCDevCardSet.NEW, c);
                }

//                D.ebugPrintlnINFO(brain.getPlayerName() + " EP(Knights)=" + deltaWinGameETAs[0] + 
//                        ", EP(Road Building)=" + deltaWinGameETAs[1] +
//                        ", EP(Monopoly)=" + deltaWinGameETAs[2] +
//                        ", EP(Year of Plenty)=" + deltaWinGameETAs[3] + 
//                        ", EP(VP card)=" + deltaWinGameETAs[4]);

                // Combine the EPs into a combined measure, simply using the cards' base frequenies
                deltaWinGameETA = (int)((double)(
                            deltaWinGameETAs[0] * 14 +
                            deltaWinGameETAs[1] * 2 + deltaWinGameETAs[2] * 2 + deltaWinGameETAs[3] * 2 +
                            deltaWinGameETAs[4] * 5) 
                        / (double) 25);
                
                D.ebugPrintlnINFO(brain.getPlayerName() + " for " + player.getName() + ": " + deltaWinGameETA + " -- " + deltaWinGameETAs[0] + "|" + 
                        deltaWinGameETAs[1] + "|" + deltaWinGameETAs[2] + "|" + deltaWinGameETAs[3] + "|" + 
                        deltaWinGameETAs[4]);
            }
            
            brain.getMemory().rememberBuildPlan(StacPossibleBuildPlan.CARD, possibleCardBuildPlan, buildingSpeedEstimate, speedupEstimate, deltaWinGameETA); 
        }
        
        // LARGEST ARMY
        int minVPtoTryLA = 5;
        if (brain.isRobotType(StacRobotType.MIN_VP_TO_TRY_LA)) {
            minVPtoTryLA = (Integer)brain.getTypeParam(StacRobotType.MIN_VP_TO_TRY_LA);
        }
        if (player.getTotalVP() >= minVPtoTryLA) {
            if (player.getGame().getNumDevCards() > 0) {
                D.ebugPrintlnINFO("Calculating Largest Army ETA");
                int laETA = 500;
                int speedupEstimate = 0; //LA doesn't speed up the game, but we can set a constant value with ES_LA
                if (brain.isRobotType(StacRobotType.ES_LA)) {
                    speedupEstimate = (Integer)brain.getTypeParam(StacRobotType.ES_LA);
                }
                int knightsToBuy = 0;
                int laSize = 0;
                SOCPlayer laPlayer = brain.getGame().getPlayerWithLargestArmy();
                if (laPlayer == null) { // no one has largest army
                    laSize = 3;
                } else if (laPlayer.getPlayerNumber() == player.getPlayerNumber()) { // we have largest army
                    D.ebugPrintlnINFO("We have largest army");
                } else {
                    laSize = laPlayer.getNumKnights() + 1;
                }
                // How many knights do we need to buy?
                if ((player.getNumKnights() + 
                        player.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT) +
                        player.getDevCards().getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.KNIGHT))
                        < laSize) {
                    knightsToBuy = laSize - (player.getNumKnights() + player.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT));
                }
                D.ebugPrintlnINFO("knightsToBuy = "+knightsToBuy);
                if (player.getGame().getNumDevCards() >= knightsToBuy) {      
                    // Figure out how long it takes to buy this many knights
                    SOCResourceSet targetResources = new SOCResourceSet();
                    for (int i = 0; i < knightsToBuy; i++) {
                        targetResources.add(SOCGame.CARD_SET);
                    }
                    try {
                        SOCResSetBuildTimePair timePair = ourBSE.calculateRollsFast(player.getResources(), targetResources, 100, player.getPortFlags());
                        laETA = timePair.getRolls();
                        laETA += 4; //we can't play the card immediately but have to wait a round!
                        
                        //adjust with favouring factor, if spcified
                        if (brain.isRobotType(StacRobotType.FAVOUR_LA)) {
                            double favouringLAFactor = (Double)brain.getTypeParam(StacRobotType.FAVOUR_LA);
                            double newETA = laETA - (favouringLAFactor * laETA);
                            laETA = (int)newETA;
                            if (laETA < 0) {
                                laETA = 0;
                            }
                        }
                    } catch (CutoffExceededException ex) {
                        laETA = 100;
                    }
                } else {
                    // Not enough dev cards left
                }
                D.ebugPrintlnINFO("laETA = "+laETA);
                // Add the build plan to the declarative memory
                SOCBuildPlanStack laBuildPlan = new SOCBuildPlanStack();
                for (int i = 0; i < knightsToBuy; i++) {
                    SOCPossibleCard posCard = new SOCPossibleCard(player, 1);
                    laBuildPlan.push(posCard);
                }
                
                // ALTERNATIVE SIMPLE COMPUTATON OF THE deltaWinGameETA
//                // Compute the deltaWinGameETA
//                int deltaWinGameETA = 0;
//                int wgETA = ourPlayerTracker.getWinGameETA();
//                int currentVP = brain.getPlayerData().getTotalVP();
//                if (currentVP >= 8) {
//                    // With 2 more VPs we win
//                    deltaWinGameETA = 100;
//                } else {
//                    int newVP = currentVP + 2;
//                    double rollsNeededFor1VP = (double) wgETA / (10 - currentVP); //rounds needed to win per VP
//                    double deltaWinGameETA_D = (10 - newVP) * rollsNeededFor1VP;
//                    deltaWinGameETA = (int)deltaWinGameETA_D;
//                }

                // Compute the deltaWinGameETA
                int deltaWinGameETA = 0;
                if (generateDeltaWinGameETAs && laBuildPlan.size() > 0) {
                    HashMap trackersCopy = SOCPlayerTracker.copyPlayerTrackers(playerTrackers);
                    SOCPlayerTracker ourPlayerTrackerCopy = (SOCPlayerTracker)trackersCopy.get(Integer.valueOf(player.getPlayerNumber()));
                    SOCPlayer playerCopy = new SOCPlayer(ourPlayerTrackerCopy.getPlayer());
                    playerCopy.setNumKnights(playerCopy.getNumKnights()+knightsToBuy);

                    //ALTERNATIVE
                    //SOCPlayer playerWithLargestArmy = brain.getGame().getPlayerWithLargestArmy();
                    //brain.getGame().setPlayerWithLargestArmy(ourPlayerTracker.getPlayer());
                    ourPlayerTrackerCopy.setPlayer(playerCopy);
                    
                    ourPlayerTrackerCopy.recalcLargestArmyETA();
                    ourPlayerTrackerCopy.recalcWinGameETA();
                    int ourCurrentWGETACopy = ourPlayerTrackerCopy.getWinGameETA();
                    deltaWinGameETA = ourCurrentWGETA - ourCurrentWGETACopy;
                    
                    //FOR ALTERNATIVE - set game back to original state
                    //brain.getGame().setPlayerWithLargestArmy(playerWithLargestArmy);
                    
                    D.ebugPrintlnINFO(brain.getPlayerName() + " EP(LA) " + ourCurrentWGETA + " - " + ourCurrentWGETACopy + " = " + deltaWinGameETA);
                }
                
                brain.getMemory().rememberBuildPlan(StacPossibleBuildPlan.LARGEST_ARMY, laBuildPlan, laETA, speedupEstimate, deltaWinGameETA);
            }
        }

        // LONGEST ROAD
        int minVPtoTryLR = 5;
        if (brain.isRobotType(StacRobotType.MIN_VP_TO_TRY_LR)) {
            minVPtoTryLR = (Integer)brain.getTypeParam(StacRobotType.MIN_VP_TO_TRY_LR);
        }
        if (player.getTotalVP() >= minVPtoTryLR) {
            if (player.getNumPieces(SOCPlayingPiece.ROAD) > 0) {
                D.ebugPrintlnINFO("Calculating Longest Road ETA");
                int lrETA = 500;
                int speedupEstimate = 0; //LR doesn't speed up the game; NB: the old value here was 500
                if (brain.isRobotType(StacRobotType.ES_LR)) {
                    speedupEstimate = (Integer)brain.getTypeParam(StacRobotType.ES_LR);
                }
                Stack bestLRPath = null;
                int lrLength;
                SOCPlayer lrPlayer = brain.getGame().getPlayerWithLongestRoad();
                if ((lrPlayer != null) && (lrPlayer.getPlayerNumber() == player.getPlayerNumber())) { // we have longest road
                    D.ebugPrintlnINFO("We have longest road");
                } else {
                    if (lrPlayer == null) { // no one has longest road
                        lrLength = Math.max(4, player.getLongestRoadLength());
                    } else {
                        lrLength = lrPlayer.getLongestRoadLength();
                    }
                    Iterator lrPathsIter = player.getLRPaths().iterator();
                    int depth;
                    while (lrPathsIter.hasNext()) {
                        Stack path;
                        SOCLRPathData pathData = (SOCLRPathData)lrPathsIter.next();
                        depth = Math.min(((lrLength + 1) - pathData.getLength()), player.getNumPieces(SOCPlayingPiece.ROAD));
                        path = (Stack) recalcLongestRoadETAAux(player, true, pathData.getBeginning(), pathData.getLength(), lrLength, depth);
                        if ((path != null) && ((bestLRPath == null) || (path.size() < bestLRPath.size()))) {
                            bestLRPath = path;
                        }
                        path = (Stack) recalcLongestRoadETAAux(player, true, pathData.getEnd(), pathData.getLength(), lrLength, depth);
                        if ((path != null) && ((bestLRPath == null) || (path.size() < bestLRPath.size()))) {
                            bestLRPath = path;
                        }
                    }
                    if (bestLRPath != null) {
                        // Calculate LR ETA
                        D.ebugPrintlnINFO("Number of roads: "+bestLRPath.size());
                        SOCResourceSet targetResources = new SOCResourceSet();
                        for (int i = 0; i < bestLRPath.size(); i++) {
                            targetResources.add(SOCGame.ROAD_SET);
                        }
                        try {
                            SOCResSetBuildTimePair timePair = ourBSE.calculateRollsFast(player.getResources(), targetResources, 100, player.getPortFlags());
                            lrETA = timePair.getRolls();
                            
                            //adjust with favouring factor, if spcified
                            if (brain.isRobotType(StacRobotType.FAVOUR_LR)) {
                                double favouringLAFactor = (Double)brain.getTypeParam(StacRobotType.FAVOUR_LR);
                                double newETA = lrETA - (favouringLAFactor * lrETA);
                                lrETA = (int)newETA;
                                if (lrETA < 0) {
                                    lrETA = 0;
                                }
                            }
                            
//                            //compute the estimated speedup
//                            int wgETA = ourPlayerTracker.getWinGameETA();
//                            int currentVP = brain.getPlayerData().getTotalVP();
//                            if (currentVP >= 8) {
//                                speedupEstimate = 500;
//                            } else {
//                                int newVP = currentVP + 2;
//                                double roundsNeededFor1VP = (double) wgETA / (10 - currentVP); //rounds needed to win per VP
//                                double speedupEstimateD = (10 - newVP) * roundsNeededFor1VP;
//                                speedupEstimate = (int)speedupEstimateD;
//                            }
                        } catch (CutoffExceededException ex) {
                            lrETA = 100;
                        } 
                    }
                }
                D.ebugPrintlnINFO("lrETA = "+lrETA);

                // Calculate the deltaWinGameETA
                
                // Create the build plan stack with the roads needed
                // and put the pieces on the board temporarily to calculate the deltaWinGameETA
                SOCBuildPlanStack lrBuildPlan = new SOCBuildPlanStack();
                int deltaWGETA = 0;
                HashMap trackersCopy = SOCPlayerTracker.copyPlayerTrackers(playerTrackers);
                SOCPlayerTracker ourPlayerTrackerCopy = (SOCPlayerTracker)trackersCopy.get(Integer.valueOf(player.getPlayerNumber()));

                if (bestLRPath != null) {
                    while (!bestLRPath.empty()) {
                        SOCPossibleRoad pr = (SOCPossibleRoad)bestLRPath.pop();
                        D.ebugPrintlnINFO("LR road at "+brain.getGame().getBoard().edgeCoordToString(pr.getCoordinates()));
                        lrBuildPlan.push(pr);
                        
                        // If we're computing the deltaWinGameETA, put the piece on the board
                        // we're just doing this in our copy of the player tracker (we're not using tryPutPiece()), so no need to pick it up again
                        if (generateDeltaWinGameETAs) {
                            int coordinates = pr.getCoordinates();
                            SOCRoad tmpRoad = new SOCRoad(player, coordinates, board);
                            ourPlayerTrackerCopy.addOurNewRoad(tmpRoad, trackersCopy, 0);//addNewRoad(tmpRoad, trackersCopy);
                        }
                    }
                }

                // Calculate the deltaWinGameETA
                if (generateDeltaWinGameETAs && lrBuildPlan.size() > 0) {
                    ourPlayerTrackerCopy.recalcLongestRoadETA();
                    ourPlayerTrackerCopy.recalcWinGameETA();
                    int ourCurrentWGETACopy = ourPlayerTrackerCopy.getWinGameETA();
                    deltaWGETA = ourCurrentWGETA - ourCurrentWGETACopy;
                    D.ebugPrintlnINFO(brain.getPlayerName() + " EP(LR) " + ourCurrentWGETA + " - " + ourCurrentWGETACopy + " = " + deltaWGETA);

                    // ALTERNATIVE: While this just a stupid hack, it should be close to the actual value
                    //deltaWGETA = lrBuildPlan.size() * 8;
                }
                
                // Add build plan to the declarative memeory
                brain.getMemory().rememberBuildPlan(StacPossibleBuildPlan.LONGEST_ROAD, lrBuildPlan, lrETA, speedupEstimate, deltaWGETA);
            }
        }

        long end = System.nanoTime();
        long microseconds = (end - start) / 1000;
        D.ebugPrintlnINFO(brain.getGame().getName(), "generation took: " + microseconds);
    }
    
    /**
     * Determine what to build next.
     * Rewritten version of the original implementation {@link dumbFastGameStrategyOld(int[] buildingETAs)}
     * that uses the potential build plans stored in the {@link StacRobotDeclarativeMemory}.
     * Note that this method takes about twice as long to execute as the old one.
     * @param buildingETAs  the etas for building something
     * @author Markus Guhe
     */
    private SOCBuildPlanStack dumbFastGameStrategy(int[] buildingETAs) {
        // Generate all the build plans that we could execute now; they are stored in the declarative memory
        generatePossibleBuildPlans(buildingETAs);
        ArrayList<StacPossibleBuildPlan> possibleBuildPlans = brain.getMemory().getBuildPlans();

        // Make sure there is at least one build plan
        if (possibleBuildPlans.isEmpty()) {
            return new SOCBuildPlanStack();
        }
        
        // Find the best City BP
        StacPossibleBuildPlan bestCity = null;
        for (StacPossibleBuildPlan city : brain.getMemory().getCityBuildPlans()) {
            if (bestCity == null) {
                bestCity = city;
                continue;
            }
            if (city.getSpeedupEstimate() > bestCity.getSpeedupEstimate()) {
                bestCity = city;
            }
        }

        // Find the best Settlement BP
        StacPossibleBuildPlan bestSettlement = null;
        //before we execute any of the following plans we need to check again we have sufficient road pieces
        checkPossiblePlans(brain.getMemory().getSettlementBuildPlans());
        for (StacPossibleBuildPlan stm : brain.getMemory().getSettlementBuildPlans()) {
            if (bestSettlement == null) {
                bestSettlement = stm;
            } else if (stm.getBuildingSpeedEstimate() < bestSettlement.getBuildingSpeedEstimate()) {
                bestSettlement = stm;
            } else if (stm.getBuildingSpeedEstimate() == bestSettlement.getBuildingSpeedEstimate()) {
                if (stm.getSpeedupEstimate() > bestSettlement.getSpeedupEstimate()) {
                    bestSettlement = stm;
                }
            }
        }

        // Select a build plan
        StacPossibleBuildPlan selectedBuildPlan = null;
        
        // Cities
        if (bestCity != null) {
            selectedBuildPlan = bestCity;
        }
        
        // Settlements
        if (bestSettlement != null) {
            if (selectedBuildPlan == null) {
                selectedBuildPlan = bestSettlement;
            } else if (bestSettlement.getBuildingSpeedEstimate() < selectedBuildPlan.getBuildingSpeedEstimate()) {
                selectedBuildPlan = bestSettlement;
            } else if (bestSettlement.getBuildingSpeedEstimate() == selectedBuildPlan.getBuildingSpeedEstimate() && 
                        bestSettlement.getSpeedupEstimate() > selectedBuildPlan.getSpeedupEstimate()) {
                selectedBuildPlan = bestSettlement;
            }
        }

        // Largest Army
        // We're only considering LR & LA after we have the specified minimum number of VPs
        int minVPtoTryLA = 4;
        if (brain.isRobotType(StacRobotType.MIN_VP_TO_TRY_LA)) {
            minVPtoTryLA = (Integer)brain.getTypeParam(StacRobotType.MIN_VP_TO_TRY_LA);
        }
        if (player.getTotalVP() > minVPtoTryLA) {
            StacPossibleBuildPlan largestArmyBP = brain.getMemory().getLargestArmyBuildPlan();
            if (largestArmyBP != null) {
                if (selectedBuildPlan == null) {
                    selectedBuildPlan = largestArmyBP;                    
                } else if (largestArmyBP.getBuildingSpeedEstimate() < selectedBuildPlan.getBuildingSpeedEstimate()) {
                    selectedBuildPlan = largestArmyBP;
                }
            }
        }

        // Longest Road
        int minVPtoTryLR = 4;
        if (brain.isRobotType(StacRobotType.MIN_VP_TO_TRY_LR)) {
            minVPtoTryLR = (Integer)brain.getTypeParam(StacRobotType.MIN_VP_TO_TRY_LR);
        }
        if (player.getTotalVP() > minVPtoTryLR) {
            StacPossibleBuildPlan longestRoadBP = brain.getMemory().getLongestRoadBuildPlan();
            if (longestRoadBP != null) {
                if (selectedBuildPlan == null) {
                    selectedBuildPlan = longestRoadBP;
                } else if (longestRoadBP.getBuildingSpeedEstimate() < selectedBuildPlan.getBuildingSpeedEstimate()) {
                    selectedBuildPlan = longestRoadBP;
                }
            }
        }

        // DEVELOPMENT CARD
        // If we didn't select a build plan up to this point, we'll try to buy a card
        // Only considering dev cards after getting a minimal number of VP does not seem to affect performance but is part of the original method
        if (selectedBuildPlan == null) {
            if ((player.getTotalVP() <= minVPtoTryLR) && (player.getTotalVP() <= minVPtoTryLA)) {
                StacPossibleBuildPlan cardBP = brain.getMemory().getDevCardBuildPlan();
                if (cardBP != null) {
                    selectedBuildPlan = cardBP;
                }
            }
        }

        // Log and return the build plan
        if (selectedBuildPlan != null) {
            //Log the selected build plan
            logBuildPlanChoice(selectedBuildPlan.getType());
            
            return selectedBuildPlan.getBuildPlan();
        }
        return new SOCBuildPlanStack();
    }

    /**
     * Comparator sorting BPs by ETB, ES, EP.
     * Formula: ETB - s * ES - w * EP.
     * @author: Markus Guhe
     */
    final Comparator<StacPossibleBuildPlan> ETB_ES_ETW = 
        new Comparator<StacPossibleBuildPlan>() {
            @Override
            public int compare(StacPossibleBuildPlan bp1, StacPossibleBuildPlan bp2) {
                return (int) ((bp1.buildingSpeedEstimate - 
                                (brain.speedupEstimateDiscountFactor * bp1.speedupEstimate) - 
                                (brain.deltaWinGameETADiscountFactor * bp1.deltaWinGameETA)) - 
                            (bp2.buildingSpeedEstimate - 
                                (brain.speedupEstimateDiscountFactor * bp2.speedupEstimate) - 
                                (brain.deltaWinGameETADiscountFactor * bp2.deltaWinGameETA)));
            }
        };
 
    /**
     * Strategy to choose not the best build plan but the n-best build plan.
     * @param buildingETAs  ETBs for the different types of pieces.
     * @param n             the n for choosing the build plan
     * @return SOCBuildPlanStac object with the chosen build plan.
     */
    protected SOCBuildPlanStack dumbFastGameStrategyNBest(int[] buildingETAs, int n) {

        // Generate all the build plans that we could execute now; they are stored in the declarative memory
        generatePossibleBuildPlans(buildingETAs);
        ArrayList<StacPossibleBuildPlan> possibleBuildPlans = brain.getMemory().getBuildPlans();

        // Make sure there is at least one build plan
        if (possibleBuildPlans.isEmpty()) {
            return new SOCBuildPlanStack();
        }
        
        //check if we can execute all plans
        checkPossiblePlans(possibleBuildPlans);
        
        D.ebugPrintlnINFO(n + " - Unsorted: " + possibleBuildPlans);

        // Sort the possible build plans, using the appropriate soring method
        if (brain.isRobotType(StacRobotType.RANK_BPS_TRADE_OFF_ES_FACTOR) || brain.isRobotType(StacRobotType.RANK_BPS_TRADE_OFF_EP_FACTOR)) {
            Collections.sort(possibleBuildPlans, ETB_ES_ETW);
        } else {
            Collections.sort(possibleBuildPlans);
        }
        D.ebugPrintlnINFO(n + " - Sorted: " + possibleBuildPlans);
        
        // Select the n-best build plan, or the last in the array if there are fewer than n build plans
        StacPossibleBuildPlan selectedBuildPlan = null;
        if (possibleBuildPlans.size() > n) {
            selectedBuildPlan = possibleBuildPlans.get(n);
        } else if (possibleBuildPlans.size() > 0) {
            selectedBuildPlan = possibleBuildPlans.get(possibleBuildPlans.size()-1);
        }

        // Log and return the build plan
        if (selectedBuildPlan != null) {
            logBuildPlanChoice(selectedBuildPlan.getType());
            return selectedBuildPlan.getBuildPlan();
        }
        return new SOCBuildPlanStack();
    }    
    
    /**
     * Checks if the player has sufficient road pieces to execute each of the possible build plan. If not, it removes the ones it cannot from the list
     * @param possibleBuildPlans
     */
    private void checkPossiblePlans(ArrayList<StacPossibleBuildPlan> possibleBuildPlans){
        //before we are sorting we should check that these can be done (i.e. have sufficient road pieces for settlements/cities)
        ArrayList<StacPossibleBuildPlan> plansToDelete = new ArrayList();
        for(StacPossibleBuildPlan pbp : possibleBuildPlans){
        	int nr = 0;
        	SOCBuildPlanStack stack = pbp.getBuildPlan();
        	int depth = stack.getPlanDepth();
        	for(int i = 0; i < depth; i++){
        		if(stack.getPlannedPiece(i).getType() == SOCPossiblePiece.ROAD)
        			nr++;
        	}
        	if(nr > player.getNumPieces(SOCPlayingPiece.ROAD))
        		plansToDelete.add(pbp);        		
        }
        //actually remove them here
        for(StacPossibleBuildPlan pbp : plansToDelete){
        	possibleBuildPlans.remove(pbp);
        }
    }
    
    /**
     * The original JSettlers method for determining what to build next.
     * @param buildingETAs  the etas for building something
     */
    private SOCBuildPlanStack dumbFastGameStrategyOld(int[] buildingETAs)
    {
        D.ebugPrintlnINFO("***** dumbFastGameStrategyOld *****");

        SOCBuildPlanStack newBuildingPlan = new SOCBuildPlanStack();
        
        // If this game is on the 6-player board, check whether we're planning for
        // the Special Building Phase.  Can't buy cards or trade in that phase.
        final boolean forSpecialBuildingPhase =
                brain.getGame().isSpecialBuilding() || (brain.getGame().getCurrentPlayerNumber() != player.getPlayerNumber());

        int bestETA = 500;
        SOCBuildingSpeedEstimate ourBSE = brain.getEstimator(player.getNumbers());

        //set number of VPs after which we're going for LR or LA
        int minVPtoTryLR = 4;
        int minVPtoTryLA = 4;
        if (brain.isRobotType(StacRobotType.MIN_VP_TO_TRY_LR)) {
            minVPtoTryLR = (Integer)brain.getTypeParam(StacRobotType.MIN_VP_TO_TRY_LR);
        }
        if (brain.isRobotType(StacRobotType.MIN_VP_TO_TRY_LA)) {
            minVPtoTryLA = (Integer)brain.getTypeParam(StacRobotType.MIN_VP_TO_TRY_LA);
        }

        // This would be the place to generate all the build plans that this agent could choose now and store them in the declarative memory.
        // But these build plans are not used in this method.
        // Note that the build plans in the declarative memory already have adjusted values for building ETAs depending on the FAVOUR_X parameters.
        //generatePossibleBuildPlans(buildingETAs);
        
        if ((player.getTotalVP() <= minVPtoTryLR) && (player.getTotalVP() <= minVPtoTryLA)) {
            //
            // less than 5 points, don't consider LR or LA
            //

            //
            // score possible cities
            //
            if (player.getNumPieces(SOCPlayingPiece.CITY) > 0) {
                Iterator posCitiesIter = ourPlayerTracker.getPossibleCities().values().iterator();
                while (posCitiesIter.hasNext()) {
                    SOCPossibleCity posCity = (SOCPossibleCity)posCitiesIter.next();
                    D.ebugPrintlnINFO("Estimate speedup of city at "+brain.getGame().getBoard().nodeCoordToString(posCity.getCoordinates()));
                    D.ebugPrintlnINFO("Speedup = "+posCity.getSpeedupTotal());
                    D.ebugPrintlnINFO("ETA = "+buildingETAs[SOCBuildingSpeedEstimate.CITY]);
                    if ((brain != null) && (brain.getDRecorder().isOn())) {
                        brain.getDRecorder().startRecording("CITY"+posCity.getCoordinates());
                        brain.getDRecorder().record("Estimate speedup of city at "+brain.getGame().getBoard().nodeCoordToString(posCity.getCoordinates()));
                        brain.getDRecorder().record("Speedup = "+posCity.getSpeedupTotal());
                        brain.getDRecorder().record("ETA = "+buildingETAs[SOCBuildingSpeedEstimate.CITY]);
                        brain.getDRecorder().stopRecording();
                    }
                    if ((favoriteCity == null) ||
                            (posCity.getSpeedupTotal() > favoriteCity.getSpeedupTotal())) {
                        favoriteCity = posCity;
                        bestETA = buildingETAs[SOCBuildingSpeedEstimate.CITY];
                        //---MG
                        if (brain.isRobotType(StacRobotType.FAVOUR_CITIES)) {
                            //boost (i.e. reduce) the ETA by the specified parameter
                            double factor = (Double)brain.getTypeParam(StacRobotType.FAVOUR_CITIES);
                            double newBestETA = bestETA - (factor * bestETA);
                            bestETA = (int)newBestETA;
                            if (bestETA < 0) {
                                bestETA = 0;
                            }
                        }
                        //---MG end
                    }
                }
            }

            //
            // score the possible settlements
            //
            scoreSettlementsForDumb(buildingETAs[SOCBuildingSpeedEstimate.SETTLEMENT], ourBSE);
            
            //
            // pick something to build
            //
            Iterator posSetsIter = ourPlayerTracker.getPossibleSettlements().values().iterator();
            while (posSetsIter.hasNext()) {
                SOCPossibleSettlement posSet = (SOCPossibleSettlement)posSetsIter.next();
                if ((brain != null) && (brain.getDRecorder().isOn())) {
                    brain.getDRecorder().startRecording("SETTLEMENT"+posSet.getCoordinates());
                    brain.getDRecorder().record("Estimate speedup of stlmt at "+brain.getGame().getBoard().nodeCoordToString(posSet.getCoordinates()));
                    brain.getDRecorder().record("Speedup = "+posSet.getSpeedupTotal());
                    brain.getDRecorder().record("ETA = "+posSet.getETA());
                    Stack roadPath = posSet.getRoadPath();
                    if (roadPath!= null) {
                        brain.getDRecorder().record("Path:");
                        Iterator rpIter = roadPath.iterator();
                        while (rpIter.hasNext()) {
                            SOCPossibleRoad posRoad = (SOCPossibleRoad)rpIter.next();
                            brain.getDRecorder().record("Road at "+brain.getGame().getBoard().edgeCoordToString(posRoad.getCoordinates()));
                        }
                    }
                    brain.getDRecorder().stopRecording();
                }
                int bestSettlementETA = posSet.getETA();
                if (brain.isRobotType(StacRobotType.FAVOUR_SETTLEMENTS)) {
                    //boost (i.e. reduce) the ETA by the specified parameter
                    double factor = (Double)brain.getTypeParam(StacRobotType.FAVOUR_SETTLEMENTS);
                    double newBestETA = bestSettlementETA - (factor * bestSettlementETA);
                    bestSettlementETA = (int)newBestETA;
                    if (bestSettlementETA < 0) {
                        bestSettlementETA = 0;
                    }
                }
                if (bestSettlementETA < bestETA) {
                    bestETA = posSet.getETA();
                    favoriteSettlement = posSet;
                } else if (posSet.getETA() == bestETA) {
                    if (favoriteSettlement == null) {
                        if ((favoriteCity == null) || (posSet.getSpeedupTotal() > favoriteCity.getSpeedupTotal())) {
                            favoriteSettlement = posSet;
                        }
                    } else {
                        if (posSet.getSpeedupTotal() > favoriteSettlement.getSpeedupTotal()) {
                            favoriteSettlement = posSet;
                        }
                    }
                }
            }

            if (favoriteSettlement != null) {
                //
                // we want to build a settlement
                //
                D.ebugPrintlnINFO("Picked favorite settlement at "+brain.getGame().getBoard().nodeCoordToString(favoriteSettlement.getCoordinates()));
                logBuildPlanChoice(StacPossibleBuildPlan.SETTLEMENT);
                newBuildingPlan.push(favoriteSettlement);
                if (!favoriteSettlement.getNecessaryRoads().isEmpty()) {
                    //
                    // we need to build roads first
                    //	  
                    Stack roadPath = favoriteSettlement.getRoadPath();
                    while (!roadPath.empty()) {
                        newBuildingPlan.push( (SOCPossiblePiece) roadPath.pop());
                    }
                }
            } else if (favoriteCity != null) {
                //
                // we want to build a city
                //
                D.ebugPrintlnINFO("Picked favorite city at "+brain.getGame().getBoard().nodeCoordToString(favoriteCity.getCoordinates()));
                logBuildPlanChoice(StacPossibleBuildPlan.CITY);
                newBuildingPlan.push(favoriteCity);
            } else {
                //
                // we can't build a settlement or city
                //
                if ((brain.getGame().getNumDevCards() > 0) && ! forSpecialBuildingPhase)
                {
                    //
                    // buy a card if there are any left
                    //
                    D.ebugPrintlnINFO("Buy a card");
                    SOCPossibleCard posCard = new SOCPossibleCard(player, buildingETAs[SOCBuildingSpeedEstimate.CARD]);
                    logBuildPlanChoice(StacPossibleBuildPlan.CARD);
                    newBuildingPlan.push(posCard);
                }
            }
        } else {
            //
            // we have more than 4 points
            //
            int choice = -1;
            
            //
            // consider Largest Army
            //
            D.ebugPrintlnINFO("Calculating Largest Army ETA");
            int laETA = 500;
            int knightsToBuy = 0;

            if (player.getTotalVP() >= minVPtoTryLA) { // ---MG only try LA if we have the required minimum VP
                int laSize = 0;
                SOCPlayer laPlayer = brain.getGame().getPlayerWithLargestArmy();
                if (laPlayer == null) {
                    ///
                    /// no one has largest army
                    ///
                    laSize = 3;
                } else if (laPlayer.getPlayerNumber() == player.getPlayerNumber()) {
                    ///
                    /// we have largest army
                    ///
                    D.ebugPrintlnINFO("We have largest army");
                } else {
                    laSize = laPlayer.getNumKnights() + 1;
                }
                ///
                /// figure out how many knights we need to buy
                ///
                if ((player.getNumKnights() + 
                        player.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT) +
                        player.getDevCards().getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.KNIGHT))
                        < laSize) {
                    knightsToBuy = laSize - (player.getNumKnights() +
                            player.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT));
                }
                D.ebugPrintlnINFO("knightsToBuy = "+knightsToBuy);
                if (player.getGame().getNumDevCards() >= knightsToBuy) {      
                    ///
                    /// figure out how long it takes to buy this many knights
                    ///
                    SOCResourceSet targetResources = new SOCResourceSet();
                    for (int i = 0; i < knightsToBuy; i++) {
                        targetResources.add(SOCGame.CARD_SET);
                    }
                    try {
                        SOCResSetBuildTimePair timePair = ourBSE.calculateRollsFast(player.getResources(), targetResources, 100, player.getPortFlags());
                        laETA = timePair.getRolls();
                    } catch (CutoffExceededException ex) {
                        laETA = 100;
                    }      
                } else {
                    ///
                    /// not enough dev cards left
                    ///
                }
                if ((laETA < bestETA) && ! forSpecialBuildingPhase)
                {
                    bestETA = laETA;
                    choice = LA_CHOICE;
                }
            }
            D.ebugPrintlnINFO("laETA = "+laETA);

            //
            // consider Longest Road
            //
            D.ebugPrintlnINFO("Calculating Longest Road ETA");
            int lrETA = 500;
            Stack bestLRPath = null;
            int lrLength;
            if (player.getTotalVP() >= minVPtoTryLR) { // ---MG only try LR if we have the required minimum VP
                SOCPlayer lrPlayer = brain.getGame().getPlayerWithLongestRoad();
                if ((lrPlayer != null) && 
                        (lrPlayer.getPlayerNumber() == player.getPlayerNumber())) {
                    ///
                    /// we have longest road
                    ///
                    D.ebugPrintlnINFO("We have longest road");
                } else {
                    if (lrPlayer == null) {
                        ///
                        /// no one has longest road
                        ///
                        lrLength = Math.max(4, player.getLongestRoadLength());
                    } else {
                        lrLength = lrPlayer.getLongestRoadLength();
                    }
                    Iterator lrPathsIter = player.getLRPaths().iterator();
                    int depth;
                    while (lrPathsIter.hasNext()) {
                        Stack path;
                        SOCLRPathData pathData = (SOCLRPathData)lrPathsIter.next();
                        depth = Math.min(((lrLength + 1) - pathData.getLength()), player.getNumPieces(SOCPlayingPiece.ROAD));
                        path = (Stack) recalcLongestRoadETAAux(player, true, pathData.getBeginning(), pathData.getLength(), lrLength, depth);
                        if ((path != null) &&
                                ((bestLRPath == null) ||
                                        (path.size() < bestLRPath.size()))) {
                            bestLRPath = path;
                        }
                        path = (Stack) recalcLongestRoadETAAux(player, true, pathData.getEnd(), pathData.getLength(), lrLength, depth);
                        if ((path != null) &&
                                ((bestLRPath == null) ||
                                        (path.size() < bestLRPath.size()))) {
                            bestLRPath = path;
                        }
                    }
                    if (bestLRPath != null) {
                        //
                        // calculate LR eta
                        //
                        D.ebugPrintlnINFO("Number of roads: "+bestLRPath.size());
                        SOCResourceSet targetResources = new SOCResourceSet();
                        for (int i = 0; i < bestLRPath.size(); i++) {
                            targetResources.add(SOCGame.ROAD_SET);
                        }
                        try {
                            SOCResSetBuildTimePair timePair = ourBSE.calculateRollsFast(player.getResources(), targetResources, 100, player.getPortFlags());
                            lrETA = timePair.getRolls();
                        } catch (CutoffExceededException ex) {
                            lrETA = 100;
                        } 
                    }
                }
                if (lrETA < bestETA) {
                    bestETA = lrETA;
                    choice = LR_CHOICE;
                }
            }
            D.ebugPrintlnINFO("lrETA = "+lrETA);

            //
            // consider possible cities
            //
            if ((player.getNumPieces(SOCPlayingPiece.CITY) > 0) &&
                    (buildingETAs[SOCBuildingSpeedEstimate.CITY] <= bestETA)) {
                Iterator posCitiesIter = ourPlayerTracker.getPossibleCities().values().iterator();
                while (posCitiesIter.hasNext()) {
                    SOCPossibleCity posCity = (SOCPossibleCity)posCitiesIter.next();
                    if ((brain != null) && (brain.getDRecorder().isOn())) {
                        brain.getDRecorder().startRecording("CITY"+posCity.getCoordinates());
                        brain.getDRecorder().record("Estimate speedup of city at "+brain.getGame().getBoard().nodeCoordToString(posCity.getCoordinates()));
                        brain.getDRecorder().record("Speedup = "+posCity.getSpeedupTotal());
                        brain.getDRecorder().record("ETA = "+buildingETAs[SOCBuildingSpeedEstimate.CITY]);
                        brain.getDRecorder().stopRecording();
                    }
                    if ((favoriteCity == null) ||
                            (posCity.getSpeedupTotal() > favoriteCity.getSpeedupTotal())) {
                        favoriteCity = posCity;
                        bestETA = buildingETAs[SOCBuildingSpeedEstimate.CITY];
                        if (brain.isRobotType(StacRobotType.FAVOUR_CITIES)) {
                            //boost (i.e. reduce) the ETA by the specified parameter
                            double factor = (Double)brain.getTypeParam(StacRobotType.FAVOUR_CITIES);
                            double newBestETA = bestETA - (factor * bestETA);
                            bestETA = (int)newBestETA;
                            if (bestETA < 0) {
                                bestETA = 0;
                            }
                        }
                        choice = CITY_CHOICE;
                    }
                }
            }

            //
            // consider possible settlements
            //
            if (player.getNumPieces(SOCPlayingPiece.SETTLEMENT) > 0) {
                scoreSettlementsForDumb(buildingETAs[SOCBuildingSpeedEstimate.SETTLEMENT], ourBSE);
                Iterator posSetsIter = ourPlayerTracker.getPossibleSettlements().values().iterator();
                while (posSetsIter.hasNext()) {
                    SOCPossibleSettlement posSet = (SOCPossibleSettlement)posSetsIter.next();
                    if ((brain != null) && (brain.getDRecorder().isOn())) {
                        brain.getDRecorder().startRecording("SETTLEMENT"+posSet.getCoordinates());
                        brain.getDRecorder().record("Estimate speedup of stlmt at "+brain.getGame().getBoard().nodeCoordToString(posSet.getCoordinates()));
                        brain.getDRecorder().record("Speedup = "+posSet.getSpeedupTotal());
                        brain.getDRecorder().record("ETA = "+posSet.getETA());
                        Stack roadPath = posSet.getRoadPath();
                        if (roadPath!= null) {
                            brain.getDRecorder().record("Path:");
                            Iterator rpIter = roadPath.iterator();
                            while (rpIter.hasNext()) {
                                SOCPossibleRoad posRoad = (SOCPossibleRoad)rpIter.next();
                                brain.getDRecorder().record("Road at "+brain.getGame().getBoard().edgeCoordToString(posRoad.getCoordinates()));
                            }
                        }
                        brain.getDRecorder().stopRecording();
                    }
                    if ((posSet.getRoadPath() == null) || (player.getNumPieces(SOCPlayingPiece.ROAD) >= posSet.getRoadPath().size())) {
                        int bestSettlementETA = posSet.getETA();
                        if (brain.isRobotType(StacRobotType.FAVOUR_SETTLEMENTS)) {
                            //boost (i.e. reduce) the ETA by the specified parameter
                            double factor = (Double)brain.getTypeParam(StacRobotType.FAVOUR_SETTLEMENTS);
                            double newBestETA = bestSettlementETA - (factor * bestSettlementETA);
                            bestSettlementETA = (int)newBestETA;
                            if (bestSettlementETA < 0) {
                                bestSettlementETA = 0;
                            }
                        }
                        if (bestSettlementETA < bestETA) {
                            bestETA = posSet.getETA();
                            favoriteSettlement = posSet;
                            choice = SETTLEMENT_CHOICE;
                        } else if (posSet.getETA() == bestETA) {
                            if (favoriteSettlement == null) {
                                if ((favoriteCity == null) ||
                                        (posSet.getSpeedupTotal() > favoriteCity.getSpeedupTotal())) {
                                    favoriteSettlement = posSet;
                                    choice = SETTLEMENT_CHOICE;
                                }
                            } else {
                                if (posSet.getSpeedupTotal() > favoriteSettlement.getSpeedupTotal()) {
                                    favoriteSettlement = posSet;
                                }
                            }
                        }
                    }
                }
            }

            //
            // pick something to build
            //
            switch (choice) {
            case LA_CHOICE:
                D.ebugPrintlnINFO("Picked LA");
                logBuildPlanChoice(StacPossibleBuildPlan.LARGEST_ARMY);
                if (! forSpecialBuildingPhase)
                {
                    for (int i = 0; i < knightsToBuy; i++)
                    {
                        SOCPossibleCard posCard = new SOCPossibleCard(player, 1);
                        newBuildingPlan.push(posCard);
                    }
                }
                break;

            case LR_CHOICE:
                D.ebugPrintlnINFO("Picked LR");
                logBuildPlanChoice(StacPossibleBuildPlan.LONGEST_ROAD);
                while (!bestLRPath.empty()) {
                    SOCPossibleRoad pr = (SOCPossibleRoad)bestLRPath.pop();
                    D.ebugPrintlnINFO("LR road at "+brain.getGame().getBoard().edgeCoordToString(pr.getCoordinates()));
                    newBuildingPlan.push(pr);
                }
                break;

            case CITY_CHOICE:
                D.ebugPrintlnINFO("Picked favorite city at "+brain.getGame().getBoard().nodeCoordToString(favoriteCity.getCoordinates()));
                logBuildPlanChoice(StacPossibleBuildPlan.CITY);
                newBuildingPlan.push(favoriteCity);
                break;

            case SETTLEMENT_CHOICE:
                D.ebugPrintlnINFO("Picked favorite settlement at "+brain.getGame().getBoard().nodeCoordToString(favoriteSettlement.getCoordinates()));
                logBuildPlanChoice(StacPossibleBuildPlan.SETTLEMENT);
                newBuildingPlan.push(favoriteSettlement);
                if (!favoriteSettlement.getNecessaryRoads().isEmpty()) {
                    //
                    // we need to build roads first
                    //	  
                    Stack roadPath = favoriteSettlement.getRoadPath();
                    while (!roadPath.empty()) {
                        SOCPossibleRoad pr = (SOCPossibleRoad)roadPath.pop();
                        D.ebugPrintlnINFO("Nec road at "+brain.getGame().getBoard().edgeCoordToString(pr.getCoordinates()));
                        newBuildingPlan.push(pr);
                    }
                }
            }
        }
        return newBuildingPlan;
    }

    /**
     * score all possible settlements by getting their speedup total
     * calculate ETA by finding shortest path and then using a
     * SOCBuildingSpeedEstimate to find the ETA
     *
     * @param settlementETA  eta for building a settlement from now
     */
    private void scoreSettlementsForDumb(int settlementETA, SOCBuildingSpeedEstimate ourBSE) {
        D.ebugPrintlnINFO("-- scoreSettlementsForDumb --");
        Queue queue = new Queue();
        Iterator posSetsIter = ourPlayerTracker.getPossibleSettlements().values().iterator();
        while (posSetsIter.hasNext()) {
            SOCPossibleSettlement posSet = (SOCPossibleSettlement)posSetsIter.next();
            D.ebugPrintlnINFO("Estimate speedup of stlmt at "+brain.getGame().getBoard().nodeCoordToString(posSet.getCoordinates()));
            D.ebugPrintlnINFO("***    speedup total = "+posSet.getSpeedupTotal());

            ///
            /// find the shortest path to this settlement
            ///
            Vector necRoadVec = posSet.getNecessaryRoads();
            if (!necRoadVec.isEmpty()) {
                queue.clear();
                Iterator necRoadsIter = necRoadVec.iterator();
                while (necRoadsIter.hasNext()) {
                    SOCPossibleRoad necRoad = (SOCPossibleRoad)necRoadsIter.next();
                    D.ebugPrintlnINFO("-- queuing necessary road at "+brain.getGame().getBoard().edgeCoordToString(necRoad.getCoordinates()));
                    queue.put(new Pair(necRoad, null));
                }
                //
                // Do a BFS of the necessary road paths looking for the shortest one.
                //
                while (!queue.empty()) {
                    Pair dataPair = (Pair)queue.get();
                    SOCPossibleRoad curRoad = (SOCPossibleRoad)dataPair.getA();
                    D.ebugPrintlnINFO("-- current road at "+brain.getGame().getBoard().edgeCoordToString(curRoad.getCoordinates()));
                    Vector necRoads = curRoad.getNecessaryRoads();
                    if (necRoads.isEmpty()) {
                        //
                        // we have a path 
                        //
                        D.ebugPrintlnINFO("Found a path!");
                        Stack path = new Stack();
                        path.push(curRoad);
                        Pair curPair = (Pair)dataPair.getB();
                        D.ebugPrintlnINFO("curPair = "+curPair);
                        while (curPair != null) {
                            path.push(curPair.getA());
                            curPair = (Pair)curPair.getB();
                        }
                        posSet.setRoadPath(path);
                        queue.clear();
                        D.ebugPrintlnINFO("Done setting path.");
                    } else {
                        necRoadsIter = necRoads.iterator();
                        while (necRoadsIter.hasNext()) {
                            SOCPossibleRoad necRoad2 = (SOCPossibleRoad)necRoadsIter.next();
                            D.ebugPrintlnINFO("-- queuing necessary road at "+brain.getGame().getBoard().edgeCoordToString(necRoad2.getCoordinates()));
                            queue.put(new Pair(necRoad2, dataPair));
                        }
                    }
                }
                D.ebugPrintlnINFO("Done searching for path.");

                //
                // calculate ETA
                //
                SOCResourceSet targetResources = new SOCResourceSet();
                targetResources.add(SOCGame.SETTLEMENT_SET);
                int pathLength = 0;
                Stack path = posSet.getRoadPath();
                if (path != null) {
                    pathLength = path.size();
                }
                for (int i = 0; i < pathLength; i++) {
                    targetResources.add(SOCGame.ROAD_SET);
                }
                try {
                    SOCResSetBuildTimePair timePair = ourBSE.calculateRollsFast(player.getResources(), targetResources, 100, player.getPortFlags());
                    posSet.setETA(timePair.getRolls());
                } catch (CutoffExceededException ex) {
                    posSet.setETA(100);
                }
            } else {
                //
                // no roads are necessary
                //
                posSet.setRoadPath(null);
                posSet.setETA(settlementETA);
            }

            //---MG catch the case where we have no settlement piece left
            //when the treshold for trying to get LR/LA is increased above the standard 5, for some reason the robot tried to build settlements with no settlement peices left
            if (player.getNumPieces(SOCPlayingPiece.SETTLEMENT) == 0) {
                D.ebugPrintlnINFO("No more settlement pieces left!");
                posSet.setETA(100);
            }

            D.ebugPrintlnINFO("Settlement ETA = "+posSet.getETA());
        }
    }


    /**
     * Does a depth first search from the end point of the longest
     * path in a graph of nodes and returns how many roads would
     * need to be built to take longest road.
     *<P>
     * Combined implementation for use by SOCRobotDM and {@link SOCPlayerTracker}.
     *
     * @param pl            Calculate this player's longest road;
     *             typically SOCRobotDM.ourPlayerData or SOCPlayerTracker.player
     * @param wantsStack    If true, return the Stack; otherwise, return numRoads.
     * @param startNode     the path endpoint
     * @param pathLength    the length of that path
     * @param lrLength      length of longest road in the game
     * @param searchDepth   how many roads out to search
     *
     * @return if <tt>wantsStack</tt>: a {@link Stack} containing the path of roads with the last one on top, or null if it can't be done.
     *         If ! <tt>wantsStack</tt>: Integer: the number of roads needed, or 500 if it can't be done
     *         TODO: This should be private, requires some refactoring
     */
    protected static Object recalcLongestRoadETAAux
    (SOCPlayer pl, final boolean wantsStack, final int startNode, final int pathLength, final int lrLength, final int searchDepth)
    {
        D.ebugPrintlnINFO("=== recalcLongestRoadETAAux("+Integer.toHexString(startNode)+","+pathLength+","+lrLength+","+searchDepth+")");

        //
        // We're doing a depth first search of all possible road paths.
        // For similar code, see SOCPlayer.calcLongestRoad2
        //
        int longest = 0;
        int numRoads = 500;
        Pair bestPathNode = null;
        final SOCBoard board = pl.getGame().getBoard();
        final int MINEDGE = board.getMinEdge(),
                MAXEDGE = board.getMaxEdge();
        Stack pending = new Stack();
        pending.push(new Pair(new NodeLenVis(startNode, pathLength, new Vector()), null));

        while (!pending.empty())
        {
            Pair dataPair = (Pair) pending.pop();
            NodeLenVis curNode = (NodeLenVis) dataPair.getA();
            //D.ebugPrintln("curNode = "+curNode);

            final int coord = curNode.node;
            int len = curNode.len;
            Vector visited = curNode.vis;
            boolean pathEnd = false;

            //
            // check for road blocks
            //
            if (len > 0)
            {
                final int pn = pl.getPlayerNumber();
                Enumeration pEnum = board.getPieces().elements();

                while (pEnum.hasMoreElements())
                {
                    SOCPlayingPiece p = (SOCPlayingPiece) pEnum.nextElement();
                    if ((p.getCoordinates() == coord)
                            && (p.getPlayer().getPlayerNumber() != pn)
                            && ((p.getType() == SOCPlayingPiece.SETTLEMENT) || (p.getType() == SOCPlayingPiece.CITY)))
                    {
                        pathEnd = true;
                        //D.ebugPrintln("^^^ path end at "+Integer.toHexString(coord));
                        break;
                    }
                }
            }

            if (!pathEnd)
            {
                // 
                // check if we've connected to another road graph
                //
                Iterator lrPathsIter = pl.getLRPaths().iterator();
                while (lrPathsIter.hasNext())
                {
                    SOCLRPathData pathData = (SOCLRPathData) lrPathsIter.next();
                    if ((startNode != pathData.getBeginning())
                            && (startNode != pathData.getEnd())
                            && ((coord == pathData.getBeginning())
                                    || (coord == pathData.getEnd())))
                    {
                        pathEnd = true;
                        len += pathData.getLength();
                        //D.ebugPrintln("connecting to another path: " + pathData);
                        //D.ebugPrintln("len = " + len);

                        break;
                    }
                }
            }

            if (!pathEnd)
            {
                //
                // (len - pathLength) = how many new roads we've built
                //
                if ((len - pathLength) >= searchDepth)
                {
                    pathEnd = true;
                }
                //D.ebugPrintln("Reached search depth");
            }

            if (!pathEnd)
            {
                /**
                 * For each of the 3 adjacent edges of coord's node,
                 * check for unvisited legal road possibilities.
                 * When they are found, push that edge's far-end node
                 * onto the pending stack.
                 */
                pathEnd = true;

                for (int dir = 0; dir < 3; ++dir)
                {
                    int j = board.getAdjacentEdgeToNode(coord, dir);
                    if (pl.isLegalRoad(j))
                    {
                        Integer edge = Integer.valueOf(j);
                        boolean match = false;

                        for (Enumeration ev = visited.elements();
                                ev.hasMoreElements(); )
                        {
                            Integer vis = (Integer) ev.nextElement();
                            if (vis.equals(edge))
                            {
                                match = true;
                                break;
                            }
                        }

                        if (! match)
                        {
                            Vector newVis = (Vector) visited.clone();
                            newVis.addElement(edge);

                            j = board.getAdjacentNodeToNode(coord, dir);  // edge's other node
                            pending.push(new Pair(new NodeLenVis(j, len+1, newVis), dataPair));
                            pathEnd = false;
                        }
                    }
                }
            }

            if (pathEnd)
            {
                if (len > longest)
                {
                    longest = len;
                    numRoads = curNode.len - pathLength;
                    bestPathNode = dataPair;
                }
                else if ((len == longest) && (curNode.len < numRoads))
                {
                    numRoads = curNode.len - pathLength;
                    bestPathNode = dataPair;
                }
            }
        }

        if (! wantsStack)
        {
            // As used by SOCPlayerTracker.
            int rv;
            if (longest > lrLength)
                rv = numRoads;
            else
                rv = 500;
            return Integer.valueOf(rv);  // <-- Early return: ! wantsStack ---
        }

        if ((longest > lrLength) &&
                (bestPathNode != null))
        {
            //D.ebugPrintln("Converting nodes to road coords.");
            //
            // return the path in a stack with the last road on top
            //
            //
            // first, convert pairs of node coords to road coords (edge coords):
            //
            Stack temp = new Stack();
            SOCPossibleRoad posRoad;
            int coordA, coordB;
            Pair cur, parent;
            cur = bestPathNode;
            parent = (Pair)bestPathNode.getB();
            while (parent != null)
            {
                coordA = ((NodeLenVis)cur.getA()).node;
                coordB = ((NodeLenVis)parent.getA()).node;
                posRoad = new SOCPossibleRoad
                        (pl, board.getEdgeBetweenAdjacentNodes(coordA, coordB), new Vector());
                temp.push(posRoad);
                cur = parent;
                parent = (Pair) parent.getB();
            }
            //
            // reverse the order of the roads so that the last one is on top
            //
            Stack path = new Stack();
            while (!temp.empty())
                path.push(temp.pop());

            return path;

        } else {

            return null;
        }
    }

    /**
     * smart game strategy
     * use WGETA to determine best move
     *
     * @param buildingETAs  the etas for building something
     */
    private void smartGameStrategy(int[] buildingETAs)
    {
        D.ebugPrintlnINFO("***** smartGameStrategy *****");

        // If this game is on the 6-player board, check whether we're planning for
        // the Special Building Phase.  Can't buy cards or trade in that phase.
        final boolean forSpecialBuildingPhase =
                brain.getGame().isSpecialBuilding() || (brain.getGame().getCurrentPlayerNumber() != player.getPlayerNumber());

        //
        // save the lr paths list to restore later
        //
        Vector savedLRPaths[] = new Vector[brain.getGame().maxPlayers];
        for (int pn = 0; pn < brain.getGame().maxPlayers; pn++) {
            savedLRPaths[pn] = (Vector)brain.getGame().getPlayer(pn).getLRPaths().clone();
        }

        int ourCurrentWGETA = ourPlayerTracker.getWinGameETA();
        D.ebugPrintlnINFO("ourCurrentWGETA = "+ourCurrentWGETA);

        int leadersCurrentWGETA = ourCurrentWGETA;
        Iterator trackersIter = playerTrackers.values().iterator();
        while (trackersIter.hasNext()) {
            SOCPlayerTracker tracker = (SOCPlayerTracker)trackersIter.next();
            int wgeta = tracker.getWinGameETA();
            if (wgeta < leadersCurrentWGETA) {
                leadersCurrentWGETA = wgeta;
            }
        }

        /*
	    boolean goingToPlayRB = false;
	    if (!player.hasPlayedDevCard() &&
		player.getNumPieces(SOCPlayingPiece.ROAD) >= 2 &&
		player.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.ROADS) > 0) {
	      goingToPlayRB = true;
	    }
         */

        ///
        /// score the possible settlements
        ///
        if (player.getNumPieces(SOCPlayingPiece.SETTLEMENT) > 0) {
            scorePossibleSettlements(buildingETAs[SOCBuildingSpeedEstimate.SETTLEMENT], leadersCurrentWGETA);
        }

        ///
        /// collect roads that we can build now
        ///
        if (player.getNumPieces(SOCPlayingPiece.ROAD) > 0) {
            Iterator posRoadsIter = ourPlayerTracker.getPossibleRoads().values().iterator();
            while (posRoadsIter.hasNext()) {
                SOCPossibleRoad posRoad = (SOCPossibleRoad)posRoadsIter.next();
                if ((posRoad.getNecessaryRoads().isEmpty()) &&
                        (!threatenedRoads.contains(posRoad)) &&
                        (!goodRoads.contains(posRoad))) {
                    goodRoads.addElement(posRoad);
                }
            }
        }

        /*
	    ///
	    /// check everything
	    ///
	    Enumeration threatenedSetEnum = threatenedSettlements.elements();
	    while (threatenedSetEnum.hasMoreElements()) {
	      SOCPossibleSettlement threatenedSet = (SOCPossibleSettlement)threatenedSetEnum.nextElement();
	      D.ebugPrintln("*** threatened settlement at "+Integer.toHexString(threatenedSet.getCoordinates())+" has a score of "+threatenedSet.getScore());
	      if (threatenedSet.getNecessaryRoads().isEmpty() &&
		  !player.isPotentialSettlement(threatenedSet.getCoordinates())) {
		D.ebugPrintln("POTENTIAL SETTLEMENT ERROR");
		//System.exit(0);
	      } 
	    }
	    Enumeration goodSetEnum = goodSettlements.elements();
	    while (goodSetEnum.hasMoreElements()) {
	      SOCPossibleSettlement goodSet = (SOCPossibleSettlement)goodSetEnum.nextElement();
	      D.ebugPrintln("*** good settlement at "+Integer.toHexString(goodSet.getCoordinates())+" has a score of "+goodSet.getScore());
	      if (goodSet.getNecessaryRoads().isEmpty() &&
		  !player.isPotentialSettlement(goodSet.getCoordinates())) {
		D.ebugPrintln("POTENTIAL SETTLEMENT ERROR");
		//System.exit(0);
	      } 
	    }    
	    Enumeration threatenedRoadEnum = threatenedRoads.elements();
	    while (threatenedRoadEnum.hasMoreElements()) {
	      SOCPossibleRoad threatenedRoad = (SOCPossibleRoad)threatenedRoadEnum.nextElement();
	      D.ebugPrintln("*** threatened road at "+Integer.toHexString(threatenedRoad.getCoordinates())+" has a score of "+threatenedRoad.getScore());      	
	      if (threatenedRoad.getNecessaryRoads().isEmpty() &&
		  !player.isPotentialRoad(threatenedRoad.getCoordinates())) {
		D.ebugPrintln("POTENTIAL ROAD ERROR");
		//System.exit(0);
	      }
	    }
	    Enumeration goodRoadEnum = goodRoads.elements();
	    while (goodRoadEnum.hasMoreElements()) {
	      SOCPossibleRoad goodRoad = (SOCPossibleRoad)goodRoadEnum.nextElement();
	      D.ebugPrintln("*** good road at "+Integer.toHexString(goodRoad.getCoordinates())+" has a score of "+goodRoad.getScore());
	      if (goodRoad.getNecessaryRoads().isEmpty() &&
		  !player.isPotentialRoad(goodRoad.getCoordinates())) {
		D.ebugPrintln("POTENTIAL ROAD ERROR");
		//System.exit(0);
	      }
	    }  
         */

        D.ebugPrintlnINFO("PICKING WHAT TO BUILD");

        ///
        /// pick what we want to build
        ///

        ///
        /// pick a settlement that can be built now
        ///
        if (player.getNumPieces(SOCPlayingPiece.SETTLEMENT) > 0) {
            Iterator threatenedSetIter = threatenedSettlements.iterator();
            while (threatenedSetIter.hasNext()) {
                SOCPossibleSettlement threatenedSet = (SOCPossibleSettlement)threatenedSetIter.next();
                if (threatenedSet.getNecessaryRoads().isEmpty()) {
                    D.ebugPrintlnINFO("$$$$$ threatened settlement at "+Integer.toHexString(threatenedSet.getCoordinates())+" has a score of "+threatenedSet.getScore());

                    if ((favoriteSettlement == null) ||
                            (threatenedSet.getScore() > favoriteSettlement.getScore())) {
                        favoriteSettlement = threatenedSet;
                    }
                }
            } 

            Iterator goodSetIter = goodSettlements.iterator();
            while (goodSetIter.hasNext()) {
                SOCPossibleSettlement goodSet = (SOCPossibleSettlement)goodSetIter.next();
                if (goodSet.getNecessaryRoads().isEmpty()) {
                    D.ebugPrintlnINFO("$$$$$ good settlement at "+Integer.toHexString(goodSet.getCoordinates())+" has a score of "+goodSet.getScore());

                    if ((favoriteSettlement == null) ||
                            (goodSet.getScore() > favoriteSettlement.getScore())) {
                        favoriteSettlement = goodSet;
                    }
                }
            }
        }

        //
        // restore the LRPath list
        //
        D.ebugPrintlnINFO("%%% RESTORING LRPATH LIST %%%");
        for (int pn = 0; pn < brain.getGame().maxPlayers; pn++) {
            brain.getGame().getPlayer(pn).setLRPaths(savedLRPaths[pn]);
        } 

        ///
        /// pick a road that can be built now
        ///
        if (player.getNumPieces(SOCPlayingPiece.ROAD) > 0) {
            Iterator threatenedRoadIter = threatenedRoads.iterator();
            while (threatenedRoadIter.hasNext()) {
                SOCPossibleRoad threatenedRoad = (SOCPossibleRoad)threatenedRoadIter.next();
                D.ebugPrintlnINFO("$$$$$ threatened road at "+Integer.toHexString(threatenedRoad.getCoordinates()));

                if ((brain != null) && (brain.getDRecorder().isOn())) {	  
                    brain.getDRecorder().startRecording("ROAD"+threatenedRoad.getCoordinates());
                    brain.getDRecorder().record("Estimate value of road at "+brain.getGame().getBoard().edgeCoordToString(threatenedRoad.getCoordinates()));
                } 

                //
                // see how building this piece impacts our winETA
                //
                threatenedRoad.resetScore();
                float wgetaScore = getWinGameETABonusForRoad(threatenedRoad, buildingETAs[SOCBuildingSpeedEstimate.ROAD], leadersCurrentWGETA, playerTrackers);
                if ((brain != null) && (brain.getDRecorder().isOn())) {	  
                    brain.getDRecorder().stopRecording();
                } 

                D.ebugPrintlnINFO("wgetaScore = "+wgetaScore);

                if (favoriteRoad == null) {
                    favoriteRoad = threatenedRoad;
                } else {
                    if (threatenedRoad.getScore() > favoriteRoad.getScore()) {
                        favoriteRoad = threatenedRoad;
                    }
                }
            }
            Iterator goodRoadIter = goodRoads.iterator();
            while (goodRoadIter.hasNext()) {
                SOCPossibleRoad goodRoad = (SOCPossibleRoad)goodRoadIter.next();
                D.ebugPrintlnINFO("$$$$$ good road at "+Integer.toHexString(goodRoad.getCoordinates()));

                if ((brain != null) && (brain.getDRecorder().isOn())) {
                    brain.getDRecorder().startRecording("ROAD"+goodRoad.getCoordinates());
                    brain.getDRecorder().record("Estimate value of road at "+ brain.getGame().getBoard().edgeCoordToString(goodRoad.getCoordinates()));
                } 

                //
                // see how building this piece impacts our winETA
                //
                goodRoad.resetScore();
                float wgetaScore = getWinGameETABonusForRoad(goodRoad, buildingETAs[SOCBuildingSpeedEstimate.ROAD], leadersCurrentWGETA, playerTrackers);
                if ((brain != null) && (brain.getDRecorder().isOn())) {
                    brain.getDRecorder().stopRecording();
                } 

                D.ebugPrintlnINFO("wgetaScore = "+wgetaScore);					

                if (favoriteRoad == null) {
                    favoriteRoad = goodRoad;
                } else {
                    if (goodRoad.getScore() > favoriteRoad.getScore()) {
                        favoriteRoad = goodRoad;
                    }
                }
            }
        }

        //
        // restore the LRPath list
        //
        D.ebugPrintlnINFO("%%% RESTORING LRPATH LIST %%%");
        for (int pn = 0; pn < brain.getGame().maxPlayers; pn++) {
            brain.getGame().getPlayer(pn).setLRPaths(savedLRPaths[pn]);
        }  

        ///
        /// pick a city that can be built now
        ///
        if (player.getNumPieces(SOCPlayingPiece.CITY) > 0) {
            HashMap trackersCopy = SOCPlayerTracker.copyPlayerTrackers(playerTrackers);
            SOCPlayerTracker ourTrackerCopy = (SOCPlayerTracker)trackersCopy.get(Integer.valueOf(player.getPlayerNumber()));
            int originalWGETAs[] = new int[brain.getGame().maxPlayers];	 
            int WGETAdiffs[] = new int[brain.getGame().maxPlayers];	 
            Vector leaders = new Vector();
            int bestWGETA = 1000;
            // int bonus = 0;

            Iterator posCitiesIter = ourPlayerTracker.getPossibleCities().values().iterator();
            while (posCitiesIter.hasNext()) {
                SOCPossibleCity posCity = (SOCPossibleCity)posCitiesIter.next();
                if ((brain != null) && (brain.getDRecorder().isOn())) {
                    brain.getDRecorder().startRecording("CITY"+posCity.getCoordinates());
                    brain.getDRecorder().record("Estimate value of city at "+brain.getGame().getBoard().nodeCoordToString(posCity.getCoordinates()));
                } 

                //
                // see how building this piece impacts our winETA
                //
                leaders.clear();
                if ((brain != null) && (brain.getDRecorder().isOn())) {
                    brain.getDRecorder().suspend();
                }
                SOCPlayerTracker.updateWinGameETAs(trackersCopy);
                Iterator trackersBeforeIter = trackersCopy.values().iterator();
                while (trackersBeforeIter.hasNext()) {
                    SOCPlayerTracker trackerBefore = (SOCPlayerTracker)trackersBeforeIter.next();
                    D.ebugPrintlnINFO("$$$ win game ETA for player "+trackerBefore.getPlayer().getPlayerNumber()+" = "+trackerBefore.getWinGameETA());
                    originalWGETAs[trackerBefore.getPlayer().getPlayerNumber()] = trackerBefore.getWinGameETA();
                    WGETAdiffs[trackerBefore.getPlayer().getPlayerNumber()] = trackerBefore.getWinGameETA();
                    if (trackerBefore.getWinGameETA() < bestWGETA) {
                        bestWGETA = trackerBefore.getWinGameETA();
                        leaders.removeAllElements();
                        leaders.addElement(trackerBefore);
                    } else if (trackerBefore.getWinGameETA() == bestWGETA) {
                        leaders.addElement(trackerBefore);
                    }
                }		
                D.ebugPrintlnINFO("^^^^ bestWGETA = "+bestWGETA);
                if ((brain != null) && (brain.getDRecorder().isOn())) {
                    brain.getDRecorder().resume();
                }
                //
                // place the city
                //
                SOCCity tmpCity = new SOCCity(player, posCity.getCoordinates(), null);
                brain.getGame().putTempPiece(tmpCity);

                ourTrackerCopy.addOurNewCity(tmpCity);

                SOCPlayerTracker.updateWinGameETAs(trackersCopy);

                float wgetaScore = calcWGETABonusAux(originalWGETAs, trackersCopy, leaders);

                //
                // remove the city
                //
                ourTrackerCopy.undoAddOurNewCity(posCity);
                brain.getGame().undoPutTempPiece(tmpCity);

                D.ebugPrintlnINFO("*** ETA for city = "+buildingETAs[SOCBuildingSpeedEstimate.CITY]);
                if ((brain != null) && (brain.getDRecorder().isOn())) {
                    brain.getDRecorder().record("ETA = "+buildingETAs[SOCBuildingSpeedEstimate.CITY]);
                } 	

                float etaBonus = getETABonus(buildingETAs[SOCBuildingSpeedEstimate.CITY], leadersCurrentWGETA, wgetaScore);
                D.ebugPrintlnINFO("etaBonus = "+etaBonus);

                posCity.addToScore(etaBonus);
                //posCity.addToScore(wgetaScore);

                if ((brain != null) && (brain.getDRecorder().isOn())) {
                    brain.getDRecorder().record("WGETA score = "+df1.format(wgetaScore));
                    brain.getDRecorder().record("Total city score = "+df1.format(etaBonus));
                    brain.getDRecorder().stopRecording();
                } 

                D.ebugPrintlnINFO("$$$  final score = "+posCity.getScore());

                D.ebugPrintlnINFO("$$$$$ possible city at "+Integer.toHexString(posCity.getCoordinates())+" has a score of "+posCity.getScore());

                if ((favoriteCity == null) ||
                        (posCity.getScore() > favoriteCity.getScore())) {
                    favoriteCity = posCity;
                }
            }
        }

        if (favoriteSettlement != null) {
            D.ebugPrintlnINFO("### FAVORITE SETTLEMENT IS AT "+Integer.toHexString(favoriteSettlement.getCoordinates()));
            D.ebugPrintlnINFO("###   WITH A SCORE OF "+favoriteSettlement.getScore());
            D.ebugPrintlnINFO("###   WITH AN ETA OF "+buildingETAs[SOCBuildingSpeedEstimate.SETTLEMENT]);
            D.ebugPrintlnINFO("###   WITH A TOTAL SPEEDUP OF "+favoriteSettlement.getSpeedupTotal());
        }

        if (favoriteCity != null) {
            D.ebugPrintlnINFO("### FAVORITE CITY IS AT "+Integer.toHexString(favoriteCity.getCoordinates()));
            D.ebugPrintlnINFO("###   WITH A SCORE OF "+favoriteCity.getScore());
            D.ebugPrintlnINFO("###   WITH AN ETA OF "+buildingETAs[SOCBuildingSpeedEstimate.CITY]);
            D.ebugPrintlnINFO("###   WITH A TOTAL SPEEDUP OF "+favoriteCity.getSpeedupTotal());
        }

        if (favoriteRoad != null) {
            D.ebugPrintlnINFO("### FAVORITE ROAD IS AT "+Integer.toHexString(favoriteRoad.getCoordinates()));
            D.ebugPrintlnINFO("###   WITH AN ETA OF "+buildingETAs[SOCBuildingSpeedEstimate.ROAD]);
            D.ebugPrintlnINFO("###   WITH A SCORE OF "+favoriteRoad.getScore());
        }
        int pick = -1;
        ///
        /// if the best settlement can wait, and the best road can wait,
        /// and the city is the best speedup and eta, then build the city
        ///
        if ((favoriteCity != null) &&
                (player.getNumPieces(SOCPlayingPiece.CITY) > 0) &&
                (favoriteCity.getScore() > 0) &&
                ((favoriteSettlement == null) ||
                        (player.getNumPieces(SOCPlayingPiece.SETTLEMENT) == 0) || 
                        (favoriteCity.getScore() > favoriteSettlement.getScore()) ||
                        ((favoriteCity.getScore() == favoriteSettlement.getScore()) &&
                                (buildingETAs[SOCBuildingSpeedEstimate.CITY] < buildingETAs[SOCBuildingSpeedEstimate.SETTLEMENT]))) &&
                                ((favoriteRoad == null) ||
                                        (player.getNumPieces(SOCPlayingPiece.ROAD) == 0) ||
                                        (favoriteCity.getScore() > favoriteRoad.getScore()) ||
                                        ((favoriteCity.getScore() == favoriteRoad.getScore()) &&
                                                (buildingETAs[SOCBuildingSpeedEstimate.CITY] < buildingETAs[SOCBuildingSpeedEstimate.ROAD])))) {
            D.ebugPrintlnINFO("### PICKED FAVORITE CITY");
            pick = SOCPlayingPiece.CITY;
            D.ebugPrintlnINFO("$ PUSHING "+favoriteCity);
            buildingPlan.push(favoriteCity);
        } 
        ///
        /// if there is a road with a better score than
        /// our favorite settlement, then build the road, 
        /// else build the settlement
        ///
        else if ((favoriteRoad != null) &&
                (player.getNumPieces(SOCPlayingPiece.ROAD) > 0) &&
                (favoriteRoad.getScore() > 0) &&
                ((favoriteSettlement == null) ||
                        (player.getNumPieces(SOCPlayingPiece.SETTLEMENT) == 0) ||
                        (favoriteSettlement.getScore() < favoriteRoad.getScore()))) {
            D.ebugPrintlnINFO("### PICKED FAVORITE ROAD");
            pick = SOCPlayingPiece.ROAD;
            D.ebugPrintlnINFO("$ PUSHING "+favoriteRoad);
            buildingPlan.push(favoriteRoad);
        } else if ((favoriteSettlement != null) &&
                (player.getNumPieces(SOCPlayingPiece.SETTLEMENT) > 0)) {
            D.ebugPrintlnINFO("### PICKED FAVORITE SETTLEMENT");
            pick = SOCPlayingPiece.SETTLEMENT;
            D.ebugPrintlnINFO("$ PUSHING "+favoriteSettlement);
            buildingPlan.push(favoriteSettlement);
        }
        ///
        /// if buying a card is better than building...
        ///

        //
        // see how buying a card improves our win game ETA
        //
        if ((brain.getGame().getNumDevCards() > 0) && ! forSpecialBuildingPhase)
        {
            if ((brain != null) && (brain.getDRecorder().isOn())) {
                brain.getDRecorder().startRecording("DEVCARD");
                brain.getDRecorder().record("Estimate value of a dev card");
            } 

            possibleCard = getDevCardScore(buildingETAs[SOCBuildingSpeedEstimate.CARD], leadersCurrentWGETA);
            float devCardScore = possibleCard.getScore();
            D.ebugPrintlnINFO("### DEV CARD SCORE: "+devCardScore);
            if ((brain != null) && (brain.getDRecorder().isOn())) {
                brain.getDRecorder().stopRecording();
            } 

            if ((pick == -1) ||
                    ((pick == SOCPlayingPiece.CITY) &&
                            (devCardScore > favoriteCity.getScore())) ||
                            ((pick == SOCPlayingPiece.ROAD) &&
                                    (devCardScore > favoriteRoad.getScore())) ||
                                    ((pick == SOCPlayingPiece.SETTLEMENT) &&
                                            (devCardScore > favoriteSettlement.getScore()))) {
                D.ebugPrintlnINFO("### BUY DEV CARD");

                if (pick != -1) {
                    buildingPlan.pop();
                    D.ebugPrintlnINFO("$ POPPED OFF SOMETHING");
                }

                D.ebugPrintlnINFO("$ PUSHING "+possibleCard);
                buildingPlan.push(possibleCard);
            }
        }
    }
    
	/**
	 * score possible settlements for smartStrategy
	 * @param settlementETA the estimated time to build a settlement
	 * @param leadersCurrentWGETA the leading player's estimated time to win the game
	 */
    private void scorePossibleSettlements(int settlementETA, int leadersCurrentWGETA) {
        D.ebugPrintlnINFO("****** scorePossibleSettlements");
        // int ourCurrentWGETA = ourPlayerTracker.getWinGameETA();

        /*
	    boolean goingToPlayRB = false;
	    if (!player.hasPlayedDevCard() &&
		player.getNumPieces(SOCPlayingPiece.ROAD) >= 2 &&
		player.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.ROADS) > 0) {
	      goingToPlayRB = true;
	    }
         */

        Iterator posSetsIter = ourPlayerTracker.getPossibleSettlements().values().iterator();
        while (posSetsIter.hasNext()) {
            SOCPossibleSettlement posSet = (SOCPossibleSettlement)posSetsIter.next();
            D.ebugPrintlnINFO("*** scoring possible settlement at "+Integer.toHexString(posSet.getCoordinates()));
            if (!threatenedSettlements.contains(posSet)) {
                threatenedSettlements.addElement(posSet);
            } else if (!goodSettlements.contains(posSet)) {
                goodSettlements.addElement(posSet);
            }
            //
            // only consider settlements we can build now
            //
            Vector necRoadVec = posSet.getNecessaryRoads();
            if (necRoadVec.isEmpty()) {
                D.ebugPrintlnINFO("*** no roads needed");
                //
                //  no roads needed
                //
                //
                //  get wgeta score
                //
                SOCBoard board = brain.getGame().getBoard();
                SOCSettlement tmpSet = new SOCSettlement(player, posSet.getCoordinates(), board);
                if ((brain != null) && (brain.getDRecorder().isOn())) {
                    brain.getDRecorder().startRecording("SETTLEMENT"+posSet.getCoordinates());
                    brain.getDRecorder().record("Estimate value of settlement at "+board.nodeCoordToString(posSet.getCoordinates()));
                } 

                HashMap trackersCopy = SOCPlayerTracker.tryPutPiece(tmpSet, brain.getGame(), playerTrackers);
                SOCPlayerTracker.updateWinGameETAs(trackersCopy);
                float wgetaScore = calcWGETABonus(playerTrackers, trackersCopy);
                D.ebugPrintlnINFO("***  wgetaScore = "+wgetaScore);

                D.ebugPrintlnINFO("*** ETA for settlement = "+settlementETA);
                if ((brain != null) && (brain.getDRecorder().isOn())) {
                    brain.getDRecorder().record("ETA = "+settlementETA);
                } 

                float etaBonus = getETABonus(settlementETA, leadersCurrentWGETA, wgetaScore);
                D.ebugPrintlnINFO("etaBonus = "+etaBonus);

                //posSet.addToScore(wgetaScore);
                posSet.addToScore(etaBonus);

                if ((brain != null) && (brain.getDRecorder().isOn())) {
                    brain.getDRecorder().record("WGETA score = "+df1.format(wgetaScore));
                    brain.getDRecorder().record("Total settlement score = "+df1.format(etaBonus));
                    brain.getDRecorder().stopRecording();
                } 

                SOCPlayerTracker.undoTryPutPiece(tmpSet, brain.getGame());
            }
        }
    } 


    /**
     * add a bonus to the road score based on the change in 
     * win game ETA for this one road
     *
     * @param posRoad  the possible piece that we're scoring
     * @param roadETA  the eta for the road
     * @param leadersCurrentWGETA  the leaders current WGETA
     * @param playerTrackers  the player trackers (passed in as an argument for figuring out road building plan)
     */
    private float getWinGameETABonusForRoad(SOCPossibleRoad posRoad, int roadETA, int leadersCurrentWGETA, HashMap playerTrackers) {
        D.ebugPrintlnINFO("--- addWinGameETABonusForRoad");
        int ourCurrentWGETA = ourPlayerTracker.getWinGameETA();
        D.ebugPrintlnINFO("ourCurrentWGETA = "+ourCurrentWGETA);


        HashMap trackersCopy = null;
        SOCRoad tmpRoad1 = null;

        D.ebugPrintlnINFO("--- before [start] ---");
        SOCResourceSet originalResources = player.getResources().copy();
        SOCBuildingSpeedEstimate estimate = brain.getEstimator(player.getNumbers());
        //SOCPlayerTracker.playerTrackersDebug(playerTrackers);
        D.ebugPrintlnINFO("--- before [end] ---");
        try {
            SOCResSetBuildTimePair btp = estimate.calculateRollsFast(player.getResources(), SOCGame.ROAD_SET, 50, player.getPortFlags());
            btp.getResources().subtract(SOCGame.ROAD_SET);
            player.getResources().setAmounts(btp.getResources());
        } catch (CutoffExceededException e) {
            D.ebugPrintlnINFO("crap in getWinGameETABonusForRoad - "+e);
        }
        tmpRoad1 = new SOCRoad(player, posRoad.getCoordinates(), null);
        trackersCopy = SOCPlayerTracker.tryPutPiece(tmpRoad1, brain.getGame(), playerTrackers);
        SOCPlayerTracker.updateWinGameETAs(trackersCopy);
        float score = calcWGETABonus(playerTrackers, trackersCopy);

        if (!posRoad.getThreats().isEmpty()) {
            score *= threatMultiplier;
            D.ebugPrintlnINFO("***  (THREAT MULTIPLIER) score * "+threatMultiplier+" = "+score);
        }
        D.ebugPrintlnINFO("*** ETA for road = "+roadETA);
        float etaBonus = getETABonus(roadETA, leadersCurrentWGETA, score);
        D.ebugPrintlnINFO("$$$ score = "+score);
        D.ebugPrintlnINFO("etaBonus = "+etaBonus);
        posRoad.addToScore(etaBonus);

        if ((brain != null) && (brain.getDRecorder().isOn())) {
            brain.getDRecorder().record("ETA = "+roadETA);
            brain.getDRecorder().record("WGETA Score = "+df1.format(score));
            brain.getDRecorder().record("Total road score = "+df1.format(etaBonus));
        } 

        D.ebugPrintlnINFO("--- after [end] ---");
        SOCPlayerTracker.undoTryPutPiece(tmpRoad1, brain.getGame());
        player.getResources().clear();
        player.getResources().add(originalResources);
        D.ebugPrintlnINFO("--- cleanup done ---");

        return etaBonus;
    }

    /**
     * calc the win game eta bonus
     *
     * @param  trackersBefore   list of player trackers before move
     * @param  trackersAfter    list of player trackers after move
     */
    private float calcWGETABonus(HashMap trackersBefore, HashMap trackersAfter) {
        D.ebugPrintlnINFO("^^^^^ calcWGETABonus");
        int originalWGETAs[] = new int[brain.getGame().maxPlayers];	 
        int WGETAdiffs[] = new int[brain.getGame().maxPlayers];	 
        Vector leaders = new Vector();
        int bestWGETA = 1000;
        float bonus = 0;

        Iterator trackersBeforeIter = trackersBefore.values().iterator();
        while (trackersBeforeIter.hasNext()) {
            SOCPlayerTracker trackerBefore = (SOCPlayerTracker)trackersBeforeIter.next();
            D.ebugPrintlnINFO("$$$ win game ETA for player "+trackerBefore.getPlayer().getPlayerNumber()+" = "+trackerBefore.getWinGameETA());
            originalWGETAs[trackerBefore.getPlayer().getPlayerNumber()] = trackerBefore.getWinGameETA();
            WGETAdiffs[trackerBefore.getPlayer().getPlayerNumber()] = trackerBefore.getWinGameETA();

            if (trackerBefore.getWinGameETA() < bestWGETA) {
                bestWGETA = trackerBefore.getWinGameETA();
                leaders.removeAllElements();
                leaders.addElement(trackerBefore);
            } else if (trackerBefore.getWinGameETA() == bestWGETA) {
                leaders.addElement(trackerBefore);
            }
        }

        D.ebugPrintlnINFO("^^^^ bestWGETA = "+bestWGETA);

        bonus = calcWGETABonusAux(originalWGETAs, trackersAfter, leaders);

        D.ebugPrintlnINFO("^^^^ final bonus = "+bonus);

        return bonus;
    }

    /**
     * calcWGETABonusAux
     *
     * @param originalWGETAs   the original WGETAs
     * @param trackersAfter    the playerTrackers after the change
     * @param leaders          a list of leaders
     */
    private float calcWGETABonusAux(int[] originalWGETAs, HashMap trackersAfter, 
            Vector leaders) {
        int WGETAdiffs[] = new int[brain.getGame().maxPlayers];	
        int bestWGETA = 1000;
        float bonus = 0;

        for (int i = 0; i < brain.getGame().maxPlayers; i++) {
            WGETAdiffs[i] = originalWGETAs[i];
            if (originalWGETAs[i] < bestWGETA) {
                bestWGETA = originalWGETAs[i];
            }
        }

        Iterator trackersAfterIter = trackersAfter.values().iterator();
        while (trackersAfterIter.hasNext()) {
            SOCPlayerTracker trackerAfter = (SOCPlayerTracker)trackersAfterIter.next();
            WGETAdiffs[trackerAfter.getPlayer().getPlayerNumber()] -= trackerAfter.getWinGameETA();
            D.ebugPrintlnINFO("$$$ win game ETA diff for player "+trackerAfter.getPlayer().getPlayerNumber()+" = "+WGETAdiffs[trackerAfter.getPlayer().getPlayerNumber()]);
            if (trackerAfter.getPlayer().getPlayerNumber() == player.getPlayerNumber()) {
                if (trackerAfter.getWinGameETA() == 0) {
                    D.ebugPrintlnINFO("$$$$ adding win game bonus : +"+(100 / brain.getGame().maxPlayers));
                    bonus += (100.0f / (float) brain.getGame().maxPlayers);
                    if ((brain != null) && (brain.getDRecorder().isOn())) {
                        brain.getDRecorder().record("Adding Win Game bonus :"+df1.format(bonus));
                    } 
                }
            }
        }

        if ((brain != null) && (brain.getDRecorder().isOn())) {
            brain.getDRecorder().record("WGETA Diffs: "+WGETAdiffs[0]+" "+WGETAdiffs[1]+" "+WGETAdiffs[2]+" "+WGETAdiffs[3]);
        } 

        //
        // bonus is based on lowering your WGETA
        // and increaseing the leaders' WGETA
        //
        if ((originalWGETAs[player.getPlayerNumber()] > 0) &&
                (bonus == 0)) {
            bonus += ((100.0f / (float) brain.getGame().maxPlayers) * ((float)WGETAdiffs[player.getPlayerNumber()] / (float)originalWGETAs[player.getPlayerNumber()]));
        }			

        D.ebugPrintlnINFO("^^^^ our current bonus = "+bonus);
        if ((brain != null) && (brain.getDRecorder().isOn())) {
            brain.getDRecorder().record("WGETA bonus for only myself = "+df1.format(bonus));
        } 

        //
        //  try adding takedown bonus for all other players
        //  other than the leaders
        //
        for (int pn = 0; pn < brain.getGame().maxPlayers; pn++) {
            Enumeration leadersEnum = leaders.elements();
            while (leadersEnum.hasMoreElements()) {
                SOCPlayerTracker leader = (SOCPlayerTracker)leadersEnum.nextElement();
                if ((pn != player.getPlayerNumber()) &&
                        (pn != leader.getPlayer().getPlayerNumber())) {
                    if (originalWGETAs[pn] > 0) {
                        float takedownBonus = -1.0f * (100.0f / (float) brain.getGame().maxPlayers) * adversarialFactor * ((float)WGETAdiffs[pn] / (float)originalWGETAs[pn]) * ((float)bestWGETA / (float)originalWGETAs[pn]);
                        bonus += takedownBonus;
                        D.ebugPrintlnINFO("^^^^ added takedown bonus for player "+pn+" : "+takedownBonus);
                        if (((brain != null) && (brain.getDRecorder().isOn())) && (takedownBonus != 0)) {
                            brain.getDRecorder().record("Bonus for AI with "+pn+" : "+df1.format(takedownBonus));
                        } 
                    } else if (WGETAdiffs[pn] < 0) {
                        float takedownBonus = (100.0f / (float) brain.getGame().maxPlayers) * adversarialFactor;
                        bonus += takedownBonus;
                        D.ebugPrintlnINFO("^^^^ added takedown bonus for player "+pn+" : "+takedownBonus);
                        if (((brain != null) && (brain.getDRecorder().isOn())) && (takedownBonus != 0)) {
                            brain.getDRecorder().record("Bonus for AI with "+pn+" : "+df1.format(takedownBonus));
                        } 
                    }
                }
            }
        }

        //
        //  take down bonus for leaders
        //
        Enumeration leadersEnum = leaders.elements();
        while (leadersEnum.hasMoreElements()) {
            SOCPlayerTracker leader = (SOCPlayerTracker)leadersEnum.nextElement();
            if (leader.getPlayer().getPlayerNumber() != player.getPlayerNumber()) {
                if (originalWGETAs[leader.getPlayer().getPlayerNumber()] > 0) {
                    float takedownBonus = -1.0f * (100.0f / (float) brain.getGame().maxPlayers) * leaderAdversarialFactor * ((float)WGETAdiffs[leader.getPlayer().getPlayerNumber()] / (float)originalWGETAs[leader.getPlayer().getPlayerNumber()]);
                    bonus += takedownBonus;
                    D.ebugPrintlnINFO("^^^^ added takedown bonus for leader "+leader.getPlayer().getPlayerNumber()+" : +"+takedownBonus);
                    if (((brain != null) && (brain.getDRecorder().isOn())) && (takedownBonus != 0)){
                        brain.getDRecorder().record("Bonus for LI with "+leader.getPlayer().getName()+" : +"+df1.format(takedownBonus));
                    } 

                } else if (WGETAdiffs[leader.getPlayer().getPlayerNumber()] < 0) {
                    float takedownBonus = (100.0f / (float) brain.getGame().maxPlayers) * leaderAdversarialFactor;
                    bonus += takedownBonus;
                    D.ebugPrintlnINFO("^^^^ added takedown bonus for leader "+leader.getPlayer().getPlayerNumber()+" : +"+takedownBonus);
                    if (((brain != null) && (brain.getDRecorder().isOn())) && (takedownBonus != 0)) {
                        brain.getDRecorder().record("Bonus for LI with "+leader.getPlayer().getName()+" : +"+df1.format(takedownBonus));
                    } 
                }
            }
        }
        if ((brain != null) && (brain.getDRecorder().isOn())) {
            brain.getDRecorder().record("WGETA bonus = "+df1.format(bonus));
        } 

        return bonus;
    }

    /**
     * calc eta bonus
     *
     * @param leadWGETA  the wgeta of the leader
     * @param eta  the building eta
     * @return the eta bonus
     */
    private float getETABonus(int eta, int leadWGETA, float bonus) {
        D.ebugPrintlnINFO("**** getETABonus ****");
        //return Math.round(etaBonusFactor * ((100f * ((float)(maxGameLength - leadWGETA - eta) / (float)maxGameLength)) * (1.0f - ((float)leadWGETA / (float)maxGameLength))));

        if (D.ebugOn) {
            D.ebugPrintlnINFO("etaBonusFactor = "+etaBonusFactor);
            D.ebugPrintlnINFO("etaBonusFactor * 100.0 = "+(etaBonusFactor * 100.0f));
            D.ebugPrintlnINFO("eta = "+eta);
            D.ebugPrintlnINFO("maxETA = "+maxETA);
            D.ebugPrintlnINFO("eta / maxETA = "+((float)eta / (float)maxETA));
            D.ebugPrintlnINFO("1.0 - ((float)eta / (float)maxETA) = "+(1.0f - ((float)eta / (float)maxETA)));
            D.ebugPrintlnINFO("leadWGETA = "+leadWGETA);
            D.ebugPrintlnINFO("maxGameLength = "+maxGameLength);
            D.ebugPrintlnINFO("1.0 - ((float)leadWGETA / (float)maxGameLength) = "+(1.0f - ((float)leadWGETA / (float)maxGameLength)));
        }


        //return etaBonusFactor * 100.0f * ((1.0f - ((float)eta / (float)maxETA)) * (1.0f - ((float)leadWGETA / (float)maxGameLength)));

        return (bonus / (float)Math.pow((1+etaBonusFactor), eta));

        //return (bonus * (float)Math.pow(etaBonusFactor, ((float)(eta*eta*eta)/(float)1000.0)));
    }

    /**
     * calculate dev card score
     * @param cardETA estimated time to buy a card
     * @param leadersCurrentWGETA the leading player's estimated time to win the game
     * @return
     */
    private SOCPossibleCard getDevCardScore(int cardETA, int leadersCurrentWGETA) {
        float devCardScore = 0;
        D.ebugPrintlnINFO("$$$ devCardScore = +"+devCardScore);
        D.ebugPrintlnINFO("--- before [start] ---");
        // int ourCurrentWGETA = ourPlayerTracker.getWinGameETA();
        int WGETAdiffs[] = new int[brain.getGame().maxPlayers];
        int originalWGETAs[] = new int[brain.getGame().maxPlayers];	 
        int bestWGETA = 1000;
        Vector leaders = new Vector();
        Iterator trackersIter = playerTrackers.values().iterator();
        while (trackersIter.hasNext()) {
            SOCPlayerTracker tracker = (SOCPlayerTracker)trackersIter.next();
            originalWGETAs[tracker.getPlayer().getPlayerNumber()] = tracker.getWinGameETA();
            WGETAdiffs[tracker.getPlayer().getPlayerNumber()] = tracker.getWinGameETA();
            D.ebugPrintlnINFO("$$$$ win game ETA for player "+tracker.getPlayer().getPlayerNumber()+" = "+tracker.getWinGameETA());

            if (tracker.getWinGameETA() < bestWGETA) {
                bestWGETA = tracker.getWinGameETA();
                leaders.removeAllElements();
                leaders.addElement(tracker);
            } else if (tracker.getWinGameETA() == bestWGETA) {
                leaders.addElement(tracker);
            }
        }

        if ((brain != null) && (brain.getDRecorder().isOn())) {
            brain.getDRecorder().record("Estimating Knight card value ...");
        } 

        player.getGame().saveLargestArmyState();
        D.ebugPrintlnINFO("--- before [end] ---");
        player.setNumKnights(player.getNumKnights()+1);
        player.getGame().updateLargestArmy();
        D.ebugPrintlnINFO("--- after [start] ---");
        SOCPlayerTracker.updateWinGameETAs(playerTrackers);

        float bonus = calcWGETABonusAux(originalWGETAs, playerTrackers, leaders);

        //
        //  adjust for knight card distribution
        //
        D.ebugPrintlnINFO("^^^^ raw bonus = "+bonus);

        bonus *= 0.58f;
        D.ebugPrintlnINFO("^^^^ adjusted bonus = "+bonus);
        if ((brain != null) && (brain.getDRecorder().isOn())) {
            brain.getDRecorder().record("Bonus * 0.58 = "+df1.format(bonus));
        } 

        D.ebugPrintlnINFO("^^^^ bonus for +1 knight = "+bonus);
        devCardScore += bonus;

        D.ebugPrintlnINFO("--- after [end] ---");
        player.setNumKnights(player.getNumKnights()-1);
        player.getGame().restoreLargestArmyState();
        D.ebugPrintlnINFO("--- cleanup done ---");

        if ((brain != null) && (brain.getDRecorder().isOn())) {
            brain.getDRecorder().record("Estimating vp card value ...");
        } 

        //
        // see what a vp card does to our win game eta
        //
        D.ebugPrintlnINFO("--- before [start] ---");
        if ((brain != null) && (brain.getDRecorder().isOn())) {
            brain.getDRecorder().suspend();
        }
        SOCPlayerTracker.updateWinGameETAs(playerTrackers);
        if ((brain != null) && (brain.getDRecorder().isOn())) {
            brain.getDRecorder().resume();
        }
        D.ebugPrintlnINFO("--- before [end] ---");
        player.getDevCards().add(1, SOCDevCardSet.NEW, SOCDevCardConstants.CAP);
        D.ebugPrintlnINFO("--- after [start] ---");
        SOCPlayerTracker.updateWinGameETAs(playerTrackers);

        bonus = calcWGETABonusAux(originalWGETAs, playerTrackers, leaders);

        D.ebugPrintlnINFO("^^^^ our current bonus = "+bonus);

        //
        //  adjust for +1 vp card distribution
        //
        bonus *= 0.21f;
        D.ebugPrintlnINFO("^^^^ adjusted bonus = "+bonus);
        if ((brain != null) && (brain.getDRecorder().isOn())) {
            brain.getDRecorder().record("Bonus * 0.21 = "+df1.format(bonus));
        } 

        D.ebugPrintlnINFO("$$$ win game ETA bonus for +1 vp: "+bonus);
        devCardScore += bonus;

        D.ebugPrintlnINFO("--- after [end] ---");
        player.getDevCards().subtract(1, SOCDevCardSet.NEW, SOCDevCardConstants.CAP);
        D.ebugPrintlnINFO("--- cleanup done ---");

        //
        // add misc bonus
        //
        devCardScore += devCardMultiplier;
        D.ebugPrintlnINFO("^^^^ misc bonus = "+devCardMultiplier);
        if ((brain != null) && (brain.getDRecorder().isOn())) {
            brain.getDRecorder().record("Misc bonus = "+df1.format(devCardMultiplier));
        } 

        float score = getETABonus(cardETA, leadersCurrentWGETA, devCardScore);

        D.ebugPrintlnINFO("$$$$$ devCardScore = "+devCardScore);
        D.ebugPrintlnINFO("$$$$$ devCardETA = "+cardETA);
        D.ebugPrintlnINFO("$$$$$ final score = "+score);

        if ((brain != null) && (brain.getDRecorder().isOn())) {
            brain.getDRecorder().record("ETA = "+cardETA);
            brain.getDRecorder().record("dev card score = "+df1.format(devCardScore));
            brain.getDRecorder().record("Total dev card score = "+df1.format(score));
        } 

        SOCPossibleCard posCard = new SOCPossibleCard(player, cardETA);
        posCard.addToScore(score);

        return posCard;
    }

	/**
	 * @return the coordinate of the best next legal location to place the initial settlement
	 */
    protected int getNextInitialSettlements() { 
        List<SettlementNode> legalSettlements = brain.getLegalSettlements();
        rankSettlements(legalSettlements, true); // NB: Our first settlement, we have no other settlements, so considerCurrent is irrelevant 
        return legalSettlements.get(0).getNode();
    }

    /**
     * Plan our initial road.  
     * 1) Rank remaining nodes, based on unconditional value (add opponent modeling later).
     * 2) Based on how many people are left to go (note existing logic uses people before our next placement, which is horribly wrong), trim the 
     *    n+1 best nodes (have a little uncertainty - maybe make geometric?), along with any nodes invalidated by placements there
     * 3) Determine which of the 6 possible neighbours are legal based on this criterion (ie which are worth aiming for)
     * 4) Rank any legal targets
     * 5) Aim for the best legal target
     * 
     * TODO: What if there are zero legal targets? 
     *   Consider making the "likelihood" of a node remaining probabilistic, score the nodes and multiply it by the probability it's there.
     */
    public int[] planInitRoad(int settlementNode) {	
        List<SettlementNode> legalSettlements = brain.getLegalSettlements();
        rankSettlements(legalSettlements, false);

        // Initialize a dummy player to simulate placement of remaining settlements
        SOCPlayer dummy = new SOCPlayer(player.getPlayerNumber(), brain.getMemory().getGame());
        Vector<Integer> legalSettlementNodes = new Vector<Integer>();
        for (SettlementNode n : legalSettlements) {
            legalSettlementNodes.add(Integer.valueOf(n.getNode()));
        }
        dummy.setPotentialSettlements(legalSettlementNodes); 

        // How many are left to place?
        int remainingSettlements = 8 - brain.getMemory().getBoard().getSettlements().size();
        int i=0;
        // Mark off the n-best.  If we see a settlement that has been previously marked as blocked, mark it as blocked anyway, but don't decrement the 
        //  remaining settlements to allocate.  It will frequently be the case that similarly ranked settlements are neighbours, and we should assume a 
        //  worst case scenario that both are blocked
        while (remainingSettlements>0 && i<legalSettlements.size()) {
            int n = legalSettlements.get(i).getNode();
            SOCSettlement thisNode = new SOCSettlement(player, n, null);
            if (dummy.isPotentialSettlement(n)) {
                dummy.updatePotentials(thisNode);
                remainingSettlements--;
            }
            else {
                // if it's not currently potential, mark it as used anyway - be safe!
                dummy.updatePotentials(thisNode);
            }
            i++;
        }
        //List<SettlementNode> likelyAvailSettlements = getLegalSettlements(dummy);

        // Intersect this with 
        List<Integer> nodesTwoAway = brain.getMemory().getBoard().getAdjacentNodesToNode2Away(settlementNode);
        List<SettlementNode> likelyTargets = new ArrayList<SettlementNode>();
        List<SettlementNode> legalTargets = new ArrayList<SettlementNode>();
        for (Integer n : nodesTwoAway) {
            SettlementNode node = new SettlementNode(n, brain.getMemory().getBoard());
            if (dummy.isPotentialSettlement(node.getNode())) { //likelyAvailSettlements.contains(node)) {
                if (legalSettlements.contains(node)) {
                    likelyTargets.add(node);
                    legalTargets.add(node);
                }
                else {
                    D.ebugWARNING("Potential settlement is not legal: " + node.getNode());
                }
            }
            else if (legalSettlements.contains(node)) {
                legalTargets.add(node);
            }
        }
        SettlementNode target = null;
        if (likelyTargets.size() > 0) {
            // TODO: Consider multiple settlements we can get by going in a certain direction
            rankSettlements(likelyTargets, true);
            target = likelyTargets.get(0);
        }
        else if (legalTargets.size() > 0) {
            // If there are no targets which are likely to be available, head for the best one that is currently legal and cross your fingers
            rankSettlements(legalTargets, true);
            target = legalTargets.get(0);			
        }
        else {
            // Unlikely to have anywhere close to go from here.  What to do?  
            //  Consider further distances
            //  Consider longest road potential
            // For now, just take a wild guess - aim for the first element in nodesTwoAway
            target = new SettlementNode(nodesTwoAway.get(0), brain.getMemory().getBoard());
        }

        int roadEdge = brain.getMemory().getBoard().getAdjacentEdgeToNode2Away(settlementNode, target.getNode());

        return new int[] {roadEdge, target.getNode() };
    }	

    /**
     * Ranks a set of legal locations to build a settlement
     * TODO: Modify this to allow use for rankCities
     * @param nodes the nodes to rank
     * @param considerCurrent consider the already built settlement(s) or not
     */
    protected void rankSettlements(List<SettlementNode> nodes, boolean considerCurrent) {
        for (SettlementNode node : nodes) {
            node.setScore(getScore(node, considerCurrent));
        }		
        Collections.sort(nodes);
    }

    /**
     * Compute a value of a potential settlement.  Optionally consider
     * interactions with existing production 
     *  		
     * @param nodeNum
     * @param considerCurrent true if we want to compute the score for a set including our existing settlements plus this one
     * @return
     */
    protected double getScore(SettlementNode node, boolean considerCurrent) {
        List<SettlementNode> nodes = new ArrayList<SettlementNode>();
        nodes.add(node);
        if (considerCurrent) {
        	Vector settlementPieces = player.getSettlements();
        	List<SettlementNode> currentSettlements = new ArrayList<SettlementNode>();
        	for(Object piece : settlementPieces){
        		currentSettlements.add(new SettlementNode(((SOCPlayingPiece)piece).getCoordinates(), brain.getGame().getBoard()));
        	}
            nodes.addAll(currentSettlements);
        }
        return getScore(nodes);		
    }	

    protected double getScore(List<SettlementNode> nodes) {
        // Loop through all settlements, determine what hexes and numbers are covered, and how many times
        //  This allows us to discount doubled values (eg score(2*6) < score(6, 8)).  
        //  These are undesirable due to increasing variance (though we may want to flip that if you already need a miracle),
        //    and due to the fact it leads to robber 
        // figure out what hexes are next to it, what they produce
        // also get ports
        // TODO: This only changes when we build settlements/cities, so should only do this
        //  when we build - then we only need to consider the new one under consideration.
        int[] numPerRoll = new int[13];  
        Map<Integer, Integer> numPerHex = new HashMap<Integer, Integer>();
        for (SettlementNode node : nodes) {
            // Note: if we are evaluating a node without a settlement, it's a potential settlement, so treat it as one here
            int m = Math.max(node.getIncome(), 1);
            for (Hex hex : node.getHexes()) {
                numPerRoll[hex.getRoll()] += m;
                Integer nph = numPerHex.get(Integer.valueOf(hex.getCoord()));
                if (nph==null) {
                    nph = Integer.valueOf(m);
                }
                else {
                    nph = Integer.valueOf(nph + m);
                }
                numPerHex.put(Integer.valueOf(hex.getCoord()), nph);
            }
        }		

        // discount factors - to be experimented with.
        double doubleRollDiscount = 0.95;
        double doubleHexDiscount = 0.7; // Note that double hex is also a double roll by definition

        double[] resourcePer36 = new double[6]; // robber is zero		
        double totalResPer36 =0;

        boolean[] port = new boolean[6];
        // Now loop through again and increment
        for (SettlementNode node : nodes) {
            // Note: if we are evaluating a node without a settlement, it's a potential settlement, so treat it as one here
            int m = Math.max(node.getIncome(), 1);
            for (Hex hex : node.getHexes()) {
                double mult = m;
                if (numPerRoll[hex.getRoll()] > 1) {
                    mult *= doubleRollDiscount;
                    if (numPerHex.get(hex.getCoord()).intValue() > 1) {
                        mult *= doubleHexDiscount;
                    }
                }
                resourcePer36[hex.getType()] += mult * hex.getRollsPer36();
                if (hex.getType()>0) {
                    totalResPer36 += mult * hex.getRollsPer36();
                }
            }

            if (node.getPortType() >= 0) {
                port[node.getPortType()] = true;
            }
        }

        // now calculate score...
        double score = 0;

        // Add basic scores
        //  TODO: add appropriate valuation multipliers here based on rarity?
        score += resourcePer36[SOCResourceConstants.CLAY];
        score += resourcePer36[SOCResourceConstants.WOOD];
        score += resourcePer36[SOCResourceConstants.WHEAT];
        score += resourcePer36[SOCResourceConstants.SHEEP];
        score += resourcePer36[SOCResourceConstants.ORE];

        //  The number of resources of a given type we can expect to get through bank/port trades
        double[] portRes = new double[6];
        double[] portOrRollPer36 = new double[6];	
        for (int i=1; i<6; i++) {
            for (int j=1; j<6; j++) {
                if (j!=i) {
                    // Determine the exchange rate based on whether ports are owned
                    if (port[j]) {
                        portRes[i] += resourcePer36[j] / 2.0;
                    }
                    else if (port[0]) {
                        portRes[i] += resourcePer36[j] / 3.0;
                    }
                    else {
                        portRes[i] += resourcePer36[j] / 4.0;
                    }
                }
            }
            // TODO: Add a discount factor for acquiring resources through ports?
            portOrRollPer36[i] = resourcePer36[i] + portRes[i];
        }

        // complementary combinations with arbitrary multipliers, which seem
        //  to work reasonably well in practice

        // ROAD
        double favour_roads_factor = 0.5;
        if (brain.isRobotType(StacRobotType.FAVOUR_ROADS_INITIALLY)) {
            favour_roads_factor = 0.7;
        }
        score += 0.5 * Math.pow(portOrRollPer36[SOCResourceConstants.CLAY] 
                * portOrRollPer36[SOCResourceConstants.WOOD]
                        , favour_roads_factor);

        // SETTLMENT
        double favour_settlements_factor = 0.25;
        if (brain.isRobotType(StacRobotType.FAVOUR_SETTLEMENTS_INITIALLY)) {
            favour_settlements_factor = 0.5;
        }
        score += Math.pow(portOrRollPer36[SOCResourceConstants.CLAY] 
                * portOrRollPer36[SOCResourceConstants.WOOD]
                        * portOrRollPer36[SOCResourceConstants.WHEAT]
                                * portOrRollPer36[SOCResourceConstants.SHEEP]
                                        , favour_settlements_factor);

        // CITY
        double favour_cities_factor = 0.2;
        if (brain.isRobotType(StacRobotType.FAVOUR_CITIES_INITIALLY)) {
            favour_cities_factor = (Double)brain.getTypeParam(StacRobotType.FAVOUR_CITIES_INITIALLY);
        }
        score += Math.pow(Math.pow(portOrRollPer36[SOCResourceConstants.ORE], 3) 
                * Math.pow(portOrRollPer36[SOCResourceConstants.WHEAT], 2)
                , favour_cities_factor);
                  
        // DEV CARD
        double favour_dev_cards_factor = 0.3333;
        if (brain.isRobotType(StacRobotType.FAVOUR_DEV_CARDS_INITIALLY)) {
            favour_dev_cards_factor = (Double)brain.getTypeParam(StacRobotType.FAVOUR_DEV_CARDS_INITIALLY);
        }
        score += 0.25 * Math.pow(portOrRollPer36[SOCResourceConstants.ORE] 
                * portOrRollPer36[SOCResourceConstants.SHEEP]
                        * portOrRollPer36[SOCResourceConstants.WHEAT]
                                , favour_dev_cards_factor);

        // TODO: Bonus for nearby settlements to extend to (should be wary when we're in initial placement phase, likely to get blocked)
        return score;
    }

    @Override
    public int selectMoveRobber(int robberHex) {
        if (brain.isRobotType(StacRobotType.ROB_FOR_NEED)) {
            int victim = selectPlayerToRobForNeed(robberHex);
            return oldDM.selectRobberHex(robberHex, victim);
        }
        else {
            return oldDM.selectMoveRobber(robberHex);
        }
    }
    
    /**
     * Select a robbing victim based on where we are most likely to acquire our most needed resource
     * @param robberHex the current robber location
     * @return
     */
    protected int selectPlayerToRobForNeed(int robberHex) {
        // Determine what our most needed resource is - for now, just pick the one we get least of            
        SOCBuildingSpeedEstimate estimate = brain.getEstimator(brain.getPlayerData().getNumbers());

        int[] rollsPerRes = estimate.getRollsPerResource();
        int mostNeededRes=0, mostNeededRolls = 0;               
       
        for (int i=SOCResourceConstants.CLAY; i<SOCResourceConstants.UNKNOWN; i++) {
            if (rollsPerRes[i]>mostNeededRolls) {
                mostNeededRes = i;
                mostNeededRolls = rollsPerRes[i];
            }
        }
        
        // Where are we most likely to get it from?
        int bestVictim = -1;
        double bestProb = 0;
        for (int i=0; i<4; i++) {
            if (i!=brain.getPlayerData().getPlayerNumber()) {
                SOCResourceSet rs =  brain.getMemory().getOpponentResources(i);               
                int amt = rs.getAmount(mostNeededRes);
                int total = rs.getTotal();
                double prob = (double) amt / (double) total;
                if (prob > bestProb) {
                    bestVictim = i;
                    bestProb = prob;
                }
            }
        }
        
        // if nobody has the resource we're looking for, default to thwart-best behaviour
        if (bestVictim<0) {
            bestVictim = oldDM.selectPlayerToThwart(robberHex);
        }
        return bestVictim;
    }

    @Override
    public boolean shouldPlayKnightForLA() {
        return oldDM.shouldPlayKnightForLA();
    }

    @Override
    public boolean shouldPlayKnight(boolean hasRolled) {
        return oldDM.shouldPlayKnight(hasRolled);
    }

    @Override
    public boolean shouldPlayRoadbuilding() {
        return oldDM.shouldPlayRoadbuilding();
    }

    @Override
    public boolean shouldPlayDiscovery() {
        return oldDM.shouldPlayDiscovery();
    }

    @Override
    public void planInitialSettlements() {
        firstSettlement = getNextInitialSettlements();        
    }

    @Override
    public void planSecondSettlement() {
        secondSettlement = getNextInitialSettlements();
    }

    @Override
    public boolean chooseMonopoly() {
        return oldDM.chooseMonopoly();
    }

    @Override
    public void chooseFreeResources(SOCBuildPlanStack buildingPlan) {
        oldDM.chooseFreeResources(buildingPlan);
    }

	@Override
	public int choosePlayerToRob() {
		return -1; //return the unset choice
	}


}
