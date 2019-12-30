package saker.build.thirdparty.saker.util.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.JarFile;

class JarFileUtilAccessor {
	public static final Name MULTI_RELEASE_ATTRIBUTE_NAME = new Attributes.Name("Multi-Release");

	private JarFileUtilAccessor() {
		throw new UnsupportedOperationException();
	}

	public static JarFile createMultiReleaseJarFile(Path path) throws IOException {
		return createMultiReleaseJarFile(path.toFile());
	}

	public static JarFile createMultiReleaseJarFile(File file) throws IOException {
		return new JarFile(file);
	}

	public static Attributes.Name getMultiReleaseManifestAttributeName() {
		return MULTI_RELEASE_ATTRIBUTE_NAME;
	}

}
