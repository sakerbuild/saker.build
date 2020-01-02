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
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Map.Entry;

import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.dependencies.FileCollectionStrategy;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.dependencies.WildcardFileCollectionStrategy;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class FileAdditionDependencyTaskTest extends CollectingMetricEnvironmentTestCase {

	public static class FileListerTaskFactory implements TaskFactory<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

		@Override
		public Task<String> createTask(ExecutionContext executioncontext) {
			return new Task<String>() {
				@Override
				public String run(TaskContext taskcontext) {
					WildcardPath wcpath = WildcardPath.valueOf("**/*.txt");
					FileCollectionStrategy adddep = WildcardFileCollectionStrategy.create(wcpath);
					Map<SakerPath, SakerFile> files = taskcontext.getTaskUtilities()
							.collectFilesReportInputFileAndAdditionDependency(null, adddep);
					String result = "";
					try {
						for (Entry<SakerPath, SakerFile> entry : files.entrySet()) {
							SakerFile file = entry.getValue();
							result += file.getContent();
						}
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
					return result;
				}
			};
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
		FileListerTaskFactory task = new FileListerTaskFactory();
		TaskIdentifier maintaskid = strTaskId("main");

		files.putFile(PATH_WORKING_DIRECTORY.resolve("input.txt"), "1");

		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(maintaskid, "1");

		files.putFile(PATH_WORKING_DIRECTORY.resolve("input2.txt"), "2");
		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(maintaskid, "12");

		runTask("main", task);
		assertTrue(getMetric().getRunTaskIdResults().isEmpty());

		files.putFile(PATH_WORKING_DIRECTORY.resolve("input3.txt"), "3");
		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(maintaskid, "123");

		files.putFile(PATH_WORKING_DIRECTORY.resolve("inputdir/input.txt"), "4");
		runTask("main", task);
		assertMap(getMetric().getRunTaskIdResults()).contains(maintaskid, "1234");
	}

}
