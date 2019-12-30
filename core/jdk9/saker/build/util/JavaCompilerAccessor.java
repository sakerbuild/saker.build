package saker.build.util;

import java.io.IOException;

import javax.tools.DocumentationTool;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolver;

public class JavaCompilerAccessor {
	private static final ClassLoader PLATFORM_CLASSLOADER = ClassLoader.getPlatformClassLoader();
	private static final ClassLoader JDK_COMPILER_CLASSLOADER;
	static {
		try {
			JDK_COMPILER_CLASSLOADER = Class.forName("com.sun.source.tree.Tree", false, PLATFORM_CLASSLOADER)
					.getClassLoader();
		} catch (ClassNotFoundException e) {
			throw new AssertionError("Known jdk.compiler class not found.", e);
		}
	}

	private JavaCompilerAccessor() {
		throw new UnsupportedOperationException();
	}

	public static JavaCompiler getSystemJavaCompiler() {
		return ToolProvider.getSystemJavaCompiler();
	}

	public static DocumentationTool getSystemDocumentationTool() {
		return ToolProvider.getSystemDocumentationTool();
	}

	public static ClassLoader getJDKToolsClassLoader() throws IOException {
		//jdk.compiler and jdk.javadoc modules should be available on the system path
		return PLATFORM_CLASSLOADER;
	}

	public static ClassLoader getJDKToolsClassLoaderIfLoaded() {
		return PLATFORM_CLASSLOADER;
	}

	public static ClassLoaderResolver getJDKToolsClassLoaderResolver() {
		return JDKToolsClassLoaderResolver.INSTANCE;
	}

	private static enum JDKToolsClassLoaderResolver implements ClassLoaderResolver {
		INSTANCE;

		private static final String PLATFORM_CLASSLOADER_IDENTIFIER = "jdk9.classloader.platform";
		private static final String JDK_COMPILER_CLASSLOADER_IDENTIFIER = "jdk9.classloader.jdk.compiler";

		@Override
		public String getClassLoaderIdentifier(ClassLoader classloader) {
			if (classloader == PLATFORM_CLASSLOADER) {
				return PLATFORM_CLASSLOADER_IDENTIFIER;
			}
			if (classloader == JDK_COMPILER_CLASSLOADER) {
				return JDK_COMPILER_CLASSLOADER_IDENTIFIER;
			}
			return null;
		}

		@Override
		public ClassLoader getClassLoaderForIdentifier(String identifier) {
			if (PLATFORM_CLASSLOADER_IDENTIFIER.equals(identifier)) {
				return PLATFORM_CLASSLOADER;
			}
			if (JDK_COMPILER_CLASSLOADER_IDENTIFIER.equals(identifier)) {
				return JDK_COMPILER_CLASSLOADER;
			}
			return null;
		}
	}
}
