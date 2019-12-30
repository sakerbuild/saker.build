package saker.build.task;

import java.io.Externalizable;
import java.util.Collections;
import java.util.Set;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.repository.BuildRepository;
import saker.build.task.exception.TaskEnvironmentSelectionFailedException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIForbidden;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.util.rmi.wrap.RMITreeSetStringElementWrapper;

/**
 * Represents a stateless factory for tasks which are the basic execution units for the build system.
 * <p>
 * Task factories represent a task that is to be executed by the build runtime. They are a specification of how the task
 * should be executed, and what the task itself actually does.
 * <p>
 * Taks factories <b>must</b> implement the {@link #equals(Object)} and {@link #hashCode()} contract, as not doing so
 * will result in incorrect incremental builds. By checking the equality of two task factories, if two of them equals,
 * that means that they will execute exactly the same computations given the same circumstances.
 * <p>
 * Task factories should not have any state, and all of the data contained in them should be immutable.
 * <p>
 * Task factories can specify {@linkplain #getCapabilities() capabilities} which are hints for the build runtime to
 * fine-grain the execution behaviour. They can be used to signal that a task completes quickly, is remote executeble,
 * etc... Additional capabilities can be added to this interface in the future.
 * <p>
 * The task factories can also be used to start inner tasks. Inner tasks are handled differently by the build system,
 * see {@link TaskContext#startInnerTask(TaskFactory, InnerTaskExecutionParameters)} for more information.
 * <p>
 * Task factories can implement a strategy to choose a suitable build environment to execute on. This is mostly relevant
 * when designing tasks for remote execution. See {@link #getExecutionEnvironmentSelector()}.
 * <p>
 * In order to avoid thrashing the system due to too high level of concurrency, tasks can report the level of
 * computation they will use to do their work. See {@link #getRequestedComputationTokenCount()}.
 * <p>
 * Task factories are strongly encouraged to implement the {@link Externalizable} interface for faster serialization.
 * 
 * @param <R>
 *            The return type of the task.
 * @see TaskContext
 * @see BuildRepository#lookupTask(TaskName)
 */
public interface TaskFactory<R> {
	/**
	 * Capability string for specify a task that is considered to be short.
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
	 * <li>They can only wait for tasks which are also short capable.</li>
	 * <li>They cannot wait for tasks which are not yet started.</li>
	 * <li>They cannot be {@link #CAPABILITY_REMOTE_DISPATCHABLE remote dispatchable}.</li>
	 * <li>They cannot report {@link #getRequestedComputationTokenCount() computation tokens}.</li>
	 * </ul>
	 * <p>
	 * The build system can run short tasks without creating a separate thread for them. This means that
	 * {@link TaskContext#startTask(TaskIdentifier, TaskFactory, TaskExecutionParameters) starting a short task} will
	 * not return control to the starter, but wait for the execution of the task and then return control. This is an
	 * optimization can reduce unnecessary load on the OS and the build system.
	 * 
	 * @see #getCapabilities()
	 */
	public static final String CAPABILITY_SHORT_TASK = "saker.task.short";
	/**
	 * Capability string for specify a task that can be executed remotely on build clusters.
	 * <p>
	 * Remote dispatchable tasks can be transferred to remote executor instances, therefore improving the number of
	 * concurrently executing tasks and ensuring horizontal scalability.
	 * <p>
	 * This capability only used when the user configures the build execution to use at least one cluster instance.
	 * <p>
	 * When specifiying this capability, the task will be a candidate for remote dispatching. The build runtime is not
	 * required to actually execute this task on a remote machine, but it will make efforts to property distribute it
	 * based on current workloads.
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
	 * 
	 * @see #getCapabilities()
	 * @see #getExecutionEnvironmentSelector()
	 */
	public static final String CAPABILITY_REMOTE_DISPATCHABLE = "saker.task.remote.dispatchable";
	/**
	 * Capability string for specify that the results of the task can be cached and retrieved.
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
	 * This capability serves as a hint, and the build system may decide that it won't use the build cache to retrieve
	 * the results. This may be due to performance, configuration, build environment or other arbitrary reasons.
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
	 * As a general rule of thumb, only tasks should report this capability which do more work than the time it takes to
	 * retrieve their results from a network cache. That is, the time the task computation takes should outweight the
	 * network communication times.
	 * 
	 * @see #getCapabilities()
	 * @see TaskFuture#getFinished()
	 * @see TaskDependencyFuture#getFinished()
	 */
	public static final String CAPABILITY_CACHEABLE = "saker.task.remote.cacheable";

