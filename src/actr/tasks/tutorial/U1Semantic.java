package actr.tasks.tutorial;

import actr.task.*;

/**
 * Tutorial Unit 1: Semantic Task
 * 
 * @author Dario Salvucci
 */
public class U1Semantic extends Task
{
	public Result analyze (Task[] tasks, boolean output)
	{
		boolean ok = (getModel().getProcedural().getLastProductionFired().getName().getString().contains("direct-verify"));
		return new Result ("U1Semantic", ok);
	}
}
