package actr.tasks.tutorial;

import actr.task.*;

/**
 * Tutorial Unit 1: Tutor Task
 * 
 * @author Dario Salvucci
 */
public class U1Tutor extends Task
{
	public Result analyze (Task[] tasks, boolean output)
	{
		boolean ok = (getModel().getProcedural().getLastProductionFired().getName().getString().contains("add-tens-done"));
		return new Result ("U1Tutor", ok);
	}
}
