package saker.build.runtime.execution;

import saker.build.task.TaskProgressMonitor;

/**
 * Interface for monitoring and reporting progress of the build execution.
 */
public interface ExecutionProgressMonitor {

	/**
	 * Gets if the cancellation of the execution was requested for the build.
	 * 
	 * @return <code>true</code> if the build execution should be cancelled.
	 */
	public boolean isCancelled();

	/**
	 * Starts reporting the progress for a new task.
	 * 
	 * @return The created task progress monitor.
	 */
	public TaskProgressMonitor startTaskProgress();

	/**
	 * Creates a new progress monitor that ignores operations done on it.
	 * <p>
	 * Every method call on the result instance is a no-op.
	 * 
	 * @return A null progress monitor.
	 */
	public static ExecutionProgressMonitor nullMonitor() {
		return new NullProgressMonitor();
	}

	public static class NullProgressMonitor implements ExecutionProgressMonitor, TaskProgressMonitor {
		@Override
		public boolean isCancelled() {
			return false;
		}

		@Override
		public TaskProgressMonitor startTaskProgress() {
			return this;
		}
	}
}
