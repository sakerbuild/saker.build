package saker.build.thirdparty.saker.util.io;

import java.io.Reader;
import java.nio.CharBuffer;

class NullReader extends Reader {
	public static final NullReader INSTANCE = new NullReader();

	private NullReader() {
	}

	@Override
	public int read(char[] cbuf, int off, int len) {
		return -1;
	}

	@Override
	public int read(CharBuffer target) {
		return -1;
	}

	@Override
	public int read() {
		return -1;
	}

	@Override
	public int read(char[] cbuf) {
		return -1;
	}

	@Override
	public long skip(long n) {
		return 0;
	}

	@Override
	public boolean ready() {
		return true;
	}

	@Override
	public void close() {
	}

}
