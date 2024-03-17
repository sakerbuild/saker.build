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

import saker.build.task.TaskDependencyFuture;
import saker.build.task.TaskResultDependencyHandle;
import saker.build.task.dependencies.TaskOutputChangeDetector;

public final class FutureWrapperTaskResultDependencyHandle implements TaskResultDependencyHandle, Cloneable {
	private final TaskDependencyFuture<?> future;

	public FutureWrapperTaskResultDependencyHandle(TaskDependencyFuture<?> future) {
		this.future = future;
	}

	@Override
	public Object get() throws RuntimeException {
		return future.get();
	}

	@Override
	public void setTaskOutputChangeDetector(TaskOutputChangeDetector outputchangedetector)
			throws IllegalStateException, NullPointerException {
		future.setTaskOutputChangeDetector(outputchangedetector);
	}

	@Override
	public TaskResultDependencyHandle clone() {
		return new FutureWrapperTaskResultDependencyHandle(future.clone());
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + future + "]";
	}

}
