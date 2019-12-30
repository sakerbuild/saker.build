package saker.build.thirdparty.saker.util.io;

import java.io.IOException;
import java.io.OutputStream;
import java.security.Signature;
import java.security.SignatureException;

class SignatureByteSink extends OutputStream implements ByteSink {
	private Signature signature;

	public SignatureByteSink(Signature signature) {
		this.signature = signature;
	}

	public Signature getSignature() {
		return signature;
	}

	@Override
	public void write(ByteArrayRegion buf) throws IOException {
		try {
			signature.update(buf.getArray(), buf.getOffset(), buf.getLength());
		} catch (SignatureException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void write(int b) throws IOException {
		try {
			signature.update((byte) b);
		} catch (SignatureException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void write(byte[] b) throws IOException {
		try {
			signature.update(b);
		} catch (SignatureException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		try {
			signature.update(b, off, len);
		} catch (SignatureException e) {
			throw new IOException(e);
		}
	}

}
