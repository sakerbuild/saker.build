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
import java.io.Serializable;
import java.nio.file.Path;
import java.util.NavigableMap;
import java.util.Set;

import saker.apiextract.api.PublicApi;
import saker.build.exception.FileMirroringUnavailableException;
import saker.build.exception.TaskIdentifierExceptionView;
import saker.build.file.DirectoryVisitPredicate;
import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.PathKey;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.ide.configuration.IDEConfiguration;
import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionProperty;
import saker.build.runtime.execution.FileDataComputer;
import saker.build.runtime.execution.SakerLog;
import saker.build.runtime.execution.SecretInputReader;
import saker.build.task.delta.BuildDelta;
import saker.build.task.delta.DeltaType;
import saker.build.task.delta.EnvironmentDependencyDelta;
import saker.build.task.delta.ExecutionDependencyDelta;
import saker.build.task.delta.FileChangeDelta;
import saker.build.task.dependencies.FileCollectionStrategy;
import saker.build.task.dependencies.TaskOutputChangeDetector;
import saker.build.task.exception.IllegalTaskOperationException;
import saker.build.task.exception.InnerTaskInitializationException;
import saker.build.task.exception.InvalidTaskInvocationConfigurationException;
import saker.build.task.exception.TaskEnvironmentSelectionFailedException;
import saker.build.task.exception.TaskExecutionException;
import saker.build.task.exception.TaskExecutionFailedException;
import saker.build.task.exception.TaskIdentifierConflictException;
import saker.build.task.exception.TaskStandardIOLockIllegalStateException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.task.utils.TaskUtils;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIForbidden;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.rmi.wrap.RMIHashSetWrapper;
import saker.build.thirdparty.saker.util.rmi.wrap.RMITreeMapSerializeKeyRemoteValueWrapper;
import saker.build.util.exc.ExceptionView;
import saker.build.util.property.IDEConfigurationRequiredExecutionProperty;

/**
 * Task context is the primary interface for tasks to interact with the build runtime.
 * <p>
 * Task context provides the functions to handle task execution appropriately. The task context is responsible for, but
 * not limited to providing the following functionality:
 * <ul>
 * <li>Starting tasks</li>
 * <li>Getting task results and handling dependencies</li>
 * <li>Providing access to task deltas</li>
 * <li>Reporting dependencies (e.g. file, task, etc...)</li>
 * <li>Providing access to task related I/O</li>
 * <li>Specifying work directories (e.g. build and working directory)</li>
 * <li>Accessing outputs from previous runs</li>
 * <li>File mirroring to local storage</li>
 * <li>Performing other task execution related operations</li>
 * </ul>
 * <p>
 * Each executed task have their own private task context.
 * <p>
 * Each task is run with a non-empty set of build deltas that triggered its execution. For tasks which are executed for
 * the first time, {@link DeltaType#NEW_TASK} is used. For tasks which already run previously will have their build
 * deltas calculated. Deltas can be semantically separated into two categories:
 * <ul>
 * <li>Executional deltas</li>
 * <li>File change deltas</li>
 * </ul>
 * <p>
 * Executional deltas are related to task execution control. See possible {@link DeltaType} enumerations which do not
 * have <i>FILE</i> in their names.
 * <p>
 * File deltas which represent any file related changes compared to the last execution.
 * <p>
 * <b>Important</b> aspect of deltas is that they are calculated in two phases. First any non-file deltas are determined
 * (e.g. checking property dependency changes, checking if any input dependency task has changed, etc...), then if no
 * changes were detected, file deltas will be calculated. If there are any non-file deltas, then the computation of the
 * file deltas will be deferred until they are accessed by the executed task. <br>
 * By keeping this in mind, task implementations should wait for any input tasks first, and <i>after</i> that they
 * should check for any file related changes. <i>(This workflow should be kept even when deltas are not accessed. Wait
 * for input tasks first, then examine files.)</i>
 * <p>
 * We can examine the importance of this by taking a look at the following example:
 * <p>
 * The duty of task <i>A</i> is to create a file based on its configuration, let's say <i>a.txt</i>. Task <i>B</i> has
 * task <i>A</i> as an input dependency, will examine <i>a.txt</i> and produce its result. <br>
 * Let's assume that both tasks had previous runs, and examine the two scenarios where the above workflow is kept, and
 * violated. The task <i>A</i> and task <i>B</i> is started concurrently in the build system. <br>
 * <ul>
 * <li>When the workflow is kept, the task <i>B</i> will start with a delta of {@link DeltaType#TASK_CHANGE} as the
 * output task <i>A</i> cannot be determined, and is still running. Task <i>B</i> waits for task <i>A</i> to finish, and
 * checks the file deltas for any change. The file deltas are computed by the build runtime, and a change in
 * <i>a.txt</i> is correctly detected. Task <i>B</i> and <i>A</i> both produce correct outputs.</li>
 * <li>When the workflow is violated: Task <i>B</i> starts, and checks for the file deltas. Task <i>A</i> has not yet
 * finished. The build runtime computes the file deltas, and sees that <i>a.txt</i> still have the same contents as it
 * is expected for task <i>B</i>. Task <i>B</i> will decide that the file is up to date, and wait for task <i>A</i> to
 * finish as it is its input dependency as well. As the tasks run concurrently, task <i>A</i> updates the file
 * <i>a.txt</i> and finishes. The waiting by task <i>B</i> for task <i>A</i> completes, and task <i>B</i> finishes as
 * well. <br>
 * In the end we see that task <i>B</i> didn't recalculate its outputs based on the changes of <i>a.txt</i> as it didn't
 * notice the changes for it, because it decided to check the file deltas before waiting for its input dependency tasks.
 * <p>
 * This behaviour results in incorrect clean and incremental builds, and will result in ambiguous build results.</li>
 * </ul>
 * <p>
 * As an emphasis one more time, the core workflow for a task consists of first waiting for any input task dependencies,
 * and then handling file related calculations. <br>
 * If a task decides to have different task input dependencies based on contents of any file, then that is an erroneous
 * implementation.
 * <p>
 * The build runtime will emit a warning on a best-effort basis (it tries to always check this requirement, but might
 * not always detect it) when this workflow is violated.
 * <p>
 * Waiting for tasks which you've transitively started from this task itself does not violate this workflow.
 * <p>
 * Tasks can report file dependencies which state the expected content for a given path. File dependencies can have
 * arbitary nullable tags specified which can be used to semantically partition the files for the task and handle
 * incremental builds more easily. If a file dependency is reported with a given tag, then in case of changes, the
 * resulting {@link FileChangeDelta} will have the specified tag assigned to it.
 * <p>
 * It is also possible to query file deltas based on tags to make the tasks able to work on a set of changes detected by
 * the build runtime.
 * <p>
 * Simple file tag example based on an use-case for C++ compilation:
 * <p>
 * The task reports input dependency header files (.h) with the <i>HEADER</i> tag and compilation units (.cpp) with the
 * <i>SOURCE</i> tag. <br>
 * When the task is next time rerun, it can check for changes in the header files and source files separately. If it
 * sees that no changes occurred to any header files (via getting deltas for the <i>HEADER</i> tag), then it can skip
 * cross-examining which sources used the modified headers. In this case it will only need to get the file deltas for
 * the <i>SOURCE</i> tag and recompile the affected sources. <br>
 * This mechanism allows easier paralellization, separation of concerns and increased performance. <br>
 * (See {@link TaskFileDeltas#getFileDeltasWithTag(Object)})
 * <p>
 * Task contexts provide access to a private standard I/O streams for the task. The standard output can be used to
 * provide information to the user during task execution. For more information see {@link #getStandardOut()},
 * {@link #getStandardErr()}, {@link #getStandardIn()}.
 * <p>
 * The task context provides functions to mirror the files from the in-memory hierarchy to the local file system. File
 * mirroring is the process of taking a file and persisting it to the local file system. This is required as some tasks
 * might choose to implement their features by invoking external processes, which have no access to the in-memory
 * hierarchy. <br>
 * Good example for this can be C++ compilation: A task would take the source files and header directories, and mirror
 * them to the local file system. As a result of mirroring, the files will have a {@link Path} through which they can be
 * accessed externally. The task starts the C++ compiler processes with the received mirror paths as their arguments.
 * After running the compilation, the task can retrieve the results from the file system, and instantiate the in-memory
 * file representation for the build execution.
 * <p>
 * File mirroring is a necessary feature, as there are scenarios when the in-memory files have no direct local file
 * system file associated with them. One specific scenario for this is when remote execution is used to invoke a task.
 * <p>
 * The process of file mirroring will not persist a file twice if it has a path that should reside on the local file
 * system. This means that mirroring is basically 'free' performance-wise when a file is local file system compatible,
 * but only needs to do any work when a more complicated build setup is used. The execution context uses the
 * {@link SakerFile} synchronization algorithm to persist the mirrored files, so files with same contents will not
 * require unnecessary I/O operations.
 * <p>
 * The task context provides a way for deriving data from files based on their contents, and caching these datas to be
 * used by multiple tasks. This can be useful when multiple tasks might access the same computed data from files as
 * unnecessary redundant computations can be avoided. See {@link #computeFileContentData(SakerFile, FileDataComputer)}.
 * <p>
 * Methods of this class may throw (but not required in all cases) {@link IllegalTaskOperationException} if it detects
 * that they are being called after the corresponding task execution has finished. References to task contexts should
 * not be retained after the task execution is over.
 * <p>
 * The {@link #getTaskUtilities()} method provides access to an utility class which consists of methods that can improve
 * performance when handling multiple dependencies, files, and related operations. The use of this class is especially
 * recommended when designing remote executable tasks.
 * <p>
 * The {@link TaskUtils} static utility class provides utility functions for common operations for tasks.
 * <p>
 * Clients should not implement this interface.
 * 
 * @see Task#run(TaskContext)
 * @see TaskUtils
 * @see BuildDelta
 */
