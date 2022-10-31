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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import saker.build.thirdparty.saker.rmi.exception.RMICallFailedException;
import saker.build.thirdparty.saker.rmi.exception.RMICallForbiddenException;
import saker.build.thirdparty.saker.rmi.exception.RMIIOFailureException;
import saker.build.thirdparty.saker.rmi.exception.RMIObjectTransferFailureException;
import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.thirdparty.saker.util.classloader.FilteringClassLoader;
import saker.build.thirdparty.saker.util.classloader.MultiClassLoader;

/**
 * Class for enclosing RMI proxies, referenced objects, and providing invocation functionality for the RMI runtime.
 * <p>
 * The resposibility of the variables context is to provide access to the objects present in the RMI connection. Each
 * remote object can be bound to only a specific variables context, and any method invocation on it will be done through
 * its context.
 * <p>
 * The purpose of this class is to enclose series of requests and operations in a container and providing functionality
 * in regards with these objects. By enclosing them in a container, the callers are able to simply release the resources
 * after a series of requests. E.g. a client connects to a server, and makes method calls on given proxy objects. After
 * the client is done, it can close the used variables context, and all associated resource can be freed both on the
 * client and on the server. This means that the connection will no longer hold references to no longer used remote
 * objects, and this can speed up the freeing of objects for persistent connections.
 * <p>
 * The variables context provides distributed GC (Garbage Collection) features, which means that if a proxy object is no
 * longer referenced on the other endpoint, then it may be garbage collected by the JVM on the local endpoint. The
 * variables context will reference the passed objects strongly, until it is no longer referenced on the other endpoint.
 * <p>
 * The variables context will create the proxy objects by generating appropriate bytecode based on the configured
 * {@link RMITransferProperties}. These classes are defined in an internal classloader. Interfaces used in an RMI
 * connection should always have public visibility.
 * <p>
 * The variables need to be closed after they've been used. Calling {@link #close()} will flag the variables as closed,
 * and the closing will happen when there are no more running requests in it.
 * 
 * @see RMIConnection
 * @see RMITransferProperties
 */
public class RMIVariables implements AutoCloseable {
	private static final ClassLoader RMI_CLASSES_CLASSLOADER = RMIVariables.class.getClassLoader();

	private static final FilteringClassLoader RMI_PROXY_CLASSLOADER_PARENT = new FilteringClassLoader(
			RMI_CLASSES_CLASSLOADER,
			ObjectUtils.newTreeSet(RMIRuntimeException.class.getName(), RMICallFailedException.class.getName(),
					MethodTransferProperties.class.getName(), RMITransferPropertiesHolder.class.getName(),
					RemoteProxyObject.class.getName(), RemoteProxyObject.RMICacheHelper.class.getName(),
					RemoteProxyObject.RemoteInvocationRMIFailureException.class.getName(),
					RMIStatistics.class.getName()));

	private static final String PROXY_PACKAGE_NAME = ReflectUtils.getPackageNameOf(RMIVariables.class);

	private static final String PROXY_MARKER_CLASS_NAME = "saker.rmi.ProxyMarker";

	static final int NO_OBJECT_ID = -1;

	private static final AtomicIntegerFieldUpdater<RMIVariables> AIFU_objectIdProvider = AtomicIntegerFieldUpdater
			.newUpdater(RMIVariables.class, "objectIdProvider");

	private static final AtomicIntegerFieldUpdater<RMIVariables> AIFU_state = AtomicIntegerFieldUpdater
			.newUpdater(RMIVariables.class, "state");
	/**
	 * Flag set in {@link #state} if this {@link RMIVariables} is closed.
	 */
	private static final int STATE_BIT_CLOSED = 1 << 31;
	/**
	 * Flag set in {@link #state} if this {@link RMIVariables} should be closed after the last ongoing request is done.
	 */
	private static final int STATE_BIT_ABORTING = 1 << 30;
	/**
	 * Mask for {@link #state} holding the ongoing request count.
	 */
	private static final int STATE_MASK_ONGOING_REQUEST_COUNT = (1 << 30) - 1;

	private final RMIConnection connection;
	private final Object refSync = new Object();
	/**
	 * Access while locked on {@link #refSync}.
	 */
	private final Map<IdentityEqWeakReference<?>, LocalObjectReference> localObjectsToLocalReferences = new HashMap<>();
	private final ConcurrentNavigableMap<Integer, LocalObjectReference> localIdentifiersToLocalObjects = new ConcurrentSkipListMap<>();

	private final ConcurrentNavigableMap<Integer, RemoteProxyReference> cachedRemoteProxies = new ConcurrentSkipListMap<>();

	private RMIProxyClassLoader proxyBaseClassLoader;
	private ConcurrentHashMap<Set<Class<?>>, Constructor<? extends RemoteProxyObject>> proxyConstructors = new ConcurrentHashMap<>();
	private Class<?> proxyMarkerClass;
	private MethodHandles.Lookup markerClassLookup;
	private Map<Set<ClassLoader>, RMIClassDefiner> multiProxyClassDefiners = new HashMap<>();
	private final Object proxyGeneratorLock = new Object();
	private int proxyNameIdCounter = 0;

	private final int localIdentifier;
	private final int remoteIdentifier;

	@SuppressWarnings("unused")
	private volatile int objectIdProvider = 1;

	private final RMIStream stream;

	private final ReferenceQueue<Object> gcReferenceQueue = new ReferenceQueue<>();

	private final WeakReference<RMIVariables> gcThreadThisWeakReference = new WeakReference<>(this, gcReferenceQueue);

	/**
	 * Holds the closed, aborting flags and ongoing request count value.
	 * 
	 * @see #STATE_BIT_ABORTING
	 * @see #STATE_BIT_CLOSED
	 * @see #STATE_MASK_ONGOING_REQUEST_COUNT
	 */
	private volatile int state;

	private RMITransferPropertiesHolder properties;

	/**
	 * A non-reentrant lock for writing a references released command.
	 * <p>
	 * This field is kept on a per RMIVariables basis instead of a per RMIStream basis, as the objects are tracked in
	 * variables contexts.
	 * <p>
	 * The waiting on this sempahore is interruptable when writing the gc command, but is acquired uninterruptibly when
	 * waiting to write command other than gc. This is because the semaphore should be available quickly after a gc
	 * command, so interruption handling is not strictly necessary in case an other commands, however, in case when
	 * writing a gc command, it can be handled on the gc thread.
	 */
	protected final Semaphore gcCommandSemaphore = new Semaphore(1);

	RMIVariables(int localIdentifier, int remoteIdentifier, RMIConnection connection, RMIStream stream) {
		//XXX we could allow the user to add custom transfer properties just for this variables instance
		this.stream = stream;
		this.properties = AutoCreatingRMITransferProperties.create(connection.getProperties());
		this.localIdentifier = localIdentifier;
		this.remoteIdentifier = remoteIdentifier;
		this.connection = connection;
		this.proxyBaseClassLoader = new RMIProxyClassLoader(RMI_PROXY_CLASSLOADER_PARENT);
		boolean hasstatistics = connection.isStatisticsCollected();
		this.proxyMarkerClass = this.proxyBaseClassLoader.defineClass(PROXY_MARKER_CLASS_NAME,
				ProxyGenerator.generateProxyMarkerClass(PROXY_MARKER_CLASS_NAME, hasstatistics));
		this.markerClassLookup = MethodHandles.lookup().in(proxyMarkerClass);
		if (hasstatistics) {
			try {
				this.proxyMarkerClass.getField(ProxyGenerator.PROXY_MARKER_RMI_STATISTICS_FIELD_NAME).set(null,
						connection.getCollectingStatistics());
			} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
				throw new AssertionError("Failed to set field for RMI statistics collection.", e);
			}
		}

