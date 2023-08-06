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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;

import saker.apiextract.api.PublicApi;
import saker.build.exception.BuildTargetNotFoundException;
import saker.build.exception.InvalidPathFormatException;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.SakerLog;
import saker.build.runtime.execution.TargetConfiguration;
import saker.build.scripting.ScriptParsingFailedException;
import saker.build.task.BuildTargetTask;
import saker.build.task.BuildTargetTaskFactory;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionParameters;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.BuildFileTargetTaskIdentifier;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;

/**
 * Bootstrapper task for invoking a build target of a build script with the given parameters.
 * <p>
 * This task can be used to start a build target with a specific name in a build script. This task handles the
 * dependency management, parsing, and execution of the specified build target.
 * <p>
 * The static method {@link #runBootstrapping} can be called from other tasks to avoid starting this bootstrapper task
 * and still be able to invoke a build target in the same way. In that case the dependencies will be reported in the
 * caller task.
 * <p>
 * The task identifier returned by {@link #getTaskIdentifier} should be used when starting an instance of this task
 * factory.
 */
@PublicApi
public final class BuildTargetBootstrapperTaskFactory
		implements TaskFactory<StructuredObjectTaskResult>, Task<StructuredObjectTaskResult>, Externalizable {
	private static final long serialVersionUID = 1L;

	private BuildTargetBootstrapperTaskIdentifier taskId;

	/**
	 * For {@link Externalizable}.
	 */
	public BuildTargetBootstrapperTaskFactory() {
	}

	/**
	 * Creates a new instance with the given parameters.
	 * 
	 * @deprecated Use {@link #create(SakerPath, String, NavigableMap, SakerPath, SakerPath) create} instead from
	 *                 saker.build version 0.8.18.
	 * @param buildFilePath
	 *            An absolute path to the build script.
	 * @param buildTargetName
	 *            The build target name to invoke. May be <code>null</code> to
	 *            {@linkplain TaskUtils#chooseDefaultTargetName(Collection) choose the default}.
	 * @param parameters
	 *            The task parameters to pass to the build target. May be <code>null</code> or empty.
	 * @param workingDirectory
	 *            The absolute working directory for the invoked task. If <code>null</code>, the parent path of the
	 *            build file is used.
	 * @param buildDirectory
	 *            The relative build directory path.
	 * @throws NullPointerException
	 *             If argument nullability is violated.
	 * @throws InvalidPathFormatException
	 *             If path requirements are violated.
	 */
	@Deprecated
	public BuildTargetBootstrapperTaskFactory(SakerPath buildFilePath, String buildTargetName,
			NavigableMap<String, ? extends TaskIdentifier> parameters, SakerPath workingDirectory,
			SakerPath buildDirectory) throws NullPointerException, InvalidPathFormatException {
		workingDirectory = checkPaths(buildFilePath, workingDirectory, buildDirectory);

		this.taskId = BuildTargetBootstrapperTaskIdentifier.create(buildFilePath, buildTargetName, parameters,
				workingDirectory, buildDirectory);
	}

	private BuildTargetBootstrapperTaskFactory(BuildTargetBootstrapperTaskIdentifier taskId) {
		this.taskId = taskId;
	}

	private static SakerPath checkPaths(SakerPath buildFilePath, SakerPath workingDirectory, SakerPath buildDirectory) {
		SakerPathFiles.requireAbsolutePath(buildFilePath);
		SakerPathFiles.requireRelativePath(buildDirectory);
		if (workingDirectory == null) {
			workingDirectory = SakerPathFiles.requireParent(buildFilePath);
		} else {
			//we dont need to check if the path is absolute, as requiring a parent will result in an absolute path.
			SakerPathFiles.requireAbsolutePath(workingDirectory);
		}
		return workingDirectory;
	}

	/**
	 * Creates a new instance with the given parameters.
	 * 
	 * @param buildFilePath
	 *            An absolute path to the build script.
	 * @param buildTargetName
	 *            The build target name to invoke. May be <code>null</code> to
	 *            {@linkplain TaskUtils#chooseDefaultTargetName(Collection) choose the default}.
	 * @param parameters
	 *            The task parameters to pass to the build target. May be <code>null</code> or empty.
	 * @param workingDirectory
	 *            The absolute working directory for the invoked task. If <code>null</code>, the parent path of the
	 *            build file is used.
	 * @param buildDirectory
	 *            The relative build directory path.
	 * @throws NullPointerException
	 *             If argument nullability is violated.
	 * @throws InvalidPathFormatException
	 *             If path requirements are violated.
	 * @since saker.build 0.8.18
	 */
	public static BuildTargetBootstrapperTaskFactory create(SakerPath buildFilePath, String buildTargetName,
			NavigableMap<String, ? extends TaskIdentifier> parameters, SakerPath workingDirectory,
			SakerPath buildDirectory) throws NullPointerException, InvalidPathFormatException {
		workingDirectory = checkPaths(buildFilePath, workingDirectory, buildDirectory);

		return new BuildTargetBootstrapperTaskFactory(BuildTargetBootstrapperTaskIdentifier.create(buildFilePath,
				buildTargetName, parameters, workingDirectory, buildDirectory));
	}

	/**
	 * Creates a new instance with the given literal parameters.
	 * <p>
	 * The object in the parameters map will be passed as parameters to the build target. In order to achieve this, the
	 * bootstrapper will start new tasks for these literal object parameters to conform to the {@link BuildTargetTask}
	 * interface.
	 * <p>
	 * The parameters literal objects should implement the {@link Object#equals(Object)} and {@link Object#hashCode()}
	 * functions properly.
	 * 
	 * @param buildFilePath
	 *            An absolute path to the build script.
	 * @param buildTargetName
	 *            The build target name to invoke. May be <code>null</code> to
	 *            {@linkplain TaskUtils#chooseDefaultTargetName(Collection) choose the default}.
	 * @param parameters
	 *            The literal object parameters to pass to the build target. May be <code>null</code> or empty.
	 * @param workingDirectory
	 *            The absolute working directory for the invoked task. If <code>null</code>, the parent path of the
	 *            build file is used.
	 * @param buildDirectory
	 *            The relative build directory path.
	 * @throws NullPointerException
	 *             If argument nullability is violated.
	 * @throws InvalidPathFormatException
	 *             If path requirements are violated.
	 * @since saker.build 0.8.18
	 */
	public static BuildTargetBootstrapperTaskFactory createWithLiteralParameters(SakerPath buildFilePath,
			String buildTargetName, NavigableMap<String, ?> parameters, SakerPath workingDirectory,
			SakerPath buildDirectory) throws NullPointerException, InvalidPathFormatException {
		workingDirectory = checkPaths(buildFilePath, workingDirectory, buildDirectory);

		return new BuildTargetBootstrapperTaskFactory(BuildTargetBootstrapperTaskIdentifier.createWithLiteralParameters(
				buildFilePath, buildTargetName, parameters, workingDirectory, buildDirectory));
	}

	/**
	 * Gets the task identifier which should be used when starting an instance of
	 * {@link BuildTargetBootstrapperTaskFactory}.
	 * 
	 * @param task
	 *            The task to get the task identifier for.
	 * @return The task identifier.
	 */
	public static TaskIdentifier getTaskIdentifier(BuildTargetBootstrapperTaskFactory task) {
		return task.taskId;
	}

	@Override
	public Task<? extends StructuredObjectTaskResult> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public StructuredObjectTaskResult run(TaskContext taskcontext) throws Exception {
		TaskIdentifier runnertaskid = runBootstrappingImpl(taskcontext, this.taskId.buildFilePath,
				this.taskId.buildTargetName, this.taskId.getParameters(taskcontext), this.taskId.workingDirectory,
				this.taskId.buildDirectory);
		return new SimpleStructuredObjectTaskResult(runnertaskid);
	}

	/**
	 * Executes the bootstrapping of a build target task in the given task context.
	 * <p>
	 * Dependencies will be reported for the build script, and script parsing options.
	 * 
	 * @param taskcontext
	 *            The task context.
	 * @param buildFilePath
	 *            The path to the build file. Relative paths are resolved against the working directory of the task
	 *            context.
	 * @param buildTargetName
	 *            The name of the build target to invoke or <code>null</code> to
	 *            {@linkplain TaskUtils#chooseDefaultTargetName(Collection) choose the default}.
	 * @param parameters
	 *            The task parameters to invoke the build target with.
	 * @param workingDirectory
	 *            The working directory. If <code>null</code>, the parent path of the build file is used.
	 * @param buildDirectory
	 *            A relative path to the build directory to use for the invoked task. It is relative to the
	 *            {@linkplain ExecutionContext#getExecutionBuildDirectory() execution build directory}.
	 * @return The task identifier of the started build target task.
	 * @throws InvalidPathFormatException
	 *             If path requirements are violated.
	 * @throws Exception
	 *             If the build target bootstrapping fails.
	 * @see ExecutionContext#getTargetConfiguration(TaskContext, SakerFile)
	 * @see TaskExecutionParameters#setBuildDirectory(SakerPath)
	 */
	public static BuildFileTargetTaskIdentifier runBootstrapping(TaskContext taskcontext, SakerPath buildFilePath,
			String buildTargetName, NavigableMap<String, ? extends TaskIdentifier> parameters,
			SakerPath workingDirectory, SakerPath buildDirectory) throws InvalidPathFormatException, Exception {
		//we allow the build file path to be relative in the public bootstrapping method, 
		//    as it will be then resolved against the current task working directory

		SakerPathFiles.requireRelativePath(buildDirectory);
		if (workingDirectory == null) {
			workingDirectory = SakerPathFiles.requireParent(buildFilePath);
		}

		return runBootstrappingImpl(taskcontext, buildFilePath, buildTargetName, parameters, workingDirectory,
				buildDirectory);
	}

	private static BuildFileTargetTaskIdentifier runBootstrappingImpl(TaskContext taskcontext, SakerPath buildFilePath,
			String buildTargetName, NavigableMap<String, ? extends TaskIdentifier> parameters,
			SakerPath workingDirectory, SakerPath buildDirectory)
			throws FileNotFoundException, IOException, ScriptParsingFailedException {
		Objects.requireNonNull(taskcontext, "task context");
		ExecutionContext execcontext = taskcontext.getExecutionContext();
		SakerFile buildfile = taskcontext.getTaskUtilities().resolveAtPath(buildFilePath);
		if (buildfile == null) {
			throw new BuildTargetNotFoundException("Build script file not found at: " + buildFilePath);
		}
		taskcontext.getTaskUtilities().reportInputFileDependency(null, buildfile);
		TargetConfiguration tc = execcontext.getTargetConfiguration(taskcontext, buildfile);
		String targetname;
		if (buildTargetName == null) {
			Set<String> targetnames = tc.getTargetNames();
			targetname = TaskUtils.chooseDefaultTargetName(targetnames);
			if (targetname == null) {
				throw new BuildTargetNotFoundException("Failed to determine build target to invoke in: " + buildFilePath
						+ " Available targets: " + targetnames);
			}
		} else {
			targetname = buildTargetName;
		}
		BuildTargetTaskFactory bttask = tc.getTask(targetname);
		if (bttask == null) {
			throw new BuildTargetNotFoundException("Build target not found with name: " + targetname + " in file: "
					+ buildFilePath + " Available targets: " + StringUtils.toStringJoin(", ", tc.getTargetNames()));
		}
		NavigableSet<String> targetparamnames = bttask.getTargetInputParameterNames();
		if (targetparamnames != null) {
			//note: do not remove parameters based on the TargetInputParameterNames, pass the additional ones too
			//		to the BuildTargetTask
			final String ftargetname = targetname;
			ObjectUtils.iterateOrderedIterables(targetparamnames, parameters.navigableKeySet(), (l, r) -> {
				if (l == null) {
					SakerLog.warning().println("Target " + ftargetname + " in file " + buildfile.getSakerPath()
							+ " has no parameter named: " + r);
				}
			});
		}

		SakerPath taskworkingdir = taskcontext.getTaskWorkingDirectory().getSakerPath();
		if (workingDirectory == null) {
			workingDirectory = taskworkingdir;
		} else if (workingDirectory.isRelative()) {
			workingDirectory = taskworkingdir.resolve(workingDirectory);
		}

		TaskExecutionParameters execparams = new TaskExecutionParameters();
		execparams.setBuildDirectory(buildDirectory);
		execparams.setWorkingDirectory(workingDirectory);

		if (buildFilePath.isRelative()) {
			buildFilePath = taskworkingdir.resolve(buildFilePath);
		}

		BuildFileTargetTaskIdentifier runnertaskid = new BuildFileTargetTaskIdentifier(targetname, buildFilePath,
				workingDirectory, parameters);
		taskcontext.startTask(runnertaskid, new BuildTargetRunnerTaskFactory(bttask, parameters), execparams);
		return runnertaskid;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((taskId == null) ? 0 : taskId.hashCode());
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
		BuildTargetBootstrapperTaskFactory other = (BuildTargetBootstrapperTaskFactory) obj;
		if (taskId == null) {
			if (other.taskId != null)
				return false;
		} else if (!taskId.equals(other.taskId))
			return false;
		return true;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(taskId);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		taskId = (BuildTargetBootstrapperTaskIdentifier) in.readObject();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (taskId != null ? "taskId=" + taskId : "") + "]";
	}

}