package saker.build.file.content;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.UUID;

import saker.apiextract.api.PublicApi;

/**
 * Content descriptor backed by an unique identifier.
 * <p>
 * The implementation is backed by {@link UUID}.
 */
@PublicApi
public final class UUIDContentDescriptor implements ContentDescriptor, Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * The {@link UUID} of the content descriptor.
	 */
	protected UUID uuid;

	/**
	 * For {@link Externalizable}.
	 */
	public UUIDContentDescriptor() {
	}

	private UUIDContentDescriptor(UUID uuid) {
		this.uuid = uuid;
	}

	/**
	 * Creates a new content descriptor backed by a random {@link UUID}.
	 * 
	 * @return The created content descriptor.
	 * @see UUID#randomUUID()
	 */
	public static UUIDContentDescriptor random() {
		return new UUIDContentDescriptor(UUID.randomUUID());
	}

	/**
	 * Creates a new content descriptor with the given {@link UUID}.
	 * 
	 * @param uuid
	 *            The unique identifier.
	 * @return The created content descriptor.
	 */
	public static UUIDContentDescriptor valueOf(UUID uuid) {
		return new UUIDContentDescriptor(uuid);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(uuid);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		uuid = (UUID) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((uuid == null) ? 0 : uuid.hashCode());
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
		UUIDContentDescriptor other = (UUIDContentDescriptor) obj;
		if (uuid == null) {
			if (other.uuid != null)
				return false;
		} else if (!uuid.equals(other.uuid))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + uuid + "]";
	}

}
