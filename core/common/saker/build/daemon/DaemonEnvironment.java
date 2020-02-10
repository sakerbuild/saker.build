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
package saker.build.daemon;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.UUID;

import saker.build.file.path.PathKey;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerFileProvider;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.runtime.project.ProjectCacheHandle;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;

public interface DaemonEnvironment extends Closeable {
	@RMICacheResult
	public default SakerFileProvider getFileProvider() {
		return LocalFileProvider.getInstance();
	}

	public default RemoteDaemonConnection connectTo(@RMISerialize SocketAddress address) throws IOException {
		throw new IOException("Remote connection unsupported.");
	}

	@RMICacheResult
	public BuildExecutionInvoker getExecutionInvoker();

	/**
	 * @see SakerEnvironmentImpl#getEnvironmentIdentifier()
	 */
	@RMICacheResult
	@RMISerialize
	public UUID getEnvironmentIdentifier();

	public ProjectCacheHandle getProject(PathKey workingdir) throws IOException;

	/**
	 * Returns the daemon launch parameter which was used to <b>construct</b> this instance.
	 * <p>
	 * The returned launch parameters does not have its values defaultized. To retrieve the actual configuration that is
	 * currently used by the daemon use {@link #getRuntimeLaunchConfiguration()}.
	 * 
	 * @return The launch parameter which was used to construct this instance.
	 * @see #getRuntimeLaunchConfiguration()
	 */
	@RMICacheResult
	public DaemonLaunchParameters getLaunchParameters();

	/**
	 * Returns the actual configuration which is used by the current daemon.
	 * 
	 * @return The actual configuration of the daemon.
	 * @throws IllegalStateException
	 *             If the daemon has not yet been started.
	 */
	@RMICacheResult
	//TODO the runtime configuration should return the version of the runtime as well
	public DaemonLaunchParameters getRuntimeLaunchConfiguration() throws IllegalStateException;

	//doc: shuts down the daemon
	@Override
	public void close() throws IOException;

	//doc: nullable
	@RMICacheResult
	public DaemonOutputController getOutputController();
}
