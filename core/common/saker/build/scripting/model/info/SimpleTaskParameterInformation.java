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
import java.util.Set;
import java.util.TreeSet;

import saker.apiextract.api.PublicApi;
import saker.build.scripting.model.FormattedTextContent;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * Simple {@link TaskParameterInformation} data class.
 */
@PublicApi
public class SimpleTaskParameterInformation implements TaskParameterInformation, Externalizable {
	private static final long serialVersionUID = 1L;

	private TaskInformation task;
	private String name;
	private Set<String> aliases;
	private FormattedTextContent information;
	private TypeInformation typeInformation;
	private boolean required;
	private boolean deprecated;

	/**
	 * For {@link Externalizable}.
	 */
	public SimpleTaskParameterInformation() {
	}

	/**
	 * Creates a new instance with the given parameters.
	 * 
	 * @param task
	 *            The task this parameter corresponds to.
	 * @param name
	 *            The name of the parameter.
	 * @see #getParameterName()
	 * @see #getTask()
	 */
	public SimpleTaskParameterInformation(TaskInformation task, String name) {
		this.task = task;
		this.name = name;
	}

	@Override
	public TaskInformation getTask() {
		return task;
	}

	@Override
	public String getParameterName() {
		return name;
	}

	@Override
	public Set<String> getAliases() {
		return aliases;
	}

	@Override
	public FormattedTextContent getInformation() {
		return information;
	}

	@Override
	public TypeInformation getTypeInformation() {
		return typeInformation;
	}

	@Override
	public boolean isRequired() {
		return required;
	}

	@Override
	public boolean isDeprecated() {
		return deprecated;
	}

	/**
	 * Sets the parameter name.
	 * 
	 * @param name
	 *            The parameter name.
	 * @see #getParameterName()
	 */
	public void setParameterName(String name) {
		this.name = name;
	}

	/**
	 * Sets the name aliases.
	 * 
	 * @param aliases
	 *            The aliases.
	 * @see #getAliases()
	 */
	public void setAliases(Set<String> aliases) {
		this.aliases = aliases;
	}

	/**
	 * Sets the documentational information.
	 * 
	 * @param information
	 *            The information.
	 * @see #getInformation()
	 */
	public void setInformation(FormattedTextContent information) {
		this.information = information;
	}

	/**
	 * Sets the type information.
	 * 
	 * @param typeInformation
	 *            The type information.
	 * @see #getTypeInformation()
	 */
	public void setTypeInformation(TypeInformation typeInformation) {
		this.typeInformation = typeInformation;
	}

	/**
	 * Sets the required flag.
	 * 
	 * @param required
	 *            Whether the parameter is required.
	 * @see #isRequired()
	 */
	public void setRequired(boolean required) {
		this.required = required;
	}

	/**
	 * Sets the deprecated flag for this parameter.
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
		SerialUtils.writeExternalCollection(out, aliases);
		out.writeObject(information);
		out.writeObject(typeInformation);
		out.writeBoolean(required);
		out.writeBoolean(deprecated);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		name = (String) in.readObject();
		aliases = SerialUtils.readExternalCollection(new TreeSet<>(), in);
		information = (FormattedTextContent) in.readObject();
		typeInformation = (TypeInformation) in.readObject();
		required = in.readBoolean();
		deprecated = in.readBoolean();
	}

	@Override
	public String toString() {
		return "SimpleTaskParameterInformation[" + (name != null ? "name=" + name + ", " : "")
				+ (aliases != null ? "aliases=" + aliases + ", " : "")
				+ (information != null ? "information=" + information + ", " : "")
				+ (typeInformation != null ? "typeInformation=" + typeInformation + ", " : "") + "required=" + required
				+ ", deprecated=" + deprecated + "]";
	}

}
