package soc.robot.stac;

import java.util.ArrayList;
import java.util.HashMap;

import soc.game.SOCGame;
import soc.robot.SOCRobotBrain;
import soc.robot.SOCRobotClient;
import soc.robot.SOCRobotFactory;
import soc.util.CappedQueue;
import soc.util.SOCRobotParameters;


public class MCTSRobotFactory implements SOCRobotFactory {
    private final MCTSRobotType robotType;
    private final boolean fullPlan;
    
	public MCTSRobotFactory(boolean fullPlan, MCTSRobotType robotType) {  
		this.robotType = robotType;
        this.fullPlan = fullPlan;
    }

	@Override
	public SOCRobotBrain getRobot(SOCRobotClient cl, SOCRobotParameters params, SOCGame ga, CappedQueue mq) {
		return new MCTSRobotBrain(cl, params, ga, mq, fullPlan, robotType, new HashMap<String,ArrayList<String>>() );
	}

	@Override
	public boolean isType(String type) {
		return robotType.isType(type);
	}

	@Override
	public void setTypeFlag(String type) {
		robotType.addType(type);
		
	}

	@Override
	public void setTypeFlag(String type, String param) {
		robotType.addType(type,  param);
	}

}
