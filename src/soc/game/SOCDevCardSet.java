/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2007,2009 Jeremy D. Monin <jeremy@nand.net>
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
package soc.game;

import java.io.Serializable;


/**
 * This represents a collection of development cards
 */
public class SOCDevCardSet implements Serializable, Cloneable
{
    /**
     * age constants. OLD == 0, NEW == 1 (guaranteed for use in loops).
     */
    public static final int OLD = 0;
    public static final int NEW = 1;

    /**
     * the number of development cards of each type.
     * [{@link #OLD}] are the old cards.
     * [{@link #NEW}] are recently bought cards.
     * Card types as in {@link SOCDevCardConstants}.
     */
    private int[][] devCards;

    /**
     * Make an empty development card set
     */
    public SOCDevCardSet()
    {
        devCards = new int[2][SOCDevCardConstants.MAXPLUSONE];
        clear();
    }

    /**
     * Make a copy of a dev card set
     *
     * @param set  the dev card set to copy
     */
    public SOCDevCardSet(SOCDevCardSet set)
    {
        devCards = new int[2][SOCDevCardConstants.MAXPLUSONE];

        for (int i = SOCDevCardConstants.MIN;
                i < SOCDevCardConstants.MAXPLUSONE; i++)
        {
            devCards[OLD][i] = set.devCards[OLD][i];
            devCards[NEW][i] = set.devCards[NEW][i];
        }
    }

    /**
     * Create the Dev card set object
     * @param set  the dev card set to copy
     */
    public SOCDevCardSet(int[][] set)
    {
        devCards = new int[2][SOCDevCardConstants.MAXPLUSONE];

        for (int i = SOCDevCardConstants.MIN;
                i < SOCDevCardConstants.MAXPLUSONE; i++)
        {
            devCards[OLD][i] = set[OLD][i];
            devCards[NEW][i] = set[NEW][i];
        }
    }
    
    
    /**
     * set the number of old and new dev cards to zero
     */
    public void clear()
    {
        for (int i = SOCDevCardConstants.MIN;
                i < SOCDevCardConstants.MAXPLUSONE; i++)
        {
            devCards[OLD][i] = 0;
            devCards[NEW][i] = 0;
        }
    }

    /**
     * @return the number of a kind of development card
     *
     * @param age  either {@link #OLD} or {@link #NEW}
     * @param ctype
     *        the type of development card as described
     *        in {@link SOCDevCardConstants};
     *        at least {@link SOCDevCardConstants#MIN}
     *        and less than {@link SOCDevCardConstants#MAXPLUSONE}
     */
    public int getAmount(int age, int ctype)
    {
        return devCards[age][ctype];
    }

    /**
     * @return the total number of development cards
     */
    public int getTotal()
    {
        int sum = 0;

        for (int i = SOCDevCardConstants.MIN;
                i < SOCDevCardConstants.MAXPLUSONE; i++)
        {
            sum += (devCards[OLD][i] + devCards[NEW][i]);
        }

        return sum;
    }

    /**
     * set the amount of a type of card
     *
     * @param age   either {@link #OLD} or {@link #NEW}
     * @param ctype the type of development card, at least
     *              {@link SOCDevCardConstants#MIN} and less than {@link SOCDevCardConstants#MAXPLUSONE}
     * @param amt   the amount
     */
    public void setAmount(int amt, int age, int ctype)
    {
        devCards[age][ctype] = amt;
    }

    /**
     * add an amount to a type of card
     *
     * @param age   either {@link #OLD} or {@link #NEW}
     * @param ctype the type of development card, at least
     *              {@link SOCDevCardConstants#MIN} and less than {@link SOCDevCardConstants#MAXPLUSONE}
     * @param amt   the amount
     */
    public void add(int amt, int age, int ctype)
    {
        devCards[age][ctype] += amt;
    }

    /**
     * add a set to the current one
     */
    public void add(SOCDevCardSet set)
    {
    	for(int age = 0; age < 2; age++)
    		for (int type = SOCDevCardConstants.MIN; type < SOCDevCardConstants.MAXPLUSONE; type++) {
    			devCards[age][type] += set.devCards[age][type];
    		}
    }
    
    /**
     * subtract an amount from a type of card
     *
     * @param age   either {@link #OLD} or {@link #NEW}
     * @param ctype the type of development card, at least
     *              {@link SOCDevCardConstants#MIN} and less than {@link SOCDevCardConstants#MAXPLUSONE}
     * @param amt   the amount
     */
    public void subtract(int amt, int age, int ctype)
    {
        if (amt <= devCards[age][ctype])
        {
            devCards[age][ctype] -= amt;
        }
        else
        {
            devCards[age][ctype] = 0;
            devCards[age][SOCDevCardConstants.UNKNOWN] -= amt;
        }
    }

    /**
     * @return the number of old cards, both victory point cards and playable cards
     *         this set
     */
    public int getNumOldCards()
    {
        int sum = 0;
        sum += devCards[OLD][SOCDevCardConstants.KNIGHT];
        sum += devCards[OLD][SOCDevCardConstants.ROADS];
        sum += devCards[OLD][SOCDevCardConstants.DISC];
        sum += devCards[OLD][SOCDevCardConstants.MONO];
        sum += devCards[OLD][SOCDevCardConstants.CAP];
        sum += devCards[OLD][SOCDevCardConstants.LIB];
        sum += devCards[OLD][SOCDevCardConstants.UNIV];
        sum += devCards[OLD][SOCDevCardConstants.TEMP];
        sum += devCards[OLD][SOCDevCardConstants.TOW];
        sum += devCards[OLD][SOCDevCardConstants.UNKNOWN];
        return sum;
    }
    
