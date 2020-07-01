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
package saker.build.file.provider;

import java.io.Closeable;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.OpenOption;

import saker.apiextract.api.ExcludeApi;
import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIExceptionRethrow;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWriter;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.RemoteIOException;
import saker.build.thirdparty.saker.util.rmi.writer.EnumArrayRMIObjectWriteHandler;

@ExcludeApi
public interface LocalFileProviderInternalRMIAccess {
	@RMIExceptionRethrow(RemoteIOException.class)
	public OpenedRMIBufferedFileInput openRMIBufferedInput(SakerPath path,
			@RMIWriter(EnumArrayRMIObjectWriteHandler.class) OpenOption... openoptions) throws IOException;

	@RMIExceptionRethrow(RemoteIOException.class)
	public RMIBufferedFileOutput openRMIBufferedOutput(SakerPath path,
			@RMIWriter(EnumArrayRMIObjectWriteHandler.class) OpenOption[] openoptions, MultiByteArray writecontents)
			throws IOException;

	@RMIExceptionRethrow(RemoteIOException.class)
	public RMIBufferedFileOutput openRMIEnsureWriteBufferedOutput(SakerPath path,
			@RMIWriter(EnumArrayRMIObjectWriteHandler.class) OpenOption[] openoptions, int operationflags,
			MultiByteArray writecontents) throws IOException;

	@RMIExceptionRethrow(RemoteIOException.class)
	public void touchRMIOpenOutput(SakerPath path,
			@RMIWriter(EnumArrayRMIObjectWriteHandler.class) OpenOption[] openoptions, ByteArrayRegion bytes)
			throws IOException;

	@RMIExceptionRethrow(RemoteIOException.class)
	public void touchRMIEnsureWriteOpenOutput(SakerPath path,
			@RMIWriter(EnumArrayRMIObjectWriteHandler.class) OpenOption[] openoptions, int operationflags,
			ByteArrayRegion bytes) throws IOException;

	@RMIExceptionRethrow(RemoteIOException.class)
	public RMIWriteToResult writeToRMIBuffered(SakerPath path, ByteSink out, OpenOption[] openoptions)
			throws IOException;

	public interface RMIBufferedFileInput extends Closeable {
		@RMIExceptionRethrow(RemoteIOException.class)
		public RMIBufferedReadResult read(int counthint) throws IOException;
	}

	public interface RMIBufferedFileOutput {
		@RMIExceptionRethrow(RemoteIOException.class)
		public void write(MultiByteArray bytes) throws IOException;

		@RMIExceptionRethrow(RemoteIOException.class)
		public void flush(ByteArrayRegion bytes) throws IOException;

		@RMIExceptionRethrow(RemoteIOException.class)
		public void close(ByteArrayRegion bytes) throws IOException;
	}

	public static class MultiByteArray implements Externalizable {
		private static final long serialVersionUID = 1L;

		private ByteArrayRegion[] arrays;

		/**
		 * For {@link Externalizable}.
		 */
		public MultiByteArray() {
		}

		public MultiByteArray(ByteArrayRegion... arrays) {
			this.arrays = arrays;
		}

		public ByteArrayRegion[] getArrays() {
			return arrays;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			int len = 0;
			for (int i = 0; i < arrays.length; i++) {
				ByteArrayRegion bar = arrays[i];
				if (bar == null) {
					continue;
				}
				len += bar.getLength();
			}
			out.writeInt(len);
			for (int i = 0; i < arrays.length; i++) {
				ByteArrayRegion bar = arrays[i];
				if (bar == null) {
					continue;
				}
				bar.writeTo(out);
			}
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			int len = in.readInt();
			byte[] array = new byte[len];
			in.readFully(array);
		}
	}

	public static class RMIWriteToResult implements Externalizable {
		private static final long serialVersionUID = 1L;

		private ByteArrayRegion fileContents;
		private long writtenByteCount;

		/**
		 * For {@link Externalizable}.
		 */
		public RMIWriteToResult() {
		}

		public RMIWriteToResult(ByteArrayRegion fileContents) {
			this.fileContents = fileContents;
		}

		public RMIWriteToResult(long writtenBytes) {
			this.writtenByteCount = writtenBytes;
		}

		public RMIWriteToResult(ByteArrayRegion fileContents, long writtenByteCount) {
			this.fileContents = fileContents;
			this.writtenByteCount = writtenByteCount;
		}

		public ByteArrayRegion getFileContents() {
			return fileContents;
		}

		public long getWrittenByteCount() {
			return writtenByteCount;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(fileContents);
			out.writeLong(writtenByteCount);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			fileContents = (ByteArrayRegion) in.readObject();
			writtenByteCount = in.readLong();
		}
	}

	public static class RMIBufferedReadResult implements Externalizable {
		private static final long serialVersionUID = 1L;

		public static final RMIBufferedReadResult INSTANCE_NO_DATA_CLOSED = new RMIBufferedReadResult(null, true);

		protected ByteArrayRegion bytes;
		protected boolean closed;

		/**
		 * For {@link Externalizable}.
		 */
		public RMIBufferedReadResult() {
		}

		public RMIBufferedReadResult(ByteArrayRegion initialBytes, boolean closed) {
			this.bytes = initialBytes;
			this.closed = closed;
		}

		public ByteArrayRegion getBytes() {
			return bytes;
		}

		public boolean isClosed() {
			return closed;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(bytes);
			out.writeBoolean(closed);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			bytes = (ByteArrayRegion) in.readObject();
			closed = in.readBoolean();
		}
	}

	@RMIWrap(OpenedRMIBufferedFileInputRMIWrapper.class)
	public static class OpenedRMIBufferedFileInput {
		protected RMIBufferedFileInput input;
		protected RMIBufferedReadResult readResult;

		public OpenedRMIBufferedFileInput(RMIBufferedFileInput input, RMIBufferedReadResult readResult) {
			this.input = input;
			this.readResult = readResult;
		}

		public OpenedRMIBufferedFileInput(RMIBufferedFileInput input, ByteArrayRegion initialBytes, boolean closed) {
			this(input, new RMIBufferedReadResult(initialBytes, closed));
		}

		public RMIBufferedFileInput getInput() {
			return input;
		}

		public RMIBufferedReadResult getReadResult() {
			return readResult;
		}
	}

	public static class OpenedRMIBufferedFileInputRMIWrapper implements RMIWrapper {
		private OpenedRMIBufferedFileInput input;

		public OpenedRMIBufferedFileInputRMIWrapper() {
		}

		public OpenedRMIBufferedFileInputRMIWrapper(OpenedRMIBufferedFileInput input) {
			this.input = input;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			out.writeBoolean(input.readResult.closed);
			if (!input.readResult.closed) {
				out.writeRemoteObject(input.input);
			}
			out.writeObject(input.readResult.bytes);
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			boolean closed = in.readBoolean();
			RMIBufferedFileInput input;
			if (!closed) {
				input = (RMIBufferedFileInput) in.readObject();
			} else {
				input = null;
			}
			ByteArrayRegion initialbytes = (ByteArrayRegion) in.readObject();
			this.input = new OpenedRMIBufferedFileInput(input, initialbytes, closed);
		}

		@Override
		public Object resolveWrapped() {
			return input;
		}

		@Override
		public Object getWrappedObject() {
			throw new UnsupportedOperationException();
		}

	}
}