package saker.build.task;

public class RetrievedTaskResultDependencyHandle implements TaskResultDependencyHandle, Cloneable {
	private final Object result;

	public RetrievedTaskResultDependencyHandle(Object result) {
		this.result = result;
	}

	@Override
	public Object get() throws RuntimeException {
		return result;
	}

	@Override
	public TaskResultDependencyHandle clone() {
		try {
			return (TaskResultDependencyHandle) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError(e);
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (result != null ? "result=" + result : "") + "]";
	}

}
