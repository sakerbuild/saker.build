package testing.saker.build.tests.tasks.script.customlang;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class CustomLangVersionKey implements Externalizable {
	private static final long serialVersionUID = 1L;

	private String version;

	/**
	 * For {@link Externalizable}.
	 */
	public CustomLangVersionKey() {
	}

	public CustomLangVersionKey(String version) {
		this.version = version;
	}

	@Override
	public String toString() {
		return version;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((version == null) ? 0 : version.hashCode());
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
		CustomLangVersionKey other = (CustomLangVersionKey) obj;
		if (version == null) {
			if (other.version != null)
				return false;
		} else if (!version.equals(other.version))
			return false;
		return true;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeUTF(version);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		version = in.readUTF();
	}

}
