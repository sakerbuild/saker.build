package saker.build.thirdparty.saker.util.io.function;

import java.io.IOException;

/**
 * Functional interface similar to {@link Runnable} but is usable in an I/O error-prone context.
 * <p>
 * The method of this interface may throw an {@link IOException}.
 */
@FunctionalInterface
public interface IORunnable {
	/**
	 * Runs the operations that the subclass defines.
	 * 
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public void run() throws IOException;
}
