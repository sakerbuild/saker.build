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
package saker.build.thirdparty.saker.util.io;

import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;

import saker.build.thirdparty.saker.util.ArrayIterator;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.function.IOConsumer;

/**
 * Utility class containing functions for dealing with I/O related functionality.
 * <p>
 * The class defines a set of exceptions that are considered to be safe to be caught, which means that the operation
 * will not abort if an exception of the following types are caught:
 * <ul>
 * <li>{@link StackOverflowError}: The execution should be able to recover from this.</li>
 * <li>{@link OutOfMemoryError}: The execution should be able to recover from this.</li>
 * <li>{@link LinkageError}: Happens when classes are no longer compatible with each other. This usually means that the
 * runtime classpath is different from the compile classpath. This is usually non recoverable, or hard to do, but is
 * safe to catch, as this doesn't hinder the further operations on different objects.</li>
 * <li>{@link ServiceConfigurationError}: When some service configuration is invalid. This can be caught, as this is a
 * recoverable error that is more like a {@link RuntimeException}.</li>
 * <li>{@link AssertionError}: In case some assertion failed. An error of this kind is recoverable, as this doesn't
 * signal a VM error, but only some runtime validation failed in some implementation.</li>
 * <li>{@link Exception}: Generic exception, can be caught.</li>
 * </ul>
 * An exception that is an instance of the above types are considered to be <code>safe</code>, and is referred to such
 * in the documentation of utility functions. As a general rule of thumb, if an operation (e.g. closing) is to be
 * performed on multiple objects, and a safe exception is caught, the operation will continue to execute the action on
 * the remaining elements, and throw the exception after the action was performed on all of the objects.
 * <p>
 * The term <code>safe errors</code> contain only the {@link Error} types from the safe exceptions.
 * <p>
 * Many of the methods in this class work with {@link AutoCloseable} instances, but declare only {@link IOException} as
 * its thrown exception types. When an {@link AutoCloseable} throws an {@link Exception} declared by its
 * {@link AutoCloseable#close()} method, it will be wrapped into an {@link IOException}, and rethrown as that. As in
 * most cases users work with {@link Closeable} types, which throw {@link IOException}, this is an acceptable compromise
 * to support {@link AutoCloseable} types as well, but have them work like {@link Closeable Closeables}.
 * <p>
 * If the above happens for multiple {@link AutoCloseable AutoCloseables}, only the first caught exception will be
 * wrapped into an {@link IOException}, the laters will be added as suppressed exceptions to the first caught exception.
 * <p>
 * Methods that start with <code>close</code> will call {@link AutoCloseable#close()} on the argument object(s). The
 * closing will occurr for all argument objects, and any exception caught will be handled according to each method
 * documentation. Using this methods can be useful when multiple closeables need to be closed without having a nested
 * try-finally block in the caller method.
 * <p>
 * Example: <br>
 * Instead of closing multiple closeables like this:
 * 
 * <pre>
 * try {
 * 	closeable1.close();
 * } finally {
 * 	try {
 * 		closeable2.close();
 * 	} finally {
 * 		try {
 * 			closeable3.close();
 * 		} finally {
 * 			closeable4.close();
 * 		}
 * 	}
 * }
 * </pre>
 * 
 * Use:
 * 
 * <pre>
 * IOUtils.close(closeable1, closeable2, closeable3, closeable4);
 * </pre>
 * 
 * The latter approach also have the advantage that all the exceptions will be reported as suppressed, or cause
 * exceptions, instead of just the last in the first approach.
 * <p>
 * The closing methods in this class will silently ignore if the argument collection is <code>null</code>, or any of the
 * closeable object is <code>null</code>.
 */
public class IOUtils {
	/**
	 * Calls {@link AutoCloseable#close()} for every object in the argument iterable, and throws any exceptions at the
	 * end.
	 * <p>
	 * Any thrown safe errors defined by this class do not abort the operation, but they are rethrown at the end.
	 * 
	 * @param closeables
	 *            The closeables.
	 * @throws IOException
	 *             If any exception is caught during the closing of the objects.
	 */
	public static void close(AutoCloseable... closeables) throws IOException {
		if (closeables == null) {
			return;
		}
		close(new ArrayIterator<>(closeables));
	}

