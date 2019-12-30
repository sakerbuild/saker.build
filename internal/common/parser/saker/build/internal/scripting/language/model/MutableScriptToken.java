package saker.build.internal.scripting.language.model;

import java.util.Objects;

import saker.build.scripting.model.ScriptToken;

public class MutableScriptToken implements ScriptToken {
	private int offset;
	private int length;
	private String type;

	public MutableScriptToken() {
	}

	public MutableScriptToken(int offset, int length, String type) {
		Objects.requireNonNull(type, "type");
		this.offset = offset;
		this.type = type;
		this.length = length;
	}

	@Override
	public String toString() {
		return this.getClass() + " [offset=" + offset + ", " + (type != null ? "type=" + type + ", " : "") + "length="
				+ length + "]";
	}

	@Override
	public final int getLength() {
		return length;
	}

	@Override
	public final boolean isEmpty() {
		return length == 0;
	}

	@Override
	public final int getOffset() {
		return offset;
	}

	@Override
	public final String getType() {
		return type;
	}

	@Override
	public final int getEndOffset() {
		return offset + length;
	}

	public final void setLength(int length) {
		this.length = length;
	}

	public final void setOffset(int offset) {
		this.offset = offset;
	}

	public final void setType(String type) {
		this.type = type;
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
		MutableScriptToken other = (MutableScriptToken) obj;
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

}