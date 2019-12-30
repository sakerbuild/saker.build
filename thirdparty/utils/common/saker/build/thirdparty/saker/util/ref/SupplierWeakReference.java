package saker.build.thirdparty.saker.util.ref;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.function.Supplier;

/**
 * Class extending {@link WeakReference} and also implementing {@link Supplier}.
 * 
 * @param <T>
 *            The type of the referent.
 */
public class SupplierWeakReference<T> extends WeakReference<T> implements Supplier<T> {
	/**
	 * @see WeakReference#WeakReference(Object, ReferenceQueue)
	 */
	public SupplierWeakReference(T referent, ReferenceQueue<? super T> q) {
		super(referent, q);
	}

	/**
	 * @see WeakReference#WeakReference(Object)
	 */
	public SupplierWeakReference(T referent) {
		super(referent);
	}

}
