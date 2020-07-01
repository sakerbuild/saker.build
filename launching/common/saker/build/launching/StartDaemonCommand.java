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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import saker.build.daemon.DaemonLaunchParameters;
import saker.build.daemon.LocalDaemonEnvironment;
import saker.build.daemon.RemoteDaemonConnection;
import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.util.java.JavaTools;
import sipka.cmdline.api.ParameterContext;
import sipka.cmdline.runtime.ParseUtil;

/**
 * <pre>
 * Starts a build daemon with the specified parameters.
 * 
 * Build daemons are long running background processes that can 
 * be used as a cache for running builds and avoiding longer
 * recurring initialization times.
 * 
 * They can also be used as clusters to distribute build tasks
 * of builds to multiple machines over the network.
 * 
 * The same Java Runtime Environment will be used as the one used to
 * start this process. (I.e. the same java.exe is started for the daemon.)
 * 
 * Started daemons can be stopped using the 'stop' command.
 * </pre>
 */
public class StartDaemonCommand {
	@ParameterContext
	public GeneralDaemonParams daemonParams = new GeneralDaemonParams();
	@ParameterContext
	public EnvironmentParams envParams = new EnvironmentParams();
	@ParameterContext
	public StartDaemonParams startParams = new StartDaemonParams();

	@ParameterContext
	public SakerJarLocatorParamContext sakerJarParam = new SakerJarLocatorParamContext();

	public void call() throws IOException {
		SakerPath storagedirpath = daemonParams.getStorageDirectory();

		DaemonLaunchParameters thislaunchparams = daemonParams.toLaunchParameters(envParams);

		InetAddress connectaddress = InetAddress.getLoopbackAddress();
		try {
			Integer runningport = LocalDaemonEnvironment.getRunningDaemonPort(storagedirpath);
			if (runningport != null && runningport.intValue() > 0) {
				try (RemoteDaemonConnection connected = RemoteDaemonConnection
						.connect(new InetSocketAddress(connectaddress, runningport))) {
					DaemonLaunchParameters runninglaunchparams = connected.getDaemonEnvironment().getLaunchParameters();
					if (!runninglaunchparams.equals(thislaunchparams)) {
						throw throwDifferentLaunchParameters(thislaunchparams, runninglaunchparams);
					}
					System.out.println("Daemon is already running with same parameters.");
					return;
				} catch (RMIRuntimeException e) {
				}
			}
		} catch (IOException e) {
			//no daemon is running
		}

		try (RemoteDaemonConnection daemonconnection = connectOrCreateDaemon(JavaTools.getCurrentJavaExePath(),
				sakerJarParam.getSakerJarPath(), thislaunchparams)) {
			DaemonLaunchParameters runninglaunchparams = daemonconnection.getDaemonEnvironment().getLaunchParameters();
			if (!runninglaunchparams.equals(thislaunchparams)) {
				throw throwDifferentLaunchParameters(thislaunchparams, runninglaunchparams);
			}
		}
	}

	public static RemoteDaemonConnection connectOrCreateDaemon(Path javaexe, Path sakerjarpath,
			DaemonLaunchParameters launchparams) throws IOException {
		return connectOrCreateDaemon(javaexe, sakerjarpath, launchparams, null);
	}

