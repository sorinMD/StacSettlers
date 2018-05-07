package soc.server.database.stac;

import java.io.Serializable;

/**
 * Container class for storing and passing of game actions data collected from logs.
 * 
 * @author MD
 *
 */
public class GameActionRow implements Serializable{
	//action types (these should be defined in JSettlers internal logic not here and turned into integers TODO)
	public static final double TRADE = 1.0; //NOTE: this includes bank trades;
	public static final double ENDTURN = 2.0;
	public static final double ROLL = 3.0;
	public static final double BUILD = 4.0; //this action type leads to the following 3 subactions: (not sure which to use)
	public static final double BUILDROAD = 4.1;
	public static final double BUILDSETT = 4.2;
	public static final double BUILDCITY = 4.3;
	public static final double MOVEROBBER = 5.0;
	public static final double CHOOSEPLAYER = 6.0;//Who to rob
	public static final double DISCARD = 7.0;
	public static final double BUYDEVCARD = 8.0;
	public static final double PLAYDEVCARD = 9.0; //this action type leads to the following 4 subactions:
	public static final double PLAYKNIGHT = 9.1;
	public static final double PLAYMONO = 9.2;
	public static final double PLAYDISC = 9.3;
	public static final double PLAYROAD = 9.4;
	public static final double WIN = 10.0; //special case only for the replay client to handle;
	
	//columns
	/**
	 * Row's unique ID.
	 */
	private int ID;
	/**
	 * Type of action from the final static doubles in this class.(Until a more logical way of storing action types is created)
	 */
	private double type; 
	private int beforeState; 
	private int afterState;
	/**
	 * A value based on the offline evaluation function. (not sure if this shouldn't be double)
	 */
	private int value;
	
	public GameActionRow(int id){
		setID(id);
	}
	
	public GameActionRow(int id, double t, int bs, int as){
		setID(id);
		setBeforeState(bs);
		setAfterState(as);
		setType(t);
	}

	public int getID() {
		return ID;
	}

	public void setID(int iD) {
		ID = iD;
	}

	public double getType() {
		return type;
	}

	public void setType(double type) {
		this.type = type;
	}

	public int getBeforeState() {
		return beforeState;
	}

	public void setBeforeState(int beforeState) {
		this.beforeState = beforeState;
	}

	public int getAfterState() {
		return afterState;
	}

	public void setAfterState(int afterState) {
		this.afterState = afterState;
	}

	public int getValue() {
		return value;
	}

	public void setValue(int value) {
		this.value = value;
	}
	
	public String toString(){
		return "Action ID=" + ID +"|type=" + type + "|beforeState=" + beforeState + "|afterState=" + afterState + "|value=" + value;
	}
	
}
