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
package saker.build.thirdparty.saker.rmi.connection;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;

class RMICommCache<S extends ReflectionElementSupplier> {
	private final ConcurrentSkipListMap<Integer, S> readDatas;
	private final ConcurrentHashMap<S, Integer> readIndices;

	private final AtomicInteger indexCounter;
	private final ConcurrentHashMap<S, Integer> writeDatas;

	public RMICommCache() {
		readDatas = new ConcurrentSkipListMap<>();
		readIndices = new ConcurrentHashMap<>();

		indexCounter = new AtomicInteger();
		writeDatas = new ConcurrentHashMap<>();
	}

	public Integer getWriteIndex(S data) {
		return writeDatas.get(data);
	}

	public void putWrite(S data, int index) {
		writeDatas.putIfAbsent(data, index);
	}

	int putReadInternal(int index, S data) {
		readDatas.putIfAbsent(index, data);
		indexCounter.updateAndGet(c -> Math.max(c, index));
		return index;
	}

	public Integer putReadIfAbsent(S data) {
		Integer presentidx = readIndices.get(data);
		if (presentidx != null) {
			return null;
		}
		int index = indexCounter.incrementAndGet();
		Integer putidxprev = readIndices.putIfAbsent(data, index);
		if (putidxprev != null) {
			//data was put concurrently
			return null;
		}
		readDatas.putIfAbsent(index, data);
		return index;
	}

	public S getRead(int index) {
		return readDatas.get(index);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[]";
	}
}