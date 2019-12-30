package saker.build.launching;

import java.io.IOException;
import java.net.InetSocketAddress;

import saker.build.daemon.RemoteDaemonConnection;
import sipka.cmdline.api.Parameter;

/**
 * <pre>
 * Stops the daemon at the specified address.
 * </pre>
 */
public class StopDaemonCommand {
	/**
	 * <pre>
	 * The address of the daemon to connect to.
	 * If the daemon is not running at the given address, or doesn't accept
	 * client connections then an exception will be thrown.
	 * </pre>
	 */
	@Parameter("-address")
	public DaemonAddressParam address = new DaemonAddressParam();

	public void call() throws IOException {
		InetSocketAddress sockaddr = address.getSocketAddress();
		try (RemoteDaemonConnection env = RemoteDaemonConnection.connect(sockaddr)) {
			env.getDaemonEnvironment().close();
		}
	}
}