package saker.build.launching;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

class ClientAuthSSLServerSocketFactory extends ServerSocketFactory {
	private SSLServerSocketFactory factory;

	public ClientAuthSSLServerSocketFactory(SSLServerSocketFactory factory) {
		this.factory = factory;
	}

	@Override
	public ServerSocket createServerSocket(int port) throws IOException {
		return setupServerSocket(factory.createServerSocket(port));
	}

	@Override
	public ServerSocket createServerSocket(int port, int backlog) throws IOException {
		return setupServerSocket(factory.createServerSocket(port, backlog));
	}

	@Override
	public ServerSocket createServerSocket(int port, int backlog, InetAddress ifAddress) throws IOException {
		return setupServerSocket(factory.createServerSocket(port, backlog, ifAddress));
	}

	@Override
	public ServerSocket createServerSocket() throws IOException {
		return setupServerSocket(factory.createServerSocket());
	}

	private static ServerSocket setupServerSocket(ServerSocket socket) {
		SSLServerSocket ssls = (SSLServerSocket) socket;
		ssls.setNeedClientAuth(true);
		return socket;
	}

}