package saker.build.launching;

import saker.build.meta.Versions;
import saker.build.runtime.params.NestRepositoryClassPathLocation;

/**
 * <pre>
 * Displays the version information of the saker.build system.
 * </pre>
 */
public class VersionCommand {
	public void call() throws Exception {
		String ls = System.lineSeparator();
		System.out.print("Saker.build system" + ls + "Version: " + Versions.VERSION_STRING_FULL + ls
				+ "Saker.nest repository version: " + NestRepositoryClassPathLocation.DEFAULT_VERSION + ls);
	}
}
