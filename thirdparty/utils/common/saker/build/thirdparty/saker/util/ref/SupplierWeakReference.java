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
package saker.build.thirdparty.saker.util.ref;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.function.Supplier;

/**
 * Class extending {@link WeakReference} and also implementing {@link Supplier}.
 * 
 * @param <T>
 *            The type of the referent.
 */
public class SupplierWeakReference<T> extends WeakReference<T> implements Supplier<T> {
	/**
	 * @see WeakReference#WeakReference(Object, ReferenceQueue)
	 */
	public SupplierWeakReference(T referent, ReferenceQueue<? super T> q) {
		super(referent, q);
	}

	/**
	 * @see WeakReference#WeakReference(Object)
	 */
	public SupplierWeakReference(T referent) {
		super(referent);
	}

}
