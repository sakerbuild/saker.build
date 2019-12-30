package saker.build.thirdparty.saker.util.io;

import java.io.Writer;

class NullWriter extends Writer {
	public static final NullWriter INSTANCE = new NullWriter();

	private NullWriter() {
	}

	@Override
	public void write(int c) {
	}

	@Override
	public void write(char[] cbuf) {
	}

	@Override
	public void write(String str) {
	}

	@Override
	public void write(String str, int off, int len) {
	}

	@Override
	public Writer append(CharSequence csq) {
		return this;
	}

	@Override
	public Writer append(CharSequence csq, int start, int end) {
		return this;
	}

	@Override
	public Writer append(char c) {
		return this;
	}

	@Override
	public void write(final char[] cbuf, final int off, final int len) {
	}

	@Override
	public void flush() {
	}

	@Override
	public void close() {
	}

}
