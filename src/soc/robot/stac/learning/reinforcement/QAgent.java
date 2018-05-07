package soc.robot.stac.learning.reinforcement;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import aima.core.agent.Action;
import aima.core.learning.reinforcement.PerceptStateReward;
import aima.core.learning.reinforcement.agent.ReinforcementAgent;
import aima.core.probability.mdp.ActionsFunction;
import aima.core.util.datastructure.Pair;

/***
 * A general abstraction of an agent who selects actions based on a Q map.
 * Builds in general functionality such as iterating through to select the best Q value action.
 * Should be subclassed to provide a means to specify the Q function, or learn it.
 * 
 * Ugly that we have to extend from ReinforcementAgent, but the whole class structure is a little bit dodgy.  
 * @author kho30
 *
 * @param <S>
 * @param <A>
 */
public abstract class QAgent<S, A extends Action> extends ReinforcementAgent<S, A> {

	// Randomizer should we wish to do exploration, or just have an insane agent
	protected static final Random rand = new Random();
	
	// Making Q private forces everyone to use our much safer getters and setters.  That also saves the hassle of pair generation.
	//  Make it protected since subclasses will want to be able to use persistence methods
	protected final Map<QKey, Double> Q = new HashMap<QKey, Double>();
	
	protected ActionsFunction<S, A> actionsFunction;
	
	
	public QAgent(ActionsFunction<S, A> actionsFunction) {
		this.actionsFunction = actionsFunction;
	}
	
	@Override
	public A execute(PerceptStateReward<S> percept) {
		S sDelta = percept.state();
		return chooseAction(sDelta);
	}
	
	/**
	 * Find the action the learner will take.  This could be the optimal action, a random action, etc
	 * @param sDelta
	 * @return
	 */
	protected abstract A chooseAction(S sDelta);
	
	// We use this within getMaxAction to break ties between equal options, which typically simply 
	//  haven't been learned.  Create it here to save some time
	private final List<A> optimalOptions = new ArrayList<A>();
	
	// Find the optimal action given the state.
	protected A getMaxAction(S sDelta) {
		double max = Double.NEGATIVE_INFINITY;
		optimalOptions.clear();
		for (A aDelta : actionsFunction.actions(sDelta)) {
			
			double value = getQ(sDelta, aDelta);
			if (value > max) {
				optimalOptions.clear();
				max = value;
				optimalOptions.add(aDelta);
			}
			else if (value == max) {
				optimalOptions.add(aDelta);
			}
		}
		if (optimalOptions.size()==0) {
			return null;
		}
		else if (optimalOptions.size()==1) {
		    return optimalOptions.get(0);
		}
		else {
			return breakTie(optimalOptions);
		}
	}
	
	// Make this a specific function to allow us to override tiebreaking behaviour (there may be a default action to take if we aren't informed)
	protected A breakTie(List<A> actionList) {
	    return actionList.get(rand.nextInt(actionList.size()));
	}
	
	// Return a random action given the state
	protected A getRandomAction(S sDelta) {
		A a = null;
		double max = Double.NEGATIVE_INFINITY;
		for (A aDelta : actionsFunction.actions(sDelta)) {
			double value = rand.nextDouble();
			if (value > max) {
				max = value;
				a = aDelta;
			}
		}
		return a;
	}
	
	// Return the Q value associated with the optimal action
	protected double getMaxQ(S sDelta) {
		A a = getMaxAction(sDelta);
		return getQ(sDelta, a);
	}

	@Override
	// Copied from QLearningAgent - the utility of a state is the utility of the best state-action pair for that state
	public Map<S, Double> getUtility() {
		Map<S, Double> U = new HashMap<S, Double>();
		for (QKey sa : Q.keySet()) {
			Double q = Q.get(sa);
			Double u = U.get(sa.getFirst());
			if (null == u || u < q) {
				U.put(sa.getFirst(), q);
			}
		}
		return U;
	}

	@Override
	public void reset() {
		Q.clear();		
	}
	
	protected boolean isTerminal(S s) {
		Set<A> actions = actionsFunction.actions(s);
		return (actions == null || actions.isEmpty());
	}

	/***
	 * Helper function - get the Q value for an S/A pair.  Add an entry equal with value zero if not found.
	 * @param s
	 * @param a
	 * @return
	 */
	protected double getQ(S s, A a) {
		QKey sDeltaADelta = new QKey(s, a);
		Double d =  Q.get(sDeltaADelta);
		if (d == null) {
			d = Double.valueOf(0);
			Q.put(sDeltaADelta, d);
		}
		return d;
	}
	
	protected void setQ(S s, A a, double value) {
		QKey sDeltaADelta = new QKey(s, a);
		Q.put(sDeltaADelta, Double.valueOf(value));
	}
	
	protected abstract double getDefaultQ();
	
    public void printQTable(BufferedWriter w, boolean printZeros) throws IOException {
        List<QKey> keys = new ArrayList<QKey> (Q.keySet().size());
        keys.addAll(Q.keySet());
        Collections.sort(keys);
        
        for (QKey sa : keys) {
            Double q = Q.get(sa);
            if (q.doubleValue()!=0 || printZeros) {
                // Use a common separator instead of more intuitive ones to facilitate parsing
                w.write(sa.getFirst().toString() + "/" + sa.getSecond().toString() + "/");
                w.write(Double.toString(q.doubleValue()));
                w.newLine();
            }
        }
    }
    

    // Simple extension of Pair to be comparable for debug purposes - just use toString comparison
    public class QKey extends Pair<S,A> implements Comparable<QKey> {
        public QKey(S a, A b) {
            super(a, b);
        }

        @Override
        public int compareTo(QKey o) {
            int firstComp = getFirst().toString().compareTo(o.getFirst().toString());
            if ( firstComp != 0) {
                return firstComp;
            }
            else {
                return getSecond().toString().compareTo(o.getSecond().toString());
            }
        }
        
    }
}
