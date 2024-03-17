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

import saker.apiextract.api.PublicApi;
import saker.build.task.TaskResultDependencyHandle;
import saker.build.task.TaskResultResolver;
import saker.build.task.identifier.TaskIdentifier;

/**
 * {@link StructuredTaskResult} for a single object.
 * <p>
 * The result object is simply identified by the identifier of the corresponding task.
 * 
 * @see SimpleStructuredObjectTaskResult
 */
@PublicApi
public interface StructuredObjectTaskResult extends StructuredTaskResult {
	/**
	 * Gets the task identifier of the task which produces the result.
	 * <p>
	 * The result of the associated task may still be a structured task result.
	 * 
	 * @return The task identifier.
	 */
	public TaskIdentifier getTaskIdentifier();

	@Override
	public default Object toResult(TaskResultResolver results) {
		return StructuredTaskResult.getActualTaskResult(getTaskIdentifier(), results);
	}

	@Override
	public default TaskResultDependencyHandle toResultDependencyHandle(TaskResultResolver results)
			throws NullPointerException {
		return StructuredTaskResult.getActualTaskResultDependencyHandle(getTaskIdentifier(), results);
	}
}
