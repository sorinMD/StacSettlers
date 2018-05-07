package soc.robot.stac;

import java.io.Serializable;

/**
 * Track a node with an associated score, to be assigned by the caller.  Implements Comparable for easy sorting.
 * NB: This currently is only used for settlements, but could also be used for roads.  Cities will most
 * likely use a variant of the settlement code.
 * @author kho30
 *
 */
public class ScoredNode implements Comparable<ScoredNode>, Serializable {
	protected final int node;
	protected double score;
	
	public ScoredNode(int node) {
		this.node = node;
	}		

	public double getScore() {
		return score;
	}

	public void setScore(double score) {
		this.score = score;
	}

	public int getNode() {
		return node;
	}

	@Override
	public int compareTo(ScoredNode o) {
		return (int) Math.signum(o.getScore() - this.getScore());	
	}
}
