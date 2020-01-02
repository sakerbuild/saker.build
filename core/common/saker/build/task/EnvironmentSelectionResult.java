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
package saker.build.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;
import java.util.Objects;

import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * Class representing the result of an execution environment selection.
 * <p>
 * This class consits of a map of property-value pairs which were used to determine that a given execution environment
 * is suitable for invocation of an associated task. These properties are called qualifier properties. In general, if
 * any of the qualifier properties change between executions, the associated tasks can expect their reinvocation.
 * 
 * @see TaskExecutionEnvironmentSelector#isSuitableExecutionEnvironment(SakerEnvironment)
 */
public final class EnvironmentSelectionResult implements Externalizable {
	private static final long serialVersionUID = 1L;

	private Map<? extends EnvironmentProperty<?>, ?> qualifierProperties;

	/**
	 * For {@link Externalizable}.
	 */
	public EnvironmentSelectionResult() {
	}

	/**
	 * Creates a new instance with the specified qualifier properties.
	 * 
	 * @param qualifierProperties
	 *            The qualifier properties mapped to their expected values.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public EnvironmentSelectionResult(Map<? extends EnvironmentProperty<?>, ?> qualifierProperties)
			throws NullPointerException {
		Objects.requireNonNull(qualifierProperties, "qualifier properties");
		this.qualifierProperties = ImmutableUtils.makeImmutableHashMap(qualifierProperties);
	}

	/**
	 * Gets the qualifier properties of this selection result mapped to their expected values.
	 * 
	 * @return The qualifier properties.
	 */
	public Map<? extends EnvironmentProperty<?>, ?> getQualifierEnvironmentProperties() {
		return qualifierProperties;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalMap(out, qualifierProperties);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		qualifierProperties = SerialUtils.readExternalImmutableHashMap(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + qualifierProperties.hashCode();
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
		EnvironmentSelectionResult other = (EnvironmentSelectionResult) obj;
		if (qualifierProperties == null) {
			if (other.qualifierProperties != null)
				return false;
		} else if (!qualifierProperties.equals(other.qualifierProperties))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + qualifierProperties + "]";
	}

}