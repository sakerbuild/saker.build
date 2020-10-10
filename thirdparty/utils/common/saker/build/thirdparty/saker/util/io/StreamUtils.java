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
import java.io.DataInput;
import java.io.DataOutput;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Objects;

import javax.crypto.Mac;

import saker.apiextract.api.PublicApi;
import saker.build.thirdparty.saker.util.ArrayUtils;

/**
 * Utility class containing functions related to manipulating streams and related classes.
 */
@PublicApi
public class StreamUtils {
	private static final PrintStream INSTANCE_NULL_PRINT_STREAM = new PrintStream(NullOutputStream.INSTANCE);
	private static final PrintWriter INSTANCE_NULL_PRINT_WRITER = new PrintWriter(NullOutputStream.INSTANCE);
	/**
	 * The size of the allocated buffers for I/O operations if the caller didn't specify it.
	 */
	public static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

	/**
	 * Gets a null {@link InputStream} that never returns any data.
	 * 
	 * @return The input stream.
	 */
	public static InputStream nullInputStream() {
		return NullInputStream.INSTANCE;
	}

	/**
	 * Gets a null {@link ByteSource} that never returns any data.
	 * 
	 * @return The byte source.
	 */
	public static ByteSource nullByteSource() {
		return NullInputStream.INSTANCE;
	}

	/**
	 * Gets a null {@link Readable} that never returns any data.
	 * 
	 * @return The readable.
	 */
	public static Readable nullReadable() {
		return NullInputStream.INSTANCE;
	}

	/**
	 * Gets a null {@link ObjectInput} that never returns any data.
	 * 
	 * @return The object input.
	 */
	public static ObjectInput nullObjectInput() {
		return NullInputStream.INSTANCE;
	}

	/**
	 * Gets a null {@link DataInput} that never returns any data.
	 * 
	 * @return The data input.
	 */
	public static DataInput nullDataInput() {
		return NullInputStream.INSTANCE;
	}

	/**
	 * Gets a null {@link Reader} that never returns any data.
	 * 
	 * @return The reader.
	 */
	public static Reader nullReader() {
		return NullReader.INSTANCE;
	}

	/**
	 * Gets a null {@link OutputStream} that throws away any data written to it.
	 * 
	 * @return The output stream.
	 */
	public static OutputStream nullOutputStream() {
		return NullOutputStream.INSTANCE;
	}

	/**
	 * Gets a null {@link ByteSink} that throws away any data written to it.
	 * 
	 * @return The byte sink.
	 */
	public static ByteSink nullByteSink() {
		return NullOutputStream.INSTANCE;
	}

	/**
	 * Gets a null {@link Appendable} that throws away any data written to it.
	 * 
	 * @return The appendable.
	 */
	public static Appendable nullAppendable() {
		return NullOutputStream.INSTANCE;
	}

	/**
	 * Gets a null {@link ObjectOutput} that throws away any data written to it.
	 * 
	 * @return The object output.
	 */
	public static ObjectOutput nullObjectOutput() {
		return NullOutputStream.INSTANCE;
	}

	/**
	 * Gets a null {@link DataOutput} that throws away any data written to it.
	 * 
	 * @return The data output.
	 */
	public static DataOutput nullDataOutput() {
		return NullOutputStream.INSTANCE;
	}

	/**
	 * Gets a null {@link Writer} that throws away any data written to it.
	 * 
	 * @return The writer.
	 */
	public static Writer nullWriter() {
		return NullWriter.INSTANCE;
	}

	/**
	 * Gets a null {@link PrintWriter} that throws away any data written to it.
	 * 
	 * @return The writer.
	 */
	public static PrintWriter nullPrintWriter() {
		return INSTANCE_NULL_PRINT_WRITER;
	}

	/**
	 * Gets a null {@link PrintStream} that throws away any data written to it.
	 * 
	 * @return The stream.
	 */
	public static PrintStream nullPrintStream() {
		return INSTANCE_NULL_PRINT_STREAM;
	}

