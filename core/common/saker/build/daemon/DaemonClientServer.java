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
package saker.build.daemon;

import saker.build.runtime.params.InvalidBuildConfigurationException;
import saker.build.task.cluster.TaskInvokerFactory;

public interface DaemonClientServer {

	/**
	 * @param factory
	 * @throws InvalidBuildConfigurationException
	 *             Thrown if the argument has the same {@linkplain TaskInvokerFactory#getEnvironmentIdentifier()} as the
	 *             coordinator daemon.
	 */
	public default void addClientClusterTaskInvokerFactory(TaskInvokerFactory factory)
			throws InvalidBuildConfigurationException {
	}
}
