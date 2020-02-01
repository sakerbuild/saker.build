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

import java.util.Map;

/**
 * {@link ElementAccumulator} subinterface for allowing {@link Map.Entry} objects to be added to it.
 * <p>
 * Note that even though this class accumulates {@link Map.Entry} objects, adding entries with the same keys will not
 * overwrite previous additions. That is, when the accumulator is iterated over, it can return multiple entries with the
 * same keys.
 * 
 * @param <K>
 *            The key type.
 * @param <V>
 *            The value type.
 */
public interface EntryAccumulator<K, V> extends ElementAccumulator<Map.Entry<K, V>> {
	/**
	 * Adds an key-value entry pair to the accumulator.
	 * 
	 * @param key
	 *            The key.
	 * @param value
	 *            The value.
	 */
	public void put(K key, V value);
}
