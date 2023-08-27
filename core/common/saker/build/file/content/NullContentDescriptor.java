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
package saker.build.file.content;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.apiextract.api.PublicApi;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.util.data.annotation.ValueType;

/**
 * Content descriptor class which represents missing contents.
 * <p>
 * It is not recommended to use this class under normal circumstances.
 * <p>
 * The {@link #isChanged(ContentDescriptor)} method will always return <code>true</code>.
 * 
 * @see #getInstance()
 */
@PublicApi
@ValueType
public final class NullContentDescriptor implements ContentDescriptor, Externalizable {
	private static final long serialVersionUID = 1L;

	private static final NullContentDescriptor INSTANCE = new NullContentDescriptor();

	/**
	 * Creates a new instance.
	 */
	public NullContentDescriptor() {
	}

	/**
	 * Gets a singleton instance of a {@link NullContentDescriptor}.
	 * 
	 * @return The singleton instance.
	 */
	public static NullContentDescriptor getInstance() {
		return INSTANCE;
	}

	@Override
	public boolean isChanged(ContentDescriptor ref) {
		return true;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return ObjectUtils.isSameClass(this, obj);
	}
}