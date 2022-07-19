package testing.saker.build.tests.daemon;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Map;

import saker.build.daemon.DaemonLaunchParameters;
import saker.build.daemon.LocalDaemonEnvironment;
import saker.build.daemon.RemoteDaemonConnection;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;
import testing.saker.build.tests.EnvironmentTestCase;

@SakerTest
public class DaemonConnectTest extends SakerTestCase {

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
			daemonenv.start();

			Integer runtimeport = daemonenv.getRuntimeLaunchConfiguration().getPort();
			System.out.println("Port of daemon environment: " + runtimeport);
			try (RemoteDaemonConnection connection = RemoteDaemonConnection
					.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), runtimeport))) {
				assertTrue(connection.isConnected());
				System.out.println("Connected to: " + connection.getAddress());

			}
		}
	}

}
