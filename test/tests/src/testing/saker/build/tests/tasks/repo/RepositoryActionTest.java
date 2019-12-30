package testing.saker.build.tests.tasks.repo;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
public class RepositoryActionTest extends SakerTestCase {
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

		Path loaddir;
		ServiceLoaderClassPathServiceEnumerator<SakerRepositoryFactory> serviceloader = new ServiceLoaderClassPathServiceEnumerator<>(
				SakerRepositoryFactory.class);
		try (ClassPathLoadManager classpathmanager = new ClassPathLoadManager(
				EnvironmentTestCase.getStorageDirectoryPath());
				RepositoryManager repomanager = new RepositoryManager(classpathmanager,
						EnvironmentTestCase.getSakerJarPath())) {
			loaddir = classpathmanager.getClassPathLoadDirectoryPath(repoloc);

			try (SakerRepository repo = repomanager.loadRepository(repoloc, serviceloader)) {
				repo.executeAction("the.property", "the.value");
				assertEquals(System.getProperty("the.property"), "the.value");
			} catch (Exception e) {
				e.printStackTrace();
				throw e;
			}
		}
		assertEmpty(tm.getLoadedClassPaths());
		try (ClassPathLoadManager classpathmanager = new ClassPathLoadManager(
				EnvironmentTestCase.getStorageDirectoryPath());
				RepositoryManager repomanager = new RepositoryManager(classpathmanager,
						EnvironmentTestCase.getSakerJarPath())) {

			try (SakerRepository repo = repomanager.loadDirectRepository(loaddir, serviceloader)) {
				repo.executeAction("other.property", "other.value");
				assertEquals(System.getProperty("other.property"), "other.value");
			}
		}
		assertEmpty(tm.getLoadedClassPaths());
	}
}
