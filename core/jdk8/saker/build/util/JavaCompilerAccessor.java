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
package saker.build.util;

import java.io.IOException;
import java.lang.ref.Reference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

import javax.tools.DocumentationTool;
import javax.tools.JavaCompiler;

import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolver;
import saker.build.thirdparty.saker.util.classloader.JarClassLoaderDataFinder;
import saker.build.thirdparty.saker.util.classloader.MultiDataClassLoader;
import saker.build.util.config.ReferencePolicy;
import saker.build.util.java.JavaTools;

public class JavaCompilerAccessor {
	private static final String JAVACTOOL_CLASS_NAME = "com.sun.tools.javac.api.JavacTool";
	private static final String JAVADOCTOOL_CLASS_NAME = "com.sun.tools.javadoc.api.JavadocTool";
	private static final String JARSIGNER_MAIN_CLASS_NAME = "sun.security.tools.jarsigner.Main";
	private static final String TOOLS_JAR_NAME = "tools.jar";

	private static final Map<String, JarClassLoaderDataFinder> jdkJarDataFinders = new ConcurrentSkipListMap<>();
	private static final Map<String, Reference<ClassLoader>> jdkJars = new ConcurrentSkipListMap<>();

	private JavaCompilerAccessor() {
		throw new UnsupportedOperationException();
	}

	public static JavaCompiler getSystemJavaCompiler() {
		return getSystemTool(JavaCompiler.class, JAVACTOOL_CLASS_NAME);
	}

	public static DocumentationTool getSystemDocumentationTool() {
		return getSystemTool(DocumentationTool.class, JAVADOCTOOL_CLASS_NAME);
	}

	private static <T> T getSystemTool(Class<T> clazz, String name) {
		try {
			Class<?> foundclass = getSystemToolClass(name);
			return foundclass.asSubclass(clazz).getConstructor().newInstance();
		} catch (Exception e) {
			throw new RuntimeException("Failed to instantiate java tool class: " + name, e);
		}
	}

	private static Class<?> getSystemToolClass(String name) throws ClassNotFoundException, IOException {
		return Class.forName(name, false, getJDKToolsClassLoader());
	}

	public synchronized static ClassLoader getJDKToolsClassLoader() throws IOException {
		ClassLoader cl = ObjectUtils.getReference(jdkJars.get(TOOLS_JAR_NAME));
		if (cl == null) {
			JarClassLoaderDataFinder datafinder = jdkJarDataFinders.get(TOOLS_JAR_NAME);
			if (datafinder == null) {
				Path jdkjarpath = getJDKJarPath(TOOLS_JAR_NAME);
				datafinder = new JarClassLoaderDataFinder(jdkjarpath);
				jdkJarDataFinders.put(TOOLS_JAR_NAME, datafinder);
			}
			cl = new MultiDataClassLoader((ClassLoader) null, datafinder);
			jdkJars.put(TOOLS_JAR_NAME, ReferencePolicy.createReference(cl));
		}
		return cl;
	}

	public static ClassLoader getJDKToolsClassLoaderIfLoaded() {
		return ObjectUtils.getReference(jdkJars.get(TOOLS_JAR_NAME));
	}

	public static ClassLoaderResolver getJDKToolsClassLoaderResolver() {
		return JDKToolsClassLoaderResolver.INSTANCE;
	}

	public static Class<?> getJarSignerMainClass() {
		try {
			return getSystemToolClass(JARSIGNER_MAIN_CLASS_NAME);
		} catch (ClassNotFoundException | IOException e) {
			throw new RuntimeException("Failed to get jarsigner tool class: " + JARSIGNER_MAIN_CLASS_NAME, e);
		}
	}

	private static Path getJDKJarPath(String jarname) {
		Path path = JavaTools.getJavaInstallationDirectory();
		Path firstguesspath = path.resolve("lib").resolve(jarname);
		if (Files.isRegularFile(firstguesspath)) {
			//in some cases, the jar may be in the jre/lib directory.
			//e.g. for Intellij JRE installations where the JDK is installed to somewhere:
			//   .....\com.jetbrains\jbre\jbrex8u152b1343.15_windows_x64\jre\lib\tools.jar
			//so we try if the jar exists directly under the JRE.
			return firstguesspath;
		}
		if (path.getFileName().toString().equalsIgnoreCase("jre")) {
			//if we're in a jre directory, try resolving it through the parent
			//any failures will be detected by the caller as this is the last resort
			return path.getParent().resolve("lib").resolve(jarname);
		}
		//if we're not in the jre directory, and haven't found it under the lib/.jar path, just return it
		//failures are detected by the caller
		return firstguesspath;
	}

	private static enum JDKToolsClassLoaderResolver implements ClassLoaderResolver {
		INSTANCE;

		private static final String CLASSLOADER_IDENTIFIER = "jdk8.tools.classloader";

		@Override
		public String getClassLoaderIdentifier(ClassLoader classloader) {
			if (classloader == JavaCompilerAccessor.getJDKToolsClassLoaderIfLoaded()) {
				return CLASSLOADER_IDENTIFIER;
			}
			return null;
		}

		@Override
		public ClassLoader getClassLoaderForIdentifier(String identifier) {
			if (CLASSLOADER_IDENTIFIER.equals(identifier)) {
				try {
					return JavaCompilerAccessor.getJDKToolsClassLoader();
				} catch (IOException e) {
					//ignored, just not found.
				}
			}
			return null;
		}
	}
}
