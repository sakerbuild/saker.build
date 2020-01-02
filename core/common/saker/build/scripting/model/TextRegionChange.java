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
package saker.build.scripting.model;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.apiextract.api.PublicApi;
import saker.build.thirdparty.saker.util.ObjectUtils;

/**
 * Represents a change in a file document at a given region.
 * <p>
 * A text region change consists of an offset and length which specifies what characters were modified, and a
 * {@link String} text that represents what was inserted in the place of the modification. Clients can think of this as
 * the offset and length defines the region of selection, and the text defines the replacement for the selected region.
 * <p>
 * The length for the region might be zero, in which case the change represents a direct insertion.
 * <p>
 * The text might be empty (<code>""</code>), in which case the change represents text deletion.
 * <p>
 * The class works on decoded character data, and not on the bytes themselves.
 */
@PublicApi
public class TextRegionChange implements Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * The region offset in the file.
	 */
	protected int offset;
	/**
	 * The length of the region.
	 * <p>
	 * Might be 0.
	 */
	protected int length;
	/**
	 * The inserted text.
	 * <p>
	 * Might be empty (<code>""</code>).
	 */
	protected String text;

	/**
	 * For {@link Externalizable}.
	 */
	public TextRegionChange() {
	}

	/**
	 * Creates a new instance with the given arguments.
	 * 
	 * @param offset
	 *            The file offset for the change.
	 * @param length
	 *            The region length for the change.
	 * @param text
	 *            The inserted text or <code>null</code> or empty (<code>""</code>) in case of deletion.
	 * @throws IllegalArgumentException
	 *             If offset or length is negative.
	 */
	public TextRegionChange(int offset, int length, String text) throws IllegalArgumentException {
		if (length < 0) {
			throw new IllegalArgumentException("length is negative: " + length);
		}
		if (offset < 0) {
			throw new IllegalArgumentException("offset is negative: " + length);
		}
		this.offset = offset;
		this.length = length;
		//normalize null to empty
		this.text = ObjectUtils.isNullOrEmpty(text) ? "" : text;
	}

	/**
	 * Gets the offset of the modification.
	 * 
	 * @return The offset.
	 */
	public int getOffset() {
		return offset;
	}

	/**
	 * Gets the length of the modified region.
	 * 
	 * @return The region length.
	 */
	public int getLength() {
		return length;
	}

	/**
	 * Gets the inserted text.
	 * 
	 * @return The inserted text. Empty (<code>""</code>) if this change represents deletion.
	 */
	public String getText() {
		return text;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + offset + ", (" + length + "): " + text + "]";
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(offset);
		out.writeInt(length);
		out.writeUTF(text);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		offset = in.readInt();
		length = in.readInt();
		text = in.readUTF();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + length;
		result = prime * result + offset;
		result = prime * result + text.hashCode();
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
		TextRegionChange other = (TextRegionChange) obj;
		if (length != other.length)
			return false;
		if (offset != other.offset)
			return false;
		if (!text.equals(other.text))
			return false;
		return true;
	}

}