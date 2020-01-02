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

import saker.build.file.ByteArraySakerFile;
import saker.build.file.SakerDirectory;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class InvalidateContentsTest extends CollectingMetricEnvironmentTestCase {
	//TODO create a cluster test for this too

	public static class InvalidatorTaskFactory implements TaskFactory<String>, Task<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			SakerDirectory bd = taskcontext.getTaskBuildDirectory();
			ExecutionPathConfiguration pathconfig = taskcontext.getExecutionContext().getPathConfiguration();
			SakerPath bdpath = bd.getSakerPath();

			ByteArraySakerFile outtxtfile = new ByteArraySakerFile("out.txt", "content".getBytes());
			bd.add(outtxtfile);

			SakerPath txtpath = outtxtfile.getSakerPath();

			SakerFileProvider bdfp = pathconfig.getFileProvider(bdpath);

			assertException(IOException.class, () -> bdfp.getAllBytes(txtpath));

			outtxtfile.synchronize();
			assertEquals(bdfp.getAllBytes(txtpath).toString(), "content");

			bdfp.setFileBytes(txtpath, ByteArrayRegion.wrap("fpmod".getBytes()));
			assertEquals(bdfp.getAllBytes(txtpath).toString(), "fpmod");

			outtxtfile.synchronize();
			assertEquals(bdfp.getAllBytes(txtpath).toString(), "fpmod");

			taskcontext.invalidate(SakerPathFiles.getPathKey(bdfp, txtpath));
			assertEquals(bdfp.getAllBytes(txtpath).toString(), "fpmod");

			outtxtfile.synchronize();
			assertEquals(bdfp.getAllBytes(txtpath).toString(), "content");

			return "hello";
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public int hashCode() {
			return getClass().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		InvalidatorTaskFactory main = new InvalidatorTaskFactory();

		runTask("main", main);
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), "hello");
	}

}
