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
package saker.build.runtime.environment;

import java.io.Externalizable;

import saker.build.runtime.execution.ExecutionProperty;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionUtilities;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIForbidden;

/**
 * Specifies a property which can be derived from the build environment.
 * <p>
 * Properties are used to determine various aspects of the current build environment. They do not depend on the current
 * build configuration, but only depend on the environment of the executing computer.
 * <p>
 * Implementations are required to override {@link #equals(Object)} and {@link #hashCode()}. Property implementations
 * acts as a key to determine what they compute from the given environment.
 * <p>
 * Task implementations can use these classes to depend on various aspects of the build environment. If any of the
 * dependent properties changes, the task will be rerun with appropriate deltas.
 * <p>
 * It is assumed that environment properties stay the same between consecutive build executions.
 * <p>
 * It is strongly recommended that implementations and the calculated property values implement the
 * {@link Externalizable} interface.
 * <p>
 * Good examples for environment properties:
 * <ul>
 * <li>Current Java Runtime version.</li>
 * <li>Version of the used compiler for a given language.</li>
 * <li>Underlying operating system type.</li>
 * <li>{@linkplain SakerEnvironment#getUserParameters() Environment user parameters}.</li>
 * </ul>
 * <p>
 * Bad examples for environment properties:
 * <ul>
 * <li>Current working directory.</li>
 * <li>Execution user parameters.</li>
 * </ul>
 * <p>
 * For these kind of properties, {@link ExecutionProperty} should be used.
 * 
 * @param <T>
 *            The type of the returned property.
 * @see TaskContext#reportEnvironmentDependency(EnvironmentProperty, Object)
 * @see SakerEnvironment#getEnvironmentPropertyCurrentValue(EnvironmentProperty)
 * @see TaskExecutionUtilities#getReportEnvironmentDependency(EnvironmentProperty)
 */
public interface EnvironmentProperty<T> {
	/**
	 * Computes the value of this environment property.
	 * <p>
	 * It is strongly recommended for the returned value to implement {@link #equals(Object)} in order for proper
	 * incremental functionality.
	 * 
	 * @param environment
	 *            The environment to use for the computation.
	 * @return The computed value.
	 * @throws Exception
	 *             If any exception happens during the computation of the property.
	 */
	@RMIForbidden
	public T getCurrentValue(SakerEnvironment environment) throws Exception;

	/**
	 * Determines if this property will compute the same values as the parameter.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj);

	@Override
	public int hashCode();
}
