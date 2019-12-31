package saker.build.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

import saker.apiextract.api.PublicApi;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.content.EmptyContentDescriptor;
import saker.build.file.content.HashContentDescriptor;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;

/**
 * {@link SakerFile} implementation with contents that is backed by a byte array.
 * <p>
 * The file takes a byte array during construction, and will return that when any content requesting methods are called.
 * All opening methods of this file class is efficient.
 * <p>
 * <b>Warning:</b> This class doesn't make an internal copy of the byte array passed to it during construction.
 * Modifying the contents of the underlying array can result in improper operation, and broken incremental builds.
 */
@PublicApi
public class ByteArraySakerFile extends SakerFileBase {
	private ByteArrayRegion bytes;
	private volatile ContentDescriptor contentDescriptor = null;

	/**
	 * Creates a new instance with the given name and no byte contents.
	 * <p>
	 * Generally, this constructor is not useful at all, because the created file will have no byte contents at all.
	 * 
	 * @param name
	 *            The name of the file.
	 * @see SakerFileBase#SakerFileBase(String)
	 */
	public ByteArraySakerFile(String name) {
		super(name);
		this.bytes = ByteArrayRegion.EMPTY;
		this.contentDescriptor = EmptyContentDescriptor.INSTANCE;
	}

	/**
	 * Creates a new instance with the given name and byte array.
	 * 
	 * @param name
	 *            The name of the file.
	 * @param data
	 *            The byte contents of the file.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @see SakerFileBase#SakerFileBase(String)
	 */
	public ByteArraySakerFile(String name, byte[] data) throws NullPointerException {
		this(name, ByteArrayRegion.wrap(data));
	}

	/**
	 * Creates a new instance with the given name and range of byte array.
	 * 
	 * @param name
	 *            The name of the file.
	 * @param data
	 *            The byte array for the file.
	 * @param start
	 *            The starting offset of the byte array range. (inclusive)
	 * @param end
	 *            The ending offset of the range. (exclusive)
	 * @throws NullPointerException
	 *             If the name or array is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the specified range is out of bounds.
	 */
	public ByteArraySakerFile(String name, byte[] data, int start, int end)
			throws NullPointerException, IllegalArgumentException {
		this(name, ByteArrayRegion.wrap(data, start, end - start));
	}

	/**
	 * Creates a new instance with the given name and byte array region.
	 * 
	 * @param name
	 *            The name of the file.
	 * @param data
	 *            The byte contents of the file.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @see SakerFileBase#SakerFileBase(String)
	 */
	public ByteArraySakerFile(String name, ByteArrayRegion data) throws NullPointerException {
		super(name);
		Objects.requireNonNull(data, "data");
		this.bytes = data;
	}

	@Override
	public int getEfficientOpeningMethods() {
		return OPENING_METHODS_ALL;
	}

	@Override
	public ContentDescriptor getContentDescriptor() {
		ContentDescriptor cd = contentDescriptor;
		if (cd != null) {
			return cd;
		}
		synchronized (this) {
			cd = contentDescriptor;
			if (cd != null) {
				return cd;
			}
			cd = HashContentDescriptor.hash(getBytesImpl());
			contentDescriptor = cd;
			return cd;
		}
	}

	@Override
	public ByteArrayRegion getBytesImpl() {
		return bytes;
	}

	@Override
	public String getContentImpl() throws IOException {
		return getBytesImpl().toString();
	}

	@Override
	public InputStream openInputStreamImpl() throws IOException {
		//XXX should we somehow disallow downcasting the returned stream to make sure the byte array is not modifiable
		return new UnsyncByteArrayInputStream(bytes);
	}

	@Override
	public void writeToStreamImpl(OutputStream os) throws IOException {
		bytes.writeTo(os);
	}
}
