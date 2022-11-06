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
import saker.build.task.exception.TaskExecutionFailedException;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class AbortExceptionTaskTest extends CollectingMetricEnvironmentTestCase {

	private static class AbortingTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			taskcontext.abortExecution(new RuntimeException("aborted."));
			return null;
		}
	}

	private static class NonNullAbortingTaskFactory implements TaskFactory<Integer>, Task<Integer>, Externalizable {
		private static final long serialVersionUID = 1L;

		private int increment;

		public NonNullAbortingTaskFactory() {
		}

		public NonNullAbortingTaskFactory(int increment) {
			this.increment = increment;
		}

		@Override
		public Task<? extends Integer> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public Integer run(TaskContext taskcontext) throws Exception {
			taskcontext.abortExecution(new NumberFormatException("aborted."));
			Number prevout = taskcontext.getPreviousTaskOutput(Number.class);
			if (prevout == null) {
				return increment;
			}
			return prevout.intValue() + increment;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + increment;
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
			NonNullAbortingTaskFactory other = (NonNullAbortingTaskFactory) obj;
			if (increment != other.increment)
				return false;
			return true;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeInt(increment);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			increment = in.readInt();
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("NonNullAbortingTaskFactory[increment=");
			builder.append(increment);
			builder.append("]");
			return builder.toString();
		}
	}

	private static class MultiAbortingTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			taskcontext.abortExecution(new RuntimeException("first"));
			taskcontext.abortExecution(new RuntimeException("second"));
			return null;
		}
	}

	private static class MultiExaminerTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			try {
				taskcontext.getTaskUtilities().runTaskResult(strTaskId("multiabort"), new MultiAbortingTaskFactory());
				fail();
			} catch (TaskExecutionFailedException e) {
				assertEquals(e.getCause().getMessage(), "first");
				assertEquals(e.getSuppressed()[0].getMessage(), "second");
			}
			return "result";
		}
	}

	private static class MultiAbortThrowingTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			taskcontext.abortExecution(new RuntimeException("first"));
			taskcontext.abortExecution(new RuntimeException("second"));
			throw new RuntimeException("thrown");
		}
	}

	private static class MultiThrowingExaminerTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			try {
				taskcontext.getTaskUtilities().runTaskResult(strTaskId("multiabort"),
						new MultiAbortThrowingTaskFactory());
				fail();
			} catch (TaskExecutionFailedException e) {
				assertEquals(e.getCause().getMessage(), "thrown");
				assertEquals(e.getSuppressed()[0].getMessage(), "first");
				assertEquals(e.getSuppressed()[1].getMessage(), "second");
			}
			return "throwresult";
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		AbortingTaskFactory task = new AbortingTaskFactory();
		assertTaskException(RuntimeException.class, () -> runTask("main", task));
		assertNotEmpty(getMetric().getRunTaskIdFactories());

		assertTaskException(RuntimeException.class, () -> runTask("main", task));
		assertEmpty(getMetric().getRunTaskIdFactories());

		assertTaskException(RuntimeException.class, () -> runTask("multiexaminer", new MultiExaminerTaskFactory()));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("multiexaminer")), "result");

		assertTaskException(RuntimeException.class,
				() -> runTask("multithrowexaminer", new MultiThrowingExaminerTaskFactory()));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("multithrowexaminer")), "throwresult");

		assertTaskException(NumberFormatException.class, () -> runTask("nonnull", new NonNullAbortingTaskFactory(1)));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("nonnull")), 1);
		assertTaskException(NumberFormatException.class, () -> runTask("nonnull", new NonNullAbortingTaskFactory(2)));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("nonnull")), 3);
		assertTaskException(NumberFormatException.class, () -> runTask("nonnull", new NonNullAbortingTaskFactory(2)));
		assertEmpty(getMetric().getRunTaskIdFactories());
		assertTaskException(NumberFormatException.class, () -> runTask("nonnull", new NonNullAbortingTaskFactory(3)));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("nonnull")), 6);
	}

}
