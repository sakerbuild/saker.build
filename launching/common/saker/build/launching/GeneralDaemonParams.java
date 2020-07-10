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

import sipka.cmdline.api.Parameter;

class GeneralDaemonParams extends GeneralDaemonParamsBase {
	/**
	 * <pre>
	 * Specifies the port on which the daemon should listen for incoming connections.
	 * 
	 * If the port is 0, it will be assigned by the operating system, therefore 
	 * it may be random.
	 * 
	 * If not specified, the default port of 3500 will be used.
	 * </pre>
	 */
	@Parameter("-port")
	public PortParam port = PortParam.DEFAULT;

	@Override
	protected Integer getPort() {
		return port.getPort();
	}
}
