package saker.build.thirdparty.saker.util.ref;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.function.Supplier;

/**
 * Class extending {@link SoftReference} and also implementing {@link Supplier}.
 * 
 * @param <T>
 *            The type of the referent.
 */
public class SupplierSoftReference<T> extends SoftReference<T> implements Supplier<T> {
	/**
	 * @see SoftReference#SoftReference(Object, ReferenceQueue)
	 */
	public SupplierSoftReference(T referent, ReferenceQueue<? super T> q) {
		super(referent, q);
	}

	/**
	 * @see SoftReference#SoftReference(Object)
	 */
	public SupplierSoftReference(T referent) {
		super(referent);
	}

}
