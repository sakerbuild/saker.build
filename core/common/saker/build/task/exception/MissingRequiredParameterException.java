package saker.build.task.exception;

import saker.build.task.ParameterizableTask;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.annot.SakerInput;

/**
 * Exception signaling that a required parameter was not provided by the caller.
 * 
 * @see ParameterizableTask
 * @see SakerInput#required()
 */
public class MissingRequiredParameterException extends TaskParameterException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see TaskParameterException#TaskParameterException(String, TaskIdentifier)
	 */
	public MissingRequiredParameterException(String message, TaskIdentifier taskIdentifier) {
		super(message, taskIdentifier);
	}

}
