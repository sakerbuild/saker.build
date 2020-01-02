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
import java.util.NavigableMap;

import saker.build.task.utils.StructuredTaskResult;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIForbidden;

/**
 * Tasks are the basic execution units for the build system
 * <p>
 * Tasks are instantiated by {@linkplain TaskFactory task factories}, and they are called at most once in their lifetime.
 * Tasks receive a context object as a parameter during execution which can be used to communicate with the build system
 * appropriately.
 * <p>
 * It is a common scenario that tasks require to be parameterized by external agents. This is supported by implementing
 * {@link ParameterizableTask}, which have an appropriate method that is used for initializing the parameters. Clients
 * of the {@link Task} interface should handle if the tasks implement this extension interface.
 * <p>
 * Tasks are always run on the appropriate worker machine and will not be called through RMI.
 * <p>
 * Tasks can return instances of {@link StructuredTaskResult} as their result which should be handled specially. See the
 * documentation for the interface for more information. Task results should be serializable, preferably
 * {@link Externalizable}.
 * <p>
 * Tasks may throw an exception during execution which will cause the build execution to abort with that exception.
 * 
 * @param <R>
 *            The return type of the task.
 * @see TaskFactory
 * @see TaskContext
 * @see ParameterizableTask
 * @see StructuredTaskResult
 */
public interface Task<R> {
	/**
	 * Executes this task.
	 * <p>
	 * If this task is an intance of {@link ParameterizableTask}, then
	 * {@link ParameterizableTask#initParameters(TaskContext, NavigableMap)} will be called prior to this.
	 * <p>
	 * This method is called at most once during the lifetime of the task object.
	 * 
	 * @param taskcontext
	 *            The task context to communicate with the build system.
	 * @return The result of this task, may be an instance of {@link StructuredTaskResult} or any arbitrary object.
	 * @throws Exception
	 *             For any exception that caused this task to fail.
	 * @see TaskContext
	 * @see StructuredTaskResult
	 */
	@RMIForbidden
	public R run(TaskContext taskcontext) throws Exception;
}
