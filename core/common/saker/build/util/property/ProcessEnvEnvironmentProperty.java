/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package saker.build.util.property;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.SakerEnvironment;

/**
 * {@link EnvironmentProperty} implementation that queries the value of an {@linkplain System#getenv() environment
 * variable}.
 * 
 * @since 0.8.10
 */
public final class ProcessEnvEnvironmentProperty implements EnvironmentProperty<String>, Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * The name of the environment variable.
	 */
	protected String name;

	/**
	 * For {@link Externalizable}.
	 */
	public ProcessEnvEnvironmentProperty() {
	}

	/**
	 * Creates a new instance with the specified environment variable name.
	 * 
	 * @param name
	 *            The environment variable name.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public ProcessEnvEnvironmentProperty(String name) throws NullPointerException {
		Objects.requireNonNull(name, "name");
		this.name = name;
	}

	@Override
	public String getCurrentValue(SakerEnvironment environment) throws Exception {
		return System.getenv(name);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeUTF(name);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		name = in.readUTF();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
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
		ProcessEnvEnvironmentProperty other = (ProcessEnvEnvironmentProperty) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + name + "]";
	}
}
