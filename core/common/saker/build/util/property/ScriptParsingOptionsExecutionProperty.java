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
package saker.build.util.property;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.apiextract.api.PublicApi;
import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionProperty;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptOptionsConfig;
import saker.build.scripting.ScriptAccessProvider;
import saker.build.scripting.ScriptParsingOptions;
import saker.build.scripting.SimpleScriptParsingOptions;

/**
 * {@link ExecutionProperty} implementation for retrieving the current {@link ScriptParsingOptions} for a script path
 * specified by the current {@link ExecutionScriptConfiguration} of the build execution.
 * <p>
 * For retrieving the {@link ScriptAccessProvider} for the script as well, use
 * {@link ScriptParsingConfigurationExecutionProperty}.
 */
@PublicApi
public final class ScriptParsingOptionsExecutionProperty
		implements ExecutionProperty<ScriptParsingOptions>, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath scriptPath;

	/**
	 * For {@link Externalizable}.
	 */
	public ScriptParsingOptionsExecutionProperty() {
	}

	/**
	 * Creates a new instance with the specified script path.
	 * 
	 * @param scriptPath
	 *            The script path.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the path is not absolute.
	 */
	public ScriptParsingOptionsExecutionProperty(SakerPath scriptPath)
			throws NullPointerException, InvalidPathFormatException {
		SakerPathFiles.requireAbsolutePath(scriptPath);
		this.scriptPath = scriptPath;
	}

	/**
	 * Gets the script path this property was initialized with.
	 * 
	 * @return The script path.
	 */
	public SakerPath getScriptPath() {
		return scriptPath;
	}

	@Override
	public ScriptParsingOptions getCurrentValue(ExecutionContext executioncontext) {
		ExecutionScriptConfiguration scriptconfiguration = executioncontext.getScriptConfiguration();
		ScriptOptionsConfig scriptconfig = scriptconfiguration.getScriptOptionsConfig(scriptPath);
		if (scriptconfig == null) {
			return null;
		}
		return new SimpleScriptParsingOptions(scriptPath, scriptconfig.getOptions());
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(scriptPath);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		scriptPath = (SakerPath) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((scriptPath == null) ? 0 : scriptPath.hashCode());
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
		ScriptParsingOptionsExecutionProperty other = (ScriptParsingOptionsExecutionProperty) obj;
		if (scriptPath == null) {
			if (other.scriptPath != null)
				return false;
		} else if (!scriptPath.equals(other.scriptPath))
			return false;
		return true;
	}

}