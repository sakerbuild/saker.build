package saker.build.thirdparty.saker.util.io;

import java.util.ConcurrentModificationException;

/**
 * Exception thrown when a serializing implementation encounters different number of items than expected.
 * <p>
 * This can happen if a serializer queries the size of a collection/map, and later sees less or more number of elements
 * than previously queried. This can happen if the collection was concurrently modified between querying the size of it,
 * and actually serializing the elements.
 * <p>
 * The {@link #getDifferenceCount()} method can be used to check the relation whether less or more elements have been
 * encountered.
 */
public class SerializationConcurrentModificationException extends ConcurrentModificationException {
	private static final long serialVersionUID = 1L;

	private final int differenceCount;

	/**
	 * Creates a new instance with the given difference count.
	 * 
	 * @param differenceCount
	 *            The difference count.
	 * @throws IllegalArgumentException
	 *             If the difference count is 0.
	 */
	public SerializationConcurrentModificationException(int differenceCount) throws IllegalArgumentException {
		super(differenceCount < 0 ? "More elements than expected in the serialized collection."
				: "Less elements than expected in the serialized collection by " + differenceCount + ".");
		if (differenceCount == 0) {
			throw new IllegalArgumentException("The difference count is 0.");
		}
		this.differenceCount = differenceCount;
	}

	/**
	 * Gets the difference count relative to the expected serialization count.
	 * <p>
	 * If the returned count is positive, it means that the serializer encountered less elements than expected. The
	 * returned number of elements were missing from the serialized collection.
	 * <p>
	 * If the number is negative, it means that the collections had more elements in the collection compared to the
	 * previously reported size. Negative numbers may not be accurate, meaning that there may be more elements than the
	 * negative result reports. E.g. if this method reports -1, then there may be more than one unserialized elements
	 * than just 1.
	 * 
	 * @return The element difference count of the serialization.
	 */
	public int getDifferenceCount() {
		return differenceCount;
	}
}
