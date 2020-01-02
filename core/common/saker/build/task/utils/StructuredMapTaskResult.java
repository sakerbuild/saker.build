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

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;

import saker.apiextract.api.PublicApi;
import saker.build.task.TaskResultResolver;
import saker.build.thirdparty.saker.util.ObjectUtils;

/**
 * {@link StructuredTaskResult} interface for a map.
 * <p>
 * The interface contains the results mapped to {@link String} keys. The keys can contain <code>null</code>.
 * 
 * @see SimpleStructuredMapTaskResult
 */
@PublicApi
public interface StructuredMapTaskResult extends StructuredTaskResult {
	/**
	 * Gets the size of this map result.
	 * <p>
	 * The size is constant during the lifetime of this object.
	 * 
	 * @return The size.
	 */
	public default int size() {
		return ObjectUtils.sizeOfIterator(taskIterator());
	}

	/**
	 * Gets an iterator for the entries in this structured map result.
	 * 
	 * @return The iterator.
	 */
	public Iterator<? extends Map.Entry<String, ? extends StructuredTaskResult>> taskIterator();

	/**
	 * Gets the task result for a given key.
	 * 
	 * @param key
	 *            The map key. May be <code>null</code>.
	 * @return The task result, or <code>null</code> if not found.
	 */
	public default StructuredTaskResult getTask(String key) {
		Iterator<? extends Entry<String, ? extends StructuredTaskResult>> it = taskIterator();
		if (key == null) {
			while (it.hasNext()) {
				Entry<String, ? extends StructuredTaskResult> entry = it.next();
				if (entry.getKey() == null) {
					return entry.getValue();
				}
			}
		} else {
			while (it.hasNext()) {
				Entry<String, ? extends StructuredTaskResult> entry = it.next();
				if (key.equals(entry.getKey())) {
					return entry.getValue();
				}
			}
		}
		return null;
	}

	/**
	 * Performs an action for each entry in this map result.
	 * 
	 * @param consumer
	 *            The action.
	 * @throws NullPointerException
	 *             If the action is <code>null</code>.
	 */
	public default void forEach(BiConsumer<? super String, ? super StructuredTaskResult> consumer)
			throws NullPointerException {
		Iterator<? extends Entry<String, ? extends StructuredTaskResult>> it = taskIterator();
		while (it.hasNext()) {
			Entry<String, ? extends StructuredTaskResult> entry = it.next();
			consumer.accept(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public Map<String, ?> toResult(TaskResultResolver results);
}