	public static RemoteDaemonConnection connectOrCreateDaemon(Path javaexe, Path sakerjarpath,
			DaemonLaunchParameters launchparams, StartDaemonParams startparams) throws IOException {
		SakerPath storagedirpath = launchparams.getStorageDirectory();
		InetAddress connectaddress = InetAddress.getLoopbackAddress();
		Integer runningport = null;
		try {
			runningport = LocalDaemonEnvironment.getRunningDaemonPort(storagedirpath);
		} catch (IOException e) {
			throw new IOException("Failed to determine daemon state at storage directory: " + storagedirpath, e);
		}
		if (runningport != null) {
			if (runningport.intValue() > 0) {
				try {
					return RemoteDaemonConnection.connect(new InetSocketAddress(connectaddress, runningport));
				} catch (RMIRuntimeException e) {
					throw new IOException("Failed to communicate with daemon at: " + storagedirpath, e);
				}
			}
			//a daemon is already running at the given storage directory, and we cannot connect to it, as it does not accept connections
			throw new IOException("Cannot use daemon at storage directory: " + storagedirpath);
		}
		//no daemon is running at the given path, try to start it
		List<String> commands = new ArrayList<>();
		commands.add(javaexe.toString());
		//TODO should also add -D system properties if necessary
		commands.add("-cp");
		commands.add(sakerjarpath.toString());
		commands.add(saker.build.launching.Main.class.getName());
		commands.add("daemon");
		commands.add("run");

		addDaemonLaunchParametersToCommandLine(commands, launchparams, startparams);

		ProcessBuilder pb = new ProcessBuilder(commands);
		pb.redirectErrorStream(true);

		Process proc = pb.start();

		// 12 * 250 = 3000 ms of waiting for connection
		for (int i = 0; i < 12; i++) {
			if (!proc.isAlive()) {
				StreamUtils.copyStream(proc.getInputStream(), System.err);
				throw new IOException("Failed to start daemon, exit code: " + proc.exitValue());
			}
			runningport = LocalDaemonEnvironment.getRunningDaemonPort(storagedirpath);
			if (runningport == null) {
				try {
					Thread.sleep(250);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IOException("Daemon initialization interrupted.", e);
				}
				continue;
			}
			if (runningport == 0) {
				throw new IOException("Failed to determine started daemon port. (read zero)");
			}
			return RemoteDaemonConnection.connect(new InetSocketAddress(connectaddress, runningport));
		}
		StreamUtils.copyStream(proc.getInputStream(), System.err);
		throw new IOException("Failed to connect to started daemon, timed out.");
	}

	private static void addDaemonLaunchParametersToCommandLine(List<String> commands,
			DaemonLaunchParameters launchparams, StartDaemonParams startparams) {
		SakerPath storagedir = launchparams.getStorageDirectory();
		if (storagedir != null) {
			commands.add("-storage-directory");
			commands.add(storagedir.toString());
		}
		if (launchparams.isActsAsServer()) {
			commands.add("-server");
		}
		Integer port = launchparams.getPort();
		if (port != null) {
			commands.add("-port");
			commands.add(port.toString());
		}
		int threadfactor = launchparams.getThreadFactor();
		if (threadfactor > 0) {
			commands.add("-thread-factor");
			commands.add(Integer.toString(threadfactor));
		}
		if (launchparams.isActsAsCluster()) {
			commands.add("-cluster-enable");
		}
		SakerPath clustermirrordir = launchparams.getClusterMirrorDirectory();
		if (clustermirrordir != null) {
			commands.add("-cluster-mirror-directory");
			commands.add(clustermirrordir.toString());
		}
		Map<String, String> envuserparams = launchparams.getUserParameters();
		if (!ObjectUtils.isNullOrEmpty(envuserparams)) {
			for (Entry<String, String> entry : envuserparams.entrySet()) {
				commands.add(ParseUtil.toKeyValueArgument("-EU", entry.getKey(), entry.getValue()));
			}
		}
		if (startparams != null) {
			for (DaemonAddressParam addr : startparams.connectClientParam) {
				commands.add("-connect-client");
				commands.add(addr.argument);
			}
		}
	}

	private static IOException throwDifferentLaunchParameters(DaemonLaunchParameters thislaunchparams,
			DaemonLaunchParameters launchparams) throws IOException {
		System.err.println("Daemon is already running with different parameters: ");
		try {
			InfoDaemonCommand.printInformation(launchparams, System.err);
		} catch (IOException e) {
		}
		System.err.println("Requested parameters: ");
		try {
			InfoDaemonCommand.printInformation(thislaunchparams, System.err);
		} catch (IOException e) {
		}
		throw new IOException("Daemon is already running with different parameters.");
	}
}