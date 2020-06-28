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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
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

	private final Collection<Socket> unhandledSockets = ConcurrentHashMap.newKeySet();
	private final ConcurrentSkipListMap<UUID, WeakReference<RMIConnection>> connections = new ConcurrentSkipListMap<>();

	private volatile int state = STATE_UNSTARTED;
	private volatile ThreadWorkPool serverThreadWorkPool;

	/**
	 * Creates a new server instance with the given arguments.
	 * 
	 * @param socketfactory
	 *            The server socket factory to use to create the server socket or <code>null</code> to use none.
	 * @param port
	 *            The port number to listen for connections, or 0 to automatically allocate.
	 * @param bindaddress
	 *            The local InetAddress the server will bind to.
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
	 */
	public final void start() {
		start(null);
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
	 */
	public final void start(ThreadGroup threadpoolthreadgroup) {
		ThreadWorkPool tpool = startServerOperationStates(threadpoolthreadgroup);
		tpool.offer(() -> {
			Thread.currentThread().setContextClassLoader(null);
			acceptConnectionsImpl(tpool);
		});
	}

	/**
	 * Starts the accepting of connections on this thread.
	 * <p>
	 * This method is the same as calling {@link #acceptConnections(ThreadGroup)} with <code>null</code> thread group.
	 */
	public final void acceptConnections() {
		acceptConnections(null);
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
	 */
	public final void acceptConnections(ThreadGroup threadpoolthreadgroup) {
		ThreadWorkPool tpool = startServerOperationStates(threadpoolthreadgroup);
		acceptConnectionsImpl(tpool);
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
		synchronized (this) {
			state = STATE_CLOSED;
			IOUtils.closePrint(acceptorSocket);
			IOUtils.closePrint(unhandledSockets);
			unhandledSockets.clear();
		}

		ThreadWorkPool tpool = serverThreadWorkPool;
		if (tpool != null) {
			tpool.exit();
		}
		closeImpl();
		if (tpool != null) {
			removeCloseWaitAllConnections();
			tpool.closeInterruptible();
		}
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
		synchronized (this) {
			state = STATE_CLOSED;
			IOUtils.closePrint(acceptorSocket);
			IOUtils.closePrint(unhandledSockets);
			unhandledSockets.clear();
		}

		closeImpl();

		ThreadWorkPool tpool = serverThreadWorkPool;
		if (tpool != null) {
			removeCloseAllConnections();
			tpool.exit();
		}
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
		Objects.requireNonNull(address, "address");
		int connectiontimeoutms = DEFAULT_CONNECTION_TIMEOUT_MS;
		try (Socket s = socketfactory == null ? SocketChannel.open().socket() : socketfactory.createSocket()) {
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
				throw new IOException("Invalid magic: 0x" + Integer.toHexString(magic));
			}
			short remoteversion = datais.readShort();
			short useversion = remoteversion > RMIConnection.PROTOCOL_VERSION_LATEST
					? RMIConnection.PROTOCOL_VERSION_LATEST
					: remoteversion;
			if (useversion <= 0) {
				throw new IOException("Invalid version detected: 0x" + Integer.toHexString(useversion));
			}
			short response = datais.readShort();
			if (response != COMMAND_SHUTDOWN_SERVER_RESPONSE) {
				throw new RMIShutdownRequestDeniedException(
						"Failed to shutdown server (response code: " + response + ")", address);
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
	 * @param socketfactory
	 *            The socket factory to use for connection, or <code>null</code> to use none.
	 * @param address
	 *            The address to send the ping to.
	 * @return <code>true</code> if the server at the given address responded to the ping successfully.
	 * @throws NullPointerException
	 *             If the address is <code>null</code>.
	 */
	public static boolean pingServer(SocketFactory socketfactory, SocketAddress address) throws NullPointerException {
		Objects.requireNonNull(address, "address");
		int connectiontimeoutms = DEFAULT_CONNECTION_TIMEOUT_MS;
		try (Socket s = socketfactory == null ? SocketChannel.open().socket() : socketfactory.createSocket()) {
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

	static RMIConnection newConnection(RMIOptions options, SocketFactory socketfactory, SocketAddress address)
			throws IOException {
		Socket sockclose = null;
		IOException exc = null;
		try {
			//eclipse warns that socket might be unclosed
			//but it will be closed
			@SuppressWarnings("resource")
			Socket s;
			if (socketfactory == null) {
				s = SocketChannel.open().socket();
			} else {
				s = socketfactory.createSocket();
			}
			sockclose = s;

			RMIServer.initSocketOptions(s);

			s.connect(address, 5000);

			OutputStream sockout = s.getOutputStream();
			InputStream sockin = s.getInputStream();

			DataOutputStream dataos = new DataOutputStream(sockout);
			DataInputStream datais = new DataInputStream(sockin);
			dataos.writeShort(RMIServer.CONNECTION_MAGIC_NUMBER);
			dataos.writeShort(RMIConnection.PROTOCOL_VERSION_LATEST);
			dataos.writeShort(RMIServer.COMMAND_NEW_CONNECTION);
			dataos.flush();
			short magic = datais.readShort();
			if (magic != RMIServer.CONNECTION_MAGIC_NUMBER) {
				throw new IOException("Invalid magic: 0x" + Integer.toHexString(magic));
			}
			short remoteversion = datais.readShort();
			short useversion = remoteversion > RMIConnection.PROTOCOL_VERSION_LATEST
					? RMIConnection.PROTOCOL_VERSION_LATEST
					: remoteversion;
			if (useversion <= 0) {
				//invalid version selected
				throw new IOException("Invalid version: 0x" + Integer.toHexString(magic));
			}
			short cmd = datais.readShort();
			if (cmd != RMIServer.COMMAND_NEW_CONNECTION_RESPONSE) {
				throw new IOException("Invalid response: " + cmd);
			}
			long mostsig = datais.readLong();
			long leastsig = datais.readLong();

			s.setSoTimeout(0);

			UUID uuid = new UUID(mostsig, leastsig);
			sockclose = null;
			StreamConnector streamconnector = new StreamConnector(useversion, uuid, socketfactory, address);
			return new RMIConnection(options, new StreamPair(sockin, sockout), useversion, streamconnector);
		} catch (IOException e) {
			exc = e;
		} finally {
			exc = IOUtils.closeExc(exc, sockclose);
		}
		throw exc;
	}

	private void acceptConnectionsImpl(ThreadWorkPool tpool) {
		try (ServerSocket accsocket = this.acceptorSocket) {
			while (state == STATE_RUNNING) {
				Socket accepted = accsocket.accept();
				synchronized (this) {
					if (state != STATE_RUNNING) {
						IOUtils.closePrint(accepted);
						break;
					}
					unhandledSockets.add(accepted);
					tpool.offer(() -> handleAcceptedConnection(accepted));
				}
			}
		} catch (IOException e) {
			if (state == STATE_RUNNING) {
				e.printStackTrace();
			}
		}
	}

	private void handleAcceptedConnection(Socket accepted) {
		Thread.currentThread().setContextClassLoader(null);

		Socket socketclose = accepted;
		WeakReference<RMIConnection> connref = null;
		UUID connuuidtoremove = null;
		try {
			if (state != STATE_RUNNING) {
				return;
			}
			initSocketOptions(accepted);

			accepted.setSoTimeout(DEFAULT_CONNECTION_TIMEOUT_MS);
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
					} catch (IOException | RuntimeException e) {
						IOUtils.closePrint(connection);
						dataos.writeShort(COMMAND_ERROR_SETUP_FAILED);
						dataos.flush();
						break;
					}

					RMIStream stream = new RMIStream(connection, socketis, socketos);

					connref = new WeakReference<>(connection);
					connections.put(connuuidtoremove, connref);

					dataos.writeShort(COMMAND_NEW_CONNECTION_RESPONSE);
					dataos.writeLong(connuuidtoremove.getMostSignificantBits());
					dataos.writeLong(connuuidtoremove.getLeastSignificantBits());
					dataos.flush();
					accepted.setSoTimeout(0);
					synchronized (this) {
						if (state != STATE_RUNNING) {
							IOUtils.closePrint(stream, connection);
							return;
						}
						socketclose = null;
						connuuidtoremove = null;
					}

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
		} catch (Exception e) {
			//failed to establish connection
			//ignore exception
			//socket is getting closed in finally
			e.printStackTrace();
		} finally {
			unhandledSockets.remove(accepted);
			if (socketclose != null) {
				IOUtils.closePrint(socketclose);
			}
			if (connuuidtoremove != null) {
				connections.remove(connuuidtoremove, connref);
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
		synchronized (this) {
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

	private static final class StreamConnector implements IOFunction<Collection<? super AutoCloseable>, StreamPair> {
		private final short useversion;
		private final UUID uuid;
		private final SocketFactory socketfactory;
		private final SocketAddress address;

		private StreamConnector(short useversion, UUID uuid, SocketFactory socketfactory, SocketAddress address) {
			this.useversion = useversion;
			this.uuid = uuid;
			this.socketfactory = socketfactory;
			this.address = address;
		}

		@Override
		public StreamPair apply(Collection<? super AutoCloseable> closer) throws IOException {
			Socket ssockclose = null;
			try {
				Socket sock;
				if (socketfactory == null) {
					sock = SocketChannel.open().socket();
				} else {
					sock = socketfactory.createSocket();
				}
				closer.add(sock);
				ssockclose = sock;

				RMIServer.initSocketOptions(sock);

				sock.setSoTimeout(RMIServer.DEFAULT_CONNECTION_TIMEOUT_MS);
				sock.connect(address, RMIServer.DEFAULT_CONNECTION_TIMEOUT_MS);

				OutputStream ssockout = sock.getOutputStream();
				InputStream ssockin = sock.getInputStream();
				DataOutputStream sdataos = new DataOutputStream(ssockout);
				DataInputStream sdatais = new DataInputStream(ssockin);
				sdataos.writeShort(RMIServer.CONNECTION_MAGIC_NUMBER);
				sdataos.writeShort(useversion);
				sdataos.writeShort(RMIServer.COMMAND_NEW_STREAM);
				sdataos.writeLong(uuid.getMostSignificantBits());
				sdataos.writeLong(uuid.getLeastSignificantBits());
				sdataos.flush();

				short smagic = sdatais.readShort();
				if (smagic != RMIServer.CONNECTION_MAGIC_NUMBER) {
					throw new IOException("Invalid magic: 0x" + Integer.toHexString(smagic));
				}
				short sremoteversion = sdatais.readShort();
				short suseversion = sremoteversion > RMIConnection.PROTOCOL_VERSION_LATEST
						? RMIConnection.PROTOCOL_VERSION_LATEST
						: sremoteversion;
				if (suseversion != useversion) {
					throw new IOException("Invalid version detected: 0x" + Integer.toHexString(suseversion));
				}
				short response = sdatais.readShort();
				if (response != RMIServer.COMMAND_NEW_STREAM_RESPONSE) {
					throw new IOException("Failed to create new stream. Error code: " + response);
				}

				sock.setSoTimeout(0);
				ssockclose = null;
				closer.remove(sock);
				return new StreamPair(ssockin, ssockout);
			} catch (Exception e) {
				//failed to connect, or other error
				throw e;
			} finally {
				IOUtils.closePrint(ssockclose);
			}
		}

	}
}
