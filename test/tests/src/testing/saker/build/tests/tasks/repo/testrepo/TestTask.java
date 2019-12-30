package testing.saker.build.tests.tasks.repo.testrepo;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;

public class TestTask implements TaskFactory<String>, Task<String>, Externalizable {
	private static final long serialVersionUID = 1L;

	@Override
	public Task<? extends String> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj != null && this.getClass() == obj.getClass();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	}

	@Override
	public String run(TaskContext taskcontext) throws Exception {
		System.out.println("TestTask.createTask(...).new Task() {...}.run()");
		return "hello";
	}
}