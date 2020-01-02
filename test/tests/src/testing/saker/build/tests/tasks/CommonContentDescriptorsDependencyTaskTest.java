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
import java.nio.file.DirectoryNotEmptyException;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeSet;

import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.CommonTaskContentDescriptors;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.dependencies.FileCollectionStrategy;
import saker.build.task.utils.dependencies.WildcardFileCollectionStrategy;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.factories.FileStringContentTaskFactory;

@SakerTest
public class CommonContentDescriptorsDependencyTaskTest extends CollectingMetricEnvironmentTestCase {

	public static class PresenceDependentTaskFactory implements TaskFactory<Set<SakerPath>>, Externalizable {
		private static final long serialVersionUID = 1L;

		private FileCollectionStrategy additionDependency;

		public PresenceDependentTaskFactory() {
		}

		public PresenceDependentTaskFactory(FileCollectionStrategy additionDependency) {
			this.additionDependency = additionDependency;
		}

		@Override
		public Task<? extends Set<SakerPath>> createTask(ExecutionContext executioncontext) {
			return new Task<Set<SakerPath>>() {
				@Override
				public Set<SakerPath> run(TaskContext taskcontext) throws Exception {
					NavigableMap<SakerPath, SakerFile> files = taskcontext.getTaskUtilities()
							.collectFilesReportAdditionDependency(null, additionDependency);

					for (SakerPath path : files.keySet()) {
						taskcontext.reportInputFileDependency(null, path, CommonTaskContentDescriptors.PRESENT);
						taskcontext.getTaskUtilities().startTaskFuture(strTaskId("sub/" + path.getFileName()),
								new FileStringContentTaskFactory(path));
					}

					return new TreeSet<>(files.navigableKeySet());
				}
			};
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(additionDependency);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			additionDependency = (FileCollectionStrategy) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((additionDependency == null) ? 0 : additionDependency.hashCode());
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
			PresenceDependentTaskFactory other = (PresenceDependentTaskFactory) obj;
			if (additionDependency == null) {
				if (other.additionDependency != null)
					return false;
			} else if (!additionDependency.equals(other.additionDependency))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "PresenceDependentTaskFactory ["
					+ (additionDependency != null ? "additionDependency=" + additionDependency : "") + "]";
		}
	}

	private static final SakerPath PATH_NOTPRESENT = PATH_WORKING_DIRECTORY.resolve("notpresent.txt");
	private static final SakerPath PATH_PRESENT = PATH_WORKING_DIRECTORY.resolve("present.txt");
	private static final SakerPath PATH_NOTFILE = PATH_WORKING_DIRECTORY.resolve("notfile.txt");
	private static final SakerPath PATH_NOTDIRECTORY = PATH_WORKING_DIRECTORY.resolve("notdirectory.txt");
	private static final SakerPath PATH_FILE = PATH_WORKING_DIRECTORY.resolve("file.txt");
	private static final SakerPath PATH_DIRECTORY = PATH_WORKING_DIRECTORY.resolve("directory.txt");
	private static final SakerPath PATH_DONTCARE = PATH_WORKING_DIRECTORY.resolve("dontcare.txt");

	public static class ReportingTaskFactory implements TaskFactory<Void>, Task<Void>, Externalizable {
		private static final long serialVersionUID = 1L;

		private ContentDescriptor descriptor;
		private SakerPath path;

		/**
		 * For {@link Externalizable}.
		 */
		public ReportingTaskFactory() {
		}

		public ReportingTaskFactory(ContentDescriptor descriptor, SakerPath path) {
			this.descriptor = descriptor;
			this.path = path;
		}

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			taskcontext.reportInputFileDependency(null, path, descriptor);
			return null;
		}

