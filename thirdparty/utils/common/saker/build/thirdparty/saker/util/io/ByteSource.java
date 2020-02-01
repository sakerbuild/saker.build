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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.util.Objects;

import saker.build.thirdparty.saker.rmi.annot.invoke.RMIExceptionRethrow;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMIRedirect;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.util.rmi.wrap.RMIByteRegionWrapper;

/**
 * Interface for handling byte stream input.
 * <p>
 * Byte source is similar to {@link InputStream}, but is defined as an interface for allowing stream operations over
 * RMI. RMI solutions can create proxy objects for network transferred objects only for interfaces, therefore it was
 * necessary to declare such an interface that is usable for this use-case.
 * <p>
 * This interface works in similar ways as the {@link InputStream} class, but is designed to be RMI compatible. The
 * interface also contains extra method(s) for more efficient implementations of common use-cases (like
 * {@link #writeTo(ByteSink)}).
 * <p>
 * To convert between {@link InputStream} and {@link ByteSource} objects use the static methods declared in this
 * interface.
 * <p>
 * Byte source implementations are not thread-safe by default.
 * 
 * @see ByteSink
 */
public interface ByteSource extends Closeable {
	/**
	 * Reads a single byte from this byte source.
	 * <p>
	 * This method works similarly to {@link InputStream#read()}.
	 * 
	 * @return An unsigned single byte or negative if no more bytes available.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public default int read() throws IOException {
		ByteArrayRegion r = read(1);
		if (r.isEmpty()) {
			return -1;
		}
		return r.get(0) & 0xFF;
	}

	/**
	 * Reads bytes from this byte source and writes them to the argument buffer.
	 * <p>
	 * The number of bytes read is based on the buffer length.
	 * <p>
	 * This method works similarly to {@link InputStream#read(byte[], int, int)}.
	 * <p>
	 * RMI method calls to this method is redirected to {@link #redirectReadCall(ByteSource, ByteRegion)}.
	 * 
	 * @param buffer
	 *            The buffer to read the bytes to.
	 * @return The number of bytes read and put into the buffer. Negative result means that the end of the byte source
	 *             has been reached.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	@RMIRedirect(method = "redirectReadCall")
	//XXX fix this in RMI
//	@RMIExceptionRethrow(RemoteIOException.class)
	public int read(@RMIWrap(RMIByteRegionWrapper.class) ByteRegion buffer) throws IOException, NullPointerException;

	/**
	 * Reads a given number of bytes from this byte source.
	 * <p>
	 * This method reads at most the specified number of bytes from this byte source and returns it as a byte array
	 * region. The resulting buffer may contain less than the requested number of bytes. This doesn't mean that the end
	 * of the stream has been reached, but only that reading more bytes will most likely block.
	 * <p>
	 * If a byte array of zero length is returned, that means that the end of the stream is reached (or the requested
	 * count was less or equal to 0).
	 * <p>
	 * It is recommended that the number of bytes to read is not too large, so a buffer allocated for that size will not
	 * cause {@link OutOfMemoryError}.
	 * 
	 * @param count
	 *            The number of bytes to read.
	 * @return The bytes read from the stream.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public default ByteArrayRegion read(int count) throws IOException {
		byte[] array = new byte[count];
		ByteArrayRegion result = ByteArrayRegion.wrap(array);
		int readcount = read(result);
		if (readcount > 0) {
			if (readcount == count) {
				return result;
			}
			return ByteArrayRegion.wrap(array, 0, readcount);
		}
		return ByteArrayRegion.EMPTY;
	}

	/**
	 * Writes the remaining bytes in this byte source to the specified byte sink.
	 * <p>
	 * This method will take the remaining bytes in this byte source and write it to the specified byte sink. It is
	 * expected that after this method finishes, reading from this byte source will not return any bytes.
	 * 
	 * @param out
	 *            The byte sink to write the bytes to.
	 * @return The number of bytes actually written to the sink. Always zero or greater.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public default long writeTo(ByteSink out) throws IOException, NullPointerException {
		byte[] bufbytes = new byte[8 * 1024];
		ByteArrayRegion buffer = ByteArrayRegion.wrap(bufbytes);
		long count = 0;
		for (int read; (read = this.read(buffer)) > 0;) {
			out.write(ByteArrayRegion.wrap(bufbytes, 0, read));
			count += read;
		}
		return count;
	}

	/**
	 * Skips over a number of bytes in this byte source.
	 * <p>
	 * This method will discard given amount of bytes from this byte source. This works the same way as calling
	 * {@link #read()} <code>n</code> times.
	 * <p>
	 * This method works similarly to {@link InputStream#skip(long)}.
	 * <p>
	 * This method may skip over 0 bytes even if there are still bytes available from this source. It may be recommended
	 * that in order to skip an exact amount of bytes, that external mechanisms are used to ensure valid operation.
	 * 
	 * @param n
	 *            The number of bytes to skip over.
	 * @return The number of bytes actually skipped.
	 * @throws IOException
	 *             In case of I/O error.
	 * @see StreamUtils#skipSourceExactly(ByteSource, long)
	 */
	@RMIExceptionRethrow(RemoteIOException.class)
	public default long skip(long n) throws IOException {
		return StreamUtils.skipSourceByReading(this, n);
	}

