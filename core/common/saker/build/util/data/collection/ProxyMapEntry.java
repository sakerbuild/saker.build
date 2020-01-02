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
package saker.build.util.data.collection;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Map.Entry;

import saker.build.util.data.ConversionContext;
import saker.build.util.data.DataConverterUtils;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class ProxyMapEntry implements Map.Entry {

	protected final ConversionContext keyConversionContext;
	protected final ConversionContext valueConversionContext;
	protected final Entry entry;
	protected transient final Type selfKeyType;
	protected transient final Type selfValueType;

	public ProxyMapEntry(ConversionContext keyConversionContext, ConversionContext valueConversionContext, Entry entry,
			Type selfKeyType, Type selfValueType) {
		this.keyConversionContext = keyConversionContext;
		this.valueConversionContext = valueConversionContext;
		this.entry = entry;
		this.selfKeyType = selfKeyType;
		this.selfValueType = selfValueType;
	}

	@Override
	public Object getKey() {
		// return K
		return DataConverterUtils.convert(keyConversionContext, entry.getKey(), selfKeyType);
	}

	@Override
	public Object getValue() {
		// return V
		return DataConverterUtils.convert(valueConversionContext, entry.getValue(), selfValueType);
	}

	@Override
	public Object setValue(Object value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int hashCode() {
		Object key = getKey();
		Object value = getValue();
		return (key == null ? 0 : key.hashCode()) ^ (value == null ? 0 : value.hashCode());
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Entry)) {
			return false;
		}
		Entry e1 = this;
		Entry e2 = (Entry) obj;
		Object selfkey = e1.getKey();
		Object okey = e2.getKey();
		Object selfvalue = e1.getValue();
		Object ovalue = e2.getValue();
		return (selfkey == null ? okey == null : selfkey.equals(okey))
				&& (selfvalue == null ? ovalue == null : selfvalue.equals(ovalue));
	}

	@Override
	public String toString() {
		return getKey() + "=" + getValue();
	}
}
