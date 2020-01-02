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
package testing.saker.build.tests.tasks.factories;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.SimpleStructuredListTaskResult;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class StructuredListReturnerTaskFactory
		implements TaskFactory<StructuredTaskResult>, Task<StructuredTaskResult>, Externalizable {
	private static final long serialVersionUID = 1L;

	private List<TaskIdentifier> taskId;

	/**
	 * For {@link Externalizable}.
	 */
	public StructuredListReturnerTaskFactory() {
	}

	public StructuredListReturnerTaskFactory(List<TaskIdentifier> taskId) {
		this.taskId = ImmutableUtils.makeImmutableList(taskId);
	}

	@Override
	public Task<? extends StructuredTaskResult> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public StructuredTaskResult run(TaskContext taskcontext) throws Exception {
		List<StructuredTaskResult> tasks = new ArrayList<>();
		for (TaskIdentifier tid : taskId) {
			tasks.add(new SimpleStructuredObjectTaskResult(tid));
		}
		return new SimpleStructuredListTaskResult(tasks);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, taskId);
		out.writeObject(taskId);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		taskId = SerialUtils.readExternalImmutableList(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((taskId == null) ? 0 : taskId.hashCode());
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
		StructuredListReturnerTaskFactory other = (StructuredListReturnerTaskFactory) obj;
		if (taskId == null) {
			if (other.taskId != null)
				return false;
		} else if (!taskId.equals(other.taskId))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "StructuredListReturnerTaskFactory[" + (taskId != null ? "taskId=" + taskId : "") + "]";
	}
}
