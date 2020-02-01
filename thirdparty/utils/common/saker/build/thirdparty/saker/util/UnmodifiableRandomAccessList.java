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

import java.io.Externalizable;
import java.util.List;
import java.util.RandomAccess;

class UnmodifiableRandomAccessList<E> extends UnmodifiableList<E> implements RandomAccess {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public UnmodifiableRandomAccessList() {
	}

	public UnmodifiableRandomAccessList(List<? extends E> list) {
		super(list);
	}

	@Override
	public List<E> subList(int fromIndex, int toIndex) {
		return new UnmodifiableRandomAccessList<>(list.subList(fromIndex, toIndex));
	}
}
