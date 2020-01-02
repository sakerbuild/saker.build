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

import saker.build.task.TaskContext;
import saker.build.task.TaskDependencyFuture;
import saker.build.task.TaskResultCollection;
import saker.build.task.dependencies.TaskOutputChangeDetector;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class MultiTaskOutputChangeDetectorTaskTest extends CollectingMetricEnvironmentTestCase {

	private static class StringEndingsTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public StringEndingsTaskFactory() {
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			TaskDependencyFuture<?> future = taskcontext.getTaskDependencyFuture(strTaskId("str"));
			String str = future.get().toString();
			char first = str.charAt(0);
			char last = str.charAt(str.length() - 1);
			future.setTaskOutputChangeDetector(new CharAtTaskOutputChangeDetector(0, first));
			future.setTaskOutputChangeDetector(new CharAtTaskOutputChangeDetector(str.length() - 1, last));
			return Character.toString(first) + last;
		}
	}

	public static class CharAtTaskOutputChangeDetector implements TaskOutputChangeDetector, Externalizable {
		private static final long serialVersionUID = 1L;

		private int idx;
		private char expected;

		/**
		 * For {@link Externalizable}.
		 */
		public CharAtTaskOutputChangeDetector() {
		}

		public CharAtTaskOutputChangeDetector(int idx, char expected) {
			this.idx = idx;
			this.expected = expected;
		}

		@Override
		public boolean isChanged(Object taskoutput) {
			String str = taskoutput.toString();
			return str.charAt(idx) != expected;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeInt(idx);
			out.writeChar(expected);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			idx = in.readInt();
			expected = in.readChar();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + expected;
			result = prime * result + idx;
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
			CharAtTaskOutputChangeDetector other = (CharAtTaskOutputChangeDetector) obj;
			if (expected != other.expected)
				return false;
			if (idx != other.idx)
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "CharAtTaskOutputChangeDetector[idx=" + idx + ", expected=" + expected + "]";
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		TaskResultCollection res;
		StringEndingsTaskFactory endings = new StringEndingsTaskFactory();
		ChildTaskStarterTaskFactory main1 = new ChildTaskStarterTaskFactory().add(strTaskId("waiter"), endings)
				.add(strTaskId("str"), new StringTaskFactory("abc"));

		runTask("main", main1);
		assertMap(getMetric().getRunTaskIdResults()).containsKey(strTaskId("main")).contains(strTaskId("waiter"), "ac")
				.contains(strTaskId("str"), "abc").noRemaining();
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main", "waiter", "str"));

		runTask("main", main1);
		assertEmpty(getMetric().getRunTaskIdFactories());

		ChildTaskStarterTaskFactory main2 = new ChildTaskStarterTaskFactory().add(strTaskId("waiter"), endings)
				.add(strTaskId("str"), new StringTaskFactory("axc"));
		res = runTask("main", main2);
		assertEquals(res.getTaskResult(strTaskId("waiter")), "ac");
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("str"), "axc").containsKey(strTaskId("main"))
				.noRemaining();
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main", "str"));

		runTask("main", main2);
		assertEmpty(getMetric().getRunTaskIdFactories());

		ChildTaskStarterTaskFactory main3 = new ChildTaskStarterTaskFactory().add(strTaskId("waiter"), endings)
				.add(strTaskId("str"), new StringTaskFactory("xxc"));
		res = runTask("main", main3);
		assertEquals(res.getTaskResult(strTaskId("waiter")), "xc");
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("str"), "xxc")
				.contains(strTaskId("waiter"), "xc").containsKey(strTaskId("main")).noRemaining();
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main", "waiter", "str"));

		runTask("main", main3);
		assertEmpty(getMetric().getRunTaskIdFactories());

		ChildTaskStarterTaskFactory main4 = new ChildTaskStarterTaskFactory().add(strTaskId("waiter"), endings)
				.add(strTaskId("str"), new StringTaskFactory("xxx"));
		res = runTask("main", main4);
		assertEquals(res.getTaskResult(strTaskId("waiter")), "xx");
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("str"), "xxx")
				.contains(strTaskId("waiter"), "xx").containsKey(strTaskId("main")).noRemaining();
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main", "waiter", "str"));

		runTask("main", main4);
		assertEmpty(getMetric().getRunTaskIdFactories());
	}

}
