package saker.build.thirdparty.saker.util.io;

class CloseProtectedByteSource extends ByteSourceInputStream {

	public CloseProtectedByteSource(ByteSource source) {
		super(source);
	}

	@Override
	public void close() {
	}

}
