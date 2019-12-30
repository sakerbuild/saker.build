package saker.build.scripting.model;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

import saker.apiextract.api.PublicApi;

/**
 * Simple {@link ScriptToken} data class implementation.
 */
@PublicApi
public class SimpleScriptToken implements ScriptToken, Externalizable {
	private static final long serialVersionUID = 1L;

	private int offset;
	private int length;
	private String type;

	/**
	 * For {@link Externalizable}.
	 */
	public SimpleScriptToken() {
	}

	/**
	 * Constructs a new token with the given arguments.
	 * 
	 * @param offset
	 *            The offset.
	 * @param length
	 *            The length.
	 * @param type
	 *            The token type.
	 * @throws NullPointerException
	 *             If type is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If offset or length is negative.
	 */
	public SimpleScriptToken(int offset, int length, String type)
			throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(type, "type");
		if (offset < 0) {
			throw new IllegalArgumentException("Offset is negative.");
		}
		if (length < 0) {
			throw new IllegalArgumentException("Length is negative.");
		}
		this.offset = offset;
		this.type = type;
		this.length = length;
	}

	@Override
	public int getLength() {
		return length;
	}

	@Override
	public boolean isEmpty() {
		return length == 0;
	}

	@Override
	public int getOffset() {
		return offset;
	}

	@Override
	public String getType() {
		return type;
	}

	@Override
	public final int getEndOffset() {
		return offset + length;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(offset);
		out.writeInt(length);
		out.writeObject(type);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		offset = in.readInt();
		length = in.readInt();
		type = (String) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + length;
		result = prime * result + offset;
		result = prime * result + ((type == null) ? 0 : type.hashCode());
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
		SimpleScriptToken other = (SimpleScriptToken) obj;
		if (length != other.length)
			return false;
		if (offset != other.offset)
			return false;
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return this.getClass() + " [offset=" + offset + ", " + (type != null ? "type=" + type + ", " : "") + "length="
				+ length + "]";
	}
}