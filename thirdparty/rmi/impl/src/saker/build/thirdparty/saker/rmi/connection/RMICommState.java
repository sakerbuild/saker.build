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
package saker.build.thirdparty.saker.rmi.connection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import saker.build.thirdparty.saker.rmi.connection.RMIStream.BootstrapClassReflectionElementSupplier;
import saker.build.thirdparty.saker.rmi.connection.RMIStream.ClassLoaderReflectionElementSupplier;
import saker.build.thirdparty.saker.rmi.connection.RMIStream.ClassReflectionElementSupplier;
import saker.build.thirdparty.saker.rmi.connection.RMIStream.ConstructorReflectionElementSupplier;
import saker.build.thirdparty.saker.rmi.connection.RMIStream.FieldReflectionElementSupplier;
import saker.build.thirdparty.saker.rmi.connection.RMIStream.MethodReflectionElementSupplier;
import saker.build.thirdparty.saker.util.ImmutableUtils;

class RMICommState {
	//preload primitives and common classes
	public static final Set<Class<?>> DEFAULT_CLASSES = ImmutableUtils
			.makeImmutableLinkedHashSet(new Class<?>[] { void.class, byte.class, short.class, int.class, long.class,
					char.class, float.class, double.class, boolean.class, Void.class, Byte.class, Short.class,
					Integer.class, Long.class, Character.class, Float.class, Double.class, Boolean.class, Object.class,
					String.class, Class.class, Constructor.class, Method.class, Field.class });

	private final RMICommCache<ClassReflectionElementSupplier> classes;
	private final RMICommCache<ClassLoaderReflectionElementSupplier> classLoaders;

	private final RMICommCache<MethodReflectionElementSupplier> methods;
	private final RMICommCache<ConstructorReflectionElementSupplier> constructors;
	private final RMICommCache<FieldReflectionElementSupplier> fields;

	public RMICommState() {
		classes = new RMICommCache<>();
		classLoaders = new RMICommCache<>();

		constructors = new RMICommCache<>();
		methods = new RMICommCache<>();
		fields = new RMICommCache<>();
		int i = 0;
		for (Class<?> c : DEFAULT_CLASSES) {
			ClassReflectionElementSupplier s = new BootstrapClassReflectionElementSupplier(c);
			classes.putWrite(s, i);
			classes.putReadInternal(i, s);
			++i;
		}
	}

	public RMICommCache<ClassReflectionElementSupplier> getClasses() {
		return classes;
	}

	public RMICommCache<ClassLoaderReflectionElementSupplier> getClassLoaders() {
		return classLoaders;
	}

	public RMICommCache<ConstructorReflectionElementSupplier> getConstructors() {
		return constructors;
	}

	public RMICommCache<MethodReflectionElementSupplier> getMethods() {
		return methods;
	}

	public RMICommCache<FieldReflectionElementSupplier> getFields() {
		return fields;
	}

	@Override
	public String toString() {
		return "RMICommState[" + (classes != null ? "classes=" + classes + ", " : "")
				+ (constructors != null ? "constructors=" + constructors + ", " : "")
				+ (methods != null ? "methods=" + methods + ", " : "")
				+ (classLoaders != null ? "classLoaders=" + classLoaders + ", " : "")
				+ (fields != null ? "fields=" + fields : "") + "]";
	}

}
