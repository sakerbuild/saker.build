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
import java.util.Objects;
import java.util.Set;

import saker.apiextract.api.PublicApi;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.SakerLog;
import saker.build.task.ParameterizableTask;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.trace.BuildTrace;

/**
 * {@link TaskFactory} for running a specified task with the given parameters.
 * <p>
 * This task can be used to run dynamically looked up tasks in a build system compatible way. The implementation will
 * properly initialize and execute the specified task.
 * <p>
 * If the specified task is an instance of {@link ParameterizableTask}, then their parameters will be initialized
 * accordingly. If not, and parameters were specified for it, a warning will be emitted.
 * <p>
 * The task will set the specified {@link TaskName} as the
 * {@linkplain TaskContext#setStandardOutDisplayIdentifier(String) standard output display identifier}.
 * <p>
 * It is recommended that tasks use this task factory to invoke dynamically looked up tasks so they are not invoked
 * multiple times with the same parameters.
 * <p>
 * The task identifier returned by {@link #getTaskIdentifier} should be used when starting an instance of this task
 * factory.
 * 
 * @param <T>
 *            The result type of the invoked task.
 * @see TaskInvocationBootstrapperTaskFactory
 */
@PublicApi
public final class TaskInvocationRunnerTaskFactory<T> implements TaskFactory<T>, Task<T>, Externalizable {
	private static final long serialVersionUID = 1L;

	private TaskInvocationRunnerTaskIdentifier<T> taskId;

	/**
	 * For {@link Externalizable}.
	 */
	public TaskInvocationRunnerTaskFactory() {
	}

	/**
	 * Creates a new instance with the given arguments.
	 * 
	 * @param taskName
	 *            The task name of the specified task. May be <code>null</code>. It is only used for warnings, and
	 *            setting the display identifier for the invoked tasks. (transient)
	 * @param taskFactory
	 *            The task factory to invoke.
	 * @param parametersNameTaskIds
	 *            The parameters to pass to the invoked task. Passing <code>null</code> is the same as no parameters.
	 * @throws NullPointerException
	 *             If the task factory is <code>null</code>.
	 */
	public TaskInvocationRunnerTaskFactory(TaskName taskName, TaskFactory<T> taskFactory,
			Map<String, TaskIdentifier> parametersNameTaskIds) throws NullPointerException {
		Objects.requireNonNull(taskFactory, "task factory");
		this.taskId = new TaskInvocationRunnerTaskIdentifier<>(taskName, taskFactory,
				ObjectUtils.isNullOrEmpty(parametersNameTaskIds) ? Collections.emptyNavigableMap()
						: ImmutableUtils.makeImmutableNavigableMap(parametersNameTaskIds));
	}

	/**
	 * Gets the task identifier which should be used when starting an instance of
	 * {@link TaskInvocationRunnerTaskFactory}.
	 * 
	 * @param task
	 *            The task to get the task identifier for.
	 * @return The task identifier.
	 */
	public static TaskIdentifier getTaskIdentifier(TaskInvocationRunnerTaskFactory<?> task) {
		return task.taskId;
	}

	@Override
	public Task<? extends T> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public Set<String> getCapabilities() {
		return this.taskId.taskFactory.getCapabilities();
	}

	@Override
	public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
		return this.taskId.taskFactory.getExecutionEnvironmentSelector();
	}

	@Override
	public int getRequestedComputationTokenCount() {
		return this.taskId.taskFactory.getRequestedComputationTokenCount();
	}

	@Override
	public T run(TaskContext taskcontext) throws Exception {
		Task<? extends T> realsubtask = this.taskId.taskFactory.createTask(taskcontext.getExecutionContext());
		if (realsubtask == null) {
			throw new NullPointerException(
					"Task factory created null task: " + this.taskId.taskFactory.getClass().getName());
		}

		if (this.taskId.taskName != null) {
			taskcontext.setStandardOutDisplayIdentifier(this.taskId.taskName.toString());
		}

		BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_EXTERNAL);

		if (realsubtask instanceof ParameterizableTask) {
			ParameterizableTask<?> paramtask = (ParameterizableTask<?>) realsubtask;
			paramtask.initParameters(taskcontext, this.taskId.parametersNameTaskIds);
		} else if (!this.taskId.parametersNameTaskIds.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			sb.append("Parameters were provided for non parameterizable task: ");
			if (this.taskId.taskName != null) {
				sb.append(this.taskId.taskName);
				sb.append(" : ");
			}
			sb.append(this.taskId.taskFactory.getClass().getName());
			SakerLog.warning().println(sb.toString());
		}
		return realsubtask.run(taskcontext);
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
		TaskInvocationRunnerTaskFactory<?> other = (TaskInvocationRunnerTaskFactory<?>) obj;
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

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(taskId);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		taskId = (TaskInvocationRunnerTaskIdentifier<T>) in.readObject();
	}
}
