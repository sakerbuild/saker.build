package saker.build.task;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;

import saker.build.exception.InvalidFileTypeException;
import saker.build.exception.InvalidPathFormatException;
import saker.build.exception.PropertyComputationFailedException;
import saker.build.exception.TaskIdentifierExceptionView;
import saker.build.file.DirectoryVisitPredicate;
import saker.build.file.RemoteExecutionSakerFileRMIWrapper;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.PathKey;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.execution.ExecutionDirectoryContext;
import saker.build.runtime.execution.ExecutionProperty;
import saker.build.task.dependencies.FileCollectionStrategy;
import saker.build.task.exception.IllegalTaskOperationException;
import saker.build.task.exception.TaskExecutionFailedException;
import saker.build.task.exception.TaskIdentifierConflictException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIForbidden;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.rmi.wrap.RMIArrayListRemoteElementWrapper;
import saker.build.thirdparty.saker.util.rmi.wrap.RMIArrayListWrapper;
import saker.build.thirdparty.saker.util.rmi.wrap.RMIInputStreamWrapper;
import saker.build.thirdparty.saker.util.rmi.wrap.RMIOutputStreamWrapper;
import saker.build.thirdparty.saker.util.rmi.wrap.RMITreeMapSerializeKeyRemoteValueWrapper;
import saker.build.thirdparty.saker.util.rmi.wrap.RMITreeMapSerializeKeySerializeValueWrapper;
import saker.build.util.exc.ExceptionView;

/**
 * Interface for an utility class that provides extension functions for {@link TaskContext}.
 * <p>
 * This interface defines methods for the following reasons:
 * <ul>
 * <li>Convenience, some methods require less parameters so callers don't need to bother dealing with the fully
 * parameterized counterparts.</li>
 * <li>Bulk methods which result in better performance, as they only need to be called once instead of calling a single
 * method multiple times.</li>
 * <li>Remote execution support methods, to avoid multiple unnecessary RMI calls and do a specific operation quicker.
 * This can also include optional caching.</li>
 * <li>Additional utilities for communicating with the build system.</li>
 * </ul>
 * <p>
 * Convenience methods (unless noted otherwise) work the same way as their same named counterparts in
 * {@link TaskContext}.
 * <p>
 * Bulk methods provide better performance for working with larger data sets. They are usually the equivalent of calling
 * the corresponding singular parameterized function multiple times for every element in an argument collection. These
 * methods should be preferred, as the build system can optimize handling of these. They are also strongly recommended
 * when designing tasks for remote execution, as these require significantly less RMI calls, therefore less delay due to
 * network communication. See: reporting dependencies, starting tasks, invalidating files.
 * <p>
 * Remote execution support methods are present to increase performance of remote execution capable tasks, and to
 * provide functionality which would require extra work for task implementations to handle. These methods can employ
 * cluster-local caching for file contents, and reduce the number of RMI calls required to do something. See: file
 * resolution, file manipulation, file content retrieval, file creation, and other methods. <br>
 * These methods can be called even if a task is not designed for remote execution, they can be handled the same way.
 * They are only present for performance and utility reasons.
 * <p>
 * The reason of the existence of this utility class is that although these methods could be declared in the
 * {@link TaskContext} interface, it was decided to export these as these would pollute the {@link TaskContext}
 * interface.
 * <p>
 * The naming of the task starting methods are based on their return value. Methods that end with <code>Future</code>
 * will return a future to the started task(s), methods that end with <code>Result</code> returns the result of the task
 * execution, and methods that doesn't have a special ending will return <code>void</code>. These methods differ from
 * {@link TaskContext#startTask(TaskIdentifier, TaskFactory, TaskExecutionParameters) startTask} as that returns a
 * future, but methods in this interface is specially named. Tasks should consider choosing the <code>void</code>
 * returning methods when they are not intereseted in the result in any way, as it can reduce the load on the RMI
 * runtime when remote execution is used.
 * <p>
 * Clients should not implement this interface. Additional methods can be added to this interface without prior notice.
 */
public interface TaskExecutionUtilities {
	/**
	 * Gets the task context which this utilities belong to.
	 * 
	 * @return The task context.
	 */
	@RMICacheResult
	public TaskContext getTaskContext();

//	
//	File dependency reporting related utility functions
//	

	/**
	 * Reports an input file dependency for the path of the file and its contents.
	 * <p>
	 * See {@link TaskContext#reportInputFileDependency(Object, SakerPath, ContentDescriptor)}.
	 * 
	 * @param tag
	 *            The tag for the dependency.
	 * @param file
	 *            The file to report the dependency for.
	 * @throws InvalidPathFormatException
	 *             If the path of the file is not absolute.
	 * @throws NullPointerException
	 *             If the file is <code>null</code>.
	 */
	public void reportInputFileDependency(@RMISerialize Object tag, SakerFile file)
			throws InvalidPathFormatException, NullPointerException;

	/**
	 * Bulk method for {@link #reportInputFileDependency(Object, SakerFile)}.
	 * 
	 * @param tag
	 *            The tag for the dependency.
	 * @param files
	 *            The files to report the dependencies for.
	 * @throws InvalidPathFormatException
	 *             If the path of a file is not absolute.
	 * @throws NullPointerException
	 *             If the files or any of its elements are <code>null</code>.
	 */
	public default void reportInputFileDependency(@RMISerialize Object tag,
			@RMIWrap(RMIArrayListRemoteElementWrapper.class) Iterable<? extends SakerFile> files)
			throws InvalidPathFormatException, NullPointerException {
		for (SakerFile file : files) {
			reportInputFileDependency(tag, file);
		}
	}

	/**
	 * Bulk method for {@link TaskContext#reportInputFileDependency(Object, SakerPath, ContentDescriptor)}.
	 * <p>
	 * Each path-content descriptor pair will be reported as an input dependency.
	 * 
	 * @param tag
	 *            The tag for the dependency.
	 * @param pathcontents
	 *            The paths to contents map for each dependency.
	 * @throws NullPointerException
	 *             If the path contents are <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the map is not sorted by natural order.
	 */
	public void reportInputFileDependency(@RMISerialize Object tag,
			@RMIWrap(RMITreeMapSerializeKeySerializeValueWrapper.class) NavigableMap<SakerPath, ? extends ContentDescriptor> pathcontents)
			throws NullPointerException, IllegalArgumentException;

	/**
	 * Collects the files for the given file collection strategy.
	 * <p>
	 * This method doesn't report the argument as dependency.
	 * <p>
	 * <b>Important:</b> Calling this method will trigger the computation of file deltas as it uses
	 * {@link TaskContext#getPreviousFileAdditionDependency(FileCollectionStrategy)} to avoid redundant computations.
	 * <p>
	 * This method is the same as:
	 * 
	 * <pre>
	 * collectionstrategy.collectFiles(taskcontext);
	 * </pre>
	 * <p>
	 * When designing tasks for remote execution, it is recommended to call this method instead invoking
	 * {@link FileCollectionStrategy#collectFiles(ExecutionDirectoryContext, TaskDirectoryContext)} directly.
	 * <p>
	 * For correct incremental operation tasks should not forget to report the dependencies as well. Consider calling
	 * {@link #collectFilesReportInputFileAndAdditionDependency(Object, FileCollectionStrategy)} instead of this
	 * function.
	 * 
	 * @param collectionstrategy
	 *            The file collection strategy.
	 * @return The collected files.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @see TaskContext#reportInputFileAdditionDependency(Object, FileCollectionStrategy)
	 */
	@RMIWrap(RMITreeMapSerializeKeyRemoteValueWrapper.class)
	public NavigableMap<SakerPath, SakerFile> collectFiles(FileCollectionStrategy collectionstrategy)
			throws NullPointerException;

	/**
	 * Collects the files for the given file addition dependency, and reports it and the files as dependencies.
	 * <p>
	 * This method collects the files for the specified addition dependency, reports the addition dependency, and
	 * reports the input dependencies for the collected files with their respective content descriptors.
	 * <p>
	 * <b>Important:</b> Calling this method will trigger the computation of file deltas as it uses
	 * {@link TaskContext#getPreviousFileAdditionDependency(FileCollectionStrategy)} to avoid redundant computations.
	 * 
	 * @param tag
	 *            The tag for the dependencies.
	 * @param fileadditiondependency
	 *            The file addition dependency.
	 * @return The collected files.
	 * @throws NullPointerException
	 *             If the file addition dependency is <code>null</code>.
	 * @see TaskContext#reportInputFileAdditionDependency(Object, FileCollectionStrategy)
	 * @see TaskContext#reportInputFileDependency(Object, SakerPath, ContentDescriptor)
	 */
	@RMIWrap(RMITreeMapSerializeKeyRemoteValueWrapper.class)
	public default NavigableMap<SakerPath, SakerFile> collectFilesReportInputFileAndAdditionDependency(
			@RMISerialize Object tag, FileCollectionStrategy fileadditiondependency) throws NullPointerException {
		return collectFilesReportInputFileAndAdditionDependency(tag, Collections.singleton(fileadditiondependency));
	}

