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
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.SortedMap;

import saker.apiextract.api.PublicApi;
import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * {@link ContentDescriptor} implementation that contains child content descriptors mapped to their associated paths.
 * <p>
 * The content descriptor consists of a {@link Map} that contains entries with path keys corresponding to associated
 * content descriptors. When changes are detected between two descriptors, each path-descriptor pair is checked against
 * the argument descriptor with the same associated path.
 */
@PublicApi
public class MultiPathContentDescriptor implements ContentDescriptor, Externalizable {
	private static final long serialVersionUID = 1L;

	private NavigableMap<SakerPath, ContentDescriptor> children;

	/**
	 * For {@link Externalizable}.
	 * <p>
	 * If not used for externalizing, this constructor creates a new instance without any child descriptors.
	 */
	public MultiPathContentDescriptor() {
		this.children = Collections.emptyNavigableMap();
	}

	/**
	 * Creates a new instance with the given path-descriptor pairs.
	 * <p>
	 * The constructor makes a copy of the argument map.
	 * 
	 * @param children
	 *            The paths and descriptors.
	 * @throws NullPointerException
	 *             If the argument or any paths or descriptors are <code>null</code>.
	 */
	public MultiPathContentDescriptor(Map<SakerPath, ? extends ContentDescriptor> children)
			throws NullPointerException {
		//XXX maybe do the validation during the iteration over the entries in constructor maps? this and other constructors
		ObjectUtils.requireNonNullEntryKeyValues(children);
		this.children = ImmutableUtils.makeImmutableNavigableMap(children);
	}

	/**
	 * Creates a new instance with the given path-descriptor pairs.
	 * <p>
	 * <p>
	 * The constructor makes a copy of the argument map. For faster construction, it is recommended that the argument is
	 * ordered by natural order.
	 * 
	 * @param children
	 *            The paths and descriptors.
	 * @throws NullPointerException
	 *             If the argument or any paths or descriptors are <code>null</code>.
	 */
	public MultiPathContentDescriptor(SortedMap<SakerPath, ? extends ContentDescriptor> children)
			throws NullPointerException {
		ObjectUtils.requireNonNullEntryKeyValues(children);
		if (ObjectUtils.isNaturalOrder(children.comparator())) {
			this.children = ImmutableUtils.makeImmutableNavigableMap(children);
		} else {
			//do not keep comparator, cast to map
			this.children = ImmutableUtils
					.makeImmutableNavigableMap((Map<SakerPath, ? extends ContentDescriptor>) children);
		}
	}

	/**
	 * Gets the contents contained in this content descriptor mapped to their respective paths.
	 * 
	 * @return The unmodifiable map of the contents.
	 */
	public NavigableMap<SakerPath, ContentDescriptor> getContents() {
		return children;
	}

	@Override
	public boolean isChanged(ContentDescriptor content) {
		if (!(content instanceof MultiPathContentDescriptor)) {
			return true;
		}
		MultiPathContentDescriptor other = (MultiPathContentDescriptor) content;
		return isChangedCollection(this.children, other.children);
	}

	private static boolean isChangedCollection(NavigableMap<SakerPath, ? extends ContentDescriptor> base,
			NavigableMap<SakerPath, ? extends ContentDescriptor> content) {
		if (base.size() != content.size()) {
			return true;
		}
		return ObjectUtils.compareOrderedMaps(base, content, (l, r) -> {
			boolean changed = l.isChanged(r);
			return changed ? 1 : 0;
		}) != 0;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalMap(out, children);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		children = SerialUtils.readExternalSortedImmutableNavigableMap(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((children == null) ? 0 : children.hashCode());
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
		MultiPathContentDescriptor other = (MultiPathContentDescriptor) obj;
		if (!ObjectUtils.mapOrderedEquals(this.children, other.children)) {
			return false;
		}
		return true;
	}
}
