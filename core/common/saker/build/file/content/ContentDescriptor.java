package saker.build.file.content;

import java.io.Externalizable;

import saker.build.task.TaskFactory;

/**
 * Content descriptors uniquely describe the contents of the associated subject.
 * <p>
 * Content descriptors are often used with files do determine if their contents have changed compared to a previous
 * version. Although they are mostly used with files, they can be associated to other kind of datas as well. It is
 * recommended that instances are lightweight.
 * <p>
 * A content descriptor contains one method which is responsible for detecting changes when a previous content
 * descriptor instance is passed to them. They check any changes that is present between the two instances, and return
 * <code>true</code> if they are different.
 * <p>
 * Subclasses should satisfy the {@link #equals(Object)} and {@link #hashCode()} contract.
 * <p>
 * Implementers of this interface are recommended to provide a stable hash code. This is in order for any clients to
 * take advantage of the {@linkplain TaskFactory#CAPABILITY_CACHEABLE cacheability} of build tasks. Note, that this
 * implies that implementations should not be <code>enum</code>s.
 * <p>
 * It is strongly recommended that subclasses implement the {@link Externalizable} interface.
 * <p>
 * As a design decision, it was chosen to define this interface instead of just relying on the {@link #equals(Object)}
 * method of objects. Subclasses can fine-grain change detection which can result in reduction of unnecessary
 * computation, and make the runtime more error-tolerant.
 */
public interface ContentDescriptor {
	/**
	 * Detects changes between this content descriptor and the previous one.
	 * <p>
	 * The default implementation compares the two objects using {@link #equals(Object)}.
	 * <p>
	 * Implementations are encouraged to check the type of the parameter using <code>instanceof</code> before casting
	 * them. It is further encouraged to check the content differences against interfaces instead of direct class
	 * implementations.
	 * <p>
	 * One can think about this function as <code>this</code> is the expected content of a file, and the parameter is
	 * the actual contents. If this method returns <code>true</code> then the file associated with <code>this</code>
	 * should replace the file associated with the parameter contents. This is mainly used when synchronizing files in
	 * memory to the filesystem.
	 * 
	 * @param previouscontent
	 *            The previous content to examine. Might be <code>null</code> to represent that the previous content
	 *            doesn't exist.
	 * @return <code>true</code> if the contents described by this descriptor differs from the parameter.
	 */
	public default boolean isChanged(ContentDescriptor previouscontent) {
		return !this.equals(previouscontent);
	}

	/**
	 * Checks if this content descriptor is exactly the same as the parameter.
	 * <p>
	 * It is not required by implementations that if {@link #equals(Object)} returns <code>true</code>, then
	 * {@link #isChanged(ContentDescriptor)} should return <code>false</code>. As a possible scenario, two contents can
	 * equal, and still report changes between same contents. (E.g. {@link NullContentDescriptor} which equal, but
	 * consider everything changed) <br>
	 * By employing this mechanism implementations can be more error-prone in regards with deleted/missing/erroneous
	 * contents.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj);

	@Override
	public int hashCode();
}
