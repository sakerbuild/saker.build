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
import java.util.Map;
import java.util.Map.Entry;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class SequentialChildTaskStarterTaskFactory implements TaskFactory<Void>, Task<Void>, Externalizable {
	private static final long serialVersionUID = 1L;

	private List<Map.Entry<TaskIdentifier, TaskFactory<?>>> tasks = new ArrayList<>();

	public SequentialChildTaskStarterTaskFactory() {
	}

	public SequentialChildTaskStarterTaskFactory add(TaskIdentifier taskid, TaskFactory<?> factory) {
		tasks.add(ImmutableUtils.makeImmutableMapEntry(taskid, factory));
		return this;
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
	public Task<Void> createTask(ExecutionContext context) {
		return this;
	}

	@Override
	public Void run(TaskContext context) {
		for (Entry<? extends TaskIdentifier, ? extends TaskFactory<?>> entry : tasks) {
			TaskIdentifier taskid = entry.getKey();
			TaskFactory<?> taskfactory = entry.getValue();
			System.out.println("SEQ START: " + context.getTaskId() + " - " + taskid);
			Object result = runTask(context, taskid, taskfactory);
			System.out.println("SEQ WAIT:  " + context.getTaskId() + " - " + taskid + " -> " + result);
		}
		return null;
	}

	protected Object runTask(TaskContext context, TaskIdentifier taskid, TaskFactory<?> taskfactory) {
		//Note: keep this as runTaskResult by default
		return context.getTaskUtilities().runTaskResult(taskid, taskfactory);
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
		SequentialChildTaskStarterTaskFactory other = (SequentialChildTaskStarterTaskFactory) obj;
		if (tasks == null) {
			if (other.tasks != null)
				return false;
		} else if (!tasks.equals(other.tasks))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "SequentialChildTaskStarterTaskFactory [" + tasks + "]";
	}

}