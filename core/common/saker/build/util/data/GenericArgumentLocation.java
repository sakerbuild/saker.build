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
package saker.build.util.data;

import java.util.Arrays;

import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.util.data.annotation.ConverterConfiguration;

/**
 * Class representing a location of a generic argument in a given type declaration.
 * 
 * @see ConversionContext
 * @see ConverterConfiguration#genericArgumentIndex()
 */
public class GenericArgumentLocation {
	/**
	 * A singleton instance representing the outermost type declaration.
	 */
	public static final GenericArgumentLocation INSTANCE_ROOT = new GenericArgumentLocation(
			ObjectUtils.EMPTY_INT_ARRAY);

	private int[] location;

	GenericArgumentLocation(int[] location) {
		this.location = location;
	}

	/**
	 * Creates a child declaration with the specified generic argument index.
	 * 
	 * @param index
	 *            The argument index.
	 * @return The newly created generic argument location.
	 */
	public GenericArgumentLocation child(int index) {
		int[] resloc = Arrays.copyOf(location, location.length + 1);
		resloc[location.length] = index;
		return new GenericArgumentLocation(resloc);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(location);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		GenericArgumentLocation other = (GenericArgumentLocation) obj;
		if (!Arrays.equals(location, other.location))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + Arrays.toString(location) + "]";
	}

}
