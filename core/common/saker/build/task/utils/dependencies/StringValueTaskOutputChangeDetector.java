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
package saker.build.task.utils.dependencies;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

import saker.build.task.dependencies.TaskOutputChangeDetector;
import saker.build.util.data.annotation.ValueType;

/**
 * {@link TaskOutputChangeDetector} that compares the string representation ({@link Object#toString()}) of the task
 * output with an expected value.
 * 
 * @since saker.build 0.8.21
 */
@ValueType
public final class StringValueTaskOutputChangeDetector implements TaskOutputChangeDetector, Externalizable {
	private static final long serialVersionUID = 1L;

	private String expected;

	/**
	 * For {@link Externalizable}.
	 */
	public StringValueTaskOutputChangeDetector() {
	}

	/**
	 * Creates a new instance with the given expected string representation.
	 * 
	 * @param expected
	 *            The expected string representation. (May be <code>null</code>)
	 */
	public StringValueTaskOutputChangeDetector(String expected) {
		this.expected = expected;
	}

	@Override
	public boolean isChanged(Object taskoutput) {
		return !Objects.equals(expected, Objects.toString(taskoutput, null));
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(expected);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		expected = (String) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((expected == null) ? 0 : expected.hashCode());
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
		StringValueTaskOutputChangeDetector other = (StringValueTaskOutputChangeDetector) obj;
		if (expected == null) {
			if (other.expected != null)
				return false;
		} else if (!expected.equals(other.expected))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + expected + "]";
	}
}