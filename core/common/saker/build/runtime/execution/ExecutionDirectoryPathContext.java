package saker.build.runtime.execution;

import java.util.NavigableSet;

import saker.build.file.SakerDirectory;
import saker.build.file.path.SakerPath;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.task.TaskContext;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.util.rmi.wrap.RMITreeSetStringElementWrapper;

/**
 * Container providing acces to the base directory paths used during build execution.
 * 
 * @see SakerDirectory
 * @see ExecutionDirectoryContext
 * @see ExecutionContext
 */
public interface ExecutionDirectoryPathContext {
	/**
	 * Gets the absolute path to the base execution working directory.
	 * <p>
	 * The working directory is the same as it was configured by the user at the start of the build execution.
	 * Generally, it is recommended for task implementations to use the
	 * {@linkplain TaskContext#getTaskWorkingDirectoryPath() task working directory} instead.
	 * 
	 * @return The execution working directory.
	 * @see ExecutionPathConfiguration
	 */
	@RMICacheResult
	public SakerPath getExecutionWorkingDirectoryPath();

	/**
	 * Gets the absolute path to the base build directory for the build execution.
	 * <p>
	 * The execution directory path is the same as it was configured by the user at the start of the build execution. It
	 * may be <code>null</code>, if no build directory was specified.
	 * <p>
	 * Generally, task implementations should use the {@linkplain TaskContext#getTaskBuildDirectoryPath() task build
	 * directory} instead.
	 * 
	 * @return The absolute build directory path or <code>null</code> if not configured.
	 */
	@RMICacheResult
	public SakerPath getExecutionBuildDirectoryPath();

	/**
	 * Gets the names of the root directories for the build execution.
	 * <p>
	 * The names of the root directories are specified by the user at the start of the build execution. These root names
	 * should be used to resolve execution paths during the build.
	 * 
	 * @return An immutable set of root directory names.
	 * @see ExecutionPathConfiguration
	 */
	@RMICacheResult
	@RMIWrap(RMITreeSetStringElementWrapper.class)
	public NavigableSet<String> getRootDirectoryNames();
}
