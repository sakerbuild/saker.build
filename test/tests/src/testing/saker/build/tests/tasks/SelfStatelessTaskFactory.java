package testing.saker.build.tests.tasks;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;

public abstract class SelfStatelessTaskFactory<R> extends StatelessTaskFactory<R> implements Task<R> {
	private static final long serialVersionUID = 1L;

	@Override
	public final Task<? extends R> createTask(ExecutionContext executioncontext) {
		return this;
	}

}
