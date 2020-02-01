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

import java.nio.BufferOverflowException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * {@link EntryAccumulator} implementation that can accumulate entries into a pre-allocated array with a fixed capacity.
 * <p>
 * Instances of this class is created for a given capacity. At most {@link #capacity()} number of entries can be added
 * to the accumulator. When the accumulator cannot store the added element, {@link BufferOverflowException} will be
 * thrown.
 * <p>
 * This accumulator can not be used concurrently from multiple threads.
 * <p>
 * When varying, but large number of elements are to be accumulated, consider using
 * {@link PartitionedEntryAccumulatorArray} as that can dynamically allocate the underlying array when necessary.
 * 
 * @param <K>
 *            The key type.
 * @param <V>
 *            The value type.
 */
public class EntryAccumulatorArray<K, V> implements EntryAccumulator<K, V> {
	private final Object[] items;
	private int index;

	/**
	 * Creates a new instance for the given capacity.
	 * 
	 * @param capacity
	 *            The capacity.
	 * @throws NegativeArraySizeException
	 *             If the capacity is negative.
	 */
	public EntryAccumulatorArray(int capacity) throws NegativeArraySizeException {
		this.items = new Object[capacity * 2];
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @throws BufferOverflowException
	 *             If no more space available for storing the entry.
	 */
	@Override
	public void put(K key, V value) throws BufferOverflowException {
		int idx = index;
		if (idx >= items.length) {
			throw new BufferOverflowException();
		}
		items[idx] = key;
		items[idx + 1] = value;
		index = idx + 2;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @throws NullPointerException
	 *             If the entry is <code>null</code>.
	 * @throws BufferOverflowException
	 *             If no more space available for storing the entry.
	 */
	@Override
	public void add(Entry<K, V> entry) throws NullPointerException, BufferOverflowException {
		Objects.requireNonNull(entry, "entry");
		put(entry.getKey(), entry.getValue());
	}

	@Override
	public int size() {
		return index / 2;
	}

	/**
	 * Gets the capacity of this accumulator.
	 * <p>
	 * At most {@link #capacity()} number of entries can be added to this instance.
	 * 
	 * @return The capacity.
	 */
	public int capacity() {
		return items.length / 2;
	}

	@Override
	public Iterator<Map.Entry<K, V>> iterator() {
		return new Iterator<Map.Entry<K, V>>() {
			private final int end = index;

			private int idx;

			@Override
			public boolean hasNext() {
				return idx < end;
			}

			@SuppressWarnings("unchecked")
			@Override
			public Entry<K, V> next() {
				int idx = this.idx;
				if (idx >= end) {
					throw new NoSuchElementException();
				}
				K key = (K) items[idx];
				V value = (V) items[idx + 1];
				this.idx = idx + 2;
				return ImmutableUtils.makeImmutableMapEntry(key, value);
			}
		};
	}

}
