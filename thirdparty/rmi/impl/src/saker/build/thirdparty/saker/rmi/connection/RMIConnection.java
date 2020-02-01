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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import saker.build.thirdparty.saker.rmi.connection.RMIStream.ClassLoaderNotFoundIOException;
import saker.build.thirdparty.saker.rmi.exception.RMIIOFailureException;
import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;
import saker.build.thirdparty.saker.util.ConcurrentPrependAccumulator;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolver;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.function.ThrowingRunnable;
import saker.build.thirdparty.saker.util.io.DataOutputUnsyncByteArrayOutputStream;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.StreamPair;
import saker.build.thirdparty.saker.util.io.function.IOFunction;
import saker.build.thirdparty.saker.util.ref.StrongSoftReference;
import saker.build.thirdparty.saker.util.thread.ParallelExecutionException;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils.ThreadWorkPool;

/**
 * Class representing an established RMI connection.
 * <p>
 * This class is the main enclosing coordinator for RMI connections.
 * <p>
 * An instance of this class can be created using the {@link RMIOptions} class.
 * <p>
 * The connection class holds information required for the RMI runtime to operate, and is the main representation of the
 * local endpoint of a RMI connection.
 * <p>
 * Objects in an RMI connection can be used via the {@link RMIVariables} class which provide the functionality for
 * creating remote objects and invoking methods. New or existing instances of variable contexts can be retrieved via the
 * {@link #newVariables()} or {@link #getVariables(String)} functions.
 * <p>
 * Context variables can be added to an RMI connection, which are basically named global variables present in a
 * connection. Clients of a connection can use these names to retrieve objects from the other endpoin. This is often
 * useful when an RMI server provides a specific functionality via an interface and clients want to get the remote
 * object to call the given functions on. See {@link #putContextVariable(String, Object)} and
 * {@link RMIVariables#getRemoteContextVariable(String)}.
 * <p>
 * The RMI connections need to be closed after being used. The closing may be synchronous or asynchronous based on the
 * caller. If the {@link #close()} method is being called through and RMI invocation, then the connection will be closed
 * asynchronously.
 */
public final class RMIConnection implements AutoCloseable {
	/**
	 * The latest protocol version.
	 */
	//IMPORTANT: In the event of incrementing this protocol version, tests should be made that ensures proper rmi connection handshakes
	public static final short PROTOCOL_VERSION_LATEST = 0x0001;

	/**
	 * The protocol version of the first RMI library release.
	 */
	public static final int PROTOCOL_VERSION_1 = 0x0001;

	/**
	 * I/O error listener interface to get notified about connection errors.
	 * <p>
	 * This interface is used in relation with {@link RMIConnection} to be notified when the connection errors are
	 * experienced. This is often due to the network conditions being poor, or the connection breaks up otherwise.
	 * <p>
	 * This event listener is <b>not</b> called when object transfer or other semantic errors are detected during RMI
	 * calls.
	 */
	public interface IOErrorListener {
		/**
		 * Notifies the listener about some I/O error in the connection.
		 * <p>
		 * The exception argument is often an instance of {@link IOException}.
		 * 
		 * @param exc
		 *            The exception that caused the error.
		 */
		public void onIOError(Throwable exc);
	}

	private static final AtomicIntegerFieldUpdater<RMIConnection> ARFU_streamRoundRobin = AtomicIntegerFieldUpdater
			.newUpdater(RMIConnection.class, "streamRoundRobin");
	private static final AtomicIntegerFieldUpdater<RMIConnection> ARFU_offeredStreamTaskCount = AtomicIntegerFieldUpdater
			.newUpdater(RMIConnection.class, "offeredStreamTaskCount");
	private static final AtomicIntegerFieldUpdater<RMIConnection> AIFU_variablesIdentifierCounter = AtomicIntegerFieldUpdater
			.newUpdater(RMIConnection.class, "variablesIdentifierCounter");

	private final ThreadWorkPool taskPool;

	@SuppressWarnings("unused")
	private volatile int streamRoundRobin;

