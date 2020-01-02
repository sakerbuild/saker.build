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
package saker.build.util.file;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableSet;

import saker.apiextract.api.PublicApi;
import saker.build.file.DirectoryVisitPredicate;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWriter;
import saker.build.thirdparty.saker.rmi.io.writer.SerializeRMIObjectWriteHandler;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * {@link DirectoryVisitPredicate} implementation that accepts only the files which were listed during instantiation.
 * <p>
 * This class takes a set of relative paths which list the files that should be accepted by this visitor.
 * <p>
 * The visitor can accept both files and directories.
 */
@RMIWriter(SerializeRMIObjectWriteHandler.class)
@PublicApi
public class FixedDirectoryVisitPredicate implements DirectoryVisitPredicate, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath basePath;
	private NavigableSet<SakerPath> relativeFiles;

	/**
	 * For {@link Externalizable}.
	 */
	public FixedDirectoryVisitPredicate() {
	}

	/**
	 * Creates a new instance for the specified file set.
	 * 
	 * @param relativeFiles
	 *            The relative path of files to accept.
	 */
	public FixedDirectoryVisitPredicate(NavigableSet<SakerPath> relativeFiles) {
		if (SakerPathFiles.hasAbsolutePath(relativeFiles)) {
			throw new IllegalArgumentException("Files must contain only relative paths.");
		}
		this.basePath = SakerPath.EMPTY;
		this.relativeFiles = relativeFiles;
	}

	private FixedDirectoryVisitPredicate(SakerPath basePath, NavigableSet<SakerPath> relativeFiles) {
		this.basePath = basePath;
		this.relativeFiles = relativeFiles;
	}

	@Override
	public DirectoryVisitPredicate directoryVisitor(String name, SakerDirectory directory) {
		SakerPath resolved = basePath.resolve(name);
		NavigableSet<SakerPath> submap = SakerPathFiles.getPathSubSetDirectoryChildren(relativeFiles, resolved, false);
		if (submap.isEmpty()) {
			return null;
		}
		return new FixedDirectoryVisitPredicate(resolved, submap);
	}

	@Override
	public boolean visitFile(String name, SakerFile file) {
		SakerPath resolved = basePath.resolve(name);
		return relativeFiles.contains(resolved);
	}

	@Override
	public boolean visitDirectory(String name, SakerDirectory directory) {
		return visitFile(name, directory);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(basePath);
		SerialUtils.writeExternalCollection(out, relativeFiles);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		basePath = (SakerPath) in.readObject();
		relativeFiles = SerialUtils.readExternalSortedImmutableNavigableSet(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((basePath == null) ? 0 : basePath.hashCode());
		result = prime * result + ((relativeFiles == null) ? 0 : relativeFiles.hashCode());
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
		FixedDirectoryVisitPredicate other = (FixedDirectoryVisitPredicate) obj;
		if (basePath == null) {
			if (other.basePath != null)
				return false;
		} else if (!basePath.equals(other.basePath))
			return false;
		if (relativeFiles == null) {
			if (other.relativeFiles != null)
				return false;
		} else if (!relativeFiles.equals(other.relativeFiles))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[basePath=" + basePath + ", relativeFiles=" + relativeFiles + "]";
	}

}
