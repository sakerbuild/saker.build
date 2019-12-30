package saker.build.internal.scripting.language.task.result;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.task.TaskResultDependencyHandle;
import saker.build.task.TaskResultResolver;
import saker.build.task.utils.StructuredTaskResult;

public final class SimpleSakerTaskResult<R> implements SakerTaskResult {

	private static final long serialVersionUID = 1L;

	public static final SakerTaskResult NULL_VALUE = new SimpleSakerTaskResult<>(null);

	protected R value;

	public SimpleSakerTaskResult() {
	}

	public SimpleSakerTaskResult(R value) {
		this.value = value;
	}

	public R getValue() {
		return value;
	}

	@Override
	public Object get(TaskResultResolver results) {
		return value;
	}

	@Override
	public Object toResult(TaskResultResolver results) {
		if (value instanceof StructuredTaskResult) {
			return ((StructuredTaskResult) value).toResult(results);
		}
		return value;
	}

	@Override
	public TaskResultDependencyHandle toResultDependencyHandle(TaskResultResolver results) throws NullPointerException {
		if (value instanceof StructuredTaskResult) {
			return ((StructuredTaskResult) value).toResultDependencyHandle(results);
		}
		return SakerTaskResult.super.toResultDependencyHandle(results);
	}

	@Override
	public TaskResultDependencyHandle getDependencyHandle(TaskResultResolver results,
			TaskResultDependencyHandle handleforthis) {
		if (value instanceof StructuredTaskResult) {
			return ((StructuredTaskResult) value).toResultDependencyHandle(results);
		}
		return handleforthis;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(value);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		value = (R) in.readObject();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + value + "]";
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
		SimpleSakerTaskResult<?> other = (SimpleSakerTaskResult<?>) obj;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}

}
