/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
