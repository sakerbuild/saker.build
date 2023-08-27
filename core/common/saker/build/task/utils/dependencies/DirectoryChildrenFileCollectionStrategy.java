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
import saker.build.util.data.annotation.ValueType;

/**
 * File collection strategy for collecting children of a directory.
 * 
 * @see #create(SakerPath)
 */
@ValueType
public class DirectoryChildrenFileCollectionStrategy implements FileCollectionStrategy, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath directory;

	/**
	 * For {@link Externalizable}.
	 */
	public DirectoryChildrenFileCollectionStrategy() {
	}

	private DirectoryChildrenFileCollectionStrategy(SakerPath directory) {
		this.directory = directory;
	}

	/**
	 * Creates a file collection strategy using this strategy.
	 * 
	 * @param directory
	 *            The directory to collect the children of.
	 * @return The created collection strategy.
	 */
	public static FileCollectionStrategy create(SakerPath directory) {
		return new DirectoryChildrenFileCollectionStrategy(directory);
	}

	@Override
	public NavigableMap<SakerPath, SakerFile> collectFiles(ExecutionDirectoryContext executiondirectorycontext,
			TaskDirectoryContext directorycontext) {
		SakerDirectory workingdir = directorycontext.getTaskWorkingDirectory();
		SakerDirectory dir = getActualDirectory(executiondirectorycontext, workingdir, this.directory);
		if (dir == null) {
			return Collections.emptyNavigableMap();
		}
		NavigableMap<SakerPath, SakerFile> result = SakerPathFiles.getDirectoryChildrenByPath(dir);
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
		DirectoryChildrenFileCollectionStrategy other = (DirectoryChildrenFileCollectionStrategy) obj;
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
