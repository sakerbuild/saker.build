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
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolver;

final class ClassLoaderResolverObjectOutputStream extends ObjectOutputStream {
	private ClassLoaderResolver registry;

	public ClassLoaderResolverObjectOutputStream(ClassLoaderResolver registry, OutputStream out) throws IOException {
		super(out);
		this.registry = registry;
	}

	@Override
	protected void annotateClass(Class<?> cl) throws IOException {
		String id = registry.getClassLoaderIdentifier(cl.getClassLoader());
		if (id == null) {
			id = "";
		}
		writeUTF(id);
	}

	@Override
	protected void annotateProxyClass(Class<?> cl) throws IOException {
		String id = registry.getClassLoaderIdentifier(cl.getClassLoader());
		if (id == null) {
			id = "";
		}
		writeUTF(id);
	}
}