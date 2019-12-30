package saker.build.thirdparty.saker.util.io;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;

class MessageDigestByteSink extends OutputStream implements ByteSink {
	private MessageDigest digest;

	public MessageDigestByteSink(MessageDigest digest) {
		this.digest = digest;
	}

	public MessageDigest getDigest() {
		return digest;
	}

	@Override
	public void write(ByteArrayRegion buf) throws IOException {
		digest.update(buf.getArray(), buf.getOffset(), buf.getLength());
	}

	@Override
	public void write(int b) throws IOException {
		digest.update((byte) b);
	}

	@Override
	public void write(byte[] b) throws IOException {
		digest.update(b);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		digest.update(b, off, len);
	}

}
