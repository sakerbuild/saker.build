package saker.build.thirdparty.saker.util.classloader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Objects;
import java.util.function.Supplier;

import saker.build.thirdparty.saker.util.io.ByteSource;

/**
 * {@link ClassLoaderDataFinder} implementation that supports retrieving resources from a subdirectory.
 * <p>
 * All resource retrieving functions in this class will be forwarded to an appropriate subject ( {@link ClassLoader} or
 * {@link ClassLoaderDataFinder}) with the resource path prepended with the subdirectory.
 * <p>
 * Use the static {@link #create} factory functions to optain an instance.
 */
public abstract class SubDirectoryClassLoaderDataFinder implements ClassLoaderDataFinder {
	private static final class LoaderSubDirectoryClassLoaderDataFinder extends SubDirectoryClassLoaderDataFinder {
		private final ClassLoader classLoader;

		private LoaderSubDirectoryClassLoaderDataFinder(String directory, ClassLoader classLoader) {
			super(directory);
			this.classLoader = classLoader;
		}

		@Override
		public Supplier<ByteSource> getResource(String name) {
			URL res = classLoader.getResource(this.directory + name);
			if (res == null) {
				return null;
			}
			return () -> {
				try {
					return ByteSource.valueOf(res.openStream());
				} catch (IOException e) {
				}
				return null;
			};
		}

		@Override
		public ByteSource getResourceAsStream(String name) {
			InputStream is = classLoader.getResourceAsStream(this.directory + name);
			if (is == null) {
				return null;
			}
			return ByteSource.valueOf(is);
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + (classLoader != null ? "classLoader=" + classLoader + ", " : "")
					+ (directory != null ? "directory=" + directory : "") + "]";
		}

	}

	private static final class FinderSubDirectoryClassLoaderDataFinder extends SubDirectoryClassLoaderDataFinder {
		private final ClassLoaderDataFinder finder;

		private FinderSubDirectoryClassLoaderDataFinder(String directory, ClassLoaderDataFinder finder) {
			super(directory);
			this.finder = finder;
		}

		@Override
		public Supplier<? extends ByteSource> getResource(String name) {
			return finder.getResource(this.directory + name);
		}

		@Override
		public ByteSource getResourceAsStream(String name) {
			return finder.getResourceAsStream(this.directory + name);
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + (finder != null ? "finder=" + finder + ", " : "")
					+ (directory != null ? "directory=" + directory : "") + "]";
		}

	}

	/**
	 * Creates a new instance that forwards its resource retrievel requests to the given class loader.
	 * <p>
	 * The argument subdirectory is normalized, all backslash (<code>'\\'</code>) characters will be replaced with a
	 * forward slash (<code>'/'</code>). The subdirectory argument may end with a slash character, but not required.
	 * 
	 * @param subdirectory
	 *            The subdirectory to use.
	 * @param classLoader
	 *            The class loader to forward the resource retrieval requests to.
	 * @return The created data finder.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static SubDirectoryClassLoaderDataFinder create(String subdirectory, ClassLoader classLoader)
			throws NullPointerException {
		Objects.requireNonNull(classLoader, "class loader");
		return new LoaderSubDirectoryClassLoaderDataFinder(normalizeDirectory(subdirectory), classLoader);
	}

	/**
	 * Creates a new instance that forwards its resource retrieval requests to the given data finder.
	 * <p>
	 * The argument subdirectory is normalized, all backslash (<code>'\\'</code>) characters will be replaced with a
	 * forward slash (<code>'/'</code>). The subdirectory argument may end with a slash character, but not required.
	 * 
	 * @param subdirectory
	 *            The subdirectory to use.
	 * @param finder
	 *            The finder to forward the resource retrieval requests to.
	 * @return The created data finder.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static SubDirectoryClassLoaderDataFinder create(String subdirectory, ClassLoaderDataFinder finder)
			throws NullPointerException {
		Objects.requireNonNull(finder, "data finder");
		return new FinderSubDirectoryClassLoaderDataFinder(normalizeDirectory(subdirectory), finder);
	}

	private static String normalizeDirectory(String directory) throws NullPointerException {
		Objects.requireNonNull(directory, "directory");
		directory = directory.replace('\\', '/');
		if (!directory.endsWith("/")) {
			directory += "/";
		}
		return directory;
	}

	protected final String directory;

	SubDirectoryClassLoaderDataFinder(String directory) {
		this.directory = directory;
	}

	/**
	 * Gets the subdirectory that this data finder uses.
	 * 
	 * @return The subdirectory.
	 */
	public String getDirectory() {
		return directory;
	}

}
