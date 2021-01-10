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

import javax.net.SocketFactory;

import saker.build.daemon.DaemonLaunchParameters;
import saker.build.daemon.LocalDaemonEnvironment;
import saker.build.daemon.LocalDaemonEnvironment.RunningDaemonConnectionInfo;
import saker.build.daemon.RemoteDaemonConnection;
import saker.build.file.path.SakerPath;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.execution.SakerLog;
import saker.build.runtime.execution.SakerLog.CommonExceptionFormat;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils.ThreadWorkPool;
import saker.build.util.exc.ExceptionView;
import sipka.cmdline.api.Parameter;
import sipka.cmdline.api.ParameterContext;

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
	 * The network address of the daemon to connect to.
	 * If the daemon is not running at the given address, or doesn't accept
	 * client connections then an exception will be thrown.
	 * 
	 * If not specified, information about locally running daemons will be printed.
	 * </pre>
	 */
	@Parameter("-address")
	public DaemonAddressParam addressParam;

	@ParameterContext
	public AuthKeystoreParamContext authParams = new AuthKeystoreParamContext();

	public void call() throws IOException {
		ReentrantLock iolock = new ReentrantLock();

		if (addressParam != null) {
			RunningDaemonConnectionInfo[] outconninfo = { null };
			InetSocketAddress address = this.addressParam.getSocketAddressThrowArgumentException();
			SocketFactory socketfactory = authParams.getSocketFactoryForDaemonConnection(address, null, outconninfo);
			printDaemonInformationOfDaemon(address, iolock, outconninfo[0], socketfactory, null);
			return;
		}

		List<RunningDaemonConnectionInfo> connectioninfos = LocalDaemonEnvironment
				.getRunningDaemonInfos(SakerEnvironmentImpl.getDefaultStorageDirectory());
		if (connectioninfos.isEmpty()) {
			System.out.println("No daemon running on the local machine.");
			return;
		}
		try (ThreadWorkPool pool = ThreadUtils.newDynamicWorkPool()) {
			for (RunningDaemonConnectionInfo conninfo : connectioninfos) {
				pool.offer(() -> {
					printDaemonInformationOfDaemon(conninfo, iolock);
				});
			}
		}
	}

	private void printDaemonInformationOfDaemon(RunningDaemonConnectionInfo conninfo, ReentrantLock iolock) {
		String sslkspathstr = conninfo.getSslKeystorePath();
		Exception keystoreautoopenexception = null;
		SocketFactory socketfactory;
		if (ObjectUtils.isNullOrEmpty(sslkspathstr)) {
			//don't use an ssl socket factory even if specified, as the daemon doesn't use it
			socketfactory = null;
		} else {
			//use file system instead of Paths.get so we don't use other funky uris or things.
			try {
				socketfactory = authParams.getSocketFactoryForDefaultedKeystore(SakerPath.valueOf(sslkspathstr));
			} catch (Exception e) {
				keystoreautoopenexception = e;
				socketfactory = null;
			}
		}
		printDaemonInformationOfDaemon(
				DaemonAddressParam.getDefaultLocalDaemonSocketAddressWithPort(conninfo.getPort()), iolock, conninfo,
				socketfactory, keystoreautoopenexception);
	}

	private static void printDaemonInformationOfDaemon(InetSocketAddress sockaddress, Lock lock,
			RunningDaemonConnectionInfo conninfo, SocketFactory socketfactory, Exception keystoreautoopenexception) {
		RemoteDaemonConnection connected;
		try {
			connected = RemoteDaemonConnection.connect(socketfactory, sockaddress);
		} catch (IOException e) {
			lock.lock();
			try {
				System.out.println("No daemon running at address or failed to connect: " + sockaddress);
				SakerLog.printFormatException(ExceptionView.create(e), CommonExceptionFormat.NO_TRACE);
				if (conninfo != null) {
					String keystorepath = conninfo.getSslKeystorePath();
					if (!ObjectUtils.isNullOrEmpty(keystorepath)) {
						System.out.println("Daemon uses keystore for authentication: " + keystorepath);
					}
				}
				if (keystoreautoopenexception != null) {
					System.out.println("Failed to automatically open keystore for daemon.");
					LaunchConfigUtils.printArgumentExceptionOmitTraceIfSo(keystoreautoopenexception);
				}
			} finally {
				//empty line
				System.out.println();
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
			if (conninfo != null) {
				String keystorepath = conninfo.getSslKeystorePath();
				if (!ObjectUtils.isNullOrEmpty(keystorepath)) {
					System.out.println("Daemon uses keystore for authentication: " + keystorepath);
				}
			}
			System.out.println("Configuration: ");
			printInformation(launchconfig, System.out);
		} catch (Exception e) {
			System.out.println("Operation failed: ");
			e.printStackTrace();
		} finally {
			if (locked) {
				//empty line
				System.out.println();
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
