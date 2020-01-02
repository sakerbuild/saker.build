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
package saker.build.task.exception;

import java.util.HashMap;
import java.util.Map;

import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ImmutableUtils;

/**
 * Exception collecting information about multiple task execution failures.
 * <p>
 * The task identifier for this exceptions is the identifier that was used to start the root task of the execution.
 * <p>
 * The related exceptions can be accessed via {@link #getTaskExceptions()} or {@link #getSuppressed()}
 */
public class MultiTaskExecutionFailedException extends TaskExecutionException {
	private static final long serialVersionUID = 1L;

	/**
	 * The map holding the related exceptions mapped to their task identifiers.
	 */
	protected Map<TaskIdentifier, TaskException> taskExceptions = new HashMap<>();

	/**
	 * Creates a new container exception instance.
	 * 
	 * @param taskid
	 *            The root task identifier which was used to start the build.
	 */
	MultiTaskExecutionFailedException(TaskIdentifier taskid) {
		super(taskid);
	}

	/**
	 * Adds a related exception to this container.
	 * 
	 * @param taskid
	 *            The task identifier associated with the exception.
	 * @param exc
	 *            The task exception which should be added.
	 */
	void addException(TaskIdentifier taskid, TaskException exc) {
		TaskException prev = taskExceptions.putIfAbsent(taskid, exc);
		if (prev != null) {
			prev.addSuppressed(exc);
		}
		addSuppressed(exc);
	}

	/**
	 * Gets the related exceptions mapped to their task identifiers.
	 * 
	 * @return An unmodifiable map of exceptions.
	 */
	public Map<TaskIdentifier, ? extends TaskException> getTaskExceptions() {
		return ImmutableUtils.unmodifiableMap(taskExceptions);
	}

}
