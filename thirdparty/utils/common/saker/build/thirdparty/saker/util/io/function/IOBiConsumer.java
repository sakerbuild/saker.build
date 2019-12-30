package saker.build.thirdparty.saker.util.io.function;

import java.io.IOException;
import java.util.function.BiConsumer;

/**
 * Functional interface similar to {@link BiConsumer} but is usable in an I/O error-prone context.
 * <p>
 * The method of this interface may throw an {@link IOException}.
 * 
 * @param <T>
 *            The type of the first argument.
 * @param <U>
 *            The type of the second argument.
 */
@FunctionalInterface
public interface IOBiConsumer<T, U> {
	/**
	 * Performs the operation for the given arguments.
	 * 
	 * @param t
	 *            The first argument.
	 * @param u
	 *            The second argument.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	void accept(T t, U u) throws IOException;
}
