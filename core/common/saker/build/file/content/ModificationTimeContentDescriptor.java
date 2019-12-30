package saker.build.file.content;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Date;

public class ModificationTimeContentDescriptor implements ContentDescriptor, Externalizable {
	private static final long serialVersionUID = 1L;

	private long millis;

	/**
	 * For {@link Externalizable}.
	 */
	public ModificationTimeContentDescriptor() {
	}

	public ModificationTimeContentDescriptor(long millis) {
		this.millis = millis;
	}

	public long getMillis() {
		return millis;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeLong(millis);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException {
		millis = in.readLong();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (millis ^ (millis >>> 32));
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
		ModificationTimeContentDescriptor other = (ModificationTimeContentDescriptor) obj;
		if (millis != other.millis)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[millis=" + millis + ", " + new Date(millis) + "]";
	}

}
