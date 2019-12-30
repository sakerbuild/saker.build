package saker.build.ide.support.properties;

public class JarClassPathLocationIDEProperty implements ClassPathLocationIDEProperty {
	private String connectionName;
	private String jarPath;

	public JarClassPathLocationIDEProperty(String connectionName, String jarPath) {
		this.connectionName = connectionName;
		this.jarPath = jarPath;
	}

	@Override
	public <R, P> R accept(Visitor<R, P> visitor, P param) {
		return visitor.visit(this, param);
	}

	public String getJarPath() {
		return jarPath;
	}

	public String getConnectionName() {
		return connectionName;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((connectionName == null) ? 0 : connectionName.hashCode());
		result = prime * result + ((jarPath == null) ? 0 : jarPath.hashCode());
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
		JarClassPathLocationIDEProperty other = (JarClassPathLocationIDEProperty) obj;
		if (connectionName == null) {
			if (other.connectionName != null)
				return false;
		} else if (!connectionName.equals(other.connectionName))
			return false;
		if (jarPath == null) {
			if (other.jarPath != null)
				return false;
		} else if (!jarPath.equals(other.jarPath))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + jarPath + "]";
	}

}