	/**
	 * Calls {@link AutoCloseable#close()} for every object in the argument iterable, and throws any exceptions at the
	 * end.
	 * <p>
	 * Any thrown safe errors defined by this class do not abort the operation, but they are rethrown at the end.
	 * 
	 * @param closeables
	 *            The closeables.
	 * @throws IOException
	 *             If any exception is caught during the closing of the objects.
	 */
	public static void close(Iterable<? extends AutoCloseable> closeables) throws IOException {
		if (closeables == null) {
			return;
		}
		close(closeables.iterator());
	}

	/**
	 * Calls {@link AutoCloseable#close()} for every object in the argument iterable, and throws any exceptions at the
	 * end.
	 * <p>
	 * Any thrown safe errors defined by this class do not abort the operation, but they are rethrown at the end.
	 * 
	 * @param closeables
	 *            The closeables.
	 * @throws IOException
	 *             If any exception is caught during the closing of the objects.
	 */
	public static void close(Iterator<? extends AutoCloseable> closeables) throws IOException {
		if (closeables == null) {
			return;
		}
		Error err = null;
		IOException exc = null;
		while (closeables.hasNext()) {
			AutoCloseable c = closeables.next();
			if (c == null) {
				continue;
			}
			try {
				c.close();
			} catch (Exception e) {
				exc = addExc(exc, e);
			} catch (StackOverflowError | OutOfMemoryError | LinkageError | ServiceConfigurationError
					| AssertionError e) {
				err = addExc(err, e);
			}
		}
		Throwable t = addExc((Throwable) exc, err);
		if (t != null) {
			//throw errors as they directly are
			throw ObjectUtils.sneakyThrow(t);
		}
	}

	/**
	 * Calls {@link AutoCloseable#close()} for every object in the argument iterable, and prints the stack trace of the
	 * possibly thrown exceptions.
	 * <p>
	 * Any thrown safe errors defined by this class do not abort the operation, but they are rethrown at the end.
	 * <p>
	 * Any {@link Exception Exceptions} thrown from closing methods will be printed using
	 * {@link Throwable#printStackTrace()}.
	 * 
	 * @param closeables
	 *            The closeables.
	 */
	public static void closePrint(AutoCloseable... closeables) {
		if (closeables == null) {
			return;
		}
		closePrint(new ArrayIterator<>(closeables));
	}

	/**
	 * Calls {@link AutoCloseable#close()} for every object in the argument iterable, and prints the stack trace of the
	 * possibly thrown exceptions.
	 * <p>
	 * Any thrown safe errors defined by this class do not abort the operation, but they are rethrown at the end.
	 * <p>
	 * Any {@link Exception Exceptions} thrown from closing methods will be printed using
	 * {@link Throwable#printStackTrace()}.
	 * 
	 * @param closeables
	 *            The closeables.
	 */
	public static void closePrint(Iterable<? extends AutoCloseable> closeables) {
		if (closeables == null) {
			return;
		}
		closePrint(closeables.iterator());
	}

	/**
	 * Calls {@link AutoCloseable#close()} for every object in the argument iterable, and prints the stack trace of the
	 * possibly thrown exceptions.
	 * <p>
	 * Any thrown safe errors defined by this class do not abort the operation, but they are rethrown at the end.
	 * <p>
	 * Any {@link Exception Exceptions} thrown from closing methods will be printed using
	 * {@link Throwable#printStackTrace()}.
	 * 
	 * @param closeables
	 *            The closeables.
	 */
	public static void closePrint(Iterator<? extends AutoCloseable> closeables) {
		if (closeables == null) {
			return;
		}
		Error err = null;
		IOException exc = null;
		while (closeables.hasNext()) {
			AutoCloseable c = closeables.next();
			if (c == null) {
				continue;
			}
			try {
				c.close();
			} catch (Exception e) {
				exc = addExc(exc, e);
			} catch (StackOverflowError | OutOfMemoryError | LinkageError | ServiceConfigurationError
					| AssertionError e) {
				err = addExc(err, e);
			}
		}
		printExc(exc);
		//errors need to be rethrown
		throwExc(err);
	}

