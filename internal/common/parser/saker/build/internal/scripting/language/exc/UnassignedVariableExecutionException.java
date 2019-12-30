package saker.build.internal.scripting.language.exc;

import saker.build.task.identifier.TaskIdentifier;

public class UnassignedVariableExecutionException extends SakerScriptEvaluationException {
	private static final long serialVersionUID = 1L;

	public UnassignedVariableExecutionException(String message, Throwable cause, TaskIdentifier taskIdentifier) {
		super(message, cause, taskIdentifier);
	}

	public UnassignedVariableExecutionException(String message, TaskIdentifier taskIdentifier) {
		super(message, taskIdentifier);
	}

}
