package saker.build.thirdparty.saker.util.io;

class CloseProtectedByteSink extends ByteSinkOutputStream {

	public CloseProtectedByteSink(ByteSink sink) {
		super(sink);
	}

	@Override
	public void close() {
	}
}