		@Override
		public Task<? extends Void> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(descriptor);
			out.writeObject(path);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			descriptor = (ContentDescriptor) in.readObject();
			path = (SakerPath) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((descriptor == null) ? 0 : descriptor.hashCode());
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
			ReportingTaskFactory other = (ReportingTaskFactory) obj;
			if (descriptor == null) {
				if (other.descriptor != null)
					return false;
			} else if (!descriptor.equals(other.descriptor))
				return false;
			if (path == null) {
				if (other.path != null)
					return false;
			} else if (!path.equals(other.path))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "ReportingTaskFactory [descriptor=" + descriptor + ", path=" + path + "]";
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		testPresent();
		testNotPresent();

		testIsNotFile();
		testIsNotDirectory();

		testIsFile();
		testIsDirectory();

		testDontCare();
		testDontCareAddition();
	}

	private void testPresent() throws Throwable, AssertionError, IOException {
		ReportingTaskFactory factory = new ReportingTaskFactory(CommonTaskContentDescriptors.PRESENT, PATH_PRESENT);
		runTask("present", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("present"));

		runTask("present", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("present"));

		files.putFile(factory.path, "content");
		runTask("present", factory);
		assertEmpty(getMetric().getRunTaskIdFactories());

		runTask("present", factory);
		assertEmpty(getMetric().getRunTaskIdFactories());

		files.putFile(factory.path, "modified");
		runTask("present", factory);
		assertEmpty(getMetric().getRunTaskIdFactories());

		files.delete(factory.path);
		runTask("present", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("present"));

		files.createDirectories(factory.path);
		runTask("present", factory);
		assertEmpty(getMetric().getRunTaskIdFactories());
	}

	private void testNotPresent() throws Throwable, AssertionError, IOException, DirectoryNotEmptyException {
		ReportingTaskFactory factory = new ReportingTaskFactory(CommonTaskContentDescriptors.NOT_PRESENT,
				PATH_NOTPRESENT);
		runTask("notpresent", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("notpresent"));

		runTask("notpresent", factory);
		assertEmpty(getMetric().getRunTaskIdFactories());

		files.putFile(factory.path, "content");
		runTask("notpresent", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("notpresent"));

		runTask("notpresent", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("notpresent"));

		files.delete(factory.path);
		runTask("notpresent", factory);
		assertEmpty(getMetric().getRunTaskIdFactories());

		runTask("notpresent", factory);
		assertEmpty(getMetric().getRunTaskIdFactories());

		files.createDirectories(factory.path);
		runTask("notpresent", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("notpresent"));
	}

	private void testIsNotFile() throws Throwable {
		ReportingTaskFactory factory = new ReportingTaskFactory(CommonTaskContentDescriptors.IS_NOT_FILE, PATH_NOTFILE);
		runTask("notfile", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("notfile"));

		runTask("notfile", factory);
		assertEmpty(getMetric().getRunTaskIdFactories());

		files.putFile(factory.path, "content");
		runTask("notfile", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("notfile"));

		runTask("notfile", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("notfile"));

		files.delete(factory.path);
		runTask("notfile", factory);
		assertEmpty(getMetric().getRunTaskIdFactories());

		files.createDirectories(factory.path);
		runTask("notfile", factory);
		assertEmpty(getMetric().getRunTaskIdFactories());
	}

	private void testIsNotDirectory() throws Throwable {
		ReportingTaskFactory factory = new ReportingTaskFactory(CommonTaskContentDescriptors.IS_NOT_DIRECTORY,
				PATH_NOTDIRECTORY);
		runTask("notdir", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("notdir"));

		runTask("notdir", factory);
		assertEmpty(getMetric().getRunTaskIdFactories());

		files.putFile(factory.path, "content");
		runTask("notdir", factory);
		assertEmpty(getMetric().getRunTaskIdFactories());

		files.delete(factory.path);
		runTask("notdir", factory);
		assertEmpty(getMetric().getRunTaskIdFactories());

		files.createDirectories(factory.path);
		runTask("notdir", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("notdir"));

		runTask("notdir", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("notdir"));
	}

	private void testIsDirectory() throws Throwable {
		ReportingTaskFactory factory = new ReportingTaskFactory(CommonTaskContentDescriptors.IS_DIRECTORY,
				PATH_DIRECTORY);
		runTask("directory", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("directory"));

		runTask("directory", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("directory"));

		files.putFile(factory.path, "content");
		runTask("directory", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("directory"));

		files.delete(factory.path);
		runTask("directory", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("directory"));

		files.createDirectories(factory.path);
		runTask("directory", factory);
		assertEmpty(getMetric().getRunTaskIdFactories());

		runTask("directory", factory);
		assertEmpty(getMetric().getRunTaskIdFactories());

		files.delete(factory.path);
		runTask("directory", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("directory"));
	}

	private void testIsFile() throws Throwable {
		ReportingTaskFactory factory = new ReportingTaskFactory(CommonTaskContentDescriptors.IS_FILE, PATH_FILE);
		runTask("file", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("file"));

		runTask("file", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("file"));

		files.putFile(factory.path, "content");
		runTask("file", factory);
		assertEmpty(getMetric().getRunTaskIdFactories());

		runTask("file", factory);
		assertEmpty(getMetric().getRunTaskIdFactories());

		files.putFile(factory.path, "modified");
		runTask("file", factory);
		assertEmpty(getMetric().getRunTaskIdFactories());

		files.delete(factory.path);
		runTask("file", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("file"));

		files.createDirectories(factory.path);
		runTask("file", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("file"));
	}

	private void testDontCare() throws Throwable {
		ReportingTaskFactory factory = new ReportingTaskFactory(CommonTaskContentDescriptors.DONT_CARE, PATH_DONTCARE);
		runTask("dontcare", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("dontcare"));

		runTask("dontcare", factory);
		assertEmpty(getMetric().getRunTaskIdFactories());

		files.putFile(factory.path, "content");
		runTask("dontcare", factory);
		assertEmpty(getMetric().getRunTaskIdFactories());

		runTask("dontcare", factory);
		assertEmpty(getMetric().getRunTaskIdFactories());

		files.putFile(factory.path, "modified");
		runTask("dontcare", factory);
		assertEmpty(getMetric().getRunTaskIdFactories());

		files.delete(factory.path);
		runTask("dontcare", factory);
		assertEmpty(getMetric().getRunTaskIdFactories());

		files.createDirectories(factory.path);
		runTask("dontcare", factory);
		assertEmpty(getMetric().getRunTaskIdFactories());
	}

	public static class DontCareAdditionReportingTaskFactory implements TaskFactory<Void>, Task<Void>, Externalizable {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public DontCareAdditionReportingTaskFactory() {
		}

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			taskcontext.reportInputFileAdditionDependency(null,
					WildcardFileCollectionStrategy.create(WildcardPath.valueOf("*.txt")));
			taskcontext.reportInputFileDependency(null, SakerPath.valueOf("file.txt"),
					CommonTaskContentDescriptors.DONT_CARE);
			return null;
		}

		@Override
		public Task<? extends Void> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

		@Override
		public int hashCode() {
			return getClass().getName().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}

	}

	private void testDontCareAddition() throws Throwable {
		files.clearDirectoryRecursively(PATH_WORKING_DIRECTORY);

		DontCareAdditionReportingTaskFactory factory = new DontCareAdditionReportingTaskFactory();
		runTask("dontcareaddition", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("dontcareaddition"));

		runTask("dontcareaddition", factory);
		assertEmpty(getMetric().getRunTaskIdFactories());

		files.putFile(PATH_WORKING_DIRECTORY.resolve("file.txt"), "content");
		runTask("dontcareaddition", factory);
		assertEmpty(getMetric().getRunTaskIdFactories());
		
		//it is reinvoked, as the dont care dependency wasnt reported for file2.txt
		files.putFile(PATH_WORKING_DIRECTORY.resolve("file2.txt"), "content");
		runTask("dontcareaddition", factory);
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), strTaskIdSetOf("dontcareaddition"));
	}
}
