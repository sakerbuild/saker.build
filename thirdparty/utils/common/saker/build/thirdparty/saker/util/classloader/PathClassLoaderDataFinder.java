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
import java.io.InterruptedIOException;
import java.nio.channels.Channels;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;

/**
 * {@link ClassLoaderDataFinder} implementation that is based on a path to a directory.
 * <p>
 * The path of the data finder points to a directory, and the resources are found by resolving the name against the
 * directory.
 */
public class PathClassLoaderDataFinder implements ClassLoaderDataFinder {
	/**
	 * The absolute path to the directory that this data finder uses.
	 */
	protected final Path path;

	/**
	 * Creates a new instance that uses the directory at the argument path.
	 * 
	 * @param path
	 *            The absolute path to the directory.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the path is not absolute.
	 */
	public PathClassLoaderDataFinder(Path path) throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(path, "path");
		if (!path.isAbsolute()) {
			throw new IllegalArgumentException("Path is not absolute: " + path);
		}
		this.path = path.normalize();
	}

	/**
	 * Creates a new instance that uses the directory at the argument path.
	 * 
	 * @param file
	 *            The absolute path to the directory.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the path is not absolute.
	 */
	public PathClassLoaderDataFinder(File file) throws NullPointerException, IllegalArgumentException {
		this(Objects.requireNonNull(file, "file").toPath());
	}

	@Override
	public Supplier<ByteSource> getResource(String name) {
		Path resolved = resolve(name);
		if (resolved == null) {
			return null;
		}
		return () -> {
			try {
				return ByteSource.valueOf(Files.newInputStream(resolved));
			} catch (IOException e) {
			}
			return null;
		};
	}

	@Override
	public ByteSource getResourceAsStream(String name) {
		Path resolved = resolve(name);
		if (resolved == null) {
			return null;
		}
		try {
			return ByteSource.valueOf(Files.newInputStream(resolved));
		} catch (IOException e) {
		}
		return null;
	}

	@Override
	public ByteArrayRegion getResourceBytes(String name) {
		Path resolved = resolve(name);
		if (resolved == null) {
			return null;
		}
		SeekableByteChannel channel;
		try {
			channel = Files.newByteChannel(resolved);
		} catch (IOException e) {
			//file not found or something
			return null;
		}
		boolean interrupted = false;
		try {
			try (UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream()) {
				while (true) {
					try {
						baos.readFrom(Channels.newInputStream(channel));
					} catch (InterruptedIOException | ClosedByInterruptException e) {
						if (Thread.interrupted()) {
							interrupted = true;
						}
						//current thread interrupted, try again
						//reopen and set the position to try again
						IOUtils.closeExc(channel);
						channel = null;
						try {
							channel = Files.newByteChannel(resolved);
							channel.position(baos.size());
						} catch (IOException e2) {
							// failed to reopen the channel
							return null;
						}
						continue;
					} catch (IOException e) {
						// read failure, cant do anything about this one
						return null;
					}
					return baos.toByteArrayRegion();
				}
			}
		} finally {
			//the channel must be closed
			//exception of the closing is ignored
			IOUtils.closeExc(channel);
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private Path resolve(String name) {
		Path resolved = path.resolve(name);
		//no need to normalize before checking startswith
		//we dont support paths that contain /./ or /../ path names.
		if (!resolved.startsWith(path)) {
			return null;
		}
		return resolved;
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + "[" + path + "]";
	}
}
