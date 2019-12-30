package saker.build.thirdparty.saker.util.io;

import java.io.IOException;
import java.io.OutputStream;

class SynchronizedByteSink extends OutputStream implements ByteSink {
	protected final ByteSink out;

	public SynchronizedByteSink(ByteSink out) {
		this.out = out;
	}

	@Override
	public synchronized void write(int b) throws IOException {
		out.write(b);
	}

	@Override
	public synchronized void write(byte[] b) throws IOException {
		out.write(ByteArrayRegion.wrap(b));
	}

	@Override
	public synchronized void write(byte[] b, int off, int len) throws IOException {
		out.write(ByteArrayRegion.wrap(b, off, len));
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
		out.write(buf);
	}

	@Override
	public synchronized long readFrom(ByteSource in) throws IOException {
		return out.readFrom(in);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + out + "]";
	}

}
