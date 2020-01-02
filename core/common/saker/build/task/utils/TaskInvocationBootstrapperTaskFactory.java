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
package saker.build.task.utils;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;

import saker.apiextract.api.PublicApi;
import saker.build.exception.PropertyComputationFailedException;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.repository.TaskNotFoundException;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionParameters;
import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;

/**
 * Bootstrapper task for looking up a task by a given name and invoking it with the given parameters.
 * <p>
 * This task can be used to look up a task based on a specified {@linkplain TaskName task name} and repository
 * identifier, and invoke it with the specified parameters. This task takes care of reporting the proper dependencies
 * for correct incremental behaviour.
 * <p>
 * The static method {@link #runBootstrapping(TaskContext, TaskName, NavigableMap, String, TaskExecutionParameters)} can
 * be called from other tasks to avoid starting this bootstrapper and still be able to invoke a task in the same way. In
 * that case the dependencies will be reported in the caller task.
 * <p>
 * The task identifier returned by {@link #getTaskIdentifier} should be used when starting an instance of this task
 * factory.
 * 
 * @see TaskInvocationRunnerTaskFactory
 */
@PublicApi
public final class TaskInvocationBootstrapperTaskFactory
		implements TaskFactory<StructuredObjectTaskResult>, Task<StructuredObjectTaskResult>, Externalizable {
	private static final long serialVersionUID = 1L;

	private TaskInvocationBootstrapperTaskIdentifier taskId;

	/**
	 * For {@link Externalizable}.
	 */
	public TaskInvocationBootstrapperTaskFactory() {
	}

	/**
	 * Creates a new instance with the given parameters.
	 * 
	 * @param taskName
	 *            The name of the task to look up and invoke.
	 * @param repository
	 *            The repository to search for the task in. May be <code>null</code>.
	 * @param parametersNameTaskIds
	 *            The parameters to pass to the invoked task. Passing <code>null</code> is same as no parameters.
	 * @throws NullPointerException
	 *             If the task name is <code>null</code>.
	 */
	public TaskInvocationBootstrapperTaskFactory(TaskName taskName, String repository,
			Map<String, TaskIdentifier> parametersNameTaskIds) throws NullPointerException {
		Objects.requireNonNull(taskName, "task name");
		this.taskId = new TaskInvocationBootstrapperTaskIdentifier(taskName, repository,
				ObjectUtils.isNullOrEmpty(parametersNameTaskIds) ? Collections.emptyNavigableMap()
						: ImmutableUtils.makeImmutableNavigableMap(parametersNameTaskIds));
	}

	/**
	 * Gets the task identifier which should be used when starting an instance of
	 * {@link TaskInvocationBootstrapperTaskFactory}.
	 * 
	 * @param task
	 *            The task to get the task identifier for.
	 * @return The task identifier.
	 */
	public static TaskIdentifier getTaskIdentifier(TaskInvocationBootstrapperTaskFactory task) {
		return task.taskId;
	}

	@Override
	public StructuredObjectTaskResult run(TaskContext taskcontext) throws Exception {
		TaskIdentifier taskid = runBootstrappingImpl(taskcontext, this.taskId.taskName,
				this.taskId.parametersNameTaskIds, this.taskId.repository, null);
		return new SimpleStructuredObjectTaskResult(taskid);
	}

	/**
	 * Executes the bootstrapping in the specified task context.
	 * <p>
	 * An {@link TaskLookupExecutionProperty} execution dependency will be reported for the task.
	 * 
	 * @param taskcontext
	 *            The task context.
	 * @param taskname
	 *            The name of the task to look up and invoke.
	 * @param parametersNameTaskIds
	 *            The parameters to pass to the invoked task. Passing <code>null</code> is same as no parameters.
	 * @param repository
	 *            The repository to search for the task in. May be <code>null</code>.
	 * @param taskexecutionparams
	 *            The task execution parameters to invoke the task with. May be <code>null</code>.
	 * @return The task identifier of the started task invocation.
	 * @throws TaskNotFoundException
	 *             If the task with the given name was not found.
	 * @throws NullPointerException
	 *             If task context or task name is <code>null</code>.
	 * @see TaskContext#startTask(TaskIdentifier, TaskFactory, TaskExecutionParameters)
	 */
	public static TaskIdentifier runBootstrapping(TaskContext taskcontext, TaskName taskname,
			NavigableMap<String, TaskIdentifier> parametersNameTaskIds, String repository,
			TaskExecutionParameters taskexecutionparams) throws TaskNotFoundException, NullPointerException {
		Objects.requireNonNull(taskcontext, "task context");
		Objects.requireNonNull(taskname, "task name");
		return runBootstrappingImpl(taskcontext, taskname, parametersNameTaskIds, repository, taskexecutionparams);
	}

	private static TaskIdentifier runBootstrappingImpl(TaskContext taskcontext, TaskName taskname,
			NavigableMap<String, TaskIdentifier> parametersNameTaskIds, String repository,
			TaskExecutionParameters taskexecutionparams) throws TaskNotFoundException {
		TaskFactory<?> factory;
		try {
			factory = taskcontext.getTaskUtilities()
					.getReportExecutionDependency(new TaskLookupExecutionProperty(taskname, repository));
		} catch (PropertyComputationFailedException e) {
			Throwable cause = e.getCause();
			if (cause instanceof TaskNotFoundException) {
				throw (TaskNotFoundException) cause;
			}
			throw new TaskNotFoundException(cause, taskname);
		}
		//XXX invoke a constructor that doesn't copy the param map
		TaskInvocationRunnerTaskFactory<?> invokerfactory = new TaskInvocationRunnerTaskFactory<>(taskname, factory,
				parametersNameTaskIds);
		TaskIdentifier taskid = TaskInvocationRunnerTaskFactory.getTaskIdentifier(invokerfactory);
		taskcontext.startTask(taskid, invokerfactory, taskexecutionparams);
		return taskid;
	}

	@Override
	public Task<? extends StructuredObjectTaskResult> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(taskId);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		taskId = (TaskInvocationBootstrapperTaskIdentifier) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((taskId == null) ? 0 : taskId.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TaskInvocationBootstrapperTaskFactory other = (TaskInvocationBootstrapperTaskFactory) obj;
		if (taskId == null) {
			if (other.taskId != null)
				return false;
		} else if (!taskId.equals(other.taskId))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (taskId != null ? "taskId=" + taskId : "") + "]";
	}

}
