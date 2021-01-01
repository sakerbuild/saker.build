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

import java.io.IOException;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Supplier;

import saker.build.file.content.ContentDatabaseImpl;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionContextImpl;
import saker.build.task.ForwardingTaskInvoker;
import saker.build.task.TaskInvocationManager.TaskInvocationContext;
import saker.build.task.TaskInvoker;
import saker.build.task.cluster.TaskInvokerFactory;
import saker.build.task.cluster.TaskInvokerFactoryRMIWrapper;
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
import saker.build.thirdparty.saker.util.function.LazySupplier;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.util.rmi.SakerRMIHelper;

class RemoteDaemonConnectionImpl implements RemoteDaemonConnection {

	protected static final class ConnectionImpl {
		protected final RMIConnection rmiConnection;
		protected final DaemonEnvironment environment;
		protected final Supplier<TaskInvokerFactory> clusterInvokerFactory;
		protected final ClassLoaderResolverRegistry connectionClassLoaderRegistry;

		public ConnectionImpl(RMIConnection rmiConnection, DaemonEnvironment environment, RMIVariables vars) {
			this.rmiConnection = rmiConnection;
			this.environment = environment;
			Supplier<DaemonAccess> accesssupplier = LazySupplier.of(() -> {
				return (DaemonAccess) vars
						.getRemoteContextVariable(LocalDaemonEnvironment.RMI_CONTEXT_VARIABLE_DAEMON_ACCESS);
			});
			this.clusterInvokerFactory = LazySupplier.of(() -> accesssupplier.get().getClusterTaskInvokerFactory());
			this.connectionClassLoaderRegistry = (ClassLoaderResolverRegistry) rmiConnection.getClassLoaderResolver();
		}
	}

	private static final AtomicReferenceFieldUpdater<RemoteDaemonConnectionImpl, ConnectionImpl> ARFU_connection = AtomicReferenceFieldUpdater
			.newUpdater(RemoteDaemonConnectionImpl.class, ConnectionImpl.class, "connection");

	private volatile ConnectionImpl connection;

	private final SocketAddress address;

	private Optional<Throwable> connectionErrorException = null;
	private final Collection<ConnectionIOErrorListener> errorListeners = new HashSet<>();

	RemoteDaemonConnectionImpl(SocketAddress address, RMIConnection rmiConnection, DaemonEnvironment remoteEnvironment,
			RMIVariables vars) {
		this.address = address;
		this.connection = new ConnectionImpl(rmiConnection, remoteEnvironment, vars);
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
	public TaskInvokerFactory getClusterTaskInvokerFactory() {
		ConnectionImpl connection = getConnection();
		TaskInvokerFactory invokerfactory = connection.clusterInvokerFactory.get();
		if (invokerfactory == null) {
			return null;
		}
		return new TaskInvokerFactoryImpl(connection, invokerfactory);
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

	@RMIWrap(TaskInvokerFactoryRMIWrapper.class)
	public static final class TaskInvokerFactoryImpl implements TaskInvokerFactory {
		private final ClassLoaderResolverRegistry connectionClassLoaderRegistry;
		private final TaskInvokerFactory invokerFactory;

		private TaskInvokerFactoryImpl(ConnectionImpl connection, TaskInvokerFactory invokerfactory) {
			connectionClassLoaderRegistry = connection.connectionClassLoaderRegistry;
			this.invokerFactory = invokerfactory;
		}

		public TaskInvokerFactoryImpl(ClassLoaderResolverRegistry connectionClassLoaderRegistry,
				TaskInvokerFactory invokerFactory) {
			this.connectionClassLoaderRegistry = connectionClassLoaderRegistry;
			this.invokerFactory = invokerFactory;
		}

		@Override
		public TaskInvoker createTaskInvoker(ExecutionContext executioncontext,
				TaskInvokerInformation invokerinformation) throws IOException, NullPointerException {
			Objects.requireNonNull(executioncontext, "execution context");
			if (RMIConnection.isRemoteObject(executioncontext)) {
				throw new RMICallForbiddenException("Cannot create task invoker for remote execution context.");
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
			TaskInvoker subjecttaskinvoker = invokerFactory.createTaskInvoker(executioncontext, invokerinformation);
			return new ForwardingTaskInvoker(subjecttaskinvoker) {
				@Override
				public void run(TaskInvocationContext context) throws Exception {
					try {
						connectionClassLoaderRegistry.register(resolverid, dbclresolver);
						super.run(context);
					} finally {
						connectionClassLoaderRegistry.unregister(resolverid, dbclresolver);
					}
				}
			};
		}

		@Override
		public UUID getEnvironmentIdentifier() {
			return invokerFactory.getEnvironmentIdentifier();
		}
	}

}
