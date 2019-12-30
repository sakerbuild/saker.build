package saker.build.thirdparty.saker.rmi.connection;

class GetContextVariableResponse implements RequestResponse {
	private final Object variable;

	public GetContextVariableResponse(Object variable) {
		this.variable = variable;
	}

	public Object getVariable() {
		return variable;
	}
}
