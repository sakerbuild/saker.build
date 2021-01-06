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

import javax.net.ssl.SSLContext;

import saker.build.daemon.DaemonLaunchParameters;
import saker.build.daemon.DaemonOutputController.StreamToken;
import saker.build.daemon.LocalDaemonEnvironment.AddressResolver;
import saker.build.daemon.LocalDaemonEnvironment;
import saker.build.daemon.WeakRefDaemonOutputController;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.util.config.JVMSynchronizationObjects;
import sipka.cmdline.api.Flag;
import sipka.cmdline.api.Parameter;
import sipka.cmdline.api.ParameterContext;
import sipka.cmdline.runtime.MissingArgumentException;

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
	public EnvironmentParamContext envParams = new EnvironmentParamContext();
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

	/**
	 * <pre>
	 * Flag to specify that the JVM shouldn't be forcibly exited when a stop request
	 * is made towards the build daemon.
	 * 
	 * Generally developers shouldn't use this flag and may be useful only when the 
	 * build system is run programmatically rather than via command line.
	 * </pre>
	 */
	@Parameter("-no-jvm-exit")
	@Flag
	public boolean noJvmExit = false;

	@ParameterContext
	public SakerJarLocatorParamContext sakerJarParam = new SakerJarLocatorParamContext();

	@ParameterContext
	public AuthKeystoreParamContext authParams = new AuthKeystoreParamContext();

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

			boolean noexit = noJvmExit;
			SSLContext sslcontext = authParams.getSSLContext();
			daemonenv = new LocalDaemonEnvironment(sakerJarParam.getSakerJarPath(), launchparams, outputcontroller,
					LaunchingUtils.getSocketFactory(sslcontext)) {
				@Override
				protected void closeImpl() {
					//when the daemon environment is being closed, call system.exit so any mistakenly unjoined threads won't prevent JVM shutdown
					if (!noexit) {
						System.exit(0);
					}
				}
			};
			daemonenv.setServerSocketFactory(LaunchingUtils.getServerSocketFactory(sslcontext));
			daemonenv.setSslKeystorePath(authParams.getAuthKeystorePath());
			if (!ObjectUtils.isNullOrEmpty(startParams.connectClientParam)) {
				if (!launchparams.isActsAsCluster()) {
					throw new MissingArgumentException("Cannot connect to daemon as client without acting as cluster.",
							GeneralDaemonParams.PARAM_NAME_CLUSTER_ENABLE);
				}
				Set<AddressResolver> serveraddresses = new LinkedHashSet<>();
				ThreadUtils.runParallelItems(startParams.connectClientParam, addr -> {
					serveraddresses.add(addr.getAsAddressResolver());
				});
				daemonenv.setConnectToAsClusterAddresses(serveraddresses);
			}

			Integer serverport = launchparams.getPort();
			Integer useserverport;
			if (serverport == null) {
				useserverport = null;
			} else {
				useserverport = serverport < 0 ? DaemonLaunchParameters.DEFAULT_PORT : serverport;
			}

			if (useserverport == null) {
				if (ObjectUtils.isNullOrEmpty(startParams.connectClientParam)) {
					throw new IllegalArgumentException("Invalid daemon start parameters. "
							+ "Daemon doesn't accept connections, or connects to servers. It has no purpose. "
							+ "Specify -port or -connect-client parameters.");
				}
			}

			daemonenv.start();

			//print this AFTER the daemon is started, so the starter process can already connect
			if (useserverport != null) {
				System.out.println(FIRST_LINE_WITH_PORT_PREFIX + useserverport + FIRST_LINE_WITH_PORT_SUFFIX);
			} else {
				System.out.println(FIRST_LINE_NON_SERVER_DAEMON);
			}

			SocketAddress serveraddr = daemonenv.getServerSocketAddress();
			if (serveraddr != null) {
				System.out.println("Daemon listening at address: " + serveraddr);
			}

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