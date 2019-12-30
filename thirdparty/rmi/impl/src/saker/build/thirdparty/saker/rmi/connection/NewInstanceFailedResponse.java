package saker.build.thirdparty.saker.rmi.connection;

import java.lang.reflect.InvocationTargetException;

import saker.build.thirdparty.saker.rmi.exception.RMICallFailedException;

class NewInstanceFailedResponse extends NewInstanceResponse {
	private final Throwable exception;

	public NewInstanceFailedResponse(boolean invokerThreadInterrupted, int deliveredInterruptRequestCount,
			Throwable exception) {
		super(invokerThreadInterrupted, deliveredInterruptRequestCount, RMIVariables.NO_OBJECT_ID);
		this.exception = exception;
	}

	@Override
	public int getRemoteId() throws InvocationTargetException, RMICallFailedException {
		if (exception instanceof InvocationTargetException) {
			exception.fillInStackTrace();
			throw (InvocationTargetException) exception;
		}
		throw new RMICallFailedException(exception);
	}
}
