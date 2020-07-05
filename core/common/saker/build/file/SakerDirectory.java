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

import java.io.IOException;
import java.util.NavigableMap;

import saker.build.file.content.ContentDescriptor;
import saker.build.file.content.DirectoryContentDescriptor;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.task.TaskExecutionUtilities;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.util.rmi.wrap.RMITreeMapSerializeKeyRemoteValueWrapper;
import saker.build.thirdparty.saker.util.rmi.wrap.RMITreeMapWrapper;

/**
 * Interface for directory file representation in the build system.
 * <p>
 * Directories contain children files which can be manipulated with the appropriate methods.
 * <p>
 * Unlike {@link SakerFile}, none of the methods in this interface will cause implicit synchronizations. Trying to
 * retrieve contents of a directory will result in an {@link IOException} with unsupported message.
 * <p>
 * Directories track their children and employ lazy loading for populating their table. When the build execution is
 * started, the root directories are determined by the execution configuration. The directories do not load their
 * contained files immediately at construction time, but employ a lazy loading to fill this table. This behaviour is
 * called populating the directory, and is always happening implicitly.
 * <p>
 * Populating can occur in the following ways:
 * <ol>
 * <li>Partial population</li>
 * <li>Full population</li>
 * </ol>
 * Partial populating occurs when a specific file is accessed by name. This results in reading the attributes of the
 * file from the file system and adding the file to the table if it exists. Partial population does not occur if a
 * directory is already fully populated.
 * <p>
 * Full populating occurs in any other cases, e.g. retrieving all children, removing a file). Full populating can occur
 * for a directory at most once in its execution lifetime.
 * <p>
 * The populating behaviour only affects files which have not yet been modified by the user. If a task decides to
 * overwrite a file with a new directory, then that directory will be treated as an empty fully populated directory.
 * <p>
 * During synchronization of directories, a file system folder will be created at the specified path. A snapshot of the
 * files will be taken, and those will be synchronized accordingly. The synchronization methods can accept a
 * {@link DirectoryVisitPredicate} as a parameter to control which files and subdirectories should be synchronized.
 * Synchronizing a directory will delete any files at the child path that is not present in the in-memory representation
 * of the directory, unless the {@link DirectoryVisitPredicate#getSynchronizeFilesToKeep()} specifies othervise.
 * <p>
 * The synchronization of directories are often executed in parallel, using a thread pool.
 * <p>
 * Instances of {@link SakerFile} can be checked if they represent a directory by using the
 * <code>(file instanceof {@link SakerDirectory})</code> expression on them.
 * <p>
 * When designing tasks for remote execution it is strongly recommended that directories are accessed through methods of
 * the {@link SakerPathFiles} utility class.
 * <p>
 * Clients should not implement this interface, at all. Directories should not be treated in any other way, as just a
 * container for files.
 * 
 * @see SakerFile
 */
public interface SakerDirectory extends SakerFile {
	/**
	 * Gets the child file for the specified name.
	 * <p>
	 * A partial population will be executed if the file is not found and the directory is not yet fully populated.
	 * 
	 * @param name
	 *            The name of the file.
	 * @return The file, or <code>null</code> if not found.
	 * @throws IllegalArgumentException
	 *             If the file name is invalid or <code>null</code>.
	 */
	public SakerFile get(String name) throws IllegalArgumentException;

	/**
	 * Gets a snapshot of the children of this directory.
	 * <p>
	 * The directory will be fully populated.
	 * <p>
	 * The returned map might be unmodifiable, but is not required to be. Modifications to the return value will not be
	 * propagated back to the directory.
	 * 
	 * @return The children of this directory mapped by their names.
	 */
	@RMIWrap(RMITreeMapSerializeKeyRemoteValueWrapper.class)
	public NavigableMap<String, ? extends SakerFile> getChildren();

	/**
	 * Gets the child directory for the specified name.
	 * <p>
	 * Works the same way as {@link #get(String)}, but returns <code>null</code> if the file is not a directory.
	 * 
	 * @param name
	 *            The name of the directory.
	 * @return The directory, or <code>null</code> if no directory found for the specified name.
	 * @throws IllegalArgumentException
	 *             If the file name is invalid or <code>null</code>.
	 */
	public default SakerDirectory getDirectory(String name) throws IllegalArgumentException {
		SakerFile f = get(name);
		if (f == null) {
			return null;
		}
		if (f instanceof SakerDirectory) {
			return (SakerDirectory) f;
		}
		return null;
	}

	/**
	 * Gets a child directory with the specified name or creates one if not found.
	 * <p>
	 * Works the same way as {@link #getDirectory(String)}, but adds a new directory with the specified name if not
	 * found.
	 * <p>
	 * The newly added directory will replace any file that is present and not a directory.
	 * 
	 * @param name
	 *            The name of the directory.
	 * @return The child directory with the specified file name. Never <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the file name is invalid or <code>null</code>.
	 * @see {@link #getDirectoryCreateIfAbsent(String)}
	 */
	public SakerDirectory getDirectoryCreate(String name) throws IllegalArgumentException;