	/**
	 * Gets an output stream that forwards its calls to the argument, and every method of it is synchronized on
	 * <code>this</code>.
	 * 
	 * @param out
	 *            The output stream.
	 * @return The synchronized output stream, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static OutputStream synchronizedOutputStream(OutputStream out) {
		if (out == null) {
			return null;
		}
		return new SynchronizedOutputStream(out);
	}

	/**
	 * Gets an output stream that forwards its calls to the argument, and every method of it is synchronized on
	 * <code>this</code>.
	 * 
	 * @param out
	 *            The byte sink.
	 * @return The synchronized output stream, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static OutputStream synchronizedOutputStream(ByteSink out) {
		if (out == null) {
			return null;
		}
		return new SynchronizedByteSink(out);
	}

	/**
	 * Gets a byte sink that forwards its calls to the argument, and every method of it is synchronized on
	 * <code>this</code>.
	 * 
	 * @param out
	 *            The output stream.
	 * @return The synchronized byte sink, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static ByteSink synchronizedByteSink(OutputStream out) {
		if (out == null) {
			return null;
		}
		return new SynchronizedOutputStream(out);
	}

	/**
	 * Gets a byte sink that forwards its calls to the argument, and every method of it is synchronized on
	 * <code>this</code>.
	 * 
	 * @param out
	 *            The byte sink.
	 * @return The synchronized byte sink, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static ByteSink synchronizedByteSink(ByteSink out) {
		if (out == null) {
			return null;
		}
		return new SynchronizedByteSink(out);
	}

	/**
	 * Gets an input stream that forwards its calls to the argument, and every method of it is synchronized on
	 * <code>this</code>.
	 * 
	 * @param in
	 *            The input stream.
	 * @return The synchronized input stream, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static InputStream synchronizedInputStream(InputStream in) {
		if (in == null) {
			return null;
		}
		return new SynchronizedInputStream(in);
	}

	/**
	 * Gets an input stream that forwards its calls to the argument, and every method of it is synchronized on
	 * <code>this</code>.
	 * 
	 * @param in
	 *            The byte source.
	 * @return The synchronized input stream, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static InputStream synchronizedInputStream(ByteSource in) {
		if (in == null) {
			return null;
		}
		return new SynchronizedByteSource(in);
	}

	/**
	 * Gets a byte source that forwards its calls to the argument, and every method of it is synchronized on
	 * <code>this</code>.
	 * 
	 * @param in
	 *            The input stream.
	 * @return The synchronized byte source, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static ByteSource synchronizedByteSource(InputStream in) {
		if (in == null) {
			return null;
		}
		return new SynchronizedInputStream(in);
	}

	/**
	 * Gets a byte source that forwards its calls to the argument, and every method of it is synchronized on
	 * <code>this</code>.
	 * 
	 * @param in
	 *            The byte source.
	 * @return The synchronized byte source, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static ByteSource synchronizedByteSource(ByteSource in) {
		if (in == null) {
			return null;
		}
		return new SynchronizedByteSource(in);
	}

	/**
	 * Close protects the argument output stream.
	 * <p>
	 * Calling {@link OutputStream#close()} on the returned stream will not close the argument stream.
	 * 
	 * @param out
	 *            The output stream.
	 * @return The close protected stream, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static OutputStream closeProtectedOutputStream(OutputStream out) {
		if (out == null) {
			return null;
		}
		return new CloseProtectedOutputStream(out);
	}

	/**
	 * Close protects the argument byte sink.
	 * <p>
	 * Calling {@link ByteSink#close()} on the returned sink will not close the argument sink.
	 * 
	 * @param out
	 *            The byte sink.
	 * @return The close protected sink, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static ByteSink closeProtectedByteSink(ByteSink out) {
		if (out == null) {
			return null;
		}
		return new CloseProtectedByteSink(out);
	}

	/**
	 * Close protects the argument input stream.
	 * <p>
	 * Calling {@link InputStream#close()} on the returned stream will not close the argument stream.
	 * 
	 * @param in
	 *            The input stream.
	 * @return The close protected stream, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static InputStream closeProtectedInputStream(InputStream in) {
		if (in == null) {
			return null;
		}
		return new CloseProtectedInputStream(in);
	}

	/**
	 * Close protects the argument byte source.
	 * <p>
	 * Calling {@link ByteSource#close()} on the returned source will not close the argument source.
	 * 
	 * @param in
	 *            The byte source.
	 * @return The close protected source, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static ByteSource closeProtectedByteSource(ByteSource in) {
		if (in == null) {
			return null;
		}
		return new CloseProtectedByteSource(in);
	}

	/**
	 * Converts the argument {@link MessageDigest} to an {@link OutputStream}.
	 * <p>
	 * All the bytes written to the output stream will result in a call to
	 * {@link MessageDigest#update(byte[], int, int)} or related methods.
	 * <p>
	 * Closing the returned stream will have no effect on the argument message digest.
	 * 
	 * @param digest
	 *            The message digest.
	 * @return The digest as an output stream, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static OutputStream toOutputStream(MessageDigest digest) {
		if (digest == null) {
			return null;
		}
		return new MessageDigestByteSink(digest);
	}

	/**
	 * Converts the argument {@link MessageDigest} to an {@link ByteSink}.
	 * <p>
	 * All the bytes written to the byte sink will result in a call to {@link MessageDigest#update(byte[], int, int)} or
	 * related methods.
	 * <p>
	 * Closing the returned sink will have no effect on the argument message digest.
	 * 
	 * @param digest
	 *            The message digest.
	 * @return The digest as a byte sink, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static ByteSink toByteSink(MessageDigest digest) {
		if (digest == null) {
			return null;
		}
		return new MessageDigestByteSink(digest);
	}

	/**
	 * Converts the argument {@link Mac} to an {@link OutputStream}.
	 * <p>
	 * All the bytes written to the output stream will result in a call to {@link Mac#update(byte[], int, int)} or
	 * related methods.
	 * <p>
	 * Closing the returned stream will have no effect on the argument <code>Mac</code>.
	 * 
	 * @param mac
	 *            The mac.
	 * @return The mac as an output stream, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static OutputStream toOutputStream(Mac mac) {
		if (mac == null) {
			return null;
		}
		return new MacByteSink(mac);
	}

	/**
	 * Converts the argument {@link Mac} to an {@link ByteSink}.
	 * <p>
	 * All the bytes written to the byte sink will result in a call to {@link Mac#update(byte[], int, int)} or related
	 * methods.
	 * <p>
	 * Closing the returned sink will have no effect on the argument <code>Mac</code>.
	 * 
	 * @param mac
	 *            The mac.
	 * @return The mac as a byte sink, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static ByteSink toByteSink(Mac mac) {
		if (mac == null) {
			return null;
		}
		return new MacByteSink(mac);
	}

	/**
	 * Converts the argument {@link Signature} to an {@link OutputStream}.
	 * <p>
	 * All the bytes written to the output stream will result in a call to {@link Signature#update(byte[], int, int)} or
	 * related methods.
	 * <p>
	 * Closing the returned stream will have no effect on the argument signature object.
	 * <p>
	 * Any {@link SignatureException SignatureExceptions} thrown by the argument will be relayed in an
	 * {@link IOException}.
	 * 
	 * @param signature
	 *            The signature.
	 * @return The signature as an output stream, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static OutputStream toOutputStream(Signature signature) {
		if (signature == null) {
			return null;
		}
		return new SignatureByteSink(signature);
	}

	/**
	 * Converts the argument {@link Signature} to a {@link ByteSink}.
	 * <p>
	 * All the bytes written to the output stream will result in a call to {@link Signature#update(byte[], int, int)} or
	 * related methods.
	 * <p>
	 * Closing the returned stream will have no effect on the argument signature object.
	 * <p>
	 * Any {@link SignatureException SignatureExceptions} thrown by the argument will be wrapped in an
	 * {@link IOException}.
	 * 
	 * @param signature
	 *            The signature.
	 * @return The signature as a byte sink, or <code>null</code> if the argument is <code>null</code>.
	 */
	public static ByteSink toByteSink(Signature signature) {
		if (signature == null) {
			return null;
		}
		return new SignatureByteSink(signature);
	}

