package actr.tasks.tutorial;

import actr.task.*;

/**
 * Tutorial Unit 1: Addition Task
 * 
 * @author Dario Salvucci
 */
public class U1Addition extends Task
{
	public Result analyze (Task[] tasks, boolean output)
	{
		boolean ok = (getModel().getProcedural().getLastProductionFired().getName().getString().contains("terminate-addition"));
		return new Result ("U1Addition", ok);
	}
}