@PublicApi
public interface TaskContext extends TaskResultResolver, TaskDirectoryContext, TaskDirectoryPathContext {
	/**
	 * Gets the execution context which is used to run this task.
	 * 
	 * @return The execution context.
	 */
	@RMICacheResult
	public ExecutionContext getExecutionContext();

	/**
	 * Gets the output of the task from the previous run.
	 * 
	 * @param <T>
	 *            The expected type of the previous output.
	 * @param type
	 *            The type to check if the previous output is an instance of.
	 * @return The result of the previous run. <code>null</code> if the task was not run before this execution, it
	 *             returned <code>null</code>, or it is not an instance of the specified type.
	 * @throws NullPointerException
	 *             If tag or type is <code>null</code>.
	 * @see Task#run(TaskContext)
	 */
	@RMISerialize
	public <T> T getPreviousTaskOutput(Class<T> type) throws NullPointerException;

	/**
	 * Gets the previously set task output for the given tag.
	 * 
	 * @param <T>
	 *            The expected type of the value.
	 * @param tag
	 *            The tag to retrieve the associated value for.
	 * @param type
	 *            The type to check if the associated value is an instance of.
	 * @return The value which was associated to the specified tag. <code>null</code> if the task was not run before
	 *             this execution, the value was not found, or it is not an instance of the specified type.
	 * @throws NullPointerException
	 *             If tag or type is <code>null</code>.
	 * @see #setTaskOutput(Object, Object)
	 */
	@RMISerialize
	public <T> T getPreviousTaskOutput(@RMISerialize Object tag, Class<T> type) throws NullPointerException;

	/**
	 * Sets the output of the task for a given tag.
	 * <p>
	 * Arbitrary tag-value pairs can be set as a task output which can be retrieved in a later execution when the task
	 * is incrementally invoked.
	 * <p>
	 * The tags and values should be preferrably {@link Externalizable}, but at least {@link Serializable}.
	 * <p>
	 * The build system does not handle these tags and values in a special way. They only serve a purpose of storing
	 * arbitrary data between incremental executions of the same task.
	 * <p>
	 * The tags should implement {@link Object#equals(Object)} and {@link Object#hashCode()}. If this method is called
	 * multiple times with the same tag, then the latter value will be associated with the given tag.
	 * 
	 * @param tag
	 *            An arbitrary tag to associate the value with.
	 * @param value
	 *            The value to set.
	 * @throws NullPointerException
	 *             If tag or value is <code>null</code>.
	 * @see #getPreviousTaskOutput(Object, Class)
	 */
	public void setTaskOutput(@RMISerialize Object tag, @RMISerialize Object value) throws NullPointerException;

	/**
	 * Sets an arbitrary meta-data as a result of this task.
	 * <p>
	 * Meta-datas can be used by the executing environment (e.g. IDEs, custom launchers) to extract information from the
	 * executed tasks.
	 * 
	 * @param metadataid
	 *            The identifier of the meta-data. Should follow package naming conventions.
	 * @param value
	 *            The value to set as meta-data.
	 * @throws NullPointerException
	 *             If any of the parameters are <code>null</code>.
	 */
	public void setMetaData(String metadataid, @RMISerialize Object value) throws NullPointerException;

	/**
	 * Gets the current state of previously reported file input dependencies for a given tag.
	 * <p>
	 * The resulting map will have one entry for every input file reported previously. The resulting map contains the
	 * files which were used to compute deltas for the task.
	 * <p>
	 * It is recommended to get the files using this method if there are no deltas for the given tag, as it can avoid
	 * unnecessary computations by the task.
	 * <p>
	 * <b>Important:</b> Calling this method will trigger the computation of file deltas. Any input dependency tasks
	 * should be waited for before calling this method. By waiting for tasks after file delta computation, you risk the
	 * file deltas being incorrectly reported which can result in incorrect incremental builds. See {@link TaskContext}
	 * documentation for more info.
	 * 
	 * @param tag
	 *            An arbitrary nullable tag. See {@link TaskContext} documentation for more info.
	 * @return An unmodifiable map of files which were reported as inputs. <code>null</code> values mean the file
	 *             doesn't exist at a given path.
	 */
	@RMIWrap(RMITreeMapSerializeKeyRemoteValueWrapper.class)
	public NavigableMap<SakerPath, ? extends SakerFile> getPreviousInputDependencies(@RMISerialize Object tag);

