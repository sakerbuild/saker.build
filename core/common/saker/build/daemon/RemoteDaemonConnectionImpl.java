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

import java.net.SocketAddress;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import saker.build.file.content.ContentDatabaseImpl;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionContextImpl;
import saker.build.task.TaskInvocationManager.TaskInvocationContext;
import saker.build.task.cluster.TaskInvoker;
import saker.build.task.cluster.TaskInvokerRMIWrapper;
import saker.build.task.cluster.TaskInvokerInformation;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.connection.RMIConnection;
import saker.build.thirdparty.saker.rmi.connection.RMIVariables;
import saker.build.thirdparty.saker.rmi.exception.RMICallForbiddenException;
import saker.build.thirdparty.saker.util.ArrayUtils;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolver;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolverRegistry;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.util.rmi.SakerRMIHelper;

class RemoteDaemonConnectionImpl implements RemoteDaemonConnection {

	protected static final class ConnectionImpl {
		protected final RMIConnection rmiConnection;
		protected final DaemonEnvironment environment;
		protected final boolean clusterAvailable;
		protected final ClassLoaderResolverRegistry connectionClassLoaderRegistry;
		protected final UUID environmentIdentifier;

		public ConnectionImpl(RMIConnection rmiConnection, DaemonAccess access, DaemonEnvironment environment) {
			this.rmiConnection = rmiConnection;
			this.environment = environment;
			this.clusterAvailable = access.isClusterAvailable();
			this.environmentIdentifier = access.getEnvironmentIdentifier();
			this.connectionClassLoaderRegistry = (ClassLoaderResolverRegistry) rmiConnection.getClassLoaderResolver();
		}
	}

	private static final AtomicReferenceFieldUpdater<RemoteDaemonConnectionImpl, ConnectionImpl> ARFU_connection = AtomicReferenceFieldUpdater
			.newUpdater(RemoteDaemonConnectionImpl.class, ConnectionImpl.class, "connection");

	private volatile ConnectionImpl connection;

	private final SocketAddress address;

	private Optional<Throwable> connectionErrorException = null;
	private final Collection<ConnectionIOErrorListener> errorListeners = new HashSet<>();

	RemoteDaemonConnectionImpl(SocketAddress address, RMIConnection rmiConnection, DaemonAccess access,
			DaemonEnvironment remoteEnvironment) {
		this.address = address;
		this.connection = new ConnectionImpl(rmiConnection, access, remoteEnvironment);
		rmiConnection.addErrorListener(this::onConnectionIOError);
		rmiConnection.addCloseListener(this::onConnectionClose);
	}

	@Override
	public DaemonEnvironment getDaemonEnvironment() {
		return getConnection().environment;
	}

	@Override
	public void addConnectionIOErrorListener(ConnectionIOErrorListener listener) {
		Optional<Throwable> excoptional;
		synchronized (errorListeners) {
			excoptional = connectionErrorException;
			if (excoptional == null) {
				errorListeners.add(listener);
				return;
			}
		}
		//call this outside of the lock
		listener.onConnectionError(excoptional.orElse(null));
	}

	private void onConnectionClose() {
		onConnectionIOError(null);
	}

	private void onConnectionIOError(Throwable exc) {
		Collection<ConnectionIOErrorListener> copy;
		synchronized (errorListeners) {
			if (connectionErrorException == null || !connectionErrorException.isPresent()) {
				connectionErrorException = Optional.ofNullable(exc);
			} else {
				Throwable heldexc = connectionErrorException.get();
				if (exc != null) {
					heldexc.addSuppressed(exc);
				}
				exc = heldexc;
			}
			if (errorListeners.isEmpty()) {
				return;
			}
			copy = ImmutableUtils.makeImmutableList(errorListeners);
			errorListeners.clear();
		}
		Throwable[] listenerexceptions = ObjectUtils.EMPTY_THROWABLE_ARRAY;
		for (ConnectionIOErrorListener l : copy) {
			try {
				l.onConnectionError(exc);
			} catch (Exception e) {
				listenerexceptions = ArrayUtils.appended(listenerexceptions, e);
			}
		}
		if (listenerexceptions.length > 0) {
			RuntimeException ex = new RuntimeException("Connection error listeners caused an exception.");
			for (Throwable t : listenerexceptions) {
				ex.addSuppressed(t);
			}
			throw ex;
		}
	}

