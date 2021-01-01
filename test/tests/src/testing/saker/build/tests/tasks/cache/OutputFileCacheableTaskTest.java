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
package testing.saker.build.tests.tasks.cache;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableSet;

import saker.build.file.SakerDirectory;
import saker.build.file.content.CommonContentDescriptorSupplier;
import saker.build.file.content.SerializableContentDescriptor;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.params.DatabaseConfiguration;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.tasks.file.StringSakerFile;

@SakerTest
public class OutputFileCacheableTaskTest extends CacheableTaskTestCase {
	private static final String OUTPUT_FILE_NAME = "out.txt";
	private static final SakerPath OUTPUT_PATH = PATH_BUILD_DIRECTORY.resolve(OUTPUT_FILE_NAME);

	private static class OutputFileReportingTaskFactory implements TaskFactory<String>, Task<String>, Externalizable {

		private static final long serialVersionUID = 1L;

		private String content;

		public OutputFileReportingTaskFactory() {
		}

		public OutputFileReportingTaskFactory(String content) {
			this.content = content;
		}

		@Override
		@SuppressWarnings("deprecation")
		public NavigableSet<String> getCapabilities() {
			return ImmutableUtils.singletonNavigableSet(TaskFactory.CAPABILITY_CACHEABLE);
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			SakerDirectory bdir = taskcontext.getTaskBuildDirectory();
			StringSakerFile outfile = new StringSakerFile(OUTPUT_FILE_NAME, content,
					new SerializableContentDescriptor(content));
			bdir.add(outfile);
			outfile.synchronize();
			taskcontext.getTaskUtilities().reportOutputFileDependency(null, outfile);
			return content;
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeUTF(content);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			content = in.readUTF();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((content == null) ? 0 : content.hashCode());
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
			OutputFileReportingTaskFactory other = (OutputFileReportingTaskFactory) obj;
			if (content == null) {
				if (other.content != null)
					return false;
			} else if (!content.equals(other.content))
				return false;
			return true;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		//hash the files instead of using file attributes
		parameters.setDatabaseConfiguration(
				DatabaseConfiguration.builder(CommonContentDescriptorSupplier.HASH_MD5).build());

		OutputFileReportingTaskFactory main = new OutputFileReportingTaskFactory("content");
		OutputFileReportingTaskFactory modmain = new OutputFileReportingTaskFactory("modified");

		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), main.content).noRemaining();
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf("main"));
		assertEquals(files.getAllBytes(OUTPUT_PATH).toString(), main.content);

		cleanProject();
		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), main.content).noRemaining();
		assertEquals(getMetric().getCacheRetrievedTasks(), strTaskIdSetOf("main"));
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf());
		assertEquals(files.getAllBytes(OUTPUT_PATH).toString(), main.content);

		cleanProject();
		runTask("main", modmain);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), modmain.content).noRemaining();
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf("main"));
		assertEquals(files.getAllBytes(OUTPUT_PATH).toString(), modmain.content);

		cleanProject();
		runTask("main", modmain);
		assertEquals(getMetric().getCacheRetrievedTasks(), strTaskIdSetOf("main"));
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf());
		assertEquals(files.getAllBytes(OUTPUT_PATH).toString(), modmain.content);

		cleanProject();
		runTask("main", main);
		assertMap(getMetric().getRunTaskIdResults()).contains(strTaskId("main"), main.content).noRemaining();
		assertEquals(getMetric().getCacheRetrievedTasks(), strTaskIdSetOf("main"));
		waitExecutionFinalization();
		assertEquals(getMetric().getCachePublishedTasks(), strTaskIdSetOf());
		assertEquals(files.getAllBytes(OUTPUT_PATH).toString(), main.content);
	}

}
