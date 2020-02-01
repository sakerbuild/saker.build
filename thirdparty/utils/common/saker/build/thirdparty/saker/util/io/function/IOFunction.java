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
import java.util.function.Function;

/**
 * Functional interface similar to {@link Function} but is usable in an I/O error-prone context.
 * <p>
 * The method of this interface may throw an {@link IOException}.
 * 
 * @param <T>
 *            The type of the first argument.
 * @param <R>
 *            The return type of the function.
 */
@FunctionalInterface
public interface IOFunction<T, R> {
	/**
	 * Applies the function to the given argument.
	 * 
	 * @param t
	 *            The argument.
	 * @return The calculated result value.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	R apply(T t) throws IOException;
}