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

import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.task.TaskContext;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;

@SakerTest
public class NoDualPopulationFileTest extends CollectingMetricEnvironmentTestCase {
	private static final SakerPath FILE_PATH = PATH_WORKING_DIRECTORY.resolve("file.txt");

	private static class FilePopulaterTaskFactory extends SelfStatelessTaskFactory<Void> {
		private static final long serialVersionUID = 1L;

		@Override
		public Void run(TaskContext taskcontext) throws Exception {
			SakerDirectory wd = taskcontext.getTaskWorkingDirectory();
			SakerFile f = wd.get("file.txt");
			assertNull(f);
			taskcontext.getExecutionContext().getPathConfiguration().getFileProvider(FILE_PATH)
					.writeToFile(new UnsyncByteArrayInputStream("content".getBytes()), FILE_PATH);
			f = wd.get("file.txt");
			assertNull(f);
			assertFalse(wd.getChildren().keySet().contains("file.txt"));
			return null;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		runTask("main", new FilePopulaterTaskFactory());
	}

}
