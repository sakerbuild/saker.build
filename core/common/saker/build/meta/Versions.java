package saker.build.meta;

import saker.apiextract.api.DefaultableBoolean;
import saker.apiextract.api.PublicApi;

/**
 * Utility class holding version information about the current version of the build system.
 * <p>
 * Some constants declared in this class are final, but they are not final in the API release JAR. Meaning, that users
 * can use them in if conditions and compare them against constant values without worrying about dead code elimination.
 * Make sure to use the API release JAR in your classpath when compiling classes against the build system runtime.
 */
public class Versions {
	//XXX should export the generation of these constants to the build files, or query them from the build script
	/**
	 * The major version of the Saker.build system release.
	 * <p>
	 * The major version will change when there are backward incompatible changes between releases.
	 * 
	 * @see <a href="https://semver.org/">https://semver.org/</a>
	 */
	@PublicApi(unconstantize = DefaultableBoolean.TRUE)
	public static final int VERSION_MAJOR = 0;
	/**
	 * The minor version of the Saker.build system release.
	 * <p>
	 * The minor version changes when changes are made to the build system implementation, but they are backward
	 * compatible.
	 * <p>
	 * The minor version is also changed when features are deprecated but not yet removed from the implementation.
	 * 
	 * @see <a href="https://semver.org/">https://semver.org/</a>
	 */
	@PublicApi(unconstantize = DefaultableBoolean.TRUE)
	public static final int VERSION_MINOR = 8;
	/**
	 * The patch version of the Saker.build system release.
	 * <p>
	 * The patch verison changes when bugfixes are added to the implementation.
	 * 
	 * @see <a href="https://semver.org/">https://semver.org/</a>
	 */
	@PublicApi(unconstantize = DefaultableBoolean.TRUE)
	public static final int VERSION_PATCH = 0;

	/**
	 * The full version string in the format of
	 * <code>&lt;{@link #VERSION_MAJOR major}&gt;.&lt;{@link #VERSION_MINOR minor}&gt;.&lt;{@link #VERSION_PATCH patch}&gt;</code>
	 */
	@PublicApi(unconstantize = DefaultableBoolean.TRUE)
	public static final String VERSION_STRING_FULL = VERSION_MAJOR + "." + VERSION_MINOR + "." + VERSION_PATCH;

	private Versions() {
		throw new UnsupportedOperationException();
	}
}
