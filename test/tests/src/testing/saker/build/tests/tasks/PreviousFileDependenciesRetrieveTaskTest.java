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
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeSet;

import saker.build.file.ByteArraySakerFile;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class PreviousFileDependenciesRetrieveTaskTest extends CollectingMetricEnvironmentTestCase {
	private static class PreviousInputRetrievingTaskFactory
			implements TaskFactory<Set<SakerPath>>, Task<Set<SakerPath>>, Externalizable {
		private static final long serialVersionUID = 1L;

		private Set<SakerPath> files;

		/**
		 * For {@link Externalizable}.
		 */
		public PreviousInputRetrievingTaskFactory() {
		}

		public PreviousInputRetrievingTaskFactory(Set<SakerPath> files) {
			this.files = files;
		}

		@Override
		public Set<SakerPath> run(TaskContext taskcontext) throws Exception {
			NavigableMap<SakerPath, ? extends SakerFile> prevs = taskcontext
					.getPreviousInputDependencies(PreviousInputRetrievingTaskFactory.class);
			for (SakerPath path : files) {
				taskcontext.getTaskUtilities().reportInputFileDependency(PreviousInputRetrievingTaskFactory.class,
						taskcontext.getTaskUtilities().resolveAtPath(path));
			}
			return new TreeSet<>(prevs.navigableKeySet());
		}

		@Override
		public Task<? extends Set<SakerPath>> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(files);
		}

		@SuppressWarnings("unchecked")
		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			files = (Set<SakerPath>) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((files == null) ? 0 : files.hashCode());
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
			PreviousInputRetrievingTaskFactory other = (PreviousInputRetrievingTaskFactory) obj;
			if (files == null) {
				if (other.files != null)
					return false;
			} else if (!files.equals(other.files))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "PreviousInputRetrievingTaskFactory[files=" + files + "]";
		}

	}

	private static class PreviousOutputRetrievingTaskFactory
			implements TaskFactory<Set<SakerPath>>, Task<Set<SakerPath>>, Externalizable {
		private static final long serialVersionUID = 1L;

		private Set<String> files;

		/**
		 * For {@link Externalizable}.
		 */
		public PreviousOutputRetrievingTaskFactory() {
		}

		public PreviousOutputRetrievingTaskFactory(Set<String> files) {
			this.files = files;
		}

		@Override
		public Set<SakerPath> run(TaskContext taskcontext) throws Exception {
			NavigableMap<SakerPath, ? extends SakerFile> prevs = taskcontext
					.getPreviousOutputDependencies(PreviousOutputRetrievingTaskFactory.class);
			for (String filename : files) {
				ByteArraySakerFile outfile = new ByteArraySakerFile(filename,
						ByteArrayRegion.wrap(filename.getBytes()));
				taskcontext.getTaskBuildDirectory().add(outfile);
				outfile.synchronize();
				taskcontext.getTaskUtilities().reportOutputFileDependency(PreviousOutputRetrievingTaskFactory.class,
						outfile);
			}
			return new TreeSet<>(prevs.navigableKeySet());
		}

		@Override
		public Task<? extends Set<SakerPath>> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(files);
		}

		@SuppressWarnings("unchecked")
		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			files = (Set<String>) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((files == null) ? 0 : files.hashCode());
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
			PreviousOutputRetrievingTaskFactory other = (PreviousOutputRetrievingTaskFactory) obj;
			if (files == null) {
				if (other.files != null)
					return false;
			} else if (!files.equals(other.files))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "PreviousOutputRetrievingTaskFactory[files=" + files + "]";
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		for (int i = 0; i < 5; i++) {
			this.files.putFile(PATH_WORKING_DIRECTORY.resolve("in" + i + ".txt"), "in" + i);
		}
		{
			TreeSet<SakerPath> files = new TreeSet<>();

			runTask("inmain", new PreviousInputRetrievingTaskFactory(new TreeSet<>(files)));
			assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("inmain")), setOf());

			runTask("inmain", new PreviousInputRetrievingTaskFactory(new TreeSet<>(files)));
			assertEmpty(getMetric().getRunTaskIdResults());

			files.add(PATH_WORKING_DIRECTORY.resolve("in1.txt"));
			runTask("inmain", new PreviousInputRetrievingTaskFactory(new TreeSet<>(files)));
			assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("inmain")), setOf());

			runTask("inmain", new PreviousInputRetrievingTaskFactory(new TreeSet<>(files)));
			assertEmpty(getMetric().getRunTaskIdResults());

			files.add(PATH_WORKING_DIRECTORY.resolve("in2.txt"));
			runTask("inmain", new PreviousInputRetrievingTaskFactory(new TreeSet<>(files)));
			assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("inmain")),
					setOf(PATH_WORKING_DIRECTORY.resolve("in1.txt")));

			runTask("inmain", new PreviousInputRetrievingTaskFactory(new TreeSet<>(files)));
			assertEmpty(getMetric().getRunTaskIdResults());

			files.remove(PATH_WORKING_DIRECTORY.resolve("in2.txt"));
			runTask("inmain", new PreviousInputRetrievingTaskFactory(new TreeSet<>(files)));
			assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("inmain")),
					setOf(PATH_WORKING_DIRECTORY.resolve("in1.txt"), PATH_WORKING_DIRECTORY.resolve("in2.txt")));
		}
		{
			TreeSet<String> files = new TreeSet<>();
			runTask("outmain", new PreviousOutputRetrievingTaskFactory(new TreeSet<>(files)));
			assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("outmain")), setOf());

			runTask("outmain", new PreviousOutputRetrievingTaskFactory(new TreeSet<>(files)));
			assertEmpty(getMetric().getRunTaskIdResults());

			files.add("out1.txt");
			runTask("outmain", new PreviousOutputRetrievingTaskFactory(new TreeSet<>(files)));
			assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("outmain")), setOf());

			runTask("outmain", new PreviousOutputRetrievingTaskFactory(new TreeSet<>(files)));
			assertEmpty(getMetric().getRunTaskIdResults());

			files.add("out2.txt");
			runTask("outmain", new PreviousOutputRetrievingTaskFactory(new TreeSet<>(files)));
			assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("outmain")),
					setOf(PATH_BUILD_DIRECTORY.resolve("out1.txt")));

			runTask("outmain", new PreviousOutputRetrievingTaskFactory(new TreeSet<>(files)));
			assertEmpty(getMetric().getRunTaskIdResults());

			files.remove("out2.txt");
			runTask("outmain", new PreviousOutputRetrievingTaskFactory(new TreeSet<>(files)));
			assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("outmain")),
					setOf(PATH_BUILD_DIRECTORY.resolve("out1.txt"), PATH_BUILD_DIRECTORY.resolve("out2.txt")));
		}
	}

}
