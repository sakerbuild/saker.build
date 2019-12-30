package saker.build.task.cluster;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;

import saker.build.file.DirectoryVisitPredicate;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.content.ContentDatabase;
import saker.build.file.content.ContentDatabaseImpl;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.PathKey;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.ide.configuration.IDEConfiguration;
import saker.build.runtime.environment.EnvironmentProperty;
import saker.build.runtime.execution.ExecutionProperty;
import saker.build.runtime.execution.FileDataComputer;
import saker.build.runtime.execution.SecretInputReader;
import saker.build.scripting.ScriptPosition;
import saker.build.task.InnerTaskExecutionParameters;
import saker.build.task.InnerTaskResults;
import saker.build.task.InternalTaskContext;
import saker.build.task.TaskContext;
import saker.build.task.TaskDependencyFuture;
import saker.build.task.TaskExecutionParameters;
import saker.build.task.TaskExecutionUtilities;
import saker.build.task.TaskFactory;
import saker.build.task.TaskFileDeltas;
import saker.build.task.TaskFuture;
import saker.build.task.TaskProgressMonitor;
import saker.build.task.TaskResultDependencyHandle;
import saker.build.task.delta.BuildDelta;
import saker.build.task.delta.DeltaType;
import saker.build.task.dependencies.FileCollectionStrategy;
import saker.build.task.dependencies.TaskOutputChangeDetector;
import saker.build.task.exception.IllegalTaskOperationException;
import saker.build.task.exception.TaskStandardIOLockIllegalStateException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.util.exc.ExceptionView;

class ClusterTaskContext implements TaskContext, InternalTaskContext {
	private ClusterExecutionContext executionContext;
	protected TaskContext realTaskContext;
	private ContentDatabase clusterContentDatabase;
	private TaskExecutionUtilities clusterTaskUtilities;

	public ClusterTaskContext(ClusterExecutionContext executionContext, TaskContext realTaskContext,
			ContentDatabaseImpl clusterContentDatabase, TaskExecutionUtilities taskutilities) {
		this.executionContext = executionContext;
		this.realTaskContext = realTaskContext;
		this.clusterContentDatabase = clusterContentDatabase;
		this.clusterTaskUtilities = new ClusterTaskExecutionUtilities(taskutilities, this);
	}

	public ContentDatabase getClusterContentDatabase() {
		return clusterContentDatabase;
	}

	@Override
	public ClusterExecutionContext getExecutionContext() {
		return executionContext;
	}

	@Override
	public <T> T getPreviousTaskOutput(Class<T> type) {
		return realTaskContext.getPreviousTaskOutput(type);
	}

	@Override
	public <T> T getPreviousTaskOutput(Object tag, Class<T> type) {
		return realTaskContext.getPreviousTaskOutput(tag, type);
	}

	@Override
	public void setTaskOutput(Object tag, Object value) {
		realTaskContext.setTaskOutput(tag, value);
	}

	@Override
	public void setMetaData(String metadataid, Object value) {
		realTaskContext.setMetaData(metadataid, value);
	}

	@Override
	public NavigableMap<SakerPath, ? extends SakerFile> getPreviousInputDependencies(Object tag) {
		return realTaskContext.getPreviousInputDependencies(tag);
	}

	@Override
	public NavigableMap<SakerPath, ? extends SakerFile> getPreviousOutputDependencies(Object tag) {
		return realTaskContext.getPreviousOutputDependencies(tag);
	}

	@Override
	public NavigableMap<SakerPath, SakerFile> getPreviousFileAdditionDependency(FileCollectionStrategy dependency) {
		return realTaskContext.getPreviousFileAdditionDependency(dependency);
	}

	@Override
	public Set<? extends BuildDelta> getNonFileDeltas() {
		return realTaskContext.getNonFileDeltas();
	}

	@Override
	public TaskFileDeltas getFileDeltas() {
		return realTaskContext.getFileDeltas();
	}

	@Override
	public TaskFileDeltas getFileDeltas(DeltaType deltatype) {
		return realTaskContext.getFileDeltas(deltatype);
	}

	@Override
	public SakerDirectory getTaskWorkingDirectory() {
		return realTaskContext.getTaskWorkingDirectory();
	}

	@Override
	public SakerDirectory getTaskBuildDirectory() {
		return realTaskContext.getTaskBuildDirectory();
	}

	@Override
	public <R> TaskFuture<R> startTask(TaskIdentifier taskid, TaskFactory<R> taskfactory,
			TaskExecutionParameters parameters) {
		TaskFuture<R> future = realTaskContext.startTask(taskid, taskfactory, parameters);
		return new ClusterTaskFuture<>(taskid, future, this);
	}

	@Override
	public <R> InnerTaskResults<R> startInnerTask(TaskFactory<R> taskfactory, InnerTaskExecutionParameters parameters) {
		return realTaskContext.startInnerTask(taskfactory, parameters);
	}

	@Override
	public TaskFuture<?> getTaskFuture(TaskIdentifier taskid) {
		return new ClusterTaskFuture<>(taskid, realTaskContext, this);
	}

	@Override
	public TaskDependencyFuture<?> getTaskDependencyFuture(TaskIdentifier taskid) {
		return new ClusterTaskDependencyFuture<>(taskid, this);
	}

	@Override
	public TaskResultDependencyHandle getTaskResultDependencyHandle(TaskIdentifier taskid)
			throws NullPointerException, IllegalArgumentException {
		return realTaskContext.getTaskResultDependencyHandle(taskid);
	}

	@Override
	public Object getTaskResult(TaskIdentifier taskid) {
		return realTaskContext.getTaskResult(taskid);
	}

	@Override
	public void abortExecution(Throwable cause) throws IllegalTaskOperationException {
		realTaskContext.abortExecution(cause);
	}

