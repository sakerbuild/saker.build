package saker.build.thirdparty.saker.rmi.connection;

import java.lang.reflect.InvocationTargetException;

import saker.build.thirdparty.saker.rmi.exception.RMICallFailedException;

class MethodCallFailedResponse extends MethodCallResponse {
	private final Throwable exception;

	public MethodCallFailedResponse(boolean invokerThreadInterrupted, int deliveredInterruptRequestCount,
			Throwable exception) {
		super(invokerThreadInterrupted, deliveredInterruptRequestCount, null);
		this.exception = exception;
	}

	@Override
	public Object getReturnValue() throws InvocationTargetException, RMICallFailedException {
		if (exception instanceof InvocationTargetException) {
			exception.fillInStackTrace();
			throw (InvocationTargetException) exception;
		}
		throw new RMICallFailedException(exception);
	}

}
