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

import saker.build.task.TaskResultResolver;
import saker.build.task.exception.MissingRequiredParameterException;
import saker.build.task.identifier.TaskIdentifier;

public final class MissingBuildTargetParameterSakerTaskResult extends NoSakerTaskResult {
	private static final long serialVersionUID = 1L;

	private String parameterName;

	/**
	 * For {@link Externalizable}.
	 */
	public MissingBuildTargetParameterSakerTaskResult() {
	}

	public MissingBuildTargetParameterSakerTaskResult(TaskIdentifier taskid, String parameterName) {
		super(taskid);
		this.parameterName = parameterName;
	}

	@Override
	public Object toResult(TaskResultResolver results) {
		throw new MissingRequiredParameterException("Build target parameter is missing: " + parameterName, taskId);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(parameterName);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		parameterName = (String) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((parameterName == null) ? 0 : parameterName.hashCode());
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
		MissingBuildTargetParameterSakerTaskResult other = (MissingBuildTargetParameterSakerTaskResult) obj;
		if (parameterName == null) {
			if (other.parameterName != null)
				return false;
		} else if (!parameterName.equals(other.parameterName))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + parameterName + "]";
	}

}
