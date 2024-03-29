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
import saker.build.internal.scripting.language.exc.SakerScriptEvaluationException;
import saker.build.internal.scripting.language.exc.UnassignedVariableExecutionException;
import saker.build.internal.scripting.language.task.AssignableTaskResult;
import saker.build.internal.scripting.language.task.SakerAssignTaskIdentifier;
import saker.build.internal.scripting.language.task.SakerScriptTaskIdentifier;
import saker.build.internal.scripting.language.task.operators.AssignmentTaskFactory;
import saker.build.task.TaskContext;
import saker.build.task.TaskResultDependencyHandle;
import saker.build.task.TaskResultResolver;
import saker.build.task.exception.TaskExecutionDeadlockedException;
import saker.build.task.exception.TaskExecutionFailedException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.ComposedStructuredTaskResult;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.task.utils.SupplierForwardingTaskResultDependencyHandle;

public abstract class AbstractDereferenceSakerTaskResult
		implements SakerTaskResult, AssignableTaskResult, ComposedStructuredTaskResult {
	private static final long serialVersionUID = 1L;

	protected TaskIdentifier ownerRootTaskId;

	public AbstractDereferenceSakerTaskResult() {
	}

	public AbstractDereferenceSakerTaskResult(TaskIdentifier ownerRootTaskId) {
		this.ownerRootTaskId = ownerRootTaskId;
	}

	@Override
	public Object toResult(TaskResultResolver results) {
		SakerAssignTaskIdentifier assigntaskid = getVariableAssignTaskId(results);
		StructuredTaskResult vartaskres = getVariableTaskResult(results, assigntaskid);
		try {
			return vartaskres.toResult(results);
		} catch (TaskExecutionFailedException | SakerScriptEvaluationException e) {
			throw new OperandExecutionException("Failed to evaluate $" + assigntaskid.getVariableName() + " variable.",
					e, assigntaskid);
		} catch (TaskExecutionDeadlockedException e) {
			throw new UnassignedVariableExecutionException(
					"Failed to evaluate $" + assigntaskid.getVariableName() + " variable. (execution deadlocked)",
					assigntaskid);
		}
	}

	@Override
	public TaskResultDependencyHandle toResultDependencyHandle(TaskResultResolver results) throws NullPointerException {
		return new SupplierForwardingTaskResultDependencyHandle(() -> {
			SakerAssignTaskIdentifier assigntaskid = getVariableAssignTaskId(results);
			StructuredTaskResult vartaskres = getVariableTaskResult(results, assigntaskid);
			try {
				return vartaskres.toResultDependencyHandle(results);
			} catch (TaskExecutionFailedException | SakerScriptEvaluationException e) {
				throw new OperandExecutionException(
						"Failed to evaluate $" + assigntaskid.getVariableName() + " variable.", e, assigntaskid);
			} catch (TaskExecutionDeadlockedException e) {
				throw new UnassignedVariableExecutionException(
						"Failed to evaluate $" + assigntaskid.getVariableName() + " variable. (execution deadlocked)",
						assigntaskid);
			}
		});
	}

	@Override
	public StructuredTaskResult getIntermediateTaskResult(TaskResultResolver results)
			throws NullPointerException, RuntimeException {
		SakerAssignTaskIdentifier assigntaskid = getVariableAssignTaskId(results);
		return new SimpleStructuredObjectTaskResult(assigntaskid);
	}

	@Override
	public TaskResultDependencyHandle toIntermediateTaskResultDependencyHandle(TaskResultResolver results)
			throws NullPointerException {
		return new SupplierForwardingTaskResultDependencyHandle(
				() -> TaskResultDependencyHandle.create(getIntermediateTaskResult(results)));
	}

	@Override
	public void assign(TaskContext taskcontext, SakerScriptTaskIdentifier currenttaskid, TaskIdentifier value) {
		String variablename = getVariableName(taskcontext);
		AssignmentTaskFactory.startAssignmentTask(taskcontext, currenttaskid.getRootIdentifier(), variablename, value);
	}

	private static StructuredTaskResult getVariableTaskResult(TaskResultResolver results,
			SakerAssignTaskIdentifier assigntaskid) {
		try {
			return (StructuredTaskResult) results.getTaskResult(assigntaskid);
		} catch (TaskExecutionFailedException | SakerScriptEvaluationException e) {
			throw new OperandExecutionException("Failed to evaluate $" + assigntaskid.getVariableName() + " variable.",
					e, assigntaskid);
		} catch (TaskExecutionDeadlockedException e) {
			throw new UnassignedVariableExecutionException(
					"Variable $" + assigntaskid.getVariableName() + " was not assigned. (execution deadlocked)",
					assigntaskid);
		}
	}

	private SakerAssignTaskIdentifier getVariableAssignTaskId(TaskResultResolver results) {
		String varname = getVariableName(results);
		return AssignmentTaskFactory.createAssignTaskIdentifier(ownerRootTaskId, varname);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(ownerRootTaskId);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		ownerRootTaskId = (TaskIdentifier) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((ownerRootTaskId == null) ? 0 : ownerRootTaskId.hashCode());
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
		AbstractDereferenceSakerTaskResult other = (AbstractDereferenceSakerTaskResult) obj;
		if (ownerRootTaskId == null) {
			if (other.ownerRootTaskId != null)
				return false;
		} else if (!ownerRootTaskId.equals(other.ownerRootTaskId))
			return false;
		return true;
	}

	protected abstract String getVariableName(TaskResultResolver results);

}
