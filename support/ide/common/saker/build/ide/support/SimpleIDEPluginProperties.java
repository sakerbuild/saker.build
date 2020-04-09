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
package saker.build.ide.support;

import java.util.Collections;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import saker.build.ide.support.properties.IDEPluginProperties;
import saker.build.runtime.execution.SakerLog.CommonExceptionFormat;

public class SimpleIDEPluginProperties implements IDEPluginProperties {
	private static final IDEPluginProperties EMPTY = new SimpleIDEPluginProperties();

	public static IDEPluginProperties empty() {
		return EMPTY;
	}

	private String storageDirectory;
	private Set<? extends Entry<String, String>> userParameters = Collections.emptySet();
	private String exceptionFormat;

	private SimpleIDEPluginProperties() {
	}

	private SimpleIDEPluginProperties(IDEPluginProperties copy) {
		this.storageDirectory = copy.getStorageDirectory();
		this.userParameters = SakerIDEPlugin.makeImmutableEntrySet(copy.getUserParameters());
		this.exceptionFormat = copy.getExceptionFormat();
	}

	@Override
	public String getStorageDirectory() {
		return storageDirectory;
	}

	@Override
	public Set<? extends Entry<String, String>> getUserParameters() {
		return userParameters;
	}

	@Override
	public String getExceptionFormat() {
		return exceptionFormat;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static Builder builder(IDEPluginProperties copy) {
		return new Builder(copy);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((exceptionFormat == null) ? 0 : exceptionFormat.hashCode());
		result = prime * result + ((storageDirectory == null) ? 0 : storageDirectory.hashCode());
		result = prime * result + ((userParameters == null) ? 0 : userParameters.hashCode());
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
		SimpleIDEPluginProperties other = (SimpleIDEPluginProperties) obj;
		if (exceptionFormat == null) {
			if (other.exceptionFormat != null)
				return false;
		} else if (!exceptionFormat.equals(other.exceptionFormat))
			return false;
		if (storageDirectory == null) {
			if (other.storageDirectory != null)
				return false;
		} else if (!storageDirectory.equals(other.storageDirectory))
			return false;
		if (userParameters == null) {
			if (other.userParameters != null)
				return false;
		} else if (!userParameters.equals(other.userParameters))
			return false;
		return true;
	}

	public static final class Builder {
		private SimpleIDEPluginProperties result;

		public Builder() {
			result = new SimpleIDEPluginProperties();
		}

		public Builder(IDEPluginProperties copy) {
			result = new SimpleIDEPluginProperties(copy);
		}

		public Builder setStorageDirectory(String storageDirectory) {
			result.storageDirectory = storageDirectory;
			return this;
		}

		public Builder setUserParameters(Set<? extends Entry<String, String>> userParameters) {
			result.userParameters = userParameters == null ? Collections.emptySet()
					: SakerIDEPlugin.makeImmutableEntrySet(userParameters);
			return this;
		}

		/**
		 * Sets the exception format to be used to display build exceptions.
		 * <p>
		 * See {@link CommonExceptionFormat}.
		 * 
		 * @param exceptionFormat
		 *            The execption format.
		 * @return <code>this</code>
		 */
		public Builder setExceptionFormat(String exceptionFormat) {
			result.exceptionFormat = exceptionFormat;
			return this;
		}

		public Builder setExceptionFormat(CommonExceptionFormat exceptionFormat) {
			return this.setExceptionFormat(Objects.toString(exceptionFormat, null));
		}

		public IDEPluginProperties build() {
			SimpleIDEPluginProperties res = this.result;
			if (res == null) {
				throw new IllegalStateException("Builder already used.");
			}
			this.result = null;
			return res;
		}

		public IDEPluginProperties buildReuse() {
			SimpleIDEPluginProperties res = new SimpleIDEPluginProperties(this.result);
			return res;
		}
	}
}
