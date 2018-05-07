package actr.task;

import java.awt.*;
import javax.swing.JPanel;

/**
 * The class that defines a cross component for a task interface.
 * 
 * @author Dario Salvucci
 */
public class TaskCross extends JPanel implements TaskComponent
{
	int size;
	
	/**
	 * Creates a new cross.
	 * @param x the x coordinate
	 * @param y the y coordinate
	 * @param size the size (width and height) of the cross
	 */
	public TaskCross (int x, int y, int size) 
	{
		super();
		this.size = size;
		setForeground (Color.black);
		setBounds (x, y, size, size);
	}
	
	/**
	 * Gets the kind of component (i.e., the "kind" slot of the ACT-R visual object).
	 * @return the kind string
	 */
	public String getKind () { return "cross"; }

	/**
	 * Gets the value of component (i.e., the "value" slot of the ACT-R visual object).
	 * @return the value string
	 */
	public String getValue () { return "cross"; }
	
	/**
	 * Moves the cross to a new location.
	 * @param x the x coordinate
	 * @param y the y coordinate
	 */
	public void move (int x, int y)
	{
		setBounds (x, y, size, size);
	}
		
	protected void paintComponent (Graphics g)
	{
		g.setColor (this.getForeground());
		int cx = size/2;
		int cy = size/2;
		g.drawLine (0, cy, size, cy);
		g.drawLine (cx, 0, cx, size);
	}
}
