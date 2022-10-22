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

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.exception.IllegalTaskOperationException;
import saker.build.util.property.IDEConfigurationRequiredExecutionProperty;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;

@SakerTest
public class ExecutionDependencyTaskTest extends CollectingMetricEnvironmentTestCase {
	public static class PropertyDependentTaskFactory implements TaskFactory<String>, Task<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		protected String baseResult;

		public PropertyDependentTaskFactory() {
		}

		public PropertyDependentTaskFactory(String baseResult) {
			this.baseResult = baseResult;
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(baseResult);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			baseResult = (String) in.readObject();
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			return baseResult + ":" + taskcontext.getTaskUtilities()
					.getReportExecutionDependency(IDEConfigurationRequiredExecutionProperty.INSTANCE);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((baseResult == null) ? 0 : baseResult.hashCode());
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
			if (baseResult == null) {
				if (other.baseResult != null)
					return false;
			} else if (!baseResult.equals(other.baseResult))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "PropertyDependentTaskFactory [" + "baseResult=" + baseResult + "]";
		}
	}

	private static class MultiPropertyDependentTaskFactory extends PropertyDependentTaskFactory {
		private static final long serialVersionUID = 1L;

		public MultiPropertyDependentTaskFactory() {
			super();
		}

		public MultiPropertyDependentTaskFactory(String baseResult) {
			super(baseResult);
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			boolean val = taskcontext.getTaskUtilities()
					.getReportExecutionDependency(IDEConfigurationRequiredExecutionProperty.INSTANCE);
			taskcontext.reportExecutionDependency(IDEConfigurationRequiredExecutionProperty.INSTANCE, val);
			return baseResult + "_multi:" + val;
		}
	}

	private static class MultiDifferentPropertyDependentTaskFactory extends PropertyDependentTaskFactory {
		private static final long serialVersionUID = 1L;

		public MultiDifferentPropertyDependentTaskFactory() {
			super();
		}

		public MultiDifferentPropertyDependentTaskFactory(String baseResult) {
			super(baseResult);
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			boolean val = taskcontext.getTaskUtilities()
					.getReportExecutionDependency(IDEConfigurationRequiredExecutionProperty.INSTANCE);
			//an exception is expected when trying to report the same dependency again with a different value
			assertException(IllegalTaskOperationException.class, () -> taskcontext
					.reportExecutionDependency(IDEConfigurationRequiredExecutionProperty.INSTANCE, !val));
			return baseResult + "_multidiff:" + val;
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		ChildTaskStarterTaskFactory main = new ChildTaskStarterTaskFactory().add(strTaskId("ider"),
				new PropertyDependentTaskFactory("base"));

		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("ider"), "base:false");

		runTask("main", main);
		assertEmpty(getMetric().getRunTaskIdResults());

		parameters.setRequiresIDEConfiguration(true);

		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("ider"), "base:true").noRemaining();

		runTask("main", main);
		assertEmpty(getMetric().getRunTaskIdResults());

		parameters.setRequiresIDEConfiguration(false);
		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("ider"), "base:false").noRemaining();

		runTask("multi", new ChildTaskStarterTaskFactory().add(strTaskId("ider_multi"),
				new MultiPropertyDependentTaskFactory("base")));
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("ider_multi"), "base_multi:false");

		runTask("multidiff", new ChildTaskStarterTaskFactory().add(strTaskId("ider_multidiff"),
				new MultiDifferentPropertyDependentTaskFactory("base")));
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("ider_multidiff"), "base_multidiff:false");
	}

}
