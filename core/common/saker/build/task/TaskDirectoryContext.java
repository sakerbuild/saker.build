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

import saker.build.file.SakerDirectory;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionDirectoryContext;
import saker.build.task.dependencies.FileCollectionStrategy;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;

/**
 * Container for the base directories used by a task during execution.
 * 
 * @see SakerDirectory
 * @see TaskContext
 * @see TaskDirectoryPathContext
 */
public interface TaskDirectoryContext extends TaskDirectoryPathContext {
	/**
	 * Gets the working directory for the currently executing task.
	 * <p>
	 * The working directory is based on the execution-wide base working directory and the task execution parameters
	 * specified for the task.
	 * <p>
	 * Any relative paths used by this task should be resolved against this directory.
	 * <p>
	 * <i>Note:</i> In some cases this method may return <code>null</code>. This might be for example when the build
	 * system is detecting the deltas for a given task, and the working directory path did not resolve to an actual
	 * path. Callers in {@link FileCollectionStrategy#collectFiles(ExecutionDirectoryContext, TaskDirectoryContext)}
	 * should handle the possibility of this method returning <code>null</code>. If this method is called <i>during</i>
	 * task execution (I.e. inside {@link Task#run(TaskContext)}, then this method will never return <code>null</code>.)
	 * 
	 * @return The working directory.
	 * @see TaskExecutionParameters#getBuildDirectory()
	 * @see ExecutionContext#getExecutionWorkingDirectory()
	 */
	@RMICacheResult
	public SakerDirectory getTaskWorkingDirectory();

	/**
	 * Gets the build directory for the currently executing task or <code>null</code> if not available.
	 * <p>
	 * The build directory is based on the execution-wide base build directory and the task execution parameters
	 * specified for the task.
	 * <p>
	 * It is strongly recommended that unless explicitly specified by the user, all tasks produce outputs under the
	 * directory returned by this method.
	 * <p>
	 * To avoid checking <code>null</code> result of this method consider using
	 * {@link SakerPathFiles#requireBuildDirectory(TaskDirectoryContext)} which throws an appropriate exception if it is
	 * not available.
	 * 
	 * @return The build directory or <code>null</code> if not available.
	 * @see TaskExecutionParameters#getBuildDirectory()
	 * @see ExecutionContext#getExecutionBuildDirectory()
	 */
	@RMICacheResult
	public SakerDirectory getTaskBuildDirectory();
}
