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
package soc.game;

import java.io.Serializable;
import soc.disableDebug.D;


/**
 * This class represents a trade offer in Settlers of Catan
 */
public class SOCTradeOffer implements Serializable, Cloneable
{
    public String game;
    public SOCResourceSet give;
    public SOCResourceSet get;
    public int from;
    public boolean[] to;

    /**
     * The constructor for a SOCTradeOffer
     *
     * @param  game  the name of the game in which this offer was made
     * @param  from  the number of the player making the offer
     * @param  to    a boolean array where 'true' means that the offer
     *               is being made to the player with the same number as
     *               the index of the 'true'
     * @param  give  the set of resources being given
     * @param  get   the set of resources being asked for
     */
    public SOCTradeOffer(String game, int from, boolean[] to, SOCResourceSet give, SOCResourceSet get)
    {
        this.game = game;
        this.from = from;
        this.to = to;
        this.give = give;
        this.get = get;
    }

    /**
     * make a copy of this offer
     *
     * @param offer   the trade offer to copy
     */
    public SOCTradeOffer(SOCTradeOffer offer)
    {
        game = offer.game;
        from = offer.from;
        final int maxPlayers = offer.to.length;
        to = new boolean[maxPlayers];

        for (int i = 0; i < maxPlayers; i++)
        {
            to[i] = offer.to[i];
        }

        give = offer.give.copy();
        get = offer.get.copy();
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the number of the player that made the offer
     */
    public int getFrom()
    {
        return from;
    }

    /**
     * @return the boolean array representing to whom this offer was made
     */
    public boolean[] getTo()
    {
        return to;
    }

    /**
     * @return the set of resources offered
     */
    public SOCResourceSet getGiveSet()
    {
        return give;
    }

    /**
     * @return the set of resources wanted in exchange
     */
    public SOCResourceSet getGetSet()
    {
        return get;
    }

    /**
     * @return a human readable string of data
     */
    @Override
    public String toString()
    {
        String str = "game=" + game + "|from=" + from + "|to=" + to[0];

        for (int i = 1; i < to.length; i++)
        {
            str += ("," + to[i]);
        }

        str += ("|give=" + give + "|get=" + get);

        return str;
    }
    
    // Note: Trade offer message has something like this, but that logic should have been here
    public static SOCTradeOffer parse(String s) {    	
        //game=Practice 0|from=0|to=false,false,true,true|give=clay=0|ore=0|sheep=1|wheat=0|wood=0|unknown=0|get=clay=0|ore=0|sheep=0|wheat=1|wood=0|unknown=0
        String pieces[] = s.split("\\|");
        String game = pieces[0].split("=")[1];
        int from = Integer.parseInt(pieces[1].split("=")[1]);
        boolean to[] = new boolean[4];
        String pt[] = pieces[2].split("=")[1].split(",");
        for (int i=0; i<4; i++) {
            to[i] = Boolean.parseBoolean(pt[i]);
        }
        
       
        SOCResourceSet giveSet = new SOCResourceSet();
        // special handling of clay due to give=clay=n
        int rc = Integer.parseInt(pieces[3].split("=")[2]);
        giveSet.add(rc,1);
        for (int i=1; i<6; i++) {
            rc = Integer.parseInt(pieces[3+i].split("=")[1]);
            giveSet.add(rc, i+1);
        }
        
        //guard against attempts to parse a StacTradeOffer
        if (!pieces[9].startsWith("give=clay=")) {
            if (pieces[9].startsWith("disj=")) {
                D.ebugERROR("Parsing get set in a trade offer. This seems to be a StacTradeOffer - please amend your code to call StacTradeOffer.parse()!");
            } else {
                D.ebugERROR("Parsing get set in a trade offer. Don't know what is wrong with this string!");
            }
        }
        // special handling of clay due to give=clay=n
        rc = Integer.parseInt(pieces[9].split("=")[2]);
        SOCResourceSet getSet = new SOCResourceSet();
        getSet.add(rc,1);
        for (int i=1; i<6; i++) {
            rc = Integer.parseInt(pieces[9+i].split("=")[1]);
            getSet.add(rc, i+1);
        }
        
        return new SOCTradeOffer(game, from, to, giveSet, getSet);
    }
    
    @Override
    public boolean equals(Object o) {
    	if (o instanceof SOCTradeOffer) {
    		SOCTradeOffer offer = (SOCTradeOffer) o;
    		for (int i=0; i<to.length; i++) {
    			if (to[i]!=offer.to[i]) {
    				return false;
    			}
    		}
    		return (from == offer.from
    				&& give.equals(offer.give)
    				&& get.equals(offer.get));
    	}
    	else {
    		return false;
    	}
    }
    
    /**
     * Create a new SOCTradeOffer where 'to' is the original 'from' and give and get sets are exchanged.
     * @param newFrom       the number of the new offering player
     * @return              the inverted offer
     */
    public SOCTradeOffer invertedOffer(int newFrom) {
        boolean[] newTo = new boolean[to.length];
        newTo[from] = true;
        SOCTradeOffer invertedOffer = new SOCTradeOffer(game, newFrom, newTo, get, give);
        return invertedOffer;
    }

}
