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

import java.util.Set;

import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.task.TaskContext;
import saker.build.task.exception.IllegalTaskOperationException;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.util.property.SystemPropertyEnvironmentProperty;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;
import testing.saker.build.tests.tasks.factories.StringEnvironmentPropertyDependentTaskFactory;

@SakerTest
public class EnvironmentDependencyTaskTest extends CollectingMetricEnvironmentTestCase {
	private static final String TEST_PROPERTY_NAME = EnvironmentDependencyTaskTest.class.getName() + ".test.property";

	private static class MultiReportPropertyDependentTaskFactory extends StringEnvironmentPropertyDependentTaskFactory {
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

	private static class MultiDifferentReportPropertyDependentTaskFactory
			extends StringEnvironmentPropertyDependentTaskFactory {
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

		StringEnvironmentPropertyDependentTaskFactory fac = new StringEnvironmentPropertyDependentTaskFactory(
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

		StringEnvironmentPropertyDependentTaskFactory multifac = new MultiReportPropertyDependentTaskFactory(
				new SystemPropertyEnvironmentProperty(TEST_PROPERTY_NAME));

		System.setProperty(TEST_PROPERTY_NAME, "test.multi.value");
		environment.invalidateEnvironmentPropertiesWaitExecutions(Functionals.alwaysPredicate());
		runTask(strTaskId("multi"), multifac);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("multi"), "test.multi.value").noRemaining();

		StringEnvironmentPropertyDependentTaskFactory multidifffac = new MultiDifferentReportPropertyDependentTaskFactory(
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
