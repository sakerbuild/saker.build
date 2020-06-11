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
import java.util.Set;

import saker.build.file.SakerFile;
import saker.build.file.content.PosixFilePermissionsDelegateContentDescriptor;
import saker.build.file.content.SerializableContentDescriptor;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerFileProvider;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class InvalidateDiscoverFileWithPosixPermissionTaskTest extends CollectingMetricEnvironmentTestCase {

	private static class InvalidatorTaskFactory implements TaskFactory<Object>, Task<Object>, Externalizable {
		private static final long serialVersionUID = 1L;

		private int value;
		private SakerPath outputPath = PATH_BUILD_DIRECTORY.resolve("out.txt");
		private PosixFilePermission permission = PosixFilePermission.GROUP_EXECUTE;

		/**
		 * For {@link Externalizable}.
		 */
		public InvalidatorTaskFactory() {
		}

		public InvalidatorTaskFactory(int value) {
			this.value = value;
		}

		public InvalidatorTaskFactory(int value, SakerPath outputPath) {
			this.value = value;
			this.outputPath = outputPath;
		}

		public InvalidatorTaskFactory setPermission(PosixFilePermission permission) {
			this.permission = permission;
			return this;
		}

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			ProviderHolderPathKey pk = taskcontext.getExecutionContext().getPathConfiguration().getPathKey(outputPath);
			SakerFileProvider fp = pk.getFileProvider();
			fp.writeToFile(new UnsyncByteArrayInputStream(Integer.toString(value).getBytes()), pk.getPath());
			Set<PosixFilePermission> permissionsset = setOf(permission);
			fp.setPosixFilePermissions(pk.getPath(), permissionsset);
			taskcontext.invalidate(pk);

			SakerFile f = taskcontext.getTaskUtilities().createProviderPathFileWithPosixFilePermissions(
					outputPath.getFileName() + "_f", pk, new PosixFilePermissionsDelegateContentDescriptor(
							new SerializableContentDescriptor(value), permissionsset),
					permissionsset);
			taskcontext.getTaskUtilities().resolveDirectoryAtPathCreate(outputPath.getParent()).add(f);
			f.synchronize();
			taskcontext.getTaskUtilities().reportOutputFileDependency(null, f);

			return null;
		}

		@Override
		public Task<? extends Object> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeInt(value);
			out.writeObject(outputPath);
			out.writeObject(permission);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			value = in.readInt();
			outputPath = SerialUtils.readExternalObject(in);
			permission = SerialUtils.readExternalObject(in);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((outputPath == null) ? 0 : outputPath.hashCode());
			result = prime * result + ((permission == null) ? 0 : permission.hashCode());
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
			InvalidatorTaskFactory other = (InvalidatorTaskFactory) obj;
			if (outputPath == null) {
				if (other.outputPath != null)
					return false;
			} else if (!outputPath.equals(other.outputPath))
				return false;
			if (permission != other.permission)
				return false;
			if (value != other.value)
				return false;
			return true;
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath outputPath = PATH_BUILD_DIRECTORY.resolve("out.txt");
		SakerPath syncedPath = PATH_BUILD_DIRECTORY.resolve("out.txt_f");

		InvalidatorTaskFactory main;
		main = new InvalidatorTaskFactory().setPermission(PosixFilePermission.OTHERS_EXECUTE);
		runTask("main", main);
		assertEquals(files.getPosixFilePermissions(outputPath), setOf(main.permission));
		assertEquals(files.getPosixFilePermissions(syncedPath), setOf(main.permission));

		runTask("main", main);
		assertEmpty(getMetric().getRunTaskIdFactories());

		files.delete(syncedPath);
		runTask("main", main);
		assertEquals(files.getPosixFilePermissions(outputPath), setOf(main.permission));
		assertEquals(files.getPosixFilePermissions(syncedPath), setOf(main.permission));

		files.setPosixFilePermissions(syncedPath, setOf());
		runTask("main", main);
		assertEquals(files.getPosixFilePermissions(outputPath), setOf(main.permission));
		assertEquals(files.getPosixFilePermissions(syncedPath), setOf(main.permission));

		main = new InvalidatorTaskFactory().setPermission(PosixFilePermission.GROUP_WRITE);
		runTask("main", main);
		assertEquals(files.getPosixFilePermissions(outputPath), setOf(main.permission));
		assertEquals(files.getPosixFilePermissions(syncedPath), setOf(main.permission));

		files.setPosixFilePermissions(outputPath, setOf());
		runTask("main", main);
		assertEmpty(getMetric().getRunTaskIdFactories());
		assertEquals(files.getPosixFilePermissions(outputPath), setOf());
		assertEquals(files.getPosixFilePermissions(syncedPath), setOf(main.permission));

		main = new InvalidatorTaskFactory(main.value + 1).setPermission(PosixFilePermission.GROUP_WRITE);
		runTask("main", main);
		assertEquals(files.getPosixFilePermissions(outputPath), setOf(main.permission));
		assertEquals(files.getPosixFilePermissions(syncedPath), setOf(main.permission));
	}

}
