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
import java.util.Objects;

import saker.apiextract.api.PublicApi;
import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionProperty;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptOptionsConfig;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptProviderLocation;
import saker.build.scripting.ScriptAccessProvider;
import saker.build.scripting.ScriptParsingOptions;
import saker.build.scripting.SimpleScriptParsingOptions;

/**
 * {@link ExecutionProperty} implementation for querying execution configuration related resources for the provided
 * script path.
 * <p>
 * The returned property will contain all necessary information required to parse a script for execution. It will return
 * the {@linkplain PropertyValue#getParsingOptions() script parsing options} and the
 * {@linkplain PropertyValue#getAccessProvider() language provider} for parsing.
 * <p>
 * The language provider instance in the returned property is transient. See {@link PropertyValue#getAccessProvider()}.
 */
@PublicApi
public final class ScriptParsingConfigurationExecutionProperty
		implements ExecutionProperty<ScriptParsingConfigurationExecutionProperty.PropertyValue>, Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * The computed property value for {@link ScriptParsingConfigurationExecutionProperty}.
	 * <p>
	 * Equality of the property value is determined based on the script accessor key and parsing options.
	 */
	public static final class PropertyValue implements Externalizable {
		private static final long serialVersionUID = 1L;

		private Object scriptAccessorKey;
		private ScriptParsingOptions parsingOptions;

		private transient ScriptAccessProvider accessProvider;

		/**
		 * For {@link Externalizable}.
		 */
		public PropertyValue() {
		}

		/**
		 * Creates a new instance initialized with the specified accessor key and parsing options.
		 * <p>
		 * The {@linkplain #getAccessProvider()} access provider attribute will be <code>null</code>.
		 * 
		 * @param scriptAccessorKey
		 *            The accessor key.
		 * @param parsingOptions
		 *            The parsing options.
		 * @throws NullPointerException
		 *             If any of the arguments are <code>null</code>.
		 */
		protected PropertyValue(Object scriptAccessorKey, ScriptParsingOptions parsingOptions)
				throws NullPointerException {
			Objects.requireNonNull(scriptAccessorKey, "script accessor key");
			Objects.requireNonNull(parsingOptions, "parsing options");
			this.scriptAccessorKey = scriptAccessorKey;
			this.parsingOptions = parsingOptions;
		}

		protected PropertyValue(Object scriptAccessorKey, ScriptParsingOptions parsingOptions,
				ScriptAccessProvider accessProvider) throws NullPointerException {
			Objects.requireNonNull(scriptAccessorKey, "script accessor key");
			Objects.requireNonNull(parsingOptions, "parsing options");
			Objects.requireNonNull(accessProvider, "script access provider");
			this.scriptAccessorKey = scriptAccessorKey;
			this.parsingOptions = parsingOptions;
			this.accessProvider = accessProvider;
		}

		/**
		 * Gets the script language access provider which can be used to operate on the associated script.
		 * <p>
		 * <b>Note:</b> This is a transient property, meaning that it won't be persisted between serialization and
		 * deserialization of this object. This is usually not an issue, as tasks should operate on the instance which
		 * was evaluated during build execution.
		 * 
		 * @return The script access provider for the associated script path.
		 * @see ExecutionContext#getExecutionPropertyCurrentValue(ExecutionProperty)
		 */
		public ScriptAccessProvider getAccessProvider() {
			return accessProvider;
		}

		/**
		 * Gets the parsing options to be used when parsing the associated script.
		 * 
		 * @return The parsing options.
		 */
		public ScriptParsingOptions getParsingOptions() {
			return parsingOptions;
		}

		/**
		 * Gets the script accessor key of the access provider.
		 * 
		 * @return The accessor key.
		 * @see ScriptAccessProvider#getScriptAccessorKey()
		 */
		public Object getScriptAccessorKey() {
			return scriptAccessorKey;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(scriptAccessorKey);
			out.writeObject(parsingOptions);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			scriptAccessorKey = in.readObject();
			parsingOptions = (ScriptParsingOptions) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((parsingOptions == null) ? 0 : parsingOptions.hashCode());
			result = prime * result + ((scriptAccessorKey == null) ? 0 : scriptAccessorKey.hashCode());
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
			PropertyValue other = (PropertyValue) obj;
			if (parsingOptions == null) {
				if (other.parsingOptions != null)
					return false;
			} else if (!parsingOptions.equals(other.parsingOptions))
				return false;
			if (scriptAccessorKey == null) {
				if (other.scriptAccessorKey != null)
					return false;
			} else if (!scriptAccessorKey.equals(other.scriptAccessorKey))
				return false;
			return true;
		}

	}

	private SakerPath scriptPath;

	/**
	 * For {@link Externalizable}.
	 */
	public ScriptParsingConfigurationExecutionProperty() {
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
	public ScriptParsingConfigurationExecutionProperty(SakerPath scriptPath)
			throws NullPointerException, InvalidPathFormatException {
		SakerPathFiles.requireAbsolutePath(scriptPath, "script path");
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
	public PropertyValue getCurrentValue(ExecutionContext executioncontext) {
		ExecutionScriptConfiguration scriptconfiguration = executioncontext.getScriptConfiguration();
		ScriptOptionsConfig scriptconfig = scriptconfiguration.getScriptOptionsConfig(scriptPath);
		if (scriptconfig == null) {
			return null;
		}
		ScriptProviderLocation locator = scriptconfig.getProviderLocation();
		ScriptAccessProvider accessprovider = executioncontext.getLoadedScriptAccessProvider(locator);
		if (accessprovider == null) {
			return null;
		}
		ScriptParsingOptions options = new SimpleScriptParsingOptions(scriptPath, scriptconfig.getOptions());
		return new PropertyValue(accessprovider.getScriptAccessorKey(), options, accessprovider);
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
		ScriptParsingConfigurationExecutionProperty other = (ScriptParsingConfigurationExecutionProperty) obj;
		if (scriptPath == null) {
			if (other.scriptPath != null)
				return false;
		} else if (!scriptPath.equals(other.scriptPath))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (scriptPath != null ? "scriptPath=" + scriptPath : "") + "]";
	}

}
