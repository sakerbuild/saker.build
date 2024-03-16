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

import saker.build.task.TaskResultDependencyHandle;

public final class RetrievedTaskResultDependencyHandle implements TaskResultDependencyHandle, Cloneable {
	private final Object result;

	public RetrievedTaskResultDependencyHandle(Object result) {
		this.result = result;
	}

	@Override
	public Object get() throws RuntimeException {
		return result;
	}

	@Override
	public TaskResultDependencyHandle clone() {
		try {
			return (TaskResultDependencyHandle) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError(e);
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + result + "]";
	}

}
