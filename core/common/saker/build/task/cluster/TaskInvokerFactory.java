package saker.build.task.cluster;

import java.io.IOException;
import java.util.UUID;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.TaskInvoker;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;

/**
 * Interface responsible for creating a {@link TaskInvoker} instance for a given execution.
 * <p>
 * This is usually used with remote daemons to instantiate the {@link TaskInvoker} for dispatching task
 * executions. The created task invoker is closed after the build execution is over.
 */
public interface TaskInvokerFactory {

	/**
	 * Creates a task invoker for the given execution context.
	 * <p>
	 * Implementation should expect the caller to close the returned task invoker, but should gracefully handle the case
	 * when they don't, as breaking up RMI connection or other unexpected circumstances may violate this requirement.
	 * 
	 * @param executioncontext
	 *            The execution context.
	 * @param invokerinformation
	 *            The information for the task invoker to create the appropriate context for invoking tasks.
	 * @return The created task invoker.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the execution context is <code>null</code>.
	 */
	public TaskInvoker createTaskInvoker(ExecutionContext executioncontext,
			TaskInvokerInformation invokerinformation) throws IOException, NullPointerException;

	@RMICacheResult
	@RMISerialize
	public UUID getEnvironmentIdentifier();
}
