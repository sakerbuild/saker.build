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
package saker.build.thirdparty.saker.util.io;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/**
 * Utility class for network related functionalities.
 */
public class NetworkUtils {
	private NetworkUtils() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Parses the specified string address argument and converts it to an {@link InetSocketAddress}.
	 * <p>
	 * The argument address is expected to be in the following format:
	 * <ul>
	 * <li>IPv4 address without port: <code>1.2.3.4</code></li>
	 * <li>IPv4 address with port: <code>1.2.3.4:12345</code></li>
	 * <li>IPv6 address without port: <code>[1234:56789:abcd:efAB:CDEF:0000:0000:0000]</code></li>
	 * <li>IPv6 address without port, zeros omitted: <code>[1234:56789:abcd:efAB:CDEF::]</code></li>
	 * <li>IPv6 address with port: <code>[1234:56789:abcd:efAB:CDEF:0000:0000:0000]:12345</code></li>
	 * <li>Network name without port: <code>localhost</code>, <code>MY-PC-ON-LAN</code></li>
	 * <li>Network name with port: <code>localhost:12345</code>, <code>MY-PC-ON-LAN:12345</code></li>
	 * </ul>
	 * The address part of the argument will be resolved using {@link InetAddress#getByName(String)}.
	 * <p>
	 * If no port number is specified, the default port argument will be used.
	 * 
	 * @param address
	 *            The address to parse.
	 * @param defaultport
	 *            The default port to use if the argument contains none.
	 * @return The resolved socket address.
	 * @throws UnknownHostException
	 *             If the address resolution fails.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the address format is invalid, or the port number is out of range.
	 */
	public static InetSocketAddress parseInetSocketAddress(String address, int defaultport)
			throws UnknownHostException, NullPointerException, IllegalArgumentException {
		//formats: ipv6: [address]:port
		//               [address]
		//         ipv4: 1.2.3.4:port
		//               1.2.3.4
		//         name: hostname:port
		//               hostname
		Objects.requireNonNull(address, "address");
		if (address.isEmpty()) {
			throw new UnknownHostException("Empty address.");
		}
		if (address.charAt(0) == '[') {
			//the address is ipv6
			int endbracketidx = address.lastIndexOf(']');
			if (endbracketidx < 0) {
				throw new UnknownHostException(address + ": invalid IPv6 format");
			}
			int colonidx = address.lastIndexOf(':');
			if (colonidx < endbracketidx) {
				//the last colon is before the last bracket, no end port
				//no port
				if (defaultport < 0 || defaultport > 0xFFFF) {
					throw new IllegalArgumentException("Default port number out of range: " + defaultport);
				}
				return new InetSocketAddress(InetAddress.getByName(address), defaultport);
			}
			if (colonidx == endbracketidx + 1) {
				return new InetSocketAddress(InetAddress.getByName(address.substring(0, endbracketidx + 1)),
						parsePort(address.substring(colonidx + 1)));
			}
			//has port
			throw new UnknownHostException(address + ": invalid format");
		}
		int lidx = address.lastIndexOf(':');
		if (lidx < 0) {
			//no port
			if (defaultport < 0 || defaultport > 0xFFFF) {
				throw new IllegalArgumentException("Default port number out of range: " + defaultport);
			}
			return new InetSocketAddress(InetAddress.getByName(address), defaultport);
		}
		//found a port separator colon, split and parse
		//there is port, and the address might be ipv6
		String host = address.substring(0, lidx);
		String port = address.substring(lidx + 1);
		return new InetSocketAddress(InetAddress.getByName(host), parsePort(port));
	}

	/**
	 * Parses the argument string port number and validates it to be in the required range.
	 * <p>
	 * The methods parses the argument string as an integer. The resulting value will be validated to be in the range of
	 * network port numbers ([0, 65535]). The method will allow the port number zero to be specified.
	 * 
	 * @param port
	 *            The port number string to parse.
	 * @return The port number if the argument can be parsed and is in the specified range.
	 * @throws NumberFormatException
	 *             If the argument is not a valid integral number.
	 * @throws IllegalArgumentException
	 *             If the port number is out of range.
	 * @see {@link Integer#parseInt(String)}
	 */
	public static int parsePort(String port) throws NumberFormatException, IllegalArgumentException {
		int result = Integer.parseInt(port);
		if (result < 0 || result > 0xFFFF) {
			throw new IllegalArgumentException("Port out of range [0, 65535]: " + result);
		}
		return result;
	}

}
