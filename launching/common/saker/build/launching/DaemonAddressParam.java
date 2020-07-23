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
package saker.build.launching;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Iterator;

import saker.build.daemon.DaemonLaunchParameters;
import sipka.cmdline.api.Converter;
import sipka.cmdline.runtime.ArgumentResolutionException;
import sipka.cmdline.runtime.InvalidArgumentFormatException;
import sipka.cmdline.runtime.ParseUtil;

@Converter(method = "parse")
class DaemonAddressParam {
	private String argument;
	private String argumentName;

	public DaemonAddressParam() {
	}

	public DaemonAddressParam(String argument, String argumentName) {
		this.argument = argument;
		this.argumentName = argumentName;
	}

	public String getArgumentString() {
		return argument;
	}

	public String getArgumentName() {
		return argumentName;
	}

	/**
	 * @cmd-format &lt;address>
	 */
	public static DaemonAddressParam parse(String argname, Iterator<? extends String> args) {
		String str = ParseUtil.requireNextArgument(argname, args);
		return new DaemonAddressParam(str, argname);
	}

	public static InetSocketAddress parseInetSocketAddress(String str) throws UnknownHostException {
		int defaultport = DaemonLaunchParameters.DEFAULT_PORT;
		return LaunchingUtils.parseInetSocketAddress(str, defaultport);
	}

	public InetSocketAddress getSocketAddressThrowArgumentException() {
		if (this.argument == null) {
			return getDefaultLocalDaemonSocketAddress();
		}
		try {
			return parseInetSocketAddress(this.argument);
		} catch (IOException e) {
			throw new ArgumentResolutionException("Failed to resolve network address: " + this.argument, e,
					this.argumentName);
		} catch (IllegalArgumentException e) {
			throw new InvalidArgumentFormatException("Invalid network address format: " + this.argument, e,
					this.argumentName);
		}
	}

	public InetSocketAddress getSocketAddress() throws IOException, IllegalArgumentException {
		if (this.argument == null) {
			return getDefaultLocalDaemonSocketAddress();
		}
		return parseInetSocketAddress(this.argument);
	}

	public static InetSocketAddress getDefaultLocalDaemonSocketAddress() {
		return getDefaultLocalDaemonSocketAddressWithPort(DaemonLaunchParameters.DEFAULT_PORT);
	}

	public static InetSocketAddress getDefaultLocalDaemonSocketAddressWithPort(int port) {
		return new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
	}
}