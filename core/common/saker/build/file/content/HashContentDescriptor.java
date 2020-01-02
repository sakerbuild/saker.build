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
package saker.build.file.content;

import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.security.DigestException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;

import saker.apiextract.api.PublicApi;
import saker.build.file.StreamWritable;
import saker.build.file.provider.FileHashResult;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;

/**
 * {@link ContentDescriptor} implementation that is backed by a hash result.
 * <p>
 * When changes are being detected, this class compares the hash bytes to the arugment hash bytes.
 * <p>
 * When possible, the actual hash bytes of the underlying instance will also contain the number of bytes that were used
 * to compute the hash.
 * <p>
 * A new instance can be created by calling one of the static {@linkplain #hash(byte[]) factory methods}.
 */
@PublicApi
public final class HashContentDescriptor implements ContentDescriptor, Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * {@link OutputStream} which forwards any bytes written to it to an underlying {@link MessageDigest}.
	 * <p>
	 * The class also counts the number of bytes written to the stream.
	 * <p>
	 * The hash result can be retrieved (only once) from one of the digest methods.
	 * <p>
	 * Closing the stream will not affect the underlying {@link MessageDigest}.
	 * 
	 * @see #digestWithCount()
	 * @see #digest()
	 */
	public static class MessageDigestOutputStream extends OutputStream implements ByteSink {
		/**
		 * The underlying {@link MessageDigest} for the hashing.
		 */
		protected MessageDigest digest;
		/**
		 * The number of bytes written to the stream.
		 */
		protected long count = 0;

		/**
		 * Creates a new instance that uses the default hash algorithm for the {@link HashContentDescriptor} class.
		 */
		public MessageDigestOutputStream() {
			this.digest = getDefaultDigest();
		}

		/**
		 * Creates a new instance that uses the argument {@link MessageDigest} for hashing.
		 * <p>
		 * Any bytes written to this output stream will update the given message digest.
		 * 
		 * @param digest
		 *            The message digest.
		 * @throws NullPointerException
		 *             If the argument is <code>null</code>.
		 */
		public MessageDigestOutputStream(MessageDigest digest) throws NullPointerException {
			Objects.requireNonNull(digest, "digest");
			this.digest = digest;
		}

		@Override
		public void write(int b) throws IOException {
			digest.update((byte) (b & 0xFF));
			count++;
		}

		@Override
		public void write(byte[] b) throws IOException {
			digest.update(b);
			count += b.length;
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			digest.update(b, off, len);
			count += len;
		}

		/**
		 * Creates the hash bytes by digesting the underlying {@link MessageDigest}, and appending the binary
		 * representation of the number of bytes written to this stream.
		 * <p>
		 * This, and other digesting methods can be called only once.
		 * 
		 * @return The digested hash.
		 * @see MessageDigest#digest()
		 */
		public byte[] digestWithCount() {
			return digestToArrayWithCount(count, digest);
		}

		/**
		 * Creates the hash bytes by digesting the underlying {@link MessageDigest}.
		 * <p>
		 * This, and other digesting methods can be called only once.
		 * 
		 * @return The digested hash.
		 * @see MessageDigest#digest()
		 */
		public byte[] digest() {
			return digest.digest();
		}

		@Override
		public void write(ByteArrayRegion buf) throws IOException {
			this.write(buf.getArray(), buf.getOffset(), buf.getLength());
		}

		@Override
		public void close() throws IOException {
		}
	}

	private byte[] hash;

	/**
	 * For {@link Externalizable}.
	 */
	public HashContentDescriptor() {
	}

	private HashContentDescriptor(byte[] hash) {
		this.hash = hash;
	}

	/**
	 * Creates a new instance by hashing the argument byte data.
	 * 
	 * @param data
	 *            The byte data.
	 * @return The created content descriptor.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static ContentDescriptor hash(byte[] data) throws NullPointerException {
		Objects.requireNonNull(data, "data");
		return hash(data, 0, data.length, getDefaultDigest());
	}

	/**
	 * Creates a new instance by hashing the argument byte data.
	 * 
	 * @param data
	 *            The byte data.
	 * @return The created content descriptor.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static ContentDescriptor hash(ByteArrayRegion data) throws NullPointerException {
		return hash(data, getDefaultDigest());
	}

	/**
	 * Creates a new instance by hashing the argument byte data, using the given hasher.
	 * 
	 * @param data
	 *            The byte data.
	 * @param digest
	 *            The hasher.
	 * @return The created content descriptor.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static ContentDescriptor hash(ByteArrayRegion data, MessageDigest digest) throws NullPointerException {
		Objects.requireNonNull(data, "data");
		int len = data.getLength();
		digest.update(data.getArray(), data.getOffset(), len);
		return new HashContentDescriptor(digestToArrayWithCount(len, digest));
	}

	/**
	 * Creates a new instance by hashing the argument byte data of the specified region.
	 * 
	 * @param data
	 *            The byte data.
	 * @param offset
	 *            The starting index in the array.
	 * @param len
	 *            The number of bytes to hash starting from the offset.
	 * @return The created content descriptor.
	 * @throws NullPointerException
	 *             If the array argument is <code>null</code>.
	 */
	public static ContentDescriptor hash(byte[] data, int offset, int len) throws NullPointerException {
		return hash(data, offset, len, getDefaultDigest());
	}

	/**
	 * Creates a new instance by hashing the argument byte data of the specified region, using the given hasher.
	 * 
	 * @param data
	 *            The byte data.
	 * @param offset
	 *            The starting index in the array.
	 * @param len
	 *            The number of bytes to hash starting from the offset.
	 * @param digest
	 *            The hasher.
	 * @return The created content descriptor.
	 * @throws NullPointerException
	 *             If the array or hasher arguments are <code>null</code>.
	 */
	public static ContentDescriptor hash(byte[] data, int offset, int len, MessageDigest digest)
			throws NullPointerException {
		Objects.requireNonNull(data, "data");
		digest.update(data, offset, len);
		return new HashContentDescriptor(digestToArrayWithCount(len, digest));
	}

	/**
	 * Creates a new instance by hashing the bytes from the given input stream.
	 * 
	 * @param data
	 *            The input stream of bytes.
	 * @return The created content descriptor.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If argument is <code>null</code>.
	 */
	public static ContentDescriptor hash(InputStream data) throws IOException, NullPointerException {
		return hash(data, getDefaultDigest());
	}

	/**
	 * Creates a new instance by hashing the bytes from the given input stream, using the given hasher.
	 * 
	 * @param data
	 *            The input stream of bytes.
	 * @param digest
	 *            The hasher.
	 * @return The created content descriptor.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static ContentDescriptor hash(InputStream data, MessageDigest digest)
			throws IOException, NullPointerException {
		Objects.requireNonNull(data, "data");
		try (MessageDigestOutputStream out = new MessageDigestOutputStream(digest)) {
			StreamUtils.copyStream(data, out);
			return new HashContentDescriptor(out.digestWithCount());
		}
	}

	/**
	 * Creates a new instance by hashing the bytes from the given stream writable.
	 * 
	 * @param data
	 *            The stream writable of bytes.
	 * @return The created content descriptor.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If argument is <code>null</code>.
	 */
	public static ContentDescriptor hash(StreamWritable data) throws IOException, NullPointerException {
		return hash(data, getDefaultDigest());
	}

	/**
	 * Creates a new instance by hashing the bytes from the given stream writable, using the given hasher.
	 * 
	 * @param data
	 *            The stream writable of bytes.
	 * @param digest
	 *            The hasher.
	 * @return The created content descriptor.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static ContentDescriptor hash(StreamWritable data, MessageDigest digest)
			throws IOException, NullPointerException {
		Objects.requireNonNull(data, "data");
		try (MessageDigestOutputStream out = new MessageDigestOutputStream(digest)) {
			data.writeTo((ByteSink) out);
			return new HashContentDescriptor(out.digestWithCount());
		}
	}

	/**
	 * Creates a new instance by initializing it with the specified hash.
	 * 
	 * @param hash
	 *            The hash.
	 * @return The created content descriptor.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static ContentDescriptor createWithHash(FileHashResult hash) throws NullPointerException {
		Objects.requireNonNull(hash, "hash");
		return new HashContentDescriptor(createHashArrayWithCount(hash.getCount(), hash.getHash()));
	}

	/**
	 * Creates a new instance by initializing it with the specified hash.
	 * 
	 * @param hash
	 *            The hash.
	 * @return The created content descriptor.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static ContentDescriptor createWithHash(byte[] hash) throws NullPointerException {
		Objects.requireNonNull(hash, "hash");
		return new HashContentDescriptor(hash.clone());
	}

	/**
	 * Creates a new instance by initializing it with the specified hash.
	 * 
	 * @param hash
	 *            The hash.
	 * @return The created content descriptor.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static ContentDescriptor createWithHash(ByteArrayRegion hash) throws NullPointerException {
		Objects.requireNonNull(hash, "hash");
		return new HashContentDescriptor(hash.copy());
	}

	private static MessageDigest getDefaultDigest() {
		return FileUtils.getDefaultFileHasher();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(hash);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		hash = (byte[]) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(hash);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		HashContentDescriptor other = (HashContentDescriptor) obj;
		if (!Arrays.equals(hash, other.hash))
			return false;
		return true;
	}

	@Override
	public boolean isChanged(ContentDescriptor o) {
		if (!(o instanceof HashContentDescriptor)) {
			return true;
		}
		return !Arrays.equals(this.hash, ((HashContentDescriptor) o).hash);
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + " [" + getHashHexString() + "]";
	}

	/**
	 * Gets a hexadecimal representation of the underlying hash bytes.
	 * 
	 * @return The hash bytes in hexadecimal format.
	 */
	public String getHashHexString() {
		return StringUtils.toHexString(hash);
	}

	private static byte[] createHashArrayWithCount(long count, byte[] hash) throws IllegalStateException {
		final int digestlen = hash.length;
		byte[] array;
		if (count > Integer.MAX_VALUE) {
			array = new byte[Long.BYTES + digestlen];
			SerialUtils.writeLongToBuffer(count, array, digestlen);
		} else if (count > Short.MAX_VALUE) {
			array = new byte[Integer.BYTES + digestlen];
			SerialUtils.writeIntToBuffer((int) count, array, digestlen);
		} else if (count > Byte.MAX_VALUE) {
			array = new byte[Short.BYTES + digestlen];
			SerialUtils.writeShortToBuffer((short) count, array, digestlen);
		} else {
			array = new byte[Byte.BYTES + digestlen];
			array[digestlen] = (byte) count;
		}
		System.arraycopy(hash, 0, array, 0, digestlen);
		return array;
	}

	private static byte[] digestToArrayWithCount(long count, MessageDigest digest) throws IllegalStateException {
		final int digestlen = digest.getDigestLength();
		if (digestlen == 0) {
			return createHashArrayWithCount(count, digest.digest());
		}
		byte[] array;
		if (count > Integer.MAX_VALUE) {
			array = new byte[Long.BYTES + digestlen];
			SerialUtils.writeLongToBuffer(count, array, digestlen);
		} else if (count > Short.MAX_VALUE) {
			array = new byte[Integer.BYTES + digestlen];
			SerialUtils.writeIntToBuffer((int) count, array, digestlen);
		} else if (count > Byte.MAX_VALUE) {
			array = new byte[Short.BYTES + digestlen];
			SerialUtils.writeShortToBuffer((short) count, array, digestlen);
		} else {
			array = new byte[Byte.BYTES + digestlen];
			array[digestlen] = (byte) count;
		}
		try {
			digest.digest(array, 0, digestlen);
		} catch (DigestException e) {
			throw new IllegalStateException(e);
		}
		return array;
	}
}
