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
package saker.build.task.delta;

import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.execution.ExecutionProperty;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.dependencies.FileCollectionStrategy;

/**
 * Enumeration for possible task delta types.
 * 
 * @see BuildDelta
 */
public enum DeltaType {
	/**
	 * An input file has changed.
	 * 
	 * @see TaskContext#reportInputFileDependency
	 * @see FileChangeDelta
	 */
	INPUT_FILE_CHANGE,
	/**
	 * An input file was added.
	 * 
	 * @see FileCollectionStrategy
	 * @see TaskContext#reportInputFileAdditionDependency
	 * @see FileChangeDelta
	 */
	INPUT_FILE_ADDITION,

	/**
	 * An output file changed.
	 * 
	 * @see TaskContext#reportOutputFileDependency
	 * @see FileChangeDelta
	 */
	OUTPUT_FILE_CHANGE,

	/**
	 * If the previous output of the task failed to load.
	 * <p>
	 * This can happen due to I/O error, the task threw an exception, or other arbitrary internal reasons.
	 * 
	 * @see OutputLoadFailedDelta
	 */
	OUTPUT_LOAD_FAILED,

	/**
	 * For task related changes.
	 * <p>
	 * This can happen when the {@link TaskFactory} for the given task has changed. (i.e. the implementation of the task
	 * for the given task identifier changed) <br>
	 * When any of input dependency tasks have changed. <br>
	 * The working or build directory path has been changed. <br>
	 * Incompatible configuration change compared to the previous run. (E.g. build directory no longer present) <br>
	 * Other implementation dependent internal reasons.
	 * 
	 * @see TaskChangeDelta
	 */
	TASK_CHANGE,
	/**
	 * For tasks which have no previous state.
	 * <p>
	 * This delta is used when the task is run for the first time.
	 * 
	 * @see NewTaskDelta
	 */
	NEW_TASK,

	/**
	 * An environment property changed.
	 * 
	 * @see TaskContext#reportEnvironmentDependency
	 * @see EnvironmentProperty
	 * @see EnvironmentDependencyDelta
	 */
	ENVIRONMENT_PROPERTY_CHANGED,
	/**
	 * An execution property changed.
	 * 
	 * @see TaskContext#reportExecutionDependency
	 * @see ExecutionProperty
	 * @see ExecutionDependencyDelta
	 */
	EXECUTION_PROPERTY_CHANGED,

	;
}