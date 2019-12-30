package saker.build.util.property;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.SakerEnvironment;

/**
 * {@link EnvironmentProperty} implementation that queries the value of a {@linkplain System#getProperties() system
 * property}.
 */
public final class SystemPropertyEnvironmentProperty implements EnvironmentProperty<String>, Externalizable {
	private static final long serialVersionUID = 1L;

	protected String propertyName;

	/**
	 * For {@link Externalizable}.
	 */
	public SystemPropertyEnvironmentProperty() {
	}

	/**
	 * Creates a new instance with the specified property name.
	 * 
	 * @param propertyName
	 *            The property name.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public SystemPropertyEnvironmentProperty(String propertyName) throws NullPointerException {
		Objects.requireNonNull(propertyName, "property name");
		this.propertyName = propertyName;
	}

	@Override
	public String getCurrentValue(SakerEnvironment environment) {
		return System.getProperty(propertyName);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeUTF(propertyName);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		propertyName = in.readUTF();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((propertyName == null) ? 0 : propertyName.hashCode());
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
		SystemPropertyEnvironmentProperty other = (SystemPropertyEnvironmentProperty) obj;
		if (propertyName == null) {
			if (other.propertyName != null)
				return false;
		} else if (!propertyName.equals(other.propertyName))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + propertyName + "]";
	}

}
