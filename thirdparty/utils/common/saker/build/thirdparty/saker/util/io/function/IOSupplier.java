package saker.build.thirdparty.saker.util.io.function;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Represents a supplier of results which may throw an {@link IOException}.
 * <p>
 * This functional interface is similar to {@link Supplier}, but with the addition of declaring a checked
 * {@link IOException} on the function.
 * 
 * @param <T>
 *            The result type of the supplier.
 * @see Supplier
 */
@FunctionalInterface
public interface IOSupplier<T> {
	/**
	 * Converts the argument supplier to an {@link IOSupplier}.
	 * 
	 * @param <T>
	 *            The generated type.
	 * @param supplier
	 *            The supplier.
	 * @return The converted {@link IOSupplier}.
	 */
	public static <T> IOSupplier<T> valueOf(Supplier<T> supplier) {
		return supplier::get;
	}

	/**
	 * Gets a result.
	 *
	 * @return A result.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public T get() throws IOException;
}