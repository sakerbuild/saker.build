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
import testing.saker.build.tests.tasks.factories.FileStringContentTaskFactory;

@SakerTest
public class LoopedTaskCreationTaskTest extends CollectingMetricEnvironmentTestCase {
	public static final int MOD = 3;

	public static class LoopingTaskFactory implements TaskFactory<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		private int index;

		public LoopingTaskFactory() {
		}

		public LoopingTaskFactory(int index) {
			this.index = index;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeInt(index);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			index = in.readInt();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + index;
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
			LoopingTaskFactory other = (LoopingTaskFactory) obj;
			if (index != other.index)
				return false;
			return true;
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return new Task<String>() {
				@Override
				public String run(TaskContext taskcontext) throws Exception {
					int validx = (index + MOD - 1) % MOD;
					int nextidx = (index + 1) % MOD;
					taskcontext.getTaskUtilities().startTaskFuture(strTaskId("str_" + index),
							new FileStringContentTaskFactory(PATH_WORKING_DIRECTORY.resolve("file_" + index + ".txt")));
					taskcontext.getTaskUtilities().startTaskFuture(strTaskId("loop_" + nextidx),
							new LoopingTaskFactory(nextidx));
					return taskcontext.getTaskResult(strTaskId("str_" + validx)).toString();
				}
			};
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		for (int i = 0; i < MOD; i++) {
			files.putFile(PATH_WORKING_DIRECTORY.resolve("file_" + i + ".txt"), "value_" + i);
		}
		LoopingTaskFactory inittask = new LoopingTaskFactory(0);

		runTask("loop_0", inittask);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("loop_0"), "value_" + (MOD - 1))
				.contains(strTaskId("loop_1"), "value_0").contains(strTaskId("loop_2"), "value_1")
				.contains(strTaskId("str_0"), "value_0").contains(strTaskId("str_1"), "value_1")
				.contains(strTaskId("str_2"), "value_2").noRemaining();

		runTask("loop_0", inittask);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf());

		files.putFile(PATH_WORKING_DIRECTORY.resolve("file_" + 0 + ".txt"), "value_" + 0 + "_mod");
		runTask("loop_0", inittask);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("loop_1"), "value_" + 0 + "_mod")
				.contains(strTaskId("str_0"), "value_0_mod").noRemaining();
	}

}
