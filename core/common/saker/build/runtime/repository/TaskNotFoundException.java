package saker.build.runtime.repository;

import saker.build.task.TaskName;

/**
 * Exception for reporting the scenario when a task lookup by name failed to yield any result.
 */
public class TaskNotFoundException extends RepositoryException {
	private static final long serialVersionUID = 1L;

	/**
	 * The task name which was not found.
	 */
	protected TaskName taskName;

	/**
	 * Creates a new exception instance for the specified task name.
	 * 
	 * @param taskName
	 *            The task name which was not found.
	 */
	public TaskNotFoundException(TaskName taskName) {
		super("Task not found: " + taskName);
		this.taskName = taskName;
	}

	/**
	 * Creates a new exception for the specified task name and detail message.
	 * 
	 * @param message
	 *            The detail message.
	 * @param taskName
	 *            The task name which was not found.
	 */
	public TaskNotFoundException(String message, TaskName taskName) {
		super("Task not found: " + taskName + " (" + message + ")");
		this.taskName = taskName;
	}

	/**
	 * Creates a new exception for the specified task name and detail message and cause.
	 * 
	 * @param message
	 *            The detail message.
	 * @param cause
	 *            The cause of this exception.
	 * @param taskName
	 *            The task name which was not found.
	 */
	public TaskNotFoundException(String message, Throwable cause, TaskName taskName) {
		super("Task not found: " + taskName + " (" + message + ")", cause);
		this.taskName = taskName;
	}

	/**
	 * Creates a new exception for the specified task name and cause.
	 * 
	 * @param cause
	 *            The cause of this exception.
	 * @param taskName
	 *            The task name which was not found.
	 */
	public TaskNotFoundException(Throwable cause, TaskName taskName) {
		super("Task not found: " + taskName, cause);
		this.taskName = taskName;
	}

	/**
	 * Gets the task name which was not found.
	 * 
	 * @return The task name.
	 */
	public TaskName getTaskName() {
		return taskName;
	}
}
