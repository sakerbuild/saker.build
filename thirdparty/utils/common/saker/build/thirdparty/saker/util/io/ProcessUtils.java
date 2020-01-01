package saker.build.thirdparty.saker.util.io;

import saker.build.thirdparty.saker.util.io.ProcessDestroyer;

/**
 * Utility class containing functions regarding process manipulation.
 *
 * @see Process
 */
public class ProcessUtils {
	private ProcessUtils() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Destroys a process and possibly all of its spawned child processes.
	 * <p>
	 * <b>Important:</b> This method works differently on different JRE versions.
	 * <p>
	 * On JDK 8, this will simply call {@link Process#destroy()}. Child processes spawned by the process are not
	 * affected.
	 * <p>
	 * On JDK 9 and later, this will retrieve the process handle, and destroy the process itself and the children too,
	 * recursively. <br>
	 * The process handle API was introduced in JDK 9. Using it the spawned children can be retrieved from a process,
	 * and iteratively destroyed. The destruction is done by querying if the process supports normal termination, and if
	 * so, <code>destroy()</code> will be called on them. If they don't support normal termination, or the
	 * <code>destroy()</code> call returned <code>false</code>, <code>destroyForcibly()</code> will be called on them.
	 * <p>
	 * This function should be considered as a last resort for destroying a process. Callers are strongly recommended to
	 * make sure that the process is exited cleanly by using some interprocess communication methods.
	 * <p>
	 * 
	 * @param p
	 *            The process to destroy.
	 */
	public static void destroyProcessAndPossiblyChildren(Process p) {
		//TODO test this on unix based operating systems (JDK9+)
		ProcessDestroyer.destroyProcessAndPossiblyChildren(p);
	}

	/**
	 * Gets the {@linkplain Process#exitValue() exit code} of the argument process, if it has exited.
	 * 
	 * @param p
	 *            The process.
	 * @return The exit code, or <code>null</code> if it has not yet terminated.
	 */
	public static Integer getExitCodeIfExited(Process p) {
		try {
			return p.exitValue();
		} catch (IllegalThreadStateException e) {
		}
		return null;
	}
}
