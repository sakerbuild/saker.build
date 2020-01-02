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
package saker.build.file;

import java.util.Collections;
import java.util.NavigableSet;

import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.util.rmi.wrap.RMITreeSetStringElementWrapper;

/**
 * Predicate interface that controls how the specific functions should handle files for {@link SakerDirectory}.
 * <p>
 * This interface is mostly used to query children and synchronize children of directories.
 * <p>
 * It is strongly recommended that implementations of this interface are RMI-transferrable to avoid unnecessary network
 * traffic when designing tasks for remote execution.
 * <p>
 * It is recommended that this class adheres to the {@link #equals(Object)} and {@link #hashCode()} contract.
 */
public interface DirectoryVisitPredicate {
	/**
	 * Creates a directory visitor for the subdirectory with the given name.
	 * <p>
	 * The consumer is instructed to visit the children of the directory.
	 * <p>
	 * For synchronization, returning non-<code>null</code> will cause the synchronizer to attempt synchronization of
	 * the children of the parameter. If this method returns <code>null</code>, the directory can be still synchronized
	 * (but not its children, only creating the folder on the file system) by returning <code>true</code> from
	 * {@link #visitDirectory(String, SakerDirectory)}.
	 * <p>
	 * For file collection, returning non-<code>null</code> will cause the children of the parameter directory to be
	 * visited.
	 * 
	 * @param name
	 *            The name of the directory.
	 * @param directory
	 *            The directory itself.
	 * @return The visitor for the subdirectory.
	 * @see #visitDirectory(String, SakerDirectory)
	 */
	public default DirectoryVisitPredicate directoryVisitor(String name, SakerDirectory directory) {
		return this;
	}

	/**
	 * Determines if the file with the given name should be visited by the consumer.
	 * <p>
	 * For synchronization, this means that the file will be synchronized.
	 * <p>
	 * For file collection, this means that the file will be part of the result set.
	 * 
	 * @param name
	 *            The name of the file.
	 * @param file
	 *            The file itself.
	 * @return <code>true</code> if the file should be visited.
	 */
	public boolean visitFile(String name, SakerFile file);

	/**
	 * Determines if the directory with the given name should be visited by the consumer.
	 * <p>
	 * For synchronization, returning <code>true</code> this means that the directory should be synchronized: <br>
	 * If {@link #directoryVisitor(String, SakerDirectory)} returns <code>null</code>, then only the directory will be
	 * created at the path. <br>
	 * If {@link #directoryVisitor(String, SakerDirectory)} returns non-<code>null</code>, then the synchronization of
	 * the subdirectory with its children will proceed without calling this method.
	 * <p>
	 * For file collection, returning <code>true</code> will cause the parameter to be part of the result set.
	 * 
	 * @param name
	 *            The name of the directory.
	 * @param directory
	 *            The directory itself.
	 * @return <code>true</code> if the directory should be visited.
	 */
	public boolean visitDirectory(String name, SakerDirectory directory);

	/**
	 * Gets a set of file names which should remain untouched during synchronization.
	 * <p>
	 * During the synchronization process of a directory, the files on the file system which are not present in the
	 * in-memory representation will be deleted. In other words: Any file that is present in the target directory, but
	 * not in the key-set of {@link SakerDirectory#getChildren()} will be deleted as a first step of directory
	 * synchronization. Returning a file name in this result set will cause that file to be not deleted.
	 * <p>
	 * The method can return <code>null</code>, to signal that this deletion should not take place. This can be useful
	 * when the user wants to ensure that no unknown files are deleted without knowing their names.
	 * <p>
	 * The result of this method does not affect the files which will be synchronized, but only this first stage
	 * deletion.
	 * <p>
	 * The default implementation returns an empty set, meaning that all files which are not contained on the in-memory
	 * hierarchy will be deleted.
	 * 
	 * @return The set of file names to not touch during synchronization or <code>null</code> to skip deletion.
	 */
	@RMICacheResult
	@RMIWrap(RMITreeSetStringElementWrapper.class)
	public default NavigableSet<String> getSynchronizeFilesToKeep() {
		return Collections.emptyNavigableSet();
	}

	@Override
	public int hashCode();

	/**
	 * Checks if this directory visitor would visit the same files as the parameter given the same circumstances.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj);

	/**
	 * Gets a directory visitor that visits every file and directory in the directory tree, recursively.
	 * 
	 * @return The visitor.
	 */
	public static DirectoryVisitPredicate everything() {
		return CommonDirectoryVisitPredicate.EVERYTHING;
	}

	/**
	 * Gets a directory visitor that visits none of the files.
	 * 
	 * @return The visitor.
	 */
	public static DirectoryVisitPredicate nothing() {
		return CommonDirectoryVisitPredicate.NOTHING;
	}

	/**
	 * Gets a directory visitor that visits only the children of the directory.
	 * 
	 * @return The visitor.
	 */
	public static DirectoryVisitPredicate children() {
		return CommonDirectoryVisitPredicate.CHILDREN;
	}

	/**
	 * Gets a directory visitor that visits only the child directories, not recursively.
	 * 
	 * @return The visitor.
	 */
	public static DirectoryVisitPredicate childDirectories() {
		return CommonDirectoryVisitPredicate.CHILD_DIRECTORIES;
	}

	/**
	 * Gets a directory visitor that visits only the child files, not recursively.
	 * 
	 * @return The visitor.
	 */
	public static DirectoryVisitPredicate childFiles() {
		return CommonDirectoryVisitPredicate.CHILD_FILES;
	}

	/**
	 * Gets a visitor that visits all directories in the directory tree, recursively.
	 * 
	 * @return The visitor.
	 */
	public static DirectoryVisitPredicate subDirectories() {
		return CommonDirectoryVisitPredicate.SUBDIRECTORIES;
	}

	/**
	 * Gets a directory visitor that visits all files in the directory tree, recursively.
	 * 
	 * @return The visitor.
	 */
	public static DirectoryVisitPredicate subFiles() {
		return CommonDirectoryVisitPredicate.SUBFILES;
	}
}