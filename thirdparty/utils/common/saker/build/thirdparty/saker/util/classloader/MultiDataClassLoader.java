package saker.build.thirdparty.saker.util.classloader;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.PhantomReference;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSource;

/**
 * {@link ClassLoader} implementation that uses {@link ClassLoaderDataFinder} instances to locate the classes and
 * resources.
 * <p>
 * This classloader can be used when the resources for the to be loaded classes can be retrieved from one or multiple
 * {@link ClassLoaderDataFinder} instance(s).
 * <p>
 * Users should keep track of the data finders which are used to construct a classloader and manually close them later,
 * when the classloader is no longer used. It's also possible to keep a {@link PhantomReference} to the classloader
 * instance, and when the classloader is garbage collected, close the data finders. It requires more implementational
 * code, or maybe use the <code>Cleaner</code> class, but that is only available from JRE9+.
 * <p>
 * When a resource retrieval is requested from the classloader, it will iterate through the data finders registered in
 * it, and return the first match that is found for the resource. The data finders are iterated in the order as it was
 * specified during initialization.
 * <p>
 * The classloader doesn't implement the {@link #findLibrary(String)} function. It should be overridden by subclasses if
 * they need it.
 */
public class MultiDataClassLoader extends ClassLoader {
	static {
		registerAsParallelCapable();
	}

	private final Collection<ClassLoaderDataFinder> datasFinders = new LinkedHashSet<>();

	/**
	 * Creates a new instance.
	 * <p>
	 * Duplicates are removed from the argument data finders.
	 * 
	 * @param parent
	 *            The parent classloader to use. This argument is delegated to the super constructor.
	 * @param datafinders
	 *            The data finders to use.
	 * @throws NullPointerException
	 *             If the datafinders array or any of its elements are <code>null</code>.
	 * @see ClassLoader#ClassLoader(ClassLoader)
	 */
	public MultiDataClassLoader(ClassLoader parent, ClassLoaderDataFinder... datafinders) throws NullPointerException {
		super(parent);
		Objects.requireNonNull(datafinders, "data finders");
		for (ClassLoaderDataFinder cldf : datafinders) {
			Objects.requireNonNull(cldf, "data finder");
			this.datasFinders.add(cldf);
		}
	}

	/**
	 * Creates a new instance.
	 * <p>
	 * Duplicates are removed from the argument data finders.
	 * 
	 * @param datafinders
	 *            The data finders to use.
	 * @throws NullPointerException
	 *             If the datafinders array or any of its elements are <code>null</code>.
	 * @see ClassLoader#ClassLoader()
	 */
	public MultiDataClassLoader(ClassLoaderDataFinder... datafinders) throws NullPointerException {
		Objects.requireNonNull(datafinders, "data finders");
		for (ClassLoaderDataFinder cldf : datafinders) {
			Objects.requireNonNull(cldf, "data finder");
			this.datasFinders.add(cldf);
		}
	}

	/**
	 * Creates a new instance.
	 * <p>
	 * Duplicates are removed from the argument data finders.
	 * 
	 * @param parent
	 *            The parent classloader to use. This argument is delegated to the super constructor.
	 * @param datafinders
	 *            The data finders to use.
	 * @throws NullPointerException
	 *             If the datafinders iterable or any of its elements are <code>null</code>.
	 * @see ClassLoader#ClassLoader(ClassLoader)
	 */
	public MultiDataClassLoader(ClassLoader parent, Iterable<? extends ClassLoaderDataFinder> datafinders)
			throws NullPointerException {
		super(parent);
		Objects.requireNonNull(datafinders, "data finders");
		for (ClassLoaderDataFinder cldf : datafinders) {
			Objects.requireNonNull(cldf, "data finder");
			this.datasFinders.add(cldf);
		}
	}

	/**
	 * Creates a new instance.
	 * <p>
	 * Duplicates are removed from the argument data finders.
	 * 
	 * @param datafinders
	 *            The data finders to use.
	 * @throws NullPointerException
	 *             If the datafinders iterable or any of its elements are <code>null</code>.
	 * @see ClassLoader#ClassLoader()
	 */
	public MultiDataClassLoader(Iterable<? extends ClassLoaderDataFinder> datafinders) throws NullPointerException {
		Objects.requireNonNull(datafinders, "data finders");
		for (ClassLoaderDataFinder cldf : datafinders) {
			Objects.requireNonNull(cldf, "data finder");
			this.datasFinders.add(cldf);
		}
	}

