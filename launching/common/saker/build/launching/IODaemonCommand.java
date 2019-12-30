package saker.build.launching;

import java.io.IOException;

import saker.build.daemon.DaemonOutputController;
import saker.build.daemon.DaemonOutputController.StreamToken;
import saker.build.daemon.RemoteDaemonConnection;
import saker.build.daemon.RemoteDaemonConnection.ConnectionIOErrorListener;
import saker.build.thirdparty.saker.util.io.ByteSink;
import sipka.cmdline.api.Parameter;

/**
 * <pre>
 * Attach to a daemon and forward the standard out and standard error to this process.
 * 
 * This command can be used to connect to a daemon and view the output of it.
 * This is mainly for debugging purposes.
 * </pre>
 */
public class IODaemonCommand {
	/**
	 * <pre>
	 * The address of the daemon to connect to.
	 * If the daemon is not running at the given address, or doesn't accept
	 * client connections then an exception will be thrown.
	 * </pre>
	 */
	@Parameter("-address")
	public DaemonAddressParam addressParams = new DaemonAddressParam();

	public void call() throws IOException {
		RemoteDaemonConnection connected = RemoteDaemonConnection.connect(addressParams.getSocketAddress());
		DaemonOutputController controller = connected.getDaemonEnvironment().getOutputController();
		if (controller == null) {
			throw new IOException("Failed to attach to remote daemon I/O.");
		}
		connected.addConnectionIOErrorListener(new ConnectionIOErrorListener() {
			@SuppressWarnings("unused")
			private StreamToken errtoken = controller.addStandardError(ByteSink.valueOf(System.err));
			@SuppressWarnings("unused")
			private StreamToken outtoken = controller.addStandardOutput(ByteSink.valueOf(System.out));

			@Override
			public void onConnectionError(Throwable exc) {
				System.out.println("Connection lost: " + exc);
				exc.printStackTrace();
			}
		});
	}
}