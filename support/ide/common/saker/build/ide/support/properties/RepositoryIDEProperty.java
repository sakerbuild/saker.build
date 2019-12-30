package saker.build.ide.support.properties;

public class RepositoryIDEProperty {
	private ClassPathLocationIDEProperty classPathLocation;
	private ClassPathServiceEnumeratorIDEProperty serviceEnumerator;
	private String identifier;

	public RepositoryIDEProperty(ClassPathLocationIDEProperty classPathLocation, String repositoryId,
			ClassPathServiceEnumeratorIDEProperty serviceEnumerator) {
		this.classPathLocation = classPathLocation;
		this.identifier = repositoryId;
		this.serviceEnumerator = serviceEnumerator;
	}

	public ClassPathLocationIDEProperty getClassPathLocation() {
		return classPathLocation;
	}

	public String getRepositoryIdentifier() {
		return identifier;
	}

	public ClassPathServiceEnumeratorIDEProperty getServiceEnumerator() {
		return serviceEnumerator;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((classPathLocation == null) ? 0 : classPathLocation.hashCode());
		result = prime * result + ((identifier == null) ? 0 : identifier.hashCode());
		result = prime * result + ((serviceEnumerator == null) ? 0 : serviceEnumerator.hashCode());
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
		RepositoryIDEProperty other = (RepositoryIDEProperty) obj;
		if (classPathLocation == null) {
			if (other.classPathLocation != null)
				return false;
		} else if (!classPathLocation.equals(other.classPathLocation))
			return false;
		if (identifier == null) {
			if (other.identifier != null)
				return false;
		} else if (!identifier.equals(other.identifier))
			return false;
		if (serviceEnumerator == null) {
			if (other.serviceEnumerator != null)
				return false;
		} else if (!serviceEnumerator.equals(other.serviceEnumerator))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "["
				+ (classPathLocation != null ? "classPathLocation=" + classPathLocation + ", " : "")
				+ (serviceEnumerator != null ? "serviceEnumerator=" + serviceEnumerator + ", " : "")
				+ (identifier != null ? "identifier=" + identifier : "") + "]";
	}

}