	/**
	 * Gets the data finders which are used by this classloader.
	 * 
	 * @return An unmodifiable collection of data finders.
	 */
	public Collection<ClassLoaderDataFinder> getDatasFinders() {
		return Collections.unmodifiableCollection(datasFinders);
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		Collection<ClassLoaderDataFinder> datafinders = datasFinders;
		if (!datafinders.isEmpty()) {
			ClassNotFoundException exc = null;
			for (ClassLoaderDataFinder d : datafinders) {
				ByteArrayRegion bar;
				try {
					bar = d.getClassBytes(name);
				} catch (RuntimeException e) {
					//in case of implementation error, or some others like zip is closed
					if (exc == null) {
						exc = new ClassNotFoundException(name, e);
					}
					exc.addSuppressed(e);
					continue;
				}
				if (bar != null) {
					try {
						return defineClass(name, bar.getArray(), bar.getOffset(), bar.getLength(),
								getProtectionDomain(d, name));
					} catch (LinkageError e) {
						//in case of some errors
						if (exc == null) {
							exc = new ClassNotFoundException(name, e);
						}
						exc.addSuppressed(e);
					}
				} else {
					ClassNotFoundException e = new ClassNotFoundException(name + " not found in " + d);
					if (exc == null) {
						exc = e;
					} else {
						exc.addSuppressed(e);
					}
				}
			}
			if (exc != null) {
				throw exc;
			}
		}
		return super.findClass(name);
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		ClassLoader parent = getParent();
		if (parent != null) {
			InputStream res = parent.getResourceAsStream(name);
			if (res != null) {
				return res;
			}
		} else {
			InputStream res = ClassLoaderUtil.getBootstrapLoaderResourceInputStream(name);
			if (res != null) {
				return res;
			}
		}
		for (ClassLoaderDataFinder d : datasFinders) {
			ByteSource res = d.getResourceAsStream(name);
			if (res != null) {
				return ByteSource.toInputStream(res);
			}
		}
		return null;
	}

	@Override
	protected URL findResource(String name) {
		return ClassLoaderDataFinder.toURL(name, findResourceSuppliersImpl(name));
	}

	@Override
	protected Enumeration<URL> findResources(String name) throws IOException {
		Set<URL> result = findResourceURLsImpl(name);
		if (result.isEmpty()) {
			return Collections.emptyEnumeration();
		}
		return Collections.enumeration(result);
	}

	private Set<? extends Supplier<? extends ByteSource>> findResourceSuppliersImpl(String name) {
		Set<Supplier<? extends ByteSource>> result = new LinkedHashSet<>();
		for (ClassLoaderDataFinder d : datasFinders) {
			Supplier<? extends ByteSource> url = d.getResource(name);
			if (url != null) {
				result.add(url);
			}
		}
		return result;
	}

	private Set<URL> findResourceURLsImpl(String name) {
		Set<URL> result = new LinkedHashSet<>();
		for (ClassLoaderDataFinder d : datasFinders) {
			URL url = ClassLoaderDataFinder.toURL(name, d.getResource(name));
			if (url != null) {
				result.add(url);
			}
		}
		return result;
	}

	/**
	 * Gets the protection domain that should be used when defining a class.
	 * <p>
	 * This method can be overridden by subclasses to provide a {@link ProtectionDomain} to use when defining a class.
	 * <p>
	 * The default implementation returns <code>null</code>.
	 * 
	 * @param datafinder
	 *            The data finder that was used to retrieve the class bytes.
	 * @param name
	 *            The name of the class to define.
	 * @return The protection domain to use, may be <code>null</code>.
	 * @see #defineClass(String, byte[], int, int, ProtectionDomain)
	 */
	protected ProtectionDomain getProtectionDomain(ClassLoaderDataFinder datafinder, String name) {
		return null;
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + "["
				+ String.join(", ", (Iterable<String>) datasFinders.stream().map(Object::toString)::iterator) + "]";
	}
}
