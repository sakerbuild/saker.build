package saker.build.launching;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Map.Entry;

import saker.build.daemon.DaemonLaunchParameters;
import saker.build.daemon.RemoteDaemonConnection;
import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.ObjectUtils;
import sipka.cmdline.api.Parameter;

/**
 * <pre>
 * Displays information about the daemon running at a given address.
 * </pre>
 */
public class InfoDaemonCommand {
	private static final byte[] EQUALS_SPACES_BYTES = " = ".getBytes(StandardCharsets.UTF_8);
	private static final byte[] SPACE_4_BYTES = "    ".getBytes(StandardCharsets.UTF_8);
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
		InetSocketAddress sockaddress = this.address.getSocketAddress();
		RemoteDaemonConnection connected;
		try {
			connected = RemoteDaemonConnection.connect(sockaddress);
		} catch (IOException e) {
			System.out.println("No daemon running at address or failed to connect: " + sockaddress + " (" + e + ")");
			return;
		}
		try (RemoteDaemonConnection env = connected) {
			System.out.println("Daemon running at: " + sockaddress);
			try {
				System.out.println("Configuration: ");
				printInformation(env.getDaemonEnvironment().getRuntimeLaunchConfiguration(), System.out);
			} catch (Exception e) {
				System.out.println("Operation failed: ");
				e.printStackTrace();
			}
		}
	}

	public static void printInformation(DaemonLaunchParameters launchparams, OutputStream os) throws IOException {
		os.write(
				("  Storage directory: " + launchparams.getStorageDirectory() + "\n").getBytes(StandardCharsets.UTF_8));
		Integer port = launchparams.getPort();
		if (port != null && port > 0) {
			os.write(("  Listening port: " + port + "\n").getBytes(StandardCharsets.UTF_8));
			os.write(("  Acts as server: " + launchparams.isActsAsServer() + "\n").getBytes(StandardCharsets.UTF_8));
		} else {
			os.write(("  Daemon is not accepting connections.\n").getBytes(StandardCharsets.UTF_8));
		}
		if (launchparams.isActsAsCluster()) {
			os.write(("  Acts as cluster.\n").getBytes(StandardCharsets.UTF_8));
			SakerPath clustermirrordir = launchparams.getClusterMirrorDirectory();
			if (clustermirrordir != null) {
				os.write(("    Cluster mirror directory: " + clustermirrordir + "\n").getBytes(StandardCharsets.UTF_8));
			}
		}
		Map<String, String> envuserparams = launchparams.getUserParameters();
		if (!ObjectUtils.isNullOrEmpty(envuserparams)) {
			os.write(("  Environment user parameters:\n".getBytes(StandardCharsets.UTF_8)));
			for (Entry<String, String> entry : envuserparams.entrySet()) {
				os.write(SPACE_4_BYTES);
				os.write(entry.getKey().getBytes(StandardCharsets.UTF_8));
				String val = entry.getValue();
				if (val != null) {
					os.write(EQUALS_SPACES_BYTES);
					os.write(val.getBytes(StandardCharsets.UTF_8));
				}
				os.write('\n');
			}
		}
	}
}
