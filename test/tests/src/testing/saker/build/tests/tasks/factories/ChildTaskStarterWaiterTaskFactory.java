package testing.saker.build.tests.tasks.factories;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;

import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class ChildTaskStarterWaiterTaskFactory extends ChildTaskStarterTaskFactory {
	private static final long serialVersionUID = 1L;

	private List<TaskIdentifier> waiterIds = new ArrayList<>();

	public ChildTaskStarterWaiterTaskFactory() {
	}

	public ChildTaskStarterWaiterTaskFactory addWaiter(TaskIdentifier taskid) {
		this.waiterIds.add(taskid);
		return this;
	}

	@Override
	public ChildTaskStarterWaiterTaskFactory add(TaskIdentifier taskid, TaskFactory<?> factory) {
		super.add(taskid, factory);
		return this;
	}

	@Override
	public StructuredTaskResult run(TaskContext context) throws Exception {
		StructuredTaskResult res = super.run(context);
		for (TaskIdentifier taskid : waiterIds) {
			context.getTaskResult(taskid);
		}
		return res;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		SerialUtils.writeExternalCollection(out, waiterIds);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		waiterIds = SerialUtils.readExternalImmutableList(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((waiterIds == null) ? 0 : waiterIds.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		ChildTaskStarterWaiterTaskFactory other = (ChildTaskStarterWaiterTaskFactory) obj;
		if (waiterIds == null) {
			if (other.waiterIds != null)
				return false;
		} else if (!waiterIds.equals(other.waiterIds))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "ChildTaskStarterWaiterTaskFactory [waiterIds=" + waiterIds + ", namedChildTaskValues="
				+ namedChildTaskValues + "]";
	}
}
