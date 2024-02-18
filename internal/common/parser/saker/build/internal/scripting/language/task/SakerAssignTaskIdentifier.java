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

public class SakerAssignTaskIdentifier implements TaskIdentifier, Externalizable {
	private static final long serialVersionUID = 1L;

	protected TaskIdentifier rootIdentifier;
	protected String variableName;

	public SakerAssignTaskIdentifier() {
	}

	public SakerAssignTaskIdentifier(TaskIdentifier rootIdentifier, String variablename) {
		this.rootIdentifier = rootIdentifier;
		this.variableName = variablename;
	}

	public TaskIdentifier getRootIdentifier() {
		return rootIdentifier;
	}

	public String getVariableName() {
		return variableName;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(rootIdentifier);
		out.writeObject(variableName);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		rootIdentifier = (TaskIdentifier) in.readObject();
		variableName = (String) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((rootIdentifier == null) ? 0 : rootIdentifier.hashCode());
		result = prime * result + ((variableName == null) ? 0 : variableName.hashCode());
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
		SakerAssignTaskIdentifier other = (SakerAssignTaskIdentifier) obj;
		if (rootIdentifier == null) {
			if (other.rootIdentifier != null)
				return false;
		} else if (!rootIdentifier.equals(other.rootIdentifier))
			return false;
		if (variableName == null) {
			if (other.variableName != null)
				return false;
		} else if (!variableName.equals(other.variableName))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "(assign_tid:" + rootIdentifier + "$" + variableName + ")";
	}

}