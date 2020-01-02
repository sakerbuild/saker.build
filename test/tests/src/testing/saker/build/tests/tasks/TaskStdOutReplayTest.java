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
import java.util.List;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskResultCollection;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class TaskStdOutReplayTest extends CollectingMetricEnvironmentTestCase {
	private static class PrintingTaskFactory implements TaskFactory<Void>, Task<Void>, Externalizable {
		private static final long serialVersionUID = 1L;

		private String displayIdentifier;
		private List<String> messages;

		/**
		 * For {@link Externalizable}.
		 */
		public PrintingTaskFactory() {
		}

		public PrintingTaskFactory(List<String> messages) {
			this.messages = messages;
		}

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			if (displayIdentifier != null) {
				taskcontext.setStandardOutDisplayIdentifier(displayIdentifier);
			}
			for (String m : messages) {
				taskcontext.println(m);
			}
			return null;
		}

		@Override
		public Task<? extends Void> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			SerialUtils.writeExternalCollection(out, messages);
			out.writeObject(displayIdentifier);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			messages = SerialUtils.readExternalImmutableList(in);
			displayIdentifier = (String) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((displayIdentifier == null) ? 0 : displayIdentifier.hashCode());
			result = prime * result + ((messages == null) ? 0 : messages.hashCode());
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
			PrintingTaskFactory other = (PrintingTaskFactory) obj;
			if (displayIdentifier == null) {
				if (other.displayIdentifier != null)
					return false;
			} else if (!displayIdentifier.equals(other.displayIdentifier))
				return false;
			if (messages == null) {
				if (other.messages != null)
					return false;
			} else if (!messages.equals(other.messages))
				return false;
			return true;
		}

	}

	private UnsyncByteArrayOutputStream out;

	@Override
	protected void runTestImpl() throws Throwable {
		PrintingTaskFactory task = new PrintingTaskFactory(ImmutableUtils.asUnmodifiableArrayList("m1", "m2", "m3"));

		runTask("main", task);
		assertNotEmpty(getMetric().getRunTaskIdFactories());
		assertMap(getMetric().getTaskPrintedLines()).contains(strTaskId("main"), listOf("m1", "m2", "m3"));

		runTask("main", task);
		assertEmpty(getMetric().getRunTaskIdFactories());
		assertMap(getMetric().getTaskPrintedLines()).contains(strTaskId("main"), listOf("m1", "m2", "m3"));

		task = new PrintingTaskFactory(ImmutableUtils.asUnmodifiableArrayList("modified"));

		runTask("main", task);
		assertNotEmpty(getMetric().getRunTaskIdFactories());
		assertMap(getMetric().getTaskPrintedLines()).contains(strTaskId("main"), listOf("modified"));

		runTask("main", task);
		assertEmpty(getMetric().getRunTaskIdFactories());
		assertMap(getMetric().getTaskPrintedLines()).contains(strTaskId("main"), listOf("modified"));

		PrintingTaskFactory multiline = new PrintingTaskFactory(ImmutableUtils.asUnmodifiableArrayList("l1\nl2"));
		multiline.displayIdentifier = "id";

		runTask("multiline", multiline);
		assertEquals(out.toString(), "[id]l1\n[id]l2\n");

		runTask("multiline", multiline);
		assertEmpty(getMetric().getRunTaskIdFactories());
		assertEquals(out.toString(), "[id]l1\n[id]l2\n");

		PrintingTaskFactory multilineexcl = new PrintingTaskFactory(ImmutableUtils.asUnmodifiableArrayList("!l1\n!l2"));
		multilineexcl.displayIdentifier = "id";

		runTask("multilineexcl", multilineexcl);
		assertEquals(out.toString(), "[id]!l1\n[id]!l2\n");

		runTask("multilineexcl", multilineexcl);
		assertEmpty(getMetric().getRunTaskIdFactories());
		assertEquals(out.toString(), "[id]!l1\n[id]!l2\n");
	}

	@Override
	protected TaskResultCollection runTask(String taskid, TaskFactory<?> taskfactory) throws Throwable {
		out = new UnsyncByteArrayOutputStream();
		parameters.setStandardOutput(out);
		return super.runTask(taskid, taskfactory);
	}
}
