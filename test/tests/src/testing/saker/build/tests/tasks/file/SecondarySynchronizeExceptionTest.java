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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;

import saker.build.file.ByteArraySakerFile;
import saker.build.file.SakerDirectory;
import saker.build.file.path.SakerPath;
import saker.build.task.TaskContext;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.tasks.SelfStatelessTaskFactory;

@SakerTest
public class SecondarySynchronizeExceptionTest extends EnvironmentTestCase {
	private static final SakerPath FILE_PATH = PATH_BUILD_DIRECTORY.resolve("out.txt");

	private static class MyIOException extends IOException {
		private static final long serialVersionUID = 1L;
	}

	public static class WriterTaskFactory extends SelfStatelessTaskFactory<String> {
		private static final long serialVersionUID = 1L;

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			SakerDirectory targetdir = taskcontext.getTaskUtilities()
					.resolveDirectoryAtPathCreate(FILE_PATH.getParent());
			ByteArraySakerFile file = new ByteArraySakerFile(FILE_PATH.getFileName(),
					"output".getBytes(StandardCharsets.UTF_8)) {
				@Override
				public int getEfficientOpeningMethods() {
					//so synchronization is done
					return OPENING_METHODS_NONE;
				}
			};
			targetdir.add(file);
			try {
				file.writeTo(new OutputStream() {
					@Override
					public void write(int b) throws IOException {
						throw new MyIOException();
					}
				});
				fail();
			} catch (MyIOException e) {
				e.printStackTrace();
			}
			return null;
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		WriterTaskFactory writer = new WriterTaskFactory();

		assertException(NoSuchFileException.class, () -> files.getAllBytes(FILE_PATH));

		runTask("writer", writer);

		assertEquals(files.getAllBytes(FILE_PATH).toString(), "output");
	}

}
