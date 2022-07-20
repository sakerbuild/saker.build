package testing.saker.build.tests.daemon;

import java.nio.file.Path;
import java.util.Map;

import saker.build.daemon.DaemonLaunchParameters;
import saker.build.daemon.LocalDaemonEnvironment;
import saker.build.daemon.RemoteDaemonConnection;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.thirdparty.saker.util.thread.ExceptionThread;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;
import testing.saker.build.tests.EnvironmentTestCase;

@SakerTest
public class InitRunDaemonTest extends SakerTestCase {

	@Override
	@SuppressWarnings("try") // suppress warning about explicit call to close() on an auto-closeable resource
	public void runTest(Map<String, String> parameters) throws Throwable {
		Path storagedir = EnvironmentTestCase.getStorageDirectoryPath().resolve(getClass().getName());
		LocalFileProvider.getInstance().clearDirectoryRecursively(storagedir);
		LocalFileProvider.getInstance().createDirectories(storagedir);

		DaemonLaunchParameters.Builder paramsbuilder = DaemonLaunchParameters.builder();
		//choose a free port to start the server on
		paramsbuilder.setPort(0);
		paramsbuilder.setStorageDirectory(SakerPath.valueOf(storagedir));
		DaemonLaunchParameters params = paramsbuilder.build();

		try (LocalDaemonEnvironment daemonenv = new LocalDaemonEnvironment(EnvironmentTestCase.getSakerJarPath(),
				params)) {
			daemonenv.init();

			boolean[] threadquit = { false };

			ExceptionThread excthread = new ExceptionThread() {
				@Override
				protected void runImpl() throws Exception {
					try {
						Thread.sleep(500);
						try (RemoteDaemonConnection connection = RemoteDaemonConnection
								.connect(daemonenv.getServerSocketAddress())) {
							System.out.println("InitRunDaemonTest.runTest(...).new ExceptionThread() {...}.runImpl() "
									+ connection.getAddress());
							//shut down the daemon
							threadquit[0] = true;
							connection.getDaemonEnvironment().close();
						}
					} catch (Throwable e) {
						try {
							threadquit[0] = false;
							//something failed, close the daemon environment directly, so at least the run() call returns
							daemonenv.close();
						} catch (Throwable e2) {
							e.addSuppressed(e2);
						}
						throw e;
					}
				}
			};
			excthread.start();

			daemonenv.run();
			//the run() should only return if we close the daemon environment in the separate thread
			assertTrue(threadquit[0]);

			excthread.joinThrow();
		}
	}

}
