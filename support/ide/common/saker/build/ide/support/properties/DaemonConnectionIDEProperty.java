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

import java.util.Set;

import saker.build.thirdparty.saker.util.ImmutableUtils;

public class DaemonConnectionIDEProperty {
	//XXX this is common with BuildCommand. don't duplicate code
	public static final Set<String> RESERVED_CONNECTION_NAMES = ImmutableUtils.makeImmutableNavigableSet(
			new String[] { "local", "remote", "pwd", "build", "http", "https", "ftp", "sftp", "file", "data", "jar",
					"zip", "url", "uri", "tcp", "udp", "mem", "memory", "ide", "project", "null", "storage" });

	private String netAddress;
	private String connectionName;
	private boolean useAsCluster;

	public DaemonConnectionIDEProperty(String netAddress, String connectionName, boolean useAsCluster) {
		this.netAddress = netAddress;
		this.connectionName = connectionName;
		this.useAsCluster = useAsCluster;
	}

	public static boolean isReservedConnectionName(String connectionName) {
		return RESERVED_CONNECTION_NAMES.contains(connectionName);
	}

	public static boolean isValidConnectionNameFormat(String connectionname) {
		if (connectionname.isEmpty()) {
			return false;
		}
		int len = connectionname.length();
		for (int i = 0; i < len; i++) {
			char c = connectionname.charAt(i);
			if (c >= 'a' && c <= 'z') {
				continue;
			}
			if (c >= 'A' && c <= 'Z') {
				continue;
			}
			if (c >= '0' && c <= '9') {
				continue;
			}
			if (c == '_') {
				continue;
			}
			return false;
		}
		return true;
	}

	public String getNetAddress() {
		return netAddress;
	}

	public String getConnectionName() {
		return connectionName;
	}

	public boolean isUseAsCluster() {
		return useAsCluster;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((connectionName == null) ? 0 : connectionName.hashCode());
		result = prime * result + ((netAddress == null) ? 0 : netAddress.hashCode());
		result = prime * result + (useAsCluster ? 1231 : 1237);
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
		DaemonConnectionIDEProperty other = (DaemonConnectionIDEProperty) obj;
		if (connectionName == null) {
			if (other.connectionName != null)
				return false;
		} else if (!connectionName.equals(other.connectionName))
			return false;
		if (netAddress == null) {
			if (other.netAddress != null)
				return false;
		} else if (!netAddress.equals(other.netAddress))
			return false;
		if (useAsCluster != other.useAsCluster)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[netAddress=" + netAddress + ", connectionName=" + connectionName
				+ ", useAsCluster=" + useAsCluster + "]";
	}

}