	/**
	 * Calls {@link AutoCloseable#close()} for every object in the argument iterable, and ignores the possibly thrown
	 * exceptions.
	 * <p>
	 * Any thrown safe errors defined by this class do not abort the operation, but they are rethrown at the end.
	 * 
	 * @param closeables
	 *            The closeables.
	 */
	public static void closeIgnore(AutoCloseable... closeables) {
		if (closeables == null) {
			return;
		}
		closeIgnore(new ArrayIterator<>(closeables));
	}

	/**
	 * Calls {@link AutoCloseable#close()} for every object in the argument iterable, and ignores the possibly thrown
	 * exceptions.
	 * <p>
	 * Any thrown safe errors defined by this class do not abort the operation, but they are rethrown at the end.
	 * 
	 * @param closeables
	 *            The closeables.
	 */
	public static void closeIgnore(Iterable<? extends AutoCloseable> closeables) {
		if (closeables == null) {
			return;
		}
		closeIgnore(closeables.iterator());
	}

	/**
	 * Calls {@link AutoCloseable#close()} for every object in the argument iterable, and ignores the possibly thrown
	 * exceptions.
	 * <p>
	 * Any thrown safe errors defined by this class do not abort the operation, but they are rethrown at the end.
	 * 
	 * @param closeables
	 *            The closeables.
	 */
	public static void closeIgnore(Iterator<? extends AutoCloseable> closeables) {
		if (closeables == null) {
			return;
		}
		if (closeables.hasNext()) {
			Error err = null;
			do {
				AutoCloseable c = closeables.next();
				if (c == null) {
					continue;
				}
				try {
					c.close();
				} catch (Exception ignored) {
				} catch (StackOverflowError | OutOfMemoryError | LinkageError | ServiceConfigurationError
						| AssertionError e) {
					err = addExc(err, e);
				}
			} while (closeables.hasNext());
			throwExc(err);
		}
	}

	/**
	 * Calls {@link AutoCloseable#close()} for every object in the argument iterable, and returns the thrown exception
	 * if any.
	 * <p>
	 * Any thrown safe errors defined by this class do not abort the operation, but they are collected.
	 * <p>
	 * It is strongly recommended that callers handle any returned exception somehow instead of ignoring it.
	 * 
	 * @param closeables
	 *            The closeables.
	 * @return The caught exception during closing, or <code>null</code> if there was none.
	 */
	public static IOException closeExc(AutoCloseable... closeables) {
		if (closeables == null) {
			return null;
		}
		return closeExc(new ArrayIterator<>(closeables));
	}

	/**
	 * Calls {@link AutoCloseable#close()} for every object in the argument iterable, and returns the thrown exception
	 * if any.
	 * <p>
	 * Any thrown safe errors defined by this class do not abort the operation, but they are collected.
	 * <p>
	 * It is strongly recommended that callers handle any returned exception somehow instead of ignoring it.
	 * 
	 * @param closeables
	 *            The closeables.
	 * @return The caught exception during closing, or <code>null</code> if there was none.
	 */
	public static IOException closeExc(Iterable<? extends AutoCloseable> closeables) {
		if (closeables == null) {
			return null;
		}
		return closeExc(closeables.iterator());
	}

