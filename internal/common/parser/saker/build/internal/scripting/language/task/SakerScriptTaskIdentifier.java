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
package saker.build.internal.scripting.language.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.task.identifier.TaskIdentifier;

public class SakerScriptTaskIdentifier implements TaskIdentifier, Externalizable {
	private static final long serialVersionUID = 1L;

	protected TaskIdentifier rootIdentifier;
	protected SakerTaskFactory taskFactory;

	/**
	 * For {@link Externalizable}.
	 */
	public SakerScriptTaskIdentifier() {
	}

	public SakerScriptTaskIdentifier(TaskIdentifier rootIdentifier, SakerTaskFactory taskFactory) {
		this.rootIdentifier = rootIdentifier;
		this.taskFactory = taskFactory;
	}

	public TaskIdentifier getRootIdentifier() {
		return rootIdentifier;
	}

	public SakerTaskFactory getTaskFactory() {
		return taskFactory;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(rootIdentifier);
		out.writeObject(taskFactory);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		rootIdentifier = (TaskIdentifier) in.readObject();
		taskFactory = (SakerTaskFactory) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((rootIdentifier == null) ? 0 : rootIdentifier.hashCode());
		result = prime * result + ((taskFactory == null) ? 0 : taskFactory.hashCode());
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
		SakerScriptTaskIdentifier other = (SakerScriptTaskIdentifier) obj;
		if (rootIdentifier == null) {
			if (other.rootIdentifier != null)
				return false;
		} else if (!rootIdentifier.equals(other.rootIdentifier))
			return false;
		if (taskFactory == null) {
			if (other.taskFactory != null)
				return false;
		} else if (!taskFactory.equals(other.taskFactory))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return rootIdentifier + "/" + taskFactory;
	}

}
