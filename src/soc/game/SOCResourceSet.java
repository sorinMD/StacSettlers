/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2008-2009 Jeremy D Monin <jeremy@nand.net>
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
 * This represents a collection of
 * clay, ore, sheep, wheat, and wood resources.
 * Unknown resources are also tracked here.
 * Although it's possible to store negative amounts of resources, it's discouraged.
 *
 * @see SOCResourceConstants
 * @see SOCPlayingPiece#getResourcesToBuild(int)
 */
public class SOCResourceSet implements Serializable, Cloneable
{
    /** Resource set with zero of each resource type */
    public static final SOCResourceSet EMPTY_SET = new SOCResourceSet();
    
    /**
     * the number of resources
     */
    protected int[] resources;
    
    /**
     * Make an empty resource set
     */
    public SOCResourceSet()
    {
        resources = new int[SOCResourceConstants.MAXPLUSONE];
        clear();
    }
    
    /**
     * @return a clone of the resource array
     */
    public int[] getRssArrayClone(){
    	return resources.clone();
    }
    /**
     * Make a resource set with stuff in it
     *
     * @param cl  number of clay resources
     * @param or  number of ore resources
     * @param sh  number of sheep resources
     * @param wh  number of wheat resources
     * @param wo  number of wood resources
     * @param uk  number of unknown resources
     */
    public SOCResourceSet(int cl, int or, int sh, int wh, int wo, int uk)
    {
        resources = new int[SOCResourceConstants.MAXPLUSONE];

        resources[SOCResourceConstants.CLAY]    = cl;
        resources[SOCResourceConstants.ORE]     = or;
        resources[SOCResourceConstants.SHEEP]   = sh;
        resources[SOCResourceConstants.WHEAT]   = wh;
        resources[SOCResourceConstants.WOOD]    = wo;
        resources[SOCResourceConstants.UNKNOWN] = uk;
    }

    /**
     * Make a resource set from an array
     *
     * @param rset resource set, of length 5 or 6 (clay, ore, sheep, wheat, wood, unknown).
     *     If length is 5, unknown == 0.
     * @since 1.1.08
     */
    public SOCResourceSet(int[] rset)
    {
        this(rset[0], rset[1], rset[2], rset[3], rset[4], (rset.length >= 6) ? rset[5] : 0);
    }

    /**
     * set the number of resources to zero
     */
    public void clear()
    {
        for (int i = SOCResourceConstants.MIN;
                i < SOCResourceConstants.MAXPLUSONE; i++)
        {
            resources[i] = 0;
        }
    }

    /**
     * @return the number of a kind of resource
     *
     * @param rtype  the type of resource, like {@link SOCResourceConstants#CLAY}
     */
    public int getAmount(int rtype)
    {
        return resources[rtype];
    }

    /**
     * @return the total number of resources
     */
    public int getTotal()
    {        
        return resources[SOCResourceConstants.CLAY] + resources[SOCResourceConstants.ORE] + resources[SOCResourceConstants.SHEEP] + 
                resources[SOCResourceConstants.WHEAT] + resources[SOCResourceConstants.WOOD] + resources[SOCResourceConstants.UNKNOWN];
    }

    /**
     * set the amount of a resource
     *
     * @param rtype the type of resource, like {@link SOCResourceConstants#CLAY}
     * @param amt   the amount
     */
    public void setAmount(int amt, int rtype)
    {
        resources[rtype] = amt;
    }

    /**
     * add an amount to a resource
     *
     * @param rtype the type of resource, like {@link SOCResourceConstants#CLAY}
     * @param amt   the amount; if below 0 (thus subtracting resources),
     *              the subtraction occurs and no special action is taken.
     *              {@link #subtract(int, int)} takes special action in some cases.
     */
    public void add(int amt, int rtype)
    {
        resources[rtype] += amt;
    }