	/**
	 * Calls {@link AutoCloseable#close()} for every object in the argument iterable, and returns the thrown exception
	 * if any.
	 * <p>
	 * Any thrown safe errors defined by this class do not abort the operation, but they are collected.
	 * <p>
	 * It is strongly recommended that callers handle any returned exception somehow instead of ignoring it.
	 * 
	 * @param closeables
	 *            The closeables.
	 * @return The caught exception during closing, or <code>null</code> if there was none.
	 */
	public static IOException closeExc(Iterator<? extends AutoCloseable> closeables) {
		try {
			close(closeables);
			return null;
		} catch (IOException e) {
			return e;
		}
	}

	/**
	 * Calls {@link AutoCloseable#close()} for every object in the argument iterable, and aggregates any thrown
	 * exception with the previous argument.
	 * <p>
	 * Any thrown safe errors defined by this class do not abort the operation, but they are collected.
	 * <p>
	 * If the previous argument serves as a base when collecting thrown exceptions. If there is a previous exception,
	 * any thrown exception will be added as suppressed exception to it, and that will be returned. If there is no
	 * previous, the caught exceptions is returned.
	 * <p>
	 * It is strongly recommended that callers handle any returned exception somehow instead of ignoring it.
	 * <p>
	 * This method can be used in a chained way:
	 * 
	 * <pre>
	 * IOException exc = null;
	 * exc = IOUtils.closeExc(exc, closeable1);
	 * exc = IOUtils.closeExc(exc, closeable2, closeable3);
	 * //handle the exception somehow
	 * IOUtils.throwExc(exc);
	 * </pre>
	 * 
	 * @param prevexc
	 *            The previous exception to aggregate any closing exception with or <code>null</code> if there was none.
	 * @param closeables
	 *            The closeables.
	 * @return The previous exception if non-<code>null</code>, or the caught exception during closing, or
	 *             <code>null</code> if there was none.
	 */
	public static IOException closeExc(IOException prevexc, AutoCloseable... closeables) {
		if (closeables == null) {
			return prevexc;
		}
		return closeExc(prevexc, new ArrayIterator<>(closeables));
	}

	/**
	 * Calls {@link AutoCloseable#close()} for every object in the argument iterable, and aggregates any thrown
	 * exception with the previous argument.
	 * <p>
	 * Any thrown safe errors defined by this class do not abort the operation, but they are collected.
	 * <p>
	 * If the previous argument serves as a base when collecting thrown exceptions. If there is a previous exception,
	 * any thrown exception will be added as suppressed exception to it, and that will be returned. If there is no
	 * previous, the caught exceptions is returned.
	 * <p>
	 * It is strongly recommended that callers handle any returned exception somehow instead of ignoring it.
	 * <p>
	 * This method can be used in a chained way:
	 * 
	 * <pre>
	 * IOException exc = null;
	 * exc = IOUtils.closeExc(exc, closeable1);
	 * exc = IOUtils.closeExc(exc, closeable2, closeable3);
	 * //handle the exception somehow
	 * IOUtils.throwExc(exc);
	 * </pre>
	 * 
	 * @param prevexc
	 *            The previous exception to aggregate any closing exception with or <code>null</code> if there was none.
	 * @param closeables
	 *            The closeables.
	 * @return The previous exception if non-<code>null</code>, or the caught exception during closing, or
	 *             <code>null</code> if there was none.
	 */
	public static IOException closeExc(IOException prevexc, Iterable<? extends AutoCloseable> closeables) {
		if (closeables == null) {
			return prevexc;
		}
		return closeExc(prevexc, closeables.iterator());
	}