	/**
	 * Builk method for calling
	 * {@link #collectFilesReportInputFileAndAdditionDependency(Object, FileCollectionStrategy)}.
	 * <p>
	 * This method collects the files for all the addition dependencies, and aggregates them into a single result map.
	 * 
	 * @param tag
	 *            The tag for the dependencies.
	 * @param fileadditiondependencies
	 *            The file addition dependencies.
	 * @return The collected files.
	 * @throws NullPointerException
	 *             If the file addition dependencies or any of the elements are <code>null</code>.
	 * @see TaskContext#reportInputFileAdditionDependency(Object, FileCollectionStrategy)
	 * @see TaskContext#reportInputFileDependency(Object, SakerPath, ContentDescriptor)
	 */
	@RMIWrap(RMITreeMapSerializeKeyRemoteValueWrapper.class)
	public NavigableMap<SakerPath, SakerFile> collectFilesReportInputFileAndAdditionDependency(@RMISerialize Object tag,
			@RMIWrap(RMIArrayListWrapper.class) Iterable<? extends FileCollectionStrategy> fileadditiondependencies)
			throws NullPointerException;

	/**
	 * Collects the files for the given addition dependency, and reports it.
	 * <p>
	 * This method doesn't report input file dependency to the collected files.
	 * <p>
	 * <b>Important:</b> Calling this method will trigger the computation of file deltas as it uses
	 * {@link TaskContext#getPreviousFileAdditionDependency(FileCollectionStrategy)} to avoid redundant computations.
	 * 
	 * @param tag
	 *            The tag for the dependencies.
	 * @param fileadditiondependency
	 *            The file addition dependency.
	 * @return The collected files.
	 * @throws NullPointerException
	 *             If the file addition dependency is <code>null</code>.
	 * @see #collectFilesReportInputFileAndAdditionDependency(Object, FileCollectionStrategy)
	 * @see TaskContext#reportInputFileDependency(Object, SakerPath, ContentDescriptor)
	 */
	@RMIWrap(RMITreeMapSerializeKeyRemoteValueWrapper.class)
	public default NavigableMap<SakerPath, SakerFile> collectFilesReportAdditionDependency(@RMISerialize Object tag,
			FileCollectionStrategy fileadditiondependency) throws NullPointerException {
		return collectFilesReportAdditionDependency(tag, Collections.singleton(fileadditiondependency));
	}

	/**
	 * Bulk method for calling {@link #collectFilesReportAdditionDependency(Object, FileCollectionStrategy)}.
	 * <p>
	 * This method collects the files for all the addition dependencies, and aggregates them into a single result map.
	 * 
	 * @param tag
	 *            The tag for the dependencies.
	 * @param fileadditiondependencies
	 *            The file addition dependencies.
	 * @return The collected files.
	 * @throws NullPointerException
	 *             If the file addition dependencies or any of the elements are <code>null</code>.
	 * @see #collectFilesReportAdditionDependency(Object, FileCollectionStrategy)
	 * @see TaskContext#reportInputFileAdditionDependency(Object, FileCollectionStrategy)
	 */
	@RMIWrap(RMITreeMapSerializeKeyRemoteValueWrapper.class)
	public NavigableMap<SakerPath, SakerFile> collectFilesReportAdditionDependency(@RMISerialize Object tag,
			@RMIWrap(RMIArrayListWrapper.class) Iterable<? extends FileCollectionStrategy> fileadditiondependencies)
			throws NullPointerException;

	/**
	 * Reports an output file dependency for the path of the file and its contents.
	 * <p>
	 * See {@link TaskContext#reportOutputFileDependency(Object, SakerPath, ContentDescriptor)}.
	 * 
	 * @param tag
	 *            The tag for the dependency.
	 * @param file
	 *            The file to report the dependency for.
	 * @throws InvalidPathFormatException
	 *             If the path of the file is not absolute.
	 * @throws NullPointerException
	 *             If the file is <code>null</code>.
	 */
	public void reportOutputFileDependency(@RMISerialize Object tag, SakerFile file)
			throws InvalidPathFormatException, NullPointerException;

	/**
	 * Bulk method for {@link #reportOutputFileDependency(Object, SakerFile)}.
	 * 
	 * @param tag
	 *            The tag for the dependency.
	 * @param files
	 *            The files to report the dependencies for.
	 * @throws InvalidPathFormatException
	 *             If the path of a file is not absolute.
	 * @throws NullPointerException
	 *             If the files or any of its elements are <code>null</code>.
	 */
	public default void reportOutputFileDependency(@RMISerialize Object tag,
			@RMIWrap(RMIArrayListRemoteElementWrapper.class) Iterable<? extends SakerFile> files)
			throws InvalidPathFormatException, NullPointerException {
		for (SakerFile file : files) {
			reportOutputFileDependency(tag, file);
		}
	}

	/**
	 * Bulk method for {@link TaskContext#reportOutputFileDependency(Object, SakerPath, ContentDescriptor)}.
	 * <p>
	 * Each path-content descriptor pair will be reported as an output dependency.
	 * 
	 * @param tag
	 *            The tag for the dependency.
	 * @param pathcontents
	 *            The paths to contents map for each dependency.
	 * @throws NullPointerException
	 *             If the path contents are <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the map is not sorted by natural order.
	 */
	public void reportOutputFileDependency(@RMISerialize Object tag,
			@RMIWrap(RMITreeMapSerializeKeySerializeValueWrapper.class) NavigableMap<SakerPath, ? extends ContentDescriptor> pathcontents)
			throws NullPointerException, IllegalArgumentException;

//	
//	Property dependency related utility functions
//	

	/**
	 * Gets the current value of the specified environment property and reports it as a dependency.
	 * 
	 * @param <T>
	 *            The type of the returned property.
	 * @param property
	 *            The environment property.
	 * @return The current value of the environment property.
	 * @throws NullPointerException
	 *             If the property is <code>null</code>.
	 * @throws PropertyComputationFailedException
	 *             If the argument property throws an exception during the computation of its value. The thrown
	 *             exception is available through the {@linkplain PropertyComputationFailedException#getCause() cause}.
	 * @see TaskContext#reportEnvironmentDependency(EnvironmentProperty, Object)
	 */
	@RMISerialize
	public <T> T getReportEnvironmentDependency(@RMISerialize EnvironmentProperty<T> property)
			throws NullPointerException, PropertyComputationFailedException;

	/**
	 * Gets the current value of the specified execution property and reports it as a dependency.
	 * 
	 * @param <T>
	 *            The type of the returned property.
	 * @param property
	 *            The execution property.
	 * @return The current value of the execution property.
	 * @throws NullPointerException
	 *             If the property is <code>null</code>.
	 * @throws PropertyComputationFailedException
	 *             If the argument property throws an exception during the computation of its value. The thrown
	 *             exception is available through the {@linkplain PropertyComputationFailedException#getCause() cause}.
	 * @see TaskContext#reportExecutionDependency(ExecutionProperty, Object)
	 */
	@RMISerialize
	public <T> T getReportExecutionDependency(@RMISerialize ExecutionProperty<T> property)
			throws NullPointerException, PropertyComputationFailedException;

//	
//	Task execution related utility functions
//	

	/**
	 * Starts a task and waits for its execution to complete.
	 * <p>
	 * This method is similar to {@link #runTaskResult(TaskIdentifier, TaskFactory, TaskExecutionParameters)}, but
	 * doesn't add a dependency for the task, and returns a {@link TaskFuture} instance to the caller.
	 * 
	 * @param <R>
	 *            The result type of the task.
	 * @param taskid
	 *            The task identifier for the task to run.
	 * @param taskfactory
	 *            The task factory of the task.
	 * @param parameters
	 *            The task execution parameters, or <code>null</code> to use the defaults.
	 * @return A future handle for the run task.
	 * @throws TaskIdentifierConflictException
	 *             If a task with the same identifier is already started and the task factories does not equal.
	 * @throws NullPointerException
	 *             If task identifier or task factory is <code>null</code>.
	 * @throws IllegalTaskOperationException
	 *             If the task identifier is the same as the caller.
	 */
	public <R> TaskFuture<R> runTaskFuture(@RMISerialize TaskIdentifier taskid,
			@RMISerialize TaskFactory<R> taskfactory, TaskExecutionParameters parameters)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException;

