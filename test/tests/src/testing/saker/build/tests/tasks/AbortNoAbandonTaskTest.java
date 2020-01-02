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
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class AbortNoAbandonTaskTest extends CollectingMetricEnvironmentTestCase {

	private static class AbortingTaskFactory implements TaskFactory<String>, Task<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		private boolean shouldThrow;
		private String strTaskValue;

		public AbortingTaskFactory() {
		}

		public AbortingTaskFactory(boolean shouldThrow) {
			this.shouldThrow = shouldThrow;
		}

		public AbortingTaskFactory(boolean shouldThrow, String strTaskValue) {
			this.shouldThrow = shouldThrow;
			this.strTaskValue = strTaskValue;
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			if (!shouldThrow) {
				if (strTaskValue != null) {
					taskcontext.getTaskUtilities().startTaskFuture(strTaskId(strTaskValue),
							new StringTaskFactory(strTaskValue));
				}
				return strTaskValue;
			}
			taskcontext.abortExecution(new RuntimeException("aborted."));
			return null;
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeBoolean(shouldThrow);
			out.writeObject(strTaskValue);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			shouldThrow = in.readBoolean();
			strTaskValue = (String) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (shouldThrow ? 1231 : 1237);
			result = prime * result + ((strTaskValue == null) ? 0 : strTaskValue.hashCode());
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
			AbortingTaskFactory other = (AbortingTaskFactory) obj;
			if (shouldThrow != other.shouldThrow)
				return false;
			if (strTaskValue == null) {
				if (other.strTaskValue != null)
					return false;
			} else if (!strTaskValue.equals(other.strTaskValue))
				return false;
			return true;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		runTask("main", new AbortingTaskFactory(false, "str"));
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main", "str"));
		assertEmpty(getMetric().getAbandonedTasks());

		runTask("main", new AbortingTaskFactory(false, "str"));
		assertEmpty(getMetric().getRunTaskIdFactories());
		assertEmpty(getMetric().getAbandonedTasks());

		assertException(RuntimeException.class, () -> runTask("main", new AbortingTaskFactory(true)));
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main"));
		assertNotEmpty(getMetric().getRunTaskIdFactories());
		assertEmpty(getMetric().getAbandonedTasks());

		runTask("main", new AbortingTaskFactory(false, null));
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main"));
		assertEquals(getMetric().getAbandonedTasks(), strTaskIdSetOf("str"));
	}
}