    /**
     * subtract an amount from a resource.
     * If we're subtracting more from a resource than there are of that resource,
     * set that resource to zero, and then take the difference away from the
     * {@link SOCResourceConstants#UNKNOWN} resources.
     * As a result, UNKNOWN may be less than zero afterwards.
     * TODO: Investigate whether the resources.subtract functions can be made to act similarly.  It's a little iffy having two functions with different behaviour with the same name        
     *
     * @param rtype the type of resource, like {@link SOCResourceConstants#CLAY}
     * @param amt   the amount; unlike in {@link #add(int, int)}, any amount that
     *              takes the resource below 0 is treated specially.
     */
    public void subtract(int amt, int rtype)
    {
        /**
         * if we're subtracting more from a resource than
         * there are of that resource, set that resource
         * to zero, and then take the difference away
         * from the UNKNOWN resources
         */
        if (amt > resources[rtype])
        {
            resources[SOCResourceConstants.UNKNOWN] -= (amt - resources[rtype]);
            resources[rtype] = 0;
        }
        else
        {
            resources[rtype] -= amt;
        }

        if (resources[SOCResourceConstants.UNKNOWN] < 0)
        {
            // this was written to std-err before.  This may not be appropriate, as we could be using this for a simulation or estimation in which case unknown<0 could
            //  be valid (i.e. indicating the underlying estimate was wrong)
            D.ebugPrintlnINFO("RESOURCE < 0 : RESOURCE TYPE=" + rtype);
        }
    }

    /**
     * add an entire resource set's amounts into this set.
     *
     * @param rs  the resource set
     */
    public void add(SOCResourceSet rs)
    {
        resources[SOCResourceConstants.CLAY]    += rs.getAmount(SOCResourceConstants.CLAY);
        resources[SOCResourceConstants.ORE]     += rs.getAmount(SOCResourceConstants.ORE);
        resources[SOCResourceConstants.SHEEP]   += rs.getAmount(SOCResourceConstants.SHEEP);
        resources[SOCResourceConstants.WHEAT]   += rs.getAmount(SOCResourceConstants.WHEAT);
        resources[SOCResourceConstants.WOOD]    += rs.getAmount(SOCResourceConstants.WOOD);
        resources[SOCResourceConstants.UNKNOWN] += rs.getAmount(SOCResourceConstants.UNKNOWN);
    }

    /**
     * subtract an entire resource set. If any type's amount would go below 0, set it to 0.
     *
     * @param rs  the resource set
     */
    public void subtract(SOCResourceSet rs)
    {
        resources[SOCResourceConstants.CLAY] -= rs.getAmount(SOCResourceConstants.CLAY);

        if (resources[SOCResourceConstants.CLAY] < 0)
        {
            resources[SOCResourceConstants.CLAY] = 0;
        }

        resources[SOCResourceConstants.ORE] -= rs.getAmount(SOCResourceConstants.ORE);

        if (resources[SOCResourceConstants.ORE] < 0)
        {
            resources[SOCResourceConstants.ORE] = 0;
        }

        resources[SOCResourceConstants.SHEEP] -= rs.getAmount(SOCResourceConstants.SHEEP);

        if (resources[SOCResourceConstants.SHEEP] < 0)
        {
            resources[SOCResourceConstants.SHEEP] = 0;
        }

        resources[SOCResourceConstants.WHEAT] -= rs.getAmount(SOCResourceConstants.WHEAT);

        if (resources[SOCResourceConstants.WHEAT] < 0)
        {
            resources[SOCResourceConstants.WHEAT] = 0;
        }

        resources[SOCResourceConstants.WOOD] -= rs.getAmount(SOCResourceConstants.WOOD);

        if (resources[SOCResourceConstants.WOOD] < 0)
        {
            resources[SOCResourceConstants.WOOD] = 0;
        }

        resources[SOCResourceConstants.UNKNOWN] -= rs.getAmount(SOCResourceConstants.UNKNOWN);

        if (resources[SOCResourceConstants.UNKNOWN] < 0)
        {
            resources[SOCResourceConstants.UNKNOWN] = 0;
        }
    }

    /**
     * Convert all these resources to type {@link SOCResourceConstants#UNKNOWN}.
     * Information on amount of wood, wheat, etc is no longer available.
     * Equivalent to:
     * <code>
     *    int numTotal = resSet.getTotal();
     *    resSet.clear();
     *    resSet.setAmount (SOCResourceConstants.UNKNOWN, numTotal);
     * </code>
     */
    public void convertToUnknown()
    {
        int numTotal = getTotal();
        clear();
        resources[SOCResourceConstants.UNKNOWN] = numTotal;
    }

