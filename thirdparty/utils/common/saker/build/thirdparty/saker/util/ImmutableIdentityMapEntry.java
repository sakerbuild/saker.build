package saker.build.thirdparty.saker.util;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;

final class ImmutableIdentityMapEntry<K, V> implements Map.Entry<K, V>, Externalizable {
	private static final long serialVersionUID = 1L;

	private K key;
	private V value;

	/**
	 * For {@link Externalizable}.
	 */
	public ImmutableIdentityMapEntry() {
	}

	public ImmutableIdentityMapEntry(K key, V value) {
		this.key = key;
		this.value = value;
	}

	@Override
	public K getKey() {
		return key;
	}

	@Override
	public V getValue() {
		return value;
	}

	@Override
	public V setValue(V value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(key);
		out.writeObject(value);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		key = (K) in.readObject();
		value = (V) in.readObject();
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof Map.Entry))
			return false;
		Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
		return key == e.getKey() && value == e.getValue();
	}

	@Override
	public int hashCode() {
		return System.identityHashCode(key) ^ System.identityHashCode(value);
	}

	@Override
	public String toString() {
		return key + "=" + value;
	}
}
