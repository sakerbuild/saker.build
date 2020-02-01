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
package saker.build.thirdparty.saker.util.classloader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.JarFileUtils;

/**
 * {@link ClassLoaderDataFinder} implementation that is backed by a {@link JarFile}.
 * <p>
 * This data finder will resolve the resources based on the opened JAR file. Any resources and classes will be retrieved
 * and read from it.
 * <p>
 * Any meta-data in the JAR file is ignored, i.e. manifest and others are not analyzed by the data finder itself.
 * Subclasses may modify this behaviour.
 */
public class JarClassLoaderDataFinder implements ClassLoaderDataFinder {
	/**
	 * The opened JAR file.
	 */
	protected final JarFile jar;

	/**
	 * Creates a new instance by opening the jar file at the given path.
	 * <p>
	 * The JAR file will be opened in a multi-release way on JDK9+.
	 * 
	 * @param jar
	 *            The path to the JAR file.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public JarClassLoaderDataFinder(Path jar) throws IOException, NullPointerException {
		this(JarFileUtils.createMultiReleaseJarFile(jar));
	}

	/**
	 * Creates a new instance by opening the jar file at the given path.
	 * <p>
	 * The JAR file will be opened in a multi-release way on JDK9+.
	 * 
	 * @param jar
	 *            The path to the JAR file.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public JarClassLoaderDataFinder(File jar) throws IOException, NullPointerException {
		this(JarFileUtils.createMultiReleaseJarFile(jar));
	}

	/**
	 * Creates a new data finder which uses the argument JAR file.
	 * 
	 * @param jar
	 *            The JAR file.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public JarClassLoaderDataFinder(JarFile jar) throws NullPointerException {
		Objects.requireNonNull(jar, "jar");
		this.jar = jar;
	}

	@Override
	public Supplier<ByteSource> getResource(String name) {
		JarFile jar = this.jar;
		ZipEntry entry = jar.getEntry(name);
		if (entry != null) {
			return () -> {
				try {
					return ByteSource.valueOf(jar.getInputStream(entry));
				} catch (IOException e) {
				}
				return null;
			};
		}
		return null;
	}

	@Override
	public ByteSource getResourceAsStream(String name) {
		JarFile jar = this.jar;
		ZipEntry entry = jar.getEntry(name);
		if (entry != null) {
			try {
				return ByteSource.valueOf(jar.getInputStream(entry));
			} catch (IOException e) {
			}
		}
		return null;
	}

	@Override
	public void close() throws IOException {
		closeJar();
	}

	/**
	 * Closes the JAR file itself.
	 * <p>
	 * Subclasses can override this method to do other work, or maybe avoid closing the JAR file if the subclass decides
	 * to handle it themselves.
	 * 
	 * @throws IOException
	 *             In case of I/O error.
	 */
	protected void closeJar() throws IOException {
		jar.close();
	}

	@Override
	public String toString() {
		JarFile jar = this.jar;
		return this.getClass().getSimpleName() + "[" + (jar == null ? "Closed." : jar.getName()) + "]";
	}
}