    /**
     * @return true if each resource type in set A is >= each resource type in set B
     *
     * @param a   set A
     * @param b   set B
     */
    static public boolean gte(SOCResourceSet a, SOCResourceSet b)
    {
        return (   (a.getAmount(SOCResourceConstants.CLAY)    >= b.getAmount(SOCResourceConstants.CLAY))
                && (a.getAmount(SOCResourceConstants.ORE)     >= b.getAmount(SOCResourceConstants.ORE))
                && (a.getAmount(SOCResourceConstants.SHEEP)   >= b.getAmount(SOCResourceConstants.SHEEP))
                && (a.getAmount(SOCResourceConstants.WHEAT)   >= b.getAmount(SOCResourceConstants.WHEAT))
                && (a.getAmount(SOCResourceConstants.WOOD)    >= b.getAmount(SOCResourceConstants.WOOD))
                && (a.getAmount(SOCResourceConstants.UNKNOWN) >= b.getAmount(SOCResourceConstants.UNKNOWN)));
    }

    /**
     * @return true if each resource type in set A is <= each resource type in set B
     *
     * @param a   set A
     * @param b   set B
     */
    static public boolean lte(SOCResourceSet a, SOCResourceSet b)
    {
        return (   (a.getAmount(SOCResourceConstants.CLAY)    <= b.getAmount(SOCResourceConstants.CLAY))
                && (a.getAmount(SOCResourceConstants.ORE)     <= b.getAmount(SOCResourceConstants.ORE))
                && (a.getAmount(SOCResourceConstants.SHEEP)   <= b.getAmount(SOCResourceConstants.SHEEP))
                && (a.getAmount(SOCResourceConstants.WHEAT)   <= b.getAmount(SOCResourceConstants.WHEAT))
                && (a.getAmount(SOCResourceConstants.WOOD)    <= b.getAmount(SOCResourceConstants.WOOD))
                && (a.getAmount(SOCResourceConstants.UNKNOWN) <= b.getAmount(SOCResourceConstants.UNKNOWN)));
    }

    /**
     * @return true if the specified resource type in set A is <= the same resource type in set B
     *
     * @param a   set A
     * @param b   set B
     */
    static public boolean lte(SOCResourceSet a, SOCResourceSet b, int rtype){
    	return (a.getAmount(rtype) <= b.getAmount(rtype));
    }
    
    /**
     * @return true if the specified resource type in set A is >= the same resource type in set B
     *
     * @param a   set A
     * @param b   set B
     */
    static public boolean gte(SOCResourceSet a, SOCResourceSet b, int rtype){
    	return (a.getAmount(rtype) >= b.getAmount(rtype));
    }
    
    
    /**
     * Human-readable form of the set, with format "clay=5|ore=1|sheep=0|wheat=0|wood=3|unknown=0"
     * @return a human readable longer form of the set
     * @see #toShortString()
     * @see #toFriendlyString()
     */
    @Override
    public String toString()
    {
        String s = "clay=" + resources[SOCResourceConstants.CLAY]
            + "|ore=" + resources[SOCResourceConstants.ORE]
            + "|sheep=" + resources[SOCResourceConstants.SHEEP]
            + "|wheat=" + resources[SOCResourceConstants.WHEAT]
            + "|wood=" + resources[SOCResourceConstants.WOOD]
            + "|unknown=" + resources[SOCResourceConstants.UNKNOWN];

        return s;
    }
    
    /**
     * Parse a toString representation of a resource set.
     * Note: no error checking here
     * @param s
     * @return
     */
    public static SOCResourceSet parse(String s) {
        int counts[] = new int[6];
        String p[] = s.split("\\|");
        for (int i=0; i<6; i++) {
            String pp[] = p[i].split("=");
            counts[i] = Integer.parseInt(pp[1]);
        }
        return new SOCResourceSet(counts);
    }

    /**
     * Human-readable form of the set, with format "Resources: 5 1 0 0 3 0".
     * Order of types is Clay, ore, sheep, wheat, wood, unknown.
     * @return a human readable short form of the set
     * @see #toFriendlyString()
     */
    public String toShortString()
    {
        String s = "Resources: " + resources[SOCResourceConstants.CLAY] + " "
            + resources[SOCResourceConstants.ORE] + " "
            + resources[SOCResourceConstants.SHEEP] + " "
            + resources[SOCResourceConstants.WHEAT] + " "
            + resources[SOCResourceConstants.WOOD] + " "
            + resources[SOCResourceConstants.UNKNOWN];

        return s;
    }

