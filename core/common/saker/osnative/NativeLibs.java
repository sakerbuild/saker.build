package saker.osnative;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

import saker.build.meta.PropertyNames;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.FileUtils;

public class NativeLibs {
	private static volatile Path libraryPath = null;

	public static void init(Path librarypath) throws IOException {
		if (NativeLibs.libraryPath != null) {
			return;
		}
		synchronized (NativeLibs.class) {
			if (NativeLibs.libraryPath != null) {
				return;
			}
			Path lp = librarypath.toAbsolutePath().normalize();
			Files.createDirectories(lp);
			NativeLibs.libraryPath = lp;
		}
	}

	public static Path extractLibrary(String libname) throws UnsatisfiedLinkError, IOException {
		String libresname = libname + "." + System.getProperty("os.arch");
		String fulllibname = System.mapLibraryName(libresname);
		Path libpath = getLibraryPath();
		exportLib(fulllibname, libpath);
		return libpath.resolve(fulllibname);
	}

	private static void exportLib(String libname, Path libpath) throws IOException {
		Path outpath = libpath.resolve(libname);
		String resname = "nativelib/" + libname;
		ClassLoader cl = NativeLibs.class.getClassLoader();

		try (InputStream is = cl.getResourceAsStream(resname)) {
			if (is == null) {
				throw new NoSuchFileException(libname, null,
						"ClassLoader resource not found for libname: " + libname + " with resource name: " + resname);
			}
			FileUtils.writeStreamEqualityCheckTo(is, outpath);
		}
	}

	private static Path getLibraryPath() throws UnsatisfiedLinkError {
		Path libpath = NativeLibs.libraryPath;
		if (libpath != null) {
			return libpath;
		}
		synchronized (NativeLibs.class) {
			libpath = NativeLibs.libraryPath;
			if (libpath != null) {
				return libpath;
			}
			String property = PropertyNames.getProperty(PropertyNames.PROPERTY_SAKER_OSNATIVE_LIBRARYPATH);
			if (property != null) {
				libpath = Paths.get(property).toAbsolutePath().normalize();
				NativeLibs.libraryPath = libpath;
				return libpath;
			}
			String tmpdir = System.getProperty("java.io.tmpdir");
			if (!ObjectUtils.isNullOrEmpty(tmpdir)) {
				libpath = Paths.get(tmpdir).resolve(".sakerlibs").toAbsolutePath().normalize();
				NativeLibs.libraryPath = libpath;
				try {
					Files.createDirectories(libpath);
					System.err.println("No library path defined, using: " + libpath);
					return libpath;
				} catch (IOException e) {
					UnsatisfiedLinkError texc = new UnsatisfiedLinkError(
							"Failed to create library path at: " + libpath);
					texc.initCause(e);
					throw texc;
				}
			}
			throw new UnsatisfiedLinkError("Library path couldn't be determined.");
		}
	}
}
