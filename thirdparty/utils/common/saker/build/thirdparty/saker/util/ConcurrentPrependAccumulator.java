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

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Simplified container class that can be concurrently used to add elements to it.
 * <p>
 * This class can be used by multiple threads to add elements to it. The implementation does not use any synchronization
 * when inserting or retrieving elements from it. Instances of this class can be used when multiple producers need to
 * put their results to a common container.
 * <p>
 * Good use-case for this class is when lot of threads are doing computations, and are putting their result to a common
 * container.
 * <p>
 * This class collects the elements in an prepending manner, meaning that iteration order is the reverse of the
 * insertion order.
 * <p>
 * Iterators returned by this class are <i>weakly consistent</i>.
 * 
 * @param <T>
 *            The element type.
 */
public final class ConcurrentPrependAccumulator<T> implements Iterable<T> {
	private static final class AccumulatorIterableImpl<T> implements PeekableIterable<T> {
		static final AccumulatorIterableImpl<?> EMPTY_INSTANCE = new AccumulatorIterableImpl<>(null);
		private final Node<T> node;

		AccumulatorIterableImpl(Node<T> node) {
			this.node = node;
		}

		@Override
		public T peek() {
			Node<T> n = this.node;
			if (n == null) {
				return null;
			}
			return n.item;
		}

		@Override
		public boolean isEmpty() {
			return node == null;
		}

		@Override
		public Iterator<T> iterator() {
			return new EntryIterator<>(node);
		}

		@Override
		public String toString() {
			return StringUtils.toStringJoin("[", ", ", this, "]");
		}
	}

	private static final class EntryIterator<T> implements Iterator<T> {
		private Node<T> current;

		public EntryIterator(Node<T> current) {
			this.current = current;
		}

		@Override
		public boolean hasNext() {
			return this.current != null;
		}

		@Override
		public T next() {
			Node<T> c = this.current;
			if (c == null) {
				throw new NoSuchElementException();
			}
			this.current = c.next;
			return c.item;
		}
	}

	private static final class Node<T> {
		protected final T item;
		protected Node<T> next;

		public Node(T item) {
			this.item = item;
		}

		@Override
		public String toString() {
			return Objects.toString(item);
		}
	}

	@SuppressWarnings("rawtypes")
	private static final AtomicReferenceFieldUpdater<ConcurrentPrependAccumulator, Node> ARFU_first = AtomicReferenceFieldUpdater
			.newUpdater(ConcurrentPrependAccumulator.class, Node.class, "first");

	private volatile Node<T> first;

	/**
	 * Creates a new instance without any elements.
	 */
	public ConcurrentPrependAccumulator() {
	}

	/**
	 * Creates a new instance and adds the argument array of elements to it.
	 * 
	 * @param elements
	 *            The initial elements.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public ConcurrentPrependAccumulator(T[] elements) throws NullPointerException {
		Objects.requireNonNull(elements, "elements");
		if (elements.length > 0) {
			Node<T> n = new Node<>(elements[0]);
			this.first = n;
			for (int i = 1; i < elements.length; i++) {
				Node<T> nnext = new Node<>(elements[i]);
				n.next = nnext;
				n = nnext;
			}
		}
	}

	/**
	 * Creates a new instance and adds the elements from the argument iterable to it.
	 * 
	 * @param elements
	 *            The initial elements.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public ConcurrentPrependAccumulator(Iterable<? extends T> elements) throws NullPointerException {
		Objects.requireNonNull(elements, "elements");
		Iterator<? extends T> it = elements.iterator();
		Objects.requireNonNull(it, "iterator");
		if (it.hasNext()) {
			Node<T> n = new Node<>(it.next());
			this.first = n;
			while (it.hasNext()) {
				Node<T> nnext = new Node<>(it.next());
				n.next = nnext;
				n = nnext;
			}
		}
	}

	/**
	 * Prepends an element to this accumulator.
	 * 
	 * @param item
	 *            The element.
	 */
	public void add(T item) {
		Node<T> n = new Node<>(item);
		addNodeImpl(n);
	}

