package saker.build.thirdparty.saker.util.io;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import saker.build.thirdparty.saker.util.ArrayUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;

/**
 * Class similar to {@link ByteArrayInputStream}, but the methods are not synchronized.
 * <p>
 * The class also contains more functions for more complex manipulations.
 * <p>
 * For reading structured data from the stream, use the subclass {@link DataInputUnsyncByteArrayInputStream}.
 * 
 * @see UnsyncByteArrayOutputStream
 */
public class UnsyncByteArrayInputStream extends InputStream implements ByteSource {
	/**
	 * The buffer holding the data.
	 */
	protected byte[] buffer;
	/**
	 * The last byte that can be read from the buffer. (exclusive)
	 */
	protected final int endOffset;
	/**
	 * The offset in the buffer where the data starts.
	 * <p>
	 * This field is incremented as more data is read from the stream.
	 * <p>
	 * This field is never greater than {@link #endOffset}.
	 */
	protected int position;

	/**
	 * Creates a new stream that is backed by the argument buffer.
	 * 
	 * @param buf
	 *            The byte buffer.
	 * @throws NullPointerException
	 *             If the buffer is <code>null</code>.
	 */
	public UnsyncByteArrayInputStream(byte[] buf) throws NullPointerException {
		Objects.requireNonNull(buf, "buffer");
		this.buffer = buf;
		this.position = 0;
		this.endOffset = buf.length;
	}

	/**
	 * Creates a new stream that is backed by a region in the argument array.
	 * 
	 * @param buf
	 *            The byte buffer.
	 * @param offset
	 *            The region start offset. (inclusive)
	 * @param length
	 *            The number of bytes that is available for reading from the buffer starting at the offset.
	 * @throws NullPointerException
	 *             If the buffer is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public UnsyncByteArrayInputStream(byte[] buf, int offset, int length)
			throws NullPointerException, IndexOutOfBoundsException {
		ArrayUtils.requireArrayRange(buf, offset, length);
		this.buffer = buf;
		this.position = offset;
		this.endOffset = offset + length;
	}

	/**
	 * Creates a new stream that is backed by the argument byte array region.
	 * 
	 * @param bytes
	 *            The byte array region.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public UnsyncByteArrayInputStream(ByteArrayRegion bytes) throws NullPointerException {
		Objects.requireNonNull(bytes, "buffer");
		this.buffer = bytes.getArray();
		int offset = bytes.getOffset();
		this.position = offset;
		this.endOffset = offset + bytes.getLength();
	}

	/**
	 * Converts the remaining bytes in the stream to a {@link ByteArrayRegion}.
	 * <p>
	 * This is not a reading operation, this will not cause the remaining bytes to be skipped over.
	 * 
	 * @return The byte array region for the remaining data.
	 */
	public ByteArrayRegion toByteArrayRegion() {
		return ByteArrayRegion.wrap(buffer, position, available());
	}

	/**
	 * Peeks a byte from the buffer.
	 * <p>
	 * This is same as {@link #read()}, but doesn't advance the buffer pointer.
	 * <p>
	 * The result is normalized to a 0-255 byte range.
	 * 
	 * @return The peeked byte, or -1 if no more bytes available.
	 */
	public int peek() {
		return (position < endOffset) ? (buffer[position] & 0xff) : -1;
	}

	/**
	 * Skips the remaining bytes in the stream.
	 * <p>
	 * After this method returns, reading operations will return no more bytes.
	 * 
	 * @return The number of bytes skipped.
	 */
	public long skipRemaining() {
		int result = available();
		this.position = this.endOffset;
		this.buffer = ObjectUtils.EMPTY_BYTE_ARRAY;
		return result;
	}

	/**
	 * Checks if there are any remaining bytes in the stream.
	 * 
	 * @return <code>true</code> if reading operations will not return any more bytes.
	 */
	public boolean isEmpty() {
		return available() == 0;
	}

	@Override
	public long writeTo(ByteSink out) throws IOException {
		int remain = available();
		out.write(ByteArrayRegion.wrap(buffer, position, remain));
		position = endOffset;
		return remain;
	}

	@Override
	public int read() {
		return (position < endOffset) ? (buffer[position++] & 0xff) : -1;
	}

	@Override
	public int read(byte[] b) throws IOException {
		return this.read(b, 0, b.length);
	}

	@Override
	public int read(byte[] b, int off, int len) {
		int avail = available();
		if (avail <= 0) {
			return -1;
		}
		if (len > avail) {
			len = avail;
		}
		if (len <= 0) {
			return 0;
		}
		System.arraycopy(buffer, position, b, off, len);
		position += len;
		return len;
	}

	@Override
	public int read(ByteRegion buffer) throws IOException {
		if (position >= endOffset) {
			return -1;
		}
		int avail = available();
		int len = Math.min(buffer.getLength(), avail);
		if (len <= 0) {
			return 0;
		}
		buffer.put(0, ByteArrayRegion.wrap(this.buffer, position, len));
		position += len;
		return len;
	}

	@Override
	public ByteArrayRegion read(int count) throws IOException {
		int avail = available();
		if (count > avail) {
			count = avail;
		}
		ByteArrayRegion result = ByteArrayRegion.wrap(buffer, position, count);
		this.position += count;
		return result;
	}

	@Override
	public long skip(long n) {
		if (n < 0) {
			return 0;
		}
		long avail = available();
		if (n < avail) {
			avail = n < 0 ? 0 : n;
		}

		position += avail;
		return avail;
	}

	@Override
	public int available() {
		return endOffset - position;
	}

	@Override
	public boolean markSupported() {
		return false;
	}

	@Override
	public void mark(int readAheadLimit) {
	}

	@Override
	public void reset() throws IOException {
		throw new IOException("mark/reset not supported");
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Closing the stream will release the reference to the used buffer, and no more bytes can be read from it.
	 */
	@Override
	public void close() {
		position = endOffset;
		buffer = ObjectUtils.EMPTY_BYTE_ARRAY;
	}

}