    /**
     * @return the number of new cards, both victory point cards and playable cards
     *         this set
     */
    public int getNumNewCards()
    {
        int sum = 0;
        sum += devCards[NEW][SOCDevCardConstants.CAP];
        sum += devCards[NEW][SOCDevCardConstants.LIB];
        sum += devCards[NEW][SOCDevCardConstants.UNIV];
        sum += devCards[NEW][SOCDevCardConstants.TEMP];
        sum += devCards[NEW][SOCDevCardConstants.TOW];
        sum += devCards[NEW][SOCDevCardConstants.KNIGHT];
        sum += devCards[NEW][SOCDevCardConstants.ROADS];
        sum += devCards[NEW][SOCDevCardConstants.DISC];
        sum += devCards[NEW][SOCDevCardConstants.MONO];
        sum += devCards[NEW][SOCDevCardConstants.UNKNOWN];
        return sum;
    }
    
    /**
     * @return the number of victory point cards in
     *         this set
     */
    public int getNumVPCards()
    {
        int sum = 0;

        sum += devCards[OLD][SOCDevCardConstants.CAP];
        sum += devCards[OLD][SOCDevCardConstants.LIB];
        sum += devCards[OLD][SOCDevCardConstants.UNIV];
        sum += devCards[OLD][SOCDevCardConstants.TEMP];
        sum += devCards[OLD][SOCDevCardConstants.TOW];
        sum += devCards[NEW][SOCDevCardConstants.CAP];
        sum += devCards[NEW][SOCDevCardConstants.LIB];
        sum += devCards[NEW][SOCDevCardConstants.UNIV];
        sum += devCards[NEW][SOCDevCardConstants.TEMP];
        sum += devCards[NEW][SOCDevCardConstants.TOW];

        return sum;
    }
    
    /**
     * Some card types stay in your hand after being played.
     * Count only the unplayed ones (old or new). 
     * 
     * @return the number of unplayed cards in this set
     */
    public int getNumUnplayed()
    {
        int sum = 0;

        sum += devCards[OLD][SOCDevCardConstants.KNIGHT];
        sum += devCards[OLD][SOCDevCardConstants.ROADS];
        sum += devCards[OLD][SOCDevCardConstants.DISC];
        sum += devCards[OLD][SOCDevCardConstants.MONO];
        sum += devCards[OLD][SOCDevCardConstants.UNKNOWN];
        sum += devCards[NEW][SOCDevCardConstants.KNIGHT];
        sum += devCards[NEW][SOCDevCardConstants.ROADS];
        sum += devCards[NEW][SOCDevCardConstants.DISC];
        sum += devCards[NEW][SOCDevCardConstants.MONO];
        sum += devCards[NEW][SOCDevCardConstants.UNKNOWN];
        
        return sum;
    }

    public int getNumberOfKnightDevelopmentCards() {
        return getNumberOfCards(SOCDevCardConstants.KNIGHT);
    }

    public int getNumberOfRoadDevelopmentCards() {
        return getNumberOfCards(SOCDevCardConstants.ROADS);
    }

    public int getNumberOfDiscoveryDevelopmentCards() {
        return getNumberOfCards(SOCDevCardConstants.DISC);
    }

    public int getNumberOfMonopolyDevelopmentCards() {
        return getNumberOfCards(SOCDevCardConstants.MONO);
    }

    public int getNumberOfUnknownDevelopmentCards() {
        return getNumberOfCards(SOCDevCardConstants.UNKNOWN);
    }

    private int getNumberOfCards(int type) {
        return devCards[OLD][type] + devCards[NEW][type];
    }

    /**
     * change all the new cards to old ones
     */
    public void newToOld()
    {
        for (int i = SOCDevCardConstants.MIN;
                i < SOCDevCardConstants.MAXPLUSONE; i++)
        {
            devCards[OLD][i] += devCards[NEW][i];
            devCards[NEW][i] = 0;
        }
    }
    
    public String toString() {
        String ret = "OLD:";
        for (int i = SOCDevCardConstants.MIN; i < SOCDevCardConstants.MAXPLUSONE; i++) {
            ret += devCards[OLD][i] + ",";
        }
        ret = ret.substring(0, ret.length()-1);
        ret += "|NEW:";
        for (int i = SOCDevCardConstants.MIN; i < SOCDevCardConstants.MAXPLUSONE; i++) {
            ret += devCards[NEW][i] + ",";
        }
        ret = ret.substring(0, ret.length()-1);
        return ret;
    }
    
    /**
     * NOTE: all vp cards are turned to capitols for simplicity
     * @return
     */
    public int[][] getDevCardArrayClone(){
    	int[][] cards = new int[2][SOCDevCardConstants.CAP+1];

        for (int i = SOCDevCardConstants.MIN;
                i < SOCDevCardConstants.MAXPLUSONE; i++)
        {
        	if(i <= SOCDevCardConstants.CAP){
        		cards[OLD][i] = devCards[OLD][i];
        		cards[NEW][i] = devCards[NEW][i];
        	}else{
        		cards[OLD][SOCDevCardConstants.CAP] = cards[OLD][SOCDevCardConstants.CAP] + devCards[OLD][i];
        		cards[NEW][SOCDevCardConstants.CAP] = cards[NEW][SOCDevCardConstants.CAP] + devCards[NEW][i];
        	}
        }
    	
    	return cards;
    }
    
    public int[][] getDevCardClone(){
    	return devCards.clone();
    }
    
}
