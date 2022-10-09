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
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.JarFile;

import javax.annotation.processing.Processor;

import saker.apiextract.api.ExcludeApi;

/**
 * Utility class containing functions related to {@linkplain ClassLoader class loaders}.
 */
public class ClassLoaderUtil {
	private static final ClassLoader BOOTLOADER_QUERY_CLASSLOADER = new ClassLoader(null) {
	};

	private ClassLoaderUtil() {
		throw new UnsupportedOperationException();
	}

	private static Method findAppendInstrumentationMethodInHierarchy(Class<?> clazz) {
		while (clazz != null) {
			try {
				return clazz.getDeclaredMethod("appendToClassPathForInstrumentation", String.class);
			} catch (NoSuchMethodException | SecurityException e) {
				clazz = clazz.getSuperclass();
			}
		}
		return null;
	}

	/**
	 * Appends a JAR file with the specified path to the system class loader for instrumentation.
	 * <p>
	 * This method will retrieve the {@linkplain ClassLoader#getSystemClassLoader() system classloader} and find a
	 * method with the signature <code>appendToClassPathForInstrumentation(String)</code>. If the method is found, it
	 * will be called with the argument path. If not, the parent of the system classloader is retrieved, and that will
	 * be tried, until there are no more parents.
	 * <p>
	 * The behaviour is based on the documentation of {@link Instrumentation#appendToSystemClassLoaderSearch(JarFile)}.
	 * <p>
	 * This method should be very rarely used, as its purpose is to instrument the JVM, which in most cases can be
	 * unnecessary.
	 * 
	 * @param path
	 *            The path to the JAR.
	 * @return <code>true</code> if the method was found, and was successfully called.
	 */
	//exclude from the API as this is completely untested
	//XXX test this and include in api
	@ExcludeApi
	private static boolean addClassPathForInstrumentation(String path) {
		ClassLoader cl = ClassLoader.getSystemClassLoader();
		while (cl != null) {
			Method method = findAppendInstrumentationMethodInHierarchy(cl.getClass());
			if (method != null) {
				try {
					method.setAccessible(true);
					method.invoke(cl, path);
					return true;
				} catch (SecurityException | IllegalAccessException | IllegalArgumentException
						| InvocationTargetException e) {
					return false;
				}
			}
			cl = cl.getParent();
		}
		return false;
	}

	/**
	 * Opens an input stream for the resource with the given name in the bootstrap classloader.
	 * <p>
	 * This method calls {@link ClassLoader#getResourceAsStream(String)} on the bootstrap classloader, which is
	 * retrieved using {@link #getBootstrapLoader()}.
	 * 
	 * @param name
	 *            The resource name.
	 * @return The opened input stream or <code>null</code> if not found.
	 */
	public static InputStream getBootstrapLoaderResourceInputStream(String name) {
		return BOOTLOADER_QUERY_CLASSLOADER.getResourceAsStream(name);
	}

	/**
	 * Finds a resource URL in the bootstrap classloader.
	 * <p>
	 * This method calls {@link ClassLoader#getResource(String)} on the bootstrap classloader, which is retrieved using
	 * {@link #getBootstrapLoader()}.
	 * 
	 * @param name
	 *            The resource name.
	 * @return The found resource URL or <code>null</code>.
	 */
	public static URL getBootstrapLoaderResourceURL(String name) {
		return BOOTLOADER_QUERY_CLASSLOADER.getResource(name);
	}

	/**
	 * Finds resources in the bootstrap classloader.
	 * <p>
	 * This method calls {@link ClassLoader#getResources(String)} on the bootstrap classloader, which is retrieved using
	 * {@link #getBootstrapLoader()}.
	 * 
	 * @param name
	 *            The resource name.
	 * @return An enumeration of the found resources.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public static Enumeration<URL> getBootstrapLoaderResources(String name) throws IOException {
		return BOOTLOADER_QUERY_CLASSLOADER.getResources(name);
	}

	/**
	 * Gets a {@link ClassLoader} that can be used to access resources defined by the bootstrap class loader.
	 * <p>
	 * As the bootstrap class loader is represented by the <code>null</code> parent class loader, it is not possible to
	 * directly access resources of it. You need to create a new instance of {@link ClassLoader} with <code>null</code>
	 * parent to access resources from it.
	 * <p>
	 * This method returns a such class loader, which has <code>null</code> as its parent, and doesn't load any classes.
	 * In general there is no use for using the class loader returned by this method, however it can be useful when
	 * subclassing {@link ClassLoader} and implementing complex functionality.
	 * <p>
	 * See the documentation of {@link ClassLoader} for more info about the bootstrap class loader.
	 * 
	 * @return A classloader that has <code>null</code> parent and loads no classes.
	 */
	public static ClassLoader getBootstrapLoader() {
		return BOOTLOADER_QUERY_CLASSLOADER;
	}

	/**
	 * Returns the classloader which should be used as a parent to new classloaders in order to access the JRE classes.
	 * <p>
	 * This is important to use, when compatibility with JDK 8 and the JDK 9+ should be achieved.
	 * <p>
	 * For example, when one wants to load {@link Processor}, it is necessary on JDK 9+ to have the platform classloader
	 * as a parent, as the default <code>null</code> parent doesn't contain the requested class. On JDK 8, this will
	 * return <code>null</code>, as the classloaders delegate the loading to the bootstrap classloader, which contains
	 * all the JRE classes. On JDK 9+, this will return <code>ClassLoader.getPlatformClassLoader()</code>
	 * 
	 * @return The parent classloader to use.
	 */
	public static ClassLoader getPlatformClassLoaderParent() {
		return ClassLoaderAccessor.getPlatformClassLoaderParent();
	}
}
