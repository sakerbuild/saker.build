package saker.build.thirdparty.saker.rmi.connection;

import java.lang.reflect.InvocationTargetException;

import saker.build.thirdparty.saker.rmi.exception.RMICallFailedException;
import saker.build.thirdparty.saker.rmi.exception.RMICallForbiddenException;

class DirectForbiddenMethodCallResponse extends MethodCallResponse {
	public static final DirectForbiddenMethodCallResponse INSTANCE = new DirectForbiddenMethodCallResponse();

	public DirectForbiddenMethodCallResponse() {
		super(false, 0, null);
	}

	@Override
	public Object getReturnValue() throws InvocationTargetException, RMICallFailedException {
		throw new RMICallForbiddenException(RMIStream.EXCEPTION_MESSAGE_DIRECT_REQUESTS_FORBIDDEN);
	}
}
