package saker.build.ide.support.properties;

public class ProviderMountIDEProperty {
	private String mountRoot;
	private String mountClientName;
	private String mountPath;

	public ProviderMountIDEProperty(String mountRoot, String mountClientName, String mountPath) {
		this.mountRoot = mountRoot;
		this.mountClientName = mountClientName;
		this.mountPath = mountPath;
	}

	public String getRoot() {
		return mountRoot;
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
		result = prime * result + ((mountRoot == null) ? 0 : mountRoot.hashCode());
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
		ProviderMountIDEProperty other = (ProviderMountIDEProperty) obj;
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
		if (mountRoot == null) {
			if (other.mountRoot != null)
				return false;
		} else if (!mountRoot.equals(other.mountRoot))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[mountRoot=" + mountRoot + ", mountClientName=" + mountClientName
				+ ", mountPath=" + mountPath + "]";
	}

}
