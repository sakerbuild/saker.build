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
package saker.build.runtime.execution;

import saker.build.task.TaskProgressMonitor;

/**
 * Interface for monitoring and reporting progress of the build execution.
 */
public interface ExecutionProgressMonitor {

	/**
	 * Gets if the cancellation of the execution was requested for the build.
	 * 
	 * @return <code>true</code> if the build execution should be cancelled.
	 */
	public boolean isCancelled();

	/**
	 * Starts reporting the progress for a new task.
	 * 
	 * @return The created task progress monitor.
	 */
	public TaskProgressMonitor startTaskProgress();

	/**
	 * Gets a progress monitor that ignores operations done on it.
	 * <p>
	 * Every method call on the result instance is a no-op.
	 * <p>
	 * The returned monitor may be a shared instance.
	 * 
	 * @return A null progress monitor.
	 */
	public static ExecutionProgressMonitor nullMonitor() {
		return NullProgressMonitor.INSTANCE;
	}

	public static class NullProgressMonitor implements ExecutionProgressMonitor, TaskProgressMonitor {
		public static final NullProgressMonitor INSTANCE = new NullProgressMonitor();

		@Override
		public boolean isCancelled() {
			return false;
		}

		@Override
		public TaskProgressMonitor startTaskProgress() {
			return this;
		}
	}
}
