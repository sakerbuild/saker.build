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
import sipka.cmdline.api.Parameter;

class RunGeneralDaemonParams extends GeneralDaemonParamsBase {
	/**
	 * <pre>
	 * Specifies the port on which the daemon should listen for incoming connections.
	 * 
	 * If the port is 0, it will be assigned by the operating system, therefore 
	 * it may be random.
	 * 
	 * If not specified, the default port of 3500 will be used.
	 * 
	 * A simple dash (-) can be used to signal that the daemon shouldn't listen
	 * for incoming connections in any way.
	 * </pre>
	 */
	@Parameter("-port")
	public RunDaemonPortParam port = RunDaemonPortParam.DEFAULT;

	@Override
	protected Integer getPort() {
		return port.getPort();
	}

	@Converter(method = "parse")
	public static class RunDaemonPortParam {
		public static final RunDaemonPortParam DEFAULT = new RunDaemonPortParam(DaemonLaunchParameters.DEFAULT_PORT);

		private static final RunDaemonPortParam INSTANCE_NONE = new RunDaemonPortParam(null);

		private final Integer port;

		public RunDaemonPortParam(Integer port) {
			this.port = port;
		}

		/**
		 * @param it
		 * @return
		 * @cmd-format &lt;int[0-65535]|->
		 */
		public static RunDaemonPortParam parse(String argname, Iterator<? extends String> it) {
			String n = it.next();
			if ("-".equals(n)) {
				return INSTANCE_NONE;
			}
			return new RunDaemonPortParam(LaunchingUtils.parsePort(argname, n));
		}

		public Integer getPort() {
			return port;
		}
	}

}
