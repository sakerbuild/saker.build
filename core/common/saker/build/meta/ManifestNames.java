package saker.build.meta;

/**
 * Class holding meta data about the attributes which are present in the manifest file of the build system JAR release.
 */
public class ManifestNames {
	/**
	 * Name for the version of the build system.
	 * <p>
	 * The value in the manifest is the same as the value in the {@link Versions#VERSION_STRING_FULL} field.
	 */
	public static final String VERSION = "Saker-Build-Version";

	private ManifestNames() {
		throw new UnsupportedOperationException();
	}
}
