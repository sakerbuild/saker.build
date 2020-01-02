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
package saker.build.scripting.model;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import saker.apiextract.api.PublicApi;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * Data class implementation of {@link ScriptStructureOutline}.
 * <p>
 * The class is mutable.
 */
@PublicApi
public class SimpleScriptStructureOutline implements ScriptStructureOutline, Externalizable {
	private static final long serialVersionUID = 1L;

	private List<? extends StructureOutlineEntry> rootEntries;

	private String schemaIdentifier;
	private Map<String, String> schemaMetaData = new TreeMap<>();

	/**
	 * Creates a new uninitialized instance.
	 * <p>
	 * Also used by {@link Externalizable}.
	 */
	public SimpleScriptStructureOutline() {
	}

	/**
	 * Creates a new instance and initializes it with the given root outline entries.
	 * 
	 * @param rootEntries
	 *            The root entries.
	 * @see #getRootEntries()
	 */
	public SimpleScriptStructureOutline(List<? extends StructureOutlineEntry> rootEntries) {
		this.rootEntries = rootEntries;
	}

	/**
	 * Sets the root entries of the outline.
	 * 
	 * @param rootEntries
	 *            The root entries.
	 * @see #getRootEntries()
	 */
	public void setRootEntries(List<? extends StructureOutlineEntry> rootEntries) {
		this.rootEntries = rootEntries;
	}

	/**
	 * Sets the schema identifier for the outline.
	 * 
	 * @param schemaIdentifier
	 *            The schema identifier.
	 * @see #getSchemaIdentifier()
	 */
	public void setSchemaIdentifier(String schemaIdentifier) {
		this.schemaIdentifier = schemaIdentifier;
	}

	/**
	 * Ads a schema meta-data key-value pair for the outline.
	 * 
	 * @param key
	 *            The key of the meta-data.
	 * @param value
	 *            The value of the meta-data.
	 * @see #getSchemaMetaData()
	 * @throws NullPointerException
	 *             If the key is <code>null</code>.
	 */
	public void addSchemaMetaData(String key, String value) throws NullPointerException {
		Objects.requireNonNull(key, "key");
		this.schemaMetaData.put(key, value);
	}

	@Override
	public List<? extends StructureOutlineEntry> getRootEntries() {
		return rootEntries;
	}

	@Override
	public String getSchemaIdentifier() {
		return schemaIdentifier;
	}

	@Override
	public Map<String, String> getSchemaMetaData() {
		return schemaMetaData;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((rootEntries == null) ? 0 : rootEntries.hashCode());
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
		SimpleScriptStructureOutline other = (SimpleScriptStructureOutline) obj;
		if (rootEntries == null) {
			if (other.rootEntries != null)
				return false;
		} else if (!rootEntries.equals(other.rootEntries))
			return false;
		if (schemaIdentifier == null) {
			if (other.schemaIdentifier != null)
				return false;
		} else if (!schemaIdentifier.equals(other.schemaIdentifier))
			return false;
		if (schemaMetaData == null) {
			if (other.schemaMetaData != null)
				return false;
		} else if (!schemaMetaData.equals(other.schemaMetaData))
			return false;
		return true;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, rootEntries);

		out.writeObject(schemaIdentifier);
		SerialUtils.writeExternalMap(out, schemaMetaData);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		rootEntries = SerialUtils.readExternalImmutableList(in);

		schemaIdentifier = (String) in.readObject();
		schemaMetaData = SerialUtils.readExternalMap(new TreeMap<>(), in);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + rootEntries + "]";
	}
}
