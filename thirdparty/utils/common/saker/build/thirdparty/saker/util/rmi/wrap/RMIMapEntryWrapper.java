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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.util.ImmutableUtils;

/**
 * {@link RMIWrapper} implementation that writes the key and value of a {@link Map.Entry} object and instantiates it as
 * an immutable entry when read.
 * <p>
 * The key and value is written using {@link RMIObjectOutput#writeObject(Object)}.
 * 
 * @see ImmutableUtils#makeImmutableMapEntry(Object, Object)
 */
public class RMIMapEntryWrapper implements RMIWrapper {
	private Map.Entry<?, ?> entry;

	/**
	 * Creates a new instance.
	 * <p>
	 * Users shouldn't instantiate this class manually, but leave that to the RMI runtime.
	 */
	public RMIMapEntryWrapper() {
	}

	/**
	 * Creates a new instance for a map entry.
	 * <p>
	 * Users shouldn't instantiate this class manually, but leave that to the RMI runtime.
	 * 
	 * @param entry
	 *            The entry.
	 * @throws NullPointerException
	 *             If the entry is <code>null</code>.
	 */
	public RMIMapEntryWrapper(Entry<?, ?> entry) throws NullPointerException {
		Objects.requireNonNull(entry, "entry");
		this.entry = entry;
	}

	@Override
	public void writeWrapped(RMIObjectOutput out) throws IOException {
		out.writeObject(entry.getKey());
		out.writeObject(entry.getValue());
	}

	@Override
	public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
		Object k = in.readObject();
		Object v = in.readObject();
		entry = ImmutableUtils.makeImmutableMapEntry(k, v);
	}

	@Override
	public Object resolveWrapped() {
		return entry;
	}

	@Override
	public Object getWrappedObject() {
		throw new UnsupportedOperationException();
	}

}
