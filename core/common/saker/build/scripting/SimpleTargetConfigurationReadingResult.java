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
package saker.build.scripting;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

import saker.apiextract.api.PublicApi;
import saker.build.runtime.execution.TargetConfiguration;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * Simple data class implementation of the {@link TargetConfigurationReadingResult} interface.
 */
@PublicApi
public class SimpleTargetConfigurationReadingResult implements TargetConfigurationReadingResult, Externalizable {
	private static final long serialVersionUID = 1L;

	private TargetConfiguration targetConfiguration;
	private ScriptInformationProvider informationProvider;

	/**
	 * For {@link Externalizable}.
	 */
	public SimpleTargetConfigurationReadingResult() {
	}

	/**
	 * Creates a new instance with the given arguments.
	 * 
	 * @param targetConfiguration
	 *            The target configuration.
	 * @param informationProvider
	 *            The script information provider.
	 * @throws NullPointerException
	 *             If the target configuration is <code>null</code>.
	 */
	public SimpleTargetConfigurationReadingResult(TargetConfiguration targetConfiguration,
			ScriptInformationProvider informationProvider) throws NullPointerException {
		Objects.requireNonNull(targetConfiguration, "target configuration");

		this.targetConfiguration = targetConfiguration;
		this.informationProvider = informationProvider;
	}

	@Override
	public TargetConfiguration getTargetConfiguration() {
		return targetConfiguration;
	}

	@Override
	public ScriptInformationProvider getInformationProvider() {
		return informationProvider;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(targetConfiguration);
		out.writeObject(informationProvider);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		targetConfiguration = SerialUtils.readExternalObject(in);
		informationProvider = SerialUtils.readExternalObject(in);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "["
				+ (targetConfiguration != null ? "targetConfiguration=" + targetConfiguration + ", " : "")
				+ (informationProvider != null ? "informationProvider=" + informationProvider : "") + "]";
	}

}