	/**
	 * Gets the current state of previously reported file output dependencies for a given tag.
	 * <p>
	 * The resulting map will have one entry for every output file reported previously. The resulting map contains the
	 * files which were used to compute deltas for the task.
	 * <p>
	 * <b>Important:</b> Calling this method will trigger the computation of file deltas. Any input dependency tasks
	 * should be waited for before calling this method. By waiting for tasks after file delta computation, you risk the
	 * file deltas being incorrectly reported which can result in incorrect incremental builds. See {@link TaskContext}
	 * documentation for more info.
	 * 
	 * @param tag
	 *            An arbitrary nullable tag. See {@link TaskContext} documentation for more info.
	 * @return An unmodifiable map of files which were reported as outputs. <code>null</code> values mean the file
	 *             doesn't exist at a given path.
	 */
	@RMIWrap(RMITreeMapSerializeKeyRemoteValueWrapper.class)
	public NavigableMap<SakerPath, ? extends SakerFile> getPreviousOutputDependencies(@RMISerialize Object tag);

	/**
	 * Gets the current state of collected files for a previously reported file addition dependency.
	 * <p>
	 * The resulting map will have the same entries as if the dependency was used to collect the files themselves. The
	 * resulting map was used to compute the deltas for the task.
	 * <p>
	 * Calling this method instead of collecting the files manually can result in performance increase as it can avoid
	 * duplicate computations.
	 * <p>
	 * If the file addition dependency was not reported in the previous run, <code>null</code> will be returned.
	 * <p>
	 * <b>Important:</b> Calling this method will trigger the computation of file deltas. Any input dependency tasks
	 * should be waited for before calling this method. By waiting for tasks after file delta computation, you risk the
	 * file deltas being incorrectly reported which can result in incorrect incremental builds. See {@link TaskContext}
	 * documentation for more info.
	 * 
	 * @param dependency
	 *            The previously reported file addition dependency.
	 * @return An unmodifiable map of files which were collected based on the parameter dependency or <code>null</code>
	 *             if the dependency was not reported previously.
	 * @throws NullPointerException
	 *             If the dependency is <code>null</code>.
	 */
	@RMIWrap(RMITreeMapSerializeKeyRemoteValueWrapper.class)
	public NavigableMap<SakerPath, SakerFile> getPreviousFileAdditionDependency(
			@RMISerialize FileCollectionStrategy dependency) throws NullPointerException;

	/**
	 * Gets the set of deltas which contain no file deltas.
	 * 
	 * @return An unmodifiable set of non-file deltas.
	 */
	@RMICacheResult
	@RMIWrap(RMIHashSetWrapper.class)
	public Set<? extends BuildDelta> getNonFileDeltas();

	/**
	 * Gets a file delta collection for all possible file delta types.
	 * <p>
	 * The returned container will include all file deltas regardless of {@link DeltaType}.
	 * <p>
	 * <b>Important:</b> Calling this method will trigger the computation of file deltas. Any input dependency tasks
	 * should be waited for before calling this method. By waiting for tasks after file delta computation, you risk the
	 * file deltas being incorrectly reported which can result in incorrect incremental builds. See {@link TaskContext}
	 * documentation for more info.
	 * 
	 * @return The complete file delta collection.
	 * @see #getFileDeltas(DeltaType)
	 */
	@RMICacheResult
	public TaskFileDeltas getFileDeltas();

	/**
	 * Gets a file delta collection for the given delta type.
	 * <p>
	 * The resulting container will only include deltas which type matches the given parameter.
	 * <p>
	 * If the parameter is not a file change related {@link DeltaType} then an empty container will be returned.
	 * <p>
	 * <b>Important:</b> Calling this method will trigger the computation of file deltas. Any input dependency tasks
	 * should be waited for before calling this method. By waiting for tasks after file delta computation, you risk the
	 * file deltas being incorrectly reported which can result in incorrect incremental builds. See {@link TaskContext}
	 * documentation for more info.
	 * 
	 * @param deltatype
	 *            The delta type to match.
	 * @return The file delta container for the given delta type.
	 * @throws NullPointerException
	 *             If the delta type is <code>null</code>.
	 */
	public TaskFileDeltas getFileDeltas(DeltaType deltatype) throws NullPointerException;

	/**
	 * Starts a task for execution.
	 * <p>
	 * Calling this method will post the specified task for execution to the build runtime. The time of execution is
	 * unspecified, can be run instantly, delayed to a later time, or started concurrently.
	 * <p>
	 * This method will raise a {@link TaskIdentifierConflictException} if the task with the same identifier was already
	 * started previously (by this or an other task) and the specified task factories does not equal. <b>Important:</b>
	 * It will not raise an exception if a task was already started previously but the execution parameters are
	 * different. If one needs to execute some task that relies on specific execution parameters, then the relevant
	 * parts of the execution parameters should be included in the task identifier as well.
	 * <p>
	 * For more convenient methods to start tasks, see {@link #getTaskUtilities()}.
	 * 
	 * @param <R>
	 *            The result type of the started task.
	 * @param taskid
	 *            The identifier for the task.
	 * @param taskfactory
	 *            The task factory to use for the started task.
	 * @param parameters
	 *            The task execution parameters, or <code>null</code> to use the defaults.
	 * @return A future handle for the started task.
	 * @throws TaskIdentifierConflictException
	 *             If a task with the same identifier is already started and the task factories does not equal.
	 * @throws NullPointerException
	 *             If task identifier or task factory is <code>null</code>.
	 * @throws IllegalTaskOperationException
	 *             If the task identifier is the same as the caller.
	 */
	public <R> TaskFuture<R> startTask(@RMISerialize TaskIdentifier taskid, @RMISerialize TaskFactory<R> taskfactory,
			TaskExecutionParameters parameters)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException;

