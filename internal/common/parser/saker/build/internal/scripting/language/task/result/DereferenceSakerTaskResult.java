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
package saker.build.internal.scripting.language.task.result;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.internal.scripting.language.exc.OperandExecutionException;
import saker.build.internal.scripting.language.task.SakerScriptTaskIdentifier;
import saker.build.task.TaskResultResolver;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.StructuredTaskResult;

public class DereferenceSakerTaskResult extends AbstractDereferenceSakerTaskResult {
	private static final long serialVersionUID = 1L;

	protected TaskIdentifier variableTaskId;

	public DereferenceSakerTaskResult() {
	}

	public DereferenceSakerTaskResult(SakerScriptTaskIdentifier ownerTaskId, TaskIdentifier variableTaskId) {
		super(ownerTaskId);
		this.ownerRootTaskId = ownerTaskId.getRootIdentifier();
		this.variableTaskId = variableTaskId;
	}

	@Override
	protected String getVariableName(TaskResultResolver results) {
		Object nameval = StructuredTaskResult.getActualTaskResult(variableTaskId, results);
		if (nameval == null) {
			throw new OperandExecutionException("Variable name evaluated to null.", variableTaskId);
		}
		return nameval.toString();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(variableTaskId);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		variableTaskId = (TaskIdentifier) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((variableTaskId == null) ? 0 : variableTaskId.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		DereferenceSakerTaskResult other = (DereferenceSakerTaskResult) obj;
		if (variableTaskId == null) {
			if (other.variableTaskId != null)
				return false;
		} else if (!variableTaskId.equals(other.variableTaskId))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "["
				+ (ownerRootTaskId != null ? "ownerTaskId=" + ownerRootTaskId + ", " : "")
				+ (variableTaskId != null ? "variableTaskId=" + variableTaskId : "") + "]";
	}

}
