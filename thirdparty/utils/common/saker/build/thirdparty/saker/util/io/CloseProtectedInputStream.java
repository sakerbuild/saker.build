package saker.build.thirdparty.saker.util.io;

import java.io.InputStream;

class CloseProtectedInputStream extends InputStreamByteSource {

	public CloseProtectedInputStream(InputStream in) {
		super(in);
	}

	@Override
	public void close() {
	}

}
