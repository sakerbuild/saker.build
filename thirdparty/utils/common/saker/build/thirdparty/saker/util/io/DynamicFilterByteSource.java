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
import java.util.Objects;
import java.util.function.Supplier;

/**
 * {@link ByteSource} and {@link InputStream} implementation that dynamically chooses the actual output for each read
 * operation.
 * <p>
 * this class forwards its read operations to a {@link ByteSource} that is chosen using the given function at
 * construction time. If a read operation is requested, the function is called, which can choose the stream to execute
 * the read operations on. If the function returns <code>null</code>, the fallback {@link ByteSource} will be used to
 * complete the operation.
 * <p>
 * The chooser function can decide arbitrarily, but an useful implementation is that it returns a stream based on some
 * thread local state.
 * <p>
 * Closing this stream will <b>not</b> close the fallback source, or any choosen inputs by the chooser function.
 */
public class DynamicFilterByteSource extends InputStream implements ByteSource {
	private final Supplier<? extends ByteSource> sourceProvider;
	private final ByteSource fallbackSource;

	/**
	 * Creates a new instance.
	 * 
	 * @param sourceProvider
	 *            The source provider function which is called when read operations are issued.
	 * @param fallbackSource
	 *            The fallback source which is used when the source provider fails to get an input.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public DynamicFilterByteSource(Supplier<? extends ByteSource> sourceProvider, ByteSource fallbackSource)
			throws NullPointerException {
		Objects.requireNonNull(sourceProvider, "source provider");
		Objects.requireNonNull(fallbackSource, "fallback source");
		this.sourceProvider = sourceProvider;
		this.fallbackSource = fallbackSource;
	}

	/**
	 * Gets the fallback byte source that is used when the source chooser function fails to find an input.
	 * 
	 * @return The fallback source.
	 */
	public ByteSource getFallbackSource() {
		return fallbackSource;
	}

	@Override
	public int read(ByteRegion buffer) throws IOException {
		return getCurrentInput().read(buffer);
	}

	@Override
	public int read() throws IOException {
		return getCurrentInput().read();
	}

	@Override
	public int read(byte[] b) throws IOException {
		return getCurrentInput().read(ByteArrayRegion.wrap(b));
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		return getCurrentInput().read(ByteArrayRegion.wrap(b, off, len));
	}

	@Override
	public long skip(long n) throws IOException {
		return getCurrentInput().skip(n);
	}

	@Override
	public void close() throws IOException {
		// do not close the stream
	}

	private ByteSource getCurrentInput() {
		ByteSource current = sourceProvider.get();
		if (current != null) {
			return current;
		}
		return fallbackSource;
	}
}
