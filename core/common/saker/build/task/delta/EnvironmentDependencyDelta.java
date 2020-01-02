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
package saker.build.task.delta;

import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.task.TaskContext;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;

/**
 * Build delta representing an {@link EnvironmentProperty} change.
 * <p>
 * The current value can be retrieved using
 * {@link SakerEnvironment#getEnvironmentPropertyCurrentValue(EnvironmentProperty)}.
 * 
 * @param <T>
 *            The type of the property.
 * @see TaskContext#reportEnvironmentDependency
 * @see DeltaType#ENVIRONMENT_PROPERTY_CHANGED
 */
public interface EnvironmentDependencyDelta<T> extends BuildDelta {
	/**
	 * Gets the property which has changed.
	 * 
	 * @return The property.
	 */
	@RMISerialize
	@RMICacheResult
	public EnvironmentProperty<T> getProperty();
}
