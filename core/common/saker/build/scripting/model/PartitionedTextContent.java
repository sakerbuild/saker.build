package saker.build.scripting.model;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Interface for text content which is partitioned into segments.
 * <p>
 * A partition consists of a title, subtitle, and its body text content. The partitions are an abstract representation
 * of a segment in a larger textual document.
 * <p>
 * It is implementation dependent how a consumer of a partitioned text content will display the partitions. As a general
 * rule of thumb, they are displayed in sequential order with their titles preceding them.
 * <p>
 * Partitioned text contents are usually used by script models to provide information about a code segment where the
 * information is collected from multiple sources. These text contents are usually display in a tooltip in the IDE for
 * providing information to the user.
 * 
 * @see SimplePartitionedTextContent
 */
public interface PartitionedTextContent {
	/**
	 * Gets the partitions this instance contains.
	 * <p>
	 * The returned iterable contains the partitions in the iteration order. It is recommended to use a
	 * {@link LinkedHashSet} underlying implementation to avoid partitions with duplicate contents.
	 * <p>
	 * The returned iterable is recommended to be unmodifiable.
	 * 
	 * @return An iterable of partitions.
	 */
	public Iterable<? extends TextPartition> getPartitions();

	/**
	 * Gets the hash code of the partitioned text content.
	 * <p>
	 * The has code is defined to be the following:
	 * 
	 * <pre>
	 * getPartitions().hashCode()
	 * </pre>
	 * 
	 * The partitions hash code is computed according to the {@link Set#hashCode()} definition.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);
}
