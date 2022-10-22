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
package testing.saker.build.tests.tasks;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Set;

import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.exception.IllegalTaskOperationException;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.util.property.SystemPropertyEnvironmentProperty;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;

@SakerTest
public class EnvironmentDependencyTaskTest extends CollectingMetricEnvironmentTestCase {
	private static final String TEST_PROPERTY_NAME = EnvironmentDependencyTaskTest.class.getName() + ".test.property";

	private static class PropertyDependentTaskFactory implements TaskFactory<String>, Externalizable, Task<String> {
		private static final long serialVersionUID = 1L;

		protected EnvironmentProperty<String> dependency;

		public PropertyDependentTaskFactory() {
		}

		public PropertyDependentTaskFactory(EnvironmentProperty<String> dependency) {
			this.dependency = dependency;
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			return taskcontext.getTaskUtilities().getReportEnvironmentDependency(dependency);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(dependency);
		}

		@SuppressWarnings("unchecked")
		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			dependency = (EnvironmentProperty<String>) in.readObject();
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

	private static class MultiReportPropertyDependentTaskFactory extends PropertyDependentTaskFactory {
		private static final long serialVersionUID = 1L;

		public MultiReportPropertyDependentTaskFactory() {
			super();
		}

		public MultiReportPropertyDependentTaskFactory(EnvironmentProperty<String> dependency) {
			super(dependency);
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			String result = taskcontext.getTaskUtilities().getReportEnvironmentDependency(dependency);
			String result2 = taskcontext.getTaskUtilities().getReportEnvironmentDependency(dependency);
			assertEquals(result, result2);
			return result;
		}
	}

	private static class MultiDifferentReportPropertyDependentTaskFactory extends PropertyDependentTaskFactory {
		private static final long serialVersionUID = 1L;

		public MultiDifferentReportPropertyDependentTaskFactory() {
			super();
		}

		public MultiDifferentReportPropertyDependentTaskFactory(EnvironmentProperty<String> dependency) {
			super(dependency);
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			String result = taskcontext.getTaskUtilities().getReportEnvironmentDependency(dependency);
			//an exception is expected when trying to report the same dependency again with a different value
			assertException(IllegalTaskOperationException.class,
					() -> taskcontext.reportEnvironmentDependency(dependency, result + "xxx"));
			return result;
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		System.clearProperty(TEST_PROPERTY_NAME);

		PropertyDependentTaskFactory fac = new PropertyDependentTaskFactory(
				new SystemPropertyEnvironmentProperty(TEST_PROPERTY_NAME));

		runTask(strTaskId("main"), fac);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), null).noRemaining();

		runTask(strTaskId("main"), fac);
		assertMap(getMetric().getRunTaskIdResults()).noRemaining();

		System.setProperty(TEST_PROPERTY_NAME, "test.value");
		environment.invalidateEnvironmentPropertiesWaitExecutions(Functionals.alwaysPredicate());
		runTask(strTaskId("main"), fac);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), "test.value").noRemaining();

		runTask(strTaskId("main"), fac);
		assertMap(getMetric().getRunTaskIdResults()).noRemaining();

		System.setProperty(TEST_PROPERTY_NAME, "changed");
		environment.invalidateEnvironmentPropertiesWaitExecutions(Functionals.alwaysPredicate());
		runTask(strTaskId("main"), fac);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), "changed").noRemaining();

		runTask(strTaskId("main"), fac);
		assertMap(getMetric().getRunTaskIdResults()).noRemaining();

		System.clearProperty(TEST_PROPERTY_NAME);
		environment.invalidateEnvironmentPropertiesWaitExecutions(Functionals.alwaysPredicate());
		runTask(strTaskId("main"), fac);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), null).noRemaining();

		PropertyDependentTaskFactory multifac = new MultiReportPropertyDependentTaskFactory(
				new SystemPropertyEnvironmentProperty(TEST_PROPERTY_NAME));

		System.setProperty(TEST_PROPERTY_NAME, "test.multi.value");
		environment.invalidateEnvironmentPropertiesWaitExecutions(Functionals.alwaysPredicate());
		runTask(strTaskId("multi"), multifac);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("multi"), "test.multi.value").noRemaining();

		PropertyDependentTaskFactory multidifffac = new MultiDifferentReportPropertyDependentTaskFactory(
				new SystemPropertyEnvironmentProperty(TEST_PROPERTY_NAME));

		System.setProperty(TEST_PROPERTY_NAME, "test.multi.diff.value");
		environment.invalidateEnvironmentPropertiesWaitExecutions(Functionals.alwaysPredicate());
		runTask(strTaskId("multidiff"), multidifffac);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("multidiff"), "test.multi.diff.value")
				.noRemaining();
	}

	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		return EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations())
				.setEnvironmentStorageDirectory(null).build();
	}

}
