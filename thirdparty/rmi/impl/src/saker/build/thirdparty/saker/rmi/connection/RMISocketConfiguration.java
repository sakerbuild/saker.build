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

import java.nio.channels.ClosedByInterruptException;

import javax.net.SocketFactory;

/**
 * Configuration object specifying how sockets should be handled for RMI connections.
 * <p>
 * Objects if this class are mutable, and reusable.
 * 
 * @since saker.rmi 0.8.2
 */
public final class RMISocketConfiguration {
	private SocketFactory socketFactory;
	private int connectionTimeout = -1;
	private boolean connectionInterruptible;

	/**
	 * Creates a new instance with default values.
	 */
	public RMISocketConfiguration() {
	}

	/**
	 * Creates a new instance with values copied from the parameter.
	 * 
	 * @param copy
	 *            The configuration to copy from.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public RMISocketConfiguration(RMISocketConfiguration copy) throws NullPointerException {
		this.socketFactory = copy.socketFactory;
		this.connectionTimeout = copy.connectionTimeout;
		this.connectionInterruptible = copy.connectionInterruptible;
	}

	/**
	 * Gets the socket factory.
	 * 
	 * @return The socket factory. May be <code>null</code>.
	 * @see #setSocketFactory(SocketFactory)
	 */
	public SocketFactory getSocketFactory() {
		return socketFactory;
	}

	/**
	 * Sets the socket factory.
	 * <p>
	 * The specified socket factory will be used to create new sockets. May be set to <code>null</code> to use the
	 * default mechanism.
	 * 
	 * @param socketFactory
	 *            The socket factory.
	 */
	public void setSocketFactory(SocketFactory socketFactory) {
		this.socketFactory = socketFactory;
	}

	/**
	 * Gets the timeout for initiating connections in milliseconds.
	 * <p>
	 * Negative value represents an implementation dependent default value, 0 means infinite timeout, and positive
	 * values represent the timeout value in milliseconds.
	 * 
	 * @return The timeout.
	 * @see #setConnectionTimeout(int)
	 */
	public int getConnectionTimeout() {
		return connectionTimeout;
	}

	/**
	 * Sets the timeout for initiating connections in milliseconds.
	 * <p>
	 * Negative value represents an implementation dependent default value, 0 means infinite timeout, and positive
	 * values represent the timeout value in milliseconds.
	 * 
	 * @param connectionTimeout
	 *            The timeout in millseconds.
	 */
	public void setConnectionTimeout(int connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
	}

	/**
	 * Gets if the connection initiation should be interruptible.
	 * 
	 * @return <code>true</code> if connection initiation may respond to interrupts.
	 * @see #setConnectionInterruptible(boolean)
	 */
	public boolean isConnectionInterruptible() {
		return connectionInterruptible;
	}

	/**
	 * Sets the interruptability of connection initiations.
	 * <p>
	 * When set to <code>true</code>, the attempt to initiate connections will be interruptible. This means that if the
	 * thread on which the sockets are being connected is interrupted, the sockets will be closed, and
	 * {@link ClosedByInterruptException} will be thrown.
	 * <p>
	 * The property only applies to the duration during which the RMI connection is being initiated.
	 * <p>
	 * The default value of this property is <code>false</code>.
	 * 
	 * @param connectionInterruptible
	 *            <code>true</code> to enable interruption of connecting.
	 */
	public void setConnectionInterruptible(boolean connectionInterruptible) {
		this.connectionInterruptible = connectionInterruptible;
	}
}
