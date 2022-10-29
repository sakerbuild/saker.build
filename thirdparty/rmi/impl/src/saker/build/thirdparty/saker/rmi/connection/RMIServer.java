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
package saker.build.thirdparty.saker.rmi.connection;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.AbstractSelector;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import saker.build.thirdparty.saker.rmi.connection.RMIConnection.PendingStreamTracker;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.StreamPair;
import saker.build.thirdparty.saker.util.io.function.IOFunction;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils.ThreadWorkPool;

/**
 * Server class that is capable of accepting RMI connections on a given server socket.
 * <p>
 * This class can be used to open a server socket and listen for incoming connections on it. The connections will be
 * appropriately handled using an internal protocol defined by this and the {@link RMIConnection} class.
 * <p>
 * Users can extend this class in order to specialize how the accepted connections should be handled.
 * <p>
 * The {@link ServerSocketFactory} class can be used to customize the server socket creation. This server class doesn't
 * do any security checks whether the accepted connection should be allowed to use this server. Any security checks
 * should be implemented by the subclasses.
 * <p>
 * The class is single use, meaning that after it has been started, it cannot be reset or restarted.
 * <p>
 * To ensure forward compatibility, when an RMI connection is established between a server and a client, the actual
 * protocol version is negotiated between them. This is determined by taking the highest supported protocol version
 * number by both parties. Users should keep this in mind, as the RMI runtime evolves, there might be features that is
 * only available on one endpoint, therefore that feature will not be used during the connection.
 */
public class RMIServer implements AutoCloseable {
	private static final int DEFAULT_CONNECTION_TIMEOUT_MS = 2000;

	private static final int STATE_UNSTARTED = 0;
	private static final int STATE_RUNNING = 1;
	private static final int STATE_CLOSED = -1;

	private static final AtomicIntegerFieldUpdater<RMIServer> AIFU_state = AtomicIntegerFieldUpdater
			.newUpdater(RMIServer.class, "state");
	private static final AtomicReferenceFieldUpdater<RMIServer, ThreadWorkPool> ARFU_serverThreadWorkPool = AtomicReferenceFieldUpdater
			.newUpdater(RMIServer.class, ThreadWorkPool.class, "serverThreadWorkPool");

	private static final short CONNECTION_MAGIC_NUMBER = 0x7e86;

	private static final short COMMAND_NEW_CONNECTION = 1;
	private static final short COMMAND_NEW_CONNECTION_RESPONSE = 2;
	private static final short COMMAND_NEW_STREAM = 3;
	private static final short COMMAND_NEW_STREAM_RESPONSE = 4;
	private static final short COMMAND_CONNECTION_REQUEST_FAILED = 5;
	private static final short COMMAND_SHUTDOWN_SERVER = 6;
	private static final short COMMAND_SHUTDOWN_SERVER_RESPONSE = 7;
	private static final short COMMAND_PING = 8;
	private static final short COMMAND_PONG = 9;
	private static final short COMMAND_ERROR_NO_CONNECTION = 10;
	private static final short COMMAND_ERROR_INVALID_VERSION = 11;
	private static final short COMMAND_ERROR_VALIDATION_FAILED = 12;
	private static final short COMMAND_ERROR_SETUP_FAILED = 13;

	private final ServerSocket acceptorSocket;
	private final int port;

	/**
	 * Only add to this collection in a synchronized block on <code>this</code> while also checking {@link #state}.
	 */
	private final Collection<Socket> unhandledSockets = ConcurrentHashMap.newKeySet();
	/**
	 * Only add to this collection in a synchronized block on <code>this</code> while also checking {@link #state}.
	 */
	private final ConcurrentSkipListMap<UUID, WeakReference<RMIConnection>> connections = new ConcurrentSkipListMap<>();

	private volatile int state = STATE_UNSTARTED;
	private volatile ThreadWorkPool serverThreadWorkPool;

	private int connectionTimeout = DEFAULT_CONNECTION_TIMEOUT_MS;

	/**
	 * Creates a new server instance with the given arguments.
	 * 
	 * @param socketfactory
	 *            The server socket factory to use to create the server socket or <code>null</code> to use none.
	 * @param port
	 *            The port number to listen for connections, or 0 to automatically allocate.
	 * @param bindaddress
	 *            The local {@link InetAddress} the server will bind to.
	 * @throws IOException
	 *             In case of I/O error.
	 * @see ServerSocket#ServerSocket(int, int, InetAddress)
	 * @see ServerSocketFactory#createServerSocket(int, int, InetAddress)
	 */
	public RMIServer(ServerSocketFactory socketfactory, int port, InetAddress bindaddress) throws IOException {
		if (socketfactory == null) {
			this.acceptorSocket = new ServerSocket(port, 0, bindaddress);
		} else {
			this.acceptorSocket = socketfactory.createServerSocket(port, 0, bindaddress);
		}
		this.port = acceptorSocket.getLocalPort();
	}

	/**
	 * Creates a new server instance.
	 * <p>
	 * The default server socket factory, 0 port, and <code>null</code> bind address will be used.
	 * 
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public RMIServer() throws IOException {
		this(null, 0, null);
	}

	/**
	 * Returns the port number on which this socket is listening.
	 * <p>
	 * If this instance was constructed with 0 as port number, this will return the actual allocated port nonetheless.
	 * 
	 * @return The port number.
	 * @see ServerSocket#getLocalPort()
	 */
	public final int getPort() {
		return port;
	}

	/**
	 * Returns the address of the endpoint this server socket is bound to.
	 * 
	 * @return The socket address.
	 * @see ServerSocket#getLocalSocketAddress()
	 */
	public final SocketAddress getLocalSocketAddress() {
		return acceptorSocket.getLocalSocketAddress();
	}

	/**
	 * Starts the accepting of connections on a separate thread.
	 * <p>
	 * This method is the same as calling {@link #start(ThreadGroup)} with <code>null</code> thread group.
	 * 
	 * @throws IllegalStateException
	 *             If the RMI server was already started previously.
	 */
	public final void start() throws IllegalStateException {
		start((ThreadGroup) null);
	}

