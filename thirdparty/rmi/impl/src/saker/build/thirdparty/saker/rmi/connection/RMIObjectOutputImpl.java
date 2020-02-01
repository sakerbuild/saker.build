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
import java.util.Objects;

import saker.build.thirdparty.saker.rmi.exception.RMIObjectTransferFailureException;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.DataOutputUnsyncByteArrayOutputStream;

final class RMIObjectOutputImpl implements RMIObjectOutput {
	private static final Object NO_WRAPWRITTEN_OBJECT = new Object();
	private final RMIVariables variables;
	private final RMIStream stream;
	private final DataOutputUnsyncByteArrayOutputStream out;
	private final Object currentlyWrapWrittenObject;

	public RMIObjectOutputImpl(RMIVariables variables, RMIStream stream, DataOutputUnsyncByteArrayOutputStream out) {
		this.variables = variables;
		this.stream = stream;
		this.out = out;
		this.currentlyWrapWrittenObject = NO_WRAPWRITTEN_OBJECT;
	}

	public RMIObjectOutputImpl(RMIVariables variables, RMIStream stream, DataOutputUnsyncByteArrayOutputStream out,
			Object currentlyWrapWrittenObject) {
		this.variables = variables;
		this.stream = stream;
		this.out = out;
		this.currentlyWrapWrittenObject = currentlyWrapWrittenObject;
	}

	@Override
	public void writeUTF(String s) throws IOException {
		out.writeUTF(s);
	}

	@Override
	public void writeShort(int v) throws IOException {
		out.writeShort(v);
	}

	@Override
	public void writeLong(long v) throws IOException {
		out.writeLong(v);
	}

	@Override
	public void writeInt(int v) throws IOException {
		out.writeInt(v);
	}

	@Override
	public void writeFloat(float v) throws IOException {
		out.writeFloat(v);
	}

	@Override
	public void writeDouble(double v) throws IOException {
		out.writeDouble(v);
	}

	@Override
	public void writeChars(String s) throws IOException {
		out.writeChars(s);
	}

	@Override
	public void writeChar(int v) throws IOException {
		out.writeChar(v);
	}

	@Override
	public void writeBytes(String s) throws IOException {
		out.writeBytes(s);
	}

	@Override
	public void writeByte(int v) throws IOException {
		out.writeByte(v);
	}

	@Override
	public void writeBoolean(boolean v) throws IOException {
		out.writeBoolean(v);

	}

	@Override
	public void writeObject(Object obj) throws IOException {
		stream.writeObjectFromStream(variables, obj, ObjectUtils.classOf(obj), out, currentlyWrapWrittenObject);
	}

	@Override
	public void writeRemoteObject(Object obj) throws IOException {
		stream.writeRemoteObjectFromStream(variables, obj, out, currentlyWrapWrittenObject);
	}

	@Override
	public void writeSerializedObject(Object obj) throws IOException {
		stream.writeSerializedObjectFromStream(variables, obj, out, currentlyWrapWrittenObject);
	}

	@Override
	public void writeWrappedObject(Object obj, Class<? extends RMIWrapper> wrapperclass) throws IOException {
		Objects.requireNonNull(wrapperclass, "wrapper class");
		stream.writeWrappedObjectFromStream(variables, obj, wrapperclass, out, currentlyWrapWrittenObject);
	}

	@Override
	public void writeEnumObject(Object obj) throws IOException, RMIObjectTransferFailureException {
		stream.writeEnumObjectFromStream(variables, obj, out, currentlyWrapWrittenObject);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		out.write(b, off, len);
	}

	@Override
	public void write(byte[] b) throws IOException {
		out.write(b);
	}

	@Override
	public void write(int b) throws IOException {
		out.write(b);

	}

	@Override
	public void flush() throws IOException {
	}

	@Override
	public void close() throws IOException {
	}
}