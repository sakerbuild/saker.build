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
package saker.build.thirdparty.saker.util.function;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * {@link Supplier} implementation that will lazily compute its result value once and return that in the following
 * calls.
 * <p>
 * This class will take a computer function that computes its final result value. The computer function will be called
 * the first time {@link #get()} is called on this supplier. The computer function is only called once during the
 * lifetime of this object, and its return value will be retained. Any following calls to {@link #get()} will return the
 * computed object.
 * <p>
 * Use the static <code>of(...)</code> methods to create a new instance.
 * 
 * @param <T>
 *            The type of the lazily computed object.
 */
public final class LazySupplier<T> implements Supplier<T> {
	/**
	 * Creates a new lazy supplier that uses the argument supplier to compute the lazy value.
	 * 
	 * @param initer
	 *            The value computer.
	 * @return The created lazy supplier.
	 */
	public static <Type> LazySupplier<Type> of(Supplier<? extends Type> initer) {
		return new LazySupplier<>(initer);
	}

	/**
	 * Creates a new lazy supplier that uses the argument function to compute the lazy value.
	 * <p>
	 * The function will be applied with the returned lazy supplier as its function argument.
	 * <p>
	 * Using this function to create a lazy supplier can avoid the chicken-egg problem when the computing function
	 * wishes to reference the created lazy supplier in its computing function. Some computer functions may have an
	 * use-case for this, as the computer function may have side effects.
	 * 
	 * @param initer
	 *            The value computer.
	 * @return The created lazy supplier.
	 */
	public static <Type> LazySupplier<Type> of(Function<? super LazySupplier<? extends Type>, Type> initer) {
		return new LazySupplier<>(initer);
	}

	private volatile Object state;

//	private volatile Supplier<? extends T> initer;
//	private T value;

	private LazySupplier(Function<? super LazySupplier<? extends T>, T> initer) {
		this.state = new State<>(() -> initer.apply(this));
	}

	private LazySupplier(Supplier<? extends T> initer) {
		this.state = new State<>(initer);
	}

	/**
	 * Checks if the result value of this lazy supplier has already been computed.
	 * 
	 * @return <code>true</code> if the result is ready.
	 */
	public boolean isComputed() {
		return !(state instanceof State);
	}

	/**
	 * Gets the computed result of this lazy supplier if ready.
	 * <p>
	 * If the computer function returns <code>null</code>, this function will still return <code>null</code> even if the
	 * result is already computed.
	 * 
	 * @return The computed result or <code>null</code> if not yet computed.
	 */
	@SuppressWarnings("unchecked")
	public T getIfComputed() {
		Object s = state;
		if (s instanceof State) {
			return null;
		}
		return (T) s;
	}

	/**
	 * Gets the computed result of this lazy supplier if ready, or prevents the computation of it in future calls.
	 * <p>
	 * If the value of this lazy supplier has been already computed, or is being computed concurrently, this method
	 * returns the computed value. If it hasn't been computed yet, and is not being concurrently computed, it will
	 * return <code>null</code>, and prevent any future calls to this supplier to compute the result.
	 * 
	 * @return The computed value or <code>null</code> if not yet computed.
	 */
	@SuppressWarnings("unchecked")
	public T getIfComputedPrevent() {
		Object s = state;
		if (s instanceof State<?>) {
			State<? extends T> ss = (State<? extends T>) s;
			T val;

			ss.lock.lock();
			try {
				Object cs = this.state;
				if (cs == ss) {
					this.state = null;
					val = null;
				} else {
					val = (T) cs;
				}
			} finally {
				ss.lock.unlock();
			}

			return val;
		}
		return (T) s;
	}

	@SuppressWarnings("unchecked")
	@Override
	public T get() {
		Object s = state;
		if (s instanceof State<?>) {
			State<? extends T> ss = (State<? extends T>) s;
			T val;

			ss.lock.lock();
			try {
				Object cs = this.state;
				if (cs == ss) {
					val = ss.initer.get();
					this.state = val;
				} else {
					val = (T) cs;
				}
			} finally {
				ss.lock.unlock();
			}

			return val;
		}
		return (T) s;
	}

	@Override
	public String toString() {
		Object s = state;
		if (s instanceof State<?>) {
			return getClass().getSimpleName() + "[not yet computed]";
		}
		return getClass().getSimpleName() + "[" + s + "]";
	}

	private static class State<T> {
		protected final Supplier<? extends T> initer;
		protected final ReentrantLock lock;

		public State(Supplier<? extends T> initer) {
			this.initer = initer;
			this.lock = new ReentrantLock();
		}
	}
}
