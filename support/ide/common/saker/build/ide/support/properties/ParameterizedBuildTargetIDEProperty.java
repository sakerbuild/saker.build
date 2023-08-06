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
import java.util.Map;
import java.util.NavigableMap;

import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class ParameterizedBuildTargetIDEProperty {
	private String scriptPath;
	private String targetName;

	private NavigableMap<String, String> buildTargetParameters;

	public ParameterizedBuildTargetIDEProperty(String scriptPath, String targetName) {
		this.scriptPath = scriptPath;
		this.targetName = targetName;
		this.buildTargetParameters = Collections.emptyNavigableMap();
	}

	public ParameterizedBuildTargetIDEProperty(String scriptPath, String targetName,
			Map<String, String> buildTargetParameters) {
		this.scriptPath = scriptPath;
		this.targetName = targetName;
		this.buildTargetParameters = ObjectUtils.isNullOrEmpty(buildTargetParameters) ? Collections.emptyNavigableMap()
				: ImmutableUtils.makeImmutableNavigableMap(buildTargetParameters);
	}

	public String getScriptPath() {
		return scriptPath;
	}

	public String getTargetName() {
		return targetName;
	}

	public Map<String, String> getBuildTargetParameters() {
		return buildTargetParameters;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((buildTargetParameters == null) ? 0 : buildTargetParameters.hashCode());
		result = prime * result + ((scriptPath == null) ? 0 : scriptPath.hashCode());
		result = prime * result + ((targetName == null) ? 0 : targetName.hashCode());
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
		ParameterizedBuildTargetIDEProperty other = (ParameterizedBuildTargetIDEProperty) obj;
		if (buildTargetParameters == null) {
			if (other.buildTargetParameters != null)
				return false;
		} else if (!buildTargetParameters.equals(other.buildTargetParameters))
			return false;
		if (scriptPath == null) {
			if (other.scriptPath != null)
				return false;
		} else if (!scriptPath.equals(other.scriptPath))
			return false;
		if (targetName == null) {
			if (other.targetName != null)
				return false;
		} else if (!targetName.equals(other.targetName))
			return false;
		return true;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder(getClass().getSimpleName());
		builder.append("[");
		builder.append(targetName);
		builder.append("@");
		builder.append(scriptPath);
		if (!ObjectUtils.isNullOrEmpty(buildTargetParameters)) {
			builder.append(", buildTargetParameters=");
			builder.append(buildTargetParameters);
		}
		builder.append("]");
		return builder.toString();
	}

}
