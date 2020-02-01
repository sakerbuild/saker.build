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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Objects;

/**
 * Output stream implementation that delegates its calls to a primary, and multiple secondary output streams.
 * <p>
 * This class acts as a simple forwarding output stream for the priority/primary output stream, and acts the same way as
 * {@link MultiplexOutputStream} for the secondary streams.
 * <p>
 * Then a call is issued to this stream, it is forwarded to the priority stream. After that, all of the secondary
 * streams are called for that operation. If any of the secondary streams throw an exception, it is <b>not</b>
 * propagated to the caller, but can be retrieved later by calling {@link #getSecondaryException()}.
 * <p>
 * If the priority stream throws an exception during the call, it is propagated immediately, and the secondary streams
 * are not called.
 * <p>
 * If the secondary streams threw an exception for a call, then they will not be called for future operations. Meaning,
 * that if a secondary stream threw an exception, all of them may be in an inconsistent state.
 * <p>
 * This class closes all the output streams it was constructed with (both primary and secondary).
 * <p>
 * This stream class is not thread safe.
 */
public class PriorityMultiplexOutputStream extends MultiplexOutputStream {
	private OutputStream priorityStream;
	private IOException secondaryException;

	/**
	 * Creates a new instance only for a priority stream.
	 * <p>
	 * Generally, there is no use for using this constructor, as it makes the class add no extra functionality to the
	 * stream.
	 * 
	 * @param priorityStream
	 *            The priority stream.
	 * @throws NullPointerException
	 *             If the priority stream is <code>null</code>.
	 */
	protected PriorityMultiplexOutputStream(OutputStream priorityStream) throws NullPointerException {
		super();
		Objects.requireNonNull(priorityStream, "priority stream");
		this.priorityStream = priorityStream;
	}

	/**
	 * Creates a new instance for a priority stream and secondary streams.
	 * <p>
	 * If the argument secondary streams contain any <code>null</code> values, the stream may throw a
	 * {@link NullPointerException} later.
	 * 
	 * @param priorityStream
	 *            The priority stream.
	 * @param secondarystreams
	 *            The secondary streams.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public PriorityMultiplexOutputStream(OutputStream priorityStream, OutputStream... secondarystreams)
			throws NullPointerException {
		super(secondarystreams);
		Objects.requireNonNull(priorityStream, "priority stream");
		this.priorityStream = priorityStream;
	}

	/**
	 * Creates a new instance for a priority stream and secondary streams.
	 * <p>
	 * If the argument secondary streams contain any <code>null</code> values, the stream may throw a
	 * {@link NullPointerException} later.
	 * 
	 * @param priorityStream
	 *            The priority stream.
	 * @param secondarystreams
	 *            The secondary streams.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public PriorityMultiplexOutputStream(OutputStream priorityStream,
			Collection<? extends OutputStream> secondarystreams) throws NullPointerException {
		super(secondarystreams);
		Objects.requireNonNull(priorityStream, "priority stream");
		this.priorityStream = priorityStream;
	}

	/**
	 * Gets the exception that was thrown by the secondary streams.
	 * 
	 * @return The secondary exception or <code>null</code> it none was thrown.
	 */
	public IOException getSecondaryException() {
		return secondaryException;
	}

	@Override
	public void write(int b) throws IOException {
		priorityStream.write(b);
		if (secondaryException == null) {
			try {
				super.write(b);
			} catch (IOException e) {
				secondaryException = e;
			}
		}
	}

	@Override
	public void write(byte[] b) throws IOException {
		priorityStream.write(b);
		if (secondaryException == null) {
			try {
				super.write(b);
			} catch (IOException e) {
				secondaryException = e;
			}
		}
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		priorityStream.write(b, off, len);
		if (secondaryException == null) {
			try {
				super.write(b, off, len);
			} catch (IOException e) {
				secondaryException = e;
			}
		}
	}

	@Override
	public void flush() throws IOException {
		priorityStream.flush();
		if (secondaryException == null) {
			try {
				super.flush();
			} catch (IOException e) {
				secondaryException = e;
			}
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * If the secondary streams throw an exception, it will be propagated.
	 */
	@Override
	public void close() throws IOException {
		IOException exc = IOUtils.closeExc(priorityStream);
		try {
			super.close();
		} catch (IOException e) {
			exc = IOUtils.addExc(exc, e);
		}
		IOUtils.throwExc(exc);
	}

}
