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

import saker.build.file.path.SakerPath;
import saker.build.task.identifier.TaskIdentifier;

public class StaticScriptVariableTaskIdentifier implements TaskIdentifier, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath scriptPath;
	private String variableName;

	/**
	 * For {@link Externalizable}.
	 */
	public StaticScriptVariableTaskIdentifier() {
	}

	public StaticScriptVariableTaskIdentifier(SakerPath scriptPath, String variableName) {
		this.scriptPath = scriptPath;
		this.variableName = variableName;
	}

	public String getVariableName() {
		return variableName;
	}

	public SakerPath getScriptPath() {
		return scriptPath;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(scriptPath);
		out.writeUTF(variableName);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		scriptPath = (SakerPath) in.readObject();
		variableName = in.readUTF();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((scriptPath == null) ? 0 : scriptPath.hashCode());
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
		StaticScriptVariableTaskIdentifier other = (StaticScriptVariableTaskIdentifier) obj;
		if (scriptPath == null) {
			if (other.scriptPath != null)
				return false;
		} else if (!scriptPath.equals(other.scriptPath))
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
		return getClass().getSimpleName() + "[" + (scriptPath != null ? "scriptPath=" + scriptPath + ", " : "")
				+ (variableName != null ? "variableName=" + variableName : "") + "]";
	}

}
