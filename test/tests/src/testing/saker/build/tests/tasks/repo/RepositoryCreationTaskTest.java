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
package testing.saker.build.tests.tasks.repo;

import java.util.Set;

import saker.build.file.path.SakerPath;
import saker.build.file.path.SimpleProviderHolderPathKey;
import saker.build.runtime.classpath.JarFileClassPathLocation;
import saker.build.runtime.classpath.ServiceLoaderClassPathServiceEnumerator;
import saker.build.runtime.params.ExecutionRepositoryConfiguration;
import saker.build.runtime.repository.SakerRepositoryFactory;
import testing.saker.SakerTest;
import testing.saker.build.flag.TestFlag;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.CollectingTestMetric;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;
import testing.saker.build.tests.tasks.repo.testrepo.AppendedRepositoryClass;
import testing.saker.build.tests.tasks.repo.testrepo.TestRepo;
import testing.saker.build.tests.tasks.repo.testrepo.TestRepoFactory;
import testing.saker.build.tests.tasks.repo.testrepo.TestTask;

@SakerTest
public class RepositoryCreationTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CollectingTestMetric tm = createMetric();
		TestFlag.set(tm);

		SakerPath repopath = PATH_WORKING_DIRECTORY.resolve(RepositoryTestUtils.createRepositoryJarName(getClass()));
		RepositoryTestUtils.exportTestRepositoryJarWithClasses(files, repopath, TestRepoFactory.class, TestRepo.class,
				TestTask.class);
		CombinedTargetTaskResult res;

		JarFileClassPathLocation repoloc = new JarFileClassPathLocation(
				new SimpleProviderHolderPathKey(files, repopath));

		parameters.setRepositoryConfiguration(ExecutionRepositoryConfiguration.builder()
				.add(repoloc, new ServiceLoaderClassPathServiceEnumerator<>(SakerRepositoryFactory.class)).build());

		res = runScriptTask("use");
		assertEquals(res.getTargetTaskResult("res"), "hello");

		res = runScriptTask("use");
		assertEquals(res.getTargetTaskResult("res"), "hello");
		assertEmpty(getMetric().getRunTaskIdResults());

		if (this.project != null) {
			this.project.reset();
		}

		RepositoryTestUtils.exportTestRepositoryJarWithClasses(files, repopath, TestRepoFactory.class, TestRepo.class,
				TestTask.class, AppendedRepositoryClass.class);

		res = runScriptTask("use");
		assertEquals(res.getTargetTaskResult("res"), "hello");
		assertNotEmpty(getMetric().getRunTaskIdResults());

		if (this.project != null) {
			this.project.reset();
		}

		RepositoryTestUtils.exportTestRepositoryJarWithClasses(files, repopath, TestRepoFactory.class, TestRepo.class,
				TestTask.class, AppendedRepositoryClass.class);

		res = runScriptTask("use");
		assertEquals(res.getTargetTaskResult("res"), "hello");
		assertEmpty(getMetric().getRunTaskIdResults());
	}

	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		//so the environment and classpath is closed after this test
		return EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations())
				.setEnvironmentStorageDirectory(null).build();
	}

}
