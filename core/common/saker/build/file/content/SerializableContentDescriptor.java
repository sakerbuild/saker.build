package saker.build.file.content;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.apiextract.api.PublicApi;

/**
 * Content descriptor that is compared by equality and is backed by a custom serializable object.
 * <p>
 * The underlying object should be serializable in order for proper build system operation. It is strongly recommended
 * to be {@link Externalizable} as well.
 */
@PublicApi
public class SerializableContentDescriptor implements ContentDescriptor, Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * The object that this content descriptor is backed by.
	 */
	protected Object object;

	/**
	 * For {@link Externalizable}.
	 */
	public SerializableContentDescriptor() {
	}

	/**
	 * Creates a new instance with the given object.
	 * 
	 * @param object
	 *            The object.
	 */
	public SerializableContentDescriptor(Object object) {
		this.object = object;
	}

	/**
	 * Gets the object that this content descriptor is backed by.
	 * 
	 * @return The object. (may be <code>null</code>)
	 */
	public Object getObject() {
		return object;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(object);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		object = in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((object == null) ? 0 : object.hashCode());
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
		SerializableContentDescriptor other = (SerializableContentDescriptor) obj;
		if (object == null) {
			if (other.object != null)
				return false;
		} else if (!object.equals(other.object))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + object + "]";
	}

}
