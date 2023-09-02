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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.SimpleStructuredMapTaskResult;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import testing.saker.build.tests.StringTaskIdentifier;

public class ChildTaskStarterTaskFactory
		implements TaskFactory<StructuredTaskResult>, Task<StructuredTaskResult>, Externalizable {
	private static final long serialVersionUID = 1L;

	protected Map<TaskIdentifier, TaskFactory<?>> namedChildTaskValues = new LinkedHashMap<>();

	public ChildTaskStarterTaskFactory() {
	}

	public ChildTaskStarterTaskFactory(Map<TaskIdentifier, TaskFactory<?>> namedChildTaskValues) {
		this.namedChildTaskValues = namedChildTaskValues;
	}

	public ChildTaskStarterTaskFactory add(TaskIdentifier taskid, TaskFactory<?> factory) {
		namedChildTaskValues.put(taskid, factory);
		return this;
	}

	public ChildTaskStarterTaskFactory add(String taskidname, TaskFactory<?> factory) {
		return add(new StringTaskIdentifier(taskidname), factory);
	}

	public ChildTaskStarterTaskFactory remove(TaskIdentifier taskid) {
		namedChildTaskValues.remove(taskid);
		return this;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalMap(out, namedChildTaskValues);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		namedChildTaskValues = SerialUtils.readExternalMap(new HashMap<>(), in);
	}

	@Override
	public Task<StructuredTaskResult> createTask(ExecutionContext context) {
		return this;
	}

	@Override
	public StructuredTaskResult run(TaskContext context) throws Exception {
		NavigableMap<String, StructuredTaskResult> resultmap = new TreeMap<>();
		for (Entry<? extends TaskIdentifier, ? extends TaskFactory<?>> entry : namedChildTaskValues.entrySet()) {
			TaskIdentifier taskid = entry.getKey();
			startTask(context, taskid, entry.getValue());
			resultmap.put(taskid.toString(), new SimpleStructuredObjectTaskResult(taskid));
		}
		return new SimpleStructuredMapTaskResult(resultmap);
	}

	protected void startTask(TaskContext context, TaskIdentifier taskid, TaskFactory<?> taskfactory) {
		context.getTaskUtilities().startTaskFuture(taskid, taskfactory);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((namedChildTaskValues == null) ? 0 : namedChildTaskValues.hashCode());
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
		ChildTaskStarterTaskFactory other = (ChildTaskStarterTaskFactory) obj;
		if (namedChildTaskValues == null) {
			if (other.namedChildTaskValues != null)
				return false;
		} else if (!namedChildTaskValues.equals(other.namedChildTaskValues))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "ChildTaskCreatorTaskFactory ["
				+ (namedChildTaskValues != null ? "namedChildTaskValues=" + namedChildTaskValues : "") + "]";
	}

}