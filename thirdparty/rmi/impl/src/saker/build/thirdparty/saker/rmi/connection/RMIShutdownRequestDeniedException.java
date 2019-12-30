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
