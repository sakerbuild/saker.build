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
package saker.build.runtime.execution;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;

public interface InternalExecutionContext {

	@RMISerialize
	public FilePathContents internalGetFilePathContents(SakerFile file);

	public static class FilePathContents implements Externalizable {
		private static final long serialVersionUID = 1L;

		private SakerPath path;
		private ContentDescriptor contents;

		/**
		 * For {@link Externalizable}.
		 */
		public FilePathContents() {
		}

		public FilePathContents(SakerPath path, ContentDescriptor contents) {
			this.path = path;
			this.contents = contents;
		}

		public SakerPath getPath() {
			return path;
		}

		public ContentDescriptor getContents() {
			return contents;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(path);
			out.writeObject(contents);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			path = (SakerPath) in.readObject();
			contents = (ContentDescriptor) in.readObject();
		}
	}
}
