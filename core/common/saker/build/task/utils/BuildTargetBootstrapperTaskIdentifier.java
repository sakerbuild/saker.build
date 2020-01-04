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
package saker.build.task.utils;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableMap;

import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

final class BuildTargetBootstrapperTaskIdentifier implements Externalizable, TaskIdentifier {
	private static final long serialVersionUID = 1L;

	SakerPath buildFilePath;
	String buildTargetName;

	NavigableMap<String, ? extends TaskIdentifier> parameters;
	SakerPath workingDirectory;
	SakerPath buildDirectory;

	/**
	 * For {@link Externalizable}.
	 */
	public BuildTargetBootstrapperTaskIdentifier() {
	}

	public BuildTargetBootstrapperTaskIdentifier(SakerPath buildFilePath, String buildTargetName,
			NavigableMap<String, ? extends TaskIdentifier> parameters, SakerPath workingDirectory,
			SakerPath buildDirectory) throws NullPointerException, InvalidPathFormatException {
		this.buildFilePath = buildFilePath;
		this.buildTargetName = buildTargetName;
		this.parameters = parameters;
		this.workingDirectory = workingDirectory;
		this.buildDirectory = buildDirectory;
	}

	public static TaskIdentifier getTaskIdentifier(BuildTargetBootstrapperTaskIdentifier task) {
		return task;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((buildDirectory == null) ? 0 : buildDirectory.hashCode());
		result = prime * result + ((buildFilePath == null) ? 0 : buildFilePath.hashCode());
		result = prime * result + ((buildTargetName == null) ? 0 : buildTargetName.hashCode());
		result = prime * result + ((parameters == null) ? 0 : parameters.hashCode());
		result = prime * result + ((workingDirectory == null) ? 0 : workingDirectory.hashCode());
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
		BuildTargetBootstrapperTaskIdentifier other = (BuildTargetBootstrapperTaskIdentifier) obj;
		if (buildDirectory == null) {
			if (other.buildDirectory != null)
				return false;
		} else if (!buildDirectory.equals(other.buildDirectory))
			return false;
		if (buildFilePath == null) {
			if (other.buildFilePath != null)
				return false;
		} else if (!buildFilePath.equals(other.buildFilePath))
			return false;
		if (buildTargetName == null) {
			if (other.buildTargetName != null)
				return false;
		} else if (!buildTargetName.equals(other.buildTargetName))
			return false;
		if (parameters == null) {
			if (other.parameters != null)
				return false;
		} else if (!parameters.equals(other.parameters))
			return false;
		if (workingDirectory == null) {
			if (other.workingDirectory != null)
				return false;
		} else if (!workingDirectory.equals(other.workingDirectory))
			return false;
		return true;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(buildFilePath);
		out.writeObject(buildTargetName);
		SerialUtils.writeExternalMap(out, parameters);
		out.writeObject(workingDirectory);
		out.writeObject(buildDirectory);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		buildFilePath = (SakerPath) in.readObject();
		buildTargetName = (String) in.readObject();
		parameters = SerialUtils.readExternalSortedImmutableNavigableMap(in);
		workingDirectory = (SakerPath) in.readObject();
		buildDirectory = (SakerPath) in.readObject();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (buildFilePath != null ? "buildFilePath=" + buildFilePath + ", " : "")
				+ (buildTargetName != null ? "buildTargetName=" + buildTargetName + ", " : "")
				+ (!ObjectUtils.isNullOrEmpty(parameters) ? "parameters=" + parameters + ", " : "")
				+ (workingDirectory != null ? "workingDirectory=" + workingDirectory + ", " : "")
				+ (buildDirectory != null ? "buildDirectory=" + buildDirectory : "") + "]";
	}

}