package saker.build.util.data.collection;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

import saker.build.thirdparty.saker.util.ArrayUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.util.data.ConversionContext;
import saker.build.util.data.DataConverterUtils;

@SuppressWarnings("rawtypes")
public class ProxyListArray extends AbstractCollection implements List {
	private final class ListIteratorImplementation implements ListIterator {
		private int index;

		public ListIteratorImplementation(int index) {
			this.index = index;
		}

		@Override
		public boolean hasNext() {
			return index < size();
		}

		@Override
		public Object next() {
			Object o = Array.get(array, index++);
			return DataConverterUtils.convert(conversionContext, o, selfElementType);
		}

		@Override
		public boolean hasPrevious() {
			return index > 0;
		}

		@Override
		public Object previous() {
			Object o = Array.get(array, --index);
			return DataConverterUtils.convert(conversionContext, o, selfElementType);
		}

		@Override
		public int nextIndex() {
			return index + 1;
		}

		@Override
		public int previousIndex() {
			return index - 1;
		}

		@Override
		public void set(Object e) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void add(Object e) {
			throw new UnsupportedOperationException();
		}
	}

	private final ConversionContext conversionContext;
	private final Object array;
	private final Type selfElementType;

	public ProxyListArray(ConversionContext conversionContext, Object array, Type selfElementType) {
		this.conversionContext = conversionContext;
		this.array = array;
		this.selfElementType = selfElementType;
	}

	@Override
	public int size() {
		return Array.getLength(array);
	}

	@Override
	public boolean isEmpty() {
		return size() == 0;
	}

	@Override
	public boolean contains(Object o) {
		int size = size();
		for (int i = 0; i < size; i++) {
			if (Objects.equals(o, get(i))) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Iterator iterator() {
		return listIterator();
	}

	@Override
	public boolean add(Object e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean remove(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean containsAll(Collection c) {
		if (c.size() == 0) {
			return true;
		}
		Object[] array = toArray();
		for (int i = 0; i < array.length; i++) {
			Object o = array[i];
			if (!c.contains(o)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean addAll(Collection c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean addAll(int index, Collection c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeAll(Collection c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean retainAll(Collection c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object get(int index) {
		return DataConverterUtils.convert(conversionContext, Array.get(array, index), selfElementType);
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
	public Object remove(int index) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int indexOf(Object o) {
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
		return new ListIteratorImplementation(0);
	}

	@Override
	public ListIterator listIterator(int index) {
		return new ListIteratorImplementation(index);
	}

	@Override
	public List subList(int fromIndex, int toIndex) {
		ArrayUtils.requireArrayStartEndRangeLength(size(), fromIndex, toIndex);
		//XXX make this more efficient by not copying the array
		int len = toIndex - fromIndex;
		Object narray = Array.newInstance(array.getClass().getComponentType(), len);
		System.arraycopy(array, fromIndex, narray, 0, len);
		return new ProxyListArray(conversionContext, narray, selfElementType);
	}

	@Override
	public String toString() {
		return ArrayUtils.arrayToString(array);
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
