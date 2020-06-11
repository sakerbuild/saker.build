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

import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class SimplePosixPermissionFileTaskTest extends CollectingMetricEnvironmentTestCase {

	public static class PosixFileSynchronizerTask implements TaskFactory<Object>, Task<Object>, Externalizable {
		private static final long serialVersionUID = 1L;

		private String content;
		private Set<PosixFilePermission> permissions;

		public PosixFileSynchronizerTask() {
		}

		public PosixFileSynchronizerTask(String content, Set<PosixFilePermission> permissions) {
			this.content = content;
			this.permissions = permissions;
		}

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			PosixAttributedStringSakerFile f = new PosixAttributedStringSakerFile("file.txt", permissions, content);
			SakerPathFiles.requireBuildDirectory(taskcontext).add(f);
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
			out.writeObject(content);
			SerialUtils.writeExternalCollection(out, permissions);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			content = SerialUtils.readExternalObject(in);
			permissions = SerialUtils.readExternalImmutableNavigableSet(in);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((content == null) ? 0 : content.hashCode());
			result = prime * result + ((permissions == null) ? 0 : permissions.hashCode());
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
			PosixFileSynchronizerTask other = (PosixFileSynchronizerTask) obj;
			if (content == null) {
				if (other.content != null)
					return false;
			} else if (!content.equals(other.content))
				return false;
			if (permissions == null) {
				if (other.permissions != null)
					return false;
			} else if (!permissions.equals(other.permissions))
				return false;
			return true;
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath txtpath = PATH_BUILD_DIRECTORY.resolve("file.txt");

		PosixFileSynchronizerTask main;
		main = new PosixFileSynchronizerTask("123", setOf(PosixFilePermission.OTHERS_EXECUTE));
		runTask("main", main);
		assertEquals(files.getPosixFilePermissions(txtpath), main.permissions);
		assertEquals(files.getAllBytes(txtpath).toString(), "123");

		runTask("main", main);
		assertEmpty(getMetric().getRunTaskIdFactories());

		//the task should re-run if the posix attributes are changed
		files.setPosixFilePermissions(txtpath, setOf(PosixFilePermission.GROUP_EXECUTE));
		runTask("main", main);
		assertNotEmpty(getMetric().getRunTaskIdFactories());
		assertEquals(files.getPosixFilePermissions(txtpath), main.permissions);

		main = new PosixFileSynchronizerTask("123", setOf(PosixFilePermission.OWNER_EXECUTE));
		runTask("main", main);
		assertNotEmpty(getMetric().getRunTaskIdFactories());
		assertEquals(files.getPosixFilePermissions(txtpath), main.permissions);

		files.putFile(txtpath, "mod");
		runTask("main", main);
		assertEquals(files.getPosixFilePermissions(txtpath), main.permissions);
		assertEquals(files.getAllBytes(txtpath).toString(), "123");

		main = new PosixFileSynchronizerTask("mod", setOf(PosixFilePermission.OWNER_EXECUTE));
		runTask("main", main);
		assertNotEmpty(getMetric().getRunTaskIdFactories());
		assertEquals(files.getPosixFilePermissions(txtpath), main.permissions);
		assertEquals(files.getAllBytes(txtpath).toString(), "mod");
	}

}
