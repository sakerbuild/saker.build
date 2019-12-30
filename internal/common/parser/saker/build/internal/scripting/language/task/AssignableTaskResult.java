package saker.build.internal.scripting.language.task;

import saker.build.task.TaskContext;
import saker.build.task.identifier.TaskIdentifier;

public interface AssignableTaskResult {
	public void assign(TaskContext taskcontext, SakerScriptTaskIdentifier currenttaskid, TaskIdentifier sakertaskid);
}
