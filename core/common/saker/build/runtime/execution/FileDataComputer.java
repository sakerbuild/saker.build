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
package saker.build.runtime.execution;

import java.io.Externalizable;
import java.io.IOException;

import saker.build.file.SakerFile;
import saker.build.task.TaskContext;

/**
 * Interface specifying a method to compute derived data from files.
 * <p>
 * Instances of this interface can be used to derive data based on a content of a file. This is mainly used to share
 * derived data with other tasks in the build execution, and therefore resulting in less overall computation.
 * <p>
 * There can be scenarios where multiple tasks use the same files, and they derive the same data from them while
 * converting it to a third representation. In this case it can be beneficial to only compute the intermediate
 * representation once, and have it cached for re-use.
 * <p>
 * Example: <br>
 * Some tasks have a JSON file as their inputs. A common step in these tasks is to parse the input JSON, and do their
 * work based on that. As multiple tasks will need to parse this JSON, it can result in redundant computation of the
 * same data. This is were this interface comes in, which can be used to parse the JSON only once, and have multiple
 * tasks use this representation.
 * <p>
 * The execution context provides this service via the
 * {@link TaskContext#computeFileContentData(SakerFile, FileDataComputer)} function.
 * <p>
 * Implementations should adhere to the {@link #equals(Object)} and {@link #hashCode()} contract.
 * <p>
 * Implementations should implement the {@link Externalizable} for proper serialization if needed. (Generally, instances
 * of this interface will not be persisted, but it should be serializable in order to ensure remote execution
 * compatibility.)
 * <p>
 * Results of the computation should be serializable, preferably {@link Externalizable}.
 * 
 * @param <T>
 *            The type of data this computer returns.
 */
public interface FileDataComputer<T> {
	/**
	 * Computes the data based on the contents of the parameter file.
	 * <p>
	 * If the returned value of the function is <code>null</code>, the computation handler may throw a
	 * {@link NullPointerException}.
	 * 
	 * @param file
	 *            The file to get the contents from for the computation.
	 * @return The computed data.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public T compute(SakerFile file) throws IOException;

	@Override
	public int hashCode();

	/**
	 * Checks if this data computer would compute the same data as the parameter given the same circumstances.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj);
}