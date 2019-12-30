package saker.build.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

import saker.apiextract.api.PublicApi;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;

/**
 * {@link SakerFile} implementation that delegates its calls to another file.
 * <p>
 * This file will have the same contents, content descriptor, as the subject file. This class can be basically used to
 * construct a link to another file.
 * <p>
 * The delegate file cannot be initialized with a directory as a subject.
 */
@PublicApi
public class DelegateSakerFile extends SakerFileBase {
	private final SakerFile file;

	/**
	 * Creates a new delegate file initialized with the specified name and subject.
	 * 
	 * @param name
	 *            The name to set for the created file.
	 * @param file
	 *            The subject file.
	 * @throws IllegalArgumentException
	 *             If the file is a directory.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @see SakerFileBase#SakerFileBase(String)
	 */
	public DelegateSakerFile(String name, SakerFile file) throws IllegalArgumentException, NullPointerException {
		super(name);
		Objects.requireNonNull(file, "file");
		if (file instanceof SakerDirectory) {
			throw new IllegalArgumentException("File delegate cannot be a directory.");
		}
		this.file = file;
	}

	/**
	 * Creates a new delegate file initialized with the given subject.
	 * <p>
	 * The created file will have the same name as the argument.
	 * 
	 * @param file
	 *            The subject file.
	 * @throws IllegalArgumentException
	 *             If the file is a directory.
	 * @throws NullPointerException
	 *             If the file is <code>null</code>.
	 * @see SakerFileBase#SakerFileBase(String)
	 */
	public DelegateSakerFile(SakerFile file) throws IllegalArgumentException, NullPointerException {
		this(file.getName(), file);
	}

	@Override
	public int getEfficientOpeningMethods() {
		return file.getEfficientOpeningMethods();
	}

	@Override
	public void writeToStreamImpl(OutputStream os) throws IOException {
		file.writeToStreamImpl(os);
	}

	@Override
	public InputStream openInputStreamImpl() throws IOException {
		return file.openInputStreamImpl();
	}

	@Override
	public ByteArrayRegion getBytesImpl() throws IOException {
		return file.getBytesImpl();
	}

	@Override
	public String getContentImpl() throws IOException {
		return file.getContentImpl();
	}

	@Override
	public ByteSource openByteSourceImpl() throws IOException {
		return file.openByteSourceImpl();
	}

	@Override
	public ContentDescriptor getContentDescriptor() {
		return file.getContentDescriptor();
	}

	@Override
	public boolean synchronizeImpl(ProviderHolderPathKey pathkey, ByteSink additionalwritestream)
			throws SecondaryStreamException, IOException {
		return file.synchronizeImpl(pathkey, additionalwritestream);
	}

	@Override
	public void synchronizeImpl(ProviderHolderPathKey pathkey) throws IOException {
		file.synchronizeImpl(pathkey);
	}

}