	/**
	 * Starts the accepting of connections on a separate thread.
	 * <p>
	 * This method will create a thread pool and start listening for connections in one of the threads. The thread pool
	 * will be used to handle the accepted connections. The threads in the thread pool are
	 * {@linkplain Thread#setDaemon(boolean) daemon threads} (including the one listening for connections), however
	 * users should not rely on this behaviour.
	 * <p>
	 * The argument thread group will be used as a parent thread group for the started threads in the thread pool.
	 * 
	 * @param threadpoolthreadgroup
	 *            The thread group for the thread pool or <code>null</code> to use the current one.
	 * @throws IllegalStateException
	 *             If the RMI server was already started previously.
	 */
	public final void start(ThreadGroup threadpoolthreadgroup) throws IllegalStateException {
		ThreadWorkPool tpool = startServerOperationStates(threadpoolthreadgroup);
		tpool.offer(() -> {
			RMIConnection.clearContextClassLoaderOfCurrentThread();
			acceptConnectionsImpl(runnable -> tpool.offer(runnable::run));
		});
	}

	/**
	 * Starts the accepting of connections on a given executor.
	 * <p>
	 * This method will start listening for connections using the argument executor. The handling of new connections
	 * will also use the given executor.
	 * 
	 * @param executor
	 *            The executor to accepts connections and handle new connections on.
	 * @throws NullPointerException
	 *             If the executor is <code>null</code>.
	 * @throws IllegalStateException
	 *             If the RMI server was already started previously.
	 * @since saker.rmi 0.8.3
	 */
	public final void start(Executor executor) throws NullPointerException, IllegalStateException {
		Objects.requireNonNull(executor, "executor");
		if (!AIFU_state.compareAndSet(this, STATE_UNSTARTED, STATE_RUNNING)) {
			throw new IllegalStateException("Server was already started or closed.");
		}
		executor.execute(() -> acceptConnectionsImpl(executor));
	}

	/**
	 * Starts the accepting of connections on this thread.
	 * <p>
	 * This method is the same as calling {@link #acceptConnections(ThreadGroup)} with <code>null</code> thread group.
	 * 
	 * @throws IllegalStateException
	 *             If the RMI server was already started previously.
	 */
	public final void acceptConnections() throws IllegalStateException {
		acceptConnections((ThreadGroup) null);
	}

	/**
	 * Starts the accepting of connections on this thread.
	 * <p>
	 * This method will execute the listening for connections on this thread, and therefore closing the RMI server will
	 * require external intervention.
	 * <p>
	 * This method will create a thread pool where the accepted connections will be handled. The argument thread group
	 * will be used as a parent thread group for the started threads in the thread pool. The threads in the thread pool
	 * are {@linkplain Thread#setDaemon(boolean) daemon threads}, however users should not rely on this behaviour.
	 * 
	 * @param threadpoolthreadgroup
	 *            The thread group for the thread pool or <code>null</code> to use the current one.
	 * @throws IllegalStateException
	 *             If the RMI server was already started previously.
	 */
	public final void acceptConnections(ThreadGroup threadpoolthreadgroup) throws IllegalStateException {
		ThreadWorkPool tpool = startServerOperationStates(threadpoolthreadgroup);
		acceptConnectionsImpl(runnable -> tpool.offer(runnable::run));
	}

	/**
	 * Starts the accepting of connections on this thread.
	 * <p>
	 * This method will execute the listening for connections on this thread, and therefore closing the RMI server will
	 * require external intervention.
	 * <p>
	 * The newly accepted connections will be handled on the argument {@link Executor}.
	 * 
	 * @param executor
	 *            The executor to handle new connections on.
	 * @throws NullPointerException
	 *             If the executor is <code>null</code>.
	 * @throws IllegalStateException
	 *             If the RMI server was already started previously.
	 * @since saker.rmi 0.8.3
	 */
	public final void acceptConnections(Executor executor) throws NullPointerException, IllegalStateException {
		Objects.requireNonNull(executor, "executor");
		if (!AIFU_state.compareAndSet(this, STATE_UNSTARTED, STATE_RUNNING)) {
			throw new IllegalStateException("Server was already started or closed.");
		}
		acceptConnectionsImpl(executor);
	}

	/**
	 * Sets the connection timeout for the accepted sockets.
	 * <p>
	 * This is the timeout in milliseconds for read operations on the accepted sockets. Negative value means that an
	 * implementation dependent default value is used.
	 * <p>
	 * Zero means infinite timeout, and also means that the {@link RMIServer} code won't call
	 * {@link Socket#setSoTimeout(int)} during initialization. In this case subclasses can set the timeout dynamically
	 * in {@link #validateSocket(Socket)}.
	 * 
	 * @param connectionTimeout
	 *            The connection read timeout in milliseconds.
	 * @since saker.rmi 0.8.2
	 */
	public final void setConnectionTimeout(int connectionTimeout) {
		if (connectionTimeout < 0) {
			this.connectionTimeout = DEFAULT_CONNECTION_TIMEOUT_MS;
		} else {
			this.connectionTimeout = connectionTimeout;
		}
	}

	/**
	 * Closes this RMI server and all associated RMI connections.
	 * <p>
	 * This method works the same way as {@link #close()}, but will wait for any concurrent requests to finish.
	 * 
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws InterruptedException
	 *             If the current thread was interrupted.
	 */
	public final void closeWait() throws IOException, InterruptedException {
		IOException exc = null;
		state = STATE_CLOSED;
		exc = IOUtils.closeExc(exc, acceptorSocket);
		Collection<Socket> socketstoclose;
		synchronized (this) {
			socketstoclose = ImmutableUtils.makeImmutableList(unhandledSockets);
			unhandledSockets.clear();
		}
		exc = IOUtils.closeExc(exc, socketstoclose);

		ThreadWorkPool tpool = serverThreadWorkPool;
		if (tpool != null) {
			tpool.exit();
		}
		try {
			closeImpl();
		} catch (IOException e) {
			exc = IOUtils.addExc(exc, e);
		}
		try {
			removeCloseWaitAllConnections();
		} catch (IOException | InterruptedException e) {
			//add previous exception as suppressed
			IOUtils.addExc(e, exc);
			throw e;
		}
		if (tpool != null) {
			tpool.closeInterruptible();
		}
		IOUtils.throwExc(exc);
	}

