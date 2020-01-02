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
package saker.build.runtime.params;

/**
 * Exception representing the scenario when a file system path is accessible through multiple root paths.
 * <p>
 * This can happen when such a configuration is defined that a path is accessible from multiple roots. <br>
 * Example:
 * <ul>
 * <li><code>c:/users</code> is mounted to <code>u:</code></li>
 * <li><code>c:/users/john</code> is mounted to <code>j:</code></li>
 * </ul>
 * In the above example <code>c:/users/john</code> is accessible directly through the root <code>j:</code> and the via
 * path <code>u:/john</code> using the root <code>j:</code>.
 * <p>
 * This configuration is invalid, as it would allow for multiple file representation in the build system that are
 * conflicting.
 */
public class AmbiguousPathConfigurationException extends InvalidBuildConfigurationException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see InvalidBuildConfigurationException#InvalidBuildConfigurationException()
	 */
	public AmbiguousPathConfigurationException() {
		super();
	}

	/**
	 * @see InvalidBuildConfigurationException#InvalidBuildConfigurationException(String, Throwable, boolean, boolean)
	 */
	protected AmbiguousPathConfigurationException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see InvalidBuildConfigurationException#InvalidBuildConfigurationException(String, Throwable)
	 */
	public AmbiguousPathConfigurationException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see InvalidBuildConfigurationException#InvalidBuildConfigurationException(String)
	 */
	public AmbiguousPathConfigurationException(String message) {
		super(message);
	}

	/**
	 * @see InvalidBuildConfigurationException#InvalidBuildConfigurationException(Throwable)
	 */
	public AmbiguousPathConfigurationException(Throwable cause) {
		super(cause);
	}

}
