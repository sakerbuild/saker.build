package saker.build.util.config;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

import saker.apiextract.api.PublicApi;
import saker.build.meta.PropertyNames;

/**
 * Utility class for creating weak or soft references based on the configuration of this JVM.
 * <p>
 * The created reference types can be configured using {@link PropertyNames#PROPERTY_SAKER_REFERENCE_POLICY}.
 */
@PublicApi
public class ReferencePolicy {
	/**
	 * Enumeration of possible reference policy implementations.
	 */
	public static enum ReferencePolicyCreator {
		/**
		 * Reference polity for using {@linkplain WeakReference weak references}.
		 */
		WEAK {
			@Override
			public <T> Reference<T> create(T referent) {
				return new WeakReference<>(referent);
			}

			@Override
			public <T> Reference<T> create(T referent, ReferenceQueue<? super T> queue) {
				return new WeakReference<>(referent, queue);
			}
		},
		/**
		 * Reference polity for using {@linkplain SoftReference soft references}.
		 */
		SOFT {
			@Override
			public <T> Reference<T> create(T referent) {
				return new SoftReference<>(referent);
			}

			@Override
			public <T> Reference<T> create(T referent, ReferenceQueue<? super T> queue) {
				return new SoftReference<>(referent, queue);
			}
		};

		/**
		 * Creates a reference object for the given referent.
		 * 
		 * @param <T>
		 *            The type of the referent.
		 * @param referent
		 *            The referent.
		 * @return The created reference.
		 */
		public abstract <T> Reference<T> create(T referent);

		/**
		 * Creates a reference object for the given referent and reference queue.
		 * 
		 * @param <T>
		 *            The type of the referent.
		 * @param referent
		 *            The referent.
		 * @param queue
		 *            The reference queue.
		 * @return The created reference.
		 */
		public abstract <T> Reference<T> create(T referent, ReferenceQueue<? super T> queue);
	}

	private static final ReferencePolicyCreator POLICY;

	static {
		String policy = PropertyNames.getProperty(PropertyNames.PROPERTY_SAKER_REFERENCE_POLICY);
		if (policy != null) {
			POLICY = ReferencePolicyCreator.valueOf(policy.toUpperCase());
		} else {
			POLICY = ReferencePolicyCreator.SOFT;
		}
	}

	private ReferencePolicy() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Creates a reference object for the given referent.
	 * 
	 * @param <T>
	 *            The type of the referent.
	 * @param referent
	 *            The referent.
	 * @return The created reference.
	 * @see SoftReference#SoftReference(Object)
	 * @see WeakReference#WeakReference(Object)
	 */
	public static <T> Reference<T> createReference(T referent) {
		return POLICY.create(referent);
	}

	/**
	 * Creates a reference object for the given referent and reference queue.
	 * 
	 * @param <T>
	 *            The type of the referent.
	 * @param referent
	 *            The referent.
	 * @param queue
	 *            The reference queue.
	 * @return The created reference.
	 * @see SoftReference#SoftReference(Object, ReferenceQueue)
	 * @see WeakReference#WeakReference(Object, ReferenceQueue)
	 */
	public static <T> Reference<T> createReference(T referent, ReferenceQueue<? super T> queue) {
		return POLICY.create(referent, queue);
	}
}
