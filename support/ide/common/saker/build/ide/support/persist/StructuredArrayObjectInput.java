package saker.build.ide.support.persist;

import java.io.Closeable;
import java.io.IOException;
import java.util.NoSuchElementException;

public interface StructuredArrayObjectInput extends Closeable {
	public int length();

	public StructuredDataType getNextDataType();

	public String readString() throws IOException, NoSuchElementException, DataFormatException;

	public Boolean readBoolean() throws IOException, NoSuchElementException, DataFormatException;

	public Character readChar() throws IOException, NoSuchElementException, DataFormatException;

	public Byte readByte() throws IOException, NoSuchElementException, DataFormatException;

	public Short readShort() throws IOException, NoSuchElementException, DataFormatException;

	public Integer readInt() throws IOException, NoSuchElementException, DataFormatException;

	public Long readLong() throws IOException, NoSuchElementException, DataFormatException;

	public Float readFloat() throws IOException, NoSuchElementException, DataFormatException;

	public Double readDouble() throws IOException, NoSuchElementException, DataFormatException;

	public StructuredObjectInput readObject() throws IOException, NoSuchElementException, DataFormatException;

	public StructuredArrayObjectInput readArray() throws IOException, NoSuchElementException, DataFormatException;
}