	/**
	 * Prepends an element if and only if the accumulator is currently empty.
	 * <p>
	 * If this method succeeds, the accumulator will only contain the element that was just added.
	 * 
	 * @param item
	 *            The element to add.
	 * @return <code>true</code> if the accumulator was empty and the element was successfully added.
	 * @since saker.util 0.8.3
	 */
	public boolean addIfEmpty(T item) {
		Node<T> n = new Node<>(item);
		return ARFU_first.compareAndSet(this, null, n);
	}

	private void addNodeImpl(Node<T> n) {
		while (true) {
			Node<T> f = this.first;
			n.next = f;
			if (ARFU_first.compareAndSet(this, f, n)) {
				//successfully set it
				return;
			}
			//try again with changed first
		}
	}

	/**
	 * Prepends all elements from the argument iterable to this accumulator.
	 * 
	 * @param elements
	 *            The iterable of elements.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public void addAll(Iterable<? extends T> elements) throws NullPointerException {
		Objects.requireNonNull(elements, "elements");
		elements.forEach(this::add);
	}

	/**
	 * Removes the first element from this accumulator.
	 * 
	 * @return The removed element, or <code>null</code> if it is <code>null</code> or the accumulator is empty.
	 */
	public T take() {
		while (true) {
			Node<T> n = this.first;
			if (n == null) {
				return null;
			}
			Node<T> next = n.next;
			if (ARFU_first.compareAndSet(this, n, next)) {
				return n.item;
			}
		}
	}

	/**
	 * Gets the first element in this accumulator.
	 * <p>
	 * This method doesn't remove the element itself, but just returns it.
	 * 
	 * @return The first element, or <code>null</code> if it is <code>null</code> or the accumulator is empty.
	 */
	public T peek() {
		Node<T> n = this.first;
		if (n == null) {
			return null;
		}
		return n.item;
	}

	@Override
	public Iterator<T> iterator() {
		Node<T> f = this.first;
		if (f == null) {
			return Collections.emptyIterator();
		}
		return new EntryIterator<>(f);
	}

	/**
	 * Clears this accumulator and returns an iterator for the cleared elements.
	 * 
	 * @return The iterator for the cleared elements.
	 */
	public Iterator<T> clearAndIterator() {
		@SuppressWarnings("unchecked")
		Node<T> node = ARFU_first.getAndSet(this, null);
		if (node == null) {
			return Collections.emptyIterator();
		}
		return new EntryIterator<>(node);
	}

	/**
	 * Clears this accumulator and returns an iterable for the cleared elements.
	 * <p>
	 * The iterable will always iterate on the same elements, and modifications to this accumulator are not reflected on
	 * it.
	 * 
	 * @return The iterable for the cleared elements.
	 */
	@SuppressWarnings("unchecked")
	public PeekableIterable<T> clearAndIterable() {
		Node<T> node = ARFU_first.getAndSet(this, null);
		if (node == null) {
			return (AccumulatorIterableImpl<T>) AccumulatorIterableImpl.EMPTY_INSTANCE;
		}
		return new AccumulatorIterableImpl<>(node);
	}

	/**
	 * Gets an iterable to the elements in this accumulator.
	 * <p>
	 * The returned iterable will always iterate over the same elements in the same order. Any modifications done on
	 * <code>this</code> will not be visible from the returned iterable.
	 * 
	 * @return An iterable view of this accumulator.
	 */
	@SuppressWarnings("unchecked")
	public PeekableIterable<T> iterable() {
		Node<T> node = this.first;
		if (node == null) {
			return (AccumulatorIterableImpl<T>) AccumulatorIterableImpl.EMPTY_INSTANCE;
		}
		return new AccumulatorIterableImpl<>(node);
	}

	/**
	 * Checks if there are any elements in this accumulator.
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
}
