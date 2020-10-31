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
package saker.build.daemon;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.Map;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class DaemonLaunchParameters implements Externalizable, Cloneable {
	private static final long serialVersionUID = 1L;

	public static final int DEFAULT_PORT = 3500;

	private SakerPath storageDirectory;
	private Integer port;
	private boolean actsAsServer;
	private int threadFactor;

	private boolean actsAsCluster;
	private SakerPath clusterMirrorDirectory;

	private Map<String, String> userParameters = Collections.emptyNavigableMap();

	/**
	 * For {@link Externalizable}.
	 */
	public DaemonLaunchParameters() {
	}

	@Override
	protected DaemonLaunchParameters clone() {
		try {
			return (DaemonLaunchParameters) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError(e);
		}
	}

	public SakerPath getStorageDirectory() {
		return storageDirectory;
	}

	//doc: nullable:
	//null: should run only in the current JVM, doesn't accept any connections whatsoever
	//0: automatic port selection
	// > 0: use that port
	public Integer getPort() {
		return port;
	}

	/**
	 * Gets if the daemon should accept connection from external addresses.
	 * <p>
	 * If a daemon acts as a server it can accept connections from other addresses than the loopback/localhost. If it
	 * doesn't act as a server then it can be only used on a single machine.
	 * 
	 * @return <code>true</code> if the daemon shouln't be localhost only.
	 */
	public boolean isActsAsServer() {
		return actsAsServer;
	}

	public int getThreadFactor() {
		return threadFactor;
	}

	public boolean isActsAsCluster() {
		return actsAsCluster;
	}

	public SakerPath getClusterMirrorDirectory() {
		return clusterMirrorDirectory;
	}

	public Map<String, String> getUserParameters() {
		return userParameters;
	}

	//XXX these externalization formats should be versioned, as they can be communicated between different implementations

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(storageDirectory);
		out.writeObject(port);
		out.writeBoolean(actsAsServer);
		out.writeInt(threadFactor);
		out.writeBoolean(actsAsCluster);
		out.writeObject(clusterMirrorDirectory);
		SerialUtils.writeExternalMap(out, userParameters);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		storageDirectory = (SakerPath) in.readObject();
		port = (Integer) in.readObject();
		actsAsServer = in.readBoolean();
		threadFactor = in.readInt();
		actsAsCluster = in.readBoolean();
		clusterMirrorDirectory = (SakerPath) in.readObject();
		userParameters = SerialUtils.readExternalImmutableNavigableMap(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (actsAsCluster ? 1231 : 1237);
		result = prime * result + (actsAsServer ? 1231 : 1237);
		result = prime * result + ((clusterMirrorDirectory == null) ? 0 : clusterMirrorDirectory.hashCode());
		result = prime * result + ((port == null) ? 0 : port.hashCode());
		result = prime * result + ((storageDirectory == null) ? 0 : storageDirectory.hashCode());
		result = prime * result + threadFactor;
		result = prime * result + ((userParameters == null) ? 0 : userParameters.hashCode());
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
		DaemonLaunchParameters other = (DaemonLaunchParameters) obj;
		if (actsAsCluster != other.actsAsCluster)
			return false;
		if (actsAsServer != other.actsAsServer)
			return false;
		if (clusterMirrorDirectory == null) {
			if (other.clusterMirrorDirectory != null)
				return false;
		} else if (!clusterMirrorDirectory.equals(other.clusterMirrorDirectory))
			return false;
		if (port == null) {
			if (other.port != null)
				return false;
		} else if (!port.equals(other.port))
			return false;
		if (storageDirectory == null) {
			if (other.storageDirectory != null)
				return false;
		} else if (!storageDirectory.equals(other.storageDirectory))
			return false;
		if (threadFactor != other.threadFactor)
			return false;
		if (userParameters == null) {
			if (other.userParameters != null)
				return false;
		} else if (!userParameters.equals(other.userParameters))
			return false;
		return true;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("DaemonLaunchParameters[");
		sb.append("actsAsServer=");
		sb.append(actsAsServer);
		sb.append(", threadFactor=");
		sb.append(threadFactor);
		sb.append(", actsAsCluster=");
		sb.append(actsAsCluster);
		if (storageDirectory != null) {
			sb.append(", ");
			sb.append("storageDirectory=");
			sb.append(storageDirectory);
		}
		if (port != null) {
			sb.append(", ");
			sb.append("port=");
			sb.append(port);
		}
		if (clusterMirrorDirectory != null) {
			sb.append(", ");
			sb.append("clusterMirrorDirectory=");
			sb.append(clusterMirrorDirectory);
		}
		if (userParameters != null) {
			sb.append(", ");
			sb.append("userParameters=");
			sb.append(userParameters);
		}
		sb.append("]");
		return sb.toString();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static Builder builder(DaemonLaunchParameters copy) {
		return new Builder(copy);
	}

	protected static void checkPort(int pint) {
		if (pint < 0 || pint > 0xFFFF) {
			throw new IllegalArgumentException(
					"Invalid port number: " + pint + " Must be positive in range of " + 0 + " and " + 0xFFFF);
		}
	}

	public static class Builder {
		private DaemonLaunchParameters result;

		public Builder() {
			result = new DaemonLaunchParameters();
		}

		public Builder(DaemonLaunchParameters copy) {
			result = copy.clone();
		}

		public Builder setStorageDirectory(SakerPath storageDirectory) {
			if (storageDirectory != null) {
				SakerPathFiles.requireAbsolutePath(storageDirectory);
				result.storageDirectory = storageDirectory;
			} else {
				result.storageDirectory = null;
			}
			return this;
		}

		public Builder setPort(Integer port) throws IllegalArgumentException {
			if (port != null) {
				checkPort(port);
			}
			result.port = port;
			return this;
		}

		public Builder setActsAsServer(boolean actsAsServer) {
			result.actsAsServer = actsAsServer;
			return this;
		}

		public Builder setThreadFactor(int threadFactor) {
			//normalize < 0 values to 0
			result.threadFactor = Math.max(threadFactor, 0);
			return this;
		}

		public Builder setActsAsCluster(boolean worksAsCluster) {
			result.actsAsCluster = worksAsCluster;
			return this;
		}

		public Builder setClusterMirrorDirectory(SakerPath clusterMirrorDirectory) {
			if (clusterMirrorDirectory != null) {
				SakerPathFiles.requireAbsolutePath(clusterMirrorDirectory);
				result.clusterMirrorDirectory = clusterMirrorDirectory;
			} else {
				result.clusterMirrorDirectory = null;
			}
			return this;
		}

		public Builder setUserParameters(Map<String, String> userParameters) {
			if (ObjectUtils.isNullOrEmpty(userParameters)) {
				result.userParameters = Collections.emptyNavigableMap();
			} else {
				result.userParameters = ImmutableUtils.makeImmutableNavigableMap(userParameters);
			}
			return this;
		}

		public DaemonLaunchParameters build() {
			DaemonLaunchParameters res = result;
			this.result = null;
			return res;
		}
	}

}
