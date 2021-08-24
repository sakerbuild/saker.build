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
package saker.build.thirdparty.saker.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

class Reflector {
	private Reflector() {
		throw new UnsupportedOperationException();
	}

	static MethodHandle getDefaultMethodHandle(Method method, Class<?> declaringClass)
			throws ReflectiveOperationException {
		Constructor<MethodHandles.Lookup> constructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class);
		constructor.setAccessible(true);

		return constructor.newInstance(declaringClass).unreflectSpecial(method, declaringClass);
	}

	static Object getModule(Class<?> type) {
		return null;
	}

	public static Class<?> lookupAccessClass(MethodHandles.Lookup lookup, Class<?> type) {
		return type;
	}

	public static void reachabilityFence(Object o) {
		//called from ObjectUtils, ignore on Java 8, probably fine
		//see: 	https://github.com/apache/datasketches-memory/issues/91
		//		http://mail.openjdk.java.net/pipermail/core-libs-dev/2018-February/051312.html
	}
}