	/**
	 * Closes this RMI server and all associated RMI connections.
	 * <p>
	 * This method closes the server in an asynchronous way. No more connections will be accepted, and the underlying
	 * resources are closed.
	 * <p>
	 * The still executing requests are not waited, therefore it is possible that some requests are still executing
	 * after this method returns. To wait for the concurrent requests to finish, call {@link #closeWait()}.
	 * 
	 * @throws IOException
	 *             In case of I/O error.
	 */
	@Override
	public final void close() throws IOException {
		IOException exc = null;
		state = STATE_CLOSED;
		exc = IOUtils.closeExc(exc, acceptorSocket);
		Collection<Socket> socketstoclose;
		synchronized (this) {
			socketstoclose = ImmutableUtils.makeImmutableList(unhandledSockets);
			unhandledSockets.clear();
		}
		exc = IOUtils.closeExc(exc, socketstoclose);
		try {
			closeImpl();
		} catch (IOException e) {
			exc = IOUtils.addExc(exc, e);
		}

		ThreadWorkPool tpool = serverThreadWorkPool;
		if (tpool != null) {
			tpool.exit();
		}
		//don't remove the connections from the collection, as if somebody calls closeWait after this, then the connections need to be waited for
		for (WeakReference<RMIConnection> ref : connections.values()) {
			RMIConnection conn = ObjectUtils.getReference(ref);
			if (conn == null) {
				continue;
			}
			exc = IOUtils.closeExc(exc, conn);
		}
		IOUtils.throwExc(exc);
	}

	/**
	 * Sends a shutdown request to the RMI server running at the given address if any.
	 * <p>
	 * Works the same way a {@link #shutdownServer(SocketFactory, SocketAddress)} with <code>null</code> socket factory.
	 * 
	 * @param address
	 *            The address to shutdown the server at.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the address is <code>null</code>.
	 */
	public static void shutdownServer(SocketAddress address) throws IOException, NullPointerException {
		shutdownServer(null, address);
	}

	/**
	 * Sends a shutdown request to the RMI server running at the given address if any.
	 * <p>
	 * Works the same way as {@link #shutdownServer(SocketAddress, RMISocketConfiguration)} with the given socket
	 * factory and defaults.
	 * 
	 * @param socketfactory
	 *            The socket factory to use for connection, or <code>null</code> to use none.
	 * @param address
	 *            The address to shutdown the server at.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws RMIShutdownRequestDeniedException
	 *             If the shutdown request was denied by the remote endpoint.
	 * @throws NullPointerException
	 *             If the address is <code>null</code>.
	 */
	public static void shutdownServer(SocketFactory socketfactory, SocketAddress address)
			throws IOException, RMIShutdownRequestDeniedException, NullPointerException {
		shutdownServer(socketfactory, address, DEFAULT_CONNECTION_TIMEOUT_MS, false);
	}

	/**
	 * Sends a shutdown request to the RMI server running at the given address if any.
	 * <p>
	 * A connection will be established to the given address, and the {@link RMIServer} running on that endpoint will be
	 * asked to shut down. (I.e. close itself)
	 * <p>
	 * The server will call its implementation {@link #validateShutdownRequest(Socket)} method and will decide if it
	 * wants to satisfy the request. If the remote server is shutting down, this method will complete successfully.
	 * <p>
	 * If the connection failed to establish, an {@link IOException} will be thrown.
	 * <p>
	 * If the shutdown request is denied, an {@link RMIShutdownRequestDeniedException} will be thrown.
	 * 
	 * @param address
	 *            The address to shutdown the server at.
	 * @param socketconfig
	 *            The socket configuration to use.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws RMIShutdownRequestDeniedException
	 *             If the shutdown request was denied by the remote endpoint.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @since saker.rmi 0.8.2
	 */
	public static void shutdownServer(SocketAddress address, RMISocketConfiguration socketconfig)
			throws IOException, RMIShutdownRequestDeniedException, NullPointerException {
		Objects.requireNonNull(socketconfig, "socket config");
		int timeout = socketconfig.getConnectionTimeout();
		if (timeout < 0) {
			timeout = DEFAULT_CONNECTION_TIMEOUT_MS;
		}
		shutdownServer(socketconfig.getSocketFactory(), address, timeout, socketconfig.isConnectionInterruptible());
	}

	@SuppressWarnings("try") // interruptor is not used
	private static void shutdownServer(SocketFactory socketfactory, SocketAddress address, int connectiontimeoutms,
			boolean interruptible) throws SocketException, IOException {
		Objects.requireNonNull(address, "address");
		try (Socket s = socketfactory == null ? new Socket() : socketfactory.createSocket()) {
			try (ConnectionInterruptor interruptor = interruptible ? ConnectionInterruptor.create(s) : null) {
				s.setSoTimeout(connectiontimeoutms);
				s.connect(address, connectiontimeoutms);

				OutputStream socketos = s.getOutputStream();
				InputStream socketis = s.getInputStream();
				DataOutputStream dataos = new DataOutputStream(socketos);
				DataInputStream datais = new DataInputStream(socketis);

				dataos.writeShort(RMIServer.CONNECTION_MAGIC_NUMBER);
				dataos.writeShort(RMIConnection.PROTOCOL_VERSION_LATEST);
				dataos.writeShort(COMMAND_SHUTDOWN_SERVER);
				dataos.flush();
				short magic = datais.readShort();
				if (magic != RMIServer.CONNECTION_MAGIC_NUMBER) {
					throw new IOException(
							"Invalid magic: 0x" + Integer.toHexString(magic) + " when connecting to: " + address);
				}
				short remoteversion = datais.readShort();
				short useversion = remoteversion > RMIConnection.PROTOCOL_VERSION_LATEST
						? RMIConnection.PROTOCOL_VERSION_LATEST
						: remoteversion;
				if (useversion <= 0) {
					throw new IOException("Invalid version detected: 0x" + Integer.toHexString(useversion)
							+ " when connecting to: " + address);
				}
				short response = datais.readShort();
				if (response != COMMAND_SHUTDOWN_SERVER_RESPONSE) {
					throw new RMIShutdownRequestDeniedException(
							"Failed to shutdown server at " + address + " (response code: " + response + ")", address);
				}
			} catch (SocketException e) {
				if (interruptible && Thread.currentThread().isInterrupted()) {
					ClosedByInterruptException thrown = new ClosedByInterruptException();
					thrown.addSuppressed(e);
					throw thrown;
				}
				throw e;
			}
		}
	}

