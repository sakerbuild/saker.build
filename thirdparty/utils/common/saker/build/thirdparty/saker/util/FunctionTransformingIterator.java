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
import java.util.function.Function;

class FunctionTransformingIterator<T, E> extends TransformingIterator<T, E> {
	private Function<? super T, ? extends E> transformer;

	public FunctionTransformingIterator(Iterator<? extends T> it, Function<? super T, ? extends E> transformer) {
		super(it);
		this.transformer = transformer;
	}

	@Override
	protected E transform(T value) {
		return transformer.apply(value);
	}

}
