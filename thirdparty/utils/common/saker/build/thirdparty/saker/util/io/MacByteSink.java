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

import javax.crypto.Mac;

class MacByteSink extends OutputStream implements ByteSink {
	private Mac mac;

	public MacByteSink(Mac mac) {
		this.mac = mac;
	}

	public Mac getMac() {
		return mac;
	}

	@Override
	public void write(ByteArrayRegion buf) throws IOException {
		mac.update(buf.getArray(), buf.getOffset(), buf.getLength());
	}

	@Override
	public void write(int b) throws IOException {
		mac.update((byte) b);
	}

	@Override
	public void write(byte[] b) throws IOException {
		mac.update(b);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		mac.update(b, off, len);
	}

}
