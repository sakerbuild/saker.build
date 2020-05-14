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
package saker.build.util.java;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.tools.DocumentationTool;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import saker.apiextract.api.PublicApi;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolver;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolverRegistry;
import saker.build.util.JavaCompilerAccessor;

/**
 * Utility class for handling JRE and JDK resources.
 */
@PublicApi
public class JavaTools {

	/**
	 * Gets the current value of the <code>java.version</code> system property.
	 * <p>
	 * The returned value is cached during the initialization of this class, and will return the same values, even if
	 * the system property is changed meanwhile.
	 * 
	 * @return The value of <code>java.version</code> system property.
	 */
	public static String getCurrentJavaVersionProperty() {
		return JAVA_VERSION_PROPERTY;
	}

	/**
	 * Gets the current Java Runtime major version.
	 * <p>
	 * This major version of the JRE is incremented for each major release. The first release that this method supports
	 * is release 8, so this method will always return a value greater than that.
	 * <p>
	 * This method determines the JRE version by checking for the exitence of th <code>java.lang.Runtime.Version</code>
	 * class and if it exists, the <code>major()</code> method will be called on the instance retrieved from
	 * <code>Runtime.Version()</code>.
	 * <p>
	 * If the version class is not found, it means that the current JRE doesn't contain that class, which can only
	 * happen if we're running on older releases than JRE 9. As this class will always be compiled for 8 or later
	 * releases, the major version 8 will be returned if the version class is not found.
	 * 
	 * @return The runtime major version.
	 */
	public static int getCurrentJavaMajorVersion() {
		return JAVA_MAJOR_VERSION;
	}

	/**
	 * Gets the path of the installation directory of the current JRE.
	 * <p>
	 * The returned value points to the root directory of the installation.
	 * <p>
	 * If the installation is JDK8, then the result will point to the root installation directory, not the subdirectory
	 * for the accompanying JRE. (I.e. the result is the install dir, not its <code>jre</code> subdirectory.)
	 * <p>
	 * It can be expected that there is an excecutable under this directory with the path <code>bin/java</code> or
	 * <code>bin/java.exe</code>.
	 * 
	 * @return The installation directory.
	 */
	public static Path getJavaInstallationDirectory() {
		return JAVA_INSTALLATION_DIRECTORY;
	}

	/**
	 * Gets a reference to a {@link JavaCompiler} instance.
	 * <p>
	 * It is recommended to call this instead of {@link ToolProvider#getSystemJavaCompiler()}, as the implementation can
	 * cache the classloader in relation with the {@link #getJDKToolsClassLoader()} call.
	 * 
	 * @return A Java compiler instance.
	 */
	public static JavaCompiler getSystemJavaCompiler() {
		return JavaCompilerAccessor.getSystemJavaCompiler();
	}

	/**
	 * Gets a reference to a {@link DocumentationTool} instance.
	 * <p>
	 * It is recommended to call this instead of {@link ToolProvider#getSystemDocumentationTool()}, as the
	 * implementation can cache the classloader in relation with the {@link #getJDKToolsClassLoader()} call.
	 * 
	 * @return A documentation tool instance.
	 */
	public static DocumentationTool getSystemDocumentationTool() {
		return JavaCompilerAccessor.getSystemDocumentationTool();
	}

	/**
	 * Gets the main class for the jarsigner tool.
	 * <p>
	 * The method retrieves the class that contains the <code>main(String[])</code> method for the jarsigner utility.
	 * <p>
	 * For JDK8, the method will retrieve the <code>sun.security.tools.jarsigner.Main</code> from the
	 * <code>tools.jar</code> of the JDK.
	 * <p>
	 * For JDK9 or later, the method will get the <code>sun.security.tools.jarsigner.Main</code> class from the platform
	 * classloader.
	 * <p>
	 * In general, it is recommended that callers use the {@link jdk.security.jarsigner.JarSigner} class to perform JAR
	 * signing operations on JDK 9 or later.
	 * 
	 * @return The main class for the jarsigner utility.
	 * @throws RuntimeException
	 *             If the class was not found. Callers should gracefully handle this scenario.
	 * @since 0.8.10
	 */
	public static Class<?> getJarSignerMainClass() throws RuntimeException {
		return JavaCompilerAccessor.getJarSignerMainClass();
	}

