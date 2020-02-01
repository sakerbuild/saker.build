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
import java.util.function.Consumer;

/**
 * Functional interface similar to {@link Consumer} but is usable in an I/O error-prone context.
 * <p>
 * The method of this interface may throw an {@link IOException}.
 * 
 * @param <T>
 *            The type of the first argument.
 */
@FunctionalInterface
public interface IOConsumer<T> {
	/**
	 * Performs the operation for the given argument.
	 * 
	 * @param t
	 *            The first argument.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	void accept(T t) throws IOException;
}
