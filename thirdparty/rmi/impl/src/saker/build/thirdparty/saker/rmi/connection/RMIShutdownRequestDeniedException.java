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

import java.net.SocketAddress;

import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;

/**
 * Exception thrown when a shutdown request initiated towards an {@link RMIServer} was not allowed.
 */
public class RMIShutdownRequestDeniedException extends RMIRuntimeException {
	private static final long serialVersionUID = 1L;

	private SocketAddress address;

	/**
	 * Constructs a new exception instance for the given request target address.
	 * 
	 * @param address
	 *            The address.
	 */
	public RMIShutdownRequestDeniedException(SocketAddress address) {
		this.address = address;
	}

	/**
	 * Constructs a new exception instance for the given request target address and exception message.
	 * 
	 * @param message
	 *            The message.
	 * @param address
	 *            The address.
	 */
	public RMIShutdownRequestDeniedException(String message, SocketAddress address) {
		super(message);
		this.address = address;
	}

	/**
	 * Gets the address that was the target of the shutdown request.
	 * 
	 * @return The address.
	 */
	public SocketAddress getAddress() {
		return address;
	}
}
