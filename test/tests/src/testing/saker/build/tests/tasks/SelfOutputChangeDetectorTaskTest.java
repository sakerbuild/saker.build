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
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.TaskWaitingTaskFactory;

@SakerTest
public class SelfOutputChangeDetectorTaskTest extends CollectingMetricEnvironmentTestCase {

	private static class SummingTaskFactory implements TaskFactory<Integer>, Task<Integer>, Externalizable {
		private static final long serialVersionUID = 1L;

		private int l;
		private int r;

		public SummingTaskFactory() {
		}

		public SummingTaskFactory(int l, int r) {
			this.l = l;
			this.r = r;
		}

		@Override
		public Integer run(TaskContext taskcontext) throws Exception {
			int result = l + r;
			taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
			return result;
		}

		@Override
		public Task<? extends Integer> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeInt(l);
			out.writeInt(r);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			l = in.readInt();
			r = in.readInt();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + l;
			result = prime * result + r;
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
			SummingTaskFactory other = (SummingTaskFactory) obj;
			if (l != other.l)
				return false;
			if (r != other.r)
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "SummingTaskFactory[l=" + l + ", r=" + r + "]";
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		ChildTaskStarterTaskFactory main = new ChildTaskStarterTaskFactory()
				.add(strTaskId("waiter"), new TaskWaitingTaskFactory(strTaskId("sum")))
				.add(strTaskId("sum"), new SummingTaskFactory(1, 3));

		runTask("main", main);

		runTask("main", main);
		assertEmpty(getMetric().getRunTaskIdFactories());

		ChildTaskStarterTaskFactory modsamemain = new ChildTaskStarterTaskFactory()
				.add(strTaskId("waiter"), new TaskWaitingTaskFactory(strTaskId("sum")))
				.add(strTaskId("sum"), new SummingTaskFactory(2, 2));

		//waiter should not be rerun
		runTask("main", modsamemain);
		assertEquals(getMetric().getRunTaskIdResults().keySet(), strTaskIdSetOf("main", "sum"));

		ChildTaskStarterTaskFactory modmain = new ChildTaskStarterTaskFactory()
				.add(strTaskId("waiter"), new TaskWaitingTaskFactory(strTaskId("sum")))
				.add(strTaskId("sum"), new SummingTaskFactory(10, 20));
		//all should rerun
		runTask("main", modmain);
		assertEquals(getMetric().getRunTaskIdResults().keySet(), strTaskIdSetOf("main", "waiter", "sum"));
	}

}
