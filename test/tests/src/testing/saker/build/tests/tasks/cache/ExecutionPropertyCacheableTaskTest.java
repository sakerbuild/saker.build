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
package testing.saker.build.tests.tasks.cache;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.NavigableSet;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionProperty;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.util.property.UserParameterExecutionProperty;
import testing.saker.SakerTest;

@SakerTest
public class ExecutionPropertyCacheableTaskTest extends CacheableTaskTestCase {
	private static final String TEST_PROPERTY_NAME = ExecutionPropertyCacheableTaskTest.class.getName()
			+ ".test.property";

	private static class PropertyDependentTaskFactory implements TaskFactory<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		protected ExecutionProperty<String> dependency;

		public PropertyDependentTaskFactory() {
		}

		public PropertyDependentTaskFactory(ExecutionProperty<String> dependency) {
			this.dependency = dependency;
		}

		@Override
		@SuppressWarnings("deprecation")
		public NavigableSet<String> getCapabilities() {
			return ImmutableUtils.singletonNavigableSet(TaskFactory.CAPABILITY_CACHEABLE);
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return new Task<String>() {
				@Override
				public String run(TaskContext taskcontext) throws Exception {
					String result = taskcontext.getTaskUtilities().getReportExecutionDependency(dependency);
					return result;
				}
			};
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(dependency);
		}

		@SuppressWarnings("unchecked")
		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			dependency = (ExecutionProperty<String>) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((dependency == null) ? 0 : dependency.hashCode());
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
			PropertyDependentTaskFactory other = (PropertyDependentTaskFactory) obj;
			if (dependency == null) {
				if (other.dependency != null)
					return false;
			} else if (!dependency.equals(other.dependency))
				return false;
			return true;
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		PropertyDependentTaskFactory main = new PropertyDependentTaskFactory(
				new UserParameterExecutionProperty(TEST_PROPERTY_NAME));

		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), null).noRemaining();
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf("main"));

		cleanProject();
		parameters.setUserParameters(ImmutableUtils.singletonMap(TEST_PROPERTY_NAME, "test.value"));
		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), "test.value").noRemaining();
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf("main"));

		cleanProject();
		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), "test.value").noRemaining();
		assertEquals(getMetric().getCacheRetrievedTasks(), strTaskIdSetOf("main"));
		waitExecutionFinalization();
		assertEmpty(getMetric().getCachePublishedTasks());

		cleanProject();
		parameters.setUserParameters(ImmutableUtils.singletonMap(TEST_PROPERTY_NAME, "changed"));
		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), "changed").noRemaining();
		assertEmpty(getMetric().getCacheRetrievedTasks());
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf("main"));

		cleanProject();
		parameters.setUserParameters(Collections.emptyMap());
		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), null).noRemaining();
		assertEquals(getMetric().getCacheRetrievedTasks(), strTaskIdSetOf("main"));
	}
}
