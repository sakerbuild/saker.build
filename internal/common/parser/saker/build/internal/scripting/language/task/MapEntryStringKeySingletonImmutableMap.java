package saker.build.internal.scripting.language.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class MapEntryStringKeySingletonImmutableMap<K, V> extends AbstractMap<String, V> implements Externalizable {
	private static final long serialVersionUID = 1L;

	private K key;
	private V value;

	/**
	 * For {@link Externalizable}.
	 */
	public MapEntryStringKeySingletonImmutableMap() {
	}

	public MapEntryStringKeySingletonImmutableMap(K key, V value) {
		this.key = key;
		this.value = value;
	}

	@Override
	public Set<Entry<String, V>> entrySet() {
		return ImmutableUtils.singletonSet(ImmutableUtils.makeImmutableMapEntry(Objects.toString(key, null), value));
	}

	@Override
	public V getOrDefault(Object key, V defaultValue) {
		if (Objects.equals(key, Objects.toString(this.key, null))) {
			return value;
		}
		return defaultValue;
	}

	@Override
	public void forEach(BiConsumer<? super String, ? super V> action) {
		action.accept(Objects.toString(key, null), value);
	}

	@Override
	public void replaceAll(BiFunction<? super String, ? super V, ? extends V> function) {
		throw new UnsupportedOperationException();
	}

	@Override
	public V putIfAbsent(String key, V value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean remove(Object key, Object value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean replace(String key, V oldValue, V newValue) {
		throw new UnsupportedOperationException();
	}

	@Override
	public V replace(String key, V value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public V computeIfAbsent(String key, Function<? super String, ? extends V> mappingFunction) {
		throw new UnsupportedOperationException();
	}

	@Override
	public V computeIfPresent(String key, BiFunction<? super String, ? super V, ? extends V> remappingFunction) {
		throw new UnsupportedOperationException();
	}

	@Override
	public V compute(String key, BiFunction<? super String, ? super V, ? extends V> remappingFunction) {
		throw new UnsupportedOperationException();
	}

	@Override
	public V merge(String key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int size() {
		return 1;
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public boolean containsValue(Object value) {
		return Objects.equals(this.value, value);
	}

	@Override
	public boolean containsKey(Object key) {
		return Objects.equals(key, Objects.toString(this.key, null));
	}

	@Override
	public V get(Object key) {
		if (Objects.equals(key, Objects.toString(this.key, null))) {
			return value;
		}
		return null;
	}

	@Override
	public V put(String key, V value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public V remove(Object key) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void putAll(Map<? extends String, ? extends V> m) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Set<String> keySet() {
		return ImmutableUtils.singletonSet(Objects.toString(key, null));
	}

	@Override
	public Collection<V> values() {
		return ImmutableUtils.singletonSet(value);
	}

	public Map.Entry<K, V> tojava_util_Map_Entry() {
		return ImmutableUtils.makeImmutableMapEntry(key, value);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(key);
		out.writeObject(value);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		key = SerialUtils.readExternalObject(in);
		value = SerialUtils.readExternalObject(in);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(Objects.toString(key, null)) ^ Objects.hashCode(value);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof Map)) {
			return false;
		}
		Map<?, ?> m = (Map<?, ?>) obj;
		Iterator<? extends Map.Entry<?, ?>> it = m.entrySet().iterator();
		if (!it.hasNext()) {
			return false;
		}
		Map.Entry<?, ?> entry = it.next();
		if (it.hasNext()) {
			return false;
		}
		return Objects.equals(Objects.toString(key, null), entry.getKey()) && Objects.equals(value, entry.getValue());
	}
}
