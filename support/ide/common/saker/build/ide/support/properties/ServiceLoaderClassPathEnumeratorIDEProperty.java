package saker.build.ide.support.properties;

public class ServiceLoaderClassPathEnumeratorIDEProperty implements ClassPathServiceEnumeratorIDEProperty {
	private String serviceClass;

	public ServiceLoaderClassPathEnumeratorIDEProperty(String serviceClass) {
		this.serviceClass = serviceClass;
	}

	@Override
	public <R, P> R accept(Visitor<R, P> visitor, P param) {
		return visitor.visit(this, param);
	}

	public String getServiceClass() {
		return serviceClass;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((serviceClass == null) ? 0 : serviceClass.hashCode());
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
		ServiceLoaderClassPathEnumeratorIDEProperty other = (ServiceLoaderClassPathEnumeratorIDEProperty) obj;
		if (serviceClass == null) {
			if (other.serviceClass != null)
				return false;
		} else if (!serviceClass.equals(other.serviceClass))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + serviceClass + "]";
	}

}
