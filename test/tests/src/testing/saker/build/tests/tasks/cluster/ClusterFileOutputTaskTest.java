package testing.saker.build.tests.tasks.cluster;

import java.util.Set;

import saker.build.file.path.SakerPath;
import saker.build.file.path.SimpleProviderHolderPathKey;
import saker.build.runtime.classpath.JarFileClassPathLocation;
import saker.build.runtime.classpath.ServiceLoaderClassPathServiceEnumerator;
import saker.build.runtime.params.ExecutionRepositoryConfiguration;
import saker.build.runtime.repository.SakerRepositoryFactory;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;
import testing.saker.build.tests.tasks.repo.RepositoryTestUtils;

@SakerTest
public class ClusterFileOutputTaskTest extends ClusterBuildTestCase {
	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath repopath = PATH_WORKING_DIRECTORY.resolve(RepositoryTestUtils.createRepositoryJarName(getClass()));
		RepositoryTestUtils.exportTestRepositoryJar(files, repopath);

		JarFileClassPathLocation repoloc = new JarFileClassPathLocation(
				new SimpleProviderHolderPathKey(files, repopath));

		parameters.setRepositoryConfiguration(ExecutionRepositoryConfiguration.builder()
				.add(repoloc, new ServiceLoaderClassPathServiceEnumerator<>(SakerRepositoryFactory.class)).build());

		SakerPath outputfilepath = PATH_BUILD_DIRECTORY.resolve("output.txt");

		runScriptTask("build");
		assertEquals(files.getAllBytes(outputfilepath).toString(), "content");
	}

	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		// use a private environment
		return EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations())
				.setEnvironmentStorageDirectory(null).build();
	}

}
