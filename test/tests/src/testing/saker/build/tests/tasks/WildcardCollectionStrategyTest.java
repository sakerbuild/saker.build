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
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.dependencies.FileCollectionStrategy;
import saker.build.task.utils.dependencies.WildcardFileCollectionStrategy;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;

@SakerTest
public class WildcardCollectionStrategyTest extends CollectingMetricEnvironmentTestCase {

	private static class CollectorTaskFactory
			implements TaskFactory<NavigableSet<SakerPath>>, Task<NavigableSet<SakerPath>>, Externalizable {
		private static final long serialVersionUID = 1L;
		private SakerPath directory;
		private WildcardPath wildcard;

		public CollectorTaskFactory() {
		}

		public CollectorTaskFactory(SakerPath directory, WildcardPath wildcard) {
			this.directory = directory;
			this.wildcard = wildcard;
		}

		@Override
		public Task<? extends NavigableSet<SakerPath>> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public NavigableSet<SakerPath> run(TaskContext taskcontext) throws Exception {
			FileCollectionStrategy dep = WildcardFileCollectionStrategy.create(directory, wildcard);
			System.out.println("WildcardCollectionStrategyTest.CollectorTaskFactory.run() " + dep);
			return new TreeSet<>(taskcontext.getTaskUtilities().collectFiles(dep).navigableKeySet());
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(directory);
			out.writeObject(wildcard);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			directory = (SakerPath) in.readObject();
			wildcard = (WildcardPath) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((directory == null) ? 0 : directory.hashCode());
			result = prime * result + ((wildcard == null) ? 0 : wildcard.hashCode());
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
			CollectorTaskFactory other = (CollectorTaskFactory) obj;
			if (directory == null) {
				if (other.directory != null)
					return false;
			} else if (!directory.equals(other.directory))
				return false;
			if (wildcard == null) {
				if (other.wildcard != null)
					return false;
			} else if (!wildcard.equals(other.wildcard))
				return false;
			return true;
		}

	}

	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		//don't use project as that creates a lock file in the working directory
		return EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations()).setUseProject(false).build();
	}

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath atxt = PATH_WORKING_DIRECTORY.resolve("a.txt");
		SakerPath dirp = PATH_WORKING_DIRECTORY.resolve("dir");
		SakerPath btxt = PATH_WORKING_DIRECTORY.resolve("dir/b.txt");
		files.putFile(atxt, "123");
		files.putFile(btxt, "123");

		runTask("main", new CollectorTaskFactory(null, WildcardPath.valueOf("**")));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")),
				setOf(PATH_WORKING_DIRECTORY, atxt, dirp, btxt));
		runTask("main", new CollectorTaskFactory(null, WildcardPath.valueOf("a.txt")));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), setOf(atxt));
		runTask("main", new CollectorTaskFactory(null, WildcardPath.valueOf("dir/**")));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), setOf(dirp, btxt));
		runTask("main", new CollectorTaskFactory(null, WildcardPath.valueOf("dir/**/*")));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), setOf(btxt));
		runTask("main", new CollectorTaskFactory(null, WildcardPath.valueOf("di*/**")));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), setOf(dirp, btxt));
		runTask("main", new CollectorTaskFactory(null, WildcardPath.valueOf("di*/**/*")));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), setOf(btxt));

		runTask("main", new CollectorTaskFactory(SakerPath.valueOf("dir"), WildcardPath.valueOf("**")));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), setOf(dirp, btxt));
		runTask("main", new CollectorTaskFactory(SakerPath.valueOf("dir"), WildcardPath.valueOf("**/*")));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), setOf(btxt));

		runTask("main", new CollectorTaskFactory(SakerPath.valueOf("dir"), WildcardPath.valueOf("b.txt")));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), setOf(btxt));

		runTask("main", new CollectorTaskFactory(SakerPath.valueOf("dir"), WildcardPath.valueOf("x")));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), setOf());

		//empty wildcard -> working directory
		//. is not specially handled -> empty
		runTask("main", new CollectorTaskFactory(null, WildcardPath.valueOf("")));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), setOf(PATH_WORKING_DIRECTORY));
		runTask("main", new CollectorTaskFactory(null, WildcardPath.valueOf(".")));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), setOf());
		runTask("main", new CollectorTaskFactory(null, WildcardPath.valueOf("./.")));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), setOf());

		//all dirs and files under the working dir
		runTask("main", new CollectorTaskFactory(null, WildcardPath.valueOf("*")));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), setOf(atxt, dirp));
		runTask("main", new CollectorTaskFactory(null, WildcardPath.valueOf("wd:/*")));
		assertEquals(getMetric().getRunTaskIdResults().get(strTaskId("main")), setOf(atxt, dirp));
	}

}
