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
package saker.build.runtime.execution;

import java.io.Closeable;
import java.io.IOException;

import saker.build.runtime.classpath.ClassPathLoadManager.ClassPathLock;
import saker.build.thirdparty.saker.util.classloader.MultiDataClassLoader;
import saker.build.thirdparty.saker.util.io.IOUtils;

public final class ScriptAccessorClassPathResource implements Closeable {
	protected ClassPathLock lock;
	protected MultiDataClassLoader classLoader;

	public ScriptAccessorClassPathResource(ClassPathLock lock, MultiDataClassLoader classLoader) {
		this.lock = lock;
		this.classLoader = classLoader;
	}

	public MultiDataClassLoader getClassLoader() {
		return classLoader;
	}

	public ClassPathLock getClassPathLock() {
		return lock;
	}

	@Override
	public void close() throws IOException {
		IOException exc = null;
		if (classLoader != null) {
			exc = IOUtils.closeExc(exc, classLoader.getDatasFinders());
		}
		exc = IOUtils.closeExc(exc, lock);
		IOUtils.throwExc(exc);
	}
}