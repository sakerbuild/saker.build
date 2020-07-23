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
package saker.build.launching;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import saker.apiextract.api.PublicApi;
import saker.build.exception.BuildExecutionFailedException;
import saker.build.exception.BuildTargetNotFoundException;
import saker.build.runtime.execution.SakerLog;
import saker.build.runtime.execution.SakerLog.CommonExceptionFormat;
import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.thirdparty.saker.util.classloader.MultiDataClassLoader;
import saker.build.thirdparty.saker.util.classloader.SubDirectoryClassLoaderDataFinder;
import saker.build.thirdparty.saker.util.thread.ParallelExecutionFailedException;
import saker.build.util.exc.ExceptionView;

/**
 * Main entry point to the build system for command line usage.
 * <p>
 * This class contains the {@link #main(String...)} entry point for the build system. It is to be called by the Java
 * launcher (via command line) and should not be invoked programmatically. <br>
 * Use {@link #invokeMain(String...)} for a programmatic main function for the build system.
 * <p>
 * The build system doesn't ever call {@link System#exit(int)} by itself, so it is not expected to terminate the JVM.
 * Only exception for this is the {@link #main(String...)} function of this class.
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
	 * If the operation fails, the method will handle it in a way that displays failure information in a way that is
	 * suitable for command line usage. This usually means that the error stacktraces most likely won't be displayed,
	 * but rather a description of the error.
	 * <p>
	 * The method may call {@link System#exit(int)} with a non-zero exit code instead of throwing an exception.
	 * 
	 * @param args
	 *            The command line arguments.
	 * @throws Throwable
	 *             If the operation specified by the arguments failed.
	 */
	public static void main(String... args) throws Throwable {
		try {
			mainImpl(args);
		} catch (BuildExecutionFailedException e) {
			//failure info is printed by the build command
			System.exit(1);
		} catch (BuildTargetNotFoundException e) {
			SakerLog.printFormatException(ExceptionView.create(e), CommonExceptionFormat.NO_TRACE);
			System.exit(1);
		} catch (IllegalArgumentException e) {
			if (isArgumentException(e)) {
				SakerLog.printFormatException(ExceptionView.create(e), CommonExceptionFormat.NO_TRACE);
				System.exit(1);
			} else {
				throw e;
			}
		} catch (ParallelExecutionFailedException e) {
			for (Throwable t : e.getSuppressed()) {
				if (isArgumentException(t)) {
					SakerLog.printFormatException(ExceptionView.create(t), CommonExceptionFormat.NO_TRACE);
				} else {
					SakerLog.printFormatException(ExceptionView.create(t), CommonExceptionFormat.FULL);
				}
			}
			System.exit(1);
		}
	}

	private static boolean isArgumentException(Throwable e) {
		return ReflectUtils.findClassWithNameInHierarchy(e.getClass(),
				"sipka.cmdline.runtime.ArgumentException") != null;
	}

	/**
	 * Main method of the build system for programmatic access.
	 * <p>
	 * Clients that use the build system via a programmatic API, should call this method instead of
	 * {@link #main(String...)}.
	 * <p>
	 * The difference is that this method either finish successfully, or throw an exception if failed, instead of
	 * handling it in a way that is suitable for command line usage.
	 * 
	 * @param args
	 *            The arguments.
	 * @throws Throwable
	 *             An exception if the operation failed.
	 * @since saker.build 0.8.16
	 */
	public static void invokeMain(String... args) throws Throwable {
		mainImpl(args);
	}

	private static void mainImpl(String... args)
			throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, Throwable {
		Class<?> launcherclass = Class.forName("saker.build.launching.MainCommand", false,
				INTERNAL_LAUNCHING_CLASSLOADER);
		Method mainmethod = launcherclass.getMethod("main", String[].class);
		try {
			mainmethod.invoke(null, (Object) args);
		} catch (InvocationTargetException e) {
			throw e.getCause();
		}
	}

}
