package saker.build.util;

import java.io.IOException;
import java.lang.ref.Reference;
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
			Class<?> foundclass = Class.forName(name, false, getJDKToolsClassLoader());
			return foundclass.asSubclass(clazz).getConstructor().newInstance();
		} catch (Exception e) {
			throw new RuntimeException("Failed to instantiate java tool class: " + name, e);
		}
	}

	public synchronized static ClassLoader getJDKToolsClassLoader() throws IOException {
		String jarname = TOOLS_JAR_NAME;
		ClassLoader cl = ObjectUtils.getReference(jdkJars.get(jarname));
		if (cl == null) {
			JarClassLoaderDataFinder datafinder = jdkJarDataFinders.get(jarname);
			if (datafinder == null) {
				Path jdkjarpath = getJDKJarPath(jarname);
				datafinder = new JarClassLoaderDataFinder(jdkjarpath);
				jdkJarDataFinders.put(jarname, datafinder);
			}
			cl = new MultiDataClassLoader((ClassLoader) null, datafinder);
			jdkJars.put(jarname, ReferencePolicy.createReference(cl));
		}
		return cl;
	}

	public static ClassLoader getJDKToolsClassLoaderIfLoaded() {
		return ObjectUtils.getReference(jdkJars.get(TOOLS_JAR_NAME));
	}

	public static ClassLoaderResolver getJDKToolsClassLoaderResolver() {
		return JDKToolsClassLoaderResolver.INSTANCE;
	}

	private static Path getJDKJarPath(String jarname) {
		Path path = JavaTools.getJavaInstallationDirectory();
		if (path.getFileName().toString().equalsIgnoreCase("jre")) {
			path = path.getParent();
		}
		path = path.resolve("lib").resolve(jarname);
		return path;
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
