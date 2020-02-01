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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Objects;

import saker.apiextract.api.PublicApi;

/**
 * Simple data class for holding an input-output stream pair.
 * <p>
 * This class can be useful when one wants to pass a both ends of a communication channel as a parameter or return type.
 * E.g. the streams of a {@link Socket}.
 * <p>
 * The class does not manage the lifecycle of the streams, users need to call <code>close()</code> on the if necessary.
 */
@PublicApi
public final class StreamPair {
	private final InputStream input;
	private final OutputStream output;

	/**
	 * Creates a new instance for the specified input-output streams.
	 * 
	 * @param input
	 *            The input stream.
	 * @param output
	 *            The output stream.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public StreamPair(InputStream input, OutputStream output) throws NullPointerException {
		Objects.requireNonNull(input, "input");
		Objects.requireNonNull(output, "output");
		this.input = input;
		this.output = output;
	}

	/**
	 * Creates a new stream pair based on a socket.
	 * <p>
	 * The {@link Socket#getInputStream()} and {@link Socket#getOutputStream()} methods will be used to retrieve the
	 * streams.
	 * 
	 * @param s
	 *            The socket.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the socket is <code>null</code>.
	 */
	public StreamPair(Socket s) throws IOException, NullPointerException {
		this(Objects.requireNonNull(s, "socket").getInputStream(), s.getOutputStream());
	}

	/**
	 * Gets the input stream.
	 * 
	 * @return The input.
	 */
	public InputStream getInput() {
		return input;
	}

	/**
	 * Gets the output stream.
	 * 
	 * @return The output.
	 */
	public OutputStream getOutput() {
		return output;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[input=" + input + ", output=" + output + "]";
	}

}
