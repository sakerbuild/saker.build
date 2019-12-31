package saker.build.thirdparty.saker.util.classloader;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;

import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSource;

/**
 * {@link ClassLoaderDataFinder} implementation that delegates all of its calls to a subject, but the {@link #close()}
 * call.
 * <p>
 * When this data finder is closed, it will not call {@link ClassLoaderDataFinder#close()} on the delegate.
 */
public class CloseProtectedClassLoaderDataFinder implements ClassLoaderDataFinder {
	private ClassLoaderDataFinder delegate;

	/**
	 * Creates a new instance for the given delegate data finder.
	 * 
	 * @param delegate
	 *            The data finder to forward the calls to.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public CloseProtectedClassLoaderDataFinder(ClassLoaderDataFinder delegate) throws NullPointerException {
		Objects.requireNonNull(delegate, "delegate");
		this.delegate = delegate;
	}

	@Override
	public ByteArrayRegion getClassBytes(String classname) {
		return delegate.getClassBytes(classname);
	}

	@Override
	public Supplier<? extends ByteSource> getResource(String name) {
		return delegate.getResource(name);
	}

	@Override
	public ByteSource getResourceAsStream(String name) {
		return delegate.getResourceAsStream(name);
	}

	@Override
	public ByteArrayRegion getResourceBytes(String name) {
		return delegate.getResourceBytes(name);
	}

	/**
	 * Does nothing.
	 */
	@Override
	public void close() throws IOException {
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + "[" + delegate + "]";
	}

}