	/**
	 * Converts the argument {@link ObjectOutput} to an {@link OutputStream}.
	 * <p>
	 * If the argument is already an instace of {@link OutputStream}, it will be simply returned.
	 * <p>
	 * Closing the returned stream will close the argument object output.
	 * 
	 * @param output
	 *            The object output.
	 * @return The output stream backed by the argument output, or <code>null</code> if the argument is
	 *             <code>null</code>.
	 * @see ByteSink#valueOf(ObjectOutput)
	 */
	public static OutputStream toOutputStream(ObjectOutput output) {
		if (output == null) {
			return null;
		}
		if (output instanceof OutputStream) {
			return (OutputStream) output;
		}
		return new ObjectOutputByteSinkImpl(output);
	}

	/**
	 * Converts the argument {@link ObjectInput} to an {@link InputStream}.
	 * <p>
	 * If the argument is already an instace of {@link InputStream}, it will be simply returned.
	 * <p>
	 * Closing the returned stream will close the argument object input.
	 * 
	 * @param input
	 *            The object input.
	 * @return The input stream backed by the argument input, or <code>null</code> if the argument is <code>null</code>.
	 * @see ByteSource#valueOf(ObjectInput)
	 */
	public static InputStream toInputStream(ObjectInput input) {
		if (input == null) {
			return null;
		}
		if (input instanceof InputStream) {
			return (InputStream) input;
		}
		return new ObjectInputByteSourceImpl(input);
	}

	/**
	 * Converts the argument {@link DataOutput} to a {@link ByteSink}.
	 * <p>
	 * Closing the returned sink will <b>not</b> close the data output. (As the {@link DataOutput} interface doesn't
	 * extends {@link Closeable}.)
	 * <p>
	 * This method is similar to {@link ByteSink}<code>.valueOf</code> methods, but works in a different way, therefore
	 * it was included in this utility class instead of the {@link ByteSink} interface.
	 * 
	 * @param os
	 *            The data output.
	 * @return A byte sink that uses the argument data output, or <code>null</code> if the argument is
	 *             <code>null</code>.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static ByteSink toByteSink(DataOutput os) throws NullPointerException {
		if (os == null) {
			return null;
		}
		return new DataOutputByteSinkImpl(os);
	}

	/**
	 * Copies the bytes from argument input to the specified output.
	 * <p>
	 * A buffer with the size of {@link #DEFAULT_BUFFER_SIZE} will be allocated for copying.
	 * 
	 * @param in
	 *            The input to copy.
	 * @param out
	 *            The output to write the bytes.
	 * @return The number of bytes copied.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static long copyStream(InputStream in, OutputStream out) throws IOException, NullPointerException {
		return copyStream(in, out, DEFAULT_BUFFER_SIZE);
	}

	/**
	 * Copies the bytes from argument input to the specified output using a buffer with the specified buffer size.
	 * 
	 * @param in
	 *            The input to copy.
	 * @param out
	 *            The output to write the bytes.
	 * @param buffersize
	 *            The size of the buffer to allocate for copying.
	 * @return The number of bytes copied.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws NegativeArraySizeException
	 *             If the buffer size is negative.
	 */
	public static long copyStream(InputStream in, OutputStream out, int buffersize)
			throws IOException, NullPointerException, NegativeArraySizeException {
		return copyStream(in, out, new byte[buffersize]);
	}

	/**
	 * Copies the bytes from argument input to the specified output using the specified buffer.
	 * 
	 * @param in
	 *            The input to copy.
	 * @param out
	 *            The output to write the bytes.
	 * @param buffer
	 *            The buffer to use when copying.
	 * @return The number of bytes copied.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static long copyStream(InputStream in, OutputStream out, byte[] buffer)
			throws IOException, NullPointerException {
		Objects.requireNonNull(in, "in");
		Objects.requireNonNull(out, "out");
		Objects.requireNonNull(buffer, "buffer");
		long count = 0;
		for (int read; (read = in.read(buffer)) > 0;) {
			count += read;
			out.write(buffer, 0, read);
		}
		return count;
	}

	/**
	 * Copies the bytes from argument input and updates the given message digest with it.
	 * <p>
	 * A buffer with the size of {@link #DEFAULT_BUFFER_SIZE} will be allocated for copying.
	 * 
	 * @param in
	 *            The input to copy.
	 * @param digest
	 *            The digest to update with the bytes.
	 * @return The number of bytes copied.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static long copyStream(InputStream in, MessageDigest digest) throws IOException, NullPointerException {
		return copyStream(in, digest, DEFAULT_BUFFER_SIZE);
	}

	/**
	 * Copies the bytes from argument input and updates the given message digest with it using a buffer with the
	 * specified buffer size.
	 * 
	 * @param in
	 *            The input to copy.
	 * @param digest
	 *            The digest to update with the bytes.
	 * @param buffersize
	 *            The size of the buffer to allocate for copying.
	 * @return The number of bytes copied.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws NegativeArraySizeException
	 *             If the buffer size is negative.
	 */
	public static long copyStream(InputStream in, MessageDigest digest, int buffersize)
			throws IOException, NullPointerException, NegativeArraySizeException {
		return copyStream(in, digest, new byte[buffersize]);
	}

	/**
	 * Copies the bytes from argument input and updates the given message digest with it using the specified buffer.
	 * 
	 * @param in
	 *            The input to copy.
	 * @param digest
	 *            The digest to update with the bytes.
	 * @param buffer
	 *            The buffer to use when copying.
	 * @return The number of bytes copied.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static long copyStream(InputStream in, MessageDigest digest, byte[] buffer)
			throws IOException, NullPointerException {
		Objects.requireNonNull(in, "in");
		Objects.requireNonNull(digest, "digest");
		Objects.requireNonNull(buffer, "buffer");
		long count = 0;
		for (int read; (read = in.read(buffer)) > 0;) {
			count += read;
			digest.update(buffer, 0, read);
		}
		return count;
	}

	/**
	 * Copies the bytes from argument input and updates the given signature with it.
	 * <p>
	 * A buffer with the size of {@link #DEFAULT_BUFFER_SIZE} will be allocated for copying.
	 * 
	 * @param in
	 *            The input to copy.
	 * @param signature
	 *            The signature to update with the bytes.
	 * @return The number of bytes copied.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws SignatureException
	 *             If the updating of the signature fails.
	 */
	public static long copyStream(InputStream in, Signature signature)
			throws IOException, NullPointerException, SignatureException {
		return copyStream(in, signature, DEFAULT_BUFFER_SIZE);
	}