	/**
	 * Calls {@link AutoCloseable#close()} for every object in the argument iterable, and aggregates any thrown
	 * exception with the previous argument.
	 * <p>
	 * Any thrown safe errors defined by this class do not abort the operation, but they are collected.
	 * <p>
	 * If the previous argument serves as a base when collecting thrown exceptions. If there is a previous exception,
	 * any thrown exception will be added as suppressed exception to it, and that will be returned. If there is no
	 * previous, the caught exceptions is returned.
	 * <p>
	 * It is strongly recommended that callers handle any returned exception somehow instead of ignoring it.
	 * <p>
	 * This method can be used in a chained way:
	 * 
	 * <pre>
	 * IOException exc = null;
	 * exc = IOUtils.closeExc(exc, closeable1);
	 * exc = IOUtils.closeExc(exc, closeable2, closeable3);
	 * //handle the exception somehow
	 * IOUtils.throwExc(exc);
	 * </pre>
	 * 
	 * @param prevexc
	 *            The previous exception to aggregate any closing exception with or <code>null</code> if there was none.
	 * @param closeables
	 *            The closeables.
	 * @return The previous exception if non-<code>null</code>, or the caught exception during closing, or
	 *             <code>null</code> if there was none.
	 */
	public static IOException closeExc(IOException prevexc, Iterator<? extends AutoCloseable> closeables) {
		IOException exc = closeExc(closeables);
		if (exc != null) {
			if (prevexc == null) {
				return exc;
			}
			prevexc.addSuppressed(exc);
		}
		return prevexc;
	}

	/**
	 * Aggregates two exceptions of the different type using the specified creator function.
	 * <p>
	 * This method works the same ways as {@link #addExc(IOException, Throwable)}, but uses the specified creator
	 * function to wrap the <code>next</code> exception into the type of the previous exception.
	 * 
	 * @param <E>
	 *            The previous exception type.
	 * @param <T>
	 *            The next exception type.
	 * @param prev
	 *            The previous exception or <code>null</code> if there was none.
	 * @param next
	 *            The next exception or <code>null</code> if there was none.
	 * @param creator
	 *            The creator function that is used when the next exception needs to be converted to the previous
	 *            exception type.
	 * @return The aggregated exception.
	 * @throws NullPointerException
	 *             If the creator function is <code>null</code>.
	 */
	public static <E extends Throwable, T extends Throwable> E addExc(E prev, T next,
			Function<? super T, ? extends E> creator) throws NullPointerException {
		if (next == null) {
			return prev;
		}
		if (prev == null) {
			Objects.requireNonNull(creator, "creator");
			prev = creator.apply(next);
		}
		prev.addSuppressed(next);
		return prev;
	}

	/**
	 * Aggregates two exceptions of the same type.
	 * <p>
	 * This method works the same ways as {@link #addExc(IOException, Throwable)}, but doesn't wrap the
	 * <code>next</code> exception into an {@link IOException}.
	 * 
	 * @param <E>
	 *            The exception type.
	 * @param prev
	 *            The previous exception or <code>null</code> if there was none.
	 * @param next
	 *            The next exception or <code>null</code> if there was none.
	 * @return The aggregated exception.
	 */
	public static <E extends Throwable> E addExc(E prev, E next) {
		if (next == null) {
			return prev;
		}
		if (prev == null) {
			return next;
		}
		prev.addSuppressed(next);
		return prev;
	}

	/**
	 * Aggregates two exceptions with an {@link IOException} base.
	 * <p>
	 * This method takes two exception arguments (only one of them can be non-<code>null</code>), and returns an
	 * aggregated exception based on them
	 * <p>
	 * If the next exception is <code>null</code>, the argument <code>prev</code> is returned.
	 * <p>
	 * If the <code>prev</code> argument is <code>null</code>, the next will be returned, possibly wrapping it in an
	 * {@link IOException}. If the <code>next</code> is already an instance if {@link IOException}, it will be plainly
	 * returned.
	 * <p>
	 * If both are non-<code>null</code>, then the <code>next</code> will be added to <code>prev</code> as suppressed
	 * exception, and <code>prev</code> is returned.
	 * 
	 * @param prev
	 *            The previous exception or <code>null</code> if there was none.
	 * @param next
	 *            The next exception or <code>null</code> if there was none.
	 * @return The aggregated exception.
	 */
	public static IOException addExc(IOException prev, Throwable next) {
		if (next == null) {
			return prev;
		}
		if (prev == null) {
			if (next instanceof IOException) {
				return (IOException) next;
			}
			return new IOException(next);
		}
		prev.addSuppressed(next);
		return prev;
	}

