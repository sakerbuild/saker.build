package saker.build.util.file;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.apiextract.api.PublicApi;
import saker.build.file.DirectoryVisitPredicate;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWriter;
import saker.build.thirdparty.saker.rmi.io.writer.SerializeRMIObjectWriteHandler;
import saker.build.thirdparty.saker.util.StringUtils;

/**
 * {@link DirectoryVisitPredicate} implementation that accepts files and directories that end with a given extension.
 * <p>
 * The visitor will visit the subdirectories as well. I.e. recursive visitor.
 * <p>
 * The extension will be checked on the file names in a case-insensitive manner.
 * <p>
 * The extension is not required to start with <code>'.'</code>, the class will just check that the visited file name
 * ends with the specified string in a case-insensitive manner.
 */
@RMIWriter(SerializeRMIObjectWriteHandler.class)
@PublicApi
public class IgnoreCaseExtensionDirectoryVisitPredicate implements DirectoryVisitPredicate, Externalizable {
	private static final long serialVersionUID = 1L;

	private String extension;

	/**
	 * For {@link Externalizable}.
	 */
	public IgnoreCaseExtensionDirectoryVisitPredicate() {
	}

	/**
	 * Creates a new visitor for the given extension.
	 * <p>
	 * The argument should contain the <code>'.'</code> dot prefix if necessary.
	 * 
	 * @param extension
	 *            The extension.
	 */
	public IgnoreCaseExtensionDirectoryVisitPredicate(String extension) {
		this.extension = extension;
	}

	@Override
	public boolean visitFile(String name, SakerFile file) {
		return StringUtils.endsWithIgnoreCase(name, extension);
	}

	@Override
	public boolean visitDirectory(String name, SakerDirectory directory) {
		return visitFile(name, directory);
	}

	@Override
	public DirectoryVisitPredicate directoryVisitor(String name, SakerDirectory directory) {
		return this;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeUTF(extension);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		extension = in.readUTF();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((extension == null) ? 0 : extension.hashCode());
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
		IgnoreCaseExtensionDirectoryVisitPredicate other = (IgnoreCaseExtensionDirectoryVisitPredicate) obj;
		if (extension == null) {
			if (other.extension != null)
				return false;
		} else if (!extension.equals(other.extension))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + extension + "]";
	}

}