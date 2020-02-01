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
package saker.build.thirdparty.saker.rmi.connection;

import java.io.IOException;

import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.util.io.DataInputUnsyncByteArrayInputStream;

final class RMIObjectInputImpl implements RMIObjectInput {
	private final RMIVariables variables;
	private final RMIStream stream;
	private final DataInputUnsyncByteArrayInputStream in;

	public RMIObjectInputImpl(RMIVariables variables, RMIStream stream, DataInputUnsyncByteArrayInputStream in) {
		this.variables = variables;
		this.stream = stream;
		this.in = in;
	}

	@Override
	public void readFully(byte[] b) throws IOException {
		this.in.readFully(b);
	}

	@Override
	public void readFully(byte[] b, int off, int len) throws IOException {
		this.in.readFully(b, off, len);
	}

	@Override
	public int skipBytes(int n) throws IOException {
		return this.in.skipBytes(n);
	}

	@Override
	public boolean readBoolean() throws IOException {
		return this.in.readBoolean();
	}

	@Override
	public byte readByte() throws IOException {
		return this.in.readByte();
	}

	@Override
	public int readUnsignedByte() throws IOException {
		return this.in.readUnsignedByte();
	}

	@Override
	public short readShort() throws IOException {
		return this.in.readShort();
	}

	@Override
	public int readUnsignedShort() throws IOException {
		return this.in.readUnsignedShort();
	}

	@Override
	public char readChar() throws IOException {
		return this.in.readChar();
	}

	@Override
	public int readInt() throws IOException {
		return this.in.readInt();
	}

	@Override
	public long readLong() throws IOException {
		return this.in.readLong();
	}

	@Override
	public float readFloat() throws IOException {
		return this.in.readFloat();
	}

	@Override
	public double readDouble() throws IOException {
		return this.in.readDouble();
	}

	@SuppressWarnings("deprecation")
	@Override
	public String readLine() throws IOException {
		return this.in.readLine();
	}

	@Override
	public String readUTF() throws IOException {
		return this.in.readUTF();
	}

	@Override
	public Object readObject() throws ClassNotFoundException, IOException {
		return stream.readObject(variables, this.in);
	}

	@Override
	public int read() throws IOException {
		return this.in.read();
	}

	@Override
	public int read(byte[] b) throws IOException {
		return this.in.read(b);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		return this.in.read(b, off, len);
	}

	@Override
	public long skip(long n) throws IOException {
		return this.in.skip(n);
	}

	@Override
	public int available() throws IOException {
		return this.in.available();
	}

	@Override
	public void close() throws IOException {
	}

}