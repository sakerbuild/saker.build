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

public class UnsakerTaskFactoryTaskIdentifier implements TaskIdentifier, Externalizable {
	private static final long serialVersionUID = 1L;

	protected UnsakerFutureTaskFactory taskFactory;

	public UnsakerTaskFactoryTaskIdentifier() {
	}

	public UnsakerTaskFactoryTaskIdentifier(UnsakerFutureTaskFactory taskFactory) {
		this.taskFactory = taskFactory;
	}

	public UnsakerFutureTaskFactory getTaskFactory() {
		return taskFactory;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(taskFactory);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		taskFactory = (UnsakerFutureTaskFactory) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
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
		UnsakerTaskFactoryTaskIdentifier other = (UnsakerTaskFactoryTaskIdentifier) obj;
		if (taskFactory == null) {
			if (other.taskFactory != null)
				return false;
		} else if (!taskFactory.equals(other.taskFactory))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "(unsaker:" + taskFactory + ")";
	}

}
