package testing.saker.build.tests.classpath;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import saker.build.file.path.SakerPath;
import saker.build.file.path.SimpleProviderHolderPathKey;
import saker.build.runtime.classpath.ClassPathLoadManager;
import saker.build.runtime.classpath.ClassPathLoadManager.ClassPathLock;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.runtime.classpath.JarFileClassPathLocation;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;
import testing.saker.build.flag.TestFlag;
import testing.saker.build.tests.CollectingTestMetric;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.MemoryFileProvider;
import testing.saker.build.tests.tasks.repo.RepositoryTestUtils;

@SakerTest
public class ClassPathLoadGCTest extends SakerTestCase {
	private static final int GC_TIMEOUT_MS = 50;

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

		//TODO still fails sometimes
		assertEmpty(tm.getLoadedClassPaths());
		try (ClassPathLoadManager classpathmanager = new ClassPathLoadManager(
				EnvironmentTestCase.getStorageDirectoryPath())) {
			ClassPathLock lock = classpathmanager.loadClassPath(repoloc);
			assertNotEmpty(tm.getLoadedClassPaths());
			//throw away the lock
			lock = null;

			waitEmptyLoadedClassPaths(tm);
			lock = classpathmanager.loadClassPath(repoloc);
			lock.getClassLoaderDataFinder();
			//throw away the finder
			//throw away the lock
			assertNotEmpty(tm.getLoadedClassPaths());
			lock = null;
			waitEmptyLoadedClassPaths(tm);
		}
		assertEmpty(tm.getLoadedClassPaths());
	}

	protected static void waitEmptyLoadedClassPaths(CollectingTestMetric tm) throws InterruptedException {
		for (int i = 0; i < 5000 / GC_TIMEOUT_MS; i++) {
			System.gc();
			if (tm.getLoadedClassPaths().isEmpty()) {
				return;
			}
			Thread.sleep(GC_TIMEOUT_MS);
		}
		throw new AssertionError("Failed to collect classpath by GC.");
	}

}
