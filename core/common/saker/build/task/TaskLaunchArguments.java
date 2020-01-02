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
package saker.build.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

import saker.build.task.identifier.TaskIdentifier;

/**
 * Simple data class aggregating the information necessary for starting a task in the build system.
 * <p>
 * This class is most likely used via the {@link TaskExecutionUtilities} for the current task execution. Using this
 * class, the task can start multiple subtasks in a batch (i.e. with a single call) which can increase performance when
 * designing tasks for remote execution.
 * 
 * @param <R>
 *            The result type of the started task.
 * @see TaskContext#startTask(TaskIdentifier, TaskFactory, TaskExecutionParameters)
 * @see TaskExecutionUtilities
 */
public class TaskLaunchArguments<R> implements Externalizable {
	private static final long serialVersionUID = 1L;

	private TaskIdentifier taskIdentifier;
	private TaskFactory<R> taskFactory;
	private TaskExecutionParameters executionParameters;

	/**
	 * For {@link Externalizable}.
	 */
	public TaskLaunchArguments() {
	}

	/**
	 * Creates a new instance with the given arguments.
	 * 
	 * @param taskIdentifier
	 *            The identifier for the started task.
	 * @param taskFactory
	 *            The task factory to start.
	 * @param executionParameters
	 *            The execution parameters for the started task of <code>null</code>.
	 * @throws NullPointerException
	 *             If the task identifier or task factory is <code>null</code>.
	 */
	public TaskLaunchArguments(TaskIdentifier taskIdentifier, TaskFactory<R> taskFactory,
			TaskExecutionParameters executionParameters) throws NullPointerException {
		Objects.requireNonNull(taskIdentifier, "task identifier");
		Objects.requireNonNull(taskFactory, "task factory");
		this.taskIdentifier = taskIdentifier;
		this.taskFactory = taskFactory;
		this.executionParameters = executionParameters;
	}

	/**
	 * Creates a new instance with the given arguments.
	 * <p>
	 * The execution parameters is initialized to <code>null</code>.
	 * 
	 * @param taskIdentifier
	 *            The identifier for the started task.
	 * @param taskFactory
	 *            The task factory to start.
	 * @throws NullPointerException
	 *             If the task identifier or task factory is <code>null</code>.
	 */
	public TaskLaunchArguments(TaskIdentifier taskIdentifier, TaskFactory<R> taskFactory) throws NullPointerException {
		this(taskIdentifier, taskFactory, null);
	}

	/**
	 * Gets the task identifier of the started task.
	 * 
	 * @return The task identifier.
	 */
	public TaskIdentifier getTaskIdentifier() {
		return taskIdentifier;
	}

	/**
	 * Gets the started task factory.
	 * 
	 * @return The task factory.
	 */
	public TaskFactory<R> getTaskFactory() {
		return taskFactory;
	}

	/**
	 * Gets the execution parameters used to start the task.
	 * 
	 * @return The execution parameters or <code>null</code> if unset.
	 */
	public TaskExecutionParameters getExecutionParameters() {
		return executionParameters;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(taskIdentifier);
		out.writeObject(taskFactory);
		out.writeObject(executionParameters);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		taskIdentifier = (TaskIdentifier) in.readObject();
		taskFactory = (TaskFactory<R>) in.readObject();
		executionParameters = (TaskExecutionParameters) in.readObject();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "["
				+ (taskIdentifier != null ? "taskIdentifier=" + taskIdentifier + ", " : "")
				+ (taskFactory != null ? "taskFactory=" + taskFactory + ", " : "")
				+ (executionParameters != null ? "executionParameters=" + executionParameters : "") + "]";
	}

}