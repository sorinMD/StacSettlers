package soc.robot.stac;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import soc.game.SOCBoard;

/**
 * Extension of ScoredNode for settlements.  Track the neigbouring hexes, any applicable
 *  ports, and how much income we get (1 for settlement, 2 for city)
 * 
 * @author kho30
 *
 */
public class SettlementNode extends ScoredNode implements Serializable{

	protected final Set<Hex> hexes;
	protected final int portType;
	
	protected int income; // 0 for nothing there, 1 for settlement, 2 for city
	
	
	/**
	 * Initialize a settlement node based on its coordinates and the given board
	 * @param node
	 * @param board
	 */
	public SettlementNode(int node, SOCBoard board) {
		super(node);
		
		// Set the neigbouring hexes 
		hexes = new HashSet<Hex>(3);
		Vector v = SOCBoard.getAdjacentHexesToNode(node);
		for (Object o : v) {
			Integer i = (Integer) o;
			int roll = board.getNumberOnHexFromCoord(i);
			int type = board.getHexTypeFromCoord(i);
			hexes.add(new Hex(i, roll, type));
		}
		
		portType = board.getPortTypeFromNodeCoord(node);
	}

	/**
	 * Be careful - this returns the actual Set, they can mess with it.  Trust the user!
	 * @return
	 */
	public Set<Hex> getHexes() {
		return hexes;
	}

	public int getIncome() {
		return income;
	}

	public void setIncome(int income) {
		this.income = income;
	}
	
	public int getPortType() {
		return portType;
	}

	// Basic object functions - equals and hashcode simply refer to the coordinate value.  Assumption that all calls occur within the same
	//   game, so other factors (hexes, ports) should be equivalent, and score is subjective and omitted for consideration (so we can
	//   consider intersections of sets with different scoring criteria)
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof SettlementNode) {
			return node == ((SettlementNode) obj).getNode();
		}
		else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return node;
	}

	@Override
	public String toString() {
		return Integer.toString(node) + ": " + hexes.toString();
	}

	
}