	@Override
	public boolean isConnected() {
		ConnectionImpl conn = connection;
		return conn != null && conn.rmiConnection.isConnected();
	}

	protected ConnectionImpl getConnection() {
		ConnectionImpl conn = this.connection;
		if (conn == null) {
			throw new IllegalStateException("Not connected.");
		}
		return connection;
	}

	@Override
	public TaskInvoker getClusterTaskInvoker() {
		ConnectionImpl connection = getConnection();
		if (!connection.clusterAvailable) {
			return null;
		}
		return new TaskInvokerImpl(connection.rmiConnection, connection.environmentIdentifier);
	}

	@Override
	public SocketAddress getAddress() {
		return address;
	}

	@Override
	public void close() {
		ConnectionImpl conn = ARFU_connection.getAndSet(this, null);
		if (conn != null) {
			IOUtils.closePrint(conn.rmiConnection);
			SakerRMIHelper.dumpRMIStatistics(conn.rmiConnection);
		}
	}

	@RMIWrap(TaskInvokerRMIWrapper.class)
	public static final class TaskInvokerImpl implements TaskInvoker {
		private final RMIConnection rmiConnection;
		private final ClassLoaderResolverRegistry connectionClassLoaderRegistry;
		private final UUID environmentIdentifier;

		public TaskInvokerImpl(RMIConnection rmiConnection, UUID environmentIdentifier) {
			this.rmiConnection = rmiConnection;
			this.environmentIdentifier = environmentIdentifier;
			this.connectionClassLoaderRegistry = (ClassLoaderResolverRegistry) rmiConnection.getClassLoaderResolver();
		}

		@Override
		public void run(ExecutionContext executioncontext, TaskInvokerInformation invokerinformation,
				TaskInvocationContext context) throws Exception {

			Objects.requireNonNull(executioncontext, "execution context");
			if (RMIConnection.isRemoteObject(executioncontext)) {
				throw new RMICallForbiddenException("Cannot create task invoker for remote execution context.");
			}
			if (RMIConnection.isRemoteObject(context)) {
				throw new RMICallForbiddenException("Cannot create task invoker for remote invocation context.");
			}
			if (!(executioncontext instanceof ExecutionContextImpl)) {
				throw new IllegalArgumentException(
						"Invalid execution context implementation class: " + executioncontext.getClass().getName());
			}
			ClassLoaderResolver dbclresolver = ((ContentDatabaseImpl) ((ExecutionContextImpl) executioncontext)
					.getContentDatabase()).getClassLoaderResolver();
			String resolverid = LocalDaemonEnvironment
					.createClusterTaskInvokerRMIRegistryClassResolverId(executioncontext.getPathConfiguration());

			//create the subject task invoker before modifying the registry
			//    as a single class can be available through multiple registries.
			//    if the registry is modified before creating the task invoker
			//    then the passed classes through execution context might not be found on the remote endpoint
			try {
				connectionClassLoaderRegistry.register(resolverid, dbclresolver);
				RMIVariables vars = rmiConnection.newVariables();
				try {
					LocalDaemonEnvironment.runClusterInvokerContextVariable(vars, executioncontext, invokerinformation,
							context);
				} finally {
					vars.close();
				}
				//wait for the closure of the variables, so all of the build related calls are done
				vars.waitClosure();
			} finally {
				connectionClassLoaderRegistry.unregister(resolverid, dbclresolver);
			}
		}

		@Override
		public UUID getEnvironmentIdentifier() {
			return environmentIdentifier;
		}
	}

}
