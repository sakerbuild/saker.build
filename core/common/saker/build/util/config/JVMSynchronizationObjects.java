package saker.build.util.config;

import saker.apiextract.api.PublicApi;

/**
 * This class contains singleton instances for controlling synchronized acces to JVM resources.
 * <p>
 * E.g. if one wants to replace the standard I/O streams, one should get the singleton lock instance and synchronize on
 * if to prevent race conditions.
 * <p>
 * If one needs multiple locks to acquire to do an operation then one should synchronize on them in <b>alphabetical
 * order</b>. This is in order to avoid deadlocks caused by different locking order. The alphabetical order should be
 * determined by the acquiring method name.
 * <p>
 * <i>Implementation note:</i> This class uses constant {@link String} instances to determine the locks. This is in
 * order to be able to provide a common lock object even if this class has been loaded multiple times by the JVM. See
 * {@link String#intern()}.
 */
@PublicApi
public class JVMSynchronizationObjects {
	private static final String STANDARD_IO_LOCK = "saker.build.util.config.STANDARD_IO_LOCK";

	private JVMSynchronizationObjects() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Gets the synchronization lock for accessing the {@link System} standard IO streams.
	 * 
	 * @return The lock for modifying the standard IO stream.
	 * @see System#setErr(java.io.PrintStream)
	 * @see System#setOut(java.io.PrintStream)
	 * @see System#setIn(java.io.InputStream)
	 */
	public static Object getStandardIOLock() {
		return STANDARD_IO_LOCK;
	}
}
