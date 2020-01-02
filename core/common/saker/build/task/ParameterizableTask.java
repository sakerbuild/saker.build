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

import java.util.Map;
import java.util.NavigableMap;

import saker.build.task.exception.TaskParameterException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.TaskUtils;
import saker.build.task.utils.annot.SakerInput;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIForbidden;

/**
 * Extension interface for {@link Task} for representing a task that can be parameterized.
 * <p>
 * Clients of this interface must call {@link #initParameters(TaskContext, NavigableMap)} before calling
 * {@link #run(TaskContext)}, even if no parameters were specified for the task.
 * 
 * @param <R>
 *            The return type of the task.
 */
public interface ParameterizableTask<R> extends Task<R> {
	/**
	 * Initializes the parameters of this task.
	 * <p>
	 * This method is called at most once during the lifetime of the task object and always before
	 * {@link #run(TaskContext)}.
	 * <p>
	 * The default implementation initializes the parameters of <code>this</code> task instance by looking at its
	 * declared fields annotated with {@link SakerInput} and related annotations.
	 * <p>
	 * The unnamed parameter (if any) is available in the argument map with the <code>""</code> (empty string) key.
	 * 
	 * @param taskcontext
	 *            The task context to communicate with the build system.
	 * @param parameters
	 *            The parameters mapped by parameter name to corresponding tasks.
	 * @throws TaskParameterException
	 *             In case of parameter initialization error.
	 * @see TaskUtils#initParametersOfTask(TaskContext, Object, Map)
	 */
	@RMIForbidden
	public default void initParameters(TaskContext taskcontext,
			NavigableMap<String, ? extends TaskIdentifier> parameters) throws TaskParameterException {
		TaskUtils.initParametersOfTask(taskcontext, this, parameters);
	}
}
