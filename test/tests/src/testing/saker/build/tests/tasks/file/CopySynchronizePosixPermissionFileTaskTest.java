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
import java.nio.file.attribute.PosixFilePermission;

import saker.build.file.DelegateSakerFile;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionUtilities;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.MemoryFileProvider;
import testing.saker.build.tests.tasks.factories.SequentialChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.file.SimplePosixPermissionFileTaskTest.PosixFileSynchronizerTask;

@SakerTest
public class CopySynchronizePosixPermissionFileTaskTest extends CollectingMetricEnvironmentTestCase {

	public static class CopyPosixSynchronizerTask implements TaskFactory<Object>, Task<Object>, Externalizable {
		private static final long serialVersionUID = 1L;

		//only for equality checks
		private int value;
		private SakerPath inputPath = PATH_BUILD_DIRECTORY.resolve("file.txt");
		private SakerPath outputPath = PATH_BUILD_DIRECTORY.resolve("out.txt");

		public CopyPosixSynchronizerTask() {
		}

		public CopyPosixSynchronizerTask(int value) {
			this.value = value;
		}

		public CopyPosixSynchronizerTask setInputPath(SakerPath inputPath) {
			this.inputPath = inputPath;
			return this;
		}

		public CopyPosixSynchronizerTask setOutputPath(SakerPath outputPath) {
			this.outputPath = outputPath;
			return this;
		}

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			SakerFile f = taskcontext.getTaskUtilities().resolveAtPath(inputPath);
			ExecutionPathConfiguration pathconfig = taskcontext.getExecutionContext().getPathConfiguration();
			f.synchronize(pathconfig.getPathKey(outputPath));
			DelegateSakerFile delegatef = new DelegateSakerFile(outputPath.getFileName() + "_delegate", f);
			taskcontext.getTaskUtilities().resolveDirectoryAtPathCreate(outputPath.getParent()).add(delegatef);

			delegatef.synchronize();
			taskcontext.getTaskUtilities().synchronize(pathconfig.getPathKey(inputPath),
					pathconfig.getPathKey(outputPath.resolveSibling(outputPath.getFileName() + "_s_with_p")),
					TaskExecutionUtilities.SYNCHRONIZE_FLAG_COPY_ASSOCIATED_POSIX_FILE_PERMISSIONS);
			taskcontext.getTaskUtilities().synchronize(pathconfig.getPathKey(inputPath),
					pathconfig.getPathKey(outputPath.resolveSibling(outputPath.getFileName() + "_s_without_p")),
					TaskExecutionUtilities.SYNCHRONIZE_FLAG_NONE);
			taskcontext.getTaskUtilities().reportInputFileDependency(null, f);
			taskcontext.getTaskUtilities().reportOutputFileDependency(null, delegatef);
			return null;
		}

		@Override
		public Task<? extends Object> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeInt(value);
			out.writeObject(inputPath);
			out.writeObject(outputPath);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			value = in.readInt();
			inputPath = SerialUtils.readExternalObject(in);
			outputPath = SerialUtils.readExternalObject(in);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((inputPath == null) ? 0 : inputPath.hashCode());
			result = prime * result + ((outputPath == null) ? 0 : outputPath.hashCode());
			result = prime * result + value;
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
			CopyPosixSynchronizerTask other = (CopyPosixSynchronizerTask) obj;
			if (inputPath == null) {
				if (other.inputPath != null)
					return false;
			} else if (!inputPath.equals(other.inputPath))
				return false;
			if (outputPath == null) {
				if (other.outputPath != null)
					return false;
			} else if (!outputPath.equals(other.outputPath))
				return false;
			if (value != other.value)
				return false;
			return true;
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath outpath = PATH_BUILD_DIRECTORY.resolve("out.txt");
		SakerPath filepath = PATH_BUILD_DIRECTORY.resolve("file.txt");
		SakerPath outpath_s_with_p = outpath.resolveSibling(outpath.getFileName() + "_s_with_p");
		SakerPath outpath_s_without_p = outpath.resolveSibling(outpath.getFileName() + "_s_without_p");
		SakerPath outpath_delegate = outpath.resolveSibling(outpath.getFileName() + "_delegate");

