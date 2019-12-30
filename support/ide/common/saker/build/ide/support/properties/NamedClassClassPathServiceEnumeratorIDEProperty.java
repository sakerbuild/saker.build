package saker.build.ide.support.properties;

public class NamedClassClassPathServiceEnumeratorIDEProperty implements ClassPathServiceEnumeratorIDEProperty {
	private String className;

	public NamedClassClassPathServiceEnumeratorIDEProperty(String className) throws NullPointerException {
		this.className = className;
	}

	public String getClassName() {
		return className;
	}

	@Override
	public <R, P> R accept(Visitor<R, P> visitor, P param) {
		return visitor.visit(this, param);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((className == null) ? 0 : className.hashCode());
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
		NamedClassClassPathServiceEnumeratorIDEProperty other = (NamedClassClassPathServiceEnumeratorIDEProperty) obj;
		if (className == null) {
			if (other.className != null)
				return false;
		} else if (!className.equals(other.className))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + className + "]";
	}
}
