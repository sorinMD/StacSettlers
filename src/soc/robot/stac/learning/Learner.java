package soc.robot.stac.learning;

import soc.message.SOCGameStats;
import soc.robot.stac.StacRobotBrain;

public abstract class Learner {
    protected static boolean isLearning = true;
    
    public static void setLearning(boolean learn) {
        isLearning = learn;
    }   
    
    public abstract void learn(StacRobotBrain brain, SOCGameStats stats);
    
    
}
