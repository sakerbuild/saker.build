package saker.build.file.path;

import java.io.IOException;
import java.util.Objects;

import saker.apiextract.api.PublicApi;
import saker.build.file.provider.RootFileProviderKey;
import saker.build.file.provider.SakerFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;

/**
 * Simple {@link ProviderHolderPathKey} implementation only holding the necessary attributes.
 */
@RMIWrap(SimpleProviderHolderPathKey.RMITransferWrapper.class)
@PublicApi
public final class SimpleProviderHolderPathKey extends BasicPathKey implements ProviderHolderPathKey {
	private SakerPath path;
	private RootFileProviderKey providerKey;
	private SakerFileProvider fileProvider;

	/**
	 * Creates a new path key instance by copying the membery from the argument.
	 * 
	 * @param key
	 *            The path key to base this instance on.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public SimpleProviderHolderPathKey(ProviderHolderPathKey key) throws NullPointerException {
		this(key.getPath(), key.getFileProvider(), key.getFileProviderKey());
	}

	/**
	 * Creates a new path key by replacing the path from an existing one.
	 * 
	 * @param key
	 *            The path key to base the provider attributes on.
	 * @param path
	 *            The path to use for this path key.
	 * @throws NullPointerException
	 *             If any argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the path is not absolute.
	 */
	public SimpleProviderHolderPathKey(ProviderHolderPathKey key, SakerPath path)
			throws NullPointerException, IllegalArgumentException {
		this(path, key.getFileProvider(), key.getFileProviderKey());
	}

	/**
	 * Creates a new path key for the path and provider arguments.
	 * <p>
	 * The file provider argument must be a root file provider.
	 * <p>
	 * No further checks are made on the arguments. The path may not point to a valid path on the file provider. I.e. it
	 * is not checked that the path has a valid root on the file provider.
	 * 
	 * @param fileProvider
	 *            The file provider.
	 * @param path
	 *            The path.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the file provider is not a root provider or if the path is not absolute.
	 */
	public SimpleProviderHolderPathKey(SakerFileProvider fileProvider, SakerPath path)
			throws NullPointerException, IllegalArgumentException {
		this(path, fileProvider, SakerPathFiles.requireRootFileProviderKey(fileProvider));
	}

	/**
	 * Creates a path key for the given argument members.
	 * <p>
	 * It is not checked that the given provider key corresponds to the given file provider.
	 * 
	 * @param path
	 *            The path.
	 * @param fileProvider
	 *            The file provider.
	 * @param providerKey
	 *            The key for the file provider.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the path is not absolute.
	 */
	public SimpleProviderHolderPathKey(SakerPath path, SakerFileProvider fileProvider, RootFileProviderKey providerKey)
			throws NullPointerException, IllegalArgumentException {
		SakerPathFiles.requireAbsolutePath(path);
		Objects.requireNonNull(fileProvider, "fileProvider");
		Objects.requireNonNull(providerKey, "providerKey");

		this.path = path;
		this.providerKey = providerKey;
		this.fileProvider = fileProvider;
	}

	@Override
	public SakerPath getPath() {
		return path;
	}

	@Override
	public RootFileProviderKey getFileProviderKey() {
		return providerKey;
	}

	@Override
	public SakerFileProvider getFileProvider() {
		return fileProvider;
	}

	protected static final class RMITransferWrapper implements RMIWrapper {
		private SimpleProviderHolderPathKey pathKey;

		public RMITransferWrapper() {
		}

		public RMITransferWrapper(SimpleProviderHolderPathKey pathKey) {
			this.pathKey = pathKey;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			out.writeSerializedObject(pathKey.path);
			out.writeSerializedObject(pathKey.providerKey);
			out.writeRemoteObject(pathKey.fileProvider);
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			SakerPath path = (SakerPath) in.readObject();
			RootFileProviderKey providerkey = (RootFileProviderKey) in.readObject();
			SakerFileProvider fileprovider = (SakerFileProvider) in.readObject();
			pathKey = new SimpleProviderHolderPathKey(path, fileprovider, providerkey);
		}

		@Override
		public Object getWrappedObject() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Object resolveWrapped() {
			return pathKey;
		}

	}
}
