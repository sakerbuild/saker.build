package saker.build.thirdparty.saker.util.io;

import java.io.IOException;
import java.io.OutputStream;

import javax.crypto.Mac;

class MacByteSink extends OutputStream implements ByteSink {
	private Mac mac;

	public MacByteSink(Mac mac) {
		this.mac = mac;
	}

	public Mac getMac() {
		return mac;
	}

	@Override
	public void write(ByteArrayRegion buf) throws IOException {
		mac.update(buf.getArray(), buf.getOffset(), buf.getLength());
	}

	@Override
	public void write(int b) throws IOException {
		mac.update((byte) b);
	}

	@Override
	public void write(byte[] b) throws IOException {
		mac.update(b);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		mac.update(b, off, len);
	}

}
