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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerFileProvider;
import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderDataFinder;
import saker.build.thirdparty.saker.util.classloader.MultiDataClassLoader;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.util.java.JavaTools;

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

	/**
	 * Compiles the source files in memory to bytecode.
	 * 
	 * @param files
	 *            The absolute paths of the source files.
	 * @return The outputs. The key of the result map is the relative path of the class files. E.g
	 *             <code>test/MyClass.class</code>.
	 */
	public static Map<String, ByteArrayRegion> compileJavaSources(SakerPath... files) {
		JavaCompiler compiler = JavaTools.getSystemJavaCompiler();
		DiagnosticCollector<JavaFileObject> diaglistener = new DiagnosticCollector<>();
		Collection<JavaFileObject> units = new ArrayList<>();

		for (SakerPath f : files) {
			try {
				units.add(new SimpleJavaFileObject(new URI("memory:///" + f.toStringFromRoot()),
						JavaFileObject.Kind.SOURCE) {
					@Override
					public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
						return LocalFileProvider.getInstance().getAllBytes(f).toString();
					}
				});
			} catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}
		}

		StandardJavaFileManager standardfm = compiler.getStandardFileManager(diaglistener, null, null);
		MemoryJavaFileManager memfm = new MemoryJavaFileManager(standardfm);
		CompilationTask task = compiler.getTask(null, memfm, diaglistener, null, null, units);
		Boolean result = task.call();

		for (Diagnostic<? extends JavaFileObject> d : diaglistener.getDiagnostics()) {
			System.err.println(d);
			switch (d.getKind()) {
				case ERROR:
					result = false;
					break;
				default: {
					break;
				}
			}
		}
		if (!result) {
			throw new RuntimeException("compilation failed");
		}
		Map<String, ByteArrayRegion> resultclasses = new TreeMap<>();
		for (Entry<String, MemoryJavaFileObject> entry : memfm.outputs.get(StandardLocation.CLASS_OUTPUT).entrySet()) {
			resultclasses.put(entry.getKey(), entry.getValue().getBytes());
		}
		return resultclasses;
	}

	private static class MemoryJavaFileManager extends ForwardingJavaFileManager<StandardJavaFileManager>
			implements StandardJavaFileManager {

		protected Map<Location, Map<String, MemoryJavaFileObject>> outputs = new HashMap<>();

		{
			outputs.put(StandardLocation.CLASS_OUTPUT, new TreeMap<>());
		}

		protected MemoryJavaFileManager(StandardJavaFileManager fileManager) {
			super(fileManager);
		}

		@Override
		public JavaFileObject getJavaFileForOutput(Location location, String className, Kind kind, FileObject sibling)
				throws IOException {
			Map<String, MemoryJavaFileObject> map = outputs.computeIfAbsent(location, Functionals.treeMapComputer());
			String name = className.replace('.', '/') + kind.extension;
			JavaFileObject present = map.get(name);
			if (present != null) {
				return present;
			}
			try {
				MemoryJavaFileObject result = new MemoryJavaFileObject(name, kind);
				map.put(name, result);
				return result;
			} catch (URISyntaxException e) {
				throw new IOException(e);
			}
		}

		@Override
		public Iterable<? extends JavaFileObject> getJavaFileObjectsFromFiles(Iterable<? extends File> files) {
			return fileManager.getJavaFileObjectsFromFiles(files);
		}

		@Override
		public Iterable<? extends JavaFileObject> getJavaFileObjects(File... files) {
			return fileManager.getJavaFileObjects(files);
		}

		@Override
		public Iterable<? extends JavaFileObject> getJavaFileObjectsFromStrings(Iterable<String> names) {
			return fileManager.getJavaFileObjectsFromStrings(names);
		}

		@Override
		public Iterable<? extends JavaFileObject> getJavaFileObjects(String... names) {
			return fileManager.getJavaFileObjects(names);
		}

		@Override
		public void setLocation(Location location, Iterable<? extends File> path) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public Iterable<? extends File> getLocation(Location location) {
			if (location.isOutputLocation()) {
				return null;
			}
			return fileManager.getLocation(location);
		}
	}

	private static class MemoryJavaFileObject extends SimpleJavaFileObject {
		private UnsyncByteArrayOutputStream os;

		protected MemoryJavaFileObject(String name, Kind kind) throws URISyntaxException {
			super(new URI("memoryout:///" + name), kind);
		}

		@Override
		public OutputStream openOutputStream() throws IOException {
			System.out.println("Open compilation output: " + uri);
			os = new UnsyncByteArrayOutputStream();
			return os;
		}

		public ByteArrayRegion getBytes() {
			return os.toByteArrayRegion();
		}
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

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("MemoryClassLoaderDataFinder ");
			builder.append(resourceBytes.keySet());
			return builder.toString();
		}

	}
}
