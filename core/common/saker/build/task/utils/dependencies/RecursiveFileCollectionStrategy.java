package saker.build.task.utils.dependencies;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.NavigableMap;

import saker.build.file.DirectoryVisitPredicate;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.ExecutionDirectoryContext;
import saker.build.task.TaskDirectoryContext;
import saker.build.task.dependencies.FileCollectionStrategy;

/**
 * File collection strategy for collecting all files (and directories) recursively for a given directory.
 * <p>
 * The specified directory will be part of the result file collection.
 * 
 * @see #create
 */
public class RecursiveFileCollectionStrategy implements FileCollectionStrategy, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath directory;

	/**
	 * For {@link Externalizable}.
	 */
	public RecursiveFileCollectionStrategy() {
	}

	private RecursiveFileCollectionStrategy(SakerPath directory) {
		this.directory = directory;
	}

	/**
	 * Creates a file collection strategy using this strategy.
	 * 
	 * @param directory
	 *            The directory to collect the children recursively of.
	 * @return The created collection strategy.
	 */
	public static FileCollectionStrategy create(SakerPath directory) {
		return new RecursiveFileCollectionStrategy(directory);
	}

	@Override
	public NavigableMap<SakerPath, SakerFile> collectFiles(ExecutionDirectoryContext executiondirectorycontext,
			TaskDirectoryContext directorycontext) {
		SakerDirectory workingdir = directorycontext.getTaskWorkingDirectory();
		SakerDirectory dir = getActualDirectory(executiondirectorycontext, workingdir, this.directory);
		if (dir == null) {
			return Collections.emptyNavigableMap();
		}
		SakerPath dirbasepath = dir.getSakerPath();
		NavigableMap<SakerPath, SakerFile> result = dir.getFilesRecursiveByPath(dirbasepath,
				DirectoryVisitPredicate.everything());
		result.putIfAbsent(dirbasepath, dir);
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
		out.writeObject(directory);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		directory = (SakerPath) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((directory == null) ? 0 : directory.hashCode());
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
		RecursiveFileCollectionStrategy other = (RecursiveFileCollectionStrategy) obj;
		if (directory == null) {
			if (other.directory != null)
				return false;
		} else if (!directory.equals(other.directory))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [" + directory + "]";
	}

}
