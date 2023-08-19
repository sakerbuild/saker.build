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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

import saker.build.thirdparty.saker.rmi.connection.RMIStream.RequestScopeHandler;
import saker.build.thirdparty.saker.rmi.connection.RMIStream.ThreadLocalRequestScopeHandler;
import saker.build.thirdparty.saker.rmi.exception.RMICallFailedException;
import saker.build.thirdparty.saker.rmi.exception.RMIIOFailureException;
import saker.build.thirdparty.saker.rmi.exception.RMIListenerException;
import saker.build.thirdparty.saker.rmi.exception.RMIResourceUnavailableException;
import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;
import saker.build.thirdparty.saker.util.ArrayUtils;
import saker.build.thirdparty.saker.util.ConcurrentPrependAccumulator;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolver;
import saker.build.thirdparty.saker.util.io.DataOutputUnsyncByteArrayOutputStream;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.StreamPair;
import saker.build.thirdparty.saker.util.io.function.IOFunction;
import saker.build.thirdparty.saker.util.io.function.IOSupplier;
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
	public static final short PROTOCOL_VERSION_LATEST = 0x0002;

	/**
	 * The protocol version of the first RMI library release.
	 */
	public static final int PROTOCOL_VERSION_1 = 0x0001;
	/**
	 * Version 2.
	 * <p>
	 * Modifies the object protocol to include the byte count of the transferred data in case of serializing wrapped
	 * objects and serializable objects.
	 * 
	 * @since saker.rmi 0.8.3
	 */
	public static final int PROTOCOL_VERSION_2 = 0x0002;

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
		 * The exception argument is often an instance of {@link IOException}, however, it also may be of other types.
		 * <p>
		 * The argument may be an instance of {@link RMIListenerException} when a callback threw an exception. That is,
		 * callback exceptions are also reported via this listener.
		 * <p>
		 * The invocation of this callback may not necessarily cause the closing of the RMI connection.
		 * 
		 * @param exc
		 *            The exception that caused the error.
		 */
		public void onIOError(Throwable exc);
	}

	/**
	 * Event listener that is called when the {@link RMIConnection} is closed.
	 * <p>
	 * The closing may happen due to I/O errors, or by explicit request of the other endpoint. The listener is called in
	 * any case when the connection is closed.
	 *
	 * @since saker.rmi 0.8.2
	 */
	public interface CloseListener {
		/**
		 * Notifies the listener about the connection closing.
		 */
		public void onConnectionClosed();
	}

	private static final AtomicIntegerFieldUpdater<RMIConnection> AIFU_offeredStreamTaskCount = AtomicIntegerFieldUpdater
			.newUpdater(RMIConnection.class, "offeredStreamTaskCount");
	private static final AtomicIntegerFieldUpdater<RMIConnection> AIFU_variablesIdentifierCounter = AtomicIntegerFieldUpdater
			.newUpdater(RMIConnection.class, "variablesIdentifierCounter");

	private static final AtomicReferenceFieldUpdater<RMIConnection, Thread[]> ARFU_exitWaitThreads = AtomicReferenceFieldUpdater
			.newUpdater(RMIConnection.class, Thread[].class, "exitWaitThreads");

	private static final AtomicIntegerFieldUpdater<RMIConnection> AIFU_requestIdCounter = AtomicIntegerFieldUpdater
			.newUpdater(RMIConnection.class, "requestIdCounter");

	private static final Thread[] EXIT_WAIT_THREADS_MARKER_EXITED = {};

	private final List<RMIStream> allStreams = new ArrayList<>();
	private final Collection<AutoCloseable> connectingStreamSockets = ConcurrentHashMap.newKeySet();
	/**
	 * Atomic variable holding the number of connection attempts made to establish new streams.
	 * <p>
	 * Never decremented
	 */
	private final AtomicInteger connectedOrConnectingStreamCount = new AtomicInteger(1);

	private final RMITransferProperties properties;
	private final ClassLoaderResolver classLoaderResolver;
	private final ClassLoader nullClassLoader;
	private final boolean allowDirectRequests;

	private volatile String exitMessage = null;
	private volatile boolean aborting = false;

	private final Lock stateModifyLock = new ReentrantLock();

	@SuppressWarnings("unused")
	private volatile int variablesIdentifierCounter = 1;

	private final ConcurrentMap<Integer, RMIVariables> variablesByLocalId = new ConcurrentSkipListMap<>();
	private final ConcurrentMap<String, NamedRMIVariables> variablesByNames = new ConcurrentSkipListMap<>();
	private final ConcurrentSkipListMap<String, Lock> namedVariablesGetLocks = new ConcurrentSkipListMap<>();

	private final int maxStreamCount;

	private final ConcurrentSkipListMap<String, Object> contextVariables = new ConcurrentSkipListMap<>();

	private Throwable ioErrorException = null;
	private Collection<IOErrorListener> errorListeners = ObjectUtils.newIdentityHashSet();
	private Collection<CloseListener> closeListeners = ObjectUtils.newIdentityHashSet();

	@SuppressWarnings("unused")
	private volatile int requestIdCounter;
	private final RequestScopeHandler requestScopeHandler = new ThreadLocalRequestScopeHandler();

	@SuppressWarnings("unused") // used through its atomic field updater
	private volatile int offeredStreamTaskCount;

	private final ConcurrentPrependAccumulator<StrongSoftReference<DataOutputUnsyncByteArrayOutputStream>> bufferCache = new ConcurrentPrependAccumulator<>();

	private ConcurrentSkipListMap<Integer, RequestThreadState> requestThreadStates = new ConcurrentSkipListMap<>();
	private final short protocolVersion;

	private RMIStatistics statistics;

	private final boolean objectTransferByteChecks;

	/**
	 * Only set if the {@link RMIConnection} manages its own task pool, and no {@link Executor} was set via
	 * {@link RMIOptions}.
	 */
	//effectively final
	private ThreadWorkPool taskPool;
	//effectively final
	private Executor taskExecutor;

	/**
	 * A list of threads that are waiting for the started task threads to exit.
	 */
	private volatile Thread[] exitWaitThreads = {};

	private final IOSupplier<? extends StreamPair> streamConnector;

	RMIConnection(RMIOptions options, short protocolversion) {
		this.protocolVersion = protocolversion;
		this.allowDirectRequests = options.allowDirectRequests;
		this.streamConnector = null;
		RMITransferProperties properties = options.properties;

		this.properties = properties;

		this.classLoaderResolver = defaultedClassLoaderResolver(options.classLoaderResolver);
		this.nullClassLoader = defaultedNullClassLoader(options.nullClassLoader);
		this.maxStreamCount = Math.max(options.getDefaultedMaxStreamCount(), 1);

		initTaskFields(options);
		if (options.collectStatistics) {
			this.statistics = new RMIStatistics();
		}
		this.objectTransferByteChecks = options.objectTransferByteChecks;
	}

	boolean isCustomExecutor() {
		return this.taskExecutor != null;
	}

	int getNextRequestId() {
		return AIFU_requestIdCounter.incrementAndGet(this);
	}

	RequestScopeHandler getRequestScopeHandler() {
		return requestScopeHandler;
	}

	boolean isObjectTransferByteChecks() {
		return objectTransferByteChecks;
	}

	private void initTaskFields(RMIOptions options) {
		Executor executor = options.executor;
		if (executor != null) {
			this.taskExecutor = executor;
			return;
		}

		ThreadGroup workerThreadGroup = options.workerThreadGroup;
		//create worker subgroup
		workerThreadGroup = new ThreadGroup(
				workerThreadGroup == null ? Thread.currentThread().getThreadGroup() : workerThreadGroup,
				"RMI worker group");
		ThreadWorkPool taskpool = createWorkPool(workerThreadGroup);
		this.taskPool = taskpool;
		this.taskExecutor = r -> taskpool.offer(r::run);
	}

	RMIConnection(RMIOptions options, StreamPair streams, short protocolversion,
			IOFunction<? super PendingStreamTracker, ? extends StreamPair> streamconnector) throws IOException {
		this.allowDirectRequests = options.allowDirectRequests;
		if (options.collectStatistics) {
			this.statistics = new RMIStatistics();
		}
		this.objectTransferByteChecks = options.objectTransferByteChecks;

		this.streamConnector = new IOSupplier<StreamPair>() {
			private final PendingStreamTracker pendingTracker = new PendingStreamTracker() {
				@Override
				public boolean add(AutoCloseable s) {
					//lock on state modify lock, so if close is being called concurrently,
					//we still add the socket to the collection
					stateModifyLock.lock();
					try {
						if (aborting) {
							return false;
						}
						connectingStreamSockets.add(s);
					} finally {
						stateModifyLock.unlock();
					}
					return true;
				}

				@Override
				public void remove(AutoCloseable s) {
					connectingStreamSockets.remove(s);
				}
			};

			@Override
			public StreamPair get() throws IOException {
				return streamconnector.apply(pendingTracker);
			}
		};

		StreamPair streamstoclose = streams;
		RMIStream streamclose = null;
		IOException exc = null;
		try {
			Objects.requireNonNull(streamconnector, "stream connector");
			ClassLoaderResolver classresolver = options.classLoaderResolver;
			RMITransferProperties properties = options.properties;

			this.properties = properties;
			this.classLoaderResolver = defaultedClassLoaderResolver(classresolver);
			this.nullClassLoader = defaultedNullClassLoader(options.nullClassLoader);
			this.maxStreamCount = Math.max(options.getDefaultedMaxStreamCount(), 1);
			this.protocolVersion = protocolversion;

			initTaskFields(options);

			OutputStream sockout = streams.getOutput();
			InputStream sockin = streams.getInput();

			RMIStream stream = new RMIStream(this, sockin, sockout);
			streamclose = stream;

			addStream(stream);

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
	 * Gets the RMI statistics that were collected.
	 * <p>
	 * This method returns non-<code>null</code> if and only if {@link RMIOptions#collectStatistics(boolean)} was set to
	 * <code>true</code>.
	 * <p>
	 * If the connection is still alive (i.e. not closed) then the returned statistics object may be modified if RMI
	 * calls are performed concurrently.
	 * 
	 * @return The statistics or <code>null</code> if none were collected.
	 * @since saker.rmi 0.8.2
	 */
	public RMIStatistics getStatistics() {
		return statistics;
	}

	/**
	 * Gets if RMI statistics are being collected.
	 * 
	 * @return <code>true</code> if {@link #getStatistics()} will return non-<code>null</code>.
	 * @since saker.rmi 0.8.2
	 */
	public boolean isStatisticsCollected() {
		return statistics != null;
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
	 *             If the name is <code>null</code> or empty, or the given variables context name is too long, and
	 *             cannot be encoded via UTF-8 into less than 65536 bytes.
	 */
	public RMIVariables getVariables(String name) throws RMIRuntimeException, IllegalArgumentException {
		if (ObjectUtils.isNullOrEmpty(name)) {
			throw new IllegalArgumentException("Empty or null variables name. (" + name + ")");
		}
		checkClosed();
		NamedRMIVariables result = variablesByNames.get(name);
		if (result != null) {
			result.increaseReference();
			return result;
		}
		int identifier = AIFU_variablesIdentifierCounter.getAndIncrement(this);
		final Lock namedvarlock = getNamedVariablesGetLock(name);
		stateModifyLock.lock();
		try {
			checkAborting();
			RMIStream stream;
			namedvarlock.lock();
			try {
				result = variablesByNames.get(name);
				if (result != null) {
					result.increaseReference();
					return result;
				}
				stream = getStream();
				int varsremoteid = stream.createNewVariables(name, identifier);
				result = new NamedRMIVariables(name, identifier, varsremoteid, this, stream);
				variablesByNames.put(name, result);
			} finally {
				namedvarlock.unlock();
			}
			variablesByLocalId.put(identifier, result);

			if (stream.associateVariables(result)) {
				return result;
			}
			//close and throw the exception out of the lock
		} finally {
			stateModifyLock.unlock();
		}
		result.close();
		throw new RMIIOFailureException("Failed to create variables, underlying stream IO error.");
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
		RMIStream stream = getStream();
		int varsremoteid = stream.createNewVariables(null, identifier);
		RMIVariables result = new RMIVariables(identifier, varsremoteid, this, stream);

		//check if the connection was closed meanwhile
		//close the variables, and report an appropriate exception if so
		//(if closed, then we still have to put the variables in the appropriate map
		// so the closing procedure is correct)
		//XXX there's still a hazardous state, as getVariablesByLocalId() still returns this variables
		//    so the other endpoint still could use this, but it is unlikely
		//    anyway, event in that case, the variables will still be auto-closed when the last request finishes on it
		RMIRuntimeException abortexc;
		stateModifyLock.lock();
		try {
			variablesByLocalId.put(identifier, result);
			if (this.aborting) {
				abortexc = new RMIResourceUnavailableException("Connection aborting.");
			} else {
				if (stream.associateVariables(result)) {
					return result;
				}
				//couldn't associate with the stream
				abortexc = new RMIIOFailureException("Failed to create variables, underlying stream IO error.");
			}
			//close and throw the exception out of the lock
		} finally {
			stateModifyLock.unlock();
		}
		result.close();
		throw abortexc;
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
	 * Listeners are kept until they are explicitly {@linkplain #removeErrorListener(IOErrorListener) remoeved}. They
	 * may be called concurrently, and multiple times.
	 * 
	 * @param listener
	 *            The listener.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the listener is a remote object.
	 * @throws RMIListenerException
	 *             If an I/O error was detected therefore the argument listener is called, and it threw an exception.
	 */
	public void addErrorListener(IOErrorListener listener)
			throws NullPointerException, IllegalArgumentException, RMIListenerException {
		Objects.requireNonNull(listener, "listener");
		if (isRemoteObject(listener)) {
			throw new IllegalArgumentException("Listener must be a local object.");
		}
		Throwable callexc;
		synchronized (errorListeners) {
			errorListeners.add(listener);
			callexc = ioErrorException;
			if (callexc == null) {
				return;
			}
			//listener to be called outside of lock
		}
		try {
			listener.onIOError(callexc);
		} catch (Exception e) {
			throw new RMIListenerException("RMI listener callback threw an exception.", e);
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
			throw new IllegalArgumentException("Listener must be a local object.");
		}
		synchronized (errorListeners) {
			errorListeners.remove(listener);
		}
	}

	/**
	 * Adds a connection close listener to this connection.
	 * <p>
	 * The listener is called when the connection is being closed. It may be called asynchronously, and even before this
	 * method finishes.
	 * 
	 * @param listener
	 *            The listener.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the listener is a remote object.
	 * @throws RMIListenerException
	 *             If the connection is closed, therefore the argument listener is called, and it threw an exception.
	 * @since saker.rmi 0.8.2
	 */
	public void addCloseListener(CloseListener listener)
			throws NullPointerException, IllegalArgumentException, RMIListenerException {
		Objects.requireNonNull(listener, "listener");
		if (isRemoteObject(listener)) {
			throw new IllegalArgumentException("Listener must be a local object.");
		}
		stateModifyLock.lock();
		try {
			if (aborting && this.variablesByLocalId.isEmpty()) {
				//listener to be called outside of lock
			} else {
				synchronized (closeListeners) {
					closeListeners.add(listener);
				}
				return;
			}
		} finally {
			stateModifyLock.unlock();
		}
		try {
			listener.onConnectionClosed();
		} catch (Exception e) {
			throw new RMIListenerException("RMI listener callback threw an exception.", e);
		}
	}

	/**
	 * Removes a previously added close listener.
	 * 
	 * @param listener
	 *            The listener.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the listener is a remote object.
	 * @see #addCloseListener(CloseListener)
	 * @since saker.rmi 0.8.2
	 */
	public void removeCloseListener(CloseListener listener) throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(listener, "listener");
		if (isRemoteObject(listener)) {
			throw new IllegalArgumentException("Listener must be a local object.");
		}
		synchronized (closeListeners) {
			closeListeners.remove(listener);
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
		waitRMITasks();
	}

	private void waitRMITasks() throws InterruptedException {
		if (taskPool != null) {
			try {
				taskPool.closeInterruptible();
			} catch (ParallelExecutionException e) {
			}
			return;
		}
		//add the current thread to the waiting threads
		//(some part of the loops are unrolled, that's why this code looks like it does)
		Thread[] threads = this.exitWaitThreads;
		if (threads == EXIT_WAIT_THREADS_MARKER_EXITED) {
			return;
		}
		Thread currentthread = Thread.currentThread();
		while (true) {
			Thread[] nthreads = ArrayUtils.appended(threads, currentthread);
			if (ARFU_exitWaitThreads.compareAndSet(this, threads, nthreads)) {
				threads = nthreads;
				break;
			}
			threads = this.exitWaitThreads;
			if (threads == EXIT_WAIT_THREADS_MARKER_EXITED) {
				return;
			}
		}
		//our thread has been added to the exit thread array
		while (true) {
			threads = this.exitWaitThreads;
			if (threads == EXIT_WAIT_THREADS_MARKER_EXITED) {
				return;
			}
			if (Thread.interrupted()) {
				//the thread got interrupted
				//remove the current thread from the threads array and throw an exception
				for (int i = 0; i < threads.length;) {
					if (threads[i] != currentthread) {
						i++;
						continue;
					}
					Thread[] nthreads = ArrayUtils.removedAtIndex(threads, i);
					if (ARFU_exitWaitThreads.compareAndSet(this, threads, nthreads)) {
						//successful removal, throw the exception
						throw new InterruptedException("Waiting for RMI tasks interrupted.");
					}
					//failed to update the threads variable, re-read it again, and restart the loop
					threads = this.exitWaitThreads;
					if (threads == EXIT_WAIT_THREADS_MARKER_EXITED) {
						//the tasks exited meanwhile, so we can ignore the interruption, and exit successfully as all is finished
						//reinterrupt, as we consumed it in the call above
						currentthread.interrupt();
						return;
					}
					//reinit iter variable
					i = 0;
				}
				throw new IllegalStateException(
						"Internal consistency error: Current thread not found in waiter threads when waiting for RMI tasks. Current thread: "
								+ currentthread + " Threads: " + Arrays.toString(threads));

			}
			LockSupport.park();
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

	void finishNewConnectionSetup(RMIStream stream) throws RMIRuntimeException, IOException {
		addStream(stream);
	}

	RMIVariables getVariablesByLocalId(int identifier) {
		return variablesByLocalId.get(identifier);
	}

	RMIVariables newRemoteVariables(String name, int remoteid, RMIStream stream) {
		if (ObjectUtils.isNullOrEmpty(name)) {
			return newUnnamedRemoteVariables(remoteid, stream);
		}
		return newNamedRemoteVariables(name, remoteid, stream);
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
		stateModifyLock.lock();
		try {
			closeVariablesLocked(variables, identifier);
		} finally {
			stateModifyLock.unlock();
		}
	}

	private boolean removeVariablesLocked(RMIVariables variables, int identifier) {
		boolean removed = this.variablesByLocalId.remove(identifier, variables);
		if (!removed) {
			return false;
		}
		if (variables instanceof NamedRMIVariables) {
			this.variablesByNames.remove(((NamedRMIVariables) variables).getName(), variables);
		}
		return removed;
	}

	/**
	 * Locked on {@link #stateModifyLock}.
	 * 
	 * @param variables
	 * @param identifier
	 *            The local identifier of the variables.
	 */
	private void closeVariablesLocked(RMIVariables variables, int identifier) {
		boolean removed = removeVariablesLocked(variables, identifier);
		if (!removed) {
			return;
		}

		try {
			variables.getStream().writeVariablesClosed(variables);
		} catch (RMIRuntimeException e) {
			//if stream closed or write fails, ignoreable, as the variable itself is being closed
		}
		closeIfAbortingAndNoVariablesLocked();
	}

	ClassLoader getNullClassLoader() {
		return nullClassLoader;
	}

	void offerStreamTask(Runnable task) {
		AIFU_offeredStreamTaskCount.incrementAndGet(this);
		this.taskExecutor.execute(() -> {
			try {
				clearContextClassLoaderOfCurrentThread();
				task.run();
			} finally {
				if (AIFU_offeredStreamTaskCount.decrementAndGet(this) == 0) {
					handleNoMoreRunningStreamTasks();
				}
			}
		});
	}

	protected static void clearContextClassLoaderOfCurrentThread() {
		try {
			Thread.currentThread().setContextClassLoader(null);
		} catch (UnsupportedOperationException e) {
			//setting the context class loader may be unsupported on virtual threads
			//ignore
		}
	}

	void offerVariablesTask(Runnable task) {
		offerStreamTask(task);
	}

	private void handleNoMoreRunningStreamTasks() {
		//unpark the possibly waiting threads
		Thread[] threads = ARFU_exitWaitThreads.getAndSet(this, EXIT_WAIT_THREADS_MARKER_EXITED);
		if (threads == EXIT_WAIT_THREADS_MARKER_EXITED) {
			//somebody already called this function
			return;
		}
		for (int i = 0; i < threads.length; i++) {
			LockSupport.unpark(threads[i]);
		}
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

	void addStream(RMIStream stream) throws IOException {
		stateModifyLock.lock();
		try {
			if (aborting) {
				//the connection is already aborting, ignore the stream addition, silently close it
				IOUtils.close(stream);
				return;
			}
			allStreams.add(stream);
		} finally {
			stateModifyLock.unlock();
		}
		stream.start();
	}

	void removeStream(RMIStream stream) throws IOException {
		stateModifyLock.lock();
		try {
			allStreams.remove(stream);
		} finally {
			stateModifyLock.unlock();
		}
		IOUtils.close(stream);
	}

	private RMIStream getStream() {
		checkAborting();

		RMIStream minstream = null;
		boolean connect = false;
		stateModifyLock.lock();
		try {
			//choose from existing streams
			Iterator<RMIStream> it = allStreams.iterator();
			int minc = 0;
			while (it.hasNext()) {
				RMIStream stream = it.next();
				int currentcount = stream.getAssociatedRMIVariablesCount();
				if (currentcount < 0) {
					//the stream got closed meanwhile
					continue;
				}
				if (currentcount == 0) {
					return stream;
				}
				if (minstream == null || currentcount < minc) {
					minc = currentcount;
					minstream = stream;
				}
			}
			//all streams have at least 1 RMIVariables associated with them
			//try to connect a new one if allowed, otherwise return the found one
			if (streamConnector == null || allStreams.size() >= maxStreamCount
					|| connectedOrConnectingStreamCount.getAndIncrement() >= maxStreamCount) {
				//cant connect to any more streams
				if (minstream == null) {
					throw new RMIResourceUnavailableException("No stream found.");
				}
				return minstream;
			}

			connect = true;
		} finally {
			stateModifyLock.unlock();
		}
		if (connect) {
			//if connection fails, then return the found min stream (if any)

			checkAborting();
			StreamPair streampair;
			RMIStream nstream;
			try {
				streampair = this.streamConnector.get();
				if (streampair == null) {
					throw new NullPointerException("Failed to create RMI stream pair.");
				}
				nstream = new RMIStream(this, streampair);
				try {
					addStream(nstream);
				} catch (Exception e) {
					//close the stream if we failed to add it
					IOUtils.addExc(e, IOUtils.closeExc(nstream));
					throw e;
				}
			} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
					| ServiceConfigurationError e) {
				//set the connecting count to the max stream count so we don't attempt to establish new connections, 
				//as this one failed, and we don't expect others to succeed
				connectedOrConnectingStreamCount.set(maxStreamCount);

				if (aborting) {
					//ignorable if we're already aborting
					throw new RMIResourceUnavailableException("Connection aborting.", e);
				}
				// notify the user of the RMI library about the exception
				invokeIOErrorListeners(e, false);
				if (minstream != null) {
					return minstream;
				}
				throw new RMIIOFailureException("Failed to connect new stream.", e);
			}

			return nstream;
		}
		if (minstream == null) {
			throw new RMIResourceUnavailableException("No stream found.");
		}
		return minstream;
	}

	/**
	 * Gets the current number of active streams.
	 * <p>
	 * For testing purposes only.
	 * 
	 * @return The stream count.
	 */
	int getStreamCount() {
		stateModifyLock.lock();
		try {
			return allStreams.size();
		} finally {
			stateModifyLock.unlock();
		}
	}

	void clientClose() {
		exitMessage = "Connection closed remotely.";
		abort(false);
	}

	void streamError(RMIStream stream, Throwable exc) {
		Exception runexc = null;
		try {
			//this method doesn't throw an exception, but keep this nonetheless
			invokeIOErrorListeners(exc);
		} catch (Exception e) {
			runexc = IOUtils.addExc(runexc, e);
		}
		exitMessage = "Connection stream error. (" + exc + ")";
		try {
			abort(true);
		} catch (Exception abortexc) {
			IOUtils.addExc(abortexc, runexc);

			//this method doesn't throw an exception
			invokeIOErrorListeners(abortexc);

		} catch (Throwable abortexc) {
			IOUtils.addExc(abortexc, runexc);

			//this method doesn't throw an exception
			invokeIOErrorListeners(abortexc);
			//serious error, throw back
			throw abortexc;
		}
	}

	String getClassLoaderId(ClassLoader cl) {
		if (cl == nullClassLoader) {
			return null;
		}
		if (classLoaderResolver != null) {
			String clid = classLoaderResolver.getClassLoaderIdentifier(cl);
			if (clid != null) {
				return clid;
			}
		}
		return null;
	}

	ClassLoader getClassLoaderById(String id) {
		if (id == null) {
			return nullClassLoader;
		}
		if (classLoaderResolver != null) {
			return classLoaderResolver.getClassLoaderForIdentifier(id);
		}
		return null;
	}

	Optional<ClassLoader> getClassLoaderByIdOptional(String id) {
		if (id == null) {
			return Optional.ofNullable(nullClassLoader);
		}
		if (classLoaderResolver != null) {
			ClassLoader found = classLoaderResolver.getClassLoaderForIdentifier(id);
			if (found != null) {
				return Optional.of(found);
			}
		}
		return null;
	}

	RMIStatistics getCollectingStatistics() {
		return statistics;
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
		abort(false);
	}

	private void checkClosed() {
		if (aborting) {
			StringBuilder sb = new StringBuilder();
			sb.append("Closed.");
			String exitmessage = this.exitMessage;
			if (exitmessage != null) {
				sb.append(" (");
				sb.append(exitmessage);
				sb.append(')');
			}
			throw new RMIResourceUnavailableException(sb.toString());
		}
	}

	private void checkAborting() {
		if (aborting) {
			throw new RMIResourceUnavailableException("Connection aborting.");
		}
	}

	/**
	 * Signals that the connection should be closed. This method may not actually close the connection, as some
	 * concurrent requests may keep it alive for a while.
	 * 
	 * @param onerror
	 *            <code>true</code> if aborting is performed due to an error.
	 */
	private void abort(boolean onerror) {
		if (aborting) {
			if (!onerror) {
				return;
			}
			//else if aborting due to an error, then continue
		} else {
			//set the aborting flag while locked, so no more variables and other state related objects are added to the connection
			//(if we didn't lock, then we risk new requests/variables/etc... being added after setting the aborting flag
			stateModifyLock.lock();
			try {
				aborting = true;
			} finally {
				stateModifyLock.unlock();
			}
		}
		//use a copy collection to be able to remove from the real collection while the variables are closing
		List<RMIVariables> vars = ImmutableUtils.makeImmutableList(variablesByLocalId.values());
		boolean emptyvars = vars.isEmpty();
		if (!emptyvars) {
			for (RMIVariables v : vars) {
				v.close();
			}
		}
		if (taskPool != null) {
			taskPool.exit();
		}
		if (emptyvars) {
			//variables are empty
			//we need to call closeIfAbortingAndNoVariablesLocked() in this branch
			//as that will not be called in closeVariables(RMIVariables), as no variables were closed this time

			//if there is at least 1 variable closed during this method call, then that will call closeIfAbortingAndNoVariablesLocked()
			//when it gets closed

			stateModifyLock.lock();
			try {
				closeIfAbortingAndNoVariablesLocked();
			} finally {
				stateModifyLock.unlock();
			}
		}

	}

	private static ThreadWorkPool createWorkPool(ThreadGroup threadgroup) {
		return ThreadUtils.newDynamicWorkPool(threadgroup, "RMI-worker-");
	}

	void invokeIOErrorListeners(Throwable exc, boolean storeexception) {
		Collection<IOErrorListener> ioerrorlistenerscopy;
		synchronized (errorListeners) {
			if (ioErrorException == null) {
				if (storeexception) {
					ioErrorException = exc;
				}
			} else {
				ioErrorException.addSuppressed(exc);
			}
			if (errorListeners.isEmpty()) {
				return;
			}
			ioerrorlistenerscopy = ImmutableUtils.makeImmutableList(errorListeners);
		}
		Throwable[] listenerexceptions = ObjectUtils.EMPTY_THROWABLE_ARRAY;
		for (IOErrorListener l : ioerrorlistenerscopy) {
			try {
				l.onIOError(exc);
			} catch (Exception | LinkageError | StackOverflowError | AssertionError | ServiceConfigurationError
					| OutOfMemoryError e) {
				listenerexceptions = ArrayUtils.appended(listenerexceptions, e);
			}
		}
		if (listenerexceptions.length > 0) {
			RMIListenerException ex = new RMIListenerException("IO error listeners threw an exception.");
			for (Throwable t : listenerexceptions) {
				ex.addSuppressed(t);
			}
			//invoke them again, but just print any other thrown exceptions
			//this is a hard fallback
			for (IOErrorListener l : ioerrorlistenerscopy) {
				try {
					l.onIOError(ex);
				} catch (Exception | LinkageError | StackOverflowError | AssertionError | ServiceConfigurationError
						| OutOfMemoryError e) {
					e.printStackTrace();
				}
			}
		}
	}

	void invokeIOErrorListeners(Throwable exc) {
		invokeIOErrorListeners(exc, true);
	}

	private RMIVariables newUnnamedRemoteVariables(int remoteid, RMIStream stream) {
		RMIVariables result;
		stateModifyLock.lock();
		try {
			checkAborting();
			result = new RMIVariables(AIFU_variablesIdentifierCounter.getAndIncrement(this), remoteid, this, stream);
			variablesByLocalId.put(result.getLocalIdentifier(), result);

			if (stream.associateVariables(result)) {
				return result;
			}
			//close and throw the exception out of the lock
		} finally {
			stateModifyLock.unlock();
		}
		result.close();
		throw new RMIIOFailureException("Failed to create variables, underlying stream IO error.");
	}

	private RMIVariables newNamedRemoteVariables(String name, int remoteid, RMIStream stream) {
		NamedRMIVariables result;
		final Lock namedvarlock = getNamedVariablesGetLock(name);
		stateModifyLock.lock();
		try {
			checkAborting();
			namedvarlock.lock();
			try {
				result = variablesByNames.get(name);
				if (result != null) {
					return result;
				}

				result = new NamedRMIVariables(name, AIFU_variablesIdentifierCounter.getAndIncrement(this), remoteid,
						this, stream);
				RMIVariables prev = variablesByNames.putIfAbsent(name, result);
				if (prev != null) {
					//this is a protocol error
					//the named variables already exists, but we got more than one new named variables command
					//this means that the client state didn't properly track the existence of this variables
					//therefore there's likely an internal consistency error, or race condition in the library
					IOException closingexc = IOUtils.closeExc(result);
					RMICallFailedException throwexc = new RMICallFailedException(
							"RMI connection consistency error, variables with name defined more than once: " + name);
					if (closingexc != null) {
						throwexc.addSuppressed(closingexc);
					}
					throw throwexc;
				}
			} finally {
				namedvarlock.unlock();
			}
			variablesByLocalId.put(result.getLocalIdentifier(), result);

			if (stream.associateVariables(result)) {
				return result;
			}
			//close and throw the exception out of the lock
		} finally {
			stateModifyLock.unlock();
		}
		result.close();
		throw new RMIIOFailureException("Failed to create variables, underlying stream IO error.");
	}

	private Lock getNamedVariablesGetLock(String name) {
		return namedVariablesGetLocks.computeIfAbsent(name, k -> new ReentrantLock());
	}

	/**
	 * Called with {@link #stateModifyLock} locked.
	 */
	private void closeIfAbortingAndNoVariablesLocked() {
		if (!aborting || !this.variablesByLocalId.isEmpty()) {
			return;
		}
		//all variables have been closed
		//no requests are running
		//we can close the streams now
		List<RMIStream> streamscopy = ImmutableUtils.makeImmutableList(allStreams);
		allStreams.clear();
		IOException closeexc = null;

		closeexc = IOUtils.closeExc(closeexc, streamscopy);

		closeexc = IOUtils.closeExc(closeexc, connectingStreamSockets);

		variablesByNames.clear();
		contextVariables.clear();

		Collection<CloseListener> closelistenerscopy;
		synchronized (closeListeners) {
			if (closeListeners.isEmpty()) {
				return;
			}
			closelistenerscopy = ImmutableUtils.makeImmutableList(closeListeners);
			closeListeners.clear();
		}
		Throwable[] listenerexceptions = ObjectUtils.EMPTY_THROWABLE_ARRAY;
		for (CloseListener l : closelistenerscopy) {
			try {
				l.onConnectionClosed();
			} catch (Exception | LinkageError | StackOverflowError | AssertionError | ServiceConfigurationError
					| OutOfMemoryError e) {
				listenerexceptions = ArrayUtils.appended(listenerexceptions, e);
			}
		}
		if (listenerexceptions.length > 0) {
			RMIListenerException listenerexc = new RMIListenerException("Close listeners threw an exception.");
			for (Throwable t : listenerexceptions) {
				listenerexc.addSuppressed(t);
			}
			invokeIOErrorListeners(listenerexc);
		}
		if (closeexc != null) {
			invokeIOErrorListeners(closeexc);
		}
	}

	private static ClassLoaderResolver defaultedClassLoaderResolver(ClassLoaderResolver resolver) {
		return resolver;
	}

	private static ClassLoader defaultedNullClassLoader(ClassLoader nullcl) {
		return nullcl;
	}

	interface PendingStreamTracker {
		/**
		 * @param s
		 * @return <code>true</code> if the caller should proceed, <code>false</code> if the RMI connection is closing
		 *             and caller shouldn't attempt further connections or communication.
		 */
		public boolean add(AutoCloseable s);

		public void remove(AutoCloseable s);
	}

}