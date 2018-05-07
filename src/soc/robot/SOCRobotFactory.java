package soc.robot;

import soc.game.SOCGame;
import soc.util.CappedQueue;
import soc.util.SOCRobotParameters;

/**
 * Factory class to generate RobotBrian objects.  Note that the factory
 * could optionally handle "learning" so it can learn from multiple brains
 * simultaneously
 * @author kho30
 *
 */
public interface SOCRobotFactory {

	public SOCRobotBrain getRobot(SOCRobotClient cl, SOCRobotParameters params, SOCGame ga, CappedQueue mq);
	
	public boolean isType(String type);
	public void setTypeFlag(String type);
	public void setTypeFlag(String type, String param);
}
