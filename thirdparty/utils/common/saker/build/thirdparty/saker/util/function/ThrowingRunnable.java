package saker.build.thirdparty.saker.util.function;

/**
 * Functional interface similar to {@link Runnable}, except the {@link #run()} method is allowed to throw an arbitrary
 * exception.
 * <p>
 * The {@link #run()} method is declared to be able to throw {@link Exception}, therefore a functional interface such as
 * this can be used in contexts where users may throw checked exceptions.
 */
@FunctionalInterface
public interface ThrowingRunnable {
	/**
	 * Runs the operations that the subclass defines.
	 * 
	 * @throws Exception
	 *             If the execution fails in any way.
	 */
	public void run() throws Exception;

}
