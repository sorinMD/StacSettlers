package soc.robot.stac.learning.reinforcement;

import aima.core.agent.Action;
import aima.core.learning.reinforcement.PerceptStateReward;
import aima.core.probability.mdp.ActionsFunction;

/***
 * General abstract class for an agent which learns Q (not to be confused with Q-Learning, a specific algorithm)
 * 
 * @author kho30
 *
 * @param <S>
 * @param <A>
 */
public abstract class LearningQAgent<S, A extends Action> extends QAgent<S, A> {

	// Store the previous state, action and reward in case we want to use them
	protected S s = null;
	protected A a = null;
	protected Double r = null;
	
	protected boolean isLearning = true;
	
	protected final A noop;
	
	public LearningQAgent(ActionsFunction<S, A> actionsFunction, A noopAction) {
		super(actionsFunction);
		noop = noopAction;
	}

	@Override
	public A execute(PerceptStateReward<S> percept) {		
		S sDelta = percept.state();
		double rDelta = percept.reward();
		
		// Choose the action we want to take
		A aDelta = chooseAction(sDelta);
				
		// Learn as appropriate
		if (isLearning) {
			learn(sDelta, aDelta, rDelta);
		}
		
		// Remember what we did previously - that is used by many learning functions
		s = sDelta;
		a = aDelta;
		r = rDelta;
		
		return aDelta;
	}	
	
	
	/**
	 * Learn based on the action we selected from the state we are in, and the current reward.
	 * Previous state/action/reward are stored in the object.
	 * 
	 * @param sDelta
	 * @param aDelta
	 * @param rDelta
	 */
	protected void learn(S sDelta, A aDelta, double rDelta) {			
		// noncontroversial aspect - if we are at a terminal state, rDelta is the Q value
		if (isTerminal(sDelta)) {
			setQ(sDelta,  noop, rDelta);
			terminalState(sDelta);
		}

		updateQ(sDelta, aDelta, rDelta);
	}
	
	protected abstract void terminalState(S sDelta);
	
	/**
	 * Update the Q map and do any other housekeeping.
	 * @param sDelta
	 * @param aDelta
	 * @param rDelta
	 */
	protected abstract void updateQ(S sDelta, A aDelta, double rDelta);
	
	public void setIsLearning(boolean l) {
		isLearning = l;
	}

}
