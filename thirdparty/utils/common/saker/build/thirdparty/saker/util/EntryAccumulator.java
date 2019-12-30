package saker.build.thirdparty.saker.util;

import java.util.Map;

/**
 * {@link ElementAccumulator} subinterface for allowing {@link Map.Entry} objects to be added to it.
 * <p>
 * Note that even though this class accumulates {@link Map.Entry} objects, adding entries with the same keys will not
 * overwrite previous additions. That is, when the accumulator is iterated over, it can return multiple entries with the
 * same keys.
 * 
 * @param <K>
 *            The key type.
 * @param <V>
 *            The value type.
 */
public interface EntryAccumulator<K, V> extends ElementAccumulator<Map.Entry<K, V>> {
	/**
	 * Adds an key-value entry pair to the accumulator.
	 * 
	 * @param key
	 *            The key.
	 * @param value
	 *            The value.
	 */
	public void put(K key, V value);
}
