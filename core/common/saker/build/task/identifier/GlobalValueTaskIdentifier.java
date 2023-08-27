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
package saker.build.task.identifier;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.apiextract.api.PublicApi;
import saker.build.util.data.annotation.ValueType;

/**
 * {@link TaskIdentifier} implementation for identifying a task that is globally accessible regardless of scripting
 * language implementation.
 * <p>
 * This task identifier can be used to set values during build execution to a user specified value. This task identifier
 * class is declared in the build system API to allow different scripting languages to access global values by name.
 * <p>
 * The built-in scripting language provides this feature using the <code>global(VariableName)</code> task.
 */
@PublicApi
@ValueType
public final class GlobalValueTaskIdentifier implements TaskIdentifier, Externalizable {
	private static final long serialVersionUID = 1L;

	protected String variableName;

	/**
	 * For {@link Externalizable}.
	 */
	public GlobalValueTaskIdentifier() {
	}

	/**
	 * Creates a new instance for the specified variable name
	 * 
	 * @param variableName
	 *            The variable name.
	 */
	public GlobalValueTaskIdentifier(String variableName) {
		this.variableName = variableName;
	}

	/**
	 * Gets the name of the variable.
	 * 
	 * @return The name.
	 */
	public String getVariableName() {
		return variableName;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeUTF(variableName);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		variableName = in.readUTF();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
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
		GlobalValueTaskIdentifier other = (GlobalValueTaskIdentifier) obj;
		if (variableName == null) {
			if (other.variableName != null)
				return false;
		} else if (!variableName.equals(other.variableName))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "(global:" + variableName + ")";
	}

}
