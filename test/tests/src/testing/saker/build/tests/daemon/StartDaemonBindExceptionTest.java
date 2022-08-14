package testing.saker.build.tests.daemon;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Map;

import saker.build.daemon.DaemonLaunchParameters;
import saker.build.daemon.LocalDaemonEnvironment;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.thirdparty.saker.util.function.ThrowingRunnable;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.thread.ExceptionThread;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;
import testing.saker.build.tests.EnvironmentTestCase;

/**
 * Checks that the {@link LocalDaemonEnvironment} starting throws a {@link BindException} in case the port is already
 * taken. This is necessary as we rely on the exception being {@link BindException} in some IDE support classes.
 */
@SakerTest
public class StartDaemonBindExceptionTest extends SakerTestCase {

	@Override
	@SuppressWarnings("try") // suppress Warning: explicit call to close() on an auto-closeable resource
	public void runTest(Map<String, String> parameters) throws Throwable {
		Path storagedir = EnvironmentTestCase.getStorageDirectoryPath().resolve(getClass().getName());
		LocalFileProvider.getInstance().clearDirectoryRecursively(storagedir);
		LocalFileProvider.getInstance().createDirectories(storagedir);

		try (ServerSocket socket = new ServerSocket(0)) {
			ExceptionThread acceptthread = new ExceptionThread((ThrowingRunnable) () -> {
				//the accept() should throw when the socket is closed
				Socket accepted = null;
				try {
					accepted = socket.accept();
				} catch (IOException e) {
					//expected
				} finally {
					//close the possibly accepted socket anyway, not to leak resources
					IOUtils.close(accepted);
				}
			});
			acceptthread.start();
			DaemonLaunchParameters.Builder paramsbuilder = DaemonLaunchParameters.builder();
			//choose a free port to start the server on
			paramsbuilder.setPort(socket.getLocalPort());
			paramsbuilder.setStorageDirectory(SakerPath.valueOf(storagedir));
			DaemonLaunchParameters params = paramsbuilder.build();
			System.out.println("Params: " + params);

			try (LocalDaemonEnvironment daemonenv = new LocalDaemonEnvironment(EnvironmentTestCase.getSakerJarPath(),
					params)) {
				//starting should fail with BindException, as the daemon cannot bind to the port, as it is already bound
				assertException(BindException.class, () -> daemonenv.start());

				//close the socket
				socket.close();

				//should start proparly, as the port is available now
				daemonenv.start();
			}
			acceptthread.joinThrow();
		}
	}

}
