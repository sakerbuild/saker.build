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
import java.util.List;

import saker.build.task.TaskResultResolver;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class CompoundStringLiteralSakerTaskResult implements SakerTaskResult {
	private static final long serialVersionUID = 1L;

	private List<TaskIdentifier> tasks;

	public CompoundStringLiteralSakerTaskResult() {
	}

	public CompoundStringLiteralSakerTaskResult(List<TaskIdentifier> tasks) {
		this.tasks = tasks;
	}

	@Override
	public Object toResult(TaskResultResolver results) {
		StringBuilder result = new StringBuilder();
		for (TaskIdentifier taskid : tasks) {
			StructuredTaskResult taskres = (StructuredTaskResult) results.getTaskResult(taskid);
			Object part = taskres.toResult(results);
			result.append(part);
		}
		return result.toString();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, tasks);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		tasks = SerialUtils.readExternalImmutableList(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((tasks == null) ? 0 : tasks.hashCode());
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
		CompoundStringLiteralSakerTaskResult other = (CompoundStringLiteralSakerTaskResult) obj;
		if (tasks == null) {
			if (other.tasks != null)
				return false;
		} else if (!tasks.equals(other.tasks))
			return false;
		return true;
	}

}
