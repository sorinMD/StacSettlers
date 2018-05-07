package soc.server.database;

import soc.game.SOCResourceSet;
import soc.game.SOCTradeOffer;

/**
 * Class to allow tracking of the distribution of offers-per-turn.
 * Note that while this seems to track offers that happen outside of this player's turn (ie counters and completions),
 *  this is not the case since the actual tracker, numTurnsWithTrades, is only updated when 
 *  endTurn is called.  Temporary variables are reset when startTurn is called.  Assuming
 *  these are appropriately called when the player's turn is actually starting and ending,
 *  the results should be valid.
 * @author KHO
 *
 */
public class SOCPlayerOfferStats {

    private int[] numTurnsWithTrades = new int[10];
    private int curTurnTrades;
    private int numTurns;

    private int numTurnInitialOffers = 0; //number of turn-initial offers, same as turns with at least one offer
    private int numTradesMatchingTurnInitialOffer = 0; //number of trades matching the first offer the player made in the turn
    private int numTradesNotMatchingTurnInitialOffer = 0; //number of trades not matching the first offer the player made in the turn
    private int numTradesWithoutTurnInitialOffer = 0; //number of trades without a recorded initial trade in this turn
    private int numTradesWithBankAfterTurnInitialOffer = 0; //number of trades with the bank after an offer to a player had been made
    private int numTradesWithPortAfterTurnInitialOffer = 0; //number of trades with a port after an offer to a player had been made
    private int numTradesWithBankWithoutTurnInitialOffer = 0; //number of trades with the bank without an earlier offer to a player
    private int numTradesWithPortWithoutTurnInitialOffer = 0; //number of trades with a port without an earlier offer to a player
    
    /**
     * The first offer that has been made in this turn.
     */
    SOCTradeOffer turnInitialOffer;
    
    /**
     * Notify the class that our turn is starting.  This increments the turn counter and resets
     *  temporary offer counter variable.
     */
    public void startTurn() {
        numTurns++;
        curTurnTrades = 0;
        turnInitialOffer = null;
    }
    
    public void addOffer() {
        curTurnTrades++;
    }

    /**
     * Record the initial offer in this turn. This should only be set once per turn for ALL players.
     * @param offer 
     */
    public void recordTurnInitialOffer(SOCTradeOffer offer) {
        turnInitialOffer = new SOCTradeOffer(offer);
        numTurnInitialOffers++;
    }

    public SOCTradeOffer getTurnInitialOffer() {
        return turnInitialOffer;
    }

    /**
     * Return the number of trades for this player that match the turn-initial offers.
     * @return 
     */
    public int getNumTradesMatchingTurnInitialOffer() {
        return numTradesMatchingTurnInitialOffer;
    }

    /**
     * Return the number of trades for this player without a previously recorded turn-initial offer.
     * @return 
     */
    public int getNumTradesWithoutTurnInitialOffer() {
        return numTradesWithoutTurnInitialOffer;
    }
    
    /**
     * Return the number of trades for this player that do not match the turn-initial offers.
     * @return 
     */
    public int getNumTradesNotMatchingTurnInitialOffer() {
        return numTradesNotMatchingTurnInitialOffer;
    }
    
    /**
     * Return the number of trades this player has made with the bank 
     * after previously having made an offer to another player in this turn.
     * @return 
     */
    public int getNumTradesWithBankAfterTurnInitialOffer() {
        return numTradesWithBankAfterTurnInitialOffer;
    }
    
    /**
     * Return the number of trades this player has made with the port 
     * after previously having made an offer to another player in this turn.
     * @return 
     */
    public int getNumTradesWithPortAfterTurnInitialOffer() {
        return numTradesWithPortAfterTurnInitialOffer;
    }

    /**
     * Return the number of trades this player has made with the bank 
     * without any prior offer to another player in this turn.
     * @return 
     */
    public int getNumTradesWithBankWithoutTurnInitialOffer() {
        return numTradesWithBankWithoutTurnInitialOffer;
    }
    
