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
package saker.build.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import saker.apiextract.api.PublicApi;
import saker.build.exception.InvalidPathFormatException;
import saker.build.file.content.ContentDatabase;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.SakerFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.PriorityMultiplexOutputStream;
import saker.build.thirdparty.saker.util.io.StreamUtils;

/**
 * Base implementation for the {@link SakerFile} interface.
 * <p>
 * This abstract class provides the basic implementation for the functionality of {@link SakerFile} and is the basis of
 * the in-memory file hierarchy for the build system. Clients should extend this instead of implementing
 * {@link SakerFile} directly.
 * <p>
 * This class holds a reference to the enclosing parent directory, and has a backing field {@link #name} for the
 * {@linkplain #getName() file name}.
 * <p>
 * The class manages the implicit synchronization behaviour described in {@link SakerFile}.
 * <p>
 * Subclasses are required to implement the functionality required for retrieving the content descriptor, and the
 * functionality to retrieve the actual contents of the file. It is recommended that subclasses override
 * {@link #getEfficientOpeningMethods()} along with the appropriate content retrieval methods.
 */
@PublicApi
public abstract class SakerFileBase implements SakerFile {
	private static final AtomicReferenceFieldUpdater<SakerFileBase, SakerDirectoryBase> ARFU_parent = AtomicReferenceFieldUpdater
			.newUpdater(SakerFileBase.class, SakerDirectoryBase.class, "parent");

	/**
	 * Reference to the parent directory.
	 * <p>
	 * <code>null</code> if the file was not yet attached to a parent.
	 * <p>
	 * The reference to {@link RemovedMarkerSakerDirectory#INSTANCE} if the file was removed from a parent.
	 * <p>
	 * In any other case the reference to the parent directory.
	 */
	private volatile SakerDirectoryBase parent;

	/**
	 * The name of the file.
	 */
	protected final String name;

	/**
	 * Creates a new instance with the given file name.
	 * 
	 * @param name
	 *            The file name.
	 * @throws NullPointerException
	 *             If the file name is <code>null</code>.
	 * @throws InvalidPathFormatException
	 *             If the file name is invalid.
	 * @see SakerPathFiles#requireValidFileName(String)
	 */
	public SakerFileBase(String name) throws NullPointerException, InvalidPathFormatException {
		this.name = SakerPathFiles.requireValidFileName(name);
	}

	/**
	 * Creates a new instance without any name validity checking.
	 * <p>
	 * Internal API.
	 * 
	 * @param name
	 *            The file name.
	 * @param placeholder
	 *            A placeholder for calling this method.
	 */
	/* default */ SakerFileBase(String name, Void placeholder) {
		this.name = name;
	}

	@Override
	public final SakerDirectory getParent() {
		return getParentImpl();
	}

	private SakerDirectoryBase getParentImpl() {
		SakerDirectoryBase p = parent;
		if (p == RemovedMarkerSakerDirectory.INSTANCE) {
			return null;
		}
		return p;
	}

	@Override
	public final SakerPath getSakerPath() {
		return createSakerPathBuilder().build();
	}

	@Override
	public final String getName() {
		return name;
	}

	@Override
	public final void remove() {
		SakerDirectoryBase parent = getParentImpl();
		if (parent != null) {
			parent.remove(this);
		}
	}

	@Override
	public int getEfficientOpeningMethods() {
		return OPENING_METHODS_NONE;
	}

	@Override
	public final void writeTo(OutputStream os) throws IOException {
		SakerPathFiles.writeTo(this, os, getContentDatabase());
	}

	@Override
	public final ByteSource openByteSource() throws IOException {
		return ByteSource.valueOf(openInputStream());
	}

	@Override
	public final InputStream openInputStream() throws IOException {
		return SakerPathFiles.openInputStream(this, getContentDatabase());
	}

	@Override
	public final ByteArrayRegion getBytes() throws IOException {
		return SakerPathFiles.getBytes(this, getContentDatabase());
	}

	@Override
	public final String getContent() throws IOException {
		return SakerPathFiles.getContent(this, getContentDatabase());
	}

	@Override
	public final void synchronize() throws IOException {
		synchronizeInternal();
	}

	@Override
	public final void synchronize(ProviderHolderPathKey pathkey) throws IOException {
		synchronizeInternal(pathkey);
	}

	@Override
	public final String toString() {
		return getClass().getName() + "[" + getSakerPath() + "]";
	}

	@Override
	public void synchronizeImpl(ProviderHolderPathKey pathkey) throws IOException {
		Objects.requireNonNull(pathkey, "path key");
		SakerFileProvider fp = pathkey.getFileProvider();
		SakerPath path = pathkey.getPath();
		fp.ensureWriteRequest(path, FileEntry.TYPE_FILE, SakerFileProvider.OPERATION_FLAG_DELETE_INTERMEDIATE_FILES);

		int effopenmethods = getEfficientOpeningMethods();

		if (((effopenmethods & OPENING_METHOD_GETBYTES) == OPENING_METHOD_GETBYTES)) {
			fp.setFileBytes(path, callGetBytesImpl());
		} else if (((effopenmethods & OPENING_METHOD_GETCONTENTS) == OPENING_METHOD_GETCONTENTS)) {
			fp.setFileBytes(path, ByteArrayRegion.wrap(callGetContentsImpl().getBytes(StandardCharsets.UTF_8)));
		} else if (((effopenmethods & OPENING_METHOD_OPENINPUTSTREAM) == OPENING_METHOD_OPENINPUTSTREAM)) {
			try (InputStream is = callOpenInputStreamImpl()) {
				fp.writeToFile(ByteSource.valueOf(is), path);
			}
		} else {
			try (OutputStream os = ByteSink.toOutputStream(fp.openOutput(path))) {
				writeToStreamImpl(os);
			}
		}
	}

