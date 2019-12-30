package saker.build.thirdparty.saker.rmi.connection;

class CommandFailedResponse implements RequestResponse {
	private final Throwable exception;

	public CommandFailedResponse(Throwable exception) {
		this.exception = exception;
	}

	public Throwable getException() {
		return exception;
	}
}