	/**
	 * Copies the bytes from argument input and updates the given signature with it using a buffer with the specified
	 * buffer size.
	 * 
	 * @param in
	 *            The input to copy.
	 * @param signature
	 *            The signature to update with the bytes.
	 * @param buffersize
	 *            The size of the buffer to allocate for copying.
	 * @return The number of bytes copied.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws NegativeArraySizeException
	 *             If the buffer size is negative.
	 * @throws SignatureException
	 *             If the updating of the signature fails.
	 */
	public static long copyStream(InputStream in, Signature signature, int buffersize)
			throws IOException, NullPointerException, NegativeArraySizeException, SignatureException {
		return copyStream(in, signature, new byte[buffersize]);
	}

	/**
	 * Copies the bytes from argument input and updates the given signature with it using the specified buffer.
	 * 
	 * @param in
	 *            The input to copy.
	 * @param signature
	 *            The signature to update with the bytes.
	 * @param buffer
	 *            The buffer to use when copying.
	 * @return The number of bytes copied.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws SignatureException
	 *             If the updating of the signature fails.
	 */
	public static long copyStream(InputStream in, Signature signature, byte[] buffer)
			throws IOException, NullPointerException, SignatureException {
		Objects.requireNonNull(in, "in");
		Objects.requireNonNull(signature, "signature");
		Objects.requireNonNull(buffer, "buffer");
		long count = 0;
		for (int read; (read = in.read(buffer)) > 0;) {
			count += read;
			signature.update(buffer, 0, read);
		}
		return count;
	}

	/**
	 * Copies the bytes from argument input to the specified output.
	 * <p>
	 * A buffer with the size of {@link #DEFAULT_BUFFER_SIZE} will be allocated for copying.
	 * 
	 * @param in
	 *            The input to copy.
	 * @param out
	 *            The output to write the bytes.
	 * @return The number of bytes copied.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static long copyStream(InputStream in, DataOutput out) throws IOException, NullPointerException {
		return copyStream(in, out, DEFAULT_BUFFER_SIZE);
	}

	/**
	 * Copies the bytes from argument input to the specified output using a buffer with the specified buffer size.
	 * 
	 * @param in
	 *            The input to copy.
	 * @param out
	 *            The output to write the bytes.
	 * @param buffersize
	 *            The size of the buffer to allocate for copying.
	 * @return The number of bytes copied.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws NegativeArraySizeException
	 *             If the buffer size is negative.
	 */
	public static long copyStream(InputStream in, DataOutput out, int buffersize)
			throws IOException, NullPointerException, NegativeArraySizeException {
		return copyStream(in, out, new byte[buffersize]);
	}

