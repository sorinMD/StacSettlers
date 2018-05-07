package actr.model;

import java.util.*;

/**
 * Declarative memory that holds chunks of declarative knowledge.
 *  
 * @author Dario Salvucci
 */
public class Declarative extends Module
{
	private Model model;
	private Map<Symbol,Chunk> chunks;
	private Map<String,Double> similarities;
	private Vector<Chunk> finsts;
	//	private double lastCleanup = 0;

	double retrievalThreshold = 0.0;
	double latencyFactor = 1.0;
	boolean baseLevelLearning = false;
	double baseLevelDecayRate = 0.5;
	boolean optimizedLearning = true;
	double activationNoiseS = 0;
	double goalActivation = 1.0;
	double imaginalActivation = 0;
	boolean spreadingActivation = false;
	double maximumAssociativeStrength = 0;
	boolean partialMatching = false;
	double mismatchPenalty = 0;
	int declarativeNumFinsts = 4;
	double declarativeFinstSpan = 3.0;
	boolean activationTrace = false;
	boolean addChunkOnNewRequest = true;

	Declarative (Model model)
	{
		this.model = model;
		chunks = new HashMap<Symbol,Chunk>();
		similarities = new HashMap<String,Double>();
		finsts = new Vector<Chunk>();
		//		lastCleanup = 0;
	}

        Chunk add (Chunk chunk)
	{
		return add (chunk, false);
	}

	Chunk add (Chunk chunk, boolean preventMerge)
	{
		if (get(chunk.getName()) != null) return chunk;

		if (!preventMerge)
		{
			Iterator<Chunk> it = chunks.values().iterator();
			while (it.hasNext())
			{
				Chunk existingChunk = it.next();
				if (chunk.equals (existingChunk))
				{
					existingChunk.addUse();
					model.getBuffers().replaceSlotValues (chunk, existingChunk);
					return existingChunk;
				}
			}
		}

		chunk.setFan (1);
		Iterator<Chunk> it = chunks.values().iterator();
		while (it.hasNext())
		{
			Chunk existingChunk = it.next();
			chunk.increaseFan (chunk.appearsInSlotsOf (existingChunk));
		}

		Iterator<Symbol> it2 = chunk.getSlotValues();
		while (it2.hasNext())
		{
			Chunk valueChunk = get(it2.next());
			if (valueChunk != null) valueChunk.increaseFan();
		}

		chunk.setCreationTime (model.getTime());
		chunks.put (chunk.getName(), chunk);
		return chunk;
	}

	/**
	 * Gets the full chunk for the given name.
	 * @param name the chunk name
	 * @return the named chunk, or <tt>null</tt> if not present
	 */
	public Chunk get (Symbol name) { return chunks.get (name); }

	/**
	 * Gets the size as the number of chunks in declarative memory.
	 * @return the number of chunks
	 */
	public int size () { return chunks.size(); }

	/**
	 * Gets the chunk iterator for all chunks.
	 * @return the iterator
	 */
	public Iterator<Chunk> getChunks () { return chunks.values().iterator(); }

        Chunk findRetrieval (Chunk request)
	{
		HashSet<Chunk> matches = new HashSet<Chunk>();
		if (activationTrace) model.output ("*** finding retrieval for request " + request);

		Iterator<Chunk> it = chunks.values().iterator();
		while (it.hasNext())
		{
			Chunk potential = it.next();
			boolean match = true;
			Iterator<Symbol> slots = request.getSlotNames();
			while (slots.hasNext())
			{
				Symbol slot = slots.next();
				Symbol value = request.get(slot);
				if (slot==Symbol.get(":recently-retrieved"))
				{
					if (value==Symbol.get("reset")) finsts.clear();
					else if (potential.isRetrieved() != value.toBoolean()) { match=false; continue; }
				}
				else
				{
					Symbol potval = potential.get(slot);
					if (!partialMatching
							|| (slot==Symbol.isa || slot.getString().charAt(0)==':'))
						if (potval==null || potval!=request.get(slot))
						{ match=false; continue; }
				}
			}
			if (match) matches.add (potential);
		}

		if (matches.isEmpty())
		{
			if (activationTrace) model.output ("*** no matching chunks");
			return null;
		}
		else
		{
			it = matches.iterator();
			Chunk chunk = it.next();
			if (activationTrace) model.output ("*** testing " +chunk.getName() + " " +chunk);
			double highestActivation = chunk.computeActivation (request);
			if (activationTrace)
				model.output ("*** activation " +chunk.getName() + " = " + String.format ("%.3f", highestActivation));
			Chunk highestChunk = chunk;
			while (it.hasNext())
			{
				chunk = it.next();
				if (activationTrace) model.output ("*** testing " +chunk.getName() + " " +chunk);
				double act = chunk.computeActivation (request);
				if (activationTrace)
					model.output ("*** activation " +chunk.getName() + " = " + String.format ("%.3f", act));
				if (act > highestActivation)
				{
					highestActivation = act;
					highestChunk = chunk;
				}
			}
			if (highestActivation >= retrievalThreshold)
			{
				if (activationTrace) model.output ("*** retrieving " +highestChunk.getName() + " "+highestChunk);
				return highestChunk;
			}
			else
			{
				if (activationTrace) model.output ("*** no chunk above retrieval threshold");
				return null;
			}
		}
	}

