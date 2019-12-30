package saker.build.thirdparty.saker.util.io;

import java.io.IOException;
import java.io.ObjectOutput;
import java.io.OutputStream;

final class NullOutputStream extends OutputStream implements ObjectOutput, ByteSink, Appendable {
	public static final NullOutputStream INSTANCE = new NullOutputStream();

	private NullOutputStream() {
	}

	@Override
	public void write(int b) {
	}

	@Override
	public void write(byte[] b) {
	}

	@Override
	public void write(byte[] b, int off, int len) {
	}

	@Override
	public void flush() throws IOException {
	}

	@Override
	public void close() throws IOException {
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName();
	}

	@Override
	public void writeBoolean(boolean v) throws IOException {
	}

	@Override
	public void writeByte(int v) throws IOException {
	}

	@Override
	public void writeShort(int v) throws IOException {
	}

	@Override
	public void writeChar(int v) throws IOException {
	}

	@Override
	public void writeInt(int v) throws IOException {
	}

	@Override
	public void writeLong(long v) throws IOException {
	}

	@Override
	public void writeFloat(float v) throws IOException {
	}

	@Override
	public void writeDouble(double v) throws IOException {
	}

	@Override
	public void writeBytes(String s) throws IOException {
	}

	@Override
	public void writeChars(String s) throws IOException {
	}

	@Override
	public void writeUTF(String s) throws IOException {
	}

	@Override
	public void write(ByteArrayRegion buf) throws IOException {
	}

	@Override
	public Appendable append(CharSequence csq) throws IOException {
		return this;
	}

	@Override
	public Appendable append(CharSequence csq, int start, int end) throws IOException {
		return this;
	}

	@Override
	public Appendable append(char c) throws IOException {
		return this;
	}

	@Override
	public void writeObject(Object obj) throws IOException {
	}
}