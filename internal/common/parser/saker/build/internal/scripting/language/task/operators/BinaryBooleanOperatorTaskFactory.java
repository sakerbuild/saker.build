package saker.build.internal.scripting.language.task.operators;

import java.io.Externalizable;

import saker.build.internal.scripting.language.exc.OperandExecutionException;
import saker.build.internal.scripting.language.exc.SakerScriptEvaluationException;
import saker.build.internal.scripting.language.task.SakerLiteralTaskFactory;
import saker.build.internal.scripting.language.task.SakerScriptTaskIdentifier;
import saker.build.internal.scripting.language.task.SakerScriptTaskUtils;
import saker.build.internal.scripting.language.task.SakerTaskFactory;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.internal.scripting.language.task.result.SimpleSakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.TaskFuture;
import saker.build.task.exception.TaskExecutionFailedException;
import saker.build.task.identifier.TaskIdentifier;

public abstract class BinaryBooleanOperatorTaskFactory extends BinaryOperatorTaskFactory {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public BinaryBooleanOperatorTaskFactory() {
	}

	public BinaryBooleanOperatorTaskFactory(SakerTaskFactory left, SakerTaskFactory right) {
		super(left, right);
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) throws Exception {
		SakerScriptTaskIdentifier thistaskid = (SakerScriptTaskIdentifier) taskcontext.getTaskId();
		TaskIdentifier lefttaskid = left.createSubTaskIdentifier(thistaskid);
		TaskIdentifier righttaskid = right.createSubTaskIdentifier(thistaskid);

		TaskFuture<SakerTaskResult> leftfut = taskcontext.getTaskUtilities().startTaskFuture(lefttaskid, left);
		TaskFuture<SakerTaskResult> rightfut = taskcontext.getTaskUtilities().startTaskFuture(righttaskid, right);

		boolean leftres = evaluateOperand(taskcontext, lefttaskid, leftfut, "left");
		boolean rightres = evaluateOperand(taskcontext, righttaskid, rightfut, "right");
		return new SimpleSakerTaskResult<>(apply(leftres, rightres));
	}

	private static Boolean evaluateOperand(TaskContext taskcontext, TaskIdentifier taskid,
			TaskFuture<SakerTaskResult> future, String operandname) {
		Object result;
		try {
			result = future.get().toResult(taskcontext);
		} catch (TaskExecutionFailedException | SakerScriptEvaluationException e) {
			throw new OperandExecutionException(operandname + " operand failed to evaluate.", e, taskid);
		}
		return SakerScriptTaskUtils.getConditionValue(result);
//		if (result == null) {
//			throw new OperandExecutionException(operandname + " operand evaluated to null.", taskid);
//		}
//		if (!(result instanceof Boolean)) {
//			throw new OperandExecutionException(operandname + " operand is not a boolean. (" + result.getClass().getName() + ")", taskid);
//		}
//		return (Boolean) result;
	}

	@Override
	public SakerLiteralTaskFactory tryConstantize() {
		SakerLiteralTaskFactory lc = left.tryConstantize();
		if (lc == null) {
			return null;
		}
		Object lv = lc.getValue();
		if (!(lv instanceof Boolean)) {
			return null;
		}
		SakerLiteralTaskFactory rc = right.tryConstantize();
		if (rc == null) {
			return null;
		}
		Object rv = rc.getValue();
		if (!(rv instanceof Boolean)) {
			return null;
		}
		return new SakerLiteralTaskFactory(apply((Boolean) lv, (Boolean) rv));
	}

	protected abstract boolean apply(boolean left, boolean right);

}
