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
package saker.build.scripting.model.info;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.apiextract.api.PublicApi;
import saker.build.scripting.model.FormattedTextContent;

/**
 * Simple {@link FieldInformation} data class.
 */
@PublicApi
public class SimpleFieldInformation implements FieldInformation, Externalizable {
	private static final long serialVersionUID = 1L;

	private String name;
	private TypeInformation type;
	private FormattedTextContent information;
	private boolean deprecated;

	/**
	 * Creates a new instance with the specified field name.
	 * 
	 * @param name
	 *            The field name.
	 */
	public SimpleFieldInformation(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public TypeInformation getType() {
		return type;
	}

	@Override
	public FormattedTextContent getInformation() {
		return information;
	}

	@Override
	public boolean isDeprecated() {
		return deprecated;
	}

	/**
	 * Sets the type of this field information.
	 * 
	 * @param type
	 *            The type.
	 * @see #getType()
	 */
	public void setType(TypeInformation type) {
		this.type = type;
	}

	/**
	 * Sets the documentational information for this field.
	 * 
	 * @param information
	 *            The information.
	 * @see #getInformation()
	 */
	public void setInformation(FormattedTextContent information) {
		this.information = information;
	}

	/**
	 * Sets the deprecated flag for this field.
	 * 
	 * @param deprecated
	 *            <code>true</code> if deprecated.
	 * @see #isDeprecated()
	 */
	public void setDeprecated(boolean deprecated) {
		this.deprecated = deprecated;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(name);
		out.writeObject(type);
		out.writeObject(information);
		out.writeBoolean(deprecated);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		name = (String) in.readObject();
		type = (TypeInformation) in.readObject();
		information = (FormattedTextContent) in.readObject();
		deprecated = in.readBoolean();
	}

	@Override
	public String toString() {
		return "SimpleFieldInformation[" + (name != null ? "name=" + name + ", " : "")
				+ (information != null ? "information=" + information + ", " : "")
				+ (deprecated ? "deprecated=true" : "") + "]";
	}

}
