package soc.robot.stac;

import java.util.ArrayList;
import java.util.HashMap;

import originalsmartsettlers.boardlayout.BoardLayout;
import soc.game.SOCGame;
import soc.robot.SOCRobotBrain;
import soc.robot.SOCRobotClient;
import soc.robot.SOCRobotFactory;
import soc.util.CappedQueue;
import soc.util.SOCRobotParameters;

public class OriginalSSRobotFactory  implements SOCRobotFactory {
    private final StacRobotType robotType;
    private final boolean fullPlan;
    private static BoardLayout bl = null;
    
    
	public OriginalSSRobotFactory(boolean fullPlan, StacRobotType robotType) {  
		this.robotType = robotType;
        this.fullPlan = fullPlan;
        //the robots created by this factory should use the same simulation model for efficiency and only update the parameters
        if(bl == null){
	        bl = new BoardLayout();
	        bl.InitBoard(); //default is to use the uct player
        }
    }
	/**
	 * Synchronised in case clients receive join game authorisation message at the same time
	 */
    public synchronized SOCRobotBrain getRobot(SOCRobotClient cl, SOCRobotParameters params, SOCGame ga,
            CappedQueue mq) {
        return new OriginalSSRobotBrain(cl, params, ga, mq, fullPlan, robotType, bl, new HashMap<String,ArrayList<String>>());
    }

    public boolean isType(String type) {
        return robotType.isType(type);
    }

    public void setTypeFlag(String type, String param) {
        robotType.addType(type,  param);
    }

    public void setTypeFlag(String type) {
        robotType.addType(type);
    }
	
}