	/**
	 * Pings the RMI server at the given address if there is any.
	 * <p>
	 * Works the same way a {@link #pingServer(SocketFactory, SocketAddress)} with <code>null</code> socket factory.
	 * 
	 * @param address
	 *            The address to send the ping to.
	 * @return <code>true</code> if the server was successfully pinged.
	 * @throws NullPointerException
	 *             If the address is <code>null</code>.
	 */
	public static boolean pingServer(SocketAddress address) throws NullPointerException {
		return pingServer(null, address);
	}

	/**
	 * Pings the RMI server at the given address if there is any.
	 * <p>
	 * Works the same way as {@link #pingServer(SocketAddress, RMISocketConfiguration)} with the given socket factory
	 * and defaults.
	 * 
	 * @param socketfactory
	 *            The socket factory to use for connection, or <code>null</code> to use none.
	 * @param address
	 *            The address to send the ping to.
	 * @return <code>true</code> if the server at the given address responded to the ping successfully.
	 * @throws NullPointerException
	 *             If the address is <code>null</code>.
	 */
	public static boolean pingServer(SocketFactory socketfactory, SocketAddress address) throws NullPointerException {
		return pingServer(socketfactory, address, DEFAULT_CONNECTION_TIMEOUT_MS, false);
	}

	/**
	 * Pings the RMI server at the given address if there is any.
	 * <p>
	 * This method establishes a connection to the given address, and sends a ping request to the {@link RMIServer}
	 * running at that endpoint.
	 * <p>
	 * When the server receives the request, it will call its implementation {@link #validatePingRequest(Socket)} method
	 * to check if it is allowed to respond to this request.
	 * <p>
	 * This method can be used to determine if there is an {@link RMIServer} running at the given address. If this
	 * method returns <code>true</code>, that can signal (but not always) that clients can connect and use that server.
	 * <p>
	 * This method doesn't throw {@link IOException} or others in case of errors, but will just simply return
	 * <code>false</code>.
	 * 
	 * @param address
	 *            The address to send the ping to.
	 * @param socketconfig
	 *            The socket configuration to use.
	 * @return <code>true</code> if the server at the given address responded to the ping successfully.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @since saker.rmi 0.8.2
	 */
	public static boolean pingServer(SocketAddress address, RMISocketConfiguration socketconfig)
			throws NullPointerException {
		return pingServer(socketconfig.getSocketFactory(), address, DEFAULT_CONNECTION_TIMEOUT_MS, false);
	}

	@SuppressWarnings("try") // interruptor is not used
	private static boolean pingServer(SocketFactory socketfactory, SocketAddress address, int connectiontimeoutms,
			boolean interruptible) {
		Objects.requireNonNull(address, "address");
		try (Socket s = socketfactory == null ? new Socket() : socketfactory.createSocket();
				ConnectionInterruptor interruptor = interruptible ? ConnectionInterruptor.create(s) : null) {
			s.setSoTimeout(connectiontimeoutms);
			s.connect(address, connectiontimeoutms);

			OutputStream socketos = s.getOutputStream();
			InputStream socketis = s.getInputStream();
			DataOutputStream dataos = new DataOutputStream(socketos);
			DataInputStream datais = new DataInputStream(socketis);

			dataos.writeShort(RMIServer.CONNECTION_MAGIC_NUMBER);
			dataos.writeShort(RMIConnection.PROTOCOL_VERSION_LATEST);
			dataos.writeShort(COMMAND_PING);
			dataos.flush();
			short magic = datais.readShort();
			if (magic != RMIServer.CONNECTION_MAGIC_NUMBER) {
				return false;
			}
			short remoteversion = datais.readShort();
			short useversion = remoteversion > RMIConnection.PROTOCOL_VERSION_LATEST
					? RMIConnection.PROTOCOL_VERSION_LATEST
					: remoteversion;
			if (useversion <= 0) {
				return false;
			}
			short response = datais.readShort();
			return response == COMMAND_PONG;
		} catch (IOException e) {
		}
		return false;
	}

	/**
	 * Gets the {@link RMIOptions} that should be used for the newly accepted RMI connection.
	 * <p>
	 * This method is called by the server to determine the RMI options to use for the newly accepted connection.
	 * <p>
	 * If any exception is thrown by this method, the connection setup will be aborted, and it will be refused.
	 * <p>
	 * If implementations used an {@link SSLSocketFactory} or similar during instantiation of this server, they can use
	 * the argument socket to validate the identity of the client. Implementations will have a second chance for
	 * validating the socket in {@link #setupConnection(Socket, RMIConnection)}.
	 * <p>
	 * Implementations should <b>not</b> use the input and output streams of the accepted socket.
	 * 
	 * @param acceptedsocket
	 *            The accepted socket.
	 * @param protocolversion
	 *            The negotiated protocol version of the connection.
	 * @return The RMI options to use for the connection.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws RuntimeException
	 *             If the validation of the socket failed.
	 * @see RMIConnection#PROTOCOL_VERSION_LATEST
	 */
	@SuppressWarnings("static-method")
	protected RMIOptions getRMIOptionsForAcceptedConnection(Socket acceptedsocket, int protocolversion)
			throws IOException, RuntimeException {
		return new RMIOptions();
	}

