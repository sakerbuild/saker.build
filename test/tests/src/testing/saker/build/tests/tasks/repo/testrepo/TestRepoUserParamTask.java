package testing.saker.build.tests.tasks.repo.testrepo;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;

public class TestRepoUserParamTask implements TaskFactory<String>, Task<String>, Externalizable {
	private static final long serialVersionUID = 1L;

	private String value;

	/**
	 * For {@link Externalizable}.
	 */
	public TestRepoUserParamTask() {
	}

	public TestRepoUserParamTask(String value) {
		this.value = value;
	}

	@Override
	public String run(TaskContext taskcontext) throws Exception {
		return value;
	}

	@Override
	public Task<? extends String> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(value);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		value = (String) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((value == null) ? 0 : value.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TestRepoUserParamTask other = (TestRepoUserParamTask) obj;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "TestRepoUserParamTask[value=" + value + "]";
	}

}
