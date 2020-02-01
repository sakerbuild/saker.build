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
package saker.build.thirdparty.saker.util.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.JarFile;

public class JarFileUtilAccessor {
	public static final Name MULTI_RELEASE_ATTRIBUTE_NAME = Attributes.Name.MULTI_RELEASE;

	private JarFileUtilAccessor() {
		throw new UnsupportedOperationException();
	}

	public static JarFile createMultiReleaseJarFile(Path path) throws IOException {
		return createMultiReleaseJarFile(path.toFile());
	}

	public static JarFile createMultiReleaseJarFile(File file) throws IOException {
		return new JarFile(file, true, JarFile.OPEN_READ, Runtime.version());
	}
}
