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
package saker.build.ide.configuration;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * Immutable {@link IDEConfiguration} data class implementation.
 * <p>
 * This class clones all the data passed to it during instantiation.
 */
public final class SimpleIDEConfiguration implements IDEConfiguration, Externalizable {
	private static final long serialVersionUID = 1L;

	private String type;
	private String identifier;
	/**
	 * Unmodifiable map of the fields sorted by natural order.
	 */
	private NavigableMap<String, ?> fields;

	/**
	 * For {@link Externalizable}.
	 */
	public SimpleIDEConfiguration() {
	}

	/**
	 * Creates a new instance by initializing it with the specified parameters.
	 * <p>
	 * The fields will be copied recursively.
	 * 
	 * @param type
	 *            The type of the IDE configuration.
	 * @param identifier
	 *            The identifier for the IDE configuration.
	 * @param fields
	 *            The fields to initialize this configuration with. If <code>null</code>, it is considered to be empty.
	 * @throws NullPointerException
	 *             If type or identifier is <code>null</code>.
	 */
	public SimpleIDEConfiguration(String type, String identifier, Map<String, ?> fields) throws NullPointerException {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(identifier, "identifier");
		this.type = type;
		this.identifier = identifier;
		if (!ObjectUtils.isNullOrEmpty(fields)) {
			NavigableMap<String, Object> thisfields = new TreeMap<>();
			for (Entry<String, ?> entry : fields.entrySet()) {
				Object fval = entry.getValue();
				thisfields.put(entry.getKey(), cloneFieldValue(fval));
			}
			this.fields = ImmutableUtils.unmodifiableNavigableMap(thisfields);
		} else {
			this.fields = Collections.emptyNavigableMap();
		}
	}

	/**
	 * Creates a new instance and intializes it by copying the data from the argument configuration.
	 * 
	 * @param copy
	 *            The configuration to copy.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public SimpleIDEConfiguration(IDEConfiguration copy) throws NullPointerException {
		Objects.requireNonNull(copy, "copy");
		this.type = copy.getType();
		this.identifier = copy.getIdentifier();
		Set<String> copyfieldnames = copy.getFieldNames();
		if (!ObjectUtils.isNullOrEmpty(copyfieldnames)) {
			NavigableMap<String, Object> thisfields = new TreeMap<>();
			for (String fname : copyfieldnames) {
				Object fval = copy.getField(fname);
				thisfields.put(fname, cloneFieldValue(fval));
			}
			this.fields = ImmutableUtils.unmodifiableNavigableMap(thisfields);
		} else {
			this.fields = Collections.emptyNavigableMap();
		}
	}

	@Override
	public String getType() {
		return type;
	}

	@Override
	public String getIdentifier() {
		return identifier;
	}

	@Override
	public Object getField(String fieldname) {
		return fields.get(fieldname);
	}

	@Override
	public Set<String> getFieldNames() {
		return fields.keySet();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeUTF(type);
		out.writeUTF(identifier);
		SerialUtils.writeExternalMap(out, fields);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		type = in.readUTF();
		identifier = in.readUTF();
		fields = SerialUtils.readExternalSortedImmutableNavigableMap(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((fields == null) ? 0 : fields.hashCode());
		result = prime * result + ((identifier == null) ? 0 : identifier.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SimpleIDEConfiguration other = (SimpleIDEConfiguration) obj;
		if (fields == null) {
			if (other.fields != null)
				return false;
		} else if (!fields.equals(other.fields))
			return false;
		if (identifier == null) {
			if (other.identifier != null)
				return false;
		} else if (!identifier.equals(other.identifier))
			return false;
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + identifier + ": " + type + "(" + fields + ")]";
	}

	private static Object cloneFieldValue(Object val) {
		if (val instanceof Collection) {
			Collection<?> c = (Collection<?>) val;
			List<Object> res = new ArrayList<>();
			for (Object o : c) {
				res.add(cloneFieldValue(o));
			}
			return ImmutableUtils.unmodifiableList(res);
		}
		if (val instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, ?> m = (Map<String, ?>) val;
			NavigableMap<String, Object> res = new TreeMap<>();
			for (Entry<String, ?> entry : m.entrySet()) {
				res.put(entry.getKey(), cloneFieldValue(entry.getValue()));
			}
			return ImmutableUtils.unmodifiableNavigableMap(res);
		}
		//string or primitive (or null)
		return val;
	}
}