	private final List<RMIStream> allStreams = new ArrayList<>();
	private final Collection<AutoCloseable> connectingStreamSockets = ConcurrentHashMap.newKeySet();
	private final AtomicInteger connectedOrConnectingStreams = new AtomicInteger(1);
	private WeakReference<Thread> streamAddingThread;

	private final RMITransferProperties properties;
	private final ClassLoaderResolver classLoaderResolver;
	private final ClassLoader nullClassLoader;
	private final boolean allowDirectRequests;

	private volatile String exitMessage = null;
	private volatile boolean aborting = false;
	private volatile boolean closed = false;

	private final Object stateModifyLock = new Object();

	@SuppressWarnings("unused")
	private volatile int variablesIdentifierCounter = 1;

	private final ConcurrentMap<Integer, RMIVariables> variablesByLocalId = new ConcurrentSkipListMap<>();
	private final ConcurrentMap<String, NamedRMIVariables> variablesByNames = new ConcurrentSkipListMap<>();
	private final ConcurrentSkipListMap<String, Object> namedVariablesGetLocks = new ConcurrentSkipListMap<>();

	private final int maxStreamCount;
	private final ThreadGroup workerThreadGroup;

	private final ConcurrentSkipListMap<String, Object> contextVariables = new ConcurrentSkipListMap<>();

	private Throwable ioErrorException = null;
	private Collection<IOErrorListener> errorListeners = ObjectUtils.newIdentityHashSet();

	private final RequestHandler requestHandler = new RequestHandler();
	private final ThreadLocal<AtomicInteger> currentThreadPreviousMethodCallRequestId = ThreadLocal
			.withInitial(AtomicInteger::new);

	@SuppressWarnings("unused")
	private volatile int offeredStreamTaskCount;

	private final ConcurrentPrependAccumulator<StrongSoftReference<DataOutputUnsyncByteArrayOutputStream>> bufferCache = new ConcurrentPrependAccumulator<>();

	private ConcurrentSkipListMap<Integer, RequestThreadState> requestThreadStates = new ConcurrentSkipListMap<>();
	private short protocolVersion;

	RMIConnection(RMIOptions options, short protocolversion) {
		this.protocolVersion = protocolversion;
		this.allowDirectRequests = options.allowDirectRequests;
		ThreadGroup workerThreadGroup = options.workerThreadGroup;
		RMITransferProperties properties = options.properties;

		this.properties = properties;

		this.workerThreadGroup = new ThreadGroup(
				workerThreadGroup == null ? Thread.currentThread().getThreadGroup() : workerThreadGroup,
				"RMI worker group");

		this.classLoaderResolver = defaultedClassLoaderResolver(options.classLoaderResolver);
		this.nullClassLoader = defaultedNullClassLoader(options.nullClassLoader);
		this.maxStreamCount = Math.max(options.getDefaultedMaxStreamCount(), 1);
		this.taskPool = createWorkPool();
	}

	RMIConnection(RMIOptions options, StreamPair streams, short protocolversion,
			IOFunction<Collection<? super AutoCloseable>, StreamPair> streamconnector) throws IOException {
		this.allowDirectRequests = options.allowDirectRequests;
		StreamPair streamstoclose = streams;
		RMIStream streamclose = null;
		IOException exc = null;
		try {
			Objects.requireNonNull(streamconnector, "stream connector");
			ClassLoaderResolver classresolver = options.classLoaderResolver;
			RMITransferProperties properties = options.properties;
			ThreadGroup workerThreadGroup = options.workerThreadGroup;

			this.properties = properties;
			this.workerThreadGroup = new ThreadGroup(
					workerThreadGroup == null ? Thread.currentThread().getThreadGroup() : workerThreadGroup,
					"RMI worker group");
			this.classLoaderResolver = defaultedClassLoaderResolver(classresolver);
			this.nullClassLoader = defaultedNullClassLoader(options.nullClassLoader);
			this.maxStreamCount = Math.max(options.getDefaultedMaxStreamCount(), 1);
			this.protocolVersion = protocolversion;

			OutputStream sockout = streams.getOutput();
			InputStream sockin = streams.getInput();

			RMIStream stream = new RMIStream(this, sockin, sockout);
			streamclose = stream;

			this.taskPool = createWorkPool();
			addStream(stream);
			postAddAdditionalStreams(streamconnector);

			streamstoclose = null;
			streamclose = null;
		} finally {
			if (streamstoclose != null) {
				exc = IOUtils.closeExc(exc, streamstoclose.getInput(), streamstoclose.getOutput());
			}
			exc = IOUtils.closeExc(exc, streamclose);
			IOUtils.throwExc(exc);
		}
	}

