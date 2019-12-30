package saker.build.thirdparty.saker.rmi.connection;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

class RMIGeneratingCommCache<T> {
	private final ConcurrentSkipListMap<Integer, GeneratingWeakReference<T>> readDatas;
	private final ConcurrentHashMap<GeneratingWeakReference<T>, Integer> readIndices;

	private final AtomicInteger indexCounter;
	private final ConcurrentHashMap<GeneratingWeakReference<T>, Integer> writeDatas;

	public RMIGeneratingCommCache() {
		readDatas = new ConcurrentSkipListMap<>();
		readIndices = new ConcurrentHashMap<>();

		indexCounter = new AtomicInteger();
		writeDatas = new ConcurrentHashMap<>();
	}

	public Integer getWriteIndex(T data) {
		return writeDatas.get(new RefSearcher(data));
	}

	public void putWrite(T data, Supplier<? extends T> datasupplier, int index) {
		writeDatas.putIfAbsent(new GeneratingWeakReference<>(data, datasupplier), index);
	}

	public Integer putReadIfAbsent(T data, Supplier<? extends T> datasupplier) {
		RefSearcher searcher = new RefSearcher(data);
		Integer presentidx = readIndices.get(searcher);
		if (presentidx != null) {
			return null;
		}
		GeneratingWeakReference<T> weakref = new GeneratingWeakReference<>(data, datasupplier);
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
		GeneratingWeakReference<T> ref = readDatas.get(index);
		if (ref == null) {
			return null;
		}
		return ref.get();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[]";
	}

	private final static class GeneratingWeakReference<T> implements Supplier<T> {
		private WeakReference<T> weakRef;
		private Supplier<? extends T> refSupplier;
		private final int hashCode;

		public GeneratingWeakReference(T referent, Supplier<? extends T> refSupplier) {
			this.weakRef = new WeakReference<>(referent);
			this.refSupplier = refSupplier;
			this.hashCode = referent.hashCode();
		}

		@Override
		public T get() {
			T result = weakRef.get();
			if (result == null) {
				//the object was garbage collected, regenerate it, and cache it again
				result = refSupplier.get();
				this.weakRef = new WeakReference<>(result);
			}
			return result;
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
			if (objclass == GeneratingWeakReference.class) {
				return Objects.equals(this.get(), ((GeneratingWeakReference<?>) obj).get());
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
			if (objclass == GeneratingWeakReference.class) {
				return this.object.equals(((GeneratingWeakReference<?>) obj).get());
			}
			if (objclass == RefSearcher.class) {
				return this.object.equals(((RefSearcher) obj).object);
			}
			return false;
		}
	}

}