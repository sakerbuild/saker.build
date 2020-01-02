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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import saker.apiextract.api.PublicApi;
import saker.build.task.TaskResultResolver;
import saker.build.thirdparty.saker.util.ImmutableUtils;

/**
 * {@link StructuredTaskResult} interface of an ordered collection.
 * <p>
 * Instances of this interface contains a fixed number of task results represented in an ordered manner.
 * 
 * @see SimpleStructuredListTaskResult
 */
@PublicApi
public interface StructuredListTaskResult extends StructuredTaskResult {
	/**
	 * Gets the size of this list result.
	 * <p>
	 * The size is constant during the lifetime of this object.
	 * 
	 * @return The size.
	 */
	public default int size() {
		int i = 0;
		Iterator<?> it = resultIterator();
		while (it.hasNext()) {
			it.next();
			++i;
		}
		return i;
	}

	/**
	 * Gets the result at the given index.
	 * 
	 * @param index
	 *            The index.
	 * @return The result at the given index.
	 * @throws IndexOutOfBoundsException
	 *             If the index is out of range.
	 */
	public default StructuredTaskResult getResult(int index) throws IndexOutOfBoundsException {
		if (index < 0) {
			throw new IndexOutOfBoundsException("index (" + index + ") < 0");
		}
		int i = 0;
		Iterator<? extends StructuredTaskResult> it = resultIterator();
		while (it.hasNext()) {
			StructuredTaskResult next = it.next();
			if (i == index) {
				return next;
			}
			++i;
		}
		throw new IndexOutOfBoundsException(index + " for size: " + i);
	}

	/**
	 * Gets an iterator for the task results in this list.
	 * 
	 * @return The iterator.
	 */
	public Iterator<? extends StructuredTaskResult> resultIterator();

	/**
	 * Performs an action for each task result element in this list.
	 * 
	 * @param consumer
	 *            The action to perform.
	 * @throws NullPointerException
	 *             If the action is null.
	 */
	public default void forEach(Consumer<? super StructuredTaskResult> consumer) throws NullPointerException {
		resultIterator().forEachRemaining(consumer);
	}

	@Override
	public default List<?> toResult(TaskResultResolver results) {
		List<Object> result = new ArrayList<>();
		forEach(tid -> {
			result.add(tid.toResult(results));
		});
		return ImmutableUtils.unmodifiableList(result);
	}
}
