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

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import saker.build.file.path.SakerPath;
import saker.build.file.path.SimpleProviderHolderPathKey;
import saker.build.runtime.classpath.ClassPathLoadManager;
import saker.build.runtime.classpath.JarFileClassPathLocation;
import saker.build.runtime.classpath.ServiceLoaderClassPathServiceEnumerator;
import saker.build.runtime.environment.RepositoryManager;
import saker.build.runtime.repository.SakerRepository;
import saker.build.runtime.repository.SakerRepositoryFactory;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;
import testing.saker.build.flag.TestFlag;
import testing.saker.build.tests.CollectingTestMetric;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.MemoryFileProvider;

@SakerTest
public class RepoManagerRepoCloseTest extends SakerTestCase {
	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		CollectingTestMetric tm = new CollectingTestMetric();
		TestFlag.set(tm);

		MemoryFileProvider files = new MemoryFileProvider(ImmutableUtils.singletonSet("wd:"),
				UUID.nameUUIDFromBytes(getClass().toString().getBytes(StandardCharsets.UTF_8)));
		SakerPath repopath = SakerPath.valueOf("wd:/" + RepositoryTestUtils.createRepositoryJarName(getClass()));
		RepositoryTestUtils.exportTestRepositoryJar(files, repopath);

		JarFileClassPathLocation repoloc = new JarFileClassPathLocation(
				new SimpleProviderHolderPathKey(files, repopath));
		//TODO leftover classpath from other tests still throws here:
		assertEmpty(tm.getLoadedClassPaths());
		try (ClassPathLoadManager classpathmanager = new ClassPathLoadManager(
				EnvironmentTestCase.getStorageDirectoryPath());
				RepositoryManager repomanager = new RepositoryManager(classpathmanager,
						EnvironmentTestCase.getSakerJarPath())) {
			System.clearProperty("repo_uuid");
			String uuid;
			ServiceLoaderClassPathServiceEnumerator<SakerRepositoryFactory> serviceloader = new ServiceLoaderClassPathServiceEnumerator<>(
					SakerRepositoryFactory.class);
			try (SakerRepository repo = repomanager.loadRepository(repoloc, serviceloader)) {
				repo.executeAction("the.property", "the.value");
				uuid = System.getProperty("repo_uuid");
			}
			assertNonNull(uuid);
			try (SakerRepository repo = repomanager.loadRepository(repoloc, serviceloader)) {
				repo.executeAction("the.property", "the.value");
				String nuuid = System.getProperty("repo_uuid");
				assertNotEquals(uuid, nuuid);
				uuid = nuuid;
			}

			//test reusing the repository
			SakerRepository innerrepo;
			try (SakerRepository outerrepo = repomanager.loadRepository(repoloc, serviceloader)) {
				innerrepo = repomanager.loadRepository(repoloc, serviceloader);
				outerrepo.executeAction("the.property", "the.value");
				String nuuid = System.getProperty("repo_uuid");
				assertNotEquals(uuid, nuuid);
				uuid = nuuid;
			}
			innerrepo.executeAction("the.property", "the.value");
			assertEquals(uuid, System.getProperty("repo_uuid"));
			try (SakerRepository repo = repomanager.loadRepository(repoloc, serviceloader)) {
				repo.executeAction("the.property", "the.value");
				assertEquals(uuid, System.getProperty("repo_uuid"));
			}
			innerrepo.close();
		}
		assertEmpty(tm.getLoadedClassPaths());
	}

}
