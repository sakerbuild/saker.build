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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableMap;

import saker.build.task.TaskName;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.io.SerialUtils;

final class TaskInvocationBootstrapperTaskIdentifier implements Externalizable, TaskIdentifier {
	private static final long serialVersionUID = 1L;

	TaskName taskName;
	String repository;
	NavigableMap<String, TaskIdentifier> parametersNameTaskIds;

	/**
	 * For {@link Externalizable}.
	 */
	public TaskInvocationBootstrapperTaskIdentifier() {
	}

	public TaskInvocationBootstrapperTaskIdentifier(TaskName taskName, String repository,
			NavigableMap<String, TaskIdentifier> parametersNameTaskIds) throws NullPointerException {
		this.taskName = taskName;
		this.repository = repository;
		this.parametersNameTaskIds = parametersNameTaskIds;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(taskName);
		out.writeObject(repository);
		SerialUtils.writeExternalMap(out, parametersNameTaskIds);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		taskName = (TaskName) in.readObject();
		repository = (String) in.readObject();
		parametersNameTaskIds = SerialUtils.readExternalSortedImmutableNavigableMap(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((parametersNameTaskIds == null) ? 0 : parametersNameTaskIds.hashCode());
		result = prime * result + ((repository == null) ? 0 : repository.hashCode());
		result = prime * result + ((taskName == null) ? 0 : taskName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TaskInvocationBootstrapperTaskIdentifier other = (TaskInvocationBootstrapperTaskIdentifier) obj;
		if (parametersNameTaskIds == null) {
			if (other.parametersNameTaskIds != null)
				return false;
		} else if (!parametersNameTaskIds.equals(other.parametersNameTaskIds))
			return false;
		if (repository == null) {
			if (other.repository != null)
				return false;
		} else if (!repository.equals(other.repository))
			return false;
		if (taskName == null) {
			if (other.taskName != null)
				return false;
		} else if (!taskName.equals(other.taskName))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (taskName != null ? "taskName=" + taskName + ", " : "")
				+ (repository != null ? "repository=" + repository + ", " : "")
				+ (parametersNameTaskIds != null ? "parametersNameTaskIds=" + parametersNameTaskIds : "") + "]";
	}

}
