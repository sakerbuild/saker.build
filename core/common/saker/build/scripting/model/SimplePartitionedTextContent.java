package saker.build.scripting.model;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

import saker.apiextract.api.PublicApi;
import saker.build.thirdparty.saker.util.ImmutableUtils;

/**
 * Simple implementation of {@link PartitionedTextContent}, holding precomputed data.
 */
@PublicApi
public class SimplePartitionedTextContent implements PartitionedTextContent {
	private Set<? extends TextPartition> partitions;

	/**
	 * Creates a new instance with the given partitions.
	 * 
	 * @param partitions
	 *            The partitions.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public SimplePartitionedTextContent(Collection<? extends TextPartition> partitions) throws NullPointerException {
		Objects.requireNonNull(partitions, "partitions");
		this.partitions = ImmutableUtils.makeImmutableLinkedHashSet(partitions);
	}

	/**
	 * Creates a new instance with a single partition.
	 * 
	 * @param partition
	 *            The partition.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public SimplePartitionedTextContent(TextPartition partition) throws NullPointerException {
		Objects.requireNonNull(partition, "partition");
		this.partitions = ImmutableUtils.singletonSet(partition);
	}

	@Override
	public Iterable<? extends TextPartition> getPartitions() {
		return partitions;
	}

	@Override
	public int hashCode() {
		return getPartitions().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SimplePartitionedTextContent other = (SimplePartitionedTextContent) obj;
		if (partitions == null) {
			if (other.partitions != null)
				return false;
		} else if (!partitions.equals(other.partitions))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + partitions + "]";
	}

}
