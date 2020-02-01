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
package saker.build.thirdparty.saker.util.rmi.wrap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.util.ObjectUtils;

/**
 * {@link RMIWrapper} implementation that writes {@link Iterable} or {@link Iterator} instances and reads them as
 * {@link ArrayList} instances.
 * <p>
 * The elements of the transferred iterable is written as objects.
 * 
 * @see RMIObjectOutput#writeObject(Object)
 */
public class RMIArrayListWrapper implements RMIWrapper {
	private Iterable<?> iterable;

	/**
	 * Creates a new instance.
	 * <p>
	 * Users shouldn't instantiate this class manually, but leave that to the RMI runtime.
	 */
	public RMIArrayListWrapper() {
	}

	/**
	 * Creates a new instance for an iterable.
	 * <p>
	 * Users shouldn't instantiate this class manually, but leave that to the RMI runtime.
	 * 
	 * @param iterable
	 *            The iterable.
	 */
	public RMIArrayListWrapper(Iterable<?> iterable) {
		this.iterable = iterable;
	}

	/**
	 * Constructs a new instance for an iterator.
	 * <p>
	 * The remaining elements in the iterator are added to a new {@link ArrayList}, which will be transferred.
	 * <p>
	 * Users shouldn't instantiate this class manually, but leave that to the RMI runtime.
	 * 
	 * @param iterator
	 *            The iterator.
	 */
	public RMIArrayListWrapper(Iterator<?> iterator) {
		this.iterable = ObjectUtils.newArrayList(iterator);
	}

	@Override
	public Object getWrappedObject() {
		return iterable;
	}

	@Override
	public Object resolveWrapped() {
		return iterable;
	}

	@Override
	public void writeWrapped(RMIObjectOutput out) throws IOException {
		if (iterable == null) {
			out.writeObject(CommonSentinel.NULL_INPUT);
			return;
		}
		for (Object o : iterable) {
			out.writeObject(o);
		}
		out.writeObject(CommonSentinel.END_OF_OBJECTS);
	}

	@Override
	public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
		Object obj = in.readObject();
		if (obj == CommonSentinel.NULL_INPUT) {
			iterable = null;
			return;
		}
		List<Object> l = new ArrayList<>();
		do {
			if (obj == CommonSentinel.END_OF_OBJECTS) {
				break;
			}
			l.add(obj);
			obj = in.readObject();
		} while (true);
		this.iterable = l;
	}
}
