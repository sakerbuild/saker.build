package saker.build.thirdparty.saker.util;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Comparator;

class ComparatorEmptySortedSet<E> extends EmptySortedSet<E> {
	private static final long serialVersionUID = 1L;

	private Comparator<? super E> comparator;

	/**
	 * For {@link Externalizable}.
	 */
	public ComparatorEmptySortedSet() {
	}

	public ComparatorEmptySortedSet(Comparator<? super E> comparator) {
		this.comparator = comparator;
	}

	@Override
	public Comparator<? super E> comparator() {
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
		comparator = (Comparator<? super E>) in.readObject();
	}
}
