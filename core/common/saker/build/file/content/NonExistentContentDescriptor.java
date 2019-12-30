package saker.build.file.content;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.apiextract.api.PublicApi;
import saker.build.thirdparty.saker.util.ObjectUtils;

/**
 * Content descriptor representing non-existent data.
 * <p>
 * The {@link #isChanged(ContentDescriptor)} method will return <code>false</code> if the given parameter is
 * <code>null</code>, or equals {@link #INSTANCE}.
 */
@PublicApi
public final class NonExistentContentDescriptor implements ContentDescriptor, Externalizable {
	private static final long serialVersionUID = 1L;

	//this content descriptor is not an enum so that it has a stable hash code

	/**
	 * The singleton instance.
	 */
	public static final ContentDescriptor INSTANCE = new NonExistentContentDescriptor();

	/**
	 * For {@link Externalizable}.
	 * 
	 * @see #INSTANCE
	 */
	public NonExistentContentDescriptor() {
	}

	@Override
	public int hashCode() {
		return getClass().getName().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return ObjectUtils.isSameClass(this, obj);
	}

	@Override
	public String toString() {
		return "NonExistentContentDescriptor";
	}

	@Override
	public boolean isChanged(ContentDescriptor ref) {
		if (ref == null) {
			return false;
		}
		return !this.equals(ref);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	}
}
