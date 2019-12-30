package saker.build.thirdparty.saker.rmi.connection;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;

import saker.build.thirdparty.saker.util.ObjectUtils;

class RMIWeakCommCache<T> {
	private final ConcurrentSkipListMap<Integer, WeakReference<T>> readDatas;
	private final ConcurrentHashMap<WeakReference<T>, Integer> readIndices;

	private final AtomicInteger indexCounter;
	private final ConcurrentHashMap<WeakReference<T>, Integer> writeDatas;

	public RMIWeakCommCache() {
		readDatas = new ConcurrentSkipListMap<>();
		readIndices = new ConcurrentHashMap<>();

		indexCounter = new AtomicInteger();
		writeDatas = new ConcurrentHashMap<>();
	}

	public Integer getWriteIndex(T data) {
		return writeDatas.get(new RefSearcher(data));
	}

	public void putWrite(T data, int index) {
		writeDatas.putIfAbsent(new RefSearcherWeakReference<>(data), index);
	}

	int putReadInternal(int index, T data) {
		readDatas.putIfAbsent(index, new RefSearcherWeakReference<>(data));
		indexCounter.updateAndGet(c -> Math.max(c, index));
		return index;
	}

	public Integer putReadIfAbsent(T data) {
		RefSearcher searcher = new RefSearcher(data);
		Integer presentidx = readIndices.get(searcher);
		if (presentidx != null) {
			return null;
		}
		RefSearcherWeakReference<T> weakref = new RefSearcherWeakReference<>(data);
		int index = indexCounter.incrementAndGet();
		Integer putidxprev = readIndices.putIfAbsent(weakref, index);
		if (putidxprev != null) {
			//data was put concurrently
			return null;
		}
		readDatas.putIfAbsent(index, weakref);
		return index;
	}

	public T getRead(int index) {
		return ObjectUtils.getReference(readDatas.get(index));
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[]";
	}

	private final static class RefSearcherWeakReference<T> extends WeakReference<T> {
		private final int hashCode;

		public RefSearcherWeakReference(T referent) {
			super(referent);
			this.hashCode = referent.hashCode();
		}

		@Override
		public int hashCode() {
			return hashCode;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			Class<? extends Object> objclass = obj.getClass();
			if (objclass == RefSearcherWeakReference.class) {
				return Objects.equals(this.get(), ((RefSearcherWeakReference<?>) obj).get());
			}
			if (objclass == RefSearcher.class) {
				return ((RefSearcher) obj).object.equals(this.get());
			}
			return false;
		}

	}

	private final static class RefSearcher {
		final Object object;
		private final int hashCode;

		public RefSearcher(Object object) {
			this.object = object;
			this.hashCode = object.hashCode();
		}

		@Override
		public int hashCode() {
			return hashCode;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			Class<? extends Object> objclass = obj.getClass();
			if (objclass == RefSearcherWeakReference.class) {
				return this.object.equals(((RefSearcherWeakReference<?>) obj).get());
			}
			if (objclass == RefSearcher.class) {
				return this.object.equals(((RefSearcher) obj).object);
			}
			return false;
		}
	}

}