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
package saker.build.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.identifier.TaskIdentifier;

/**
 * Specifies exeucution parameters for newly started tasks.
 * 
 * @see TaskContext#startTask(TaskIdentifier, TaskFactory, TaskExecutionParameters)
 */
public final class TaskExecutionParameters implements Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * The path to the working directory of the created task.
	 * <p>
	 * An absolute or relative path to the working directory.
	 */
	protected SakerPath workingDirectory;
	/**
	 * The path to the build directory of the created task.
	 * <p>
	 * A relative path based on the execution build directory or absolute that is under the execution build directory.
	 */
	protected SakerPath buildDirectory;

	/**
	 * Creates an instance with default values.
	 */
	public TaskExecutionParameters() {
	}

	/**
	 * Gets the working directory.
	 * 
	 * @return The working directory.
	 * @see #setWorkingDirectory(SakerPath)
	 */
	public SakerPath getWorkingDirectory() {
		return workingDirectory;
	}

	/**
	 * Sets the working directory.
	 * <p>
	 * The working directory can be set to a relative or absolute path.
	 * <p>
	 * If the path is relative, then it will be resolved against the currently executing task. (Which is the one
	 * starting the task associated with this parameters.)
	 * <p>
	 * If the path is absolute, it will be used by resolving against one of the execution root directories.
	 * <p>
	 * If <code>null</code>, then the working directory of the currently executing task will be used. (i.e. it is
	 * unchanged for the spawned task)
	 * 
	 * @param workingDirectory
	 *            The working directory to set.
	 * @see TaskContext#getTaskWorkingDirectory()
	 */
	public void setWorkingDirectory(SakerPath workingDirectory) {
		this.workingDirectory = workingDirectory;
	}

	/**
	 * Gets the build directory.
	 * 
	 * @return The build directory.
	 * @see #setBuildDirectory(SakerPath)
	 */
	public SakerPath getBuildDirectory() {
		return buildDirectory;
	}

	/**
	 * Sets the build directory.
	 * <p>
	 * The build directory can be set to a relative path. It will be resolved against the execution build directory.
	 * <b>Not</b> against the build directory of the currently executing task. (Which is the one starting the task
	 * associated with this parameters.)
	 * <p>
	 * If <code>null</code>, then the build directory of the currently executing task will be used. (i.e. it is
	 * unchanged for the spawned task)
	 * 
	 * @param buildDirectory
	 *            The build directory to set.
	 * @throws InvalidPathFormatException
	 *             If the argument is not relative, or not forward relative.
	 * @see ExecutionContext#getExecutionBuildDirectory()
	 * @see SakerPath#isForwardRelative()
	 */
	public void setBuildDirectory(SakerPath buildDirectory) throws InvalidPathFormatException {
		if (buildDirectory == null) {
			this.buildDirectory = null;
		} else {
			SakerPathFiles.requireRelativePath(buildDirectory, "build directory");
			if (!buildDirectory.isForwardRelative()) {
				throw new InvalidPathFormatException("Path is not forward relative: " + buildDirectory);
			}
			this.buildDirectory = buildDirectory;
		}
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(workingDirectory);
		out.writeObject(buildDirectory);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		workingDirectory = (SakerPath) in.readObject();
		buildDirectory = (SakerPath) in.readObject();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "["
				+ (workingDirectory != null ? "workingDirectory=" + workingDirectory + ", " : "")
				+ (buildDirectory != null ? "buildDirectory=" + buildDirectory : "") + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((buildDirectory == null) ? 0 : buildDirectory.hashCode());
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
		TaskExecutionParameters other = (TaskExecutionParameters) obj;
		if (buildDirectory == null) {
			if (other.buildDirectory != null)
				return false;
		} else if (!buildDirectory.equals(other.buildDirectory))
			return false;
		if (workingDirectory == null) {
			if (other.workingDirectory != null)
				return false;
		} else if (!workingDirectory.equals(other.workingDirectory))
			return false;
		return true;
	}
}
