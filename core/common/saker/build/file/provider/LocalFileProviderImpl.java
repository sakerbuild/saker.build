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

import java.io.IOException;
import java.nio.file.OpenOption;
import java.util.Objects;

import saker.apiextract.api.ExcludeApi;
import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import testing.saker.build.flag.TestFlag;

@RMIWrap(LocalFileProviderImpl.LocalFilesRMIWrapper.class)
@ExcludeApi
public class LocalFileProviderImpl extends LocalFileProvider implements LocalFileProviderInternalRMIAccess {
	public static class LocalFilesRMIWrapper extends ForwardingSakerFileProvider implements RMIWrapper {
		private FileProviderKey fileProviderKey;

		public LocalFilesRMIWrapper() {
			super(null);
		}

		public LocalFilesRMIWrapper(LocalFileProvider fileProvider) {
			super(fileProvider);
		}

		@Override
		public FileProviderKey getProviderKey() {
			return fileProviderKey;
		}

		@Override
		public SakerFileProvider getWrappedProvider() {
			// Local file providers don't have wrapped providers
			return null;
		}

		@Override
		public SakerPath resolveWrappedPath(SakerPath path)
				throws UnsupportedOperationException, IllegalArgumentException {
			// Local file providers don't have wrapped providers
			throw new UnsupportedOperationException();
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			out.writeRemoteObject(subject);
			out.writeSerializedObject(subject.getProviderKey());
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			SakerFileProvider fp = (SakerFileProvider) in.readObject();
			FileProviderKey fpk = (FileProviderKey) in.readObject();
			if (getProviderKeyStatic().equals(fpk)) {
				this.subject = LocalFileProvider.getInstance();

				if (TestFlag.ENABLED && TestFlag.metric().isForcedRMILocalFileProvider()) {
					this.subject = fp;
				}
			} else {
				this.subject = fp;
			}
			this.fileProviderKey = fpk;
		}

		@Override
		public ByteSource openInput(SakerPath path, OpenOption... openoptions) throws IOException {
			OpenedRMIBufferedFileInput openedinput = ((LocalFileProviderInternalRMIAccess) subject)
					.openRMIBufferedInput(path, openoptions);
			return new RMIBufferedFileInputByteSource(openedinput);
		}

		@Override
		public ByteSink openOutput(SakerPath path, OpenOption... openoptions) throws IOException {
			return new RMIBufferedFileOutputByteSink((LocalFileProviderInternalRMIAccess) subject, path, openoptions);
		}

		@Override
		public long writeTo(SakerPath path, ByteSink out, OpenOption... openoptions) throws IOException {
			RMIWriteToResult writetores = ((LocalFileProviderInternalRMIAccess) subject).writeToRMIBuffered(path, out,
					openoptions);
			long result = writetores.getWrittenByteCount();
			ByteArrayRegion fc = writetores.getFileContents();
			if (fc != null) {
				out.write(fc);
				result += fc.getLength();
			}
			return result;
		}

		@Override
		public ByteSink ensureWriteOpenOutput(SakerPath path, int operationflag, OpenOption... openoptions)
				throws IOException, NullPointerException {
			return new RMIBufferedFileOutputByteSink((LocalFileProviderInternalRMIAccess) subject, path, openoptions) {
				@Override
				protected RMIBufferedFileOutput openOutputWithContents(MultiByteArray contents) throws IOException {
					return fp.openRMIEnsureWriteBufferedOutput(this.path, this.openOptions, operationflag, contents);
				}

				@Override
				protected void touchOutputWithContents(ByteArrayRegion contents) throws IOException {
					fp.touchRMIEnsureWriteOpenOutput(this.path, this.openOptions, operationflag, contents);
				}
			};
		}

		@Override
		public Object getWrappedObject() {
			return subject;
		}

		@Override
		public Object resolveWrapped() {
			if (TestFlag.ENABLED) {
				if (TestFlag.metric().isForcedRMILocalFileProvider()) {
					return this;
				}
			}
			if (subject instanceof LocalFileProvider) {
				return subject;
			}
			//else perform wrapping
			return this;
		}
	}