	@Override
	@RMIExceptionRethrow(RemoteIOException.class)
	public default void close() throws IOException {
	}

	/**
	 * Converts the argument {@link InputStream} to a {@link ByteSource}.
	 * <p>
	 * If the argument is already a {@link ByteSource}, then it will be returned without modification.
	 * <p>
	 * Else it will be wrapped into a forwarding {@link ByteSource}.
	 * <p>
	 * Closing the result will close the argument too.
	 * 
	 * @param is
	 *            The input stream.
	 * @return The byte sink that uses the passed input stream argument, or <code>null</code> if the argument is
	 *             <code>null</code>.
	 */
	public static ByteSource valueOf(InputStream is) {
		if (is == null) {
			return null;
		}
		if (is instanceof ByteSource) {
			return (ByteSource) is;
		}
		return new InputStreamByteSource(is);
	}

	/**
	 * Converts the argument {@link ObjectInput} to a {@link ByteSource}.
	 * <p>
	 * If the argument is already a {@link ByteSource}, then it will be returned without modification.
	 * <p>
	 * Else it will be wrapped into a forwarding {@link ByteSource}.
	 * <p>
	 * Closing the result will close the argument too.
	 * 
	 * @param is
	 *            The object input.
	 * @return The byte sink that uses the passed input argument, or <code>null</code> if the argument is
	 *             <code>null</code>.
	 */
	public static ByteSource valueOf(ObjectInput is) {
		if (is == null) {
			return null;
		}
		if (is instanceof ByteSource) {
			return (ByteSource) is;
		}
		return new ObjectInputByteSourceImpl(is);
	}

	/**
	 * Converts the argument {@link ByteSource} to an {@link InputStream}.
	 * <p>
	 * If the argument is already an {@link InputStream}, then it will be returned without modification.
	 * <p>
	 * Else it will be wrapped into a forwarding {@link InputStream}.
	 * <p>
	 * Closing the result will close the argument too.
	 * 
	 * @param src
	 *            The byte source.
	 * @return The input stream that usese the passed byte source argument, or <code>null</code> if the argument is
	 *             <code>null</code>.
	 */
	public static InputStream toInputStream(ByteSource src) {
		if (src == null) {
			return null;
		}
		if (src instanceof InputStream) {
			return (InputStream) src;
		}
		return new ByteSourceInputStream(src);
	}

	/**
	 * RMI redirection method when {@link #read(ByteRegion)} is called.
	 * <p>
	 * This method is used to improve the performance of the associated method. If {@link #read(ByteRegion)} is called
	 * directly it can cost multiple {@link ByteRegion#put(int, ByteArrayRegion)} calls to fill the buffer, and thus
	 * result in lesser performance than only a single call.
	 * <p>
	 * This redirection method will call {@link #read(int)} instead with an appropriate byte number count which is
	 * determined based on a default maximum (currently 64MB) and the length of the argument buffer.
	 * <p>
	 * During the operation of this method a temporary buffer will be allocated that has at most the above size, and
	 * which is discarded after this method ends. This method redirection servers as a tradeoff between memory and
	 * network calls. We deem that it is acceptable, as another allocation of the same (or smaller) size of the argument
	 * buffer should not hinder or crash the running JVM, but multiple network calls can decrease performance more.
	 * <p>
	 * If users are trying to read more bytes than the abot 64MB default limit, then they will need to call the
	 * {@link #read(ByteRegion)} method multiple times. This is not a problem as they must expect that the reading
	 * method may not read the argument buffer fully.
	 * 
	 * @param proxy
	 *            The byte source proxy object.
	 * @param buffer
	 *            The byte buffer to read the bytes to.
	 * @return The number of bytes read.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the buffer is <code>null</code>.
	 * @see {@link #read(ByteRegion)}
	 */
	public static int redirectReadCall(ByteSource proxy, ByteRegion buffer) throws IOException, NullPointerException {
		Objects.requireNonNull(buffer, "buffer");
		//redirect the method to avoid frequent .put calls on a remote buffer object
		//   max out the readable byte count for a single request in 64MB
		int len = Math.min(buffer.getLength(), 64 * 1024 * 1024);
		ByteArrayRegion read = proxy.read(len);
		buffer.put(0, read);
		return read.getLength();
	}

}