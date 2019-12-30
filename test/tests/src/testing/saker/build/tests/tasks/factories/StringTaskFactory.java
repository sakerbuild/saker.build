package testing.saker.build.tests.tasks.factories;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.NavigableSet;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class StringTaskFactory implements TaskFactory<String>, Task<String>, Externalizable {
	private static final long serialVersionUID = 1L;

	private String result;

	private transient NavigableSet<String> capabilities = Collections.emptyNavigableSet();

	public StringTaskFactory() {
	}

	public StringTaskFactory(String result) {
		this.result = result;
	}

	public StringTaskFactory setCapabilities(NavigableSet<String> capabilities) {
		this.capabilities = capabilities;
		return this;
	}

	public String getResult() {
		return result;
	}

	@Override
	public NavigableSet<String> getCapabilities() {
		return capabilities;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(result);
		SerialUtils.writeExternalCollection(out, capabilities);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		result = (String) in.readObject();
		capabilities = SerialUtils.readExternalSortedImmutableNavigableSet(in);
	}

	@Override
	public Task<String> createTask(ExecutionContext context) {
		return this;
	}

	@Override
	public String run(TaskContext context) throws Exception {
		return result;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((this.result == null) ? 0 : this.result.hashCode());
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
		StringTaskFactory other = (StringTaskFactory) obj;
		if (result == null) {
			if (other.result != null)
				return false;
		} else if (!result.equals(other.result))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (result != null ? "result=" + result : "") + "]";
	}

}