	/**
	 * Sets up the accepted connection.
	 * <p>
	 * This method is called by the server when an accepted connection has been established, but before any streams are
	 * added to it.
	 * <p>
	 * If any exception is thrown by this method, the connection setup will be aborted, and it will be refused.
	 * <p>
	 * If implementations used an {@link SSLSocketFactory} or similar during instantiation of this server, they can use
	 * the argument socket to validate the identity of the client.
	 * <p>
	 * Implementations can use the {@link RMIConnection#putContextVariable(String, Object)} function of the connection,
	 * but should <b>not</b> start any RMI requests on it.
	 * <p>
	 * Implementations should <b>not</b> use the input and output streams of the accepted socket.
	 * <p>
	 * If this method returns successfully, the connections is deemed to be established.
	 * 
	 * @param acceptedsocket
	 *            The accepted socket.
	 * @param connection
	 *            The accepted connection.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws RuntimeException
	 *             If the validation of the socket failed.
	 */
	protected void setupConnection(Socket acceptedsocket, RMIConnection connection)
			throws IOException, RuntimeException {
	}

	/**
	 * Checks if the newly accepted socket can be used to add a new stream to the argument RMI connection.
	 * <p>
	 * This method is called by the server when a new stream is being added to a connection. This is after a socket
	 * connections is accepted, and before a new stream for it is constructed.
	 * <p>
	 * Implementations are presented an opportunity to disallow the use of the accepted socket for the specified
	 * connection. They should return <code>false</code> if the socket can't be used.
	 * <p>
	 * The default implementation returns <code>true</code>.
	 * 
	 * @param acceptedsocket
	 *            The accepted socket.
	 * @param connection
	 *            The connection to which the new stream is being added to.
	 * @return <code>true</code> if the socket can be used to access the specified connection.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	@SuppressWarnings("static-method")
	protected boolean validateConnectionStream(Socket acceptedsocket, RMIConnection connection) throws IOException {
		return true;
	}

	/**
	 * Checks if the RMI server can be shut down externally from the specified socket.
	 * <p>
	 * This method is called when a server accepts a socket connection that requests <code>this</code> RMI server to be
	 * shut down.
	 * <p>
	 * Implementations are presented an opportunity to disallow the shutdown request. They should return
	 * <code>false</code> if the RMI server should not be shut down.
	 * <p>
	 * This method should not throw any exceptions, but validation failures should be represented by returning
	 * <code>false</code>.
	 * 
	 * @param requestsocket
	 *            The socket which requests the shutdown.
	 * @return <code>true</code> if <code>this</code> RMI server should be shut down.
	 */
	@SuppressWarnings("static-method")
	protected boolean validateShutdownRequest(Socket requestsocket) {
		return true;
	}

	/**
	 * Checks if this server should respond to a ping request from the given socket successfully.
	 * <p>
	 * This method is called when a socket connection is accepted from a client and a ping request is being served.
	 * <p>
	 * It is recommended that this method does any validation in regards if the socket would be able to successfully
	 * initiate a connection to this server.
	 * <p>
	 * The default implementation returns <code>true</code>.
	 * <p>
	 * This method should not throw any exceptions, but validation failures should be represented by returning
	 * <code>false</code>.
	 * 
	 * @param requestsocket
	 *            The socket which initiated the ping request.
	 * @return <code>true</code> if the pining is successful.
	 */
	@SuppressWarnings("static-method")
	protected boolean validatePingRequest(Socket requestsocket) {
		return true;
	}

	/**
	 * Called in case an exception is encountered during the operation of the {@link RMIServer}.
	 * <p>
	 * The argument exception may be cause by IO errors, SSL exceptions, or by other reasons. Implementations can deal
	 * with the exception in any way fit.
	 * <p>
	 * If an exception is thrown from this method, its stacktrace will be printed to the standard error.
	 * <p>
	 * The default implementation prints the exception to the standard error.
	 * 
	 * @param socket
	 *            The socket to which the exception relates. May be <code>null</code> if the exception occurs when
	 *            accepting clients.
	 * @param e
	 *            The exception.
	 * @since saker.rmi 0.8.2
	 */
	@SuppressWarnings("static-method")
	protected void serverError(Socket socket, Throwable e) {
		e.printStackTrace();
	}

	/**
	 * Called right after a client connection has been accepted.
	 * <p>
	 * Implementations can use this method to validate or log information related to the accepted socket. In case of SSL
	 * connections, subclasses may perform validations.
	 * <p>
	 * Implementations should <b>not</b> use the input and output streams of the accepted socket.
	 * <p>
	 * If an exception is thrown from this method, the socket will be closed.
	 * <p>
	 * The default implementation does nothing.
	 * 
	 * @param accepted
	 *            The accepted connection.
	 * @throws IOException
	 *             In case of errors.
	 * @throws RuntimeException
	 *             In case of errors.
	 * @since saker.rmi 0.8.2
	 */
	protected void validateSocket(Socket accepted) throws IOException, RuntimeException {
	}

	/**
	 * Requests the subclasses to release its resources.
	 * <p>
	 * This method is called when the RMI server is being closed. This method is called after the underlying sockets
	 * have been closed, but before any RMI connections are closed.
	 * <p>
	 * Implementations shouldn't wait for any unrelated requests to finish in this method.
	 * 
	 * @throws IOException
	 *             In case of I/O error.
	 */
	protected void closeImpl() throws IOException {
	}

	static void initSocketOptions(Socket accepted) {
		//this method shouldn't throw an exception, as if there are any connectivity error, an exception
		//will be encountered down the line
		//if setting these options fail, we can actually continue initialization as they are not strictly required

		try {
			//the data packets are buffered in RMIStream, set the options to send them immediately
			accepted.setTcpNoDelay(true);
		} catch (Exception e) {
		}
		try {
			//keep the connection alive, even when there are no data sent for a period of time
			accepted.setKeepAlive(true);
		} catch (Exception e) {
		}
	}

	private static final byte[] NEW_CONNECTION_HELLO_BYTES;
	static {
		NEW_CONNECTION_HELLO_BYTES = new byte[3 * 2];
		SerialUtils.writeShortToBuffer(RMIServer.CONNECTION_MAGIC_NUMBER, NEW_CONNECTION_HELLO_BYTES, 0);
		SerialUtils.writeShortToBuffer(RMIConnection.PROTOCOL_VERSION_LATEST, NEW_CONNECTION_HELLO_BYTES, 2);
		SerialUtils.writeShortToBuffer(RMIServer.COMMAND_NEW_CONNECTION, NEW_CONNECTION_HELLO_BYTES, 4);
	}

