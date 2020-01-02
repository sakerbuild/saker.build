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

import java.util.List;

import saker.build.task.TaskExecutionResult.TaskDependencies;
import saker.build.task.identifier.TaskIdentifier;

public interface TaskResultHolder<R> {
	public TaskIdentifier getTaskIdentifier();

	/**
	 * Gets the returned result of the task from {@link Task#run(TaskContext)}.
	 * <p>
	 * Will be <code>null</code> if there are any exceptions.
	 * 
	 * @return The returned object.
	 */
	public R getOutput();

	/**
	 * Gets the reported exceptions using {@link TaskContext#abortExecution(Throwable)}.
	 * 
	 * @return The exceptions. May be <code>null</code> or empty.
	 */
	public List<? extends Throwable> getAbortExceptions();

	/**
	 * Gets the exception that was thrown by the task from {@link Task#run(TaskContext)}.
	 * 
	 * @return The exception or <code>null</code> if none.
	 */
	public Throwable getFailCauseException();

	public TaskDependencies getDependencies();
}
