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

import saker.build.internal.scripting.language.exc.OperandExecutionException;
import saker.build.internal.scripting.language.exc.SakerScriptEvaluationException;
import saker.build.task.TaskResultDependencyHandle;
import saker.build.task.TaskResultResolver;
import saker.build.task.exception.TaskExecutionFailedException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.StructuredObjectTaskResult;
import saker.build.task.utils.StructuredTaskResult;

public class SakerTaskObjectSakerTaskResult implements SakerTaskResult, StructuredObjectTaskResult {
	private static final long serialVersionUID = 1L;

	protected TaskIdentifier sakerTaskId;

	/**
	 * For {@link Externalizable}.
	 */
	public SakerTaskObjectSakerTaskResult() {
	}

	public SakerTaskObjectSakerTaskResult(TaskIdentifier sakertaskid) {
		this.sakerTaskId = sakertaskid;
	}

	@Override
	public TaskIdentifier getTaskIdentifier() {
		return sakerTaskId;
	}

	@Override
	public Object get(TaskResultResolver results) {
		return getSakerResult(results).get(results);
	}

	@Override
	public Object toResult(TaskResultResolver results) {
		return getSakerResult(results).toResult(results);
	}

	@Override
	public TaskResultDependencyHandle getDependencyHandle(TaskResultResolver results,
			TaskResultDependencyHandle handleforthis) {
		try {
			TaskResultDependencyHandle dephandle = results.getTaskResultDependencyHandle(sakerTaskId);
			return ((SakerTaskResult) dephandle.get()).getDependencyHandle(results, dephandle);
		} catch (TaskExecutionFailedException | SakerScriptEvaluationException e) {
			throw new OperandExecutionException("Failed to retrieve task result.", e, sakerTaskId);
		}
	}

	@Override
	public TaskResultDependencyHandle toResultDependencyHandle(TaskResultResolver results) throws NullPointerException {
		return StructuredTaskResult.getActualTaskResultDependencyHandle(sakerTaskId, results);
	}

	private SakerTaskResult getSakerResult(TaskResultResolver results) {
		try {
			return (SakerTaskResult) results.getTaskResult(sakerTaskId);
		} catch (TaskExecutionFailedException | SakerScriptEvaluationException e) {
			throw new OperandExecutionException("Failed to retrieve task result.", e, sakerTaskId);
		}
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(sakerTaskId);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		sakerTaskId = (TaskIdentifier) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((sakerTaskId == null) ? 0 : sakerTaskId.hashCode());
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
		SakerTaskObjectSakerTaskResult other = (SakerTaskObjectSakerTaskResult) obj;
		if (sakerTaskId == null) {
			if (other.sakerTaskId != null)
				return false;
		} else if (!sakerTaskId.equals(other.sakerTaskId))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + sakerTaskId + "]";
	}

}
