package saker.build.thirdparty.saker.util.ref;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Objects;

/**
 * Reference class that allows the referent to be strongly referenced alongside its soft reference.
 * <p>
 * This class works the same way as {@link SoftReference}, but when constructed, it also holds the referent by a strong
 * reference. This prevents it from being garbage collected until {@link #makeSoft()} is called.
 * <p>
 * This class is useful when dynamic caching is implemented. Making a reference soft can allow the referenced object to
 * be garbage collected, but calling {@link #makeStrong()} again, can allow the client to reuse a not yet garbage
 * collected object instead of creating a new one.
 * 
 * @param <T>
 *            The type of the referenced object.
 * @see StrongWeakReference
 */
public class StrongSoftReference<T> extends SoftReference<T> {
	@SuppressWarnings("unused")
	private T strongReference;

	/**
	 * @see SoftReference#SoftReference(Object, ReferenceQueue)}
	 */
	public StrongSoftReference(T referent, ReferenceQueue<? super T> q) {
		super(Objects.requireNonNull(referent, "referent"), q);
		this.strongReference = referent;
	}

	/**
	 * @see SoftReference#SoftReference(Object)}
	 */
	public StrongSoftReference(T referent) {
		super(referent);
		this.strongReference = referent;
	}

	@Override
	public void clear() {
		super.clear();
		strongReference = null;
	}

	/**
	 * Makes <code>this</code> reference soft by clearing its strong reference.
	 */
	public void makeSoft() {
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