	/**
	 * Gets the {@link ClassLoaderResolver} instance used by this RMI connection.
	 * <p>
	 * If it was set by {@link RMIOptions} then the specified instance else a defaulted resolver is returned.
	 * 
	 * @return The {@link ClassLoaderResolver} used to look up classes by this RMI connection.
	 */
	public ClassLoaderResolver getClassLoaderResolver() {
		return classLoaderResolver;
	}

	/**
	 * Gets or creates a variables context for the given name.
	 * <p>
	 * Named variables are present on both endpoint with a specific name. They can be used to access predetermined
	 * variables to encapsulate different parts of code.
	 * <p>
	 * The returned variables instance need to be closed for each call of {@link #getVariables(String)}. The variables
	 * will only be closed after each client closed them, as their retrieval is reference counted. A named variables
	 * context will be finally closed if any of the endpoint closes the variables completely.
	 * <p>
	 * If a named variables has been retrieved, and later closed, a new instance for the name can be retrieved again.
	 * I.e. the names are reuseable.
	 * 
	 * @param name
	 *            The name of the variables context.
	 * @return The variables for the given name.
	 * @throws RMIRuntimeException
	 *             If the operation failed.
	 * @throws IllegalArgumentException
	 *             If the name is <code>null</code> or empty.
	 */
	public RMIVariables getVariables(String name) throws RMIRuntimeException, IllegalArgumentException {
		if (ObjectUtils.isNullOrEmpty(name)) {
			throw new IllegalArgumentException("Empty or null variables name. (" + name + ")");
		}
		checkClosed();
		NamedRMIVariables got = variablesByNames.get(name);
		if (got != null) {
			got.increaseReference();
			return got;
		}
		int identifier = AIFU_variablesIdentifierCounter.getAndIncrement(this);
		synchronized (stateModifyLock) {
			checkAborting();
			synchronized (getNamedVariablesGetLock(name)) {
				got = variablesByNames.get(name);
				if (got != null) {
					got.increaseReference();
					return got;
				}
				int varsremoteid = getStream().createNewVariables(name, identifier);
				got = new NamedRMIVariables(name, identifier, varsremoteid, this);
				variablesByNames.put(name, got);
			}
			variablesByLocalId.put(identifier, got);
			return got;
		}
	}

	/**
	 * Creates a new unnamed variables context in this connection.
	 * <p>
	 * The returned variables instance need to be closed by the caller when no longer used.
	 * 
	 * @return The created variables context.
	 * @throws RMIRuntimeException
	 *             If the creation failed.
	 * @see RMIVariables
	 */
	public RMIVariables newVariables() throws RMIRuntimeException {
		checkClosed();
		int identifier = AIFU_variablesIdentifierCounter.getAndIncrement(this);
		int varsremoteid = getStream().createNewVariables(null, identifier);
		RMIVariables result = new RMIVariables(identifier, varsremoteid, this);
		variablesByLocalId.put(identifier, result);
		return result;
	}

