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
package saker.build.util.classloader;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;

import saker.apiextract.api.PublicApi;
import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderDataFinder;
import saker.build.thirdparty.saker.util.io.ByteSource;

/**
 * {@link ClassLoaderDataFinder} implementation that finds the resources based on a {@linkplain SakerFileProvider file
 * provider} and {@linkplain SakerPath path}.
 * <p>
 * The path of the data finder points to a directory, and the resources are found by resolving the name against the
 * directory.
 */
@PublicApi
public class SakerPathClassLoaderDataFinder implements ClassLoaderDataFinder {
	private SakerFileProvider fileProvider;
	private SakerPath path;

	/**
	 * Creates a new instance initialized with the given file provider and path.
	 * 
	 * @param fileProvider
	 *            The file provider.
	 * @param path
	 *            The path.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the path is not absolute.
	 */
	public SakerPathClassLoaderDataFinder(SakerFileProvider fileProvider, SakerPath path)
			throws NullPointerException, InvalidPathFormatException {
		Objects.requireNonNull(fileProvider, "file provider");
		SakerPathFiles.requireAbsolutePath(path);
		this.fileProvider = fileProvider;
		this.path = path;
	}

	/**
	 * Creates a new instance initialized with the specified path key.
	 * 
	 * @param pathkey
	 *            The path key.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public SakerPathClassLoaderDataFinder(ProviderHolderPathKey pathkey) throws NullPointerException {
		Objects.requireNonNull(pathkey, "path key");
		this.fileProvider = pathkey.getFileProvider();
		this.path = pathkey.getPath();
	}

	@Override
	public Supplier<ByteSource> getResource(String name) {
		SakerPath resolved = resolvePath(name);
		if (resolved == null) {
			return null;
		}
		return () -> {
			try {
				return this.fileProvider.openInput(resolved);
			} catch (IOException e) {
			}
			return null;
		};
	}

	@Override
	public ByteSource getResourceAsStream(String name) {
		SakerPath resolved = resolvePath(name);
		if (resolved == null) {
			return null;
		}
		try {
			return this.fileProvider.openInput(resolved);
		} catch (IOException e) {
		}
		return null;
	}

	private SakerPath resolvePath(String name) {
		SakerPath result = path.resolve(name);
		if (!result.startsWith(path)) {
			//we cannot accept relative paths that escape the path itself
			return null;
		}
		return result;
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + "[" + path + "]";
	}
}
