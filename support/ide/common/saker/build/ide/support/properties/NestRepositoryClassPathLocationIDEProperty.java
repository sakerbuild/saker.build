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
package saker.build.ide.support.properties;

import java.util.regex.Pattern;

public class NestRepositoryClassPathLocationIDEProperty implements ClassPathLocationIDEProperty {
	private static final Pattern PATTERN_NEST_REPOSITORY_VERSION_NUMBER = Pattern
			.compile("(0|([1-9][0-9]*))(\\.(0|([1-9][0-9]*)))*");

	private String version;

	public NestRepositoryClassPathLocationIDEProperty() {
	}

	public NestRepositoryClassPathLocationIDEProperty(String version) {
		this.version = version;
	}

	public String getVersion() {
		return version;
	}

	public static boolean isValidVersionNumber(String version) {
		if (version == null) {
			return false;
		}
		return PATTERN_NEST_REPOSITORY_VERSION_NUMBER.matcher(version).matches();
	}

	@Override
	public <R, P> R accept(Visitor<R, P> visitor, P param) {
		return visitor.visit(this, param);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((version == null) ? 0 : version.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		NestRepositoryClassPathLocationIDEProperty other = (NestRepositoryClassPathLocationIDEProperty) obj;
		if (version == null) {
			if (other.version != null)
				return false;
		} else if (!version.equals(other.version))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + "]";
	}

}
