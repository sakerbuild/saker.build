package saker.build.thirdparty.saker.util.io.function;

import java.io.IOException;
import java.util.function.Function;

/**
 * Functional interface similar to {@link Function} but is usable in an I/O error-prone context.
 * <p>
 * The method of this interface may throw an {@link IOException}.
 * 
 * @param <T>
 *            The type of the first argument.
 * @param <R>
 *            The return type of the function.
 */
@FunctionalInterface
public interface IOFunction<T, R> {
	/**
	 * Applies the function to the given argument.
	 * 
	 * @param t
	 *            The argument.
	 * @return The calculated result value.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	R apply(T t) throws IOException;
}