	/**
	 * Gets a classloader that can load the JDK related compiler and documentational classes.
	 * <p>
	 * The returned classloader can be used as a parent classloader to access classes for the Java Compiler API. (E.g.
	 * the source Trees).
	 * <p>
	 * On JDK8, this method will try to load the <code>lib/tools.jar</code> from the
	 * {@linkplain #getJavaInstallationDirectory() installation directory}. The classloader will be cached using weak or
	 * soft references.
	 * <p>
	 * On later versions (JDK9+), this method will return the platform classloader.
	 * <p>
	 * It is recommended to call this instead of {@link ToolProvider#getSystemToolClassLoader()}, as that is deprecated
	 * in JDK9.
	 * 
	 * @return The classloader for accessing JDK classes.
	 * @throws IOException
	 *             In case of I/O error. (If the <code>tools.jar</code> is not found or failed to load.)
	 */
	public static ClassLoader getJDKToolsClassLoader() throws IOException {
		return JavaCompilerAccessor.getJDKToolsClassLoader();
	}

	/**
	 * Gets the JDK tools classloader if it is already loaded.
	 * <p>
	 * This method is the same as {@link #getJDKToolsClassLoader()}, but returns <code>null</code> if the
	 * {@link ClassLoader} has not yet been loaded.
	 * 
	 * @return The classloader for accessing JDK classes or <code>null</code> if not yet loaded.
	 * @since saker.build 0.8.12
	 */
	public static ClassLoader getJDKToolsClassLoaderIfLoaded() {
		return JavaCompilerAccessor.getJDKToolsClassLoaderIfLoaded();
	}

	/**
	 * Gets a classloader resolver instance that resolves the classes in the {@linkplain #getJDKToolsClassLoader() JDK
	 * tools classloader}.
	 * <p>
	 * The returned instance is a singleton, it will not be garbage collected when no longer referenced. I.e. it can be
	 * registered to a {@link ClassLoaderResolverRegistry} without keeping a strong reference to the returned resolver.
	 * 
	 * @return The classloader resolver.
	 */
	public static ClassLoaderResolver getJDKToolsClassLoaderResolver() {
		return JavaCompilerAccessor.getJDKToolsClassLoaderResolver();
	}

	/**
	 * Gets the path to the <code>java</code> executable for a specified JRE or JDK installation directory.
	 * <p>
	 * The method will resolve the <code>java</code> path in the <code>bin</code> directory of the specified argument.
	 * If the argument has already a file name of <code>bin</code> then it won't be resolved twice.
	 * <p>
	 * The method resolves <code>java</code> as the last path, and will not behave distinctly for different operating
	 * systems. I.e. it will not resolve <code>java.exe</code> for Windows. Based on our experience,
	 * {@link ProcessBuilder} will accept the returned path without the <code>.exe</code> extension when starting
	 * processes, which is most likely what the caller wants.
	 * 
	 * @param jreorjdkdir
	 *            The JDK or JRE installation directory.
	 * @return The path to the <code>java</code> executable.
	 */
	public static Path getJavaExeProcessPath(Path jreorjdkdir) {
		if (!jreorjdkdir.getFileName().toString().equals("bin")) {
			jreorjdkdir = jreorjdkdir.resolve("bin");
		}
		return jreorjdkdir.resolve("java");
	}

	/**
	 * Gets the path to the <code>java</code> executable of the current JRE installation directory.
	 * 
	 * @return The <code>java</code> executable path.
	 * @see #getJavaInstallationDirectory()
	 * @see #getJavaExeProcessPath(Path)
	 */
	public static Path getCurrentJavaExePath() {
		return getJavaExeProcessPath(getJavaInstallationDirectory());
	}

	private static final Path JAVA_INSTALLATION_DIRECTORY;
	private static final String JAVA_VERSION_PROPERTY = System.getProperty("java.version");
	private static final int JAVA_MAJOR_VERSION = queryJreMajorVersion();

	static {
		Path installdir = Paths.get(System.getProperty("java.home"));
		String ifname = installdir.getFileName().toString();
		if ("bin".equals(ifname)) {
			installdir = installdir.getParent();
		} else if ("jre".equals(ifname)) {
			//the directory can be "jre" if this is a jdk 8 installation and the java.exe is called under jre/bin/java.exe
			Path parent = installdir.getParent();
			//check for the java exe under the parent dir
			//check for java and java.exe for cross platform compatibility
			if (Files.isExecutable(parent.resolve("bin/java.exe")) || Files.isExecutable(parent.resolve("bin/java"))) {
				installdir = parent;
			}
		}
		JAVA_INSTALLATION_DIRECTORY = installdir;
	}

	private static int queryJreMajorVersion() {
		try {
			Class<?> versionclass = Class.forName("java.lang.Runtime$Version", false, null);
			Method versionmethod = Runtime.class.getMethod("version");
			Object runtimeversion = versionmethod.invoke(null);
			return (int) versionclass.getMethod("major").invoke(runtimeversion);
		} catch (ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException
				| IllegalArgumentException | InvocationTargetException ignored) {
		}
		//if some methods or classes introduced in jre 9 was not found, return 8
		return 8;
	}

	private JavaTools() {
		throw new UnsupportedOperationException();
	}
}
