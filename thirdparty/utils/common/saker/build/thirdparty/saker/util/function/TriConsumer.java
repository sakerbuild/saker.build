package saker.build.thirdparty.saker.util.function;

import java.util.function.Consumer;

/**
 * Functional interface similar to {@link Consumer}, but takes 3 arguments intead of 1.
 * 
 * @param <T>
 *            The type of the first argument.
 * @param <U>
 *            The type of the second argument.
 * @param <V>
 *            The type of the third argument.
 */
@FunctionalInterface
public interface TriConsumer<T, U, V> {
	/**
	 * Performs the operation for the given arguments.
	 * 
	 * @param t
	 *            The first argument.
	 * @param u
	 *            The second argument.
	 * @param v
	 *            The third argument.
	 */
	public void accept(T t, U u, V v);
}
