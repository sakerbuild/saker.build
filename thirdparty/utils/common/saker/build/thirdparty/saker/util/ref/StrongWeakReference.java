package saker.build.thirdparty.saker.util.ref;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/**
 * Reference class that allows the referent to be strongly referenced alongside its weak reference.
 * <p>
 * This class works the same way as {@link WeakReference}, but when constructed, it also holds the referent by a strong
 * reference. This prevents it from being garbage collected until {@link #makeWeak()} is called.
 * <p>
 * This class is useful when dynamic caching is implemented. Making a reference weak can allow the referenced object to
 * be garbage collected, but calling {@link #makeStrong()} again, can allow the client to reuse a not yet garbage
 * collected object instead of creating a new one.
 * 
 * @param <T>
 *            The type of the referenced object.
 * @see StrongSoftReference
 */
public class StrongWeakReference<T> extends WeakReference<T> {
	@SuppressWarnings("unused")
	private T strongReference;

	/**
	 * @see WeakReference#WeakReference(Object, ReferenceQueue)
	 */
	public StrongWeakReference(T referent, ReferenceQueue<? super T> q) throws NullPointerException {
		super(referent, q);
		this.strongReference = referent;
	}

	/**
	 * @see WeakReference#WeakReference(Object)
	 */
	public StrongWeakReference(T referent) throws NullPointerException {
		super(referent);
		this.strongReference = referent;
	}

	@Override
	public void clear() {
		super.clear();
		strongReference = null;
	}

	/**
	 * Makes <code>this</code> reference weak by clearing its strong reference.
	 */
	public void makeWeak() {
		strongReference = null;
	}

	/**
	 * Tries to make <code>this</code> reference strong again, by assigning the strong reference to the referent.
	 * <p>
	 * This method can fail and return <code>false</code>, if the referenced object was already garbage collected. (I.e.
	 * {@link #get()} returns <code>null</code>.)
	 * <p>
	 * If this method succeeds, then the following calls to {@link #get()} will return non-<code>null</code>.
	 * 
	 * @return <code>true</code> if the reference was successfully made strong.
	 */
	public boolean makeStrong() {
		T ref = get();
		this.strongReference = ref;
		return ref != null;
	}
}
