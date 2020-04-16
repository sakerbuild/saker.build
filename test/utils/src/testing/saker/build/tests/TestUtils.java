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
package testing.saker.build.tests;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerFileProvider;
import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderDataFinder;
import saker.build.thirdparty.saker.util.classloader.MultiDataClassLoader;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;

public class TestUtils {

	private TestUtils() {
		throw new UnsupportedOperationException();
	}

	public static ClassLoader createClassLoaderForClasses(ClassLoader parent, Class<?>... classes) {
		Map<String, ByteArrayRegion> clresources = new TreeMap<>();
		for (Class<?> c : classes) {
			clresources.put(c.getName().replace('.', '/') + ".class", ReflectUtils.getClassBytesUsingClassLoader(c));
		}
		return new MultiDataClassLoader(parent, new MemoryClassLoaderDataFinder(clresources));
	}

	public static ClassLoader createClassLoaderForClasses(Class<?>... classes) {
		return createClassLoaderForClasses(null, classes);
	}

	public static class MapBuilder<M extends Map<K, V>, K, V> {
		private M subject;

		public MapBuilder(M subject) {
			this.subject = subject;
		}

		public M build() {
			return subject;
		}

		public MapBuilder<M, K, V> put(K key, V value) {
			subject.put(key, value);
			return this;
		}
	}

	public static <K, V> MapBuilder<TreeMap<K, V>, K, V> treeMapBuilder() {
		return mapBuilder(new TreeMap<K, V>());
	}

	public static <K, V> MapBuilder<HashMap<K, V>, K, V> hashMapBuilder() {
		return mapBuilder(new HashMap<K, V>());
	}

	public static <K, V> MapBuilder<HashMap<K, V>, K, V> hashMapBuilder(K key, V val) {
		return mapBuilder(new HashMap<K, V>()).put(key, val);
	}

	public static <M extends Map<K, V>, K, V> MapBuilder<M, K, V> mapBuilder(M subject) {
		return new MapBuilder<>(subject);
	}

	public static class MemoryClassLoaderDataFinder implements ClassLoaderDataFinder {
		private Map<String, ByteArrayRegion> resourceBytes;

		public MemoryClassLoaderDataFinder(Map<String, ByteArrayRegion> resourceBytes) {
			this.resourceBytes = resourceBytes;
		}

		public static MemoryClassLoaderDataFinder forZipInput(ZipInputStream zis) throws IOException {
			Map<String, ByteArrayRegion> resourceBytes = new TreeMap<>();
			for (ZipEntry entry; (entry = zis.getNextEntry()) != null;) {
				resourceBytes.put(entry.getName(), StreamUtils.readStreamFully(zis));
			}
			return new MemoryClassLoaderDataFinder(resourceBytes);
		}

		public static MemoryClassLoaderDataFinder forZipInput(SakerFileProvider fp, SakerPath path) throws IOException {
			try (InputStream is = ByteSource.toInputStream(fp.openInput(path));
					ZipInputStream zis = new ZipInputStream(is)) {
				return forZipInput(zis);
			}
		}

		@Override
		public Supplier<ByteSource> getResource(String name) {
			ByteArrayRegion got = resourceBytes.get(name);
			if (got == null) {
				return null;
			}
			return () -> new UnsyncByteArrayInputStream(got);
		}

	}
}
