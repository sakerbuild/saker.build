package saker.build.thirdparty.saker.util;

/**
 * Interface for simple container classes that allow elements to be added (accumulated) to it.
 * <p>
 * Implemetations of this interface allow elements of a given type to be added to them, and later iterated over them.
 * The actual nature of the additions, or other restrictions are implementation dependent.
 * <p>
 * This interface doesn't specify requirements regarding to thread safety, algorithmic complexity of the additions or
 * iteration order and behaviour.
 * <p>
 * Addition methods may throw implementation specific runtime exceptions if they cannot fulfil the request. E.g. no more
 * pre-allocated storage available, or requirements regarding the argument is violated.
 * 
 * @param <E>
 *            The element type.
 */
public interface ElementAccumulator<E> extends Iterable<E> {
	/**
	 * Adds an element to the accumulator.
	 * 
	 * @param element
	 *            The element to add.
	 */
	public void add(E element);

	/**
	 * Gets the current number of accumulated elements in this accumulator.
	 * 
	 * @return The number of elements present.
	 */
	public int size();
}