	/**
	 * Starts an inner task to execute in the context of this task.
	 * <p>
	 * This method will start an inner task execution using the given task factory and parameters.
	 * <p>
	 * Inner tasks are handled in a completely different way as plain tasks. The are ought to be treated as a simple
	 * function that runs in the same task context as this task. When you start an inner task, the build system will
	 * instantiate a task object from the task factory, and run it with the same task context as <code>this</code> for
	 * its parameter. Any depedencies, and operations that the inner task executes will behave the same way if the
	 * caller task executed it.
	 * <p>
	 * Inner tasks don't have a task identifier. They are not tracked by the build system, and they are not
	 * automatically rerun when incremental changes are detected. The build system will not store the inner tasks
	 * between executions, and will not emit deltas if the associated task factories
	 * {@linkplain TaskFactory#equals(Object) are changed}.
	 * <p>
	 * As they don't have a task identifier, they are allowed to execute multiple times. As they are only valid in the
	 * context of the enclosing task, they are allowed to share state between them in regards to the enclosing task
	 * context.
	 * <p>
	 * The main reason of using inner tasks is to fine-grain the performance of a composite task. As inner tasks can
	 * share state, it may be beneficial for a task to split up its work into smaller inner tasks while possibly
	 * dispatching the work to remote clusters or balancing the load with computation tokens.
	 * <p>
	 * The build system will handle the additional methods of {@link TaskFactory} which specify where and how the task
	 * can be invoked. The capabilities of the tasks are taken into account when invoking inner tasks.
	 * <p>
	 * Inner tasks cannot be {@linkplain TaskFactory#CAPABILITY_CACHEABLE cacheable}. As they are not explicitly tracked
	 * by the build system, caching them is not allowed. If you need to cache inner tasks, consider using normal
	 * {@linkplain #startTask(TaskIdentifier, TaskFactory, TaskExecutionParameters) subtasks instead}.
	 * <p>
	 * If the inner task reports {@linkplain TaskFactory#getRequestedComputationTokenCount() computation tokens}, then
	 * the enclosing task must report the {@link TaskFactory#CAPABILITY_INNER_TASKS_COMPUTATIONAL} capability to ensure
	 * proper operation. This is in order to ensure that the enclosing task complies with the restrictions that
	 * computation token usage imposes.
	 * <p>
	 * Inner tasks may be declared to be {@linkplain TaskFactory#CAPABILITY_SHORT_TASK}, in which case they behave the
	 * same way normal tasks would work. They are invoked a single time and are not duplicated (see below). Generally,
	 * using short inner tasks have limited use-cases, as the work the inner task does can be executed directly in the
	 * enclosing task.
	 * <p>
	 * Inner tasks can select their execution environment the same way as normal tasks do. See
	 * {@link TaskFactory#getExecutionEnvironmentSelector()}. The execution environment selector will be used to find
	 * only one suitable environment. When duplication is used, any further execution environments will be chosen
	 * automatically based on the reported qualifier properties. If all of the qualifier properties match on a candidate
	 * environment, it will be added to the duplicateable environments without checking the suitability again with the
	 * environment selector. <br>
	 * <b>Note: </b>The qualifier environment properties will <b>not</b> be automatically added as a dependency for the
	 * task.
	 * <p>
	 * The inner task is being run based on the above mentioned capabilities and the execution parameters of this
	 * function. When specified so using the parameters, the inner tasks can be duplicated to other build clusters used
	 * by the current build execution. In this case the build system will dispatch the tasks to the appropriate clusters
	 * and execute them possibly multiple times on each one. This process is called <i>duplication</i>.
	 * <p>
	 * Inner task duplication occurrs with respect to the selected suitable execution environments and reported
	 * computation tokens. Duplication is useful when the enclosing task can divide its work up in a way that one
	 * execution unit will correspond to one inner task, but doesn't care about where the task is being executed. In
	 * this case the task can use as much computational resources that is available to it, therefore maximizing the
	 * build performance.
	 * <p>
	 * The result of this function is an object that provides access to the results of the inner tasks. The returned
	 * object can have zero, single, or multiple results of the inner tasks. As the number of times the task is invoked
	 * dependends on the specified execution parameters, it cannot be determined how many times the inner task will
	 * actually be duplicated. The returned object can be thought as an iterator on the results of the inner tasks.
	 * <p>
	 * The enclosing tasks are not required to wait for the completions of the inner tasks. If the
	 * {@link Task#run(TaskContext)} method of the enclosing task finishes before all of the inner tasks complete, the
	 * build system will wait for the inner tasks to complete, and then deem the enclosing task to be finished. When
	 * such scenario happens, the inner task duplications are automatically
	 * {@linkplain InnerTaskResults#cancelDuplicationOptionally() cancelled} if the execution parameters permit. If any
	 * inner tasks throw an exception during the finalization of the enclosing task will cause the enclosing task to
	 * fail as a result.
	 * <p>
	 * Inner task factories are not required to implement any serialization related functionality, only of there is
	 * remote execution involved. In that case the caller should make sure to properly transfer the task factory and any
	 * shared states over RMI.
	 * 
	 * @param <R>
	 *            The result type of the inner task execution.
	 * @param taskfactory
	 *            The inner task factory to execute.
	 * @param parameters
	 *            The execution parameters for the inner task or <code>null</code> to use the defaults.
	 * @return The results object for the started inner task(s).
	 * @throws NullPointerException
	 *             If the task factory is <code>null</code>.
	 * @throws InvalidTaskInvocationConfigurationException
	 *             If the task factory reports conflicting/invalid configuration for inner tasks.
	 * @throws InnerTaskInitializationException
	 *             If the starting of the inner tasks failed on the all of the selected execution environments.
	 * @throws TaskEnvironmentSelectionFailedException
	 *             If the task factory failed to select a suitable environment to execute on. This exception may be
	 *             delayed until the first call to {@link InnerTaskResults#getNext()}.
	 */
	public <R> InnerTaskResults<R> startInnerTask(@RMISerialize TaskFactory<R> taskfactory,
			InnerTaskExecutionParameters parameters)
			throws NullPointerException, InvalidTaskInvocationConfigurationException,
			TaskEnvironmentSelectionFailedException, InnerTaskInitializationException;

	/**
	 * Gets a future handle for the specified task.
	 * <p>
	 * The task is not required to be started, or exist in the build runtime as of the calling of this function. Futures
	 * can be obtained to not yet started tasks as well.
	 * 
	 * @param taskid
	 *            The task identifier.
	 * @return The future handle for the task.
	 * @throws NullPointerException
	 *             If the task identifier is <code>null</code>.
	 * @see TaskFuture
	 */
	public TaskFuture<?> getTaskFuture(@RMISerialize TaskIdentifier taskid) throws NullPointerException;

	/**
	 * Gets a dependency future handle for the specified task.
	 * <p>
	 * The task is not required to be started, or exist in the build runtime as of the calling of this function. Futures
	 * can be obtained to not yet started tasks as well.
	 * <p>
	 * Same as:
	 * 
	 * <pre>
	 * getTaskFuture(taskid).getAsDependencyFuture();
	 * </pre>
	 * 
	 * @param taskid
	 *            The task identifier.
	 * @return The dependency future handle for the task.
	 * @throws NullPointerException
	 *             If the task identifier is <code>null</code>.
	 */
	public default TaskDependencyFuture<?> getTaskDependencyFuture(@RMISerialize TaskIdentifier taskid)
			throws NullPointerException {
		return getTaskFuture(taskid).asDependencyFuture();
	}

