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
package saker.build.thirdparty.saker.util.function;

import java.util.function.Function;

/**
 * Functional interface similar to {@link Function}, except the {@link #apply(Object)} method is allowed to throw an
 * arbitrary exception.
 * 
 * @param <T>
 *            The argument type of the function.
 * @param <R>
 *            The return type of the function.
 */
@FunctionalInterface
public interface ThrowingFunction<T, R> {
	/**
	 * Applies the function to the given argument.
	 * 
	 * @param value
	 *            The argument.
	 * @return The calculated result value.
	 * @throws Exception
	 *             In case the operation failed.
	 */
	public R apply(T value) throws Exception;
}