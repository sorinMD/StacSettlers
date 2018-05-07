package soc.robot.stac;

import supervised.main.BayesianSupervisedLearner;

import java.net.*;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Vector;
import java.util.logging.Level;

import com.google.common.io.Resources;

import simpleDS.learning.SimpleAgent;
import simpleDS.util.StringUtil;
import soc.debug.D;
import soc.dialogue.StacTradeMessage;
import soc.game.SOCBoard;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCTradeOffer;
import soc.game.StacTradeOffer;
import soc.robot.SOCBuildPlan;
import soc.robot.SOCBuildPlanStack;
import soc.robot.SOCBuildingSpeedEstimate;
import soc.robot.SOCPlayerTracker;
import soc.robot.SOCPossiblePiece;
import soc.robot.SOCPossibleSettlement;
import soc.robot.SOCResSetBuildTimePair;
import soc.robot.SOCRobotDM;
import soc.robot.SOCRobotDMImpl;
import soc.robot.SOCRobotNegotiator;
import soc.robot.SOCRobotNegotiatorImpl;
import soc.robot.stac.StacRobotNegotiator;
import soc.robot.stac.StacRobotType;
//import soc.robot.stac.StacRobotNegotiator.TradeOffer;
import soc.robot.stac.negotiationlearning.LearningNegotiator;
import soc.robot.stac.negotiationlearning.BoardGameState;
import soc.robot.stac.negotiationlearning.RewardFunction;
import soc.util.CutoffExceededException;
import weka.core.logging.Logger;

public class StacRobotNegotiator extends SOCRobotNegotiator<SOCBuildPlanStack> {

	/**
	 * Consider the entire plan when negotiating
	 */
	private final boolean useFullPlan;

	/**
	 * TODO: This is asking for trouble.  we should probably parameterize neg on braintype to allow 
	 * us to access implementation specific methods of brain, rather than storing the same variable twice.
	 */
	protected final StacRobotBrain brain;

	/**
	 *  Limit the number of trade offers any agent can make in a single game.  This is static so that it can be applied fairly to all agents.  Default to max-int (ie no limit)
	 */
	private static int maxTradeOffers = Integer.MAX_VALUE;

	/**
	 *  How many offers has the negotiator made in this game?  Used with maxTradeOffers, see above
	 */
	private int numOffersMade;

	/**
	 * Store the last offer building time so that StacRobotBrain can make a meta evaluation.
	 * This is only ever set in considerOfferOld(...)
	 */
	protected int lastOfferETA; 

	/**
	 * Store the global ETA for the last offer for sorting possible trade offers.
	 * This is only ever set in considerOfferOld(...)
	 */
	protected int lastOfferGlobalETA; 

	// HWU negotiators
	BayesianSupervisedLearner sltrader;
	//SimpleAgent deeptrader;
	LearningNegotiator mdp_negotiator;

	/**
	 * Resource set for "global" (ie not build-plan specific) ETA estimation.
	 * Used to break ties when two trades have a similar impact on current build plan ETA.
	 * This is the cost of buying one each of everything.
	 */
	private static final SOCResourceSet GLOBAL_ETA_RS = new SOCResourceSet(2, 4, 2, 3, 2, 0);    


	public StacRobotNegotiator(StacRobotBrain br, boolean fullPlan) {
		super(br);
		useFullPlan = fullPlan;
		brain = br;

		if ( brain.isRobotType(StacRobotType.SUP_LEARNING_NEGOTIATOR) ) {
			//System.out.println("TYPE=SUP!!!!");
			if ( brain.getSupervisedNegotiator() == null ) {
				System.out.println( "StacRobotNegotiator> creating SUP_LEARNING_NEGOTIATOR" );
				int withPrefsInt = (Integer) brain.getTypeParam( StacRobotType.SUP_LEARNING_NEGOTIATOR );
				boolean withPrefs = ( withPrefsInt == 1 );
				String simulationFolder = withPrefs ? "simulation2" : "simulation";
				System.out.println( "simulation folder: " + simulationFolder );
				sltrader = new BayesianSupervisedLearner( simulationFolder, "simulation", withPrefs );
				System.out.println( "created supervised learning trader" );
				brain.setSupervisedNegotiator( sltrader );
			} else {
				//System.out.println( "StacRobotNegotiator> retrieving SUP_LEARNING_NEGOTIATOR from client" );
				sltrader = brain.getSupervisedNegotiator();
			}

		} else if ( brain.isRobotType(StacRobotType.MDP_LEARNING_NEGOTIATOR) ) {
			//System.out.println("TYPE=MDP!!!!");
			if ( brain.getMDPNegotiator() == null ) {
				mdp_negotiator = new LearningNegotiator( this );
				brain.setMDPNegotiator( mdp_negotiator );
			} else {
				mdp_negotiator = brain.getMDPNegotiator();
			}
			mdp_negotiator.setPlayerNumber( brain.getPlayerNumber() );

		} else {
			//    System.out.println("TYPE=OTHER!!!!");
		}

		D.setLevel(D.INFO);
		D.ebug_disable();
	}

	/**
	 * Compute the resources required based on a build plan.
	 * Consider either the entire plan, or only the first item?  Test both!
	 * @param buildPlan
	 * @return
	 */
	protected SOCResourceSet getResourcesForPlan(SOCBuildPlan buildPlan) {
		SOCResourceSet targetResources = new SOCResourceSet();
		int numToConsider = useFullPlan ? buildPlan.getPlanDepth() : Math.min(1, buildPlan.getPlanDepth());
		for (int i=0; i<numToConsider; i++) {
			SOCPossiblePiece p = buildPlan.getPlannedPiece(i);
			targetResources.add( SOCPlayingPiece.getResourcesToBuild(p.getType()) );
		}

		return targetResources;
	}

	@Override
	public void resetOffersMade() {
		brain.getMemory().forgetAllTradeOffers();
		//offersMade = null;
	}

	@Override
	public void addToOffersMade(SOCTradeOffer offer) {
		D.ebugWARNING("Don't use addToOffersMade(SOCTradeOffer) in StacRobotNegotiator - use addToOffersMade(StacTradeOffer) instead!");
		StacTradeOffer o = new StacTradeOffer(offer);
		addToOffersMade(o);
	}

	public void addToOffersMade(StacTradeOffer offer) {
		brain.getMemory().rememberTradeOffer(offer);
	}

	@Override
	protected void resetIsSelling() {
		brain.getMemory().resetIsSelling();
	}

	@Override
	protected void markAsNotSelling(int pn, int rsrcType) {
		brain.getMemory().markAsNotSelling(pn, rsrcType);
	}

	@Override
	protected void markAsSelling(int pn, int rsrcType) {
		brain.getMemory().markAsSelling(pn, rsrcType);
	}

	@Override
	public SOCTradeOffer makeOffer(SOCBuildPlanStack buildPlan) {        
		return makeOffer(buildPlan, true, null, null);
	}

	@Override
	public void resetWantsAnotherOffer(){
		brain.getMemory().resetWantsAnotherOffer();
	}

	@Override
	public void markAsNotWantingAnotherOffer(int pn, int rsrcType){
		brain.getMemory().markAsNotWantingAnotherOffer(pn, rsrcType);
	}

	@Override
	public void markAsWantsAnotherOffer(int pn, int rsrcType){
		brain.getMemory().markAsWantsAnotherOffer(pn, rsrcType);
	}

	@Override
	public boolean wantsAnotherOffer(int pn, int rsrcType){
		return brain.getMemory().wantsAnotherOffer(pn, rsrcType);
	}

	@Override
	public void resetTradesMade() {
		brain.getMemory().resetPastTrades();
	}

	/**
	 * Actions the negotiator might want to take at the start of a turn, eg lifting or proposing trade embargoes.
	 * @param player  the player whose turn it is
	 */
	protected  void startTurnActions(int player) {
		checkForExpiredEmbargoes();
		checkForExpiredBlocks();

		//default behaviour is that we initiate embargoes at the start of the turn,
		//except if the NP_PROPOSE_EMBARGO_AFTER_OFFER flag is set
		if (!brain.isRobotType(StacRobotType.NP_PROPOSE_EMBARGO_AFTER_OFFER_FOR_IMMEDIATE_BUILD)) {
			boolean embargoPlayer = shouldPlayerBeEmbargoed(player);
			if (embargoPlayer) {
				brain.dialogueManager.sendProposeEmbargoAgainstPlayer(player);
			}
		}
	}

	protected void endTurnActions() {
		if ( brain.isRobotType(StacRobotType.MDP_LEARNING_NEGOTIATOR) ) {
			//			int totalVPs = getPlayerData().getTotalVP();
			//			System.out.printf( "StacRobotNegotiator::endTurnActions()> final update of MDP policy: %d VPs\n", totalVPs );
			//			mdp_negotiator.recordReward( totalVPs );
			mdp_negotiator.update();
			//			mdp_negotiator.numVPs = totalVPs;
			//System.out.println( "End of MDP turn" );
		}
	}

