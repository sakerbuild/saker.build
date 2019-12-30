package saker.build.thirdparty.saker.util.io.function;

import java.io.IOException;

import saker.build.thirdparty.saker.util.function.TriFunction;

/**
 * Functional interface similar to {@link TriFunction} but is usable in an I/O error-prone context.
 * <p>
 * The method of this interface may throw an {@link IOException}.
 * 
 * @param <T>
 *            The type of the first argument.
 * @param <U>
 *            The type of the second argument.
 * @param <V>
 *            The type of the third argument.
 * @param <R>
 *            The return type of the function.
 */
@FunctionalInterface
public interface IOTriFunction<T, U, V, R> {
	/**
	 * Applies the function to the given arguments.
	 * 
	 * @param t
	 *            The first argument.
	 * @param u
	 *            The second argument.
	 * @param v
	 *            The third argument.
	 * @return The calculated result value.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public R accept(T t, U u, V v) throws IOException;
}
