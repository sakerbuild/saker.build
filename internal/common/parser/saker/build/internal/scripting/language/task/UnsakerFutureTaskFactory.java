package saker.build.internal.scripting.language.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.internal.scripting.language.exc.OperandExecutionException;
import saker.build.internal.scripting.language.exc.SakerScriptEvaluationException;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.exception.TaskExecutionFailedException;
import saker.build.task.identifier.TaskIdentifier;

public class UnsakerFutureTaskFactory implements TaskFactory<Object>, Task<Object>, Externalizable {
	private static final long serialVersionUID = 1L;
	//TODO create subclasses that have a better toString explanation about what the task does

	protected TaskIdentifier sakerTaskId;

	public UnsakerFutureTaskFactory() {
	}

	public UnsakerFutureTaskFactory(TaskIdentifier sakertaskid) {
		this.sakerTaskId = sakertaskid;
	}

	@Override
	public Object run(TaskContext taskcontext) throws Exception {
		try {
			return ((SakerTaskResult) taskcontext.getTaskResult(sakerTaskId)).get(taskcontext);
		} catch (TaskExecutionFailedException | SakerScriptEvaluationException e) {
			taskcontext
					.abortExecution(new OperandExecutionException("Failed to retrieve task result.", e, sakerTaskId));
			return null;
		}
	}

	@Override
	public Task<? extends Object> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(sakerTaskId);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		sakerTaskId = (TaskIdentifier) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((sakerTaskId == null) ? 0 : sakerTaskId.hashCode());
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
		UnsakerFutureTaskFactory other = (UnsakerFutureTaskFactory) obj;
		if (sakerTaskId == null) {
			if (other.sakerTaskId != null)
				return false;
		} else if (!sakerTaskId.equals(other.sakerTaskId))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "(unsakertask:" + sakerTaskId + ")";
	}

}
