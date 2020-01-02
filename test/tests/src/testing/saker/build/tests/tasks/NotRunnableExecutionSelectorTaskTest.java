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

import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.EnvironmentSelectionResult;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskFactory;
import saker.build.task.exception.TaskEnvironmentSelectionFailedException;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.util.property.SystemPropertyEnvironmentProperty;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class NotRunnableExecutionSelectorTaskTest extends CollectingMetricEnvironmentTestCase {
	private static SystemPropertyEnvironmentProperty SELECTOR_SYSTEM_PROPERTY = new SystemPropertyEnvironmentProperty(
			"not.existing.system.property");

	private static class NotRunnableTaskFactory implements TaskFactory<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return new Task<String>() {
				@Override
				public String run(TaskContext taskcontext) throws Exception {
					throw new UnsupportedOperationException();
				}
			};
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}

		@Override
		public int hashCode() {
			return getClass().hashCode();
		}

		@Override
		public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
			return new TaskExecutionEnvironmentSelector() {

				@Override
				public EnvironmentSelectionResult isSuitableExecutionEnvironment(SakerEnvironment environment) {
					String current = environment.getEnvironmentPropertyCurrentValue(SELECTOR_SYSTEM_PROPERTY);
					if (current == null) {
						return null;
					}
					return new EnvironmentSelectionResult(
							ImmutableUtils.singletonMap(SELECTOR_SYSTEM_PROPERTY, current));
				}

				@Override
				public boolean equals(Object obj) {
					return ObjectUtils.isSameClass(this, obj);
				}

				@Override
				public int hashCode() {
					return getClass().hashCode();
				}
			};
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		assertTaskException(TaskEnvironmentSelectionFailedException.class,
				() -> runTask("main", new NotRunnableTaskFactory()));
	}

}
