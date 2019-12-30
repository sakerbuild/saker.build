package saker.build.thirdparty.saker.util;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Comparator;
import java.util.List;

class ImmutableEntryListComparatorNavigableMap<K, V> extends ImmutableEntryListNavigableMap<K, V> {
	private static final long serialVersionUID = 1L;

	private Comparator<? super K> comparator;

	/**
	 * For {@link Externalizable}.
	 */
	public ImmutableEntryListComparatorNavigableMap() {
	}

	protected ImmutableEntryListComparatorNavigableMap(List<Entry<K, V>> entries, Comparator<? super K> comparator) {
		super(entries);
		this.comparator = comparator;
	}

	@Override
	public Comparator<? super K> comparator() {
		return comparator;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(comparator);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		comparator = (Comparator<? super K>) in.readObject();
	}
}
