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
import saker.build.file.SakerDirectory;
import saker.build.thirdparty.saker.util.ObjectUtils;

/**
 * Content descriptor class representing a directory.
 * <p>
 * This content descriptor is associated with directories in the file system. All {@link SakerDirectory} instances are
 * associated with it, and directories in the file system will have this content descriptor bound to them.
 * <p>
 * <b>Note:</b> It is strongly discouraged to use this content descriptor with non-directories. The build system may
 * rely on the fact that if a file has this content descriptor, then it is a directory. Associating non-directories with
 * this content descriptor may break some functionality without explicit notice.
 * 
 * @see #INSTANCE
 */
@PublicApi
public final class DirectoryContentDescriptor implements ContentDescriptor, Externalizable {
	private static final long serialVersionUID = 1L;

	//this content descriptor is not an enum so that it has a stable hash code

	/**
	 * The singleton instance.
	 */
	public static final ContentDescriptor INSTANCE = new DirectoryContentDescriptor();

	/**
	 * For {@link Externalizable}.
	 * 
	 * @see #INSTANCE
	 */
	public DirectoryContentDescriptor() {
	}

	@Override
	public int hashCode() {
		return getClass().getName().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return ObjectUtils.isSameClass(this, obj);
	}

	@Override
	public String toString() {
		return "DirectoryContentDescriptor";
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	}
}
