package saker.build.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.task.exception.InnerTaskExecutionException;

public class CompletedInnerTaskOptionalResult<R> implements InnerTaskResultHolder<R>, Externalizable {
	private static final long serialVersionUID = 1L;

	private R result;

	/**
	 * For {@link Externalizable}.
	 */
	public CompletedInnerTaskOptionalResult() {
	}

	public CompletedInnerTaskOptionalResult(R result) {
		this.result = result;
	}

	@Override
	public R getResult() throws InnerTaskExecutionException {
		return result;
	}

	@Override
	public Throwable getExceptionIfAny() {
		return null;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(result);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		result = (R) in.readObject();
	}
}