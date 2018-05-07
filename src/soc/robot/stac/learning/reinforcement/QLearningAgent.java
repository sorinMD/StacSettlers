package soc.robot.stac.learning.reinforcement;

import java.util.HashMap;
import java.util.Map;

import soc.robot.stac.learning.reinforcement.QAgent.QKey;

import aima.core.agent.Action;
import aima.core.probability.mdp.ActionsFunction;

public class QLearningAgent<S, A extends Action> extends LearningQAgent<S, A> {
	protected static final int MIN_EXPLORATION = 2;
	
	protected double alpha, gamma, epsilon;
	protected int epsilonHalfLife;
	
	protected final Map<QKey, Integer> saCounts = new HashMap<QKey, Integer>();

    protected int numTerminalStates = 0;

	public QLearningAgent(ActionsFunction<S, A> actionsFunction, A noopAction, double alpha, double gamma, double epsilon, int epsilonHalfLife) {
		super(actionsFunction, noopAction);
		this.alpha = alpha;
		this.gamma = gamma;
		this.epsilon = epsilon;
		this.epsilonHalfLife = epsilonHalfLife;
	}

	// Use epsilon for exploration function
	protected A chooseAction(S sDelta) {
		// don't explore if we're done learning
		if (isLearning) {
		    // see if there are any actions we haven't suitably explored		    
		    for (A aDelta : actionsFunction.actions(sDelta)) {
		        // Explore a state/action if it has Q=0.0
                //  It is exceedingly unlikely that the value returns to 0, almost certainly
		        //   means it was never set
                if (getQ(sDelta, aDelta)==0.0) {
                    return aDelta;
                }                
                
		        // Ensure every state/action is explored at least MIN_EXP times
		        QKey qk = new QKey(sDelta, aDelta);
		        Integer count = saCounts.get(qk);
		        if (count == null || count.intValue() < MIN_EXPLORATION) {
		            return aDelta;
		        }		        
		    }
		    if (rand.nextDouble() < epsilon) {		
		        return getRandomAction(sDelta);
		    }
		}		    
		    
		return getMaxAction(sDelta);		
	}

	// Standard q-learning update step
	protected void updateQ(S sDelta, A aDelta, double rDelta) {
		if ( s!=null && !isTerminal(s)) {
			double q = getQ(s, a);
			int count = getSACount(s, a);
			double countAlpha = 1.0 /(double) count;
			q = q + ( countAlpha * ( r + gamma * getMaxQ(sDelta) - q));		
			setQ(s, a, q);
		}
	}

	@Override
	protected double getDefaultQ() {
		return 0;
	}

	// Get the count, and increment it (this is only called when we use it to update Q)
	private int getSACount(S s, A a) {
        QKey sa = new QKey(s, a);
        Integer i = saCounts.get(sa);
        if (i==null) {
            i = Integer.valueOf(1);            
        }
        saCounts.put(sa, Integer.valueOf(i + 1));
        return i.intValue();
    }

    @Override
    protected void terminalState(S sDelta) {
        numTerminalStates++;
        if (epsilonHalfLife>0 && numTerminalStates % epsilonHalfLife ==0) {
            epsilon *= 0.5;
        }
    }
	
}