		//dont inline this variable, or this RMIVariables is going to be strong referenced from the thread
		ReferenceQueue<Object> refqueue = gcReferenceQueue;
		WeakReference<RMIVariables> ref = gcThreadThisWeakReference;
		connection.offerVariablesTask(() -> runGcThread(refqueue, ref));
	}

	/**
	 * Checks if this variables context is closed.
	 * 
	 * @return <code>true</code> if closed.
	 */
	public boolean isClosed() {
		return (state & (STATE_BIT_CLOSED | STATE_BIT_ABORTING)) != 0;
	}

	/**
	 * Gets the RMI connection this variables context is used with.
	 * 
	 * @return The connection.
	 */
	public RMIConnection getConnection() {
		return connection;
	}

	/**
	 * Closes this RMI variables.
	 * <p>
	 * Closing of a variables context occurs asynchronously. All concurrent requests will be finished, and then the
	 * variables will be completely closed. When that occurs, no more remote method calls can be instantiated through
	 * it.
	 * <p>
	 * This method never throws an exception.
	 */
	@Override
	public void close() {
		while (true) {
			int state = this.state;
			if (((state & STATE_BIT_ABORTING) == STATE_BIT_ABORTING)) {
				//already aborting, actual closing is done in either the previous close() call
				//or when no more requests are ongoing 
				return;
			}
			if ((state & STATE_MASK_ONGOING_REQUEST_COUNT) == 0) {
				//no ongoing requests, close right away
				if (!AIFU_state.compareAndSet(this, state, state | STATE_BIT_ABORTING | STATE_BIT_CLOSED)) {
					//try again
					continue;
				}
				closeWithNoOngoingRequest();
			} else {
				//still some ongoing requests, only set aborting flag
				if (!AIFU_state.compareAndSet(this, state, state | STATE_BIT_ABORTING)) {
					//try again
					continue;
				}
			}
			break;
		}
	}

	/**
	 * Gets a named remote variable from the other RMI endpoint.
	 * <p>
	 * The remote variable is always retrieved as a remote proxy. No exceptions to this, transfer properties are not
	 * taken into account.
	 * 
	 * @param variablename
	 *            The name of the variable.
	 * @return The found remote variable or <code>null</code> if it is not set.
	 * @throws RMIIOFailureException
	 *             In case of I/O error.
	 * @see RMIConnection#putContextVariable(String, Object)
	 */
	public Object getRemoteContextVariable(String variablename) throws RMIIOFailureException {
		addOngoingRequest();
		try {
			return stream.getRemoteContextVariable(this, variablename);
		} catch (IOException e) {
			throw new RMIIOFailureException(e);
		} finally {
			removeOngoingRequest();
		}
	}

	/**
	 * Creates a new instance of the given class on the remote endpoint, and returns it as a remote object.
	 * <p>
	 * The no-arg constructor will be used when creating the object.
	 * <p>
	 * The return value of this method should be only casted to interfaces which are implemented by the constructed
	 * object.
	 * 
	 * @param type
	 *            The type to create an instance of.
	 * @return The created object as a remote proxy.
	 * @throws RMIRuntimeException
	 *             If the RMI call failed.
	 * @throws InvocationTargetException
	 *             If the constructor threw an exception.
	 * @throws NoSuchMethodException
	 *             If a no-arg constructor was not found.
	 * @see RMIOptions#allowDirectRequests(boolean)
	 */
	public Object newRemoteInstance(Class<?> type)
			throws RMIRuntimeException, InvocationTargetException, NoSuchMethodException {
		return newRemoteInstance(type.getDeclaredConstructor());
	}

	/**
	 * Creates a new instance on the remote endpoint, and returns it as a remote object.
	 * <p>
	 * The passed constructor will be invoked with the specified arguments. The constructor transfer properties based on
	 * the connection configuration will be used when transferring the arguments.
	 * <p>
	 * The return value of this method should be only casted to interfaces which are implemented by the constructed
	 * object.
	 * 
	 * @param constructor
	 *            The constructor to use when creating the object.
	 * @param arguments
	 *            The arguments to pass to the constructor.
	 * @return The created object as a remote proxy.
	 * @throws RMIRuntimeException
	 *             If the RMI call failed.
	 * @throws InvocationTargetException
	 *             If the constructor threw an exception.
	 * @see RMIOptions#allowDirectRequests(boolean)
	 */
	public Object newRemoteInstance(Constructor<?> constructor, Object... arguments)
			throws RMIRuntimeException, InvocationTargetException {
		return newRemoteInstance(getPropertiesCheckClosed().getExecutableProperties(constructor), arguments);
	}

	/**
	 * Creates a new instance on the remote endpoint, and returns it as a remote object.
	 * <p>
	 * The passed constructor with its transfer properties will be invoked with the specified arguments.
	 * <p>
	 * The return value of this method should be only casted to interfaces which are implemented by the constructed
	 * object.
	 * 
	 * @param constructor
	 *            The constructor to use when creating the object.
	 * @param arguments
	 *            The arguments to pass to the constructor.
	 * @return The created object as a remote proxy.
	 * @throws RMIRuntimeException
	 *             If the RMI call failed.
	 * @throws InvocationTargetException
	 *             If the constructor threw an exception.
	 * @see RMIOptions#allowDirectRequests(boolean)
	 */
	public Object newRemoteInstance(ConstructorTransferProperties<?> constructor, Object... arguments)
			throws RMIRuntimeException, InvocationTargetException {
		addOngoingRequest();
		try {
			return stream.newRemoteInstance(this, constructor, arguments);
		} finally {
			removeOngoingRequest();
		}
	}

	/**
	 * Invokes a static method with the given arguments.
	 * <p>
	 * The method transfer properties based on the connection configuration will be used when transferring the objects.
	 * <p>
	 * The return value will be transferred based on the associated method transfer properties for the other endpoint.
	 * 
	 * @param method
	 *            The method to invoke.
	 * @param arguments
	 *            The arguments to pass to the invoked method.
	 * @return The result of the invocation.
	 * @throws RMIRuntimeException
	 *             If the RMI call failed.
	 * @throws InvocationTargetException
	 *             If the method threw an exception.
	 * @see #invokeRemoteStaticMethod(MethodTransferProperties, Object...)
	 * @see RMIOptions#allowDirectRequests(boolean)
	 */
	public Object invokeRemoteStaticMethod(Method method, Object... arguments)
			throws RMIRuntimeException, InvocationTargetException {
		return invokeRemoteStaticMethod(getPropertiesCheckClosed().getExecutableProperties(method), arguments);
	}

	/**
	 * Invokes a static method with the given arguments.
	 * <p>
	 * The passed transfer properties will be used when transferring the objects. Every invocation property is ignored.
	 * <p>
	 * The return value will be transferred based on the associated method transfer properties for the other endpoint.
	 * 
	 * @param method
	 *            The method to invoke.
	 * @param arguments
	 *            The arguments to pass to the invoked method.
	 * @return The result of the invocation.
	 * @throws RMIRuntimeException
	 *             If the RMI call failed.
	 * @throws InvocationTargetException
	 *             If the method threw an exception.
	 * @see RMIOptions#allowDirectRequests(boolean)
	 */
	public Object invokeRemoteStaticMethod(MethodTransferProperties method, Object... arguments)
			throws RMIRuntimeException, InvocationTargetException {
		addOngoingRequest();
		try {
			return stream.callMethod(this, NO_OBJECT_ID, method, arguments);
		} finally {
			removeOngoingRequest();
		}
	}

	/**
	 * Invokes the specified method remotely on the given remote object with the passed arguments.
	 * <p>
	 * The method transfer properties will be determined based on the RMI connection configuration.
	 * {@linkplain MethodTransferProperties#isDefaultOnFailure() Default implementations} are not called,
	 * {@linkplain MethodTransferProperties#getRedirectMethod() method redirections} are not invoked, call results are
	 * not {@linkplain MethodTransferProperties#isCacheResult() cached}, and exceptions are not
	 * {@linkplain MethodTransferProperties#getRMIExceptionRethrowConstructor() rethrown}.
	 * <p>
	 * If the method is {@linkplain MethodTransferProperties#isForbidden() forbidden} to be called remotely, an
	 * {@linkplain RMICallForbiddenException exception} will be thrown.
	 * <p>
	 * The return value will be transferred based on the associated method transfer properties for the other endpoint.
	 * 
	 * @param remoteobject
	 *            The remote object.
	 * @param method
	 *            The method to call.
	 * @param arguments
	 *            The arguments to pass to the invoked method.
	 * @return The result of the invocation.
	 * @throws RMIRuntimeException
	 *             If the RMI call failed.
	 * @throws InvocationTargetException
	 *             If the method threw an exception.
	 * @throws IllegalArgumentException
	 *             If the argument is not a remote object.
	 * @see #invokeRemoteMethod(Object, MethodTransferProperties, Object...)
	 * @see RMIOptions#allowDirectRequests(boolean)
	 */
	public static Object invokeRemoteMethod(Object remoteobject, Method method, Object... arguments)
			throws RMIRuntimeException, InvocationTargetException, IllegalArgumentException {
		if (!(remoteobject instanceof RemoteProxyObject)) {
			throw new IllegalArgumentException("Object is not a remote proxy.");
		}
		RemoteProxyObject remoteproxyobj = (RemoteProxyObject) remoteobject;
		RMIVariables variables = RemoteProxyObject.getCheckVariables(remoteproxyobj);
		try {
			return variables.invokeRemoteMethodInternal(remoteproxyobj.remoteId,
					variables.getPropertiesCheckClosed().getExecutableProperties(method), arguments);
		} finally {
			RemoteProxyObject.reachabilityFence(remoteproxyobj);
		}
	}

	/**
	 * Invokes the specified method remotely on the given remote object with the passed arguments.
	 * <p>
	 * The passed transfer properties will be used when transferring the objects.
	 * {@linkplain MethodTransferProperties#isDefaultOnFailure() Default implementations} are not called,
	 * {@linkplain MethodTransferProperties#getRedirectMethod() method redirections} are not invoked, call results are
	 * not {@linkplain MethodTransferProperties#isCacheResult() cached}, and exceptions are not
	 * {@linkplain MethodTransferProperties#getRMIExceptionRethrowConstructor() rethrown}.
	 * <p>
	 * If the method is {@linkplain MethodTransferProperties#isForbidden() forbidden} to be called remotely, an
	 * {@linkplain RMICallForbiddenException exception} will be thrown.
	 * <p>
	 * The return value will be transferred based on the associated method transfer properties for the other endpoint.
	 * 
	 * @param remoteobject
	 *            The remote object.
	 * @param method
	 *            The method to call.
	 * @param arguments
	 *            The arguments to pass to the invoked method.
	 * @return The result of the invocation.
	 * @throws RMIRuntimeException
	 *             If the RMI call failed.
	 * @throws InvocationTargetException
	 *             If the method threw an exception.
	 * @throws IllegalArgumentException
	 *             If the argument is not a remote object.
	 * @see RMIOptions#allowDirectRequests(boolean)
	 */
	public static Object invokeRemoteMethod(Object remoteobject, MethodTransferProperties method, Object... arguments)
			throws RMIRuntimeException, InvocationTargetException, IllegalArgumentException {
		if (!(remoteobject instanceof RemoteProxyObject)) {
			throw new IllegalArgumentException("Object is not a remote proxy.");
		}
		RemoteProxyObject remoteproxyobj = (RemoteProxyObject) remoteobject;
		RMIVariables variables = RemoteProxyObject.getCheckVariables(remoteproxyobj);
		try {
			return variables.invokeRemoteMethodInternal(remoteproxyobj.remoteId, method, arguments);
		} finally {
			RemoteProxyObject.reachabilityFence(remoteproxyobj);
		}
	}

	/**
	 * Invokes the specified method on the given remote object with the passed arguments.
	 * <p>
	 * The method transfer properties will be determined based on the RMI connection configuration.
	 * {@linkplain MethodTransferProperties#isDefaultOnFailure() Default implementations} are not called, call results
	 * are not {@linkplain MethodTransferProperties#isCacheResult() cached}, and exceptions are not
	 * {@linkplain MethodTransferProperties#getRMIExceptionRethrowConstructor() rethrown}.
	 * <p>
	 * {@linkplain MethodTransferProperties#getRedirectMethod() Method redirections} are invoked accordingly, and if the
	 * method is {@linkplain MethodTransferProperties#isForbidden() forbidden} to be called remotely, an
	 * {@linkplain RMICallForbiddenException exception} will be thrown.
	 * <p>
	 * The return value will be transferred based on the associated method transfer properties for the other endpoint.
	 * 
	 * @param remoteobject
	 *            The remote object.
	 * @param method
	 *            The method to call.
	 * @param arguments
	 *            The arguments to pass to the invoked method.
	 * @return The result of the invocation.
	 * @throws RMIRuntimeException
	 *             If the RMI call failed.
	 * @throws InvocationTargetException
	 *             If the method threw an exception.
	 * @throws IllegalArgumentException
	 *             If the argument is not a remote object.
	 * @see #invokeMethod(Object, MethodTransferProperties, Object...)
	 * @see RMIOptions#allowDirectRequests(boolean)
	 */
	public static Object invokeMethod(Object remoteobject, Method method, Object... arguments)
			throws RMIRuntimeException, InvocationTargetException, IllegalArgumentException {
		if (!(remoteobject instanceof RemoteProxyObject)) {
			throw new IllegalArgumentException("Object is not a remote proxy.");
		}
		RemoteProxyObject remoteproxyobj = (RemoteProxyObject) remoteobject;
		RMIVariables variables = RemoteProxyObject.getCheckVariables(remoteproxyobj);
		try {
			return variables.invokeMethod(remoteproxyobj.remoteId, remoteobject,
					variables.getPropertiesCheckClosed().getExecutableProperties(method), arguments);
		} finally {
			RemoteProxyObject.reachabilityFence(remoteproxyobj);
		}
	}

	/**
	 * Invokes the specified method on the given remote object with the passed arguments.
	 * <p>
	 * The passed transfer properties will be used when transferring the objects.
	 * {@linkplain MethodTransferProperties#isDefaultOnFailure() Default implementations} are not called, call results
	 * are not {@linkplain MethodTransferProperties#isCacheResult() cached}, and exceptions are not
	 * {@linkplain MethodTransferProperties#getRMIExceptionRethrowConstructor() rethrown}.
	 * <p>
	 * {@linkplain MethodTransferProperties#getRedirectMethod() Method redirections} are invoked accordingly, and if the
	 * method is {@linkplain MethodTransferProperties#isForbidden() forbidden} to be called remotely, an
	 * {@linkplain RMICallForbiddenException exception} will be thrown.
	 * <p>
	 * The return value will be transferred based on the associated method transfer properties for the other endpoint.
	 * 
	 * @param remoteobject
	 *            The remote object.
	 * @param method
	 *            The method to call.
	 * @param arguments
	 *            The arguments to pass to the invoked method.
	 * @return The result of the invocation.
	 * @throws RMIRuntimeException
	 *             If the RMI call failed.
	 * @throws InvocationTargetException
	 *             If the method threw an exception.
	 * @throws IllegalArgumentException
	 *             If the argument is not a remote object.
	 * @see RMIOptions#allowDirectRequests(boolean)
	 */
	public static Object invokeMethod(Object remoteobject, MethodTransferProperties method, Object... arguments)
			throws RMIRuntimeException, InvocationTargetException, IllegalArgumentException {
		if (!(remoteobject instanceof RemoteProxyObject)) {
			throw new IllegalArgumentException("Object is not a remote proxy.");
		}
		RemoteProxyObject remoteproxyobj = (RemoteProxyObject) remoteobject;
		RMIVariables variables = RemoteProxyObject.getCheckVariables(remoteproxyobj);
		try {
			return variables.invokeMethod(remoteproxyobj.remoteId, remoteobject, method, arguments);
		} finally {
			RemoteProxyObject.reachabilityFence(remoteproxyobj);
		}
	}

	/**
	 * Asynchronously invokes the speified method remotely on the given remote object with the passed arguments.
	 * <p>
	 * Asynchronous invocation means that this method will not wait for the remote method execution to complete, and may
	 * return before the invocation request even arrives at the other endpoint. The return value of the invocation will
	 * not be transferred back to the caller. There will be no notification if the method invocation finished,
	 * completed, or failed.
	 * <p>
	 * The caller cannot be sure that the method was ever invoked at any point during the lifetime of the RMI
	 * connection. It may happen that the invocation request never arrives before the closing or abrupt disconnection of
	 * this connection.
	 * <p>
	 * If the remote endpoint fails to read the argument objects or the method execution fails, the resulting exception
	 * is <b>silently ignored</b>.
	 * <p>
	 * However, if the asynchronous method invocation fails due to the network connection breaking up, then the client
	 * can be sure that any pending, or subsequent invocations will eventually throw an appropriate RMI exception
	 * signalling the abrupt disconnection.
	 * <p>
	 * It is recommended that clients use asynchronous invocation only for notification purposes, where it is
	 * unnecessary to block the caller by waiting for the response. It is recommended to externally synchronize on some
	 * state to ensure that the asynchronous invocation actually went through. It is also recommended to properly test
	 * and debug any transfer errors that might occurr during invocations. In order to avoid transfer errors, it is
	 * recommended to only use <code>void</code> no-arg methods.
	 * <p>
	 * The method transfer properties will be determined based on the RMI connection configuration.
	 * {@linkplain MethodTransferProperties#isDefaultOnFailure() Default implementations} are not called,
	 * {@linkplain MethodTransferProperties#getRedirectMethod() method redirections} are not invoked, call results are
	 * not {@linkplain MethodTransferProperties#isCacheResult() cached}, and exceptions are not
	 * {@linkplain MethodTransferProperties#getRMIExceptionRethrowConstructor() rethrown}.
	 * <p>
	 * If the method is {@linkplain MethodTransferProperties#isForbidden() forbidden} to be called remotely, an
	 * {@linkplain RMICallForbiddenException exception} will be thrown.
	 * <p>
	 * The return value or exceptions are not transferred.
	 * 
	 * @param remoteobject
	 *            The remote object to invoke the method on.
	 * @param method
	 *            The method to invoke.
	 * @param arguments
	 *            The arguments to pass to the invoked method.
	 * @throws RMIIOFailureException
	 *             In case of I/O error.
	 * @throws RMIObjectTransferFailureException
	 *             If writing the arguments fail.
	 * @throws IllegalArgumentException
	 *             If the object is not a remote proxy.
	 */
	public static void invokeRemoteMethodAsync(Object remoteobject, Method method, Object... arguments)
			throws RMIIOFailureException, RMIObjectTransferFailureException, IllegalArgumentException {
		if (!(remoteobject instanceof RemoteProxyObject)) {
			throw new IllegalArgumentException("Object is not a remote proxy.");
		}
		RemoteProxyObject remoteproxyobj = (RemoteProxyObject) remoteobject;
		RMIVariables variables = RemoteProxyObject.getCheckVariables(remoteproxyobj);
		try {
			variables.invokeRemoteMethodAsync(remoteproxyobj.remoteId,
					variables.getPropertiesCheckClosed().getExecutableProperties(method), arguments);
		} finally {
			RemoteProxyObject.reachabilityFence(remoteproxyobj);
		}
	}

	/**
	 * Asynchronously invokes the speified method remotely on the given remote object with the passed arguments.
	 * <p>
	 * See {@link #invokeRemoteMethodAsync(Object, Method, Object...)} for more information.
	 * 
	 * @param remoteobject
	 *            The remote object to invoke the method on.
	 * @param method
	 *            The method to invoke.
	 * @param arguments
	 *            The arguments to pass to the invoked method.
	 * @throws RMIIOFailureException
	 *             In case of I/O error.
	 * @throws RMIObjectTransferFailureException
	 *             If writing the arguments fail.
	 * @throws IllegalArgumentException
	 *             If the object is not a remote proxy.
	 */
	public static void invokeRemoteMethodAsync(Object remoteobject, MethodTransferProperties method,
			Object... arguments)
			throws RMIIOFailureException, RMIObjectTransferFailureException, IllegalArgumentException {
		if (!(remoteobject instanceof RemoteProxyObject)) {
			throw new IllegalArgumentException("Object is not a remote proxy.");
		}
		RemoteProxyObject remoteproxyobj = (RemoteProxyObject) remoteobject;
		RMIVariables variables = RemoteProxyObject.getCheckVariables(remoteproxyobj);
		try {
			variables.invokeRemoteMethodAsync(remoteproxyobj.remoteId, method, arguments);
		} finally {
			RemoteProxyObject.reachabilityFence(remoteproxyobj);
		}
	}

	/**
	 * Same as {@link #invokeRemoteMethodAsync(Object, Method, Object...)}, but handles if the object is not remote.
	 * <p>
	 * If the object is not remote, then the method will be called without any RMI transferring.
	 * 
	 * @param object
	 *            The object to invoke the method on.
	 * @param method
	 *            The method to invoke.
	 * @param arguments
	 *            The arguments to pass to the invoked method.
	 * @throws RMIIOFailureException
	 *             In case of I/O error.
	 * @throws RMIObjectTransferFailureException
	 *             If writing the arguments fail.
	 * @throws InvocationTargetException
	 *             If the local method invocation throws an exception.
	 * @throws IllegalAccessException
	 *             If the local method invocation throws an exception.
	 * @throws IllegalArgumentException
	 *             If the local method invocation throws an exception.
	 */
	public static void invokeRemoteMethodAsyncOrLocal(Object object, Method method, Object... arguments)
			throws RMIIOFailureException, RMIObjectTransferFailureException, InvocationTargetException,
			IllegalAccessException, IllegalArgumentException {
		if (!(object instanceof RemoteProxyObject)) {
			method.invoke(object, arguments);
			return;
		}
		RemoteProxyObject remoteproxyobj = (RemoteProxyObject) object;
		RMIVariables variables = RemoteProxyObject.getCheckVariables(remoteproxyobj);
		try {
			variables.invokeRemoteMethodAsync(remoteproxyobj.remoteId,
					variables.getPropertiesCheckClosed().getExecutableProperties(method), arguments);
		} finally {
			RemoteProxyObject.reachabilityFence(remoteproxyobj);
		}
	}

	/**
	 * Same as {@link #invokeRemoteMethodAsync(Object, MethodTransferProperties, Object...)}, but handles if the object
	 * is not remote.
	 * <p>
	 * If the object is not remote, then the method will be called without any RMI transferring.
	 * 
	 * @param object
	 *            The object to invoke the method on.
	 * @param method
	 *            The method to invoke.
	 * @param arguments
	 *            The arguments to pass to the invoked method.
	 * @throws RMIIOFailureException
	 *             In case of I/O error.
	 * @throws RMIObjectTransferFailureException
	 *             If writing the arguments fail.
	 * @throws InvocationTargetException
	 *             If the local method invocation throws an exception.
	 * @throws IllegalAccessException
	 *             If the local method invocation throws an exception.
	 * @throws IllegalArgumentException
	 *             If the local method invocation throws an exception.
	 */
	public static void invokeRemoteMethodAsyncOrLocal(Object object, MethodTransferProperties method,
			Object... arguments) throws RMIIOFailureException, RMIObjectTransferFailureException,
			InvocationTargetException, IllegalAccessException, IllegalArgumentException {
		if (!(object instanceof RemoteProxyObject)) {
			method.getExecutable().invoke(object, arguments);
			return;
		}
		RemoteProxyObject remoteproxyobj = (RemoteProxyObject) object;
		RMIVariables variables = RemoteProxyObject.getCheckVariables(remoteproxyobj);
		try {
			variables.invokeRemoteMethodAsync(remoteproxyobj.remoteId, method, arguments);
		} finally {
			RemoteProxyObject.reachabilityFence(remoteproxyobj);
		}
	}

	/**
	 * Creates a new object on the remote endpoint and returns it as a remote proxy object.
	 * <p>
	 * This method works the same way as {@link #newRemoteOnlyInstance(Object, String, String[], Object[])}, with
	 * <code>null</code> classloader, and arguments.
	 * <p>
	 * The no-arg constructor will be used to instantiate the object.
	 * 
	 * @param classname
	 *            The name of the class to instantiate.
	 * @return The created object.
	 * @throws RMIRuntimeException
	 *             If the RMI call failed.
	 * @throws InvocationTargetException
	 *             If the constructor threw an exception.
	 * @throws NullPointerException
	 *             If the class name is <code>null</code>.
	 * @see RMIOptions#allowDirectRequests(boolean)
	 */
	public Object newRemoteOnlyInstance(String classname)
			throws RMIRuntimeException, InvocationTargetException, NullPointerException {
		return newRemoteOnlyInstance(null, classname, ObjectUtils.EMPTY_STRING_ARRAY, ObjectUtils.EMPTY_OBJECT_ARRAY);
	}

	/**
	 * Creates a new object on the remote endpoint and returns it as a remote proxy object.
	 * <p>
	 * This method works the same way as {@link #newRemoteOnlyInstance(Object, String, String[], Object[])}, with
	 * <code>null</code> arguments.
	 * <p>
	 * The no-arg constructor will be used to instantiate the object.
	 * 
	 * @param remoteclassloader
	 *            The remote classloader or <code>null</code>. Must be bound to this variables context.
	 * @param classname
	 *            The name of the class to instantiate.
	 * @return The created object.
	 * @throws RMIRuntimeException
	 *             If the RMI call failed.
	 * @throws InvocationTargetException
	 *             If the constructor threw an exception.
	 * @throws NullPointerException
	 *             If the class name is <code>null</code>.
	 * @see RMIOptions#allowDirectRequests(boolean)
	 */
	public Object newRemoteOnlyInstance(Object remoteclassloader, String classname)
			throws RMIRuntimeException, InvocationTargetException, NullPointerException {
		return newRemoteOnlyInstance(remoteclassloader, classname, ObjectUtils.EMPTY_STRING_ARRAY,
				ObjectUtils.EMPTY_OBJECT_ARRAY);
	}

	/**
	 * Creates a new object on the remote endpoint and returns it as a remote proxy object.
	 * <p>
	 * This method works the same way as {@link #newRemoteOnlyInstance(Object, String, String[], Object[])}, with
	 * <code>null</code> classloader.
	 * 
	 * @param classname
	 *            The name of the class to instantiate.
	 * @param constructorargumentclasses
	 *            The class names of the constructor arguments. Passing <code>null</code> is the same as empty array.
	 * @param constructorarguments
	 *            The arguments to pass to the constructor. Passing <code>null</code> is the same as empty array.
	 * @return The created object.
	 * @throws RMIRuntimeException
	 *             If the RMI call failed.
	 * @throws InvocationTargetException
	 *             If the constructor threw an exception.
	 * @throws NullPointerException
	 *             If the class name is <code>null</code>.
	 * @see RMIOptions#allowDirectRequests(boolean)
	 */
	public Object newRemoteOnlyInstance(String classname, String[] constructorargumentclasses,
			Object[] constructorarguments) throws RMIRuntimeException, InvocationTargetException, NullPointerException {
		return newRemoteOnlyInstance(null, classname, constructorargumentclasses, constructorarguments);
	}

	/**
	 * Creates a new object on the remote endpoint and returns it as a remote proxy object.
	 * <p>
	 * This method should be used when the class of the constructed object is not present on this side of the
	 * connection. The actual object class is determined based on the passed remote classloader and the string
	 * representation of the class and argument class names.
	 * <p>
	 * The constructed object will be returned by casting it to the passed proxy type. The proxy type should be an
	 * interface that is knowingly implemented by the constructed object. The proxy type may be <code>null</code>, in
	 * which case the object will be returned without downcasting.
	 * <p>
	 * The method takes a remote classloader as its first argument. It should either be <code>null</code>, or a remote
	 * proxy object to a classloader on the remote endpoint. As classloaders usually doesn't implement any interfaces,
	 * any interfaces, it is passed as an raw object to this method. It is the responsibility of the caller to correctly
	 * retrieve the remote classloader from the other endpoint.
	 * <p>
	 * If the passed classloader is <code>null</code>, then the no classloader will be used.
	 * {@link Class#forName(String, boolean, ClassLoader)} with <code>null</code> ClassLoader argument will be used to
	 * lookup the classes, not the <code>null</code> classloader defined by the
	 * {@linkplain RMIOptions#nullClassLoader(ClassLoader) RMI options}.
	 * 
	 * @param remoteclassloader
	 *            The remote classloader or <code>null</code>. Must be bound to this variables context.
	 * @param classname
	 *            The name of the class to instantiate.
	 * @param constructorargumentclasses
	 *            The class names of the constructor arguments. Passing <code>null</code> is the same as empty array.
	 * @param constructorarguments
	 *            The arguments to pass to the constructor. Passing <code>null</code> is the same as empty array.
	 * @return The created object.
	 * @throws RMIRuntimeException
	 *             If the RMI call failed.
	 * @throws InvocationTargetException
	 *             If the constructor threw an exception.
	 * @throws NullPointerException
	 *             If the class name is <code>null</code>.
	 * @see RMIOptions#allowDirectRequests(boolean)
	 */
	public Object newRemoteOnlyInstance(Object remoteclassloader, String classname, String[] constructorargumentclasses,
			Object[] constructorarguments) throws RMIRuntimeException, InvocationTargetException, NullPointerException {
		Objects.requireNonNull(classname, "class name");
		int remoteclassloaderid;
		if (remoteclassloader == null) {
			remoteclassloaderid = NO_OBJECT_ID;
		} else {
			remoteclassloaderid = getRemoteClassloaderIdentifierForRemoteMethodInvocationOrThrow(remoteclassloader);
		}

		if (constructorargumentclasses == null) {
			constructorargumentclasses = ObjectUtils.EMPTY_STRING_ARRAY;
		}
		if (constructorarguments == null) {
			constructorarguments = ObjectUtils.EMPTY_OBJECT_ARRAY;
		}
		addOngoingRequest();
		try {
			return stream.newRemoteOnlyInstance(this, remoteclassloaderid, classname, constructorargumentclasses,
					constructorarguments);
		} finally {
			removeOngoingRequest();
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [" + localIdentifier + ":" + remoteIdentifier + "]";
	}

	boolean isAborting() {
		return (state & STATE_BIT_ABORTING) == STATE_BIT_ABORTING;
	}

	void addOngoingRequest() {
		while (true) {
			int state = this.state;
			if ((state & (STATE_BIT_CLOSED | STATE_BIT_ABORTING)) != 0) {
				throw new RMIIOFailureException("Variables is closed.");
			}
			int c = state & STATE_MASK_ONGOING_REQUEST_COUNT;
			//keep the flags, add one
			if (!AIFU_state.compareAndSet(this, state, (state & ~STATE_MASK_ONGOING_REQUEST_COUNT) | (c + 1))) {
				continue;
			}
			return;
		}
	}

	void removeOngoingRequest() {
		while (true) {
			int state = this.state;

			int c = state & STATE_MASK_ONGOING_REQUEST_COUNT;
			switch (c) {
				case 0: {
					throw new IllegalStateException("No ongoing requests.");
				}
				case 1: {
					if (((state & STATE_BIT_ABORTING) == STATE_BIT_ABORTING)) {
						//closing as well
						if (!AIFU_state.compareAndSet(this, state,
								(state & ~STATE_MASK_ONGOING_REQUEST_COUNT) | STATE_BIT_CLOSED)) {
							continue;
						}
						closeWithNoOngoingRequest();
						return;
					}
					//not closing, clear request count
					if (!AIFU_state.compareAndSet(this, state, state & ~STATE_MASK_ONGOING_REQUEST_COUNT)) {
						continue;
					}
					return;
				}
				default: {
					//keep the flags, subtract one
					if (!AIFU_state.compareAndSet(this, state, (state & ~STATE_MASK_ONGOING_REQUEST_COUNT) | (c - 1))) {
						continue;
					}
					return;
				}
			}
		}
	}

	Object invokeAllowedNonRedirectMethod(int remoteid, MethodTransferProperties method, Object[] arguments)
			throws InvocationTargetException {
		addOngoingRequest();
		try {
			return stream.callMethod(this, remoteid, method, arguments);
		} finally {
			removeOngoingRequest();
		}
	}

	private void invokeAllowedNonRedirectMethodAsync(int remoteid, MethodTransferProperties method, Object[] arguments)
			throws RMIIOFailureException {
		stream.callMethodAsync(this, remoteid, method, arguments);
	}

	static Object invokeRedirectMethod(Object remoteobject, Method redirectmethod, Object[] arguments)
			throws InvocationTargetException {
		Object[] nargs = prepareRedirectArguments(remoteobject, arguments);
		return invokeRedirectWithPreparedArguments(redirectmethod, nargs);
	}

	static Object[] prepareRedirectArguments(Object remoteobject, Object[] arguments) {
		if (ObjectUtils.isNullOrEmpty(arguments)) {
			return new Object[] { remoteobject };
		}
		Object[] nargs = new Object[arguments.length + 1];
		nargs[0] = remoteobject;
		System.arraycopy(arguments, 0, nargs, 1, arguments.length);
		return nargs;
	}

	static Object invokeRedirectWithPreparedArguments(Method redirectmethod, Object[] nargs)
			throws InvocationTargetException {
		try {
			return ReflectUtils.invokeMethod(null, redirectmethod, nargs);
		} catch (IllegalAccessException | IllegalArgumentException e) {
			throw new RMICallFailedException("Failed to invoke redirect method: " + redirectmethod, e);
		}
	}

	void referencesReleased(int localid, int count) {
		LocalObjectReference localref = localIdentifiersToLocalObjects.get(localid);
		if (localref == null) {
			throw new IllegalArgumentException("Local object doesn't exist with id: " + localid);
		}
		synchronized (localref) {
			int remoterefcount = localref.remoteReferenceCount;
			if (count > remoterefcount) {
				throw new IllegalArgumentException(
						"Released count: " + count + " is greater than remote reference count: " + remoterefcount);
			}
			int nremoterefcount = remoterefcount - count;
			localref.remoteReferenceCount = nremoterefcount;
			if (nremoterefcount == 0) {
				//local object is not referenced any more on the other side
				//null out reference to allow garbage collection
				localref.strongReference = null;
			}
		}
	}

	Object getProxyIncreaseReference(Class<?> requestedclass, int remoteid) throws AssertionError {
		RemoteProxyReference cached = cachedRemoteProxies.get(remoteid);
		if (cached != null) {
			Object res = cached.get();
			if (res != null) {
				RemoteProxyReference.AIFU_referenceCount.getAndIncrement(cached);
				return res;
			}
		}
		RemoteProxyObject result = createProxyObjectForClass(requestedclass, remoteid);
		return putCreatedProxyToCache(result, remoteid);
	}

	/**
	 * The interfaces must be all interfaces, public, and non assignable!
	 */
	Object getProxyIncreaseReference(Set<Class<?>> interfaces, int remoteid) throws AssertionError {
		RemoteProxyReference cached = cachedRemoteProxies.get(remoteid);
		if (cached != null) {
			Object res = cached.get();
			if (res != null) {
				RemoteProxyReference.AIFU_referenceCount.getAndIncrement(cached);
				return res;
			}
		}
		RemoteProxyObject result = createProxyObject(interfaces, remoteid);
		return putCreatedProxyToCache(result, remoteid);
	}

	Object getObjectWithLocalId(int localid) {
		LocalObjectReference ref = localIdentifiersToLocalObjects.get(localid);
		if (ref == null) {
			return null;
		}
		return ref.strongReference;
	}

	Object requireObjectWithLocalId(int localid) {
		LocalObjectReference ref = localIdentifiersToLocalObjects.get(localid);
		if (ref == null) {
			throw new RMICallFailedException("Remote object not found with id: " + localid + " (not tracked)");
		}
		Object result = ref.strongReference;
		if (result == null) {
			throw new RMICallFailedException("Remote object not found with id: " + localid + " (released reference)");
		}
		return result;
	}

	Integer getRemoteIdentifierForObject(Object obj) {
		if (proxyMarkerClass.isInstance(obj)) {
			RemoteProxyObject proxy = (RemoteProxyObject) obj;
			return proxy.remoteId;
		}
		return null;
	}

	/**
	 * Checks if this variables context strongly references the given argument as a local object.
	 * <p>
	 * Note: used for testing purposes.
	 * 
	 * @param localobject
	 *            The object.
	 * @return <code>true</code> if this object is strongly referenced by this variables instance.
	 */
	boolean isLocalObjectKnown(Object localobject) {
		IdentityRefSearcher refsearcher = new IdentityRefSearcher(localobject);
		synchronized (refSync) {
			@SuppressWarnings("unlikely-arg-type")
			LocalObjectReference gotobjref = localObjectsToLocalReferences.get(refsearcher);
			return gotobjref != null && gotobjref.strongReference != null;
		}
	}

	int getLocalInstanceIdIncreaseReference(Object localobject) {
		IdentityRefSearcher refsearcher = new IdentityRefSearcher(localobject);
		synchronized (refSync) {
			@SuppressWarnings("unlikely-arg-type")
			LocalObjectReference gotobjref = localObjectsToLocalReferences.get(refsearcher);
			if (gotobjref != null) {
				synchronized (gotobjref) {
					++gotobjref.remoteReferenceCount;
					if (gotobjref.strongReference == null) {
						gotobjref.strongReference = localobject;
					}
				}
				return gotobjref.localId;
			}
			int id = nextId();
			IdentityEqWeakReference<?> keyref = new IdentityEqWeakReference<>(localobject, gcReferenceQueue);
			LocalObjectReference localobjref = new LocalObjectReference(localobject, gcReferenceQueue, id);
			localObjectsToLocalReferences.put(keyref, localobjref);
			localIdentifiersToLocalObjects.put(id, localobjref);
			return id;
		}
	}

	int getLiveLocalObjectCount() {
		return localIdentifiersToLocalObjects.size();
	}

	int getLiveRemoteObjectCount() {
		return cachedRemoteProxies.size();
	}

	RMITransferPropertiesHolder getProperties() {
		return properties;
	}

	RMITransferPropertiesHolder getPropertiesCheckClosed() {
		RMITransferPropertiesHolder properties = this.properties;
		if (properties == null) {
			throw new RMICallFailedException("Variables is closed.");
		}
		return properties;
	}

	int getLocalIdentifier() {
		return localIdentifier;
	}

	int getRemoteIdentifier() {
		return this.remoteIdentifier;
	}

	MethodHandles.Lookup getMarkerClassLookup() {
		return markerClassLookup;
	}

	Object invokeRemoteMethodInternal(int remoteid, MethodTransferProperties method, Object[] arguments)
			throws RMIRuntimeException, InvocationTargetException {
		checkForbidden(method);
		return invokeAllowedNonRedirectMethod(remoteid, method, arguments);
	}

	RMIStream getStream() {
		return stream;
	}

	private static class LocalObjectReference extends WeakReference<Object> {
		final int localId;
		Object strongReference;

		/**
		 * Count of the references of this object on the remote side. It is increased when a remote handle is created on
		 * the remote side.
		 * <p>
		 * Accessed by synchronizing on <code>this</code>.
		 */
		int remoteReferenceCount = 1;

		public LocalObjectReference(Object referent, ReferenceQueue<? super Object> q, int localId) {
			super(referent, q);
			this.strongReference = referent;
			this.localId = localId;
		}

	}

	private static class RemoteProxyReference extends WeakReference<RemoteProxyObject> {
		private static final AtomicIntegerFieldUpdater<RemoteProxyReference> AIFU_referenceCount = AtomicIntegerFieldUpdater
				.newUpdater(RemoteProxyReference.class, "referenceCount");

		private final int remoteId;

		volatile int referenceCount = 1;

		public RemoteProxyReference(RemoteProxyObject referent, ReferenceQueue<? super Object> q, int remoteId) {
			super(referent, q);
			this.remoteId = remoteId;
		}

		public int getRemoteId() {
			return remoteId;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + remoteId + ": " + get() + "]";
		}
	}

	private static class IdentityRefSearcher {
		final Object object;
		final int hashCode;

		public IdentityRefSearcher(Object object) {
			this.object = object;
			this.hashCode = System.identityHashCode(object);
		}

		@Override
		public int hashCode() {
			return hashCode;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			Class<? extends Object> objclass = obj.getClass();
			if (objclass == IdentityEqWeakReference.class) {
				return this.object == ((IdentityEqWeakReference<?>) obj).get();
			}
			if (objclass == IdentityRefSearcher.class) {
				return this.object == ((IdentityRefSearcher) obj).object;
			}
			return false;
		}
	}

	private static class IdentityEqWeakReference<T> extends WeakReference<T> {
		private final int hashCode;

		public IdentityEqWeakReference(T referent, ReferenceQueue<? super T> q) {
			super(referent, q);
			this.hashCode = System.identityHashCode(referent);
		}

		@Override
		public int hashCode() {
			return hashCode;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			Class<? extends Object> objclass = obj.getClass();
			if (objclass == IdentityEqWeakReference.class) {
				return this.get() == ((IdentityEqWeakReference<?>) obj).get();
			}
			if (objclass == IdentityRefSearcher.class) {
				return this.get() == ((IdentityRefSearcher) obj).object;
			}
			return false;
		}
	}

	private Object invokeMethod(int remoteid, Object remoteobject, MethodTransferProperties method, Object[] arguments)
			throws RMIRuntimeException, InvocationTargetException {
		Method redirectmethod = method.getRedirectMethod();
		if (redirectmethod != null) {
			return invokeRedirectMethod(remoteobject, redirectmethod, arguments);
		}
		checkForbidden(method);
		return invokeAllowedNonRedirectMethod(remoteid, method, arguments);
	}

	private void invokeRemoteMethodAsync(int remoteid, MethodTransferProperties method, Object[] arguments)
			throws RMIIOFailureException {
		checkForbidden(method);
		invokeAllowedNonRedirectMethodAsync(remoteid, method, arguments);
	}

	private int getRemoteClassloaderIdentifierForRemoteMethodInvocationOrThrow(Object remoteclassloader) {
		if (proxyMarkerClass.isInstance(remoteclassloader)) {
			RemoteProxyObject proxy = (RemoteProxyObject) remoteclassloader;
			return proxy.remoteId;
		}
		if (RMIConnection.isRemoteObject(remoteclassloader)) {
			throw new RMICallFailedException("Remote classloader is not bound to this variables. (" + this + ")");
		}
		throw new RMICallFailedException("Classloader instance is not a remote object.");
	}

	private int nextId() {
		return AIFU_objectIdProvider.getAndIncrement(this);
	}

	private static Set<ClassLoader> getClassLoadersForClasses(Set<Class<?>> classes) {
		//use linkedhashset for deterministic class loader order
		Set<ClassLoader> result = new LinkedHashSet<>();
		for (Class<?> c : classes) {
			ClassLoader cl = c.getClassLoader();
			if (cl != null) {
				result.add(cl);
			}
		}
		return MultiClassLoader.reduceClassLoaders(result);
	}

	private Constructor<? extends RemoteProxyObject> getProxyConstructorForRequestedClass(Class<?> requestedclass) {
		Set<Class<?>> interfaces = getProxyInterfacesForRequestedClass(requestedclass);
		Constructor<? extends RemoteProxyObject> c = proxyConstructors.get(interfaces);
		if (c == null) {
			synchronized (proxyGeneratorLock) {
				c = proxyConstructors.get(interfaces);
				if (c == null) {
					Set<ClassLoader> classloaders = ImmutableUtils.singletonSet(requestedclass.getClassLoader());
					c = getProxyConstructorWithClassLoaders(interfaces, c, classloaders);
				}
			}
		}
		return c;
	}

	private Set<Class<?>> getProxyInterfacesForRequestedClass(Class<?> requestedclass) {
		return RMIStream.getPublicNonAssignableInterfaces(requestedclass, markerClassLookup,
				connection.getCollectingStatistics());
	}

	private Constructor<? extends RemoteProxyObject> getProxyConstructor(Set<Class<?>> interfaces) {
		Constructor<? extends RemoteProxyObject> c = proxyConstructors.get(interfaces);
		if (c == null) {
			synchronized (proxyGeneratorLock) {
				c = proxyConstructors.get(interfaces);
				if (c == null) {
					Set<ClassLoader> classloaders = getClassLoadersForClasses(interfaces);
					c = getProxyConstructorWithClassLoaders(interfaces, c, classloaders);
				}
			}
		}
		return c;
	}

	private Constructor<? extends RemoteProxyObject> getProxyConstructorWithClassLoaders(Set<Class<?>> interfaces,
			Constructor<? extends RemoteProxyObject> c, Set<ClassLoader> classloaders) throws AssertionError {
		RMIClassDefiner classdefiner = multiProxyClassDefiners.get(classloaders);
		if (classdefiner == null) {
			classdefiner = new MultiClassLoaderRMIProxyClassLoader(proxyBaseClassLoader, classloaders,
					markerClassLookup);
			multiProxyClassDefiners.put(classloaders, classdefiner);
		}
		String name = PROXY_PACKAGE_NAME + ".Proxy$" + proxyNameIdCounter++;
		@SuppressWarnings("unchecked")
		Class<? extends RemoteProxyObject> proxyclass = (Class<? extends RemoteProxyObject>) classdefiner
				.defineClass(name, ProxyGenerator.generateProxy(name, interfaces,
						proxyMarkerClass.getName().replace('.', '/'), properties, connection.isStatisticsCollected()));

		//Use method handle to retrieve the initialization static method instead of usual reflection
		//that is because if we use Class.getMethod(String), then it will load the classes related to the methods
		//present in the class
		//however, if some types are not available in some other methods, then it will throw an exception
		//e.g. if a proxy has a parent interface, that has a method:
		//     void myMethod(ClassWithPrivateModifier)
		//that means that ClassWithPrivateModifier won't be accessible to the proxy class, and
		//will therefore throw an IllegalAccessException, even though we don't use this method
		//this illegal access exception is valid, however, we want to delay throwing it until someone attempts to call it
		MethodHandle initmethod = null;
		try {
			initmethod = markerClassLookup.findStatic(proxyclass, ProxyGenerator.INITIALIZE_CACHE_FIELDS_METHOD_NAME,
					MethodType.methodType(void.class, RMITransferPropertiesHolder.class));
		} catch (NoSuchMethodException e) {
		} catch (IllegalAccessException | IllegalArgumentException | SecurityException e) {
			//none of these should be thrown, as the proxy generation should properly verify the interfaces
			//as the byte code are generated
			throw new AssertionError("Failed to initialize proxy class: " + interfaces, e);
		}
		if (initmethod != null) {
			try {
				initmethod.invokeExact(properties);
			} catch (Throwable e) {
				throw new AssertionError("Failed to initialize proxy class: " + interfaces, e);
			}
		}
		try {
			c = proxyclass.getConstructor(Reference.class, int.class);
		} catch (NoSuchMethodException | SecurityException e) {
			throw new AssertionError("Proxy constructor not found.", e);
		}
		proxyConstructors.put(interfaces, c);
		return c;
	}

	private RemoteProxyObject createProxyObjectForClass(Class<?> requestedclass, int remoteid) throws AssertionError {
		return createProxyObject(getProxyConstructorForRequestedClass(requestedclass), remoteid);
	}

	private RemoteProxyObject createProxyObject(Set<Class<?>> interfaces, int remoteid) throws AssertionError {
		return createProxyObject(getProxyConstructor(interfaces), remoteid);
	}

	private RemoteProxyObject createProxyObject(Constructor<? extends RemoteProxyObject> proxyconstructor, int remoteid)
			throws AssertionError {
		try {
			return proxyconstructor.newInstance(gcThreadThisWeakReference, remoteid);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException e) {
			throw new AssertionError("Failed to instantiate proxy.", e);
		} catch (InvocationTargetException e) {
			throw new AssertionError("Proxy instantiation failed.", e.getTargetException());
		}
	}

	private RemoteProxyObject putCreatedProxyToCache(RemoteProxyObject result, int remoteid) {
		RemoteProxyReference putreference = new RemoteProxyReference(result, gcReferenceQueue, remoteid);
		RemoteProxyReference cached = cachedRemoteProxies.get(remoteid);
		while (true) {
			if (cached == null) {
				RemoteProxyReference prev = cachedRemoteProxies.putIfAbsent(remoteid, putreference);
				if (prev != null) {
					RemoteProxyObject pgot = prev.get();
					if (pgot != null) {
						RemoteProxyReference.AIFU_referenceCount.getAndIncrement(prev);
						return pgot;
					}
					cached = prev;
				} else {
					//successfully put into map
					return result;
				}
			}
			boolean replaced = cachedRemoteProxies.replace(remoteid, cached, putreference);
			if (replaced) {
				return result;
			}
			cached = cachedRemoteProxies.get(remoteid);
			if (cached != null) {
				RemoteProxyObject res = cached.get();
				if (res != null) {
					RemoteProxyReference.AIFU_referenceCount.getAndIncrement(cached);
					return res;
				}
			}
		}
	}

	private static void runGcThread(ReferenceQueue<Object> refqueue, WeakReference<RMIVariables> varsref) {
		RMIConnection.clearContextClassLoaderOfCurrentThread();
		try {
			while (true) {
				Reference<? extends Object> ref = refqueue.remove();
				RMIVariables vars = varsref.get();
				if (vars == null) {
					break;
				}
				boolean cancontinue = vars.handleGcQueuedReference(ref);
				if (!cancontinue) {
					break;
				}
			}
		} catch (InterruptedException e) {
		}
	}

	private boolean handleGcQueuedReference(Reference<? extends Object> ref) throws InterruptedException {
		if (ref instanceof RemoteProxyReference) {
			RemoteProxyReference rpr = (RemoteProxyReference) ref;
			//reference to a proxy object became unreachable
			//remove it from the map
			int remoteid = rpr.getRemoteId();
			cachedRemoteProxies.remove(remoteid, rpr);
			//notify the remote connection about the unreachability
			try {
				stream.writeCommandReferencesReleased(this, remoteid, rpr.referenceCount);
			} catch (RMIIOFailureException | IOException e) {
				//IO error occurred when we were trying to write the released command
				//we expect that we wont be able to write any command to the streams
				//so we exit the gc thread
				//any remaining references are kept, until the variables instance is closed
				//    this does nothing wrong, but just keep some objects alive
				//    they are going to be cleaned up as the variables get closed
				return false;
			}
		} else if (ref instanceof LocalObjectReference) {
			LocalObjectReference lor = (LocalObjectReference) ref;
			localIdentifiersToLocalObjects.remove(lor.localId, ref);
		} else if (ref instanceof IdentityEqWeakReference) {
			synchronized (refSync) {
				localObjectsToLocalReferences.remove(ref);
			}
		} else if (ref == gcThreadThisWeakReference) {
			return false;
		}
		return true;
	}

	private static void checkForbidden(MethodTransferProperties method) {
		if (method.isForbidden()) {
			throw new RMICallForbiddenException(method.getExecutable().toString());
		}
	}

	/**
	 * Set {@link #STATE_BIT_CLOSED} before calling this!
	 */
	private void closeWithNoOngoingRequest() {
		//enqueue a reference to notify the gc thread about exiting
		gcThreadThisWeakReference.enqueue();

		connection.closeVariables(this);

		cachedRemoteProxies.clear();
		localIdentifiersToLocalObjects.clear();
		synchronized (refSync) {
			localObjectsToLocalReferences.clear();
		}
		proxyBaseClassLoader = null;
		proxyConstructors = null;
		proxyMarkerClass = null;
		markerClassLookup = null;
		multiProxyClassDefiners = null;
		properties = null;
	}

}
