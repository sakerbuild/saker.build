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
import java.util.Set;

import saker.build.exception.PropertyComputationFailedException;
import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.task.TaskContext;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;
import testing.saker.build.tests.tasks.factories.StringEnvironmentPropertyDependentTaskFactory;

@SakerTest
public class EnvironmentPropertyComputationAbortedTaskTest extends CollectingMetricEnvironmentTestCase {

	private static final class ExtendedStringEnvironmentPropertyDependentTaskFactory
			extends StringEnvironmentPropertyDependentTaskFactory {
		private static final long serialVersionUID = 1L;

		private String extra;

		/**
		 * For {@link Externalizable}.
		 */
		public ExtendedStringEnvironmentPropertyDependentTaskFactory() {
		}

		public ExtendedStringEnvironmentPropertyDependentTaskFactory(EnvironmentProperty<String> dependency,
				String extra) {
			super(dependency);
			this.extra = extra;
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			System.out.println(
					"EnvironmentPropertyComputationAbortedTaskTest.ExtendedStringEnvironmentPropertyDependentTaskFactory.run()");
			String result = super.run(taskcontext) + extra;
			System.out.println(
					"EnvironmentPropertyComputationAbortedTaskTest.ExtendedStringEnvironmentPropertyDependentTaskFactory.run() -> "
							+ result);
			return result;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			super.writeExternal(out);
			out.writeObject(extra);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			super.readExternal(in);
			extra = (String) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = super.hashCode();
			result = prime * result + ((extra == null) ? 0 : extra.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!super.equals(obj))
				return false;
			if (getClass() != obj.getClass())
				return false;
			ExtendedStringEnvironmentPropertyDependentTaskFactory other = (ExtendedStringEnvironmentPropertyDependentTaskFactory) obj;
			if (extra == null) {
				if (other.extra != null)
					return false;
			} else if (!extra.equals(other.extra))
				return false;
			return true;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder(getClass().getSimpleName());
			builder.append("[");
			if (extra != null) {
				builder.append("extra=");
				builder.append(extra);
				builder.append(", ");
			}
			if (dependency != null) {
				builder.append("dependency=");
				builder.append(dependency);
			}
			builder.append("]");
			return builder.toString();
		}

	}

	private static final class AbortingEnvironmentProperty implements EnvironmentProperty<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		private String value;
		private transient boolean aborting;

		/**
		 * For {@link Externalizable}.
		 */
		public AbortingEnvironmentProperty() {
		}

		public AbortingEnvironmentProperty(String value, boolean aborting) {
			this.value = value;
			this.aborting = aborting;
		}

		@Override
		public String getCurrentValue(SakerEnvironment environment) throws Exception {
			if (aborting) {
				throw new InterruptedException("Interrupted: " + value);
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
			AbortingEnvironmentProperty other = (AbortingEnvironmentProperty) obj;
			if (value == null) {
				if (other.value != null)
					return false;
			} else if (!value.equals(other.value))
				return false;
			return true;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("AbortingEnvironmentProperty[");
			if (value != null) {
				builder.append("value=");
				builder.append(value);
				builder.append(", ");
			}
			builder.append("aborting=");
			builder.append(aborting);
			builder.append("]");
			return builder.toString();
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(value);
			out.writeBoolean(aborting);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			value = (String) in.readObject();
			aborting = in.readBoolean();
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		final String value = "abc";

		System.out.println("EnvironmentPropertyComputationAbortedTaskTest.runTestImpl() check aborting");
		assertTaskException(PropertyComputationFailedException.class,
				() -> runTask("main", new ExtendedStringEnvironmentPropertyDependentTaskFactory(
						new AbortingEnvironmentProperty(value, true), "main")));
		assertNotEmpty(getMetric().getRunTaskIdFactories());

		System.out.println("EnvironmentPropertyComputationAbortedTaskTest.runTestImpl() check non-aborting");
		//no longer aborting, should succeed
		runTask("main", new ExtendedStringEnvironmentPropertyDependentTaskFactory(
				new AbortingEnvironmentProperty(value, false), "main"));
		assertNotEmpty(getMetric().getRunTaskIdFactories());

		//this should succeed, as the previous successful computation is cached by the environment
		//so the property is not recomputed
		System.out.println("EnvironmentPropertyComputationAbortedTaskTest.runTestImpl() check cached aborting");
		runTask("main", new ExtendedStringEnvironmentPropertyDependentTaskFactory(
				new AbortingEnvironmentProperty(value, true), "main-run2"));
		assertNotEmpty(getMetric().getRunTaskIdFactories());
	}

	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		//always use project caching as the queried environment properties are cleared when not using cache 
		//(as ExecutionContext has its own caching for non-project)
		return EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations()).setUseProject(true).build();
	}

}
