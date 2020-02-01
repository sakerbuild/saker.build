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

import java.util.function.Supplier;

/**
 * Functional interface similar to {@link Supplier}, except the {@link #get()} method is allowed to throw an arbitrary
 * exception.
 * <p>
 * The {@link #get()} method is declared to be able to throw {@link Exception}, therefore a functional interface such as
 * this can be used in contexts where users may throw checked exceptions.
 */
@FunctionalInterface
public interface ThrowingSupplier<T> {
	/**
	 * Runs the operations that the subclass defines and gets the result.
	 * 
	 * @return Gets the result of the supplier.
	 * @throws Exception
	 *             If the execution fails in any way.
	 */
	public T get() throws Exception;

}
