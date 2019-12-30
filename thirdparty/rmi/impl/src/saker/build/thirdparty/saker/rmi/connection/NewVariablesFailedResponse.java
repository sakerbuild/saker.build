package saker.build.thirdparty.saker.rmi.connection;

import saker.build.thirdparty.saker.rmi.exception.RMICallFailedException;
import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;

class NewVariablesFailedResponse extends NewVariablesResponse {
	private RMIRuntimeException exception;

	public NewVariablesFailedResponse(RMIRuntimeException exception) {
		super(RMIVariables.NO_OBJECT_ID);
		this.exception = exception;
	}

	@Override
	public int getRemoteIdentifier() throws RMIRuntimeException {
		throw new RMICallFailedException(exception);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + exception + "]";
	}

}
