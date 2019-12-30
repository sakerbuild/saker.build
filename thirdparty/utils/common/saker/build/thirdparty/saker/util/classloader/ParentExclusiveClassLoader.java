package saker.build.thirdparty.saker.util.classloader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Objects;

/**
 * {@link ClassLoader} implementation that only allows access to the classes which are directly loaded from the
 * designated parent classloader.
 * <p>
 * This classloader is recommended to be used as a parent classloader when it is required to limit access to classes
 * which are not directly loaded by the parent classloader.
 * <p>
 * An example: <br>
 * The interface P is loaded from the classloader PCL. <br>
 * A subinterface of P, S is loaded from the classloader SCL. <br>
 * We'd like to create an implementation of S in a different classloader, that doesn't have direct access to P. In this
 * case we create a parent exclusive classloader XCL which receives SCL as its parent during construction. <br>
 * We place our implementation class I which implements S in the XCL classloader. In this case, we achieved that S is
 * visible from XCL, and S can be successfully implemented by I, but P cannot be directly implemented in XCL, as it is
 * not visible from it. In other words, we cannot define a class, that <code>implements P</code> in the classloader XCL.
 * <p>
 * The {@link #getParent()} function of this class will always return <code>null</code>.
 * <p>
 * When a class loading request is issued to this classloader, the parent will be asked to load the class. If
 * successfull, the classloader will be queried of the loaded class, and the class will only be returned if the identity
 * of the classloader is the same as the parent specified for this instance. This means that classes that are not
 * available through this classloader may still be loaded, and be considered not found by this instance.
 * <p>
 * This class has limited use-case, and probably not relevant to most users. For informational purposes, one use-case
 * for this classloader is when a class with a given name is present in a hierarchical classpath in multiple levels, and
 * one doesn't want the classes from the upper levels to leak into the lower levels. This can happen when testing
 * frameworks want to test their own implementation, but at the same time have the framework on the classpath for the
 * test runners.
 */
public class ParentExclusiveClassLoader extends ClassLoader {
	private ClassLoader parent;

	/**
	 * Creates a new instance that is exclusive to the specified parent classloader.
	 * 
	 * @param parent
	 *            The exclusive parent.
	 * @throws NullPointerException
	 *             If the parent is <code>null</code>.
	 */
	public ParentExclusiveClassLoader(ClassLoader parent) throws NullPointerException {
		super(null);
		this.parent = Objects.requireNonNull(parent, "parent");
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		ClassLoader parent = this.parent;
		Class<?> got = parent.loadClass(name);
		ClassLoader gotcl = got.getClassLoader();
		if (gotcl == parent || gotcl == null) {
			return got;
		}
		return super.findClass(name);
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		ClassLoader parent = this.parent;
		Class<?> got = parent.loadClass(name);
		ClassLoader gotcl = got.getClassLoader();
		if (gotcl == parent || gotcl == null) {
			if (resolve) {
				resolveClass(got);
			}
			return got;
		}
		throw new ClassNotFoundException(name);
	}

	@Override
	public URL getResource(String name) {
		return this.parent.getResource(name);
	}

	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		return this.parent.getResources(name);
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		return this.parent.getResourceAsStream(name);
	}
}