	/**
	 * Convenience function to call {@link #runTaskFuture(TaskIdentifier, TaskFactory, TaskExecutionParameters)} with
	 * the default task execution parameters.
	 * 
	 * @param <R>
	 *            The result type of the task.
	 * @param taskid
	 *            The task identifier for the task to run.
	 * @param taskfactory
	 *            The task factory of the task.
	 * @return A future handle for the run task.
	 * @throws TaskIdentifierConflictException
	 *             If a task with the same identifier is already started and the task factories does not equal.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws IllegalTaskOperationException
	 *             If the task identifier is the same as the caller.
	 */
	public default <R> TaskFuture<R> runTaskFuture(@RMISerialize TaskIdentifier taskid,
			@RMISerialize TaskFactory<R> taskfactory)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		return runTaskFuture(taskid, taskfactory, null);
	}

	/**
	 * Same as {@link #runTaskFuture(TaskIdentifier, TaskFactory, TaskExecutionParameters)} with the arguments derived
	 * from the specified launch arguments.
	 * 
	 * @param <R>
	 *            The result type of the task.
	 * @param task
	 *            The launch arguments of the task.
	 * @return A future handle for the run task.
	 * @throws TaskIdentifierConflictException
	 *             If a task with the same identifier is already started and the task factories does not equal.
	 * @throws NullPointerException
	 *             If task identifier or task factory is <code>null</code>.
	 * @throws IllegalTaskOperationException
	 *             If the task identifier is the same as the caller.
	 */
	public default <R> TaskFuture<R> runTaskFuture(TaskLaunchArguments<R> task)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		return runTaskFuture(task.getTaskIdentifier(), task.getTaskFactory(), task.getExecutionParameters());
	}

	/**
	 * Same as {@link #runTaskFuture(TaskIdentifier, TaskFactory, TaskExecutionParameters)} but returns
	 * <code>void</code>.
	 * 
	 * @param taskid
	 *            The task identifier for the task to run.
	 * @param taskfactory
	 *            The task factory of the task.
	 * @param parameters
	 *            The task execution parameters, or <code>null</code> to use the defaults.
	 * @throws TaskIdentifierConflictException
	 *             If a task with the same identifier is already started and the task factories does not equal.
	 * @throws NullPointerException
	 *             If task identifier or task factory is <code>null</code>.
	 * @throws IllegalTaskOperationException
	 *             If the task identifier is the same as the caller.
	 */
	public default void runTask(@RMISerialize TaskIdentifier taskid, @RMISerialize TaskFactory<?> taskfactory,
			TaskExecutionParameters parameters)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		runTaskFuture(taskid, taskfactory, parameters);
	}

	/**
	 * Same as {@link #runTaskFuture(TaskIdentifier, TaskFactory, TaskExecutionParameters)} with <code>null</code>
	 * execution parameters, but returns <code>void</code>.
	 * 
	 * @param taskid
	 *            The task identifier for the task to run.
	 * @param taskfactory
	 *            The task factory of the task.
	 * @throws TaskIdentifierConflictException
	 *             If a task with the same identifier is already started and the task factories does not equal.
	 * @throws NullPointerException
	 *             If task identifier or task factory is <code>null</code>.
	 * @throws IllegalTaskOperationException
	 *             If the task identifier is the same as the caller.
	 */
	public default void runTask(@RMISerialize TaskIdentifier taskid, @RMISerialize TaskFactory<?> taskfactory)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		runTaskFuture(taskid, taskfactory, null);
	}

	/**
	 * Same as {@link #runTask(TaskIdentifier, TaskFactory, TaskExecutionParameters)} with the arguments derived from
	 * the specified launch arguments.
	 * 
	 * @param task
	 *            The launch arguments of the task.
	 * @throws TaskIdentifierConflictException
	 *             If a task with the same identifier is already started and the task factories does not equal.
	 * @throws NullPointerException
	 *             If task identifier or task factory is <code>null</code>.
	 * @throws IllegalTaskOperationException
	 *             If the task identifier is the same as the caller.
	 */
	public default void runTask(TaskLaunchArguments<?> task)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		runTaskFuture(task.getTaskIdentifier(), task.getTaskFactory(), task.getExecutionParameters());
	}

	/**
	 * Convenience function to call {@link #runTaskResult(TaskIdentifier, TaskFactory, TaskExecutionParameters)} with
	 * the default task execution parameters.
	 * 
	 * @param taskid
	 *            The task identifier for the task to run.
	 * @param taskfactory
	 *            The task factory of the task.
	 * @return The result of the task execution.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws IllegalTaskOperationException
	 *             If the task identifier is the same as the caller.
	 */
	@RMISerialize
	public default <R> R runTaskResult(@RMISerialize TaskIdentifier taskid, TaskFactory<R> taskfactory)
			throws NullPointerException, IllegalTaskOperationException {
		return runTaskResult(taskid, taskfactory, null);
	}

	/**
	 * Starts a task and waits for its execution to complete.
	 * <p>
	 * A task dependency will be added for the started task with the same rules as in
	 * {@link TaskContext#getTaskResult(TaskIdentifier)}.
	 *
	 * @param taskid
	 *            The task identifier for the task to run.
	 * @param taskfactory
	 *            The task factory of the task.
	 * @param parameters
	 *            The task execution parameters, or <code>null</code> to use the defaults.
	 * @return The result of the task execution.
	 * @throws TaskIdentifierConflictException
	 *             If a task with the same identifier is already started and the task factories does not equal.
	 * @throws NullPointerException
	 *             If task identifier or task factory is <code>null</code>.
	 * @throws TaskExecutionFailedException
	 *             If the task execution resulted in an exception.
	 * @throws IllegalTaskOperationException
	 *             If the task identifier is the same as the caller.
	 */
	@RMISerialize
	public default <R> R runTaskResult(@RMISerialize TaskIdentifier taskid, @RMISerialize TaskFactory<R> taskfactory,
			TaskExecutionParameters parameters) throws TaskIdentifierConflictException, NullPointerException,
			TaskExecutionFailedException, IllegalTaskOperationException {
		return runTaskFuture(taskid, taskfactory, parameters).get();
	}

	/**
	 * Same as {@link #runTaskResult(TaskIdentifier, TaskFactory, TaskExecutionParameters)} with the arguments derived
	 * from the specified launch arguments.
	 * 
	 * @param <R>
	 *            The result type of the task.
	 * @param task
	 *            The launch arguments of the task.
	 * @return The result of the task execution.
	 * @throws TaskIdentifierConflictException
	 *             If a task with the same identifier is already started and the task factories does not equal.
	 * @throws NullPointerException
	 *             If task identifier or task factory is <code>null</code>.
	 * @throws TaskExecutionFailedException
	 *             If the task execution resulted in an exception.
	 * @throws IllegalTaskOperationException
	 *             If the task identifier is the same as the caller.
	 */
	@RMISerialize
	public default <R> R runTaskResult(TaskLaunchArguments<R> task) throws TaskIdentifierConflictException,
			NullPointerException, IllegalTaskOperationException, TaskExecutionFailedException {
		return runTaskResult(task.getTaskIdentifier(), task.getTaskFactory(), task.getExecutionParameters());
	}

	/**
	 * Convenience function to call {@link TaskContext#startTask(TaskIdentifier, TaskFactory, TaskExecutionParameters)}
	 * with the default task execution parameters.
	 * <p>
	 * This method returns no result.
	 * 
	 * @param taskid
	 *            The task identifier for the task to run.
	 * @param taskfactory
	 *            The task factory of the task.
	 * @throws TaskIdentifierConflictException
	 *             If a task with the same identifier is already started and the task factories does not equal.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws IllegalTaskOperationException
	 *             If the task identifier is the same as the caller.
	 */
	public default void startTask(@RMISerialize TaskIdentifier taskid, TaskFactory<?> taskfactory)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		startTaskFuture(taskid, taskfactory);
	}

	/**
	 * Convenience function to call {@link TaskContext#startTask(TaskIdentifier, TaskFactory, TaskExecutionParameters)}.
	 * <p>
	 * This method returns no result.
	 * 
	 * @param task
	 *            The launch arguments of the task.
	 * @throws TaskIdentifierConflictException
	 *             If a task with the same identifier is already started and the task factories does not equal.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws IllegalTaskOperationException
	 *             If the task identifier is the same as the caller.
	 */
	public default void startTask(TaskLaunchArguments<?> task)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		getTaskContext().startTask(task.getTaskIdentifier(), task.getTaskFactory(), task.getExecutionParameters());
	}

	/**
	 * Convenience function to call {@link TaskContext#startTask(TaskIdentifier, TaskFactory, TaskExecutionParameters)}
	 * with the default task execution parameters.
	 * 
	 * @param <R>
	 *            The result type of the task
	 * @param taskid
	 *            The task identifier for the task to run.
	 * @param taskfactory
	 *            The task factory of the task.
	 * @return A future handle for the started task.
	 * @throws TaskIdentifierConflictException
	 *             If a task with the same identifier is already started and the task factories does not equal.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws IllegalTaskOperationException
	 *             If the task identifier is the same as the caller.
	 */
	public default <R> TaskFuture<R> startTaskFuture(@RMISerialize TaskIdentifier taskid, TaskFactory<R> taskfactory)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		return getTaskContext().startTask(taskid, taskfactory, null);
	}

	/**
	 * Convenience function to call {@link TaskContext#startTask(TaskIdentifier, TaskFactory, TaskExecutionParameters)}.
	 * 
	 * @param <R>
	 *            The result type of the task
	 * @param task
	 *            The launch arguments of the task.
	 * @return A future handle for the started task.
	 * @throws TaskIdentifierConflictException
	 *             If a task with the same identifier is already started and the task factories does not equal.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws IllegalTaskOperationException
	 *             If the task identifier is the same as the caller.
	 */
	public default <R> TaskFuture<R> startTaskFuture(TaskLaunchArguments<R> task)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		return getTaskContext().startTask(task.getTaskIdentifier(), task.getTaskFactory(),
				task.getExecutionParameters());
	}

	/**
	 * Batch function for starting multiple tasks.
	 * <p>
	 * The started tasks will use the default execution parameters.
	 * <p>
	 * This function works the same way as {@link #startTasks(Map, TaskExecutionParameters)} with <code>null</code>
	 * execution parameters.
	 * 
	 * @param tasks
	 *            The collection of tasks to start.
	 * @throws TaskIdentifierConflictException
	 *             If a task with the same identifier is already started and the task factories does not equal.
	 * @throws NullPointerException
	 *             If any of the arguments or elements are <code>null</code>.
	 * @throws IllegalTaskOperationException
	 *             If a task identifier is the same as the caller.
	 * @see {@link TaskContext#startTask(TaskIdentifier, TaskFactory, TaskExecutionParameters)}
	 */
	public default void startTasks(@RMISerialize Map<? extends TaskIdentifier, ? extends TaskFactory<?>> tasks)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		this.startTasks(tasks, null);
	}

	/**
	 * Batch function for starting multiple tasks with the specified execution parameters.
	 * <p>
	 * The passed execution parameters will be used for each started task. If <code>null</code>, the default parameters
	 * will be used.
	 * 
	 * @param tasks
	 *            The collection of tasks to start.
	 * @param parameters
	 *            The execution parameter to start the tasks with.
	 * @throws TaskIdentifierConflictException
	 *             If a task with the same identifier is already started and the task factories does not equal.
	 * @throws NullPointerException
	 *             If any of the arguments or elements are <code>null</code>.
	 * @throws IllegalTaskOperationException
	 *             If a task identifier is the same as the caller.
	 * @see {@link TaskContext#startTask(TaskIdentifier, TaskFactory, TaskExecutionParameters)}
	 */
	public default void startTasks(@RMISerialize Map<? extends TaskIdentifier, ? extends TaskFactory<?>> tasks,
			TaskExecutionParameters parameters)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		Objects.requireNonNull(tasks, "tasks");
		for (Entry<? extends TaskIdentifier, ? extends TaskFactory<?>> entry : tasks.entrySet()) {
			getTaskContext().startTask(entry.getKey(), entry.getValue(), parameters);
		}
	}

	/**
	 * Batch function for starting tasks specified by the launch arguments.
	 * <p>
	 * This function starts every task with the parameters specified by each launch arguments.
	 * <p>
	 * This function works the same way as if {@link #startTask(TaskLaunchArguments)} was called for each element in the
	 * argument.
	 * <p>
	 * This function returns <code>void</code>, to retrieve the futures for the started tasks as well, use
	 * {@link #startTasksFuture(Iterable)}.
	 * 
	 * @param tasks
	 *            The tasks to start.
	 * @throws TaskIdentifierConflictException
	 *             If a task with the same identifier is already started and the task factories does not equal.
	 * @throws NullPointerException
	 *             If any of the arguments or elements are <code>null</code>.
	 * @throws IllegalTaskOperationException
	 *             If a task identifier is the same as the caller.
	 */
	public default void startTasks(@RMIWrap(RMIArrayListWrapper.class) Iterable<? extends TaskLaunchArguments<?>> tasks)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		Objects.requireNonNull(tasks, "tasks");
		for (TaskLaunchArguments<?> launchargs : tasks) {
			startTask(launchargs);
		}
	}

	/**
	 * Batch function for starting tasks with the specified launch arguments and retrieving the futures for them.
	 * <p>
	 * This function works the same way as if {@link #startTaskFuture(TaskLaunchArguments)} was called for each element
	 * in the argument.
	 * <p>
	 * The returned list of task futures will have the same order as the argument iterable.
	 * 
	 * @param tasks
	 *            The tasks to start
	 * @return The futures for the started tasks in the same order as the argument iterable.
	 * @throws TaskIdentifierConflictException
	 *             If a task with the same identifier is already started and the task factories does not equal.
	 * @throws NullPointerException
	 *             If any of the arguments or elements are <code>null</code>.
	 * @throws IllegalTaskOperationException
	 *             If a task identifier is the same as the caller.
	 */
	@RMIWrap(RMIArrayListRemoteElementWrapper.class)
	public default List<? extends TaskFuture<?>> startTasksFuture(
			@RMIWrap(RMIArrayListWrapper.class) Iterable<? extends TaskLaunchArguments<?>> tasks)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		Objects.requireNonNull(tasks, "tasks");
		List<TaskFuture<?>> result = new ArrayList<>();
		for (TaskLaunchArguments<?> launchargs : tasks) {
			TaskFuture<?> future = startTaskFuture(launchargs);
			result.add(future);
		}
		return result;
	}

