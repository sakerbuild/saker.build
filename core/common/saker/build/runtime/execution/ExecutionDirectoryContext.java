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
package saker.build.runtime.execution;

import java.util.NavigableMap;
import java.util.NavigableSet;

import saker.build.file.SakerDirectory;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIDefaultOnFailure;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIForbidden;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.util.rmi.wrap.RMITreeMapSerializeKeyRemoteValueWrapper;

/**
 * Container for the base directories used during build execution.
 * 
 * @see SakerDirectory
 * @see ExecutionContext
 * @see ExecutionDirectoryPathContext
 */
public interface ExecutionDirectoryContext extends ExecutionDirectoryPathContext {
	/**
	 * Gets the base working directory specified for the build execution.
	 * <p>
	 * This working directory represents the one which was configured by the user. Tasks are not recommended to directly
	 * use this, as they themselves can be configured on a per-task basis.
	 * 
	 * @return The base working directory for the build execution.
	 * @see ExecutionPathConfiguration#getWorkingDirectory()
	 */
	@RMICacheResult
	public SakerDirectory getExecutionWorkingDirectory();

	/**
	 * Gets the base build directory specified for the build execution.
	 * <p>
	 * This build directory represents the one which was configured by the user. Tasks are not recommended to directly
	 * use this, as they themselves can be configured on a per-task basis.
	 * <p>
	 * To avoid checking <code>null</code> result of this method consider using
	 * {@link SakerPathFiles#requireBuildDirectory(ExecutionDirectoryContext)} which throws an appropriate exception if
	 * it is not available.
	 * 
	 * @return The base build directory for the build execution or <code>null</code> if not available.
	 */
	@RMICacheResult
	public SakerDirectory getExecutionBuildDirectory();

	/**
	 * Gets the root directories specified for the build execution.
	 * <p>
	 * This method returns the root directories which can be used to resolve absolute paths during task execution. The
	 * root names are normalized as specified by the rules of {@link SakerPath}.
	 * 
	 * @return An unmodifiable map of root directories.
	 * @see ExecutionPathConfiguration#getRootNames()
	 * @see SakerPath#normalizeRoot(String)
	 */
	@RMICacheResult
	@RMIWrap(RMITreeMapSerializeKeyRemoteValueWrapper.class)
	public NavigableMap<String, ? extends SakerDirectory> getRootDirectories();

	//override so won't be unnecessarily called through rmi
	//also because this is how it should work
	@RMIForbidden
	@RMIDefaultOnFailure
	@Override
	public default NavigableSet<String> getRootDirectoryNames() {
		return getRootDirectories().navigableKeySet();
	}
}
