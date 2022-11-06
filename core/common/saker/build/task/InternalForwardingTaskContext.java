package saker.build.task;

import java.io.IOException;
import java.util.Map.Entry;

import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.scripting.ScriptPosition;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.trace.InternalBuildTrace.InternalTaskBuildTrace;

public class InternalForwardingTaskContext extends ForwardingTaskContext implements InternalTaskContext {

	public InternalForwardingTaskContext(TaskContext taskContext, TaskExecutionUtilities utilities) {
		super(taskContext, utilities);
	}

	public InternalForwardingTaskContext(TaskContext taskContext) throws NullPointerException {
		super(taskContext);
	}

	@Override
	public TaskContext internalGetTaskContextIdentity() {
		return ((InternalTaskContext) taskContext).internalGetTaskContextIdentity();
	}

	@Override
	public Entry<SakerPath, ScriptPosition> internalGetOriginatingBuildFile() {
		return ((InternalTaskContext) taskContext).internalGetOriginatingBuildFile();
	}

	@Override
	public void internalPrintlnVariables(String line) {
		((InternalTaskContext) taskContext).internalPrintlnVariables(line);
	}

	@Override
	public void internalPrintlnVerboseVariables(String line) {
		((InternalTaskContext) taskContext).internalPrintlnVerboseVariables(line);
	}

	@Override
	public PathSakerFileContents internalGetPathSakerFileContents(SakerPath path) {
		return ((InternalTaskContext) taskContext).internalGetPathSakerFileContents(path);
	}

	@Override
	public InternalTaskBuildTrace internalGetBuildTrace() {
		return ((InternalTaskContext) taskContext).internalGetBuildTrace();
	}

	@Override
	public SakerFile internalCreateProviderPathFile(String name, ProviderHolderPathKey pathkey, boolean directory)
			throws NullPointerException, IOException {
		return ((InternalTaskContext) taskContext).internalCreateProviderPathFile(name, pathkey, directory);
	}

	@Override
	public void internalAddSynchronizeInvalidatedProviderPathFileToDirectory(SakerDirectory directory,
			ProviderHolderPathKey pathkey, String filename, boolean isdirectory) throws IOException {
		((InternalTaskContext) taskContext).internalAddSynchronizeInvalidatedProviderPathFileToDirectory(directory,
				pathkey, filename, isdirectory);
	}

	@Override
	public <T> TaskFuture<T> internalStartTaskOnTaskThread(TaskIdentifier taskid, TaskFactory<T> taskfactory,
			TaskExecutionParameters parameters) {
		return ((InternalTaskContext) taskContext).internalStartTaskOnTaskThread(taskid, taskfactory, parameters);
	}

	@Override
	public <T> T internalRunTaskResultOnTaskThread(TaskIdentifier taskid, TaskFactory<T> taskfactory,
			TaskExecutionParameters parameters) {
		return ((InternalTaskContext) taskContext).internalRunTaskResultOnTaskThread(taskid, taskfactory, parameters);
	}

	@Override
	public <T> TaskFuture<T> internalRunTaskFutureOnTaskThread(TaskIdentifier taskid, TaskFactory<T> taskfactory,
			TaskExecutionParameters parameters) {
		return ((InternalTaskContext) taskContext).internalRunTaskFutureOnTaskThread(taskid, taskfactory, parameters);
	}
}
