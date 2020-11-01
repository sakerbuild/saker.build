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
import java.net.SocketAddress;
import java.util.Objects;

import javax.net.SocketFactory;

import saker.build.thirdparty.saker.rmi.connection.RMIConnection.OnlyClassLoaderResolver;
import saker.build.thirdparty.saker.rmi.exception.RMICallForbiddenException;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolver;

/**
 * Class for specifying different aspects of an RMI connection.
 * <p>
 * This class can be used in a builder pattern where the user specifies various options for the RMI connection and then
 * requests the initiation of the connection via {@link #connect}.
 * <p>
 * For more information see the public builder methods in this class.
 */
public final class RMIOptions {
	ClassLoaderResolver classLoaderResolver;
	ClassLoader nullClassLoader = null;
	RMITransferProperties properties;
	ThreadGroup workerThreadGroup;
	int maxStreamCount = -1;
	boolean allowDirectRequests = true;
	boolean collectStatistics = false;

	/**
	 * Creates a new instance with default values.
	 */
	public RMIOptions() {
	}

	/**
	 * Creates a new instance with values copied from the parameter.
	 * 
	 * @param copy
	 *            The options to copy the values from.
	 */
	public RMIOptions(RMIOptions copy) {
		this.classLoaderResolver = copy.classLoaderResolver;
		this.nullClassLoader = copy.nullClassLoader;
		this.properties = copy.properties;
		this.workerThreadGroup = copy.workerThreadGroup;
		this.maxStreamCount = copy.maxStreamCount;
		this.allowDirectRequests = copy.allowDirectRequests;
		this.collectStatistics = copy.collectStatistics;
	}

	/**
	 * Simplified method for setting both the classloader resolver and <code>null</code> classloader.
	 * <p>
	 * The parameter classloader will be used for the <code>null</code> lookup, and it will be used as a single and only
	 * classloader to lookup the used classloaders.
	 * 
	 * @param cl
	 *            The classloader to use.
	 * @return <code>this</code>
	 * @see #nullClassLoader(ClassLoader)
	 * @see #classResolver(ClassLoaderResolver)
	 */
	public RMIOptions classLoader(ClassLoader cl) {
		this.classLoaderResolver = new OnlyClassLoaderResolver(cl);
		this.nullClassLoader = cl;
		return this;
	}

	/**
	 * Specifies the strategy for looking up classloaders during the connection.
	 * <p>
	 * An instance of {@link ClassLoaderResolver} is used to derive {@link String} based identifiers from the
	 * transferred classloaders and look them up on the remote endpoint.
	 * 
	 * @param resolver
	 *            The resolver to use.
	 * @return <code>this</code>
	 */
	public RMIOptions classResolver(ClassLoaderResolver resolver) {
		this.classLoaderResolver = resolver;
		return this;
	}

	/**
	 * Specifies the classloader to use if the remote endpoint passes <code>null</code> for the transferred classloader
	 * for a given class.
	 * <p>
	 * When classes are transmitted over the connection an identifier is used to locate the classloader of the
	 * corresponding class. Some classes can have <code>null</code> as their classloader. This can happen for example if
	 * the class was loaded using the bootstrap classloader on JRE 8. (E.g. <code>Runnable.class.getClassLoader()</code>
	 * returns <code>null</code>)
	 * <p>
	 * In these cases the user can specify a given classloader so the classes a properly resolved between JRE versions.
	 * <p>
	 * Other example use-case for this is:
	 * <p>
	 * On JDK8 a class returns <code>null</code> classloader. (<code>SQLException.class.getClassLoader()</code> is
	 * <code>null</code> on JDK8) <br>
	 * On JDK9+ a class return non-<code>null</code> classloader. (<code>SQLException.class.getClassLoader()</code> is
	 * non-<code>null</code> and is the same as <code>ClassLoader.getPlatformClassLoader()</code> on JDK9)
	 * <p>
	 * In this case if the communcation is between JDK8 and 9 processes, then specifying the platform classloader as the
	 * <code>null</code> classloader on the JDK9 endpoint will result in correct operation, while leaving it unset will
	 * result in a {@link ClassNotFoundException} when looking up e.g. <code>SQLException</code> class.
	 * 
	 * @param cl
	 *            The classloader to use for class lookup.
	 * @return <code>this</code>
	 */
	public RMIOptions nullClassLoader(ClassLoader cl) {
		this.nullClassLoader = cl;
		return this;
	}

	/**
	 * Specifies the transfer properties to use for the connection.
	 * 
	 * @param properties
	 *            The properties to use.
	 * @return <code>this</code>
	 */
	public RMIOptions transferProperties(RMITransferProperties properties) {
		this.properties = properties;
		return this;
	}

	/**
	 * Specifies the thread group to use for worker threads in the connection.
	 * <p>
	 * Any created thread that is used to manage the connection will have this thread group as an ancestor. <br>
	 * Worker threads include: <br>
	 * <ul>
	 * <li>Handling requests.</li>
	 * <li>Garbage collection of RMI objects.</li>
	 * <li>Initiating connection for new streams.</li>
	 * <li>Connection stream I/O.</li>
	 * </ul>
	 * 
	 * @param threadgroup
	 *            The thread group to use.
	 * @return <code>this</code>
	 */
	public RMIOptions workerThreadGroup(ThreadGroup threadgroup) {
		this.workerThreadGroup = threadgroup;
		return this;
	}

