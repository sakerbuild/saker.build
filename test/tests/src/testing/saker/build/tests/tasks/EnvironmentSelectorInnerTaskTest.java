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
import java.util.UUID;

import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.EnvironmentSelectionResult;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskFactory;
import saker.build.task.exception.IllegalTaskOperationException;
import saker.build.task.exception.TaskEnvironmentSelectionFailedException;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.util.property.SystemPropertyEnvironmentProperty;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class EnvironmentSelectorInnerTaskTest extends CollectingMetricEnvironmentTestCase {
	public static class SelectorInnerTaskStarter implements TaskFactory<String>, Task<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		private String propertyName;
		private String reportConflictingValue;

		public SelectorInnerTaskStarter() {
		}

		public SelectorInnerTaskStarter(String propertyName) {
			this.propertyName = propertyName;
		}

		public void setReportConflictingValue(String reportConflictingValue) {
			this.reportConflictingValue = reportConflictingValue;
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			if (reportConflictingValue != null) {
				taskcontext.reportEnvironmentDependency(new SystemPropertyEnvironmentProperty(propertyName),
						reportConflictingValue);
			}
			return taskcontext.startInnerTask(new PropertySelectorInnerTaskFactory(propertyName), null).getNext()
					.getResult();
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeUTF(propertyName);
			out.writeObject(reportConflictingValue);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			propertyName = in.readUTF();
			reportConflictingValue = (String) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((propertyName == null) ? 0 : propertyName.hashCode());
			result = prime * result + ((reportConflictingValue == null) ? 0 : reportConflictingValue.hashCode());
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
			SelectorInnerTaskStarter other = (SelectorInnerTaskStarter) obj;
			if (propertyName == null) {
				if (other.propertyName != null)
					return false;
			} else if (!propertyName.equals(other.propertyName))
				return false;
			if (reportConflictingValue == null) {
				if (other.reportConflictingValue != null)
					return false;
			} else if (!reportConflictingValue.equals(other.reportConflictingValue))
				return false;
			return true;
		}

	}

	public static class PropertySelectorInnerTaskFactory implements TaskFactory<String>, Task<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		private String propertyName;

		public PropertySelectorInnerTaskFactory() {
		}

		public PropertySelectorInnerTaskFactory(String propertyName) {
			this.propertyName = propertyName;
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			return taskcontext.getTaskUtilities()
					.getReportEnvironmentDependency(new SystemPropertyEnvironmentProperty(propertyName));
		}

		@Override
		public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
			return new TaskExecutionEnvironmentSelector() {
				@Override
				public EnvironmentSelectionResult isSuitableExecutionEnvironment(SakerEnvironment environment) {
					SystemPropertyEnvironmentProperty envprop = new SystemPropertyEnvironmentProperty(propertyName);
					String propval = environment.getEnvironmentPropertyCurrentValue(envprop);
					if (propval != null) {
						return new EnvironmentSelectionResult(ImmutableUtils.singletonMap(envprop, propval));
					}
					return null;
				}
			};
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeUTF(propertyName);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			propertyName = in.readUTF();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((propertyName == null) ? 0 : propertyName.hashCode());
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
			PropertySelectorInnerTaskFactory other = (PropertySelectorInnerTaskFactory) obj;
			if (propertyName == null) {
				if (other.propertyName != null)
					return false;
			} else if (!propertyName.equals(other.propertyName))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "PropertySelectorInnerTaskFactory[propertyName=" + propertyName + "]";
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		//invalidate the cached environment properties so they are not cached and the test is properly invoked
		{
			String p1 = UUID.randomUUID().toString();
			System.setProperty(p1, "p1val");
			runTask("main", new SelectorInnerTaskStarter(p1));
			assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), "p1val");

			environment.invalidateEnvironmentPropertiesWaitExecutions(Functionals.alwaysPredicate());
			System.setProperty(p1, "p1val2");
			runTask("main", new SelectorInnerTaskStarter(p1));
			assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), "p1val2");

			System.clearProperty(p1);
			environment.invalidateEnvironmentPropertiesWaitExecutions(Functionals.alwaysPredicate());
			assertTaskException(TaskEnvironmentSelectionFailedException.class,
					() -> runTask("main", new SelectorInnerTaskStarter(p1)));
		}
		{
			String pc = UUID.randomUUID().toString();
			System.setProperty(pc, "pcval");
			assertTaskException(IllegalTaskOperationException.class, () -> {
				SelectorInnerTaskStarter task = new SelectorInnerTaskStarter(pc);
				task.setReportConflictingValue("conflicting-value");
				runTask("conlfict", task);
			});
			System.clearProperty(pc);
		}
	}

}
