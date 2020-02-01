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

import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * {@link Token} implementation that is initialized with a {@linkplain WeakReference weak} and a strong reference.
 * <p>
 * This base token implementation can be used when the server stores the passed objects using a reference, and the token
 * is returned to the client to store.
 * <p>
 * The weak reference used during the construction of the object should be stored in a collection on the server, and an
 * instance of this token should be returned to the client. Once the client releases all references to this token, the
 * server will be able to garbage collect the strongly referenced object, as there are only weak references pointing to
 * it.
 * <p>
 * It may be useful to extend this class and provide additional functions to the client to manually uninstall the token
 * reference from the server.
 * 
 * @param <T>
 *            The type of the referenced object.
 */
public class WeakReferencedToken<T> implements Token {
	/**
	 * The weak reference to the object.
	 */
	protected final WeakReference<? extends T> objectWeakRef;
	/**
	 * The strong reference to the object.
	 */
	protected final T objectStrongRef;

	private WeakReferencedToken(WeakReference<? extends T> objectWeakRef, T objectStrongRef) {
		this.objectWeakRef = objectWeakRef;
		this.objectStrongRef = objectStrongRef;
	}

	/**
	 * Constructs a new token with the given weak reference.
	 * <p>
	 * The strong reference of this token is initialized by referent of the argument.
	 * 
	 * @param objectWeakRef
	 *            The reference.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public WeakReferencedToken(WeakReference<? extends T> objectWeakRef) throws NullPointerException {
		this(Objects.requireNonNull(objectWeakRef, "object weak reference"), objectWeakRef.get());
	}
}
