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
	 * The major version of the saker.build system release.
	 * <p>
	 * The major version will change when there are backward incompatible changes between releases.
	 * 
	 * @see <a href="https://semver.org/">https://semver.org/</a>
	 */
	@PublicApi(unconstantize = DefaultableBoolean.TRUE)
	public static final int VERSION_MAJOR = 0;
	/**
	 * The minor version of the saker.build system release.
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
	 * The patch version of the saker.build system release.
	 * <p>
	 * The patch version changes when bugfixes are added to the implementation.
	 * 
	 * @see <a href="https://semver.org/">https://semver.org/</a>
	 */
	@PublicApi(unconstantize = DefaultableBoolean.TRUE)
	public static final int VERSION_PATCH = 4;

	/**
	 * The full version string in the format of
	 * <code>&lt;{@link #VERSION_MAJOR major}&gt;.&lt;{@link #VERSION_MINOR minor}&gt;.&lt;{@link #VERSION_PATCH patch}&gt;</code>
	 */
	@PublicApi(unconstantize = DefaultableBoolean.TRUE)
	public static final String VERSION_STRING_FULL = VERSION_MAJOR + "." + VERSION_MINOR + "." + VERSION_PATCH;

	/**
	 * A compound version representation of the saker.build system release.
	 * <p>
	 * The field has the value of
	 * <code>{@link #VERSION_MAJOR} * 1_000_000 + {@link #VERSION_MINOR} * 1_000 + {@link #VERSION_PATCH}</code>.
	 * <p>
	 * It can be easily used in conditional statements to query the runtime for available features.
	 * 
	 * <pre>
	 * if (Versions.VERSION_FULL_COMPOUND &gt;= 8_001) {
	 * 	// use features from 0.8.1
	 * } else if (Versions.VERSION_FULL_COMPOUND &gt;= 1_000_000) {
	 * 	// use features from 1.0.0
	 * }
	 * </pre>
	 * 
	 * Note that you shouldn't start your version numbers with the number <code>0</code>, as that represents an octal
	 * number.
	 * 
	 * @since saker.build 0.8.1
	 */
	@PublicApi(unconstantize = DefaultableBoolean.TRUE)
	public static final int VERSION_FULL_COMPOUND = VERSION_MAJOR * 1_000_000 + VERSION_MINOR * 1_000 + VERSION_PATCH;

	/**
	 * The major version of the saker.util library that is included under the <code>saker.build.thirdparty</code>
	 * package.
	 * 
	 * @since saker.build 0.8.1
	 */
	@PublicApi(unconstantize = DefaultableBoolean.TRUE)
	public static final int THIRDPARTY_SAKER_UTIL_VERSION_MAJOR = 0;
	/**
	 * The minor version of the saker.util library that is included under the <code>saker.build.thirdparty</code>
	 * package.
	 * 
	 * @since saker.build 0.8.1
	 */
	@PublicApi(unconstantize = DefaultableBoolean.TRUE)
	public static final int THIRDPARTY_SAKER_UTIL_VERSION_MINOR = 8;
	/**
	 * The patch version of the saker.util library that is included under the <code>saker.build.thirdparty</code>
	 * package.
	 * 
	 * @since saker.build 0.8.1
	 */
	@PublicApi(unconstantize = DefaultableBoolean.TRUE)
	public static final int THIRDPARTY_SAKER_UTIL_VERSION_PATCH = 0;
	/**
	 * The full version of the saker.util library that is included under the <code>saker.build.thirdparty</code> package
	 * string in the format of
	 * <code>&lt;{@link #THIRDPARTY_SAKER_UTIL_VERSION_MAJOR major}&gt;.&lt;{@link #THIRDPARTY_SAKER_UTIL_VERSION_MINOR minor}&gt;.&lt;{@link #THIRDPARTY_SAKER_UTIL_VERSION_PATCH patch}&gt;</code>
	 */
	@PublicApi(unconstantize = DefaultableBoolean.TRUE)
	public static final String THIRDPARTY_SAKER_UTIL_VERSION_FULL = THIRDPARTY_SAKER_UTIL_VERSION_MAJOR + "."
			+ THIRDPARTY_SAKER_UTIL_VERSION_MINOR + "." + THIRDPARTY_SAKER_UTIL_VERSION_PATCH;

	/**
	 * A compound version representation of the saker.util library that is included under the
	 * <code>saker.build.thirdparty</code> package.
	 * <p>
	 * The field has the value of
	 * <code>{@link #THIRDPARTY_SAKER_UTIL_VERSION_MAJOR} * 1_000_000 + {@link #THIRDPARTY_SAKER_UTIL_VERSION_MINOR} * 1_000 + {@link #THIRDPARTY_SAKER_UTIL_VERSION_PATCH}</code>.
	 * <p>
	 * It can be easily used in conditional statements to query the runtime for available features.
	 * 
	 * <pre>
	 * if (Versions.THIRDPARTY_SAKER_UTIL_VERSION_FULL_COMPOUND &gt;= 8_001) {
	 * 	// use features from 0.8.1
	 * } else if (Versions.THIRDPARTY_SAKER_UTIL_VERSION_FULL_COMPOUND &gt;= 1_000_000) {
	 * 	// use features from 1.0.0
	 * }
	 * </pre>
	 * 
	 * Note that you shouldn't start your version numbers with the number <code>0</code>, as that represents an octal
	 * number.
	 * 
	 * @since saker.build 0.8.1
	 */
	@PublicApi(unconstantize = DefaultableBoolean.TRUE)
	public static final int THIRDPARTY_SAKER_UTIL_VERSION_FULL_COMPOUND = THIRDPARTY_SAKER_UTIL_VERSION_MAJOR
			* 1_000_000 + THIRDPARTY_SAKER_UTIL_VERSION_MINOR * 1_000 + THIRDPARTY_SAKER_UTIL_VERSION_PATCH;

	/**
	 * The major version of the saker.rmi library that is included under the <code>saker.build.thirdparty</code>
	 * package.
	 * 
	 * @since saker.build 0.8.1
	 */
	@PublicApi(unconstantize = DefaultableBoolean.TRUE)
	public static final int THIRDPARTY_SAKER_RMI_VERSION_MAJOR = 0;
	/**
	 * The minor version of the saker.rmi library that is included under the <code>saker.build.thirdparty</code>
	 * package.
	 * 
	 * @since saker.build 0.8.1
	 */
	@PublicApi(unconstantize = DefaultableBoolean.TRUE)
	public static final int THIRDPARTY_SAKER_RMI_VERSION_MINOR = 8;
	/**
	 * The patch version of the saker.rmi library that is included under the <code>saker.build.thirdparty</code>
	 * package.
	 * 
	 * @since saker.build 0.8.1
	 */
	@PublicApi(unconstantize = DefaultableBoolean.TRUE)
	public static final int THIRDPARTY_SAKER_RMI_VERSION_PATCH = 0;
	/**
	 * The full version of the saker.rmi library that is included under the <code>saker.build.thirdparty</code> package
	 * string in the format of
	 * <code>&lt;{@link #THIRDPARTY_SAKER_RMI_VERSION_MAJOR major}&gt;.&lt;{@link #THIRDPARTY_SAKER_RMI_VERSION_MINOR minor}&gt;.&lt;{@link #THIRDPARTY_SAKER_RMI_VERSION_PATCH patch}&gt;</code>
	 * 
	 * @since saker.build 0.8.1
	 */
	@PublicApi(unconstantize = DefaultableBoolean.TRUE)
	public static final String THIRDPARTY_SAKER_RMI_VERSION_FULL = THIRDPARTY_SAKER_RMI_VERSION_MAJOR + "."
			+ THIRDPARTY_SAKER_RMI_VERSION_MINOR + "." + THIRDPARTY_SAKER_RMI_VERSION_PATCH;
	/**
	 * A compound version representation of the saker.rmi library that is included under the
	 * <code>saker.build.thirdparty</code> package.
	 * <p>
	 * The field has the value of
	 * <code>{@link #THIRDPARTY_SAKER_RMI_VERSION_MAJOR} * 1_000_000 + {@link #THIRDPARTY_SAKER_RMI_VERSION_MINOR} * 1_000 + {@link #THIRDPARTY_SAKER_RMI_VERSION_PATCH}</code>.
	 * <p>
	 * It can be easily used in conditional statements to query the runtime for available features.
	 * 
	 * <pre>
	 * if (Versions.THIRDPARTY_SAKER_RMI_VERSION_FULL_COMPOUND &gt;= 8_001) {
	 * 	// use features from 0.8.1
	 * } else if (Versions.THIRDPARTY_SAKER_RMI_VERSION_FULL_COMPOUND &gt;= 1_000_000) {
	 * 	// use features from 1.0.0
	 * }
	 * </pre>
	 * 
	 * Note that you shouldn't start your version numbers with the number <code>0</code>, as that represents an octal
	 * number.
	 * 
	 * @since saker.build 0.8.1
	 */
	@PublicApi(unconstantize = DefaultableBoolean.TRUE)
	public static final int THIRDPARTY_SAKER_RMI_VERSION_FULL_COMPOUND = THIRDPARTY_SAKER_RMI_VERSION_MAJOR * 1_000_000
			+ THIRDPARTY_SAKER_RMI_VERSION_MINOR * 1_000 + THIRDPARTY_SAKER_RMI_VERSION_PATCH;

	private Versions() {
		throw new UnsupportedOperationException();
	}
}
