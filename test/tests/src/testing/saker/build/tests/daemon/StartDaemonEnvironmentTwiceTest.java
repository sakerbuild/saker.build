package testing.saker.build.tests.daemon;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
	@SuppressWarnings("try") // the file lock is not used in the method body
	public void runTest(Map<String, String> parameters) throws Throwable {
		Path storagedir = EnvironmentTestCase.getStorageDirectoryPath().resolve(getClass().getName());
		LocalFileProvider.getInstance().clearDirectoryRecursively(storagedir);
		LocalFileProvider.getInstance().createDirectories(storagedir);

		DaemonLaunchParameters.Builder paramsbuilder = DaemonLaunchParameters.builder();
		//choose a free port to start the server on
		paramsbuilder.setPort(0);
		paramsbuilder.setStorageDirectory(SakerPath.valueOf(storagedir));
		DaemonLaunchParameters params = paramsbuilder.build();

		try (FileChannel fc = FileChannel.open(storagedir.resolve(LocalDaemonEnvironment.DAEMON_LOCK_FILE_NAME),
				StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {

			try (LocalDaemonEnvironment daemonenv = new LocalDaemonEnvironment(EnvironmentTestCase.getSakerJarPath(),
					params)) {
				try (FileLock lock = fc.lock()) {
					//starting should fail, as the daemon cannot lock the lock file for initialization
					assertException(Exception.class, () -> daemonenv.start()).printStackTrace();
				}

				daemonenv.start();
			}
		}
	}

}
