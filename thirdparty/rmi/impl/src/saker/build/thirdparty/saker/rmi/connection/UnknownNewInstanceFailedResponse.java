package saker.build.thirdparty.saker.rmi.connection;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;

import saker.build.thirdparty.saker.rmi.exception.RMICallFailedException;

class UnknownNewInstanceFailedResponse extends UnknownNewInstanceResponse {
	private final Throwable exception;

	public UnknownNewInstanceFailedResponse(boolean invokerThreadInterrupted, int deliveredInterruptRequestCount,
			Throwable exception) {
		super(invokerThreadInterrupted, deliveredInterruptRequestCount, RMIVariables.NO_OBJECT_ID, null);
		this.exception = exception;
	}

	@Override
	public int getRemoteId() throws InvocationTargetException, RMICallFailedException {
		throw throwException();
	}

	@Override
	public Set<Class<?>> getInterfaces() throws InvocationTargetException, RMICallFailedException {
		throw throwException();
	}

	private RuntimeException throwException() throws InvocationTargetException, RMICallFailedException {
		if (exception instanceof InvocationTargetException) {
			exception.fillInStackTrace();
			throw (InvocationTargetException) exception;
		}
		throw new RMICallFailedException(exception);
	}
}