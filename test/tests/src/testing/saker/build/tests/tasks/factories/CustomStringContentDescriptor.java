package testing.saker.build.tests.tasks.factories;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.file.content.ContentDescriptor;

public class CustomStringContentDescriptor implements ContentDescriptor, Externalizable {
	private static final long serialVersionUID = 1L;

	protected String value;

	public CustomStringContentDescriptor() {
	}

	public CustomStringContentDescriptor(String value) {
		this.value = value;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(value);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		value = (String) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((value == null) ? 0 : value.hashCode());
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
		CustomStringContentDescriptor other = (CustomStringContentDescriptor) obj;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (value != null ? "value=" + value : "") + "]";
	}

}
