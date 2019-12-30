package saker.build.task;

import java.io.IOException;
import java.util.Map.Entry;

import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.SakerPath;
import saker.build.scripting.ScriptPosition;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;

public interface InternalTaskContext {
	@RMICacheResult
	@RMISerialize
	public Entry<SakerPath, ScriptPosition> internalGetOriginatingBuildFile();

	public void internalPrintlnVariables(String line);

	public void internalPrintlnVerboseVariables(String line);

	public PathSakerFileContents internalGetPathSakerFileContents(SakerPath path);

	@RMIWrap(PathSakerFileContents.PathSakerFileContentsRMIWrapper.class)
	public static class PathSakerFileContents {
		protected SakerFile file;
		protected SakerPath path;
		protected ContentDescriptor contents;

		public PathSakerFileContents(SakerFile file, SakerPath path, ContentDescriptor contents) {
			this.file = file;
			this.path = path;
			this.contents = contents;
		}

		public SakerFile getFile() {
			return file;
		}

		public SakerPath getPath() {
			return path;
		}

		public ContentDescriptor getContents() {
			return contents;
		}

		protected static class PathSakerFileContentsRMIWrapper implements RMIWrapper {
			private PathSakerFileContents contents;

			public PathSakerFileContentsRMIWrapper() {
			}

			public PathSakerFileContentsRMIWrapper(PathSakerFileContents contents) {
				this.contents = contents;
			}

			@Override
			public void writeWrapped(RMIObjectOutput out) throws IOException {
				out.writeRemoteObject(contents.file);
				out.writeObject(contents.path);
				out.writeSerializedObject(contents.contents);
			}

			@Override
			public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
				SakerFile file = (SakerFile) in.readObject();
				SakerPath path = (SakerPath) in.readObject();
				ContentDescriptor contents = (ContentDescriptor) in.readObject();
				this.contents = new PathSakerFileContents(file, path, contents);
			}

			@Override
			public Object resolveWrapped() {
				return contents;
			}

			@Override
			public Object getWrappedObject() {
				throw new UnsupportedOperationException();
			}
		}
	}
}