	void update ()
	{
		for (int i=0 ; i<finsts.size() ; i++)
		{
			Chunk c = finsts.elementAt(i);
			if (c.getRetrievalTime() < model.getTime() - declarativeFinstSpan)
			{
				c.setRetrieved (false);
				c.setRetrievalTime (0);
				finsts.removeElementAt(i);
			}
		}

		Chunk request = model.getBuffers().get (Symbol.retrieval);
		if (request!=null && request.isRequest())
		{
			request.setRequest (false);
			model.getBuffers().clear (Symbol.retrieval);
			if (model.verboseTrace) model.output ("declarative", "start-retrieval");
			final Chunk retrieval = findRetrieval (request);
			if (retrieval != null)
			{
				double retrievalTime = latencyFactor * Math.exp(-retrieval.getActivation());
				model.getBuffers().setSlot (Symbol.retrievalState, Symbol.state, Symbol.busy);
				model.getBuffers().setSlot (Symbol.retrievalState, Symbol.buffer, Symbol.requested);
				model.addEvent (new Event (model.getTime() + retrievalTime,
						"declarative", "retrieved-chunk ["+retrieval.getName()+"]")
				{
					public void action() {
						retrieval.setRetrieved (true);
						retrieval.setRetrievalTime (model.getTime());
						finsts.add (retrieval);
						if (finsts.size() > declarativeNumFinsts) finsts.removeElementAt(0);
						retrieval.addUse();
						model.getBuffers().set (Symbol.retrieval, retrieval);
						model.getBuffers().setSlot (Symbol.retrievalState, Symbol.state, Symbol.free);
						model.getBuffers().setSlot (Symbol.retrievalState, Symbol.buffer, Symbol.full);
					}
				});
			}
			else
			{
				double retrievalTime = latencyFactor * Math.exp(- retrievalThreshold);
				model.getBuffers().setSlot (Symbol.retrievalState, Symbol.state, Symbol.busy);
				model.addEvent (new Event (model.getTime() + retrievalTime,
						"declarative", "retrieval-failure")
				{
					public void action() {
						model.getBuffers().setSlot (Symbol.retrievalState, Symbol.state, Symbol.error);
						model.getBuffers().setSlot (Symbol.retrievalState, Symbol.buffer, Symbol.empty);
					}
				});
			}
		}

		//		if (false) //model.getTime() > lastCleanup + 60.0)
		//		{
		//			compact();
		//			lastCleanup = model.getTime();
		//		}
	}


	//	void compact ()
	//	{
	//		Vector<Symbol> toRemove = new Vector<Symbol>();
	//		Iterator<Symbol> it = chunks.keySet().iterator();
	//		while (it.hasNext())
	//		{
	//			Symbol key = it.next();
	//			Chunk chunk = get (key);
	//			if (chunk.getCreationTime() < model.getTime() - 60.0
	//					&& chunk.getUseCount() == 1
	//					&& chunk.getBaseLevel() < -1.0)
	//				toRemove.add (key);
	//		}
	//		it = toRemove.iterator();
	//		while (it.hasNext()) chunks.remove (it.next());
	//	}

	/**
	 * Gets the similarity of two chunks.
	 * @param chunk1 the first chunk
	 * @param chunk2 the second chunk
	 * @return the similarity of the chunks, or -1.0 if none has been specified
	 */
	public double getSimilarity (Symbol chunk1, Symbol chunk2)
	{
		if (chunk1==chunk2) return 0;
		Double d = similarities.get (chunk1.getString()+"$"+chunk2.getString());
		if (d==null) d = similarities.get (chunk2.getString()+"$"+chunk1.getString());
		if (d==null) return -1.0;
		else return d.doubleValue();
	}

	void setSimilarity (Symbol chunk1, Symbol chunk2, double value)
	{
		similarities.put (chunk1.getString()+"$"+chunk2.getString(), new Double(value));
	}

	void setAllBaseLevels (double baseLevel)
	{
		Iterator<Chunk> it = chunks.values().iterator();
		while (it.hasNext()) it.next().setBaseLevel(baseLevel);
	}

	/** 
	 * Gets a string representation of the declarative store including all chunks. 
	 * @return the string
	 */
	public String toString ()
	{
		String s = "";
		Iterator<Chunk> it = chunks.values().iterator();
		while (it.hasNext()) s += it.next() + "\n";
		return s;
	}

        /** 
	 * Gets a string representation of the declarative store for the chunks of the specified type. 
         * Also prints the chunks' current activation value.
         * @param  chunkType  the chunk type (value of the `isa' slot) to be printed
	 * @return the string
         * @author Markus Guhe
	 */
	public String toString (String chunkType)
	{
		String s = "";
		Iterator<Chunk> it = chunks.values().iterator();
		while (it.hasNext()) {
                    Chunk ch = it.next();
                    if (ch.getSlotValue(Symbol.get("isa")) == Symbol.get(chunkType)) {
                        s += ch + " " + ch.getActivation() + "\n";
                    }
                }
		return s;
	}

        /** 
	 * Gets a string representation of the declarative store and prints the chunks' current activation. 
	 * @return the string
         * @author Markus Guhe
	 */
	public String toStringWithActivation()
	{
		String s = "";
		Iterator<Chunk> it = chunks.values().iterator();
		while (it.hasNext()) {
                    Chunk ch = it.next();
                    s += ch + " " + ch.getActivation() + "\n";
                }
		return s;
	}
}