	/**
	 * Gets the task result for the given task identifier.
	 * <p>
	 * This method waits for the specified task if needed, and works the same way as {@link TaskFuture#get()}.
	 * <p>
	 * If this method throws a {@link TaskExecutionFailedException}, it is considered to be a valid return scenario. A
	 * dependency on the the associated task will be installed as if a return value was returned.
	 * <p>
	 * Callers should handle the possiblity of tasks returning {@link StructuredTaskResult} instances.
	 * 
	 * @param taskid
	 *            The task identifier to retrieve the results for.
	 * @return The result of the task execution. Only <code>null</code> if the task returned <code>null</code> as a
	 *             result.
	 * @throws TaskExecutionException
	 *             In case of any exceptions related to the task execution.
	 * @throws NullPointerException
	 *             if the task identifier is <code>null</code>.
	 */
	@Override
	@RMISerialize
	public default Object getTaskResult(@RMISerialize TaskIdentifier taskid)
			throws TaskExecutionException, NullPointerException {
		return getTaskFuture(taskid).get();
	}

	/**
	 * Signals an error during the execution of this task to the build runtime.
	 * <p>
	 * Calling this function will set a pending cause exception for the task result. Tasks which retrieve the result of
	 * this task will receive this exception.
	 * <p>
	 * The execution of this task will continue, and the task may still return an object from the
	 * {@link Task#run(TaskContext)} method. This task result will not be visible to other tasks, but this task can
	 * still retrieve it using {@link #getPreviousTaskOutput(Class)} when it is run next time.
	 * <p>
	 * Aborting excecution using this functions is preferable to throwing an exception directly when the task is able to
	 * handle previously thrown exceptions in the next run.
	 * <p>
	 * If the task uses this function to abort, then any results produced by the current run will be visible in the next
	 * one. The base for delta calculation will be the current run instead of the last previously successfully finished
	 * run.
	 * <p>
	 * It is useful to use this function when the task actually finishes its calculations successfully, but it is
	 * required to abort due to a semantic build error.
	 * <p>
	 * This method may be called multiple times. The first exception that is used to call this method will be the
	 * {@linkplain Throwable#getCause() cause} of the task execution exception, and any further exceptions will be added
	 * as {@linkplain Throwable#addSuppressed(Throwable) suppressed} exceptions.
	 * 
	 * @param cause
	 *            The abortion cause exception which should be reported.
	 * @throws NullPointerException
	 *             If the cause is <code>null</code>.
	 */
	public void abortExecution(@RMISerialize Throwable cause) throws NullPointerException;

	/**
	 * Gets the task identifier which was used to start this task.
	 * 
	 * @return The task identifier of this task.
	 * @see TaskIdentifier
	 */
	@RMICacheResult
	@RMISerialize
	public TaskIdentifier getTaskId();

	/**
	 * Reports an environment dependency for this task to the build runtime.
	 * <p>
	 * This method records the dependency value for the given property for the execution runtime. When the task is
	 * executed next time in an incremental manner, the current value for the property will be compared to the expected
	 * value. If it changed, an appropriate delta will be triggered and the task will be rerun.
	 * 
	 * @param <T>
	 *            The type of the property
	 * @param environmentproperty
	 *            The environment property.
	 * @param expectedvalue
	 *            The expected value of the property.
	 * @throws NullPointerException
	 *             If the property is <code>null</code>.
	 * @throws IllegalTaskOperationException
	 *             If the property dependency was reported multiple times and they do not equal.
	 * @see DeltaType#ENVIRONMENT_PROPERTY_CHANGED
	 * @see EnvironmentDependencyDelta
	 */
	public <T> void reportEnvironmentDependency(@RMISerialize EnvironmentProperty<T> environmentproperty,
			@RMISerialize T expectedvalue) throws NullPointerException, IllegalTaskOperationException;

	/**
	 * Reports an execution property dependency for this task to the build runtime.
	 * <p>
	 * This method records the dependency value for the given property for the execution runtime. When the task is
	 * executed next time in an incremental manner, the current value for the property will be compared to the expected
	 * value. If it changed, an appropriate delta will be triggered and the task will be rerun.
	 * 
	 * @param <T>
	 *            The type of the property
	 * @param executionproperty
	 *            The execution property.
	 * @param expectedvalue
	 *            The expected value of the property.
	 * @throws NullPointerException
	 *             If the property is <code>null</code>.
	 * @throws IllegalTaskOperationException
	 *             If the property dependency was reported multiple times and they do not equal.
	 * @see DeltaType#EXECUTION_PROPERTY_CHANGED
	 * @see ExecutionDependencyDelta
	 */
	public <T> void reportExecutionDependency(@RMISerialize ExecutionProperty<T> executionproperty,
			@RMISerialize T expectedvalue) throws NullPointerException, IllegalTaskOperationException;

	/**
	 * Sets the task output change detector for this task.
	 * <p>
	 * Setting an output change detector for the self task can result in skipping rerunning the tasks which depend on
	 * this task. The set detector should report the output unchanged if the object returned from
	 * {@link Task#run(TaskContext)} can be considered as unchanged from an external perspective.
	 * <p>
	 * The task can report themselves as unchanged, if output files of the task changed, but the information held in the
	 * output object remained the same. Usually {@link EqualityTaskOutputChangeDetector} is used with this method
	 * instantiated with the returned output object.
	 * <p>
	 * A very simple example for this: <br>
	 * A task calculates the sum of two input integers. In the first run it calculates 1 + 3. The input integers change
	 * for the next build, and the task will now calculate 2 + 2. <br>
	 * As the outputs of the task is the same, setting a self change detector can result in not rerunning any other
	 * tasks which depend on this.
	 * 
	 * @param changedetector
	 *            The output change detector to set.
	 * @throws IllegalTaskOperationException
	 *             If the change detector was already set for this task. (This method can be called only once.)
	 * @throws NullPointerException
	 *             If the change detector is <code>null</code>.
	 */
	public void reportSelfTaskOutputChangeDetector(@RMISerialize TaskOutputChangeDetector changedetector)
			throws IllegalTaskOperationException, NullPointerException;

