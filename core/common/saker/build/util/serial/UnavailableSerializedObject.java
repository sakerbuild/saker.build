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
package saker.build.util.serial;

import java.io.IOException;

class UnavailableSerializedObject<T> implements SerializedObject<T> {
	public static final UnavailableSerializedObject<?> INSTANCE = new UnavailableSerializedObject<>();

	@SuppressWarnings("unchecked")
	public static <T> UnavailableSerializedObject<T> instance() {
		return (UnavailableSerializedObject<T>) INSTANCE;
	}

	@Override
	public T get() throws IOException, ClassNotFoundException {
		throw new SerializationReflectionException("Object is not yet accessible.");
	}
}
