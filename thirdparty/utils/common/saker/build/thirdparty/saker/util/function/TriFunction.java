package saker.build.thirdparty.saker.util.function;

import java.util.function.Function;

/**
 * Functional interface similar to {@link Function}, but takes 3 arguments instead of 1.
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
public interface TriFunction<T, U, V, R> {
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
	 */
	public R apply(T t, U u, V v);
}
