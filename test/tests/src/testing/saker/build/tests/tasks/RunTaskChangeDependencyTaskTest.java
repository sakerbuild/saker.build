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

import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.StringTaskIdentifier;
import testing.saker.build.tests.tasks.factories.FileStringContentTaskFactory;

@SakerTest
public class RunTaskChangeDependencyTaskTest extends CollectingMetricEnvironmentTestCase {

	public static final SakerPath INPUT_FILE1_PATH = PATH_WORKING_DIRECTORY.resolve("input1.txt");
	public static final SakerPath INPUT_FILE2_PATH = PATH_WORKING_DIRECTORY.resolve("input2.txt");
	public static final SakerPath INPUT_FILE3_PATH = PATH_WORKING_DIRECTORY.resolve("input3.txt");

	private static class RunnerTaskFactory implements TaskFactory<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		private List<TaskFactory<String>> tasks;

		public RunnerTaskFactory() {
		}

		public RunnerTaskFactory(List<TaskFactory<String>> tasks) {
			this.tasks = tasks;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(tasks);
		}

		@SuppressWarnings("unchecked")
		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			tasks = (List<TaskFactory<String>>) in.readObject();
		}

		@Override
		public Task<String> createTask(ExecutionContext executioncontext) {
			return new Task<String>() {
				@Override
				public String run(TaskContext taskcontext) {
					String result = "";
					int i = 1;
					StringTaskIdentifier id = (StringTaskIdentifier) taskcontext.getTaskId();
					for (TaskFactory<String> f : tasks) {
						result += taskcontext.getTaskUtilities().runTaskResult(strTaskId(id.getName() + "_" + i++), f);
					}
					return result;
				}
			};
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((tasks == null) ? 0 : tasks.hashCode());
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
			RunnerTaskFactory other = (RunnerTaskFactory) obj;
			if (tasks == null) {
				if (other.tasks != null)
					return false;
			} else if (!tasks.equals(other.tasks))
				return false;
			return true;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		RunnerTaskFactory task = new RunnerTaskFactory(ImmutableUtils.asUnmodifiableArrayList(
				new FileStringContentTaskFactory(INPUT_FILE1_PATH), new FileStringContentTaskFactory(INPUT_FILE2_PATH),
				new RunnerTaskFactory(
						ImmutableUtils.asUnmodifiableArrayList(new FileStringContentTaskFactory(INPUT_FILE3_PATH)))));

		files.putFile(INPUT_FILE1_PATH, "1");
		files.putFile(INPUT_FILE2_PATH, "2");
		files.putFile(INPUT_FILE3_PATH, "3");

		runTask("main", task);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(),
				strTaskIdSetOf("main", "main_1", "main_2", "main_3", "main_3_1"));
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), "123").contains(strTaskId("main_1"), "1")
				.contains(strTaskId("main_2"), "2").contains(strTaskId("main_3"), "3")
				.contains(strTaskId("main_3_1"), "3");

		runTask("main", task);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf());

		files.putFile(INPUT_FILE1_PATH, "1mod");
		runTask("main", task);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main", "main_1"));
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), "1mod23").contains(strTaskId("main_1"),
				"1mod");

		files.putFile(INPUT_FILE3_PATH, "3mod");
		runTask("main", task);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main", "main_3", "main_3_1"));
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), "1mod23mod")
				.contains(strTaskId("main_3"), "3mod").contains(strTaskId("main_3_1"), "3mod");
	}

}
