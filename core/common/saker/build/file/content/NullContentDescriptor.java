package saker.build.file.content;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.apiextract.api.PublicApi;
import saker.build.thirdparty.saker.util.ObjectUtils;

/**
 * Content descriptor class which represents missing contents.
 * <p>
 * It is not recommended to use this class under normal circumstances.
 * <p>
 * The {@link #isChanged(ContentDescriptor)} method will always return <code>true</code>.
 * 
 * @see #getInstance()
 */
@PublicApi
public final class NullContentDescriptor implements ContentDescriptor, Externalizable {
	private static final long serialVersionUID = 1L;

	private static final NullContentDescriptor INSTANCE = new NullContentDescriptor();

	/**
	 * Creates a new instance.
	 */
	public NullContentDescriptor() {
	}

	/**
	 * Gets a singleton instance of a {@link NullContentDescriptor}.
	 * 
	 * @return The singleton instance.
	 */
	public static NullContentDescriptor getInstance() {
		return INSTANCE;
	}

	@Override
	public boolean isChanged(ContentDescriptor ref) {
		return true;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return ObjectUtils.isSameClass(this, obj);
	}
}