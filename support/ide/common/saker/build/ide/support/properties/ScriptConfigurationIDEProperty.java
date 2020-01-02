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
package saker.build.ide.support.properties;

import java.util.Collections;
import java.util.Map.Entry;
import java.util.Set;

import saker.build.ide.support.SakerIDEPlugin;

public class ScriptConfigurationIDEProperty {
	private ClassPathLocationIDEProperty classPathLocation;
	private ClassPathServiceEnumeratorIDEProperty serviceEnumerator;
	private Set<? extends Entry<String, String>> scriptOptions;
	private String scriptsWildcard;

	public ScriptConfigurationIDEProperty(String scriptsWildcard, Set<? extends Entry<String, String>> scriptOptions,
			ClassPathLocationIDEProperty classPathLocation, ClassPathServiceEnumeratorIDEProperty serviceEnumerator) {
		this.classPathLocation = classPathLocation;
		this.serviceEnumerator = serviceEnumerator;
		this.scriptOptions = scriptOptions == null ? Collections.emptySet()
				: SakerIDEPlugin.unmodifiablizeEntrySet(scriptOptions);
		this.scriptsWildcard = scriptsWildcard;
	}

	public ClassPathLocationIDEProperty getClassPathLocation() {
		return classPathLocation;
	}

	public Set<? extends Entry<String, String>> getScriptOptions() {
		return scriptOptions;
	}

	public String getScriptsWildcard() {
		return scriptsWildcard;
	}

	public ClassPathServiceEnumeratorIDEProperty getServiceEnumerator() {
		return serviceEnumerator;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((classPathLocation == null) ? 0 : classPathLocation.hashCode());
		result = prime * result + ((scriptOptions == null) ? 0 : scriptOptions.hashCode());
		result = prime * result + ((scriptsWildcard == null) ? 0 : scriptsWildcard.hashCode());
		result = prime * result + ((serviceEnumerator == null) ? 0 : serviceEnumerator.hashCode());
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
		ScriptConfigurationIDEProperty other = (ScriptConfigurationIDEProperty) obj;
		if (classPathLocation == null) {
			if (other.classPathLocation != null)
				return false;
		} else if (!classPathLocation.equals(other.classPathLocation))
			return false;
		if (scriptOptions == null) {
			if (other.scriptOptions != null)
				return false;
		} else if (!scriptOptions.equals(other.scriptOptions))
			return false;
		if (scriptsWildcard == null) {
			if (other.scriptsWildcard != null)
				return false;
		} else if (!scriptsWildcard.equals(other.scriptsWildcard))
			return false;
		if (serviceEnumerator == null) {
			if (other.serviceEnumerator != null)
				return false;
		} else if (!serviceEnumerator.equals(other.serviceEnumerator))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[classPathLocation=" + classPathLocation + ", serviceEnumerator="
				+ serviceEnumerator + ", scriptOptions=" + scriptOptions + ", scriptsWildcard=" + scriptsWildcard + "]";
	}

}
