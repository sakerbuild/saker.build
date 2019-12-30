package saker.build.thirdparty.saker.util;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

class UnmodifiableMapEntryIterator<K, V> implements Iterator<Map.Entry<K, V>> {
	private Iterator<? extends Map.Entry<? extends K, ? extends V>> it;

	public UnmodifiableMapEntryIterator(Iterator<? extends Map.Entry<? extends K, ? extends V>> it) {
		this.it = it;
	}

	@Override
	public boolean hasNext() {
		return it.hasNext();
	}

	@Override
	public Map.Entry<K, V> next() {
		Entry<? extends K, ? extends V> n = it.next();
		return ImmutableUtils.unmodifiableMapEntry(n);
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException("remove");
	}

	@Override
	public void forEachRemaining(Consumer<? super Map.Entry<K, V>> action) {
		it.forEachRemaining(e -> ImmutableUtils.unmodifiableMapEntry(e));
	}
}
