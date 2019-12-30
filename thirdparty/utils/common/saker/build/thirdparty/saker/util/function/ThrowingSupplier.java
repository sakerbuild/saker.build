package saker.build.thirdparty.saker.util.function;

import java.util.function.Supplier;

/**
 * Functional interface similar to {@link Supplier}, except the {@link #get()} method is allowed to throw an arbitrary
 * exception.
 * <p>
 * The {@link #get()} method is declared to be able to throw {@link Exception}, therefore a functional interface such as
 * this can be used in contexts where users may throw checked exceptions.
 */
@FunctionalInterface
public interface ThrowingSupplier<T> {
	/**
	 * Runs the operations that the subclass defines and gets the result.
	 * 
	 * @return Gets the result of the supplier.
	 * @throws Exception
	 *             If the execution fails in any way.
	 */
	public T get() throws Exception;

}
