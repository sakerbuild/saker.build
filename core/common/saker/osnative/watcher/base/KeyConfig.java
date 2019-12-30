package saker.osnative.watcher.base;

import java.nio.file.Path;

public final class KeyConfig implements Comparable<KeyConfig> {
	private final int queryFlags;
	private final Path path;

	public KeyConfig(int queryFlags, Path path) {
		this.queryFlags = queryFlags;
		this.path = path;
	}

	public Path getPath() {
		return path;
	}

	public int getQueryFlags() {
		return queryFlags;
	}

	public boolean isFileTreeWatching() {
		return (queryFlags & SakerNativeWatchKey.FLAG_QUERY_FILE_TREE) == SakerNativeWatchKey.FLAG_QUERY_FILE_TREE;
	}

	@Override
	public int compareTo(KeyConfig o) {
		int cmp;
		if ((cmp = Integer.compare(queryFlags, o.queryFlags)) != 0) {
			return cmp;
		}
		if ((cmp = path.compareTo(o.path)) != 0) {
			return cmp;
		}
		return 0;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + path.hashCode();
		result = prime * result + queryFlags;
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
		KeyConfig other = (KeyConfig) obj;
		if (!path.equals(other.path)) {
			return false;
		}
		if (queryFlags != other.queryFlags) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + path + "]";
	}
}