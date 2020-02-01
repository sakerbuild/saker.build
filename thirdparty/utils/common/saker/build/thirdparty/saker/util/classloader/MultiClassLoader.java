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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;

/**
 * {@link ClassLoader} implementation that aggregates multiple parent classloaders.
 * <p>
 * This classloader forwards class loading and resource retrieval requests to the enclosed classloader references.
 * <p>
 * This classloader can be used when a parent classloader is required that provides access to multiple separate
 * classloaders. This is often useful when multiple classpaths are loaded from different sources and a newly loaded
 * classpath entity needs access to more already loaded classpaths.
 * <p>
 * This class is subclassable, but generally is not recommended. Users can use one of the static {@link #create} methods
 * to create a new instance.
 */
public class MultiClassLoader extends ClassLoader {
	/**
	 * The collection of classloaders which are used by this classloader.
	 * <p>
	 * Immutable collection.
	 */
	protected final Collection<ClassLoader> classLoaders;

	/**
	 * Delegates to {@link #MultiClassLoader(ClassLoader, Collection)} with <code>null</code> parent classloader.
	 */
	protected MultiClassLoader(Collection<? extends ClassLoader> classLoaders) {
		this(null, classLoaders);
	}

	/**
	 * Creates a new instance that has the argument parent classloader, and the specified classloaders.
	 * 
	 * @param parent
	 *            The parent classloader.
	 * @param classLoaders
	 *            The classloaders to delegate to.
	 */
	protected MultiClassLoader(ClassLoader parent, Collection<? extends ClassLoader> classLoaders) {
		super(parent);
		this.classLoaders = ImmutableUtils.makeImmutableLinkedHashSet(classLoaders);
	}

	/**
	 * Gets a classloader which loads classes using the argument classloaders.
	 * <p>
	 * The classloaders will be reduced according to the rules of {@link #reduceClassLoaders(Collection)}.
	 * <p>
	 * The returned classloader might not be an instance of {@link MultiClassLoader}. This can be the case when the
	 * argument collection is empty, or contains a single item and the parent is <code>null</code>.
	 * 
	 * @param parent
	 *            The parent classloader to use.
	 * @param classloaders
	 *            The classloaders.
	 * @return A classloader defined by the argument.
	 * @throws NullPointerException
	 *             If the classloaders are <code>null</code>.
	 */
	public static ClassLoader create(ClassLoader parent, Collection<? extends ClassLoader> classloaders)
			throws NullPointerException {
		Set<ClassLoader> cls = reduceClassLoaders(classloaders);

		int clsize = cls.size();
		if (clsize == 0) {
			if (parent == null) {
				return ClassLoaderUtil.getBootstrapLoader();
			}
			return parent;
		}
		if (clsize == 1 && parent == null) {
			return cls.iterator().next();
		}

		return new MultiClassLoader(parent, classloaders);
	}

	/**
	 * Gets a classloader which loads classes using the argument classloaders.
	 * <p>
	 * The classloaders will be reduced according to the rules of {@link #reduceClassLoaders(Collection)}.
	 * <p>
	 * The <code>null</code> classloader will be used as a parent classloader for the newly created classloader. That is
	 * the bootstrap classloader, and not the system classloader.
	 * <p>
	 * Unlike the constructor {@link ClassLoader#ClassLoader()}, which has the
	 * {@linkplain ClassLoader#getSystemClassLoader() system classloader} as its parent, the returned classloader will
	 * have the bootstrap classloader instead.
	 * 
	 * @param classloaders
	 *            The classloaders.
	 * @return A classloader defined by the argument.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static ClassLoader create(Collection<? extends ClassLoader> classloaders) throws NullPointerException {
		return create(null, classloaders);
	}

	/**
	 * Reduces the argument classloaders to its smallest set that doesn't contain duplicate parent classloaders.
	 * <p>
	 * All classloaders will be part of the result set, unless:
	 * <ul>
	 * <li>It is <code>null</code>. <code>null</code> classloader represents the bootstrap classloader, which is always
	 * accessible.</li>
	 * <li>A classloader is already present as a {@linkplain ClassLoader#getParent() parent} of an other classloader.
	 * E.g. If P is the parent of C, then if the set of [P, C, X] is reduced, the result will only be [C, X], as P is
	 * already accessible via C.</li>
	 * </ul>
	 * 
	 * @param classloaders
	 *            The classloaders to reduce.
	 * @return A set of reduced classloaders, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static Set<ClassLoader> reduceClassLoaders(Collection<? extends ClassLoader> classloaders) {
		if (classloaders == null) {
			return null;
		}
		Set<ClassLoader> cls = new LinkedHashSet<>();
		outer: for (ClassLoader c : classloaders) {
			if (c == null) {
				continue;
			}
			if (cls.isEmpty()) {
				cls.add(c);
			} else {
				for (ClassLoader prevc : cls) {
					if (ReflectUtils.hasParentClassLoader(prevc, c)) {
						//c is already added as a parent of prevc
						continue outer;
					}
				}
				for (Iterator<ClassLoader> it = cls.iterator(); it.hasNext();) {
					ClassLoader prevc = it.next();
					if (ReflectUtils.hasParentClassLoader(c, prevc)) {
						it.remove();
					}
				}
				cls.add(c);
			}
		}
		return cls;
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		ClassNotFoundException exc = null;
		for (ClassLoader cl : classLoaders) {
			try {
				return cl.loadClass(name);
			} catch (ClassNotFoundException e) {
				exc = IOUtils.addExc(exc, e);
			}
		}
		IOUtils.throwExc(exc);
		throw new ClassNotFoundException(name);
	}

	@Override
	protected URL findResource(String name) {
		for (ClassLoader cl : classLoaders) {
			URL res = cl.getResource(name);
			if (res != null) {
				return res;
			}
		}
		return null;
	}

	@Override
	protected Enumeration<URL> findResources(String name) throws IOException {
		Set<URL> result = new LinkedHashSet<>();
		for (ClassLoader cl : classLoaders) {
			Enumeration<URL> clfoundres = cl.getResources(name);
			while (clfoundres.hasMoreElements()) {
				result.add(clfoundres.nextElement());
			}
		}
		return Collections.enumeration(result);
	}

	//no need to implement findLibrary, as System.loadLibrary and other functions are based on the classloader of the caller
	//    and as this classloader doesn't directly define any classes, therefore it won't be called, but on the defining classloader directly 

	@Override
	public InputStream getResourceAsStream(String name) {
		ClassLoader parent = getParent();
		if (parent != null) {
			InputStream got = parent.getResourceAsStream(name);
			if (got == null) {
				got = ClassLoader.getSystemResourceAsStream(name);
			}
			if (got != null) {
				return got;
			}
		}
		for (ClassLoader cl : classLoaders) {
			InputStream res = cl.getResourceAsStream(name);
			if (res != null) {
				return res;
			}
		}
		return null;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "["
				+ String.join(", ", (Iterable<String>) classLoaders.stream().map(ClassLoader::toString)::iterator)
				+ "]";
	}
}