//	
//	File related utility functions
//	

	/**
	 * Resolves a file or directory at the given path.
	 * <p>
	 * If the path is relative, it will be resolved against the current task working directory.
	 * <p>
	 * If it is absolute, the execution root directories will be used as a base of resolution.
	 * 
	 * @param path
	 *            The path to resolve.
	 * @return The found file at the given path or <code>null</code> if not found.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 * @see SakerDirectory#get(String)
	 * @see SakerPathFiles#resolveAtPath(TaskContext, SakerPath)
	 */
	public SakerFile resolveAtPath(SakerPath path) throws NullPointerException;

	/**
	 * Resolves a file but not a directory at the given path.
	 * <p>
	 * If the path is relative, it will be resolved against the current task working directory.
	 * <p>
	 * If it is absolute, the execution root directories will be used as a base of resolution.
	 * 
	 * @param path
	 *            The path to resolve.
	 * @return The found file at the given path or <code>null</code> if not found or it's a directory.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 * @see SakerDirectory#get(String)
	 * @see SakerPathFiles#resolveFileAtPath(TaskContext, SakerPath)
	 */
	public SakerFile resolveFileAtPath(SakerPath path) throws NullPointerException;

	/**
	 * Resolves a directory at a given path.
	 * <p>
	 * If the path is relative, it will be resolved against the current task working directory.
	 * <p>
	 * If it is absolute, the execution root directories will be used as a base of resolution.
	 * 
	 * @param path
	 *            The path to resolve.
	 * @return The found directory at the given path or <code>null</code> if a directory was not found at the path.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 * @see SakerDirectory#getDirectory(String)
	 * @see SakerPathFiles#resolveDirectoryAtPath(TaskContext, SakerPath)
	 */
	public SakerDirectory resolveDirectoryAtPath(SakerPath path) throws NullPointerException;

	/**
	 * Resolves a directory at a given path, creating it if necessary, overwriting already existing files.
	 * <p>
	 * If the path is relative, it will be resolved against the current task working directory.
	 * <p>
	 * If it is absolute, the execution root directories will be used as a base of resolution.
	 * 
	 * @param path
	 *            The path to resolve.
	 * @return The directory at the given path.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 * @see SakerDirectory#getDirectoryCreate(String)
	 * @see SakerPathFiles#resolveDirectoryAtPathCreate(TaskContext, SakerPath)
	 */
	public SakerDirectory resolveDirectoryAtPathCreate(SakerPath path) throws NullPointerException;

	/**
	 * Resolves a directory at a given path, creating it if possible.
	 * <p>
	 * This method doesn't overwrite the already existing file at the path if it's not a directory.
	 * <p>
	 * If the path is relative, it will be resolved against the current task working directory.
	 * <p>
	 * If it is absolute, the execution root directories will be used as a base of resolution.
	 * 
	 * @param path
	 *            The path to resolve.
	 * @return The directory at the given path or <code>null</code> if it cannot be created.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 * @see {@link SakerDirectory#getDirectoryCreateIfAbsent(String)}
	 * @see SakerPathFiles#resolveDirectoryAtPathCreateIfAbsent(TaskContext, SakerPath)
	 */
	public SakerDirectory resolveDirectoryAtPathCreateIfAbsent(SakerPath path) throws NullPointerException;

	/**
	 * Same as {@link #resolveAtPath(SakerPath)}, but uses the specified base directory to resolve relative paths.
	 * 
	 * @param basedir
	 *            The base directory.
	 * @param path
	 *            The path to resolve.
	 * @return The found file at the given path or <code>null</code> if not found.
	 * @throws NullPointerException
	 *             If path is <code>null</code>, or path is relative and base directory is <code>null</code>.
	 * @see SakerPathFiles#resolveAtPath(ExecutionDirectoryContext, SakerDirectory, SakerPath)
	 */
	public SakerFile resolveAtPath(SakerDirectory basedir, SakerPath path) throws NullPointerException;

	/**
	 * Same as {@link #resolveFileAtPath(SakerPath)}, but uses the specified base directory to resolve relative paths.
	 * 
	 * @param basedir
	 *            The base directory.
	 * @param path
	 *            The path to resolve.
	 * @return The found file at the given path or <code>null</code> if not found or it's a directory.
	 * @throws NullPointerException
	 *             If path is <code>null</code>, or path is relative and base directory is <code>null</code>.
	 * @see SakerPathFiles#resolveFileAtPath(ExecutionDirectoryContext, SakerDirectory, SakerPath)
	 */
	public SakerFile resolveFileAtPath(SakerDirectory basedir, SakerPath path) throws NullPointerException;

	/**
	 * Same as {@link #resolveDirectoryAtPath(SakerPath)}, but uses the specified base directory to resolve relative
	 * paths.
	 * 
	 * @param basedir
	 *            The base directory.
	 * @param path
	 *            The path to resolve.
	 * @return The found directory at the given path or <code>null</code> if a directory was not found at the path.
	 * @throws NullPointerException
	 *             If path is <code>null</code>, or path is relative and base directory is <code>null</code>.
	 * @see SakerPathFiles#resolveDirectoryAtPath(ExecutionDirectoryContext, SakerDirectory, SakerPath)
	 */
	public SakerDirectory resolveDirectoryAtPath(SakerDirectory basedir, SakerPath path) throws NullPointerException;

	/**
	 * Same as {@link #resolveDirectoryAtPathCreate(SakerPath)}, but uses the specified base directory to resolve
	 * relative paths.
	 * 
	 * @param basedir
	 *            The base directory.
	 * @param path
	 *            The path to resolve.
	 * @return The directory at the given path.
	 * @throws NullPointerException
	 *             If path is <code>null</code>, or path is relative and base directory is <code>null</code>.
	 * @see SakerPathFiles#resolveDirectoryAtPathCreate(ExecutionDirectoryContext, SakerDirectory, SakerPath)
	 */
	public SakerDirectory resolveDirectoryAtPathCreate(SakerDirectory basedir, SakerPath path)
			throws NullPointerException;

	/**
	 * Same as {@link #resolveDirectoryAtPathCreateIfAbsent(SakerPath)}, but uses the specified base directory to
	 * resolve relative paths.
	 * 
	 * @param basedir
	 *            The base directory.
	 * @param path
	 *            The path to resolve.
	 * @return The directory at the given path or <code>null</code> if it cannot be created.
	 * @throws NullPointerException
	 *             If path is <code>null</code>, or path is relative and base directory is <code>null</code>.
	 * @see SakerPathFiles#resolveDirectoryAtPathCreateIfAbsent(ExecutionDirectoryContext, SakerDirectory, SakerPath)
	 */
	public SakerDirectory resolveDirectoryAtPathCreateIfAbsent(SakerDirectory basedir, SakerPath path)
			throws NullPointerException;

	/**
	 * Resolves a file or directory using the given relative path against a base directory.
	 * 
	 * @param basedir
	 *            The base directory.
	 * @param path
	 *            The relative path.
	 * @return The resolved file or <code>null</code> if not found.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the path is not relative.
	 * @see SakerPathFiles#resolve(SakerDirectory, String)
	 * @see SakerPathFiles#resolveAtRelativePath(SakerDirectory, SakerPath)
	 */
	public default SakerFile resolveAtRelativePath(SakerDirectory basedir, SakerPath path)
			throws NullPointerException, InvalidPathFormatException {
		return SakerPathFiles.resolveAtRelativePath(basedir, path);
	}

	/**
	 * Resolves a file but not a directory using the given relative path against a base directory.
	 * 
	 * @param basedir
	 *            The base directory.
	 * @param path
	 *            The relative path.
	 * @return The resolved file or <code>null</code> if not found.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the path is not relative.
	 * @see SakerPathFiles#resolveFile(SakerDirectory, String)
	 * @see SakerPathFiles#resolveAtRelativePath(SakerDirectory, SakerPath)
	 */
	public default SakerFile resolveFileAtRelativePath(SakerDirectory basedir, SakerPath path)
			throws NullPointerException, InvalidPathFormatException {
		return SakerPathFiles.resolveFileAtRelativePath(basedir, path);
	}

	/**
	 * Resolves a directory using the given relative path against a base directory.
	 * 
	 * @param basedir
	 *            The base directory.
	 * @param path
	 *            The relative path.
	 * @return The resolved directory or <code>null</code> if a directory was not found at the path.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the path is not relative.
	 * @see SakerPathFiles#resolveDirectory(SakerDirectory, String)
	 * @see SakerPathFiles#resolveDirectoryAtRelativePath(SakerDirectory, SakerPath)
	 */
	public default SakerDirectory resolveDirectoryAtRelativePath(SakerDirectory basedir, SakerPath path)
			throws NullPointerException, InvalidPathFormatException {
		return SakerPathFiles.resolveDirectoryAtRelativePath(basedir, path);
	}

	/**
	 * Resolves a directory using the given relative path against a base directory, creating it if necessary,
	 * overwriting already existing files.
	 * 
	 * @param basedir
	 *            The base directory.
	 * @param path
	 *            The relative path.
	 * @return The directory at the given path.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the path is not relative.
	 * @see SakerPathFiles#resolveDirectoryAtRelativePathCreate(SakerDirectory, SakerPath)
	 */
	public default SakerDirectory resolveDirectoryAtRelativePathCreate(SakerDirectory basedir, SakerPath path)
			throws NullPointerException, InvalidPathFormatException {
		return SakerPathFiles.resolveDirectoryAtRelativePathCreate(basedir, path);
	}

	/**
	 * Resolves a directory using the given relative path against a base directory, creating it if possible.
	 * <p>
	 * This method doesn't overwrite the already existing file at the path if it's not a directory.
	 * 
	 * @param basedir
	 *            The base directory.
	 * @param path
	 *            The relative path.
	 * @return The directory at the given path or <code>null</code> if it cannot be created.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the path is not relative.
	 * @see SakerPathFiles#resolveDirectoryAtRelativePathCreateIfAbsent(SakerDirectory, SakerPath)
	 */
	public default SakerDirectory resolveDirectoryAtRelativePathCreateIfAbsent(SakerDirectory basedir, SakerPath path)
			throws NullPointerException, InvalidPathFormatException {
		return SakerPathFiles.resolveDirectoryAtRelativePathCreateIfAbsent(basedir, path);
	}

	/**
	 * Resolves a file or directory using the given absolute path.
	 * 
	 * @param path
	 *            The absolute path.
	 * @return The resolved file or <code>null</code> if not found.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the path is not absolute.
	 * @see SakerPathFiles#resolve(SakerDirectory, String)
	 * @see ExecutionDirectoryContext#getRootDirectories()
	 * @see SakerPathFiles#resolveAtAbsolutePath(ExecutionDirectoryContext, SakerPath)
	 */
	public SakerFile resolveAtAbsolutePath(SakerPath path) throws NullPointerException, InvalidPathFormatException;

	/**
	 * Resolves a file but not a directory using the given absolute path.
	 * 
	 * @param path
	 *            The absolute path.
	 * @return The resolved file or <code>null</code> if not found or it's a directory.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the path is not absolute.
	 * @see SakerPathFiles#resolveFile(SakerDirectory, String)
	 * @see ExecutionDirectoryContext#getRootDirectories()
	 * @see SakerPathFiles#resolveFileAtAbsolutePath(ExecutionDirectoryContext, SakerPath)
	 */
	public SakerFile resolveFileAtAbsolutePath(SakerPath path) throws NullPointerException, InvalidPathFormatException;

	/**
	 * Resolves a directory using the given absolute path.
	 * 
	 * @param path
	 *            The absolute path.
	 * @return The resolved directory or <code>null</code> if a directory was not found at the path.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the path is not absolute.
	 * @see SakerPathFiles#resolveDirectory(SakerDirectory, String)
	 * @see ExecutionDirectoryContext#getRootDirectories()
	 * @see SakerPathFiles#resolveDirectoryAtAbsolutePath(ExecutionDirectoryContext, SakerPath)
	 */
	public SakerDirectory resolveDirectoryAtAbsolutePath(SakerPath path)
			throws NullPointerException, InvalidPathFormatException;

	/**
	 * Resolves a directory using the given absolute path, creating it if necessary, overwriting already existing files.
	 * 
	 * @param path
	 *            The absolute path.
	 * @return The directory at the given path.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the path is not absolute.
	 * @see SakerPathFiles#resolveDirectoryCreate(SakerDirectory, String)
	 * @see ExecutionDirectoryContext#getRootDirectories()
	 * @see SakerPathFiles#resolveDirectoryAtAbsolutePathCreate(ExecutionDirectoryContext, SakerPath)
	 */
	public SakerDirectory resolveDirectoryAtAbsolutePathCreate(SakerPath path)
			throws NullPointerException, InvalidPathFormatException;

	/**
	 * Resolves a directory using the given absolute path, creating it if possible.
	 * <p>
	 * This method doesn't overwrite the already existing file at the path if it's not a directory.
	 * 
	 * @param path
	 *            The absolute path.
	 * @return The directory at the given path or <code>null</code> if it cannot be created.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the path is not absolute.
	 * @see SakerPathFiles#resolveDirectoryCreateIfAbsent(SakerDirectory, String)
	 * @see ExecutionDirectoryContext#getRootDirectories()
	 * @see SakerPathFiles#resolveDirectoryAtAbsolutePathCreateIfAbsent(ExecutionDirectoryContext, SakerPath)
	 */
	public SakerDirectory resolveDirectoryAtAbsolutePathCreateIfAbsent(SakerPath path)
			throws NullPointerException, InvalidPathFormatException;

	/**
	 * Resolves a file or directory with the given path names against a base directory.
	 * <p>
	 * The path names are interpreted as if they were relative compared to the base directory.
	 * 
	 * @param basedir
	 *            The base directory.
	 * @param names
	 *            The relative path names.
	 * @return The resolved file or <code>null</code> if not found.
	 * @throws NullPointerException
	 *             If any of the arguments or path names are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If any of the path name has invalid format.
	 * @see #resolveAtRelativePath(SakerDirectory, SakerPath)
	 * @see SakerPathFiles#requireValidFileName(String)
	 * @see SakerPathFiles#resolveAtRelativePathNames(SakerDirectory, Iterable)
	 */
	public default SakerFile resolveAtRelativePathNames(SakerDirectory basedir,
			@RMIWrap(RMIArrayListWrapper.class) Iterable<? extends String> names)
			throws NullPointerException, InvalidPathFormatException {
		return SakerPathFiles.resolveAtRelativePathNames(basedir, names);
	}

	/**
	 * Resolves a file but not a directory with the given path names against a base directory.
	 * <p>
	 * The path names are interpreted as if they were relative compared to the base directory.
	 * 
	 * @param basedir
	 *            The base directory.
	 * @param names
	 *            The relative path names.
	 * @return The resolved file or <code>null</code> if not found or it's a directory.
	 * @throws NullPointerException
	 *             If any of the arguments or path names are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If any of the path name has invalid format.
	 * @see #resolveFileAtRelativePath(SakerDirectory, SakerPath)
	 * @see SakerPathFiles#requireValidFileName(String)
	 * @see SakerPathFiles#resolveFileAtRelativePathNames(SakerDirectory, Iterable)
	 */
	public default SakerFile resolveFileAtRelativePathNames(SakerDirectory basedir,
			@RMIWrap(RMIArrayListWrapper.class) Iterable<? extends String> names)
			throws NullPointerException, InvalidPathFormatException {
		return SakerPathFiles.resolveFileAtRelativePathNames(basedir, names);
	}

	/**
	 * Resolves a directory with the given path names against a base directory.
	 * <p>
	 * The path names are interpreted as if they were relative compared to the base directory.
	 * 
	 * @param basedir
	 *            The base directory.
	 * @param names
	 *            The relative path names.
	 * @return The resolved directory or <code>null</code> if a directory was not found at the path.
	 * @throws NullPointerException
	 *             If any of the arguments or path names are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If any of the path name has invalid format.
	 * @see #resolveDirectoryAtRelativePath(SakerDirectory, SakerPath)
	 * @see SakerPathFiles#requireValidFileName(String)
	 * @see SakerPathFiles#resolveDirectoryAtRelativePathNames(SakerDirectory, Iterable)
	 */
	public default SakerDirectory resolveDirectoryAtRelativePathNames(SakerDirectory basedir,
			@RMIWrap(RMIArrayListWrapper.class) Iterable<? extends String> names)
			throws NullPointerException, InvalidPathFormatException {
		return SakerPathFiles.resolveDirectoryAtRelativePathNames(basedir, names);
	}

	/**
	 * Resolves a directory with the given path names against a base directory, creating it if necessary, overwriting
	 * already existing files.
	 * <p>
	 * The path names are interpreted as if they were relative compared to the base directory.
	 * 
	 * @param basedir
	 *            The base directory.
	 * @param names
	 *            The relative path names.
	 * @return The directory at the given path.
	 * @throws NullPointerException
	 *             If any of the arguments or path names are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If any of the path name has invalid format.
	 * @see #resolveDirectoryAtRelativePathCreate(SakerDirectory, SakerPath)
	 * @see SakerPathFiles#requireValidFileName(String)
	 * @see SakerPathFiles#resolveDirectoryAtRelativePathNamesCreate(SakerDirectory, Iterable)
	 */
	public default SakerDirectory resolveDirectoryAtRelativePathNamesCreate(SakerDirectory basedir,
			@RMIWrap(RMIArrayListWrapper.class) Iterable<? extends String> names)
			throws NullPointerException, InvalidPathFormatException {
		return SakerPathFiles.resolveDirectoryAtRelativePathNamesCreate(basedir, names);
	}

	/**
	 * Resolves a directory with the given path names against a base directory, creating it if possible.
	 * <p>
	 * This method doesn't overwrite the already existing file at the path if it's not a directory.
	 * <p>
	 * The path names are interpreted as if they were relative compared to the base directory.
	 * 
	 * @param basedir
	 *            The base directory.
	 * @param names
	 *            The relative path names.
	 * @return The directory at the given path or <code>null</code> if it cannot be created.
	 * @throws NullPointerException
	 *             If any of the arguments or path names are <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If any of the path name has invalid format.
	 * @see #resolveDirectoryAtRelativePathCreateIfAbsent(SakerDirectory, SakerPath)
	 * @see SakerPathFiles#requireValidFileName(String)
	 * @see SakerPathFiles#resolveDirectoryAtRelativePathNamesCreateIfAbsent(SakerDirectory, Iterable)
	 */
	public default SakerDirectory resolveDirectoryAtRelativePathNamesCreateIfAbsent(SakerDirectory basedir,
			@RMIWrap(RMIArrayListWrapper.class) Iterable<? extends String> names)
			throws NullPointerException, InvalidPathFormatException {
		return SakerPathFiles.resolveDirectoryAtRelativePathNamesCreateIfAbsent(basedir, names);
	}

	/**
	 * Adds a file to the specified directory.
	 * <p>
	 * This method works the same way as {@link SakerDirectory#add(SakerFile)}, but returns a reference to the argument
	 * file. It is mostly useful when designing tasks for remote execution, as the files need to be transferred to the
	 * coordinator machine, therefore the actually added file to the directory might be different than the file passed
	 * as parameter. See {@link SakerDirectory#add(SakerFile)} documentation for more info.
	 * <p>
	 * This method can be called from both remote and non-remote executed tasks.
	 * 
	 * @param directory
	 *            The directory to add the file to.
	 * @param file
	 *            The file to add.
	 * @return The file which was added to the directory. It is the same as the parameter if called from non-remote
	 *             executed tasks.
	 * @throws NullPointerException
	 *             If any of the parameters are <code>null</code>.
	 */
	public default SakerFile addFile(SakerDirectory directory,
			@RMIWrap(RemoteExecutionSakerFileRMIWrapper.class) SakerFile file) throws NullPointerException {
		Objects.requireNonNull(directory, "directory");
		directory.add(file);
		return file;
	}

	/**
	 * Add a file to the specified directory only if there is no file present yet with the same name.
	 * <p>
	 * This method works the same way as {@link SakerDirectory#addIfAbsent(SakerFile)}, but returns the reference to the
	 * argument file. It is mostly useful when designing tasks for remote execution, as the files need to be transferred
	 * to the coordinator machine, therefore the actually added file to the directory might be different than the file
	 * passed as parameter. See {@link SakerDirectory#addIfAbsent(SakerFile)} documentation for more info.
	 * <p>
	 * This method can be called from both remote and non-remote executed tasks.
	 * 
	 * @param directory
	 *            The directory to add the file to.
	 * @param file
	 *            The file to add.
	 * @return The file which was added to the directory or <code>null</code> if there is already a file with the same
	 *             name. If non-<code>null</code>, it is the same as the parameter if called from non-remote executed
	 *             tasks.
	 * @throws NullPointerException
	 *             If any of the parameters are <code>null</code>.
	 */
	public default SakerFile addFileIfAbsent(SakerDirectory directory,
			@RMIWrap(RemoteExecutionSakerFileRMIWrapper.class) SakerFile file) throws NullPointerException {
		Objects.requireNonNull(directory, "directory");
		SakerFile prev = directory.addIfAbsent(file);
		if (prev == null) {
			//the file was added
			return file;
		}
		return null;
	}

	/**
	 * Add a file to the specified directory only if there is no directory present with the same name.
	 * <p>
	 * This method works the same way as {@link SakerDirectory#addOverwriteIfNotDirectory(SakerFile)}, but returns the
	 * reference to the argument file. It is mostly useful when designing tasks for remote execution, as the files need
	 * to be transferred to the coordinator machine, therefore the actually added file to the directory might be
	 * different than the file passed as parameter. See {@link SakerDirectory#addOverwriteIfNotDirectory(SakerFile)}
	 * documentation for more info.
	 * <p>
	 * This method can be called from both remote and non-remote executed tasks.
	 * 
	 * @param directory
	 *            The directory to add the file to.
	 * @param file
	 *            The file to add.
	 * @return The file which was added to the directory or <code>null</code> if there is a directory present with the
	 *             same name. If non-<code>null</code>, it is the same as the parameter if called from non-remote
	 *             executed tasks.
	 * @throws NullPointerException
	 *             If any of the parameters are <code>null</code>.
	 */
	public default SakerFile addFileOverwriteIfNotDirectory(SakerDirectory directory,
			@RMIWrap(RemoteExecutionSakerFileRMIWrapper.class) SakerFile file) throws NullPointerException {
		Objects.requireNonNull(directory, "directory");
		SakerDirectory presentdir = directory.addOverwriteIfNotDirectory(file);
		if (presentdir == null) {
			//the file was added
			return file;
		}
		//the directory wasn't overwritten
		return null;
	}

	/**
	 * Writes the contents of the file to the specified output stream.
	 * <p>
	 * Calling this method might implicitly synchronize the file to its real location, or to some other cache location
	 * when remote execution is used.
	 * 
	 * @param file
	 *            The file.
	 * @param os
	 *            The output stream to write the contents of the file to.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @see SakerFile#writeTo(OutputStream)
	 */
	public void writeTo(SakerFile file, @RMIWrap(RMIOutputStreamWrapper.class) OutputStream os)
			throws IOException, NullPointerException;

	/**
	 * Writes the contents of the file to the specified byte sink.
	 * <p>
	 * Calling this method might implicitly synchronize the file to its real location, or to some other cache location
	 * when remote execution is used.
	 * 
	 * @param file
	 *            The file.
	 * @param os
	 *            The byte sink to write the contents of the file to.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @see SakerFile#writeTo(ByteSink)
	 */
	public void writeTo(SakerFile file, ByteSink os) throws IOException, NullPointerException;

	/**
	 * Opens an input stream to the contents of the file.
	 * <p>
	 * Calling this method might implicitly synchronize the file to its real location, or to some other cache location
	 * when remote execution is used.
	 * 
	 * @param file
	 *            The file.
	 * @return The opened input stream.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the file is <code>null</code>.
	 * @see SakerFile#openInputStream()
	 */
	@RMIWrap(RMIInputStreamWrapper.class)
	public InputStream openInputStream(SakerFile file) throws IOException, NullPointerException;

	/**
	 * Opens a byte source to the contents of the file.
	 * <p>
	 * Calling this method might implicitly synchronize the file to its real location, or to some other cache location
	 * when remote execution is used.
	 * 
	 * @param file
	 *            The file.
	 * @return The opened byte source.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the file is <code>null</code>.
	 * @see SakerFile#openByteSource()
	 */
	public ByteSource openByteSource(SakerFile file) throws IOException, NullPointerException;

	/**
	 * Gets the raw contents of the file as a byte array.
	 * <p>
	 * Calling this method might implicitly synchronize the file to its real location, or to some other cache location
	 * when remote execution is used.
	 * 
	 * @param file
	 *            The file.
	 * @return The raw contents of the file.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the file is <code>null</code>.
	 * @see SakerFile#getBytes()
	 */
	public ByteArrayRegion getBytes(SakerFile file) throws IOException, NullPointerException;

	/**
	 * Gets the contents of the file as a {@link String}.
	 * <p>
	 * Calling this method might implicitly synchronize the file to its real location, or to some other cache location
	 * when remote execution is used.
	 * 
	 * @param file
	 *            The file.
	 * @return The string contents of the file.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the file is <code>null</code>.
	 * @see SakerFile#getContent()
	 */
	public String getContent(SakerFile file) throws IOException, NullPointerException;

	/**
	 * Creates a {@link SakerFile} instance which has its contents backed by the specified file system file at the given
	 * path.
	 * <p>
	 * This method can be used to include files from a given file system in the build. The resulting file needs to be
	 * added to a {@link SakerDirectory} to be included in the in-memory hierarchy.
	 * <p>
	 * If the file at the given path is a directory, a {@link SakerDirectory} instance will be returned that contains
	 * all the files which are present in the given directory. (The directory may be lazily populated.)
	 * 
	 * @param name
	 *            The name to create the file with.
	 * @param pathkey
	 *            The path to the contents which is used by the created file.
	 * @return The file or directory instance.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws IOException
	 *             If accessing the file at the given path results in an I/O error.
	 */
	public SakerFile createProviderPathFile(String name, ProviderHolderPathKey pathkey)
			throws NullPointerException, IOException;

	/**
	 * Creates a {@link SakerFile} instance which has its contents backed by the specified file system file at the given
	 * path, and associates the argument content descriptor with the current contents.
	 * <p>
	 * <b>The file represented with the given path must be a file, not a directory.</b>
	 * <p>
	 * The argument content descriptor will be associated with the current contents of the file system at the given
	 * path. This means that unless some other agent modifies the contents at the given path, calling
	 * {@link SakerFile#getContentDescriptor()} will return the passed argument for the returned file.
	 * <p>
	 * This method can be used to include files from a given file system in the build. The resulting file needs to be
	 * added to a {@link SakerDirectory} to be included in the in-memory hierarchy.
	 * 
	 * @param name
	 *            The name to create the file with.
	 * @param pathkey
	 *            The path to the contents which is used by the created file.
	 * @param currentpathcontentdescriptor
	 *            The content descriptor to associate with the current file system contents.
	 * @return The created file.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws IOException
	 *             If the operation fails due to I/O error.
	 * @throws InvalidFileTypeException
	 *             If the file is a directory.
	 */
	public SakerFile createProviderPathFile(String name, ProviderHolderPathKey pathkey,
			ContentDescriptor currentpathcontentdescriptor)
			throws IOException, NullPointerException, InvalidFileTypeException;

	/**
	 * Converts the given {@linkplain SakerFile files} to a map with their retrieved paths as keys.
	 * <p>
	 * The implementation doesn't examine the nature of the retrieved paths. If there are conflicting paths, then the
	 * resulting map may be smaller in size than the passed input. E.g. If multiple files have no parent, but same file
	 * name, only one will be in the result map with its name.
	 * <p>
	 * When calling this during remote execution, it is recommended that the files reside on the coordinator machine.
	 * 
	 * @param files
	 *            The files to map to their paths.
	 * @return A new map of paths mapped to the corresponding files.
	 * @throws NullPointerException
	 *             If the files or any of its elements are <code>null</code>.
	 */
	@RMIWrap(RMITreeMapSerializeKeyRemoteValueWrapper.class)
	public default NavigableMap<SakerPath, SakerFile> toPathFileMap(
			@RMIWrap(RMIArrayListRemoteElementWrapper.class) Iterable<? extends SakerFile> files)
			throws NullPointerException {
		return SakerPathFiles.toPathFileMap(files);
	}

	/**
	 * Converts the given {@linkplain SakerFile files} to a map with their retrieved paths as keys and content
	 * descriptors as values.
	 * <p>
	 * The implementation doesn't examine the nature of the retrieved paths. If there are conflicting paths, then the
	 * resulting map may be smaller in size than the passed input. E.g. If multiple files have no parent, but same file
	 * name, only one will be in the result map with its name.
	 * <p>
	 * When calling this during remote execution, it is recommended that the files reside on the coordinator machine.
	 * 
	 * @param files
	 *            The files to map to their paths and contents.
	 * @return A new map of paths mapped to the corresponding content descriptors.
	 * @throws NullPointerException
	 *             If the files or any of its elements are <code>null</code>.
	 */
	@RMIWrap(RMITreeMapSerializeKeySerializeValueWrapper.class)
	public default NavigableMap<SakerPath, ContentDescriptor> toPathContentMap(
			@RMIWrap(RMIArrayListRemoteElementWrapper.class) Iterable<? extends SakerFile> files)
			throws NullPointerException {
		return SakerPathFiles.toPathContentMap(files);
	}

	/**
	 * Gets the content descriptors of the children of the specified directory.
	 * <p>
	 * The resulting map is mapped by the file names of the children.
	 * 
	 * @param dir
	 *            The directory.
	 * @return A new map of file names mapped to their content descriptors.
	 * @throws NullPointerException
	 *             If the directory is <code>null</code>.
	 */
	public default NavigableMap<String, ContentDescriptor> getChildrenFileContents(SakerDirectory dir)
			throws NullPointerException {
		return SakerPathFiles.getChildrenFileContents(dir);
	}

