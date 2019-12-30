package testing.saker.build.tests;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderDataFinder;
import saker.build.thirdparty.saker.util.classloader.MultiDataClassLoader;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSource;
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
