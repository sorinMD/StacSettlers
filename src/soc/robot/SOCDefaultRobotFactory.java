package soc.robot;

import soc.game.SOCGame;
import soc.util.CappedQueue;
import soc.util.SOCRobotParameters;

public class SOCDefaultRobotFactory implements SOCRobotFactory {

	public SOCDefaultRobotFactory() {	}

	public SOCRobotBrain getRobot(SOCRobotClient cl, SOCRobotParameters params, SOCGame ga,
			CappedQueue mq) {
		return new SOCRobotBrainImpl(cl, params, ga, mq);
	}

    public boolean isType(String type) {
        return false;
    }

    public void setTypeFlag(String type, String param) {
        // Do nothing or throw an exception?
        throw new UnsupportedOperationException("Factory does not support type flags");
    }

    public void setTypeFlag(String type) {
        // Do nothing or throw an exception?
        throw new UnsupportedOperationException("Factory does not support type flags");
    }



}
