package saker.build.thirdparty.saker.util.function;

import java.util.function.Function;

/**
 * Functional interface similar to {@link Function}, except the {@link #apply(Object)} method is allowed to throw an
 * arbitrary exception.
 * 
 * @param <T>
 *            The argument type of the function.
 * @param <R>
 *            The return type of the function.
 */
@FunctionalInterface
public interface ThrowingFunction<T, R> {
	/**
	 * Applies the function to the given argument.
	 * 
	 * @param value
	 *            The argument.
	 * @return The calculated result value.
	 * @throws Exception
	 *             In case the operation failed.
	 */
	public R apply(T value) throws Exception;
}