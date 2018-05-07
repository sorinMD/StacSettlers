package soc.server.database.stac;

import java.util.Arrays;

/**
 * The row of a table containing the value of a state.
 * @author MD
 */
public class StateValueRow {

	public static long counter = 0;
	
	private long id;
	private int[] state;
	private double value;
	
	public StateValueRow(int[] vector, double value) {
		id = counter;
		state = vector;
		this.value = value;
		counter++;
	}
	
	public StateValueRow(long id, int[] vector, double value) {
		this.id = id;
		state = vector;
		this.value = value;

	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public int[] getState() {
		return state;
	}

	public void setState(int[] state) {
		this.state = state;
	}

	public double getValue() {
		return value;
	}

	public void setValue(double value) {
		this.value = value;
	}

	public String toString(){
		return "Row ID=" + id +"|value=" + value + "|State=" + Arrays.toString(state);
	}
	
}