	/**
	 * Collects the argument exception into a list.
	 * <p>
	 * This method will add the argument exception to the specified {@link List} instance. If the given list is
	 * <code>null</code>, a new one will be instantiated, and returned. If non-<code>null</code>, the exception is added
	 * to it, and the list itself is returned.
	 * <p>
	 * If the exception is <code>null</code>, the argument list is directly returned.
	 * <p>
	 * This method can be used in a chain to lazily instantiate an exception container when any exceptions is thrown.
	 * Example:
	 * 
	 * <pre>
	 * List&lt;Exception&gt; excs = null;
	 * while (condition) {
	 * 	try {
	 * 		//some work
	 * 	} catch (Exception e) {
	 * 		excs = IOUtils.collectExc(excs, e);
	 * 	}
	 * }
	 * //handle the collected exceptions if any. 
	 * </pre>
	 * 
	 * @param <T>
	 *            The type of the exception.
	 * @param collector
	 *            The exception collector list or <code>null</code>, if none.
	 * @param exc
	 *            The exception to collect.
	 * @return The list that aggregates the exceptions.
	 */
	public static <T extends Throwable> List<T> collectExc(List<T> collector, T exc) {
		if (exc == null) {
			return collector;
		}
		if (collector == null) {
			collector = new ArrayList<>();
		}
		collector.add(exc);
		return collector;
	}

	/**
	 * Prints the stacktrace of the argument exception if it is non-<code>null</code>.
	 * 
	 * @param t
	 *            The exception.
	 */
	public static void printExc(Throwable t) {
		if (t != null) {
			t.printStackTrace();
		}
	}

	/**
	 * Throws the argument exception if it is non-<code>null</code>.
	 * 
	 * @param throwable
	 *            The exception.
	 * @throws T
	 *             The argument if non-<code>null</code>.
	 */
	public static <T extends Throwable> void throwExc(T throwable) throws T {
		if (throwable != null) {
			throw throwable;
		}
	}

	/**
	 * Executes the given I/O action for every object in the argument iterable.
	 * <p>
	 * The action will called in order for every object in the iterable. If an action throws an exception, it will be
	 * rethrown from this method after the remaining actions for the objects are called. Any thrown safe exception
	 * defined by this class do not abort the operation.
	 * 
	 * @param <T>
	 *            The type of the objects.
	 * @param objects
	 *            The objects to call the action for.
	 * @param action
	 *            The action.
	 * @throws IOException
	 *             If an action throws an exception. Non {@link IOException} exceptions will be wrapped into one.
	 * @throws NullPointerException
	 *             If the action is <code>null</code>.
	 */
	public static <T> void foreach(Iterable<? extends T> objects, IOConsumer<? super T> action)
			throws IOException, NullPointerException {
		if (objects == null) {
			return;
		}
		Iterator<? extends T> it = objects.iterator();
		if (it.hasNext()) {
			Objects.requireNonNull(action, "action");
			Error err = null;
			IOException exc = null;
			do {
				try {
					T t = it.next();
					action.accept(t);
				} catch (Exception e) {
					exc = addExc(exc, e);
				} catch (StackOverflowError | OutOfMemoryError | LinkageError | ServiceConfigurationError
						| AssertionError e) {
					err = addExc(err, e);
				}
			} while (it.hasNext());
			Throwable t = addExc((Throwable) exc, err);
			if (t != null) {
				//throw errors as they directly are
				throw ObjectUtils.sneakyThrow(t);
			}
		}
	}

