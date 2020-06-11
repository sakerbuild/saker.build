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
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

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

	@Override
	public Set<PosixFilePermission> getPosixFilePermissions() {
		return file.getPosixFilePermissions();
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