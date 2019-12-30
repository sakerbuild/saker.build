package saker.build.thirdparty.saker.util.io.function;

import java.io.IOException;
import java.util.function.BiFunction;

/**
 * Functional interface similar to {@link BiFunction} but is usable in an I/O error-prone context.
 * <p>
 * The method of this interface may throw an {@link IOException}.
 * 
 * @param <T>
 *            The type of the first argument.
 * @param <U>
 *            The type of the second argument.
 * @param <R>
 *            The return type of the function.
 */
@FunctionalInterface
public interface IOBiFunction<T, U, R> {
	/**
	 * Applies the function to the given arguments.
	 * 
	 * @param t
	 *            The first argument.
	 * @param u
	 *            The second argument.
	 * @return The calculated result value.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	R apply(T t, U u) throws IOException;
}