package testing.saker.build.tests.scenario;

import java.nio.file.Path;
import java.util.Map;

import saker.build.daemon.DaemonLaunchParameters;
import saker.build.daemon.LocalDaemonEnvironment;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;
import testing.saker.build.tests.EnvironmentTestCase;

/**
 * Checks that if starting a {@link LocalDaemonEnvironment} fails for the first time, it can be started again if the
 * error is fixed.
 */
@SakerTest
public class StartDaemonEnvironmentTwiceTest extends SakerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		Path storagedir = EnvironmentTestCase.getStorageDirectoryPath().resolve(getClass().getName());
		LocalFileProvider.getInstance().clearDirectoryRecursively(storagedir);

		DaemonLaunchParameters.Builder paramsbuilder = DaemonLaunchParameters.builder();
		//choose a free port to start the server on
		paramsbuilder.setPort(0);
		paramsbuilder.setStorageDirectory(SakerPath.valueOf(storagedir));
		DaemonLaunchParameters params = paramsbuilder.build();

		try (LocalDaemonEnvironment daemonenv = new LocalDaemonEnvironment(EnvironmentTestCase.getSakerJarPath(),
				params)) {
			//starting on a destroyed thread group should fail
			ThreadGroup tg = new ThreadGroup("destroyed-group");
			tg.destroy();
			daemonenv.setServerThreadGroup(tg);
			assertException(Exception.class, () -> daemonenv.start()).printStackTrace();

			daemonenv.setServerThreadGroup(null);
			daemonenv.start();
		}
	}

}
