package saker.build.internal.scripting.language.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.task.identifier.TaskIdentifier;

public class UnsakerTaskFactoryTaskIdentifier implements TaskIdentifier, Externalizable {
	private static final long serialVersionUID = 1L;

	protected UnsakerFutureTaskFactory taskFactory;

	public UnsakerTaskFactoryTaskIdentifier() {
	}

	public UnsakerTaskFactoryTaskIdentifier(UnsakerFutureTaskFactory taskFactory) {
		this.taskFactory = taskFactory;
	}

	public UnsakerFutureTaskFactory getTaskFactory() {
		return taskFactory;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(taskFactory);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		taskFactory = (UnsakerFutureTaskFactory) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((taskFactory == null) ? 0 : taskFactory.hashCode());
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
		UnsakerTaskFactoryTaskIdentifier other = (UnsakerTaskFactoryTaskIdentifier) obj;
		if (taskFactory == null) {
			if (other.taskFactory != null)
				return false;
		} else if (!taskFactory.equals(other.taskFactory))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "(unsaker:" + taskFactory + ")";
	}

}
