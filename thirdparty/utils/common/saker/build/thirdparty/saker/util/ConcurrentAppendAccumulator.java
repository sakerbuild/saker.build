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
 * This class collects the elements in an appending manner, meaning that iteration order is the same as insertion order.
 * <p>
 * Iterators returned by this class are <i>weakly consistent</i>.
 * <p>
 * If the user of this class doesn't care about the iteration order, consider using
 * {@link ConcurrentPrependAccumulator}, as that might provide a slightly better concurrent performance.
 * 
 * @param <T>
 *            The element type.
 */
public final class ConcurrentAppendAccumulator<T> implements Iterable<T> {
	private static final class NodeIterator<T> implements Iterator<T> {
		private Node<T> n;

		NodeIterator(Node<T> n) {
			this.n = n;
		}

		@Override
		public boolean hasNext() {
			return n != null;
		}

		@Override
		public T next() {
			if (n == null) {
				throw new NoSuchElementException();
			}
			T res = n.item;
			n = n.next;
			return res;
		}
	}

	private static final class Node<T> {
		@SuppressWarnings("rawtypes")
		private static final AtomicReferenceFieldUpdater<ConcurrentAppendAccumulator.Node, Node> ARFU_next = AtomicReferenceFieldUpdater
				.newUpdater(ConcurrentAppendAccumulator.Node.class, Node.class, "next");

		final T item;
		volatile Node<T> next;

		public Node(T item) {
			this.item = item;
		}
	}

	private static final class State<T> {
		@SuppressWarnings("rawtypes")
		private static final AtomicReferenceFieldUpdater<State, Node> ARFU_last = AtomicReferenceFieldUpdater
				.newUpdater(State.class, Node.class, "last");

		private final Node<T> first;

		volatile Node<T> last;

		public State(Node<T> first) {
			this.first = first;
			this.last = first;
		}

		void addConstruct(T item) {
			Node<T> n = new Node<>(item);
			this.last.next = n;
			this.last = n;
		}

		public void add(T item) {
			add(new Node<>(item));
		}

		public void add(Node<T> n) {
			//we need to set the next link before swapping the last, else the change may not be visible to other threads and can chain up badly
			while (true) {
				Node<T> last = this.last;
				if (Node.ARFU_next.compareAndSet(last, null, n)) {
					if (!ARFU_last.compareAndSet(this, last, n)) {
						//this should always succeed, as the last link cannot be swapped without successfully setting the next node first
						throw new AssertionError("Failed to CAS last link.");
					}
					return;
				}
				//just continue we need to wait until another thread swaps the last field
				//XXX we should yield or call Thread.onSpinWait() here, but that is only available on JDK9+...
			}
		}
	}

	@SuppressWarnings("rawtypes")
	private static final AtomicReferenceFieldUpdater<ConcurrentAppendAccumulator, State> ARFU_state = AtomicReferenceFieldUpdater
			.newUpdater(ConcurrentAppendAccumulator.class, State.class, "state");

	private volatile State<T> state;

	/**
	 * Creates a new instance without any elements.
	 */
	public ConcurrentAppendAccumulator() {
	}

	/**
	 * Creates a new instance and adds the argument array of elements to it.
	 * 
	 * @param elements
	 *            The initial elements.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public ConcurrentAppendAccumulator(T[] elements) throws NullPointerException {
		Objects.requireNonNull(elements, "elements");
		if (elements.length > 0) {
			T first = elements[0];
			state = new State<>(new Node<T>(first));
			for (int i = 1; i < elements.length; i++) {
				T item = elements[i];
				state.addConstruct(item);
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
	public ConcurrentAppendAccumulator(Iterable<? extends T> elements) throws NullPointerException {
		Objects.requireNonNull(elements, "elements");
		Iterator<? extends T> it = elements.iterator();
		if (it.hasNext()) {
			T first = it.next();
			state = new State<>(new Node<T>(first));
			while (it.hasNext()) {
				T item = it.next();
				state.addConstruct(item);
			}
		}
	}

	@Override
	public Iterator<T> iterator() {
		State<T> s = state;
		if (s == null) {
			return Collections.emptyIterator();
		}
		return new NodeIterator<>(s.first);
	}

	/**
	 * Clears this accumulator and returns an iterator for the cleared elements.
	 * 
	 * @return The iterator for the cleared elements.
	 */
	public Iterator<T> clearAndIterator() {
		@SuppressWarnings("unchecked")
		State<T> state = ARFU_state.getAndSet(this, null);
		if (state == null) {
			return Collections.emptyIterator();
		}
		return new NodeIterator<>(state.first);
	}

	/**
	 * Clears this accumulator and returns an iterable for the cleared elements.
	 * <p>
	 * The iterable will always iterate on the same elements, and modifications to this accumulator are not reflected on
	 * it.
	 * 
	 * @return The iterable for the cleared elements.
	 */
	public Iterable<T> clearAndIterable() {
		@SuppressWarnings("unchecked")
		State<T> state = ARFU_state.getAndSet(this, null);
		if (state == null) {
			return Collections.emptyList();
		}
		return () -> new NodeIterator<>(state.first);
	}

	/**
	 * Appends an element to this accumulator.
	 * 
	 * @param item
	 *            The element.
	 */
	public void add(T item) {
		//unroll 1 loop to delay instantiation of the new state
		State<T> s = state;
		if (s != null) {
			s.add(item);
			return;
		}
		Node<T> n = new Node<>(item);
		State<T> nstate = new State<>(n);
		while (true) {
			if (ARFU_state.compareAndSet(this, null, nstate)) {
				//successfully set state
				return;
			}
			s = state;
			if (s != null) {
				s.add(n);
				return;
			}
		}
	}

	/**
	 * Appends all elements from the argument iterable to this accumulator.
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
	 * Gets the current last element of this accumulator.
	 * 
	 * @return The last element, or <code>null</code> if it is <code>null</code> or the accumulator is empty.
	 */
	public T last() {
		State<T> s = state;
		if (s != null) {
			return s.last.item;
		}
		return null;
	}

	/**
	 * Gets the current first element of this accumulator.
	 * 
	 * @return The first element, or <code>null</code> if it is <code>null</code> or the accumulator is empty.
	 */
	public T first() {
		State<T> s = state;
		if (s != null) {
			return s.first.item;
		}
		return null;
	}

	/**
	 * Checks if there are any elements in this accumulator.
	 * 
	 * @return <code>true</code> if the accumulator is empty.
	 */
	public boolean isEmpty() {
		return state == null;
	}

	/**
	 * Clears this accumulator.
	 */
	public void clear() {
		this.state = null;
	}

	@Override
	public String toString() {
//		return StringUtils.join(", ", "[", "]", this);
		return StringUtils.toStringJoin("[", ", ", this, "]");
	}
}
