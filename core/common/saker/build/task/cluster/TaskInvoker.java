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
package saker.build.task.cluster;

import java.util.UUID;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.TaskInvocationManager.TaskInvocationContext;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;

/**
 * An interface for invoking tasks in a given execution context, with a specific invocation context.
 * <p>
 * This is usually implemented by build clusters.
 */
public interface TaskInvoker {

	/**
	 * Runs the task invocation for the given contexts.
	 * 
	 * @param executioncontext
	 *            The execution context.
	 * @param invokerinformation
	 *            The information for the task invoker to create the appropriate context for invoking tasks.
	 * @param context
	 *            The context to run on.
	 * @throws Exception
	 *             In case of error.
	 */
	public void run(ExecutionContext executioncontext, TaskInvokerInformation invokerinformation,
			TaskInvocationContext context) throws Exception;

	@RMICacheResult
	@RMISerialize
	public UUID getEnvironmentIdentifier();
}
