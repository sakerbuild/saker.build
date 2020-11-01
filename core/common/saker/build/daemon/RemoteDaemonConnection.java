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

import javax.net.SocketFactory;

import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.task.cluster.TaskInvokerFactory;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;
import saker.build.thirdparty.saker.rmi.connection.RMIConnection;
import saker.build.thirdparty.saker.rmi.connection.RMIOptions;
import saker.build.thirdparty.saker.rmi.connection.RMISocketConfiguration;
import saker.build.thirdparty.saker.rmi.connection.RMIVariables;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolver;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolverRegistry;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.util.rmi.SakerRMIHelper;

public interface RemoteDaemonConnection extends Closeable {
	public interface ConnectionIOErrorListener {
		/**
		 * @param exc
		 *            May be <code>null</code> if the connection closed properly.
		 */
		public void onConnectionError(Throwable exc);
	}

	public DaemonEnvironment getDaemonEnvironment();

	public boolean isConnected();

	@RMISerialize
	@RMICacheResult
	public SocketAddress getAddress();

	public void addConnectionIOErrorListener(ConnectionIOErrorListener listener);

	//doc: returns null if unsupported
	public TaskInvokerFactory getClusterTaskInvokerFactory();

	//doc: closes the connection, not the daemon environment
	@Override
	public void close() throws IOException;

	//doc: class loader resolver will be overwritten
	public static RemoteDaemonConnection connect(SocketFactory socketfactory, SocketAddress address,
			RMIOptions rmioptions) throws IOException {
		RMISocketConfiguration socketconfig = new RMISocketConfiguration();
		socketconfig.setSocketFactory(socketfactory);
		//be interruptible by default
		socketconfig.setConnectionInterruptible(true);

		RMIConnection connection = initiateRMIConnection(address, rmioptions, socketconfig);
		RMIVariables vars = null;
		try {
			vars = connection.newVariables();
			DaemonAccess access = (DaemonAccess) vars
					.getRemoteContextVariable(LocalDaemonEnvironment.RMI_CONTEXT_VARIABLE_DAEMON_ACCESS);
			if (access == null) {
				throw new IOException("Failed to connect to remote daemon. No daemon access available.",
						IOUtils.closeExc(vars, connection));
			}
			DaemonEnvironment remoteenv = access.getDaemonEnvironment();
			if (remoteenv == null) {
				throw new IOException("Failed to connect to remote daemon. No environment found.",
						IOUtils.closeExc(vars, connection));
			}
			return new RemoteDaemonConnectionImpl(address, connection, remoteenv, vars);
		} catch (IOException e) {
			throw IOUtils.closeExc(e, vars, connection);
		} catch (Throwable e) {
			IOUtils.addExc(e, IOUtils.closeExc(vars, connection));
			throw e;
		}
	}

	public static RemoteDaemonConnection connect(SocketAddress address, RMIOptions rmioptions) throws IOException {
		return connect(null, address, rmioptions);
	}

	public static RemoteDaemonConnection connect(SocketFactory socketfactory, SocketAddress address)
			throws IOException {
		return connect(socketfactory, address, null);
	}

	public static RemoteDaemonConnection connect(SocketAddress address) throws IOException {
		return connect(SocketFactory.getDefault(), address);
	}

	public static ClassLoaderResolverRegistry createConnectionBaseClassLoaderResolver() {
		return SakerEnvironmentImpl.createEnvironmentBaseClassLoaderResolverRegistry();
	}

	@Deprecated
	public static RMIConnection initiateRMIConnection(SocketFactory socketfactory, SocketAddress address)
			throws IOException {
		return initiateRMIConnection(socketfactory, address, null);
	}

	public static RMIConnection initiateRMIConnection(SocketAddress address, RMISocketConfiguration socketconfig)
			throws IOException {
		return initiateRMIConnection(address, null, socketconfig);
	}

	@Deprecated
	public static RMIConnection initiateRMIConnection(SocketFactory socketfactory, SocketAddress address,
			RMIOptions rmioptions) throws IOException {
		RMISocketConfiguration socketconfig = new RMISocketConfiguration();
		socketconfig.setSocketFactory(socketfactory);
		return initiateRMIConnection(address, rmioptions, socketconfig);
	}

	public static RMIConnection initiateRMIConnection(SocketAddress address, RMIOptions rmioptions,
			RMISocketConfiguration socketconfig) throws IOException {
		//do not need to keep reference to connection or variables
		//    they are going to be closed when the connection is closed

		if (rmioptions == null) {
			rmioptions = SakerRMIHelper.createBaseRMIOptions();
		}

		ClassLoaderResolver clresolver = rmioptions.getClassLoaderResolver();
		if (clresolver == null) {
			clresolver = new ClassLoaderResolverRegistry(createConnectionBaseClassLoaderResolver());
			rmioptions.classResolver(clresolver);
		}
		RMIConnection connection = rmioptions.connect(address, socketconfig);
		return connection;
	}
}
