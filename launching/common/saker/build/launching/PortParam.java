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

import java.util.Iterator;

import saker.build.daemon.DaemonLaunchParameters;
import sipka.cmdline.api.Converter;

@Converter(method = "parse")
class PortParam {
	public static final PortParam DEFAULT = new PortParam(DaemonLaunchParameters.DEFAULT_PORT);

	private final int port;

	public PortParam(int port) {
		this.port = port;
	}

	/**
	 * @param it
	 * @return
	 * @cmd-format &lt;int[0-65535]>
	 */
	public static PortParam parse(Iterator<? extends String> it) {
		return new PortParam(LaunchingUtils.parsePort(it.next()));
	}

	public int getPort() {
		return this.port;
	}
}