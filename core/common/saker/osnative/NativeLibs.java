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
package saker.osnative;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import saker.build.meta.PropertyNames;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.trace.InternalBuildTraceImpl;

public class NativeLibs {
	private static final Lock INIT_LOCK = new ReentrantLock();
	private static volatile Path libraryPath = null;

	public static void init(Path librarypath) throws IOException {
		if (NativeLibs.libraryPath != null) {
			return;
		}
		INIT_LOCK.lock();
		try {
			if (NativeLibs.libraryPath != null) {
				return;
			}
			Path lp = librarypath.toAbsolutePath().normalize();
			Files.createDirectories(lp);
			NativeLibs.libraryPath = lp;
		} finally {
			INIT_LOCK.unlock();
		}
	}

	public static Path extractLibrary(String libname) throws UnsatisfiedLinkError, IOException {
		String libresname = libname + "." + System.getProperty("os.arch");
		String fulllibname = System.mapLibraryName(libresname);

		ClassLoader cl = NativeLibs.class.getClassLoader();
		String resname = "nativelib/" + fulllibname;
		try (InputStream is = cl.getResourceAsStream(resname)) {
			if (is == null) {
				//the library is not present for the given platform
				return null;
			}
			Path outpath = getLibraryPath().resolve(fulllibname);
			FileUtils.writeStreamEqualityCheckTo(is, outpath);
			return outpath;
		}
	}

	private static Path getLibraryPath() throws UnsatisfiedLinkError {
		Path libpath = NativeLibs.libraryPath;
		if (libpath != null) {
			return libpath;
		}
		INIT_LOCK.lock();
		try {
			libpath = NativeLibs.libraryPath;
			if (libpath != null) {
				return libpath;
			}
			String property = PropertyNames.getProperty(PropertyNames.PROPERTY_SAKER_OSNATIVE_LIBRARYPATH);
			if (property != null) {
				libpath = Paths.get(property).toAbsolutePath().normalize();
			} else {
				String tmpdir = System.getProperty("java.io.tmpdir");
				if (!ObjectUtils.isNullOrEmpty(tmpdir)) {
					libpath = Paths.get(tmpdir).resolve(".sakerlibs").toAbsolutePath().normalize();
				}
			}
			if (libpath != null) {
				try {
					Files.createDirectories(libpath);
					NativeLibs.libraryPath = libpath;
					return libpath;
				} catch (IOException e) {
					UnsatisfiedLinkError texc = new UnsatisfiedLinkError(
							"Failed to create library path at: " + libpath);
					texc.initCause(e);
					throw texc;
				}
			}
			throw new UnsatisfiedLinkError("Library path couldn't be determined. No java.io.tmpdir or "
					+ PropertyNames.PROPERTY_SAKER_OSNATIVE_LIBRARYPATH + " specified.");
		} catch (Throwable e) {
			try {
				InternalBuildTraceImpl.ignoredStaticException(e);
			} catch (Throwable e2) {
				//shouldn't really happen but just in case
				e.addSuppressed(e2);
			}
			throw e;
		} finally {
			INIT_LOCK.unlock();
		}
	}
}
