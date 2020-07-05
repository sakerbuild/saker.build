package saker.build.file;

import java.io.IOException;
import java.util.Objects;

import saker.build.file.content.ContentDescriptor;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;

/**
 * A data class holding a {@link SakerFile} instance and optionally associated content information.
 * <p>
 * The class can be used to hold data about a {@link SakerFile} and its related content information.
 * 
 * @since saker.build 0.8.15
 */
@RMIWrap(SakerFileContentInformationHolder.HolderRMIWrapper.class)
public final class SakerFileContentInformationHolder {
	protected SakerFile file;
	protected ContentDescriptor contentDescriptor;

	/**
	 * Creates a new instance.
	 * 
	 * @param file
	 *            The file.
	 * @param contentDescriptor
	 *            The associated content descriptor. May be <code>null</code>.
	 * @throws NullPointerException
	 *             If the file is <code>null</code>.
	 */
	public SakerFileContentInformationHolder(SakerFile file, ContentDescriptor contentDescriptor)
			throws NullPointerException {
		Objects.requireNonNull(file, "file");
		this.file = file;
		this.contentDescriptor = contentDescriptor;
	}

	/**
	 * Gets the file.
	 * 
	 * @return The file.
	 */
	public SakerFile getFile() {
		return file;
	}

	/**
	 * Gets the content descriptor associated to the file.
	 * 
	 * @return The content descriptor or <code>null</code> if not contained in this instance.
	 */
	public ContentDescriptor getContentDescriptor() {
		return contentDescriptor;
	}

	public static final class HolderRMIWrapper implements RMIWrapper {
		private SakerFileContentInformationHolder holder;

		public HolderRMIWrapper() {
		}

		public HolderRMIWrapper(SakerFileContentInformationHolder holder) {
			this.holder = holder;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			out.writeRemoteObject(holder.file);
			out.writeSerializedObject(holder.contentDescriptor);
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			SakerFile f = (SakerFile) in.readObject();
			ContentDescriptor cd = (ContentDescriptor) in.readObject();
			holder = new SakerFileContentInformationHolder(f, cd);
		}

		@Override
		public Object resolveWrapped() {
			return holder;
		}

		@Override
		public Object getWrappedObject() {
			throw new UnsupportedOperationException();
		}
	}
}