	/**
	 * Compute the best offer to make for the current build plan.
	 * This is the new STAC way of computing trade offers.
	 * @param buildPlan         the build plan towards which the trade is intended
	 * @param filterLikely      Whether we should filter based on what opponents are likely to accept.  Default behaviour is true, but may want to 
	 *                          override this, for example when using persuasive moves, or when using "simply ask" behavior
	 * @param invertedOriginalOfferForCounteroffer  if non-null, this offer is excluded as a possible offer (intended for use when computing a counteroffer)
	 * @param possibleReceiversForCounteroffer      the players that we can make the offer to (intended for use when computing a counteroffer)
	 * @return                  a StacTradeOffer or null
	 */
	public StacTradeOffer makeOffer(SOCBuildPlanStack buildPlan, boolean filterLikely, SOCTradeOffer invertedOriginalOfferForCounteroffer, boolean[] possibleReceiversForCounteroffer) {
		if (brain.isRobotType(StacRobotType.NO_TRADES)) {
			return null;
		}

		if (numOffersMade >= maxTradeOffers) {
			return null;
		}

		//FOR DEBUGGING
		if (D.ebugIsEnabled()) {
			if (!brain.isRobotType(StacRobotType.USE_OLD_NEGOTIATOR)) {
				SOCTradeOffer oldOffer = makeOfferImplOld(buildPlan);
				SOCTradeOffer newOffer = makeOfferImpl(buildPlan, true, null, null);
				if ((oldOffer != null && newOffer == null) ||
						(oldOffer == null && newOffer != null) ||
						(oldOffer != null && newOffer != null && !oldOffer.equals(newOffer))) {
					SOCResourceSet targetResources = getResourcesForPlan(buildPlan);
					SOCBuildingSpeedEstimate estimate = brain.getEstimator(ourPlayerData.getNumbers());

					int oldBatnaBuildingTime = Integer.MAX_VALUE;
					int newBatnaBuildingTime = Integer.MAX_VALUE;
					if (oldOffer != null) {
						oldBatnaBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, oldOffer.getGiveSet(), oldOffer.getGetSet(), estimate);
					}
					if (newOffer != null) {
						newBatnaBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, newOffer.getGiveSet(), newOffer.getGetSet(), estimate);
					}
					System.err.println("***** TRADES DIFFER *****\nOld offer (BT=" + oldBatnaBuildingTime + "): " + oldOffer + "\nNew offer (BT=" + newBatnaBuildingTime + "): " + newOffer );
				}
			}
		}

		StacTradeOffer ret = null;
		if (brain.isRobotType(StacRobotType.USE_OLD_NEGOTIATOR)) {
			SOCTradeOffer offer = makeOfferImplOld(buildPlan);
			if (offer != null)
				ret = new StacTradeOffer(offer);
		}
		else {
			/* Debug code begins */
			/*brain.sendChatText("BPC: " + getResourcesForPlan(buildPlan).toString());
            ret = makeOfferImplOld(buildPlan);
            if (ret == null) {
                brain.sendChatText("Old: null");
            }
            else {
                brain.sendChatText("Old: " + ret.toString());
            }*/
			/* Debug code ends */
			ret = makeOfferImpl(buildPlan, filterLikely, invertedOriginalOfferForCounteroffer, possibleReceiversForCounteroffer);
		}
		if (ret != null)
			numOffersMade++;

		return ret;
	}

	/**
	 * Refactored version of the original makeOffer method. 
	 * The basic approach is to generate all legal offers and then filter out offers that don't match a list of criteria:
	 * - We've made this or a more general offer before
	 * - Do we want to make this offer (is making the trade better than out BATNA)
	 * - Do we believe opponents can accept the offer
	 * - Do we believe opponents will accept the offer
	 * The remaining offers are then ranked and one of the best offers is chosen.
	 * This version of the method can also generate counteroffers.
	 * @param buildPlan                             the build plan towards which we're trying to trade
	 * @param filterLikely                          flag whether offers should be filtered out that we think the opponent likely won't accept
	 * @param invertedOriginalOfferForCounteroffer  for generating a counteroffer: an inverted version of the offer we're responding to
	 * @param possibleReceiversForCounteroffer      for generating a counteroffer: the list of possible recipients
	 * @return                                      a StacTradeOffer the robot wants to make or null
	 */
	private StacTradeOffer makeOfferImpl(SOCBuildPlanStack buildPlan, boolean filterLikely, SOCTradeOffer invertedOriginalOfferForCounteroffer, boolean[] possibleReceiversForCounteroffer) {
		SOCResourceSet targetResources = getResourcesForPlan(buildPlan); 
		SOCResourceSet ourResources = ourPlayerData.getResources();

		// Quickly return null if we're good to execute our build plan or have no resources
		if (ourResources.contains(targetResources) || ourResources.getTotal() == 0) {
			return null;
		}

		StacTradeOffer selectedOffer = null;
		StacTradeOffer classOffer = null;
		boolean to[] = getBaseRecipients();        

		if ( brain.isRobotType(StacRobotType.MDP_LEARNING_NEGOTIATOR) && !buildPlan.isEmpty() ) {// && brain.isOurTurn() ) {
			//return null;
			if ( mdp_negotiator.numVPs == 0 ) // value when makeOffer called for the first time
				mdp_negotiator.numVPs = brain.getPlayerData().getTotalVP();

			StacTradeOffer mdpOffer = mdp_negotiator.makeOffer( buildPlan, ourResources, brain.getGame().getName(), brain.getPlayerNumber(), ourPlayerData );
			if ( mdpOffer != null ) {
				if ( noRecipients(mdpOffer) ) {
					//mdp_negotiator.resetTradeRespCntr();
					//System.out.printf( "StacRobotNegotiator> selected no recipients MDP offer: %s\n", mdpOffer.toString() );
					return null;
				}
				if ( !brain.getMemory().pastTradeOfferExists(mdpOffer) ) {
					if (possibleReceiversForCounteroffer != null) {
						//System.out.println( "Adjusting receivers of counteroffer from MDP" );
						mdpOffer = new StacTradeOffer( mdpOffer.getGame(), mdpOffer.getFrom(), possibleReceiversForCounteroffer, mdpOffer.getGiveSet(), false, mdpOffer.getGetSet(), false );
					}
					//mdp_negotiator.setTradeRespCntr( 0 );
					//updatePreferences( mdpOffer ); // in case the opponents model our preferences...
					//System.out.printf( "StacRobotNegotiator> selected MDP offer: %s\n", mdpOffer.toString() );
					return mdpOffer;
				}
				//mdp_negotiator.resetTradeRespCntr();
				//System.out.printf( "StacRobotNegotiator> removing offer from history: %s\n", mdpOffer.toString() );
				//System.out.println( "StacRobotNegotiator> make offer: MDP returning null" );
				mdp_negotiator.cancelLastAction();
				return null;
			}
		}

		//Get the trades we could legally make with our resources without considering opponent resources
		List<TradeOfferWithStats> trades = getLegalOffers(to, !brain.isRobotType(StacRobotType.DONT_CONSIDER_OFFERING_2_RESOURCES), !brain.isRobotType(StacRobotType.DONT_CONSIDER_ASKING_FOR_2_RESOURCES));

		if (trades != null) {            

			if ( brain.isRobotType(StacRobotType.RANDOM_LEGAL_NEGOTIATOR) ) { //&& brain.isOurTurn() ) {
				if ( !trades.isEmpty() ) {
					StacTradeOffer randomOffer = selectRandomLegalOffer( trades );
					if (possibleReceiversForCounteroffer != null)
						return new StacTradeOffer( randomOffer.getGame(), randomOffer.getFrom(), possibleReceiversForCounteroffer, randomOffer.getGiveSet(), false, randomOffer.getGetSet(), false );
					return randomOffer;
					//return selectTopOffer( trades );
				}
			}

			if (D.ebugIsEnabled()) {
				System.err.println("\nLegal trades: " + trades.size());
				for (TradeOfferWithStats tradeOffer : trades) {
					SOCBuildingSpeedEstimate estimate = brain.getEstimator(ourPlayerData.getNumbers());
					int batnaBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, tradeOffer.getOffer().getGiveSet(), tradeOffer.getOffer().getGetSet(), estimate);
					System.err.println("\tBT=" + batnaBuildingTime + "  " + tradeOffer);
				}
			}

			//Filter the trades we have already made
			//Filter trades where we reverse a resource exchange with the receiver
			List<TradeOfferWithStats> remove = new ArrayList<TradeOfferWithStats>();
			for (TradeOfferWithStats t : trades) {
				StacTradeOffer offerToCheck = t.getOffer(); //new StacTradeOffer(game.toString(), opn, offeredTo, t.give, t.disjGive, t.get, t.disjGet);
				//Note: this only checks the give and get sets and not the addressees. 
				//Guess the assumption is that if the offer has come up previously, we considered all possible addressees already
				boolean offerMade = brain.getMemory().pastTradeOfferExists(offerToCheck);
				if (offerMade) {
					remove.add(t);
				} else {
					//Note: here, we're removing recipients from the 'to' with which there would be a reverse exchange of resources
					boolean noRecipientsRemaining = removeRecipientsWithReverseResourceExchange(offerToCheck);
					if (noRecipientsRemaining)
						remove.add(t);
				}
			}
			trades.removeAll(remove);
			if (trades.isEmpty())
				return null;

			//Filter out the trades that we don't think our opponent can make
			boolean optimisticAboutOpponentUnknowns = !brain.isRobotType(StacRobotType.PESSIMISTIC_ABOUT_OPPONENT_UNKONWNS);
			filterLegal(trades, optimisticAboutOpponentUnknowns);
			if (trades.isEmpty())
				return null;

			//Eliminate the trade offers that are worse than ones we already made, e.g. "give 1o, rec 1c" is worse than "give 2o, rec 1c"
			if (!brain.isRobotType(StacRobotType.DONT_FILTER_WORSE_OFFERS)) {
				filterWorseThanHistory(trades); 
				if (trades.isEmpty())
					return null;
			}

			//Filter out offers where we give away too many resources of the types we need for the BBP, so that we won't be able to execute our BBP
			if (brain.isRobotType(StacRobotType.FILTER_GIVING_AWAY_RESOURCES_NEEDED_FOR_BBP)) {
				filterGivingAwayResourcesNeededForBBP(trades); 
				if (trades.isEmpty())
					return null;
			}

			//Filter out offers with which we can get a blocked resource (if we're complying with these blocks)
			if (brain.isRobotType(StacRobotType.NP_BLOCK_TRADES_GULLIBLE) && brain.getMemory().getBlockedResources() != null) {
				filterTradesContainingBlockedResources(trades);
				if (trades.isEmpty())
					return null;
			}

			//Handling counteroffers
			if (invertedOriginalOfferForCounteroffer != null) {
				//Filter out offers that are not towards the possible receiver of a counteroffer 
				if (possibleReceiversForCounteroffer != null) {
					remove.clear();
					for (TradeOfferWithStats trade : trades) {
						boolean[] offerTo = trade.getOffer().getTo();
						for (int p = 0; p < possibleReceiversForCounteroffer.length; p++) {
							if (offerTo[p] && !possibleReceiversForCounteroffer[p])
								remove.add(trade);
						}
					}
					trades.removeAll(remove);
					if (0 == trades.size())
						return null;
				}

				//Filter out offers that match the original offer
				remove.clear();
				for (TradeOfferWithStats trade : trades) {
					SOCTradeOffer offer = trade.getOffer();
					//make sure that one of the give or get sets are equal in the two offers (so that we make a relevant counteroffer)
					if (offer.getFrom() == invertedOriginalOfferForCounteroffer.getFrom() &&
							offer.getGiveSet().equals(invertedOriginalOfferForCounteroffer.getGiveSet()) &&
							offer.getGetSet().equals(invertedOriginalOfferForCounteroffer.getGetSet())) {
						//check if the "to" arrays are disjoint
						boolean toAreDisjoint = true;
						for (int p = 0; p < offer.getTo().length; p++) {
							if (offer.getTo()[p] && invertedOriginalOfferForCounteroffer.getTo()[p]) {
								toAreDisjoint = false;
								break;
							}
						}
						if (!toAreDisjoint)
							remove.add(trade);
					}
				}
				trades.removeAll(remove);
				if (trades.isEmpty())
					return null;
			}

            if (D.ebugIsEnabled()) {
                System.err.println("Possible trades (ranked & filtered): " + trades.size());
                for (TradeOfferWithStats tradeOffer : trades) {
                    SOCBuildingSpeedEstimate estimate = brain.getEstimator(ourPlayerData.getNumbers());
                    int batnaBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, tradeOffer.getOffer().getGiveSet(), tradeOffer.getOffer().getGetSet(), estimate);
                    System.err.println("\tBT=" + batnaBuildingTime + "  " + tradeOffer);
                }
            }

			//Calculate the ETA and globalETA in the TradeOfferWithStats objects
			//Before this, they are undefined (actually: 1000). (We didn't need them up to now.)
			calculateBuildTimesAndFilterBadTrades(trades, buildPlan);

			/*
            if (brain.getPlayerNumber() == 2) {
                D.ebug_enable();
                D.ebugPrintINFO("\nRanked trades: " + trades.size() + "\n");
                for (TradeOfferWithStats t : trades)
                    D.ebugPrintlnINFO("   " + t.toString());
                D.ebug_disable();
            }
			 */

			//            if (brain.getPlayerNumber() == 2) {
			//                D.ebug_enable();
			//                D.ebugPrintINFO("\nRanked trades: " + trades.size() + "\n");
			//                for (TradeOfferWithStats t : trades)
			//                    D.ebugPrintlnINFO("   " + t.toString());
			//                D.ebug_disable();
			//            }

			//Keep the top trade offer, if we later decide to use it (if we're an OPTIMISTIC_OFFERER)
			//(After filtering the offers we think out opponent will accept.)
			StacTradeOffer topOffer = null;
			if (brain.isRobotType(StacRobotType.OPTIMISTIC_OFFERER) && trades.size() > 0) {
				//Rank the trades by their ETAs (or global ETAs)
				Collections.sort(trades);
				topOffer = trades.get(0).getOffer();
			}

			//Filter out trades that we don't think our opponents will accept
			//TODO: this probably won't work, because we only have imperfect information about the other agent 
			if (filterLikely) {
				filterLikely(trades, buildPlan);
				if (trades.isEmpty())
					return null;
			}

			if (D.ebugIsEnabled()) {
				System.err.println("Possible trades (ranked & filtered): " + trades.size());
				for (TradeOfferWithStats tradeOffer : trades) {
					SOCBuildingSpeedEstimate estimate = brain.getEstimator(ourPlayerData.getNumbers());
					int batnaBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, tradeOffer.getOffer().getGiveSet(), tradeOffer.getOffer().getGetSet(), estimate);
					System.err.println("\tBT=" + batnaBuildingTime + "  " + tradeOffer);
				}
			}

			//Aggregate multiple trade offers with the same give or get sets and aggregate them into a disjunctive or partial offer
			//We're not doing this, if we're computing a counteroffer; counteroffers should always be complete offers!
			if (invertedOriginalOfferForCounteroffer == null && brain.isRobotType(StacRobotType.NP_AGGREGATE_TRADE_OFFERS)) {
				List<TradeOfferWithStats> aggregateTrades = aggregateTrades(trades);
				if (aggregateTrades.size() > 0) {
					trades = aggregateTrades;
				}
			}

			//Pick one of the best offers
			if (trades.size() > 0) {
				selectedOffer = pickABestOffer(trades);
				D.ebugPrintINFO("Selected offer: " + selectedOffer.toString());
			}

			/****************************************************************************************************/
			brain.pointsTracker_rec.put(ourPlayerData.getName(), new Integer(ourPlayerData.getTotalVP()));

			if ( brain.isRobotType(StacRobotType.SUP_LEARNING_NEGOTIATOR) && !trades.isEmpty() ) {
				classOffer = selectOfferFromClassifier( trades );
				if ( classOffer != null ) {
					updatePreferences( classOffer );
				}
				return classOffer;				

			} else if ( brain.isRobotType(StacRobotType.RANDOM_NEGOTIATOR) && !trades.isEmpty() ) {
				int randomIndex = (int) Math.floor(Math.random() * trades.size());
				classOffer = trades.get(randomIndex).getOffer();
				return classOffer;

			} else if ( brain.isRobotType(StacRobotType.DRL_LEARNING_NEGOTIATOR) && !trades.isEmpty() ) {
				brain.rewards.reset();
				classOffer = null;
				for (int i=0; i<trades.size(); i++) {
					TradeOfferWithStats t = trades.get(i);
					//int opn = ourPlayerData.getPlayerNumber();
					//boolean[] offeredTo = new boolean[game.maxPlayers];
					//StacTradeOffer offerToCheck = new StacTradeOffer( game.toString(), opn, offeredTo, t.give, false, t.get, false );
					brain.rewards.setRewards(t.give.toString(), t.get.toString(), trades.size(), t.getOffer(), ""+(t.eta+t.globalETA));
				}

				String givables = selectedOffer.give.toString();
				String receivables = selectedOffer.get.toString();
				String defaultTrade = brain.rewards.getTradeFromGivablesReceivables(givables, receivables);
				String actions = brain.rewards.getAllowedActions(defaultTrade);
				if (actions != null && !actions.equals("")) {
					// DRL agent as client
					/*String agent = ourPlayerData.getName();
					if (agent.indexOf("_") == -1) {
						agent = "0";
					} else {
						agent = agent.substring(agent.indexOf("_")+1);
						agent = ""+(Integer.parseInt(agent)-1);
					}
					double reward = getOpponentBasedReward(ourPlayerData.getName());
					double _reward = getPointBasedReward(ourPlayerData.getName());
					String rewards = brain.rewards.getVectorOfRewards(actions, (reward+_reward), defaultTrade);

					String state = BoardGameState.getBoardRepresentation(ourPlayerData, game, null, buildPlan);
					int dialogues = 1;// TBD
					String message = "agent="+agent+"|state="+state+"|actions="+actions+"|rewards="+rewards+"|dialogues="+dialogues;
					brain.dialogueManager.deeptrader.sendMessage(message);
					String lastDeepTrade = null;
					while (true) {
						try {
							Thread.sleep(1);
							//System.out.println("waiting...");
							lastDeepTrade = brain.dialogueManager.deeptrader.getLastTrade(agent);
							if (lastDeepTrade != null && !lastDeepTrade.equals("undefined")) {
								if (brain.rewards.hasAction(lastDeepTrade)) {
									break;
								} else {
									System.out.println("WARNING: UNKNOWN lastDeepTrade="+lastDeepTrade);
								}
							}

						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					} 
					classOffer = brain.rewards.offerList.get(lastDeepTrade);
					brain.dialogueManager.deeptrader.setLastTrade(agent, null);*/

					// DRL agent as server
					String state = BoardGameState.getBoardRepresentation(ourPlayerData, game, null, buildPlan);
					String lastDeepTrade = getTradeFromServer("agent=0|state="+state+"|actions="+actions); 
					classOffer = brain.rewards.offerList.get(lastDeepTrade);

					// sanity check
					if (classOffer == null) { 
						System.out.println("WARNING: selectedOffer is NULL actions="+actions + " rewards.offerList="+brain.rewards.offerList.keySet().toString() + " lastDeepTrade="+lastDeepTrade);
						System.out.println("trades="+trades.toString());
						System.exit(0);

					} else {
						return classOffer;
					}
				}
			}
			/****************************************************************************************************/

			//If we haven't chosen anything yet, and we're an optimistic offerer, choose the best of our trade offers regardless of the previous decision
			if (selectedOffer == null && brain.isRobotType(StacRobotType.OPTIMISTIC_OFFERER)) {
				Integer thresh = (Integer) brain.getTypeParam(StacRobotType.OPTIMISTIC_OFFERER);
				if ((RANDOM.nextInt(100)) < thresh) {
					selectedOffer = topOffer;
				}
			}
		}

		//for hitting a breakpoint during debugging
		//        if (selectedOffer != null && (selectedOffer.hasDisjunctiveGiveSet() || selectedOffer.hasDisjunctiveGetSet()))
		//            System.err.println("disjunctive res set!");
		return selectedOffer;
	}

	private String getTradeFromServer(String message) {
		String host = "localhost";
		int port = 7777;
		String inputLine = "";

		try {
			Socket clientSocket = new Socket(host, port);
			PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
			writer.println(message);

			BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			while((inputLine = reader.readLine()) != null) break;
			clientSocket.close();

		} catch(IOException e) {
			System.out.println("StacRobotNegotiator: Couldn't communicate with " + host + " on port: " + port);
			e.printStackTrace();
		}

		return inputLine;
	}

	private double getPointBasedReward(String agentInFocus) {
		double currPointsPlayer = brain.pointsTracker_rec.get(agentInFocus).doubleValue();
		double reward = 0;//currPointsPlayer - prevPointsPlayer;
		if (currPointsPlayer > brain.prevPointsPlayer && brain.prevPointsPlayer > 0) {
			reward = (double) (currPointsPlayer - brain.prevPointsPlayer)*1;
			//} else if (currPointsPlayer > 0) {
			//	reward = ((double) currPointsPlayer/10);
		}

		reward += ((double) currPointsPlayer/10);

		brain.prevPointsPlayer = (int) currPointsPlayer;
		brain.pointsTracker_old.putAll(brain.pointsTracker_rec);

		return reward;
	}

	private double getOpponentBasedReward(String agentInFocus) {
		double currPointsPlayer = 0;
		double reward = 0;

		for (String agent : brain.pointsTracker_rec.keySet()) {
			if (agent.equals(agentInFocus)) {
				currPointsPlayer += brain.pointsTracker_rec.get(agentInFocus).doubleValue();
			} else {
				currPointsPlayer -= brain.pointsTracker_rec.get(agent).doubleValue();
			}
		}

		if (currPointsPlayer != brain.prevPointsPlayer) {// && prevPointsPlayer > 0) {
			reward = (currPointsPlayer - brain.prevPointsPlayer)*1;
		}
		brain.prevPointsPlayer = (int) currPointsPlayer;

		return reward;
	}

	private void updatePreferences( SOCTradeOffer offer ) {
		String player = ourPlayerData.getName();
		String receivables = offer.getGetSet().toString();
		accumulatePreferences(player, receivables);
		/*
    	String givables = offer.getGiveSet().toString();
    	String resources = ourPlayerData.getResources().toString();
    	System.out.println("*PLAYER:"+player + " HAS:"+resources + " GIV:"+givables+ " GET:"+receivables + " bestScore="+bestScore);// + " give="+selectedOffer.getGiveSet());
    	if (player.equals("SUPERVISED_BAYESIAN")) {
    		IOUtil.printHashMapArrayList(brain.tradePreferences, "brain.tradePreferences");
    	}*/    	
	}

	@SuppressWarnings("unchecked")
	public float[] getExpectedResourcesFromRoll( int pn ) {
		float[] board_value_resources = new float[5];

		//TODO take into account probability of dice rolls for the hexes: 
		float[] dice_probs = new float[11];
		for ( int j = 0; j < dice_probs.length; j++ ) {
			if ( j < 6 )
				dice_probs[j] = (float) (j+1) / 36;
			else
				dice_probs[j] = (float) (11-j) / 36;
		}

		SOCBoard board = ourPlayerData.getGame().getBoard();
		int robber_coord = board.getRobberHex();
		Vector<SOCPlayingPiece> pieces = board.getPieces();
		int num_opp_set = 0;
		int num_opp_cit = 0;
		for ( SOCPlayingPiece piece : pieces ) {
			if ( piece.getPlayer().getPlayerNumber() != pn ) {
				int c = piece.getCoordinates();
				Vector<Integer> adj_hexes = SOCBoard.getAdjacentHexesToNode( c ); 
				for ( Integer hex : adj_hexes ) {
					if ( robber_coord != hex ) {
						int hex_num = board.getNumberOnHexFromCoord( hex );
						if ( hex_num > 1 && hex_num < 13 ) {
							int hex_type = board.getHexTypeFromCoord( hex );
							if ( piece.getType() == SOCPlayingPiece.SETTLEMENT ) {
								num_opp_set++;
								// add expected number of resources from dice roll for this settlement
								board_value_resources[ hex_type - SOCBoard.CLAY_HEX ] += 1 * dice_probs[hex_num-2];
							} else if ( piece.getType() == SOCPlayingPiece.CITY ) {
								num_opp_cit++;
								// add expected number of resources from dice roll for this city
								board_value_resources[ hex_type - SOCBoard.CLAY_HEX ] += 2 * dice_probs[hex_num-2];
							}
						}
					}
				}        		
			}
		}
		System.out.printf( "expResRoll> %d settls %d cities", num_opp_set, num_opp_cit );
		for ( int i = 0; i < board_value_resources.length; i++ )
			System.out.printf( " %d:%.3f", i, board_value_resources[i] );
		System.out.println();

		return board_value_resources;
	}

	private void accumulatePreferences(String player, String receivables) {
		ArrayList<String> resources = StringUtil.getArrayListFromString(receivables, "|");
		for (String pair : resources) {
			String res = pair.substring(0, pair.indexOf("="));
			String val = pair.substring(pair.indexOf("=")+1);
			if (!val.equals("0") && !res.equals("unknown")) {
				ArrayList<String> localPreferences = brain.tradePreferences.get(player);
				if (localPreferences == null) {
					localPreferences = new ArrayList<String>();

				}
				if (!localPreferences.contains(res)) { 
					localPreferences.add(res);
					brain.tradePreferences.put(player, localPreferences);
				}
			}
		}
	}

	private StacTradeOffer selectOfferFromClassifier( List<TradeOfferWithStats> trades ) {

		StacTradeOffer bestOffer = null;
		Iterator<TradeOfferWithStats> i = trades.iterator();
		double bestScore = 0;
		while (i.hasNext()) {
			TradeOfferWithStats t = i.next();
			int opn = ourPlayerData.getPlayerNumber();
			boolean[] offeredTo = new boolean[game.maxPlayers];
			StacTradeOffer offerToCheck = new StacTradeOffer( game.toString(), opn, offeredTo, t.give, false, t.get, false );

			if ( !brain.getMemory().pastTradeOfferExists(offerToCheck) ) {
				String numKnights = ""+ourPlayerData.getNumKnights();
				String numDevCards = ""+ourPlayerData.getDevCards().getTotal();
				String numRoads = ""+ourPlayerData.getRoads().size();
				String numSettlements = ""+ourPlayerData.getSettlements().size();
				String numCities = ""+ourPlayerData.getCities().size();
				String buildups = "knights="+numKnights + "|devcards="+numDevCards + "|roads="+numRoads + "|settlements="+numSettlements + "|cities="+numCities;
				//System.out.println("buildups=>"+buildups);
				String resources = ourPlayerData.getResources().toString();
				double score = sltrader.getScoredOffer(resources, buildups, t.give.toString(), t.get.toString(), brain.tradePreferences);
				if (score > bestScore) {
					bestOffer = t.getOffer();
					bestScore = score;
				}
				//int randomIndex = (int) Math.floor(Math.random() * trades.size());
				//selectedOffer = trades.get(randomIndex).getOffer();
				//System.out.println("offer="+t.toString() + " resources=" + resources + " score="+score);// + " give="+selectedOffer.getGiveSet());
				//System.out.println("HAS:"+resources + " GIV:"+t.give.toString()+ " GET:"+t.get.toString() + " score="+score);// + " give="+selectedOffer.getGiveSet());
			}
		}

		//System.out.printf( "Selected legal offer after data-driven re-ranking: %s\n", (bestOffer==null?"null":bestOffer.toString()) );
		return bestOffer;

	}

	/*private void recordInfo_TradeOffers( List<TradeOfferWithStats> trades ) {

		rewards = new RewardFunction();
		StacTradeOffer bestOffer = null;
		Iterator<TradeOfferWithStats> i = trades.iterator();
		int counter = 0;
		while (i.hasNext()) {
			TradeOfferWithStats t = i.next();
			int opn = ourPlayerData.getPlayerNumber();
			boolean[] offeredTo = new boolean[game.maxPlayers];
			StacTradeOffer offerToCheck = new StacTradeOffer( game.toString(), opn, offeredTo, t.give, false, t.get, false );

<<<<<<< .working
			//System.out.println("hello from selectOfferFromClassifier!!! trades="+trades.size());
=======
    /**
	 * Get the (public) VP of the current leader.
	 * @return 
	 */

	private StacTradeOffer selectRandomLegalOffer( List<TradeOfferWithStats> trades ) {
		Random rand = new Random();
		int rand_off_ind;
		int ind_count = 0;
		TradeOfferWithStats rand_off;
		StacTradeOffer rand_tr_off;
		while ( ind_count <= 3 ) {
			ind_count++;
			rand_off_ind = rand.nextInt( trades.size() );
			rand_off = trades.get( rand_off_ind );
			rand_tr_off = rand_off.getOffer();
			if ( !brain.getMemory().pastTradeOfferExists(rand_tr_off) && !noRecipients(rand_off) ) {
				System.out.printf( "Selected random legal offer: %s\n", rand_tr_off.toString() );
				return rand_tr_off;
			}
		}
		return null;
	}

	private StacTradeOffer selectTopOffer( List<TradeOfferWithStats> trades ) {
		StacTradeOffer top_tr_off;
		//    	List<TradeOfferWithStats> trades_reversed = new ArrayList<TradeOfferWithStats>( trades );
		//    	Collections.reverse( trades_reversed );
		//    	for ( TradeOfferWithStats to : trades_reversed ) {
		for ( TradeOfferWithStats to : trades ) {
			top_tr_off = to.getOffer();
			if ( !brain.getMemory().pastTradeOfferExists(top_tr_off) ) {
				System.out.printf( "Selected top legal offer: %s\n", top_tr_off.toString() );
				return top_tr_off;
			}
		}
		return null;
	}

	private boolean noRecipients( TradeOfferWithStats trade ) {
		return noRecipients( trade.getOffer() );
	}

	private boolean noRecipients( SOCTradeOffer trade ) {
		boolean[] to = trade.getTo();
		for ( int i = 0; i < to.length; i++ )
			if ( to[i] )
				return false;
		return true;
	}

	public SOCPlayer getPlayerData() {
		return brain.getPlayerData();
	}

	/**
	 * Tell the negotiator to avoid trading with a given player.
	 * NB: Part of Kevin's implementation!
	 * @param playerNum
	 */
	public void blockTrades(int playerNum) {
		brain.getMemory().blockTrades(playerNum);
	}
	/**
	 * NB: Part of Kevin's implementation!
	 * @param playerNum
	 * @return 
	 */
	public boolean isBlockingTrades(int playerNum) {
		return brain.getMemory().isBlockingTrades(playerNum);
	}

	/**
	 * If we lift embargoes after a specific number of turns, check whether they are expiring.
	 * Also tell the other players with a chat message via the dialogue manager.
	 */
	protected void checkForExpiredEmbargoes() {
		//check whether it's time to lift embargoes
		if (brain.isRobotType(StacRobotType.NP_LIFT_EMBARGOES_AFTER_TURNS)) {
			int embargoLength = (Integer)brain.getTypeParam(StacRobotType.NP_LIFT_EMBARGOES_AFTER_TURNS);
			for (int p = 0; p < game.getPlayers().length; p++) {
				if (brain.getMemory().isPlayerEmbargoed(p)) {
					int embargoStart = brain.getMemory().getTurnEmbargoStarted(p);
					int currentTurn = game.getTurnCount();
					int turnsEmbargoInEffect = currentTurn - embargoStart;
					if (turnsEmbargoInEffect >= embargoLength) {
						brain.getMemory().setEmbargoAgainstPlayer(p, false);
						int pVP = game.getPlayer(p).getPublicVP();
						//                        System.err.println(game.getTurnCount() + ": " + brain.getPlayerNumber() + 
						//                                ": Lifting embargo against player " + p + " who has " + pVP + "VP. I have " + brain.getPlayerData().getPublicVP() + "VP.");
						brain.dialogueManager.sendLiftEmbargoAgainstPlayer(p);
					}
				}
			}
		}
	}

	/**
	 * Check whether we want to propose an embargo against a player.
	 * If all conditions are met, instruct the dialogue manager to send the dialogue move.
	 * @param player  the player to check
	 * @return        boolean whether the player should be embargoed
	 */
	protected boolean shouldPlayerBeEmbargoed(int player) {
		//consider staring an embargo against the player who start his turn now
		//DO THIS BEFORE THE OTHER START TURN CHAT
		if (brain.isRobotType(StacRobotType.NP_PROPOSE_EMBARGO_WHEN_LEADER_HAS_VP)) {

			//get VPs for player whose turn is starting and for the current leader
			int pVP = game.getPlayer(player).getPublicVP();
			int leaderVP = getLeaderVP();

			//get the VP threshold for considering embargoes
			int vpThreshold = 0;
			if (brain.isRobotType(StacRobotType.NP_PROPOSE_EMBARGO_WHEN_LEADER_HAS_VP)) {
				vpThreshold = (Integer)brain.getTypeParam(StacRobotType.NP_PROPOSE_EMBARGO_WHEN_LEADER_HAS_VP);
			}

			//get the number of embargoes we can propose in this game
			int numOfEmbargoesICanProposeInThisGame = 1000;
			if (brain.isRobotType(StacRobotType.NP_NUM_EMBARGOES_AGENT_CAN_PROPOSE)) {
				numOfEmbargoesICanProposeInThisGame = (Integer)brain.getTypeParam(StacRobotType.NP_NUM_EMBARGOES_AGENT_CAN_PROPOSE);
			}
			//            System.err.println(getPlayerNumber() + ": Considering embargo against player " + player + " who has " + pVP + "VP. I have " + getPlayerData().getPublicVP() + "VP.");

			if (player != brain.getPlayerNumber() && //don't embargo ourself
					brain.getMemory().getNumOfEmbargoedPlayers() < 1 && //only embargo 1 player at most
					pVP >= vpThreshold &&
					game.getPlayer(player).getPublicVP() >= leaderVP && //only embago the leader
					brain.getMemory().getNumEmbargoesProposed() < numOfEmbargoesICanProposeInThisGame) {
				//                System.err.println(game.getTurnCount() + ": " + brain.getPlayerNumber() + 
				//                        ": Proposing embargo against player " + player + " who has " + pVP + "VP. I have " + brain.getPlayerData().getPublicVP() + "VP.");
				brain.getMemory().setEmbargoAgainstPlayer(player, true);
				brain.getMemory().incNumEmbargoesProposed();
				return true;
			}
		}

		return false;
	}

	/** 
	 * Given a trade offer by an opponent, do we want to embargo the opponent?
	 * @param offer
	 * @return 
	 */
	protected boolean shouldPlayerBeEmbargoedAfterTradeOffer(SOCTradeOffer offer) {
		//first do the normal check
		boolean preconditions = shouldPlayerBeEmbargoed(offer.getFrom());
		if (!preconditions) {
			return false;
		}

		return brain.getNegotiator().isOpponentOfferForImmediateBuild(offer);
	}

	/**
	 * The dialogue manager tells us that a player proposed a trade embargo against another player.
	 * @param proposer      player proposing the embargo
	 * @param embargoTarget player targeted by the proposed embargo
	 */
	protected void embargoProposed(int proposer, int embargoTarget) {
		if (brain.isRobotType(StacRobotType.NP_COMPLY_WITH_EMBARGOES)) {
			if (embargoTarget != brain.getPlayerNumber()) {
				//                System.err.println(game.getTurnCount() + ": " + brain.getPlayerNumber() + 
				//                        ": Accepting embargo against player " + embargoTarget + " who has " + game.getPlayer(embargoTarget).getTotalVP() + "VP. I have " + brain.getPlayerData().getTotalVP() + "VP.");
				brain.getMemory().setEmbargoAgainstPlayer(embargoTarget, true);
				brain.getMemory().incNumEmbargoesCompliedWith();
				brain.getDialogueManager().sendComplyingWithEmbargoAgainstPlayer(embargoTarget);
			}
		}
	}

	/**
	 * Decide if we should try to block the offer.
	 * @param offer  offer under consideration
	 * @return block yes/no
	 */
	boolean shouldOfferBeBlocked(SOCTradeOffer offer) {
		boolean response = true;

		//just a stupid decision that we're blocking everybody else's offers but not our own
		if (offer.getFrom() == brain.getPlayerNumber()) {
			return false;
		}

		// testig how far the leader is
		if (brain.isRobotType(StacRobotType.NP_PROPOSE_BLOCK_WHEN_LEADER_HAS_VP)) {
			int minVP = (Integer)brain.getTypeParam(StacRobotType.NP_PROPOSE_BLOCK_WHEN_LEADER_HAS_VP);
			int leaderVP = getLeaderVP();
			if (leaderVP < minVP) {
				return false;
			}            
		}

		if (brain.isRobotType(StacRobotType.NP_NUM_BLOCKS_AGENT_CAN_PROPOSE)) {
			int numCanPropose = (Integer)brain.getTypeParam(StacRobotType.NP_NUM_BLOCKS_AGENT_CAN_PROPOSE);
			int numHasProposed = brain.getMemory().getNumBlockMoves();
			if (numHasProposed >= numCanPropose) {
				return false;
			}
		}

		if (brain.isRobotType(StacRobotType.NP_BLOCK_ONLY_FOR_IMMEDIATE_BUILD)) {
			response = isOpponentOfferForImmediateBuild(offer);
		}

		return response;
	}

	/**
	 * If we lift blocked resources after a specific number of turns, check whether the block is expiring.
	 * This means, we can make offers for the blocked resources again.
	 */

	protected void checkForExpiredBlocks() {
		// reset blocked resources if conditions are met, i.e. from now on we can try to negotiate to get any resource again
		if (brain.isRobotType(StacRobotType.NP_LIFT_BLOCK_AFTER_TURNS)) {
			int numBlockTurns = (Integer)brain.getTypeParam(StacRobotType.NP_LIFT_BLOCK_AFTER_TURNS);
			int turnBlockStarted = brain.getMemory().getTurnBlockStarted();
			// see whehter a block is in effect
			if (turnBlockStarted > 0) {
				int turnsBlockInEffect = game.getTurnCount() - turnBlockStarted;
				if (turnsBlockInEffect >= numBlockTurns) {
					brain.getMemory().setTurnBlockStarted(0);
					brain.getMemory().setBlockedResources(new SOCResourceSet());
				}
			}
		}

	}

	/**
	 * Get the ratio for trades with bank/port for the specified player and resource.
	 * @param pn
	 * @param res
	 * @return 
	 */
	protected int tradeRatioForResource(int pn, int res) {
		boolean[] ports = brain.getMemory().getPlayer(pn).getPortFlags();
		int tradeRatio;
		if (ports[res]) {
			tradeRatio = 2;
		} else if (ports[SOCBoard.MISC_PORT]) {
			tradeRatio = 3;
		} else {
			tradeRatio = 4;
		}
		return tradeRatio;
	}

	/**
	 * Can/do we want to force other robots to accept the offer?
	 * If we're in a context without any prior rejections, the rejections parameter should be null.
	 * @param offer  the offer we're thinking about forcing
	 * @param buildPlan
	 * @param rejections  the rejections we received for offer
	 * @return
	 */
	protected boolean shouldOfferBeForced(SOCTradeOffer offer, SOCBuildPlanStack buildPlan, boolean[] rejections) {    	    	
		if (brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_PROPOSER)) {
			//if we're only making force-accepts after a rejection check if we're in a state after our offer has been rejected
			if (brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_ONLY_AFTER_REJECT)) {
				if (rejections == null) {
					return false;
				}
				boolean rejected = false;
				for (boolean r : rejections) {
					rejected |= r;
				}
				if (!rejected) {
					return false;
				}
			}

			// check if we only make force-accept offers if we know that at least one of the intended trade partners has the necessary resources
			if (brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_ONLY_IF_POSSIBLE)) {
				boolean forced = false; //if we don't find an opponent who can make the trade, we don't make a force-accept
				boolean to[] = offer.getTo();
				for (int p = 0; p < game.getPlayers().length; p++) {
					if (to[p]) {
						SOCResourceSet opponentRes = brain.getMemory().getOpponentResources(p);
						SOCResourceSet getRes = offer.getGetSet();
						if (opponentRes.contains(getRes)) {
							forced = true;
							break;
						}
					}
				}
				// we believe, no opponent can make the trade, so we're done
				if (!forced) {
					return false;
				}
			}

			if (brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_BUILD)) {
				SOCResourceSet targetResources = getResourcesForPlan(buildPlan);
				SOCResourceSet currentResources = brain.getMemory().getResources();
				SOCResourceSet getResources = offer.getGetSet();
				SOCResourceSet resourcesAfterTrade = currentResources.copy();
				resourcesAfterTrade.add(getResources);
				//TODO: we have to subtract the resources we're giving away here!
				if (!resourcesAfterTrade.contains(targetResources)) {
					// I can't build immediately after this trade, so I'm done
					return false;
				}
			}


			//*******************************************
			// here start the moves considering how to persuade the opponent
			boolean makeMiscMove = false;


			// "make this trade, and you can build immediately too!"
			if (brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BUILD) ||
					brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_MISC_MOVES_ALL)) {
				boolean forced = false; //if we don't find an opponent who can build immediately after making the trade, we don't make a force-accept
				for (int pn = 0; pn < 4; pn++) {
					if (offer.getTo()[pn]) {
						// can he build with the resources after the trade?
						SOCResourceSet resourcesAfterTrade = opponentResourcesAfterTrade(offer, pn);
						boolean[] piecesAvailbleAfterTrade = piecesAvailableToBuildWithResources(resourcesAfterTrade);
						for (boolean value : piecesAvailbleAfterTrade) {
							if (value) {
								forced = true;
								makeMiscMove = true;
							}
						}
					}
				}
				// we believe, no opponent can build immediately after the trade, so we're done
				if (!forced) {
					//HACKY COMBINATION OF "FORCE_ACCEPT_ONLY" PARAMETERS
					if (!brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_MISC_MOVES_ALL)) {
						return false;
					}
				}
			}

			// "make this trade, and you can build immediately something that you couldn't build before!"
			if (brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BUILD_NOT_POSSIBLE_BEFORE) ||
					brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_MISC_MOVES_ALL) ||
					brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_MISC_MOVES_NPB)) {
				boolean forced = false; //if we don't find an opponent who can build something new immediately after making the trade, we don't make a force-accept
				for (int pn = 0; pn < 4; pn++) {
					if (offer.getTo()[pn]) {
						// what can he build with the resources he already has?
						SOCResourceSet oppResources = brain.getMemory().getOpponentResources(pn);
						boolean[] piecesAvailbleBeforeTrade = piecesAvailableToBuildWithResources(oppResources);

						// can he build with the resources after the trade?
						SOCResourceSet resourcesAfterTrade = opponentResourcesAfterTrade(offer, pn);
						boolean[] piecesAvailbleAfterTrade = piecesAvailableToBuildWithResources(resourcesAfterTrade);
						for (int i = 0; i < piecesAvailbleAfterTrade.length; i++) {
							if (!piecesAvailbleBeforeTrade[i] && piecesAvailbleAfterTrade[i]) {
								forced = true;
								makeMiscMove = true;
							}
						}
					}
				}
				// we believe, no opponent can build immediately after the trade, so we're done
				if (!forced) {
					//HACKY COMBINATION OF "FORCE_ACCEPT_ONLY" PARAMETERS
					if (!brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_MISC_MOVES_ALL) && !brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_MISC_MOVES_NPB)) {
						return false;
					}
				}
			}

			if (brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BANK_TRADE) ||
					brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_MISC_MOVES_ALL)) {
				boolean forced = false; //if we don't find an opponent who can build something new immediately after making the trade, we don't make a force-accept
				for (int pn = 0; pn < 4; pn++) {
					if (offer.getTo()[pn]) {
						// can he make a bank or port trade after the trade?
						SOCResourceSet resourcesAfterTrade = opponentResourcesAfterTrade(offer, pn);
						for (int res = SOCResourceConstants.CLAY; res <= SOCResourceConstants.WOOD; res++) {
							int tradeRatio = tradeRatioForResource(pn, res);
							if (resourcesAfterTrade.getAmount(res) >= tradeRatio) {
								forced = true;
								makeMiscMove = true;
							}
						}
					}
				}
				// we believe, no opponent can build immediately after the trade, so we're done
				if (!forced) {
					//HACKY COMBINATION OF "FORCE_ACCEPT_ONLY" PARAMETERS
					if (!brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_MISC_MOVES_ALL)) {
						return false;
					}
				}
			}

			if (brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BANK_TRADE_NOT_POSSIBLE_BEFORE) ||
					brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_MISC_MOVES_ALL) ||
					brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_MISC_MOVES_NPB)) {
				boolean forced = false; //if we don't find an opponent who can build something new immediately after making the trade, we don't make a force-accept
				for (int pn = 0; pn < 4; pn++) {
					if (offer.getTo()[pn]) {
						// can he make a bank or port trade after the trade that's not possible before?
						SOCResourceSet resourcesBeforeTrade = brain.getMemory().getOpponentResources(pn);
						SOCResourceSet resourcesAfterTrade = opponentResourcesAfterTrade(offer, pn);
						for (int res = SOCResourceConstants.CLAY; res < SOCResourceConstants.WOOD; res++) {
							int tradeRatio = tradeRatioForResource(pn, res);
							if (resourcesBeforeTrade.getAmount(res) < tradeRatio && resourcesAfterTrade.getAmount(res) >= tradeRatio) {
								forced = true;
								makeMiscMove = true;
								break;
							}
						}
					}
				}
				// we believe, no opponent can make a bank/port trtade after the trade that he couldn't make before anyway, so we're done
				if (!forced) {
					//HACKY COMBINATION OF "FORCE_ACCEPT_ONLY" PARAMETERS
					if (!brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_MISC_MOVES_ALL) && !brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_MISC_MOVES_NPB)) {
						return false;
					}
				}
			}

			//            

			// "make this trade, and you can immediately make a bank trade or build something that you couldn't build before!"
			if (brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BUILD_OR_BANK_TRADE_NOT_POSSIBLE_BEFORE) ||
					brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_MISC_MOVES_ALL) ||
					brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_MISC_MOVES_NPB)) {
				boolean forced = false; //if we don't find an opponent who can build something new immediately after making the trade, we don't make a force-accept
				for (int pn = 0; pn < 4; pn++) {
					if (offer.getTo()[pn]) {
						//what resources does pn have before and after the trade?
						SOCResourceSet resourcesBeforeTrade = brain.getMemory().getOpponentResources(pn);
						SOCResourceSet resourcesAfterTrade = opponentResourcesAfterTrade(offer, pn);

						// can he build something after the trade not availbale before?
						boolean[] piecesAvailbleBeforeTrade = piecesAvailableToBuildWithResources(resourcesBeforeTrade);
						boolean[] piecesAvailbleAfterTrade = piecesAvailableToBuildWithResources(resourcesAfterTrade);
						for (int i = 0; i < piecesAvailbleAfterTrade.length; i++) {
							if (!piecesAvailbleBeforeTrade[i] && piecesAvailbleAfterTrade[i]) {
								forced = true;
								makeMiscMove = true;
								break;
							}
						}

						//have we already found something?
						if (!forced) {

							// can he make a bank or port trade after the trade that's not possible before?
							for (int res = SOCResourceConstants.CLAY; res <= SOCResourceConstants.WOOD; res++) {
								int tradeRatio = tradeRatioForResource(pn, res);
								if (resourcesBeforeTrade.getAmount(res) < tradeRatio && resourcesAfterTrade.getAmount(res) >= tradeRatio) {
									forced = true;
									makeMiscMove = true;
									break;
								}
							}
						}
					}
				}
				// we believe, no opponent fulfils one of the conditions above, so we're done
				if (!forced) {
					//HACKY COMBINATION OF "FORCE_ACCEPT_ONLY" PARAMETERS
					if (!brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_MISC_MOVES_ALL) && !brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_MISC_MOVES_NPB)) {
						return false;
					}
				}
			}

			// "make this trade, and you can make this bank trade and then build immediately something that you couldn't build before!"
			if (brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BANK_TRADE_FOR_BUILD_NOT_POSSIBLE_BEFORE) ||
					brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BANK_TRADE_FOR_IMMEDIATE_BUILD_NOT_POSSIBLE_BEFORE) ||
					brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_MISC_MOVES_ALL) ||
					brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_MISC_MOVES_NPB)) {
				//if we don't find an opponent who after the trade can make a bank trade and then build something new immediately, we don't make a force-accept
				boolean forced = false;

				for (int pn = 0; pn < 4; pn++) {
					if (offer.getTo()[pn]) {
						// what can he build with the resources he already has?
						SOCResourceSet oppResources = brain.getMemory().getOpponentResources(pn);
						boolean[] piecesAvailbleBeforeTrade = piecesAvailableToBuildWithResources(oppResources);

						// can he make a bank or port trade after the trade?
						SOCResourceSet resourcesAfterTrade = opponentResourcesAfterTrade(offer, pn);
						for (int rsTypeForBankPortTrade = SOCResourceConstants.CLAY; rsTypeForBankPortTrade < SOCResourceConstants.WOOD; rsTypeForBankPortTrade++) {
							int tradeRatio = tradeRatioForResource(pn, rsTypeForBankPortTrade);

							//is a bank/port trade possible?
							if (resourcesAfterTrade.getAmount(rsTypeForBankPortTrade) >= tradeRatio) {
								SOCResourceSet resourcesAfterBankTrade = resourcesAfterTrade.copy();
								resourcesAfterBankTrade.subtract(tradeRatio, rsTypeForBankPortTrade);

								//is there a piece for which the player is missing 1 resource?
								SOCResourceSet[] resSets = new SOCResourceSet[]{SOCGame.ROAD_SET, SOCGame.SETTLEMENT_SET, SOCGame.CITY_SET, SOCGame.CARD_SET};
								//We want to test these in reverse order, otherwise, for example, Settlements are never suggested, because Roads are a subset
								for (int resSetCounter = (resSets.length - 1); resSetCounter > -1 ; resSetCounter--) {

									//get the opponent's resources after the build (the difference between what he has after the bank trade and after the build)
									SOCResourceSet pieceResSet = resSets[resSetCounter];
									SOCResourceSet rsDiff = resourcesAfterBankTrade.copy();
									for (int rscConst = SOCResourceConstants.CLAY; rscConst < SOCResourceConstants.WOOD; rscConst++) {
										rsDiff.setAmount(rsDiff.getAmount(rscConst) - pieceResSet.getAmount(rscConst), rscConst);
									}

									//is there exactly one resource missing for building the piece (after trade + bank/port trade)?
									int numOfMinusOnes = 0;
									for (int rscConst = SOCResourceConstants.CLAY; rscConst < SOCResourceConstants.WOOD; rscConst++) {
										if (rsDiff.getAmount(rscConst) == -1) {
											numOfMinusOnes++;
										}
									}

									//check that this "-1 resource" is not the one he's giving away in the bank/port trade                                    
									//test if there is exactly one resource that's missing for the buid plan
									//this resource the opponent could buy with the bank/port trade
									//if that's not a piece available to him without the trade, that's the one we'll suggest to him!
									if (numOfMinusOnes == 1 && !piecesAvailbleBeforeTrade[resSetCounter] && rsDiff.getAmount(rsTypeForBankPortTrade) != -1) {

										//check whether that's a possible move for the opponent
										SOCPlayerTracker tracker = brain.getPlayerTrackers().get(pn);
										//                                        brain.printMess("possible settlements: " + tracker.getPossibleSettlements().toString());


										// are we checking for IMMEDIATE build?
										if (brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BANK_TRADE_FOR_IMMEDIATE_BUILD_NOT_POSSIBLE_BEFORE)) {

											SOCPlayer player = brain.getMemory().getPlayer(pn);
											SOCBuildingSpeedEstimate currentBSE = brain.getEstimator(player.getNumbers());
											int currentBuildingETAs[] = currentBSE.getEstimatesFromNowFast(resourcesAfterBankTrade, player.getPortFlags());

											// currentBuildingETAs contains the piece types in the same order as resSetCounter
											if (currentBuildingETAs[resSetCounter] == 0) {
												forced = true;
												makeMiscMove = true;
											}
										}

										// are we checking for ANY possible build?
										if (brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_ONLY_FOR_IMMEDIATE_OPPONENT_BANK_TRADE_FOR_BUILD_NOT_POSSIBLE_BEFORE)) {
											if ((resSetCounter == 0 && !tracker.getPossibleRoads().isEmpty()) ||
													(resSetCounter == 1 && !tracker.getPossibleSettlements().isEmpty()) ||
													(resSetCounter == 2 && !tracker.getPossibleCities().isEmpty()) ||
													(resSetCounter == 3 && brain.getMemory().getGame().getNumDevCards() > 0)) {
												forced = true;
												makeMiscMove = true;
											}
										}
									}
								}
							}
						}
					}
				}
				// we believe, no opponent can build immediately after the trade, so we're done
				if (!forced) {
					//HACKY COMBINATION OF "FORCE_ACCEPT_ONLY" PARAMETERS
					//the HACK contains these options here, but they are the last ones in the list, so we didn't find a persuasion argument
					//                    if (!brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_MISC_MOVES_ALL) && !brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_MISC_MOVES_NPB)) {
					if (!makeMiscMove) {
						return false;
					}
					//                }
				}
			}
		}

		// make sure we still have force-accept moves left in this game
		if (brain.getMemory().getNumForceAcceptMovesLeft() <= 0) {
			return false;
		}
		if (brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_WHEN_LEADER_HAS_VP)) {
			int minVP = (Integer)brain.getTypeParam(StacRobotType.NP_FORCE_ACCEPT_WHEN_LEADER_HAS_VP);
			int leaderVP = getLeaderVP();
			if (leaderVP < minVP) {
				return false;
			}
		}

		//if none of the above conditions means we're NOT forcing, we WILL force
		return true;
	}

	/**
	 * Should an offer be forced, given that the normal offer has been rejected?
	 * @param offer  original offer
	 * @param rejections  opponents who rejected the offer (this isn't even used, it acts as a flag to differentiate between overloading methods)
	 * @return 
	 */
	protected boolean shouldOfferBeForced(SOCTradeOffer offer, boolean[] rejections) {
		//check whether we're a forcing agent at all 
		//and also whether we're making force accepts after rejects
		//        if (brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_PROPOSER) && brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_ONLY_AFTER_REJECT)) {
		SOCBuildPlanStack buildPlan = brain.getBuildingPlan();
		if (buildPlan == null || buildPlan.getPlanDepth() == 0) {
			brain.getDecisionMaker().planStuff();
			buildPlan = brain.getBuildingPlan();
		}

		return shouldOfferBeForced(offer, buildPlan, rejections);
	}

	/**
	 * Can/do we want to force other robots to accept the offer?
	 * This should be called when there is no specific build plan: 
	 * before calling the proper method, the decision maker is asked for a build plan.
	 * @param offer offer to consider
	 * @return      boolean whether the offer should be 'forced'
	 */
	protected boolean shouldOfferBeForced(SOCTradeOffer offer) {
		SOCBuildPlanStack buildPlan = brain.getBuildingPlan();
		if (buildPlan == null || buildPlan.getPlanDepth() == 0) {
			brain.getDecisionMaker().planStuff();
			buildPlan = brain.getBuildingPlan();
		}

		return shouldOfferBeForced(offer, buildPlan, null);
	}


	/**
	 * Check whether we believe that the offer made by an opponent will allow him to build immediately.
	 * @param offer  Offer made by an opponent
	 * @return will opponent be able to build immediately after the trade?
	 */
	protected boolean isOpponentOfferForImmediateBuild(SOCTradeOffer offer) {
		boolean response = false;

		int offererPN = offer.getFrom();
		SOCPossiblePiece predictedPiece = null;

		// if the opponent announced the build plan, we assume that's what he'd trying to build
		if (brain.getMemory().wasTargetPieceAnnounced(offererPN)) {
			predictedPiece = brain.getMemory().getTargetPiece(offererPN);
		} else {
			SOCBuildPlanStack predictedBP = brain.getNegotiator().predictBuildPlan(offer.getFrom());

			// we may not have come up with a BBP for that player
			if (predictedBP == null || predictedBP.size() < 1) {
				response = false;
			} else {
				predictedPiece = predictedBP.getPlannedPiece(0);
			}
		}

		//make sure we have a predicted piece available for our inference
		if (predictedPiece != null) {
			SOCResourceSet resourcesForPiece = new SOCResourceSet();
			switch (predictedPiece.getType()) {
			case SOCPossiblePiece.ROAD:
				resourcesForPiece = SOCGame.ROAD_SET;
				break;
			case SOCPossiblePiece.SETTLEMENT:
				resourcesForPiece = SOCGame.SETTLEMENT_SET;
				break;
			case SOCPossiblePiece.CITY:
				resourcesForPiece = SOCGame.CITY_SET;
				break;
			case SOCPossiblePiece.CARD:
				resourcesForPiece = SOCGame.CARD_SET;
				break;                                    
			}

			//determine the resources we believe the offerer will have after the trade
			SOCResourceSet offererResources = brain.getMemory().getOpponentResources(offer.getFrom());
			SOCResourceSet resourcesAfterTrade = offererResources.copy();
			resourcesAfterTrade.add(offer.getGetSet());
			resourcesAfterTrade.subtract(offer.getGiveSet());

			if (resourcesAfterTrade.contains(resourcesForPiece)) {
				response = true;
			}
		}

		return response;
	}

	/**
	 * Test which pieces can be built with the specified resources.
	 * @param availableRes  the resources available to the player
	 * @return a boolean array coding for {Road, Settlement, City, Card}
	 */
	protected boolean[] piecesAvailableToBuildWithResources(SOCResourceSet availableRes) {
		boolean[] pieces = new boolean[4];
		SOCResourceSet[] resSets = new SOCResourceSet[]{SOCGame.ROAD_SET, SOCGame.SETTLEMENT_SET, SOCGame.CITY_SET, SOCGame.CARD_SET};
		for (int i = 0; i < resSets.length; i++) {
			pieces[i] = availableRes.contains(resSets[i]);
		}

		return pieces;
	}

	protected boolean[] bankPortTradesAvailableWithResources(SOCResourceSet availableRes) {
		boolean[] bankPortTrades = new boolean[SOCResourceConstants.WOOD];
		for (int rtype = 0; rtype < SOCResourceConstants.WOOD; rtype++) {
			int tradeRatio = tradeRatioForResource(brain.getPlayerNumber(), rtype);
			bankPortTrades[rtype] = (availableRes.getAmount(rtype) >= tradeRatio);
		}

		return bankPortTrades;
	}

	/**
	 * Return the resources we believe the opponent to have after he makes the trade.
	 * @param offer  our offer
	 * @param opponentPlayerNumber  the opponent whose resources we want to know about
	 * @return  will opponent be able to build immediately after the trade?
	 */
	protected SOCResourceSet opponentResourcesAfterTrade(SOCTradeOffer offer, int opponentPlayerNumber) {
		SOCResourceSet opponentResources = brain.getMemory().getOpponentResources(opponentPlayerNumber);
		SOCResourceSet resourcesAfterTrade = opponentResources.copy();

		//TODO decide if this is useful to have - check if based on this robots interpretation of the recipients resource whether the trade is possible at all.
		//Function is an added precaution to make sure no completely false persuasion/frc_acc is issued
		int UNKNOWNCOUNT = resourcesAfterTrade.getAmount(SOCResourceConstants.UNKNOWN);

		for (int i = SOCResourceConstants.MIN; i<SOCResourceConstants.UNKNOWN;i++){
			if(offer.getGetSet().getAmount(i)>resourcesAfterTrade.getAmount(i)){
				UNKNOWNCOUNT -= (offer.getGetSet().getAmount(i)-resourcesAfterTrade.getAmount(i));
			}
		}
		if(UNKNOWNCOUNT<0){
			//OFFER IS NOT POSSIBLE SO THEY MIGHT ASWELL HAVE NO RESOURCES
			return new SOCResourceSet();
		}

		//determine the resources we believe the offerer will have after the trade
		resourcesAfterTrade.add(offer.getGiveSet());
		resourcesAfterTrade.subtract(offer.getGetSet());

		return resourcesAfterTrade;
	}

	/**
	 * Get the (public) VP of the current leader.
	 * @return 
	 */
	protected int getLeaderVP() {
		int leaderVP = 0;
		for (SOCPlayer p : game.getPlayers()) {
			if (p.getPublicVP() > leaderVP) {
				leaderVP = p.getPublicVP();
			}
		}
		return leaderVP;
	}


	/**
	 * Return a list of all opponents we would consider trading with.
	 * Exclude ourselves
	 * Exclude anyone too close to winning
	 * Exclude anyone we're in a race with
	 * @return
	 */
	protected boolean[] getBaseRecipients() {
		boolean[] ret = new boolean[4];
		for (int j=0; j<game.maxPlayers; j++) {
			if (j == this.ourPlayerData.getPlayerNumber()) {
				ret[j] = false;
			} else if (brain.getMemory().getOpponentResourcesTotal(j) == 0) {
				ret[j] = false;                
			} else {
				// default to true, unless we're blocking trades with this player
				ret[j] = !brain.getMemory().isBlockingTrades(j);

				// Now check for all reasons we might not want to trade with them
				// TODO: Make it configurable/learnable whether these are applied
				if (playerWinThreat(j)) {
					ret[j] = false;
				}

				//check whether this player is embargoed
				if (brain.getMemory().isPlayerEmbargoed(j)) {
					D.ebugPrintlnINFO(game.getTurnCount() + ": " + brain.getPlayerNumber() + ": Not offering to embargoed player " + j);
					ret[j] = false;
				}

				// TODO: Add in-a-race condition

			}
		}
		return ret;
	}

	/**
	 * Is the player a threat to win the game?
	 * @param pNum the player number of the player we are checking 
	 * @return true if the player is close to winning the game
	 */
	protected boolean playerWinThreat(int pNum) {
		//TODO: Simply returning true from this method, increases the win rate to 0.322 against 0-best!
		if (brain.isRobotType(StacRobotType.CONTINUE_TRADING_WITH_AGENTS_CLOSE_TO_WINNING)) {
			return false;
		}

		int senderWGETA = ((SOCPlayerTracker) playerTrackers.get(pNum)).getWinGameETA();

		// Add VP threshold - the win-game-cutoff seems to be letting silly things through
		return senderWGETA < WIN_GAME_CUTOFF || game.getPlayer(pNum).getPublicVP() >= 8;
	}

	/** 
	 * Return a list of trade offers we can legally make. 
	 * Parameterized to indicate whether we want to consider two for one in either direction.
	 * @param to        The base recipients. Used to prevent offering trades to global/local threats.
	 * @param twoForOne Flag whether we consider giving away 2 resources for 1
	 * @param oneForTwo Flag whether we consider wanting 2 resources for 1
	 * @return List of legal trade offers
	 */
	protected List<TradeOfferWithStats> getLegalOffers(boolean[] to, boolean twoForOne, boolean oneForTwo) {       
		List<TradeOfferWithStats> legalOffers = new ArrayList<TradeOfferWithStats>();

		SOCResourceSet current = brain.getMemory().getResources();
		if (current.getTotal() == 0) {
			return legalOffers;
		}        

		// Iterate through giveable (quickest filter) 
		int ourPn = ourPlayerData.getPlayerNumber();
		for (int give = SOCResourceConstants.CLAY; give<=SOCResourceConstants.WOOD; give++) {
			if (current.getAmount(give)>0) {
				SOCResourceSet rsGive = new SOCResourceSet();
				rsGive.add(1, give);

				// Iterate through gettable (everything but giving)
				for (int get = SOCResourceConstants.CLAY; get<=SOCResourceConstants.WOOD; get++) {
					if (give != get) {
						// Add the one-for-one
						SOCResourceSet rsGet = new SOCResourceSet();
						rsGet.add(1, get);
						legalOffers.add(new TradeOfferWithStats(rsGive, false, rsGet, false, ourPn, to));

						// Imbalanced trades
						// consider giving 2 resources
						if (twoForOne) {
							for (int give2 = give; give2<=SOCResourceConstants.WOOD; give2++) { //otherwise we're looking at the options twice
								if (give2 != get) {
									SOCResourceSet rsGive2 = rsGive.copy();
									rsGive2.add(1, give2);
									if (current.contains(rsGive2)) {
										legalOffers.add(new TradeOfferWithStats(rsGive2, false, rsGet, false, ourPn, to));
									}
								}
							}
						}

						// consider asking for 2 resources
						if (oneForTwo) {
							for (int get2 = get; get2<=SOCResourceConstants.WOOD; get2++) {
								if (get2 != give) {
									SOCResourceSet rsGet2 = rsGet.copy();
									rsGet2.add(1, get2);
									if (current.contains(rsGive)) {
										legalOffers.add(new TradeOfferWithStats(rsGive, false, rsGet2, false, ourPn, to));
									}
								}
							}
						}
					}
				}
			}
		}

		//THIS IS MUCH LESS EFFICIENT!
		//        SOCResourceSet[] allSets = 
		//                {new SOCResourceSet(2, 0, 0, 0, 0, 0), new SOCResourceSet(0, 2, 0, 0, 0, 0), new SOCResourceSet(0, 0, 2, 0, 0, 0), 
		//                new SOCResourceSet(0, 0, 0, 2, 0, 0), new SOCResourceSet(0, 0, 0, 0, 2, 0), 
		//                new SOCResourceSet(1, 1, 0, 0, 0, 0), new SOCResourceSet(1, 0, 1, 0, 0, 0), new SOCResourceSet(1, 0, 0, 1, 0, 0), 
		//                new SOCResourceSet(1, 0, 0, 0, 1, 0), new SOCResourceSet(0, 1, 1, 0, 0, 0), new SOCResourceSet(0, 1, 0, 1, 0, 0), 
		//                new SOCResourceSet(0, 1, 0, 0, 1, 0), new SOCResourceSet(0, 0, 1, 1, 0, 0), new SOCResourceSet(0, 0, 1, 0, 1, 0), 
		//                new SOCResourceSet(0, 0, 0, 1, 1, 0),
		//                new SOCResourceSet(1, 0, 0, 0, 0, 0), new SOCResourceSet(0, 1, 0, 0, 0, 0), new SOCResourceSet(0, 0, 1, 0, 0, 0), 
		//                new SOCResourceSet(0, 0, 0, 1, 0, 0), new SOCResourceSet(0, 0, 0, 0, 1, 0)};
		//        SOCResourceSet[] oneSets = 
		//                {new SOCResourceSet(1, 0, 0, 0, 0, 0), new SOCResourceSet(0, 1, 0, 0, 0, 0), new SOCResourceSet(0, 0, 1, 0, 0, 0), 
		//                new SOCResourceSet(0, 0, 0, 1, 0, 0), new SOCResourceSet(0, 0, 0, 0, 1, 0)};
		//        
		//        SOCResourceSet[] giveSets = twoForOne ? allSets : oneSets;
		//        SOCResourceSet[] getSets = oneForTwo ? allSets.clone() : oneSets.clone();
		//        int ourPn = ourPlayerData.getPlayerNumber();
		//        
		//        for (SOCResourceSet give : giveSets) {
		//            for (SOCResourceSet get : getSets) {
		//                if (current.contains(give) && give.disjoint(get))
		//                    legalOffers.add(new TradeOfferWithStats(give, false, get, false, ourPn, to));
		//            }
		//        }

		return legalOffers;
	}

	/**
	 * Decide whether we want to make this trade.
	 * Comparison of possible trade with the BATNA.
	 * This method unifies the comparisons made for offering trades (called via makeOffer.calculateBuildTimesAndFilterBadTrades())
	 * and considerOffer (considerOfferOld()). 
	 * considerOffer was just comparing the trade's ETA with the BATNA while makeOffer was making this more elaborate comparison.
	 * @param trade                 the trade offer
	 * @param batna                 the BATNA (trade offer)
	 * @param batnaBuildingTime     BATNA ETA (ETB)
	 * @return true if the trade is better than the BATNA
	 */
	private boolean acceptTrade(TradeOfferWithStats trade, SOCTradeOffer batna, int batnaBuildingTime) {

		//Basic condition
		if (trade.eta > batnaBuildingTime) {
			D.ebugPrintlnINFO(brain.getPlayerName() + ": " + "Don't accept trade (1)\n\tTrade: " + trade.toString() + "\n\tBATNA" + batna + "BATNA building time: " + batnaBuildingTime);
			return false;
		}   

		// If ETA == BATNA, allow it if the BATNA involves a port trade and this is better.
		// Only consider it if we can build immediately.
		else if (trade.eta == batnaBuildingTime) {
			if (trade.eta != 0 || (batna != null && batna.getGiveSet().getTotal() <= trade.give.getTotal())) {
				D.ebugPrintlnINFO(brain.getPlayerName() + ": " + "Don't accept trade (2)\n\tTrade: " + trade.toString() + "\n\tBATNA" + batna + "BATNA building time: " + batnaBuildingTime);
				return false;
			}
		}

		// If the trade does not allow us to build immediately, we want at least a minimum improvement
		else if (trade.eta > 0) {

			// Check whether we require a minimum improvement (in turns) before we consider a trade
			Integer thresh = (Integer) brain.getTypeParam(StacRobotType.NP_IMPROVEMENT_OVER_BATNA_THRESHOLD);
			int threshold = thresh == null ? 1 : thresh;
			int improvement = batnaBuildingTime - trade.eta;
			if (improvement < threshold) {
				D.ebugPrintlnINFO(brain.getPlayerName() + ": " + "Don't accept trade (3)\n\tTrade: " + trade.toString() + "\n\tBATNA" + batna + "BATNA building time: " + batnaBuildingTime);
				return false;
			}
			// The same with a ratio
			double improvementRatio = ((double) improvement) / ((double) batnaBuildingTime);
			Double threshRatio = (Double) brain.getTypeParam(StacRobotType.NP_IMPROVEMENT_OVER_BATNA_THRESHOLD_RATIO);
			double thresholdRatio = threshRatio == null ? 0 : threshRatio;
			if (improvementRatio < thresholdRatio) {
				D.ebugPrintlnINFO(brain.getPlayerName() + ": " + "Don't accept trade (4)\n\tTrade: " + trade.toString() + "\n\tBATNA" + batna + "BATNA building time: " + batnaBuildingTime);
				return false;
			}

			//TODO: use a threshold so as not to benefit others more than us
		}

		String infoString = brain.getPlayerName() + ": " + "Accept trade\n\tTrade: " + trade.toString() + "\n\tBATNA" + batna + "BATNA building time: " + batnaBuildingTime;
		D.ebugPrintlnINFO(infoString);
		return true;
	}

	/**
	 * Calculate ETA and time to attain resources for all trades in the list and filter out bad trades (trade worse than BATNA).
	 * @param trades    List of possible trades
	 * @param buildPlan The best build plan we're pursuing
	 */
	protected void calculateBuildTimesAndFilterBadTrades(final List<TradeOfferWithStats> trades, SOCBuildPlanStack buildPlan) {
		SOCResourceSet targetResources = getResourcesForPlan(buildPlan);   
		SOCBuildingSpeedEstimate estimate = brain.getEstimator(ourPlayerData.getNumbers());

		// Calculate raw time to build
		SOCResourceSet giveResourceSet = new SOCResourceSet();
		SOCResourceSet getResourceSet = new SOCResourceSet();
		int batnaBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

		// Calculate the BATNA time to build
		// TODO: Does this consider the scenario of multiple bank trades?
		SOCTradeOffer batna = getOfferToBank(buildPlan);
		if (batna != null) {
			batnaBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, batna.getGiveSet(), batna.getGetSet(), estimate);
		}

		/// consider offers where we give one for two unneeded we 
		/// we can use at a bank or port
		// HOW ABOUT GIVING AWAY NEEDED RESOURCES?

		// Remove all trade offers that are worse than the BATNA
		List<TradeOfferWithStats> remove = new ArrayList<TradeOfferWithStats>();
		for (TradeOfferWithStats t : trades) {
			// Get ETA, remove if worse than BATNA
			t.eta = getETAToTargetResources(ourPlayerData, targetResources, t.give, t.get, estimate);

			if (!acceptTrade(t, batna,  batnaBuildingTime)) {
				remove.add(t);
			} else {
				// Tiebreaker for ranking trades: What's the ETA for the "global" target
				t.globalETA = getETAToTargetResources(ourPlayerData, GLOBAL_ETA_RS, t.give, t.get, estimate);
			}
		}
		trades.removeAll(remove);
	}

	/**
	 * Filter based on what trades are legal (ie opponents can do).
	 * Optimistic determines whether we assume UNKNOWN could be the resource in question
	 * @param trades        list of trades to filter
	 * @param optimistic    are we optimistic about unknown resources?
	 */
	protected void filterLegal(final List<TradeOfferWithStats> trades, boolean optimistic) {
		List<TradeOfferWithStats> remove = new ArrayList<TradeOfferWithStats>();
		for (TradeOfferWithStats t : trades) {
			boolean hasLegal = false;
			for (int p = 0; p < game.maxPlayers; p++) {
				if (t.to[p]) {
					if (!isLegal(t.get, p, optimistic)) {
						t.to[p] = false;
					} else {
						hasLegal = true;
					}
				}
			}
			if (!hasLegal) {
				remove.add(t);
			}    
		}
		trades.removeAll(remove);
	}

	/**
	 * Filter based on what trades are likely to be completed (ie opponents WILL do).
	 * Use original logic of an inverted "considerOffer".
	 * @param trades    List of TradeOffers to be filtered. The filtered objects are removed from this List object.
	 * @param buildPlan The build plan towards which the trade is considered
	 */
	protected void filterLikely(final List<TradeOfferWithStats> trades, SOCBuildPlanStack buildPlan) {
		List<TradeOfferWithStats> remove = new ArrayList<TradeOfferWithStats>();
		for (TradeOfferWithStats t : trades) {
			boolean hasLikely = false;
			for (int p=0; p<4; p++) {
				if (t.to[p]) {
					// Is "isSelling" true for any requested resource?                    
					for (int r=SOCResourceConstants.CLAY; r<=SOCResourceConstants.WOOD; r++) {
						if (t.get.getAmount(r)>0 && ! brain.getMemory().isSellingResource(p, r)) {
							t.to[p] = false;
							break;
						}
					}

					// If the above didn't disqualify, check whether we think they'd accept
					if (t.to[p]) {
						int offerResponse = ACCEPT_OFFER;
						if (!brain.isRobotType(StacRobotType.BUILD_PLAN_INDIFFERENT)) {

							// try to guess the opponent's response
							boolean[] receiver = new boolean[game.maxPlayers];
							receiver[p] = true;
							SOCTradeOffer offerToTest = new SOCTradeOffer(game.getName(), brain.getPlayerNumber(), receiver, t.give, t.get);

							// see if we will/can force other robots to accept the offer
							boolean forced = shouldOfferBeForced(offerToTest, buildPlan, null);

							offerResponse = guessOpponentResponse(offerToTest, p);
						}    

						if (offerResponse == ACCEPT_OFFER) {
							hasLikely = true;
						}
						// Do not set to=false if we don't think the player would accept -
						//  they may surprise us or make a fruitful counter-offer
					}
				}
			}
			if (!hasLikely) {
				remove.add(t);
			}    
		}
		trades.removeAll(remove);
	}

	/**
	 * Filter out offers that are 'worse' than one we already made earlier. 
	 * Example: a 1-1 after a 2-1: "give 1o, rec 1c" is worse than "give 2o, rec 1c".
	 * @param trades the list of trades to be filtered
	 */
	private void filterWorseThanHistory(final List<TradeOfferWithStats> trades) {
		List<TradeOfferWithStats> remove = new ArrayList<TradeOfferWithStats>();

		ArrayList<StacTradeOffer> pastTradeOffers = brain.getMemory().retrieveAllTradeOffers();
		for (TradeOfferWithStats offer : trades) {
			for (SOCTradeOffer pastOffer : pastTradeOffers) {
				boolean[] pastTo = pastOffer.getTo();
				boolean[] offerTo = offer.getOffer().getTo();
				// receipients of offer are identical
				if (pastTo[0] == offerTo[0] && pastTo[1] == offerTo[1] && pastTo[2] == offerTo[2] && pastTo[3] == offerTo[3]) { 
					// get same give less; also captures identical offers
					if (pastOffer.getGetSet().equals(offer.getOffer().getGetSet()) && pastOffer.getGiveSet().contains(offer.getOffer().getGiveSet())) {
						remove.add(offer);
					} 
					// give same get more; this case does not seem occur 
					else if (offer.getOffer().getGetSet().contains(pastOffer.getGetSet()) && offer.getOffer().getGiveSet().equals(pastOffer.getGiveSet())) { // give same get more
						remove.add(offer);
					}
				}
			}
		}
		trades.removeAll(remove);
	}

	/**
	 * Check in both directions, that the give and get resources weren't exchanged already in the reverse direction.
	 * This modifies the 'to' attribute of the offer object by removing possible recipients of the message if that'd mean a reverse exchange of resources.
	 * @param offer the offer to be manipulated (Note that this cannot be a disjunctive offer!)
	 * @return      true if all recipients have been removed from the offer
	 */
	protected boolean removeRecipientsWithReverseResourceExchange(SOCTradeOffer offer) {
		for (StacRobotDeclarativeMemory.Trade trade : brain.getMemory().getPastTrades()) {
			boolean[] recipients = offer.getTo();
			SOCResourceSet offerGiveSet = offer.getGiveSet();
			SOCResourceSet offerGetSet = offer.getGetSet();
			if (recipients[trade.to] && (!offerGiveSet.disjoint(trade.getSet) || !offerGetSet.disjoint(trade.giveSet))) {
				recipients[trade.to] = false;
			} else if (recipients[trade.from] && (!offerGiveSet.disjoint(trade.giveSet) || !offerGetSet.disjoint(trade.getSet))) {
				recipients[trade.from] = false;
			}
			//check whether there is at least one recipient left
			//compute the remaining number of recipients of this offer
			int numberOfRecipients = 0;
			for (int p = 0; p < recipients.length; p++)
				if (recipients[p])
					numberOfRecipients++;
			//if there are no more recipients left, 
			if (numberOfRecipients == 0)
				return true;
		}
		return false;
	}

	/** Filter offers where we give away too many resources of the types we need for the BBP, so that we won't be able to execute our BBP. */
	private void filterGivingAwayResourcesNeededForBBP(final List<TradeOfferWithStats> trades) {
		List<TradeOfferWithStats> remove = new ArrayList<TradeOfferWithStats>();

		// See what resources we have and what resources the BBP requires.
		SOCResourceSet currentResources = brain.getMemory().getResources();
		SOCBuildPlanStack bbp = brain.getMemory().getCurrentBuildPlan();
		SOCResourceSet resourcesNeededForBBP = bbp.totalResourcesForBuidPlan();

		// See what resources we have extra, i.e. which resources we can spare (the subtract method takes care that no value is < 0)
		SOCResourceSet extraResources = currentResources.copy();
		extraResources.subtract(resourcesNeededForBBP);

		// Now get all the trade offers where we give away too much
		for (TradeOfferWithStats offer : trades) {
			SOCResourceSet giveSet = offer.getOffer().getGiveSet();
			if (!extraResources.contains(giveSet)) {
				remove.add(offer);
			}
		}
		trades.removeAll(remove);
	}

	/**
	 * Filter based on what trades are likely to be completed (ie opponents WILL do).
	 * Use original logic of an inverted "considerOffer".
	 * @param trades List of TradeOffers to be filtered. The filtered objects are removed from this List object.
	 */
	protected void filterTradesContainingBlockedResources(final List<TradeOfferWithStats> trades) {
		List<TradeOfferWithStats> remove = new ArrayList<TradeOfferWithStats>();
		for (TradeOfferWithStats t : trades) {
			// see if this trade contains a get-resource that is blocked (then remove the trade from the list)
			boolean hasBlockedResource = false;
			for (int r = SOCResourceConstants.CLAY; r <= SOCResourceConstants.WOOD; r++) {
				if (t.getOffer().getGetSet().getAmount(r) > 0 && brain.getMemory().getBlockedResources().getAmount(r) > 0) {
					hasBlockedResource = true;
					break;
				}
			}

			if (hasBlockedResource) {
				remove.add(t);
			}    
		}
		trades.removeAll(remove);
	}

	/**
	 * Helper function for aggregateTrades() that adds a TradeOfferWithStats to the list if it is not in there already.
	 * Also takes the minimum values of the two offers eta and globalETA
	 * @param offer
	 * @param offerList
	 * @return 
	 */
	private void addTradeOfferToList(TradeOfferWithStats offer, List<TradeOfferWithStats> offerList) {
		for (TradeOfferWithStats offerInList : offerList) {
			//if the offer already is in the list, just update the values for eta and globalETA
			if (Arrays.equals(offer.to, offerInList.to) && 
					offer.give.equals(offerInList.give) && offer.disjGive == offerInList.disjGive &&
					offer.get.equals(offerInList.get) && offer.disjGet == offerInList.disjGet) { // && offer.eta == offerInList.eta && offer.globalETA == offerInList.globalETA) {
				offerInList.eta = Math.min(offerInList.eta, offer.eta);
				offerInList.globalETA = Math.min(offerInList.globalETA, offer.globalETA);
				return;
			}
		}
		offerList.add(offer);
	}

	/**
	 * Try to aggregate multiple trade offers into one.
	 * Create partial or disjunctive offers.
	 * Checks the past offers whether the trade has been offered before
	 * @param trades    the set of potential trades
	 * @return          ranked list of aggregated trades (if any)
	 */
	//TODO: Value function for combined offers
	private List<TradeOfferWithStats> aggregateTrades(List<TradeOfferWithStats> trades) {
		List<TradeOfferWithStats> aggregatedTrades = new ArrayList<TradeOfferWithStats>();
		for (int i1 = 0; i1 < trades.size()-1; i1++) { //iterate up to penultimate element
			TradeOfferWithStats to1 = trades.get(i1);
			boolean aggregatedTO1 = false;
			for (int i2 = i1+1; i2 < trades.size(); i2++) { //iterate from the next element till the end of the list
				TradeOfferWithStats to2 = trades.get(i2);

				//TODO: Reconsider - this way seems to be the fastest way of getting the aggregated offers, but this potentially generates all combinations 
				//if there are many (or all) offers where resource sets are equal, e.g. if all offers ask for ore

				//same recipients?
				if (Arrays.equals(to1.getOffer().getTo(), to2.getOffer().getTo())) {
					TradeOfferWithStats aggTO = null;

					//conditions for aggregating two trades
					//- two give or get sets are equal
					//- the 'source' sets of the unchanged set have the same flag for disjoint (although the flag should always be false here)

					//conditions for using a disjunctive offer rather than a partial one
					//- the 'source' sets of the sets to be joined are disjoint
					//- the 'source' sets of the sets to be joined each do not specify more than 1 resource type
					//- the aggregated offer is not complex, i.e. it does not specify more than 3 resource types
					//- neither of the 'source' sets is already disjoint

					//equal give sets
					if (to1.give.equals(to2.give) && to1.disjGive == to2.disjGive && !to1.get.equals(to2.get)) {
						if (!brain.isRobotType(StacRobotType.NP_DONT_MAKE_DISJUNCTIVE_OFFERS)
								&& to1.get.disjoint(to2.get) 
								&& to1.get.numberOfResourceTypes() < 2 && to2.get.numberOfResourceTypes() < 2 
								&& (to1.get.numberOfResourceTypes() + to2.get.numberOfResourceTypes() + to1.give.numberOfResourceTypes()) <= 3 // equivalent: to1.give.numberOfResourceTypes() == 1
								&& !to1.disjGet && !to2.disjGet)
							aggTO = new TradeOfferWithStats(to1.give, false, to1.get.union(to2.get), true, to1.from, to1.to);
						else if (!brain.isRobotType(StacRobotType.NP_DONT_MAKE_PARTIAL_OFFERS))
							aggTO = new TradeOfferWithStats(to1.give, false, new SOCResourceSet(), false, to1.from, to1.to);
					}
					//equal get sets
					if (!to1.give.equals(to2.give) && to1.disjGet == to2.disjGet && to1.get.equals(to2.get)) {
						if (!brain.isRobotType(StacRobotType.NP_DONT_MAKE_DISJUNCTIVE_OFFERS)
								&& to1.give.disjoint(to2.give) 
								&& to1.give.numberOfResourceTypes() < 2 && to2.give.numberOfResourceTypes() < 2 
								&& (to1.give.numberOfResourceTypes() + to2.give.numberOfResourceTypes() + to1.get.numberOfResourceTypes()) <= 3 // equivalent: to1.get.numberOfResourceTypes() == 1
								&& !to1.disjGive && !to2.disjGive)
							aggTO = new TradeOfferWithStats(to1.give.union(to2.give), true, to1.get, false, to1.from, to1.to);
						else if (!brain.isRobotType(StacRobotType.NP_DONT_MAKE_PARTIAL_OFFERS))
							aggTO = new TradeOfferWithStats(new SOCResourceSet(), false, to1.get, false, to1.from, to1.to);
					}
					//have we made this or a more general offer before?
					if (aggTO != null && !brain.getMemory().pastTradeOfferExists(aggTO.getOffer())) {
						if (brain.isRobotType(StacRobotType.NP_MAX_DELTA_ETB_FOR_OFFER_AGGREGATION)) {
							int deltaETA = to1.deltaETA(to2);
							int maxDeltaETA = (Integer)brain.getTypeParam(StacRobotType.NP_MAX_DELTA_ETB_FOR_OFFER_AGGREGATION);
							if (deltaETA > maxDeltaETA)
								continue;
						}

						aggTO.eta = to1.eta < to2.eta ? to1.eta : to2.eta;
						aggTO.globalETA = to1.globalETA < to2.globalETA ? to1.globalETA : to2.globalETA;
						//A sample of 100 games computed the ∆(ETA) 412827 times for 4 robots. The distribution was as follows:
						//1     2       3       4       5       6       7       8       9       10
						//88.18	3.13	3.56	1.89	1.45	0.79	0.43	0.28	0.13	0.06
						addTradeOfferToList(aggTO, aggregatedTrades);
						D.ebugPrintlnINFO("Aggregated trade offer: " + aggTO.toString() + "\n\tOffer 1: " + to1.toString() + "\n\tOffer 2: " + to2.toString());
						aggregatedTO1 = true;
					}
				}
			}
			//we tried all combinations, now we see whether we aggregated this trade with something else
			//NB: we already filtered out the past offers from the list of complete offers before, so we can add it safely here
			if (!aggregatedTO1)
				addTradeOfferToList(to1, aggregatedTrades);
		}

		Collections.sort(aggregatedTrades);

		return aggregatedTrades;
	}

	/**
	 * From the list of TradeOfferWithStats, take the ones with the best ETB & global ETB and pick one at random.
	 * @param offers    unsorted list of TradeOfferWithStats
	 * @return          the offer as StacTradeOffer
	 */
	private StacTradeOffer pickABestOffer(List<TradeOfferWithStats> offers) {

		//sort for best offer
		Collections.sort(offers);

		//choose the counteroffer among the ones with best ETA/global ETA to avoid possible effect of a standard sequence/list
		List<TradeOfferWithStats> allTopTrades = new ArrayList<TradeOfferWithStats>();
		int topETA = offers.get(0).eta;
		int topGlobalETA = offers.get(0).globalETA;
		for (TradeOfferWithStats o : offers) {
			if (o.eta == topETA && o.globalETA == topGlobalETA)
				allTopTrades.add(o);
			else
				break;
		}
		TradeOfferWithStats chosenOffer = allTopTrades.get(RANDOM.nextInt(allTopTrades.size()));
		return chosenOffer.getOffer();
	}

	/**
	 * Legacy version of makerOfferImpl - funny iteration structure, not modular
	 * @param buildPlan
	 * @return
	 */
	protected SOCTradeOffer makeOfferImplOld(SOCBuildPlanStack buildPlan) {
		if (buildPlan == null || buildPlan.empty())
			return null;

		SOCPossiblePiece targetPiece = buildPlan.peek();
		D.ebugPrintlnINFO("***** MAKE OFFER *****");

		if (targetPiece == null)
		{
			return null;
		}

		StacTradeOffer offer = null;

		SOCResourceSet targetResources = getResourcesForPlan(buildPlan); 
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

		//---MG
		//Just make a partial offer requesting the resource we need; 
		//don't do the rest of the complete-offer computation
		//just stupidly make a partial trade offer for the `first' resource we need
		if (brain.isRobotType(StacRobotType.SIMPLY_ASK)) {
			//look for the first resource we need but don't have and make a stoopid partial for it (`I need 1 X.')
			for (int rsrcType = SOCResourceConstants.CLAY; rsrcType <= SOCResourceConstants.WOOD; rsrcType++) {
				int numberOfResourceNeeded = targetResources.getAmount(rsrcType);
				int numberOfResourceAvailable = ourPlayerData.getResources().getAmount(rsrcType);

				if (numberOfResourceNeeded > 0 && numberOfResourceAvailable < numberOfResourceNeeded) {
					//create the resource set for the offer
					getResourceSet.clear(); //getResourceSet may still contain values from the previous iteration
					getResourceSet.setAmount(1, rsrcType);

					//make the offer to all players, except ourself
					boolean[] to = new boolean[game.maxPlayers];
					for (int i = 0; i < game.maxPlayers; i++) {
						if (i != ourPlayerData.getPlayerNumber()) {
							to[i] = true;
						}
					}

					//create the offer
					offer = new StacTradeOffer(game.getName(), ourPlayerData.getPlayerNumber(), to, giveResourceSet, false, getResourceSet, false);

					//see if we've made this offer before
					boolean match;
					if (brain.isRobotType(StacRobotType.USE_ACT_R_DECLARATIVE_MEMORY)) {
						int opn = ourPlayerData.getPlayerNumber();
						boolean[] offeredTo = new boolean[game.maxPlayers];
						StacTradeOffer offerToCheck = new StacTradeOffer(game.toString(), opn, offeredTo, giveResourceSet, false, getResourceSet, false);
						match = brain.getMemory().pastTradeOfferExists(offerToCheck); //match = brain.getMemory().pastTradeOfferExistsInACTRDM(offerToCheck);
						if (match) {
							D.ebugPrintlnINFO("### " + ourPlayerData.getName() + ": already made this non-belief partial offer: " + offer.toString());
							offer = null;
						}
					} else {
						if (brain.getMemory().pastTradeOfferExists(offer)) {
							D.ebugPrintlnINFO("### " + ourPlayerData.getName() + ": already made this non-belief partial offer: " + offer.toString());
							offer = null;
							break;
						}
					}

					if (offer != null) {
						D.ebugPrintlnINFO("### " + ourPlayerData.getName() + ": making non-belief partial offer: " + offer.toString());
						D.ebugPrintlnINFO("Build plan: " + buildPlan.toString());

						//remember that we've made this offer
						addToOffersMade(offer);

						return offer; //quick exit from for loop
					}
				}
			}   
			return offer;
		}

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

		if (D.ebugIsEnabled())
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

		if (D.ebugIsEnabled())
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
				if ((pn != ourPlayerData.getPlayerNumber()) && (brain.getMemory().isSellingResource(pn, rsrcType)))
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
					// TODO: Debug this, I'm not sure it's working as advertised
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
			// Make partial offers 50% or 100% of the time, if we're such a robot
			if (brain.isRobotType(StacRobotType.PARTIALISING_COMPLETE_OFFERS_50_PERCENT)) {
				if (RANDOM.nextBoolean()) {
					offer.getGiveSet().clear(); // Is this safe???
					// I hate how resource sets are used - so many reference risks.  Would love to refactor 
					// to make ResourceSet, which is immutable, and ResourceTracker, which extends and offers the same getter functionality, 
					// but also setters.  
					//              System.out.println("***************************** Agent making a partial offer!");
				}
			} else if (brain.isRobotType(StacRobotType.PARTIALISING_COMPLETE_OFFERS_100_PERCENT)) {
				StacTradeOffer partialisedOffer = new StacTradeOffer(offer);
				partialisedOffer.getGiveSet().clear();
				//                //mark the receiver to get another offer for these resources; this is so we make another (complete) offer if the completed offer we get in return is unacceptable to us
				//                for (int p = 0; p < game.maxPlayers; p++) {
				//                    for (int r = SOCResourceConstants.CLAY; r < SOCResourceConstants.UNKNOWN; r++) {
				//                        markAsWantsAnotherOffer(p, r);
				//                    }
				//                }
				//check if we have made this partialised offer before
				//                System.err.println(brain.getNumberOfMesagesReceived() + ": Past offers: " + brain.getMemory().retrieveAllTradeOffers());
				boolean tst = brain.getMemory().retrieveAllTradeOffers().contains(partialisedOffer);
				if (!tst) {
					//                    boolean[] to = offer.getTo();
					//                    String printString = "From:" + offer.getFrom() + " To:"; // + to[0] + "|" + to[1] + "|" + to[2] + "|" + to[3];
					//                    if (to[0])
					//                        printString += "T";
					//                    else
					//                        printString += "F";
					//                    for (int i = 1; i < to.length; i++) {
					//                        if (to[i])
					//                            printString += ("," + "T");
					//                        else
					//                            printString += ("," + "F");
					//                    }
					//                    printString += (" Give=" + offer.getGiveSet() + " Get=" + offer.getGetSet());
					//                    System.err.println(brain.getNumberOfMesagesReceived() + ": Full:  " + printString); //mes.toString());

					brain.getMemory().setOriginalBestCompleteTradeOffer(offer); //remember this for later when we get a response
					offer = partialisedOffer;
				}
			}
		}
		return offer;
	}

	@Override
	/**
	 * This uses the makeOffer method wraps the old 
	 */
	public StacTradeOffer makeCounterOffer(SOCTradeOffer originalOffer) {
		if (brain.isRobotType(StacRobotType.NO_TRADES)) {
			return null;
		}

		lastOfferETA = 1000;
		lastOfferGlobalETA = 1000;
		SOCBuildPlanStack ourBuildingPlan = brain.getBuildingPlan();

		StacTradeOffer counterOffer = null;
		if (brain.isRobotType(StacRobotType.USE_OLD_NEGOTIATOR_COUNTEROFFER)) {
			SOCTradeOffer co = makeCounterOfferOld(originalOffer);
			if (co != null)
				counterOffer = new StacTradeOffer(co);
		} else {
			if (ourBuildingPlan == null)
				return null;
			SOCTradeOffer invertedOffer = originalOffer.invertedOffer(brain.getPlayerNumber());
			boolean[] possibleTo = new boolean[brain.getGame().maxPlayers];
			possibleTo[originalOffer.getFrom()] = true;
			counterOffer = makeOffer(ourBuildingPlan, true, invertedOffer, possibleTo);
		}
		//Store lastOfferETA, which is used by the brain for a meta-evaluation
		//TODO: the counterOffer can contain disjunctive resource sets, but the computations for the ETAs below just assume that they are conjunctive sets
		if (counterOffer != null) {
			SOCResourceSet targetResources = this.getResourcesForPlan(brain.getBuildingPlan());
			SOCBuildingSpeedEstimate estimate = brain.getEstimator(ourPlayerData.getNumbers());
			lastOfferETA = getETAToTargetResources(ourPlayerData, targetResources, counterOffer.getGiveSet(), counterOffer.getGetSet(), estimate);
			lastOfferGlobalETA = getETAToTargetResources(ourPlayerData, GLOBAL_ETA_RS, counterOffer.getGiveSet(), counterOffer.getGetSet(), estimate);
		}

		return counterOffer;
	}        

	private SOCTradeOffer makeCounterOfferOld(SOCTradeOffer originalOffer)
	{

		D.ebugPrintlnINFO("***** MAKE COUNTER OFFER *****");

		SOCTradeOffer counterOffer = null;

		SOCBuildPlanStack ourBuildingPlan = brain.getBuildingPlan();
		SOCPossiblePiece targetPiece = brain.getMemory().getTargetPiece(ourPlayerData.getPlayerNumber());

		if (targetPiece == null)
		{
			if (ourBuildingPlan.empty())
			{
				SOCRobotDM simulator;
				D.ebugPrintlnINFO("**** our building plan is empty ****");
				simulator = new SOCRobotDMImpl(brain.getRobotParameters(), playerTrackers, ourPlayerTracker, ourPlayerData, ourBuildingPlan, brain.getRobotParameters().getStrategyType());
				simulator.planStuff();
			}

			if (ourBuildingPlan.empty())
			{
				return null;
			}

			targetPiece = (SOCPossiblePiece) ourBuildingPlan.peek();
			brain.getMemory().setTargetPieceUnannounced(ourPlayerData.getPlayerNumber(), targetPiece);//its our plan so it doesn't matter if its announced or not
		}

		SOCResourceSet targetResources = this.getResourcesForPlan(ourBuildingPlan);
		SOCResourceSet ourResources = ourPlayerData.getResources();

		D.ebugPrintlnINFO("*** targetResources = " + targetResources);
		D.ebugPrintlnINFO("*** ourResources = " + ourResources);

		if (ourResources.contains(targetResources))
		{
			return null;
		}

		if (ourResources.getAmount(SOCResourceConstants.UNKNOWN) > 0)
		{
			D.ebugPrintlnINFO("AGG WE HAVE UNKNOWN RESOURCES !!!! %%%%%%%%%%%%%%%%%%%%%%%%%%%%");

			return null;
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

		if (D.ebugIsEnabled())
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

		if (D.ebugIsEnabled())
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

	@Override
	public SOCTradeOffer getOfferToBank(SOCBuildPlanStack buildPlan, SOCResourceSet ourResources)
	{
		if (buildPlan==null || buildPlan.isEmpty()) {
			return null;
		}
		SOCResourceSet targetResources = getResourcesForPlan(buildPlan);
		return getOfferToBank(targetResources, ourResources);
	}

	/**
	 * The actual code for executing {@link #getOfferToBank(SOCBuildPlanStack, SOCResourceSet)} 
	 * @param targetResources
	 * @param ourResources
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

	@Override
	public int considerOffer(SOCTradeOffer offer, int receiverNum)
	{ 
		return considerOfferNONPERSUASION(offer, receiverNum, false);
	}

	/**
	 * The actual code for executing {@link #considerOffer(SOCTradeOffer, int)}
	 * @param offer
	 * @param receiverNum
	 * @param forced whether the trade is declared as forced
	 * @return
	 */
	public int considerOfferNONPERSUASION(SOCTradeOffer offer, int receiverNum, boolean forced)
	{
		// Use different functions to evaluate our own responses and to guess our opponent's response.  Call the wrapper function,
		//  which checks legal trade, then passes to a (possibly overridden) evaluating function.
		if (receiverNum == brain.getPlayerNumber()) {
			if (brain.getMemory().isBlockingTrades(offer.getFrom())) {
				return REJECT_OFFER;
			}
			else {
				return considerOfferToMeWrapper(offer, forced);
			}
		}
		else {
			return guessOpponentResponseWrapper(offer, receiverNum);
		}
	}

	/**
	 * Helper function to determine whether a trade is potentially legal.  
	 * Could a resource set with unknown resources possibly contain the resources asked for in a trade?
	 * This is just a wrapper for {@link #isLegal(SOCResourceSet rsrcsOut, int giverNumber, boolean optimistic)}.
	 * @param offer
	 * @param receiverNum
	 * @param optimistic
	 * @return
	 */
	public boolean isLegal(SOCTradeOffer offer, int receiverNum, boolean optimistic) {     
		SOCResourceSet rsrcsOut = offer.getGetSet();
		return isLegal(rsrcsOut, receiverNum, optimistic);
	}

	/**
	 * Test whether a player can legally give away the resources, ie whether he has the resources.
	 * @param rsrcsOut      the givable resources
	 * @param giverNumber   player number of the giver
	 * @param optimistic    are we optimistic about unknown resources?
	 * @return              true if the player has the givable resources
	 */
	private boolean isLegal(SOCResourceSet rsrcsOut, int giverNumber, boolean optimistic) {
		// TODO: Consider UNKNOWN resources for future purposes - eg "I'll trade you a clay for a wood and any other resource".  Originally, this
		//  was part of the loop, but this caused problems in the earlier implementation.

		SOCResourceSet resources = brain.getMemory().getOpponentResources(giverNumber);
		if (optimistic)
			return rsrcsOut.isOptimisticSubsetOf(resources);
		else
			return rsrcsOut.isPessimisticSubsetOf(resources);
	}

	/**
	 * Determine whether there is a piece that I can only build/buy after accepting the trade.
	 * @param offer
	 * @return 
	 */
	private boolean buildOnlyPossibleWithTrade(SOCTradeOffer offer) {
		// what can I build with the resources I already have?
		SOCResourceSet myResources = brain.getMemory().getResources();
		boolean[] piecesAvailbleBeforeTrade = piecesAvailableToBuildWithResources(myResources);

		// determine my resources after the trade
		SOCResourceSet resourcesAfterTrade = myResources.copy();
		resourcesAfterTrade.add(offer.getGiveSet());
		resourcesAfterTrade.subtract(offer.getGetSet());

		// can I build something with the resources after the trade not possible before?
		boolean[] piecesAvailbleAfterTrade = piecesAvailableToBuildWithResources(resourcesAfterTrade);
		for (int i = 0; i < piecesAvailbleAfterTrade.length; i++) {
			if (!piecesAvailbleBeforeTrade[i] && piecesAvailbleAfterTrade[i]) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Determine whether I can make a bank/port trade only after accepting the offer.
	 * @param offer
	 * @return 
	 */
	private boolean bankTradeOnlyPossibleWithTrade(SOCTradeOffer offer) {
		// what can I build with the resources I already have?
		SOCResourceSet myResources = brain.getMemory().getResources();
		boolean[] bpTradesAvailbleBeforeTrade = bankPortTradesAvailableWithResources(myResources);

		// determine my resources after the trade
		SOCResourceSet resourcesAfterTrade = myResources.copy();
		resourcesAfterTrade.add(offer.getGiveSet());
		resourcesAfterTrade.subtract(offer.getGetSet());

		// can I make a bank/port trade with the resources after the trade not possible before?
		boolean[] bpTradesAvailbleAfterTrade = bankPortTradesAvailableWithResources(resourcesAfterTrade);
		for (int i = 0; i < bpTradesAvailbleBeforeTrade.length; i++) {
			if (!bpTradesAvailbleBeforeTrade[i] && bpTradesAvailbleAfterTrade[i]) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Simple wrapper - check we have resources, then call the implementation of the trade consideration function
	 * @param offer
	 * @param forced
	 * @return
	 */
	private int considerOfferToMeWrapper(SOCTradeOffer offer, boolean forced) {    	
		int myPlayerNum = brain.getPlayerNumber();

		// Verify we have the resources before evaluating whether we'd like to do the deal
		// we can just be optimistic, because we have no unknown resoruces in our own hand
		if (!isLegal(offer, myPlayerNum, true)) {
			return REJECT_OFFER;
		}

		// Handle our response to a force-accept move
		if (forced) {
			// If we're gullible when we get a force accept moves, simply accept the offer.
			if (brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_GULLIBLE)) {
				return StacRobotNegotiator.ACCEPT_OFFER;
			}

			// Only comply with a force-accept move if we can build something that we can't build without the trade
			if (brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_COMPLY_ONLY_FOR_IMMEDIATE_BUILD_NOT_POSSIBLE_BEFORE)) {

				if (buildOnlyPossibleWithTrade(offer)) {
					return StacRobotNegotiator.ACCEPT_OFFER;
				}
			}

			// Only comply with a force-accept move if there is a bank/port trade that we can't make without the trade
			if (brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_COMPLY_ONLY_FOR_IMMEDIATE_BANK_TRADE_NOT_POSSIBLE_BEFORE)) {

				if (bankTradeOnlyPossibleWithTrade(offer)) {
					return StacRobotNegotiator.ACCEPT_OFFER;
				}
			}

			// Only comply with a force-accept move if we can build something that we can't build without the trade
			// Or if there is a bank/port trade that we can't make without the trade
			if (brain.isRobotType(StacRobotType.NP_FORCE_ACCEPT_COMPLY_ONLY_FOR_IMMEDIATE_BUILD_OR_BANK_TRADE_NOT_POSSIBLE_BEFORE)) {
				if (buildOnlyPossibleWithTrade(offer) || bankTradeOnlyPossibleWithTrade(offer)) {
					return StacRobotNegotiator.ACCEPT_OFFER;
				}
			}
		}

		// verify the player is not embargoed
		int pn = offer.getFrom();
		if (brain.getMemory().isPlayerEmbargoed(pn)) {
			D.ebugPrintlnINFO(game.getTurnCount() + ": " + brain.getPlayerNumber() + ": Rejecting offer from embargoed player " + pn);
			return StacRobotNegotiator.REJECT_OFFER;
		}

		if (brain.isRobotType(StacRobotType.PARTIALISING_COMPLETE_OFFERS_100_PERCENT) || brain.isRobotType(StacRobotType.PARTIALISING_COMPLETE_OFFERS_50_PERCENT)) {
			return considerOfferToMePartialisingRobot(offer);
		}

		return considerOfferToMe(offer);
	}

	/**
	 * Function to consider an offer that has been sent to this agent.  
	 * The default behaviour is to use the original considerOffer function.  
	 * This may be overridden to develop agents with different trade policies/strategy
	 * It is assumed when this is called that we have the resources being requested.
	 * @param offer   the trade offer
	 * @return
	 */
	protected int considerOfferToMe(SOCTradeOffer offer) {
		//if the player is close to winning, don't trade with him
		int offerer = offer.getFrom();
		if (playerWinThreat(offerer))
			return REJECT_OFFER;

//		if ( brain.isRobotType(StacRobotType.MDP_LEARNING_NEGOTIATOR) ) {
//			if ( mdp_negotiator.build_plan != null )
//				return mdp_negotiator.considerOffer( offer, brain.getGame().getName(), brain.getPlayerNumber(), ourPlayerData );
//		}
//
		return considerOfferOld(offer, brain.getPlayerNumber());
	}

	/**
	 * Consider an offer that has been sent to this agent when the agent is a partialising agent.
	 * @param offer
	 * @return 
	 * @author Markus Guhe
	 */
	protected int considerOfferToMePartialisingRobot(SOCTradeOffer offer) {
		//if the offer we got matches the one we had originally in mind we accept immediately 
		//(it may be better)
		SOCTradeOffer bestCompleteOffer = brain.getMemory().getOriginalBestCompleteTradeOffer();
		if (bestCompleteOffer != null) {
			boolean offerMatches = true;
			SOCResourceSet whatIGet = offer.getGiveSet();
			SOCResourceSet whatIGive = offer.getGetSet();
			if (!whatIGet.contains(bestCompleteOffer.getGetSet()))
				offerMatches = false;
			if (!bestCompleteOffer.getGiveSet().contains(whatIGive))
				offerMatches = false;
			if (offerMatches) {
				//reset the original best complete trade offer because we may get another accept
				brain.getMemory().setOriginalBestCompleteTradeOffer(null);
				return ACCEPT_OFFER;
			}
		}

		return considerOfferOld(offer, brain.getPlayerNumber());
	}

	/**
	 * Simple wrapper: check if opponent MIGHT have the requested resources and then guess the response
	 * @param offer        the trade offer
	 * @param receiverNum  the opponent we're considering making the trade offer to
	 * @return             the decision (REJECT_OFFER, ACCEPT_OFFER, COUNTER_OFFER, COMPLETE_OFFER)
)
	 */
	protected int guessOpponentResponseWrapper(SOCTradeOffer offer, int receiverNum) {
		boolean optimisticAboutOpponentUnknowns = !brain.isRobotType(StacRobotType.PESSIMISTIC_ABOUT_OPPONENT_UNKONWNS);
		if (!isLegal(offer, receiverNum, optimisticAboutOpponentUnknowns)) {
			return REJECT_OFFER;
		}
		else {
			return guessOpponentResponse(offer, receiverNum);
		}
	}

	/**
	 * Function to try to guess how an opponent would respond to a trade.  Default behaviour is to use the original considerOffer function.
	 * @param offer        the trade offer
	 * @param receiverNum  the opponent we're considering making the trade offer to
	 * @return             the decision (REJECT_OFFER, ACCEPT_OFFER, COUNTER_OFFER, COMPLETE_OFFER)
	 */
	protected int guessOpponentResponse(SOCTradeOffer offer, int receiverNum) {
		// if we can make a force-accept move, assume that our trade offer will be accepted
		SOCBuildPlanStack buildPlan = brain.getBuildingPlan();
		if (shouldOfferBeForced(offer, buildPlan, null)) {
			return StacRobotNegotiator.ACCEPT_OFFER;
		}
		return considerOfferOld(offer, receiverNum);
	}

	/**
	 * The old considerOffer method, which was used both to determine if an opponent would possibly accept, and also to decide whether we should accept
	 * trade offers sent to us.  These are very different decisions (eg we may want to be optimistic and always assume someone will accept, we may
	 * be more cautious with opponents near winning than we expect our opponents to be, etc).
	 * This is still the default behavior for both decisions, unless overridden.  Make it protected in case other implementations want to use this under certain conditions
	 * @param offer        the trade offer
	 * @param receiverNum  the opponent we're considering making the trade offer to
	 * @return             the decision (REJECT_OFFER, ACCEPT_OFFER, COUNTER_OFFER, COMPLETE_OFFER)
	 */
	protected int considerOfferOld(SOCTradeOffer offer, int receiverNum) {
		if (brain.isRobotType(StacRobotType.NO_TRADES)) {
			return SOCRobotNegotiatorImpl.REJECT_OFFER;
		}        

		SOCResourceSet rsrcsOut = offer.getGetSet();
		SOCResourceSet rsrcsIn = offer.getGiveSet();

		return considerOfferOld(offer.getFrom(), receiverNum, rsrcsOut, rsrcsIn);

	}

	/**
	 * Implementation of actual logic, which can be used with just resource sets and doesn't require the offer object, which may not exist.
	 * @param senderNum
	 * @param receiverNum
	 * @param rsrcsOut
	 * @param rsrcsIn
	 * @return
	 */
	protected int considerOfferOld(int senderNum, int receiverNum, SOCResourceSet rsrcsOut, SOCResourceSet rsrcsIn) {
		/// This version should be faster
		D.ebugPrintlnINFO("***** CONSIDER OFFER OLD *****");

		SOCPlayer receiverPlayerData = game.getPlayer(receiverNum);
		SOCResourceSet receiverResources = brain.getMemory().getOpponentResources(receiverNum);

		// if the receiver doesn't have what's asked for, they'll reject
		if ((receiverResources.getAmount(SOCResourceConstants.UNKNOWN) == 0) && (!receiverResources.contains(rsrcsOut))) {
			D.ebugPrintlnINFO("Reject offer; receiver does not have resource asked for.");
			return REJECT_OFFER;
		}

		D.ebugPrintlnINFO("senderNum = " + senderNum);
		D.ebugPrintlnINFO("receiverNum = " + receiverNum);
		D.ebugPrintlnINFO("rsrcs from receiver = " + rsrcsOut);
		D.ebugPrintlnINFO("rsrcs to receiver = " + rsrcsIn);

		SOCPlayerTracker receiverPlayerTracker = (SOCPlayerTracker) playerTrackers.get(Integer.valueOf(receiverNum));
		if (receiverPlayerTracker == null) {
			D.ebugPrintlnINFO("Reject offer; receiverPlayerTracker == null");
			return REJECT_OFFER;
		}

		SOCPlayerTracker senderPlayerTracker = (SOCPlayerTracker) playerTrackers.get(Integer.valueOf(senderNum));
		if (senderPlayerTracker == null) {
			D.ebugPrintlnINFO("Reject offer; senderPlayerTracker == null");
			return REJECT_OFFER;
		}

		SOCPossiblePiece receiverTargetPiece = brain.getMemory().getTargetPiece(receiverNum);
		D.ebugPrintlnINFO("targetPieces[" + receiverNum + "] = " + receiverTargetPiece);
		SOCBuildPlanStack receiverBuildingPlan;
		if (receiverTargetPiece == null) {
			receiverBuildingPlan = predictBuildPlan(receiverNum);
			if (receiverBuildingPlan.empty()) {
				D.ebugPrintlnINFO("Reject offer; receiverBuildingPlan is empty");
				return REJECT_OFFER;
			}
			receiverTargetPiece = (SOCPossiblePiece) receiverBuildingPlan.peek();
			brain.getMemory().setTargetPieceUnannounced(receiverNum, receiverTargetPiece);
		} else {
			receiverBuildingPlan  = new SOCBuildPlanStack();
			receiverBuildingPlan.push(receiverTargetPiece);         
		}
		D.ebugPrintlnINFO("receiverTargetPiece = " + receiverTargetPiece);

		SOCPossiblePiece senderTargetPiece = brain.getMemory().getTargetPiece(senderNum);
		D.ebugPrintlnINFO("targetPieces[" + senderNum + "] = " + senderTargetPiece);
		if (senderTargetPiece == null) {
			SOCBuildPlanStack senderBuildingPlan = predictBuildPlan(senderNum);
			if (senderBuildingPlan.empty()) {
				D.ebugPrintlnINFO("Reject offer; senderBuildingPlan is empty");
				return REJECT_OFFER;
			}
			senderTargetPiece = (SOCPossiblePiece) senderBuildingPlan.peek();
			brain.getMemory().setTargetPieceUnannounced(senderNum, senderTargetPiece);
		}
		D.ebugPrintlnINFO("senderTargetPiece = " + senderTargetPiece);
		
		// in case of HWU MDP negotiator, now run offer response policy
		if ( brain.isRobotType(StacRobotType.MDP_LEARNING_NEGOTIATOR) ) {
			//return REJECT_OFFER;
			if ( mdp_negotiator.build_plan != null )
				return mdp_negotiator.considerOffer( rsrcsOut, rsrcsIn, brain.getGame().getName(), brain.getPlayerNumber(), ourPlayerData );
		}

		int senderWGETA = senderPlayerTracker.getWinGameETA();
		if (senderWGETA > WIN_GAME_CUTOFF) {
			//  see if the sender is in a race with the receiver
			boolean inARace = false;

			if ((receiverTargetPiece.getType() == SOCPossiblePiece.SETTLEMENT) || (receiverTargetPiece.getType() == SOCPossiblePiece.ROAD)) {
				Enumeration threatsEnum = receiverTargetPiece.getThreats().elements();
				while (threatsEnum.hasMoreElements()) {
					SOCPossiblePiece threat = (SOCPossiblePiece) threatsEnum.nextElement();
					if ((threat.getType() == senderTargetPiece.getType()) && (threat.getCoordinates() == senderTargetPiece.getCoordinates())) {
						inARace = true;
						D.ebugPrintlnINFO("inARace == true (threat from sender)");
						break;
					}
				}
				if (!inARace && receiverTargetPiece.getType() == SOCPossiblePiece.SETTLEMENT) {
					Enumeration conflictsEnum = ((SOCPossibleSettlement) receiverTargetPiece).getConflicts().elements();
					while (conflictsEnum.hasMoreElements()) {
						SOCPossibleSettlement conflict = (SOCPossibleSettlement) conflictsEnum.nextElement();
						if ((senderTargetPiece.getType() == SOCPossiblePiece.SETTLEMENT) && (conflict.getCoordinates() == senderTargetPiece.getCoordinates())) {
							inARace = true;
							D.ebugPrintlnINFO("inARace == true (conflict with sender)");
							break;
						}
					}
				}
			}

			if (!inARace) {
				/// see if this is good for the receiver
				SOCResourceSet targetResources = getResourcesForPlan(receiverBuildingPlan) ;                    
				SOCBuildingSpeedEstimate estimate = brain.getEstimator(receiverPlayerData.getNumbers());

				SOCTradeOffer receiverBatna = getOfferToBank(targetResources, receiverPlayerData.getResources());
				D.ebugPrintlnINFO("*** receiverBatna = " + receiverBatna);

				int batnaBuildingTime = getETAToTargetResources(receiverPlayerData, targetResources, SOCResourceSet.EMPTY_SET, SOCResourceSet.EMPTY_SET, estimate);
				D.ebugPrintlnINFO("*** batnaBuildingTime = " + batnaBuildingTime);

				int offerBuildingTime = getETAToTargetResources(receiverPlayerData, targetResources, rsrcsOut, rsrcsIn, estimate);
				D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);

				//Store building time for this offer so that we can make a meta evaluations when we return from this routine,
				//but only if this building time is better than the BATNA, otherwise take 1000 as the usual worst case value.
				//(If the BATNA is better than the offer, then we shouldn't accept the trade: it will be better to trade with bank/port or not to trade.) 
				boolean to[] = new boolean[game.maxPlayers];
				to[receiverNum] = true;
				int offerGlobalETA = getETAToTargetResources(receiverPlayerData, GLOBAL_ETA_RS, rsrcsOut, rsrcsIn, estimate);
				TradeOfferWithStats t = new TradeOfferWithStats(rsrcsOut, false, rsrcsIn, false, senderNum, to, offerBuildingTime, offerGlobalETA);
				if (acceptTrade(t, receiverBatna, batnaBuildingTime)) {
					//original:  if (offerBuildingTime < batnaBuildingTime) {
					lastOfferETA = offerBuildingTime; 
					lastOfferGlobalETA = offerGlobalETA;
					D.ebugPrintlnINFO("Accept offer");
					return ACCEPT_OFFER;
				} else {
					lastOfferETA = 1000;
					lastOfferGlobalETA = 1000;
					D.ebugPrintlnINFO("Counter offer");
					return COUNTER_OFFER;
				}                
			}
		}

		return REJECT_OFFER;
	}

	/**
	 * Aux function to compute the number of rolls until we reach the target given a possible offer.
	 *
	 * @param player             our player data
	 * @param targetResources    the resources we want
	 * @param giveSet            the set of resources we're giving
	 * @param getSet             the set of resources we're receiving
	 * @param estimate           a SOCBuildingSpeedEstimate for our player
	 * @return                   estimated time until we have the targetResources
	 */
	protected int getETAToTargetResources(SOCPlayer player, SOCResourceSet targetResources, SOCResourceSet giveSet, SOCResourceSet getSet, SOCBuildingSpeedEstimate estimate)
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
		}

		D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
		D.ebugPrintlnINFO("*** ourResourcesCopy = " + ourResourcesCopy);

		return (offerBuildingTime);
	}

	/**
	 * aux function for make offer
	 * @param giveResourceSet
	 * @param getResourceSet
	 * @param neededResource
	 * @return
	 */
	private StacTradeOffer makeOfferAux(SOCResourceSet giveResourceSet, SOCResourceSet getResourceSet, int neededResource)
	{
		D.ebugPrintlnINFO("**** makeOfferAux ****");
		D.ebugPrintlnINFO("giveResourceSet = " + giveResourceSet);
		D.ebugPrintlnINFO("getResourceSet = " + getResourceSet);

		StacTradeOffer offer = null;

		///
		/// see if we've made this offer before
		///
		int opn = ourPlayerData.getPlayerNumber();
		boolean[] offeredTo = new boolean[game.maxPlayers];
		StacTradeOffer offerToCheck = new StacTradeOffer(game.toString(), opn, offeredTo, giveResourceSet, false, getResourceSet, false);
		boolean match = brain.getMemory().pastTradeOfferExists(offerToCheck);

		///
		/// see if somone is offering this to us
		///
		if (!match)
		{
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
					boolean isSellingResource = brain.getMemory().isSellingResource(i, neededResource);
					D.ebugPrintlnINFO("** isSellingResource[" + i + "][" + neededResource + "] = " + isSellingResource);

					if ((i != opn) && isSellingResource &&
							(! game.isSeatVacant(i)) &&
							(brain.getMemory().getOpponentResourcesTotal(i) >= getResourceSet.getTotal()))
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

				boolean isSellingResource = brain.getMemory().isSellingResource(curpn, neededResource);
				if (isSellingResource && (brain.getMemory().getOpponentResourcesTotal(curpn) >= getResourceSet.getTotal()))
				{
					D.ebugPrintlnINFO("** isSellingResource[" + curpn + "][" + neededResource + "] = " + isSellingResource);

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
				offer = new StacTradeOffer(game.getName(), ourPlayerData.getPlayerNumber(), offeredTo, giveResourceSet, false, getResourceSet, false);

				///
				/// only make the offer if we think somone will take it
				///
				boolean acceptable = false;

				for (int pn = 0; pn < game.maxPlayers; pn++)
				{
					if (offeredTo[pn])
					{
						int offerResponse = ACCEPT_OFFER;
						if (!brain.isRobotType(StacRobotType.BUILD_PLAN_INDIFFERENT)) {
							offerResponse = considerOffer(offer, pn);
							D.ebugPrintlnINFO("* considerOffer2(offer, " + pn + ") = " + offerResponse);
						}    

						if (offerResponse == ACCEPT_OFFER)
						{
							if (!brain.isRobotType(StacRobotType.PROBABILISTIC_OFFER_DENIER) || RANDOM.nextInt(100) < (Integer) brain.getTypeParam(StacRobotType.PROBABILISTIC_OFFER_DENIER) ) { 
								acceptable = true;    
								break;
							} else {
								//we have to "remember" this as if we made the offer so that it is not tried again later on (when the above test may well succeed)
								addToOffersMade(offer);
								break;
							}
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
		// Note that this is called from within the parent class's constructor (not ideal, not necessary), and therefore
		//  STAC variables are not yet assigned.  Checking null for brain gets around this, and doesn't
		//  change functionality.
		if (brain!=null && brain.isRobotType(StacRobotType.LISTEN_BUILD_PLAN) ) {
			// Special handling if we are listening to build plan announcements.  Only clear the
			//  piece (ie force a simulation) if the piece we have stored was not an announced 
			//  element of a build plan.
			// TODO: When we consider announcements that don't happen 100% of the time, we'll 
			//  need to reconsider this.
			for (int i=0; i<4; i++) {
				if (!brain.getMemory().wasTargetPieceAnnounced(i)) {
					brain.getMemory().setTargetPieceUnannounced(i, null); //if it wasn't announced, clear it
				}
			}
		}
		else if(brain!=null){
			brain.getMemory().resetTargetPieces(); //these will be initialised when the memory is created
		}
	}

	@Override
	public void setTargetPiece(int pn, SOCBuildPlanStack buildPlan) {
		if (buildPlan!=null && buildPlan.size() > 0 ) {
			brain.getMemory().setTargetPieceUnannounced(pn, buildPlan.peek());
		}
		else {
			brain.getMemory().setTargetPieceUnannounced(pn, null);
		}
	}

	/**
	 * 
	 * @param pn
	 * @param p
	 */
	public void setTargetPiece(int pn, SOCPossiblePiece p) {
		brain.getMemory().setTargetPieceAnnounced(pn, p);     

		// Debug code below: Determine whether the announced piece is any different from what we would have predicted
		// TODO: Rework this to get actual statistics of these phenomena to get a better estimate of how much info this adds
		/*
        if (pn != this.ourPlayerData.getPlayerNumber()) {        
            // See if this is any different from what our simulator would have predicted...
            SOCBuildPlanStack buildingPlan  = new SOCBuildPlanStack();

            SOCPlayerTracker pt = (SOCPlayerTracker) playerTrackers.get(Integer.valueOf(pn));
            SOCPlayer pd = game.getPlayer(pn);

            SOCRobotDM<SOCBuildPlanStack> simulator = new SOCRobotDMImpl(brain.getRobotParameters(), playerTrackers, pt, pd, buildingPlan, brain.getRobotParameters().getStrategyType());
            simulator.planStuff();

            if (buildingPlan.isEmpty()) {
                if (p!=null) {
                    System.err.println("Simulator said null, plan announced");
                }
                else {
                    System.err.println("Null announced and predicted");
                }
            }
            else {
                SOCPossiblePiece pp = buildingPlan.peek();
                // NB: I initially added a .equals to SOCPossiblePiece - this broke something unrelated, where .equals is implicitly assumed to mean object equality only
                if (p == null) {
                    System.err.println("Announced null, simulator disagrees");
                }
                else if (pp.getType() == p.getType() && pp.getCoordinates() == p.getCoordinates()) {
                    System.err.println("Correct prediction");
                }
                else if (pp.getType() == p.getType() ) {
                    System.err.println("Incorrect prediction - coord only");
                }
                else {
                    System.err.println("Announced: " + p.toString() + " vs Predicted: " + pp.toString());
                } 
            }        
        }*/

	}

	//    @Override
	//    protected int handlePartialOffer(SOCTradeOffer offer) {
	//        // TODO: This is slightly problematic in that the old negotiator doesn't have the same targetpieces - 
	//        //  should probably update that first, but 
	//        return oldNeg.handlePartialOffer(offer);
	//    }

	/**
	 * Treat a partial offer that's been made to me and decide whether there's a completed offer I want to make as counteroffer.
	 * If there is such a counteroffer, it is stored in the declarative memory as bestCompletedOffer.
	 * @param offer the partial trade offer
	 * @return      decision how to react to the offer
	 */
	@Override
	public int handlePartialOffer(SOCTradeOffer offer)   {
		TradeOfferWithStats counteroffer = handlePartialOfferImpl(offer);
		if (counteroffer != null) {
			setBestCompletedOffer(counteroffer.getOffer(), lastOfferETA, lastOfferGlobalETA);
			return COMPLETE_OFFER;
		} else 
			return REJECT_OFFER;
	}

	/**
	 * This is the actual functionality for responding to partial offers.
	 * It returns the counteroffer, not just the response to the offer, where null means the offer is rejected.
	 * (We can't accept partial offers anyway.)
	 * @param offer the partial offer
	 * @return      the counteroffer as TradeOfferWithStats or null
	 */
	protected TradeOfferWithStats handlePartialOfferImpl(SOCTradeOffer offer)   {
		if (brain.isRobotType(StacRobotType.NO_TRADES)) {
			return null;
		}

		//if we randomly complete partial offers, just choose a random combination of the resources we have to complete the offer
		if (brain.isRobotType(StacRobotType.RANDOM_COMPLETION_OF_PARTIAL_OFFERS)) {
			SOCResourceSet currentResouces = brain.getMemory().getResources();
			SOCResourceSet randomGiveResources = new SOCResourceSet();

			for (int rsrcType = SOCResourceConstants.CLAY; rsrcType <= SOCResourceConstants.WOOD; rsrcType++) {
				int currentAmount = currentResouces.getAmount(rsrcType);
				if (currentAmount > 0) {
					int randomAmount = RANDOM.nextInt(currentAmount) + 1; //nextInt give a number between 0 and (parameter value - 1)
					randomGiveResources.setAmount(randomAmount, rsrcType);
				}
			}

			//test whether the (randomly) completed offer is acceptable
			StacTradeOffer completedOffer = new StacTradeOffer(offer.getGame(), offer.getFrom(), offer.getTo(), randomGiveResources, false, offer.getGetSet(), false); // make a copy of the current partial offer             
			int response = considerOffer(completedOffer, ourPlayerData.getPlayerNumber()); //the negotiator's response will be 1000 if the offer is worse than the BATNA
			if (response == SOCRobotNegotiator.REJECT_OFFER) {
				return null;
			} else {
				D.ebugPrintlnINFO("### " + ourPlayerData.getName() + ": Found an acceptable completed counter offer: " + completedOffer.toString() + "; offer building time: " + lastOfferETA);

				//remember the random offer as `best' offer
				return new TradeOfferWithStats(completedOffer, lastOfferETA, lastOfferGlobalETA);
			}
		}



		//now we look for a more sensible completing of partial offers
		D.ebugPrintlnINFO("### " + ourPlayerData.getName() + ": Trying to complete a partial offer: " + offer.toString());

		//all combinations of resources we're considering to complete the offer
		SOCResourceSet potentialResourceSet[] = {
				new SOCResourceSet(2, 0, 0, 0, 0, 0), new SOCResourceSet(0, 2, 0, 0, 0, 0), new SOCResourceSet(0, 0, 2, 0, 0, 0), 
				new SOCResourceSet(0, 0, 0, 2, 0, 0), new SOCResourceSet(0, 0, 0, 0, 2, 0), 
				new SOCResourceSet(1, 1, 0, 0, 0, 0), new SOCResourceSet(1, 0, 1, 0, 0, 0), new SOCResourceSet(1, 0, 0, 1, 0, 0), 
				new SOCResourceSet(1, 0, 0, 0, 1, 0), new SOCResourceSet(0, 1, 1, 0, 0, 0), new SOCResourceSet(0, 1, 0, 1, 0, 0), 
				new SOCResourceSet(0, 1, 0, 0, 1, 0), new SOCResourceSet(0, 0, 1, 1, 0, 0), new SOCResourceSet(0, 0, 1, 0, 1, 0), 
				new SOCResourceSet(0, 0, 0, 1, 1, 0), 
				new SOCResourceSet(1, 0, 0, 0, 0, 0), new SOCResourceSet(0, 1, 0, 0, 0, 0), new SOCResourceSet(0, 0, 1, 0, 0, 0), 
				new SOCResourceSet(0, 0, 0, 1, 0, 0), new SOCResourceSet(0, 0, 0, 0, 1, 0)};

		//reset the completed offer that may still be around from a previous call
		brain.getMemory().resetBestCompletedTradeOffer();

		//below, we're considering the inverse of a trade when estimating the opponent's response; just compute the to array once outside the loop
		boolean[] inverseOfferTo = new boolean[offer.getTo().length];
		inverseOfferTo[offer.getFrom()] = true;

		//identify whether givables or receivables were left unspecified in the offer
		boolean offerGivablesUnspecified = offer.getGiveSet().isEmptySet();
		//        if (offer.getGetSet().isEmptySet()) {
		//            offerGivablesUnspecified = false;
		//        } else if (offer.getGiveSet().isEmptySet()) {
		//            offerGivablesUnspecified = true;
		//        } else {
		//            D.ebugWARNING("This is not a partial offer - handlePartialOffer() should not have been called!");
		//            return null;
		//        }

		SOCResourceSet proposedGiveResources = offer.getGiveSet();
		SOCResourceSet proposedGetResources = offer.getGetSet();
		SOCResourceSet currentResouces = brain.getMemory().getResources();

		//Lists with the possible completed offers
		//TODO: May be more efficient to just keep the one offer of each type that has the best ETA for us 
		//(lowest for "we would accept", highest for "opponent should accept")
		List<TradeOfferWithStats> completedOffersWeWouldAccpet = new ArrayList<TradeOfferWithStats>();
		List<TradeOfferWithStats> completedOffersTheOpponentShouldAccept = new ArrayList<TradeOfferWithStats>();
		List<TradeOfferWithStats> completedOffersBothConditions = new ArrayList<TradeOfferWithStats>();
		for (SOCResourceSet resourcesSetToTest : potentialResourceSet) {
			if (offerGivablesUnspecified)
				proposedGiveResources = resourcesSetToTest;
			else
				proposedGetResources = resourcesSetToTest;

			//can we make this trade?
			if (proposedGiveResources.disjoint(proposedGetResources) && currentResouces.contains(proposedGetResources)) {
				// Before going into deep calculations of our build times, etc, we should first consider whether the
				//  offer is even possible - ie whether the initiator has, or might have, the resources we are looking for.
				//  We can use considerOffer2 for this, in the inverse direction, which will check this among the first things it does (not quite correctly, but in line with the rest of the code, at least
				//  However, this goes deeper and considers whether we think the recipient would accept (or at least counter). It may end up taking longer in some cases (typically when we can't rule out at least half the options)
				//  Additionally, we may sometimes want to come back with an offer we feel they will reject, because it will let us negotiate towards something
				//  mutually beneficial.  Try this for now...

				//Do we believe the opponent has the resources we're asking for?
				SOCResourceSet opponentResToCheck = offerGivablesUnspecified ? proposedGiveResources : proposedGetResources;
				boolean opponentHasResourcesForTrade = brain.getMemory().getOpponentResources(offer.getFrom()).contains(opponentResToCheck);
				if (!opponentHasResourcesForTrade)
					continue;

				//The counteroffer that we'd eventually make inverts the sender/recipient & give/get sets, 
				//so to estimate the opponent response, we have to create the offer that's the inverse to our completed offer
				StacTradeOffer inverseOffer = new StacTradeOffer(offer.getGame(), ourPlayerData.getPlayerNumber(), inverseOfferTo, proposedGetResources, false, proposedGiveResources, false);

				//Don't make repeat offers 
				//NOTE: when using ACT-R DM this calls pastTradeOfferExistsInACTRDM(), which does not check the from and to fields!
				if (brain.getMemory().pastTradeOfferExists(inverseOffer)) {
					continue;
				}

				//Do we believe the opponent would accept our completed counteroffer?
				//NB: The ETA we get in lastOfferETA is the opponent's ETA
				SOCTradeOffer completedOffer = new SOCTradeOffer(offer.getGame(), offer.getFrom(), offer.getTo(), proposedGiveResources, proposedGetResources); // make a copy of the current partial offer
				TradeOfferWithStats completedOfferAsTradeOffer = new TradeOfferWithStats(proposedGiveResources, false, proposedGetResources, false, offer.getFrom(), offer.getTo(), lastOfferETA, lastOfferGlobalETA);
				int response = guessOpponentResponseWrapper(completedOffer, offer.getFrom());//considerOffer(inverseOffer, offer.getFrom());
				boolean opponentWouldAccept = false;
				if (response == ACCEPT_OFFER) {
					D.ebugPrintlnINFO("### " + ourPlayerData.getName() + ": Found an acceptable completed counter offer the opponent should accept: " + completedOffer.toString() + "; ETA: " + lastOfferETA);
					completedOffersTheOpponentShouldAccept.add(completedOfferAsTradeOffer);
					opponentWouldAccept = true;
				}

				//Would we want to make this trade?
				//NB: The response can in principle also be a counteroffer, but we are accepting a counteroffer to our stipulated completed offer would be to speculative to propose
				response = considerOffer(completedOffer, ourPlayerData.getPlayerNumber()); //the negotiator's response will be 1000 if the offer is worse than the BATNA
				if (response == SOCRobotNegotiator.ACCEPT_OFFER) {
					D.ebugPrintlnINFO("### " + ourPlayerData.getName() + ": Found an acceptable completed counter offer we would accept: " + completedOffer.toString() + "; ETA: " + lastOfferETA);
					completedOffersWeWouldAccpet.add(completedOfferAsTradeOffer);
					if (opponentWouldAccept)
						completedOffersBothConditions.add(completedOfferAsTradeOffer);
				}
			}
		}

		//Sort the completed offers by their ETA, prefer shortest for us, largest for opponent offers
		Collections.sort(completedOffersTheOpponentShouldAccept, Collections.reverseOrder());
		Collections.sort(completedOffersWeWouldAccpet);
		Collections.sort(completedOffersBothConditions);

		if (D.ebugIsEnabled()) {
			String str = brain.getPlayerName() + " - Completed offers the opponent should accept";
			for (TradeOfferWithStats o : completedOffersTheOpponentShouldAccept) {
				str += "\n" + o.toString();
			}
			str += "\nCompleted offers we would accept";
			for (TradeOfferWithStats o : completedOffersWeWouldAccpet) {
				str += "\n" + o.toString();
			}
			str += "\nCompleted offers with both conditions true";
			for (TradeOfferWithStats o : completedOffersBothConditions) {
				str += "\n" + o.toString();
			}
			D.ebugPrintlnINFO(str);
		}

		//Select one of the completed offers 
		//If we're choosing an offer from the "opponent should accept" list, the ETB (eta) value is that of the opponent, not ours,
		//so we'll recompute it below if necessary.
		boolean needToComputeOurETA = false;
		TradeOfferWithStats selectedBestCompletedOffer = null;
		if (brain.isRobotType(StacRobotType.PARTIAL_COMPLETION_PREFER_MY_BEST)) {
			// we prefer to use the completion that's best for us; if there's none, take the one that's worst for the opponent
			if (completedOffersWeWouldAccpet.size() > 0)
				selectedBestCompletedOffer = completedOffersWeWouldAccpet.get(0);
			else if (completedOffersTheOpponentShouldAccept.size() > 0) {
				selectedBestCompletedOffer = completedOffersTheOpponentShouldAccept.get(0);
				needToComputeOurETA = true;
			}
		} else if (brain.isRobotType(StacRobotType.PARTIAL_COMPLETION_CHOOSE_MY_BEST)) {
			// just choose one offer we would like to accept
			if (completedOffersWeWouldAccpet.size() > 0) {
				selectedBestCompletedOffer = completedOffersWeWouldAccpet.get(0);
			}
		} else if (brain.isRobotType(StacRobotType.PARTIAL_COMPLETION_PREFER_OPPONENT_WORST)) {
			// we prefer the worst completed offer for our opponent but take the one that's best for us otherwise
			if (completedOffersTheOpponentShouldAccept.size() > 0) {
				selectedBestCompletedOffer = completedOffersTheOpponentShouldAccept.get(0);
				needToComputeOurETA = true;
			} else if (completedOffersWeWouldAccpet.size() > 0)
				selectedBestCompletedOffer = completedOffersWeWouldAccpet.get(0);
		} else if (brain.isRobotType(StacRobotType.PARTIAL_COMPLETION_CHOOSE_OPPONENT_WORST)) {
			//is there an offer we believe the opponent would accept? If so, choose one of them
			if (completedOffersTheOpponentShouldAccept.size() > 0 ) {
				selectedBestCompletedOffer = completedOffersTheOpponentShouldAccept.get(0);
				needToComputeOurETA = true;
			}
		} else if (brain.isRobotType(StacRobotType.PARTIAL_COMPLETION_CHOOSE_OPPONENT_WORST) && brain.isRobotType(StacRobotType.PARTIAL_COMPLETION_CHOOSE_MY_BEST)) {
			if (completedOffersBothConditions.size() > 0) {
				selectedBestCompletedOffer = completedOffersBothConditions.get(0);
			}
		} else {
			//this is the same as PARTIAL_COMPLETION_CHOOSE_MY_BEST -- this seems to be a strongest policy, so this is default behaviour
			// just choose one offer we would like to accept
			if (completedOffersWeWouldAccpet.size() > 0) {
				selectedBestCompletedOffer = completedOffersWeWouldAccpet.get(0);
			}
		}

		if (selectedBestCompletedOffer != null) {
			//the ETA values of the selectedBestCompletedOffer contain the opponent's ETAs, so we need to recompute them here for ourself
			if (needToComputeOurETA) {
				SOCBuildPlanStack buildPlan = brain.getBuildingPlan();
				SOCResourceSet targetResources = getResourcesForPlan(buildPlan);
				SOCBuildingSpeedEstimate estimate = brain.getEstimator(ourPlayerData.getNumbers());
				selectedBestCompletedOffer.eta = getETAToTargetResources(ourPlayerData, targetResources, selectedBestCompletedOffer.give, selectedBestCompletedOffer.get, estimate);
				selectedBestCompletedOffer.globalETA = getETAToTargetResources(ourPlayerData, GLOBAL_ETA_RS, selectedBestCompletedOffer.give, selectedBestCompletedOffer.get, estimate);
			}

			//If there's an offer we'd like to accept, return now to make this as a completed (`counter') offer
			//This works by storing the completed trade in the declarative memory and returning the response to the calling method
			TradeOfferWithStats sbco = new TradeOfferWithStats(
					selectedBestCompletedOffer.give, selectedBestCompletedOffer.disjGive, selectedBestCompletedOffer.get, selectedBestCompletedOffer.disjGet,
					offer.getFrom(), selectedBestCompletedOffer.to, 
					selectedBestCompletedOffer.eta, selectedBestCompletedOffer.globalETA);
			D.ebugPrintlnINFO("### " + ourPlayerData.getName() + ": The completed offer we want to make: " + selectedBestCompletedOffer.toString() + "; build time: " + selectedBestCompletedOffer.eta + "; now returning `3'");
			return sbco;
		}

		D.ebugPrintlnINFO("### " + ourPlayerData.getName() + ": there's no completed offer I want to make - rejecting offer");
		return null;
	}

	/**
	 * The offer is an offer that has disjunctive resource sets and/or is partial offer, ie it has an empty resource set.
	 * Test all the offers that this implies and decide whether we want to make a (completing) counteroffer.
	 * @param offer the incomplete offer
	 * @return      a StacTradeOffer if we want to make a counteroffer or null if there is none (tantamount to rejecting the incomplete offer)
	 */
	protected StacTradeOffer handleDisjunctiveOrPartialOffer(StacTradeOffer offer) {
		List<SOCResourceSet> giveSetsToTest = new ArrayList<>();
		List<SOCResourceSet> getSetsToTest = new ArrayList<>();

		//assemble give sets we need to test
		if (offer.hasDisjunctiveGiveSet()) {
			for (int rtype = SOCResourceConstants.CLAY; rtype <= SOCResourceConstants.WOOD; ++rtype) {
				if (offer.getGiveSet().getAmount(rtype) > 0) {
					SOCResourceSet rs = new SOCResourceSet();
					rs.setAmount(offer.getGiveSet().getAmount(rtype), rtype);
					giveSetsToTest.add(rs);
				}
			}
		} else {
			SOCResourceSet giveSet = offer.getGiveSet();
			if (!giveSet.isEmptySet())
				giveSetsToTest.add(giveSet);
		}

		//assemble get sets we need to test
		if (offer.hasDisjunctiveGetSet()) {
			for (int rtype = SOCResourceConstants.CLAY; rtype <= SOCResourceConstants.WOOD; ++rtype) {
				if (offer.getGetSet().getAmount(rtype) > 0) {
					SOCResourceSet rs = new SOCResourceSet();
					rs.setAmount(offer.getGetSet().getAmount(rtype), rtype);
					getSetsToTest.add(rs);
				}
			}
		} else {
			SOCResourceSet getSet = offer.getGetSet();
			if (!getSet.isEmptySet())
				getSetsToTest.add(getSet);
		}

		//create all the offers defined by the disjunctive sets that we need to test
		List<StacTradeOffer>allOffers = new ArrayList<>();
		for (SOCResourceSet giveSet : giveSetsToTest) {
			for (SOCResourceSet getsSet : getSetsToTest) {
				allOffers.add(new StacTradeOffer(offer.getGame(), offer.getFrom(), offer.getTo(), giveSet, false, getsSet, false));
			}
		}

		//go through all offers, decide whether we would accept one and keep in mind that some offers can be partial offers
		//collect the possible counteroffers
		List<TradeOfferWithStats> possibleCounterOffers = new ArrayList<>();
		for (StacTradeOffer offerToTest : allOffers) {

			//is this a partial offer? we can now handle both, empty give and get sets 
			if (offerToTest.isPartialOffer()) {
				TradeOfferWithStats completedCounterOffer = handlePartialOfferImpl(offerToTest);
				if (completedCounterOffer != null)
					possibleCounterOffers.add(completedCounterOffer);
				//so it's a complete offer
			} else {
				int offerResponse = considerOffer(offerToTest, brain.getPlayerNumber());
				if (offerResponse == ACCEPT_OFFER) {
					TradeOfferWithStats completedCounterOffer = new TradeOfferWithStats(offerToTest.invertedOffer(ourPlayerData.getPlayerNumber()), lastOfferETA, lastOfferGlobalETA);
					possibleCounterOffers.add(completedCounterOffer);
				}
			}
		}
		if (possibleCounterOffers.isEmpty())
			return null;

		//choose which offer to accept
		StacTradeOffer chosenCounterOffer = pickABestOffer(possibleCounterOffers);

		//        D.ebug_enable();
		//        D.ebugPrintINFO("Making counteroffer for offer\n" + offer.toString() + "\n" + chosenCounterOffer.toString());
		//        D.ebug_disable();

		return chosenCounterOffer;
	}    



	public static void setMaxTrades(int i) {
		maxTradeOffers = i;        
	}

	/**
	 * Get the predicted build plan of an opponent
	 * TODO: Refactor predictions above to use this
	 * @param playerNum
	 * @return
	 */
	public SOCBuildPlanStack predictBuildPlan(int playerNum) {
		SOCBuildPlanStack bp  = new SOCBuildPlanStack();
		SOCPlayerTracker playerTracker = (SOCPlayerTracker) playerTrackers.get(Integer.valueOf(playerNum));
		SOCPlayer playerData = game.getPlayer(playerNum);

		// Ensure the player data we are using contains our declarative memory's belief about their resources.
		//  Save the existing resources and restore after to avoid side-effects
		SOCResourceSet rs = playerData.getResources();        
		SOCResourceSet rsOld = rs.copy();
		rs.clear();
		rs.add(brain.getMemory().getOpponentResources(playerNum));

		// Use our own strategy, the 0-best or the one of the original robot
		if (brain.isRobotType(StacRobotType.ASSUME_OTHER_AGENTS_USE_OUR_STRATEGY)) {
			StacRobotDM simulatorDM = new StacRobotDM(brain, playerTrackers, playerTracker, playerTracker.getPlayer(), bp);
			simulatorDM.planStuff();
			//TODO: Use 0-best opponent brain to guess opponent response; EXPERIMENTAL
		} else if (brain.isRobotType(StacRobotType.ASSUME_OTHER_AGENTS_USE_0_BEST_STRATEGY)) {
			//create an opponent brain with the standard 0-best robot-types
			StacRobotType opponentType = new StacRobotType();
			opponentType.addType(StacRobotType.ASSUME_OTHER_AGENTS_USE_OUR_STRATEGY);
			opponentType.addType(StacRobotType.ALWAYS_ASSUMING_IS_SELLING);
			opponentType.addType(StacRobotType.TRY_N_BEST_BUILD_PLANS, "0");
			StacRobotBrain opponentBrain = new StacRobotBrain(brain, opponentType);

			//this probably won't work, because even though we have an opponent brain, the player trackers still use OUR brain
			StacRobotDM simulatorDM = new StacRobotDM(opponentBrain, playerTrackers, playerTracker, playerTracker.getPlayer(), bp);
			simulatorDM.planStuff();
		} else {
			SOCRobotDM simulator = new SOCRobotDMImpl(brain.getRobotParameters(), playerTrackers, playerTracker, playerData, bp, brain.getRobotParameters().getStrategyType());
			simulator.planStuff();
		}

		// Restore original resources to avoid side-effects
		rs.clear();
		rs.add(rsOld);
		return bp;
	}

	@Override
	public void setBestCompletedOffer(SOCTradeOffer offer) {
		Exception e =  new Exception("You have to specify an eta for the best completed offer in class StacRobotNegotiator");
		e.printStackTrace();
	}

	protected void setBestCompletedOffer(StacTradeOffer offer, int eta, int globalETA) {
		brain.getMemory().rememberBestCompletedTradeOffer(offer, eta, globalETA);
	}

	@Override
	public SOCTradeOffer getBestCompletedOffer() {
		return brain.getMemory().retrieveBestCompletedTradeOffer();
	}

	@Override
	protected void recordResourcesFromOffer(SOCTradeOffer offer){
		if(StacRobotBrain.isChatNegotiation()){
			//do nothing as we are handling all the recording logic in the dialogue manager
		}else
			super.recordResourcesFromOffer(offer);
	}

	@Override
	protected void recordResourcesFromReject(int rejector){
		if(StacRobotBrain.isChatNegotiation()){
			//do nothing as we are handling all the recording logic in the dialogue manager
		}else
			super.recordResourcesFromReject(rejector);
	}

	@Override
	protected void recordResourcesFromRejectAlt(int rejector){
		if(StacRobotBrain.isChatNegotiation()){
			//do nothing as we are handling all the recording logic in the dialogue manager
		}else
			super.recordResourcesFromRejectAlt(rejector);
	}

	@Override
	protected void recordResourcesFromNoResponse(SOCTradeOffer ourCurrentOffer){
		if(StacRobotBrain.isChatNegotiation()){
			//do nothing as we are handling all the recording logic in the dialogue manager
		}else
			super.recordResourcesFromNoResponse(ourCurrentOffer);
	}

	//=======================================================================================

	/** 
	 * Helper class that basically does the same as {@link StacTradeOffer} but allows us to store the ETB values with the offer.
	 * It stores the values of a StacTradeOffer directly, to reduce the overhead w.r.t. messages.
 History: There used to be two classes TradeOfferWithStats and OfferWithStats that were bot doing these things; this is now combined here.
	 */
	protected class TradeOfferWithStats implements Comparable<TradeOfferWithStats> {
		int from;
		boolean[] to;
		SOCResourceSet give;
		SOCResourceSet get;
		boolean disjGive, disjGet;

		// If the trade happens, what is our ETA for our current build plan
		int eta;

		// The ETA to get resources to build one of every type of piece (used as tiebreaker when sorting)
		int globalETA;

		private TradeOfferWithStats(SOCResourceSet give, boolean disjGive, SOCResourceSet get, boolean disjGet, int from, boolean[] to, int eta, int globalETA) {
			this.give = give;
			this.get = get;
			this.disjGive = disjGive;
			this.disjGet = disjGet;
			this.from = from;
			this.to = new boolean[to.length];
			System.arraycopy(to, 0, this.to, 0, to.length);
			this.eta = eta;
			this.globalETA = globalETA;
		}

		private TradeOfferWithStats(SOCResourceSet give, boolean disjGive, SOCResourceSet get, boolean disjGet, int from, boolean[] to) {
			this(give, disjGive, get, disjGet, from, to, 1000, 1000);
		}

		private TradeOfferWithStats(StacTradeOffer offer, int eta, int globalETA) {
			this(offer.getGiveSet(), offer.hasDisjunctiveGiveSet(), offer.getGetSet(), offer.hasDisjunctiveGetSet(), offer.getFrom(), offer.getTo(), eta, globalETA);
		}

		//NOT USED
		//        public boolean isLegal() {
		//            for (int i=0; i<to.length; i++) {
		//                if (to[i])
		//                    return true;
		//            }   
		//            return false;
		//        }

		private StacTradeOffer getOffer() {
			return new StacTradeOffer(game.getName(), from, to, give, disjGive, get, disjGet);
		}

		/**
		 * Comparing two trade offers.
		 * ETA is first point of comparison (prefer trades that get us closest to our current goal).
		 * Second comparison is the difference in how long it would take to accrue get vs. give (prefer trades that 
		 * get difficult to attain resources and give easy ones).
		 * This method implements the Comparable interface.
		 * @param o  The trade offer this one is being compared to.
		 * @return Difference between the two offers' ETAs (or globalETAs)
		 */
		@Override
		public int compareTo(TradeOfferWithStats o) {
			if (eta != o.eta) { 
				return eta - o.eta;
				//ADDING THESE COMPARISONS CAUSES A ~10% INCREASE ON THE RUNTIME OF A SIMULATION
				//            } else if (get.getTotal() != o.get.getTotal()) {
				//                return get.getTotal() - o.get.getTotal();
				//            } else if (give.getTotal() != o.give.getTotal()) {
				//                return give.getTotal() - o.give.getTotal();
			} else {
				return globalETA - o.globalETA; 
			}
		}

		private int deltaETA(TradeOfferWithStats offer) {
			int diff = eta - offer.eta;
			return Math.abs(diff);
		}

		@Override
		public String toString() {
			String str = "game=" + game.getName() + "|ETA=" + eta + "|globalETA=" + globalETA + "|from=" + from + "|to=" + to[0];
			for (int i = 1; i < to.length; i++) {
				str += ("," + to[i]);
			}
			str += ("|give=" + give.toVeryShortString() + " d=" + Boolean.toString(disjGive) + "|get=" + get.toVeryShortString() + " d=" + Boolean.toString(disjGet));
			return str;
		}
	}

}
