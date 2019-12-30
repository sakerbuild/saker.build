package saker.build.daemon;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import saker.build.file.content.ContentDatabaseImpl;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionContextImpl;
import saker.build.task.ForwardingTaskInvoker;
import saker.build.task.TaskInvocationManager.TaskInvocationContext;
import saker.build.task.TaskInvoker;
import saker.build.task.cluster.TaskInvokerFactory;
import saker.build.task.cluster.TaskInvokerInformation;
import saker.build.thirdparty.saker.rmi.connection.RMIConnection;
import saker.build.thirdparty.saker.rmi.connection.RMIVariables;
import saker.build.thirdparty.saker.rmi.exception.RMICallForbiddenException;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolver;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolverRegistry;
import saker.build.thirdparty.saker.util.function.LazySupplier;
import saker.build.thirdparty.saker.util.io.IOUtils;

class RemoteDaemonConnectionImpl implements RemoteDaemonConnection {
	protected static final class ConnectionImpl {
		protected final RMIConnection rmiConnection;
		protected final DaemonEnvironment environment;
		protected final LazySupplier<TaskInvokerFactory> clusterInvokerFactory;
		protected final ClassLoaderResolverRegistry connectionClassLoaderRegistry;

		public ConnectionImpl(RMIConnection rmiConnection, DaemonEnvironment environment, RMIVariables vars) {
			this.rmiConnection = rmiConnection;
			this.environment = environment;
			this.clusterInvokerFactory = LazySupplier.of(() -> {
				return (TaskInvokerFactory) vars.getRemoteContextVariable(
						LocalDaemonEnvironment.RMI_CONTEXT_VARIABLE_DAEMON_CLUSTER_INVOKER_FACTORY);
			});
			this.connectionClassLoaderRegistry = (ClassLoaderResolverRegistry) rmiConnection.getClassLoaderResolver();
		}
	}

	private static final AtomicReferenceFieldUpdater<RemoteDaemonConnectionImpl, ConnectionImpl> ARFU_connection = AtomicReferenceFieldUpdater
			.newUpdater(RemoteDaemonConnectionImpl.class, ConnectionImpl.class, "connection");

	private volatile ConnectionImpl connection;

	private final SocketAddress address;

	private Throwable connectionErrorException = null;
	private final Collection<ConnectionIOErrorListener> errorListeners = new HashSet<>();

	RemoteDaemonConnectionImpl(SocketAddress address, RMIConnection rmiConnection, DaemonEnvironment remoteEnvironment,
			RMIVariables vars) {
		this.address = address;
		this.connection = new ConnectionImpl(rmiConnection, remoteEnvironment, vars);
		rmiConnection.addErrorListener(this::onConnectionIOError);
	}

	@Override
	public DaemonEnvironment getDaemonEnvironment() {
		return getConnection().environment;
	}

	@Override
	public void addConnectionIOErrorListener(ConnectionIOErrorListener listener) {
		synchronized (errorListeners) {
			if (connectionErrorException == null) {
				errorListeners.add(listener);
				return;
			}
		}
		//call this outside of the lock
		listener.onConnectionError(connectionErrorException);
	}

	private void onConnectionIOError(Throwable exc) {
		Collection<ConnectionIOErrorListener> copy;
		synchronized (errorListeners) {
			if (connectionErrorException == null) {
				connectionErrorException = exc;
			} else {
				connectionErrorException.addSuppressed(exc);
			}
			if (errorListeners.isEmpty()) {
				return;
			}
			copy = new ArrayList<>(errorListeners);
			errorListeners.clear();
		}
		for (ConnectionIOErrorListener l : copy) {
			l.onConnectionError(exc);
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
		return new TaskInvokerFactory() {

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
				TaskInvoker subjecttaskinvoker = invokerfactory.createTaskInvoker(executioncontext,
						invokerinformation);
				return new ForwardingTaskInvoker(subjecttaskinvoker) {
					@Override
					public void run(TaskInvocationContext context) throws InterruptedException {
						try {
							connection.connectionClassLoaderRegistry.register(resolverid, dbclresolver);
							super.run(context);
						} finally {
							connection.connectionClassLoaderRegistry.unregister(resolverid, dbclresolver);
						}
					}
				};
			}

			@Override
			public UUID getEnvironmentIdentifier() {
				return invokerfactory.getEnvironmentIdentifier();
			}
		};
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
		}
	}

}
