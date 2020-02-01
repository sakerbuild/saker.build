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

import java.util.Objects;
import java.util.Set;

import saker.build.thirdparty.saker.util.ImmutableUtils;

/**
 * {@link ClassLoader} implementation that only provides access for a given set of class names from its parent.
 * <p>
 * An instance of this classloader is constructed with a parent delegate and a set of allowed class names. When a class
 * finding request is made, it is checked that the allowed class names contain the requested class name. If so, the
 * parent classloader will be asked to load the class with the specific name. If not, a {@link ClassNotFoundException}
 * will be thrown.
 * <p>
 * This classloader can be used to limit the available classes from the specified parent classloader. Unlike
 * {@link ParentExclusiveClassLoader}, this classloader doesn't limit the loaded classes to the parent, only that they
 * are accessible through the parent.
 */
public class FilteringClassLoader extends ClassLoader {
	private final ClassLoader parent;
	private final Set<String> allowedClassNames;

	/**
	 * Creates a new instance with the given parent and allowed class names.
	 * 
	 * @param parent
	 *            The parent classloader to filter.
	 * @param allowedClassNames
	 *            The names of the allowed classes.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public FilteringClassLoader(ClassLoader parent, Set<String> allowedClassNames) throws NullPointerException {
		super(null);
		Objects.requireNonNull(parent, "parent");
		Objects.requireNonNull(allowedClassNames, "allowed class names");
		this.parent = parent;
		this.allowedClassNames = ImmutableUtils.makeImmutableNavigableSet(allowedClassNames);
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		if (allowedClassNames.contains(name)) {
			return parent.loadClass(name);
		}
		return super.findClass(name);
	}

}
