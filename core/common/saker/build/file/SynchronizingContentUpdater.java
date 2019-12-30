package saker.build.file;

import java.io.IOException;

import saker.build.file.content.ContentDatabase.ContentUpdater;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.util.io.ByteSink;

@RMIWrap(SynchronizingContentUpdater.UpdaterRMIWrapper.class)
public final class SynchronizingContentUpdater implements ContentUpdater {
	private SakerFile file;
	private ProviderHolderPathKey pathKey;

	public SynchronizingContentUpdater(SakerFile file, ProviderHolderPathKey pathKey) {
		this.file = file;
		this.pathKey = pathKey;
	}

	@Override
	public void update() throws IOException {
		file.synchronizeImpl(pathKey);
	}

	@Override
	public boolean updateWithStream(ByteSink os) throws IOException, SecondaryStreamException {
		return file.synchronizeImpl(pathKey, os);
	}

	protected static final class UpdaterRMIWrapper implements RMIWrapper {
		private SynchronizingContentUpdater updater;

		public UpdaterRMIWrapper() {
		}

		public UpdaterRMIWrapper(SynchronizingContentUpdater updater) {
			this.updater = updater;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			out.writeRemoteObject(updater.file);
			out.writeObject(updater.pathKey);
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			SakerFile file = (SakerFile) in.readObject();
			ProviderHolderPathKey pathkey = (ProviderHolderPathKey) in.readObject();
			updater = new SynchronizingContentUpdater(file, pathkey);
		}

		@Override
		public Object getWrappedObject() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Object resolveWrapped() {
			return updater;
		}

	}
}