package saker.build.internal.scripting.language.task.result;

import java.util.NavigableMap;

import saker.build.task.TaskResultDependencyHandle;
import saker.build.task.TaskResultResolver;
import saker.build.task.utils.SimpleStructuredMapTaskResult;
import saker.build.task.utils.StructuredTaskResult;

public class SakerMapTaskResult extends SimpleStructuredMapTaskResult implements SakerTaskResult {
	private static final long serialVersionUID = 1L;

	public SakerMapTaskResult() {
		super();
	}

	public SakerMapTaskResult(NavigableMap<String, ? extends StructuredTaskResult> itemTaskIds) {
		super(itemTaskIds);
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
