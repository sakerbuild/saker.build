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
import saker.build.task.TaskContext;
import saker.build.task.TaskResultDependencyHandle;
import saker.build.task.TaskResultResolver;
import saker.build.task.dependencies.CommonTaskOutputChangeDetector;
import saker.build.task.exception.TaskExecutionDeadlockedException;
import saker.build.task.identifier.GlobalValueTaskIdentifier;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.StructuredObjectTaskResult;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;

public class GlobalVariableTaskResult implements SakerTaskResult, AssignableTaskResult, StructuredObjectTaskResult {
	private static final long serialVersionUID = 1L;

	private GlobalValueTaskIdentifier variableTaskIdentifier;

	/**
	 * For {@link Externalizable}.
	 */
	public GlobalVariableTaskResult() {
	}

	public GlobalVariableTaskResult(GlobalValueTaskIdentifier variableTaskIdentifier) {
		this.variableTaskIdentifier = variableTaskIdentifier;
	}

	@Override
	public TaskIdentifier getTaskIdentifier() {
		return variableTaskIdentifier;
	}

	@Override
	public Object toResult(TaskResultResolver results) {
		Object vartaskresult;
		try {
			vartaskresult = results.getTaskResult(variableTaskIdentifier);
		} catch (TaskExecutionDeadlockedException e) {
			throw unassignedDeadlockedExc();
		}
		if (vartaskresult instanceof StructuredTaskResult) {
			return ((StructuredTaskResult) vartaskresult).toResult(results);
		}
		return vartaskresult;
	}

	@Override
	public Object get(TaskResultResolver results) {
		Object result;
		try {
			result = results.getTaskResult(variableTaskIdentifier);
		} catch (TaskExecutionDeadlockedException e) {
			throw unassignedDeadlockedExc();
		}
		if (result instanceof SakerTaskResult) {
			return ((SakerTaskResult) result).get(results);
		}
		return result;
	}

	@Override
	public TaskResultDependencyHandle getDependencyHandle(TaskResultResolver results,
			TaskResultDependencyHandle handleforthis) {
		TaskResultDependencyHandle dephandle = results.getTaskResultDependencyHandle(variableTaskIdentifier);
		Object handleval;
		try {
			handleval = dephandle.get();
		} catch (TaskExecutionDeadlockedException e) {
			throw unassignedDeadlockedExc();
		}
		if (handleval instanceof SakerTaskResult) {
			dephandle.setTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(handleval));
			TaskResultDependencyHandle resulthandle = ((SakerTaskResult) handleval).getDependencyHandle(results,
					dephandle);
			if (resulthandle == dephandle) {
				return resulthandle.clone();
			}
			return resulthandle;
		}
		dephandle.setTaskOutputChangeDetector(CommonTaskOutputChangeDetector.notInstanceOf(SakerTaskResult.class));
		return dephandle.clone();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(variableTaskIdentifier);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		variableTaskIdentifier = (GlobalValueTaskIdentifier) in.readObject();
	}

	@Override
	public void assign(TaskContext taskcontext, SakerScriptTaskIdentifier currenttaskid, TaskIdentifier value) {
		taskcontext.getTaskUtilities().startTaskFuture(variableTaskIdentifier,
				new SakerTaskResultLiteralTaskFactory(value));
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
		GlobalVariableTaskResult other = (GlobalVariableTaskResult) obj;
		if (variableTaskIdentifier == null) {
			if (other.variableTaskIdentifier != null)
				return false;
		} else if (!variableTaskIdentifier.equals(other.variableTaskIdentifier))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "["
				+ (variableTaskIdentifier != null ? "variableTaskIdentifier=" + variableTaskIdentifier : "") + "]";
	}

	private UnassignedVariableExecutionException unassignedDeadlockedExc() {
		return new UnassignedVariableExecutionException("Variable global(" + variableTaskIdentifier.getVariableName()
				+ ") was not assigned. (execution deadlocked)", variableTaskIdentifier);
	}
}
