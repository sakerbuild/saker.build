package saker.build.thirdparty.saker.util.io;

import java.io.IOException;
import java.io.InputStream;

class SynchronizedByteSource extends InputStream implements ByteSource {
	protected final ByteSource in;

	public SynchronizedByteSource(ByteSource in) {
		this.in = in;
	}

	@Override
	public synchronized int read(ByteRegion buffer) throws IOException {
		return in.read(buffer);
	}

	@Override
	public synchronized int read() throws IOException {
		return in.read();
	}

	@Override
	public synchronized ByteArrayRegion read(int count) throws IOException {
		return in.read(count);
	}

	@Override
	public synchronized long writeTo(ByteSink out) throws IOException {
		return in.writeTo(out);
	}

	@Override
	public synchronized int read(byte[] b) throws IOException {
		return in.read(ByteArrayRegion.wrap(b));
	}

	@Override
	public synchronized int read(byte[] b, int off, int len) throws IOException {
		return in.read(ByteArrayRegion.wrap(b, off, len));
	}

	@Override
	public synchronized long skip(long n) throws IOException {
		return in.skip(n);
	}

	@Override
	public synchronized int available() throws IOException {
		return super.available();
	}

	@Override
	public synchronized void close() throws IOException {
		in.close();
	}

	@Override
	public synchronized void mark(int readlimit) {
		super.mark(readlimit);
	}

	@Override
	public synchronized void reset() throws IOException {
		super.reset();
	}

	@Override
	public boolean markSupported() {
		return super.markSupported();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + in + "]";
	}
}
