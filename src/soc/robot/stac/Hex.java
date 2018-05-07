package soc.robot.stac;

import java.io.Serializable;

/**
 * Information about a hex - coordinates, resource type, rolls, plus associated helper functions
 * To minimize unnecessessary interaction with the constant-soup in the board class
 * @author kho30
 *
 */
public class Hex implements Serializable{

	private final int coord;
	private final int roll;
	private final int type;
	
	// How often is a number rolled, per 36 rolls?  The existing implementation uses rolls per 100, which introduces rounding errors
	private static final int ROLLS_PER_36[] = {0, 0, 1, 2, 3, 4, 5, 6, 5, 4, 3, 2, 1};
	
	public Hex(int coord, int roll, int type) {
		this.coord = coord;
		this.roll = roll;
		
		if (type<6) {
			this.type = type;
		}
		else if (type==6) { // treat water and desert as equivalent
			this.type = 0;			
		}
		else {
			this.type = type - 7; // turn port tiles into just the resource - we track ports elsewhere
		}
		
	}

	public int getCoord() {
		return coord;
	}

	public int getRoll() {
		return roll;
	}

	public int getType() {
		return type;
	}
	
	public int getRollsPer36() {
		return ROLLS_PER_36[roll];
	}

	// Object basics: Assume a given Hex is constant for a given game (ie if a.coord=b.coord, a.roll=b.roll, etc)
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Hex) {
			Hex h = (Hex) obj;
			return h.coord == coord;
		}
		else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return coord;
	}

	@Override
	public String toString() {
		return roll + " " + type;
	}
	
	
}
