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
package saker.build.file.path;

import saker.apiextract.api.PublicApi;
import saker.build.file.provider.SakerPathFiles;

/**
 * Abstract {@link PathKey} implementation which implements the {@link #equals(Object)} and {@link #hashCode()}
 * specification.
 * <p>
 * Subclasses should implement the remaining methods accordingly.
 */
@PublicApi
public abstract class BasicPathKey implements PathKey {
	/**
	 * Creates a new instance.
	 */
	public BasicPathKey() {
	}

	@Override
	public final int hashCode() {
		return getPath().hashCode();
	}

	@Override
	public final boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof PathKey)) {
			return false;
		}
		PathKey other = (PathKey) obj;
		if (!SakerPathFiles.isSameProvider(getFileProviderKey(), other.getFileProviderKey())) {
			return false;
		}
		if (!this.getPath().equals(other.getPath())) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + getFileProviderKey() + " : " + getPath() + "]";
	}
}
