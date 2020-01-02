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
import java.util.Objects;
import java.util.Set;

import saker.apiextract.api.PublicApi;
import saker.build.thirdparty.saker.util.ImmutableUtils;

/**
 * {@link FormattedTextContent} implementation holding a single formatted text content.
 */
@PublicApi
public class SingleFormattedTextContent implements FormattedTextContent, Externalizable {
	private static final long serialVersionUID = 1L;

	private String text;
	private String format;

	/**
	 * For {@link Externalizable}.
	 */
	public SingleFormattedTextContent() {
	}

	/**
	 * Creates a new instance with the given contents.
	 * 
	 * @param format
	 *            The format identifier of the content.
	 * @param text
	 *            The text content.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public SingleFormattedTextContent(String format, String text) throws NullPointerException {
		Objects.requireNonNull(format, "format");
		Objects.requireNonNull(text, "text");
		this.format = format;
		this.text = text;
	}

	/**
	 * Gets the text used to construct this instance.
	 * 
	 * @return The text.
	 */
	public String getText() {
		return text;
	}

	/**
	 * Gets the format used to construct this instance.
	 * 
	 * @return The format identifier.
	 */
	public String getFormat() {
		return format;
	}

	@Override
	public String getFormattedText(String format) {
		if (!this.format.equals(format)) {
			throw new IllegalArgumentException("Invalid format: " + format + " expected: " + this.format);
		}
		return text;
	}

	@Override
	public Set<String> getAvailableFormats() {
		return ImmutableUtils.singletonSet(format);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeUTF(text);
		out.writeUTF(format);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		text = in.readUTF();
		format = in.readUTF();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((format == null) ? 0 : format.hashCode());
		result = prime * result + ((text == null) ? 0 : text.hashCode());
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
		SingleFormattedTextContent other = (SingleFormattedTextContent) obj;
		if (format == null) {
			if (other.format != null)
				return false;
		} else if (!format.equals(other.format))
			return false;
		if (text == null) {
			if (other.text != null)
				return false;
		} else if (!text.equals(other.text))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[text=" + text + ", format=" + format + "]";
	}

}