	/**
	 * Gets a child directory with the specified name or creates one if not found, but does not overwrite existing
	 * files.
	 * <p>
	 * Works the same way as {@link #getDirectoryCreate(String)}, but does not overwrite if a non-directory file is
	 * present with the specified name.
	 * 
	 * @param name
	 *            The name of the directory.
	 * @return The child directory with the specified name, or <code>null</code> if there is a non-directory file in its
	 *             place.
	 * @throws IllegalArgumentException
	 *             If the file name is invalid or <code>null</code>.
	 */
	public SakerDirectory getDirectoryCreateIfAbsent(String name) throws IllegalArgumentException;

	/**
	 * Adds a file to this directory.
	 * <p>
	 * This method will overwrite any files that exist with the same name.
	 * <p>
	 * <b>Important:</b> When designing tasks for remote execution, it should be noted that the argument file which is
	 * passed to this function will not have its parent adjusted. This means that if you add a file to a directory,
	 * calling {@link #getParent()} after this method finishes, will not return <code>this</code> directory. It is
	 * recommended to make files RMI-transferrable when executing remote tasks by overriding
	 * {@link SakerFile#getRemoteExecutionRMIWrapper()}.
	 * <p>
	 * During calling this, a delegate file will be added to the directory by default. That file will have a reference
	 * to the actual argument over RMI, and that file will be the one having <code>this</code> as a parent. In order to
	 * have a reference to the delegate file, use {@link TaskExecutionUtilities#addFile(SakerDirectory, SakerFile)},
	 * which returns the file object that is actually added to the directory.
	 * 
	 * @param file
	 *            The file to add.
	 * @return The file that was overwritten by the argument or <code>null</code> if there was no such file.
	 * @throws NullPointerException
	 *             If the file is <code>null</code>.
	 * @throws IllegalStateException
	 *             If the file already has a parent or it has been already removed from its previous one.
	 */
	public SakerFile add(@RMIWrap(RemoteExecutionSakerFileRMIWrapper.class) SakerFile file)
			throws NullPointerException, IllegalStateException;

	/**
	 * Adds a file to this directory only if there is no file present yet with the same name.
	 * <p>
	 * This method will not overwrite any files, and is a no-op if the operation cannot be completed.
	 * <p>
	 * The same remote execution related design notes apply as in {@link #add(SakerFile)}.
	 * 
	 * @param file
	 *            The file to add.
	 * @return The file that is present in this directory with the same name or <code>null</code> if the argument file
	 *             was successfully added.
	 * @throws NullPointerException
	 *             If the file is <code>null</code>.
	 * @throws IllegalStateException
	 *             If the file already has a parent or it has been already removed from its previous one.
	 * @see TaskExecutionUtilities#addFileIfAbsent(SakerDirectory, SakerFile)
	 */
	public SakerFile addIfAbsent(@RMIWrap(RemoteExecutionSakerFileRMIWrapper.class) SakerFile file)
			throws NullPointerException, IllegalStateException;

	/**
	 * Adds a file to this directory only if there is no directory present with the same name.
	 * <p>
	 * This method will add the argument file to the directory, and overwrite any files which are present with the same
	 * name. However, it will not overwrite directories which are present with the same name.
	 * <p>
	 * If a directory is present with the same name, this method call is no-op.
	 * <p>
	 * The same remote execution related design notes apply as in {@link #add(SakerFile)}.
	 * 
	 * @param file
	 *            The file to add.
	 * @return The directory that is present in this directory with the same name or <code>null</code> if the argument
	 *             file was successfully added. If a non-directory file was overwritten during this method call, still
	 *             <code>null</code> will be returned.
	 * @throws NullPointerException
	 *             If the file is <code>null</code>.
	 * @throws IllegalStateException
	 *             If the file already has a parent or it has been already removed from its previous one.
	 * @see TaskExecutionUtilities#addFileOverwriteIfNotDirectory(SakerDirectory, SakerFile)
	 */
	public SakerDirectory addOverwriteIfNotDirectory(@RMIWrap(RemoteExecutionSakerFileRMIWrapper.class) SakerFile file)
			throws NullPointerException, IllegalStateException;

	/**
	 * Removes all child files from this directory.
	 * <p>
	 * The directory will be set as fully populated during this call.
	 */
	public void clear();

	/**
	 * Checks if the directory contains any children.
	 * <p>
	 * A full population will occur if no file has been added, or no file has been partially populated for this
	 * directory yet.
	 * 
	 * @return <code>true</code> if this directory has no children.
	 */
	public default boolean isEmpty() {
		return getChildren().isEmpty();
	}

