/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Some documentation javadocs here are Copyright (C) 2009 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCResourceSet;

import java.io.Serializable;
import java.util.Stack;
import java.util.Vector;
import soc.disableDebug.D;
import soc.game.SOCBoard;
import soc.game.SOCPlayerNumbers;


/**
 * This is a possible settlement that we can build
 *
 * @author Robert S Thomas
 *
 */
public class SOCPossibleSettlement extends SOCPossiblePiece implements Serializable
{
    protected Vector necessaryRoads;
    protected Vector conflicts;

    /**
     * Speedup per building type.  Indexed from {@link SOCBuildingSpeedEstimate#MIN}
     * to {@link SOCBuildingSpeedEstimate#MAXPLUSONE}.
     */
    protected int[] speedup = { 0, 0, 0, 0 };

    protected int numberOfNecessaryRoads;
    protected Stack roadPath;

    // The brain used to calculate speedup
    transient protected SOCRobotBrain brain;

    /**
     * constructor
     *
     * @param pl  the owner
     * @param co  coordinates;
     * @param nr  necessaryRoads;
     */
    public SOCPossibleSettlement(SOCRobotBrain brain, SOCPlayer pl, int co, Vector nr)
    {
        if (nr == null)
            throw new IllegalArgumentException("nr null");
        this.brain = brain;
        pieceType = SOCPossiblePiece.SETTLEMENT;
        player = pl;
        coord = co;
        necessaryRoads = nr;
        eta = 0;
        threats = new Vector();
        biggestThreats = new Vector();
        conflicts = new Vector();
        threatUpdatedFlag = false;
        hasBeenExpanded = false;
        numberOfNecessaryRoads = -1;
        roadPath = null;

        updateSpeedup();
    }

    /**
     * Incomplete constructor.
     * Should only be called from SOCPossiblePiece.parse
     */
    public SOCPossibleSettlement(SOCPlayer pl, int co, Vector nr)
    {
        if (nr == null)
            throw new IllegalArgumentException("nr null");
        this.brain = brain;
        pieceType = SOCPossiblePiece.SETTLEMENT;
        player = pl;
        coord = co;
        necessaryRoads = nr;
        eta = 0;
        threats = new Vector();
        biggestThreats = new Vector();
        conflicts = new Vector();
        threatUpdatedFlag = false;
        hasBeenExpanded = false;
        numberOfNecessaryRoads = -1;
        roadPath = null;
    }
    
    /**
     * copy constructor
     *
     * Note: This will not copy vectors, only make empty ones
     *
     * @param ps  the possible settlement to copy
     */
    public SOCPossibleSettlement(SOCPossibleSettlement ps)
    {
        //D.ebugPrintln(">>>> Copying possible settlement: "+ps);
        pieceType = SOCPossiblePiece.SETTLEMENT;
        player = ps.getPlayer();
        coord = ps.getCoordinates();
        necessaryRoads = new Vector(ps.getNecessaryRoads().size());
        eta = ps.getETA();
        threats = new Vector();
        biggestThreats = new Vector();
        conflicts = new Vector(ps.getConflicts().size());
        threatUpdatedFlag = false;
        hasBeenExpanded = false;

        int[] psSpeedup = ps.getSpeedup();

        for (int buildingType = SOCBuildingSpeedEstimate.MIN;
                buildingType < SOCBuildingSpeedEstimate.MAXPLUSONE;
                buildingType++)
        {
            speedup[buildingType] = psSpeedup[buildingType];
        }

        numberOfNecessaryRoads = ps.getNumberOfNecessaryRoads();

        if (ps.getRoadPath() == null)
        {
            roadPath = null;
        }
        else
        {
            roadPath = (Stack) ps.getRoadPath().clone();
        }
    }

    /**
     * @return the shortest road path to this settlement
     */
    public Stack getRoadPath()
    {
        return roadPath;
    }

    /**
     * @param path  a stack containing the shortest road path to this settlement
     */
    public void setRoadPath(Stack path)
    {
        roadPath = path;
    }

    /**
     * @return the list of necessary roads
     */
    public Vector getNecessaryRoads()
    {
        return necessaryRoads;
    }

    /**
     * @return the minimum number of necessary roads
     */
    public int getNumberOfNecessaryRoads()
    {
        return numberOfNecessaryRoads;
    }

    /**
     * set the minimum number of necessary roads
     *
     * @param num  the minimum number of necessary roads
     */
    public void setNumberOfNecessaryRoads(int num)
    {
        numberOfNecessaryRoads = num;
    }

