package saker.build.ide.support.properties;

public class HttpUrlJarClassPathLocationIDEProperty implements ClassPathLocationIDEProperty {
	private String url;

	public HttpUrlJarClassPathLocationIDEProperty(String url) {
		this.url = url;
	}

	@Override
	public <R, P> R accept(Visitor<R, P> visitor, P param) {
		return visitor.visit(this, param);
	}

	public String getUrl() {
		return url;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((url == null) ? 0 : url.hashCode());
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
		HttpUrlJarClassPathLocationIDEProperty other = (HttpUrlJarClassPathLocationIDEProperty) obj;
		if (url == null) {
			if (other.url != null)
				return false;
		} else if (!url.equals(other.url))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + url + "]";
	}

}