	static RMIConnection newConnection(RMIOptions options, SocketFactory socketfactory, SocketAddress address)
			throws IOException {
		return newConnection(options, socketfactory, address, DEFAULT_CONNECTION_TIMEOUT_MS, false);
	}

	static RMIConnection newConnection(RMIOptions options, SocketAddress address, RMISocketConfiguration socketconfig)
			throws IOException {
		int timeout = socketconfig.getConnectionTimeout();
		if (timeout < 0) {
			timeout = DEFAULT_CONNECTION_TIMEOUT_MS;
		}
		return newConnection(options, socketconfig.getSocketFactory(), address, timeout,
				socketconfig.isConnectionInterruptible());
	}

	@SuppressWarnings("try") // interruptor is not used
	private static RMIConnection newConnection(RMIOptions options, SocketFactory socketfactory, SocketAddress address,
			int connectiontimeout, boolean interruptible) throws IOException {
		Socket sockclose = null;
		IOException exc = null;
		try {
			//eclipse warns that socket might be unclosed
			//but it will be closed
			@SuppressWarnings("resource")
			Socket s;
			if (socketfactory == null) {
				s = new Socket();
			} else {
				s = socketfactory.createSocket();
			}
			sockclose = s;
			long mostsig;
			long leastsig;
			short useversion;
			OutputStream sockout;
			InputStream sockin;

			try (ConnectionInterruptor interruptor = interruptible ? ConnectionInterruptor.create(s) : null) {
				RMIServer.initSocketOptions(s);

				s.setSoTimeout(connectiontimeout);
				s.connect(address, connectiontimeout);

				sockout = s.getOutputStream();
				sockin = s.getInputStream();

				DataInputStream datais = new DataInputStream(sockin);
				sockout.write(NEW_CONNECTION_HELLO_BYTES);
				short magic = datais.readShort();
				if (magic != RMIServer.CONNECTION_MAGIC_NUMBER) {
					if (!(s instanceof SSLSocket)) {
						throw new IOException("Invalid magic: 0x" + Integer.toHexString(magic) + " when connecting to: "
								+ address + " (attempting to connect to SSL socket?)");
					}
					throw new IOException(
							"Invalid magic: 0x" + Integer.toHexString(magic) + " when connecting to: " + address);
				}
				short remoteversion = datais.readShort();
				useversion = remoteversion > RMIConnection.PROTOCOL_VERSION_LATEST
						? RMIConnection.PROTOCOL_VERSION_LATEST
						: remoteversion;
				if (useversion <= 0) {
					//invalid version selected
					throw new IOException("Invalid version: 0x" + Integer.toHexString(useversion)
							+ " when connecting to: " + address);
				}
				short cmd = datais.readShort();
				if (cmd != RMIServer.COMMAND_NEW_CONNECTION_RESPONSE) {
					throw new IOException("Invalid response: " + cmd);
				}
				mostsig = datais.readLong();
				leastsig = datais.readLong();

				s.setSoTimeout(0);
			} catch (SocketException e) {
				if (interruptible && Thread.currentThread().isInterrupted()) {
					ClosedByInterruptException thrown = new ClosedByInterruptException();
					thrown.addSuppressed(e);
					throw thrown;
				}
				throw e;
			}

			UUID uuid = new UUID(mostsig, leastsig);
			sockclose = null;
			StreamConnector streamconnector = new StreamConnector(useversion, uuid, socketfactory, address,
					connectiontimeout);
			return new RMIConnection(options, new StreamPair(sockin, sockout), useversion, streamconnector);
		} catch (IOException e) {
			exc = e;
		} finally {
			exc = IOUtils.closeExc(exc, sockclose);
		}
		throw exc;
	}

	@SuppressWarnings("try") // interruptor is not used
	private void acceptConnectionsImpl(Executor executor) {
		try (ServerSocket accsocket = this.acceptorSocket;
				ConnectionInterruptor interruptor = ConnectionInterruptor.create(accsocket)) {
			while (state == STATE_RUNNING) {
				Socket accepted = accsocket.accept();

				//use flag so we don't close the socket inside the synchronized block
				boolean abort = false;
				synchronized (this) {
					if (state != STATE_RUNNING) {
						abort = true;
					} else {
						unhandledSockets.add(accepted);
					}
				}
				if (abort) {
					IOUtils.close(accepted);
					break;
				}
				executor.execute(() -> handleAcceptedConnection(accepted));
			}
		} catch (IOException e) {
			//we accept an IOException if the server has been closed
			//as closing the socket will cause accept() to throw
			if (state == STATE_RUNNING) {
				try {
					serverError(null, e);
				} catch (Throwable e2) {
					//add the original exception as suppressed for printing
					e2.addSuppressed(e);
					e2.printStackTrace();
				}
			}
		}
	}

