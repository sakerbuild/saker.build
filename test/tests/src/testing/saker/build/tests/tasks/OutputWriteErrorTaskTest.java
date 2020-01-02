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
import saker.build.task.delta.impl.OutputLoadFailedDeltaImpl;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class OutputWriteErrorTaskTest extends CollectingMetricEnvironmentTestCase {
	public static class ErrorWriter implements Externalizable {
		private static final long serialVersionUID = 1L;

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			throw new IOException("Intentional failure.");
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}
	}

	public static class LoaderTaskFactory implements TaskFactory<ErrorWriter>, Externalizable {
		private static final long serialVersionUID = 1L;

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

		@Override
		public Task<ErrorWriter> createTask(ExecutionContext executioncontext) {
			return new Task<ErrorWriter>() {
				@Override
				public ErrorWriter run(TaskContext taskcontext) {
					return new ErrorWriter();
				}
			};
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

	@Override
	protected void runTestImpl() throws Throwable {
		LoaderTaskFactory task = new LoaderTaskFactory();

		runTask("main", task);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main"));

		runTask("main", task);
		if (project == null) {
			assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main"));
			assertMap(getMetric().getRunTaskIdDeltas())
					.contains(strTaskId("main"), setOf(OutputLoadFailedDeltaImpl.INSTANCE)).noRemaining();
		} else {
			assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf());
			assertMap(getMetric().getRunTaskIdDeltas()).noRemaining();
		}
	}

}
