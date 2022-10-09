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

import java.util.concurrent.locks.AbstractQueuedSynchronizer;
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

	private LazySupplier(Function<? super LazySupplier<? extends T>, T> initer) {
		this.state = new FunctionLazyState<>(initer);
	}

	private LazySupplier(Supplier<? extends T> initer) {
		this.state = new SupplierLazyState<>(initer);
	}

	/**
	 * Checks if the result value of this lazy supplier has already been computed.
	 * 
	 * @return <code>true</code> if the result is ready.
	 */
	public boolean isComputed() {
		return !(state instanceof LazyState);
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
		if (s instanceof LazyState) {
			return null;
		}
		return (T) s;
	}

	/**
	 * Gets the computed result of this lazy supplier if ready, or prevents the computation of it in future calls.
	 * <p>
	 * If the value of this lazy supplier has been already computed, or is being computed concurrently, this method
	 * returns the computed value. If it hasn't been computed yet, and is not being concurrently computed, it will
	 * return <code>null</code>, and prevent the initialization of this supplier. (All future {@link #get()} and other
	 * calls will return <code>null</code>.)
	 * 
	 * @return The computed value or <code>null</code> if not yet computed.
	 */
	@SuppressWarnings("unchecked")
	public T getIfComputedPrevent() {
		Object s = state;
		if (s instanceof LazyState<?>) {
			LazyState<T> ss = (LazyState<T>) s;
			T val;

			ss.acquireLock();
			try {
				Object cs = this.state;
				if (cs == ss) {
					this.state = null;
					val = null;
				} else {
					val = (T) cs;
				}
			} finally {
				ss.releaseLock();
			}

			return val;
		}
		return (T) s;
	}

	@SuppressWarnings("unchecked")
	@Override
	public T get() {
		Object s = state;
		if (s instanceof LazyState<?>) {
			LazyState<T> ss = (LazyState<T>) s;
			T val;

			ss.acquireLock();
			try {
				Object cs = this.state;
				if (cs == ss) {
					val = ss.getInitialValue(this);
					this.state = val;
				} else {
					val = (T) cs;
				}
			} finally {
				ss.releaseLock();
			}

			return val;
		}
		return (T) s;
	}

	@Override
	public String toString() {
		Object s = state;
		if (s instanceof LazyState<?>) {
			return getClass().getSimpleName() + "[not yet computed]";
		}
		return getClass().getSimpleName() + "[" + s + "]";
	}

	//extend from AbstractQueuedSynchronizer to avoid allocating a Semaphore for synchronization
	//results in fewer object allocations
	private abstract static class LazyState<T> extends AbstractQueuedSynchronizer {
		private static final long serialVersionUID = 1L;

		private static final int STATE_AVAILABLE = 1;
		private static final int STATE_LOCKED = 0;

		public abstract T getInitialValue(LazySupplier<T> supplier);

		public LazyState() {
			//set permits for synchronization
			setState(STATE_AVAILABLE);
		}

		protected void acquireLock() {
			Thread currentthread = Thread.currentThread();
			if (!tryAcquire()) {
				if (getExclusiveOwnerThread() == currentthread) {
					throw new IllegalThreadStateException("LazySupplier initializer method called recursively.");
				}
				acquireUninterruptibly();
			}
			setExclusiveOwnerThread(currentthread);
		}

		protected void releaseLock() {
			setExclusiveOwnerThread(null);
			release(0); // arg is ignored
		}

		@Override
		protected boolean tryRelease(int ignored) {
			return compareAndSetState(STATE_LOCKED, STATE_AVAILABLE);
		}

		@Override
		protected boolean tryAcquire(int ignored) {
			return compareAndSetState(STATE_AVAILABLE, STATE_LOCKED);
		}

		private final boolean tryAcquire() {
			return compareAndSetState(STATE_AVAILABLE, STATE_LOCKED);
		}

		private final void acquireUninterruptibly() {
			acquire(0); // arg is ignored
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder(getClass().getSimpleName());
			builder.append("[]");
			return builder.toString();
		}

	}

	private static class SupplierLazyState<T> extends LazyState<T> {
		private static final long serialVersionUID = 1L;

		protected final Supplier<? extends T> initer;

		public SupplierLazyState(Supplier<? extends T> initer) {
			this.initer = initer;
		}

		@Override
		public T getInitialValue(LazySupplier<T> supplier) {
			return initer.get();
		}
	}

	private static class FunctionLazyState<T> extends LazyState<T> {
		private static final long serialVersionUID = 1L;

		protected final Function<? super LazySupplier<T>, ? extends T> initer;

		public FunctionLazyState(Function<? super LazySupplier<T>, ? extends T> initer) {
			this.initer = initer;
		}

		@Override
		public T getInitialValue(LazySupplier<T> supplier) {
			return initer.apply(supplier);
		}
	}

}
