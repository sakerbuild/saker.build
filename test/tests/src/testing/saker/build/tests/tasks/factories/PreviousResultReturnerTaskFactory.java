package testing.saker.build.tests.tasks.factories;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import testing.saker.build.tests.StringTaskIdentifier;

public class PreviousResultReturnerTaskFactory
		implements TaskFactory<PreviousResultReturnerTaskFactory.StringReference>, Externalizable {
	private static final long serialVersionUID = 1L;

	public static class StringReference implements Externalizable {
		private static final long serialVersionUID = 1L;

		public String value;

		public StringReference() {
		}

		public StringReference(String value) {
			this.value = value;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeUTF(value);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			value = in.readUTF();
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
			StringReference other = (StringReference) obj;
			if (value == null) {
				if (other.value != null)
					return false;
			} else if (!value.equals(other.value))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "StringReference [" + (value != null ? "value=" + value : "") + "]";
		}

	}

	private String modifyToken;
	private String value;

	public PreviousResultReturnerTaskFactory() {
	}

	public PreviousResultReturnerTaskFactory(String modifyToken, String value) {
		this.modifyToken = modifyToken;
		this.value = value;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeUTF(value);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		value = in.readUTF();
	}

	@Override
	public Task<StringReference> createTask(ExecutionContext context) {
		return new Task<PreviousResultReturnerTaskFactory.StringReference>() {

			@Override
			public StringReference run(TaskContext context) {
				StringReference prev = context.getPreviousTaskOutput(StringReference.class);
				if (prev != null) {
					return prev;
				}
				context.getTaskUtilities().startTaskFuture(new StringTaskIdentifier("child"),
						new StringTaskFactory(value));
				return new StringReference(value);
			}
		};
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((modifyToken == null) ? 0 : modifyToken.hashCode());
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
		PreviousResultReturnerTaskFactory other = (PreviousResultReturnerTaskFactory) obj;
		if (modifyToken == null) {
			if (other.modifyToken != null)
				return false;
		} else if (!modifyToken.equals(other.modifyToken))
			return false;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "PreviousResultReturnerTaskFactory [" + (modifyToken != null ? "modifyToken=" + modifyToken + ", " : "")
				+ (value != null ? "value=" + value : "") + "]";
	}

}
