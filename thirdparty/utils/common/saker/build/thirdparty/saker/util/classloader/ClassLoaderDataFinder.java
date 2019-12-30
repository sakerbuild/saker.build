package saker.build.thirdparty.saker.util.classloader;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.NoSuchFileException;
import java.util.Collections;
import java.util.function.Supplier;

import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;

/**
 * Interface for providing resources to classloader implementations.
 * <p>
 * This interface can be used by some classloader implementations to find the data for loading classes or retrieving
 * resources.
 * <p>
 * This interface is RMI compatible, and was primarily defined to allow class loading in an RMI compatible way.
 * <p>
 * Instances of this interface may hold unmanaged data, therefore clients should {@link ClassLoaderDataFinder#close()}
 * them when no longer used.
 * 
 * @see MultiDataClassLoader
 */
public interface ClassLoaderDataFinder extends Closeable {
	/**
	 * Gets the bytes of the class denoted by the given binary name.
	 * 
	 * @param classname
	 *            The class name.
	 * @return The bytes of the class or <code>null</code> if not found.
	 */
	public default ByteArrayRegion getClassBytes(String classname) {
		String path = classname.replace('.', '/').concat(".class");
		return getResourceBytes(path);
	}

	/**
	 * Gets the bytes of a resource specified by the given name.
	 * <p>
	 * The name is a slash (<code>'/'</code>) separated path to the resource to be found.
	 * 
	 * @param name
	 *            The name of the resource.
	 * @return The bytes of the resource or <code>null</code> if not found.
	 */
	public default ByteArrayRegion getResourceBytes(String name) {
		try (ByteSource is = getResourceAsStream(name)) {
			if (is == null) {
				return null;
			}
			try (UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream()) {
				baos.readFrom(is);
				return baos.toByteArrayRegion();
			}
		} catch (IOException e) {
		}
		return null;
	}

	/**
	 * Gets a supplier for an existing resource stream specified by the given name.
	 * <p>
	 * The name is a slash (<code>'/'</code>) separated path to the resource to be found.
	 * <p>
	 * The opened stream by the returned supplier should be closed for each returned stream.
	 * <p>
	 * The returned supplier can still return <code>null</code>, if there was opening error in the stream.
	 * 
	 * @param name
	 *            The name of the resource.
	 * @return A supplier which opens a stream to the resource bytes or <code>null</code> if the resource is not found.
	 */
	public Supplier<? extends ByteSource> getResource(String name);

	/**
	 * Opens a stream to the resource specified by the given name.
	 * <p>
	 * The name is a slash (<code>'/'</code>) separated path to the resource to be found.
	 * <p>
	 * The returned stream should be closed by the caller.
	 * 
	 * @param name
	 *            The name of the resource.
	 * @return The opened stream to the resource, or <code>null</code> if not found.
	 */
	public default ByteSource getResourceAsStream(String name) {
		Supplier<? extends ByteSource> found = getResource(name);
		if (found == null) {
			return null;
		}
		return found.get();
	}

	@Override
	public default void close() throws IOException {
	}

	/**
	 * Converts the resource stream for a given name and resource supplier to an {@link URL}.
	 * <p>
	 * The implementation creates a new {@link URL} with a custom {@link URLStreamHandler} that opens a connection,
	 * which returns an {@link InputStream}, that is backed by the opened resource streams. Every opened stream by the
	 * {@link URLConnection} should be closed.
	 * 
	 * @param name
	 *            The name of the resource.
	 * @param resourcestreamsupplier
	 *            The supplier of the streams for the resource.
	 * @return An URL which opens a connection to the given supplier or <code>null</code> if the argument is
	 *             <code>null</code>.
	 * @see #getResource(String)
	 */
	public static URL toURL(String name, Supplier<? extends ByteSource> resourcestreamsupplier) {
		if (resourcestreamsupplier == null) {
			return null;
		}
		return toURL(name, Collections.singleton(resourcestreamsupplier));
	}

	/**
	 * Converts the specified iterable of resources stream suppliers to an {@link URL}.
	 * <p>
	 * The implementation creates a new {@link URL} with a custom {@link URLStreamHandler} that opens a connection,
	 * which returns an {@link InputStream}, that is backed by the first resource stream found in the argument iterable.
	 * The opened streams from the returned {@link URLConnection} should be closed by the callers.
	 * <p>
	 * Although the suppliers should not return <code>null</code>, this method handles if they do. In which case the
	 * next supplier will be checked to open a stream to its resource, until there are no more. If no supplier opens a
	 * stream, an exception is thrown when the input stream is opened.
	 * 
	 * @param name
	 *            The name of the resource.
	 * @param resourcestreamsuppliers
	 *            The iterable of suppliers for the resource.
	 * @return An URL which opens a connection to the first resource stream found or <code>null</code> if the argument
	 *             is <code>null</code>.
	 */
	public static URL toURL(String name, Iterable<? extends Supplier<? extends ByteSource>> resourcestreamsuppliers) {
		if (ObjectUtils.isNullOrEmpty(resourcestreamsuppliers)) {
			return null;
		}
		try {
			return new URL("cldfistream", null, 0, name, new URLStreamHandler() {
				@Override
				protected URLConnection openConnection(URL u) throws IOException {
					return new URLConnection(u) {
						@Override
						public void connect() throws IOException {
						}

						@Override
						public InputStream getInputStream() throws IOException {
							for (Supplier<? extends ByteSource> ressupplier : resourcestreamsuppliers) {
								ByteSource result = ressupplier.get();
								if (result != null) {
									return ByteSource.toInputStream(result);
								}
							}
							throw new NoSuchFileException(name);
						}
					};
				}
			});
		} catch (MalformedURLException e) {
		}
		return null;
	}
}