    /**
     * Intended for more condensed (easier to read) output while debugging.
     * @return 
     */
    public String toVeryShortString() {
        String s = resources[SOCResourceConstants.CLAY] + " "
            + resources[SOCResourceConstants.ORE] + " "
            + resources[SOCResourceConstants.SHEEP] + " "
            + resources[SOCResourceConstants.WHEAT] + " "
            + resources[SOCResourceConstants.WOOD] + " "
            + resources[SOCResourceConstants.UNKNOWN];

        return s;
    }
    
    /**
     * Human-readable form of the set, with format "5 clay,1 ore,3 wood"
     * @return a human readable longer form of the set;
     *         if the set is empty, return the string "nothing".
     * @see #toShortString()
     */
    public String toFriendlyString()
    {
        StringBuffer sb = new StringBuffer();
        if (toFriendlyString(sb)){
            String message = sb.toString();
            String[] messageWords = message.split(" ");
            sb = new StringBuffer();
            
            //Remove the number 1 as a quantifier of a resource 
            for (String word : messageWords){
            	if(!word.equals("1")){
            		if(sb.length()==0){
	            		sb.append(word);
	            	}
	            	else{
	            		sb.append(" " + word);
	            	}
            	}
            }
            message = sb.toString();
            
            try{
            	//Remove the last comma and replace it with a "and"
	            if(message.lastIndexOf(",") != -1){
	                message = (message.substring(0, message.lastIndexOf(","))+" and"+message.substring(message.lastIndexOf(",")+1, sb.length()));
	            }
            }
            catch (StringIndexOutOfBoundsException e){
            	System.out.println(e.getMessage());
            	System.out.println(message);
            }
            return message;
        }
        else{
            return "nothing";
        }
    }

    /**
     * Human-readable form of the set, with format "5 clay, 1 ore, 3 wood".
     * Unknown resources aren't mentioned.
     * @param sb Append into this buffer.
     * @return true if anything was appended, false if sb unchanged (this resource set is empty).
     * @see #toFriendlyString()
     */
    public boolean toFriendlyString(StringBuffer sb)
    {
        boolean needComma = false;  // Has a resource already been appended to sb?
        int amt;

        for (int res = SOCResourceConstants.CLAY; res <= SOCResourceConstants.WOOD; ++res)
        {
            amt = resources[res];
            if (amt == 0)
                continue;

            if (needComma)
                sb.append(", ");
            sb.append(amt);                
            sb.append(" ");
            sb.append(SOCResourceConstants.resName(res));
            needComma = true;
        }
        return needComma;  // Did we append anything?
    }

    /**
     * @return true if sub is in this set
     *
     * @param sub  the sub set
     */
    public boolean contains(SOCResourceSet sub)
    {
        return gte(this, sub);
    }