	/**
	 * Synchronizes this directory and all of its children, recursively.
	 * <p>
	 * The synchronization algorithm is described in the documentation of {@link SakerDirectory} interface.
	 * <p>
	 * The {@link DirectoryVisitPredicate#everything()} predicate will be used for synchronization.
	 * 
	 * @throws IOException
	 *             In case of I/O error or if the file doesn't have a parent.
	 */
	@Override
	public void synchronize() throws IOException;

	/**
	 * Synchronizes this directory and all of its children using the given predicate.
	 * <p>
	 * The synchronization algorithm is described in the documentation of {@link SakerDirectory} interface.
	 * 
	 * @param synchpredicate
	 *            The synchronization predicate or <code>null</code> to use
	 *            {@link DirectoryVisitPredicate#everything()}.
	 * @throws IOException
	 *             In case of I/O error or if the file doesn't have a parent.
	 */
	public void synchronize(DirectoryVisitPredicate synchpredicate) throws IOException;

	/**
	 * Synchronizes this directory and all of its children, recursively.
	 * <p>
	 * The synchronization algorithm is described in the documentation of {@link SakerDirectory} interface.
	 * <p>
	 * Same as:
	 * 
	 * <pre>
	 * synchronize(pathkey, DirectoryVisitPredicate.everything());
	 * </pre>
	 * 
	 * @param pathkey
	 *            The target location of the synchronization.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the target location is <code>null</code>.
	 */
	@Override
	public default void synchronize(ProviderHolderPathKey pathkey) throws IOException, NullPointerException {
		synchronize(pathkey, DirectoryVisitPredicate.everything());
	}

	/**
	 * Synchronizes this directory and all of its children using the given predicate.
	 * <p>
	 * The synchronization algorithm is described in the documentation of {@link SakerDirectory} interface.
	 * 
	 * @param pathkey
	 *            The target location of the synchronization.
	 * @param synchpredicate
	 *            The predicate to use for synchronization. It this is <code>null</code>,
	 *            {@link DirectoryVisitPredicate#everything()} will be used.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the target location is <code>null</code>.
	 */
	public void synchronize(ProviderHolderPathKey pathkey, DirectoryVisitPredicate synchpredicate)
			throws IOException, NullPointerException;

	/**
	 * Gets the children of this directory tree with for a given predicate.
	 * <p>
	 * This methods accepts a base path as a parameter which is used to resolve the relative paths of the children. This
	 * can be used to efficiently construct a map that contains the files based on the needs of the caller.
	 * <p>
	 * The result map doesn't include <code>this</code> directory instance.
	 * <p>
	 * Pass {@link SakerPath#EMPTY} to have the key paths remain relative to <code>this</code> directory, or pass the
	 * result of {@link #getSakerPath()} to construct a map with absolute key paths.
	 * <p>
	 * Convenience functions with less parameters available in {@link SakerPathFiles#getFilesRecursiveByPath}
	 * 
	 * @param basepath
	 *            The base path to resolve the relative paths against.
	 * @param filepredicate
	 *            The predicate to define the files to visit. If this is <code>null</code>,
	 *            {@link DirectoryVisitPredicate#everything()} will be used.
	 * @return The collected children. The returned map is mutable.
	 * @throws NullPointerException
	 *             If base path is <code>null</code>.
	 */
	@RMIWrap(RMITreeMapSerializeKeyRemoteValueWrapper.class)
	public NavigableMap<SakerPath, SakerFile> getFilesRecursiveByPath(SakerPath basepath,
			DirectoryVisitPredicate filepredicate) throws NullPointerException;

	/**
	 * Gets a snapshot of the children of this directory mapped to an {@link SakerFileContentInformationHolder}
	 * instance.
	 * <p>
	 * The method returns the children of the directory the same way as {@link #getChildren()}, but includes additional
	 * information alongside the {@link SakerFile} instance.
	 * <p>
	 * The argument predicate is used to determine for which files to include additional information. If
	 * {@link DirectoryVisitPredicate#visitFile(String, SakerFile) visitFile} returns <code>false</code>, then only the
	 * {@link SakerFile} instance will be contained in the associated {@link SakerFileContentInformationHolder}.
	 * 
	 * @param childpredicate
	 *            The predicate to use. May be <code>null</code>, in which case
	 *            {@link DirectoryVisitPredicate#everything()} is used.
	 * @return The directory children mapped to their content information holders.
	 * @since saker.build 0.8.15
	 */
	@RMIWrap(RMITreeMapWrapper.class)
	public NavigableMap<String, ? extends SakerFileContentInformationHolder> getChildrenContentInformation(
			DirectoryVisitPredicate childpredicate);

	/**
	 * {@inheritDoc}
	 * <p>
	 * Always returns {@link DirectoryContentDescriptor#INSTANCE} for directories.
	 */
	@Override
	public ContentDescriptor getContentDescriptor();
}
