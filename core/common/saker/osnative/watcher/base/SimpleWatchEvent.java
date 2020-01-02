/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
