package saker.build.thirdparty.saker.util.io;

import java.io.IOException;
import java.io.OutputStream;

class SynchronizedOutputStream extends OutputStream implements ByteSink {
	protected final OutputStream out;

	public SynchronizedOutputStream(OutputStream out) {
		this.out = out;
	}

	@Override
	public synchronized void write(int b) throws IOException {
		out.write(b);
	}

	@Override
	public synchronized void write(byte[] b) throws IOException {
		out.write(b);
	}

	@Override
	public synchronized void write(byte[] b, int off, int len) throws IOException {
		out.write(b, off, len);
	}

	@Override
	public synchronized void flush() throws IOException {
		out.flush();
	}

	@Override
	public synchronized void close() throws IOException {
		out.close();
	}

	@Override
	public synchronized void write(ByteArrayRegion buf) throws IOException {
		buf.writeTo(out);
	}

	@Override
	public synchronized long readFrom(ByteSource in) throws IOException {
		return ByteSink.super.readFrom(in);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + out + "]";
	}
}
