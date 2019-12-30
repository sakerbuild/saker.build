package saker.build.task.dependencies;

import java.io.Externalizable;
import java.util.NavigableMap;

import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionDirectoryContext;
import saker.build.task.TaskContext;
import saker.build.task.TaskDirectoryContext;
import saker.build.task.TaskExecutionUtilities;
import saker.build.task.utils.dependencies.DirectoryChildrenFileCollectionStrategy;
import saker.build.task.utils.dependencies.RecursiveFileCollectionStrategy;
import saker.build.task.utils.dependencies.WildcardFileCollectionStrategy;

/**
 * Strategy interface for collecting and discovering files for task execution.
 * <p>
 * Implementations are responsible for enumerating the requested files given the current execution and task directory
 * configuration.
 * <p>
 * This interface is used for detecting any addition-wise changes to the file system during incremental builds. If a
 * task is run for a given file set, and new files are added by the user, implementations of this interface can detect
 * the addition of the new files, and a rerun will be triggered for the task with appropriate deltas.
 * <p>
 * Implementations can collect files and directories alike as they wish.
 * <p>
 * Subclasses should satisfy the {@link #equals(Object)} and {@link #hashCode()} contract.
 * <p>
 * It is strongly recommended that subclasses implement the {@link Externalizable} interface.
 * <p>
 * Examples: <br>
 * <ul>
 * <li>Collect files in a given directory. ({@link DirectoryChildrenFileCollectionStrategy})</li>
 * <li>Collect files with a given extension, recursively. ({@link RecursiveFileCollectionStrategy})</li>
 * <li>Collect files based on a wildcard. ({@link WildcardFileCollectionStrategy})</li>
 * </ul>
 * <p>
 * It is recommended that subclasses have a static factory method (with name <code>create</code>) to instantiate an
 * appropriately made {@link FileCollectionStrategy} instance.
 * 
 * @see TaskContext#reportInputFileAdditionDependency(Object, FileCollectionStrategy)
 */
public interface FileCollectionStrategy {
	/**
	 * Collects the files for the given configuration using this strategy.
	 * <p>
	 * Implementations should use the passed parameter to retrieve the directories of interest and collect the requested
	 * files accordingly.
	 * <p>
	 * This method should not be called directly, but through
	 * {@link TaskExecutionUtilities#collectFiles(FileCollectionStrategy)}, after constructing an appropriate
	 * {@link FileCollectionStrategy} instance.
	 * <p>
	 * Implementations should gracefully handle the case when the
	 * {@linkplain TaskDirectoryContext#getTaskWorkingDirectory() task working directory} is <code>null</code>. When it
	 * is <code>null</code>, implementations should handle that case in the same way it would handle if the directory
	 * did not exist.
	 * 
	 * @param executiondirectorycontext
	 *            The execution directory context.
	 * @param taskdirectorycontext
	 *            The task directory context.
	 * @return A naturally ordered navigable map with the discovered files, with absolute paths as keys.
	 */
	public NavigableMap<SakerPath, SakerFile> collectFiles(ExecutionDirectoryContext executiondirectorycontext,
			TaskDirectoryContext taskdirectorycontext);

	/**
	 * Check if this strategy will collect the same files as the argument given they are passed the same parameters.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj);

	@Override
	public int hashCode();
}
