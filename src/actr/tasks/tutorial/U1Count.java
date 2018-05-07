package actr.tasks.tutorial;

import actr.task.*;

/**
 * Tutorial Unit 1: Count Task
 * 
 * @author Dario Salvucci
 */
public class U1Count extends Task
{
	public Result analyze (Task[] tasks, boolean output)
	{
		boolean ok = (getModel().getProcedural().getLastProductionFired().getName().getString().contains("stop"));
		return new Result ("U1Count", ok);
	}
}
