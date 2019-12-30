package saker.build.ide.support.persist;

import java.io.Closeable;
import java.io.IOException;

public interface StructuredObjectOutput extends Closeable {
	public void writeField(String name) throws IOException, DuplicateObjectFieldException;
	
	public void writeField(String name, String value) throws IOException, DuplicateObjectFieldException;

	public void writeField(String name, boolean value) throws IOException, DuplicateObjectFieldException;

	public void writeField(String name, char value) throws IOException, DuplicateObjectFieldException;

	public void writeField(String name, byte value) throws IOException, DuplicateObjectFieldException;

	public void writeField(String name, short value) throws IOException, DuplicateObjectFieldException;

	public void writeField(String name, int value) throws IOException, DuplicateObjectFieldException;

	public void writeField(String name, long value) throws IOException, DuplicateObjectFieldException;

	public void writeField(String name, float value) throws IOException, DuplicateObjectFieldException;

	public void writeField(String name, double value) throws IOException, DuplicateObjectFieldException;

	public StructuredObjectOutput writeObject(String name) throws IOException, DuplicateObjectFieldException;

	public StructuredArrayObjectOutput writeArray(String name) throws IOException, DuplicateObjectFieldException;
}
