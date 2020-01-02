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
