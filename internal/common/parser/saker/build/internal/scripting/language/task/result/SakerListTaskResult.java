package saker.build.internal.scripting.language.task.result;

import java.util.List;

import saker.build.task.TaskResultDependencyHandle;
import saker.build.task.TaskResultResolver;
import saker.build.task.utils.SimpleStructuredListTaskResult;
import saker.build.task.utils.StructuredTaskResult;

public class SakerListTaskResult extends SimpleStructuredListTaskResult implements SakerTaskResult {
	private static final long serialVersionUID = 1L;

	public SakerListTaskResult() {
		super();
	}

	public SakerListTaskResult(List<? extends StructuredTaskResult> elementTaskIds) {
		super(elementTaskIds);
	}

	@Override
	public Object get(TaskResultResolver results) {
		return this;
	}

	@Override
	public TaskResultDependencyHandle getDependencyHandle(TaskResultResolver results,
			TaskResultDependencyHandle handleforthis) {
		return handleforthis;
	}
}
