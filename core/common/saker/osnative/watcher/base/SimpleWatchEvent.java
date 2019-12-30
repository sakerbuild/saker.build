package saker.osnative.watcher.base;

import java.nio.file.WatchEvent;

public final class SimpleWatchEvent<T> implements WatchEvent<T> {
	private final WatchEvent.Kind<T> kind;
	private final T context;
	private final transient int count;

	public SimpleWatchEvent(WatchEvent.Kind<T> kind, int count, T context) {
		this.kind = kind;
		this.count = count;
		this.context = context;
	}

	@Override
	public WatchEvent.Kind<T> kind() {
		return kind;
	}

	@Override
	public int count() {
		return count;
	}

	@Override
	public T context() {
		return context;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((context == null) ? 0 : context.hashCode());
		result = prime * result + kind.hashCode();
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
		SimpleWatchEvent<?> other = (SimpleWatchEvent<?>) obj;
		if (context == null) {
			if (other.context != null)
				return false;
		} else if (!context.equals(other.context))
			return false;
		if (this.kind != other.kind) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + kind.name() + (context == null ? "" : ": " + context) + "]";
	}

}
