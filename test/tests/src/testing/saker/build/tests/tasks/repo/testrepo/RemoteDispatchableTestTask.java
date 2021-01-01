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
package testing.saker.build.tests.tasks.repo.testrepo;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableSet;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskFactory;
import saker.build.task.utils.annot.SakerInput;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.TestClusterNameExecutionEnvironmentSelector;
import testing.saker.build.tests.tasks.cluster.ClusterBuildTestCase;

public class RemoteDispatchableTestTask implements TaskFactory<String>, Externalizable {
	private static final long serialVersionUID = 1L;

	private final class TaskImplementation implements ParameterizableTask<String> {
		@SakerInput
		public String Input;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			if (!taskcontext.getExecutionContext().getEnvironment().getUserParameters()
					.get(EnvironmentTestCase.TEST_CLUSTER_NAME_ENV_PARAM)
					.equals(ClusterBuildTestCase.DEFAULT_CLUSTER_NAME)) {
				throw new AssertionError();
			}
			return "_" + Input + "_";
		}
	}

	@Override
	@SuppressWarnings("deprecation")
	public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
		return new TestClusterNameExecutionEnvironmentSelector(ClusterBuildTestCase.DEFAULT_CLUSTER_NAME);
	}

	@Override
	@SuppressWarnings("deprecation")
	public NavigableSet<String> getCapabilities() {
		return ObjectUtils.newTreeSet(CAPABILITY_REMOTE_DISPATCHABLE);
	}

	@Override
	public Task<? extends String> createTask(ExecutionContext executioncontext) {
		return new TaskImplementation();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj != null && this.getClass() == obj.getClass();
	}
}
