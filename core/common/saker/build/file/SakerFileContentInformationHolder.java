package saker.build.file;

import java.io.IOException;

import saker.build.file.content.ContentDescriptor;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;

@RMIWrap(SakerFileContentInformationHolder.HolderRMIWrapper.class)
public final class SakerFileContentInformationHolder {
	protected SakerFile file;
	protected ContentDescriptor contentDescriptor;

	public SakerFileContentInformationHolder(SakerFile file, ContentDescriptor contentDescriptor) {
		this.file = file;
		this.contentDescriptor = contentDescriptor;
	}

	public SakerFile getFile() {
		return file;
	}

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
