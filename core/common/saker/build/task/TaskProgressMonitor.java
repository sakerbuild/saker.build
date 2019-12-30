package saker.build.task;

/**
 * Interface for monitoring and reporting progress of tasks during execution.
 * <p>
 * This interface is planned to be extended in the future for reified information reporting.
 */
public interface TaskProgressMonitor {
	/**
	 * Gets if the cancellation of the execution was requested for the task.
	 * 
	 * @return <code>true</code> if the task execution should be cancelled.
	 */
	public boolean isCancelled();
}
