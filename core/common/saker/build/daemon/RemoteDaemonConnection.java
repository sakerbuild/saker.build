package saker.build.daemon;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketAddress;

import javax.net.SocketFactory;

import saker.build.task.cluster.TaskInvokerFactory;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;
import saker.build.thirdparty.saker.rmi.connection.RMIConnection;
import saker.build.thirdparty.saker.rmi.connection.RMIOptions;
import saker.build.thirdparty.saker.rmi.connection.RMIVariables;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolver;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolverRegistry;
import saker.build.thirdparty.saker.util.classloader.SingleClassLoaderResolver;
import saker.build.thirdparty.saker.util.io.IOUtils;

public interface RemoteDaemonConnection extends Closeable {
	public interface ConnectionIOErrorListener {
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
		//do not need to keep reference to connection or variables
		//    they are going to be closed when the connection is closed

		if (rmioptions == null) {
			rmioptions = new RMIOptions();
		}

		ClassLoaderResolver clresolver = rmioptions.getClassLoaderResolver();
		if (clresolver == null) {
			clresolver = createConnectionBaseClassLoaderResolver();
		}
		ClassLoaderResolverRegistry clregistry = new ClassLoaderResolverRegistry(clresolver);
		rmioptions.classResolver(clregistry);
		RMIConnection connection = rmioptions.connect(socketfactory, address);
		RMIVariables vars = null;
		try {
			vars = connection.newVariables();
			DaemonEnvironment remoteenv = (DaemonEnvironment) vars
					.getRemoteContextVariable(LocalDaemonEnvironment.RMI_CONTEXT_VARIABLE_DAEMON_ENVIRONMENT_INSTANCE);
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

	public static SingleClassLoaderResolver createConnectionBaseClassLoaderResolver() {
		return new SingleClassLoaderResolver("base.classes", DaemonEnvironment.class.getClassLoader());
	}
}