	/**
	 * Copies the bytes from argument input to the specified output using the specified buffer.
	 * 
	 * @param in
	 *            The input to copy.
	 * @param out
	 *            The output to write the bytes.
	 * @param buffer
	 *            The buffer to use when copying.
	 * @return The number of bytes copied.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static long copyStream(InputStream in, DataOutput out, byte[] buffer)
			throws IOException, NullPointerException {
		Objects.requireNonNull(in, "in");
		Objects.requireNonNull(out, "out");
		Objects.requireNonNull(buffer, "buffer");
		long count = 0;
		for (int read; (read = in.read(buffer)) > 0;) {
			count += read;
			out.write(buffer, 0, read);
		}
		return count;
	}

	/**
	 * Consumes the argument input stream by reading it until no more data is available.
	 * <p>
	 * An internal work buffer is allocated with the size of {@link #DEFAULT_BUFFER_SIZE}.
	 * 
	 * @param in
	 *            The input.
	 * @return The number of bytes consumed from the stream.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static long consumeStream(InputStream in) throws IOException, NullPointerException {
		return consumeStream(in, new byte[DEFAULT_BUFFER_SIZE]);
	}

	/**
	 * Consumes the argument input stream by reading it until no more data is available, using the given buffer.
	 * <p>
	 * The method will use the argument buffer to read the input to it. It is recommended that callers do not share the
	 * argument buffer for concurrent readings.
	 * 
	 * @param in
	 *            The input.
	 * @param consumebuffer
	 *            The work buffer to use.
	 * @return The number of bytes consumed from the stream.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static long consumeStream(InputStream in, byte[] consumebuffer) throws IOException, NullPointerException {
		Objects.requireNonNull(in, "in");
		Objects.requireNonNull(consumebuffer, "consume buffer");
		long res = 0;
		for (int c; (c = in.read(consumebuffer)) > 0;) {
			res += c;
		}
		return res;
	}

	/**
	 * Reads the argument input fully, and returns the read bytes.
	 * 
	 * @param in
	 *            The input.
	 * @return The read bytes.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static ByteArrayRegion readStreamFully(InputStream in) throws IOException, NullPointerException {
		Objects.requireNonNull(in, "in");
		try (UnsyncByteArrayOutputStream os = new UnsyncByteArrayOutputStream(DEFAULT_BUFFER_SIZE)) {
			os.readFrom(in);
			return os.toByteArrayRegion();
		}
	}

	/**
	 * Reads the argument input fully, and returns the read bytes.
	 * 
	 * @param in
	 *            The input.
	 * @return The read bytes.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static ByteArrayRegion readSourceFully(ByteSource in) throws IOException, NullPointerException {
		Objects.requireNonNull(in, "in");
		try (UnsyncByteArrayOutputStream os = new UnsyncByteArrayOutputStream(DEFAULT_BUFFER_SIZE)) {
			os.readFrom(in);
			return os.toByteArrayRegion();
		}
	}

	/**
	 * Reads the argument input fully, and decodes the bytes using {@linkplain StandardCharsets#UTF_8 UTF-8}.
	 * 
	 * @param in
	 *            The input.
	 * @return The decoded string.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static String readStreamStringFully(InputStream in) throws IOException, NullPointerException {
		return readStreamStringFully(in, StandardCharsets.UTF_8);
	}

	/**
	 * Reads the argument input fully, and decodes the bytes using the given charset.
	 * 
	 * @param in
	 *            The input.
	 * @param charset
	 *            The charset to use when decoding the bytes.
	 * @return The decoded string.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static String readStreamStringFully(InputStream in, Charset charset)
			throws IOException, NullPointerException {
		Objects.requireNonNull(in, "in");
		Objects.requireNonNull(charset, "charset");
		try (UnsyncByteArrayOutputStream os = new UnsyncByteArrayOutputStream(DEFAULT_BUFFER_SIZE)) {
			os.readFrom(in);
			return os.toString(charset);
		}
	}

	/**
	 * Reads the argument input fully, and decodes the bytes using {@linkplain StandardCharsets#UTF_8 UTF-8}.
	 * 
	 * @param in
	 *            The input.
	 * @return The decoded string.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static String readSourceStringFully(ByteSource in) throws IOException, NullPointerException {
		return readSourceStringFully(in, StandardCharsets.UTF_8);
	}

	/**
	 * Reads the argument input fully, and decodes the bytes using the given charset.
	 * 
	 * @param in
	 *            The input.
	 * @param charset
	 *            The charset to use when decoding the bytes.
	 * @return The decoded string.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static String readSourceStringFully(ByteSource in, Charset charset)
			throws IOException, NullPointerException {
		Objects.requireNonNull(in, "in");
		Objects.requireNonNull(charset, "charset");
		try (UnsyncByteArrayOutputStream os = new UnsyncByteArrayOutputStream(DEFAULT_BUFFER_SIZE)) {
			os.readFrom(in);
			return os.toString(charset);
		}
	}

	/**
	 * Reads the argument readable input fully, and returns the read characters as a {@link String}.
	 * <p>
	 * This method is the same as {@link #readReadableCharSequenceFully(Readable)}, but calls
	 * {@link CharSequence#toString()} on the result.
	 * 
	 * @param in
	 *            The input.
	 * @return The read string.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static String readReadableStringFully(Readable in) throws IOException, NullPointerException {
		return readReadableCharSequenceFully(in).toString();
	}

	/**
	 * Reads the argument readable input fully, and returns the read characters as a {@link CharSequence}.
	 * 
	 * @param in
	 *            The input.
	 * @return The read char sequence.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static CharSequence readReadableCharSequenceFully(Readable in) throws IOException, NullPointerException {
		Objects.requireNonNull(in, "in");
		char[] buffer = new char[DEFAULT_BUFFER_SIZE];
		StringBuilder builder = new StringBuilder(DEFAULT_BUFFER_SIZE);
		CharBuffer cbuffer = CharBuffer.wrap(buffer);
		while (true) {
			int read = in.read(cbuffer);
			if (read <= 0) {
				break;
			}
			builder.append(buffer, 0, read);
			cbuffer.clear();
		}
		return builder;
	}

	/**
	 * Reads exactly the specified number of bytes from the given input or throws an {@link EOFException} if not enough
	 * bytes are available.
	 * <p>
	 * <b>Warning:</b> If the argument input is capable of blocking, this method may never return unless external
	 * intervention is made. If less than <code>count</code> bytes are available when this method is called, the reading
	 * will block, until either more bytes, or no more are available.
	 * <p>
	 * E.g. in case of a network TCP stream, if one wants to read 10 bytes, and only 5 is available right now, this
	 * method will block, until the other endpoint of the connection sends 5 more bytes, or the connection is closed.
	 * 
	 * @param in
	 *            The input.
	 * @param count
	 *            The number of bytes to read.
	 * @return The read bytes.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws EOFException
	 *             If not enough bytes are available from the input.
	 * @throws NegativeArraySizeException
	 *             If the specified number of bytes to read is neagive.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static byte[] readStreamBytesExactly(InputStream in, int count)
			throws IOException, EOFException, NegativeArraySizeException, NullPointerException {
		byte[] bytes = new byte[count];
		readStreamBytesExactly(in, bytes, 0, count);
		return bytes;
	}

	/**
	 * Reads the argument buffer fully or throws an {@link EOFException} if not enough bytes are available.
	 * <p>
	 * <b>Warning:</b> If the argument input is capable of blocking, this method may never return unless external
	 * intervention is made. If less than <code>buffer.length</code> bytes are available when this method is called, the
	 * reading will block, until either more bytes, or no more are available.
	 * <p>
	 * E.g. in case of a network TCP stream, if one wants to read 10 bytes, and only 5 is available right now, this
	 * method will block, until the other endpoint of the connection sends 5 more bytes, or the connection is closed.
	 * 
	 * @param in
	 *            The input.
	 * @param buffer
	 *            The buffer to put the read bytes in.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws EOFException
	 *             If not enough bytes are available from the input.
	 * @throws NullPointerException
	 *             If the input or the buffer is <code>null</code>.
	 */
	public static void readStreamBytesExactly(InputStream in, byte[] buffer)
			throws IOException, EOFException, NullPointerException {
		Objects.requireNonNull(buffer, "buffer");
		readStreamBytesExactly(in, buffer, 0, buffer.length);
	}

