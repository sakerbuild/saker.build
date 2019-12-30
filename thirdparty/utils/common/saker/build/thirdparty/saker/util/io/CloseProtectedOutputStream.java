package saker.build.thirdparty.saker.util.io;

import java.io.OutputStream;

class CloseProtectedOutputStream extends OutputStreamByteSink {

	public CloseProtectedOutputStream(OutputStream out) {
		super(out);
	}

	@Override
	public void close() {
	}
}
