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

/**
 * Interface for monitoring and reporting progress of tasks during execution.
 * <p>
 * This interface is planned to be extended in the future for reified information reporting.
 */
public interface TaskProgressMonitor {
	/**
	 * Gets if the cancellation of the execution was requested for the task.
	 * 
	 * @return <code>true</code> if the task execution should be cancelled.
	 */
	public boolean isCancelled();
}
