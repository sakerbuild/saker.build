package saker.build.internal.scripting.language.exc;

import saker.build.task.identifier.TaskIdentifier;

public class OperandExecutionException extends SakerScriptEvaluationException {
	private static final long serialVersionUID = 1L;

	public OperandExecutionException(String message, TaskIdentifier taskIdentifier) {
		super(message, taskIdentifier);
	}

	protected OperandExecutionException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace, TaskIdentifier taskIdentifier) {
		super(message, cause, enableSuppression, writableStackTrace, taskIdentifier);
	}

	public OperandExecutionException(String message, Throwable cause, TaskIdentifier taskIdentifier) {
		super(message, cause, taskIdentifier);
	}

	public OperandExecutionException(TaskIdentifier taskIdentifier) {
		super(taskIdentifier);
	}

	public OperandExecutionException(Throwable cause, TaskIdentifier taskIdentifier) {
		super(cause, taskIdentifier);
	}

}
