package saker.build.thirdparty.saker.rmi.connection;

import java.lang.reflect.InvocationTargetException;

import saker.build.thirdparty.saker.rmi.exception.RMICallFailedException;
import saker.build.thirdparty.saker.rmi.exception.RMICallForbiddenException;

class DirectForbiddenNewInstanceResponse extends NewInstanceResponse {
	public static final DirectForbiddenNewInstanceResponse INSTANCE = new DirectForbiddenNewInstanceResponse();

	public DirectForbiddenNewInstanceResponse() {
		super(false, 0, RMIVariables.NO_OBJECT_ID);
	}

	@Override
	public int getRemoteId() throws InvocationTargetException, RMICallFailedException {
		throw new RMICallForbiddenException(RMIStream.EXCEPTION_MESSAGE_DIRECT_REQUESTS_FORBIDDEN);
	}
}
