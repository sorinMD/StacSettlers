/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2009 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCTradeOffer;
import soc.message.SOCMakeOffer;
import soc.message.SOCRejectOffer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Vector;

import java.util.Random;


/**
 * Moved the routines that make and
 * consider offers out of the robot
 * brain.
 *
 * Refactored to move much of the decision making into implementations.
 * TODO: Reactor more, eg "isSelling" functionality, which we will definitely
 * want to experiment with (deciding what constitutes unwillingness to trade
 * is a crucial aspect of trade strategy)
 *
 * @author Robert S. Thomas
 */
public abstract class SOCRobotNegotiator<BP extends SOCBuildPlan>
{
    // Use a randomizer to decide whether to make a partial offer, occasionally
    protected static final Random RANDOM = new Random();

    protected static final int WIN_GAME_CUTOFF = 25;
    public static final int REJECT_OFFER = 0;
    public static final int ACCEPT_OFFER = 1;
    public static final int COUNTER_OFFER = 2;
    public static final int COMPLETE_OFFER = 3;
    protected final SOCRobotBrain<?, ?, BP> brain;
    protected SOCGame game;
    protected HashMap playerTrackers;
    protected SOCPlayerTracker ourPlayerTracker;
    protected SOCPlayer ourPlayerData;
       
    /**
     * constructor
     *
     * @param br  the robot brain
     */
    public SOCRobotNegotiator(SOCRobotBrain<?, ?, BP> br)
    {
        brain = br;
        playerTrackers = brain.getPlayerTrackers();
        ourPlayerTracker = brain.getOurPlayerTracker();
        ourPlayerData = brain.getOurPlayerData();
        game = brain.getGame();

        resetTargetPieces();
    }

    /**
     * reset target pieces for all players
     */
    public abstract void resetTargetPieces();

    /**
     * set a target piece for a player
     *
     * @param pn  the player number
     * @param buildPlan  the current build plan
     */
    public abstract void setTargetPiece(int pn, BP buildPlan); //SOCPossiblePiece piece)

    /**
     * Forget all trade offers made.
     */
    public abstract void resetOffersMade();

    /**
     * Remembers an offer.
     *
     * @param offer  the offer
     */
    public abstract void addToOffersMade(SOCTradeOffer offer);

    /**
     * reset the isSellingResource array so that
     * if the player has the resource, then he is selling it
     */
    protected abstract void resetIsSelling();
    
    /**
     * mark a player as not selling a resource
     *
     * @param pn         the number of the player
     * @param rsrcType   the type of resource
     */
    protected abstract void markAsNotSelling(int pn, int rsrcType);

    /**
     * mark a player as willing to sell a resource
     *
     * @param pn         the number of the player
     * @param rsrcType   the type of resource
     */
    protected abstract void markAsSelling(int pn, int rsrcType);
    
    /**
     * Forget the trades we made.
     */
    public abstract void resetTradesMade();

    /***
     * make an offer to another player
     *
     * @param buildPlan  our build plan
     * @return the offer we want to make, or null for no offer
     */
    public abstract SOCTradeOffer makeOffer(BP buildPlan);

    /**
     * @return a counter offer or null
     *
     * @param originalOffer  the offer given to us
     */
    public abstract SOCTradeOffer makeCounterOffer(SOCTradeOffer originalOffer);

    /**
     * @return the offer that we'll make to the bank/ports
     *
     * @param targetResources  what resources we want
     * @param ourResources     the resources we have
     */
    public abstract SOCTradeOffer getOfferToBank(BP buildPlan, SOCResourceSet ourResources);


    /**
     * consider an offer made by another player
     *
     * @param offer  the offer to consider
     * @param receiverNum  the player number of the receiver
     *
     * @return if we want to accept, reject, or make a counter offer
     */
    public abstract int considerOffer(SOCTradeOffer offer, int receiverNum);

  
    /**
     * Determine whether to make a counter for a given partial offer
     *  Set the appropraite variables, return the status of the offer
     * @param offer
     * @return
     */
    protected abstract int handlePartialOffer(SOCTradeOffer offer);
    
    /**
     * reset the wantsAnotherOffer array to all false
     */
    public abstract void resetWantsAnotherOffer();
    
    /**
     * mark a player as not wanting another offer
     *
     * @param pn         the number of the player
     * @param rsrcType   the type of resource
     */
    public abstract void markAsNotWantingAnotherOffer(int pn, int rsrcType);
    
    /**
     * mark a player as wanting another offer
     *
     * @param pn         the number of the player
     * @param rsrcType   the type of resource
     */
    public abstract void markAsWantsAnotherOffer(int pn, int rsrcType);
    
    /**
     * @return true if the player is marked as wanting a better offer
     *
     * @param pn         the number of the player
     * @param rsrcType   the type of resource
     */
    public abstract boolean wantsAnotherOffer(int pn, int rsrcType);
    
    /**
     * @param offer the new best completed offer
     */
    public abstract void setBestCompletedOffer(SOCTradeOffer offer);
    
    /**
     * @return the best completed offer (either directly or from memory)
     */
    public abstract SOCTradeOffer getBestCompletedOffer();

///methods used by all implementations///
    /**
     * @return the offer that we'll make to the bank/ports
     *
     * @param targetResources  what resources we want
     */
    public SOCTradeOffer getOfferToBank(BP buildPlan) //SOCResourceSet targetResources)
    {
        return getOfferToBank(buildPlan, ourPlayerData.getResources());
    }
    
