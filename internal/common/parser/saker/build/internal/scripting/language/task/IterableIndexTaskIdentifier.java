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

public class IterableIndexTaskIdentifier implements TaskIdentifier, Externalizable {
	private static final long serialVersionUID = 1L;

	protected TaskIdentifier iterableTaskId;
	protected int index;
	protected Object iterableModificationStamp;

	public IterableIndexTaskIdentifier() {
	}

	public IterableIndexTaskIdentifier(TaskIdentifier iterableTaskId, int index, Object iterableModificationStamp) {
		this.iterableTaskId = iterableTaskId;
		this.index = index;
		this.iterableModificationStamp = iterableModificationStamp;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + index;
		result = prime * result + ((iterableModificationStamp == null) ? 0 : iterableModificationStamp.hashCode());
		result = prime * result + ((iterableTaskId == null) ? 0 : iterableTaskId.hashCode());
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
		IterableIndexTaskIdentifier other = (IterableIndexTaskIdentifier) obj;
		if (index != other.index)
			return false;
		if (iterableModificationStamp == null) {
			if (other.iterableModificationStamp != null)
				return false;
		} else if (!iterableModificationStamp.equals(other.iterableModificationStamp))
			return false;
		if (iterableTaskId == null) {
			if (other.iterableTaskId != null)
				return false;
		} else if (!iterableTaskId.equals(other.iterableTaskId))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "(iterableidx_tid:" + iterableTaskId + "[" + index + "])";
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(iterableTaskId);
		out.writeInt(index);
		out.writeObject(iterableModificationStamp);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		iterableTaskId = (TaskIdentifier) in.readObject();
		index = in.readInt();
		iterableModificationStamp = in.readObject();
	}
}