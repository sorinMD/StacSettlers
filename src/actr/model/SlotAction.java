package actr.model;

/**
 * Action performed for a single slot within a buffer action.
 *  
 * @author Dario Salvucci
 */
class SlotAction
{
	private Model model;
	private Symbol slot, value;

	SlotAction (Model model, Symbol slot, Symbol value)
	{
		this.model = model;
		this.slot = slot;
		this.value = value;
	}
	
	SlotAction copy ()
	{
		return new SlotAction (model, slot, value);
	}

	public boolean equals (SlotAction sa2)
	{
		return (slot==sa2.slot && value==sa2.value);
	}
	
	public Symbol getSlot() { return slot; }
	public Symbol getValue() { return value; }
	
	void fire (Instantiation inst, Chunk bufferChunk)
	{
		Symbol realSlot = (slot.isVariable()) ? inst.get(slot) : slot;
		if (realSlot==null) return;
		Symbol realValue = (value.isVariable()) ? inst.get(value) : value;
		bufferChunk.set (realSlot, realValue);
	}

	void specialize (Symbol variable, Symbol instvalue)
	{
		if (slot==variable) slot = instvalue;
		if (value==variable) value = instvalue;
	}	
	
	/** 
	 * Gets a string representation of the slot action, with standard indentation. 
	 * 
	 * @return the string
	 */
	public String toString ()
	{
		return "      " + slot + " " + value;
	}
}
