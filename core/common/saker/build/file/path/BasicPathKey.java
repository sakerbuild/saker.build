package saker.build.file.path;

import saker.apiextract.api.PublicApi;
import saker.build.file.provider.SakerPathFiles;

/**
 * Abstract {@link PathKey} implementation which implements the {@link #equals(Object)} and {@link #hashCode()}
 * specification.
 * <p>
 * Subclasses should implement the remaining methods accordingly.
 */
@PublicApi
public abstract class BasicPathKey implements PathKey {
	/**
	 * Creates a new instance.
	 */
	public BasicPathKey() {
	}

	@Override
	public final int hashCode() {
		return getPath().hashCode();
	}

	@Override
	public final boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof PathKey)) {
			return false;
		}
		PathKey other = (PathKey) obj;
		if (!SakerPathFiles.isSameProvider(getFileProviderKey(), other.getFileProviderKey())) {
			return false;
		}
		if (!this.getPath().equals(other.getPath())) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + getFileProviderKey() + " : " + getPath() + "]";
	}
}
