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
import java.util.Objects;

import saker.build.file.ByteArraySakerFile;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class LoopingFileAdditionTest extends CollectingMetricEnvironmentTestCase {
	private static final SakerPath INPUTFILE_PATH = PATH_BUILD_DIRECTORY.resolve("in/in.txt");

	private static class FileLoopingTaskFactory implements TaskFactory<Object>, Task<Object>, Externalizable {
		private static final long serialVersionUID = 1L;

		private String contents;
		private String additionalState;

		public FileLoopingTaskFactory() {
		}

		public FileLoopingTaskFactory(String contents) {
			this.contents = contents;
		}

		public FileLoopingTaskFactory(String contents, String additionalState) {
			this.contents = contents;
			this.additionalState = additionalState;
		}

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			SakerDirectory outdir = taskcontext.getTaskUtilities()
					.resolveDirectoryAtPathCreate(INPUTFILE_PATH.getParent());
			SakerFile addfile = new ByteArraySakerFile(INPUTFILE_PATH.getFileName(), contents.getBytes());
			SakerFile syncfile;
			while (true) {
				SakerFile prevfile = outdir.addIfAbsent(addfile);
				if (prevfile != null) {
					//a file was already present
					//check if it has the same content descriptor, in which case we don't need to overwrite
					if (!Objects.equals(prevfile.getContentDescriptor(), addfile.getContentDescriptor())) {
						//overwrite it
						prevfile.remove();
						continue;
					} else {
						syncfile = prevfile;
					}
				} else {
					syncfile = addfile;
				}
				break;
			}
			syncfile.synchronize();
			return null;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(contents);
			out.writeObject(additionalState);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			contents = (String) in.readObject();
			additionalState = (String) in.readObject();
		}

		@Override
		public Task<? extends Object> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((additionalState == null) ? 0 : additionalState.hashCode());
			result = prime * result + ((contents == null) ? 0 : contents.hashCode());
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
			FileLoopingTaskFactory other = (FileLoopingTaskFactory) obj;
			if (additionalState == null) {
				if (other.additionalState != null)
					return false;
			} else if (!additionalState.equals(other.additionalState))
				return false;
			if (contents == null) {
				if (other.contents != null)
					return false;
			} else if (!contents.equals(other.contents))
				return false;
			return true;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		runTask("main", new FileLoopingTaskFactory("content"));
		assertEquals(files.getAllBytes(INPUTFILE_PATH).toString(), "content");

		runTask("main", new FileLoopingTaskFactory("content", "state"));
		assertNotEmpty(getMetric().getRunTaskIdFactories());
		assertEquals(files.getAllBytes(INPUTFILE_PATH).toString(), "content");

		runTask("main", new FileLoopingTaskFactory("mod", "state"));
		assertNotEmpty(getMetric().getRunTaskIdFactories());
		assertEquals(files.getAllBytes(INPUTFILE_PATH).toString(), "mod");
	}

}
