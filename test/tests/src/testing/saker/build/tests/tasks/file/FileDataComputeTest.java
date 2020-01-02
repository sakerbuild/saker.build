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
package testing.saker.build.tests.tasks.file;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.FileDataComputer;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;

@SakerTest
public class FileDataComputeTest extends CollectingMetricEnvironmentTestCase {

	private static class DataComputer implements FileDataComputer<String> {
		protected static AtomicInteger computeCount = new AtomicInteger();

		private String appended;

		public DataComputer(String appended) {
			this.appended = appended;
		}

		@Override
		public String compute(SakerFile file) throws IOException {
			computeCount.incrementAndGet();
			return file.getContent() + appended;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((appended == null) ? 0 : appended.hashCode());
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
			DataComputer other = (DataComputer) obj;
			if (appended == null) {
				if (other.appended != null)
					return false;
			} else if (!appended.equals(other.appended))
				return false;
			return true;
		}

	}

	private static class FileDataComputerTaskFactory implements TaskFactory<String>, Task<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		private SakerPath filePath;
		private String appended;

		public FileDataComputerTaskFactory() {
		}

		public FileDataComputerTaskFactory(SakerPath filePath, String appended) {
			this.filePath = filePath;
			this.appended = appended;
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			SakerFile f = taskcontext.getTaskUtilities().resolveAtPath(filePath);
			taskcontext.getTaskUtilities().reportInputFileDependency(null, f);
			return taskcontext.computeFileContentData(f, new DataComputer(appended));
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(filePath);
			out.writeObject(appended);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			filePath = (SakerPath) in.readObject();
			appended = (String) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((appended == null) ? 0 : appended.hashCode());
			result = prime * result + ((filePath == null) ? 0 : filePath.hashCode());
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
			FileDataComputerTaskFactory other = (FileDataComputerTaskFactory) obj;
			if (appended == null) {
				if (other.appended != null)
					return false;
			} else if (!appended.equals(other.appended))
				return false;
			if (filePath == null) {
				if (other.filePath != null)
					return false;
			} else if (!filePath.equals(other.filePath))
				return false;
			return true;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath fpath = PATH_WORKING_DIRECTORY.resolve("file.txt");
		files.putFile(fpath, "content");

		runTask("main", new FileDataComputerTaskFactory(fpath, "append"));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), "contentappend");
		assertEquals(DataComputer.computeCount.get(), 1);

		//run the same task with different task id, but ensure that the content was cached through the project
		runTask("main2", new FileDataComputerTaskFactory(fpath, "append"));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main2")), "contentappend");
		assertEquals(DataComputer.computeCount.get(), 1);

		//change the computed data
		runTask("main", new FileDataComputerTaskFactory(fpath, "append2"));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), "contentappend2");
		assertEquals(DataComputer.computeCount.get(), 2);

		//the previous one still cached
		runTask("main", new FileDataComputerTaskFactory(fpath, "append"));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), "contentappend");
		assertEquals(DataComputer.computeCount.get(), 2);

		//check multiple accesses
		runTask("main",
				new ChildTaskStarterTaskFactory().add(strTaskId("1"), new FileDataComputerTaskFactory(fpath, "append"))
						.add(strTaskId("2"), new FileDataComputerTaskFactory(fpath, "append"))
						.add(strTaskId("3"), new FileDataComputerTaskFactory(fpath, "append")));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("1")), "contentappend");
		assertEquals(DataComputer.computeCount.get(), 2);

		//test invalidation
		project.waitExecutionFinalization();
		project.getCachedFileContentDataComputeHandler().invalidate(fpath);
		runTask("main", new FileDataComputerTaskFactory(fpath, "append"));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), "contentappend");
		assertEquals(DataComputer.computeCount.get(), 3);
	}

	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		//always use project	
		return EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations()).setUseProject(true).build();
	}

}
