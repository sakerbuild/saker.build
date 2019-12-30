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
