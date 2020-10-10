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
package saker.osnative.watcher;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.trace.BuildTrace;
import saker.build.trace.InternalBuildTraceImpl;
import saker.osnative.NativeLibs;
import testing.saker.build.flag.TestFlag;

public class NativeWatcherService {
	private static volatile Boolean loaded = null;
	private static Class<? extends RegisteringWatchService> implementationClass = null;

	public static Class<? extends RegisteringWatchService> getNativeImplementationClass() {
		if (implementationClass != null) {
			return implementationClass;
		}
		if (TestFlag.ENABLED) {
			if (!TestFlag.metric().isNativeWatcherEnabled()) {
				return null;
			}
		}
		if (loaded == null) {
			synchronized (NativeWatcherService.class) {
				if (loaded == null) {
					String cname = NativeWatcherService.class.getName();
					try {
						try {
							Path libpath = NativeLibs.extractLibrary(cname);
							if (libpath != null) {
								System.load(libpath.toString());
								loaded = true;
							} else {
								// native lib is not present for this platform
								loaded = false;
							}
						} catch (IOException e) {
							UnsatisfiedLinkError ule = new UnsatisfiedLinkError(
									"Failed to load native watcher library for: " + cname);
							ule.initCause(e);
							throw ule;
						}
						// UnsatisfiedLinkError caught below
					} catch (Exception | StackOverflowError | OutOfMemoryError | AssertionError | LinkageError e) {
						if (TestFlag.ENABLED) {
							e.printStackTrace();
						}
						InternalBuildTraceImpl.ignoredStaticException(e);
						loaded = false;
					}
				}
			}
		}
		if (!loaded) {
			return null;
		}
		try {
			String implclassname = getImplementationClassName_native();
			implementationClass = Class.forName(implclassname, false, NativeWatcherService.class.getClassLoader())
					.asSubclass(RegisteringWatchService.class);
			return implementationClass;
		} catch (ClassNotFoundException | ClassCastException e) {
			throw new AssertionError(e);
		}
	}

	/**
	 * @return The created native watch service or <code>null</code> if cannot be created.
	 */
	public static RegisteringWatchService newInstance() {
		Class<? extends RegisteringWatchService> nativeclass = getNativeImplementationClass();
		if (nativeclass == null) {
			return null;
		}
		try {
			return ReflectUtils.newInstance(nativeclass);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
		}
		return null;
	}

	private native static String getImplementationClassName_native();
}
