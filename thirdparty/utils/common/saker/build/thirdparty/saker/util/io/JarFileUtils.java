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
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import saker.build.thirdparty.saker.util.io.JarFileUtilAccessor;

/**
 * Utility class containing functions dealing with JAR files.
 * 
 * @see JarFile
 */
public class JarFileUtils {

	/**
	 * Creates a new {@link JarFile} that supports the Multi-Release rules if the current JRE is version 9 or later.
	 * <p>
	 * The created JAR file will access the resources contained in it with respect to the current JRE version. This is
	 * only of importance if the current JRE is version 9 or later.
	 * 
	 * @param path
	 *            The path to the JAR file.
	 * @return The opened JAR file.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static JarFile createMultiReleaseJarFile(Path path) throws IOException, NullPointerException {
		Objects.requireNonNull(path, "path");
		return JarFileUtilAccessor.createMultiReleaseJarFile(path);
	}

	/**
	 * Creates a new {@link JarFile} that supports the Multi-Release rules if the current JRE is version 9 or later.
	 * <p>
	 * The created JAR file will access the resources contained in it with respect to the current JRE version. This is
	 * only of importance if the current JRE is version 9 or later.
	 * 
	 * @param file
	 *            The JAR file location.
	 * @return The opened JAR file.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static JarFile createMultiReleaseJarFile(File file) throws IOException, NullPointerException {
		Objects.requireNonNull(file, "file");
		return JarFileUtilAccessor.createMultiReleaseJarFile(file);
	}

	/**
	 * Gets the manifest attribute name that has the value of <code>Multi-Release</code>.
	 * 
	 * @return The attribute name.
	 */
	public static Attributes.Name getMultiReleaseManifestAttributeName() {
		return JarFileUtilAccessor.MULTI_RELEASE_ATTRIBUTE_NAME;
	}

	private JarFileUtils() {
		throw new UnsupportedOperationException();
	}
}
