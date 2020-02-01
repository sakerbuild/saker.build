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

import java.util.Collection;

import saker.build.thirdparty.saker.util.classloader.MultiClassLoader;

class MultiClassLoaderRMIProxyClassLoader extends MultiClassLoader implements RMIClassDefiner {
	public MultiClassLoaderRMIProxyClassLoader(ClassLoader parent, Collection<? extends ClassLoader> classLoaders) {
		super(parent, classLoaders);
	}

	@Override
	public Class<?> defineClass(String name, byte[] bytes) {
		return defineClass(name, bytes, 0, bytes.length);
	}
}