	/**
	 * Sets a named variable for this RMI connection.
	 * <p>
	 * Named variables can be used to retrieve pre-defined objects remotely. If a connection is supposed to be utilized
	 * for a specific task then setting a variable on the server side can make it easy for the client to establish the
	 * communication.
	 * <p>
	 * The variables are retrivable on the remote endpoint using {@link RMIVariables#getRemoteContextVariable(String)}.
	 * <p>
	 * Any previously set variable with the same name will be overwritten. Setting <code>null</code> will remove the
	 * variable.
	 * <p>
	 * It is recommended that context variables have an interface which can be used to retrieve on the other side.
	 * 
	 * @param variablename
	 *            The name of the variable.
	 * @param var
	 *            The variable object instance or <code>null</code> to remove the current object.
	 */
	public void putContextVariable(String variablename, Object var) {
		if (var == null) {
			this.contextVariables.remove(variablename);
		} else {
			this.contextVariables.put(variablename, var);
		}
	}

	/**
	 * Gets a previously set named variable on this side of the RMI connection.
	 * <p>
	 * Only local variables are retrieved and no RMI request is made to the other endpoint.
	 * 
	 * @param variablename
	 *            The name for the variable.
	 * @return The variable with the name or <code>null</code> if it is not set.
	 * @see #putContextVariable(String, Object)
	 */
	public Object getLocalContextVariable(String variablename) {
		return this.contextVariables.get(variablename);
	}

	/**
	 * Checks if this connection is about to be closed, and should no longer be used.
	 * <p>
	 * A connection is aborting, if there were any I/O errors, or it was explicitly closed, but the closing cannot be
	 * done synchronously.
	 * 
	 * @return <code>true</code> if the connection is aborting.
	 */
	public boolean isAborting() {
		return aborting;
	}

	/**
	 * Check if the connection is still in a valid state.
	 * <p>
	 * This method will return false if any I/O errors are detected, or the connection has been closed.
	 * 
	 * @return If the connection is still alive.
	 */
	public boolean isConnected() {
		return !aborting;
	}

