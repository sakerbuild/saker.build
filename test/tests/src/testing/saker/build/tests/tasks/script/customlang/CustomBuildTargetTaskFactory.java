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
package testing.saker.build.tests.tasks.script.customlang;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.NavigableSet;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.BuildTargetTask;
import saker.build.task.BuildTargetTaskFactory;
import saker.build.task.BuildTargetTaskResult;
import saker.build.task.SimpleBuildTargetTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.TaskUtils;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.TestClusterNameExecutionEnvironmentSelector;
import testing.saker.build.tests.tasks.cluster.ClusterBuildTestCase;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

public final class CustomBuildTargetTaskFactory implements BuildTargetTaskFactory, BuildTargetTask, Externalizable {

	public static final class RemoteableStringTaskFactory extends StringTaskFactory {
		private static final long serialVersionUID = 1L;

		public RemoteableStringTaskFactory() {
		}

		public RemoteableStringTaskFactory(String result) {
			super(result);
		}

		@Override
		public NavigableSet<String> getCapabilities() {
			return ImmutableUtils.singletonNavigableSet(TaskFactory.CAPABILITY_REMOTE_DISPATCHABLE);
		}

		@Override
		public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
			return new TestClusterNameExecutionEnvironmentSelector(ClusterBuildTestCase.DEFAULT_CLUSTER_NAME);
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			if (!taskcontext.getExecutionContext().getEnvironment().getUserParameters()
					.get(EnvironmentTestCase.TEST_CLUSTER_NAME_ENV_PARAM)
					.equals(ClusterBuildTestCase.DEFAULT_CLUSTER_NAME)) {
				throw new AssertionError();
			}
			return super.run(taskcontext);
		}
	}

	private static final long serialVersionUID = 1L;

	private TaskName taskName;

	/**
	 * For {@link Externalizable}.
	 */
	public CustomBuildTargetTaskFactory() {
	}

	public CustomBuildTargetTaskFactory(TaskName taskname) {
		this.taskName = taskname;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(taskName);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		taskName = (TaskName) in.readObject();
	}

	@Override
	public NavigableSet<String> getTargetInputParameterNames() {
		return Collections.emptyNavigableSet();
	}

	@Override
	public BuildTargetTask createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public BuildTargetTaskResult run(TaskContext taskcontext) throws Exception {
		invoker:
		{
			if (taskName.getTaskQualifiers().isEmpty()) {
				String taskname = taskName.getName();
				switch (taskname) {
					case "builtin.task": {
						String val = getAppendClassBasedStringValue();
						StringTaskFactory taskfactory = new StringTaskFactory(val);
						taskcontext.getTaskUtilities().startTaskFuture(
								TaskIdentifier.builder(String.class.getName()).field("val", taskname).build(),
								taskfactory);
						break invoker;
					}
					case "builtin.remote.task": {
						String val = getAppendClassBasedStringValue() + "_remote";
						StringTaskFactory taskfactory = new RemoteableStringTaskFactory(val);
						taskcontext.getTaskUtilities().startTaskFuture(
								TaskIdentifier.builder(String.class.getName()).field("val", taskname).build(),
								taskfactory);
						break invoker;
					}
					default: {
						break;
					}
				}
			}
			ExecutionContext execcontext = taskcontext.getExecutionContext();
			TaskFactory<?> task = TaskUtils.createTask(execcontext, taskName, null);
			TaskIdentifier subid = TaskIdentifier.builder(CustomBuildTargetTaskFactory.class.getName())
					.field("this", taskcontext.getTaskId()).field("taskname", taskName).build();
			taskcontext.getTaskUtilities().startTaskFuture(subid, task);
		}
		return new SimpleBuildTargetTaskResult(Collections.emptyNavigableMap());
	}

	private static String getAppendClassBasedStringValue() {
		String val;
		try {
			new AppendCustomLangClass();
			val = "append";
		} catch (LinkageError e) {
			val = "hello";
		}
		return val;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((taskName == null) ? 0 : taskName.hashCode());
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
		CustomBuildTargetTaskFactory other = (CustomBuildTargetTaskFactory) obj;
		if (taskName == null) {
			if (other.taskName != null)
				return false;
		} else if (!taskName.equals(other.taskName))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (taskName != null ? "taskName=" + taskName : "") + "]";
	}
}