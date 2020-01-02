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
import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;

/**
 * Container providing access to the base directory paths for a task during build execution.
 * 
 * @see SakerDirectory
 * @see TaskContext
 * @see TaskDirectoryContext
 */
public interface TaskDirectoryPathContext {
	/**
	 * Gets the path of the working directory for the associated task.
	 * <p>
	 * The returned path is the absolute execution path to the working directory of the task. This is affected by the
	 * {@linkplain TaskExecutionParameters task execution parameters}.
	 * <p>
	 * The path is the same as {@link TaskContext#getTaskWorkingDirectory()}{@link SakerDirectory#getSakerPath()
	 * .getSakerPath()} would return.
	 * <p>
	 * It can be used to resolve relative user arguments against it without actually querying the {@link SakerDirectory}
	 * instance.
	 * 
	 * @return The absolute path of the working directory.
	 */
	@RMICacheResult
	public SakerPath getTaskWorkingDirectoryPath();

	/**
	 * Gets the path of the build directory for the associated task.
	 * <p>
	 * The returned path is the absolute execution path to the build directory of the task. This is affected by the
	 * {@linkplain TaskExecutionParameters task execution parameters}. The result may be <code>null</code>, if there's
	 * no build directory configured for the task or build execution.
	 * <p>
	 * The path is the same as {@link TaskContext#getTaskBuildDirectory()}{@link SakerDirectory#getSakerPath()
	 * .getSakerPath()} would return.
	 * 
	 * @return The absolute path of the build directory or <code>null</code> if none.
	 */
	@RMICacheResult
	public SakerPath getTaskBuildDirectoryPath();
}