    /**
     * Return the number of trades this player has made with the port 
     * without any prior offer to another player in this turn.
     * @return 
     */
    public int getNumTradesWithPortWithoutTurnInitialOffer() {
        return numTradesWithPortWithoutTurnInitialOffer;
    }

    /**
     * Notify the class that our turn is ending.  This stores the actual result.
     */
    public void endTurn() {
        if (curTurnTrades >= numTurnsWithTrades.length) {
            curTurnTrades = numTurnsWithTrades.length - 1;
        }
        numTurnsWithTrades[curTurnTrades] ++;     
        curTurnTrades = 0;
    }
    
    /**
     * Get the probability, for each number of offers, that the player makes that many offers
     *  in a given turn.  The last entry is a catch-all for all high-offer turns.  
     * @return
     */
    public double[] getProbs() {
        double[] ret = new double[numTurnsWithTrades.length];
        for (int i=0; i<numTurnsWithTrades.length; i++) {
            ret[i] = (double) numTurnsWithTrades[i] / (double) numTurns;
        }
        return ret;
    }

    /**
     * Get the number of turn-initial offers for this player.
     * @return an int with the number of turn-initial offers
     */
    public int getNumTurnInitialOffers() {
        return numTurnInitialOffers;
    }

    public void evaluateAcceptedOffer(SOCTradeOffer acceptedOffer) {
        if (turnInitialOffer == null) {
            numTradesWithoutTurnInitialOffer++;
            return;
        }
        
        //check whether the initial offer was a partial offer (either giveSet or getSet are empty)
        //and only check whether the resources that were specified match the acceptedOffer
        if (turnInitialOffer.getGetSet().getTotal() == 0 || turnInitialOffer.getGiveSet().getTotal() == 0) {
            //but first check whether the direction of the accepted offer is the same as the turn initial offer,
            //i.e. check whether offerer and accepter have changed roles, which can happen for partial offers,
            //because the player the offer is being made to completes the initial partial offer
            int proposerOfTurnInitialOffer = turnInitialOffer.getFrom();
            int proposerOfAcceptedOffer = acceptedOffer.getFrom();
            boolean[] addressesOfAcceptedOffer = acceptedOffer.getTo();
            if (proposerOfAcceptedOffer != proposerOfTurnInitialOffer) { //(addressesOfAcceptedOffer[proposerOfTurnInitialOffer]) {
                //TODO: This occurs only rarely, but here the give and get sets of one of the offers should be exchanged 
                //(after cheking that the trade is acceteped by one of the players it was initially made to)
                //System.err.println("propsers are different!");
            }
            if (turnInitialOffer.getGetSet().getTotal() == 0) {
                if (turnInitialOffer.getGiveSet().contains(acceptedOffer.getGiveSet())) {
                    numTradesMatchingTurnInitialOffer++;
                } else {
                    numTradesNotMatchingTurnInitialOffer++;
                }
            } else if (turnInitialOffer.getGiveSet().getTotal() == 0) {
                if (acceptedOffer.getGetSet().contains(turnInitialOffer.getGetSet())) {
                    numTradesMatchingTurnInitialOffer++;
                } else {
                    numTradesNotMatchingTurnInitialOffer++;
                }
            }
        }

        //the initial offer was a complete offer
        if (turnInitialOffer.equals(acceptedOffer)) {
            numTradesMatchingTurnInitialOffer++;
        } else {
            numTradesNotMatchingTurnInitialOffer++;
        }
    }
    
    public void evaluateBankTrade(SOCResourceSet getSet, SOCResourceSet giveSet) {
        final int giveTotal = giveSet.getTotal();
        final int getTotal = getSet.getTotal();
        if (turnInitialOffer != null) {
            if ((giveTotal / getTotal) == 4) { //4:1 trade
                numTradesWithBankAfterTurnInitialOffer++;
            } else {  // 3:1 or 2:1 trade
                numTradesWithPortAfterTurnInitialOffer++;
            }
        } else {
            if ((giveTotal / getTotal) == 4) { //4:1 trade
                numTradesWithBankWithoutTurnInitialOffer++;
            } else {  // 3:1 or 2:1 trade
                numTradesWithPortWithoutTurnInitialOffer++;
            }            
        }
    }
}
