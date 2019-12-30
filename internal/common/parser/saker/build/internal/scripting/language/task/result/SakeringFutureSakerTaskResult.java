package saker.build.internal.scripting.language.task.result;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.internal.scripting.language.exc.OperandExecutionException;
import saker.build.internal.scripting.language.exc.SakerScriptEvaluationException;
import saker.build.task.TaskResultDependencyHandle;
import saker.build.task.TaskResultResolver;
import saker.build.task.dependencies.CommonTaskOutputChangeDetector;
import saker.build.task.exception.TaskExecutionFailedException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.StructuredObjectTaskResult;
import saker.build.task.utils.StructuredTaskResult;

public class SakeringFutureSakerTaskResult implements SakerTaskResult, StructuredObjectTaskResult {
	private static final long serialVersionUID = 1L;

	protected TaskIdentifier taskId;

	/**
	 * For {@link Externalizable}.
	 */
	public SakeringFutureSakerTaskResult() {
	}

	public SakeringFutureSakerTaskResult(TaskIdentifier futuretaskid) {
		this.taskId = futuretaskid;
	}

	@Override
	public TaskIdentifier getTaskIdentifier() {
		return taskId;
	}

	@Override
	public Object get(TaskResultResolver results) {
		try {
			Object taskres = results.getTaskResult(taskId);
			if (taskres instanceof SakerTaskResult) {
				return ((SakerTaskResult) taskres).get(results);
			}
			return taskres;
		} catch (TaskExecutionFailedException | SakerScriptEvaluationException e) {
			throw new OperandExecutionException("Failed to retrieve task result.", e, taskId);
		}
	}

	@Override
	public TaskResultDependencyHandle getDependencyHandle(TaskResultResolver results,
			TaskResultDependencyHandle handleforthis) {
		TaskResultDependencyHandle dephandle = results.getTaskResultDependencyHandle(taskId);
		Object handleval = dephandle.get();
		if (handleval instanceof SakerTaskResult) {
			dephandle.setTaskOutputChangeDetector(CommonTaskOutputChangeDetector.isInstanceOf(SakerTaskResult.class));
			TaskResultDependencyHandle resulthandle = ((SakerTaskResult) handleval).getDependencyHandle(results,
					dephandle);
			if (resulthandle == dephandle) {
				return resulthandle.clone();
			}
			return resulthandle;
		}
		dephandle.setTaskOutputChangeDetector(CommonTaskOutputChangeDetector.notInstanceOf(SakerTaskResult.class));
		return dephandle.clone();
	}

	@Override
	public Object toResult(TaskResultResolver results) {
		//converting to SakerTaskResult can be omitted
		return StructuredTaskResult.getActualTaskResult(taskId, results);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(taskId);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		taskId = (TaskIdentifier) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((taskId == null) ? 0 : taskId.hashCode());
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
		SakeringFutureSakerTaskResult other = (SakeringFutureSakerTaskResult) obj;
		if (taskId == null) {
			if (other.taskId != null)
				return false;
		} else if (!taskId.equals(other.taskId))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + taskId + "]";
	}

}
