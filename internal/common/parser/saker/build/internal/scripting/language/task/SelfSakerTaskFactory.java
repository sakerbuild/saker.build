package saker.build.internal.scripting.language.task;

import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;

public abstract class SelfSakerTaskFactory extends SakerTaskFactory implements Task<SakerTaskResult> {
	private static final long serialVersionUID = 1L;

	@Override
	public final Task<? extends SakerTaskResult> createTask(ExecutionContext executioncontext) {
		return this;
	}
}
