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
import java.util.Collection;

import saker.apiextract.api.PublicApi;
import saker.build.scripting.model.FormattedTextContent;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * Simple {@link BuildTargetInformation} data class.
 * 
 * @since saker.build 0.8.18
 */
@PublicApi
public class SimpleBuildTargetInformation implements BuildTargetInformation, Externalizable {
	private static final long serialVersionUID = 1L;

	private String targetName;
	private FormattedTextContent information;
	private Collection<? extends BuildTargetParameterInformation> parameters;

	/**
	 * For {@link Externalizable}.
	 */
	public SimpleBuildTargetInformation() {
	}

	/**
	 * Creates a new instance with the specified name.
	 * 
	 * @param targetName
	 *            The target name.
	 */
	public SimpleBuildTargetInformation(String targetName) {
		this.targetName = targetName;
	}

	@Override
	public String getTargetName() {
		return targetName;
	}

	@Override
	public Collection<? extends BuildTargetParameterInformation> getParameters() {
		return parameters;
	}

	@Override
	public FormattedTextContent getInformation() {
		return information;
	}

	/**
	 * Sets the parameters of this build target.
	 * 
	 * @param parameters
	 *            The parameters.
	 * @see #getParameters()
	 */
	public void setParameters(Collection<? extends BuildTargetParameterInformation> parameters) {
		this.parameters = parameters;
	}

	/**
	 * Sets the documentational information of this build target.
	 * 
	 * @param information
	 *            The information.
	 * @see #getInformation()
	 */
	public void setInformation(FormattedTextContent information) {
		this.information = information;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(targetName);
		out.writeObject(information);
		SerialUtils.writeExternalCollection(out, parameters);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		targetName = (String) in.readObject();
		information = (FormattedTextContent) in.readObject();
		parameters = SerialUtils.readExternalArrayList(in);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder(getClass().getSimpleName());
		builder.append("[targetName=");
		builder.append(targetName);
		builder.append(", information=");
		builder.append(information);
		builder.append(", parameters=");
		builder.append(parameters);
		builder.append("]");
		return builder.toString();
	}

}
