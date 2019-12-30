package saker.build.launching;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import saker.build.file.path.SakerPath;
import saker.build.meta.ManifestNames;
import saker.build.meta.Versions;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.thirdparty.saker.util.io.NetworkUtils;

public class LaunchingUtils {
	private static Manifest findManifestInClassPathEntry(String cpentry) {
		try (InputStream is = Files.newInputStream(Paths.get(cpentry));
				JarInputStream jis = new JarInputStream(is)) {
			Manifest manifest = jis.getManifest();
			if (manifest != null) {
				return manifest;
			}

			for (JarEntry je; (je = jis.getNextJarEntry()) != null;) {
				if (JarFile.MANIFEST_NAME.equals(je.getName())) {
					return new Manifest(jis);
				}
			}
		} catch (IOException e) {
		}
		return null;
	}

	private static SakerPath searchForSakerJarInClassPath(String cp) {
		if (ObjectUtils.isNullOrEmpty(cp)) {
			return null;
		}
		Iterator<? extends CharSequence> it = StringUtils.splitCharSequenceIterator(cp, File.pathSeparatorChar);
		while (it.hasNext()) {
			String cpentry = it.next().toString();
			if (!FileUtils.hasExtensionIgnoreCase(cpentry, "jar")) {
				continue;
			}
			Manifest manifest = findManifestInClassPathEntry(cpentry);
			if (manifest != null) {
				Attributes mainattrs = manifest.getMainAttributes();
				String implver = mainattrs.getValue(ManifestNames.VERSION);
				if (implver != null) {
					if (Versions.VERSION_STRING_FULL.equals(implver)) {
						return SakerPath.valueOf(cpentry);
					}
				}
			}
		}
		return null;
	}

	public static SakerPath searchForSakerJarInClassPath() {
		SakerPath sakerJar;
		sakerJar = searchForSakerJarInClassPath(System.getProperty("java.class.path"));
		if (sakerJar != null) {
			return sakerJar;
		}
		sakerJar = searchForSakerJarInClassPath(System.getProperty("jdk.module.path"));
		return sakerJar;
	}

	public static SakerPath absolutize(SakerPath path) {
		if (path == null) {
			return null;
		}
		if (path.isAbsolute()) {
			return path;
		}
		return SakerPath.valueOf(System.getProperty("user.dir")).resolve(path);
	}

	public static int parsePort(String port) {
		return NetworkUtils.parsePort(port);
	}

	public static List<String> parseRemainingCommand(Iterator<String> it) {
		List<String> result = new ArrayList<>();
		ObjectUtils.addAll(result, it);
		return result;
	}

	public static InetSocketAddress parseInetSocketAddress(String str, int defaultport) throws UnknownHostException {
		return NetworkUtils.parseInetSocketAddress(str, defaultport);
	}
}
