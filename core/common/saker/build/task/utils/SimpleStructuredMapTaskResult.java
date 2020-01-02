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
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.BiConsumer;

import saker.apiextract.api.PublicApi;
import saker.build.task.TaskResultResolver;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * Simple {@link StructuredMapTaskResult} implementation that is backed by a map.
 */
@PublicApi
public class SimpleStructuredMapTaskResult implements StructuredMapTaskResult, Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * The immutable map of items mapped to their names.
	 */
	protected NavigableMap<String, ? extends StructuredTaskResult> itemTaskIds;

	/**
	 * For {@link Externalizable}.
	 */
	public SimpleStructuredMapTaskResult() {
	}

	/**
	 * Creates a new instance with the specified items.
	 * <p>
	 * If the argument is <code>null</code>, an empty map is used.
	 * 
	 * @param itemTaskIds
	 *            The items to initialize this map with.
	 */
	public SimpleStructuredMapTaskResult(NavigableMap<String, ? extends StructuredTaskResult> itemTaskIds) {
		if (itemTaskIds == null) {
			this.itemTaskIds = Collections.emptyNavigableMap();
		} else {
			this.itemTaskIds = ImmutableUtils.makeImmutableNavigableMap(itemTaskIds);
		}
	}

	@Override
	public int size() {
		return itemTaskIds.size();
	}

	@Override
	public Iterator<? extends Entry<String, ? extends StructuredTaskResult>> taskIterator() {
		return itemTaskIds.entrySet().iterator();
	}

	@Override
	public StructuredTaskResult getTask(String key) {
		return itemTaskIds.get(key);
	}

	@Override
	public void forEach(BiConsumer<? super String, ? super StructuredTaskResult> consumer) {
		itemTaskIds.forEach(consumer);
	}

	@Override
	public Map<String, ?> toResult(TaskResultResolver results) {
		//XXX could use transforming navigable map to convert faster
		NavigableMap<String, Object> result = new TreeMap<>(StringUtils.nullsFirstStringComparator());
		for (Entry<String, ? extends StructuredTaskResult> entry : itemTaskIds.entrySet()) {
			Object valobj = entry.getValue().toResult(results);
			String keystr = entry.getKey();
			result.put(keystr, valobj);
		}
		return ImmutableUtils.unmodifiableNavigableMap(result);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalMap(out, itemTaskIds);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		itemTaskIds = SerialUtils.readExternalMap(new TreeMap<>(StringUtils.nullsFirstStringComparator()), in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((itemTaskIds == null) ? 0 : itemTaskIds.hashCode());
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
		SimpleStructuredMapTaskResult other = (SimpleStructuredMapTaskResult) obj;
		if (itemTaskIds == null) {
			if (other.itemTaskIds != null)
				return false;
		} else if (!itemTaskIds.equals(other.itemTaskIds))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + itemTaskIds + "]";
	}

}
