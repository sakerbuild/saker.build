package saker.build.thirdparty.saker.util;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Simplified container that can be concurrently used to add key-value entries to it.
 * <p>
 * This class can be used by multiple threads to add key-value entries to it. The implementation does not use any
 * synchronization when inserting or retrieving entries from it. Instances of this class can be used when multiple
 * producers need to put their results to a common container.
 * <p>
 * Good use-case for this class is when lot of threads are doing computations, and are putting their result to a common
 * container.
 * <p>
 * Although this accumulator contains {@link Map.Entry} objects, this class doesn't work like a {@link Map}. Multiple
 * entries can be recorded with the same key.
 * <p>
 * This class works the same way as a {@link ConcurrentPrependAccumulator
 * ConcurrentPrependAccumulator&lt;Map.Entry&lt;K, V&gt;&gt;} would, but is implemented to allow users to not allocate a
 * new {@link Map.Entry} instance for each key-value pair stored.
 * <p>
 * This class collects the elements in an prepending manner, meaning that iteration order is the reverse of the
 * insertion order.
 * <p>
 * Iterators returned by this class are <i>weakly consistent</i>.
 * <p>
 * The method {@link Entry#setValue(Object)} on entries returned by this instance can be called to modify a recorded
 * entry in the accumulator.
 * 
 * @param <K>
 *            The key type.
 * @param <V>
 *            The value type.
 */
public final class ConcurrentPrependEntryAccumulator<K, V> implements Iterable<Map.Entry<K, V>> {
	private static final class EntryIterator<K, V> implements Iterator<Map.Entry<K, V>> {
		private Node<K, V> current;

		public EntryIterator(Node<K, V> current) {
			this.current = current;
		}

		@Override
		public boolean hasNext() {
			return this.current != null;
		}

		@Override
		public Map.Entry<K, V> next() {
			Node<K, V> c = this.current;
			if (c == null) {
				throw new NoSuchElementException();
			}
			this.current = c.next;
			return c;
		}
	}

	private static final class Node<K, V> implements Map.Entry<K, V> {
		@SuppressWarnings("rawtypes")
		private static final AtomicReferenceFieldUpdater<ConcurrentPrependEntryAccumulator.Node, Object> ARFU_value = AtomicReferenceFieldUpdater
				.newUpdater(ConcurrentPrependEntryAccumulator.Node.class, Object.class, "value");

		private final K key;
		private volatile V value;

		private Node<K, V> next;

		public Node(K key, V value) {
			this.key = key;
			this.value = value;
		}

		public Node(K key, V value, Node<K, V> next) {
			this.key = key;
			this.value = value;
			this.next = next;
		}

		@Override
		public K getKey() {
			return key;
		}

		@Override
		public V getValue() {
			return value;
		}

		@SuppressWarnings("unchecked")
		@Override
		public V setValue(V value) {
			return (V) ARFU_value.getAndSet(this, value);
		}

		@Override
		public String toString() {
			return Objects.toString(key) + "=" + Objects.toString(value);
		}

		@Override
		public int hashCode() {
			return ObjectUtils.mapEntryHash(key, value);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof Map.Entry)) {
				return false;
			}
			Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
			return Objects.equals(key, e.getKey()) && Objects.equals(value, e.getValue());
		}

	}

	@SuppressWarnings("rawtypes")
	private static final AtomicReferenceFieldUpdater<ConcurrentPrependEntryAccumulator, Node> ARFU_first = AtomicReferenceFieldUpdater
			.newUpdater(ConcurrentPrependEntryAccumulator.class, Node.class, "first");

	private volatile Node<K, V> first;

	/**
	 * Creates a new instance without any entries.
	 */
	public ConcurrentPrependEntryAccumulator() {
	}

	/**
	 * Creates a new instance and adds the entries from the argument map to it.
	 * 
	 * @param map
	 *            The initial map of entries.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public ConcurrentPrependEntryAccumulator(Map<? extends K, ? extends V> map) throws NullPointerException {
		Objects.requireNonNull(map, "map");
		map.forEach(this::addConstruct);
	}

	/**
	 * Adds an entry to this accumulator.
	 * 
	 * @param key
	 *            The key of the entry.
	 * @param value
	 *            The value of the entry.
	 */
	public void add(K key, V value) {
		Node<K, V> n = new Node<>(key, value);
		while (true) {
			Node<K, V> f = this.first;
			n.next = f;
			if (ARFU_first.compareAndSet(this, f, n)) {
				//successfully set it
				return;
			}
			//try again with changed first
		}
	}

	/**
	 * Adds all entries in the argument map to this accumulator.
	 * 
	 * @param map
	 *            The map of entries.
	 * @throws NullPointerException
	 *             If the map is <code>null</code>.
	 */
	public void addAll(Map<? extends K, ? extends V> map) throws NullPointerException {
		Objects.requireNonNull(map, "map");
		map.forEach(this::add);
	}

	/**
	 * Adds all values in the argument iterable to this accumulator, each one of them mapped to the specified key.
	 * <p>
	 * If the values iterable is empty, no entries are added.
	 * 
	 * @param key
	 *            The key to map the values to.
	 * @param values
	 *            The iterable of values.
	 * @throws NullPointerException
	 *             If the values iterable is <code>null</code>.
	 */
	public void addAllWithKey(K key, Iterable<? extends V> values) throws NullPointerException {
		Objects.requireNonNull(values, "values");
		values.forEach(v -> add(key, v));
	}

	/**
	 * Adds all keys in the argument iterable to this accumulator, each one of them mapped to the specified value.
	 * <p>
	 * If the keys iterable is empty, no entries are added.
	 * 
	 * @param keys
	 *            The iterable of keys.
	 * @param value
	 *            The value to map the keys to.
	 * @throws NullPointerException
	 *             If the keys iterable is <code>null</code>.
	 */
	public void addAllWithValue(Iterable<? extends K> keys, V value) throws NullPointerException {
		Objects.requireNonNull(keys, "keys");
		keys.forEach(k -> add(k, value));
	}

	/**
	 * Removes the first entry from this accumulator.
	 * 
	 * @return The removed entry, or <code>null</code> if the accumulator is empty.
	 */
	public Entry<K, V> take() {
		while (true) {
			Node<K, V> n = this.first;
			if (n == null) {
				return null;
			}
			Node<K, V> next = n.next;
			if (ARFU_first.compareAndSet(this, n, next)) {
				return n;
			}
		}
	}

	/**
	 * Gets the first entry in this accumulator.
	 * <p>
	 * This method doesn't remove the entry itself, but just returns it.
	 * 
	 * @return The first netry, or <code>null</code> if the accumulator is empty.
	 */
	public Entry<K, V> peek() {
		Node<K, V> n = this.first;
		if (n == null) {
			return null;
		}
		return n;
	}

	@Override
	public Iterator<Map.Entry<K, V>> iterator() {
		Node<K, V> f = this.first;
		if (f == null) {
			return Collections.emptyIterator();
		}
		return new EntryIterator<>(f);
	}

	/**
	 * Clears this accumulator and returns an iterator for the cleared entries.
	 * 
	 * @return The iterator for the cleared entries.
	 */
	public Iterator<Map.Entry<K, V>> clearAndIterator() {
		@SuppressWarnings("unchecked")
		Node<K, V> node = ARFU_first.getAndSet(this, null);
		if (node == null) {
			return Collections.emptyIterator();
		}
		return new EntryIterator<>(node);
	}

	/**
	 * Clears this accumulator and returns an iterable for the cleared entries.
	 * <p>
	 * The iterable will always iterate on the same entries, and modifications to this accumulator are not reflected on
	 * it.
	 * 
	 * @return The iterable for the cleared entries.
	 */
	public Iterable<Map.Entry<K, V>> clearAndIterable() {
		@SuppressWarnings("unchecked")
		Node<K, V> node = ARFU_first.getAndSet(this, null);
		if (node == null) {
			return Collections.emptyNavigableSet();
		}
		return () -> new EntryIterator<>(node);
	}

	/**
	 * Checks if there are any entries in this accumulator.
	 * 
	 * @return <code>true</code> if the accumulator is empty.
	 */
	public boolean isEmpty() {
		return this.first == null;
	}

	/**
	 * Clears this accumulator.
	 */
	public void clear() {
		this.first = null;
	}

	@Override
	public String toString() {
		return StringUtils.toStringJoin("[", ", ", this, "]");
	}

	private void addConstruct(K key, V value) {
		Node<K, V> n = new Node<>(key, value, this.first);
		first = n;
	}
}
