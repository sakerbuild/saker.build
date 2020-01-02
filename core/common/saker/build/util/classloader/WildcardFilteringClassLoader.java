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
package saker.build.util.classloader;

import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import saker.build.file.path.WildcardPath;

/**
 * {@link ClassLoader} implementation that only finds classes which have their name match the specified wildcards.
 * <p>
 * This classloader is constructed with a given classloader, and a set of wildcard names. When a class loading request
 * is received, this classloader will examine the name of the to be loaded class, and check if any of the specified
 * wildcards include it. If yes, then the specified parent classloader will be asked to load the class with the given
 * name. If no matching wildcard found, then an appropriate exception will be thrown during class loading.
 * <p>
 * This classloader can be used to restrict access to classloader.
 * <p>
 * The {@link #getParent()} function of this class will always return <code>null</code>.
 * <p>
 * The wildcard mechanism works the same way as {@link WildcardPath}, but the <code>'.'</code> dot is considered to be
 * the separactor character instead of the slash.
 * <p>
 * Use the static factory methods to create a new instance.
 */
public class WildcardFilteringClassLoader extends ClassLoader {
	private final ClassLoader parent;
	private final Set<WildcardPath> allowedPatterns;

	private WildcardFilteringClassLoader(ClassLoader parent, Set<WildcardPath> allowedPatterns) {
		this.parent = parent;
		this.allowedPatterns = allowedPatterns;
	}

	/**
	 * Creates a new instance for the given parent classloader and allowed wildcard patterns.
	 * <p>
	 * If no allowed patterns are specified (i.e. empty iterable), <code>null</code> is returned.
	 * 
	 * @param parent
	 *            The parent classloader to delegate to.
	 * @param allowedpatterns
	 *            The allowed patterns of class names. The patterns should be in dot separated format.
	 * @return The classloader based on the arguments.
	 * @throws NullPointerException
	 *             If any of the arguments, or patterns are <code>null</code>.
	 */
	public static ClassLoader create(ClassLoader parent, Iterable<? extends CharSequence> allowedpatterns)
			throws NullPointerException {
		Objects.requireNonNull(parent, "parent");
		Objects.requireNonNull(allowedpatterns, "allowed patterns");
		Iterator<? extends CharSequence> it = allowedpatterns.iterator();
		if (!it.hasNext()) {
			return null;
		}
		Set<WildcardPath> patterns = new TreeSet<>();
		while (it.hasNext()) {
			CharSequence cs = it.next();
			Objects.requireNonNull(cs, "pattern");
			patterns.add(WildcardPath.valueOf(cs.toString().replace('.', '/')));
		}
		return new WildcardFilteringClassLoader(parent, patterns);
	}

	/**
	 * Creates a new instance for the given parent classloader and allowed wildcard patterns.
	 * <p>
	 * If no allowed patterns are specified (i.e. empty iterable), <code>null</code> is returned.
	 * 
	 * @param parent
	 *            The parent classloader to delegate to.
	 * @param allowedpatterns
	 *            The allowed patterns of class names. The patterns should be in dot separated format.
	 * @return The classloader based on the arguments.
	 * @throws NullPointerException
	 *             If any of the arguments, or patterns are <code>null</code>.
	 */
	public static ClassLoader create(ClassLoader parent, CharSequence... allowedpatterns) throws NullPointerException {
		Objects.requireNonNull(parent, "parent");
		Objects.requireNonNull(allowedpatterns, "allowed patterns");
		if (allowedpatterns.length == 0) {
			return null;
		}
		Set<WildcardPath> patterns = new TreeSet<>();
		for (int i = 0; i < allowedpatterns.length; i++) {
			CharSequence cs = allowedpatterns[i];
			Objects.requireNonNull(cs, "pattern");
			patterns.add(WildcardPath.valueOf(cs.toString().replace('.', '/')));
		}
		return new WildcardFilteringClassLoader(parent, patterns);
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		String slashedname = name.replace('.', '/');
		for (WildcardPath wc : allowedPatterns) {
			if (wc.includes(slashedname)) {
				return parent.loadClass(name);
			}
		}
		return super.findClass(name);
	}

}