	/**
	 * Reports a file addition dependency for this task to the build runtime.
	 * <p>
	 * The build executor will collect the files for the dependency next time the task is incrementally run. If it
	 * detects any added files, then an appropriate delta will be triggered, and the task will be rerun.
	 * <p>
	 * <b>Important:</b> The task needs to report any used files as input dependencies via
	 * {@link #reportInputFileDependency(Object, SakerPath, ContentDescriptor)}, else a delta will be triggered for the
	 * unreported files as well next time the task is run.
	 * <p>
	 * Example: <br>
	 * The task reports a file addition dependency, and reports the file <i>A</i> as input. <br>
	 * The user adds file <i>B</i> as a new file. Next time the task is incrementally run, the build executor collects
	 * the files, and will report file <i>B</i> as an addition with the appropriate delta and rerun the task. <br>
	 * If the task didn't report file <i>A</i>, then the build executor would report file <i>A</i> as an addition, even
	 * though it is not newly added. It is important to report the files the task uses as input dependency.
	 * <p>
	 * A file addition dependency can be reported multiple times, with different tags as well.
	 * 
	 * @param tag
	 *            An arbitrary nullable tag. See {@link TaskContext} documentation for more info.
	 * @param dependency
	 *            The dependency to report.
	 * @throws NullPointerException
	 *             If dependency is <code>null</code>.
	 * @see DeltaType#INPUT_FILE_ADDITION
	 * @see FileChangeDelta
	 * @see #reportInputFileDependency(Object, SakerPath, ContentDescriptor)
	 */
	public void reportInputFileAdditionDependency(@RMISerialize Object tag,
			@RMISerialize FileCollectionStrategy dependency) throws NullPointerException;

	/**
	 * Reports a file input content dependency for this task to the build runtime.
	 * <p>
	 * The build executor will check if the contents of the file at the given path have changed next time the task is
	 * incrementally run. If it detects changes, then an appropriate delta will be triggered, and the task will be
	 * rerun.
	 * <p>
	 * When an input dependency is reported multiple times with the same path and tag, then the actually recorded
	 * contents is chosen in an implementation dependent manner, and no exception is thrown.
	 * <p>
	 * See {@link #getTaskUtilities()} for reporting bulk file dependencies, or working with file instances directly.
	 * 
	 * @param tag
	 *            An arbitrary nullable tag. See {@link TaskContext} documentation for more info.
	 * @param path
	 *            The path of the file. If relative, it will be resolved against the current task working directory.
	 * @param expectedcontent
	 *            The expected content of the file. <code>null</code> is treated as if the file is expected to not
	 *            exist.
	 * @throws NullPointerException
	 *             If path is <code>null</code>.
	 * @see DeltaType#INPUT_FILE_CHANGE
	 * @see FileChangeDelta
	 * @see CommonTaskContentDescriptors
	 */
	public void reportInputFileDependency(@RMISerialize Object tag, SakerPath path,
			@RMISerialize ContentDescriptor expectedcontent) throws NullPointerException;

	/**
	 * Reports a file output content dependency for this task to the build runtime.
	 * <p>
	 * The build executor will check if the contents of the file at the given path have changed next time the task is
	 * incrementally run. If it detects changes, then an appropriate delta will be triggered, and the task wil be rerun.
	 * <p>
	 * When an output dependency is reported multiple times with the same path and tag, then the actually recorded
	 * contents is chosen in an implementation dependent manner, and no exception is thrown
	 * <p>
	 * It is recommended that all output files are under the build directory, unless explicitly specified by the user.
	 * <p>
	 * The reported output files are not automatically synchronized by the build runtime. (See
	 * {@link SakerFile#synchronize()})
	 * <p>
	 * See {@link #getTaskUtilities()} for reporting bulk file dependencies, or working with file instances directly.
	 * 
	 * @param tag
	 *            An arbitrary nullable tag. See {@link TaskContext} documentation for more info.
	 * @param path
	 *            The path of the file. If relative, it will be resolved against the current task working directory.
	 * @param expectedcontent
	 *            The expected content of the file. <code>null</code> is treated as if the file is expected to not
	 *            exist.
	 * @throws NullPointerException
	 *             If path is <code>null</code>.
	 * @see DeltaType#OUTPUT_FILE_CHANGE
	 * @see FileChangeDelta
	 * @see CommonTaskContentDescriptors
	 */
	public void reportOutputFileDependency(@RMISerialize Object tag, SakerPath path,
			@RMISerialize ContentDescriptor expectedcontent) throws NullPointerException;

	/**
	 * Reports an IDE configuration for this task.
	 * <p>
	 * IDE configurations can be considered as meta-data from the tasks which can be used to properly configure an
	 * editor for IDE related features.
	 * <p>
	 * Tasks are not required to always report IDE configurations, it can be toggled by the user using execution
	 * parameters. If a task decides to adhere these configuration changes, make sure to report an execution dependency,
	 * so the task is rerun when the parameter changes. (See {@link IDEConfigurationRequiredExecutionProperty})
	 * 
	 * @param configuration
	 *            The IDE configuration.
	 * @throws NullPointerException
	 *             If the configuration is <code>null</code>.
	 * @see ExecutionContext#isIDEConfigurationRequired()
	 */
	public void reportIDEConfiguration(@RMISerialize IDEConfiguration configuration) throws NullPointerException;

	/**
	 * Sets the line display identifier for this task.
	 * <p>
	 * The line display identifier is prepended to each line printed to the standard output during the execution. The
	 * identifier format is surrounded by brackets. (E.g. <code>[&lt;identifier&gt;]</code>)
	 * <p>
	 * The display identifier is not printed to the standard error output.
	 * 
	 * @param displayid
	 *            The display id to set. Pass <code>null</code> to disable it.
	 * @throws IllegalTaskOperationException
	 *             If this method is called after the task has finished.
	 * @see #getStandardOut()
	 * @see #println(String)
	 */
	public void setStandardOutDisplayIdentifier(String displayid) throws IllegalTaskOperationException;

	/**
	 * Gets the standard output for this task.
	 * <p>
	 * The standard output is private to the currently executed task. It is buffered, and is flushed when the execution
	 * sees it as a best opportunity. The buffering occurs on a per-line basis, no partially finished lines will be
	 * printed, unless the execution-wide standard I/O lock is acquired.
	 * <p>
	 * The bytes written to this stream is examined and every line is prepended with the currently set display
	 * identifier. (See {@link #setStandardOutDisplayIdentifier(String)})
	 * <p>
	 * The bytes written to this stream is not replayed to the user if the task is not rerun due to no deltas. To print
	 * information which are replayed use {@link #println(String)}.
	 * <p>
	 * When buffered lines are flushed from this stream they might be interlaced with output lines of concurrently
	 * executing tasks. They might be interlaced, but they will not be mangled.
	 * 
	 * @return The standard output for this task.
	 * @throws IllegalTaskOperationException
	 *             If this method is called after the task has finished.
	 * @see #println(String)
	 * @see #getStandardIn()
	 */
	@RMICacheResult
	public ByteSink getStandardOut() throws IllegalTaskOperationException;

	/**
	 * Gets the standard error for this task.
	 * <p>
	 * The standard error is private to the currently executed task. It is buffered, but unlike the standard out, will
	 * not be flushed during the execution of the task, only after it has finished. It will be flushed as a block of
	 * bytes and will not be interlaced with concurrently executing tasks.
	 * <p>
	 * The display identifier is not prepended to the lines of this output. The contents written to standard err will
	 * not be replayed when the task is not rerun due to no deltas.
	 * <p>
	 * The returned sink is not thread-safe, the caller must ensure proper synchronization, otherwise bytes written to
	 * the sink may be lost, or scrambled.
	 *
	 * @return The standard error for this task.
	 * @throws IllegalTaskOperationException
	 *             If this method is called after the task has finished.
	 */
	@RMICacheResult
	public ByteSink getStandardErr() throws IllegalTaskOperationException;

