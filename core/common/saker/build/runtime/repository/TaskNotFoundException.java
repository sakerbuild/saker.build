/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
