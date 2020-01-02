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
package saker.build.util.cache;

import java.lang.ref.WeakReference;
import java.util.ServiceConfigurationError;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import saker.apiextract.api.PublicApi;
import saker.build.thirdparty.saker.util.ConcurrentPrependAccumulator;

/**
 * Static class for allowing clients to trim memory of objects when appropriate.
 * <p>
 * {@link MemoryTrimmer} serves as JVM-wide utility class to collect objects and trim their memory allocation when an
 * appropriate time is found to do it. This time is usually after a build execution is finished.
 * <p>
 * Clients can use the {@link #add(Object, Consumer)} function to post objects for trimming later.
 * <p>
 * All of the posted objects in this class are weakly referenced, except the trimmer functions. This allows premature
 * garbage collection of the trimmable objects. The trimmer functions should not strongly reference the trimmable
 * object, it will be passed to it as an argument when trimming occurs.
 * <p>
 * Trimming can be invoked manually by anyone using the functions starting with <code>trim</code>. The class ensures
 * that a trimming function for a single object will be only called at most once for each {@link #add(Object, Consumer)}
 * calls with it.
 */
@PublicApi
public class MemoryTrimmer {
	private static class TrimmerReference<T> extends WeakReference<T> {
		protected Consumer<? super T> trimmerFunction;

		public TrimmerReference(T referent, Consumer<? super T> trimmerfunction) {
			super(referent);
			this.trimmerFunction = trimmerfunction;
		}

		/**
		 * Trims the referent.
		 * 
		 * @return <code>true</code> if anything was trimmed.
		 */
		public boolean trim() {
			Consumer<? super T> tf = trimmerFunction;
			if (tf == null) {
				return false;
			}
			trimmerFunction = null;
			T obj = get();
			if (obj != null) {
				try {
					tf.accept(obj);
				} catch (Exception | StackOverflowError | LinkageError | AssertionError | OutOfMemoryError
						| ServiceConfigurationError e) {
					//catch common non-JVM death related errors
					e.printStackTrace();
				}
				return true;
			}
			return false;
		}
	}
	// XXX make the class configureable using system properties? (NO_TRIM, IMMEDIATE, DELAYED)

	private static final ConcurrentPrependAccumulator<TrimmerReference<?>> trimmerList = new ConcurrentPrependAccumulator<>();

	private MemoryTrimmer() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Posts the object for trimming using the trimmer function.
	 * <p>
	 * The trimmer function must not hold a strong reference to the object. The object will be passed to the trimmer
	 * function at the time of trimming.
	 * <p>
	 * Callers can expect the object to be trimmed in any time after initiating this method call. However, they must not
	 * expect that the trimming will always or even deterministicly occur.
	 * 
	 * @param object
	 *            The object to trim.
	 * @param trimmerfunction
	 *            The trimmer function.
	 */
	public static <T> void add(T object, Consumer<? super T> trimmerfunction) {
		trimmerList.add(new TrimmerReference<>(object, trimmerfunction));
	}

	/**
	 * Trims all the objects in the queue.
	 */
	public static void trimAll() {
		for (TrimmerReference<?> ref; (ref = trimmerList.take()) != null;) {
			ref.trim();
		}
	}

	/**
	 * Trims the objects in the queue with optional interruptability.
	 * <p>
	 * The trimming will stop if either no more objects remaining or the predicate returns <code>true</code>.
	 * 
	 * @param shouldinterruptpredicate
	 *            The interrupt predicate.
	 */
	public static void trimInterruptible(BooleanSupplier shouldinterruptpredicate) {
		//only call after each trimmed object
		//if a referent was already garbage collected, then we shouldn't call the predicate, as calling it too often might not be desirable
		//    and iterating through collected objects are fast enough to not be a performance problem
		for (TrimmerReference<?> ref; (ref = trimmerList.take()) != null;) {
			boolean trimmed = ref.trim();
			if (trimmed) {
				if (shouldinterruptpredicate.getAsBoolean()) {
					break;
				}
			}
		}
	}
}
