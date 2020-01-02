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
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import saker.apiextract.api.PublicApi;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * {@link FormattedTextContent} implementation holding multiple formats and their corresponding text contents.
 */
@PublicApi
public class MultiFormattedTextContent implements FormattedTextContent, Externalizable {
	private static final long serialVersionUID = 1L;

	private NavigableMap<String, String> formatTexts;

	/**
	 * For {@link Externalizable}.
	 */
	public MultiFormattedTextContent() {
	}

	private MultiFormattedTextContent(NavigableMap<String, String> formatTexts) {
		this.formatTexts = formatTexts;
	}

	/**
	 * Creates a new instance with the specified formats.
	 * 
	 * @param formatTexts
	 *            The format identifiers mapped to their textual contents.
	 * @return The created formatted text content.
	 * @throws IllegalArgumentException
	 *             If the argument map is empty.
	 * @throws NullPointerException
	 *             If the argument or any of its element is <code>null</code>.
	 */
	public static FormattedTextContent create(Map<String, String> formatTexts)
			throws IllegalArgumentException, NullPointerException {
		Objects.requireNonNull(formatTexts, "format texts");
		if (formatTexts.isEmpty()) {
			throw new IllegalArgumentException("Empty formatted texts.");
		}
		Iterator<Entry<String, String>> it = formatTexts.entrySet().iterator();
		Entry<String, String> entry = it.next();
		if (!it.hasNext()) {
			String key = entry.getKey();
			String value = entry.getValue();
			Objects.requireNonNull(key, "a format is null");
			Objects.requireNonNull(value, "text is null for format");
			return new SingleFormattedTextContent(key, value);
		}
		NavigableMap<String, String> map = new TreeMap<>();
		while (true) {
			String key = entry.getKey();
			String value = entry.getValue();

			Objects.requireNonNull(key, "a format is null");
			Objects.requireNonNull(value, "text is null for format");

			map.put(key, value);
			if (!it.hasNext()) {
				break;
			}
			entry = it.next();
		}
		return new MultiFormattedTextContent(ImmutableUtils.unmodifiableNavigableMap(map));
	}

	@Override
	public String getFormattedText(String format) throws IllegalArgumentException {
		String got = this.formatTexts.get(format);
		if (got == null) {
			throw new IllegalArgumentException("No text found for format: " + format);
		}
		return got;
	}

	@Override
	public Set<String> getAvailableFormats() {
		//field is unmodifiable
		return formatTexts.keySet();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalMap(out, formatTexts);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		formatTexts = SerialUtils.readExternalSortedImmutableNavigableMap(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((formatTexts == null) ? 0 : formatTexts.hashCode());
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
		MultiFormattedTextContent other = (MultiFormattedTextContent) obj;
		if (formatTexts == null) {
			if (other.formatTexts != null)
				return false;
		} else if (!formatTexts.equals(other.formatTexts))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[formatTexts=" + formatTexts + "]";
	}

}
