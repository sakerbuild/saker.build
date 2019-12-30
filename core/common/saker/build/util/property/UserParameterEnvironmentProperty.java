package saker.build.util.property;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.SakerEnvironment;

/**
 * Environment property implementation for retrieving an environment user parameter for a given key.
 * 
 * @see SakerEnvironment#getUserParameters()
 */
public final class UserParameterEnvironmentProperty implements EnvironmentProperty<String>, Externalizable {
	private static final long serialVersionUID = 1L;

	private String parameter;

	/**
	 * For {@link Externalizable}.
	 */
	public UserParameterEnvironmentProperty() {
	}

	/**
	 * Creates a new instance for the given parameter.
	 * 
	 * @param parameter
	 *            The key to retrieve the user value for.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public UserParameterEnvironmentProperty(String parameter) throws NullPointerException {
		Objects.requireNonNull(parameter, "parameter");
		this.parameter = parameter;
	}

	@Override
	public String getCurrentValue(SakerEnvironment environment) {
		return environment.getUserParameters().get(parameter);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeUTF(parameter);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		parameter = in.readUTF();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((parameter == null) ? 0 : parameter.hashCode());
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
		UserParameterEnvironmentProperty other = (UserParameterEnvironmentProperty) obj;
		if (parameter == null) {
			if (other.parameter != null)
				return false;
		} else if (!parameter.equals(other.parameter))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + parameter + "]";
	}

}