	/**
	 * Gets the standard input for this task.
	 * <p>
	 * The standard input is shared by all concurrently running tasks in the same build environment. The current task
	 * must lock on the standard I/O lock via {@link #acquireStandardIOLock()} before calling any reading functions on
	 * the returned stream. Not locking will result in a {@link TaskStandardIOLockIllegalStateException} when calling
	 * reading functions.
	 * <p>
	 * The standard input and output can be both used when the lock is acquired to provide an interactive interface to
	 * the user.
	 * 
	 * @return The standard input for the build execution.
	 * @throws IllegalTaskOperationException
	 *             If this method is called after the task has finished.
	 * @see #acquireStandardIOLock()
	 */
	@RMICacheResult
	public ByteSource getStandardIn() throws IllegalTaskOperationException;

	/**
	 * Acquires the execution-wide lock for accessing the standard I/O.
	 * <p>
	 * Acquiring the lock will prevent any access to the standard I/O streams of the execution by other tasks. This
	 * means that the locker will have exclusive access to the input and output of the execution. Any output that is
	 * produced by concurrent tasks will be written out when this task releases the lock.
	 * <p>
	 * The lock is task execution based and not thread based. This means that the lock can be acquired on one thread and
	 * can be released on an other thread. Trying to acquire the lock multiple times will result in an exception.
	 * <p>
	 * It is recommended to acquire this lock when the task wants to display information that is necessary to be grouped
	 * together. Without locking, the lines printed to the output streams might be interlaced with the output of
	 * concurrent tasks.
	 * <p>
	 * It is required to acquire the lock to access the standard input. If the task wants to read input from the
	 * standard input stream and the lock is not acquired, then an exception will be thrown. This is to prevent
	 * misalignment of typed data from the user by concurrent output writing, and due to the fact that the standard
	 * input is one single shared stream of input for all concurrent tasks.
	 * 
	 * @throws InterruptedException
	 *             If the current thread is interrupted.
	 * @throws TaskStandardIOLockIllegalStateException
	 *             If the lock was already acquired by this task.
	 * @see #releaseStandardIOLock()
	 * @see #getStandardIn()
	 * @see #getStandardOut()
	 */
	public void acquireStandardIOLock() throws InterruptedException, TaskStandardIOLockIllegalStateException;

	/**
	 * Releases the previously acquired execution-wide lock for accessing the standard I/O.
	 * <p>
	 * When the lock is released all partially finished lines of {@link #getStandardOut()} will be ended.
	 * 
	 * @throws TaskStandardIOLockIllegalStateException
	 *             If the lock was not acquired.
	 * @see #acquireStandardIOLock()
	 * @see #getStandardIn()
	 * @see #getStandardOut()
	 */
	public void releaseStandardIOLock() throws TaskStandardIOLockIllegalStateException;

	/**
	 * Mirrors a file to the local file system.
	 * <p>
	 * If the file is a directory, then the parameter predicate will be used to select the files to mirror.
	 * <p>
	 * See {@link TaskContext} documentation for more information about mirroring.
	 * 
	 * @param file
	 *            The file to miror.
	 * @param synchpredicate
	 *            The predicate to use for mirroring. If this is <code>null</code>,
	 *            {@link DirectoryVisitPredicate#everything()} will be used.
	 * @return The path to the mirrored file.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If file is <code>null</code>.
	 * @throws FileMirroringUnavailableException
	 *             If file mirroring is not available for this file.
	 * @see ExecutionContext#toUnmirrorPath(Path)
	 * @see ExecutionContext#toMirrorPath(SakerPath)
	 * @see ExecutionContext#getMirrorDirectory()
	 */
	@RMIForbidden
	public Path mirror(SakerFile file, DirectoryVisitPredicate synchpredicate)
			throws IOException, NullPointerException, FileMirroringUnavailableException;

	/**
	 * Mirrors a file with all children, recursively if the file is a directory.
	 * <p>
	 * Same as calling:
	 * 
	 * <pre>
	 * {@linkplain #mirror(SakerFile, DirectoryVisitPredicate) mirror}(file, DirectoryVisitPredicate.everything());
	 * </pre>
	 * 
	 * @param file
	 *            The file to mirror.
	 * @return The path to the mirrored file.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If file is <code>null</code>.
	 * @throws FileMirroringUnavailableException
	 *             If file mirroring is not available for this file.
	 * @see ExecutionContext#toUnmirrorPath(Path)
	 * @see ExecutionContext#toMirrorPath(SakerPath)
	 * @see ExecutionContext#getMirrorDirectory()
	 */
	@RMIForbidden
	public default Path mirror(SakerFile file)
			throws IOException, NullPointerException, FileMirroringUnavailableException {
		return mirror(file, DirectoryVisitPredicate.everything());
	}

	/**
	 * Computes some data based on a contents of a file.
	 * <p>
	 * The execution context will ensure that a given data for a file and a computer is only computed once during an
	 * execution.
	 * <p>
	 * The computed datas will be cached during the execution. The cache uses the content descriptor, path of the file,
	 * and the computer as a key to the cached datas. Files which are not attached to any parents will not have their
	 * computed datas cached.
	 * <p>
	 * Multiple concurrent computation can run for a given file, but only one computation will run at the same time for
	 * {@linkplain FileDataComputer file data computers} that {@linkplain FileDataComputer#equals(Object) equal}.
	 * 
	 * @param <T>
	 *            The computed data type.
	 * @param file
	 *            The file for data computation.
	 * @param computer
	 *            The computer to use for deriving the data.
	 * @return The computed data.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the parameters are <code>null</code> or the computer computes <code>null</code> value.
	 * @throws RuntimeException
	 *             If the data computer throws a runtime exception. The exception is directly relayed to the caller.
	 */
	@RMISerialize
	public <T> T computeFileContentData(SakerFile file, @RMISerialize FileDataComputer<T> computer)
			throws IOException, NullPointerException, RuntimeException;

	/**
	 * Gets the input reader that can be used to read secret input from the user.
	 * <p>
	 * The returned reader can be used to prompt the user to enter data in a secret way. This usually means that the
	 * characters the user enters will not be directly displayed on the computer screen, but will be hidden. (E.g. the
	 * console doesn't echo back the characters, or using a password text box.)
	 * <p>
	 * Reading functions on the returned result may lock the {@linkplain #acquireStandardIOLock() standard IO lock}
	 * before reading the secret input, but may not based on the implementation.
	 * <p>
	 * Callers must handle the case if the secret reader is not available. This usually means that the build execution
	 * was configured in a way that direct input reading is not possible. (E.g. during a CI (continuous integration)
	 * build.)
	 * 
	 * @return The secret input reader or <code>null</code> if not available.
	 */
	@RMICacheResult
	public SecretInputReader getSecretReader();

