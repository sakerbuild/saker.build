package saker.build.runtime.execution;

import java.util.NavigableMap;

import saker.build.file.SakerDirectory;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;

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
	public NavigableMap<String, ? extends SakerDirectory> getRootDirectories();
}