	/**
	 * Reads exactly the specified number of bytes into the argument buffer with the given offset or throws an
	 * {@link EOFException} if not enough bytes are available.
	 * <p>
	 * <b>Warning:</b> If the argument input is capable of blocking, this method may never return unless external
	 * intervention is made. If less than <code>count</code> bytes are available when this method is called, the reading
	 * will block, until either more bytes, or no more are available.
	 * <p>
	 * E.g. in case of a network TCP stream, if one wants to read 10 bytes, and only 5 is available right now, this
	 * method will block, until the other endpoint of the connection sends 5 more bytes, or the connection is closed.
	 * 
	 * @param in
	 *            The input.
	 * @param buffer
	 *            The buffer to put the read bytes in.
	 * @param offset
	 *            The offset index at which to start putting the bytes. (inclusive)
	 * @param length
	 *            The number of bytes to read.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws EOFException
	 *             If not enough bytes are available from the input.
	 * @throws NullPointerException
	 *             If the input or the buffer is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static void readStreamBytesExactly(InputStream in, byte[] buffer, int offset, int length)
			throws IOException, EOFException, NullPointerException, IndexOutOfBoundsException {
		int res = readFillStreamBytes(in, buffer, offset, length);
		if (res < length) {
			throw new EOFException("Failed to read exactly " + length + " bytes. Only read " + res + ".");
		}
	}

	/**
	 * Reads exactly the specified number of bytes from the given input or throws an {@link EOFException} if failed.
	 * <p>
	 * <b>Warning:</b> If the argument input is capable of blocking, this method may never return unless external
	 * intervention is made. If less than <code>count</code> bytes are available when this method is called, the reading
	 * will block, until either more bytes, or no more are available.
	 * <p>
	 * E.g. in case of a network TCP stream, if one wants to read 10 bytes, and only 5 is available right now, this
	 * method will block, until the other endpoint of the connection sends 5 more bytes, or the connection is closed.
	 * 
	 * @param in
	 *            The input.
	 * @param count
	 *            The number of bytes to read.
	 * @return The read bytes.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws EOFException
	 *             If not enough bytes are available from the input.
	 * @throws NegativeArraySizeException
	 *             If the specified number of bytes to read is neagive.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static byte[] readSourceBytesExactly(ByteSource in, int count)
			throws IOException, EOFException, NegativeArraySizeException, NullPointerException {
		byte[] bytes = new byte[count];
		readSourceBytesExactly(in, bytes, 0, count);
		return bytes;
	}

	/**
	 * Reads the argument buffer fully or throws an {@link EOFException} if not enough bytes are available.
	 * <p>
	 * <b>Warning:</b> If the argument input is capable of blocking, this method may never return unless external
	 * intervention is made. If less than <code>buffer.length</code> bytes are available when this method is called, the
	 * reading will block, until either more bytes, or no more are available.
	 * <p>
	 * E.g. in case of a network TCP stream, if one wants to read 10 bytes, and only 5 is available right now, this
	 * method will block, until the other endpoint of the connection sends 5 more bytes, or the connection is closed.
	 * 
	 * @param in
	 *            The input.
	 * @param buffer
	 *            The buffer to put the read bytes in.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws EOFException
	 *             If not enough bytes are available from the input.
	 * @throws NullPointerException
	 *             If the input or the buffer is <code>null</code>.
	 */
	public static void readSourceBytesExactly(ByteSource in, byte[] buffer)
			throws IOException, EOFException, NullPointerException {
		Objects.requireNonNull(buffer, "buffer");
		readSourceBytesExactly(in, buffer, 0, buffer.length);
	}

	/**
	 * Reads exactly the specified number of bytes into the argument buffer with the given offset or throws an
	 * {@link EOFException} if not enough bytes are available.
	 * <p>
	 * <b>Warning:</b> If the argument input is capable of blocking, this method may never return unless external
	 * intervention is made. If less than <code>count</code> bytes are available when this method is called, the reading
	 * will block, until either more bytes, or no more are available.
	 * <p>
	 * E.g. in case of a network TCP stream, if one wants to read 10 bytes, and only 5 is available right now, this
	 * method will block, until the other endpoint of the connection sends 5 more bytes, or the connection is closed.
	 * 
	 * @param in
	 *            The input.
	 * @param buffer
	 *            The buffer to put the read bytes in.
	 * @param offset
	 *            The offset index at which to start putting the bytes. (inclusive)
	 * @param length
	 *            The number of bytes to read.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws EOFException
	 *             If not enough bytes are available from the input.
	 * @throws NullPointerException
	 *             If the input or the buffer is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static void readSourceBytesExactly(ByteSource in, byte[] buffer, int offset, int length)
			throws IOException, EOFException, NullPointerException, IndexOutOfBoundsException {
		int res = readFillSourceBytes(in, buffer, offset, length);
		if (res < length) {
			throw new EOFException("Failed to read exactly " + length + " bytes. Only read " + res + ".");
		}
	}

	/**
	 * Reads bytes from the input into the argument buffer until either the buffer is filled, or no more bytes are
	 * available.
	 * <p>
	 * This method works like {@link #readStreamBytesExactly(InputStream, byte[])}, but doesn't throw an
	 * {@link EOFException} if not enough bytes are available to fill the buffer, just returns.
	 * 
	 * @param in
	 *            The input.
	 * @param buffer
	 *            The buffer to put the read bytes in.
	 * @return The number of bytes read.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the input or the buffer is <code>null</code>.
	 */
	public static int readFillStreamBytes(InputStream in, byte[] buffer) throws IOException, NullPointerException {
		Objects.requireNonNull(in, "input");
		Objects.requireNonNull(buffer, "buffer");
		return readFillStreamBytesImpl(in, buffer, 0, buffer.length);
	}

	/**
	 * Reads bytes from the input into the argument buffer with the given range until either the range is filled, or no
	 * more bytes are available.
	 * <p>
	 * This method works like {@link #readStreamBytesExactly(InputStream, byte[])}, but doesn't throw an
	 * {@link EOFException} if not enough bytes are available to fill the buffer, just returns.
	 * 
	 * @param in
	 *            The input.
	 * @param buffer
	 *            The buffer to put the read bytes in.
	 * @param offset
	 *            The offset index at which to start putting the bytes. (inclusive)
	 * @param length
	 *            The number of bytes to read.
	 * @return The number of bytes read.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the input or the buffer is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static int readFillStreamBytes(InputStream in, byte[] buffer, int offset, int length)
			throws IOException, NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(in, "input");
		ArrayUtils.requireArrayRange(buffer, offset, length);
		return readFillStreamBytesImpl(in, buffer, offset, length);
	}

	/**
	 * Reads bytes from the input into the argument buffer until either the buffer is filled, or no more bytes are
	 * available.
	 * <p>
	 * This method works like {@link #readStreamBytesExactly(InputStream, byte[])}, but doesn't throw an
	 * {@link EOFException} if not enough bytes are available to fill the buffer, just returns.
	 * 
	 * @param in
	 *            The input.
	 * @param buffer
	 *            The buffer to put the read bytes in.
	 * @return The number of bytes read.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the input or the buffer is <code>null</code>.
	 */
	public static int readFillSourceBytes(ByteSource in, byte[] buffer) throws IOException, NullPointerException {
		Objects.requireNonNull(in, "input");
		Objects.requireNonNull(buffer, "buffer");
		return readFillSourceBytesImpl(in, buffer, 0, buffer.length);
	}

