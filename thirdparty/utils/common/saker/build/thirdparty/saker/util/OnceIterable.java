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
package saker.build.thirdparty.saker.util;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

class OnceIterable<T> implements Iterable<T> {
	@SuppressWarnings("rawtypes")
	private static final AtomicReferenceFieldUpdater<OnceIterable, Iterator> ARFU_iterator = AtomicReferenceFieldUpdater
			.newUpdater(OnceIterable.class, Iterator.class, "iterator");

	@SuppressWarnings("unused")
	private volatile Iterator<T> iterator;

	public OnceIterable(Iterator<T> iterator) {
		this.iterator = iterator;
	}

	@Override
	public Iterator<T> iterator() {
		@SuppressWarnings("unchecked")
		Iterator<T> got = ARFU_iterator.getAndSet(this, null);
		if (got == null) {
			throw new IllegalStateException("Already returned iterator.");
		}
		return got;
	}

}
