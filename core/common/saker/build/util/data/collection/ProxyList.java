package saker.build.util.data.collection;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.util.data.ConversionContext;
import saker.build.util.data.DataConverterUtils;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class ProxyList extends ProxyCollection<List> implements List {
	private final class ListIteratorImplementation implements ListIterator {
		private ListIterator it;

		public ListIteratorImplementation(ListIterator it) {
			this.it = it;
		}

		@Override
		public boolean hasNext() {
			return it.hasNext();
		}

		@Override
		public Object next() {
			return DataConverterUtils.convert(conversionContext, it.next(), selfElementType);
		}

		@Override
		public boolean hasPrevious() {
			return it.hasPrevious();
		}

		@Override
		public Object previous() {
			return DataConverterUtils.convert(conversionContext, it.previous(), selfElementType);
		}

		@Override
		public int nextIndex() {
			return it.nextIndex();
		}

		@Override
		public int previousIndex() {
			return it.previousIndex();
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void set(Object e) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void add(Object e) {
			throw new UnsupportedOperationException();
		}
	}

	public ProxyList(ConversionContext conversionContext, List coll, Type selfElementType) {
		super(conversionContext, coll, selfElementType);
	}

	@Override
	public Object get(int index) {
		return DataConverterUtils.convert(conversionContext, coll.get(index), selfElementType);
	}

	@Override
	public Object set(int index, Object element) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void add(int index, Object element) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean addAll(int index, Collection c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object remove(int index) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int indexOf(Object o) {
		// Object
		int size = size();
		for (int i = 0; i < size; i++) {
			if (Objects.equals(o, get(i))) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public int lastIndexOf(Object o) {
		// Object
		int size = size();
		for (int i = size - 1; i >= 0; i--) {
			if (Objects.equals(o, get(i))) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public ListIterator listIterator() {
		return new ListIteratorImplementation(coll.listIterator());
	}

	@Override
	public ListIterator listIterator(int index) {
		return new ListIteratorImplementation(coll.listIterator(index));
	}

	@Override
	public List subList(int fromIndex, int toIndex) {
		return new ProxyList(conversionContext, coll.subList(fromIndex, toIndex), selfElementType);
	}

	@Override
	public int hashCode() {
		return ObjectUtils.listHash(this);
	}

	@Override
	public boolean equals(Object o) {
		// based on AbstractList source code
		if (o == this)
			return true;
		if (!(o instanceof List))
			return false;
		ListIterator e1 = listIterator();
		ListIterator e2 = ((List) o).listIterator();
		while (e1.hasNext() && e2.hasNext()) {
			Object o1 = e1.next();
			Object o2 = e2.next();
			if (!Objects.equals(o1, o2)) {
				return false;
			}
		}
		return !(e1.hasNext() || e2.hasNext());
	}

}