	/**
	 * Reads bytes from the input into the argument buffer with the given range until either the range is filled, or no
	 * more bytes are available.
	 * <p>
	 * This method works like {@link #readStreamBytesExactly(InputStream, byte[])}, but doesn't throw an
	 * {@link EOFException} if not enough bytes are available to fill the buffer, just returns.
	 * 
	 * @param in
	 *            The input.
	 * @param buffer
	 *            The buffer to put the read bytes in.
	 * @param offset
	 *            The offset index at which to start putting the bytes. (inclusive)
	 * @param length
	 *            The number of bytes to read.
	 * @return The number of bytes read.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the input or the buffer is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static int readFillSourceBytes(ByteSource in, byte[] buffer, int offset, int length)
			throws IOException, NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(in, "input");
		ArrayUtils.requireArrayRange(buffer, offset, length);
		return readFillSourceBytesImpl(in, buffer, offset, length);
	}

	/**
	 * Reads bytes from the input into the argument buffer until either the buffer is filled, or no more bytes are
	 * available.
	 * <p>
	 * This method works like {@link #readStreamBytesExactly(InputStream, byte[])}, but doesn't throw an
	 * {@link EOFException} if not enough bytes are available to fill the buffer, just returns.
	 * 
	 * @param in
	 *            The input.
	 * @param buffer
	 *            The buffer to put the read bytes in.
	 * @return The number of bytes read.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the input or the buffer is <code>null</code>.
	 */
	public static int readFillObjectInputBytes(ObjectInput in, byte[] buffer) throws IOException, NullPointerException {
		Objects.requireNonNull(in, "input");
		Objects.requireNonNull(buffer, "buffer");
		return readFillObjectInputBytesImpl(in, buffer, 0, buffer.length);
	}

	/**
	 * Reads bytes from the input into the argument buffer with the given range until either the range is filled, or no
	 * more bytes are available.
	 * <p>
	 * This method works like {@link #readStreamBytesExactly(InputStream, byte[])}, but doesn't throw an
	 * {@link EOFException} if not enough bytes are available to fill the buffer, just returns.
	 * 
	 * @param in
	 *            The input.
	 * @param buffer
	 *            The buffer to put the read bytes in.
	 * @param offset
	 *            The offset index at which to start putting the bytes. (inclusive)
	 * @param length
	 *            The number of bytes to read.
	 * @return The number of bytes read.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the input or the buffer is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             If the specified range is out of bounds.
	 */
	public static int readFillObjectInputBytes(ObjectInput in, byte[] buffer, int offset, int length)
			throws IOException, NullPointerException, IndexOutOfBoundsException {
		Objects.requireNonNull(in, "input");
		ArrayUtils.requireArrayRange(buffer, offset, length);
		return readFillObjectInputBytesImpl(in, buffer, offset, length);
	}

	/**
	 * Skips the argument number of bytes from the given input, throwing an {@link EOFException} if the operation fails.
	 * <p>
	 * This method will try to skip the given number amount of bytes. It will first try to use the
	 * {@link ByteSource#skip(long)} method. If that returns zero or negative, then the method will try to directly read
	 * bytes from the input into an allocated work buffer. If after all not enough bytes are skipped, an
	 * {@link EOFException} will be thrown with a message containing the remaining amount of bytes.
	 * <p>
	 * If the number of bytes to skip is zero or negative, this method will always succeed.
	 * 
	 * @param in
	 *            The input.
	 * @param n
	 *            The number of bytes to skip.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws EOFException
	 *             If the method failed to skip over the specified number of bytes.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static void skipSourceExactly(ByteSource in, long n) throws IOException, EOFException, NullPointerException {
		Objects.requireNonNull(in, "input");
		ByteArrayRegion consumebar = null;
		while (n > 0) {
			long skipped = in.skip(n);
			if (skipped <= 0) {
				if (consumebar == null) {
					consumebar = ByteArrayRegion.wrap(new byte[(int) Math.min(n, DEFAULT_BUFFER_SIZE)]);
				}
				int rskipped = in.read(consumebar);
				if (rskipped <= 0) {
					throw new EOFException("Failed to skip " + n + " more bytes.");
				}
				n -= rskipped;
				continue;
			}
			n -= skipped;
		}
	}

	/**
	 * Skips the argument number of bytes from the given input, throwing an {@link EOFException} if the operation fails.
	 * <p>
	 * This method will try to skip the given number amount of bytes. It will first try to use the
	 * {@link InputStream#skip(long)} method. If that returns zero or negative, then the method will try to directly
	 * read bytes from the input into an allocated work buffer. If after all not enough bytes are skipped, an
	 * {@link EOFException} will be thrown with a message containing the remaining amount of bytes.
	 * <p>
	 * If the number of bytes to skip is zero or negative, this method will always succeed.
	 * 
	 * @param in
	 *            The input.
	 * @param n
	 *            The number of bytes to skip.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws EOFException
	 *             If the method failed to skip over the specified number of bytes.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static void skipStreamExactly(InputStream in, long n)
			throws IOException, EOFException, NullPointerException {
		Objects.requireNonNull(in, "input");
		if (n <= 0) {
			return;
		}
		byte[] consumebuffer = null;
		while (n > 0) {
			long skipped = in.skip(n);
			if (skipped <= 0) {
				if (consumebuffer == null) {
					consumebuffer = new byte[(int) Math.min(n, DEFAULT_BUFFER_SIZE)];
				}
				int rskipped = in.read(consumebuffer, 0, (int) Math.min(n, consumebuffer.length));
				if (rskipped <= 0) {
					throw new EOFException("Failed to skip " + n + " more bytes.");
				}
				n -= rskipped;
				continue;
			}
			n -= skipped;
		}
	}

	/**
	 * Skips the specified number of bytes from the input by reading the given number of bytes from it.
	 * <p>
	 * This method will always read the bytes from the input, and never calls {@link ByteSource#skip(long)}.
	 * <p>
	 * If the number of bytes to skip is zero or negative, this method will always succeed and return 0.
	 * 
	 * @param in
	 *            The input.
	 * @param n
	 *            The number of bytes to skip.
	 * @return The actual number of bytes skipped.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static long skipSourceByReading(ByteSource in, long n) throws IOException, NullPointerException {
		Objects.requireNonNull(in, "input");
		if (n <= 0) {
			return 0;
		}
		byte[] consumebuffer = new byte[(int) Math.min(DEFAULT_BUFFER_SIZE, n)];
		long skipped = 0;
		while (skipped < n) {
			int readc = in
					.read(ByteArrayRegion.wrap(consumebuffer, 0, Math.min((int) (n - skipped), consumebuffer.length)));
			if (readc <= 0) {
				return skipped;
			}
			skipped += readc;
		}
		return skipped;
	}

	/**
	 * Skips the specified number of bytes from the input by reading the given number of bytes from it.
	 * <p>
	 * This method will always read the bytes from the input, and never calls {@link InputStream#skip(long)}.
	 * <p>
	 * If the number of bytes to skip is zero or negative, this method will always succeed and return 0.
	 * 
	 * @param in
	 *            The input.
	 * @param n
	 *            The number of bytes to skip.
	 * @return The actual number of bytes skipped.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the input is <code>null</code>.
	 */
	public static long skipStreamByReading(InputStream in, long n) throws IOException, NullPointerException {
		Objects.requireNonNull(in, "input");
		if (n <= 0) {
			return 0;
		}
		byte[] consumebuffer = new byte[(int) Math.min(DEFAULT_BUFFER_SIZE, n)];
		long skipped = 0;
		while (skipped < n) {
			int readc = in.read(consumebuffer, 0, Math.min((int) (n - skipped), consumebuffer.length));
			if (readc <= 0) {
				return skipped;
			}
			skipped += readc;
		}
		return skipped;
	}

