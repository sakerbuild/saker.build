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

import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;

/**
 * Common superinterface for build task deltas.
 * <p>
 * Subclasses should implement {@link #equals(Object)} and {@link #hashCode()}.
 *
 * @see DeltaType
 */
public interface BuildDelta {
	/**
	 * Gets the type of this build delta.
	 * 
	 * @return The type.
	 */
	@RMICacheResult
	public DeltaType getType();

	@Override
	public boolean equals(Object obj);

	@Override
	public int hashCode();
}