	static class RMIBufferedFileOutputByteSink implements ByteSink {
		public static final int DEFAULT_BUFFER_SIZE = 16 * 1024;

		protected LocalFileProviderInternalRMIAccess fp;
		protected SakerPath path;
		protected OpenOption[] openOptions;

		private RMIBufferedFileOutput output;
		private UnsyncByteArrayOutputStream buffer;

		public RMIBufferedFileOutputByteSink(LocalFileProviderInternalRMIAccess fp, SakerPath path,
				OpenOption[] openoptions) {
			if (openoptions == null) {
				openoptions = EMPTY_OPEN_OPTIONS;
			} else {
				openoptions = openoptions.clone();
			}
			this.fp = fp;
			this.path = path;
			this.openOptions = openoptions;
		}

		@Override
		public void write(ByteArrayRegion buf) throws IOException, NullPointerException {
			int buflen = buf.getLength();
			if (buflen <= 0) {
				return;
			}
			if (fp == null && output == null) {
				throw new IOException("Not open.");
			}

			int csize = buffer == null ? 0 : buffer.size();
			int totalbytes = csize + buflen;
			if (totalbytes <= DEFAULT_BUFFER_SIZE) {
				if (buffer == null) {
					buffer = new UnsyncByteArrayOutputStream(DEFAULT_BUFFER_SIZE);
				}
				buffer.write(buf);
				return;
			}
			//more than buffer size amount of bytes need to be written
			MultiByteArray contents = new MultiByteArray(getBufferBytesClear(), buf);
			if (fp != null) {
				try {
					output = openOutputWithContents(contents);
				} finally {
					fp = null;
					path = null;
					openOptions = null;
				}
			} else {
				output.write(contents);
			}
		}

		@Override
		public void write(int b) throws IOException {
			// TODO Auto-generated method stub
			ByteSink.super.write(b);
		}

		@Override
		public long readFrom(ByteSource in) throws IOException, NullPointerException {
			Objects.requireNonNull(in, "in");
			if (fp == null && output == null) {
				throw new IOException("Not open.");
			}
			if (buffer == null) {
				buffer = new UnsyncByteArrayOutputStream(DEFAULT_BUFFER_SIZE);
			}

			long c = 0;
			while (true) {
				int toread = DEFAULT_BUFFER_SIZE - buffer.size();
				//if toread == 0, then readFrom is no-op
				int read = buffer.readFrom(in, toread);
				c += read;
				if (read < toread) {
					//no more bytes in the input, break the loop
					break;
				}
				//the input was fully read. write it to the output
				MultiByteArray contents = new MultiByteArray(getBufferBytesClear());
				if (fp != null) {
					try {
						output = openOutputWithContents(contents);
					} finally {
						fp = null;
						path = null;
						openOptions = null;
					}
				} else {
					output.write(contents);
				}
			}
			return c;
		}

		@Override
		public void flush() throws IOException {
			if (fp != null) {
				try {
					ByteArrayRegion bytes = getBufferBytesClear();
					output = openOutputWithContents(bytes == null ? null : new MultiByteArray(bytes));
				} finally {
					fp = null;
					path = null;
					openOptions = null;
				}
			} else {
				if (output == null) {
					throw new IOException("Not open.");
				}
				ByteArrayRegion bytes = getBufferBytesClear();
				output.flush(bytes);
			}
		}

		@Override
		public void close() throws IOException {
			if (fp != null) {
				try {
					touchOutputWithContents(getBufferBytesClear());
				} finally {
					fp = null;
					path = null;
					openOptions = null;
				}
			} else {
				if (output == null) {
					//multiple closing, no-op
					return;
				}
				output.close(getBufferBytesClear());
				output = null;
			}
		}

		protected void touchOutputWithContents(ByteArrayRegion contents) throws IOException {
			fp.touchRMIOpenOutput(path, openOptions, contents);
		}

