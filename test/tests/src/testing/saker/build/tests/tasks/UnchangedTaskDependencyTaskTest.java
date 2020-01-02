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

import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.FileStringContentTaskFactory;
import testing.saker.build.tests.tasks.factories.StringTaskConcatTaskFactory;

/**
 * Similar test to {@link IntermediateTaskDependencyTaskTest}, but it is to verify that given a file change, when a task
 * output is not changed, the dependent tasks will not be rerun.
 */
@SakerTest
public class UnchangedTaskDependencyTaskTest extends CollectingMetricEnvironmentTestCase {
	private static final SakerPath INPUT_FILE = PATH_WORKING_DIRECTORY.resolve("input.txt");

	public static class ComposerTaskFactory implements TaskFactory<Void>, Externalizable {
		private static final long serialVersionUID = 1L;

		public ComposerTaskFactory() {
		}

		@Override
		public Task<Void> createTask(ExecutionContext excontext) {
			return new Task<Void>() {
				@Override
				public Void run(TaskContext taskcontext) {
					FileStringContentTaskFactory filetaskfactory = new FileStringContentTaskFactory(INPUT_FILE);
					filetaskfactory.setTrackUnchanged(true);
					TaskIdentifier filetaskid = strTaskId("file");
					taskcontext.getTaskUtilities().startTaskFuture(filetaskid, filetaskfactory);
					taskcontext.getTaskUtilities().startTaskFuture(strTaskId("concat"),
							new StringTaskConcatTaskFactory("concatstr", filetaskid));
					return null;
				}
			};
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
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
		files.putFile(INPUT_FILE, "content");

		ComposerTaskFactory factory = new ComposerTaskFactory();

		runTask("main", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("main", "file", "concat"));
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("file"), "content").contains(strTaskId("concat"),
				"concatstrcontent");

		runTask("main", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf());

		files.touch(INPUT_FILE);
		runTask("main", factory);
		assertMap(getMetric().getRunTaskIdFactories()).containsKey(strTaskId("file"));
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("file"), "content");

		runTask("main", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf());

		files.putFile(INPUT_FILE, "modcontent");
		runTask("main", factory);
		assertMap(getMetric().getRunTaskIdFactories()).containsKey(strTaskId("file"));
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("file"), "modcontent");

		runTask("main", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf());
	}

}
