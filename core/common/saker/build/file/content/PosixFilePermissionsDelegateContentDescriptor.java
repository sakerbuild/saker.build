package saker.build.file.content;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.attribute.PosixFilePermission;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;

import saker.apiextract.api.PublicApi;
import saker.build.file.SakerFile;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * Delegate {@link ContentDescriptor} that also holds associated posix file permissions.
 * <p>
 * Instances of this content descriptor can (and recommended to) be used when {@linkplain SakerFile files} are
 * associated with posix file permissions. That is, if the {@link SakerFile#getPosixFilePermissions()} method is to
 * return non-<code>null</code>, then its {@linkplain SakerFile#getContentDescriptor() content descriptor} should
 * reflect those permissions as well.
 * <p>
 * Clients can use {@link #get(ContentDescriptor, Set)} to create a new instance.
 * 
 * @since saker.build 0.8.13
 */
@PublicApi
public class PosixFilePermissionsDelegateContentDescriptor implements ContentDescriptor, Externalizable {
	private static final long serialVersionUID = 1L;

	private ContentDescriptor contents;
	private NavigableSet<PosixFilePermission> permissions;

	/**
	 * For {@link Externalizable}.
	 */
	public PosixFilePermissionsDelegateContentDescriptor() {
	}

	/**
	 * Creates a new instance.
	 * 
	 * @param contents
	 *            The delegate content descriptor.
	 * @param permissions
	 *            The posix file permissions.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public PosixFilePermissionsDelegateContentDescriptor(ContentDescriptor contents,
			Set<PosixFilePermission> permissions) throws NullPointerException {
		Objects.requireNonNull(contents, "contents");
		Objects.requireNonNull(permissions, "permissions");
		this.contents = contents;
		//XXX should use immutable enum sets or something
		this.permissions = ImmutableUtils.makeImmutableNavigableSet(permissions);
	}

	/**
	 * Gets a content descriptor that reflects the argument posix file permissions.
	 * <p>
	 * If the specified permissions set is <code>null</code>, then this method will just return the argument content
	 * descriptor without creating a new {@link PosixFilePermissionsDelegateContentDescriptor} instance.
	 * 
	 * @param contents
	 *            The delegate content descriptor.
	 * @param permissions
	 *            The posix file permissions.
	 * @return A content descriptor that reflects the argument posix file permissions.
	 * @throws NullPointerException
	 *             If the delegate content descriptor is <code>null</code>.
	 */
	public static ContentDescriptor get(ContentDescriptor contents, Set<PosixFilePermission> permissions)
			throws NullPointerException {
		Objects.requireNonNull(contents, "contents");
		if (permissions == null) {
			return contents;
		}
		return new PosixFilePermissionsDelegateContentDescriptor(contents, permissions);
	}

	/**
	 * Gets the delegate content desctiptor.
	 * 
	 * @return The contents.
	 */
	public ContentDescriptor getContents() {
		return contents;
	}

	/**
	 * Gets the associated posix file permissions.
	 * 
	 * @return An unmodifiable set of posix file permissions.
	 */
	public Set<PosixFilePermission> getPosixFilePermissions() {
		return permissions;
	}

	@Override
	public boolean isChanged(ContentDescriptor previouscontent) {
		if (!(previouscontent instanceof PosixFilePermissionsDelegateContentDescriptor)) {
			return true;
		}
		PosixFilePermissionsDelegateContentDescriptor c = (PosixFilePermissionsDelegateContentDescriptor) previouscontent;
		if (this.contents.isChanged(c.contents)) {
			return true;
		}
		if (!this.permissions.equals(c.permissions)) {
			return true;
		}
		return false;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(contents);
		SerialUtils.writeExternalCollection(out, permissions);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		contents = SerialUtils.readExternalObject(in);
		permissions = SerialUtils.readExternalSortedImmutableNavigableSet(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((contents == null) ? 0 : contents.hashCode());
		result = prime * result + ((permissions == null) ? 0 : permissions.hashCode());
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
		PosixFilePermissionsDelegateContentDescriptor other = (PosixFilePermissionsDelegateContentDescriptor) obj;
		if (contents == null) {
			if (other.contents != null)
				return false;
		} else if (!contents.equals(other.contents))
			return false;
		if (permissions == null) {
			if (other.permissions != null)
				return false;
		} else if (!permissions.equals(other.permissions))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (contents != null ? "contents=" + contents + ", " : "")
				+ (permissions != null ? "permissions=" + permissions : "") + "]";
	}

}
