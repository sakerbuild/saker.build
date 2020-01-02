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

import java.util.Map;

import saker.apiextract.api.PublicApi;

/**
 * Simple data class implementation of {@link ScriptTokenInformation}.
 */
@PublicApi
public class SimpleScriptTokenInformation implements ScriptTokenInformation {
	private PartitionedTextContent description;
	private String schemaIdentifier;
	private Map<String, String> schemaMetaData;

	/**
	 * Creates a new instance and initializes it with the specified {@linkplain #getDescription() description}.
	 * 
	 * @param description
	 *            The description.
	 */
	public SimpleScriptTokenInformation(PartitionedTextContent description) {
		this.description = description;
	}

	@Override
	public PartitionedTextContent getDescription() {
		return description;
	}

	@Override
	public String getSchemaIdentifier() {
		return schemaIdentifier;
	}

	@Override
	public Map<String, String> getSchemaMetaData() {
		return schemaMetaData;
	}

	/**
	 * Sets the {@linkplain #getSchemaIdentifier() schema identifier}.
	 * 
	 * @param schemaIdentifier
	 *            The schema identifier.
	 */
	public void setSchemaIdentifier(String schemaIdentifier) {
		this.schemaIdentifier = schemaIdentifier;
	}

	/**
	 * Sets the {@linkplain #getSchemaMetaData() schema meta-data}.
	 * 
	 * @param schemaMetaData
	 *            The schema meta-data.
	 */
	public void setSchemaMetaData(Map<String, String> schemaMetaData) {
		this.schemaMetaData = schemaMetaData;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((description == null) ? 0 : description.hashCode());
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
		SimpleScriptTokenInformation other = (SimpleScriptTokenInformation) obj;
		if (description == null) {
			if (other.description != null)
				return false;
		} else if (!description.equals(other.description))
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
}
