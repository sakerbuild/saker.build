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
import saker.build.task.identifier.TaskIdentifier;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class ComputationTokenWaitTaskTest extends CollectingMetricEnvironmentTestCase {

	public static class CTWaiterTaskFactory implements TaskFactory<Void>, Task<Void>, Externalizable {
		private static final long serialVersionUID = 1L;

		private TaskIdentifier waitTaskId;

		public CTWaiterTaskFactory() {
		}

		public CTWaiterTaskFactory(TaskIdentifier waitTaskId) {
			this.waitTaskId = waitTaskId;
		}

		@Override
		public Task<? extends Void> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(waitTaskId);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			waitTaskId = (TaskIdentifier) in.readObject();
		}

		@Override
		@SuppressWarnings("deprecation")
		public int getRequestedComputationTokenCount() {
			return 1;
		}

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			taskcontext.getTaskResult(waitTaskId);
			return null;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((waitTaskId == null) ? 0 : waitTaskId.hashCode());
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
			CTWaiterTaskFactory other = (CTWaiterTaskFactory) obj;
			if (waitTaskId == null) {
				if (other.waitTaskId != null)
					return false;
			} else if (!waitTaskId.equals(other.waitTaskId))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "CTWaiterTaskFactory[" + (waitTaskId != null ? "waitTaskId=" + waitTaskId : "") + "]";
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		ChildTaskStarterTaskFactory main = new ChildTaskStarterTaskFactory()
				.add(strTaskId("waiter"), new CTWaiterTaskFactory(strTaskId("str")))
				.add(strTaskId("str"), new StringTaskFactory("str"));

		assertTaskException(IllegalTaskOperationException.class, () -> runTask("main", main));
	}

}