	/**
	 * Capability string for specifying a task that will start inner task with computation tokens.
	 * <p>
	 * If a task wishes to start inner tasks that report 1 or more computation tokens, then the enclosing task must
	 * report this capability. This is in order to ensure that the proper restrictions are placed in the build system
	 * for the enclosing and inner tasks as well. See {@link #getRequestedComputationTokenCount()} for the nature of
	 * restrictions.
	 * 
	 * @see #getCapabilities()
	 * @see #getRequestedComputationTokenCount()
	 * @see TaskContext#startInnerTask(TaskFactory, InnerTaskExecutionParameters)
	 */
	public static final String CAPABILITY_INNER_TASKS_COMPUTATIONAL = "saker.task.inner.tasks.computational";

	/**
	 * Creates a task instance.
	 * <p>
	 * Every task instance is used for only one invocation.
	 * 
	 * @param executioncontext
	 *            The execution context that is used to run the task.
	 * @return The created task.
	 */
	@RMIForbidden
	public Task<? extends R> createTask(ExecutionContext executioncontext);

	/**
	 * Gets the capabilities of this task.
	 * <p>
	 * Unrecognized capabilities will be silently ignored by the build system.
	 * 
	 * @return An unmodifiable set of capability strings.
	 * @see #CAPABILITY_SHORT_TASK
	 * @see #CAPABILITY_REMOTE_DISPATCHABLE
	 */
	@RMICacheResult
	@RMIWrap(RMITreeSetStringElementWrapper.class)
	public default Set<String> getCapabilities() {
		return Collections.emptyNavigableSet();
	}

	/**
	 * Gets an environment selector to determine if the task can execute in a given build environment.
	 * <p>
	 * Implementation should return a new instance for every invocation of this method, as
	 * {@link TaskExecutionEnvironmentSelector} is a stateful class.
	 * <p>
	 * If two task factories equal, then their returned environment selectors should equal as well.
	 * <p>
	 * If an environment selector fails to find a suitable environment, then an instance of
	 * {@link TaskEnvironmentSelectionFailedException} will be thrown by the build system and the build execution will
	 * abort.
	 * <p>
	 * The default implementation returns a selector which enables the task to use any build environment.
	 * 
	 * @return The environment selector.
	 * @see AnyTaskExecutionEnvironmentSelector
	 */
	public default TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
		return AnyTaskExecutionEnvironmentSelector.INSTANCE;
	}

	/**
	 * Gets the computation token count consumed by this task during execution.
	 * <p>
	 * Computation tokens are used to prevent thrashing of the execution machine when too many concurrent operations are
	 * running. A computation token represents one unit of computational operation that uses one CPU thread on 100%
	 * usage. This method returns the average number of computation tokens the task uses during its execution. The task
	 * will start to run when the requested number of tokens are available for it.
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
	 * The default implementation returns 0, meaning no computation tokens requested.
	 * 
	 * @return 1 or more to specify how many computation tokens the execution of task requires.
	 * @see TaskFuture#getFinished()
	 * @see TaskDependencyFuture#getFinished()
	 */
	public default int getRequestedComputationTokenCount() {
		return 0;
	}

	@Override
	public int hashCode();

	/**
	 * Checks if this task equals will execute exactly the same computations given the same circumstances as the
	 * parameter.
	 * <p>
	 * The checks for equality should also take the {@linkplain #getExecutionEnvironmentSelector() execution environment
	 * selector} into account.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj);
}
