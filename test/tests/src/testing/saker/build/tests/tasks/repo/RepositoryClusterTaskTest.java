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

import saker.build.file.path.SakerPath;
import saker.build.file.path.SimpleProviderHolderPathKey;
import saker.build.runtime.classpath.JarFileClassPathLocation;
import saker.build.runtime.classpath.ServiceLoaderClassPathServiceEnumerator;
import saker.build.runtime.params.ExecutionRepositoryConfiguration;
import saker.build.runtime.repository.SakerRepositoryFactory;
import testing.saker.SakerTest;
import testing.saker.build.tests.tasks.cluster.ClusterBuildTestCase;
import testing.saker.build.tests.tasks.cluster.TestClusterNameExecutionEnvironmentSelector;
import testing.saker.build.tests.tasks.repo.testrepo.RemoteDispatchableTestTask;
import testing.saker.build.tests.tasks.repo.testrepo.TestRepo;
import testing.saker.build.tests.tasks.repo.testrepo.TestRepoFactory;
import testing.saker.build.tests.tasks.repo.testrepo.TestTask;

@SakerTest
public class RepositoryClusterTaskTest extends ClusterBuildTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath repopath = PATH_WORKING_DIRECTORY.resolve(RepositoryTestUtils.createRepositoryJarName(getClass()));
		RepositoryTestUtils.exportTestRepositoryJarWithClasses(files, repopath, TestRepoFactory.class, TestRepo.class,
				TestTask.class, RemoteDispatchableTestTask.class, TestClusterNameExecutionEnvironmentSelector.class);
		JarFileClassPathLocation repoloc = new JarFileClassPathLocation(
				new SimpleProviderHolderPathKey(files, repopath));
		parameters.setRepositoryConfiguration(ExecutionRepositoryConfiguration.builder()
				.add(repoloc, new ServiceLoaderClassPathServiceEnumerator<>(SakerRepositoryFactory.class)).build());

		CombinedTargetTaskResult res;

		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("result"), "_in_");

		res = runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdFactories());
	}

}
