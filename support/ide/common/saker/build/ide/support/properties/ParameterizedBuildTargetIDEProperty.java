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
import java.util.UUID;

import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class ParameterizedBuildTargetIDEProperty {
	/**
	 * An unique id (likely {@link UUID}) of this parameterized build target to uniquely identify it in the IDE
	 * properties.
	 */
	private String uuid;
	private String scriptPath;
	private String targetName;

	private String displayName;

	private NavigableMap<String, String> buildTargetParameters;

	public ParameterizedBuildTargetIDEProperty(String uuid, String scriptPath, String targetName, String displayName) {
		this.uuid = uuid;
		this.scriptPath = scriptPath;
		this.targetName = targetName;
		this.displayName = displayName;
		this.buildTargetParameters = Collections.emptyNavigableMap();
	}

	public ParameterizedBuildTargetIDEProperty(String uuid, String scriptPath, String targetName, String displayName,
			Map<String, String> buildTargetParameters) {
		this.uuid = uuid;
		this.scriptPath = scriptPath;
		this.targetName = targetName;
		this.displayName = displayName;
		this.buildTargetParameters = ObjectUtils.isNullOrEmpty(buildTargetParameters) ? Collections.emptyNavigableMap()
				: ImmutableUtils.makeImmutableNavigableMap(buildTargetParameters);
	}

	public String getUuid() {
		return uuid;
	}

	/**
	 * Gets the path of the build script.
	 * <p>
	 * The script path is relative to the project root directory. (This relativeness is required so that the
	 * parameterized build target configuration is independent of the execution path configuration. Therefore the path
	 * configuration can be freely changed without the need to change these path values.)
	 * 
	 * @return The path.
	 */
	public String getScriptPath() {
		return scriptPath;
	}

	public String getTargetName() {
		return targetName;
	}

	public String getDisplayName() {
		return displayName;
	}

	public NavigableMap<String, String> getBuildTargetParameters() {
		return buildTargetParameters;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((uuid == null) ? 0 : uuid.hashCode());
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
		if (displayName == null) {
			if (other.displayName != null)
				return false;
		} else if (!displayName.equals(other.displayName))
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
		if (uuid == null) {
			if (other.uuid != null)
				return false;
		} else if (!uuid.equals(other.uuid))
			return false;
		return true;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder(getClass().getSimpleName());
		builder.append("[");
		if (displayName != null) {
			builder.append(displayName);
			builder.append(" (");
			builder.append(targetName);
			builder.append("@");
			builder.append(scriptPath);
			builder.append(')');
		} else {
			builder.append(targetName);
			builder.append("@");
			builder.append(scriptPath);
		}
		if (uuid != null) {
			builder.append(", uuid=");
			builder.append(uuid);
		}
		if (!ObjectUtils.isNullOrEmpty(buildTargetParameters)) {
			builder.append(", buildTargetParameters=");
			builder.append(buildTargetParameters);
		}
		builder.append("]");
		return builder.toString();
	}

}