    /**
     * update the speedup that this settlement gives.
     * This has been a do-nothing method (all code commented out) since March 2004 or earlier.
     */
    public void updateSpeedup()
    {
           D.ebugPrintlnINFO("****************************** (SETTLEMENT) updateSpeedup at "+Integer.toHexString(coord));
           D.ebugPrintlnINFO("SOCPN:"+player.getNumbers());
           D.ebugPrintINFO("PFLAGS:");
           boolean portFlags[] = player.getPortFlags();
           if (D.ebugOn) {
             for (int port = SOCBoard.MISC_PORT; port <= SOCBoard.WOOD_PORT; port++) {
               D.ebugPrintINFO(portFlags[port]+",");
             }
           }
           D.ebugPrintlnINFO();
//           SOCBuildingSpeedEstimate bse1 = new SOCBuildingSpeedEstimate(player.getNumbers());
           SOCBuildingSpeedEstimate bse1 = brain.getEstimator(player.getNumbers());
           int ourBuildingSpeed[] = bse1.getEstimatesFromNothingFast(player.getPortFlags());
           //
           //  get new numbers
           //
           SOCPlayerNumbers newNumbers = new SOCPlayerNumbers(player.getNumbers());
           newNumbers.updateNumbers(coord, player.getGame().getBoard());
           D.ebugPrintlnINFO("----- new numbers and ports -----");
           D.ebugPrintlnINFO("SOCPN:"+newNumbers);
           D.ebugPrintINFO("PFLAGS:");
           //
           //  get new ports
           //
           Integer coordInteger = Integer.valueOf(this.getCoordinates());
           boolean newPortFlags[] = new boolean[SOCBoard.WOOD_PORT+1];
           for (int port = SOCBoard.MISC_PORT; port <= SOCBoard.WOOD_PORT; port++) {
             newPortFlags[port] = player.getPortFlag(port);
             if (player.getGame().getBoard().getPortCoordinates(port).contains(coordInteger)) {
               newPortFlags[port] = true;
             }
             D.ebugPrintINFO(portFlags[port]+",");
           }
           D.ebugPrintlnINFO();
//           SOCBuildingSpeedEstimate bse2 = new SOCBuildingSpeedEstimate(newNumbers);
           SOCBuildingSpeedEstimate bse2 = brain.getEstimator(newNumbers);
           int speed[] = bse2.getEstimatesFromNothingFast(newPortFlags);
           for (int buildingType = SOCBuildingSpeedEstimate.MIN;
                buildingType < SOCBuildingSpeedEstimate.MAXPLUSONE;
                buildingType++) {
             D.ebugPrintlnINFO("!@#$% ourBuildingSpeed["+buildingType+"]="+ourBuildingSpeed[buildingType]+" speed["+buildingType+"]="+speed[buildingType]);
             speedup[buildingType] = ourBuildingSpeed[buildingType] - speed[buildingType];
           }
    }

    /**
     * @return the list of conflicting settlements
     */
    public Vector getConflicts()
    {
        return conflicts;
    }

    /**
     * add a possible road to the list of necessary roads
     *
     * @param rd  the road
     */
    public void addNecessaryRoad(SOCPossibleRoad rd)
    {
        necessaryRoads.addElement(rd);
    }

    /**
     * add a conflicting settlement
     *
     * @param s  the settlement
     */
    public void addConflict(SOCPossibleSettlement s)
    {
        conflicts.addElement(s);
    }

    /**
     * remove a conflicting settlement
     *
     * @param s  the settlement
     */
    public void removeConflict(SOCPossibleSettlement s)
    {
        conflicts.removeElement(s);
    }

    /**
     * @return the speedup for this settlement
     */
    public int[] getSpeedup()
    {
        return speedup;
    }

    /**
     * @return the sum of all of the speedup numbers
     */
    public int getSpeedupTotal()
    {
        int sum = 0;

        for (int buildingType = SOCBuildingSpeedEstimate.MIN;
                buildingType < SOCBuildingSpeedEstimate.MAXPLUSONE;
                buildingType++)
        {
            sum += speedup[buildingType];
        }

        return sum;
    }

    @Override
    public SOCResourceSet getResourceCost() {
        return SOCGame.SETTLEMENT_SET;
    }

	public void setBrain(SOCRobotBrain brain) {
		this.brain = brain;
	}
}