	private void handleAcceptedConnection(final Socket accepted) {
		RMIConnection.clearContextClassLoaderOfCurrentThread();

		Socket socketclose = accepted;
		WeakReference<RMIConnection> connref = null;
		UUID connuuidtoremove = null;
		Throwable exception = null;
		try {
			if (state != STATE_RUNNING) {
				return;
			}
			validateSocket(accepted);
			initSocketOptions(accepted);

			if (connectionTimeout > 0) {
				accepted.setSoTimeout(connectionTimeout);
			}
			InputStream socketis = accepted.getInputStream();
			OutputStream socketos = accepted.getOutputStream();

			DataInputStream datais = new DataInputStream(socketis);
			DataOutputStream dataos = new DataOutputStream(socketos);
			dataos.writeShort(CONNECTION_MAGIC_NUMBER);
			dataos.writeShort(RMIConnection.PROTOCOL_VERSION_LATEST);
			dataos.flush();
			short magic = datais.readShort();
			if (magic != CONNECTION_MAGIC_NUMBER) {
				return;
			}
			short remoteversion = datais.readShort();
			short useversion = remoteversion > RMIConnection.PROTOCOL_VERSION_LATEST
					? RMIConnection.PROTOCOL_VERSION_LATEST
					: remoteversion;
			if (useversion < RMIConnection.PROTOCOL_VERSION_LATEST) {
				//invalid version selected
				return;
			}

			short cmd = datais.readShort();
			switch (cmd) {
				case COMMAND_NEW_CONNECTION: {
					connuuidtoremove = UUID.randomUUID();

					RMIOptions options;
					try {
						options = getRMIOptionsForAcceptedConnection(accepted, useversion);
					} catch (IOException | RuntimeException e) {
						dataos.writeShort(COMMAND_ERROR_SETUP_FAILED);
						dataos.flush();
						break;
					}
					RMIConnection connection = new RMIConnection(options, useversion);
					try {
						setupConnection(accepted, connection);
					} catch (Exception e) {
						try {
							dataos.writeShort(COMMAND_ERROR_SETUP_FAILED);
							dataos.flush();
						} catch (Exception e2) {
							e.addSuppressed(e2);
						}
						try {
							connection.close();
						} catch (Exception e2) {
							e.addSuppressed(e2);
						}
						throw e;
					}

					RMIStream stream = new RMIStream(connection, socketis, socketos);

					connref = new WeakReference<>(connection);

					boolean abort = false;
					synchronized (this) {
						if (state != STATE_RUNNING) {
							abort = true;
						} else {
							connections.put(connuuidtoremove, connref);
						}
					}
					if (abort) {
						IOUtils.close(stream, connection);
						return;
					}

					dataos.writeShort(COMMAND_NEW_CONNECTION_RESPONSE);
					dataos.writeLong(connuuidtoremove.getMostSignificantBits());
					dataos.writeLong(connuuidtoremove.getLeastSignificantBits());
					dataos.flush();
					accepted.setSoTimeout(0);
					if (state != STATE_RUNNING) {
						IOUtils.close(stream, connection);
						return;
					}
					socketclose = null;
					connuuidtoremove = null;

					connection.finishNewConnectionSetup(stream);
					break;
				}
				case COMMAND_NEW_STREAM: {
					long msb = datais.readLong();
					long lsb = datais.readLong();
					UUID uuid = new UUID(msb, lsb);
					connref = connections.get(uuid);
					RMIConnection connection = ObjectUtils.getReference(connref);
					if (connection == null) {
						connections.remove(uuid, connref);
						dataos.writeShort(COMMAND_ERROR_NO_CONNECTION);
						dataos.flush();
						break;
					}
					if (useversion != connection.getProtocolVersion()) {
						//the stream should have the same protocol version as the associated connection
						dataos.writeShort(COMMAND_ERROR_INVALID_VERSION);
						dataos.flush();
						break;
					}
					if (!validateConnectionStream(accepted, connection)) {
						dataos.writeShort(COMMAND_ERROR_VALIDATION_FAILED);
						dataos.flush();
						break;
					}
					dataos.writeShort(COMMAND_NEW_STREAM_RESPONSE);
					dataos.flush();

					accepted.setSoTimeout(0);
					connection.addStream(new RMIStream(connection, socketis, socketos));
					socketclose = null;
					break;
				}
				case COMMAND_SHUTDOWN_SERVER: {
					if (!validateShutdownRequest(accepted)) {
						dataos.writeShort(COMMAND_ERROR_VALIDATION_FAILED);
						dataos.flush();
						break;
					}
					dataos.writeShort(COMMAND_SHUTDOWN_SERVER_RESPONSE);
					dataos.flush();
					close();
					break;
				}
				case COMMAND_PING: {
					if (!validatePingRequest(accepted)) {
						dataos.writeShort(COMMAND_ERROR_VALIDATION_FAILED);
						dataos.flush();
						break;
					}
					dataos.writeShort(COMMAND_PONG);
					dataos.flush();
					break;
				}
				default: {
					throw new IOException("Unknown connection command: " + cmd);
				}
			}
		} catch (IOException e) {
			//includes SSLExceptions
			// communication error
			exception = e;
		} catch (Exception e) {
			//some RMI library error?
			exception = e;
		} finally {
			unhandledSockets.remove(accepted);
			if (socketclose != null) {
				exception = IOUtils.addExc(exception, IOUtils.closeExc(socketclose));
			}
			if (connuuidtoremove != null) {
				connections.remove(connuuidtoremove, connref);
			}
			if (exception != null) {
				try {
					serverError(accepted, exception);
				} catch (Throwable e2) {
					e2.addSuppressed(exception);
					e2.printStackTrace();
				}
			}
		}
	}

	private void removeCloseAllConnections() throws IOException {
		IOException exc = null;
		while (true) {
			Entry<UUID, WeakReference<RMIConnection>> entry = connections.pollFirstEntry();
			if (entry == null) {
				break;
			}
			WeakReference<RMIConnection> ref = entry.getValue();
			exc = IOUtils.closeExc(exc, ref.get());
		}
		IOUtils.throwExc(exc);
	}

	private void removeCloseWaitAllConnections() throws InterruptedException, IOException {
		while (true) {
			Entry<UUID, WeakReference<RMIConnection>> entry = connections.pollFirstEntry();
			if (entry == null) {
				break;
			}
			WeakReference<RMIConnection> ref = entry.getValue();
			RMIConnection conn = ref.get();
			if (conn != null) {
				try {
					conn.closeWait();
				} catch (IOException | InterruptedException e) {
					//put it back so it is closed if the server is closed again
					connections.putIfAbsent(entry.getKey(), ref);
					throw e;
				}
			}
		}
	}

	private ThreadWorkPool startServerOperationStates(ThreadGroup taskpoolthreadgroup) {
		synchronized (this) { // this is in a synchronized block due to possible concurrent closing in close()
			if (!AIFU_state.compareAndSet(this, STATE_UNSTARTED, STATE_RUNNING)) {
				throw new IllegalStateException("Server was already started or closed.");
			}
			ThreadWorkPool tpool = ThreadUtils.newDynamicWorkPool(taskpoolthreadgroup, "RMI-server-");
			if (!ARFU_serverThreadWorkPool.compareAndSet(this, null, tpool)) {
				throw new AssertionError("RMI server task pool was already set.");
			}
			return tpool;
		}
	}

