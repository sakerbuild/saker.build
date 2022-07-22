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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;

import javax.net.SocketFactory;

import saker.build.daemon.DaemonLaunchParameters;
import saker.build.daemon.LocalDaemonEnvironment;
import saker.build.daemon.LocalDaemonEnvironment.RunningDaemonConnectionInfo;
import saker.build.daemon.RemoteDaemonConnection;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
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
	private static final Set<StandardOpenOption> OPENOPTIONS_CREATE_NEW_WRITE = EnumSet
			.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
	private static final FileAttribute<Set<PosixFilePermission>> FILEATTRIBUTE_POSIX_RW____ = PosixFilePermissions
			.asFileAttribute(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));

	@ParameterContext
	public GeneralDaemonParams daemonParams = new GeneralDaemonParams();
	@ParameterContext
	public EnvironmentParamContext envParams = new EnvironmentParamContext();
	@ParameterContext
	public StartDaemonParams startParams = new StartDaemonParams();

	@ParameterContext
	public SakerJarLocatorParamContext sakerJarParam = new SakerJarLocatorParamContext();

	@ParameterContext
	public AuthKeystoreParamContext authParams = new AuthKeystoreParamContext();

	public void call() throws IOException {
		SakerPath storagedirpath = daemonParams.getStorageDirectory();

		DaemonLaunchParameters thislaunchparams = daemonParams.toLaunchParameters(envParams);

		InetAddress connectaddress = InetAddress.getLoopbackAddress();
		Collection<RunningDaemonConnectionInfo> connectioninfos = Collections.emptyList();
		try {
			connectioninfos = LocalDaemonEnvironment.getRunningDaemonInfos(storagedirpath);
			if (!connectioninfos.isEmpty()) {
				Integer launchparamport = thislaunchparams.getPort();
				if (launchparamport != null && launchparamport < 0) {
					launchparamport = DaemonLaunchParameters.DEFAULT_PORT;
				}
				RunningDaemonConnectionInfo runningconninfo = getConnectionInfoForPort(connectioninfos,
						launchparamport);
				if (runningconninfo != null) {
					if (!isAuthParamsMatch(this.authParams, runningconninfo)) {
						String daemonkspathstr = runningconninfo.getSslKeystorePath();
						SakerPath authkspath = this.authParams.getAuthKeystorePath();

						System.err.println(
								"Requested authentication keystore: " + (authkspath == null ? "<none>" : authkspath));
						System.err.println("Authentication keystore used by daemon: "
								+ (ObjectUtils.isNullOrEmpty(daemonkspathstr) ? "<none>" : daemonkspathstr));
						throw new IllegalStateException(
								"Daemon is already running with different authentication parameters.");
					}
					InetSocketAddress address = new InetSocketAddress(connectaddress, launchparamport);
					SocketFactory socketfactory = authParams.getSocketFactoryForDaemonConnection(address,
							LocalFileProvider.toRealPath(thislaunchparams.getStorageDirectory()), null);
					try (RemoteDaemonConnection connected = RemoteDaemonConnection.connect(socketfactory, address)) {
						DaemonLaunchParameters runninglaunchparams = connected.getDaemonEnvironment()
								.getLaunchParameters();
						if (!runninglaunchparams.equals(thislaunchparams)) {
							throw throwDifferentLaunchParameters(thislaunchparams, runninglaunchparams);
						}
						System.out.println(
								"Daemon is already running with same parameters. (Port " + launchparamport + ")");
						return;
					} catch (RMIRuntimeException e) {
					}
				}
			}
		} catch (IOException e) {
			//no daemon is running
		}

		try (RemoteDaemonConnection daemonconnection = connectOrCreateDaemon(JavaTools.getCurrentJavaExePath(),
				sakerJarParam.getSakerJarPath(), thislaunchparams, authParams, startParams, connectioninfos)) {
			DaemonLaunchParameters runninglaunchparams = daemonconnection.getDaemonEnvironment().getLaunchParameters();
			if (!runninglaunchparams.equals(thislaunchparams)) {
				throw throwDifferentLaunchParameters(thislaunchparams, runninglaunchparams);
			}
		}
	}

	private static boolean isAuthParamsMatch(AuthKeystoreParamContext authparams,
			RunningDaemonConnectionInfo conninfo) {
		String conninfokeystorepath = conninfo.getSslKeystorePath();
		SakerPath authkspath = authparams.getAuthKeystorePath();
		if (authkspath == null) {
			return ObjectUtils.isNullOrEmpty(conninfokeystorepath);
		}
		if (ObjectUtils.isNullOrEmpty(conninfokeystorepath)) {
			return false;
		}
		return SakerPath.valueOf(conninfokeystorepath).equals(authkspath);
	}

	public static RemoteDaemonConnection connectOrCreateDaemon(Path javaexe, Path sakerjarpath,
			DaemonLaunchParameters launchparams) throws IOException {
		return connectOrCreateDaemon(javaexe, sakerjarpath, launchparams, null, null, Collections.emptyList());
	}

	public static RemoteDaemonConnection connectOrCreateDaemon(Path javaexe, Path sakerjarpath,
			DaemonLaunchParameters launchparams, AuthKeystoreParamContext authparams) throws IOException {
		return connectOrCreateDaemon(javaexe, sakerjarpath, launchparams, authparams, null, Collections.emptyList());
	}

	public static RemoteDaemonConnection connectOrCreateDaemon(Path javaexe, Path sakerjarpath,
			DaemonLaunchParameters launchparams, AuthKeystoreParamContext authparams, StartDaemonParams startparams)
			throws IOException {
		Collection<RunningDaemonConnectionInfo> runningconninfos = Collections.emptyList();
		try {
			runningconninfos = LocalDaemonEnvironment.getRunningDaemonInfos(launchparams.getStorageDirectory());
		} catch (IOException e) {
			throw new IOException(
					"Failed to determine daemon state at storage directory: " + launchparams.getStorageDirectory(), e);
		}
		return connectOrCreateDaemon(javaexe, sakerjarpath, launchparams, authparams, startparams, runningconninfos);
	}

	public static RemoteDaemonConnection connectOrCreateDaemon(Path javaexe, Path sakerjarpath,
			DaemonLaunchParameters launchparams, AuthKeystoreParamContext authparams, StartDaemonParams startparams,
			Iterable<? extends RunningDaemonConnectionInfo> runningconninfos) throws IOException {
		Integer launchparamport = launchparams.getPort();
		if (launchparamport == null) {
			throw new IllegalArgumentException("Cannot connect to daemon without server port.");
		}
		InetAddress loopbackaddress = InetAddress.getLoopbackAddress();
		if (!ObjectUtils.isNullOrEmpty(runningconninfos)) {
			if (launchparamport != null && launchparamport < 0) {
				launchparamport = DaemonLaunchParameters.DEFAULT_PORT;
			}
			RunningDaemonConnectionInfo runninginfo = getConnectionInfoForPort(runningconninfos, launchparamport);
			if (runninginfo != null) {
				SocketFactory connectsocketfactory = null;
				Exception socketfactoryexc = null;
				String sslkspathstr = runninginfo.getSslKeystorePath();
				if (!ObjectUtils.isNullOrEmpty(sslkspathstr)) {
					try {
						SakerPath keystorepath = SakerPath.valueOf(sslkspathstr);
						if (authparams == null) {
							connectsocketfactory = LaunchingUtils
									.getSocketFactory(LaunchingUtils.createSSLContext(keystorepath));
						} else {
							connectsocketfactory = authparams.getSocketFactoryForDefaultedKeystore(keystorepath);
						}
					} catch (Exception e) {
						socketfactoryexc = e;
					}
				}
				InetSocketAddress address = new InetSocketAddress(loopbackaddress, launchparamport);
				try {
					return RemoteDaemonConnection.connect(connectsocketfactory, address);
				} catch (RMIRuntimeException | IOException e) {
					IOException texc = new IOException("Failed to communicate with daemon: " + address, e);
					IOUtils.addExc(texc, socketfactoryexc);
					throw texc;
				}
			}
		}

		SocketFactory socketfactory;
		if (authparams == null) {
			socketfactory = null;
		} else {
			socketfactory = authparams.getSocketFactory();
		}

		return createDaemon(javaexe, sakerjarpath, launchparams, authparams, startparams, socketfactory);
	}

	public static RemoteDaemonConnection createDaemon(Path javaexe, Path sakerjarpath,
			DaemonLaunchParameters launchparams, AuthKeystoreParamContext authparams, StartDaemonParams startparams,
			SocketFactory socketfactory) throws IOException {
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

		Throwable thrownexc = null;
		Path cmdfilepath = null;
		try {
			if (authparams != null) {
				List<String> authcmdline = authparams.toCommandLineArguments();
				if (!ObjectUtils.isNullOrEmpty(authcmdline)) {
					SakerPath kspath = authparams.getAuthKeystorePath();
					if (kspath != null) {
						String tmpdir = System.getProperty("java.io.tmpdir");
						if (ObjectUtils.isNullOrEmpty(tmpdir)) {
							throw new IllegalArgumentException("Temp directory unavailable.");
						}
						Path tmpdirpath = Paths.get(tmpdir);
						Files.createDirectories(tmpdirpath);
						cmdfilepath = tmpdirpath.resolve("auth-params." + UUID.randomUUID());
						//attempt to create the file with only user rights if possible. this is not supported on windows
						FileChannel cmdfilechannel = null;
						try {
							try {
								cmdfilechannel = FileChannel.open(cmdfilepath, OPENOPTIONS_CREATE_NEW_WRITE,
										FILEATTRIBUTE_POSIX_RW____);
							} catch (UnsupportedOperationException e) {
								cmdfilechannel = FileChannel.open(cmdfilepath, OPENOPTIONS_CREATE_NEW_WRITE);
							}
							OutputStream os = Channels.newOutputStream(cmdfilechannel);
							os.write(StringUtils.toStringJoin(System.lineSeparator(), authcmdline)
									.getBytes(StandardCharsets.UTF_8));
							commands.add("@!delete!@" + cmdfilepath);
						} finally {
							IOUtils.close(cmdfilechannel);
						}
					}
				}
			}

			ProcessBuilder pb = new ProcessBuilder(commands);
			pb.redirectErrorStream(true);

			Process proc = pb.start();
			//signal that we're not writing anything to the stdin of the daemon
			proc.getOutputStream().close();

			try (UnsyncByteArrayOutputStream linebuf = new UnsyncByteArrayOutputStream()) {
				String[] firstline = { null };
				InputStream procinstream = proc.getInputStream();
				//read on a different thread so we can properly timeout
				Thread outreadthread = ThreadUtils.startDaemonThread(() -> {
					try {
						while (true) {
							//TODO don't read byte by byte, buf more efficiently
							int r = procinstream.read();
							if (r < 0) {
								break;
							}
							if (r == '\r' || r == '\n') {
								firstline[0] = linebuf.toString();
								linebuf.write(r);
								break;
							} else {
								linebuf.write(r);
							}
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				});
				try {
					try {
						outreadthread.join(3 * 1000);
					} catch (InterruptedException e) {
						throw new IOException("Failed to start daemon, initialization interrupted.", e);
					}
					if (outreadthread.isAlive()) {
						outreadthread.interrupt();
					}
					if (firstline[0] == null) {
						throw new IOException("Failed to start daemon, timed out.");
					}
					if (RunDaemonCommand.FIRST_LINE_NON_SERVER_DAEMON.equals(firstline[0])) {
						throw new IllegalArgumentException("Cannot connect to daemon without server port.");
					}
					Matcher flmatcher = RunDaemonCommand.FIRST_LINE_PATTERN_WITH_PORT.matcher(firstline[0]);
					if (!flmatcher.matches()) {
						throw new IllegalArgumentException(
								"Failed to initialize daemon, it did not report listening port number correctly. ("
										+ firstline[0] + ")");
					}
					int daemonport = Integer
							.parseInt(flmatcher.group(RunDaemonCommand.FIRST_LINE_PATTERN_WITH_PORT_GROUP_PORTNUMBER));
					return RemoteDaemonConnection.connect(socketfactory,
							new InetSocketAddress(InetAddress.getLoopbackAddress(), daemonport));
				} catch (Throwable e) {
					try {
						linebuf.writeTo(System.err);
						//read on a new different thread, so we can interrupt it 
						Thread readonerrorthread = ThreadUtils.startDaemonThread(() -> {
							try {
								StreamUtils.copyStream(procinstream, System.err);
							} catch (IOException er) {
								e.addSuppressed(er);
							}
						});
						try {
							readonerrorthread.join(1 * 1000);
						} catch (InterruptedException ie) {
							e.addSuppressed(ie);
						}
						readonerrorthread.interrupt();
					} catch (Exception e2) {
						e.addSuppressed(e2);
					}
					throw e;
				}
			}
		} catch (Throwable e) {
			thrownexc = e;
			throw e;
		} finally {
			try {
				if (cmdfilepath != null) {
					Files.deleteIfExists(cmdfilepath);
				}
			} catch (Exception e) {
				if (thrownexc != null) {
					thrownexc.addSuppressed(e);
				} else {
					throw e;
				}
			}
		}
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
				commands.add(addr.getArgumentString());
			}
		}
	}

	private static IOException throwDifferentLaunchParameters(DaemonLaunchParameters thislaunchparams,
			DaemonLaunchParameters launchparams) {
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
		throw new IllegalStateException("Daemon is already running with different parameters.");
	}

	private static RunningDaemonConnectionInfo getConnectionInfoForPort(
			Iterable<? extends RunningDaemonConnectionInfo> infos, Integer port) {
		if (port == null) {
			return null;
		}
		return getConnectionInfoForPort(infos, port.intValue());
	}

	private static RunningDaemonConnectionInfo getConnectionInfoForPort(
			Iterable<? extends RunningDaemonConnectionInfo> infos, int port) {
		for (RunningDaemonConnectionInfo i : infos) {
			if (i.getPort() == port) {
				return i;
			}
		}
		return null;
	}

}