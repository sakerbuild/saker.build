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
import java.net.InetSocketAddress;

import javax.net.SocketFactory;

import saker.build.daemon.RemoteDaemonConnection;
import sipka.cmdline.api.Parameter;
import sipka.cmdline.api.ParameterContext;
import sipka.cmdline.api.SubCommand;

/**
 * <pre>
 * Stops the build daemon at the specified address.
 * </pre>
 */
@SubCommand(name = "all", type = StopAllDaemonCommand.class)
public class StopDaemonCommand {
	/**
	 * <pre>
	 * The network address of the daemon to connect to.
	 * If the daemon is not running at the given address, or doesn't accept
	 * client connections then an exception will be thrown.
	 * </pre>
	 */
	@Parameter("-address")
	public DaemonAddressParam addressParam = new DaemonAddressParam();

	@ParameterContext
	public AuthKeystoreParamContext authParams = new AuthKeystoreParamContext();

	public void call() throws IOException {
		InetSocketAddress address = addressParam.getSocketAddressThrowArgumentException();
		SocketFactory socketfactory = authParams.getSocketFactoryForDaemonConnection(address);
		try (RemoteDaemonConnection connection = RemoteDaemonConnection.connect(socketfactory, address)) {
			connection.getDaemonEnvironment().close();
		} catch (Exception e) {
			throw new IOException("Failed to close daemon at: " + address, e);
		}
		System.out.println("Build daemon at " + address + " has been stopped.");
	}
}