		SequentialChildTaskStarterTaskFactory main = new SequentialChildTaskStarterTaskFactory()
				.add(strTaskId("1"), new PosixFileSynchronizerTask("123", setOf(PosixFilePermission.GROUP_EXECUTE)))
				.add(strTaskId("2"), new CopyPosixSynchronizerTask(1));
		runTask("main", main);
		assertEquals(files.getPosixFilePermissions(filepath), setOf(PosixFilePermission.GROUP_EXECUTE));
		assertEquals(files.getPosixFilePermissions(outpath), setOf(PosixFilePermission.GROUP_EXECUTE));
		assertEquals(files.getPosixFilePermissions(outpath_s_with_p), setOf(PosixFilePermission.GROUP_EXECUTE));
		assertEquals(files.getPosixFilePermissions(outpath_s_without_p),
				MemoryFileProvider.FILE_DEFAULT_POSIX_PERMISSIONS);
		assertEquals(files.getPosixFilePermissions(outpath_delegate), setOf(PosixFilePermission.GROUP_EXECUTE));

		runTask("main", main);
		assertEmpty(getMetric().getRunTaskIdFactories());

		files.setPosixFilePermissions(filepath, setOf(PosixFilePermission.OTHERS_READ));
		runTask("main", main);
		assertEquals(files.getPosixFilePermissions(filepath), setOf(PosixFilePermission.GROUP_EXECUTE));
		assertEquals(files.getPosixFilePermissions(outpath), setOf(PosixFilePermission.GROUP_EXECUTE));
		assertEquals(files.getPosixFilePermissions(outpath_s_with_p), setOf(PosixFilePermission.GROUP_EXECUTE));
		assertEquals(files.getPosixFilePermissions(outpath_s_without_p),
				MemoryFileProvider.FILE_DEFAULT_POSIX_PERMISSIONS);
		assertEquals(files.getPosixFilePermissions(outpath_delegate), setOf(PosixFilePermission.GROUP_EXECUTE));

		main = new SequentialChildTaskStarterTaskFactory()
				.add(strTaskId("1"), new PosixFileSynchronizerTask("123", setOf(PosixFilePermission.OWNER_WRITE)))
				.add(strTaskId("2"), new CopyPosixSynchronizerTask(1));
		runTask("main", main);
		assertEquals(files.getPosixFilePermissions(filepath), setOf(PosixFilePermission.OWNER_WRITE));
		assertEquals(files.getPosixFilePermissions(outpath), setOf(PosixFilePermission.OWNER_WRITE));
		assertEquals(files.getPosixFilePermissions(outpath_s_with_p), setOf(PosixFilePermission.OWNER_WRITE));
		assertEquals(files.getPosixFilePermissions(outpath_s_without_p),
				MemoryFileProvider.FILE_DEFAULT_POSIX_PERMISSIONS);
		assertEquals(files.getPosixFilePermissions(outpath_delegate), setOf(PosixFilePermission.OWNER_WRITE));

		runTask("main", main);
		assertEmpty(getMetric().getRunTaskIdFactories());

		//only the copy task should re-run.
		//check that the posix permissions are retrieved from the content database and reapplied correctly
		//without the original task re-running
		main = new SequentialChildTaskStarterTaskFactory()
				.add(strTaskId("1"), new PosixFileSynchronizerTask("123", setOf(PosixFilePermission.OWNER_WRITE)))
				.add(strTaskId("2"), new CopyPosixSynchronizerTask(2));
		files.setPosixFilePermissions(outpath, setOf());
		runTask("main", main);
		assertEquals(files.getPosixFilePermissions(filepath), setOf(PosixFilePermission.OWNER_WRITE));
		assertEquals(files.getPosixFilePermissions(outpath), setOf(PosixFilePermission.OWNER_WRITE));
		assertEquals(files.getPosixFilePermissions(outpath_s_with_p), setOf(PosixFilePermission.OWNER_WRITE));
		assertEquals(files.getPosixFilePermissions(outpath_s_without_p),
				MemoryFileProvider.FILE_DEFAULT_POSIX_PERMISSIONS);
		assertEquals(files.getPosixFilePermissions(outpath_delegate), setOf(PosixFilePermission.OWNER_WRITE));

		files.setPosixFilePermissions(outpath_delegate, setOf());
		runTask("main", main);
		assertEquals(files.getPosixFilePermissions(outpath_delegate), setOf(PosixFilePermission.OWNER_WRITE));
	}

}