	@Override
	public TaskIdentifier getTaskId() {
		return realTaskContext.getTaskId();
	}

	@Override
	public <T> void reportEnvironmentDependency(EnvironmentProperty<T> systemproperty, T expectedvalue) {
		realTaskContext.reportEnvironmentDependency(systemproperty, expectedvalue);
	}

	@Override
	public <T> void reportExecutionDependency(ExecutionProperty<T> executionproperty, T expectedvalue) {
		realTaskContext.reportExecutionDependency(executionproperty, expectedvalue);
	}

	@Override
	public void reportSelfTaskOutputChangeDetector(TaskOutputChangeDetector changedetector) {
		realTaskContext.reportSelfTaskOutputChangeDetector(changedetector);
	}

	@Override
	public void reportInputFileDependency(Object tag, SakerPath path, ContentDescriptor expectedcontent) {
		realTaskContext.reportInputFileDependency(tag, path, expectedcontent);
	}

	@Override
	public void reportOutputFileDependency(Object tag, SakerPath path, ContentDescriptor expectedcontent) {
		realTaskContext.reportOutputFileDependency(tag, path, expectedcontent);
	}

	@Override
	public void reportInputFileAdditionDependency(Object tag, FileCollectionStrategy dependency) {
		realTaskContext.reportInputFileAdditionDependency(tag, dependency);
	}

	@Override
	public void reportIDEConfiguration(IDEConfiguration configuration) {
		realTaskContext.reportIDEConfiguration(configuration);
	}

	@Override
	public ByteSink getStandardOut() {
		return realTaskContext.getStandardOut();
	}

	@Override
	public void setStandardOutDisplayIdentifier(String displayid) {
		// XXX flush the standard out if it is buffered
		realTaskContext.setStandardOutDisplayIdentifier(displayid);
	}

	@Override
	public ByteSink getStandardErr() {
		return realTaskContext.getStandardErr();
	}

	@Override
	public ByteSource getStandardIn() {
		return realTaskContext.getStandardIn();
	}

	@Override
	public void acquireStandardIOLock() throws InterruptedException, TaskStandardIOLockIllegalStateException {
		realTaskContext.acquireStandardIOLock();
	}

	@Override
	public void releaseStandardIOLock() throws TaskStandardIOLockIllegalStateException {
		realTaskContext.releaseStandardIOLock();
	}

	@Override
	public SecretInputReader getSecretReader() {
		return realTaskContext.getSecretReader();
	}

	@Override
	public void println(String line) {
		realTaskContext.println(line);
	}

	@Override
	public void replayPrintln(String line) throws NullPointerException {
		realTaskContext.replayPrintln(line);
	}

	@Override
	public TaskExecutionUtilities getTaskUtilities() {
		return clusterTaskUtilities;
	}

	@Override
	public TaskProgressMonitor getProgressMonitor() {
		return realTaskContext.getProgressMonitor();
	}

	@Override
	public void invalidate(PathKey pathkey) {
		realTaskContext.invalidate(pathkey);
		invalidateInClusterDatabase(pathkey);
	}

	@Override
	public ContentDescriptor invalidateGetContentDescriptor(ProviderHolderPathKey pathkey) {
		ContentDescriptor result = realTaskContext.invalidateGetContentDescriptor(pathkey);
		ContentDatabase clusterdb = clusterContentDatabase;
		if (clusterdb != null) {
			clusterdb.invalidate(pathkey);
		}
		return result;
	}

	@Override
	public void reportIgnoredException(ExceptionView e) {
		realTaskContext.reportIgnoredException(e);
	}

	@Override
	public SakerPath getTaskWorkingDirectoryPath() {
		return realTaskContext.getTaskWorkingDirectoryPath();
	}

	@Override
	public SakerPath getTaskBuildDirectoryPath() {
		return realTaskContext.getTaskBuildDirectoryPath();
	}

	@Override
	public Path mirror(SakerFile file, DirectoryVisitPredicate synchpredicate) throws IOException {
		return executionContext.mirror(file, synchpredicate);
	}

	@Override
	public <T> T computeFileContentData(SakerFile file, FileDataComputer<T> computer) throws IOException {
		// XXX error handle RMI transfer, and maybe keep local cache and optimize based on RMI object locations
		return realTaskContext.computeFileContentData(file, computer);
	}

	@Override
	public Entry<SakerPath, ScriptPosition> internalGetOriginatingBuildFile() {
		return ((InternalTaskContext) realTaskContext).internalGetOriginatingBuildFile();
	}

	@Override
	public void internalPrintlnVariables(String line) {
		((InternalTaskContext) realTaskContext).internalPrintlnVariables(line);
	}

	@Override
	public void internalPrintlnVerboseVariables(String line) {
		((InternalTaskContext) realTaskContext).internalPrintlnVerboseVariables(line);
	}

	@Override
	public PathSakerFileContents internalGetPathSakerFileContents(SakerPath path) {
		return ((InternalTaskContext) realTaskContext).internalGetPathSakerFileContents(path);
	}

	public void invalidateInClusterDatabase(Iterable<? extends PathKey> pathkeys) {
		Objects.requireNonNull(pathkeys, "path keys");
		ContentDatabase clusterdb = clusterContentDatabase;
		if (clusterdb != null) {
			for (PathKey pk : pathkeys) {
				Objects.requireNonNull(pk, "path key element");
				clusterdb.invalidate(pk);
			}
		}
	}

	public void invalidateInClusterDatabase(PathKey pathkey) {
		Objects.requireNonNull(pathkey, "path key");
		ContentDatabase clusterdb = clusterContentDatabase;
		if (clusterdb != null) {
			clusterdb.invalidate(pathkey);
		}
	}
}