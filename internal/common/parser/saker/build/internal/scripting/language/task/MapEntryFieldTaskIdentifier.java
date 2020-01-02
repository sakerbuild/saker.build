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

public class MapEntryFieldTaskIdentifier implements TaskIdentifier, Externalizable {
	private static final long serialVersionUID = 1L;

	protected TaskIdentifier iterableTaskId;
	protected Object key;
	protected String fieldId;
	protected Object iterableModificationStamp;

	public MapEntryFieldTaskIdentifier() {
	}

	public MapEntryFieldTaskIdentifier(TaskIdentifier iterableTaskId, Object key, String fieldId,
			Object iterableModificationStamp) {
		this.iterableTaskId = iterableTaskId;
		this.key = key;
		this.fieldId = fieldId;
		this.iterableModificationStamp = iterableModificationStamp;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeUTF(fieldId);
		out.writeObject(iterableTaskId);
		out.writeObject(key);
		out.writeObject(iterableModificationStamp);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		fieldId = in.readUTF();
		iterableTaskId = (TaskIdentifier) in.readObject();
		key = in.readObject();
		iterableModificationStamp = in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((fieldId == null) ? 0 : fieldId.hashCode());
		result = prime * result + ((iterableModificationStamp == null) ? 0 : iterableModificationStamp.hashCode());
		result = prime * result + ((iterableTaskId == null) ? 0 : iterableTaskId.hashCode());
		result = prime * result + ((key == null) ? 0 : key.hashCode());
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
		MapEntryFieldTaskIdentifier other = (MapEntryFieldTaskIdentifier) obj;
		if (fieldId == null) {
			if (other.fieldId != null)
				return false;
		} else if (!fieldId.equals(other.fieldId))
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
		if (key == null) {
			if (other.key != null)
				return false;
		} else if (!key.equals(other.key))
			return false;
		return true;
	}

}