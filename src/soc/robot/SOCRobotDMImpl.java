package soc.robot;

import java.text.DecimalFormat;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Stack;
import java.util.Vector;

import soc.disableDebug.D;
import soc.game.SOCBoard;
import soc.game.SOCCity;
import soc.game.SOCDevCardConstants;
import soc.game.SOCDevCardSet;
import soc.game.SOCGame;
import soc.game.SOCLRPathData;
import soc.game.SOCPlayer;
import soc.game.SOCPlayerNumbers;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCRoad;
import soc.game.SOCSettlement;
import soc.util.CutoffExceededException;
import soc.util.NodeLenVis;
import soc.util.Pair;
import soc.util.Queue;
import soc.util.SOCRobotParameters;

/**
 * Refactored to provide a layer of abstraction.
 * @author kho30
 *
 */
public class SOCRobotDMImpl extends SOCRobotDM<SOCBuildPlanStack> {

	/**
	 * The brain providing access to some utilities and fields
	 */
    protected final SOCRobotBrain<?, ?, SOCBuildPlanStack> brain;
    
    /**
     * A reference to the game object stored in the brain
     */
    protected SOCGame game;

    /**
     * used to cache resource estimates for the board
     */
    protected int[] resourceEstimates;

    /**
     * constructor
     *
     * @param br  the robot brain
     */
    public SOCRobotDMImpl(SOCRobotBrain<?, ?, SOCBuildPlanStack> br, int strategyType, SOCBuildPlanStack plan) {
        super();
        this.strategy=strategyType;
        brain = br;
        playerTrackers = brain.getPlayerTrackers();
        ourPlayerTracker = brain.getOurPlayerTracker();
        player = brain.getOurPlayerData();
        buildingPlan = plan;
        game = brain.getGame();

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


    /**
     * constructor
     * 
     * this is if you don't want to use a brain
     *
     * @param params  the robot parameters
     * @param pt   the player trackers
     * @param opt  our player tracker
     * @param opd  our player data
     * @param bp   our building plan
     */
    public SOCRobotDMImpl(SOCRobotParameters params,
            HashMap pt,
            SOCPlayerTracker opt,
            SOCPlayer opd,
            SOCBuildPlanStack bp,
            int strategyType) {
        super();
        this.strategy = strategyType;
        brain = null;
        playerTrackers = pt;
        ourPlayerTracker = opt;
        player = opd;
        buildingPlan = bp;
        game = player.getGame();

        maxGameLength = params.getMaxGameLength();
        maxETA = params.getMaxETA();
        etaBonusFactor = params.getETABonusFactor();
        adversarialFactor = params.getAdversarialFactor();
        leaderAdversarialFactor = params.getLeaderAdversarialFactor();
        devCardMultiplier = params.getDevCardMultiplier();
        threatMultiplier = params.getThreatMultiplier();

        threatenedRoads = new Vector();
        goodRoads = new Vector();
        threatenedSettlements = new Vector();
        goodSettlements = new Vector();
    }


    /**
     * @return favorite settlement
     */
    public SOCPossibleSettlement getFavoriteSettlement() {
        return favoriteSettlement;
    }

    /**
     * @return favorite city
     */
    public SOCPossibleCity getFavoriteCity() {
        return favoriteCity;
    }

    /**
     * @return favorite road
     */
    public SOCPossibleRoad getFavoriteRoad() {
        return favoriteRoad;
    }

    /**
     * @return possible card
     */
    public SOCPossibleCard getPossibleCard() {
        return possibleCard;
    }

    /**
     * make some building plans.
     * Calls either {@link #smartGameStrategy(int[])} or {@link #dumbFastGameStrategy(int[])}.
     * Both of these will check whether this is our normal turn, or if
     * it's the 6-player board's {@link SOCGame#SPECIAL_BUILDING Special Building Phase}.
     *
     * @param strategy  an integer that determines which strategy is used (SMART_STRATEGY | FAST_STRATEGY)
     */
    public void planStuff() {
        //long startTime = System.currentTimeMillis();
        D.ebugPrintlnINFO("PLANSTUFF");

        SOCBuildingSpeedEstimate currentBSE = getEstimator(player.getNumbers());
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
            dumbFastGameStrategy(currentBuildingETAs);
            break;
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

                HashMap trackersCopy = SOCPlayerTracker.tryPutPiece(tmpRoad, game, playerTrackers);
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

                SOCPlayerTracker.undoTryPutPiece(tmpRoad, game);

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
    }

    /**
     * dumbFastGameStrategy
     * uses rules to determine what to build next
     *
     * @param buildingETAs  the etas for building something
     */
    private void dumbFastGameStrategy(int[] buildingETAs)
    {
        D.ebugPrintlnINFO("***** dumbFastGameStrategy *****");

        // If this game is on the 6-player board, check whether we're planning for
        // the Special Building Phase.  Can't buy cards or trade in that phase.
        final boolean forSpecialBuildingPhase =
                game.isSpecialBuilding() || (game.getCurrentPlayerNumber() != player.getPlayerNumber());

        int bestETA = 500;
        SOCBuildingSpeedEstimate ourBSE = getEstimator(player.getNumbers());

        if (player.getTotalVP() < 5) {
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
                    D.ebugPrintlnINFO("Estimate speedup of city at "+game.getBoard().nodeCoordToString(posCity.getCoordinates()));
                    D.ebugPrintlnINFO("Speedup = "+posCity.getSpeedupTotal());
                    D.ebugPrintlnINFO("ETA = "+buildingETAs[SOCBuildingSpeedEstimate.CITY]);
                    if ((brain != null) && (brain.getDRecorder().isOn())) {
                        brain.getDRecorder().startRecording("CITY"+posCity.getCoordinates());
                        brain.getDRecorder().record("Estimate speedup of city at "+game.getBoard().nodeCoordToString(posCity.getCoordinates()));
                        brain.getDRecorder().record("Speedup = "+posCity.getSpeedupTotal());
                        brain.getDRecorder().record("ETA = "+buildingETAs[SOCBuildingSpeedEstimate.CITY]);
                        brain.getDRecorder().stopRecording();
                    }
                    if ((favoriteCity == null) ||
                            (posCity.getSpeedupTotal() > favoriteCity.getSpeedupTotal())) {
                        favoriteCity = posCity;
                        bestETA = buildingETAs[SOCBuildingSpeedEstimate.CITY];
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
                    brain.getDRecorder().record("Estimate speedup of stlmt at "+game.getBoard().nodeCoordToString(posSet.getCoordinates()));
                    brain.getDRecorder().record("Speedup = "+posSet.getSpeedupTotal());
                    brain.getDRecorder().record("ETA = "+posSet.getETA());
                    Stack roadPath = posSet.getRoadPath();
                    if (roadPath!= null) {
                        brain.getDRecorder().record("Path:");
                        Iterator rpIter = roadPath.iterator();
                        while (rpIter.hasNext()) {
                            SOCPossibleRoad posRoad = (SOCPossibleRoad)rpIter.next();
                            brain.getDRecorder().record("Road at "+game.getBoard().edgeCoordToString(posRoad.getCoordinates()));
                        }
                    }
                    brain.getDRecorder().stopRecording();
                }
                if (posSet.getETA() < bestETA) {
                    bestETA = posSet.getETA();
                    favoriteSettlement = posSet;
                } else if (posSet.getETA() == bestETA) {
                    if (favoriteSettlement == null) {
                        if ((favoriteCity == null) || 
                                (posSet.getSpeedupTotal() > favoriteCity.getSpeedupTotal())) {
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
                D.ebugPrintlnINFO("Picked favorite settlement at "+game.getBoard().nodeCoordToString(favoriteSettlement.getCoordinates()));
                buildingPlan.push(favoriteSettlement);
                if (!favoriteSettlement.getNecessaryRoads().isEmpty()) {
                    //
                    // we need to build roads first
                    //	  
                    Stack roadPath = favoriteSettlement.getRoadPath();
                    while (!roadPath.empty()) {
                        buildingPlan.push( (SOCPossiblePiece) roadPath.pop());
                    }
                }
            } else if (favoriteCity != null) {
                //
                // we want to build a city
                //
                D.ebugPrintlnINFO("Picked favorite city at "+game.getBoard().nodeCoordToString(favoriteCity.getCoordinates()));
                buildingPlan.push(favoriteCity);
            } else {
                //
                // we can't build a settlement or city
                //
                if ((game.getNumDevCards() > 0) && ! forSpecialBuildingPhase)
                {
                    //
                    // buy a card if there are any left
                    //
                    D.ebugPrintlnINFO("Buy a card");
                    SOCPossibleCard posCard = new SOCPossibleCard(player, buildingETAs[SOCBuildingSpeedEstimate.CARD]);
                    buildingPlan.push(posCard);
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
            int laSize = 0;
            SOCPlayer laPlayer = game.getPlayerWithLargestArmy();
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
            int knightsToBuy = 0;
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
            D.ebugPrintlnINFO("laETA = "+laETA);

            //
            // consider Longest Road
            //
            D.ebugPrintlnINFO("Calculating Longest Road ETA");
            int lrETA = 500;
            Stack bestLRPath = null;
            int lrLength;
            SOCPlayer lrPlayer = game.getPlayerWithLongestRoad();
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
                        brain.getDRecorder().record("Estimate speedup of city at "+game.getBoard().nodeCoordToString(posCity.getCoordinates()));
                        brain.getDRecorder().record("Speedup = "+posCity.getSpeedupTotal());
                        brain.getDRecorder().record("ETA = "+buildingETAs[SOCBuildingSpeedEstimate.CITY]);
                        brain.getDRecorder().stopRecording();
                    }
                    if ((favoriteCity == null) ||
                            (posCity.getSpeedupTotal() > favoriteCity.getSpeedupTotal())) {
                        favoriteCity = posCity;
                        bestETA = buildingETAs[SOCBuildingSpeedEstimate.CITY];
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
                        brain.getDRecorder().record("Estimate speedup of stlmt at "+game.getBoard().nodeCoordToString(posSet.getCoordinates()));
                        brain.getDRecorder().record("Speedup = "+posSet.getSpeedupTotal());
                        brain.getDRecorder().record("ETA = "+posSet.getETA());
                        Stack roadPath = posSet.getRoadPath();
                        if (roadPath!= null) {
                            brain.getDRecorder().record("Path:");
                            Iterator rpIter = roadPath.iterator();
                            while (rpIter.hasNext()) {
                                SOCPossibleRoad posRoad = (SOCPossibleRoad)rpIter.next();
                                brain.getDRecorder().record("Road at "+game.getBoard().edgeCoordToString(posRoad.getCoordinates()));
                            }
                        }
                        brain.getDRecorder().stopRecording();
                    }
                    if ((posSet.getRoadPath() == null) ||
                            (player.getNumPieces(SOCPlayingPiece.ROAD) >= posSet.getRoadPath().size())) {
                        if (posSet.getETA() < bestETA) {
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
                if (! forSpecialBuildingPhase)
                {
                    for (int i = 0; i < knightsToBuy; i++)
                    {
                        SOCPossibleCard posCard = new SOCPossibleCard(player, 1);
                        buildingPlan.push(posCard);
                    }
                }
                break;

            case LR_CHOICE:
                D.ebugPrintlnINFO("Picked LR");
                while (!bestLRPath.empty()) {
                    SOCPossibleRoad pr = (SOCPossibleRoad)bestLRPath.pop();
                    D.ebugPrintlnINFO("LR road at "+game.getBoard().edgeCoordToString(pr.getCoordinates()));
                    buildingPlan.push(pr);
                }
                break;

            case CITY_CHOICE:
                D.ebugPrintlnINFO("Picked favorite city at "+game.getBoard().nodeCoordToString(favoriteCity.getCoordinates()));
                buildingPlan.push(favoriteCity);
                break;

            case SETTLEMENT_CHOICE:
                D.ebugPrintlnINFO("Picked favorite settlement at "+game.getBoard().nodeCoordToString(favoriteSettlement.getCoordinates()));
                buildingPlan.push(favoriteSettlement);
                if (!favoriteSettlement.getNecessaryRoads().isEmpty()) {
                    //
                    // we need to build roads first
                    //	  
                    Stack roadPath = favoriteSettlement.getRoadPath();
                    while (!roadPath.empty()) {
                        SOCPossibleRoad pr = (SOCPossibleRoad)roadPath.pop();
                        D.ebugPrintlnINFO("Nec road at "+game.getBoard().edgeCoordToString(pr.getCoordinates()));
                        buildingPlan.push(pr);
                    }
                }
            }
        }
    }

    /**
     * score all possible settlements by getting their speedup total
     * calculate ETA by finding shortest path and then using a
     * SOCBuildingSpeedEstimate to find the ETA
     *
     * @param settlementETA  eta for building a settlement from now
     * @param ourBSE the building speed estimator this decision maker is using
     */
    private void scoreSettlementsForDumb(int settlementETA, SOCBuildingSpeedEstimate ourBSE) {
        D.ebugPrintlnINFO("-- scoreSettlementsForDumb --");
        Queue queue = new Queue();
        Iterator posSetsIter = ourPlayerTracker.getPossibleSettlements().values().iterator();
        while (posSetsIter.hasNext()) {
            SOCPossibleSettlement posSet = (SOCPossibleSettlement)posSetsIter.next();
            D.ebugPrintlnINFO("Estimate speedup of stlmt at "+game.getBoard().nodeCoordToString(posSet.getCoordinates()));
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
                    D.ebugPrintlnINFO("-- queuing necessary road at "+game.getBoard().edgeCoordToString(necRoad.getCoordinates()));
                    queue.put(new Pair(necRoad, null));
                }
                //
                // Do a BFS of the necessary road paths looking for the shortest one.
                //
                while (!queue.empty()) {
                    Pair dataPair = (Pair)queue.get();
                    SOCPossibleRoad curRoad = (SOCPossibleRoad)dataPair.getA();
                    D.ebugPrintlnINFO("-- current road at "+game.getBoard().edgeCoordToString(curRoad.getCoordinates()));
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
                            D.ebugPrintlnINFO("-- queuing necessary road at "+game.getBoard().edgeCoordToString(necRoad2.getCoordinates()));
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
                game.isSpecialBuilding() || (game.getCurrentPlayerNumber() != player.getPlayerNumber());

        //
        // save the lr paths list to restore later
        //
        Vector savedLRPaths[] = new Vector[game.maxPlayers];
        for (int pn = 0; pn < game.maxPlayers; pn++) {
            savedLRPaths[pn] = (Vector)game.getPlayer(pn).getLRPaths().clone();
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
	    if (!ourPlayerData.hasPlayedDevCard() &&
		ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) >= 2 &&
		ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.ROADS) > 0) {
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
		  !ourPlayerData.isPotentialSettlement(threatenedSet.getCoordinates())) {
		D.ebugPrintln("POTENTIAL SETTLEMENT ERROR");
		//System.exit(0);
	      } 
	    }
	    Enumeration goodSetEnum = goodSettlements.elements();
	    while (goodSetEnum.hasMoreElements()) {
	      SOCPossibleSettlement goodSet = (SOCPossibleSettlement)goodSetEnum.nextElement();
	      D.ebugPrintln("*** good settlement at "+Integer.toHexString(goodSet.getCoordinates())+" has a score of "+goodSet.getScore());
	      if (goodSet.getNecessaryRoads().isEmpty() &&
		  !ourPlayerData.isPotentialSettlement(goodSet.getCoordinates())) {
		D.ebugPrintln("POTENTIAL SETTLEMENT ERROR");
		//System.exit(0);
	      } 
	    }    
	    Enumeration threatenedRoadEnum = threatenedRoads.elements();
	    while (threatenedRoadEnum.hasMoreElements()) {
	      SOCPossibleRoad threatenedRoad = (SOCPossibleRoad)threatenedRoadEnum.nextElement();
	      D.ebugPrintln("*** threatened road at "+Integer.toHexString(threatenedRoad.getCoordinates())+" has a score of "+threatenedRoad.getScore());      	
	      if (threatenedRoad.getNecessaryRoads().isEmpty() &&
		  !ourPlayerData.isPotentialRoad(threatenedRoad.getCoordinates())) {
		D.ebugPrintln("POTENTIAL ROAD ERROR");
		//System.exit(0);
	      }
	    }
	    Enumeration goodRoadEnum = goodRoads.elements();
	    while (goodRoadEnum.hasMoreElements()) {
	      SOCPossibleRoad goodRoad = (SOCPossibleRoad)goodRoadEnum.nextElement();
	      D.ebugPrintln("*** good road at "+Integer.toHexString(goodRoad.getCoordinates())+" has a score of "+goodRoad.getScore());
	      if (goodRoad.getNecessaryRoads().isEmpty() &&
		  !ourPlayerData.isPotentialRoad(goodRoad.getCoordinates())) {
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
        for (int pn = 0; pn < game.maxPlayers; pn++) {
            game.getPlayer(pn).setLRPaths(savedLRPaths[pn]);
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
                    brain.getDRecorder().record("Estimate value of road at "+game.getBoard().edgeCoordToString(threatenedRoad.getCoordinates()));
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
                    brain.getDRecorder().record("Estimate value of road at "+game.getBoard().edgeCoordToString(goodRoad.getCoordinates()));
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
        for (int pn = 0; pn < game.maxPlayers; pn++) {
            game.getPlayer(pn).setLRPaths(savedLRPaths[pn]);
        }  

        ///
        /// pick a city that can be built now
        ///
        if (player.getNumPieces(SOCPlayingPiece.CITY) > 0) {
            HashMap trackersCopy = SOCPlayerTracker.copyPlayerTrackers(playerTrackers);
            SOCPlayerTracker ourTrackerCopy = (SOCPlayerTracker)trackersCopy.get(Integer.valueOf(player.getPlayerNumber()));
            int originalWGETAs[] = new int[game.maxPlayers];	 
            int WGETAdiffs[] = new int[game.maxPlayers];	 
            Vector leaders = new Vector();
            int bestWGETA = 1000;
            // int bonus = 0;

            Iterator posCitiesIter = ourPlayerTracker.getPossibleCities().values().iterator();
            while (posCitiesIter.hasNext()) {
                SOCPossibleCity posCity = (SOCPossibleCity)posCitiesIter.next();
                if ((brain != null) && (brain.getDRecorder().isOn())) {
                    brain.getDRecorder().startRecording("CITY"+posCity.getCoordinates());
                    brain.getDRecorder().record("Estimate value of city at "+game.getBoard().nodeCoordToString(posCity.getCoordinates()));
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
                game.putTempPiece(tmpCity);

                ourTrackerCopy.addOurNewCity(tmpCity);

                SOCPlayerTracker.updateWinGameETAs(trackersCopy);

                float wgetaScore = calcWGETABonusAux(originalWGETAs, trackersCopy, leaders);

                //
                // remove the city
                //
                ourTrackerCopy.undoAddOurNewCity(posCity);
                game.undoPutTempPiece(tmpCity);

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
        if ((game.getNumDevCards() > 0) && ! forSpecialBuildingPhase)
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
	    if (!ourPlayerData.hasPlayedDevCard() &&
		ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) >= 2 &&
		ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.ROADS) > 0) {
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
                SOCBoard board = game.getBoard();
                SOCSettlement tmpSet = new SOCSettlement(player, posSet.getCoordinates(), board);
                if ((brain != null) && (brain.getDRecorder().isOn())) {
                    brain.getDRecorder().startRecording("SETTLEMENT"+posSet.getCoordinates());
                    brain.getDRecorder().record("Estimate value of settlement at "+board.nodeCoordToString(posSet.getCoordinates()));
                } 

                HashMap trackersCopy = SOCPlayerTracker.tryPutPiece(tmpSet, game, playerTrackers);
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

                SOCPlayerTracker.undoTryPutPiece(tmpSet, game);
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
        SOCBuildingSpeedEstimate estimate = getEstimator(player.getNumbers());
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
        trackersCopy = SOCPlayerTracker.tryPutPiece(tmpRoad1, game, playerTrackers);
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
        SOCPlayerTracker.undoTryPutPiece(tmpRoad1, game);
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
        int originalWGETAs[] = new int[game.maxPlayers];	 
        int WGETAdiffs[] = new int[game.maxPlayers];	 
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
        int WGETAdiffs[] = new int[game.maxPlayers];	
        int bestWGETA = 1000;
        float bonus = 0;

        for (int i = 0; i < game.maxPlayers; i++) {
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
                    D.ebugPrintlnINFO("$$$$ adding win game bonus : +"+(100 / game.maxPlayers));
                    bonus += (100.0f / (float) game.maxPlayers);
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
            bonus += ((100.0f / (float) game.maxPlayers) * ((float)WGETAdiffs[player.getPlayerNumber()] / (float)originalWGETAs[player.getPlayerNumber()]));
        }			

        D.ebugPrintlnINFO("^^^^ our current bonus = "+bonus);
        if ((brain != null) && (brain.getDRecorder().isOn())) {
            brain.getDRecorder().record("WGETA bonus for only myself = "+df1.format(bonus));
        } 

        //
        //  try adding takedown bonus for all other players
        //  other than the leaders
        //
        for (int pn = 0; pn < game.maxPlayers; pn++) {
            Enumeration leadersEnum = leaders.elements();
            while (leadersEnum.hasMoreElements()) {
                SOCPlayerTracker leader = (SOCPlayerTracker)leadersEnum.nextElement();
                if ((pn != player.getPlayerNumber()) &&
                        (pn != leader.getPlayer().getPlayerNumber())) {
                    if (originalWGETAs[pn] > 0) {
                        float takedownBonus = -1.0f * (100.0f / (float) game.maxPlayers) * adversarialFactor * ((float)WGETAdiffs[pn] / (float)originalWGETAs[pn]) * ((float)bestWGETA / (float)originalWGETAs[pn]);
                        bonus += takedownBonus;
                        D.ebugPrintlnINFO("^^^^ added takedown bonus for player "+pn+" : "+takedownBonus);
                        if (((brain != null) && (brain.getDRecorder().isOn())) && (takedownBonus != 0)) {
                            brain.getDRecorder().record("Bonus for AI with "+pn+" : "+df1.format(takedownBonus));
                        } 
                    } else if (WGETAdiffs[pn] < 0) {
                        float takedownBonus = (100.0f / (float) game.maxPlayers) * adversarialFactor;
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
                    float takedownBonus = -1.0f * (100.0f / (float) game.maxPlayers) * leaderAdversarialFactor * ((float)WGETAdiffs[leader.getPlayer().getPlayerNumber()] / (float)originalWGETAs[leader.getPlayer().getPlayerNumber()]);
                    bonus += takedownBonus;
                    D.ebugPrintlnINFO("^^^^ added takedown bonus for leader "+leader.getPlayer().getPlayerNumber()+" : +"+takedownBonus);
                    if (((brain != null) && (brain.getDRecorder().isOn())) && (takedownBonus != 0)){
                        brain.getDRecorder().record("Bonus for LI with "+leader.getPlayer().getName()+" : +"+df1.format(takedownBonus));
                    } 

                } else if (WGETAdiffs[leader.getPlayer().getPlayerNumber()] < 0) {
                    float takedownBonus = (100.0f / (float) game.maxPlayers) * leaderAdversarialFactor;
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
        int WGETAdiffs[] = new int[game.maxPlayers];
        int originalWGETAs[] = new int[game.maxPlayers];	 
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
     * Takes a table of nodes and adds a weighted score to
     * each node score in the table.  Nodes touching hexes
     * with better numbers get better scores.  Also numbers
     * that the player isn't touching yet are better than ones
     * that the player is already touching.
     *
     * @param nodes    the table of nodes with scores. key = Int node, value = Int score, to be modified in this method
     * @param player   the player that we are doing the rating for
     * @param weight   a number that is multiplied by the score
     */
    protected void bestSpotForNumbers(Hashtable nodes, SOCPlayer player, int weight)
    {
        int[] numRating = SOCNumberProbabilities.INT_VALUES;
        SOCBoard board = game.getBoard();
        int oldScore;
        Enumeration nodesEnum = nodes.keys();

        while (nodesEnum.hasMoreElements())
        {
            Integer node = (Integer) nodesEnum.nextElement();

            //D.ebugPrintln("BSN - looking at node "+Integer.toHexString(node.intValue()));
            oldScore = ((Integer) nodes.get(node)).intValue();

            int score = 0;
            Enumeration hexesEnum = SOCBoard.getAdjacentHexesToNode(node.intValue()).elements();

            while (hexesEnum.hasMoreElements())
            {
                final int hex = ((Integer) hexesEnum.nextElement()).intValue();
                final int number = board.getNumberOnHexFromCoord(hex);
                score += numRating[number];

                if ((number != 0) && (!player.getNumbers().hasNumber(number)))
                {
                    /**
                     * add a bonus for numbers that the player doesn't already have
                     */

                    //D.ebugPrintln("ADDING BONUS FOR NOT HAVING "+number);
                    score += numRating[number];
                }

                //D.ebugPrintln(" -- -- Adding "+numRating[board.getNumberOnHexFromCoord(hex)]);
            }

            /*
             * normalize score and multiply by weight
             * 80 is highest practical score
             * lowest score is 0
             */
            int nScore = ((score * 100) / 80) * weight;
            Integer finalScore = Integer.valueOf(nScore + oldScore);
            nodes.put(node, finalScore);

            //D.ebugPrintln("BSN -- put node "+Integer.toHexString(node.intValue())+" with old score "+oldScore+" + new score "+nScore);
        }
    }

    /**
     * Takes a table of nodes and adds a weighted score to
     * each node score in the table.  A vector of nodes that
     * we want to be on is also taken as an argument.
     * Here are the rules for scoring:
     * If a node is in the desired set of nodes it gets 100.
     * Otherwise it gets 0.
     *
     * @param nodesIn   the table of nodes to evaluate
     * @param nodeSet   the set of desired nodes
     * @param weight    the score multiplier
     */
    protected void bestSpotInANodeSet(Hashtable nodesIn, Vector nodeSet, int weight)
    {
        Enumeration nodesInEnum = nodesIn.keys();

        while (nodesInEnum.hasMoreElements())
        {
            Integer nodeCoord = (Integer) nodesInEnum.nextElement();
            int node = nodeCoord.intValue();
            int score = 0;
            final int oldScore = ((Integer) nodesIn.get(nodeCoord)).intValue();

            Enumeration nodeSetEnum = nodeSet.elements();

            while (nodeSetEnum.hasMoreElements())
            {
                int target = ((Integer) nodeSetEnum.nextElement()).intValue();

                if (node == target)
                {
                    score = 100;

                    break;
                }
            }

            /**
             * multiply by weight
             */
            score *= weight;

            nodesIn.put(nodeCoord, Integer.valueOf(oldScore + score));

            //D.ebugPrintln("BSIANS -- put node "+Integer.toHexString(node)+" with old score "+oldScore+" + new score "+score);
        }
    }

    /**
     * this is a function more for convience
     * given a set of nodes, run a bunch of metrics across them
     * to find which one is best for building a
     * settlement
     *
     * @param nodes          a hashtable of nodes, the scores in the table will be modified.
     *                            Key = coord Integer; value = score Integer.
     * @param numberWeight   the weight given to nodes on good numbers
     * @param miscPortWeight the weight given to nodes on 3:1 ports
     * @param portWeight     the weight given to nodes on good 2:1 ports
     */
    private void scoreNodesForSettlements(Hashtable nodes, final int numberWeight, final int miscPortWeight, final int portWeight)
    {
        /**
         * favor spots with the most high numbers
         */
        bestSpotForNumbers(nodes, player, numberWeight);

        /**
         * favor spots on good ports:
         */
        /**
         * check if this is on a 3:1 ports, only if we don't have one
         */
        if (!player.getPortFlag(SOCBoard.MISC_PORT))
        {
            Vector miscPortNodes = game.getBoard().getPortCoordinates(SOCBoard.MISC_PORT);
            bestSpotInANodeSet(nodes, miscPortNodes, miscPortWeight);
        }

        /**
         * check out good 2:1 ports that we don't have
         */
        int[] resourceEstimates = estimateResourceRarity();

        for (int portType = SOCBoard.CLAY_PORT; portType <= SOCBoard.WOOD_PORT;
                portType++)
        {
            /**
             * if the chances of rolling a number on the resource is better than 1/3,
             * then it's worth looking at the port
             */
            if ((resourceEstimates[portType] > 33) && (!player.getPortFlag(portType)))
            {
                Vector portNodes = game.getBoard().getPortCoordinates(portType);
                int estimatedPortWeight = (resourceEstimates[portType] * portWeight) / 56;
                bestSpotInANodeSet(nodes, portNodes, estimatedPortWeight);
            }
        }
    }    

    /**
     * Estimate the rarity of each resource, given this board's resource locations vs dice numbers.
     * Cached after the first call.
     *
     * @return an array of rarity numbers where
     *         estimates[SOCBoard.CLAY_HEX] == the clay rarity,
     *         as an integer percentage 0-100 of dice rolls.
     */
    protected int[] estimateResourceRarity()
    {
        if (resourceEstimates == null)
        {
            SOCBoard board = game.getBoard();
            final int[] numberWeights = SOCNumberProbabilities.INT_VALUES;

            resourceEstimates = new int[SOCResourceConstants.UNKNOWN];  // uses 1 to 5 (CLAY to WOOD)
            resourceEstimates[0] = 0;

            // look at each hex
            final int L = board.getNumberLayout().length;
            for (int i = 0; i < L; i++)
            {
                final int hexNumber = board.getNumberOnHexFromNumber(i);
                if (hexNumber > 0)
                    resourceEstimates[board.getHexTypeFromNumber(i)] += numberWeights[hexNumber];
            }
        }

        //D.ebugPrint("Resource Estimates = ");
        //for (int i = 1; i < 6; i++)
        //{
        //D.ebugPrint(i+":"+resourceEstimates[i]+" ");
        //}

        //D.ebugPrintln();
        return resourceEstimates;
    }
    
    @Override
    public boolean shouldPlayKnightForLA() {
        // Default behavior - play if we have enough cards to get LA
        boolean ret = false;
        SOCPlayer laPlayer = game.getPlayerWithLargestArmy();

        if (((laPlayer != null) && (laPlayer.getPlayerNumber() != player.getPlayerNumber())) || (laPlayer == null))
        {
            int larmySize;

            if (laPlayer == null)
            {
                larmySize = 3;
            }
            else
            {
                larmySize = laPlayer.getNumKnights() + 1;
            }

            if (((player.getNumKnights() + player.getDevCards().getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.KNIGHT) + player.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT)) >= larmySize))
            {
                ret = true;
            }
        }
        return ret;
    }

    @Override
    public boolean shouldPlayKnight(boolean hasRolled) {
        // Default behavior - play if the robber is on one of our hexes.
        return (! (player.getNumbers().getNumberResourcePairsForHex(game.getBoard().getRobberHex())).isEmpty());
    }

    @Override
    public boolean shouldPlayRoadbuilding() {
        // Default behavior - if we can play it, do it.
        return true;
    }

    @Override
    public boolean shouldPlayDiscovery() {
        SOCResourceSet ourResources = player.getResources();
        int numNeededResources = 0;

        SOCPossiblePiece targetPiece = (SOCPossiblePiece) buildingPlan.peek();
        SOCResourceSet targetResources = SOCPlayingPiece.getResourcesToBuild(targetPiece.getType());

        for (int resource = SOCResourceConstants.CLAY;
                resource <= SOCResourceConstants.WOOD;
                resource++)
        {
            int diff = targetResources.getAmount(resource) - ourResources.getAmount(resource);

            if (diff > 0)
            {
                numNeededResources += diff;
            }
        }

        // TODO: What if we only need one resource?  May be worth playing anyway...
        return (numNeededResources == 2);
    }

    @Override
    public void planInitialSettlements() 
    {
        D.ebugPrintlnINFO("--- planInitialSettlements");

        int[] rolls;
        Enumeration hexes;
        int speed;
        boolean allTheWay;
        firstSettlement = 0;
        secondSettlement = 0;

        int bestSpeed = 4 * SOCBuildingSpeedEstimate.DEFAULT_ROLL_LIMIT;
        SOCBoard board = game.getBoard();
        SOCPlayerNumbers playerNumbers = new SOCPlayerNumbers(board.getBoardEncodingFormat());
        int probTotal;
        int bestProbTotal;
        boolean[] ports = new boolean[SOCBoard.WOOD_PORT + 1];
        SOCBuildingSpeedEstimate estimate = getEstimator();
        int[] prob = SOCNumberProbabilities.INT_VALUES;

        bestProbTotal = 0;

        for (int firstNode = board.getMinNode(); firstNode <= SOCBoard.MAXNODE; firstNode++)
        {
            if (player.isLegalSettlement(firstNode))
            {
                Integer firstNodeInt = Integer.valueOf(firstNode);

                //
                // this is just for testing purposes
                //
                D.ebugPrintlnINFO("FIRST NODE -----------");
                D.ebugPrintlnINFO("firstNode = " + board.nodeCoordToString(firstNode));
                D.ebugPrintINFO("numbers:[");
                playerNumbers.clear();
                probTotal = 0;
                hexes = SOCBoard.getAdjacentHexesToNode(firstNode).elements();

                while (hexes.hasMoreElements())
                {
                    Integer hex = (Integer) hexes.nextElement();
                    int number = board.getNumberOnHexFromCoord(hex.intValue());
                    int resource = board.getHexTypeFromCoord(hex.intValue());
                    playerNumbers.addNumberForResource(number, resource, hex.intValue());
                    probTotal += prob[number];
                    D.ebugPrintINFO(number + " ");
                }

                D.ebugPrintlnINFO("]");
                D.ebugPrintINFO("ports: ");

                for (int portType = SOCBoard.MISC_PORT;
                        portType <= SOCBoard.WOOD_PORT; portType++)
                {
                    if (board.getPortCoordinates(portType).contains(firstNodeInt))
                    {
                        ports[portType] = true;
                    }
                    else
                    {
                        ports[portType] = false;
                    }

                    D.ebugPrintINFO(ports[portType] + "  ");
                }

                D.ebugPrintlnINFO();
                D.ebugPrintlnINFO("probTotal = " + probTotal);
                estimate.recalculateEstimates(playerNumbers);
                speed = 0;
                allTheWay = false;

                try
                {
                    speed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.SETTLEMENT_SET, 300, ports).getRolls();
                    speed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.CITY_SET, 300, ports).getRolls();
                    speed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.CARD_SET, 300, ports).getRolls();
                    speed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.ROAD_SET, 300, ports).getRolls();
                }
                catch (CutoffExceededException e) {}

                rolls = estimate.getEstimatesFromNothingFast(ports, 300);
                D.ebugPrintINFO(" road: " + rolls[SOCBuildingSpeedEstimate.ROAD]);
                D.ebugPrintINFO(" stlmt: " + rolls[SOCBuildingSpeedEstimate.SETTLEMENT]);
                D.ebugPrintINFO(" city: " + rolls[SOCBuildingSpeedEstimate.CITY]);
                D.ebugPrintlnINFO(" card: " + rolls[SOCBuildingSpeedEstimate.CARD]);
                D.ebugPrintlnINFO("speed = " + speed);

                //
                // end test
                //
                for (int secondNode = firstNode + 1; secondNode <= SOCBoard.MAXNODE;
                        secondNode++)
                {
                    if ((player.isLegalSettlement(secondNode)) && (! board.getAdjacentNodesToNode(secondNode).contains(firstNodeInt)))
                    {
                        D.ebugPrintlnINFO("firstNode = " + board.nodeCoordToString(firstNode));
                        D.ebugPrintlnINFO("secondNode = " + board.nodeCoordToString(secondNode));

                        Integer secondNodeInt = Integer.valueOf(secondNode);

                        /**
                         * get the numbers for these settlements
                         */
                        D.ebugPrintINFO("numbers:[");
                        playerNumbers.clear();
                        probTotal = 0;
                        hexes = SOCBoard.getAdjacentHexesToNode(firstNode).elements();

                        while (hexes.hasMoreElements())
                        {
                            Integer hex = (Integer) hexes.nextElement();
                            int number = board.getNumberOnHexFromCoord(hex.intValue());
                            int resource = board.getHexTypeFromCoord(hex.intValue());
                            playerNumbers.addNumberForResource(number, resource, hex.intValue());
                            probTotal += prob[number];
                            D.ebugPrintINFO(number + " ");
                        }

                        D.ebugPrintINFO("] [");
                        hexes = SOCBoard.getAdjacentHexesToNode(secondNode).elements();

                        while (hexes.hasMoreElements())
                        {
                            Integer hex = (Integer) hexes.nextElement();
                            int number = board.getNumberOnHexFromCoord(hex.intValue());
                            int resource = board.getHexTypeFromCoord(hex.intValue());
                            playerNumbers.addNumberForResource(number, resource, hex.intValue());
                            probTotal += prob[number];
                            D.ebugPrintINFO(number + " ");
                        }

                        D.ebugPrintlnINFO("]");

                        /**
                         * see if the settlements are on any ports
                         */
                        D.ebugPrintINFO("ports: ");

                        for (int portType = SOCBoard.MISC_PORT;
                                portType <= SOCBoard.WOOD_PORT; portType++)
                        {
                            if ((board.getPortCoordinates(portType).contains(firstNodeInt)) || (board.getPortCoordinates(portType).contains(secondNodeInt)))
                            {
                                ports[portType] = true;
                            }
                            else
                            {
                                ports[portType] = false;
                            }

                            D.ebugPrintINFO(ports[portType] + "  ");
                        }

                        D.ebugPrintlnINFO();
                        D.ebugPrintlnINFO("probTotal = " + probTotal);

                        /**
                         * estimate the building speed for this pair
                         */
                        estimate.recalculateEstimates(playerNumbers);
                        speed = 0;
                        allTheWay = false;

                        try
                        {
                            speed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.SETTLEMENT_SET, bestSpeed, ports).getRolls();

                            if (speed < bestSpeed)
                            {
                                speed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.CITY_SET, bestSpeed, ports).getRolls();

                                if (speed < bestSpeed)
                                {
                                    speed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.CARD_SET, bestSpeed, ports).getRolls();

                                    if (speed < bestSpeed)
                                    {
                                        speed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.ROAD_SET, bestSpeed, ports).getRolls();
                                        allTheWay = true;
                                    }
                                }
                            }
                        }
                        catch (CutoffExceededException e)
                        {
                            speed = bestSpeed;
                        }

                        rolls = estimate.getEstimatesFromNothingFast(ports, bestSpeed);
                        D.ebugPrintINFO(" road: " + rolls[SOCBuildingSpeedEstimate.ROAD]);
                        D.ebugPrintINFO(" stlmt: " + rolls[SOCBuildingSpeedEstimate.SETTLEMENT]);
                        D.ebugPrintINFO(" city: " + rolls[SOCBuildingSpeedEstimate.CITY]);
                        D.ebugPrintlnINFO(" card: " + rolls[SOCBuildingSpeedEstimate.CARD]);
                        D.ebugPrintlnINFO("allTheWay = " + allTheWay);
                        D.ebugPrintlnINFO("speed = " + speed);

                        /**
                         * keep the settlements with the best speed
                         */
                        if (speed < bestSpeed)
                        {
                            firstSettlement = firstNode;
                            secondSettlement = secondNode;
                            bestSpeed = speed;
                            bestProbTotal = probTotal;
                            D.ebugPrintlnINFO("bestSpeed = " + bestSpeed);
                            D.ebugPrintlnINFO("bestProbTotal = " + bestProbTotal);
                        }
                        else if ((speed == bestSpeed) && allTheWay)
                        {
                            if (probTotal > bestProbTotal)
                            {
                                D.ebugPrintlnINFO("Equal speed, better prob");
                                firstSettlement = firstNode;
                                secondSettlement = secondNode;
                                bestSpeed = speed;
                                bestProbTotal = probTotal;
                                D.ebugPrintlnINFO("firstSettlement = " + Integer.toHexString(firstSettlement));
                                D.ebugPrintlnINFO("secondSettlement = " + Integer.toHexString(secondSettlement));
                                D.ebugPrintlnINFO("bestSpeed = " + bestSpeed);
                                D.ebugPrintlnINFO("bestProbTotal = " + bestProbTotal);
                            }
                        }
                    }
                }
            }
        }

        /**
         * choose which settlement to place first
         */
        playerNumbers.clear();
        hexes = SOCBoard.getAdjacentHexesToNode(firstSettlement).elements();

        while (hexes.hasMoreElements())
        {
            int hex = ((Integer) hexes.nextElement()).intValue();
            int number = board.getNumberOnHexFromCoord(hex);
            int resource = board.getHexTypeFromCoord(hex);
            playerNumbers.addNumberForResource(number, resource, hex);
        }

        Integer firstSettlementInt = Integer.valueOf(firstSettlement);

        for (int portType = SOCBoard.MISC_PORT; portType <= SOCBoard.WOOD_PORT;
                portType++)
        {
            if (board.getPortCoordinates(portType).contains(firstSettlementInt))
            {
                ports[portType] = true;
            }
            else
            {
                ports[portType] = false;
            }
        }

        estimate.recalculateEstimates(playerNumbers);

        int firstSpeed = 0;
        int cutoff = 100;

        try
        {
            firstSpeed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.SETTLEMENT_SET, cutoff, ports).getRolls();
        }
        catch (CutoffExceededException e)
        {
            firstSpeed += cutoff;
        }

        try
        {
            firstSpeed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.CITY_SET, cutoff, ports).getRolls();
        }
        catch (CutoffExceededException e)
        {
            firstSpeed += cutoff;
        }

        try
        {
            firstSpeed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.CARD_SET, cutoff, ports).getRolls();
        }
        catch (CutoffExceededException e)
        {
            firstSpeed += cutoff;
        }

        try
        {
            firstSpeed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.ROAD_SET, cutoff, ports).getRolls();
        }
        catch (CutoffExceededException e)
        {
            firstSpeed += cutoff;
        }

        playerNumbers.clear();
        hexes = SOCBoard.getAdjacentHexesToNode(secondSettlement).elements();

        while (hexes.hasMoreElements())
        {
            int hex = ((Integer) hexes.nextElement()).intValue();
            int number = board.getNumberOnHexFromCoord(hex);
            int resource = board.getHexTypeFromCoord(hex);
            playerNumbers.addNumberForResource(number, resource, hex);
        }

        Integer secondSettlementInt = Integer.valueOf(secondSettlement);

        for (int portType = SOCBoard.MISC_PORT; portType <= SOCBoard.WOOD_PORT;
                portType++)
        {
            if (board.getPortCoordinates(portType).contains(secondSettlementInt))
            {
                ports[portType] = true;
            }
            else
            {
                ports[portType] = false;
            }
        }

        estimate.recalculateEstimates(playerNumbers);

        int secondSpeed = 0;

        try
        {
            secondSpeed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.SETTLEMENT_SET, bestSpeed, ports).getRolls();
        }
        catch (CutoffExceededException e)
        {
            secondSpeed += cutoff;
        }