	@Override
	public boolean synchronizeImpl(ProviderHolderPathKey pathkey, ByteSink additionalwritestream)
			throws SecondaryStreamException, IOException {
		Objects.requireNonNull(pathkey, "path key");
		Objects.requireNonNull(additionalwritestream, "stream");

		SakerFileProvider fp = pathkey.getFileProvider();
		SakerPath path = pathkey.getPath();
		fp.ensureWriteRequest(path, FileEntry.TYPE_FILE, SakerFileProvider.OPERATION_FLAG_DELETE_INTERMEDIATE_FILES);

		int effopenmethods = getEfficientOpeningMethods();

		if (((effopenmethods & OPENING_METHOD_GETBYTES) == OPENING_METHOD_GETBYTES)) {
			ByteArrayRegion bytes = callGetBytesImpl();
			fp.setFileBytes(path, bytes);
			additionalwritestream.write(bytes);
		} else if (((effopenmethods & OPENING_METHOD_GETCONTENTS) == OPENING_METHOD_GETCONTENTS)) {
			ByteArrayRegion bytes = ByteArrayRegion.wrap(callGetContentsImpl().getBytes(StandardCharsets.UTF_8));
			fp.setFileBytes(path, bytes);
			additionalwritestream.write(bytes);
		} else if (((effopenmethods & OPENING_METHOD_OPENINPUTSTREAM) == OPENING_METHOD_OPENINPUTSTREAM)) {
			try (InputStream is = callOpenInputStreamImpl();
					ReadWritingSynchronizingInputStream readingin = new ReadWritingSynchronizingInputStream(is,
							additionalwritestream)) {
				fp.writeToFile(ByteSource.valueOf(readingin), path);
				IOException sec = readingin.getSecondaryException();
				if (sec != null) {
					throw new SecondaryStreamException(sec);
				}

			}
		} else {
			try (OutputStream os = ByteSink.toOutputStream(fp.openOutput(path));
					PriorityMultiplexOutputStream multiplexos = new PriorityMultiplexOutputStream(os,
							StreamUtils.closeProtectedOutputStream(ByteSink.toOutputStream(additionalwritestream)))) {
				writeToStreamImpl(multiplexos);
				IOException sec = multiplexos.getSecondaryException();
				if (sec != null) {
					throw new SecondaryStreamException(sec);
				}
			}
		}
		return true;
	}

	/* default */ SakerPath.Builder createSakerPathBuilder() {
		SakerDirectoryBase parent = this.parent;
		if (parent == null) {
			return SakerPath.builder().append(name);
		}
		return parent.createSakerPathBuilder().append(name);
	}

	/* default */ ContentDatabase getContentDatabase() {
		SakerDirectoryBase parent = this.parent;
		if (parent == null) {
			return null;
		}
		return parent.getContentDatabase();
	}

	/* default */ void synchronizeInternal(ProviderHolderPathKey pathkey) throws IOException {
		SakerPathFiles.synchronizeFile(this, pathkey, getContentDatabase());
	}

	/* default */ void synchronizeInternal() throws IOException {
		SakerPathFiles.synchronizeFile(this, getContentDatabase());
	}

	/* default */ static boolean internal_casParent(SakerFileBase file, SakerDirectoryBase expect,
			SakerDirectoryBase update) {
		return ARFU_parent.compareAndSet(file, expect, update);
	}

	/* default */ static void internal_setParent(SakerFileBase file, SakerDirectoryBase parent) {
		file.parent = parent;
	}

	/* default */ static SakerDirectoryBase internal_getParent(SakerFileBase file) {
		return file.parent;
	}

	private InputStream callOpenInputStreamImpl() throws IOException {
		InputStream result = openInputStreamImpl();
		if (result == null) {
			throw new IOException("Failed to open input stream, subclass returned null.");
		}
		return result;
	}

	private ByteArrayRegion callGetBytesImpl() throws IOException {
		ByteArrayRegion result = getBytesImpl();
		if (result == null) {
			throw new IOException("Failed to get bytes, subclass returned null.");
		}
		return result;
	}

	private String callGetContentsImpl() throws IOException {
		String result = getContentImpl();
		if (result == null) {
			throw new IOException("Failed to get contents, subclass returned null.");
		}
		return result;
	}

	private static class ReadWritingSynchronizingInputStream extends InputStream {
		private InputStream source;
		private ByteSink sink;
		private IOException secondaryException;

		public ReadWritingSynchronizingInputStream(InputStream source, ByteSink sink) {
			this.source = source;
			this.sink = sink;
		}

		public IOException getSecondaryException() {
			return secondaryException;
		}

		@Override
		public int read() throws IOException {
			int r = source.read();
			if (r >= 0) {
				if (secondaryException == null) {
					try {
						sink.write(r);
					} catch (IOException e) {
						secondaryException = e;
					}
				}
			}
			return r;
		}

		@Override
		public int read(byte[] b) throws IOException {
			return this.read(b, 0, b.length);
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int read = source.read(b, off, len);
			if (read > 0) {
				if (secondaryException == null) {
					try {
						sink.write(ByteArrayRegion.wrap(b, off, read));
					} catch (IOException e) {
						secondaryException = e;
					}
				}
			}
			return read;
		}

		//skipping is by reading so that is fine
	}
}
