package saker.build.ide.support.persist;

import java.io.Closeable;
import java.io.IOException;

public interface StructuredArrayObjectOutput extends Closeable {
	public void write(String value) throws IOException;

	public void write(boolean value) throws IOException;

	public void write(char value) throws IOException;

	public void write(byte value) throws IOException;

	public void write(short value) throws IOException;

	public void write(int value) throws IOException;

	public void write(long value) throws IOException;

	public void write(float value) throws IOException;

	public void write(double value) throws IOException;

	public StructuredObjectOutput writeObject() throws IOException;

	public StructuredArrayObjectOutput writeArray() throws IOException;
}
