package saker.build.file.content;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;

import saker.apiextract.api.PublicApi;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * {@link ContentDescriptor} implementation that aggregates multiple child content descriptors.
 * <p>
 * This content descriptor contains multiple child content descriptors, and checks each of the against the corresponding
 * other when changes are to be detected.
 * <p>
 * The content descriptor stores the children in order. This means that the order of the content descriptors are defined
 * during instantiation. When changes are detected, each content descriptor changes will be checked against the argument
 * content descriptor with the same index.
 * <p>
 * Use the static factory methods to create a new instance.
 */
@PublicApi
public final class MultiContentDescriptor implements ContentDescriptor, Externalizable {
	private static final long serialVersionUID = 1L;

	private Collection<ContentDescriptor> children;

	/**
	 * For {@link Externalizable}.
	 */
	public MultiContentDescriptor() {
	}

	private MultiContentDescriptor(Collection<ContentDescriptor> children) {
		this.children = children;
	}

	/**
	 * Creates a new instance for the given content descriptors.
	 * <p>
	 * If the argument is empty, {@link EmptyContentDescriptor} will be returned.
	 * <p>
	 * If the argument contains only a single descriptor, that single descriptor will be returned.
	 * 
	 * @param descriptors
	 *            The descriptors to create a content descriptor for.
	 * @return The content descriptor representing the arguments.
	 * @throws NullPointerException
	 *             If the argument, or any descriptor is <code>null</code> in it.
	 */
	public static ContentDescriptor create(Collection<? extends ContentDescriptor> descriptors)
			throws NullPointerException {
		Objects.requireNonNull(descriptors, "descriptors");
		if (descriptors.isEmpty()) {
			return EmptyContentDescriptor.INSTANCE;
		}
		Collection<ContentDescriptor> descset = ImmutableUtils.makeImmutableList(descriptors);
		if (descset.contains(null)) {
			throw new NullPointerException("null descriptor in arguments.");
		}
		if (descset.size() == 1) {
			return descset.iterator().next();
		}
		return new MultiContentDescriptor(descset);
	}

	/**
	 * Creates a new instance for the given content descriptors.
	 * <p>
	 * If the argument is empty, {@link EmptyContentDescriptor} will be returned.
	 * <p>
	 * If the argument contains only a single descriptor, that single descriptor will be returned.
	 * 
	 * @param descriptors
	 *            The descriptors to create a content descriptor for.
	 * @return The content descriptor representing the arguments.
	 * @throws NullPointerException
	 *             If the argument, or any descriptor is <code>null</code> in it.
	 */
	public static ContentDescriptor create(ContentDescriptor... descriptors) throws NullPointerException {
		Objects.requireNonNull(descriptors, "descriptors");
		if (descriptors.length == 0) {
			return EmptyContentDescriptor.INSTANCE;
		}
		if (descriptors.length == 1) {
			return Objects.requireNonNull(descriptors[0], "element");
		}
		Collection<ContentDescriptor> descset = ImmutableUtils.makeImmutableList(descriptors);
		if (descset.contains(null)) {
			throw new NullPointerException("null descriptor in arguments.");
		}
		if (descset.size() == 1) {
			return descset.iterator().next();
		}
		return new MultiContentDescriptor(descset);
	}

	@Override
	public synchronized boolean isChanged(ContentDescriptor content) {
		if (content == null || getClass() != content.getClass()) {
			return true;
		}
		MultiContentDescriptor other = (MultiContentDescriptor) content;
		return isChangedCollection(this.children, other.children);
	}

	private static boolean isChangedCollection(Collection<? extends ContentDescriptor> expected,
			Collection<? extends ContentDescriptor> current) {
		if (expected.size() != current.size()) {
			return true;
		}
		Iterator<? extends ContentDescriptor> it = expected.iterator();
		Iterator<? extends ContentDescriptor> oit = current.iterator();
		while (it.hasNext()) {
			ContentDescriptor in = it.next();
			ContentDescriptor oin = oit.next();
			if (in.isChanged(oin)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, children);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		children = SerialUtils.readExternalImmutableLinkedHashSet(in);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + children + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((children == null) ? 0 : children.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MultiContentDescriptor other = (MultiContentDescriptor) obj;
		if (!ObjectUtils.collectionOrderedEquals(this.children, other.children)) {
			return false;
		}
		return true;
	}

}
