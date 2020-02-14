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

import saker.build.thirdparty.saker.util.ObjectUtils;

public class MountPathIDEProperty {
	private String mountClientName;
	private String mountPath;

	public MountPathIDEProperty(String mountClientName, String mountPath) {
		this.mountClientName = mountClientName;
		this.mountPath = mountPath;
	}

	public static MountPathIDEProperty create(String mountClientName, String mountPath) {
		if (ObjectUtils.isNullOrEmpty(mountClientName) && ObjectUtils.isNullOrEmpty(mountPath)) {
			return null;
		}
		return new MountPathIDEProperty(mountClientName, mountPath);
	}

	public String getMountClientName() {
		return mountClientName;
	}

	public String getMountPath() {
		return mountPath;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((mountClientName == null) ? 0 : mountClientName.hashCode());
		result = prime * result + ((mountPath == null) ? 0 : mountPath.hashCode());
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
		MountPathIDEProperty other = (MountPathIDEProperty) obj;
		if (mountClientName == null) {
			if (other.mountClientName != null)
				return false;
		} else if (!mountClientName.equals(other.mountClientName))
			return false;
		if (mountPath == null) {
			if (other.mountPath != null)
				return false;
		} else if (!mountPath.equals(other.mountPath))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[mountClientName=" + mountClientName + ", mountPath=" + mountPath + "]";
	}

}