	/**
	 * Adds an I/O error listener to this connection.
	 * <p>
	 * The listener is called when I/O errors are detected in the connection.
	 * <p>
	 * If an I/O error was already detected, then the listener will be called before this method returns.
	 * <p>
	 * When an I/O error occurs, the listeners are called, and this connection will remove all installed listeners.
	 * 
	 * @param listener
	 *            The listener.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the listener is a remote object.
	 */
	public void addErrorListener(IOErrorListener listener) throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(listener, "listener");
		if (isRemoteObject(listener)) {
			throw new IllegalArgumentException("Error listener must be a local object.");
		}
		synchronized (errorListeners) {
			if (ioErrorException != null) {
				listener.onIOError(ioErrorException);
				return;
			}
			errorListeners.add(listener);
		}
	}

	/**
	 * Removes a previously added error listener.
	 * 
	 * @param listener
	 *            The listener.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the listener is a remote object.
	 * @see #addErrorListener(IOErrorListener)
	 */
	public void removeErrorListener(IOErrorListener listener) throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(listener, "listener");
		if (isRemoteObject(listener)) {
			throw new IllegalArgumentException("Error listener must be a local object.");
		}
		synchronized (errorListeners) {
			errorListeners.remove(listener);
		}
	}

	/**
	 * Closes the RMI connection and waits for the pending requests to be finished.
	 * <p>
	 * This method works the same way as {@link #close()}, but waits for the requests to properly finish before
	 * returning.
	 * 
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws InterruptedException
	 *             If the current thread was interrupted.
	 */
	public void closeWait() throws IOException, InterruptedException {
		closeImpl();
		try {
			taskPool.closeInterruptible();
		} catch (ParallelExecutionException e) {
		}
	}

	/**
	 * Marks the RMI connection as closed.
	 * <p>
	 * Closing an RMI connection does not happen immediately, calling this method only signals the connection to be
	 * closed when the last pending request finishes.
	 * <p>
	 * To wait for the pending requests to finish, call {@link #closeWait()}.
	 * <p>
	 * If any request is made to any of the proxy objects in this connection after closing, the request may succeed, or
	 * an appropriate {@link RMIRuntimeException} may be thrown.
	 * <p>
	 * Callers should ensure that no requests are running in the system before calling this method. This often requires
	 * external synchronization by the user.
	 * 
	 * @throws IOException
	 *             In case of I/O error.
	 */
	@Override
	public void close() throws IOException {
		closeImpl();
	}

	/**
	 * Checks if the argument is a remote proxy object.
	 * <p>
	 * This method checks if the object is created by the RMI library, and calls to it will be dispatched over an RMI
	 * connection. As this is a static method, it doesn't check if it belongs to any specific RMI connection, but only
	 * checks if the objects is remote.
	 * <p>
	 * Calling this method has the same performance cost as an <code>instanceof</code> expression.
	 * 
	 * @param obj
	 *            The object to check
	 * @return <code>true</code> if the argument is a remote proxy.
	 */
	public static boolean isRemoteObject(Object obj) {
		return obj instanceof RemoteProxyObject;
	}

	/**
	 * Checks if the argument objects are both remote proxies and are used with the same RMI variables context.
	 * 
	 * @param obj1
	 *            The first object.
	 * @param obj2
	 *            The second object.
	 * @return <code>true</code> if both objects are remote, and bound to the same RMI variables context.
	 * @see RMIVariables
	 */
	public static boolean isSameLocationRemoteObjects(Object obj1, Object obj2) {
		if (!(obj1 instanceof RemoteProxyObject) || !(obj2 instanceof RemoteProxyObject)) {
			return false;
		}
		RMIVariables v1 = RemoteProxyObject.getVariables((RemoteProxyObject) obj1);
		if (v1 == null) {
			return false;
		}
		RMIVariables v2 = RemoteProxyObject.getVariables((RemoteProxyObject) obj2);
		return v1 == v2;
	}

	boolean isAllowDirectRequests() {
		return allowDirectRequests;
	}

	short getProtocolVersion() {
		return protocolVersion;
	}

	void addRequestThread(int reqid, Thread thread) {
		RequestThreadState add = new RequestThreadState(thread);
		RequestThreadState s = requestThreadStates.putIfAbsent(reqid, add);
		if (s != null) {
			s.initThread(thread);
		}
	}

	//returns the number of delivered interrupt requests
	int removeRequestThread(int reqid) {
		RequestThreadState s = requestThreadStates.remove(reqid);
		return s.finish();
	}

	void interruptRequestThread(int reqid) {
		//XXX if the request is interrupted after it is finished, then a thread state will be stuck in the map as memory leak.
		RequestThreadState s = requestThreadStates.computeIfAbsent(reqid, (k) -> new RequestThreadState());
		s.interrupt();
	}

	RequestHandler getRequestHandler() {
		return requestHandler;
	}

	ThreadLocal<AtomicInteger> getCurrentThreadPreviousMethodCallRequestIdThreadLocal() {
		return currentThreadPreviousMethodCallRequestId;
	}

	ThreadWorkPool getThreadWorkPool() {
		return taskPool;
	}

	void finishNewConnectionSetup(RMIStream stream) {
		addStream(stream);
	}

	RMIVariables getVariablesByLocalId(int identifier) {
		return variablesByLocalId.get(identifier);
	}

	RMIVariables newRemoteVariables(String name, int remoteid) throws IOException {
		if (ObjectUtils.isNullOrEmpty(name)) {
			return newUnnamedRemoteVariables(remoteid);
		}
		return newNamedRemoteVariables(name, remoteid);
	}

	void remotelyClosedVariables(RMIVariables vars) {
		if (vars == null) {
			return;
		}
		//do not remove the variables from the collections yet, as some pending requests might be still happening
		vars.close();
	}

	void closeVariables(RMIVariables variables) {
		int identifier = variables.getLocalIdentifier();
		synchronized (stateModifyLock) {
			boolean removed = this.variablesByLocalId.remove(identifier, variables);
			if (removed) {
				if (variables instanceof NamedRMIVariables) {
					this.variablesByNames.remove(((NamedRMIVariables) variables).getName(), variables);
				}

				try {
					getStream().writeVariablesClosed(variables);
				} catch (IOException | RMIRuntimeException e) {
				}
				closeIfAbortingAndNoVariables();
			}
		}
	}

	ClassLoader getNullClassLoader() {
		return nullClassLoader;
	}

	void offerStreamTask(ThrowingRunnable task) {
		ARFU_offeredStreamTaskCount.incrementAndGet(this);
		taskPool.offer(() -> {
			Thread.currentThread().setContextClassLoader(null);
			try {
				task.run();
			} finally {
				int c = ARFU_offeredStreamTaskCount.decrementAndGet(this);
				if (c == 0) {
					//this was the last stream task to run
					//stream reading tasks have already exited (or this was it)
					//no streams running -> no more requests will be added
					//close the request handler as it will not receive respones anymore
					requestHandler.close();
				}
			}
		});
	}

	StrongSoftReference<DataOutputUnsyncByteArrayOutputStream> getCachedByteBuffer() {
		for (StrongSoftReference<DataOutputUnsyncByteArrayOutputStream> got; (got = bufferCache.take()) != null;) {
			if (got.makeStrong()) {
				DataOutputUnsyncByteArrayOutputStream buf = got.get();
				buf.reset();
				return got;
			}
		}
		return new StrongSoftReference<>(new DataOutputUnsyncByteArrayOutputStream());
	}

	void releaseCachedByteBuffer(StrongSoftReference<DataOutputUnsyncByteArrayOutputStream> buffer) {
		buffer.makeSoft();
		bufferCache.add(buffer);
	}

	RMITransferProperties getProperties() {
		return properties;
	}

	void addStream(RMIStream stream) throws RMIRuntimeException {
		checkClosed();
		synchronized (stateModifyLock) {
			if (aborting) {
				IOUtils.closePrint(stream);
				return;
			}
			streamRoundRobin = allStreams.size();
			allStreams.add(stream);
		}
		stream.start();
	}

	void removeStream(RMIStream stream) {
		boolean removed;
		synchronized (stateModifyLock) {
			removed = allStreams.remove(stream);
		}
		if (removed) {
			connectedOrConnectingStreams.decrementAndGet();
		}
		IOUtils.closePrint(stream);
	}

	RMIStream getStream() {
		checkClosed();
		synchronized (stateModifyLock) {
			if (allStreams.isEmpty()) {
				throw new RMIIOFailureException("No stream found.");
			}
			return allStreams.get(ARFU_streamRoundRobin.getAndIncrement(this) % allStreams.size());
		}
	}

	void clientClose() {
		exitMessage = "Connection closed remotely.";
		abort();
	}

	void streamError(RMIStream stream, Throwable exc) {
		onIOError(exc);
		exitMessage = "Connection stream error. (" + exc + ")";
		abort();
	}

	String getClassLoaderId(ClassLoader cl) {
		if (cl == nullClassLoader) {
			return null;
		}
		String clid = classLoaderResolver.getClassLoaderIdentifier(cl);
		if (clid == null) {
			return null;
		}
		return clid;
	}

	ClassLoader getClassLoaderById(String id) {
		if (id == null) {
			return nullClassLoader;
		}
		return classLoaderResolver.getClassLoaderForIdentifier(id);
	}

	Optional<ClassLoader> getClassLoaderByIdOptional(String id) {
		if (id == null) {
			return Optional.ofNullable(nullClassLoader);
		}
		ClassLoader found = classLoaderResolver.getClassLoaderForIdentifier(id);
		if (found == null) {
			return null;
		}
		return Optional.ofNullable(found);
	}

	ClassLoader getClassLoaderByIdOrThrow(String id) throws ClassLoaderNotFoundIOException {
		if (id == null) {
			return nullClassLoader;
		}
		ClassLoader found = classLoaderResolver.getClassLoaderForIdentifier(id);
		if (found == null) {
			throw new ClassLoaderNotFoundIOException(id);
		}
		return found;
	}

	static final class OnlyClassLoaderResolver implements ClassLoaderResolver {
		private ClassLoader cl;

		public OnlyClassLoaderResolver(ClassLoader cl) {
			this.cl = cl;
		}

		@Override
		public String getClassLoaderIdentifier(ClassLoader classloader) {
			return "cl";
		}

		@Override
		public ClassLoader getClassLoaderForIdentifier(String identifier) {
			return cl;
		}
	}

	private static class RequestThreadState {
		private static final AtomicIntegerFieldUpdater<RMIConnection.RequestThreadState> AIFU_interrupted = AtomicIntegerFieldUpdater
				.newUpdater(RMIConnection.RequestThreadState.class, "interrupted");
		private static final AtomicReferenceFieldUpdater<RMIConnection.RequestThreadState, Thread> ARFU_thread = AtomicReferenceFieldUpdater
				.newUpdater(RMIConnection.RequestThreadState.class, Thread.class, "thread");
		private static final AtomicIntegerFieldUpdater<RMIConnection.RequestThreadState> AIFU_interruptCount = AtomicIntegerFieldUpdater
				.newUpdater(RMIConnection.RequestThreadState.class, "interruptCount");

		private static final int STATE_FINISHED = -1;
		private static final int STATE_UNINTERRUPTED = 0;
		private static final int STATE_WAS_INTERRUPTED = 1;
		private static final int STATE_INTERRUPTED = 2;

		private volatile int interrupted = 0;
		volatile Thread thread;
		volatile int interruptCount;

		public RequestThreadState() {
		}

		public RequestThreadState(Thread thread) {
			this.thread = thread;
		}

		//synchronization is required to ensure that an interrupt request doesnt interrupt a thread which already executes a different request

		public void initThread(Thread t) {
			if (!ARFU_thread.compareAndSet(this, null, t)) {
				throw new AssertionError("Thread is already set.");
			}
			if (this.interrupted == STATE_INTERRUPTED) {
				synchronized (this) {
					if (AIFU_interrupted.compareAndSet(this, STATE_INTERRUPTED, STATE_WAS_INTERRUPTED)) {
						AIFU_interruptCount.incrementAndGet(this);
						t.interrupt();
					}
				}
			}
		}

		public int finish() {
			this.thread = null;
			int v = AIFU_interrupted.getAndSet(this, STATE_FINISHED);
			if (v == STATE_UNINTERRUPTED) {
				return 0;
			}
			//the thread was interrupted at least once, or is still interrupting 
			synchronized (this) {
				//synchronize to ensure that any interruptions finish before exiting this method
				return this.interruptCount;
			}
		}

		public void interrupt() {
			int val = AIFU_interrupted.updateAndGet(this, v -> {
				if (v == STATE_FINISHED) {
					return STATE_FINISHED;
				}
				return STATE_INTERRUPTED;
			});
			if (val != STATE_FINISHED) {
				synchronized (this) {
					Thread t = thread;
					if (t == null) {
						return;
					}
					if (AIFU_interrupted.compareAndSet(this, STATE_INTERRUPTED, STATE_WAS_INTERRUPTED)) {
						AIFU_interruptCount.incrementAndGet(this);
						t.interrupt();
					}
				}
			}
		}
	}

	private void closeImpl() {
		abort();
		synchronized (stateModifyLock) {
			closeIfAbortingAndNoVariables();
		}
	}

	private void checkClosed() {
		if (closed) {
			throw new RMIIOFailureException("Closed." + (exitMessage == null ? "" : " (" + exitMessage + ")"));
		}
	}

	private void checkAborting() {
		//holds lock for stateModifyLock
		if (aborting) {
			throw new RMIIOFailureException("Connection aborting.");
		}
	}

	private void abort() {
		if (aborting) {
			return;
		}
		aborting = true;
		//use a copy collection to be able to remove from the real collection while the variables are closing
		for (RMIVariables v : new ArrayList<>(variablesByLocalId.values())) {
			v.close();
		}
		taskPool.exit();
		synchronized (stateModifyLock) {
			closeIfAbortingAndNoVariables();
		}
	}

	private ThreadWorkPool createWorkPool() {
		return ThreadUtils.newDynamicWorkPool(workerThreadGroup, "RMI-worker-");
	}

	private void postAddAdditionalStreams(
			IOFunction<Collection<? super AutoCloseable>, ? extends StreamPair> streamconnector) {
		taskPool.offer(() -> addAdditionalStreams(streamconnector));
	}

	private void onIOError(Throwable exc) {
		Collection<IOErrorListener> copy;
		synchronized (errorListeners) {
			if (ioErrorException == null) {
				ioErrorException = exc;
			}
			if (errorListeners.isEmpty()) {
				return;
			}
			copy = new ArrayList<>(errorListeners);
			errorListeners.clear();
		}
		for (IOErrorListener l : copy) {
			l.onIOError(exc);
		}
	}

	private RMIVariables newUnnamedRemoteVariables(int remoteid) {
		synchronized (stateModifyLock) {
			checkAborting();
			RMIVariables got = new RMIVariables(AIFU_variablesIdentifierCounter.getAndIncrement(this), remoteid, this);
			variablesByLocalId.put(got.getLocalIdentifier(), got);
			return got;
		}
	}

	private RMIVariables newNamedRemoteVariables(String name, int remoteid) throws IOException {
		synchronized (stateModifyLock) {
			checkAborting();
			NamedRMIVariables got;
			synchronized (getNamedVariablesGetLock(name)) {
				got = variablesByNames.get(name);
				if (got != null) {
					return got;
				}

				got = new NamedRMIVariables(name, AIFU_variablesIdentifierCounter.getAndIncrement(this), remoteid,
						this);
				RMIVariables prev = variablesByNames.putIfAbsent(name, got);
				if (prev != null) {
					IOException cause = IOUtils.closeExc(got);
					throw new IOException("Variables with name defined more than once: " + name, cause);
				}
			}
			variablesByLocalId.put(got.getLocalIdentifier(), got);
			return got;
		}
	}

	private Object getNamedVariablesGetLock(String name) {
		return namedVariablesGetLocks.computeIfAbsent(name, Functionals.objectComputer());
	}

	private void closeIfAbortingAndNoVariables() {
		if (!aborting || !this.variablesByLocalId.isEmpty()) {
			return;
		}
		//all variables have been closed
		//no requests are running
		//we can close the streams now
		ArrayList<RMIStream> streamscopy = new ArrayList<>(allStreams);
		allStreams.clear();
		for (RMIStream stream : streamscopy) {
			stream.close();
		}

		IOUtils.closePrint(connectingStreamSockets);
		//interrupt the stream adding thread if any
		//if the stream adding thread stays interrupted in the task pool that is fine, as there are no more requests running.
		ThreadUtils.interruptThread(ObjectUtils.getReference(streamAddingThread));

		variablesByNames.clear();
		contextVariables.clear();
	}

	private void addAdditionalStreams(
			IOFunction<Collection<? super AutoCloseable>, ? extends StreamPair> streamconnector) throws IOException {
		this.streamAddingThread = new WeakReference<>(Thread.currentThread());
		try {
			while (!aborting) {
				int cocstreams = connectedOrConnectingStreams.getAndIncrement();
				if (cocstreams < maxStreamCount) {
					StreamPair streampair;
					try {
						streampair = streamconnector.apply(connectingStreamSockets);
					} catch (IOException e) {
						throw e;
					}

					RMIStream nstream = new RMIStream(this, streampair);
					addStream(nstream);
				} else {
					//dont need to connect to any more streams
					connectedOrConnectingStreams.decrementAndGet();
					break;
				}
			}
		} finally {
			this.streamAddingThread = null;
		}
	}

	private static ClassLoaderResolver defaultedClassLoaderResolver(ClassLoaderResolver resolver) {
		return resolver;
	}

	private static ClassLoader defaultedNullClassLoader(ClassLoader nullcl) {
		return nullcl;
	}

}