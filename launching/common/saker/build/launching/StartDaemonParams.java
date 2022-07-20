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

import java.util.ArrayList;
import java.util.Collection;

import sipka.cmdline.api.MultiParameter;
import sipka.cmdline.api.Parameter;

class StartDaemonParams {
	public static final String PARAM_NAME_CONNECT_CLIENT = "-connect-client";

	/**
	 * <pre>
	 * Specifies the network addresses of server daemons to which 
	 * the daemon should connect to as a client.
	 * 
	 * The build daemon will attempt to connect to the specified daemons as clients. 
	 * This can be useful if the daemon can act as a cluster as the server daemon can 
	 * automatically use this one to participate in builds.
	 * 
	 * The connected clients can be used as clusters for build execution by using the 
	 * -cluster-use-clients flag.
	 * </pre>
	 */
	@Parameter(PARAM_NAME_CONNECT_CLIENT)
	@MultiParameter(DaemonAddressParam.class)
	public Collection<DaemonAddressParam> connectClientParam = new ArrayList<>();
}
