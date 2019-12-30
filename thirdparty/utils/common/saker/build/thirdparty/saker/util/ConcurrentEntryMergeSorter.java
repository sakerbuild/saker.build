package saker.build.thirdparty.saker.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Class for concurrently collecting sorted {@link Map.Entry} iterables and then later merge-sorting them.
 * <p>
 * This class provides functionality for concurrently accumulating iterables of map entries, and the later efficiently
 * sorting them or iterating over them via merge-sort. The concurrent nature of the class comes from the fact that
 * iterables can be added to it without requiring synchronization between threads.
 * <p>
 * <b>Important:</b> Any added entry iterable must be already
 * {@linkplain ObjectUtils#isStrictlySortedEntries(Iterable, Comparator) stricly ordered} by the comparator that is
 * going to be used for merge-sorting. Violating this requirement will result in incorrect operationg of the
 * merge-sorted results.
 * <p>
 * Generally, an instance of this class is used in the following way:
 * <ol>
 * <li>A new instance is created.</li>
 * <li>Some arbitrary work is done by the caller, which results in one or more map entries that need to be sorted.</li>
 * <li>Sorted iterables are added to the sorter instance, this can happen concurrently by multiple threads.</li>
 * <li>The user decides to collect the entries, which is done by merge-sorting the previously collected sorted iterables
 * and putting them into an appropriate collection, or iterator.</li>
 * <li>The created collection/iterator can be used by the caller.</li>
 * </ol>
 * During a merge-sort, multiple values can be mapped to the same keys. To resolve this ambiguity, users can supply a
 * {@link MatchingKeyPolicy} for the merge-sort to decide which value to keep for the given key.
 * <p>
 * Using this class instead of directly putting the entries into a result collection can be more efficient, as the
 * sorting can be done with fewer comparisons. However, in order for this class to work, the added iterables must be
 * already sorted. If you're unsure whether the iterables that you're using with this class, don't use this, and fall
 * back to some other solution, as the sorting algorithm in this class <b>will</b> break if unsorted iterables are added
 * to it.
 * <p>
 * The iterables are to be added with their estimated sizes provided. With this size, the sorter can allocate a result
 * storage for the sorted collections more efficiently, that can result in fewer reallocations. These sizes are
 * estimates, clients are not required to provide actually correct values, but they are strongly encouraged to do so.
 * 
 * @param <K>
 *            The key type.
 * @param <V>
 *            The value type.
 */
public final class ConcurrentEntryMergeSorter<K, V> {
	//XXX should implement sorted validation, and size validation for testing (guarded by TestFlag, and validated using a forwarding iterator)
	/**
	 * Enumeration for specifying which value should be kept if there are multiple conflicting keys.
	 * <p>
	 * During merge-sort, a possible scenario is when the same keys are encountered in different iterables. In this case
	 * the sorting algorithm must decide which mapping to preserve.
	 */
	public enum MatchingKeyPolicy {
		/**
		 * Policy for declaring that the caller doesn't care which value is kept for conflicting keys.
		 */
		DONT_CARE {
			@Override
			<K, V> IterableState<K, V> apply(IterableState<K, V> firststate, IterableState<K, V>[] iterables,
					int matchingidx) {
				return firststate;
			}
		},
		/**
		 * Policy for declaring that the key-value pair that was added later should be kept.
		 * <p>
		 * If a key-value pair K-V1 and K-V2 is present during the sorting, and K-V1 was added to the
		 * {@link ConcurrentEntryMergeSorter} <i>before</i> K-V2, then the value <b>V2</b> should be kept.
		 */
		CHOOSE_LATEST {
			@Override
			<K, V> IterableState<K, V> apply(IterableState<K, V> firststate, IterableState<K, V>[] iterables,
					int matchingidx) {
				IterableState<K, V> matching = iterables[matchingidx];
				if (matching.iterableAge < firststate.iterableAge) {
					return firststate;
				}
				iterables[matchingidx] = firststate;
				return matching;
			}
		},
		/**
		 * Policy for declaring that the key-value pair that was added earlier should be kept.
		 * <p>
		 * If a key-value pair K-V1 and K-V2 is present during the sorting, and K-V1 was added to the
		 * {@link ConcurrentEntryMergeSorter} <i>before<ib> K-V2, then the value <b>V1</b> should be kept.
		 */
		CHOOSE_EARLIEST {
			@Override
			<K, V> IterableState<K, V> apply(IterableState<K, V> firststate, IterableState<K, V>[] iterables,
					int matchingidx) {
				IterableState<K, V> matching = iterables[matchingidx];
				if (matching.iterableAge > firststate.iterableAge) {
					return firststate;
				}
				iterables[matchingidx] = firststate;
				return matching;
			}
		},
//		FAIL {
//			@Override
//			<K, V> IterableState<K, V> apply(IterableState<K, V> firststate, IterableState<K, V>[] iterables, int matchingidx) {
//				//XXX reify exception
//				throw new IllegalArgumentException("Entry collision for key: " + firststate.nextEntry.getKey());
//			}
//		}
		;

		abstract <K, V> IterableState<K, V> apply(IterableState<K, V> firststate, IterableState<K, V>[] iterables,
				int matchingidx);

	}

	private final ConcurrentPrependAccumulator<MergerIterable<K, V>> iterables = new ConcurrentPrependAccumulator<>();

	/**
	 * Creates a new empty instance.
	 */
	public ConcurrentEntryMergeSorter() {
	}

	/**
	 * Checks if any iterables was added to this merge sorter.
	 * <p>
	 * Note that if there was any iterable added, the result of the sorting may still be empty.
	 * 
	 * @return <code>true</code> if there is at least one iterable in this sorter.
	 */
	public boolean isAnyIterableAdded() {
		return !iterables.isEmpty();
	}

	/**
	 * Adds a collection of entries to the sorter.
	 * <p>
	 * The estimated size of the iterable will be based on the {@link Collection#size()}. If the collection is a
	 * concurrent collection, consider calling {@link #add(Iterable, int)} instead.
	 * <p>
	 * The iteration order of the entries <b>must</b> be sorted by the comparator that is going to be used for sorting.
	 * 
	 * @param entries
	 *            The entries to add.
	 * @throws NullPointerException
	 *             If the collection is <code>null</code>.
	 */
	public void add(Collection<? extends Entry<? extends K, ? extends V>> entries) throws NullPointerException {
		Objects.requireNonNull(entries, "entries");
		this.add(entries, entries.size());
	}

	/**
	 * Adds an iterable of entries with the estimated size to the sorter.
	 * <p>
	 * The iteration order of the entries <b>must</b> be sorted by the comparator that is going to be used for sorting.
	 * 
	 * @param entries
	 *            The iterable of entries.
	 * @param size
	 *            The estimated size.
	 * @throws IllegalArgumentException
	 *             If the size is negative.
	 * @throws NullPointerException
	 *             If the entries iterable is <code>null</code>.
	 */
	public void add(Iterable<? extends Entry<? extends K, ? extends V>> entries, int size)
			throws IllegalArgumentException, NullPointerException {
		Objects.requireNonNull(entries, "entries");
		if (size < 0) {
			throw new IllegalArgumentException("Negative size. (" + size + ")");
		}
		iterables.add(new MergerIterable<>(entries, size));
	}

	/**
	 * Adds the entries of a map to the sorter.
	 * <p>
	 * The estimated size of the iterable will be based on the {@link Map#size()}. If the map is a concurrent map,
	 * consider calling {@link #add(Map, int)} instead.
	 * <p>
	 * The iteration order of the entries <b>must</b> be sorted by the comparator that is going to be used for sorting.
	 * 
	 * @param map
	 *            The map entries to add.
	 * @throws NullPointerException
	 *             If the map is <code>null</code>.
	 */
	public void add(Map<? extends K, ? extends V> map) throws NullPointerException {
		Objects.requireNonNull(map, "map");
		int size = map.size();
		add(map, size);
	}

	/**
	 * Adds the entries of a map with the estimated size to the sorter.
	 * <p>
	 * The iteration order of the entries <b>must</b> be sorted by the comparator that is going to be used for sorting.
	 * 
	 * @param map
	 *            The map entries to add.
	 * @param size
	 *            The estimated size.
	 * @throws IllegalArgumentException
	 *             If the size is negative.
	 * @throws NullPointerException
	 *             If the entries iterable is <code>null</code>.
	 */
	public void add(Map<? extends K, ? extends V> map, int size) throws IllegalArgumentException, NullPointerException {
		Objects.requireNonNull(map, "map");
		if (size < 0) {
			throw new IllegalArgumentException("Negative size. (" + size + ")");
		}
		iterables.add(new MergerIterable<>(map.entrySet(), size));
	}

	/**
	 * Executes the sorting for the currently added iterables and creates a new {@link ConcurrentSkipListMap}.
	 * <p>
	 * Callers <b>must</b> ensure that all previously added iterables are sorted by the argument comparator.
	 * 
	 * @param matchingPolicy
	 *            The matching policy for determining which values to keep for conflicting keys.
	 * @param comparator
	 *            The comparator that defines the sorting order, or <code>null</code> to use the natural order of the
	 *            keys.
	 * @return The created map.
	 * @throws NullPointerException
	 *             If the matching policy is <code>null</code>.
	 */
	public ConcurrentSkipListMap<K, V> createConcurrentSkipListMap(MatchingKeyPolicy matchingPolicy,
			Comparator<? super K> comparator) throws NullPointerException {
		if (!isAnyIterableAdded()) {
			return new ConcurrentSkipListMap<>(comparator);
		}
		Objects.requireNonNull(matchingPolicy, "matching policy");
		return ObjectUtils.createConcurrentSkipListMapFromSortedIterator(
				createMergingIterator(matchingPolicy, comparator), comparator);
	}

	/**
	 * Executes the sorting for the currently added iterables and creates a new {@link ConcurrentSkipListMap}.
	 * <p>
	 * Callers <b>must</b> ensure that all previously added iterables are sorted by their natural order.
	 * <p>
	 * This is the same as:
	 * 
	 * <pre>
	 * createConcurrentSkipListMap(matchingPolicy, null);
	 * </pre>
	 * 
	 * @param matchingPolicy
	 *            The matching policy for determining which values to keep for conflicting keys.
	 * @return The created map.
	 * @throws NullPointerException
	 *             If the matching policy is <code>null</code>.
	 */
	public ConcurrentSkipListMap<K, V> createConcurrentSkipListMap(MatchingKeyPolicy matchingPolicy)
			throws NullPointerException {
		return createConcurrentSkipListMap(matchingPolicy, null);
	}

	/**
	 * Executes the sorting for the currently added iterables and creates a new {@link ConcurrentSkipListMap}.
	 * <p>
	 * Callers <b>must</b> ensure that all previously added iterables are sorted by the argument comparator.
	 * <p>
	 * This is the same as:
	 * 
	 * <pre>
	 * createConcurrentSkipListMap(MatchingKeyPolicy.DONT_CARE, comparator);
	 * </pre>
	 * 
	 * @param comparator
	 *            The comparator that defines the sorting order, or <code>null</code> to use the natural order of the
	 *            keys.
	 * @return The created map.
	 */
	public ConcurrentSkipListMap<K, V> createConcurrentSkipListMap(Comparator<? super K> comparator) {
		return createConcurrentSkipListMap(MatchingKeyPolicy.DONT_CARE, comparator);
	}

	/**
	 * Executes the sorting for the currently added iterables and creates a new {@link ConcurrentSkipListMap}.
	 * <p>
	 * Callers <b>must</b> ensure that all previously added iterables are sorted by their natural order.
	 * <p>
	 * This is the same as:
	 * 
	 * <pre>
	 * createConcurrentSkipListMap(MatchingKeyPolicy.DONT_CARE, null);
	 * </pre>
	 * 
	 * @return The created map.
	 */
	public ConcurrentSkipListMap<K, V> createConcurrentSkipListMap() {
		return createConcurrentSkipListMap(MatchingKeyPolicy.DONT_CARE, null);
	}

	/**
	 * Executes the sorting for the currently added iterables and creates a new {@link TreeMap}.
	 * <p>
	 * Callers <b>must</b> ensure that all previously added iterables are sorted by the argument comparator.
	 * 
	 * @param matchingPolicy
	 *            The matching policy for determining which values to keep for conflicting keys.
	 * @param comparator
	 *            The comparator that defines the sorting order, or <code>null</code> to use the natural order of the
	 *            keys.
	 * @return The created map.
	 * @throws NullPointerException
	 *             If the matching policy is <code>null</code>.
	 */
	public TreeMap<K, V> createTreeMap(MatchingKeyPolicy matchingPolicy, Comparator<? super K> comparator)
			throws NullPointerException {
		//TreeMap uses the size of the passed map so we have to collect the entries first
		if (!isAnyIterableAdded()) {
			return new TreeMap<>(comparator);
		}
		MergingIterator<K, V> mergingiterator = createMergingIterator(matchingPolicy, comparator);
		ArrayList<Entry<? extends K, ? extends V>> entries = new ArrayList<>(mergingiterator.maxSize);
		for (Iterator<? extends Entry<? extends K, ? extends V>> it = mergingiterator; it.hasNext();) {
			Entry<? extends K, ? extends V> entry = it.next();
			entries.add(entry);
		}
		return ObjectUtils.createTreeMapFromSortedIterator(entries.iterator(), entries.size(), comparator);
	}

	/**
	 * Executes the sorting for the currently added iterables and creates a new {@link TreeMap}.
	 * <p>
	 * Callers <b>must</b> ensure that all previously added iterables are sorted by their natural order.
	 * <p>
	 * This is the same as:
	 * 
	 * <pre>
	 * createTreeMap(matchingPolicy, null);
	 * </pre>
	 * 
	 * @param matchingPolicy
	 *            The matching policy for determining which values to keep for conflicting keys.
	 * @return The created map.
	 * @throws NullPointerException
	 *             If the matching policy is <code>null</code>.
	 */
	public TreeMap<K, V> createTreeMap(MatchingKeyPolicy matchingPolicy) throws NullPointerException {
		return createTreeMap(matchingPolicy, null);
	}

	/**
	 * Executes the sorting for the currently added iterables and creates a new {@link TreeMap}.
	 * <p>
	 * Callers <b>must</b> ensure that all previously added iterables are sorted by the argument comparator.
	 * <p>
	 * This is the same as:
	 * 
	 * <pre>
	 * createTreeMap(MatchingKeyPolicy.DONT_CARE, comparator);
	 * </pre>
	 * 
	 * @param comparator
	 *            The comparator that defines the sorting order, or <code>null</code> to use the natural order of the
	 *            keys.
	 * @return The created map.
	 */
	public TreeMap<K, V> createTreeMap(Comparator<? super K> comparator) {
		return createTreeMap(MatchingKeyPolicy.DONT_CARE, comparator);
	}

	/**
	 * Executes the sorting for the currently added iterables and creates a new {@link TreeMap}.
	 * <p>
	 * Callers <b>must</b> ensure that all previously added iterables are sorted by their natural order.
	 * <p>
	 * This is the same as:
	 * 
	 * <pre>
	 * createTreeMap(MatchingKeyPolicy.DONT_CARE, null);
	 * </pre>
	 * 
	 * @return The created map.
	 */
	public TreeMap<K, V> createTreeMap() {
		return createTreeMap(MatchingKeyPolicy.DONT_CARE, null);
	}

	/**
	 * Executes the sorting for the currently added iterables and creates a new immutable navigable map.
	 * <p>
	 * Callers <b>must</b> ensure that all previously added iterables are sorted by the argument comparator.
	 * 
	 * @param matchingPolicy
	 *            The matching policy for determining which values to keep for conflicting keys.
	 * @param comparator
	 *            The comparator that defines the sorting order, or <code>null</code> to use the natural order of the
	 *            keys.
	 * @return The created map.
	 * @throws NullPointerException
	 *             If the matching policy is <code>null</code>.
	 */
	public NavigableMap<K, V> createImmutableNavigableMap(MatchingKeyPolicy matchingPolicy,
			Comparator<? super K> comparator) throws NullPointerException {
		if (!isAnyIterableAdded()) {
			return ImmutableUtils.emptyNavigableMap(comparator);
		}
		MergingIterator<K, V> mergingiterator = createMergingIterator(matchingPolicy, comparator);
		ArrayList<Entry<K, V>> entries = new ArrayList<>(mergingiterator.maxSize);
		for (Iterator<? extends Entry<? extends K, ? extends V>> it = mergingiterator; it.hasNext();) {
			Entry<? extends K, ? extends V> entry = it.next();
			entries.add(ImmutableUtils.makeImmutableMapEntry(entry));
		}
		return ObjectUtils.createImmutableNavigableMapFromSortedIterator(entries.iterator(), entries.size(),
				comparator);
	}

	/**
	 * Executes the sorting for the currently added iterables and creates a new immutable navigable map.
	 * <p>
	 * Callers <b>must</b> ensure that all previously added iterables are sorted by their natural order.
	 * <p>
	 * This is the same as:
	 * 
	 * <pre>
	 * createImmutableNavigableMap(matchingPolicy, null);
	 * </pre>
	 * 
	 * @param matchingPolicy
	 *            The matching policy for determining which values to keep for conflicting keys.
	 * @return The created map.
	 * @throws NullPointerException
	 *             If the matching policy is <code>null</code>.
	 */
	public NavigableMap<K, V> createImmutableNavigableMap(MatchingKeyPolicy matchingPolicy)
			throws NullPointerException {
		return createImmutableNavigableMap(matchingPolicy, null);
	}

	/**
	 * Executes the sorting for the currently added iterables and creates a new immutable navigable map.
	 * <p>
	 * Callers <b>must</b> ensure that all previously added iterables are sorted by the argument comparator.
	 * <p>
	 * This is the same as:
	 * 
	 * <pre>
	 * createImmutableNavigableMap(MatchingKeyPolicy.DONT_CARE, comparator);
	 * </pre>
	 * 
	 * @param comparator
	 *            The comparator that defines the sorting order, or <code>null</code> to use the natural order of the
	 *            keys.
	 * @return The created map.
	 */
	public NavigableMap<K, V> createImmutableNavigableMap(Comparator<? super K> comparator) {
		return createImmutableNavigableMap(MatchingKeyPolicy.DONT_CARE, comparator);
	}

	/**
	 * Executes the sorting for the currently added iterables and creates a new immutable navigable map.
	 * <p>
	 * Callers <b>must</b> ensure that all previously added iterables are sorted by their natural order.
	 * <p>
	 * This is the same as:
	 * 
	 * <pre>
	 * createImmutableNavigableMap(MatchingKeyPolicy.DONT_CARE, null);
	 * </pre>
	 * 
	 * @return The created map.
	 */
	public NavigableMap<K, V> createImmutableNavigableMap() {
		return createImmutableNavigableMap(MatchingKeyPolicy.DONT_CARE, null);
	}

	/**
	 * Executes the sorting for the currently added iterables and returns an iterator for the entries.
	 * <p>
	 * Callers <b>must</b> ensure that all previously added iterables are sorted by the argument comparator.
	 * <p>
	 * The actual sorting is done while the entries are being iterated. The class will not pre-allocate any storage or
	 * sort the entries before returning the iterator for the sorted entries.
	 * <p>
	 * The entries that the iterator iterates over are the same entry objects which are present in the iterables added
	 * to this sorter.
	 * 
	 * @param matchingPolicy
	 *            The matching policy for determining which values to keep for conflicting keys.
	 * @param comparator
	 *            The comparator that defines the sorting order, or <code>null</code> to use the natural order of the
	 *            keys.
	 * @return An iterator that iterates over the sorted entries.
	 * @throws NullPointerException
	 *             If the matching policy is <code>null</code>.
	 */
	public Iterator<? extends Entry<? extends K, ? extends V>> iterator(MatchingKeyPolicy matchingPolicy,
			Comparator<? super K> comparator) throws NullPointerException {
		if (!isAnyIterableAdded()) {
			return Collections.emptyIterator();
		}
		return createMergingIterator(matchingPolicy, comparator);
	}

	/**
	 * Executes the sorting for the currently added iterables and returns an iterator for the entries.
	 * <p>
	 * Callers <b>must</b> ensure that all previously added iterables are sorted by their natural order.
	 * <p>
	 * The actual sorting is done while the entries are being iterated. The class will not pre-allocate any storage or
	 * sort the entries before returning the iterator for the sorted entries.
	 * <p>
	 * The entries that the iterator iterates over are the same entry objects which are present in the iterables added
	 * to this sorter.
	 * <p>
	 * This is the same as:
	 * 
	 * <pre>
	 * iterator(matchingPolicy, null);
	 * </pre>
	 * 
	 * @param matchingPolicy
	 *            The matching policy for determining which values to keep for conflicting keys.
	 * @return An iterator that iterates over the sorted entries.
	 * @throws NullPointerException
	 *             If the matching policy is <code>null</code>.
	 */
	public Iterator<? extends Entry<? extends K, ? extends V>> iterator(MatchingKeyPolicy matchingPolicy)
			throws NullPointerException {
		return iterator(matchingPolicy, null);
	}

	/**
	 * Executes the sorting for the currently added iterables and returns an iterator for the entries.
	 * <p>
	 * Callers <b>must</b> ensure that all previously added iterables are sorted by the argument comparator.
	 * <p>
	 * The actual sorting is done while the entries are being iterated. The class will not pre-allocate any storage or
	 * sort the entries before returning the iterator for the sorted entries.
	 * <p>
	 * The entries that the iterator iterates over are the same entry objects which are present in the iterables added
	 * to this sorter.
	 * <p>
	 * This is the same as:
	 * 
	 * <pre>
	 * iterator(MatchingKeyPolicy.DONT_CARE, comparator);
	 * </pre>
	 * 
	 * @param comparator
	 *            The comparator that defines the sorting order, or <code>null</code> to use the natural order of the
	 *            keys.
	 * @return An iterator that iterates over the sorted entries.
	 */
	public Iterator<? extends Entry<? extends K, ? extends V>> iterator(Comparator<? super K> comparator) {
		return iterator(MatchingKeyPolicy.DONT_CARE, comparator);
	}

	/**
	 * Executes the sorting for the currently added iterables and returns an iterator for the entries.
	 * <p>
	 * Callers <b>must</b> ensure that all previously added iterables are sorted by their natural order.
	 * <p>
	 * The actual sorting is done while the entries are being iterated. The class will not pre-allocate any storage or
	 * sort the entries before returning the iterator for the sorted entries.
	 * <p>
	 * The entries that the iterator iterates over are the same entry objects which are present in the iterables added
	 * to this sorter.
	 * <p>
	 * This is the same as:
	 * 
	 * <pre>
	 * iterator(MatchingKeyPolicy.DONT_CARE, null);
	 * </pre>
	 * 
	 * @return An iterator that iterates over the sorted entries.
	 */
	public Iterator<? extends Entry<? extends K, ? extends V>> iterator() {
		return iterator(MatchingKeyPolicy.DONT_CARE, null);
	}

	private MergingIterator<K, V> createMergingIterator(MatchingKeyPolicy matchingPolicy,
			Comparator<? super K> mergecomparator) throws NullPointerException {
		Objects.requireNonNull(matchingPolicy, "matching policy");
		return new MergingIterator<>(matchingPolicy, mergecomparator, iterables);
	}

	private static final class MergingIterator<K, V> implements Iterator<Map.Entry<? extends K, ? extends V>> {
		private final MatchingKeyPolicy matchingPolicy;
		private final Comparator<IterableState<K, V>> stateComparator;

		private final IterableState<K, V>[] iterables;
		private final int maxSize;

		private int validIteratorStart = 0;
		private int validIteratorCount = 0;

		@SuppressWarnings({ "unchecked", "rawtypes" })
		public MergingIterator(MatchingKeyPolicy matchingPolicy, Comparator<? super K> mergecomparator,
				ConcurrentPrependAccumulator<MergerIterable<K, V>> mergeiterables) {
			this.matchingPolicy = matchingPolicy;
			if (mergecomparator == null) {
				this.stateComparator = (l, r) -> ((Comparable) l.nextEntry.getKey()).compareTo(r.nextEntry.getKey());
			} else {
				this.stateComparator = (l, r) -> mergecomparator.compare(l.nextEntry.getKey(), r.nextEntry.getKey());
			}
			IterableState<K, V>[] iterables = new IterableState[8];

			int iterableage = 0;

			int msize = 0;
			iterables_loop:
			for (MergerIterable<K, V> mergeiterable : mergeiterables) {
				Iterator<? extends Entry<? extends K, ? extends V>> it = mergeiterable.iterable.iterator();
				if (!it.hasNext()) {
					continue;
				}
				IterableState<K, V> state = new IterableState<>(it, it.next(), iterableage++);
				while (true) {
					int binaryidx = Arrays.binarySearch(iterables, 0, validIteratorCount, state, stateComparator);
					if (binaryidx >= 0) {
						//state with the same entry is already in the iterables
						state = matchingPolicy.apply(state, iterables, binaryidx);
						if (!state.iterator.hasNext()) {
							continue iterables_loop;
						}
						state.nextEntry = state.iterator.next();
					} else {
						//we found an insertion point
						if (validIteratorCount == iterables.length) {
							//grow the array if we filled it
							iterables = Arrays.copyOf(iterables, iterables.length * 2);
						}
						int insertidx = -binaryidx - 1;
						System.arraycopy(iterables, insertidx, iterables, insertidx + 1,
								validIteratorCount - insertidx);
						iterables[insertidx] = state;
						break;
					}
				}
				msize += mergeiterable.size;
				++validIteratorCount;
			}
			this.iterables = iterables;
			this.maxSize = msize;
		}

		@Override
		public boolean hasNext() {
			return validIteratorCount > 0;
		}

		@Override
		public Entry<? extends K, ? extends V> next() {
			if (validIteratorCount == 0) {
				throw new NoSuchElementException();
			}
			int validiterstart = this.validIteratorStart;
			int validitercount = this.validIteratorCount;
			IterableState<K, V>[] iterables = this.iterables;
			IterableState<K, V> firststate = iterables[validiterstart];
			final Entry<? extends K, ? extends V> result = firststate.nextEntry;
			while (true) {
				if (!firststate.iterator.hasNext()) {
					//the first iterator has no more elements, just remove it from the array
					++validIteratorStart;
					--validIteratorCount;
					break;
				}
				firststate.nextEntry = firststate.iterator.next();
				if (validitercount > 1) {
					//do a shortcut comparison with the second entry to quickly determine if it is still less
					int secondcmp = stateComparator.compare(firststate, iterables[validiterstart + 1]);
					if (secondcmp > 0) {
						//the first entry is greater than the second, so reordering needs to be done.
						if (validitercount == 2) {
							//there are only 2 iterables, just swap them
							iterables[validiterstart] = iterables[validiterstart + 1];
							iterables[validiterstart + 1] = firststate;
						} else {
							//start from the index 2, as we already compared with the second one to be greater than that
							int binaryidx = Arrays.binarySearch(iterables, validiterstart + 2,
									validiterstart + validitercount, firststate, stateComparator);
							if (binaryidx >= 0) {
								//the next entry is already in the array with an iterable
								//continue the loop so this iterator is going to the next
								firststate = matchingPolicy.apply(firststate, iterables, binaryidx);
								continue;
							}
							//insert the iterator to the specified position
							int insertindex = -binaryidx - 1;
							System.arraycopy(iterables, validiterstart + 1, iterables, validiterstart,
									insertindex - validiterstart - 1);
							iterables[insertindex - 1] = firststate;
						}
					} else if (secondcmp == 0) {
						//the first entry equals to the second.
						//continue the loop so this iterator is going to the next
						firststate = matchingPolicy.apply(firststate, iterables, validiterstart + 1);
						continue;
					}
					//else the first entry is strictly less than the second, this is fine
					//break out of the loop below
				}
				break;
			}

			return result;
		}

	}

	private static final class IterableState<K, V> {
		protected final Iterator<? extends Map.Entry<? extends K, ? extends V>> iterator;
		protected final int iterableAge;

		protected Map.Entry<? extends K, ? extends V> nextEntry;

		public IterableState(Iterator<? extends Entry<? extends K, ? extends V>> iterator,
				Map.Entry<? extends K, ? extends V> nextEntry, int iterableAge) {
			this.iterator = iterator;
			this.nextEntry = nextEntry;
			this.iterableAge = iterableAge;
		}

		@Override
		public String toString() {
			return Objects.toString(nextEntry);
		}
	}

	private static final class MergerIterable<K, V> {
		protected final Iterable<? extends Map.Entry<? extends K, ? extends V>> iterable;
		protected final int size;

		public MergerIterable(Iterable<? extends Map.Entry<? extends K, ? extends V>> iterable, int size) {
			this.iterable = iterable;
			this.size = size;
		}
	}

}
