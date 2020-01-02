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

import java.util.Collections;
import java.util.Map;

import saker.apiextract.api.PublicApi;

/**
 * A simple {@link TextPartition} implementation holding the data passed to it.
 */
@PublicApi
public class SimpleTextPartition implements TextPartition {
	private String title;
	private String subTitle;
	private FormattedTextContent content;
	private String schemaIdentifier;
	private Map<String, String> schemaMetaData = Collections.emptyMap();

	/**
	 * Creates a new instance with the given values.
	 * 
	 * @param title
	 *            The title of the partition.
	 * @param subTitle
	 *            The subtitle of the partition.
	 * @param content
	 *            The text body contents for the partition.
	 */
	public SimpleTextPartition(String title, String subTitle, FormattedTextContent content) {
		this.title = title;
		this.subTitle = subTitle;
		this.content = content;
	}

	@Override
	public String getTitle() {
		return title;
	}

	@Override
	public String getSubTitle() {
		return subTitle;
	}

	@Override
	public FormattedTextContent getContent() {
		return content;
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
	 *            The meta-data.
	 */
	public void setSchemaMetaData(Map<String, String> schemaMetaData) {
		if (schemaMetaData == null) {
			this.schemaMetaData = Collections.emptyMap();
		} else {
			this.schemaMetaData = schemaMetaData;
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((content == null) ? 0 : content.hashCode());
		result = prime * result + ((subTitle == null) ? 0 : subTitle.hashCode());
		result = prime * result + ((title == null) ? 0 : title.hashCode());
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
		SimpleTextPartition other = (SimpleTextPartition) obj;
		if (content == null) {
			if (other.content != null)
				return false;
		} else if (!content.equals(other.content))
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
		if (subTitle == null) {
			if (other.subTitle != null)
				return false;
		} else if (!subTitle.equals(other.subTitle))
			return false;
		if (title == null) {
			if (other.title != null)
				return false;
		} else if (!title.equals(other.title))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (title != null ? "title=" + title + ", " : "")
				+ (subTitle != null ? "subTitle=" + subTitle + ", " : "")
				+ (content != null ? "content=" + content + ", " : "")
				+ (schemaIdentifier != null ? "schemaIdentifier=" + schemaIdentifier + ", " : "")
				+ (schemaMetaData != null ? "schemaMetaData=" + schemaMetaData : "") + "]";
	}

}