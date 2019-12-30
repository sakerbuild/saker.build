package saker.build.task.utils.dependencies;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.NavigableMap;
import java.util.Objects;

import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.ExecutionDirectoryContext;
import saker.build.task.TaskDirectoryContext;
import saker.build.task.dependencies.FileCollectionStrategy;
import saker.build.thirdparty.saker.util.ImmutableUtils;

/**
 * File collection strategy for collecting a single file (or directory) at a given path.
 * 
 * @see #create
 */
public class PathFileCollectionStrategy implements FileCollectionStrategy, Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * Creates a file collection strategy using this strategy.
	 * 
	 * @param path
	 *            The path of the file to collect.
	 * @return The created collection strategy.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static FileCollectionStrategy create(SakerPath path) throws NullPointerException {
		Objects.requireNonNull(path, "path");
		return new PathFileCollectionStrategy(path);
	}

	private SakerPath path;

	/**
	 * For {@link Externalizable}.
	 */
	public PathFileCollectionStrategy() {
	}

	private PathFileCollectionStrategy(SakerPath path) {
		this.path = path;
	}

	@Override
	public NavigableMap<SakerPath, SakerFile> collectFiles(ExecutionDirectoryContext executiondirectorycontext,
			TaskDirectoryContext directorycontext) {
		SakerDirectory workingdir = directorycontext.getTaskWorkingDirectory();
		if (workingdir == null) {
			if (path.isRelative()) {
				return Collections.emptyNavigableMap();
			}
		}
		SakerFile file = SakerPathFiles.resolveAtPath(executiondirectorycontext, workingdir, path);
		if (file == null) {
			return Collections.emptyNavigableMap();
		}
		SakerPath fileabspath = file.getSakerPath();
		return ImmutableUtils.singletonNavigableMap(fileabspath, file);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(path);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		path = (SakerPath) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((path == null) ? 0 : path.hashCode());
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
		PathFileCollectionStrategy other = (PathFileCollectionStrategy) obj;
		if (path == null) {
			if (other.path != null)
				return false;
		} else if (!path.equals(other.path))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [" + path + "]";
	}

}
