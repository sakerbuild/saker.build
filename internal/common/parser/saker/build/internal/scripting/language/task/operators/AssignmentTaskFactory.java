package saker.build.internal.scripting.language.task.operators;

import java.util.Map;

import saker.build.internal.scripting.language.exc.OperandExecutionException;
import saker.build.internal.scripting.language.exc.SakerScriptEvaluationException;
import saker.build.internal.scripting.language.task.AssignableTaskResult;
import saker.build.internal.scripting.language.task.SakerAssignTaskIdentifier;
import saker.build.internal.scripting.language.task.SakerLiteralTaskFactory;
import saker.build.internal.scripting.language.task.SakerScriptTaskIdentifier;
import saker.build.internal.scripting.language.task.SakerTaskFactory;
import saker.build.internal.scripting.language.task.SakerTaskResultLiteralTaskFactory;
import saker.build.internal.scripting.language.task.result.SakerTaskObjectSakerTaskResult;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.exception.TaskExecutionFailedException;
import saker.build.task.identifier.TaskIdentifier;

public class AssignmentTaskFactory extends BinaryOperatorTaskFactory {
	private static final long serialVersionUID = 1L;

	public AssignmentTaskFactory() {
	}

	public AssignmentTaskFactory(SakerTaskFactory left, SakerTaskFactory right) {
		super(left, right);
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) {
		SakerScriptTaskIdentifier thistaskid = (SakerScriptTaskIdentifier) taskcontext.getTaskId();
		TaskIdentifier ltaskid = left.createSubTaskIdentifier(thistaskid);
		TaskIdentifier rtaskid = right.createSubTaskIdentifier(thistaskid);
		taskcontext.getTaskUtilities().startTaskFuture(rtaskid, right);

		SakerTaskResult leftres = taskcontext.getTaskUtilities().runTaskResult(ltaskid, left);
		if (!(leftres instanceof AssignableTaskResult)) {
			throw new OperandExecutionException("Left operand is not assignable.", ltaskid);
		}
		AssignableTaskResult assignableresult = (AssignableTaskResult) leftres;
		try {
			assignableresult.assign(taskcontext, thistaskid, rtaskid);
		} catch (TaskExecutionFailedException | SakerScriptEvaluationException e) {
			taskcontext.abortExecution(
					new OperandExecutionException("Failed to evaluate assignment operator", e, rtaskid));
			return null;
		}
		return new SakerTaskObjectSakerTaskResult(rtaskid);
	}

	@Override
	public SakerLiteralTaskFactory tryConstantize() {
		return null;
	}

	public static TaskIdentifier startAssignmentTask(TaskContext taskcontext, TaskIdentifier roottaskid,
			String variablename, TaskIdentifier sakertaskidvalue) {
		TaskIdentifier assigntaskid = createAssignTaskIdentifier(roottaskid, variablename);
		taskcontext.getTaskUtilities().startTaskFuture(assigntaskid, new SakerTaskResultLiteralTaskFactory(sakertaskidvalue));
		return assigntaskid;
	}

	public static TaskIdentifier createAssignTaskIdentifier(TaskIdentifier roottaskid, String variablename) {
		return new SakerAssignTaskIdentifier(roottaskid, variablename);
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new AssignmentTaskFactory(cloneHelper(taskfactoryreplacements, left),
				cloneHelper(taskfactoryreplacements, right));
	}

	@Override
	public String toString() {
		return "(assign:(" + left + " = " + right + "))";
	}

}
