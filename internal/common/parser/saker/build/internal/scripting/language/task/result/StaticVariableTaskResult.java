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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.internal.scripting.language.exc.UnassignedVariableExecutionException;
import saker.build.internal.scripting.language.task.AssignableTaskResult;
import saker.build.internal.scripting.language.task.SakerScriptTaskIdentifier;
import saker.build.internal.scripting.language.task.SakerTaskResultLiteralTaskFactory;
import saker.build.internal.scripting.language.task.StaticScriptVariableTaskIdentifier;
import saker.build.task.TaskContext;
import saker.build.task.TaskResultResolver;
import saker.build.task.exception.TaskExecutionDeadlockedException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.StructuredObjectTaskResult;

public class StaticVariableTaskResult implements SakerTaskResult, AssignableTaskResult, StructuredObjectTaskResult {
	private static final long serialVersionUID = 1L;

	private StaticScriptVariableTaskIdentifier variableTaskIdentifier;

	/**
	 * For {@link Externalizable}.
	 */
	public StaticVariableTaskResult() {
	}

	public StaticVariableTaskResult(StaticScriptVariableTaskIdentifier variableTaskIdentifier) {
		this.variableTaskIdentifier = variableTaskIdentifier;
	}

	@Override
	public TaskIdentifier getTaskIdentifier() {
		return variableTaskIdentifier;
	}

	@Override
	public Object toResult(TaskResultResolver results) throws NullPointerException, RuntimeException {
		try {
			return StructuredObjectTaskResult.super.toResult(results);
		} catch (TaskExecutionDeadlockedException e) {
			throw new UnassignedVariableExecutionException(
					"Variable static(" + variableTaskIdentifier.getVariableName() + ") in "
							+ variableTaskIdentifier.getScriptPath() + " was not assigned. (execution deadlocked)",
					variableTaskIdentifier);
		}
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(variableTaskIdentifier);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		variableTaskIdentifier = (StaticScriptVariableTaskIdentifier) in.readObject();
	}

	@Override
	public void assign(TaskContext taskcontext, SakerScriptTaskIdentifier currenttaskid, TaskIdentifier value) {
		taskcontext.getTaskUtilities().startTask(variableTaskIdentifier, new SakerTaskResultLiteralTaskFactory(value));
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((variableTaskIdentifier == null) ? 0 : variableTaskIdentifier.hashCode());
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
		StaticVariableTaskResult other = (StaticVariableTaskResult) obj;
		if (variableTaskIdentifier == null) {
			if (other.variableTaskIdentifier != null)
				return false;
		} else if (!variableTaskIdentifier.equals(other.variableTaskIdentifier))
			return false;
		return true;
	}

}