//	
//	Execution related utility functions
//	

	/**
	 * Bulk method for {@link TaskContext#invalidate(PathKey)}.
	 * 
	 * @param pathkeys
	 *            The path keys to invalidate.
	 * @throws NullPointerException
	 *             If the argument, or any of its element is <code>null</code>.
	 */
	public void invalidate(@RMIWrap(RMIArrayListWrapper.class) Iterable<? extends PathKey> pathkeys)
			throws NullPointerException;

	/**
	 * Reports an unhandled exception that occurred during the execution of the task for informational purposes.
	 * <p>
	 * This method works the same way as {@link TaskContext#reportIgnoredException(ExceptionView)}, but takes care of
	 * converting the exception to an appropriate {@link ExceptionView}.
	 * 
	 * @param e
	 *            The exception.
	 * @see TaskContext#reportIgnoredException(ExceptionView)
	 */
	public default void reportIgnoredException(Throwable e) {
		if (e == null) {
			e = new NullPointerException("ignored exception");
		}
		getTaskContext().reportIgnoredException(TaskIdentifierExceptionView.create(e));
	}

	/**
	 * Synchronization operation flag representing no flags.
	 * 
	 * @see #synchronize(ProviderHolderPathKey, ProviderHolderPathKey, int)
	 */
	public static final int SYNCHRONIZE_FLAG_NONE = 0;
	/**
	 * Synchronization operation flag to specify that non-empty directories shouldn't be deleted as part of the
	 * operation.
	 * 
	 * @see #synchronize(ProviderHolderPathKey, ProviderHolderPathKey, int)
	 */
	public static final int SYNCHRONIZE_FLAG_NO_OVERWRITE_DIRECTORY = 1 << 0;

	/**
	 * Synchronization operation flag to signal that the synchronization may delete intermediate files when creating the
	 * parent directories.
	 * 
	 * @see #synchronize(ProviderHolderPathKey, ProviderHolderPathKey, int)
	 */
	public static final int SYNCHRONIZE_FLAG_DELETE_INTERMEDIATE_FILES = 1 << 1;

	/**
	 * Executes synchronization of files from the given source path to the specified target path.
	 * <p>
	 * This method synchronizes the contents of the source path file to the target path.
	 * <p>
	 * If the source file is a file, its contents will be updated to the target path. If a file already exists at the
	 * target path, it will be overwritten. Parent directories will be created at the target path if necessary.
	 * Specifying the flag {@link #SYNCHRONIZE_FLAG_NO_OVERWRITE_DIRECTORY} will cause the synchronization to throw an
	 * exception if a <b>non-empty</b> directory already exists at the target. Empty directories at the target path will
	 * be deleted and overwritten with the byte contents of the source file.
	 * <p>
	 * If the source file is a directory, then a directory will be created at the target path. <i>Children of the source
	 * directory are not synchronized.</i> If a file already exists at the target path, it will be deleted and a
	 * directory is created instead. If a directory already exists at the target path, no operations are done. Parent
	 * directories are created appropriately.
	 * 
	 * @param source
	 *            The source path key.
	 * @param target
	 *            The synchronization target path key.
	 * @param syncflag
	 *            The synchronization flags. 0, or any of the <code>SYNCHRONIZE_FLAG_*</code> constants in the class.
	 * @return The content descriptor that is associated with the synchronized contents.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	@RMISerialize
	public ContentDescriptor synchronize(ProviderHolderPathKey source, ProviderHolderPathKey target, int syncflag)
			throws IOException, NullPointerException;

	/**
	 * Add and synchronizes a {@link SakerFile} to the specified directory with the given name that has its contents
	 * backed by the given path key.
	 * <p>
	 * This method can be used to perform multiple operations in a single call. The method will
	 * {@linkplain TaskContext#invalidate(PathKey) invalidate} the contents at the given path key and
	 * {@linkplain #createProviderPathFile(String, ProviderHolderPathKey) create a provider path file} for the given
	 * path key. The created {@link SakerFile} is the added to the argument directory, and then
	 * {@linkplain SakerFile#synchronize() syncronized}.
	 * <p>
	 * Calling this method can be useful to batch the above operations in a single call. It is usually useful when you
	 * invoke an external process that produces a result file and want to add it to the build system file hierarchy.
	 * 
	 * @param directory
	 *            The directory to add the file to.
	 * @param pathkey
	 *            The path key at which the given result file resides.
	 * @param filename
	 *            The name of the created {@link SakerFile} that is added to the directory.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public void addSynchronizeInvalidatedProviderPathFileToDirectory(SakerDirectory directory,
			ProviderHolderPathKey pathkey, String filename) throws IOException, NullPointerException;

	/**
	 * Executes the mirroring for a directory at the given execution path.
	 * <p>
	 * The method will resolve the specified path to a directory and execute the
	 * {@linkplain TaskContext#mirror(SakerFile, DirectoryVisitPredicate) mirroring}.
	 * <p>
	 * If the file doesn't exist, or not a directory, an exception is thrown.
	 * 
	 * @param path
	 *            The path of the directory to mirror. Relative paths are resolved against the
	 *            {@linkplain TaskContext#getTaskWorkingDirectoryPath() task working directory}.
	 * @param synchpredicate
	 *            The predicate to use for mirroring. If this is <code>null</code>,
	 *            {@link DirectoryVisitPredicate#everything()} will be used.
	 * @return The path to the mirrored directory.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws InvalidFileTypeException
	 *             If the file at the path is not a directory.
	 * @throws FileNotFoundException
	 *             If no file found at the given path.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 */
	@RMIForbidden
	public Path mirrorDirectoryAtPath(SakerPath path, DirectoryVisitPredicate synchpredicate)
			throws IOException, InvalidFileTypeException, FileNotFoundException, NullPointerException;

	/**
	 * Executes the mirroring for a file at the given execution path.
	 * <p>
	 * The method will resolve the specified path to a file and execute the {@linkplain TaskContext#mirror(SakerFile)
	 * mirroring}.
	 * <p>
	 * If the file doesn't exist, or is a directory, an exception is thrown.
	 * 
	 * @param path
	 *            The path of the file to mirror. Relative paths are resolved against the
	 *            {@linkplain TaskContext#getTaskWorkingDirectoryPath() task working directory}.
	 * @return The path to the mirrored file.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws InvalidFileTypeException
	 *             If the file at the path is a directory.
	 * @throws FileNotFoundException
	 *             If no file found at the given path.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 * @see #mirrorFileAtPathContents(SakerPath)
	 */
	@RMIForbidden
	public Path mirrorFileAtPath(SakerPath path)
			throws IOException, InvalidFileTypeException, FileNotFoundException, NullPointerException;

	/**
	 * Executes the mirroring for a file at the given execution path and gets the file content descriptor.
	 * <p>
	 * The method works the same way as {@link #mirrorFileAtPath(SakerPath)}, but returns the content descriptor of the
	 * mirrored file.
	 * 
	 * @param path
	 *            The path of the file to mirror. Relative paths are resolved against the
	 *            {@linkplain TaskContext#getTaskWorkingDirectoryPath() task working directory}.
	 * @return The path and contents of the mirrored file.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws InvalidFileTypeException
	 *             If the file at the path is a directory.
	 * @throws FileNotFoundException
	 *             If no file found at the given path.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 */
	@RMIForbidden
	public MirroredFileContents mirrorFileAtPathContents(SakerPath path)
			throws IOException, InvalidFileTypeException, FileNotFoundException, NullPointerException;

	/**
	 * Holds information about a {@linkplain #getPath() path} and associated {@linkplain #getContents() contents}.
	 * <p>
	 * The class is usually used as a plain data holder class or as a result of an operation.
	 */
	public static final class MirroredFileContents {
		private Path path;
		private ContentDescriptor contents;

		/**
		 * Creates a new instance and initializes it with the given arguments.
		 * <p>
		 * The arguments may be <code>null</code>.
		 * 
		 * @param path
		 *            The path.
		 * @param contents
		 *            The contents.
		 */
		public MirroredFileContents(Path path, ContentDescriptor contents) {
			this.path = path;
			this.contents = contents;
		}

		/**
		 * Gets the path.
		 * 
		 * @return The path. May be <code>null</code>.
		 */
		public Path getPath() {
			return path;
		}

		/**
		 * Gets the contents.
		 * 
		 * @return The contents. May be <code>null</code>.
		 */
		public ContentDescriptor getContents() {
			return contents;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((contents == null) ? 0 : contents.hashCode());
			result = prime * result + ((path == null) ? 0 : path.hashCode());
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
			MirroredFileContents other = (MirroredFileContents) obj;
			if (contents == null) {
				if (other.contents != null)
					return false;
			} else if (!contents.equals(other.contents))
				return false;
			if (path == null) {
				if (other.path != null)
					return false;
			} else if (!path.equals(other.path))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + (path != null ? "path=" + path + ", " : "")
					+ (contents != null ? "contents=" + contents : "") + "]";
		}

	}
}
