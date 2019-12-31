package saker.build.thirdparty.saker.rmi.connection;

import java.io.IOException;
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
					RemoteProxyObject.RemoteInvocationRMIFailureException.class.getName()));

	private static final String PROXY_PACKAGE_NAME = ReflectUtils.getPackageNameOf(RMIVariables.class);

	private static final String PROXY_MARKER_CLASS_NAME = "saker.rmi.ProxyMarker";

	static final int NO_OBJECT_ID = -1;

	private static final AtomicIntegerFieldUpdater<RMIVariables> AIFU_objectIdProvider = AtomicIntegerFieldUpdater
			.newUpdater(RMIVariables.class, "objectIdProvider");
	private static final AtomicIntegerFieldUpdater<RMIVariables> AIFU_ongoingRequestCount = AtomicIntegerFieldUpdater
			.newUpdater(RMIVariables.class, "ongoingRequestCount");

	private final RMIConnection connection;
	private final Object refSync = new Object();
	private final Map<IdentityEqWeakReference<?>, LocalObjectReference> localObjectsToLocalReferences = new HashMap<>();
	private final ConcurrentNavigableMap<Integer, LocalObjectReference> localIdentifiersToLocalObjects = new ConcurrentSkipListMap<>();

	private final ConcurrentNavigableMap<Integer, RemoteProxyReference> cachedRemoteProxies = new ConcurrentSkipListMap<>();

	private RMIProxyClassLoader proxyBaseClassLoader;
	private ConcurrentHashMap<Set<Class<?>>, Constructor<? extends RemoteProxyObject>> proxyConstructors = new ConcurrentHashMap<>();
	private Class<?> proxyMarkerClass;
	private Map<Set<ClassLoader>, RMIClassDefiner> multiProxyClassDefiners = new HashMap<>();
	private final Object proxyGeneratorLock = new Object();
	private int proxyNameIdCounter = 0;

	private final int localIdentifier;
	private final int remoteIdentifier;

	@SuppressWarnings("unused")
	private volatile int objectIdProvider = 1;

	private final ReferenceQueue<Object> gcReferenceQueue = new ReferenceQueue<>();

	private final WeakReference<RMIVariables> gcThreadThisWeakReference = new WeakReference<>(this, gcReferenceQueue);

	private volatile boolean aborting = false;
	private volatile boolean closed = false;

	@SuppressWarnings("unused")
	private volatile int ongoingRequestCount = 0;

	private RMITransferPropertiesHolder properties;

	RMIVariables(int localIdentifier, int remoteIdentifier, RMIConnection connection) {
		//XXX we could allow the user to add custom transfer properties just for this variables instance
		this.properties = AutoCreatingRMITransferProperties.create(connection.getProperties());
		this.localIdentifier = localIdentifier;
		this.remoteIdentifier = remoteIdentifier;
		this.connection = connection;
		this.proxyBaseClassLoader = new RMIProxyClassLoader(RMI_PROXY_CLASSLOADER_PARENT);
		this.proxyMarkerClass = this.proxyBaseClassLoader.defineClass(PROXY_MARKER_CLASS_NAME,
				ProxyGenerator.generateProxyMarkerClass(PROXY_MARKER_CLASS_NAME));

		//dont inline this variable, or this RMIVariables is going to be strong referenced from the thread
		ReferenceQueue<Object> refqueue = gcReferenceQueue;
		WeakReference<RMIVariables> ref = gcThreadThisWeakReference;
		connection.getThreadWorkPool().offer(() -> runGcThread(refqueue, ref));
	}

	/**
	 * Checks if this variables context is closed.
	 * 
	 * @return <code>true</code> if closed.
	 */
	public boolean isClosed() {
		return aborting;
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
	 * variables will be completly closed. When that occurs, no more remote method calls can be instantiated through it.
	 * <p>
	 * This method never throws an exception.
	 */
	@Override
	public void close() {
		if (aborting) {
			return;
		}
		aborting = true;
		if (AIFU_ongoingRequestCount.compareAndSet(this, 0, -1)) {
			closeWithNoOngoingRequest();
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
		RMIStream stream = connection.getStream();
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
		return newRemoteInstance(this.properties.getExecutableProperties(constructor), arguments);
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
		RMIStream stream = connection.getStream();
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
		return invokeRemoteStaticMethod(this.properties.getExecutableProperties(method), arguments);
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
		RMIStream stream = connection.getStream();
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
		return variables.invokeRemoteMethod(remoteproxyobj.remoteId,
				variables.properties.getExecutableProperties(method), arguments);
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
		return variables.invokeRemoteMethod(remoteproxyobj.remoteId, method, arguments);
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
		return variables.invokeMethod(remoteproxyobj.remoteId, remoteobject,
				variables.properties.getExecutableProperties(method), arguments);
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
		return variables.invokeMethod(remoteproxyobj.remoteId, remoteobject, method, arguments);
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
		variables.invokeRemoteMethodAsync(remoteproxyobj.remoteId, variables.properties.getExecutableProperties(method),
				arguments);
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
		variables.invokeRemoteMethodAsync(remoteproxyobj.remoteId, method, arguments);
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
		variables.invokeRemoteMethodAsync(remoteproxyobj.remoteId, variables.properties.getExecutableProperties(method),
				arguments);
		return;
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
		variables.invokeRemoteMethodAsync(remoteproxyobj.remoteId, method, arguments);
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
		RMIStream stream = connection.getStream();
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
		return aborting;
	}

	void addOngoingRequest() {
		AIFU_ongoingRequestCount.updateAndGet(this, c -> {
			if (c < 0 || closed) {
				throw new RMIIOFailureException("Variables is closed.");
			}
			return c + 1;
		});
	}

	void removeOngoingRequest() {
		int res = AIFU_ongoingRequestCount.updateAndGet(this, c -> {
			if (c == 1 && aborting) {
				return -1;
			}
			return c - 1;
		});
		if (res < 0) {
			closeWithNoOngoingRequest();
		}
	}

	Object invokeAllowedNonRedirectMethod(int remoteid, MethodTransferProperties method, Object[] arguments)
			throws InvocationTargetException {
		addOngoingRequest();
		RMIStream stream = connection.getStream();
		try {
			return stream.callMethod(this, remoteid, method, arguments);
		} finally {
			removeOngoingRequest();
		}
	}

	private void invokeAllowedNonRedirectMethodAsync(int remoteid, MethodTransferProperties method, Object[] arguments)
			throws RMIIOFailureException {
		RMIStream stream = connection.getStream();
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
			if (count > localref.remoteReferenceCount) {
				throw new IllegalArgumentException("Released count: " + count
						+ " is greater than remote reference count: " + localref.remoteReferenceCount);
			}
			localref.remoteReferenceCount -= count;
			if (localref.remoteReferenceCount == 0) {
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

	Integer getRemoteIdentifierForObject(Object obj) {
		if (proxyMarkerClass.isInstance(obj)) {
			RemoteProxyObject proxy = (RemoteProxyObject) obj;
			return proxy.remoteId;
		}
		return null;
	}

	int getLocalInstanceIdIncreaseReference(Object localobject) {
		synchronized (refSync) {
			@SuppressWarnings("unlikely-arg-type")
			LocalObjectReference got = localObjectsToLocalReferences.get(new IdentityRefSearcher(localobject));
			if (got != null) {
				synchronized (got) {
					++got.remoteReferenceCount;
					if (got.strongReference == null) {
						got.strongReference = localobject;
					}
				}
				return got.localId;
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

	RMITransferPropertiesHolder getProperties() {
		return properties;
	}

	int getLocalIdentifier() {
		return localIdentifier;
	}

	int getRemoteIdentifier() {
		return this.remoteIdentifier;
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

	private Object invokeRemoteMethod(int remoteid, MethodTransferProperties method, Object[] arguments)
			throws RMIRuntimeException, InvocationTargetException {
		checkForbidden(method);
		return invokeAllowedNonRedirectMethod(remoteid, method, arguments);
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
		Set<Class<?>> interfaces = RMIStream.getPublicNonAssignableInterfaces(requestedclass);
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
			classdefiner = new MultiClassLoaderRMIProxyClassLoader(proxyBaseClassLoader, classloaders);
			multiProxyClassDefiners.put(classloaders, classdefiner);
		}
		String name = PROXY_PACKAGE_NAME + ".Proxy$" + proxyNameIdCounter++;
		@SuppressWarnings("unchecked")
		Class<? extends RemoteProxyObject> proxyclass = (Class<? extends RemoteProxyObject>) classdefiner
				.defineClass(name, ProxyGenerator.generateProxy(name, interfaces, proxyMarkerClass, properties));
		try {
			proxyclass.getMethod(ProxyGenerator.INITIALIZE_CACHE_FIELDS_METHOD_NAME, RMITransferPropertiesHolder.class)
					.invoke(null, properties);
		} catch (NoSuchMethodException e) {
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | SecurityException e) {
			throw new AssertionError("Failed to initialize proxy class: " + interfaces, e);
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
		Thread.currentThread().setContextClassLoader(null);
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

	private boolean handleGcQueuedReference(Reference<? extends Object> ref) {
		if (ref instanceof RemoteProxyReference) {
			RemoteProxyReference rpr = (RemoteProxyReference) ref;
			//reference to a proxy object became unreachable
			//remove it from the map
			int remoteid = rpr.getRemoteId();
			cachedRemoteProxies.remove(remoteid, rpr);
			//notify the remote connection about the unreachability
			try {
				connection.getStream().writeCommandReferencesReleased(this, remoteid, rpr.referenceCount);
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

	private void closeWithNoOngoingRequest() {
		closed = true;

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
		multiProxyClassDefiners = null;
		properties = null;
	}

}
