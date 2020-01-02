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
package saker.build.task.utils.dependencies;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.NavigableMap;

import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.ExecutionDirectoryContext;
import saker.build.task.TaskDirectoryContext;
import saker.build.task.dependencies.FileCollectionStrategy;
import saker.build.util.file.IgnoreCaseExtensionDirectoryVisitPredicate;

/**
 * File collection strategy for collection all files (and directories) recursively under a given directory with the
 * given extension.
 * <p>
 * The extension is compared in a case-insensitive manner. The class doesn't add the <code>'.'</code> dot character
 * before the extension phrase during construction. This class simply checks if file names end with the given phrase in
 * a case-insensitive manner.
 * 
 * @see #create
 * @see IgnoreCaseExtensionDirectoryVisitPredicate
 */
public class RecursiveIgnoreCaseExtensionFileCollectionStrategy implements FileCollectionStrategy, Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * The extension to check if the file names end with.
	 */
	protected String extension;
	/**
	 * The directory under which to collect the children.
	 */
	protected SakerPath directory;

	/**
	 * For {@link Externalizable}.
	 */
	public RecursiveIgnoreCaseExtensionFileCollectionStrategy() {
	}

	private RecursiveIgnoreCaseExtensionFileCollectionStrategy(String extension, SakerPath directory) {
		this.extension = extension;
		this.directory = directory;
	}

	/**
	 * Creates a file collection strategy using this strategy.
	 * 
	 * @param directory
	 *            The directory to collect the children recursively of.
	 * @param extension
	 *            The required extension for the collected files. The extension should be prepended by the
	 *            <code>'.'</code> character if necessary.
	 * @return The created collection strategy.
	 */
	public static FileCollectionStrategy create(SakerPath directory, String extension) {
		return new RecursiveIgnoreCaseExtensionFileCollectionStrategy(extension, directory);
	}

	/**
	 * Creates a file collection strategy using this strategy.
	 * <p>
	 * The task working directory is used as a base path.
	 * 
	 * @param extension
	 *            The required extension for the collected files. The extension should be prepended by the
	 *            <code>'.'</code> character if necessary.
	 * @return The created collection strategy.
	 */
	public static FileCollectionStrategy create(String extension) {
		return create(null, extension);
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
		return dir.getFilesRecursiveByPath(dirbasepath, new IgnoreCaseExtensionDirectoryVisitPredicate(extension));
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
		out.writeUTF(extension);
		out.writeObject(directory);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		extension = in.readUTF();
		directory = (SakerPath) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((directory == null) ? 0 : directory.hashCode());
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
		RecursiveIgnoreCaseExtensionFileCollectionStrategy other = (RecursiveIgnoreCaseExtensionFileCollectionStrategy) obj;
		if (directory == null) {
			if (other.directory != null)
				return false;
		} else if (!directory.equals(other.directory))
			return false;
		if (extension == null) {
			if (other.extension != null)
				return false;
		} else if (!extension.equals(other.extension))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (extension != null ? "extension=" + extension + ", " : "")
				+ (directory != null ? "directory=" + directory : "") + "]";
	}

}
