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
package saker.build.task;

import java.io.Externalizable;
import java.util.Set;

import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIForbidden;

/**
 * Task environment selectors are used to choose an appropriate build environment for tasks to run on.
 * <p>
 * Task environment selectors are used by {@linkplain TaskFactory task factories} to determine which
 * {@linkplain SakerEnvironment build environment} is suitable for their execution. They do this selection by examining
 * the {@linkplain EnvironmentProperty environment properties} of the build environment and deciding they are
 * appropriate for the associated task.
 * <p>
 * The implementations are required to report the environment properties which resulted in successful selection of the
 * build environment.
 * <p>
 * Implementations are required to be {@link Externalizable}, and/or in any other way RMI-transferrable, as methods of
 * this interface cannot be called through RMI.
 * <p>
 * During the environment selection process, the environment selector instance will be transferred to the machine which
 * the given build environment resides on. This behaviour is applied when build clusters are used.
 * <p>
 * Implementations should adhere to the contract specified by {@link #equals(Object)} and {@link #hashCode()}.
 */
public interface TaskExecutionEnvironmentSelector {
	/**
	 * Checks if the argument build environment is suitable for running the associated task with this selector.
	 * <p>
	 * Implementers of this method should determine its results only based on the {@linkplain EnvironmentProperty
	 * environment properties} of the build environment. Implementations of this interface are required to return these
	 * properties in the result object with their corresponding values.
	 * <p>
	 * Implicit dependencies will be reported for the qualifier environment properties for the task if the environment
	 * is deemed to be suitable. <br>
	 * <b>Note:</b> The implicit dependencies will <b>not</b> be reported for inner tasks.
	 * <p>
	 * Implementations cannot use {@link SakerEnvironment#getEnvironmentIdentifier()} to deem a given environment
	 * suitable. Doing so would make the builds dependent on random numbers, therefore greatly reducing reproducibility.
	 * Tasks can select already known environments to execute inner tasks on via setting the
	 * {@linkplain InnerTaskExecutionParameters#setAllowedClusterEnvironmentIdentifiers(Set) allowed environment
	 * identifiers} of the inner task parameters.
	 * 
	 * @param environment
	 *            The build environment.
	 * @return The selection result which contain the qualifier properties, or <code>null</code> if the environment is
	 *             not suitable for invoking the associated task.
	 */
	@RMIForbidden
	public EnvironmentSelectionResult isSuitableExecutionEnvironment(SakerEnvironment environment);

	@Override
	public int hashCode();

	/**
	 * Checks if this environment selector would find suitable the same environments as the parameter given the same
	 * circumstances.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj);
}
