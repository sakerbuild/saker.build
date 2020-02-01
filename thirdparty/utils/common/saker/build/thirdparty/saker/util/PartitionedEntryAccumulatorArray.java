/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package saker.build.thirdparty.saker.util;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

/**
 * {@link EntryAccumulator} implementation that can accumulate entries for a pre-defined fixed capacity.
 * <p>
 * This class is similar to {@link EntryAccumulatorArray}, but is more suitable for use when the user expects varying,
 * but large number of elements to be accumulated.
 * <p>
 * This class doesn't allocate the whole array for the given capacity, but does it progressively as more entries are
 * added to it. The class divides the capacity into same sized partitions, and will allocate each partition when
 * necessary.
 * <p>
 * This class should be used when possibly both large and small number of entries may be accumulated.
 * <p>
 * This accumulator can not be used concurrently from multiple threads.
 * <p>
 * Use one of the static {@link #create(int, int)} functions to create a new instance. If the capacity is small enough,
 * only a simpler {@link EntryAccumulatorArray} will be created, instead of partitioning the array.
 * 
 * @param <K>
 *            The key type.
 * @param <V>
 *            The value type.
 */
public class PartitionedEntryAccumulatorArray<K, V> implements EntryAccumulator<K, V> {
	/**
	 * The default partition size when not specified by the caller.
	 */
	private static final int DEFAULT_PARTITION_SIZE = 4096;

	private final int partitionCapacity;
	private final Object[][] arrays;
	private int arrayIndex = 0;
	private Object[] currentArray;
	private int index;

	private PartitionedEntryAccumulatorArray(int arraycount, int partitionCapacity) {
		this.partitionCapacity = partitionCapacity;
		arrays = new Object[arraycount][];
		currentArray = new Object[partitionCapacity * 2];
		arrays[0] = currentArray;
	}

	/**
	 * Creates a new entry accumulator for the given maximum capacity and partition size.
	 * <p>
	 * The returned accumulator is capable of holding at most the specified capacity number of entries.
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param maxcapacity
	 *            The capacity to create an accumulator for.
	 * @param partitionsize
	 *            The partition size to use for dynamically allocating the underlying arrays.
	 * @return The created accumulator.
	 * @throws IllegalArgumentException
	 *             If the capacity or partition size is negative.
	 */
	public static <K, V> EntryAccumulator<K, V> create(int maxcapacity, int partitionsize)
			throws IllegalArgumentException {
		if (maxcapacity < 0) {
			throw new IllegalArgumentException("Capacity is negative. (" + maxcapacity + ")");
		}
		if (partitionsize < 0) {
			throw new IllegalArgumentException("Partition size is negative. (" + maxcapacity + ")");
		}
		if (maxcapacity < partitionsize) {
			return new EntryAccumulatorArray<>(maxcapacity);
		}
		return new PartitionedEntryAccumulatorArray<>((maxcapacity + partitionsize - 1) / partitionsize, partitionsize);
	}

	/**
	 * Creates a new entry accumulator that is capable of holding at most the specified capacity number of entries.
	 * <p>
	 * This is the same as {@link #create(int, int)} with using the default partition size.
	 * ({@value #DEFAULT_PARTITION_SIZE})
	 * 
	 * @param <K>
	 *            The key type.
	 * @param <V>
	 *            The value type.
	 * @param maxcapacity
	 *            The capacity to create an accumulator for.
	 * @return The created accumulator.
	 * @throws IllegalArgumentException
	 *             If the capacity is negative.
	 */
	public static <K, V> EntryAccumulator<K, V> create(int maxcapacity) throws IllegalArgumentException {
		return create(maxcapacity, DEFAULT_PARTITION_SIZE);
	}

	@Override
	public void put(K key, V value) {
		int idx = index;
		if (idx >= currentArray.length) {
			currentArray = new Object[partitionCapacity * 2];
			arrays[++arrayIndex] = currentArray;
			idx = 0;
		}
		currentArray[idx] = key;
		currentArray[idx + 1] = value;
		index = idx + 2;
	}

	@Override
	public void add(Entry<K, V> element) {
		put(element.getKey(), element.getValue());
	}

	@Override
	public int size() {
		return arrayIndex * partitionCapacity + index / 2;
	}

	@Override
	public Iterator<Entry<K, V>> iterator() {
		return new Iterator<Map.Entry<K, V>>() {
			private int itArrayIndex = 0;
			private Object[] itCurrentArray = arrays[0];
			private int itIndex = 0;
			private int count = 0;

			private final int end = size();

			@Override
			public boolean hasNext() {
				return count < end;
			}

			@SuppressWarnings("unchecked")
			@Override
			public Entry<K, V> next() {
				if (count >= end) {
					throw new NoSuchElementException();
				}
				int itidx = itIndex;
				if (itidx >= itCurrentArray.length) {
					itCurrentArray = arrays[++itArrayIndex];
					itidx = 0;
				}
				++count;
				K key = (K) itCurrentArray[itidx];
				V value = (V) itCurrentArray[itidx + 1];
				itIndex = itidx + 2;
				return ImmutableUtils.makeImmutableMapEntry(key, value);
			}
		};
	}
}
