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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Supplier;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.SakerLog;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskResultCollection;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class StandardIOFormatterTest extends CollectingMetricEnvironmentTestCase {

	private static class PrintingTask implements TaskFactory<Void>, Task<Void>, Externalizable {
		private static final long serialVersionUID = 1L;

		private String printBeforeSet;
		private String printAfterSet;

		public PrintingTask() {
		}

		public PrintingTask(String printBeforeSet, String printAfterSet) {
			this.printBeforeSet = printBeforeSet;
			this.printAfterSet = printAfterSet;
		}

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			if (printBeforeSet != null) {
				System.out.print(printBeforeSet);
			}
			taskcontext.setStandardOutDisplayIdentifier("task");
			if (printAfterSet != null) {
				System.out.print(printAfterSet);
			}
			return null;
		}

		@Override
		public Task<? extends Void> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(printBeforeSet);
			out.writeObject(printAfterSet);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			printBeforeSet = (String) in.readObject();
			printAfterSet = (String) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((printAfterSet == null) ? 0 : printAfterSet.hashCode());
			result = prime * result + ((printBeforeSet == null) ? 0 : printBeforeSet.hashCode());
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
			PrintingTask other = (PrintingTask) obj;
			if (printAfterSet == null) {
				if (other.printAfterSet != null)
					return false;
			} else if (!printAfterSet.equals(other.printAfterSet))
				return false;
			if (printBeforeSet == null) {
				if (other.printBeforeSet != null)
					return false;
			} else if (!printBeforeSet.equals(other.printBeforeSet))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "PrintingTask[" + (printBeforeSet != null ? "printBeforeSet=" + printBeforeSet + ", " : "")
					+ (printAfterSet != null ? "printAfterSet=" + printAfterSet : "") + "]";
		}
	}

	private static class JavacRegressionTask implements TaskFactory<Void>, Task<Void>, Externalizable {
		//this test tests some experienced scenario when the java compiler module printed invalid format:
		//[taskid]No files changed...
		//[taskid][taskid]
		//[taskid]some log
		private static final long serialVersionUID = 1L;

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			//erroneous:
//			[taskid]pre
//			[taskid]test
//			[taskid][taskid]
//			[taskid]post
			taskcontext.setStandardOutDisplayIdentifier("taskid");
			System.out.println("pre");
			SakerLog.log().verbose().println("test");
			SakerLog.log().verbose().println();
			System.out.println("post");
			return null;
		}

		@Override
		public Task<? extends Void> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public int hashCode() {
			return getClass().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}
	}

	private static class SinkWriteTask implements TaskFactory<Void>, Task<Void>, Externalizable {
		private static final long serialVersionUID = 1L;

		private byte[] bytes;

		/**
		 * For {@link Externalizable}.
		 */
		public SinkWriteTask() {
		}

		public SinkWriteTask(byte[] bytes) {
			this.bytes = bytes;
		}

		public SinkWriteTask(String str) {
			this(str.getBytes(StandardCharsets.UTF_8));
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(bytes);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			bytes = (byte[]) in.readObject();
		}

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			taskcontext.setStandardOutDisplayIdentifier("id");
			taskcontext.getStandardOut().write(ByteArrayRegion.wrap(bytes));
			return null;
		}

		@Override
		public Task<? extends Void> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + Arrays.hashCode(bytes);
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
			SinkWriteTask other = (SinkWriteTask) obj;
			if (!Arrays.equals(bytes, other.bytes))
				return false;
			return true;
		}

	}

	private UnsyncByteArrayOutputStream out;

	@Override
	protected void runTestImpl() throws Throwable {
		Supplier<String> msgsupplier = () -> out.toString().replace("\n", "\\n").replace("\r", "\\r");
		String ls = "\r\n";
		runTask("main", new PrintingTask(null, "printed"));
		assertTrue(!out.toString().contains("[task][task]"), msgsupplier);
		assertTrue(out.toString().contains("[task]printed"), msgsupplier);

		runTask("main", new PrintingTask(null, "l1\nl2"));
		assertTrue(!out.toString().contains("[task][task]"), msgsupplier);
		assertTrue(out.toString().contains("[task]l1\n[task]l2"), msgsupplier);

		runTask("main", new PrintingTask(null, "\nl1\nl2"));
		assertTrue(!out.toString().contains("[task][task]"), msgsupplier);
		assertTrue(out.toString().contains("[task]\n[task]l1\n[task]l2"), msgsupplier);

		runTask("main", new PrintingTask(null, "l1\r"));
		assertTrue(!out.toString().contains("[task][task]"), msgsupplier);
		assertTrue(out.toString().contains("[task]l1\r"), msgsupplier);

		runTask("main", new PrintingTask(null, "l1" + ls));
		assertTrue(!out.toString().contains("[task][task]"), msgsupplier);
		assertTrue(out.toString().contains("[task]l1" + ls), msgsupplier);

		runTask("main", new PrintingTask(null, "l1\r" + ls));
		assertTrue(!out.toString().contains("[task][task]"), msgsupplier);
		assertTrue(out.toString().contains("[task]l1\r[task]" + ls), msgsupplier);

		runTask("main", new PrintingTask("bset", null));
		assertTrue(!out.toString().contains("[task][task]"), msgsupplier);
		assertTrue(out.toString().equals("bset\n"), msgsupplier);

		runTask("main", new PrintingTask("bset", "aset"));
		assertTrue(!out.toString().contains("[task][task]"), msgsupplier);
		assertTrue(out.toString().equals("bset\n[task]aset\n"), msgsupplier);

		runTask("main", new PrintingTask(null, "aset"));
		assertTrue(!out.toString().contains("[task][task]"), msgsupplier);
		assertTrue(out.toString().equals("[task]aset\n"), msgsupplier);

		runTask("main", new PrintingTask(null, null));
		assertTrue(!out.toString().contains("[task][task]"), msgsupplier);
		assertTrue(out.toString().equals(""), msgsupplier);

		String sysls = System.lineSeparator();
		//as the pre and post messages are printed using System.out.println, use the system line separator there
		runTask("regr", new JavacRegressionTask());
		assertTrue(
				out.toString().equals("[taskid]pre" + sysls + "[taskid]test\n" + "[taskid]\n" + "[taskid]post" + sysls),
				msgsupplier);

		runTask("sink", new SinkWriteTask("A\r\nB\r\n"));
		assertTrue(out.toString().equals("[id]A\r\n[id]B\r\n"), msgsupplier);
		
		runTask("sink", new SinkWriteTask("A\nB\n"));
		assertTrue(out.toString().equals("[id]A\n[id]B\n"), msgsupplier);
		
		runTask("sink", new SinkWriteTask("A\rB\r"));
		assertTrue(out.toString().equals("[id]A\r[id]B\r"), msgsupplier);
		
		runTask("sink", new SinkWriteTask("A\rB\n"));
		assertTrue(out.toString().equals("[id]A\r[id]B\n"), msgsupplier);
		
		runTask("sink", new SinkWriteTask("A\rB\n\n"));
		assertTrue(out.toString().equals("[id]A\r[id]B\n[id]\n"), msgsupplier);
		
		runTask("sink", new SinkWriteTask("A\r\rB\n\n"));
		assertTrue(out.toString().equals("[id]A\r[id]\r[id]B\n[id]\n"), msgsupplier);
		
		runTask("sink", new SinkWriteTask("A\r\nB\n\n"));
		assertTrue(out.toString().equals("[id]A\r\n[id]B\n[id]\n"), msgsupplier);
	}

	@Override
	protected TaskResultCollection runTask(String taskid, TaskFactory<?> taskfactory) throws Throwable {
		out = new UnsyncByteArrayOutputStream();
		parameters.setStandardOutput(out);
		return super.runTask(taskid, taskfactory);
	}

}
