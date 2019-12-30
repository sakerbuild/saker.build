package saker.build.thirdparty.saker.rmi.connection;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;

import saker.build.thirdparty.saker.rmi.exception.RMICallFailedException;
import saker.build.thirdparty.saker.rmi.exception.RMICallForbiddenException;

class DirectForbiddenUnknownNewInstanceResponse extends UnknownNewInstanceResponse {
	public static final DirectForbiddenUnknownNewInstanceResponse INSTANCE = new DirectForbiddenUnknownNewInstanceResponse();

	public DirectForbiddenUnknownNewInstanceResponse() {
		super(false, 0, RMIVariables.NO_OBJECT_ID, Collections.emptySet());
	}

	@Override
	public int getRemoteId() throws InvocationTargetException, RMICallFailedException {
		throw new RMICallForbiddenException(RMIStream.EXCEPTION_MESSAGE_DIRECT_REQUESTS_FORBIDDEN);
	}
}
