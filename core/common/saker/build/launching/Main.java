package saker.build.launching;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import saker.apiextract.api.PublicApi;
import saker.build.thirdparty.saker.util.classloader.MultiDataClassLoader;
import saker.build.thirdparty.saker.util.classloader.SubDirectoryClassLoaderDataFinder;

/**
 * Main entry point to the build system for command line usage.
 * <p>
 * This class contains the {@link #main(String...)} function for the build system. This can be called directly from the
 * command line, or in other ways if needed to be called programmatically.
 * <p>
 * The build system doesn't ever call {@link System#exit(int)} by itself, so it is not expected to terminate the JVM.
 * (However, malicious third party plugins may interfere with this, but this should not be considered under normal
 * use-case.)
 * <p>
 * The main method will either finish successfully, or throw an exception if failed.
 */
@PublicApi
public final class Main {
	private static final ClassLoader INTERNAL_LAUNCHING_CLASSLOADER;
	static {
		SubDirectoryClassLoaderDataFinder subdirfinder = SubDirectoryClassLoaderDataFinder.create("internal/launching",
				Main.class.getClassLoader());
		INTERNAL_LAUNCHING_CLASSLOADER = new MultiDataClassLoader(subdirfinder);
	}

	private Main() {
		throw new UnsupportedOperationException();
	}

	/**
	 * The main method of the build system launcher.
	 * <p>
	 * The arguments will be parsed based on the command line interface of the build system, and the appropriate actions
	 * will be taken.
	 * <p>
	 * If the operation fails, this method will throw an exception. If it succeeds, it will return normally.
	 * 
	 * @param args
	 *            The command line arguments.
	 * @throws Throwable
	 *             If the operation specified by the arguments failed.
	 */
	public static void main(String... args) throws Throwable {
		Class<?> launcherclass = Class.forName("saker.build.launching.Launcher", false, INTERNAL_LAUNCHING_CLASSLOADER);
		Method mainmethod = launcherclass.getMethod("main", String[].class);
		try {
			mainmethod.invoke(null, (Object) args);
		} catch (InvocationTargetException e) {
			throw e.getCause();
		}
	}
}
