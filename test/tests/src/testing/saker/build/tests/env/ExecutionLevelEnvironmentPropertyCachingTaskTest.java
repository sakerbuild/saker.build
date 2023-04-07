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
package testing.saker.build.tests.env;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.exception.PropertyComputationFailedException;
import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.task.TaskContext;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;

@SakerTest
public class ExecutionLevelEnvironmentPropertyCachingTaskTest extends CollectingMetricEnvironmentTestCase {

	private static final class TestEnvironmentProperty implements EnvironmentProperty<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		private String value;
		private transient boolean abort;

		/**
		 * For {@link Externalizable}.
		 */
		public TestEnvironmentProperty() {
		}

		public TestEnvironmentProperty(String value, boolean abort) {
			this.value = value;
			this.abort = abort;
		}

		@Override
		public String getCurrentValue(SakerEnvironment environment) throws Exception {
			if (abort) {
				throw new InterruptedException("aborted: " + value);
			}
			return value;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((value == null) ? 0 : value.hashCode());
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
			TestEnvironmentProperty other = (TestEnvironmentProperty) obj;
			if (value == null) {
				if (other.value != null)
					return false;
			} else if (!value.equals(other.value))
				return false;
			return true;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder(getClass().getSimpleName());
			builder.append("[");
			if (value != null) {
				builder.append("value=");
				builder.append(value);
				builder.append(", ");
			}
			builder.append("]");
			return builder.toString();
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(value);
			out.writeBoolean(abort);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			value = (String) in.readObject();
			abort = in.readBoolean();
		}
	}

	private static final class CachingTestTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			TestEnvironmentProperty abortingprop = new TestEnvironmentProperty("propval", true);
			TestEnvironmentProperty nonabortingprop = new TestEnvironmentProperty("propval", false);

			Throwable cause1 = assertException(PropertyComputationFailedException.class,
					() -> taskcontext.getTaskUtilities().getReportEnvironmentDependency(abortingprop)).getCause();

			//even though the test property won't throw an exception, the getting of the dependency should throw
			//as the previous exception result should be cached, and rethrown again
			Throwable cause2 = assertException(PropertyComputationFailedException.class,
					() -> taskcontext.getTaskUtilities().getReportEnvironmentDependency(nonabortingprop)).getCause();

			//the causes should be cached, and be the same
			assertIdentityEquals(cause1, cause2);

			return taskcontext.getTaskUtilities()
					.getReportEnvironmentDependency(new TestEnvironmentProperty("second", false));
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		runTask("main", new CachingTestTaskFactory());
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), "second");
	}

}
