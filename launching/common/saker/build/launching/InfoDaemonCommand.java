/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package saker.build.launching;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import saker.build.daemon.DaemonLaunchParameters;
import saker.build.daemon.LocalDaemonEnvironment;
import saker.build.daemon.RemoteDaemonConnection;
import saker.build.file.path.SakerPath;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils.ThreadWorkPool;
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
	 * 
	 * If not specified, information about locally running daemons will be printed.
	 * </pre>
	 */
	@Parameter("-address")
	public DaemonAddressParam address;

	public void call() throws IOException {
		ReentrantLock iolock = new ReentrantLock();

		if (address == null) {
			List<Integer> ports = LocalDaemonEnvironment
					.getRunningDaemonPorts(SakerEnvironmentImpl.getDefaultStorageDirectory());
			if (!ports.isEmpty()) {
				try (ThreadWorkPool pool = ThreadUtils.newDynamicWorkPool()) {
					for (Integer port : ports) {
						pool.offer(() -> {
							printDaemonInformationOfDaemon(
									DaemonAddressParam.getDefaultLocalDaemonSocketAddressWithPort(port), iolock);
						});
					}
				}
			} else {
				System.out.println("No daemon running on the local machine.");
			}
		} else {
			printDaemonInformationOfDaemon(this.address.getSocketAddressThrowArgumentException(), iolock);
		}
	}

	private static void printDaemonInformationOfDaemon(InetSocketAddress sockaddress, Lock lock) {
		RemoteDaemonConnection connected;
		try {
			connected = RemoteDaemonConnection.connect(sockaddress);
		} catch (IOException e) {
			lock.lock();
			try {
				System.out
						.println("No daemon running at address or failed to connect: " + sockaddress + " (" + e + ")");
			} finally {
				lock.unlock();
			}
			return;
		}
		boolean locked = false;
		try (RemoteDaemonConnection env = connected) {
			DaemonLaunchParameters launchconfig = env.getDaemonEnvironment().getRuntimeLaunchConfiguration();
			lock.lock();
			locked = true;
			System.out.println("Daemon running at: " + sockaddress);
			System.out.println("Configuration: ");
			printInformation(launchconfig, System.out);
		} catch (Exception e) {
			System.out.println("Operation failed: ");
			e.printStackTrace();
		} finally {
			if (locked) {
				lock.unlock();
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
