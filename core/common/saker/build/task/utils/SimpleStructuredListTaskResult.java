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
package saker.build.task.utils;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import saker.apiextract.api.PublicApi;
import saker.build.task.TaskResultResolver;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * Simple {@link StructuredListTaskResult} implementation that is backed by a list of elements.
 */
@PublicApi
public class SimpleStructuredListTaskResult implements StructuredListTaskResult, Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * The immutable list of elements.
	 */
	protected List<? extends StructuredTaskResult> elementTaskIds;

	/**
	 * For {@link Externalizable}.
	 */
	public SimpleStructuredListTaskResult() {
	}

	/**
	 * Creates a new instance with the specified elementy.
	 * <p>
	 * If the argument is <code>null</code>, an empty list is used.
	 * 
	 * @param elementTaskIds
	 *            The elements to initialize this map with.
	 */
	public SimpleStructuredListTaskResult(List<? extends StructuredTaskResult> elementTaskIds) {
		if (elementTaskIds == null) {
			this.elementTaskIds = Collections.emptyList();
		} else {
			this.elementTaskIds = ImmutableUtils.makeImmutableList(elementTaskIds);
		}
	}

	@Override
	public int size() {
		return elementTaskIds.size();
	}

	@Override
	public StructuredTaskResult getResult(int index) {
		return elementTaskIds.get(index);
	}

	@Override
	public Iterator<? extends StructuredTaskResult> resultIterator() {
		return elementTaskIds.iterator();
	}

	@Override
	public void forEach(Consumer<? super StructuredTaskResult> consumer) {
		elementTaskIds.forEach(consumer);
	}

	@Override
	public List<?> toResult(TaskResultResolver results) {
		if (elementTaskIds.isEmpty()) {
			return Collections.emptyList();
		}
		Object[] elements = new Object[elementTaskIds.size()];
		int i = 0;
		for (StructuredTaskResult tfid : elementTaskIds) {
			elements[i++] = tfid.toResult(results);
		}
		return ImmutableUtils.asUnmodifiableArrayList(elements);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, elementTaskIds);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		elementTaskIds = SerialUtils.readExternalImmutableList(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((elementTaskIds == null) ? 0 : elementTaskIds.hashCode());
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
		SimpleStructuredListTaskResult other = (SimpleStructuredListTaskResult) obj;
		if (elementTaskIds == null) {
			if (other.elementTaskIds != null)
				return false;
		} else if (!elementTaskIds.equals(other.elementTaskIds))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + elementTaskIds + "]";
	}

}
