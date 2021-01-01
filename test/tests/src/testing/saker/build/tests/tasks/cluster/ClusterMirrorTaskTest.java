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
package testing.saker.build.tests.tasks.cluster;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.Path;
import java.util.NavigableSet;

import saker.build.daemon.LocalDaemonEnvironment;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.TestClusterNameExecutionEnvironmentSelector;

@SakerTest
public class ClusterMirrorTaskTest extends ClusterBuildTestCase {
	public static class FileMirroringTaskFactory implements TaskFactory<Void>, Task<Void>, Externalizable {
		private static final long serialVersionUID = 1L;

		private SakerPath path;

		public FileMirroringTaskFactory() {
		}

		public FileMirroringTaskFactory(SakerPath path) {
			this.path = path;
		}

		@Override
		@SuppressWarnings("deprecation")
		public NavigableSet<String> getCapabilities() {
			return ObjectUtils.newTreeSet(CAPABILITY_REMOTE_DISPATCHABLE);
		}

		@Override
		@SuppressWarnings("deprecation")
		public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
			return new TestClusterNameExecutionEnvironmentSelector(ClusterBuildTestCase.DEFAULT_CLUSTER_NAME);
		}

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			assertEquals(
					taskcontext.getExecutionContext().getEnvironment().getUserParameters()
							.get(EnvironmentTestCase.TEST_CLUSTER_NAME_ENV_PARAM),
					ClusterBuildTestCase.DEFAULT_CLUSTER_NAME);

			SakerFile file = taskcontext.getTaskUtilities().resolveAtPath(path);
			taskcontext.reportInputFileDependency(null, path, file == null ? null : file.getContentDescriptor());
			if (file != null) {
				taskcontext.mirror(file);
			}
			//to test transfer of the deltas over RMI
			taskcontext.getNonFileDeltas().forEach(System.out::println);
			taskcontext.getFileDeltas().getFileDeltas().forEach(System.out::println);
			return null;
		}

		@Override
		public Task<? extends Void> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(path);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			path = (SakerPath) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((path == null) ? 0 : path.hashCode());
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
			FileMirroringTaskFactory other = (FileMirroringTaskFactory) obj;
			if (path == null) {
				if (other.path != null)
					return false;
			} else if (!path.equals(other.path))
				return false;
			return true;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		LocalFileProvider localfiles = LocalFileProvider.getInstance();

		SakerPath inputtxtpath = PATH_WORKING_DIRECTORY.resolve("input.txt");
		Path clustermirrordir = LocalDaemonEnvironment.getMirrorDirectoryPathForWorkingDirectory(
				getClusterMirrorDirectory(DEFAULT_CLUSTER_NAME),
				parameters.getPathConfiguration().getWorkingDirectoryPathKey());
		Path inputtxtmirrorpath = clustermirrordir.resolve("wd_").resolve("input.txt");

		try {
			localfiles.clearDirectoryRecursively(clustermirrordir);
		} catch (IOException e) {
		}

		FileMirroringTaskFactory task = new FileMirroringTaskFactory(inputtxtpath);

		runTask("main", task);
		assertEquals(getMetric().getRunTaskIdResults().keySet(), strTaskIdSetOf("main"));

		files.putFile(inputtxtpath, "input");
		runTask("main", task);
		assertEquals(getMetric().getRunTaskIdResults().keySet(), strTaskIdSetOf("main"));
		assertEquals(localfiles.getAllBytes(inputtxtmirrorpath).toString(), "input");

		runTask("main", task);
		assertEquals(getMetric().getRunTaskIdResults().keySet(), strTaskIdSetOf());
		assertEquals(localfiles.getAllBytes(inputtxtmirrorpath).toString(), "input");

		files.putFile(inputtxtpath, "modified");
		runTask("main", task);
		assertEquals(getMetric().getRunTaskIdResults().keySet(), strTaskIdSetOf("main"));
		assertEquals(localfiles.getAllBytes(inputtxtmirrorpath).toString(), "modified");
	}

}
