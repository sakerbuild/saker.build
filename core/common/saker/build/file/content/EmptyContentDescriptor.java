package saker.build.file.content;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.apiextract.api.PublicApi;
import saker.build.thirdparty.saker.util.ObjectUtils;

/**
 * Content descriptor class representing empty contents.
 * 
 * @see #INSTANCE
 */
@PublicApi
public final class EmptyContentDescriptor implements ContentDescriptor, Externalizable {
	private static final long serialVersionUID = 1L;

	//this content descriptor is not an enum so that it has a stable hash code

	/**
	 * The singleton instance.
	 */
	public static final ContentDescriptor INSTANCE = new EmptyContentDescriptor();

	/**
	 * For {@link Externalizable}.
	 * 
	 * @see #INSTANCE
	 */
	public EmptyContentDescriptor() {
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
		return "EmptyContentDescriptor";
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	}
}
