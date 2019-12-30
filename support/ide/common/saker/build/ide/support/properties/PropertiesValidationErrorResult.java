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
		return getClass().getSimpleName() + "[errorType=" + errorType + ", relatedSubject=" + relatedSubject + "]";
	}

}
