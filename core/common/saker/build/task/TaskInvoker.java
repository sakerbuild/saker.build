package saker.build.task;

import saker.build.task.TaskInvocationManager.TaskInvocationContext;

public interface TaskInvoker {
	public void run(TaskInvocationContext context) throws InterruptedException;
}