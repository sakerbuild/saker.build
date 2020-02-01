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
package saker.build.thirdparty.saker.util.io.function;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Represents a supplier of results which may throw an {@link IOException}.
 * <p>
 * This functional interface is similar to {@link Supplier}, but with the addition of declaring a checked
 * {@link IOException} on the function.
 * 
 * @param <T>
 *            The result type of the supplier.
 * @see Supplier
 */
@FunctionalInterface
public interface IOSupplier<T> {
	/**
	 * Converts the argument supplier to an {@link IOSupplier}.
	 * 
	 * @param <T>
	 *            The generated type.
	 * @param supplier
	 *            The supplier.
	 * @return The converted {@link IOSupplier}.
	 */
	public static <T> IOSupplier<T> valueOf(Supplier<T> supplier) {
		return supplier::get;
	}

	/**
	 * Gets a result.
	 *
	 * @return A result.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public T get() throws IOException;
}