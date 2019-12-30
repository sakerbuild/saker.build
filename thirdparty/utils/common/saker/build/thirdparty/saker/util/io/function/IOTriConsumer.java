package saker.build.thirdparty.saker.util.io.function;

import java.io.IOException;

import saker.build.thirdparty.saker.util.function.TriConsumer;

/**
 * Functional interface similar to {@link TriConsumer} but is usable in an I/O error-prone context.
 * <p>
 * The method of this interface may throw an {@link IOException}.
 * 
 * @param <T>
 *            The type of the first argument.
 * @param <U>
 *            The type of the second argument.
 * @param <V>
 *            The type of the third argument.
 */
@FunctionalInterface
public interface IOTriConsumer<T, U, V> {
	/**
	 * Performs the operation for the given arguments.
	 * 
	 * @param t
	 *            The first argument.
	 * @param u
	 *            The second argument.
	 * @param v
	 *            The third argument.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public void accept(T t, U u, V v) throws IOException;
}
