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

import saker.apiextract.api.PublicApi;
import saker.build.task.exception.TaskEnvironmentSelectionFailedException;
import saker.build.task.identifier.TaskIdentifier;

/**
 * Data class holding information about invocation related configuration for build tasks.
 * <p>
 * The class describes how a given tasks requests it is to be invoked.
 * <p>
 * Use {@link #builder()} to create a new instance.
 * <p>
 * Some common configurations are available as singleton instances in this class. See the <code>INSTANCE_*</code> static
 * fields.
 * 
 * @since saker.build 0.8.12
 * @see TaskFactory#getInvocationConfiguration()
 */
@PublicApi
public final class TaskInvocationConfiguration implements Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * Singleton instance for the default task invocation configuration.
	 * <p>
	 * This is the default configuration that {@linkplain TaskFactory task factories} use unless specified otherwise.
	 */
	public static final TaskInvocationConfiguration INSTANCE_DEFAULTS = new TaskInvocationConfiguration(
			AnyTaskExecutionEnvironmentSelector.INSTANCE);
	/**
	 * Singleton instance that is a {@linkplain #isShort() short task}.
	 */
	public static final TaskInvocationConfiguration INSTANCE_SHORT_TASK = new TaskInvocationConfiguration(
			AnyTaskExecutionEnvironmentSelector.INSTANCE);
	static {
		INSTANCE_SHORT_TASK.shortTask = true;
	}

	protected TaskExecutionEnvironmentSelector environmentSelector;
	protected int computationTokenCount;
	protected boolean shortTask;
	protected boolean remoteDispatchable;
	protected boolean cacheable;
	protected boolean innerTasksComputationals;

	/**
	 * For {@link Externalizable}.
	 */
	public TaskInvocationConfiguration() {
		this.environmentSelector = AnyTaskExecutionEnvironmentSelector.INSTANCE;
	}

	protected TaskInvocationConfiguration(TaskInvocationConfiguration config) {
		this.environmentSelector = config.environmentSelector;
		this.computationTokenCount = config.computationTokenCount;
		this.shortTask = config.shortTask;
		this.remoteDispatchable = config.remoteDispatchable;
		this.cacheable = config.cacheable;
		this.innerTasksComputationals = config.innerTasksComputationals;
	}

	protected TaskInvocationConfiguration(TaskExecutionEnvironmentSelector environmentSelector) {
		if (environmentSelector == null) {
			this.environmentSelector = AnyTaskExecutionEnvironmentSelector.INSTANCE;
		} else {
			this.environmentSelector = environmentSelector;
		}
	}

	/**
	 * Gets a new builder.
	 * 
	 * @return The created builder.
	 */
	public static TaskInvocationConfiguration.Builder builder() {
		return new Builder();
	}

	/**
	 * Gets a new builder that is initialized with the argument configuration.
	 * 
	 * @param config
	 *            The initializer configuration.
	 * @return The created builder.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>,
	 */
	public static TaskInvocationConfiguration.Builder builder(TaskInvocationConfiguration config)
			throws NullPointerException {
		Objects.requireNonNull(config, "config");
		return new Builder(config);
	}

	/**
	 * Gets the if the build task is short.
	 * <p>
	 * If a task reports themselves as short then they are considered to be fast to execute. This is in a sense that the
	 * execution of the task is shorter than creating a separate thread and running them concurrently. As a general rule
	 * of thumb, if the execution time of a task is comparable to the time that a thread takes to start, then it should
	 * be short.
	 * <p>
	 * It is recommended that tasks which wait for no other tasks, have no dependencies, do no heavy computations, and
	 * do no I/O operations, are good subjects to be short.
	 * <p>
	 * The following additional restrictions apply to short tasks:
	 * <ul>
	 * <li>They can only wait for tasks which are also short.</li>
	 * <li>They cannot wait for tasks which are not yet started.</li>
	 * <li>They cannot be {@link #isRemoteDispatchable() remote dispatchable}.</li>
	 * <li>They cannot report {@link #getRequestedComputationTokenCount() computation tokens}.</li>
	 * </ul>
	 * <p>
	 * The build system can run short tasks without creating a separate thread for them. This means that
	 * {@link TaskContext#startTask(TaskIdentifier, TaskFactory, TaskExecutionParameters) starting a short task} will
	 * not return control to the starter, but wait for the execution of the task and then return control. This is an
	 * optimization can reduce unnecessary load on the OS and the build system.
	 * 
	 * @return <code>true</code> if the build task is short.
	 */
	public boolean isShort() {
		return shortTask;
	}

	/**
	 * Gets if the task is remote dispatchable.
	 * <p>
	 * Remote dispatchable tasks can be transferred to remote executor instances, therefore improving the number of
	 * concurrently executing tasks and ensuring horizontal scalability.
	 * <p>
	 * This facility only used when the user configures the build execution to use at least one cluster instance.
	 * <p>
	 * When specifiying this configuration, the task will be a candidate for remote dispatching. The build runtime is
	 * not required to actually execute this task on a remote machine, but it will make efforts to property distribute
	 * it based on current workloads.
	 * <p>
	 * When a task reports themselves as remote dispatchable, a restriction is placed on them that they cannot wait for
	 * other tasks. This restriction is necessary, as the deadlock detection is only feasible on the main executor
	 * machine. (Note that this restriction is usually non-distruptive. As generally remote dispatchable tasks are used
	 * for heavily computational workload, they usually report {@linkplain #getRequestedComputationTokenCount()
	 * computation tokens} to signal the amount of work done. In that case, they already can't wait for other tasks.
	 * This restriction may be lifted in the future, or may be only employed if the task is actually being run on a
	 * cluster.) <br>
	 * Tasks can retrieve {@linkplain TaskFuture#getFinished() finished results} nonetheless.
	 * <p>
	 * Designing a task to be remote dispatchable can improve performance, as it will result in more utilization of
	 * overall resources available to the build system. Remote dispatchable tasks should be carefully implemented, and
	 * use the appropriate functions for avoiding performance traps. See the
	 * <a href="https://saker.build/saker.build/doc/extending/taskdev/buildclusters.html">remote execution guide</a> of
	 * the build system for best practices.
	 * <p>
	 * Good example for a remote executable task is C++ compilation, where source files can be transferred to clusters,
	 * compiled, and the result returned back to the main executor. For a large set of files, the compilation tasks can
	 * be distributed to multiple machines, and the overall compilation can complete much faster than if only a single
	 * machine was used.
	 * <p>
	 * To choose an appropriate build environment for the task, {@link #getExecutionEnvironmentSelector()} can be used.
	 * Note that if the {@link TaskExecutionEnvironmentSelector#isRestrictedToLocalEnvironment()} method of the
	 * environment selector returns <code>true</code>, then the remote dispatching will not proceed.
	 * 
	 * @return <code>true</code> if the build task is remote dispatchable.
	 * @see #getExecutionEnvironmentSelector()
	 */
	public boolean isRemoteDispatchable() {
		return remoteDispatchable;
	}

	/**
	 * Gets if the task is cacheable.
	 * <p>
	 * Cacheable tasks allow the build system to retrieve the result of the execution from external sources, or publish
	 * their results to a database.
	 * <p>
	 * Cacheable tasks are used with build caches. Build caches are background daemon processes which provide access to
	 * results of previously run tasks. If a task reports themself as cacheable, the build system may try to retrieve
	 * its previously run result from a build cache configured to the current execution. After a cacheable task
	 * executes, the build system may publish the results of the task to the configured build cache, so the outputs will
	 * be available for future reuse.
	 * <p>
	 * This configuration serves as a hint, and the build system may decide that it won't use the build cache to
	 * retrieve the results. This may be due to performance, configuration, build environment or other arbitrary
	 * reasons.
	 * <p>
	 * The build system will only retrieve the results for a task if the published task is applicable to the current
	 * build environment. Meaning, that if any dependendencies of the published task have been changed in the current
	 * run, then it won't be reused.
	 * <p>
	 * Cacheable tasks are strongly recommended to comply with the following restrictions:
	 * <ul>
	 * <li>The task identifier for the task should have a stable {@linkplain TaskIdentifier#hashCode() hash code}. This
	 * means that the task identifier should return the same hash code for the same objects between different executions
	 * of the Java process. This usually requires that the task identifier doesn't derive its hash code from the
	 * {@linkplain System#identityHashCode(Object) identity hash code}, {@linkplain Class#hashCode() class hash code},
	 * or in any way runtime dependent values. With that in mind, {@linkplain Enum enums} cannot be used as task
	 * identifiers, because their hash code is not stable.</li>
	 * <li>The task cannot wait on the result of another task, but it can only retrieve its finished results. This means
	 * that the task may only use the finished result retrieval methods of other tasks. This requirement is aligned with
	 * the {@linkplain #getRequestedComputationTokenCount() computation token} usage.</li>
	 * </ul>
	 * The above restrictions are not hard restrictions, meaning that in case of their violation, the build runtime will
	 * not throw an exception, but just ignore the task instance for possible build cache usage.
	 * <p>
	 * The above restrictions are required in order to provide an efficient and sane implementation for the build
	 * system, and may be lifted in the future, but task implementations should align their behaviour with these in
	 * place nonetheless.
	 * <p>
	 * As a general rule of thumb, only tasks should report this configuration which do more work than the time it takes
	 * to retrieve their results from a network cache. That is, the time the task computation takes should outweight the
	 * network communication times.
	 * 
	 * @return <code>true</code> if the build task is cacheable.
	 */
	public boolean isCacheable() {
		return cacheable;
	}

	/**
	 * Gets if the inner tasks of the build tasks can use computation tokens.
	 * <p>
	 * If a task wishes to start inner tasks that report 1 or more computation tokens, then the enclosing task must
	 * report this configuration. This is in order to ensure that the proper restrictions are placed in the build system
	 * for the enclosing and inner tasks as well. See {@link #getRequestedComputationTokenCount()} for the nature of
	 * restrictions.
	 * 
	 * @return <code>true</code> if the task uses computational inner tasks.
	 */
	public boolean isInnerTasksComputationals() {
		return innerTasksComputationals;
	}

	/**
	 * Gets the computation token count consumed by this task during execution.
	 * <p>
	 * Computation tokens are used to prevent thrashing of the execution machine when too many concurrent operations are
	 * running. A computation token represents one unit of computational operation that uses one CPU thread on 100%.
	 * This method returns the average number of computation tokens the task uses during its execution. The task will
	 * start to run when the requested number of tokens are available for it.
	 * <p>
	 * If a task returns <code> &gt; 0</code> amount of computation tokens then a restriction is placed on them that
	 * they can't wait for other tasks in the build system. This is in order to prevent involuntarily deadlocking the
	 * execution.
	 * <p>
	 * (Reasoning: Tasks will not start execution until they can allocate the required amount of computation tokens for
	 * themselves. If a tasks attempts to wait for a task which cannot start due to not being able to allocate enough
	 * computation tokens will deadlock the build execution, although they could probably finish if computation tokens
	 * didn't exist. Implementing active deadlock detection for this behaviour is not deemed to be feasible, so the
	 * above restriction is placed on tasks which require computation tokens.)
	 * <p>
	 * If your task really needs to wait for an input task then we recommend waiting for them in a parent task and start
	 * the actual computation in a sub-task with computation tokens. Dependencies on input tasks can be specified by
	 * using the finished retrieval methods of the task futures which do not require waiting for the subject task.
	 * <p>
	 * The values 0 or less means that no computation tokens are requested.
	 * 
	 * @return The requested computation token count.
	 * @see TaskFuture#getFinished()
	 * @see TaskDependencyFuture#getFinished()
	 */
	public int getRequestedComputationTokenCount() {
		return computationTokenCount;
	}

	/**
	 * Gets an environment selector to determine if the task can execute in a given build environment.
	 * <p>
	 * If two task factories equal, then their returned environment selectors should equal as well.
	 * <p>
	 * If an environment selector fails to find a suitable environment, then an exception instance of
	 * {@link TaskEnvironmentSelectionFailedException} will be thrown by the build system and the build execution will
	 * abort.
	 * <p>
	 * The default implementation returns a selector which enables the task to use any build environment.
	 * 
	 * @return The environment selector.
	 * @see AnyTaskExecutionEnvironmentSelector
	 */
	public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
		return environmentSelector;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(environmentSelector);
		out.writeInt(computationTokenCount);
		out.writeBoolean(shortTask);
		out.writeBoolean(remoteDispatchable);
		out.writeBoolean(cacheable);
		out.writeBoolean(innerTasksComputationals);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		environmentSelector = (TaskExecutionEnvironmentSelector) in.readObject();
		computationTokenCount = in.readInt();
		shortTask = in.readBoolean();
		remoteDispatchable = in.readBoolean();
		cacheable = in.readBoolean();
		innerTasksComputationals = in.readBoolean();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (cacheable ? 1231 : 1237);
		result = prime * result + computationTokenCount;
		result = prime * result + ((environmentSelector == null) ? 0 : environmentSelector.hashCode());
		result = prime * result + (innerTasksComputationals ? 1231 : 1237);
		result = prime * result + (remoteDispatchable ? 1231 : 1237);
		result = prime * result + (shortTask ? 1231 : 1237);
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
		TaskInvocationConfiguration other = (TaskInvocationConfiguration) obj;
		if (cacheable != other.cacheable)
			return false;
		if (computationTokenCount != other.computationTokenCount)
			return false;
		if (environmentSelector == null) {
			if (other.environmentSelector != null)
				return false;
		} else if (!environmentSelector.equals(other.environmentSelector))
			return false;
		if (innerTasksComputationals != other.innerTasksComputationals)
			return false;
		if (remoteDispatchable != other.remoteDispatchable)
			return false;
		if (shortTask != other.shortTask)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[environmentSelector=" + environmentSelector + ", computationTokenCount="
				+ computationTokenCount + ", shortTask=" + shortTask + ", remoteDispatchable=" + remoteDispatchable
				+ ", cacheable=" + cacheable + ", innerTasksComputationals=" + innerTasksComputationals + "]";
	}

	/**
	 * Builder class for {@link TaskInvocationConfiguration}.
	 * <p>
	 * Use {@link TaskInvocationConfiguration#builder()} to create a new instance.
	 */
	public static final class Builder {
		private TaskInvocationConfiguration result;

		protected Builder() {
			result = new TaskInvocationConfiguration();
		}

		protected Builder(TaskInvocationConfiguration config) {
			result = new TaskInvocationConfiguration(config);
		}

		/**
		 * Sets if the task is {@linkplain TaskInvocationConfiguration#isShort() short}.
		 * 
		 * @param shortTask
		 *            <code>true</code> if the task is short.
		 * @return <code>this</code>
		 * @see TaskInvocationConfiguration#isShort()
		 */
		public Builder setShort(boolean shortTask) {
			result.shortTask = shortTask;
			return this;
		}

		/**
		 * Sets if the task is {@linkplain TaskInvocationConfiguration#isRemoteDispatchable() remote dispatchable}.
		 * 
		 * @param remoteDispatchable
		 *            <code>true</code> if the task is remote dispatchable.
		 * @return <code>this</code>
		 * @see TaskInvocationConfiguration#isRemoteDispatchable()
		 */
		public Builder setRemoteDispatchable(boolean remoteDispatchable) {
			result.remoteDispatchable = remoteDispatchable;
			return this;
		}

		/**
		 * Sets if the task is {@linkplain TaskInvocationConfiguration#isCacheable() cacheable}.
		 * 
		 * @param cacheable
		 *            <code>true</code> if the task is remote dispatchable.
		 * @return <code>this</code>
		 * @see TaskInvocationConfiguration#isCacheable()
		 */
		public Builder setCacheable(boolean cacheable) {
			result.cacheable = cacheable;
			return this;
		}

		/**
		 * Sets if the inner tasks of the build task are
		 * {@linkplain TaskInvocationConfiguration#isInnerTasksComputationals() computational}.
		 * 
		 * @param innerTasksComputationals
		 *            <code>true</code> if the inner tasks are computational.
		 * @return <code>this</code>
		 * @see TaskInvocationConfiguration#isInnerTasksComputationals()
		 */
		public Builder setInnerTasksComputationals(boolean innerTasksComputationals) {
			result.innerTasksComputationals = innerTasksComputationals;
			return this;
		}

		/**
		 * Sets the {@linkplain TaskInvocationConfiguration#getRequestedComputationTokenCount() requested computation
		 * token count}.
		 * 
		 * @param computationTokenCount
		 *            The computation token count.
		 * @return <code>this</code>
		 * @see TaskInvocationConfiguration#getRequestedComputationTokenCount()
		 */
		public Builder setRequestedComputationTokenCount(int computationTokenCount) {
			result.computationTokenCount = computationTokenCount;
			return this;
		}

		/**
		 * Sets the {@linkplain TaskInvocationConfiguration#getExecutionEnvironmentSelector() execution environment
		 * selector}.
		 * <p>
		 * If <code>null</code> is passed as an argument, {@link AnyTaskExecutionEnvironmentSelector} is used.
		 * 
		 * @param environmentSelector
		 *            The environment selector.
		 * @return <code>this</code>
		 * @see TaskInvocationConfiguration#getExecutionEnvironmentSelector()
		 */
		public Builder setExecutionEnvironmentSelector(TaskExecutionEnvironmentSelector environmentSelector) {
			if (environmentSelector == null) {
				result.environmentSelector = AnyTaskExecutionEnvironmentSelector.INSTANCE;
			} else {
				result.environmentSelector = environmentSelector;
			}
			return this;
		}

		/**
		 * Builds the task invocation configuration.
		 * <p>
		 * The builder <b>cannot</b> be reused after this call.
		 * 
		 * @return The created configuration.
		 * @see #buildReuse()
		 */
		public TaskInvocationConfiguration build() {
			TaskInvocationConfiguration res = result;
			this.result = null;
			return res;
		}

		/**
		 * Builds the task invocation configuration and allows reusing this builder.
		 * 
		 * @return The created configuration.
		 */
		public TaskInvocationConfiguration buildReuse() {
			return new TaskInvocationConfiguration(result);
		}

		/**
		 * Gets the current value of the short task setting.
		 * 
		 * @return <code>true</code> if the task is short.
		 * @since saker.build 0.8.14
		 */
		public boolean isShort() {
			return result.shortTask;
		}

		/**
		 * Gets the current value of the remote dispatchability.
		 * 
		 * @return <code>true</code> if the task is remote dispatchable.
		 * @since saker.build 0.8.14
		 */
		public boolean isRemoteDispatchable() {
			return result.remoteDispatchable;
		}

		/**
		 * Gets the current value of the cacheability.
		 * 
		 * @return <code>true</code> if the task is cacheable.
		 * @since saker.build 0.8.14
		 */
		public boolean isCacheable() {
			return result.cacheable;
		}

		/**
		 * Gets the current value of the inner task computationality.
		 * 
		 * @return <code>true</code> if the inner tasks are computationals.
		 * @since saker.build 0.8.14
		 */
		public boolean isInnerTasksComputationals() {
			return result.innerTasksComputationals;
		}

		/**
		 * Gets the current value of the requested computation tokens.
		 * 
		 * @return The number of requested computation tokens.
		 * @since saker.build 0.8.14
		 */
		public int getRequestedComputationTokenCount() {
			return result.computationTokenCount;
		}

		/**
		 * Gets the currently set execution environment selector.
		 * 
		 * @return The environment selector.
		 * @since saker.build 0.8.14
		 */
		public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
			return result.environmentSelector;
		}
	}

}