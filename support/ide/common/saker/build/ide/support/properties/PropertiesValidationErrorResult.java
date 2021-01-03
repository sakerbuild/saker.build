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

public final class PropertiesValidationErrorResult {
	public final String errorType;
	public final Object relatedSubject;

	public PropertiesValidationErrorResult(String errorType, Object relatedSubject) {
		this.errorType = errorType;
		this.relatedSubject = relatedSubject;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((errorType == null) ? 0 : errorType.hashCode());
		result = prime * result + ((relatedSubject == null) ? 0 : relatedSubject.hashCode());
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
		PropertiesValidationErrorResult other = (PropertiesValidationErrorResult) obj;
		if (errorType == null) {
			if (other.errorType != null)
				return false;
		} else if (!errorType.equals(other.errorType))
			return false;
		if (relatedSubject == null) {
			if (other.relatedSubject != null)
				return false;
		} else if (!relatedSubject.equals(other.relatedSubject))
			return false;
		return true;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getClass().getSimpleName());
		sb.append("[errorType=");
		sb.append(errorType);
		if (relatedSubject != null) {
			sb.append(", relatedSubject=");
			sb.append(relatedSubject);
		}
		sb.append("]");
		return sb.toString();
	}

}
