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
import java.util.List;

import javax.net.SocketFactory;

import saker.build.daemon.LocalDaemonEnvironment;
import saker.build.daemon.RemoteDaemonConnection;
import saker.build.daemon.LocalDaemonEnvironment.RunningDaemonConnectionInfo;
import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils.ThreadWorkPool;
import sipka.cmdline.api.Parameter;
import sipka.cmdline.api.ParameterContext;

/**
 * <pre>
 * Stops multiple build daemons.
 * 
 * The command can be used to stop all daemons running on 
 * the local machine. All daemons that are configured to use
 * the given storage directory will be stopped.
 * 
 * If no storage directory is specified, the default one is used.
 * </pre>
 */
public class StopAllDaemonCommand {
	/**
	 * <pre>
	 * Specifies the storage directory of the build daemons to be stopped.
	 * </pre>
	 */
	@Parameter({ StorageDirectoryParamContext.PARAM_NAME_STORAGE_DIRECTORY,
			StorageDirectoryParamContext.PARAM_NAME_STORAGE_DIR, StorageDirectoryParamContext.PARAM_NAME_SD })
	public SakerPath storageDirectory;

	@ParameterContext
	public AuthKeystoreParamContext authParams = new AuthKeystoreParamContext();

	public void call() throws IOException {
		List<RunningDaemonConnectionInfo> connectioninfos = LocalDaemonEnvironment
				.getRunningDaemonInfos(StorageDirectoryParamContext.getStorageDirectoryOrDefault(storageDirectory));
		if (connectioninfos.isEmpty()) {
			System.out.println("No daemons are running.");
			return;
		}
		InetAddress loopbackddress = InetAddress.getLoopbackAddress();
		try (ThreadWorkPool pool = ThreadUtils.newDynamicWorkPool()) {
			for (RunningDaemonConnectionInfo conninfo : connectioninfos) {
				pool.offer(() -> {
					InetSocketAddress address = new InetSocketAddress(loopbackddress, conninfo.getPort());
					try {
						String sslkspathstr = conninfo.getSslKeystorePath();
						SocketFactory socketfactory;
						if (ObjectUtils.isNullOrEmpty(sslkspathstr)) {
							socketfactory = null;
						} else {
							socketfactory = authParams
									.getSocketFactoryForDefaultedKeystore(SakerPath.valueOf(sslkspathstr));
						}
						try (RemoteDaemonConnection connection = RemoteDaemonConnection.connect(socketfactory,
								address)) {
							connection.getDaemonEnvironment().close();
						}
						System.out.println("Build daemon at " + address + " has been stopped.");
					} catch (Exception e) {
						throw new IOException("Failed to close daemon at: " + address, e);
					}
				});
			}
		}
	}
}
