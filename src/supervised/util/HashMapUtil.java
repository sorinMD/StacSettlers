package supervised.util;

import java.util.*;


public class HashMapUtil {
	/**
	 * increment the value count in the HashMap
	 */
	public static void incrementHashTable(HashMap collection, String key, int update) {
		if (collection.containsKey(key)) {
			Integer items = (Integer) collection.get(key);
			int updated = items.intValue() + update;
			collection.put(key, new Integer(updated));

		} else {
			collection.put(key, new Integer(update));
		}
	}

	/**
	 * increment the value count in the HashMap
	 */
	public static void incrementHashTable(HashMap collection, String key, float update) {
		if (collection.containsKey(key)) {
			Float items = (Float) collection.get(key);
			float updated = items.floatValue() + update;
			collection.put(key, new Float(updated));
			
		} else {
			collection.put(key, new Float(update));
		}
	}
}
