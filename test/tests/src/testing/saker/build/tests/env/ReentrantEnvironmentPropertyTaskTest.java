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
import saker.build.runtime.environment.ForwardingImplSakerEnvironment;
import saker.build.runtime.environment.SakerEnvironment;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.StringEnvironmentPropertyDependentTaskFactory;

@SakerTest
public class ReentrantEnvironmentPropertyTaskTest extends CollectingMetricEnvironmentTestCase {

	private static final class ReentrantEnvironmentProperty implements EnvironmentProperty<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		private String value;

		/**
		 * For {@link Externalizable}.
		 */
		public ReentrantEnvironmentProperty() {
		}

		public ReentrantEnvironmentProperty(String value) {
			this.value = value;
		}

		@Override
		public String getCurrentValue(SakerEnvironment environment) throws Exception {
			return environment.getEnvironmentPropertyCurrentValue(this);
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
			ReentrantEnvironmentProperty other = (ReentrantEnvironmentProperty) obj;
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
			builder.append("ReentrantEnvironmentProperty[");
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
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			value = (String) in.readObject();
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		PropertyComputationFailedException envfailexc = assertException(PropertyComputationFailedException.class,
				() -> environment.getEnvironmentPropertyCurrentValue(new ForwardingImplSakerEnvironment(environment),
						new ReentrantEnvironmentProperty("envtest")));
		assertInstanceOf(envfailexc.getCause(), IllegalThreadStateException.class);

		PropertyComputationFailedException taskfailexc = assertTaskException(PropertyComputationFailedException.class,
				() -> runTask("main", new StringEnvironmentPropertyDependentTaskFactory(
						new ReentrantEnvironmentProperty("tasktest"))));
		assertInstanceOf(taskfailexc.getCause(), IllegalThreadStateException.class);
		//test depends on the exception message
		//to check that the reentrancy is detected by the execution context level caching instead of in the environment
		assertEquals(taskfailexc.getCause().getMessage(), "Reentrant attempt for computing property.");
	}

}
