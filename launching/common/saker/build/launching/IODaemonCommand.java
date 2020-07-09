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

import saker.build.daemon.DaemonOutputController;
import saker.build.daemon.DaemonOutputController.StreamToken;
import saker.build.daemon.RemoteDaemonConnection;
import saker.build.daemon.RemoteDaemonConnection.ConnectionIOErrorListener;
import saker.build.thirdparty.saker.util.io.ByteSink;
import sipka.cmdline.api.Parameter;

/**
 * <pre>
 * Attach to a daemon and forward the standard out and standard error to this process.
 * 
 * This command can be used to connect to a daemon and view the output of it.
 * This is mainly for debugging purposes.
 * </pre>
 */
public class IODaemonCommand {
	/**
	 * <pre>
	 * The address of the daemon to connect to.
	 * If the daemon is not running at the given address, or doesn't accept
	 * client connections then an exception will be thrown.
	 * </pre>
	 */
	@Parameter("-address")
	public DaemonAddressParam addressParams = new DaemonAddressParam();

	public void call() throws IOException {
		try (RemoteDaemonConnection connected = RemoteDaemonConnection.connect(addressParams.getSocketAddress())) {
			DaemonOutputController controller = connected.getDaemonEnvironment().getOutputController();
			if (controller == null) {
				throw new IOException("Failed to attach to remote daemon I/O. (Not available)");
			}
			connected.addConnectionIOErrorListener(new ConnectionIOErrorListener() {
				@SuppressWarnings("unused")
				private StreamToken errtoken = controller.addStandardError(ByteSink.valueOf(System.err));
				@SuppressWarnings("unused")
				private StreamToken outtoken = controller.addStandardOutput(ByteSink.valueOf(System.out));

				@Override
				public void onConnectionError(Throwable exc) {
					System.out.println("Connection lost: " + exc);
					exc.printStackTrace();
				}
			});
		}
	}
}