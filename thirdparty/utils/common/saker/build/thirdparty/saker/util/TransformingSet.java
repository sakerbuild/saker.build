package saker.build.thirdparty.saker.util;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Pseudo-{@link Set} implementation that dynamically generated the elements for it based on the elements of a subject
 * set.
 * <p>
 * The set is immutable, and designed to provide a set implementation that is only used to iterate over its elements.
 * Each element in this set is dynamically generated from the undelying set.
 * <p>
 * Any method that is not the {@link #iterator()}, {@link #forEach(Consumer)}, {@link #size()} functions, may throw an
 * {@link UnsupportedOperationException} any time.
 * <p>
 * <b>Important:</b> Implementations should ensure that the transformed elements still stay unique in the set, as they
 * was in the subject set. Violating this may result in undefined behaviour in some implementations.
 * <p>
 * An use-case for this kind of set is to create a new {@link Set} or {@link Collection} with the given elements without
 * pre-allocating the transformed elements beforehand.
 * <p>
 * Example: <br>
 * A new set that is created from a set of integers, which have the original integer elements squared.
 * 
 * <pre>
 * Set&lt;Integer&gt; ints = ...;
 * Set&lt;Integer&gt; squares = new TreeSet&lt;&gt;(new TransformingSet&lt;Integer, Integer&gt;(ints) {
 * 	&#64;Override
 * 	protected Integer transform(Integer e) {
 * 		return e * e;
 * 	}
 * });
 * </pre>
 * 
 * Constructing a collection in this way instead of calling {@link Collection#add(Object)} for every object can be more
 * efficient, as new collection constructors can allocate and construct their instances more efficiently.
 * 
 * @param <SE>
 *            The source set element type.
 * @param <E>
 *            The element type of this set.
 */
public abstract class TransformingSet<SE, E> extends AbstractSet<E> {
	/**
	 * The backing set of the transforming set.
	 */
	protected final Set<? extends SE> set;

	/**
	 * Creates a new instance with the given set.
	 * 
	 * @param set
	 *            The subject set.
	 * @throws NullPointerException
	 *             If the set is <code>null</code>.
	 */
	public TransformingSet(Set<? extends SE> set) throws NullPointerException {
		Objects.requireNonNull(set, "set");
		this.set = set;
	}

	/**
	 * Transforms the source set element to the actual element.
	 * 
	 * @param e
	 *            The element to transform.
	 * @return The transformed element.
	 */
	protected abstract E transform(SE e);

	@Override
	public Iterator<E> iterator() {
		Iterator<? extends SE> it = set.iterator();
		return new Iterator<E>() {
			@Override
			public boolean hasNext() {
				return it.hasNext();
			}

			@Override
			public E next() {
				return transform(it.next());
			}
		};
	}

	@Override
	public int size() {
		return set.size();
	}

	@Override
	public boolean isEmpty() {
		return set.isEmpty();
	}

	@Override
	public void forEach(Consumer<? super E> action) {
		Objects.requireNonNull(action, "action");
		set.forEach(e -> action.accept(transform(e)));
	}

}
