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
import java.nio.file.NoSuchFileException;

import saker.build.file.ByteArraySakerFile;
import saker.build.file.SakerDirectory;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionParameters;
import saker.build.task.TaskFactory;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;

@SakerTest
public class BuildDirectoryParameterAbandonTaskTest extends CollectingMetricEnvironmentTestCase {

	private static class BuildDirectoryFileOutputTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			SakerDirectory builddir = taskcontext.getTaskBuildDirectory();
			ByteArraySakerFile f = new ByteArraySakerFile("output.txt", "content".getBytes(StandardCharsets.UTF_8));
			builddir.add(f);
			taskcontext.getTaskUtilities().reportOutputFileDependency(null, f);
			f.synchronize();
			return null;
		}
	}

	private static class BuildDirectorySetterTaskFactory implements TaskFactory<Void>, Task<Void>, Externalizable {
		private static final long serialVersionUID = 1L;

		private String subTaskId;
		private TaskFactory<?> subTaskFactory;
		private SakerPath paramBuildDirectory;

		public BuildDirectorySetterTaskFactory() {
		}

		public BuildDirectorySetterTaskFactory(String subTaskId, TaskFactory<?> subTaskFactory,
				SakerPath paramBuildDirectory) {
			this.subTaskId = subTaskId;
			this.subTaskFactory = subTaskFactory;
			this.paramBuildDirectory = paramBuildDirectory;
		}

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			TaskExecutionParameters parameters = new TaskExecutionParameters();

			parameters.setBuildDirectory(paramBuildDirectory);
			taskcontext.startTask(strTaskId(subTaskId), subTaskFactory, parameters);
			return null;
		}

		@Override
		public Task<? extends Void> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeUTF(subTaskId);
			out.writeObject(subTaskFactory);
			out.writeObject(paramBuildDirectory);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			subTaskId = in.readUTF();
			subTaskFactory = (TaskFactory<?>) in.readObject();
			paramBuildDirectory = (SakerPath) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((paramBuildDirectory == null) ? 0 : paramBuildDirectory.hashCode());
			result = prime * result + ((subTaskFactory == null) ? 0 : subTaskFactory.hashCode());
			result = prime * result + ((subTaskId == null) ? 0 : subTaskId.hashCode());
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
			BuildDirectorySetterTaskFactory other = (BuildDirectorySetterTaskFactory) obj;
			if (paramBuildDirectory == null) {
				if (other.paramBuildDirectory != null)
					return false;
			} else if (!paramBuildDirectory.equals(other.paramBuildDirectory))
				return false;
			if (subTaskFactory == null) {
				if (other.subTaskFactory != null)
					return false;
			} else if (!subTaskFactory.equals(other.subTaskFactory))
				return false;
			if (subTaskId == null) {
				if (other.subTaskId != null)
					return false;
			} else if (!subTaskId.equals(other.subTaskId))
				return false;
			return true;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath filepath = PATH_BUILD_DIRECTORY.resolve("subdir/output.txt");
		SakerPath absfilepath = PATH_BUILD_DIRECTORY.resolve("othersubdir/output.txt");
		BuildDirectorySetterTaskFactory relbuilddirmain = new BuildDirectorySetterTaskFactory("sub1",
				new BuildDirectoryFileOutputTaskFactory(), SakerPath.valueOf("subdir"));
		BuildDirectorySetterTaskFactory otherbuilddirmain = new BuildDirectorySetterTaskFactory("sub3",
				new BuildDirectoryFileOutputTaskFactory(), SakerPath.valueOf("othersubdir"));
		BuildDirectorySetterTaskFactory main2 = new BuildDirectorySetterTaskFactory("sub2",
				new StringTaskFactory("second"), null);

		runTask("main", relbuilddirmain);
		assertEquals(files.getAllBytes(filepath).toString(), "content");

		runTask("main", main2);
		assertEquals(getMetric().getAbandonedTasks(), strTaskIdSetOf("sub1"));
		if (project != null) {
			//wait execution finalization for project, so abandoned tasks are handled
			project.waitExecutionFinalization();
		}
		assertException(NoSuchFileException.class, () -> files.getAllBytes(filepath));

		//put it back
		runTask("main", relbuilddirmain);
		assertEquals(files.getAllBytes(filepath).toString(), "content");

		runTask("main", otherbuilddirmain);
		assertEquals(files.getAllBytes(absfilepath).toString(), "content");
		assertEquals(getMetric().getAbandonedTasks(), strTaskIdSetOf("sub1"));
		if (project != null) {
			//wait execution finalization for project, so abandoned tasks are handled
			project.waitExecutionFinalization();
		}
		assertException(NoSuchFileException.class, () -> files.getAllBytes(filepath));

		runTask("main", main2);
		assertEquals(getMetric().getAbandonedTasks(), strTaskIdSetOf("sub3"));
		if (project != null) {
			//wait execution finalization for project, so abandoned tasks are handled
			project.waitExecutionFinalization();
		}
		assertException(NoSuchFileException.class, () -> files.getAllBytes(absfilepath));
	}

}
