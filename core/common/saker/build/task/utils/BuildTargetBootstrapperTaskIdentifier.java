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
import java.util.Collections;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;

import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.TaskInvocationConfiguration;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

class BuildTargetBootstrapperTaskIdentifier implements Externalizable, TaskIdentifier {
	private static final long serialVersionUID = 1L;

	SakerPath buildFilePath;
	String buildTargetName;

	/**
	 * Value types are either literal objects
	 * ({@link #createWithLiteralParameters(SakerPath, String, NavigableMap, SakerPath, SakerPath)
	 * createWithLiteralParameters}) or {@link TaskIdentifier TaskIdentifiers}
	 * ({@link #create(SakerPath, String, NavigableMap, SakerPath, SakerPath) create}).
	 */
	protected NavigableMap<String, ?> parameters;
	SakerPath workingDirectory;
	SakerPath buildDirectory;

	/**
	 * For {@link Externalizable}.
	 */
	public BuildTargetBootstrapperTaskIdentifier() {
	}

	BuildTargetBootstrapperTaskIdentifier(SakerPath buildFilePath, String buildTargetName, SakerPath workingDirectory,
			SakerPath buildDirectory) throws NullPointerException, InvalidPathFormatException {
		this.buildFilePath = buildFilePath;
		this.buildTargetName = buildTargetName;
		this.workingDirectory = workingDirectory;
		this.buildDirectory = buildDirectory;
	}

	/**
	 * Gets the parameters for the build target.
	 * <p>
	 * The resolution of the parameters may cause some tasks related to them being started.
	 * 
	 * @param taskcontext
	 *            The task context the build target is being bootstrapped in.
	 * @return The parameters.
	 * @see #createWithLiteralParameters(SakerPath, String, NavigableMap, SakerPath, SakerPath)
	 */
	@SuppressWarnings("unchecked")
	public NavigableMap<String, ? extends TaskIdentifier> getParameters(TaskContext taskcontext) {
		return (NavigableMap<String, ? extends TaskIdentifier>) parameters;
	}

	static BuildTargetBootstrapperTaskIdentifier create(SakerPath buildFilePath, String buildTargetName,
			NavigableMap<String, ? extends TaskIdentifier> parameters, SakerPath workingDirectory,
			SakerPath buildDirectory) throws NullPointerException, InvalidPathFormatException {
		BuildTargetBootstrapperTaskIdentifier result = new BuildTargetBootstrapperTaskIdentifier(buildFilePath,
				buildTargetName, workingDirectory, buildDirectory);
		result.parameters = ObjectUtils.isNullOrEmpty(parameters) ? Collections.emptyNavigableMap()
				: ImmutableUtils.makeImmutableNavigableMap(parameters);
		return result;
	}

	static BuildTargetBootstrapperTaskIdentifier createWithLiteralParameters(SakerPath buildFilePath,
			String buildTargetName, NavigableMap<String, ?> parameters, SakerPath workingDirectory,
			SakerPath buildDirectory) throws NullPointerException, InvalidPathFormatException {
		if (ObjectUtils.isNullOrEmpty(parameters)) {
			return create(buildFilePath, buildTargetName, null, workingDirectory, buildDirectory);
		}
		BuildTargetBootstrapperTaskIdentifier result = new ObjectParameterizedBuildTargetBootstrapperTaskIdentifier(
				buildFilePath, buildTargetName, workingDirectory, buildDirectory);
		result.parameters = ObjectUtils.isNullOrEmpty(parameters) ? Collections.emptyNavigableMap()
				: ImmutableUtils.makeImmutableNavigableMap(parameters);
		return result;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((buildFilePath == null) ? 0 : buildFilePath.hashCode());
		result = prime * result + ((buildTargetName == null) ? 0 : buildTargetName.hashCode());
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

	private static class ObjectParameterizedBuildTargetBootstrapperTaskIdentifier
			extends BuildTargetBootstrapperTaskIdentifier {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public ObjectParameterizedBuildTargetBootstrapperTaskIdentifier() {
		}

		public ObjectParameterizedBuildTargetBootstrapperTaskIdentifier(SakerPath buildFilePath, String buildTargetName,
				SakerPath workingDirectory, SakerPath buildDirectory)
				throws NullPointerException, InvalidPathFormatException {
			super(buildFilePath, buildTargetName, workingDirectory, buildDirectory);
		}

		@Override
		public NavigableMap<String, ? extends TaskIdentifier> getParameters(TaskContext taskcontext) {
			TreeMap<String, TaskIdentifier> result = new TreeMap<>();
			for (Entry<String, ?> entry : this.parameters.entrySet()) {
				BuildTargetParameterLiteralTaskFactory paramtaskfactory = new BuildTargetParameterLiteralTaskFactory(
						entry.getValue());
				taskcontext.startTask(paramtaskfactory, paramtaskfactory, null);
				result.put(entry.getKey(), paramtaskfactory);
			}
			return result;
		}

	}

	private static final class BuildTargetParameterLiteralTaskFactory
			implements TaskFactory<Object>, Task<Object>, TaskIdentifier, Externalizable {
		private static final long serialVersionUID = 1L;

		private Object value;

		/**
		 * For {@link Externalizable}.
		 */
		public BuildTargetParameterLiteralTaskFactory() {
		}

		public BuildTargetParameterLiteralTaskFactory(Object value) {
			this.value = value;
		}

		@Override
		public TaskInvocationConfiguration getInvocationConfiguration() {
			return TaskInvocationConfiguration.INSTANCE_SHORT_TASK;
		}

		@Override
		public Task<? extends Object> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			return value;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(value);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			value = in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((value == null) ? 0 : value.hashCode());
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
			BuildTargetParameterLiteralTaskFactory other = (BuildTargetParameterLiteralTaskFactory) obj;
			if (value == null) {
				if (other.value != null)
					return false;
			} else if (!value.equals(other.value))
				return false;
			return true;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder(getClass().getSimpleName());
			builder.append("[value=");
			builder.append(value);
			builder.append("]");
			return builder.toString();
		}
	}

}