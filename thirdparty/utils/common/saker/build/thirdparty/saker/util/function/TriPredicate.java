package saker.build.thirdparty.saker.util.function;

import java.util.function.Predicate;

/**
 * Functional interface similar to {@link Predicate}, but takes 3 arguments instead of 1.
 * 
 * @param <T>
 *            The type of the first argument.
 * @param <U>
 *            The type of the second argument.
 * @param <V>
 *            The type of the third argument.
 */
@FunctionalInterface
public interface TriPredicate<T, U, V> {
	/**
	 * Tests if the arguments should be accepted in the context of the caller operation.
	 * 
	 * @param t
	 *            The first argument.
	 * @param u
	 *            The second argument.
	 * @param v
	 *            The third argument.
	 * @return <code>true</code> to accept the arguments.
	 */
	public boolean test(T t, U u, V v);
}
