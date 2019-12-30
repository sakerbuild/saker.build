package saker.build.thirdparty.saker.util;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.function.Consumer;

class ReversingList<E> extends AbstractCollection<E> implements List<E> {
	List<E> list;

	public ReversingList(List<E> list) {
		this.list = list;
	}

	@Override
	public boolean add(E e) {
		list.add(0, e);
		return true;
	}

	@Override
	public void add(int index, E element) {
		if (index == 0) {
			//add to the first
			list.add(element);
			return;
		}
		list.add(idx(index - 1), element);
	}

	@Override
	public E set(int index, E element) {
		return list.set(idx(index), element);
	}

	@Override
	public E get(int index) {
		return list.get(idx(index));
	}

	@Override
	public int size() {
		return list.size();
	}

	@Override
	public void clear() {
		list.clear();
	}

	@Override
	public List<E> subList(int fromIndex, int toIndex) {
		int size = list.size();
		int len = toIndex - fromIndex;
		int realtoidx = size - fromIndex;
		int realfromidx = realtoidx - len;
		return new ReversingList<>(list.subList(realfromidx, realtoidx));
	}

	@Override
	public void forEach(Consumer<? super E> action) {
		Objects.requireNonNull(action, "action");
		ListIterator<E> lit = list.listIterator(list.size());
		while (lit.hasPrevious()) {
			action.accept(lit.previous());
		}
	}

	@Override
	public Iterator<E> iterator() {
		return listIterator();
	}

	@Override
	public ListIterator<E> listIterator() {
		return new ReverseListIterator<>(list.listIterator(list.size()));
	}

	@Override
	public ListIterator<E> listIterator(int index) {
		return new ReverseListIterator<>(list.listIterator(list.size() - index));
	}

	@Override
	public void sort(Comparator<? super E> c) {
		list.sort(Collections.reverseOrder(c));
	}

	@Override
	public boolean isEmpty() {
		return list.isEmpty();
	}

	@Override
	public Object[] toArray() {
		Object[] result = list.toArray();
		ArrayUtils.reverse(result);
		return result;
	}

	@Override
	public <T> T[] toArray(T[] a) {
		T[] result = list.toArray(a);
		ArrayUtils.reverse(result, 0, size());
		return result;
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		boolean modified = false;
		for (E e : c) {
			add(index++, e);
			modified = true;
		}
		return modified;
	}

	@Override
	public E remove(int index) {
		return list.remove(idx(index));
	}

	@Override
	public int indexOf(Object o) {
		int res = list.lastIndexOf(o);
		if (res < 0) {
			return res;
		}
		return idx(res);
	}

	@Override
	public int lastIndexOf(Object o) {
		int res = list.indexOf(o);
		if (res < 0) {
			return res;
		}
		return idx(res);
	}

	@Override
	public int hashCode() {
		return ObjectUtils.listHash(this);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (!(obj instanceof List)) {
			return false;
		}

		Iterator<?> e1 = iterator();
		Iterator<?> e2 = ((List<?>) obj).iterator();
		while (e1.hasNext()) {
			if (!e2.hasNext()) {
				return false;
			}
			Object o1 = e1.next();
			Object o2 = e2.next();
			if (!Objects.equals(o1, o2)) {
				return false;
			}
		}
		if (e2.hasNext()) {
			return false;
		}
		return true;
	}

	private int idx(int index) {
		return list.size() - 1 - index;
	}

	private static class ReverseListIterator<T> implements ListIterator<T> {
		private ListIterator<T> it;

		public ReverseListIterator(ListIterator<T> it) {
			this.it = it;
		}

		@Override
		public boolean hasNext() {
			return it.hasPrevious();
		}

		@Override
		public T next() {
			return it.previous();
		}

		@Override
		public boolean hasPrevious() {
			return it.hasNext();
		}

		@Override
		public T previous() {
			return it.next();
		}

		@Override
		public int nextIndex() {
			return it.previousIndex();
		}

		@Override
		public int previousIndex() {
			return it.nextIndex();
		}

		@Override
		public void remove() {
			it.remove();
		}

		@Override
		public void set(T e) {
			it.set(e);
		}

		@Override
		public void add(T e) {
			it.add(e);
		}
	}

}
