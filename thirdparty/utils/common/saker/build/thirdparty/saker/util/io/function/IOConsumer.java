package saker.build.thirdparty.saker.util.io.function;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Functional interface similar to {@link Consumer} but is usable in an I/O error-prone context.
 * <p>
 * The method of this interface may throw an {@link IOException}.
 * 
 * @param <T>
 *            The type of the first argument.
 */
@FunctionalInterface
public interface IOConsumer<T> {
	/**
	 * Performs the operation for the given argument.
	 * 
	 * @param t
	 *            The first argument.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	void accept(T t) throws IOException;
}