        try
        {
            secondSpeed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.CITY_SET, bestSpeed, ports).getRolls();
        }
        catch (CutoffExceededException e)
        {
            secondSpeed += cutoff;
        }

        try
        {
            secondSpeed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.CARD_SET, bestSpeed, ports).getRolls();
        }
        catch (CutoffExceededException e)
        {
            secondSpeed += cutoff;
        }

        try
        {
            secondSpeed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.ROAD_SET, bestSpeed, ports).getRolls();
        }
        catch (CutoffExceededException e)
        {
            secondSpeed += cutoff;
        }

        if (firstSpeed > secondSpeed)
        {
            int tmp = firstSettlement;
            firstSettlement = secondSettlement;
            secondSettlement = tmp;
        }

        D.ebugPrintlnINFO(board.nodeCoordToString(firstSettlement) + ":" + firstSpeed + ", " + board.nodeCoordToString(secondSettlement) + ":" + secondSpeed);
    }


    @Override
    public void planSecondSettlement() {
        D.ebugPrintlnINFO("--- planSecondSettlement");

        int bestSpeed = 4 * SOCBuildingSpeedEstimate.DEFAULT_ROLL_LIMIT;
        SOCBoard board = game.getBoard();
        SOCPlayerNumbers playerNumbers = new SOCPlayerNumbers(board.getBoardEncodingFormat());
        boolean[] ports = new boolean[SOCBoard.WOOD_PORT + 1];
        SOCBuildingSpeedEstimate estimate = getEstimator();
        int probTotal;
        int bestProbTotal;
        final int[] prob = SOCNumberProbabilities.INT_VALUES;
        final int firstNode = firstSettlement;
        final Integer firstNodeInt = Integer.valueOf(firstNode);

        bestProbTotal = 0;
        secondSettlement = -1;

        for (int secondNode = board.getMinNode(); secondNode <= SOCBoard.MAXNODE; secondNode++)
        {
            if ((player.isLegalSettlement(secondNode)) && (! board.getAdjacentNodesToNode(secondNode).contains(firstNodeInt)))
            {
                Integer secondNodeInt = Integer.valueOf(secondNode);

                /**
                 * get the numbers for these settlements
                 */
                D.ebugPrintINFO("numbers: ");
                playerNumbers.clear();
                probTotal = 0;

                Enumeration hexes = SOCBoard.getAdjacentHexesToNode(firstNode).elements();

                while (hexes.hasMoreElements())
                {
                    final int hex = ((Integer) hexes.nextElement()).intValue();
                    int number = board.getNumberOnHexFromCoord(hex);
                    int resource = board.getHexTypeFromCoord(hex);
                    playerNumbers.addNumberForResource(number, resource, hex);
                    probTotal += prob[number];
                    D.ebugPrintINFO(number + " ");
                }

                hexes = SOCBoard.getAdjacentHexesToNode(secondNode).elements();

                while (hexes.hasMoreElements())
                {
                    final int hex = ((Integer) hexes.nextElement()).intValue();
                    int number = board.getNumberOnHexFromCoord(hex);
                    int resource = board.getHexTypeFromCoord(hex);
                    playerNumbers.addNumberForResource(number, resource, hex);
                    probTotal += prob[number];
                    D.ebugPrintINFO(number + " ");
                }

                /**
                 * see if the settlements are on any ports
                 */
                D.ebugPrintINFO("ports: ");

                for (int portType = SOCBoard.MISC_PORT;
                        portType <= SOCBoard.WOOD_PORT; portType++)
                {
                    if ((board.getPortCoordinates(portType).contains(firstNodeInt)) || (board.getPortCoordinates(portType).contains(secondNodeInt)))
                    {
                        ports[portType] = true;
                    }
                    else
                    {
                        ports[portType] = false;
                    }

                    D.ebugPrintINFO(ports[portType] + "  ");
                }

                D.ebugPrintlnINFO();
                D.ebugPrintlnINFO("probTotal = " + probTotal);

                /**
                 * estimate the building speed for this pair
                 */
                estimate.recalculateEstimates(playerNumbers);

                int speed = 0;

                try
                {
                    speed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.SETTLEMENT_SET, bestSpeed, ports).getRolls();

                    if (speed < bestSpeed)
                    {
                        speed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.CITY_SET, bestSpeed, ports).getRolls();

                        if (speed < bestSpeed)
                        {
                            speed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.CARD_SET, bestSpeed, ports).getRolls();

                            if (speed < bestSpeed)
                            {
                                speed += estimate.calculateRollsFast(SOCResourceSet.EMPTY_SET, SOCGame.ROAD_SET, bestSpeed, ports).getRolls();
                            }
                        }
                    }
                }
                catch (CutoffExceededException e)
                {
                    speed = bestSpeed;
                }

                D.ebugPrintlnINFO(Integer.toHexString(firstNode) + ", " + Integer.toHexString(secondNode) + ":" + speed);

                /**
                 * keep the settlements with the best speed
                 */
                if ((speed < bestSpeed) || (secondSettlement < 0))
                {
                    firstSettlement = firstNode;
                    secondSettlement = secondNode;
                    bestSpeed = speed;
                    bestProbTotal = probTotal;
                    D.ebugPrintlnINFO("firstSettlement = " + Integer.toHexString(firstSettlement));
                    D.ebugPrintlnINFO("secondSettlement = " + Integer.toHexString(secondSettlement));

                    int[] rolls = estimate.getEstimatesFromNothingFast(ports);
                    D.ebugPrintINFO("road: " + rolls[SOCBuildingSpeedEstimate.ROAD]);
                    D.ebugPrintINFO(" stlmt: " + rolls[SOCBuildingSpeedEstimate.SETTLEMENT]);
                    D.ebugPrintINFO(" city: " + rolls[SOCBuildingSpeedEstimate.CITY]);
                    D.ebugPrintlnINFO(" card: " + rolls[SOCBuildingSpeedEstimate.CARD]);
                    D.ebugPrintlnINFO("bestSpeed = " + bestSpeed);
                }
                else if (speed == bestSpeed)
                {
                    if (probTotal > bestProbTotal)
                    {
                        firstSettlement = firstNode;
                        secondSettlement = secondNode;
                        bestSpeed = speed;
                        bestProbTotal = probTotal;
                        D.ebugPrintlnINFO("firstSettlement = " + Integer.toHexString(firstSettlement));
                        D.ebugPrintlnINFO("secondSettlement = " + Integer.toHexString(secondSettlement));

                        int[] rolls = estimate.getEstimatesFromNothingFast(ports);
                        D.ebugPrintINFO("road: " + rolls[SOCBuildingSpeedEstimate.ROAD]);
                        D.ebugPrintINFO(" stlmt: " + rolls[SOCBuildingSpeedEstimate.SETTLEMENT]);
                        D.ebugPrintINFO(" city: " + rolls[SOCBuildingSpeedEstimate.CITY]);
                        D.ebugPrintlnINFO(" card: " + rolls[SOCBuildingSpeedEstimate.CARD]);
                        D.ebugPrintlnINFO("bestSpeed = " + bestSpeed);
                    }
                }
            }
        }
    }

    @Override
    public int[] planInitRoad(int settlementNode) {
        /**	        
         * Score the nearby nodes to build road towards: Key = coord Integer; value = Integer score towards "best" node.
         */
        Hashtable twoAway = new Hashtable();

        D.ebugPrintlnINFO("--- placeInitRoad");

        /**
         * look at all of the nodes that are 2 away from the
         * last settlement, and pick the best one
         */
        SOCBoard board = game.getBoard();

        for (int facing = 1; facing <= 6; ++facing)
        {
            // each of 6 directions: NE, E, SE, SW, W, NW
            int tmp = board.getAdjacentNodeToNode2Away(settlementNode, facing);
            if ((tmp != -9) && player.isLegalSettlement(tmp))
                twoAway.put(Integer.valueOf(tmp), Integer.valueOf(0));
        }

        scoreNodesForSettlements(twoAway, 3, 5, 10);

        //D.ebugPrintln("Init Road for " + client.getNickname());

        /**
         * create a dummy player to calculate possible places to build
         * taking into account where other players will build before
         * we can.
         */
        SOCPlayer dummy = new SOCPlayer(player.getPlayerNumber(), game);

        if (game.getGameState() == SOCGame.START1B)
        {
            /**
             * do a look ahead so we don't build toward a place
             * where someone else will build first.
             */
            int numberOfBuilds = numberOfEnemyBuilds();
            D.ebugPrintlnINFO("Other players will build " + numberOfBuilds + " settlements before I get to build again.");

            if (numberOfBuilds > 0)
            {
                /**
                 * rule out where other players are going to build
                 */
                Hashtable allNodes = new Hashtable();
                final int minNode = board.getMinNode();

                for (int i = minNode; i <= SOCBoard.MAXNODE; i++)
                {
                    if (player.isLegalSettlement(i))
                    {
                        D.ebugPrintlnINFO("-- potential settlement at " + Integer.toHexString(i));
                        allNodes.put(Integer.valueOf(i), Integer.valueOf(0));
                    }
                }

                /**
                 * favor spots with the most high numbers
                 */
                bestSpotForNumbers(allNodes, 100);

                /**
                 * favor spots near good ports
                 */
                /**
                 * check 3:1 ports
                 */
                Vector miscPortNodes = game.getBoard().getPortCoordinates(SOCBoard.MISC_PORT);
                bestSpot2AwayFromANodeSet(allNodes, miscPortNodes, 5);

                /**
                 * check out good 2:1 ports
                 */
                for (int portType = SOCBoard.CLAY_PORT;
                        portType <= SOCBoard.WOOD_PORT; portType++)
                {
                    /**
                     * if the chances of rolling a number on the resource is better than 1/3,
                     * then it's worth looking at the port
                     */
                    if (resourceEstimates[portType] > 33)
                    {
                        Vector portNodes = game.getBoard().getPortCoordinates(portType);
                        int portWeight = (resourceEstimates[portType] * 10) / 56;
                        bestSpot2AwayFromANodeSet(allNodes, portNodes, portWeight);
                    }
                }

                /*
                 * create a list of potential settlements that takes into account
                 * where other players will build
                 */
                Vector psList = new Vector();

                for (int j = minNode; j <= SOCBoard.MAXNODE; j++)
                {
                    if (player.isLegalSettlement(j))
                    {
                        D.ebugPrintlnINFO("- potential settlement at " + Integer.toHexString(j));
                        psList.addElement(Integer.valueOf(j));
                    }
                }

                dummy.setPotentialSettlements(psList);

                for (int builds = 0; builds < numberOfBuilds; builds++)
                {
                    BoardNodeScorePair bestNodePair = new BoardNodeScorePair(0, 0);
                    Enumeration nodesEnum = allNodes.keys();

                    while (nodesEnum.hasMoreElements())
                    {
                        Integer nodeCoord = (Integer) nodesEnum.nextElement();
                        final int score = ((Integer) allNodes.get(nodeCoord)).intValue();
                        D.ebugPrintlnINFO("NODE = " + Integer.toHexString(nodeCoord.intValue()) + " SCORE = " + score);

                        if (bestNodePair.getScore() < score)
                        {
                            bestNodePair.setScore(score);
                            bestNodePair.setNode(nodeCoord.intValue());
                        }
                    }

                    /**
                     * pretend that someone has built a settlement on the best spot
                     */
                    dummy.updatePotentials(new SOCSettlement(player, bestNodePair.getNode(), null));

                    /**
                     * remove this spot from the list of best spots
                     */
                    allNodes.remove(Integer.valueOf(bestNodePair.getNode()));
                }
            }
        }

        /**
         * Find the best scoring node
         */
        BoardNodeScorePair bestNodePair = new BoardNodeScorePair(0, 0);
        Enumeration cenum = twoAway.keys();

        while (cenum.hasMoreElements())
        {
            Integer coord = (Integer) cenum.nextElement();
            final int score = ((Integer) twoAway.get(coord)).intValue();

            D.ebugPrintlnINFO("Considering " + Integer.toHexString(coord.intValue()) + " with a score of " + score);

            if (dummy.isPotentialSettlement(coord.intValue()))
            {
                if (bestNodePair.getScore() < score)
                {
                    bestNodePair.setScore(score);
                    bestNodePair.setNode(coord.intValue());
                }
            }
            else
            {
                D.ebugPrintlnINFO("Someone is bound to ruin that spot.");
            }
        }

        // Reminder: settlementNode == ourPlayerData.getLastSettlementCoord()
        final int destination = bestNodePair.getNode();  // coordinate of future settlement
        // 2 nodes away from settlementNode
        final int roadEdge   // will be adjacent to settlementNode
        = board.getAdjacentEdgeToNode2Away(settlementNode, destination);

        dummy.destroyPlayer();
        return new int[] {roadEdge, destination};

    }

    /**
     * Takes a table of nodes and adds a weighted score to
     * each node score in the table.  Nodes touching hexes
     * with better numbers get better scores.
     *
     * @param nodes    the table of nodes with scores
     * @param weight   a number that is multiplied by the score
     */
    private void bestSpotForNumbers(Hashtable nodes, int weight)
    {
        int[] numRating = SOCNumberProbabilities.INT_VALUES;
        SOCBoard board = game.getBoard();
        int oldScore;
        Enumeration nodesEnum = nodes.keys();

        while (nodesEnum.hasMoreElements())
        {
            Integer node = (Integer) nodesEnum.nextElement();

            //D.ebugPrintln("BSN - looking at node "+Integer.toHexString(node.intValue()));
            oldScore = ((Integer) nodes.get(node)).intValue();

            int score = 0;
            Enumeration hexesEnum = SOCBoard.getAdjacentHexesToNode(node.intValue()).elements();

            while (hexesEnum.hasMoreElements())
            {
                int hex = ((Integer) hexesEnum.nextElement()).intValue();
                score += numRating[board.getNumberOnHexFromCoord(hex)];

                //D.ebugPrintln(" -- -- Adding "+numRating[board.getNumberOnHexFromCoord(hex)]);
            }

            /*
             * normalize score and multiply by weight
             * 40 is highest practical score
             * lowest score is 0
             */
            int nScore = ((score * 100) / 40) * weight;
            Integer finalScore = Integer.valueOf(nScore + oldScore);
            nodes.put(node, finalScore);

            //D.ebugPrintln("BSN -- put node "+Integer.toHexString(node.intValue())+" with old score "+oldScore+" + new score "+nScore);
        }
    }

    @Override
    public boolean chooseMonopoly()
    {
        int bestResourceCount = 0;
        int bestResource = 0;

        for (int resource = SOCResourceConstants.CLAY;
                resource <= SOCResourceConstants.WOOD; resource++)
        {
            //D.ebugPrintln("$$ resource="+resource);
            int freeResourceCount = 0;
            boolean twoForOne = false;
            boolean threeForOne = false;

            if (player.getPortFlag(resource))
            {
                twoForOne = true;
            }
            else if (player.getPortFlag(SOCBoard.MISC_PORT))
            {
                threeForOne = true;
            }

            int resourceTotal = 0;

            for (int pn = 0; pn < game.maxPlayers; pn++)
            {
                if (player.getPlayerNumber() != pn)
                {
                    resourceTotal += game.getPlayer(pn).getResources().getAmount(resource);

                    //D.ebugPrintln("$$ resourceTotal="+resourceTotal);
                }
            }

            if (twoForOne)
            {
                freeResourceCount = resourceTotal / 2;
            }
            else if (threeForOne)
            {
                freeResourceCount = resourceTotal / 3;
            }
            else
            {
                freeResourceCount = resourceTotal / 4;
            }

            //D.ebugPrintln("freeResourceCount="+freeResourceCount);
            if (freeResourceCount > bestResourceCount)
            {
                bestResourceCount = freeResourceCount;
                bestResource = resource;
            }
        }

        if (bestResourceCount > 2)
        {
            monopolyChoice = bestResource;

            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * Select the player to target with the robber given the current hex the robber is on.  
     * Default implementation targets the player closest to winning.  This may be overridden, for 
     *  example, to target the player with the resources we want most badly.
     * @param robberHex the current location of the robber
     * @return
     */
    public int selectPlayerToThwart(int robberHex) {
        /**
         * decide which player we want to thwart
         */
        int[] winGameETAs = new int[game.maxPlayers];
        for (int i = game.maxPlayers - 1; i >= 0; --i)
            winGameETAs[i] = 100;
        Iterator trackersIter = playerTrackers.values().iterator();

        while (trackersIter.hasNext())
        {
            SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();
            D.ebugPrintlnINFO("%%%%%%%%% TRACKER FOR PLAYER " + tracker.getPlayer().getPlayerNumber());

            try
            {
                tracker.recalcWinGameETA();
                winGameETAs[tracker.getPlayer().getPlayerNumber()] = tracker.getWinGameETA();
                D.ebugPrintlnINFO("winGameETA = " + tracker.getWinGameETA());
            }
            catch (NullPointerException e)
            {
                D.ebugPrintlnINFO("Null Pointer Exception calculating winGameETA");
                winGameETAs[tracker.getPlayer().getPlayerNumber()] = 500;
            }
        }

        int victimNum = -1;

        for (int pnum = 0; pnum < game.maxPlayers; pnum++)
        {
            if (! game.isSeatVacant(pnum))
            {
                if ((victimNum < 0) && (pnum != player.getPlayerNumber()))
                {
                    // The first pick
                    D.ebugPrintlnINFO("Picking a robber victim: pnum=" + pnum);
                    victimNum = pnum;
                }
                else if ((pnum != player.getPlayerNumber()) && (winGameETAs[pnum] < winGameETAs[victimNum]))
                {
                    // A better pick
                    D.ebugPrintlnINFO("Picking a better robber victim: pnum=" + pnum);
                    victimNum = pnum;
                }
            }
        }
        // Postcondition: victimNum != -1 due to "First pick" in loop.
        
        return victimNum;
    }
    
    /**
     * Select the hex to rob, based on the fact we are targeting the specified player, and the robber is currently on the specified hex
     * @param robberHex the robber's current location
     * @param victimNum the targeted player
     * @return
     */
    public int selectRobberHex(int robberHex, int victimNum) {
        /**
         * figure out the best way to thwart that player
         */
        SOCPlayer victim = game.getPlayer(victimNum);
        SOCBuildingSpeedEstimate estimate = getEstimator();
        int bestHex = robberHex;
        int worstSpeed = 0;
        final boolean skipDeserts = game.isGameOptionSet("RD");  // can't move robber to desert
        SOCBoard gboard = (skipDeserts ? game.getBoard() : null);

        int[] hexes = game.getBoard().getHexLandCoords();
        for (int i = 0; i < hexes.length; i++)
        {
            /**
             * only check hexes that we're not touching,
             * and not the robber hex, and possibly not desert hexes
             */
            if ((hexes[i] != robberHex)
                    && player.getNumbers().getNumberResourcePairsForHex(hexes[i]).isEmpty()
                    && ! (skipDeserts && (gboard.getHexTypeFromCoord(hexes[i]) == SOCBoard.DESERT_HEX )))
            {
                estimate.recalculateEstimates(victim.getNumbers(), hexes[i]);

                int[] speeds = estimate.getEstimatesFromNothingFast(victim.getPortFlags());
                int totalSpeed = 0;

                for (int j = SOCBuildingSpeedEstimate.MIN;
                        j < SOCBuildingSpeedEstimate.MAXPLUSONE; j++)
                {
                    totalSpeed += speeds[j];
                }

                D.ebugPrintlnINFO("total Speed = " + totalSpeed);

                if (totalSpeed > worstSpeed)
                {
                    bestHex = hexes[i];
                    worstSpeed = totalSpeed;
                    D.ebugPrintlnINFO("bestHex = " + Integer.toHexString(bestHex));
                    D.ebugPrintlnINFO("worstSpeed = " + worstSpeed);
                }
            }
        }

        D.ebugPrintlnINFO("%%% bestHex = " + Integer.toHexString(bestHex));

        /**
         * pick a spot at random if we can't decide.
         * Don't pick deserts if the game option is set.
         * Don't pick one of our hexes if at all possible.
         * It's not likely we'll need to pick one of our hexes
         * (we try 30 times to avoid it), so there isn't code here
         * to pick the 'least bad' one.
         * (TODO) consider that: it would be late in the game.
         *       Use similar algorithm as picking for opponent,
         *       but apply it worst vs best.
         */
        if (bestHex == robberHex)
        {
            int numRand = 0;
            while ((bestHex == robberHex)
                    || (skipDeserts
                            && (gboard.getHexTypeFromCoord(bestHex) == SOCBoard.DESERT_HEX ))
                            || ((numRand < 30)
                                    && player.getNumbers().getNumberResourcePairsForHex(bestHex).isEmpty()))
            {
                bestHex = hexes[Math.abs(brain.rand.nextInt()) % hexes.length];
                // D.ebugPrintln("%%% random pick = " + Integer.toHexString(bestHex));
                System.err.println("%%% random pick = " + Integer.toHexString(bestHex));
                ++numRand;
            }
        }
        return bestHex;
    }
    
    
    @Override
    public int selectMoveRobber(int robberHex) {        
        int victimNum = selectPlayerToThwart(robberHex);        
        return this.selectRobberHex(robberHex, victimNum);
    }

    /**
     * What resources to receive from playing a discovery development card given a targetted to have specific resource set
     * @param targetResources the resource set representing the goal
     */
    private void chooseFreeResources(SOCResourceSet targetResources)     {
        /**
         * clear our resource choices
         */
        resourceChoices.clear();

        /**
         * find the most needed resource by looking at
         * which of the resources we still need takes the
         * longest to aquire
         */
        SOCResourceSet rsCopy = player.getResources().copy();
        SOCBuildingSpeedEstimate estimate = getEstimator(player.getNumbers());
        int[] rollsPerResource = estimate.getRollsPerResource();

        for (int resourceCount = 0; resourceCount < 2; resourceCount++)
        {
            int mostNeededResource = -1;

            for (int resource = SOCResourceConstants.CLAY;
                    resource <= SOCResourceConstants.WOOD; resource++)
            {
                if (rsCopy.getAmount(resource) < targetResources.getAmount(resource))
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

            resourceChoices.add(1, mostNeededResource);
            rsCopy.add(1, mostNeededResource);
        }
    }

    @Override
    public void chooseFreeResources(SOCBuildPlanStack buildingPlan) {
        SOCPossiblePiece targetPiece = (SOCPossiblePiece) buildingPlan.getPlannedPiece(0);
        SOCResourceSet targetResources = SOCPlayingPiece.getResourcesToBuild(targetPiece.getType());

        chooseFreeResources(targetResources);
    }


    /**
     * Takes a table of nodes and adds a weighted score to
     * each node score in the table.  A vector of nodes that
     * we want to be near is also taken as an argument.
     * Here are the rules for scoring:
     * If a node is two away from a node in the desired set of nodes it gets 100.
     * Otherwise it gets 0.
     *
     * @param nodesIn   the table of nodes to evaluate
     * @param nodeSet   the set of desired nodes
     * @param weight    the score multiplier
     */
    protected void bestSpot2AwayFromANodeSet(Hashtable nodesIn, Vector nodeSet, int weight)
    {
        final SOCBoard board = game.getBoard();
        Enumeration nodesInEnum = nodesIn.keys();

        while (nodesInEnum.hasMoreElements())
        {
            Integer nodeCoord = (Integer) nodesInEnum.nextElement();
            int node = nodeCoord.intValue();
            int score = 0;
            final int oldScore = ((Integer) nodesIn.get(nodeCoord)).intValue();

            Enumeration nodeSetEnum = nodeSet.elements();

            while (nodeSetEnum.hasMoreElements())
            {
                int target = ((Integer) nodeSetEnum.nextElement()).intValue();

                if (node == target)
                {
                    break;
                }
                else if (board.isNode2AwayFromNode(node, target))
                {
                    score = 100;
                }
            }

            /**
             * multiply by weight
             */
            score *= weight;

            nodesIn.put(nodeCoord, Integer.valueOf(oldScore + score));

            //D.ebugPrintln("BS2AFANS -- put node "+Integer.toHexString(node)+" with old score "+oldScore+" + new score "+score);
        }
    }
    
    /**
     * @return the number of builds before the next turn during init placement
     */
    protected int numberOfEnemyBuilds()
    {
        int numberOfBuilds = 0;
        int pNum = game.getCurrentPlayerNumber();

        /**
         * This is the clockwise direction
         */
        if ((game.getGameState() == SOCGame.START1A) || (game.getGameState() == SOCGame.START1B))
        {
            do
            {
                /**
                 * look at the next player
                 */
                pNum++;

                if (pNum >= game.maxPlayers)
                {
                    pNum = 0;
                }

                if ((pNum != game.getFirstPlayer()) && ! game.isSeatVacant (pNum))
                {
                    numberOfBuilds++;
                }
            }
            while (pNum != game.getFirstPlayer());
        }

        /**
         * This is the counter-clockwise direction
         */
        do
        {
            /**
             * look at the next player
             */
            pNum--;

            if (pNum < 0)
            {
                pNum = game.maxPlayers - 1;
            }

            if ((pNum != game.getCurrentPlayerNumber()) && ! game.isSeatVacant (pNum))
            {
                numberOfBuilds++;
            }
        }
        while (pNum != game.getCurrentPlayerNumber());

        return numberOfBuilds;
    }
    
    /**
     * Estimator constructor.  While this is preferably deferred to the brain,
     *  in simulation situations this object doesn't have a brain.
     *  
     * Note that this may be overridden by extending classes.  However, it's 
     *  also not unreasonable to expect that simulation of opponent planning 
     *  would involve a more rough estimation than considering our own plans.
     * @param numbers
     * @return
     */
    protected SOCBuildingSpeedEstimate getEstimator(SOCPlayerNumbers numbers) {
        if (brain!=null) {
            return brain.getEstimator(numbers);
        }
        else {
            return new SOCBuildingSpeedFast(numbers);
        }
    }
    
    /**
     * Estimator constructor.  While this is preferably deferred to the brain,
     *  in simulation situations this object doesn't have a brain.
     *  
     * Note that this may be overridden by extending classes.  However, it's 
     *  also not unreasonable to expect that simulation of opponent planning 
     *  would involve a more rough estimation than considering our own plans.
     * @return
     */
    protected SOCBuildingSpeedEstimate getEstimator() {
        if (brain!=null) {
            return brain.getEstimator();
        }
        else {
            return new SOCBuildingSpeedFast();
        }
    }


	@Override
	public int choosePlayerToRob() {
		return -1; //return the unset choice because I am afraid I will break the unsetlling fragile logic in SOCRobotBrain
	}
}
