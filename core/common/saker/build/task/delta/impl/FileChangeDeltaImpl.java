package saker.build.task.delta.impl;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.task.delta.DeltaType;
import saker.build.task.delta.FileChangeDelta;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;

@RMIWrap(FileChangeDeltaImpl.DeltaRMIWrapper.class)
public final class FileChangeDeltaImpl implements FileChangeDelta, Externalizable {
	private static final long serialVersionUID = 1L;

	private DeltaType type;
	private Object tag;

	private SakerPath filePath;
	private transient SakerFile file;

	/**
	 * For {@link Externalizable}.
	 */
	public FileChangeDeltaImpl() {
	}

	public FileChangeDeltaImpl(DeltaType type, Object tag, SakerPath filePath, SakerFile file) {
		this.type = type;
		this.tag = tag;
		this.filePath = filePath;
		this.file = file;
	}

	@Override
	public SakerFile getFile() {
		return file;
	}

	@Override
	public DeltaType getType() {
		return type;
	}

	@Override
	public SakerPath getFilePath() {
		return filePath;
	}

	@Override
	public Object getTag() {
		return tag;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		//using only these fields in the hash code should be enough
		result = prime * result + Objects.hashCode(filePath);
		result = prime * result + type.hashCode();
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		FileChangeDeltaImpl other = (FileChangeDeltaImpl) obj;
		if (!Objects.equals(filePath, other.filePath)) {
			return false;
		}
		if (tag == null) {
			if (other.tag != null)
				return false;
		} else if (!tag.equals(other.tag))
			return false;
		if (type != other.type)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "FileChangeDeltaImpl[" + (type != null ? "type=" + type + ", " : "")
				+ (tag != null ? "tag=" + tag + ", " : "") + (filePath != null ? "filePath=" + filePath : "") + ", ("
				+ (file == null ? "file is null" : "file is present") + ")]";
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(file);
		out.writeObject(filePath);
		out.writeObject(type);
		out.writeObject(tag);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		file = (SakerFile) in.readObject();
		filePath = (SakerPath) in.readObject();
		type = (DeltaType) in.readObject();
		tag = in.readObject();
	}

	protected static final class DeltaRMIWrapper implements RMIWrapper {
		private FileChangeDelta delta;

		public DeltaRMIWrapper() {
		}

		public DeltaRMIWrapper(FileChangeDelta delta) {
			this.delta = delta;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			out.writeRemoteObject(delta.getFile());
			out.writeObject(delta.getFilePath());
			out.writeObject(delta.getType());
			out.writeSerializedObject(delta.getTag());
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			SakerFile file = (SakerFile) in.readObject();
			SakerPath filePath = (SakerPath) in.readObject();
			DeltaType type = (DeltaType) in.readObject();
			Object tag = in.readObject();
			this.delta = new FileChangeDeltaImpl(type, tag, filePath, file);
		}

		@Override
		public Object resolveWrapped() {
			return delta;
		}

		@Override
		public Object getWrappedObject() {
			throw new UnsupportedOperationException();
		}

	}

}
