package saker.build.thirdparty.saker.util.io;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.CharBuffer;

class NullInputStream extends InputStream implements ObjectInputByteSource, Readable {
	public static final NullInputStream INSTANCE = new NullInputStream();

	private NullInputStream() {
	}

	@Override
	public int read() {
		return -1;
	}

	@Override
	public int read(byte[] b) {
		return -1;
	}

	@Override
	public int read(byte[] b, int off, int len) {
		return -1;
	}

	@Override
	public long skip(long n) {
		return 0;
	}

	@Override
	public int available() {
		return 0;
	}

	@Override
	public void close() {
	}

	@Override
	public void mark(int readlimit) {
	}

	@Override
	public void reset() {
	}

	@Override
	public boolean markSupported() {
		return false;
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName();
	}

	@Override
	public void readFully(byte[] b) throws IOException {
		throw new EOFException();
	}

	@Override
	public void readFully(byte[] b, int off, int len) throws IOException {
		throw new EOFException();
	}

	@Override
	public int skipBytes(int n) throws IOException {
		throw new EOFException();
	}

	@Override
	public boolean readBoolean() throws IOException {
		throw new EOFException();
	}

	@Override
	public byte readByte() throws IOException {
		throw new EOFException();
	}

	@Override
	public int readUnsignedByte() throws IOException {
		throw new EOFException();
	}

	@Override
	public short readShort() throws IOException {
		throw new EOFException();
	}

	@Override
	public int readUnsignedShort() throws IOException {
		throw new EOFException();
	}

	@Override
	public char readChar() throws IOException {
		throw new EOFException();
	}

	@Override
	public int readInt() throws IOException {
		throw new EOFException();
	}

	@Override
	public long readLong() throws IOException {
		throw new EOFException();
	}

	@Override
	public float readFloat() throws IOException {
		throw new EOFException();
	}

	@Override
	public double readDouble() throws IOException {
		throw new EOFException();
	}

	@Override
	public String readLine() throws IOException {
		throw new EOFException();
	}

	@Override
	public String readUTF() throws IOException {
		throw new EOFException();
	}

	@Override
	public int read(ByteRegion buffer) throws IOException {
		return -1;
	}

	@Override
	public int read(CharBuffer cb) throws IOException {
		return -1;
	}

	@Override
	public Object readObject() throws ClassNotFoundException, IOException {
		throw new EOFException();
	}

}
