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
public class OpenFileClusterCacheTest extends ClusterBuildTestCase {

	private static class FileOpenerTaskFactory implements TaskFactory<String>, Task<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		private SakerPath path;

		public FileOpenerTaskFactory() {
		}

		public FileOpenerTaskFactory(SakerPath path) {
			this.path = path;
		}

		@Override
		public NavigableSet<String> getCapabilities() {
			return ObjectUtils.newTreeSet(CAPABILITY_REMOTE_DISPATCHABLE);
		}

		@Override
		public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
			return new TestClusterNameExecutionEnvironmentSelector(ClusterBuildTestCase.DEFAULT_CLUSTER_NAME);
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			assertEquals(
					taskcontext.getExecutionContext().getEnvironment().getUserParameters()
							.get(EnvironmentTestCase.TEST_CLUSTER_NAME_ENV_PARAM),
					ClusterBuildTestCase.DEFAULT_CLUSTER_NAME);

			SakerFile file = taskcontext.getTaskUtilities().resolveAtPath(path);
			String result = taskcontext.getTaskUtilities().getContent(file);
			taskcontext.getTaskUtilities().reportOutputFileDependency(null, file);
			return result;
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
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
			FileOpenerTaskFactory other = (FileOpenerTaskFactory) obj;
			if (path == null) {
				if (other.path != null)
					return false;
			} else if (!path.equals(other.path))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "FileOpenerTaskFactory[" + (path != null ? "path=" + path : "") + "]";
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		LocalFileProvider localfiles = LocalFileProvider.getInstance();

		SakerPath filepath = PATH_WORKING_DIRECTORY.resolve("input.txt");
		FileOpenerTaskFactory task = new FileOpenerTaskFactory(filepath);

		Path clustermirrordir = LocalDaemonEnvironment.getMirrorDirectoryPathForWorkingDirectory(
				getClusterMirrorDirectory(DEFAULT_CLUSTER_NAME),
				parameters.getPathConfiguration().getWorkingDirectoryPathKey());
		Path inputtxtmirrorpath = clustermirrordir.resolve("wd_").resolve("input.txt");
		try {
			localfiles.clearDirectoryRecursively(clustermirrordir);
		} catch (IOException e) {
		}

		files.putFile(filepath, "content");

		runTask("main", task);
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), "content");
		//assert that the file has been cached
		assertEquals(localfiles.getAllBytes(inputtxtmirrorpath).toString(), "content");

		runTask("main", task);
		assertEmpty(getMetric().getRunTaskIdResults());

		files.putFile(filepath, "modified");
		runTask("main", task);
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), "modified");
		assertEquals(localfiles.getAllBytes(inputtxtmirrorpath).toString(), "modified");
	}

}