	private static final class StreamConnector implements IOFunction<PendingStreamTracker, StreamPair> {
		private final short useVersion;
		private final UUID uuid;
		private final SocketFactory socketFactory;
		private final SocketAddress address;

		private final int handshakeTimeout;

		private StreamConnector(short useversion, UUID uuid, SocketFactory socketfactory, SocketAddress address,
				int handshakeTimeout) {
			this.useVersion = useversion;
			this.uuid = uuid;
			this.socketFactory = socketfactory;
			this.address = address;
			this.handshakeTimeout = handshakeTimeout;
		}

		@Override
		public StreamPair apply(PendingStreamTracker closer) throws IOException {
			Socket ssockclose = null;
			Throwable exc = null;
			try {
				Socket sock;
				if (socketFactory == null) {
					sock = new Socket();
				} else {
					sock = socketFactory.createSocket();
				}
				ssockclose = sock;
				boolean added = closer.add(sock);
				if (!added) {
					//not adding any more streams/sockets, return null
					return null;
				}
				OutputStream ssockout;
				InputStream ssockin;
				try {
					//make this interruptible
					try (ConnectionInterruptor interruptor = ConnectionInterruptor.create(sock)) {
						try {
							RMIServer.initSocketOptions(sock);

							sock.setSoTimeout(handshakeTimeout);
							sock.connect(address, handshakeTimeout);

							ssockout = sock.getOutputStream();
							ssockin = sock.getInputStream();
							DataOutputStream sdataos = new DataOutputStream(ssockout);
							DataInputStream sdatais = new DataInputStream(ssockin);
							sdataos.writeShort(RMIServer.CONNECTION_MAGIC_NUMBER);
							sdataos.writeShort(useVersion);
							sdataos.writeShort(RMIServer.COMMAND_NEW_STREAM);
							sdataos.writeLong(uuid.getMostSignificantBits());
							sdataos.writeLong(uuid.getLeastSignificantBits());
							sdataos.flush();

							short smagic = sdatais.readShort();
							if (smagic != RMIServer.CONNECTION_MAGIC_NUMBER) {
								if (!(sock instanceof SSLSocket)) {
									throw new IOException(
											"Invalid magic: 0x" + Integer.toHexString(smagic) + " when connecting to: "
													+ address + " (attempting to connect to SSL socket?)");
								}
								throw new IOException("Invalid magic: 0x" + Integer.toHexString(smagic)
										+ " when connecting to: " + address);
							}
							short sremoteversion = sdatais.readShort();
							short suseversion = sremoteversion > RMIConnection.PROTOCOL_VERSION_LATEST
									? RMIConnection.PROTOCOL_VERSION_LATEST
									: sremoteversion;
							if (suseversion != useVersion) {
								throw new IOException("Invalid version detected: 0x" + Integer.toHexString(suseversion)
										+ " when connecting to: " + address);
							}
							short response = sdatais.readShort();
							if (response != RMIServer.COMMAND_NEW_STREAM_RESPONSE) {
								throw new IOException("Failed to create new stream when connecting to: " + address
										+ " (Error code: " + response + ")");
							}
						} catch (Throwable e) {
							IOException cexc = interruptor.closeException;
							if (cexc != null) {
								e.addSuppressed(cexc);
							}
							throw e;
						}
					}
					sock.setSoTimeout(0);
					ssockclose = null;
				} finally {
					//always remove the socket from the closer after it was successfully added
					//after we return the streams from the socket, it is no longer our responsibility to close it
					closer.remove(sock);
				}
				return new StreamPair(ssockin, ssockout);
			} catch (InterruptedIOException e) {
				exc = e;

				//reinterrupt so the interruption flag for the thread is not lost
				Thread.currentThread().interrupt();
				throw e;
			} catch (Throwable e) {
				exc = e;
				//failed to connect, or other error
				throw e;
			} finally {
				IOException sockexc = IOUtils.closeExc(ssockclose);
				//throw or add as suppressed exception based on if we're throwing right now
				if (sockexc != null) {
					if (exc == null) {
						throw sockexc;
					}
					exc.addSuppressed(sockexc);
				}
			}
		}
	}

	/**
	 * Socket closer in case of interruption.
	 * <p>
	 * Normally, interrupting a thread that is waiting on a connecting socket won't cause the connecting to get aborted.
	 * We use this selector to get notified about the interruption, and close the associated socket.
	 * <p>
	 * Some related info about this: https://github.com/NWilson/javaInterruptHook
	 */
	private static class ConnectionInterruptor extends AbstractSelector {
		private static final AtomicReferenceFieldUpdater<RMIServer.ConnectionInterruptor, IOException> ARFU_closeException = AtomicReferenceFieldUpdater
				.newUpdater(RMIServer.ConnectionInterruptor.class, IOException.class, "closeException");

		protected final Closeable socket;
		protected volatile IOException closeException;

		private ConnectionInterruptor(Closeable socket) {
			super(null);
			this.socket = socket;
		}

		protected static ConnectionInterruptor create(Closeable socket) {
			ConnectionInterruptor result = new ConnectionInterruptor(socket);
			result.begin();
			return result;
		}

		@Override
		protected void implCloseSelector() {
			end();
		}

		@Override
		protected SelectionKey register(AbstractSelectableChannel ch, int ops, Object att) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Set<SelectionKey> keys() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Set<SelectionKey> selectedKeys() {
			throw new UnsupportedOperationException();
		}

		@Override
		public int selectNow() throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public int select(long timeout) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public int select() throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public Selector wakeup() {
			try {
				socket.close();
			} catch (IOException e) {
				while (true) {
					IOException ce = this.closeException;
					if (ce != null) {
						ce.addSuppressed(e);
						break;
					}
					if (ARFU_closeException.compareAndSet(this, null, e)) {
						break;
					}
				}
			}
			return this;
		}

	}
}
