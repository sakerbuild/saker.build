package saker.build.file.provider;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.file.path.SakerPath;

class DirectoryMountProviderKey implements FileProviderKey, Externalizable {
	private static final long serialVersionUID = 1L;

	private FileProviderKey subjectKey;
	private String rootName;
	private SakerPath directoryPath;

	/**
	 * For {@link Externalizable}.
	 */
	public DirectoryMountProviderKey() {
	}

	public DirectoryMountProviderKey(FileProviderKey subjectKey, String rootName, SakerPath directoryPath) {
		this.subjectKey = subjectKey;
		this.rootName = rootName;
		this.directoryPath = directoryPath;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((directoryPath == null) ? 0 : directoryPath.hashCode());
		result = prime * result + ((rootName == null) ? 0 : rootName.hashCode());
		result = prime * result + ((subjectKey == null) ? 0 : subjectKey.hashCode());
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
		DirectoryMountProviderKey other = (DirectoryMountProviderKey) obj;
		if (directoryPath == null) {
			if (other.directoryPath != null)
				return false;
		} else if (!directoryPath.equals(other.directoryPath))
			return false;
		if (rootName == null) {
			if (other.rootName != null)
				return false;
		} else if (!rootName.equals(other.rootName))
			return false;
		if (subjectKey == null) {
			if (other.subjectKey != null)
				return false;
		} else if (!subjectKey.equals(other.subjectKey))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getName() + "-" + subjectKey.toString() + "-" + rootName + "-" + directoryPath;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(subjectKey);
		out.writeUTF(rootName);
		out.writeObject(directoryPath);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		subjectKey = (FileProviderKey) in.readObject();
		rootName = in.readUTF();
		directoryPath = (SakerPath) in.readObject();
	}

}