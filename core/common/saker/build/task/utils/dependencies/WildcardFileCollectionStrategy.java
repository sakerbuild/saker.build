package saker.build.task.utils.dependencies;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableMap;

import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.file.path.WildcardPath.ReducedWildcardPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.ExecutionDirectoryContext;
import saker.build.task.TaskDirectoryContext;
import saker.build.task.dependencies.FileCollectionStrategy;

/**
 * File collection strategy for collecting files based on a wildcard.
 * 
 * @see #create
 */
public class WildcardFileCollectionStrategy implements FileCollectionStrategy, Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * The base directory of the wildcard resolution.
	 * <p>
	 * Might be <code>null</code>.
	 */
	protected SakerPath directory;
	/**
	 * The wildcard to match the files.
	 */
	protected WildcardPath wildcard;

	/**
	 * Creates a file collection strategy using this strategy.
	 * 
	 * @param wildcard
	 *            The wildcard to use.
	 * @return The created collection strategy.
	 */
	public static FileCollectionStrategy create(WildcardPath wildcard) {
		return create((SakerPath) null, wildcard);
	}

	/**
	 * Creates a file collection strategy using this strategy.
	 * 
	 * @param relativeDirectory
	 *            A relative directory to prepend the wildcard with. May be <code>null</code>.
	 * @param wildcard
	 *            The wildcard to use.
	 * @return The created collection strategy.
	 */
	public static FileCollectionStrategy create(SakerPath relativeDirectory, WildcardPath wildcard) {
		ReducedWildcardPath reduced = wildcard.reduce();
		SakerPath reducedfile = reduced.getFile();
		WildcardPath reducedwc = reduced.getWildcard();
		if (reducedwc == null) {
			if (relativeDirectory != null) {
				reducedfile = relativeDirectory.tryResolve(reducedfile);
			}
			return PathFileCollectionStrategy.create(reducedfile);
		}
		if (reducedfile != null) {
			if (relativeDirectory == null) {
				relativeDirectory = reducedfile;
			} else {
				relativeDirectory = relativeDirectory.tryResolve(reducedfile);
			}
		}
		if (reducedwc.isRecursiveAllFilesPath()) {
			return RecursiveFileCollectionStrategy.create(relativeDirectory);
		}
		return new WildcardFileCollectionStrategy(relativeDirectory, reducedwc);
	}

	/**
	 * For {@link Externalizable}.
	 */
	public WildcardFileCollectionStrategy() {
	}

	private WildcardFileCollectionStrategy(SakerPath directory, WildcardPath wildcard) {
		this.directory = directory;
		this.wildcard = wildcard;
	}

	/**
	 * Gets the wildcard of this file collection strategy.
	 * 
	 * @return The wildcard.
	 */
	public WildcardPath getWildcard() {
		return wildcard;
	}

	@Override
	public NavigableMap<SakerPath, SakerFile> collectFiles(ExecutionDirectoryContext executiondirectorycontext,
			TaskDirectoryContext directorycontext) {
		SakerDirectory workingdir = directorycontext.getTaskWorkingDirectory();
		SakerDirectory reldir = getActualDirectory(executiondirectorycontext, workingdir, this.directory);
		NavigableMap<SakerPath, SakerFile> result = wildcard.getFiles(executiondirectorycontext, reldir);
		return result;
	}

	private static SakerDirectory getActualDirectory(ExecutionDirectoryContext executiondirectorycontext,
			SakerDirectory workingdir, SakerPath directory) {
		SakerDirectory dir;
		if (directory == null) {
			dir = workingdir;
		} else if (directory.isAbsolute()) {
			dir = SakerPathFiles.resolveDirectoryAtAbsolutePath(executiondirectorycontext, directory);
		} else {
			if (workingdir == null) {
				dir = null;
			} else {
				dir = SakerPathFiles.resolveDirectoryAtRelativePath(workingdir, directory);
			}
		}
		return dir;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(wildcard);
		out.writeObject(directory);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		wildcard = (WildcardPath) in.readObject();
		directory = (SakerPath) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((directory == null) ? 0 : directory.hashCode());
		result = prime * result + ((wildcard == null) ? 0 : wildcard.hashCode());
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
		WildcardFileCollectionStrategy other = (WildcardFileCollectionStrategy) obj;
		if (directory == null) {
			if (other.directory != null)
				return false;
		} else if (!directory.equals(other.directory))
			return false;
		if (wildcard == null) {
			if (other.wildcard != null)
				return false;
		} else if (!wildcard.equals(other.wildcard))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (directory != null ? "directory=" + directory + ", " : "")
				+ (wildcard != null ? "wildcard=" + wildcard : "") + "]";
	}

}