	/**
	 * Prints a line of string to the standard output of this task.
	 * <p>
	 * The lines printed using this function will be replayed if the task is not executed again due to no incremental
	 * changes.
	 * <p>
	 * The line is automatically prepended by the display identifier if set.
	 * <p>
	 * To display uniformly formatted data, we recommend using {@link SakerLog} logging functions.
	 * <p>
	 * The argument line will be automatically appended with a line ending character(s).
	 * 
	 * @param line
	 *            The line to print.
	 * @see #setStandardOutDisplayIdentifier(String)
	 * @see SakerLog
	 * @throws NullPointerException
	 *             If the line is <code>null</code>
	 * @throws IllegalTaskOperationException
	 *             If this method is called after the task has finished.
	 */
	public void println(String line) throws NullPointerException, IllegalTaskOperationException;

	/**
	 * Stores a line of string to be replayed for the task if it is not re-run due to no incremental changes.
	 * <p>
	 * This method works the same way as {@link #println(String)}, however, it doesn't actually print the line to the
	 * output. It only stores the line to be printed if the task is not executed again due to no incremental changes.
	 * 
	 * @param line
	 *            The line to print.
	 * @throws NullPointerException
	 *             If the line is <code>null</code>
	 * @throws IllegalTaskOperationException
	 *             If this method is called after the task has finished.
	 */
	public void replayPrintln(String line) throws NullPointerException, IllegalTaskOperationException;

	/**
	 * Gets the utility class for this task context.
	 * <p>
	 * The utility instance provides bulk operations, utility functions, and extensions for working with task context.
	 * <p>
	 * Usage of this is especially recommended when designing remote executable tasks.
	 * <p>
	 * The task utilities exist for the purpose of keeping the {@link TaskContext} interface clean and functions which
	 * could bloat the interface are implemented in the utilities instance.
	 * 
	 * @return The utility instance.
	 */
	@RMICacheResult
	public TaskExecutionUtilities getTaskUtilities();

	/**
	 * Gets the progress monitor for this task.
	 * <p>
	 * The progress monitor can be used to check if the execution has been cancelled externally.
	 * 
	 * @return The progress monitor. (Never <code>null</code>.)
	 */
	@RMICacheResult
	public TaskProgressMonitor getProgressMonitor();

	/**
	 * Notifies the build system that a file at the given path was externally modified.
	 * <p>
	 * This method needs to be called when tasks decides to externally modify files which are referenced by the build
	 * system. <br>
	 * External modifications include: <br>
	 * <ul>
	 * <li>Modifying a file using an external process. (E.g. spawning an external compiler process)</li>
	 * <li>Using direct Java I/O APIs to modify files. E.g. {@link java.io.File}, {@link java.nio.file.Path} and related
	 * classes.</li>
	 * <li>Directly modifying files using {@linkplain SakerFileProvider file providers}.</li>
	 * <li>Other file modifications that aren't done through the {@link SakerFile} API.</li>
	 * </ul>
	 * This method is required to be called so synchronization of the in-memory file hierarchy can work correctly.
	 * <p>
	 * If task implementations forget to call this method, the synchronization of the files might not work as expected.
	 * Synchronizations might be skipped, while the file contents won't be the same as expected.
	 * <p>
	 * Generally if task implementations do not overwrite files multiple times, and keep intermediate files separate,
	 * they don't need to expect failures. Tasks should aim to mainly use the {@link SakerFile} API or if they really
	 * need to, only work with the local file system.
	 * <p>
	 * If task implementations only use {@link SakerFile#synchronize()} and related functions, they don't need to call
	 * this.
	 * <p>
	 * Calling this method will <b>not</b> modify the {@link SakerFile} in-memory hierarchy in any way.
	 * <p>
	 * Internal implementation note: This function invalidates the specified handle for the content database. As the
	 * content database can cache file disk contents, this method needs to be called so the cached data is invalidated.
	 * If this method is not called, then the content database might not re-check the disk contents before
	 * synchronization. In that case the synchronization will be skipped, even though it should happen.
	 * <p>
	 * When remote execution (via clusters) are used, this method will invalidate the local cache content database and
	 * the main content database as well. In some cases it can happen that the content database on the actual file
	 * system where the file resides will not be invalidated. This can happen when a task on cluster A modifies a file
	 * on cluster B. It is a very insignificant edge-case and it it strongly discouraged to configure an execution where
	 * such a scenario can happen.
	 * 
	 * @param pathkey
	 *            The path key to the file.
	 * @throws NullPointerException
	 *             If the path key is <code>null</code>.
	 * @see TaskExecutionUtilities#invalidate(Iterable)
	 * @see LocalFileProvider#getPathKey(Path)
	 * @see LocalFileProvider#getPathKeyStatic(Path)
	 * @see SakerPathFiles#getPathKey(SakerFileProvider, SakerPath)
	 */
	public void invalidate(PathKey pathkey) throws NullPointerException;

	/**
	 * Notifies the build system that a file at the given path was externally modified and retrieves the content
	 * descriptor for the path.
	 * <p>
	 * This method works the same way as {@link #invalidate(PathKey)}, but reads the content descriptor at the given
	 * path after the invalidation.
	 * 
	 * @param pathkey
	 *            The path key to the file.
	 * @return The current content descriptor for the path, or <code>null</code> if the file doesn't exist.
	 * @throws NullPointerException
	 *             If the path key is <code>null</code>.
	 */
	@RMISerialize
	public ContentDescriptor invalidateGetContentDescriptor(ProviderHolderPathKey pathkey) throws NullPointerException;

	/**
	 * Reports an unhandled exception that occurred during the execution of the task for informational purposes.
	 * <p>
	 * Unhandled exceptions do not modify the operation of the task or the build execution. The build system may present
	 * these to the user to debug or analyze possible inconsistent build output.
	 * <p>
	 * Ignoring an exception should not modify the output of a task in any way.
	 * <p>
	 * One good example for an ignored exception is one thrown during verbose logging to the standard output. When the
	 * task tries to write some verbose information about the execution output, but it fails with an exception, then the
	 * task might choose to ignore the thrown exception, as it doesn't modify the result of the task in any way, but
	 * only the displayed information to the user.
	 * <p>
	 * The build system may handle the ignored exceptions in an implementation dependent way.
	 * <p>
	 * If the parameter is <code>null</code>, a {@link NullPointerException} will be ignored.
	 * 
	 * @param e
	 *            The exception.
	 * @see TaskExecutionUtilities#reportIgnoredException(Throwable)
	 * @see TaskIdentifierExceptionView#create(Throwable)
	 */
	public void reportIgnoredException(ExceptionView e);
}
