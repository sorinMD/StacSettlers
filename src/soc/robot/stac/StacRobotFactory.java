package soc.robot.stac;

import java.util.ArrayList;
import java.util.HashMap;

import soc.game.SOCGame;
import soc.robot.SOCRobotBrain;
import soc.robot.SOCRobotClient;
import soc.robot.SOCRobotFactory;
import soc.util.CappedQueue;
import soc.util.SOCRobotParameters;

public class StacRobotFactory implements SOCRobotFactory {

    private final boolean fullPlan;
        
    private StacRobotType robotType;
        
    public StacRobotFactory(boolean fullPlan) {
        this.fullPlan = fullPlan;
        this.robotType = new StacRobotType();
        robotType.addType(StacRobotType.ORIGINAL_ROBOT);
    }
        
    public StacRobotFactory(boolean fullPlan, StacRobotType robotType) {
        this.fullPlan = fullPlan;
        this.robotType = robotType;
    }
    
    public StacRobotFactory(boolean fullPlan, String[] typeFlags) {
        this.fullPlan = fullPlan;
        robotType = new StacRobotType();
        for (String s : typeFlags) {
            if (s.contains(":")) {
                String sp[] = s.split(":");
                robotType.addType(sp[0], sp[1]);
            } else {
                robotType.addType(s);
            }
        }
    }
    public void setTypeFlag(String type) {        
        robotType.addType(type);
    }
    
    public void setTypeFlag(String type, String param) {
        robotType.addType(type,  param);
    }
    
    public boolean isType(String type) {
        return robotType.isType(type);
    }

    public SOCRobotBrain getRobot(SOCRobotClient cl, SOCRobotParameters params, SOCGame ga, CappedQueue mq) {
        return new StacRobotBrain(cl, params, ga, mq, fullPlan, robotType, new HashMap<String,ArrayList<String>>() );
    }

}