    /**
     * @return true if the argument is a SOCResourceSet containing the same amounts of each resource, including UNKNOWN
     *
     * @param anObject  the object in question
     */
    @Override
    public boolean equals(Object anObject)
    {
        if ((anObject instanceof SOCResourceSet)
                && (((SOCResourceSet) anObject).getAmount(SOCResourceConstants.CLAY)    == resources[SOCResourceConstants.CLAY])
                && (((SOCResourceSet) anObject).getAmount(SOCResourceConstants.ORE)     == resources[SOCResourceConstants.ORE])
                && (((SOCResourceSet) anObject).getAmount(SOCResourceConstants.SHEEP)   == resources[SOCResourceConstants.SHEEP])
                && (((SOCResourceSet) anObject).getAmount(SOCResourceConstants.WHEAT)   == resources[SOCResourceConstants.WHEAT])
                && (((SOCResourceSet) anObject).getAmount(SOCResourceConstants.WOOD)    == resources[SOCResourceConstants.WOOD])
                && (((SOCResourceSet) anObject).getAmount(SOCResourceConstants.UNKNOWN) == resources[SOCResourceConstants.UNKNOWN]))
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * Same as equals() but without checking the UNKNOWN resources.
     * @param resSet2   the other resource set with which to check this limited equality
     * @return          true if the sets are equal, not considering unknown resources
     */
    public boolean equalsWithoutUnknowns(SOCResourceSet resSet2) {
        return (((SOCResourceSet) resSet2).getAmount(SOCResourceConstants.CLAY)      == resources[SOCResourceConstants.CLAY])
                && (((SOCResourceSet) resSet2).getAmount(SOCResourceConstants.ORE)   == resources[SOCResourceConstants.ORE])
                && (((SOCResourceSet) resSet2).getAmount(SOCResourceConstants.SHEEP) == resources[SOCResourceConstants.SHEEP])
                && (((SOCResourceSet) resSet2).getAmount(SOCResourceConstants.WHEAT) == resources[SOCResourceConstants.WHEAT])
                && (((SOCResourceSet) resSet2).getAmount(SOCResourceConstants.WOOD)  == resources[SOCResourceConstants.WOOD]);
    }

    /**
     * Determine if the resources are disjoint from the ones in the specified set.
     * @param testSet   the set to be tested for disjointness
     * @return          true if the resource sets don't share any resource
     */
    public boolean disjoint(SOCResourceSet testSet) {
        boolean commonResource = 
                (resources[SOCResourceConstants.CLAY] > 0 && testSet.resources[SOCResourceConstants.CLAY] > 0) ||
                (resources[SOCResourceConstants.ORE] > 0 && testSet.resources[SOCResourceConstants.ORE] > 0) ||
                (resources[SOCResourceConstants.SHEEP] > 0 && testSet.resources[SOCResourceConstants.SHEEP] > 0) ||
                (resources[SOCResourceConstants.WHEAT] > 0 && testSet.resources[SOCResourceConstants.WHEAT] > 0) ||
                (resources[SOCResourceConstants.WOOD] > 0 && testSet.resources[SOCResourceConstants.WOOD] > 0) ||
                (resources[SOCResourceConstants.UNKNOWN] > 0 && testSet.resources[SOCResourceConstants.UNKNOWN] > 0);
        return !commonResource;
    }

    /**
     * Intersection of the two sets.
     * (Returned set contains the minimum of the two sets for each resource.)
     * @param testSet   the set with which to intersect
     * @return          a new SOCResourceSet with the intersection of resources
     */
    public SOCResourceSet intersect(SOCResourceSet testSet) {
        SOCResourceSet sharedResourceSet = new SOCResourceSet();
        sharedResourceSet.resources[SOCResourceConstants.CLAY] = resources[SOCResourceConstants.CLAY] < testSet.resources[SOCResourceConstants.CLAY] ? resources[SOCResourceConstants.CLAY] : testSet.resources[SOCResourceConstants.CLAY];
        sharedResourceSet.resources[SOCResourceConstants.ORE] = resources[SOCResourceConstants.ORE] < testSet.resources[SOCResourceConstants.ORE] ? resources[SOCResourceConstants.ORE] : testSet.resources[SOCResourceConstants.ORE];
        sharedResourceSet.resources[SOCResourceConstants.SHEEP] = resources[SOCResourceConstants.SHEEP] < testSet.resources[SOCResourceConstants.SHEEP] ? resources[SOCResourceConstants.SHEEP] : testSet.resources[SOCResourceConstants.SHEEP];
        sharedResourceSet.resources[SOCResourceConstants.WHEAT] = resources[SOCResourceConstants.WHEAT] < testSet.resources[SOCResourceConstants.WHEAT] ? resources[SOCResourceConstants.WHEAT] : testSet.resources[SOCResourceConstants.WHEAT];
        sharedResourceSet.resources[SOCResourceConstants.WOOD] = resources[SOCResourceConstants.WOOD] < testSet.resources[SOCResourceConstants.WOOD] ? resources[SOCResourceConstants.WOOD] : testSet.resources[SOCResourceConstants.WOOD];
        sharedResourceSet.resources[SOCResourceConstants.UNKNOWN] = resources[SOCResourceConstants.UNKNOWN] < testSet.resources[SOCResourceConstants.UNKNOWN] ? resources[SOCResourceConstants.UNKNOWN] : testSet.resources[SOCResourceConstants.UNKNOWN];
        return sharedResourceSet;
    }

    /**
     * Union of the two sets.
     * (Returned set contains the sum of the two sets for each resource.)
     * @param uniSet   the set with which to intersect
     * @return         a new SOCResourceSet with the intersection of resources
     */
    public SOCResourceSet union(SOCResourceSet uniSet) {
        SOCResourceSet unionSet = new SOCResourceSet();
        unionSet.resources[SOCResourceConstants.CLAY] = resources[SOCResourceConstants.CLAY] + uniSet.resources[SOCResourceConstants.CLAY];
        unionSet.resources[SOCResourceConstants.ORE] = resources[SOCResourceConstants.ORE] + uniSet.resources[SOCResourceConstants.ORE];
        unionSet.resources[SOCResourceConstants.SHEEP] = resources[SOCResourceConstants.SHEEP] + uniSet.resources[SOCResourceConstants.SHEEP];
        unionSet.resources[SOCResourceConstants.WHEAT] = resources[SOCResourceConstants.WHEAT] + uniSet.resources[SOCResourceConstants.WHEAT];
        unionSet.resources[SOCResourceConstants.WOOD] = resources[SOCResourceConstants.WOOD] + uniSet.resources[SOCResourceConstants.WOOD];
        unionSet.resources[SOCResourceConstants.UNKNOWN] = resources[SOCResourceConstants.UNKNOWN] + uniSet.resources[SOCResourceConstants.UNKNOWN];
        return unionSet;
    }
    
    /**
     * Test whether this is a subset of the specified res set. 
     * A subset is defined as: The amounts of each resource have to be either equal or zero in the subset.
     * <p> See {@link #contains} for subsets quantities less or equal those in the superset.
     * @param superset  the superset against which to test
     * @return          true if this is a subset in the above sense
     */
    public boolean isSubsetOf(SOCResourceSet superset) {
        return
            (resources[SOCResourceConstants.CLAY] == 0    || resources[SOCResourceConstants.CLAY] == superset.resources[SOCResourceConstants.CLAY]) &&
            (resources[SOCResourceConstants.ORE] == 0     || resources[SOCResourceConstants.ORE] == superset.resources[SOCResourceConstants.ORE]) &&
            (resources[SOCResourceConstants.SHEEP] == 0   || resources[SOCResourceConstants.SHEEP] == superset.resources[SOCResourceConstants.SHEEP]) &&
            (resources[SOCResourceConstants.WHEAT] == 0   || resources[SOCResourceConstants.WHEAT] == superset.resources[SOCResourceConstants.WHEAT]) &&
            (resources[SOCResourceConstants.WOOD] == 0    || resources[SOCResourceConstants.WOOD] == superset.resources[SOCResourceConstants.WOOD]) &&
            (resources[SOCResourceConstants.UNKNOWN] == 0 || resources[SOCResourceConstants.UNKNOWN] == superset.resources[SOCResourceConstants.UNKNOWN]);
    }

    /**
     * Test whether this is an 'optimistic' subset of the specified res set. 
     * A subset is defined as: The amounts of each resource have to be less or equal in the subset.
     * <p>
     * Optimistic means that the UNKNOWN resources of the superset are used up for the cases where a resource in the subset are greater than the ones in the superset.
     * @param superset  the superset against which to test
     * @return          true if this is a subset in the above sense
     */
    public boolean isOptimisticSubsetOf(SOCResourceSet superset) {
        int[] resDifference = new int[SOCResourceConstants.UNKNOWN];
        resDifference[SOCResourceConstants.CLAY] = resources[SOCResourceConstants.CLAY] - superset.resources[SOCResourceConstants.CLAY];
        resDifference[SOCResourceConstants.ORE] = resources[SOCResourceConstants.ORE] - superset.resources[SOCResourceConstants.ORE];
        resDifference[SOCResourceConstants.SHEEP] = resources[SOCResourceConstants.SHEEP] - superset.resources[SOCResourceConstants.SHEEP];
        resDifference[SOCResourceConstants.WHEAT] = resources[SOCResourceConstants.WHEAT] - superset.resources[SOCResourceConstants.WHEAT];
        resDifference[SOCResourceConstants.WOOD] = resources[SOCResourceConstants.WOOD] - superset.resources[SOCResourceConstants.WOOD];
        
        if (resDifference[SOCResourceConstants.CLAY] < 0) resDifference[SOCResourceConstants.CLAY] = 0;
        if (resDifference[SOCResourceConstants.ORE] < 0) resDifference[SOCResourceConstants.ORE] = 0;
        if (resDifference[SOCResourceConstants.SHEEP] < 0) resDifference[SOCResourceConstants.SHEEP] = 0;
        if (resDifference[SOCResourceConstants.WHEAT] > 0) resDifference[SOCResourceConstants.WHEAT] = 0;
        if (resDifference[SOCResourceConstants.WOOD] < 0) resDifference[SOCResourceConstants.WOOD] = 0;
        
        return (resDifference[SOCResourceConstants.CLAY] + resDifference[SOCResourceConstants.ORE] + resDifference[SOCResourceConstants.SHEEP] + 
                    resDifference[SOCResourceConstants.WHEAT] + resDifference[SOCResourceConstants.WOOD]) 
                <= superset.resources[SOCResourceConstants.UNKNOWN];
    }

    /**
     * Test whether this is an 'pessimistic' subset of the specified res set. 
     * A subset is defined as: The amounts of each resource have to be less or equal in the subset.
     * Pessimistic means that the UNKNOWN resources of the superset are *not* used, cf. {@link #isOptimisticSubsetOf}.
     * @param superset  the superset against which to test
     * @return          true if this is a subset in the above sense
     */
    public boolean isPessimisticSubsetOf(SOCResourceSet superset) {
        int[] resDifference = new int[SOCResourceConstants.UNKNOWN];
        resDifference[SOCResourceConstants.CLAY] = resources[SOCResourceConstants.CLAY] - superset.resources[SOCResourceConstants.CLAY];
        resDifference[SOCResourceConstants.ORE] = resources[SOCResourceConstants.ORE] - superset.resources[SOCResourceConstants.ORE];
        resDifference[SOCResourceConstants.SHEEP] = resources[SOCResourceConstants.SHEEP] - superset.resources[SOCResourceConstants.SHEEP];
        resDifference[SOCResourceConstants.WHEAT] = resources[SOCResourceConstants.WHEAT] - superset.resources[SOCResourceConstants.WHEAT];
        resDifference[SOCResourceConstants.WOOD] = resources[SOCResourceConstants.WOOD] - superset.resources[SOCResourceConstants.WOOD];
        
        return
            resDifference[SOCResourceConstants.CLAY]     <= 0
            && resDifference[SOCResourceConstants.ORE]   <= 0
            && resDifference[SOCResourceConstants.SHEEP] <= 0
            && resDifference[SOCResourceConstants.WHEAT] <= 0
            && resDifference[SOCResourceConstants.WOOD]  <= 0;
    }

    
    /**
     * Test whether this is the empty set.
     * (Perhaps a bit more efficient than using equals().)
     * @return          true if this set is empty
     */
    public boolean isEmptySet() {
        return
            resources[SOCResourceConstants.CLAY]    == 0 &&
            resources[SOCResourceConstants.ORE]     == 0 &&
            resources[SOCResourceConstants.SHEEP]   == 0 &&
            resources[SOCResourceConstants.WHEAT]   == 0 &&
            resources[SOCResourceConstants.WOOD]    == 0 &&
            resources[SOCResourceConstants.UNKNOWN] == 0;
    }

    /**
     * Returns the number of different non-zero resources specified in the set.
     * @return  int with the number of types
     */
    public int numberOfResourceTypes() {
        int num = 0;
        for (int r = SOCResourceConstants.CLAY; r <= SOCResourceConstants.WOOD; r++) {
            if (resources[r] > 0)
                num++;
        }
        return num;
    }
    
    /**
     * Returns the type of one of the shared resources, excluding UNKNOWN resources.
     * (The test is performed in standard resource order and is not randomised.)
     * @param testSet   resource set against which to test
     * @return          resource type of a shared resource or UNKNOWN if there's none
     */
    public int aCommonResource(SOCResourceSet testSet) {
        SOCResourceSet sharedResourceSet = this.intersect(testSet);
        for (int rs = SOCResourceConstants.CLAY; rs <= SOCResourceConstants.WOOD; rs++) {
            if (sharedResourceSet.getAmount(rs) > 0)
                return rs;
        }
        return SOCResourceConstants.UNKNOWN;
    }
    
    /**
     * @return a hashcode for this data
     */
    @Override
    public int hashCode()
    {
        return this.toString().hashCode();
    }

    /**
     * @return a copy of this resource set
     */
    public SOCResourceSet copy()
    {
        SOCResourceSet copy = new SOCResourceSet();
        copy.add(this);

        return copy;
    }

    /**
     * copy a resource set into this one. This one's current data is lost and overwritten.
     *
     * @param set  the set to copy from
     */
    public void setAmounts(SOCResourceSet set)
    {
        resources[SOCResourceConstants.CLAY]    = set.getAmount(SOCResourceConstants.CLAY);
        resources[SOCResourceConstants.ORE]     = set.getAmount(SOCResourceConstants.ORE);
        resources[SOCResourceConstants.SHEEP]   = set.getAmount(SOCResourceConstants.SHEEP);
        resources[SOCResourceConstants.WHEAT]   = set.getAmount(SOCResourceConstants.WHEAT);
        resources[SOCResourceConstants.WOOD]    = set.getAmount(SOCResourceConstants.WOOD);
        resources[SOCResourceConstants.UNKNOWN] = set.getAmount(SOCResourceConstants.UNKNOWN);
    }

    /**
     * Weird SS method for getting the type of resource for a bank/port trade only as it assumes only one resource type is contained in this set.
     * @return
     */
    public int pickResource()
    {
        if (resources[SOCResourceConstants.CLAY]>0)
            return SOCResourceConstants.CLAY;
        if (resources[SOCResourceConstants.ORE]>0)
            return SOCResourceConstants.ORE;
        if (resources[SOCResourceConstants.SHEEP]>0)
            return SOCResourceConstants.SHEEP;
        if (resources[SOCResourceConstants.WHEAT]>0)
            return SOCResourceConstants.WHEAT;
        if (resources[SOCResourceConstants.WOOD]>0)
            return SOCResourceConstants.WOOD;
        return -1;
    }
    
    /**
     * Find the resource type that is not 0. Used by StacChatTradeMsgParser for resolving anaphoric references.
     * Returns SOCResourceConstants.UNKNOWN if more than 1 resource has a positive quantity.
     * @return  SOCResourceConstants value of the resource type
     */
    public int resourceTypeInResourceSet() {
        int rtype = SOCResourceConstants.UNKNOWN;
        for (int r = SOCResourceConstants.CLAY ; r <= SOCResourceConstants.WOOD; r++) {
            if (resources[r] > 0) {
                //check if there was another resource that had a quantity > 0
                if (rtype != SOCResourceConstants.UNKNOWN) {
                    return SOCResourceConstants.UNKNOWN;
                }
                rtype = r;
            }
        }
        return rtype;
    }
    
    /**
     * Create a new SOCResourceSet from two sets where the first contains the number of resources in the UNKNOWN slot and the second one the resource type.
     * Used by StacChatTradeMsgParser for resolving anaphoric references.
     * @param resSetWithNumberOfUnknowns    SOCResourceSet with the number of resources in the new SOCResourceSet
     * @param resSetWithResType             SOCResourceSet with the type of resource in the new SOCResourceSet
     * @return                              the newly generated SOCResourceSet, or null if there was a problem, e.g. if the resource type cannot be identified
     */
    public static SOCResourceSet resourceSetFromResourceSetWithUnknown(SOCResourceSet resSetWithNumberOfUnknowns, SOCResourceSet resSetWithResType) {
        int quantUnknown = resSetWithNumberOfUnknowns.getAmount(SOCResourceConstants.UNKNOWN);
        if (quantUnknown != 0) {
            int rtype = resSetWithResType.resourceTypeInResourceSet();
            if (rtype < SOCResourceConstants.UNKNOWN) {
                SOCResourceSet resultSet = new SOCResourceSet();
                resultSet.setAmount(quantUnknown, rtype);
                return resultSet;
            } 
        }
        return null;
    }
    
}