		protected RMIBufferedFileOutput openOutputWithContents(MultiByteArray contents) throws IOException {
			return fp.openRMIBufferedOutput(path, openOptions, contents);
		}

		private ByteArrayRegion getBufferBytesClear() {
			if (buffer == null) {
				return null;
			}
			ByteArrayRegion bytes = buffer.toByteArrayRegion();
			buffer.reset();
			return bytes;
		}
	}

	//XXX should extend InputStream as well
	static final class RMIBufferedFileInputByteSource implements ByteSource {
		public static final int DEFAULT_BUFFER_SIZE = 16 * 1024;

		private final RMIBufferedFileInput in;
		private ByteArrayRegion buffer;
		private boolean closed;

		RMIBufferedFileInputByteSource(OpenedRMIBufferedFileInput openedinput) {
			this.in = openedinput.getInput();
			RMIBufferedReadResult readresult = openedinput.getReadResult();
			this.buffer = readresult.getBytes();
			this.closed = readresult.isClosed();
		}

		@Override
		public int read(ByteRegion buffer) throws IOException, NullPointerException {
			ByteArrayRegion thisbuf = this.buffer;
			if (thisbuf != null) {
				int availablebytes = thisbuf.getLength();
				if (availablebytes > 0) {
					int buflen = buffer.getLength();
					if (availablebytes > buflen) {
						int thisoffset = thisbuf.getOffset();
						byte[] thisarray = thisbuf.getArray();
						buffer.put(0, ByteArrayRegion.wrap(thisarray, thisoffset, buflen));
						this.buffer = ByteArrayRegion.wrap(thisarray, thisoffset + buflen, availablebytes - buflen);
						return buflen;
					}
					buffer.put(0, thisbuf);
					this.buffer = null;
					return availablebytes;
				}
				//no available bytes
				this.buffer = null;
			}
			if (closed) {
				return -1;
			}
			//no bytes available, need to read
			int buflen = buffer.getLength();
			RMIBufferedReadResult readresult = in.read(Math.max(buflen, DEFAULT_BUFFER_SIZE));
			thisbuf = readresult.getBytes();
			if (thisbuf != null && thisbuf.isEmpty()) {
				thisbuf = null;
			}
			this.buffer = thisbuf;
			this.closed = readresult.isClosed();
			if (thisbuf == null) {
				return -1;
			}
			int availablebytes = thisbuf.getLength();
			if (availablebytes > buflen) {
				int thisoffset = thisbuf.getOffset();
				byte[] thisarray = thisbuf.getArray();
				buffer.put(0, ByteArrayRegion.wrap(thisarray, thisoffset, buflen));
				this.buffer = ByteArrayRegion.wrap(thisarray, thisoffset + buflen, availablebytes - buflen);
				return buflen;
			}
			buffer.put(0, thisbuf);
			this.buffer = null;
			return availablebytes;
		}

		@Override
		public long writeTo(ByteSink out) throws IOException, NullPointerException {
			long c = 0;
			while (true) {
				ByteArrayRegion thisbuf = this.buffer;
				if (thisbuf != null) {
					out.write(thisbuf);
					c += thisbuf.getLength();
					this.buffer = null;
				}
				if (closed) {
					break;
				}
				RMIBufferedReadResult readresult = in.read(DEFAULT_BUFFER_SIZE);
				thisbuf = readresult.getBytes();
				if (thisbuf != null && thisbuf.isEmpty()) {
					thisbuf = null;
				}
				this.buffer = thisbuf;
				this.closed = readresult.isClosed();
			}
			return c;
		}

		@Override
		public long skip(long n) throws IOException {
			// TODO Auto-generated method stub
			return ByteSource.super.skip(n);
		}

		@Override
		public void close() throws IOException {
			if (this.closed) {
				//already closed
				return;
			}
			this.buffer = null;
			this.closed = true;
			this.in.close();
		}
	}
}