    /**
     * Updates in order to point to the new/correct fields updated in the brain
     */
    public void update(){
        playerTrackers = brain.getPlayerTrackers();
        ourPlayerTracker = brain.getOurPlayerTracker();
        ourPlayerData = brain.getOurPlayerData();
        game = brain.getGame();
    }

///logic recording isSelling or wantingAnotherOffer based on responses: Accept, Reject or no response///
    /**
     * Marks what a player wants or is not selling based on the received offer.
     * @param offer the offer we have received
     */
    protected void recordResourcesFromOffer(SOCTradeOffer offer){
        ///
        /// record that this player wants to sell me the stuff
        ///
        SOCResourceSet giveSet = offer.getGiveSet();

        for (int rsrcType = SOCResourceConstants.CLAY;
                rsrcType <= SOCResourceConstants.WOOD;
                rsrcType++)
        {
            if (giveSet.getAmount(rsrcType) > 0)
            {
                D.ebugPrintlnINFO("%%% player " + offer.getFrom() + " wants to sell " + rsrcType);
                markAsWantsAnotherOffer(offer.getFrom(), rsrcType);
            }
        }

        ///
        /// record that this player is not selling the resources 
        /// he is asking for
        ///
        SOCResourceSet getSet = offer.getGetSet();

        for (int rsrcType = SOCResourceConstants.CLAY;
                rsrcType <= SOCResourceConstants.WOOD;
                rsrcType++)
        {
            if (getSet.getAmount(rsrcType) > 0)
            {
                D.ebugPrintlnINFO("%%% player " + offer.getFrom() + " wants to buy " + rsrcType + " and therefore does not want to sell it");
                markAsNotSelling(offer.getFrom(), rsrcType);
            }
        }
    }
    
    /**
     * Marks what resources a player is not selling based on a reject to our offer
     * @param rejector the player number corresponding to the player who has rejected an offer
     */
    protected void recordResourcesFromReject(int rejector){
        ///
        /// see if everyone has rejected our offer
        ///
    	if (brain.waitingForTradeResponse) { 
            // If this is false, it means the rejected trade was accepted by another player.
            //  Since it has been cleared from the data object, it unfortunately cannot be
            //  passed to the negotiator.
            //  TODO: Rework so that we have access to this?
            if (ourPlayerData.getCurrentOffer() != null) 
            {
                D.ebugPrintlnINFO("%%%%%%%%% REJECT OFFER %%%%%%%%%%%%%");
    
                ///
                /// record which player said no
                ///
                SOCResourceSet getSet = ourPlayerData.getCurrentOffer().getGetSet();
    
                for (int rsrcType = SOCResourceConstants.CLAY;
                        rsrcType <= SOCResourceConstants.WOOD;
                        rsrcType++)
                {
                    if ((getSet.getAmount(rsrcType) > 0) && (!wantsAnotherOffer(rejector, rsrcType)))
                        markAsNotSelling(rejector, rsrcType);
                }
            }       
        }
    }
    
    /**
     * Marks what resources a player is not selling based on a reject to other offers
     * @param rejector the player number corresponding to the player who has rejected an offer
     */
    protected void recordResourcesFromRejectAlt(int rejector){
    	///
    	/// we also want to watch rejections of other players' offers
        ///
        D.ebugPrintlnINFO("%%%% ALT REJECT OFFER %%%%");

        for (int pn = 0; pn < game.maxPlayers; pn++)
        {
        	SOCTradeOffer offer = game.getPlayer(pn).getCurrentOffer();

        	if (offer != null)
            {
        		boolean[] offeredTo = offer.getTo();

                if (offeredTo[rejector])
                {
                	//
                    // I think they were rejecting this offer
                    // mark them as not selling what was asked for
                    //
                    SOCResourceSet getSet = offer.getGetSet();

                    for (int rsrcType = SOCResourceConstants.CLAY;
                    		rsrcType <= SOCResourceConstants.WOOD;
                            rsrcType++)
                    {
                    	if ((getSet.getAmount(rsrcType) > 0) && (!wantsAnotherOffer(rejector, rsrcType)))
                    		markAsNotSelling(rejector, rsrcType);
                    }
                }
            }
        }
    }
    
    /**
     * This is called when players haven't responded to our offer, so we assume they are not selling and that they don't want anything else
     * Marks the resources we offered as not selling and marks that the player doesn't want a different offer for that resource
     * @param ourCurrentOffer the offer we made and not received an answer to
     */
    protected void recordResourcesFromNoResponse(SOCTradeOffer ourCurrentOffer){
    	boolean[] offeredTo = ourCurrentOffer.getTo();
        SOCResourceSet getSet = ourCurrentOffer.getGetSet();

        for (int rsrcType = SOCResourceConstants.CLAY;
                rsrcType <= SOCResourceConstants.WOOD;
                rsrcType++)
        {
            if (getSet.getAmount(rsrcType) > 0)
            {
                for (int pn = 0; pn < game.maxPlayers; pn++)
                {
                    if (offeredTo[pn])
                    {
                        markAsNotSelling(pn, rsrcType);
                        markAsNotWantingAnotherOffer(pn, rsrcType);
                    }
                }
            }
        }
    }
}
