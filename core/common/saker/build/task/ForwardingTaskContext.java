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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;

import saker.apiextract.api.PublicApi;
import saker.build.exception.FileMirroringUnavailableException;
import saker.build.exception.InvalidFileTypeException;
import saker.build.exception.InvalidPathFormatException;
import saker.build.exception.PropertyComputationFailedException;
import saker.build.file.DirectoryVisitPredicate;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.PathKey;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.ide.configuration.IDEConfiguration;
import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionProperty;
import saker.build.runtime.execution.FileDataComputer;
import saker.build.runtime.execution.SecretInputReader;
import saker.build.task.delta.BuildDelta;
import saker.build.task.delta.DeltaType;
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
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.util.exc.ExceptionView;

/**
 * {@link TaskContext} and {@link TaskExecutionUtilities} implementation that forwards its calls to a concrete
 * implementation.
 * <p>
 * Clients may extend this class. New methods may and will be added in the future.
 */
@PublicApi
public class ForwardingTaskContext implements TaskContext, TaskExecutionUtilities {
	/**
	 * The concrete {@link TaskContext}.
	 */
	protected final TaskContext taskContext;
	/**
	 * The concrete {@link TaskExecutionUtilities}.
	 */
	protected final TaskExecutionUtilities utilities;

	/**
	 * Creates a new instance initialized with the task context which to forward the calls.
	 * 
	 * @param taskContext
	 *            The task context.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public ForwardingTaskContext(TaskContext taskContext) throws NullPointerException {
		Objects.requireNonNull(taskContext, "task context");
		this.taskContext = taskContext;
		this.utilities = taskContext.getTaskUtilities();
	}

	@Override
	public SakerDirectory getTaskWorkingDirectory() {
		return taskContext.getTaskWorkingDirectory();
	}

	@Override
	public SakerDirectory getTaskBuildDirectory() {
		return taskContext.getTaskBuildDirectory();
	}

	@Override
	public SakerPath getTaskWorkingDirectoryPath() {
		return taskContext.getTaskWorkingDirectoryPath();
	}

	@Override
	public SakerPath getTaskBuildDirectoryPath() {
		return taskContext.getTaskBuildDirectoryPath();
	}

	@Override
	public ExecutionContext getExecutionContext() {
		return taskContext.getExecutionContext();
	}

	@Override
	public <T> T getPreviousTaskOutput(Class<T> type) throws NullPointerException {
		return taskContext.getPreviousTaskOutput(type);
	}

	@Override
	public <T> T getPreviousTaskOutput(Object tag, Class<T> type) throws NullPointerException {
		return taskContext.getPreviousTaskOutput(overrideTaskOutputTag(tag), type);
	}

	@Override
	public void setTaskOutput(Object tag, Object value) throws NullPointerException {
		taskContext.setTaskOutput(overrideTaskOutputTag(tag), value);
	}

	@Override
	public void setMetaData(String metadataid, Object value) throws NullPointerException {
		taskContext.setMetaData(metadataid, value);
	}

	@Override
	public NavigableMap<SakerPath, ? extends SakerFile> getPreviousInputDependencies(Object tag) {
		return taskContext.getPreviousInputDependencies(overrideInputFileTag(tag));
	}

	@Override
	public NavigableMap<SakerPath, ? extends SakerFile> getPreviousOutputDependencies(Object tag) {
		return taskContext.getPreviousOutputDependencies(overrideOutputFileTag(tag));
	}

	@Override
	public NavigableMap<SakerPath, SakerFile> getPreviousFileAdditionDependency(FileCollectionStrategy dependency)
			throws NullPointerException {
		return taskContext.getPreviousFileAdditionDependency(dependency);
	}

	@Override
	public Set<? extends BuildDelta> getNonFileDeltas() {
		return taskContext.getNonFileDeltas();
	}

	@Override
	public TaskFileDeltas getFileDeltas() {
		return taskContext.getFileDeltas();
	}

	@Override
	public TaskFileDeltas getFileDeltas(DeltaType deltatype) throws NullPointerException {
		return taskContext.getFileDeltas();
	}

	@Override
	public <R> TaskFuture<R> startTask(TaskIdentifier taskid, TaskFactory<R> taskfactory,
			TaskExecutionParameters parameters)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		return taskContext.startTask(taskid, taskfactory, parameters);
	}

	@Override
	public <R> InnerTaskResults<R> startInnerTask(TaskFactory<R> taskfactory, InnerTaskExecutionParameters parameters)
			throws NullPointerException, InvalidTaskInvocationConfigurationException,
			TaskEnvironmentSelectionFailedException, InnerTaskInitializationException {
		return taskContext.startInnerTask(taskfactory, parameters);
	}

	@Override
	public TaskFuture<?> getTaskFuture(TaskIdentifier taskid) throws NullPointerException {
		return taskContext.getTaskFuture(taskid);
	}

	@Override
	public void abortExecution(Throwable cause) throws NullPointerException {
		taskContext.abortExecution(cause);
	}

	@Override
	public TaskIdentifier getTaskId() {
		return taskContext.getTaskId();
	}

	@Override
	public <T> void reportEnvironmentDependency(EnvironmentProperty<T> environmentproperty, T expectedvalue)
			throws NullPointerException, IllegalTaskOperationException {
		taskContext.reportEnvironmentDependency(environmentproperty, expectedvalue);
	}

	@Override
	public <T> void reportExecutionDependency(ExecutionProperty<T> executionproperty, T expectedvalue)
			throws NullPointerException, IllegalTaskOperationException {
		taskContext.reportExecutionDependency(executionproperty, expectedvalue);
	}

	@Override
	public void reportSelfTaskOutputChangeDetector(TaskOutputChangeDetector changedetector)
			throws IllegalTaskOperationException, NullPointerException {
		taskContext.reportSelfTaskOutputChangeDetector(changedetector);
	}

	@Override
	public void reportInputFileAdditionDependency(Object tag, FileCollectionStrategy dependency)
			throws NullPointerException {
		taskContext.reportInputFileAdditionDependency(overrideInputFileTag(tag), dependency);
	}

	@Override
	public void reportInputFileDependency(Object tag, SakerPath path, ContentDescriptor expectedcontent)
			throws NullPointerException {
		taskContext.reportInputFileDependency(overrideInputFileTag(tag), path, expectedcontent);
	}

	@Override
	public void reportOutputFileDependency(Object tag, SakerPath path, ContentDescriptor expectedcontent)
			throws NullPointerException {
		taskContext.reportOutputFileDependency(overrideOutputFileTag(tag), path, expectedcontent);
	}

	@Override
	public void reportIDEConfiguration(IDEConfiguration configuration) throws NullPointerException {
		taskContext.reportIDEConfiguration(configuration);
	}

	@Override
	public void setStandardOutDisplayIdentifier(String displayid) {
		taskContext.setStandardOutDisplayIdentifier(displayid);
	}

	@Override
	public ByteSink getStandardOut() {
		return taskContext.getStandardOut();
	}

	@Override
	public ByteSink getStandardErr() {
		return getStandardErr();
	}

	@Override
	public ByteSource getStandardIn() {
		return taskContext.getStandardIn();
	}

	@Override
	public void acquireStandardIOLock() throws InterruptedException, TaskStandardIOLockIllegalStateException {
		taskContext.acquireStandardIOLock();
	}

	@Override
	public void releaseStandardIOLock() throws TaskStandardIOLockIllegalStateException {
		taskContext.releaseStandardIOLock();
	}

	@Override
	public Path mirror(SakerFile file, DirectoryVisitPredicate synchpredicate)
			throws IOException, NullPointerException, FileMirroringUnavailableException {
		return taskContext.mirror(file, synchpredicate);
	}

	@Override
	public <T> T computeFileContentData(SakerFile file, FileDataComputer<T> computer)
			throws IOException, NullPointerException, RuntimeException {
		return taskContext.computeFileContentData(file, computer);
	}

	@Override
	public SecretInputReader getSecretReader() {
		return taskContext.getSecretReader();
	}

	@Override
	public void println(String line) {
		taskContext.println(line);
	}

	@Override
	public void replayPrintln(String line) throws NullPointerException {
		taskContext.println(line);
	}

	@Override
	public TaskExecutionUtilities getTaskUtilities() {
		return this;
	}

	@Override
	public TaskProgressMonitor getProgressMonitor() {
		return taskContext.getProgressMonitor();
	}

	@Override
	public void invalidate(PathKey pathkey) throws NullPointerException {
		taskContext.invalidate(pathkey);
	}

	@Override
	public ContentDescriptor invalidateGetContentDescriptor(ProviderHolderPathKey pathkey) throws NullPointerException {
		return taskContext.invalidateGetContentDescriptor(pathkey);
	}

	@Override
	public void reportIgnoredException(ExceptionView e) {
		taskContext.reportIgnoredException(e);
	}

	@Override
	public TaskDependencyFuture<?> getTaskDependencyFuture(TaskIdentifier taskid) throws NullPointerException {
		return taskContext.getTaskDependencyFuture(taskid);
	}

	@Override
	public TaskResultDependencyHandle getTaskResultDependencyHandle(TaskIdentifier taskid)
			throws NullPointerException, IllegalArgumentException {
		return taskContext.getTaskResultDependencyHandle(taskid);
	}

	@Override
	public Object getTaskResult(TaskIdentifier taskid) throws TaskExecutionException, NullPointerException {
		return taskContext.getTaskResult(taskid);
	}

	@Override
	public Path mirror(SakerFile file) throws IOException, NullPointerException, FileMirroringUnavailableException {
		return taskContext.mirror(file);
	}

	@Override
	public TaskContext getTaskContext() {
		return this;
	}

	@Override
	public void reportInputFileDependency(Object tag, SakerFile file)
			throws InvalidPathFormatException, NullPointerException {
		utilities.reportInputFileDependency(overrideInputFileTag(tag), file);
	}

	@Override
	public void reportInputFileDependency(Object tag, NavigableMap<SakerPath, ? extends ContentDescriptor> pathcontents)
			throws NullPointerException, IllegalArgumentException {
		utilities.reportInputFileDependency(overrideInputFileTag(tag), pathcontents);
	}

	@Override
	public NavigableMap<SakerPath, SakerFile> collectFiles(FileCollectionStrategy collectionstrategy)
			throws NullPointerException {
		return utilities.collectFiles(collectionstrategy);
	}

	@Override
	public NavigableMap<SakerPath, SakerFile> collectFilesReportInputFileAndAdditionDependency(Object tag,
			FileCollectionStrategy fileadditiondependency) throws NullPointerException {
		return utilities.collectFilesReportInputFileAndAdditionDependency(overrideInputFileTag(tag),
				fileadditiondependency);
	}

	@Override
	public NavigableMap<SakerPath, SakerFile> collectFilesReportInputFileAndAdditionDependency(Object tag,
			Iterable<? extends FileCollectionStrategy> fileadditiondependencies) throws NullPointerException {
		return utilities.collectFilesReportInputFileAndAdditionDependency(overrideInputFileTag(tag),
				fileadditiondependencies);
	}

	@Override
	public NavigableMap<SakerPath, SakerFile> collectFilesReportAdditionDependency(Object tag,
			FileCollectionStrategy fileadditiondependency) throws NullPointerException {
		return utilities.collectFilesReportAdditionDependency(overrideInputFileTag(tag), fileadditiondependency);
	}

	@Override
	public NavigableMap<SakerPath, SakerFile> collectFilesReportAdditionDependency(Object tag,
			Iterable<? extends FileCollectionStrategy> fileadditiondependencies) throws NullPointerException {
		return utilities.collectFilesReportAdditionDependency(overrideInputFileTag(tag), fileadditiondependencies);
	}

	@Override
	public void reportOutputFileDependency(Object tag, SakerFile file)
			throws InvalidPathFormatException, NullPointerException {
		utilities.reportOutputFileDependency(overrideOutputFileTag(tag), file);
	}

	@Override
	public void reportOutputFileDependency(Object tag,
			NavigableMap<SakerPath, ? extends ContentDescriptor> pathcontents)
			throws NullPointerException, IllegalArgumentException {
		utilities.reportOutputFileDependency(overrideOutputFileTag(tag), pathcontents);
	}

	@Override
	public <T> T getReportEnvironmentDependency(EnvironmentProperty<T> property)
			throws NullPointerException, PropertyComputationFailedException {
		return utilities.getReportEnvironmentDependency(property);
	}

	@Override
	public <T> T getReportExecutionDependency(ExecutionProperty<T> property)
			throws NullPointerException, PropertyComputationFailedException {
		return utilities.getReportExecutionDependency(property);
	}

	@Override
	public <R> TaskFuture<R> runTaskFuture(TaskIdentifier taskid, TaskFactory<R> taskfactory,
			TaskExecutionParameters parameters)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		return utilities.runTaskFuture(taskid, taskfactory, parameters);
	}

	@Override
	public SakerFile resolveAtPath(SakerPath path) throws NullPointerException {
		return utilities.resolveAtPath(path);
	}

	@Override
	public SakerFile resolveFileAtPath(SakerPath path) throws NullPointerException {
		return utilities.resolveFileAtPath(path);
	}

	@Override
	public SakerDirectory resolveDirectoryAtPath(SakerPath path) throws NullPointerException {
		return utilities.resolveDirectoryAtPath(path);
	}

	@Override
	public SakerDirectory resolveDirectoryAtPathCreate(SakerPath path) throws NullPointerException {
		return utilities.resolveDirectoryAtPathCreate(path);
	}

	@Override
	public SakerDirectory resolveDirectoryAtPathCreateIfAbsent(SakerPath path) throws NullPointerException {
		return utilities.resolveDirectoryAtPathCreateIfAbsent(path);
	}

	@Override
	public SakerFile resolveAtPath(SakerDirectory basedir, SakerPath path) throws NullPointerException {
		return utilities.resolveAtPath(basedir, path);
	}

	@Override
	public SakerFile resolveFileAtPath(SakerDirectory basedir, SakerPath path) throws NullPointerException {
		return utilities.resolveFileAtPath(basedir, path);
	}

	@Override
	public SakerDirectory resolveDirectoryAtPath(SakerDirectory basedir, SakerPath path) throws NullPointerException {
		return utilities.resolveDirectoryAtPath(basedir, path);
	}

	@Override
	public SakerDirectory resolveDirectoryAtPathCreate(SakerDirectory basedir, SakerPath path)
			throws NullPointerException {
		return utilities.resolveDirectoryAtPathCreate(basedir, path);
	}

	@Override
	public SakerDirectory resolveDirectoryAtPathCreateIfAbsent(SakerDirectory basedir, SakerPath path)
			throws NullPointerException {
		return utilities.resolveDirectoryAtPathCreateIfAbsent(basedir, path);
	}

	@Override
	public SakerFile resolveAtAbsolutePath(SakerPath path) throws NullPointerException, InvalidPathFormatException {
		return utilities.resolveAtAbsolutePath(path);
	}

	@Override
	public SakerFile resolveFileAtAbsolutePath(SakerPath path) throws NullPointerException, InvalidPathFormatException {
		return utilities.resolveFileAtAbsolutePath(path);
	}

	@Override
	public SakerDirectory resolveDirectoryAtAbsolutePath(SakerPath path)
			throws NullPointerException, InvalidPathFormatException {
		return utilities.resolveDirectoryAtAbsolutePath(path);
	}

	@Override
	public SakerDirectory resolveDirectoryAtAbsolutePathCreate(SakerPath path)
			throws NullPointerException, InvalidPathFormatException {
		return utilities.resolveDirectoryAtAbsolutePathCreate(path);
	}

	@Override
	public SakerDirectory resolveDirectoryAtAbsolutePathCreateIfAbsent(SakerPath path)
			throws NullPointerException, InvalidPathFormatException {
		return utilities.resolveDirectoryAtAbsolutePathCreateIfAbsent(path);
	}

	@Override
	public void writeTo(SakerFile file, OutputStream os) throws IOException, NullPointerException {
		utilities.writeTo(file, os);
	}

	@Override
	public void writeTo(SakerFile file, ByteSink os) throws IOException, NullPointerException {
		utilities.writeTo(file, os);
	}

	@Override
	public InputStream openInputStream(SakerFile file) throws IOException, NullPointerException {
		return utilities.openInputStream(file);
	}

	@Override
	public ByteSource openByteSource(SakerFile file) throws IOException, NullPointerException {
		return utilities.openByteSource(file);
	}

	@Override
	public ByteArrayRegion getBytes(SakerFile file) throws IOException, NullPointerException {
		return utilities.getBytes(file);
	}

	@Override
	public String getContent(SakerFile file) throws IOException, NullPointerException {
		return utilities.getContent(file);
	}

	@Override
	public SakerFile createProviderPathFile(String name, ProviderHolderPathKey pathkey)
			throws NullPointerException, IOException {
		return utilities.createProviderPathFile(name, pathkey);
	}

	@Override
	public SakerFile createProviderPathFile(String name, ProviderHolderPathKey pathkey,
			ContentDescriptor currentpathcontentdescriptor)
			throws IOException, NullPointerException, InvalidFileTypeException {
		return utilities.createProviderPathFile(name, pathkey, currentpathcontentdescriptor);
	}

	@Override
	public void invalidate(Iterable<? extends PathKey> pathkeys) throws NullPointerException {
		utilities.invalidate(pathkeys);
	}

	@Override
	public ContentDescriptor synchronize(ProviderHolderPathKey source, ProviderHolderPathKey target, int syncflag)
			throws IOException, NullPointerException {
		return utilities.synchronize(source, target, syncflag);
	}

	@Override
	public void reportInputFileDependency(Object tag, Iterable<? extends SakerFile> files)
			throws InvalidPathFormatException, NullPointerException {
		utilities.reportInputFileDependency(overrideInputFileTag(tag), files);
	}

	@Override
	public void reportOutputFileDependency(Object tag, Iterable<? extends SakerFile> files)
			throws InvalidPathFormatException, NullPointerException {
		utilities.reportOutputFileDependency(overrideOutputFileTag(tag), files);
	}

	@Override
	public <R> TaskFuture<R> runTaskFuture(TaskIdentifier taskid, TaskFactory<R> taskfactory)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		return utilities.runTaskFuture(taskid, taskfactory);
	}

	@Override
	public <R> TaskFuture<R> runTaskFuture(TaskLaunchArguments<R> task)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		return utilities.runTaskFuture(task);
	}

	@Override
	public void runTask(TaskIdentifier taskid, TaskFactory<?> taskfactory, TaskExecutionParameters parameters)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		utilities.runTask(taskid, taskfactory, parameters);
	}

	@Override
	public void runTask(TaskIdentifier taskid, TaskFactory<?> taskfactory)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		utilities.runTask(taskid, taskfactory);
	}

	@Override
	public void runTask(TaskLaunchArguments<?> task)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		utilities.runTask(task);
	}

	@Override
	public <R> R runTaskResult(TaskIdentifier taskid, TaskFactory<R> taskfactory)
			throws NullPointerException, IllegalTaskOperationException {
		return utilities.runTaskResult(taskid, taskfactory);
	}

	@Override
	public <R> R runTaskResult(TaskIdentifier taskid, TaskFactory<R> taskfactory, TaskExecutionParameters parameters)
			throws TaskIdentifierConflictException, NullPointerException, TaskExecutionFailedException,
			IllegalTaskOperationException {
		return utilities.runTaskResult(taskid, taskfactory, parameters);
	}

	@Override
	public <R> R runTaskResult(TaskLaunchArguments<R> task) throws TaskIdentifierConflictException,
			NullPointerException, IllegalTaskOperationException, TaskExecutionFailedException {
		return utilities.runTaskResult(task);
	}

	@Override
	public void startTask(TaskIdentifier taskid, TaskFactory<?> taskfactory)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		utilities.startTask(taskid, taskfactory);
	}

	@Override
	public void startTask(TaskLaunchArguments<?> task)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		utilities.startTask(task);
	}

	@Override
	public <R> TaskFuture<R> startTaskFuture(TaskIdentifier taskid, TaskFactory<R> taskfactory)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		return utilities.startTaskFuture(taskid, taskfactory);
	}

	@Override
	public <R> TaskFuture<R> startTaskFuture(TaskLaunchArguments<R> task)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		return utilities.startTaskFuture(task);
	}

	@Override
	public void startTasks(Map<? extends TaskIdentifier, ? extends TaskFactory<?>> tasks)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		utilities.startTasks(tasks);
	}

	@Override
	public void startTasks(Map<? extends TaskIdentifier, ? extends TaskFactory<?>> tasks,
			TaskExecutionParameters parameters)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		utilities.startTasks(tasks, parameters);
	}

	@Override
	public void startTasks(Iterable<? extends TaskLaunchArguments<?>> tasks)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		utilities.startTasks(tasks);
	}

	@Override
	public List<? extends TaskFuture<?>> startTasksFuture(Iterable<? extends TaskLaunchArguments<?>> tasks)
			throws TaskIdentifierConflictException, NullPointerException, IllegalTaskOperationException {
		return utilities.startTasksFuture(tasks);
	}

	@Override
	public SakerFile resolveAtRelativePath(SakerDirectory basedir, SakerPath path)
			throws NullPointerException, InvalidPathFormatException {
		return utilities.resolveAtRelativePath(basedir, path);
	}

	@Override
	public SakerFile resolveFileAtRelativePath(SakerDirectory basedir, SakerPath path)
			throws NullPointerException, InvalidPathFormatException {
		return utilities.resolveFileAtRelativePath(basedir, path);
	}

	@Override
	public SakerDirectory resolveDirectoryAtRelativePath(SakerDirectory basedir, SakerPath path)
			throws NullPointerException, InvalidPathFormatException {
		return utilities.resolveDirectoryAtRelativePath(basedir, path);
	}

	@Override
	public SakerDirectory resolveDirectoryAtRelativePathCreate(SakerDirectory basedir, SakerPath path)
			throws NullPointerException, InvalidPathFormatException {
		return utilities.resolveDirectoryAtRelativePathCreate(basedir, path);
	}

	@Override
	public SakerDirectory resolveDirectoryAtRelativePathCreateIfAbsent(SakerDirectory basedir, SakerPath path)
			throws NullPointerException, InvalidPathFormatException {
		return utilities.resolveDirectoryAtRelativePathCreateIfAbsent(basedir, path);
	}

	@Override
	public SakerFile resolveAtRelativePathNames(SakerDirectory basedir, Iterable<? extends String> names)
			throws NullPointerException, InvalidPathFormatException {
		return utilities.resolveAtRelativePathNames(basedir, names);
	}

	@Override
	public SakerFile resolveFileAtRelativePathNames(SakerDirectory basedir, Iterable<? extends String> names)
			throws NullPointerException, InvalidPathFormatException {
		return utilities.resolveFileAtRelativePathNames(basedir, names);
	}

	@Override
	public SakerDirectory resolveDirectoryAtRelativePathNames(SakerDirectory basedir, Iterable<? extends String> names)
			throws NullPointerException, InvalidPathFormatException {
		return utilities.resolveDirectoryAtRelativePathNames(basedir, names);
	}

	@Override
	public SakerDirectory resolveDirectoryAtRelativePathNamesCreate(SakerDirectory basedir,
			Iterable<? extends String> names) throws NullPointerException, InvalidPathFormatException {
		return utilities.resolveDirectoryAtRelativePathNamesCreate(basedir, names);
	}

	@Override
	public SakerDirectory resolveDirectoryAtRelativePathNamesCreateIfAbsent(SakerDirectory basedir,
			Iterable<? extends String> names) throws NullPointerException, InvalidPathFormatException {
		return utilities.resolveDirectoryAtRelativePathNamesCreateIfAbsent(basedir, names);
	}

	@Override
	public SakerFile addFile(SakerDirectory directory, SakerFile file) throws NullPointerException {
		return utilities.addFile(directory, file);
	}

	@Override
	public SakerFile addFileIfAbsent(SakerDirectory directory, SakerFile file) throws NullPointerException {
		return utilities.addFileIfAbsent(directory, file);
	}

	@Override
	public SakerFile addFileOverwriteIfNotDirectory(SakerDirectory directory, SakerFile file)
			throws NullPointerException {
		return utilities.addFileOverwriteIfNotDirectory(directory, file);
	}

	@Override
	public NavigableMap<SakerPath, SakerFile> toPathFileMap(Iterable<? extends SakerFile> files)
			throws NullPointerException {
		return utilities.toPathFileMap(files);
	}

	@Override
	public NavigableMap<SakerPath, ContentDescriptor> toPathContentMap(Iterable<? extends SakerFile> files)
			throws NullPointerException {
		return utilities.toPathContentMap(files);
	}

	@Override
	public NavigableMap<String, ContentDescriptor> getChildrenFileContents(SakerDirectory dir)
			throws NullPointerException {
		return utilities.getChildrenFileContents(dir);
	}

	@Override
	public void reportIgnoredException(Throwable e) {
		utilities.reportIgnoredException(e);
	}

	@Override
	public void addSynchronizeInvalidatedProviderPathFileToDirectory(SakerDirectory directory,
			ProviderHolderPathKey pathkey, String filename) throws IOException, NullPointerException {
		utilities.addSynchronizeInvalidatedProviderPathFileToDirectory(directory, pathkey, filename);
	}

	@Override
	public Path mirrorDirectoryAtPath(SakerPath path, DirectoryVisitPredicate synchpredicate) throws IOException {
		return utilities.mirrorDirectoryAtPath(path, synchpredicate);
	}

	@Override
	public Path mirrorFileAtPath(SakerPath path)
			throws IOException, InvalidFileTypeException, FileNotFoundException, NullPointerException {
		return utilities.mirrorFileAtPath(path);
	}

	@Override
	public MirroredFileContents mirrorFileAtPathContents(SakerPath path)
			throws IOException, InvalidFileTypeException, FileNotFoundException, NullPointerException {
		return utilities.mirrorFileAtPathContents(path);
	}

	/**
	 * Presents an opportunity to subclasses to override a given input file tag.
	 * <p>
	 * This method is called for each file tag that is passed to any of the file dependency management methods.
	 * <p>
	 * The default implementation directly returns the argument.
	 * 
	 * @param tag
	 *            The tag. May be <code>null</code>.
	 * @return The new tag to replace the argument with.
	 * @see TaskContext#reportInputFileDependency(Object, SakerPath, ContentDescriptor)
	 * @see TaskContext#reportInputFileAdditionDependency(Object, FileCollectionStrategy)
	 */
	protected Object overrideInputFileTag(Object tag) {
		return tag;
	}

	/**
	 * Presents an opportunity to subclasses to override a given output file tag.
	 * <p>
	 * This method is called for each file tag that is passed to any of the file dependency management methods.
	 * <p>
	 * The default implementation directly returns the argument.
	 * 
	 * @param tag
	 *            The tag. May be <code>null</code>.
	 * @return The new tag to replace the argument with.
	 * @see TaskContext#reportOutputFileDependency(Object, SakerPath, ContentDescriptor)
	 */
	protected Object overrideOutputFileTag(Object tag) {
		return tag;
	}

	/**
	 * Presents an opportunity to subclasses to override a given task output tag.
	 * <p>
	 * This method is called for each task output tag that is passed to any of the task output management methods.
	 * <p>
	 * The default implementation directly returns the argument.
	 * 
	 * @param tag
	 *            The tag. May be <code>null</code>.
	 * @return The new tag to replace the argument with.
	 * @see TaskContext#setTaskOutput(Object, Object)
	 * @see TaskContext#getPreviousTaskOutput(Object, Class)
	 */
	protected Object overrideTaskOutputTag(Object tag) {
		return tag;
	}
}
