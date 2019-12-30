package saker.build.task;

import saker.build.task.TaskInvocationManager.TaskInvocationContext;

public class ForwardingTaskInvoker implements TaskInvoker {
	protected final TaskInvoker subject;

	public ForwardingTaskInvoker(TaskInvoker subject) {
		this.subject = subject;
	}

	@Override
	public void run(TaskInvocationContext context) throws InterruptedException {
		subject.run(context);
	}
}