	/**
	 * Sets the maximum stream count to create when connecting to the remote endpoint.
	 * <p>
	 * Higher values can result in lower latency, as the congestion might decrease. Choosing too large values can result
	 * in thrashing, therefore users should choose an optimal value based on available CPU and network conditions.
	 * <p>
	 * The default value is chosen based on the current environment. (Currently equals to
	 * <code>Runtime.getRuntime().availableProcessors()</code>, may be modified in the future.)
	 * <p>
	 * Values less than 0 represent the default value, 0 will be normalized to 1, any other value will result in
	 * creation of that many streams.
	 * 
	 * @param count
	 *            The stream count to use during connection. -1 to reset to the default value.
	 * @return <code>this</code>
	 */
	public RMIOptions maxStreamCount(int count) {
		this.maxStreamCount = count;
		return this;
	}

	/**
	 * Sets whether the remote endpoint can issue direct requests through the RMI connection.
	 * <p>
	 * Direct requests are any constructor calls, or static method calls, or basically any method calls which are not
	 * called through remote proxy objects. If the other endpoint tries to call a constructor, a static method or a
	 * non-remote proxy method, an {@link RMICallForbiddenException} will be thrown.
	 * <p>
	 * This option can ensure that the client doesn't instantiate any objects inside this JVM. Setting this to
	 * <code>false</code> will ensure that only such methods are called which have an appropriate remote proxy subject.
	 * <p>
	 * Note, that if this options is set to <code>false</code>, the client won't be able to call anything through the
	 * RMI connection, unless it is able to retrieve a {@linkplain RMIConnection#putContextVariable(String, Object)
	 * context variable} from the connection.
	 * <p>
	 * This options should be set to <code>false</code> when any side can expect that the other endpoint may work in a
	 * malicious way.
	 * <p>
	 * Note, that special care must be done when designing security sensitive applications. Setting this flag to
	 * <code>false</code> is not enough for designing secure applications.
	 * 
	 * @param allowDirectRequests
	 *            <code>true</code> to allow direct requests from the other endpoint.
	 * @return <code>this</code>
	 */
	public RMIOptions allowDirectRequests(boolean allowDirectRequests) {
		this.allowDirectRequests = allowDirectRequests;
		return this;
	}

	/**
	 * Specifies if RMI statistics should be collected during the lifetime of the RMI connection.
	 * <p>
	 * The default value is <code>false</code>.
	 * 
	 * @param collectStatistics
	 *            <code>true</code> to collect statistics.
	 * @return <code>this</code>
	 * @see RMIConnection#getStatistics()
	 * @since saker.rmi 0.8.2
	 */
	public RMIOptions collectStatistics(boolean collectStatistics) {
		this.collectStatistics = collectStatistics;
		return this;
	}

	/**
	 * Initiates the connection with the given parameters.
	 * <p>
	 * This instance can be reused after calling this method.
	 * 
	 * @param socketfactory
	 *            The socket factory to use or <code>null</code>.
	 * @param address
	 *            The address to connect to.
	 * @return The initiated connection.
	 * @throws IOException
	 *             In case of connection failure.
	 * @throws NullPointerException
	 *             If the address is <code>null</code>.
	 * @see RMIConnection
	 */
	public RMIConnection connect(SocketFactory socketfactory, SocketAddress address)
			throws IOException, NullPointerException {
		Objects.requireNonNull(address, "addres");
		return RMIServer.newConnection(this, socketfactory, address);
	}

	/**
	 * Initiates the connection with the given parameters.
	 * <p>
	 * Same as {@link #connect(SocketFactory, SocketAddress)} with <code>null</code> socket factory.
	 * <p>
	 * This instance can be reused after calling this method.
	 * 
	 * @param address
	 *            The address to connect to.
	 * @return The initiated connection.
	 * @throws IOException
	 *             In case of connection failure.
	 * @throws NullPointerException
	 *             If the address is <code>null</code>.
	 */
	public RMIConnection connect(SocketAddress address) throws IOException, NullPointerException {
		return this.connect(null, address);
	}

	/**
	 * Initiates the connection to the given address with the specified socket configuration.
	 * 
	 * @param address
	 *            The address to connect to.
	 * @param socketconfig
	 *            The socket configuration.
	 * @return The initiated connection.
	 * @throws IOException
	 *             In case of connection failure.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @since saker.rmi 0.8.2
	 */
	public RMIConnection connect(SocketAddress address, RMISocketConfiguration socketconfig)
			throws IOException, NullPointerException {
		Objects.requireNonNull(socketconfig, "socket config");
		Objects.requireNonNull(address, "addres");
		return RMIServer.newConnection(this, address, socketconfig);
	}

	/**
	 * Gets the optionally defaulted specified max stream count.
	 * <p>
	 * The result is always greater or equals to zero.
	 * 
	 * @return The specified max stream count.
	 */
	int getDefaultedMaxStreamCount() {
		int c = maxStreamCount;
		if (c < 0) {
			return Runtime.getRuntime().availableProcessors();
		}
		return c;
	}

	/**
	 * Gets the currently set classloader resolver.
	 * 
	 * @return The currently set classloader resolver or <code>null</code> if it was not set.
	 * @see #classResolver(ClassLoaderResolver)
	 */
	public ClassLoaderResolver getClassLoaderResolver() {
		return classLoaderResolver;
	}

	/**
	 * Gety the currently set <code>null</code> classloader.
	 * 
	 * @return The currently set <code>null</code> classloader or <code>null</code> if not set.
	 * @see #nullClassLoader(ClassLoader)
	 */
	public ClassLoader getNullClassLoader() {
		return nullClassLoader;
	}
}