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
 * Simple {@link LiteralInformation} data class.
 */
@PublicApi
public class SimpleLiteralInformation implements LiteralInformation, Externalizable {
	private static final long serialVersionUID = 1L;

	private String literal;
	private FormattedTextContent information;
	private String relation;
	private TypeInformation type;
	private boolean deprecated;

	/**
	 * For {@link Externalizable}.
	 */
	public SimpleLiteralInformation() {
	}

	/**
	 * Construct a new instance for the given literal.
	 * 
	 * @param literal
	 *            The literal.
	 * @see #getLiteral()
	 */
	public SimpleLiteralInformation(String literal) {
		this.literal = literal;
	}

	@Override
	public String getLiteral() {
		return literal;
	}

	@Override
	public FormattedTextContent getInformation() {
		return information;
	}

	@Override
	public String getRelation() {
		return relation;
	}

	@Override
	public TypeInformation getType() {
		return type;
	}

	@Override
	public boolean isDeprecated() {
		return deprecated;
	}

	/**
	 * Sets the literal.
	 * 
	 * @param literal
	 *            The literal.
	 * @see #getLiteral()
	 */
	public void setLiteral(String literal) {
		this.literal = literal;
	}

	/**
	 * Sets the information for this literal.
	 * 
	 * @param information
	 *            The information.
	 * @see #getInformation()
	 */
	public void setInformation(FormattedTextContent information) {
		this.information = information;
	}

	/**
	 * Sets the relation information.
	 * 
	 * @param relation
	 *            The relation.
	 * @see #getRelation()
	 */
	public void setRelation(String relation) {
		this.relation = relation;
	}

	/**
	 * Sets the type of this literal.
	 * 
	 * @param type
	 *            The type.
	 * @see #getType()
	 */
	public void setType(TypeInformation type) {
		this.type = type;
	}

	/**
	 * Sets the deprecated flag for this literal.
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
		out.writeObject(literal);
		out.writeObject(information);
		out.writeObject(relation);
		out.writeObject(type);
		out.writeBoolean(deprecated);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		literal = (String) in.readObject();
		information = (FormattedTextContent) in.readObject();
		relation = (String) in.readObject();
		type = (TypeInformation) in.readObject();
		deprecated = in.readBoolean();
	}

	@Override
	public String toString() {
		return "SimpleLiteralInformation[" + (literal != null ? "literal=" + literal + ", " : "")
				+ (information != null ? "information=" + information + ", " : "")
				+ (relation != null ? "relation=" + relation + ", " : "") + (type != null ? "type=" + type + ", " : "")
				+ "deprecated=" + deprecated + "]";
	}

}
