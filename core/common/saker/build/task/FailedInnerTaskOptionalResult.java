package saker.build.task;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.task.exception.InnerTaskExecutionException;

public class FailedInnerTaskOptionalResult<R> implements InnerTaskResultHolder<R>, Externalizable {
	private static final long serialVersionUID = 1L;

	private String message;
	private Throwable cause;

	/**
	 * For {@link Externalizable}.
	 */
	public FailedInnerTaskOptionalResult() {
	}

	public FailedInnerTaskOptionalResult(Throwable cause) {
		this.cause = cause;
	}

	public FailedInnerTaskOptionalResult(String message, Throwable cause) {
		this.message = message;
		this.cause = cause;
	}

	@Override
	public R getResult() throws InnerTaskExecutionException {
		throw new InnerTaskExecutionException(cause);
	}

	@Override
	public Throwable getExceptionIfAny() {
		return cause;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(message);
		out.writeObject(cause);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		message = (String) in.readObject();
		cause = (Throwable) in.readObject();
	}

}