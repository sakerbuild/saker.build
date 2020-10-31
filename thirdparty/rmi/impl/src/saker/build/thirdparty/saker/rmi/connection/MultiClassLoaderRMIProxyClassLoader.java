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

import java.lang.invoke.MethodHandles;
import java.util.Collection;

import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.thirdparty.saker.util.classloader.MultiClassLoader;
import saker.build.thirdparty.saker.util.io.IOUtils;

class MultiClassLoaderRMIProxyClassLoader extends MultiClassLoader implements RMIClassDefiner {
	private MethodHandles.Lookup lookup;

	public MultiClassLoaderRMIProxyClassLoader(ClassLoader parent, Collection<? extends ClassLoader> classLoaders,
			MethodHandles.Lookup lookup) {
		super(parent, classLoaders);
		this.lookup = lookup;
	}

	@Override
	public Class<?> defineClass(String name, byte[] bytes) {
		return defineClass(name, bytes, 0, bytes.length);
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		//we need to perform the access checks so we don't load the class from an inaccessible place
		//errors related to this happened when:
		//  two classloaders were used: [platform classloader, explicitly opened jdk.compiler module classloader]
		//the jdk.compiler module was opened and reloaded by a different agent in the runtime
		//in this case when com.sun.tools.javac.jvm.PoolConstant was loaded on Java 15+, then
		//the platform classloader found the class in the original jdk.compiler module
		//however, it is not accessible by the proxy classloader
		//so we need to perform the access check from the lookup related to the proxy classloader
		//and if it fails, we proceed to the next classloader to load the class
		//in this case it will find the class in the opened jdk.compiler module classloader, and works fine
		ClassNotFoundException exc = null;
		for (ClassLoader cl : classLoaders) {
			Class<?> loaded;
			try {
				loaded = cl.loadClass(name);
			} catch (ClassNotFoundException e) {
				exc = IOUtils.addExc(exc, e);
				continue;
			}
			try {
				ReflectUtils.lookupAccessClass(lookup, loaded);
			} catch (IllegalAccessException | SecurityException e) {
				if (exc == null) {
					exc = new ClassNotFoundException(name, e);
				} else {
					exc.addSuppressed(e);
				}
				continue;
			}
			return loaded;
		}
		IOUtils.throwExc(exc);
		throw new ClassNotFoundException(name);
	}
}