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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.net.SocketAddress;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

import saker.build.daemon.DaemonLaunchParameters;
import saker.build.daemon.DaemonOutputController.StreamToken;
import saker.build.daemon.LocalDaemonEnvironment;
import saker.build.daemon.WeakRefDaemonOutputController;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.util.config.JVMSynchronizationObjects;
import sipka.cmdline.api.Flag;
import sipka.cmdline.api.Parameter;
import sipka.cmdline.api.ParameterContext;

/**
 * <pre>
 * Runs the build daemon in this process with the given parameters.
 * 
 * This command can be used to run the daemon manually, when you don't want the
 * build system to automatically manage its lifetime.
 * 
 * This command can also be used to start a daemon with debugging enabled, and
 * connect to it via a debug client. One example for it is:
 * 
 *     java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=1044 -jar ...
 * 
 * After starting it you can use the debugger of your choosing to debug the daemon process.
 * </pre>
 */
public class RunDaemonCommand {
	private static final String FIRST_LINE_WITH_PORT_PREFIX = "Starting daemon on port ";
	private static final String FIRST_LINE_WITH_PORT_SUFFIX = "...";

	public static final String FIRST_LINE_NON_SERVER_DAEMON = "Starting daemon...";
	public static final Pattern FIRST_LINE_PATTERN_WITH_PORT = Pattern.compile(
			Pattern.quote("Starting daemon on port ") + "([0-9]+)" + Pattern.quote(FIRST_LINE_WITH_PORT_SUFFIX));
	public static final int FIRST_LINE_PATTERN_WITH_PORT_GROUP_PORTNUMBER = 1;

	private static class TokenRefs {
		//only just for holding the reference
		@SuppressWarnings("unused")
		private static StreamToken token = null;
	}

	@ParameterContext
	public RunGeneralDaemonParams daemonParams = new RunGeneralDaemonParams();
	@ParameterContext
	public EnvironmentParams envParams = new EnvironmentParams();
	@ParameterContext
	public StartDaemonParams startParams = new StartDaemonParams();

	/**
	 * <pre>
	 * Specify this flag to not display the standard output and error of the process.
	 * </pre>
	 */
	@Parameter("-no-output")
	@Flag
	public boolean noOutput = false;

	@ParameterContext
	public SakerJarLocatorParamContext sakerJarParam = new SakerJarLocatorParamContext();

	@SuppressWarnings("resource")
	public void call() throws FileNotFoundException, IOException {
		PrintStream err = System.err;
		PrintStream out = System.out;
		LocalDaemonEnvironment daemonenv = null;
		try {
			DaemonLaunchParameters launchparams = daemonParams.toLaunchParameters(envParams);

			WeakRefDaemonOutputController outputcontroller = new WeakRefDaemonOutputController();
			if (!noOutput) {
				TokenRefs.token = outputcontroller.replaceStandardIOAndAttach();
			} else {
				outputcontroller.replaceStandardIO();
			}

			daemonenv = new LocalDaemonEnvironment(sakerJarParam.getSakerJarPath(), launchparams, outputcontroller);
			if (!ObjectUtils.isNullOrEmpty(startParams.connectClientParam)) {
				if (!launchparams.isActsAsCluster()) {
					throw new IllegalArgumentException(
							"Cannot connect to daemon as client without acting as cluster. (Use "
									+ GeneralDaemonParams.PARAM_NAME_CLUSTER_ENABLE + ")");
				}
				Set<SocketAddress> serveraddresses = new LinkedHashSet<>();
				for (DaemonAddressParam addr : startParams.connectClientParam) {
					serveraddresses.add(addr.getSocketAddress());
				}
				daemonenv.setConnectToAsClusterAddresses(serveraddresses);
			}

			Integer serverport = launchparams.getPort();
			Integer useserverport;
			if (serverport == null) {
				useserverport = null;
			} else {
				useserverport = serverport < 0 ? DaemonLaunchParameters.DEFAULT_PORT : serverport;
			}

			if (useserverport != null) {
				System.out.println(FIRST_LINE_WITH_PORT_PREFIX + useserverport + FIRST_LINE_WITH_PORT_SUFFIX);
			} else {
				if (ObjectUtils.isNullOrEmpty(startParams.connectClientParam)) {
					throw new IllegalArgumentException("Invalid daemon start parameters. "
							+ "Daemon doesn't accepts connections, or connects to servers. It has no purpose. "
							+ "Specify -port or -connect-client parameters.");
				}
				System.out.println(FIRST_LINE_NON_SERVER_DAEMON);
			}

			daemonenv.start();

			System.out.println("Daemon listening at address: " + daemonenv.getServerSocketAddress());

			if (!noOutput) {
				System.out.println("Running daemon with configuration: ");
				InfoDaemonCommand.printInformation(daemonenv.getRuntimeLaunchConfiguration(), System.out);
			}
		} catch (Throwable e) {
			if (daemonenv != null) {
				IOUtils.addExc(e, IOUtils.closeExc(daemonenv));
			}
			//restore the streams so the caller can print the exception appropriately
			synchronized (JVMSynchronizationObjects.getStandardIOLock()) {
				System.setOut(out);
				System.setErr(err);
			}
			throw e;
		}
	}
}