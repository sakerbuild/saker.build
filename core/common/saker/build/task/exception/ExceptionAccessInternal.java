package saker.build.task.exception;

import saker.apiextract.api.ExcludeApi;
import saker.build.task.identifier.TaskIdentifier;

@ExcludeApi
public class ExceptionAccessInternal {
	private ExceptionAccessInternal() {
		throw new UnsupportedOperationException();
	}

	public static TaskExecutionFailedException createTaskExecutionFailedException(Throwable cause,
			TaskIdentifier taskid) {
		return new TaskExecutionFailedException(cause, taskid);
	}

	public static TaskExecutionFailedException createTaskExecutionFailedException(String message, Throwable cause,
			TaskIdentifier taskid) {
		return new TaskExecutionFailedException(message, cause, taskid);
	}

	public static MultiTaskExecutionFailedException createMultiTaskExecutionFailedException(TaskIdentifier taskid) {
		return new MultiTaskExecutionFailedException(taskid);
	}

	public static void addMultiTaskExecutionFailedCause(MultiTaskExecutionFailedException exc, TaskIdentifier taskid,
			TaskException cause) {
		exc.addException(taskid, cause);
	}

	public static TaskExecutionDeadlockedException createTaskExecutionDeadlockedException(TaskIdentifier taskid) {
		return new TaskExecutionDeadlockedException(taskid);
	}

	public static TaskResultWaitingInterruptedException createTaskResultWaitingInterruptedException(
			TaskIdentifier taskid) {
		return new TaskResultWaitingInterruptedException(taskid);
	}

}