	/**
	 * Acquires the lock in interruptable mode, and throws {@link InterruptedIOException} if the current thread is
	 * interrupted.
	 * <p>
	 * If the locking is interrupted, the current thread interrupt status will be set.
	 * 
	 * @param lock
	 *            The lock to acquire.
	 * @param message
	 *            The message to instantiate the new {@link InterruptedIOException} with. (May be <code>null</code>)
	 * @throws NullPointerException
	 *             If the lock is <code>null</code>.
	 * @throws InterruptedIOException
	 *             If the current thread is interrupted while acquiring the lock.
	 * @see Lock#lockInterruptibly()
	 * @see InterruptedIOException#InterruptedIOException(String)
	 * @since saker.util 0.8.4
	 */
	public static void lockIO(Lock lock, String message) throws NullPointerException, InterruptedIOException {
		Objects.requireNonNull(lock, "lock");
		try {
			lock.lockInterruptibly();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new InterruptedIOException(message);
		}
	}

	/**
	 * Acquires the lock in interruptable mode, and throws {@link InterruptedIOException} if the current thread is
	 * interrupted.
	 * <p>
	 * If the locking is interrupted, the current thread interrupt status will be set.
	 * 
	 * @param lock
	 *            The lock to acquire.
	 * @throws NullPointerException
	 *             If the lock is <code>null</code>.
	 * @throws InterruptedIOException
	 *             If the current thread is interrupted while acquiring the lock.
	 * @see Lock#lockInterruptibly()
	 * @see InterruptedIOException#InterruptedIOException()
	 * @since saker.util 0.8.4
	 */
	public static void lockIO(Lock lock) throws NullPointerException, InterruptedIOException {
		Objects.requireNonNull(lock, "lock");
		try {
			lock.lockInterruptibly();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new InterruptedIOException();
		}
	}

	/**
	 * Attempts to acquire the lock in interruptable mode, and throws {@link InterruptedIOException} if the current
	 * thread is interrupted.
	 * <p>
	 * If the locking is interrupted, the current thread interrupt status will be set.
	 * 
	 * @param lock
	 *            The lock to acquire.
	 * @param time
	 *            The maximum time to wait for the lock.
	 * @param unit
	 *            The time unit of the {@code time} argument.
	 * @param message
	 *            The message to instantiate the new {@link InterruptedIOException} with. (May be <code>null</code>)
	 * @return <code>true</code> if the lock was acquired and <code>false</code> if the waiting time elapsed before the
	 *             lock was acquired.
	 * @throws NullPointerException
	 *             If the lock is <code>null</code>.
	 * @throws InterruptedIOException
	 *             If the current thread is interrupted while acquiring the lock.
	 * @see Lock#tryLock(long, TimeUnit)
	 * @see InterruptedIOException#InterruptedIOException(String)
	 * @since saker.util 0.8.4
	 */
	public static boolean tryLockIO(Lock lock, long time, TimeUnit unit, String message)
			throws NullPointerException, InterruptedIOException {
		Objects.requireNonNull(lock, "lock");
		try {
			return lock.tryLock(time, unit);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new InterruptedIOException(message);
		}
	}

	/**
	 * Attempts to acquire the lock in interruptable mode, and throws {@link InterruptedIOException} if the current
	 * thread is interrupted.
	 * <p>
	 * If the locking is interrupted, the current thread interrupt status will be set.
	 * 
	 * @param lock
	 *            The lock to acquire.
	 * @param time
	 *            The maximum time to wait for the lock.
	 * @param unit
	 *            The time unit of the {@code time} argument.
	 * @return <code>true</code> if the lock was acquired and <code>false</code> if the waiting time elapsed before the
	 *             lock was acquired.
	 * @throws NullPointerException
	 *             If the lock is <code>null</code>.
	 * @throws InterruptedIOException
	 *             If the current thread is interrupted while acquiring the lock.
	 * @see Lock#tryLock(long, TimeUnit)
	 * @see InterruptedIOException#InterruptedIOException()
	 * @since saker.util 0.8.4
	 */
	public static boolean tryLockIO(Lock lock, long time, TimeUnit unit)
			throws NullPointerException, InterruptedIOException {
		Objects.requireNonNull(lock, "lock");
		try {
			return lock.tryLock(time, unit);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new InterruptedIOException();
		}
	}

	private IOUtils() {
		throw new UnsupportedOperationException();
	}
}
