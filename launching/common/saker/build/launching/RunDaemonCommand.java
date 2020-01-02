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

import saker.build.daemon.DaemonLaunchParameters;
import saker.build.daemon.DaemonOutputController.StreamToken;
import saker.build.daemon.LocalDaemonEnvironment;
import saker.build.daemon.WeakRefDaemonOutputController;
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
	private static class TokenRefs {
		//only just for holding the reference
		@SuppressWarnings("unused")
		private static StreamToken token = null;
	}

	@ParameterContext
	public GeneralDaemonParams daemonParams = new GeneralDaemonParams();
	@ParameterContext
	public EnvironmentParams envParams = new EnvironmentParams();

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

	public void call() throws FileNotFoundException, IOException {
		DaemonLaunchParameters launchparams = daemonParams.toLaunchParameters(envParams);

		WeakRefDaemonOutputController outputcontroller = new WeakRefDaemonOutputController();
		if (!noOutput) {
			TokenRefs.token = outputcontroller.replaceStandardIOAndAttach();
		} else {
			outputcontroller.replaceStandardIO();
		}

		@SuppressWarnings("resource")
		LocalDaemonEnvironment daemonenv = new LocalDaemonEnvironment(sakerJarParam.getSakerJarPath(), launchparams,
				outputcontroller);

		System.out.println("Starting daemon...");

		daemonenv.start();

		System.out.println("Daemon listening at address: " + daemonenv.getServerSocketAddress());

		if (!noOutput) {
			System.out.println("Running daemon with configuration: ");
			InfoDaemonCommand.printInformation(daemonenv.getRuntimeLaunchConfiguration(), System.out);
		}
	}
}