	/**
	 * Reads bytes from the given input and puts then into the argument buffer.
	 * <p>
	 * This method will efficiently handle the case when the buffer is an instance of {@link ByteArrayRegion}, and read
	 * directly into the backing array.
	 * <p>
	 * In other cases it will allocate a work buffer that will serve as a temporary work memory region. It will be lated
	 * put into the argument buffer via {@link ByteRegion#put(int, ByteArrayRegion)}.
	 * 
	 * @param in
	 *            The input.
	 * @param buffer
	 *            The buffer to read into.
	 * @return The number of bytes read into the buffer.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static int readFromStream(InputStream in, ByteRegion buffer) throws IOException, NullPointerException {
		Objects.requireNonNull(in, "input");
		Objects.requireNonNull(buffer, "buffer");
		if (buffer instanceof ByteArrayRegion) {
			//we can read directly to the byte array
			ByteArrayRegion bar = (ByteArrayRegion) buffer;
			return in.read(bar.getArray(), bar.getOffset(), bar.getLength());
		}
		byte[] bytebuf = new byte[Math.min(buffer.getLength(), DEFAULT_BUFFER_SIZE)];
		int r = in.read(bytebuf);
		if (r <= 0) {
			return r;
		}
		buffer.put(0, ByteArrayRegion.wrap(bytebuf, 0, r));
		return r;
	}

	/**
	 * Reads bytes from the given input and puts then into the argument buffer.
	 * <p>
	 * This method will efficiently handle the case when the buffer is an instance of {@link ByteArrayRegion}, and read
	 * directly into the backing array.
	 * <p>
	 * In other cases it will allocate a work buffer that will serve as a temporary work memory region. It will be lated
	 * put into the argument buffer via {@link ByteRegion#put(int, ByteArrayRegion)}.
	 * 
	 * @param in
	 *            The input.
	 * @param buffer
	 *            The buffer to read into.
	 * @return The number of bytes read into the buffer.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static int readFromObjectInput(ObjectInput in, ByteRegion buffer) throws IOException, NullPointerException {
		Objects.requireNonNull(in, "input");
		Objects.requireNonNull(buffer, "buffer");
		if (buffer instanceof ByteArrayRegion) {
			//we can read directly to the byte array
			ByteArrayRegion bar = (ByteArrayRegion) buffer;
			return in.read(bar.getArray(), bar.getOffset(), bar.getLength());
		}
		byte[] bytebuf = new byte[Math.min(buffer.getLength(), DEFAULT_BUFFER_SIZE)];
		int r = in.read(bytebuf);
		if (r <= 0) {
			return r;
		}
		buffer.put(0, ByteArrayRegion.wrap(bytebuf, 0, r));
		return r;
	}

	private static int readFillStreamBytesImpl(InputStream in, byte[] buffer, int offset, int length)
			throws IOException {
		if (length <= 0) {
			return 0;
		}
		int result = 0;
		while (true) {
			int read = in.read(buffer, offset, length);
			if (read <= 0) {
				break;
			}
			length -= read;
			result += read;
			offset += read;
			if (length <= 0) {
				break;
			}
		}
		return result;
	}

	private static int readFillSourceBytesImpl(ByteSource in, byte[] buffer, int offset, int length)
			throws IOException {
		if (length <= 0) {
			return 0;
		}
		int result = 0;
		while (true) {
			int read = in.read(ByteArrayRegion.wrap(buffer, offset, length));
			if (read <= 0) {
				break;
			}
			length -= read;
			result += read;
			offset += read;
			if (length <= 0) {
				break;
			}
		}
		return result;
	}

	private static int readFillObjectInputBytesImpl(ObjectInput in, byte[] buffer, int offset, int length)
			throws IOException {
		if (length <= 0) {
			return 0;
		}
		int result = 0;
		while (true) {
			int read = in.read(buffer, offset, length);
			if (read <= 0) {
				break;
			}
			length -= read;
			result += read;
			offset += read;
			if (length <= 0) {
				break;
			}
		}
		return result;
	}

	private StreamUtils() {
		throw new UnsupportedOperationException();
	}
}
