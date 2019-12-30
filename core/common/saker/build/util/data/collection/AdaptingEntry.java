package saker.build.util.data.collection;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import saker.build.util.data.DataConverterUtils;

public class AdaptingEntry implements Map.Entry<Object, Object> {
	protected final ClassLoader cl;
	protected final Entry<?, ?> entry;

	public AdaptingEntry(ClassLoader cl, Entry<?, ?> entry) {
		this.cl = cl;
		this.entry = entry;
	}

	@Override
	public Object getKey() {
		return DataConverterUtils.adaptInterface(cl, entry.getKey());
	}

	@Override
	public Object getValue() {
		return DataConverterUtils.adaptInterface(cl, entry.getValue());
	}

	@Override
	public Object setValue(Object value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int hashCode() {
		Object k = getKey();
		Object v = getValue();
		return (k == null ? 0 : k.hashCode()) ^ (v == null ? 0 : v.hashCode());
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Map.Entry)) {
			return false;
		}
		Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
		return Objects.equals(getKey(), e.getKey()) && Objects.equals(getValue(), e.getValue());
	}

	@Override
	public String toString() {
		return getKey() + "=" + getValue();
	}
}
