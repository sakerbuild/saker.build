package saker.build.thirdparty.saker.rmi.connection;

import java.io.IOException;

interface RedispatchResponse extends RequestResponse {
	public void executeRedispatchAction() throws IOException;
}